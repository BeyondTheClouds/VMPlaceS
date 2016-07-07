#! /bin/bash

source xprc

function do_abort() {
	kill -2 $current_pid
	exit 2
}

trap do_abort SIGINT

error=0

# run <nb nodes> <algo> <implem>
# Ex: run 128 centralized scheduling.centralized.entropy2.Entropy2RP
function run() {
	n_nodes=$1
	algo=$2
	implem=$3
	turn_off=$4

	if [ "$3" != "none" ]
	then
		implem="-Dsimulator.implementation=$implem"

		# Yes, this is ugly
		name=`echo ${implem} | rev | cut -d "." -f1 | rev`
		name="${algo}-${name}-${n_nodes}"
	else
		implem=''
		name="${algo}-${n_nodes}"
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

	n_vms=$(($n_nodes * 7))

	SIM_ARGS="-Dsimulator.algorithm=$algo $implem"
	SIM_ARGS="$SIM_ARGS -Dhostingnodes.number=$n_nodes"
	SIM_ARGS="$SIM_ARGS -Dservicenodes.number=$n_service"
	SIM_ARGS="$SIM_ARGS -Dvm.number=$n_vms"
	SIM_ARGS="$SIM_ARGS -Dhostingnodes.cpunumber=8"
	SIM_ARGS="$SIM_ARGS -Dhostingnodes.memorytotal=131072"
	SIM_ARGS="$SIM_ARGS -Dhosts.turn_off=$turn_off"

	echo '----------------------------------------'
	echo "Running $algo $implem with $n_nodes compute and $n_service service nodes turning off hosts: $turn_off"
	echo "Command: java $VM_OPTIONS $SIM_ARGS simulation.Main $PROGRAM_ARGUMENTS"
	echo '----------------------------------------'
	java $VM_OPTIONS $SIM_ARGS simulation.Main $PROGRAM_ARGUMENTS &
	current_pid=$!
	wait $current_pid
	ret=$?

	echo Returned $ret

	if [ $ret -ne 0 && $ret -ne 131 ]
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
abort=0

rm -rf logs/ffd

{
	for n in $nodes; do
		run $n centralized scheduling.centralized.entropy2.Entropy2RP false
		run $n centralized scheduling.centralized.btrplace.BtrPlaceRP false
		run $n centralized scheduling.centralized.ffd.LazyFirstFitDecreased false
		run $n centralized scheduling.centralized.ffd.OptimisticFirstFitDecreased false

		run $n centralized scheduling.centralized.entropy2.Entropy2RP true
		run $n centralized scheduling.centralized.btrplace.BtrPlaceRP true
		run $n centralized scheduling.centralized.ffd.LazyFirstFitDecreased true
		run $n centralized scheduling.centralized.ffd.OptimisticFirstFitDecreased true

		#run $n hierarchical false

		#run $n distributed false
	done
} 2>&1 | tee run_all.log

if [ ! $error ]
	then
		visu/energy_plot.py run_all.log energy.dat
	fi
