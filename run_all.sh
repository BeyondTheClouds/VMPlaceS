#! /bin/bash

PID_FILE=/tmp/vmplaces.pid

source xprc

function do_abort() {
	echo Killing java process with PID `cat $PID_FILE`
	kill -9 `cat $PID_FILE`
	rm -f $PID_FILE
	exit 2
}

trap do_abort SIGINT

error=0

# run <nb nodes> <algo> <implem> <turn off> <threshold>
# Ex: run 128 centralized scheduling.centralized.entropy2.Entropy2RP
function run() {
	n_nodes=$1
	algo=$2
	implem=$3
	turn_off=$4
	threshold=100

	if [ "$3" != "none" ]
	then
		implem="-Dsimulator.implementation=$implem"

		# Yes, this is ugly
		name=`echo ${implem} | rev | cut -d "." -f1 | rev`
		name="${algo}-${name}-${n_nodes}-${turn_off}"
	else
		implem=''
		name="${algo}-${n_nodes}-${turn_off}"
	fi

	if [ ! -z "$5" ]
	then
		threshold="$5"
		name="${name}-${threshold}"
	fi

	n_service='1'
	case "$algo" in
		"centralized")
			n_service=1
			;;
		"hierarchical")
			n_service=$(($n_nodes / 32 + 1))
			;;
		"distributed")
			n_service=0
			;;
	esac

	n_vms=$(($n_nodes * 10))
	mean=60
	std=20

	SIM_ARGS="-Dsimulator.algorithm=$algo $implem"
	SIM_ARGS="$SIM_ARGS -Dhostingnodes.number=$n_nodes"
	SIM_ARGS="$SIM_ARGS -Dservicenodes.number=$n_service"
	SIM_ARGS="$SIM_ARGS -Dvm.number=$n_vms"
	SIM_ARGS="$SIM_ARGS -Dhostingnodes.cpunumber=8"
	SIM_ARGS="$SIM_ARGS -Dhostingnodes.memorytotal=32768"
	SIM_ARGS="$SIM_ARGS -Dhosts.turn_off=$turn_off"
	SIM_ARGS="$SIM_ARGS -Dload.mean=$mean"
	SIM_ARGS="$SIM_ARGS -Dload.std=$std"
	SIM_ARGS="$SIM_ARGS -Dsimulator.ffd.threshold=$threshold"

	echo '----------------------------------------'
	echo "Running $algo $implem with $n_nodes compute and $n_service service nodes turning off hosts: $turn_off, load.mean=$mean, load.std=$std"
	echo "Command: java $VM_OPTIONS $SIM_ARGS simulation.Main $PROGRAM_ARGUMENTS"
	echo '----------------------------------------'
	java $VM_OPTIONS $SIM_ARGS simulation.Main $PROGRAM_ARGUMENTS &
	pid=$!
	echo $pid > $PID_FILE
	wait $pid
	ret=$?

	echo java returned $ret

	if [ $ret -ne 0 ] && [ $ret -ne 134 ]
	then
		error=1
		exit $ret
	fi

	mkdir -p visu/events/$name
	cp events.json visu/events/$name/
}

#######################################
#                Main                 #
#######################################

# Number of hosting nodes
nodes='64'
thresholds='100 90 80 70 60 50'
abort=0

rm -rf logs/ffd
rm -i energy.dat

{
	for n in $nodes; do
		run $n centralized scheduling.centralized.entropy2.Entropy2RP false
		run $n centralized scheduling.centralized.entropy2.Entropy2RP true

#		run $n centralized scheduling.centralized.ffd.OptimisticFirstFitDecreased false
#		run $n centralized scheduling.centralized.ffd.OptimisticFirstFitDecreased true

		for th in $thresholds; do
			run $n centralized scheduling.centralized.ffd.LazyFirstFitDecreased false $th
			run $n centralized scheduling.centralized.ffd.LazyFirstFitDecreased true $th
		done
	done
} 2>&1 | tee run_all.log


if [ ! $error ]
then
	visu/energy_plot.py run_all.log energy.dat
fi
