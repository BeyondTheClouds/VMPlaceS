package scheduling.dvms;

import java.util.LinkedList;
import java.util.List;

import java.util.concurrent.ExecutorService;


import scheduling.dvms.DVMSClientForSG;
import dvms.clientserver.Server;
import dvms.configuration.DVMSManagedElementSet;
import dvms.log.Logger;
import dvms.message.EventMessage;
import dvms.message.NegotiationOnReservationMessage;
import dvms.message.NodeReservationMessage;
import dvms.message.ReservationMessage;
import dvms.message.UpdateNodeReservationMessage;
import dvms.message.EventMessage.EventState;

//Represents a component in charge of making (node) reservations
public class DVMSReservationMaker {
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Class variable
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Delay between two pollings while trying to reserve nodes for scheduling
	private static final long NODE_RESERVATION_POLLING_DELAY = 1000;
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	private final DVMSNode node;
	private final EventMessage event;
	//private final ExecutorService executor;
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public DVMSReservationMaker(DVMSNode node, EventMessage event, ExecutorService executor) {
		super();
		this.node = node;
		this.event = event;
	//	this.executor = executor;
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Reserve nodes for scheduling
	//Returns
	//	-an empty set of nodes if the event has already been solved
	//	-a set containing at least the first node (event sender) and the last node (solver).
	//		There is no point starting a scheduling without:
	//			*the first node, as we are trying to solve the event it sent
	//			*the last node, which was not taken into account by previous scheduling that failed -> if the
	//		new node is not considered, we are pretty sure that the current scheduling will fail
	public DVMSManagedElementSet<DVMSNode> tryReserveNodes() throws InterruptedException {
		//DVMSManagedElementSet<DVMSNode> reservedNodes = new DVMSManagedElementSet<DVMSNode>();
		
		for(Server node : event.getInformationVector())
			reserveNode(node);
		
		//return reservedNodes;
		return node.getKnowledgeBase().getMonitoringInformation();
	}
	
	//Reserve a node; the method blocks until the node is reserved
	private void reserveNode(Server node) throws InterruptedException {
		boolean nodeSuccessfullyReserved = false;
		
		do{
			nodeSuccessfullyReserved = tryReserveNode(node);
			
			if(!nodeSuccessfullyReserved){
				Thread.sleep(NODE_RESERVATION_POLLING_DELAY);
			}
		} while(!nodeSuccessfullyReserved);
	}
	
	//Try to reserve a node ; if the node is held by another node, try to negotiate with it
	//Returns
	//	-true if the node has been reserved (maybe after negotiation)
	//	-false otherwise
	private boolean tryReserveNode(Server node) throws InterruptedException {
		boolean nodeSuccessfullyReserved = false;
		Server nodeHolder;
		
		nodeHolder = tryReserveNodeOrGetHolder(node);
		nodeSuccessfullyReserved = (nodeHolder == null);
		
		if(!nodeSuccessfullyReserved){
			nodeSuccessfullyReserved = tryReserveAlreadyReservedNode(node, nodeHolder);
		}
		
		return nodeSuccessfullyReserved;
	}
	
	//Try to reserve a node
	//If not possible, return the node holder
	//Returns
	//	-null if the node has been reserved
	//	-the server associated to the holder otherwise
	private Server tryReserveNodeOrGetHolder(Server nodeToReserve) throws InterruptedException {
		Server reservationHolder = null;
		boolean nodeReserved = false;
		NodeReservationMessage nodeReservation = new NodeReservationMessage(node.getAssociatedServer(), nodeToReserve, event);
	//	Future<Object> reservationAnswer = executor.submit(new DVMSClientForSG(nodeReservation))
		Object reservationAnswer=null;
		try {
			reservationAnswer = new DVMSClientForSG(nodeReservation).call();
			nodeReserved = (reservationAnswer instanceof DVMSNode);

			if(nodeReserved){
				node.getKnowledgeBase().addReservation((DVMSNode)reservationAnswer, nodeReservation);
			}

			else{
				reservationHolder = (Server) reservationAnswer;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Logger.log(e);
			
		}
	
		return reservationHolder;
	}

	//Try to reserve a node held by another holder, by means of negotiations
	//Returns true if the node has been reserved, false otherwise
	@Deprecated
	private boolean tryReserveAlreadyReservedNode(Server nodeToReserve, Server reservationHolder){
		boolean success = false;
		System.exit(-1);
//		Object answer;
//		NodeReservationMessage updatedNodeReservation;
//		
//		try {
//			//Start negotiating
//			answer = executor.submit(new DVMSClientForSG(new NegotiationOnReservationMessage(
//					node.getAssociatedServer(), 
//					reservationHolder, 
//					nodeToReserve, 
//					node.getKnowledgeBase().getStrengthOfWill()))).get();
//		
//			//If negotiation succeeds, update node holder
//			if(answer instanceof NodeReservationMessage){
//				updatedNodeReservation = new NodeReservationMessage(node.getAssociatedServer(), nodeToReserve, event);
//				
//				answer = executor.submit(new DVMSClientForSG(new UpdateNodeReservationMessage(
//						node.getAssociatedServer(),
//						nodeToReserve,
//						(NodeReservationMessage)answer,
//						updatedNodeReservation))).get();
//				
//				if(answer instanceof DVMSNode){
//					success = true;
//					node.getKnowledgeBase().addReservation((DVMSNode)answer, updatedNodeReservation);
//				}
//			}
//		} catch (InterruptedException e) {
//			Logger.log(e);
//		} catch (ExecutionException e) {
//			Logger.log(e);
//		}
		
		return success;
	}
	
	
	
}
