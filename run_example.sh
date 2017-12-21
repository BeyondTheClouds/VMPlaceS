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

function run() {
	echo '----------------------------------------'
	echo "Running $algo $implem with $n_nodes compute and $n_service service nodes turning off hosts: $turn_off, load.mean=$mean, load.std=$std"
	echo "Command: java $VM_OPTIONS $SIM_ARGS simulation.Main $PROGRAM_ARGUMENTS"
	echo "Command: PROGRAM_ARGUMENTS $PROGRAM_ARGUMENTS"
	echo '----------------------------------------'
	java $VM_OPTIONS simulation.Main $PROGRAM_ARGUMENTS &
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
abort=0

rm -rf logs/ffd

{
	run $nodes example scheduling.simple.ExampleReconfigurationPlanner false
} 2>&1 | tee run_all.log


if [ ! $error ]
	then
		visu/energy_plot.py run_all.log energy.dat
	fi
