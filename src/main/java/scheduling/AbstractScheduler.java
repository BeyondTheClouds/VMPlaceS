package scheduling;

import configuration.SimulatorProperties;
import configuration.XHost;
import org.simgrid.msg.*;
import simulation.SimulatorManager;
import trace.Trace;

import java.util.Collection;
import java.util.Random;

/**
 * Abstract scheduler that must be extended by implemented algorithms.
 *
 * @param <Conf> Class representing the configuration of the implemented algorithm
 * @param <RP> Class representing the reconfiguration plan of the implemented algorithm
 */
public abstract class AbstractScheduler<Conf, RP> implements Scheduler {

	/**
	 * The initial configuration.
	 */
	public Conf source;

	/**
	 * The resulting configuration.
	 */
	public Conf destination;

	/**
	 * The computed reconfiguration plan
	 */
	public RP reconfigurationPlan;
	/**
     * Indicates if the reconfiguration plan has been aborted
     */
    public boolean isRPAborted;

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
	
	//The number of migrations inside the reconfiguration plan
	protected int nbMigrations;
	
	//The depth of the graph of the reconfiguration actions
	protected int planGraphDepth;

    //Adrien, just a hack to serialize configuration and reconfiguration into a particular file name
    protected int id;

    // Indicates if a migration is ongoing.
	private int ongoingMigration = 0 ;

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

    /**
     * Method used in the constructor to map the VMPlaceS configuration to the implemented algorithm configuration.
     * @param xHosts xHosts
     * @return the created configuration
     */
	protected abstract Conf extractConfiguration(Collection<XHost> xHosts);

    /**
     * Applies the reconfiguration plan from source model to dest model.
     */
    protected abstract void applyReconfigurationPlan();

	private void incMig(){
        this.ongoingMigration++ ;
        Trace.hostVariableAdd(SimulatorManager.getInjectorNodeName(), "NB_MIG", 1);
    }

	private void decMig() {
        this.ongoingMigration-- ;
    }

	protected boolean ongoingMigration() {
        return (this.ongoingMigration != 0);
    }

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
                                incMig();

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
                                    isRPAborted = true;

                                }
                                decMig();
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
