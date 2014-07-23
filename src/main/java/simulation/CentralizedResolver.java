package simulation;

import java.util.Random;

import configuration.SimulatorProperties;
import configuration.XHost;
import entropy.configuration.*;
import org.simgrid.msg.*;

import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;

import scheduling.entropyBased.EntropyProperties;
import scheduling.Scheduler.ComputingState;
import scheduling.Scheduler;
import scheduling.entropyBased.entropy2.Entropy2RP;


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
    public void main2(String[] args) throws MsgException {
        double period = EntropyProperties.getEntropyPeriodicity();
        int numberOfCrash = 0;
        int numberOfBrokenPlan = 0;

        Trace.hostSetState(SimulatorManager.getInjectorNodeName(), "SERVICE", "free");

        long previousDuration = 0;
        Entropy2RP scheduler;
        Entropy2RP.Entropy2RPRes entropyRes;
        while (!SimulatorManager.isEndOfInjection()) {

            long wait = ((long) (period * 1000)) - previousDuration;
            if (wait > 0)
                Process.sleep(wait); // instead of waitFor that takes into account only seconds

			/* Compute and apply the plan */
            scheduler = new Entropy2RP((Configuration) Entropy2RP.ExtractConfiguration(SimulatorManager.getSGHostingHosts()), loopID++);
            entropyRes = scheduler.checkAndReconfigure(SimulatorManager.getSGHostingHosts());
            previousDuration = entropyRes.getDuration();
            if (entropyRes.getRes() == 0) {
                Msg.info("Reconfiguration ok (duration: " + previousDuration + ")");
            } else if (entropyRes.getRes() == -1) {
                Msg.info("No viable solution (duration: " + previousDuration + ")");
                numberOfCrash++;
            } else { // res == -2 Reconfiguration has not been correctly performed
                Msg.info("Reconfiguration plan has been broken (duration: " + previousDuration + ")");
                numberOfBrokenPlan++;
            }
        }
        Msg.info("Entropy2RP did not find solutions "+numberOfCrash+" times / "+loopID+" and "+numberOfBrokenPlan+" plans have not been completely performed");

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
                            if( !sourceHost.isOff() && !destHost.isOff() && sourceHost.migrate(args[0],destHost) == 0 ) {
                                Msg.info("End of migration of VM " + args[0] + " from " + args[1] + " to " + args[2]);
                                // Decrement the number of on-going migrating process
                                CentralizedResolver.decMig();
                                if (!destHost.isViable()) {
                                    Msg.info("ARTIFICIAL VIOLATION ON " + destHost.getName() + "\n");
                                    Trace.hostSetState(destHost.getName(), "PM", "violation-out");
                                }
                                if (sourceHost.isViable()) {
                                    Msg.info("SOLVED VIOLATION ON " + sourceHost.getName() + "\n");
                                    Trace.hostSetState(sourceHost.getName(), "PM", "normal");
                                }
                            }else{
                                // TODO raise an exception since the migration crash and thus the reconfigurationPlan is not valid anymore
                                //CentralizedResolved.setRPDeprecated();
                                Msg.info("Reconfiguration plan cannot be completely applied so abort it");
                                System.exit(-1);
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
