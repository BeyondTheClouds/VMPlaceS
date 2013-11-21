package scheduling.dvms;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import dvms.clientserver.Server;

import dvms.message.NodeReservationMessage;

//Represents the holder of the (node) reservations
public final class DVMSReservationsHolder {
	public final static long SEED = 23;

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Reservations held by the Reservation holder
	private final Map<Server, NodeReservationMessage> heldReservations;
	private final Object lockHeldReservations;
	
	//Is the reservation holder open to negotiations?
	private boolean negotiationsOpen;
	
	//The holder's strength of will to obtain a reservation
	private int strengthOfWill;
	private final Object lockStrengthOfWill;
	
	//Randomizer used to compute the strength of will
	private final Random randomizer;
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public DVMSReservationsHolder(){
		heldReservations = new HashMap<Server, NodeReservationMessage>();
		randomizer = new Random(SEED);
		
		lockHeldReservations = new Object();
		lockStrengthOfWill = new Object();
		
		initialize();
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Accessors
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public boolean isNegotiationsOpen() {
		return negotiationsOpen;
	}

	@Deprecated
	public int getStrengthOfWill(){
		synchronized (lockStrengthOfWill) {
			if(strengthOfWill == 0){
				computeStrengthOfWill();
			}
			
			return strengthOfWill;
		}
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Clear reservations, reopen negotiations and reset strength of will
	public void initialize(){
		synchronized (lockHeldReservations) {
			heldReservations.clear();
		}
		
		negotiationsOpen = true;
		
		synchronized (lockStrengthOfWill) {
			strengthOfWill = 0;
		}
	}
	
	//Add a reservation
	public void addReservation(DVMSNode reservedNode, NodeReservationMessage reservation){
		synchronized (lockHeldReservations) {
			heldReservations.put(reservedNode.getAssociatedServer(), reservation);
		}
	}
	
	//Check whether a reservation on a node is held
	//Returns
	//	-true if this is the case
	//	-false otherwise
	public boolean isNodeReserved(DVMSNode node){
		synchronized (lockHeldReservations) {
			return heldReservations.get(node.getAssociatedServer()) != null;
		}
	}
	
	//Close negotiations: reservations will not be freed upon other holders' requests
	public void closeNegotiations(){
		negotiationsOpen = false;
	}
	
	//Compute the strength of will a holder wants
	//	-to keep the reservation it already holds
	//	-to acquire new reservations from other holders
	@Deprecated
	public void computeStrengthOfWill(){
		synchronized (lockStrengthOfWill) {
			strengthOfWill = randomizer.nextInt();
		}
	}
	
	//Returns a list of the reservations held
	public List<NodeReservationMessage> getHeldReservations(){
		synchronized (lockHeldReservations) {
			return new LinkedList<NodeReservationMessage>(heldReservations.values());
		}
	}
	
	//Retrieve a node reservation, and remove it from the set of reservations held
	//Returns the node reservation
	@Deprecated
	public NodeReservationMessage getAndRemoveReservation(Server nodeToFree){
		synchronized (lockHeldReservations) {
			return heldReservations.remove(nodeToFree);
		}
	}
}
