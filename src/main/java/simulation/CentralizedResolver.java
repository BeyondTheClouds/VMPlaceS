package simulation;

import java.util.Random;

import configuration.SimulatorProperties;
import configuration.XHost;
import entropy.configuration.*;
import org.simgrid.msg.*;

import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;

import scheduling.EntropyProperties;
import scheduling.Scheduler.ComputingState;
import scheduling.Scheduler;
import scheduling.entropy.Entropy2RP;


public class CentralizedResolver extends Process {

    static int ongoingMigration = 0 ;
    static int loopID = 0 ;

	CentralizedResolver(Host host, String name, String[] args) throws HostNotFoundException, NativeException  {
		super(host, name, args);
	}

	/**
	 * @param args
     * A stupid main to easily comment main2 ;)
	 */
    public void main(String[] args) throws MsgException{
       main2(args);
    }
    public void main2(String[] args) throws MsgException{
        double period = EntropyProperties.getEntropyPeriodicity();
		int numberOfCrash = 0;

		Trace.hostSetState(SimulatorManager.getServiceNodeName(), "SERVICE", "free");

        long previousDuration = 0;

        while(!SimulatorManager.isEndOfInjection()) {

            long wait = ((long)(period*1000)) - previousDuration;
            if (wait > 0)
                Process.sleep(wait); // instead of waitFor that takes into account only seconds

			/* Compute and apply the plan */
			Scheduler scheduler;
			long beginTimeOfCompute;
			long endTimeOfCompute;
			long computationTime;
			ComputingState computingState;
            double reconfigurationTime = 0;

			/* Tracing code */
			Trace.hostSetState(SimulatorManager.getServiceNodeName(), "SERVICE", "compute");
		    int i;
            for (XHost h:SimulatorManager.getSGHosts()){
				if(!h.isViable())
					Trace.hostPushState(h.getName(), "PM", "violation-det");
				Trace.hostSetState(h.getName(), "SERVICE", "booked");
            }

			Msg.info("Launching scheduler (loopId = "+loopID+") - start to compute");

			scheduler = new Entropy2RP((Configuration)Entropy2RP.ExtractConfiguration(SimulatorManager.getSGHosts()), loopID ++);

            beginTimeOfCompute = System.currentTimeMillis();
			computingState = scheduler.computeReconfigurationPlan();
			endTimeOfCompute = System.currentTimeMillis();
			computationTime = (endTimeOfCompute - beginTimeOfCompute);

            Process.sleep(computationTime); // instead of waitFor that takes into account only seconds

			Msg.info("Computation time (in ms):" + computationTime);
            previousDuration = computationTime ;

			if(computingState.equals(ComputingState.NO_RECONFIGURATION_NEEDED)){
				Msg.info("Configuration remains unchanged");
				Trace.hostSetState(SimulatorManager.getServiceNodeName(), "SERVICE", "free");
			} else if(computingState.equals(ComputingState.SUCCESS)){
				int cost = scheduler.getReconfigurationPlanCost();

				/* Tracing code */
				// TODO Adrien -> Adrien, try to consider only the nodes that are impacted by the reconfiguration plan
				for (XHost h: SimulatorManager.getSGHosts())
					Trace.hostSetState(h.getName(), "SERVICE", "reconfigure");

				Msg.info("Starting reconfiguration");
                double startReconfigurationTime =  Msg.getClock() * 1000;
				scheduler.applyReconfigurationPlan();
				double endReconfigurationTime =  Msg.getClock() * 1000;
                reconfigurationTime = endReconfigurationTime - startReconfigurationTime;
                Msg.info("Reconfiguration time (in ms): "+ reconfigurationTime);
                previousDuration += reconfigurationTime ;

				Msg.info("Number of nodes used: " + SimulatorManager.getNbOfActiveHosts()) ;

			} else { 
				System.err.println("The resolver does not find any solutions - EXIT");
				numberOfCrash++;
				Msg.info("Entropy has encountered an error (nb: " + numberOfCrash +")");
			}

		/* Tracing code */
	    	for (XHost h: SimulatorManager.getSGHosts())
					Trace.hostSetState(h.getName(), "SERVICE", "free");

            Trace.hostSetState(SimulatorManager.getServiceNodeName(), "SERVICE", "free");

		}

	}


    private static void incMig(){
        Trace.hostVariableAdd("node0", "NB_MIG", 1);
        ongoingMigration++ ;
    }
    private static void decMig() {
        ongoingMigration-- ;
    }

    public static boolean ongoingMigration() {
        return (ongoingMigration != 0);
    }

    public static void relocateVM(String VMName, String sourceName, String destName) {
        Random rand = new Random(SimulatorProperties.getSeed());

        Msg.info("Relocate VM "+VMName+" (from "+sourceName+" to "+destName+")");

        if(destName != null){
            String[] args = new String[3];

            args[0] = VMName;
            args[1] = sourceName;
            args[2] = destName;
            // Asynchronous migration
            // The process is launched on the source node
            try {
                CentralizedResolver.incMig();
                new Process(Host.getByName(sourceName),"Migrate-"+rand.nextDouble(),args) {
                    public void main(String[] args){
                        XHost destHost = null;
                        XHost sourceHost = null;
                        try {
                            sourceHost = SimulatorManager.getXHostByName(args[1]);
                            destHost = SimulatorManager.getXHostByName(args[2]);
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.err.println("You are trying to migrate from/to a non existing node");
                        }
                        if(destHost != null){
                            sourceHost.migrate(args[0],destHost);
                            Msg.info("End of migration of VM " + args[0] + " from " + args[1] + " to " + args[2]);
                            // Decrement the number of on-going migrating process
                            CentralizedResolver.decMig();
                            if(!destHost.isViable()){
                                Msg.info("ARTIFICIAL VIOLATION ON "+destHost.getName()+"\n");
                                Trace.hostSetState(destHost.getName(), "PM", "violation-out");
                            }
                            if(sourceHost.isViable()){
                                Msg.info("SOLVED VIOLATION ON "+sourceHost.getName()+"\n");
                                Trace.hostSetState(sourceHost.getName(), "PM", "normal");
                            }
                        }
                    }
                }.start();

            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            System.err.println("You are trying to relocate a VM on a non existing node");
            System.exit(-1);
        }
    }
}
