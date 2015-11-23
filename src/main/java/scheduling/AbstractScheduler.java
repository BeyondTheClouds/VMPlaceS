package scheduling;

import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.*;
import simulation.SimulatorManager;
import trace.Trace;

import java.util.Collection;
import java.util.Random;

/**
 * Abstract scheduler that must be extended by implemented algorithms.
 *
 * @param <Configuration> Class representing the configuration of the implemented algorithm
 * @param <ReconfigurationPlan> Class representing the reconfiguration plan of the implemented algorithm
 */
public abstract class AbstractScheduler<Planner, Configuration, ReconfigurationPlan> implements Scheduler {

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Fields //////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Planner.
     */
    protected Planner planner;

	/**
	 * The initial configuration.
	 */
	protected Configuration source;

	/**
	 * The resulting configuration.
	 */
	protected Configuration destination;

	/**
	 * The computed reconfiguration plan
	 */
	protected ReconfigurationPlan reconfigurationPlan;

	/**
     * Indicates if the reconfiguration plan has been aborted
     */
    protected boolean rpAborted;

	/**
	 * The time spent to compute VMPP
	 * @deprecated Please consider that this value is currently deprecated and will be set to zero until it will be fixed - Adrien, Nov 18 2011
	 */
	protected long timeToComputeVMPP;

	/**
	 * The time spent to compute the reconfiguration plan.
	 */
	protected long timeToComputeVMRP;

	/**
	 * 	The time spent to apply the reconfiguration plan.
	 */
	protected long timeToApplyReconfigurationPlan;

	/**
	 * 	The cost of the reconfiguration plan.
	 */
	protected int planCost;

    /**
     * The number of migrations inside the reconfiguration plan.
     */
	protected int nbMigrations;
	
    /**
     * The depth of the graph of the reconfiguration actions.
     */
	protected int planGraphDepth;

    /**
     * Id to serialize configuration and reconfiguration into a particular file name.
     */
    protected int id;

    /**
     * Number of ongoing migrations.
     */
	private int ongoingMigrations = 0 ;

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Constructors ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
	 * Constructor initializing fields and creating the source configuration regarding xHosts.
	 */
	protected AbstractScheduler(Collection<XHost> xHosts){
		timeToComputeVMPP = 0;
		timeToComputeVMRP = 0;
		timeToApplyReconfigurationPlan = 0;
		planCost = 0;
		nbMigrations = 0;
		planGraphDepth = 0;
        this.source = this.extractConfiguration(xHosts);
	}


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Abstract methods ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Method used in the constructor to map the VMPlaceS configuration to the implemented algorithm configuration.
     * @param xHosts xHosts
     * @return the created configuration
     */
	protected abstract Configuration extractConfiguration(Collection<XHost> xHosts);

    /**
     * Applies the reconfiguration plan from source model to dest model.
     */
    protected abstract void applyReconfigurationPlan();

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Implemented methods /////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Incs the number of ongoing migrations.
     */
	private void incOngoingMigrations(){
        this.ongoingMigrations++ ;
        Trace.hostVariableAdd(SimulatorManager.getInjectorNodeName(), "NB_MIG", 1);
    }

    /**
     * Decs the number of ongoing migrations.
     */
	private void decOngoingMigrations() {
        this.ongoingMigrations-- ;
    }

    /**
     * Indicates if there are still some ongoing migrations.
     * @return true or false
     */
	protected boolean ongoingMigrations() {
        return (this.ongoingMigrations != 0);
    }


