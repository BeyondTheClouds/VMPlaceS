#! /bin/bash

VM_OPTIONS='-Xmx4G -d64 -Dlogback.configurationFile=config/logback.xml -cp target/simulation.jar:../simgrid/simgrid.jar'
PROGRAM_ARGUMENTS='./config/three_servers.xml ./config/three_servers_deploy.xml --cfg=cpu/optim:Full --cfg=tracing:1 --cfg=tracing/filename:simu.trace --cfg=tracing/platform:1'

END=0
trap "END=1" SIGINT SIGTERM

function run_migration() {
	load=$1
	dp_intensity=$2

	cat > config/three_servers_deploy.xml << EOL
<?xml version='1.0'?>
<!DOCTYPE platform SYSTEM "http://simgrid.gforge.inria.fr/simgrid/simgrid.dtd">
<platform version="4">
  <process host="node2" function="migration.Migrator">
  	<argument value="$load" />
  	<argument value="$dp_intensity" />
  </process>
</platform>
EOL

	java $VM_OPTIONS migration.Migration $PROGRAM_ARGUMENTS
}

########################################
# Main
########################################

run_migration 80 80 &
wait $!
exit

for i in `seq 10 10 90`; do
	for j in `seq 10 10 80`; do
		if [ $END -eq 1 ]; then
			echo "Interrupted"
			exit 1
		fi

		run_migration $i $j &
		wait $!
	done
done
