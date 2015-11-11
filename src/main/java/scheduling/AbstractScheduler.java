package scheduling;

import configuration.XHost;

import java.util.Collection;

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
	protected int reconfigurationPlanCost;
	
	//The number of migrations inside the reconfiguration plan
	protected int nbMigrations;
	
	//The depth of the graph of the reconfiguration actions
	protected int reconfigurationGraphDepth;

    //Adrien, just a hack to serialize configuration and reconfiguration into a particular file name
    protected int id;

	/**
	 * Constructor initializing fields and creating the source configuration regarding xHosts.
	 */
	protected AbstractScheduler(Collection<XHost> xHosts){
		timeToComputeVMPP = 0;
		timeToComputeVMRP = 0;
		timeToApplyReconfigurationPlan = 0;
		reconfigurationPlanCost = 0;
		nbMigrations = 0;
		reconfigurationGraphDepth = 0;
        this.source = this.extractConfiguration(xHosts);
	}

    /**
     * Method used in the constructor to get map the VMPlaceS configuration to the implemented algorithm configuration.
     * @param xHosts xHosts
     * @return the created configuration
     */
	protected abstract Conf extractConfiguration(Collection<XHost> xHosts);

}