    /**
     * Relocated a VM according to a reconfiguration plan.
     * @param vmName vm name
     * @param sourceName source configuration name
     * @param destName destination configuration name
     */
	public void relocateVM(final String vmName, final String sourceName, final String destName) {

        Random rand = new Random(SimulatorProperties.getSeed());

        Msg.info("Relocate VM " + vmName + " (from " + sourceName + " to " + destName + ")");

        if (destName != null) {
            String[] args = new String[3];

            args[0] = vmName;
            args[1] = sourceName;
            args[2] = destName;
            // Asynchronous migration
            // The process is launched on the source node
            try {
                new org.simgrid.msg.Process(Host.getByName(sourceName), "Migrate-" + rand.nextDouble(), args) {
                    public void main(String[] args) {
                        XHost destHost = null;
                        XHost sourceHost = null;

                        try {
                            sourceHost = SimulatorManager.getXHostByName(args[1]);
                            destHost = SimulatorManager.getXHostByName(args[2]);
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.err.println("You are trying to migrate from/to a non existing node");
                        }

                        if (destHost != null) {
                            if (!sourceHost.isOff() && !destHost.isOff()) {
                                incOngoingMigrations();

                                double timeStartingMigration = Msg.getClock();
                                Trace.hostPushState(vmName, "SERVICE", "migrate", String.format("{\"vm_name\": \"%s\", \"from\": \"%s\", \"to\": \"%s\"}", vmName, sourceName, destName));
                                int res = sourceHost.migrate(args[0], destHost);
                                // TODO, we should record the res of the migration operation in order to count for instance how many times a migration crashes ?
                                // To this aim, please extend the hostPopState API to add meta data information
                                Trace.hostPopState(vmName, "SERVICE", String.format("{\"vm_name\": \"%s\", \"result\": %d}", vmName, res));
                                double migrationDuration = Msg.getClock() - timeStartingMigration;

                                if (res == 0) {
                                    Msg.info("End of migration of VM " + args[0] + " from " + args[1] + " to " + args[2]);

                                    if (!destHost.isViable()) {
                                        Msg.info("ARTIFICIAL VIOLATION ON " + destHost.getName() + "\n");
                                        // If Trace.hostGetState(destHost.getName(), "PM").equals("normal")
                                        Trace.hostSetState(destHost.getName(), "PM", "violation-out");
                                    }
                                    if (sourceHost.isViable()) {
                                        Msg.info("SOLVED VIOLATION ON " + sourceHost.getName() + "\n");
                                        Trace.hostSetState(sourceHost.getName(), "PM", "normal");
                                    }

                                    /* Export that the migration has finished */
                                    Trace.hostSetState(vmName, "migration", "finished", String.format("{\"vm_name\": \"%s\", \"from\": \"%s\", \"to\": \"%s\", \"duration\": %f}", vmName, sourceName, destName, migrationDuration));
                                    Trace.hostPopState(vmName, "migration");
                                } else {

                                    Trace.hostSetState(vmName, "migration", "failed", String.format("{\"vm_name\": \"%s\", \"from\": \"%s\", \"to\": \"%s\", \"duration\": %f}", vmName, sourceName, destName, migrationDuration));
                                    Trace.hostPopState(vmName, "migration");

                                    Msg.info("Something was wrong during the migration of  " + args[0] + " from " + args[1] + " to " + args[2]);
                                    Msg.info("Reconfiguration plan cannot be completely applied so abort it");
                                    rpAborted = true;

                                }
                                decOngoingMigrations();
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

    /**
     * Suspends a VM.
     * @param vmName vm name
     * @param hostName host name
     */
    public void suspendVM(final String vmName, final String hostName) {

        Msg.info("Suspending VM " + vmName + " on " + hostName);

        if (vmName != null) {
            Random rand = new Random(SimulatorProperties.getSeed());

            String[] args = new String[2];
            args[0] = vmName;
            args[1] = hostName;

            // Todo Killian - Why am I doing this rand.nextDouble() thing ?
            try {
                new org.simgrid.msg.Process(Host.getByName(hostName), "Suspend-" + rand.nextDouble(), args) {

                    @Override
                    public void main(String[] args) throws MsgException {
                        XVM vm =  SimulatorManager.getXVMByName(args[0]);
                        XHost host = SimulatorManager.getXHostByName(args[1]);

                        if (vm != null) {
                            double timeStartingSuspension = Msg.getClock();
                            Trace.hostPushState(args[0], "SERVICE", "suspend", String.format("{\"vm_name\": \"%s\", \"on\": \"%s\"}", args[0], args[1]));
                            int res = vm.suspend();
                            Trace.hostPopState(args[0], "SERVICE", String.format("{\"vm_name\": \"%s\", \"result\": %d}", args[0], res));
                            double suspensionDuration = Msg.getClock() - timeStartingSuspension;

                            switch (res) {
                                case 0:
                                    Msg.info("End of migration of VM " + args[0] + " from " + args[1] + " to " + args[2]);

                                    if (host.isViable()) {
                                        Msg.info("SOLVED VIOLATION ON " + host.getName() + "\n");
                                        Trace.hostSetState(host.getName(), "PM", "normal");
                                    } else {
                                        Msg.info("ARTIFICIAL VIOLATION ON " + host.getName() + "\n");
                                        Trace.hostSetState(host.getName(), "PM", "violation-out");
                                    }

                                    /* Export that the suspension has finished */
                                    Trace.hostSetState(args[0], "suspension", "finished", String.format("{\"vm_name\": \"%s\", \"on\": \"%s\", \"duration\": %f}", args[0], args[1], suspensionDuration));
                                    Trace.hostPopState(args[0], "suspension");
                                case 1:
                                    Trace.hostSetState(args[0], "suspension", "cancelled", String.format("{\"vm_name\": \"%s\", \"on\": \"%s\", \"duration\": %f}", args[0], args[1], suspensionDuration));
                                    Trace.hostPopState(args[0], "suspension");

                                    Msg.info("The VM " + args[0] + " on " + args[1] + " is already suspended.");
                                    // Todo : no need to abort the ReconfigurationPlan here ?
                                case -1:
                                    Trace.hostSetState(args[0], "suspension", "failed", String.format("{\"vm_name\": \"%s\", \"on\": \"%s\", \"duration\": %f}", args[0], args[1], suspensionDuration));
                                    Trace.hostPopState(args[0], "suspension");

                                    Msg.info("Something went wrong during the suspension of  " + args[0] + " on " + args[1]);
                                    Msg.info("Reconfiguration plan cannot be completely applied so abort it");
                                    rpAborted = true;
                                default:
                                    System.err.println("Unexpected result from XVM.suspend()");
                                    System.exit(-1);
                            }
                        }
                    }
                }.start();
            } catch (HostNotFoundException e) {
                e.printStackTrace();
                // Todo better exc handling
            }
        } else {
            System.err.println("You are trying to suspend a non-existing VM");
            System.exit(-1);
        }
    }

    /**
     * Resumes a VM.
     * @param vmName vm name
     * @param hostName host name
     */
    public void resumeVM(final String vmName, final String hostName) {
        Msg.info("Resuming VM " + vmName + " on " + hostName);

        if (vmName != null) {
            Random rand = new Random(SimulatorProperties.getSeed());

            String[] args = new String[2];
            args[0] = vmName;
            args[1] = hostName;

            // Todo Killian - Why am I doing this rand.nextDouble() thing ?
            try {
                new org.simgrid.msg.Process(Host.getByName(hostName), "Resume-" + rand.nextDouble(), args) {

                    @Override
                    public void main(String[] args) throws MsgException {
                        XVM vm =  SimulatorManager.getXVMByName(args[0]);
                        XHost host = SimulatorManager.getXHostByName(args[1]);

                        if (vm != null) {
                            double timeStartingResumption = Msg.getClock();
                            Trace.hostPushState(args[0], "SERVICE", "resume", String.format("{\"vm_name\": \"%s\", \"on\": \"%s\"}", args[0], args[1]));
                            int res = vm.resume();
                            Trace.hostPopState(args[0], "SERVICE", String.format("{\"vm_name\": \"%s\", \"result\": %d}", args[0], res));
                            double resumptionDuration = Msg.getClock() - timeStartingResumption;

                            switch (res) {
                                case 0:
                                    Msg.info("End of resumption of VM " + args[0] + " from " + args[1] + " to " + args[2]);

                                    if (host.isViable()) {
                                        Msg.info("SOLVED VIOLATION ON " + host.getName() + "\n");
                                        Trace.hostSetState(host.getName(), "PM", "normal");
                                    } else {
                                        Msg.info("ARTIFICIAL VIOLATION ON " + host.getName() + "\n");
                                        Trace.hostSetState(host.getName(), "PM", "violation-out");
                                    }

                                    /* Export that the suspension has finished */
                                    Trace.hostSetState(args[0], "resumption", "finished", String.format("{\"vm_name\": \"%s\", \"on\": \"%s\", \"duration\": %f}", args[0], args[1], resumptionDuration));
                                    Trace.hostPopState(args[0], "resumption");
                                case 1:
                                    Trace.hostSetState(args[0], "resumption", "cancelled", String.format("{\"vm_name\": \"%s\", \"on\": \"%s\", \"duration\": %f}", args[0], args[1], resumptionDuration));
                                    Trace.hostPopState(args[0], "resumption");

                                    Msg.info("The VM " + args[0] + " on " + args[1] + " is already suspended.");
                                    // Todo : no need to abort the ReconfigurationPlan here ?
                                case -1:
                                    Trace.hostSetState(args[0], "resumption", "failed", String.format("{\"vm_name\": \"%s\", \"on\": \"%s\", \"duration\": %f}", args[0], args[1], resumptionDuration));
                                    Trace.hostPopState(args[0], "resumption");

                                    Msg.info("Something went wrong during the resumption of  " + args[0] + " on " + args[1]);
                                    Msg.info("Reconfiguration plan cannot be completely applied so abort it");
                                    rpAborted = true;
                                default:
                                    System.err.println("Unexpected result from XVM.resume()");
                                    System.exit(-1);
                            }
                        }
                    }
                }.start();
            } catch (HostNotFoundException e) {
                e.printStackTrace();
                // Todo better exc handling
            }
        } else {
            System.err.println("You are trying to resume a non-existing VM");
            System.exit(-1);
        }
    }

}
