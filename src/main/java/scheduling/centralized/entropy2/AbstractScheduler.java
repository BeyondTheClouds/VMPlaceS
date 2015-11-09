package scheduling.centralized.entropy2;

import entropy.configuration.Configuration;
import entropy.plan.TimedReconfigurationPlan;
import scheduling.Scheduler;

//An abstract scheduler
public abstract class AbstractScheduler implements Scheduler {

	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//The initial configuration
	protected Configuration initialConfiguration;
	
	//The new/final configuration
	protected Configuration newConfiguration;
	
	//The reconfiguration plan
	protected TimedReconfigurationPlan reconfigurationPlan;
	
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
	
	protected AbstractScheduler(){
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
	
	public Configuration getNewConfiguration() {
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
