package scheduling;

//An abstract scheduler
public abstract class AbstractScheduler<Config, ReConfigPlan> implements Scheduler {

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//The initial configuration
	protected final Config initialConfiguration;
	
	//The new/final configuration
	protected Config newConfiguration;
	
	//The reconfiguration plan
	protected ReConfigPlan reconfigurationPlan;
	
	/**
	 * The time spent to compute VMPP
	 *  @deprecated Please consider that this value is currently deprecated and will be set to zero untill it will be fixed - Adrien, Nov 18 2011
	 */
	protected long timeToComputeVMPP;
	
	//The time spent to compute VMRP
	protected long timeToComputeVMRP;
	
	//The time spent to apply the reconfiguration plan
	protected long timeToApplyReconfigurationPlan;
	
	//The cost of the reconfiguration plan
	protected int reconfigurationPlanCost;
	
	//The number of migrations inside the reconfiguration plan
	protected int nbMigrations;
	
	//The depth of the graph of the reconfiguration actions
	protected int reconfigurationGraphDepth;
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	protected AbstractScheduler(Config initialConfiguration){
		this.initialConfiguration = initialConfiguration;
		timeToComputeVMPP = 0;
		timeToComputeVMRP = 0;
		timeToApplyReconfigurationPlan = 0;
		reconfigurationPlanCost = 0;
		nbMigrations = 0;
		reconfigurationGraphDepth = 0;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Accessors
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public Config getNewConfiguration() {
		return newConfiguration;
	}

	public int getReconfigurationPlanCost() {
		return reconfigurationPlanCost;
	}
	
	/**
	 *  @deprecated Please consider that this value is currently deprecated and will be set to zero untill it will be fixed - Adrien, Nov 18 2011
	 */
	public long getTimeToComputeVMPP() {
		return timeToComputeVMPP;
	}

	public long getTimeToComputeVMRP() {
		return timeToComputeVMRP;
	}

	public long getTimeToApplyReconfigurationPlan() {
		return timeToApplyReconfigurationPlan;
	}
	
	public int getNbMigrations(){
		return nbMigrations;
	}
	
	public int getReconfigurationGraphDepth(){
		return reconfigurationGraphDepth;
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Abstract methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public abstract ComputingState computeReconfigurationPlan();

    /**
     * @return 0 if the reconfiguration plan has been correctly performed (i.e. completely)
     */
	public abstract void applyReconfigurationPlan();
}
