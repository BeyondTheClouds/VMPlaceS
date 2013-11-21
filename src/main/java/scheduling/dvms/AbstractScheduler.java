package scheduling.dvms;

import java.util.List;
import java.util.Map;

import dvms.configuration.DVMSManagedElementSet;
import dvms.message.ReservationMessage;
import dvms.scheduling.ComputingState;

//An abstract scheduler which is used when the node needs to compute a new schedule
public abstract class AbstractScheduler {

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Nodes considered for scheduling
	private final DVMSManagedElementSet<DVMSNode> nodesConsidered;
	
	//The node on which the scheduler is running
	protected final DVMSNode node;
	
	/**
	 * The time spent to compute VMPP
	 *  @deprecated Please consider that this value is currently deprecated and will be set to zero until it will be fixed - Adrien, Nov 18 2011
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
	
	public AbstractScheduler(DVMSManagedElementSet<DVMSNode> nodesConsidered, DVMSNode node) {
		this.nodesConsidered = nodesConsidered;
		this.node = node;
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

	public DVMSManagedElementSet<DVMSNode> getNodesConsidered() {
		return nodesConsidered;
	}

	public DVMSNode getNode() {
		return node;
	}
	
	public int getReconfigurationPlanCost(){
		return reconfigurationPlanCost;
	}
	
	/**
	 *  @deprecated Please consider that this value is currently deprecated and will be set to zero until it will be fixed - Adrien, Nov 18 2011
	 */
	public long getTimeToComputeVMPP(){
		return timeToComputeVMPP;
	}
	
	public long getTimeToComputeVMRP(){
		return timeToComputeVMRP;
	}
	
	public long getTimeToApplyReconfigurationPlan(){
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

	//Computes a reconfiguration plan
	//Returns true only if a plan was found
	public abstract ComputingState /*boolean*/ computeReconfigurationPlan();

	//Computes the reservations needed to apply the reconfiguration plan
	@Deprecated
	public abstract List<ReservationMessage> computeReservations();

	//Applies the reconfiguration plan
	public abstract void applyReconfigurationPlan() throws InterruptedException;

	//Returns a simplified view (map) of the new configuration
	//Key: node name
	//Value: names of the VMs hosted by this node
	public abstract Map<String, List<String>> getNewConfiguration();
}
