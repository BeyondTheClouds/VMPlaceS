package scheduling.dvms;

import java.util.List;

import dvms.configuration.DVMSManagedElementSet;

import dvms.message.NegotiationOnReservationMessage;
import dvms.message.NodeReservationMessage;

public class DVMSKnowledgeBase {
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private final DVMSReservationsHolder reservationsHolder;
	private final DVMSMonitoringInformation monitoringInformation;
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public DVMSKnowledgeBase() {
		super();
		reservationsHolder = new DVMSReservationsHolder();
		monitoringInformation = new DVMSMonitoringInformation();
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Clear knowledge base
	public void clear(){
		reservationsHolder.initialize();
		monitoringInformation.clear();
	}
	
	//Is the reservation holder open to negotiations?
	public boolean isNegotiationsOpen(){
		return reservationsHolder.isNegotiationsOpen();
	}
	
	//The holder's strength of will to obtain a reservation
	public int getStrengthOfWill(){
		return reservationsHolder.getStrengthOfWill();
	}
	
	//Add a reservation and update monitoring information
	public void addReservation(DVMSNode reservedNode, NodeReservationMessage reservation){
		reservationsHolder.addReservation(reservedNode, reservation);
		monitoringInformation.updateMonitoringInformation(reservedNode);
	}
	
	//Check whether a reservation on a node is held
	//Returns
	//	-true if this is the case
	//	-false otherwise
	public boolean isNodeReserved(DVMSNode node){
		return reservationsHolder.isNodeReserved(node);
	}
	
	//Close negotiations: reservations will not be freed upon other holders' requests
	public void closeNegotiations(){
		reservationsHolder.closeNegotiations();
	}
	
	//Returns a list of the reservations held
	public List<NodeReservationMessage> getHeldReservations(){
		return reservationsHolder.getHeldReservations();
	}
	
	//Decide whether to give or not a reservation to another holder
	//Returns
	//	-the reservation, if it is given to the other holder
	//	-null if it is not
	public NodeReservationMessage processNegotiationRequest(NegotiationOnReservationMessage negotiation){
		NodeReservationMessage result = null;
		
		if(reservationsHolder.isNegotiationsOpen()){
			while(getStrengthOfWill() == negotiation.getStrengthOfWill()){
				reservationsHolder.computeStrengthOfWill();
			}
			
			if(getStrengthOfWill() < negotiation.getStrengthOfWill()){
				result = reservationsHolder.getAndRemoveReservation(negotiation.getNodeToReserve());
				monitoringInformation.removeMonitoringInformation(negotiation.getNodeToReserve());
			}
		}
		
		return result;
	}
	
	//Get monitoring information
	public DVMSManagedElementSet<DVMSNode> getMonitoringInformation(){
		return monitoringInformation.getMonitoringInformation();
	}
}
