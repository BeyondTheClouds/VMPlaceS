package scheduling.dvms;


import java.net.UnknownHostException;


import org.simgrid.msg.Host;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.HostNotFoundException;
import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;

import org.simgrid.msg.Task;
import org.simgrid.trace.Trace;

import configuration.ConfigurationManager;

import simulation.Main;


import dvms.clientserver.Server;
import dvms.configuration.DVMSVirtualMachine;

import dvms.log.Logger;
import dvms.message.AbstractMessage;
import dvms.message.EventMessage;
import dvms.message.MigrationMessage;
import dvms.message.NegotiationOnReservationMessage;
import dvms.message.NodeReservationMessage;
import dvms.message.PingMessage;
import dvms.message.ReservationMessage;
import dvms.message.TestMessage;
import dvms.message.UpdateNodeReservationMessage;
import dvms.message.EventMessage.EventType;
import dvms.message.ReservationMessage.ReservationOperation;
import entropy.configuration.Node;
import entropy.configuration.SimpleVirtualMachine;
import entropy.configuration.VirtualMachine;

//Represents a server running on a worker node
//Currently, this server can only process on request at a time -> less concurrent access to the node object
public class DVMSServerForSG extends Process {
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Class variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	//Maximum number of threads that can be executed by the server simultaneously
	public static final int MAX_NUMBER_OF_THREADS = 5;

	//Time to wait before resending a low priority event, if it had not been solved
	public static final long TIME_BEFORE_RESENDING_LOW_PRIORITY_EVENT = 20000;
		
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	//The server
	private final ServerForSG server;
	
	//The node on which the server runs
	private final DVMSNode node;
	
	//The monitor running on the same node as the server
	private final DVMSMonitor monitor;

	
	// The mail box where clients post their messages
	private String mBox;
	
		
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public DVMSServerForSG(Host host, String name, int port, DVMSNode node, DVMSMonitor monitor) throws UnknownHostException  {
		super(host, name);
		
		// DVMSServerForSG constructor
		this.server = new ServerForSG(port);
		this.node = node;
		
		this.mBox= this.server.getHost() +":"+port;
		this.monitor = monitor;
		
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Accessors
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public Server getServer(){
		return server;
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////


	@Override
	public void main(String[] args) throws MsgException {
	
		if(node != null){
			node.setAssociatedServer(server);
		}

		AbstractMessage msg;

		while(!Main.isEndOfInjection()){
			
			try{
				SendMsgForSG req=(SendMsgForSG) Task.receive(this.mBox); 
				msg = (AbstractMessage) req.getMessage();
				Logger.log(node.getName() + ": received " + msg);


				//Process an event message
				if(msg instanceof EventMessage){
					processEventMessage((EventMessage)msg, req.getReplyBox());
				}

				//Process a reservation message
				else if(msg instanceof ReservationMessage){
					processReservationMessage((ReservationMessage)msg, req.getReplyBox());
				}

				else if(msg instanceof NegotiationOnReservationMessage){
					processNegotiationOnReservationMessage((NegotiationOnReservationMessage)msg, req.getReplyBox());
				}

				else if(msg instanceof UpdateNodeReservationMessage){
					processReservationMessage(((UpdateNodeReservationMessage) msg).getOldReservation(), req.getReplyBox());
					processReservationMessage(((UpdateNodeReservationMessage) msg).getNewReservation(), req.getReplyBox());
				}

				//Process a migration message
				else if(msg instanceof MigrationMessage){
					processMigrationMessage((MigrationMessage)msg, req.getReplyBox());
				}

				//Process a test message
				else if(msg instanceof TestMessage){
					System.out.println(((TestMessage)msg).getMessage());
				}

				else if(msg instanceof PingMessage){
					SendMsgForSG reply  = new SendMsgForSG("pong",
							req.getReplyBox(),
							null);
					reply.send();								
				}

			} catch (Exception e) {
				Logger.log(e);
			}
		}// End of while
		Msg.info("End of server"); 
	}

	private void processEventMessage(final EventMessage event, String replyBox) {
		switch(event.getState()){
		
		case PROCESSING://If a reconfiguration message needs to be processed
			
			//If the event crossed the whole ring without finding a solution...
			if(server.equals(event.getOrigin())){
				Logger.log(node.getName() + ": dropping event");
				Msg.info(node.getName()+ " is dropping the event (event went back to the initiator)");
				
				cancelReservations(event);
				
				if(EventType.NODE_OVERLOAD.equals(event.getType()))
					node.removeEvent(event);
				
				//Avoid relaunching low priority events immediately if no solution was found previously
				else if(EventType.NODE_UNDERLOAD.equals(event.getType())){
					new CustomizableProcess() {
						public void call(){
							try {
								waitFor(TIME_BEFORE_RESENDING_LOW_PRIORITY_EVENT);
							} catch (HostFailureException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							node.removeEvent(event);
						}
					}; 
				}
				
				monitor.notifyThatISPEnds(event);
			}
			
			else{
				monitor.updateMonitoringInformation();//Ensure monitoring information is up-to-date before processing event
				
				//Forward the event if the node is overloaded, computing, or reserved for a scheduling
				if(node.isOverloaded() || node.isComputingReconfigurationPlan() || node.getNodeReservation() != null){
					Logger.log(node.getName() + ": forwarding event");
					forwardEvent(event);
				}
				
				//Launch a scheduling
				else{
					//monitor.updateMonitoringInformation();
					
					//Node auto reservation
					NodeReservationMessage nodeReservation = new NodeReservationMessage(node.getAssociatedServer(), node.getAssociatedServer(), event);
					boolean reservationAccepted = node.addReservation(nodeReservation);
					
					if(reservationAccepted){
						Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "booked"); 
						DVMSScheduling computation = new DVMSScheduling(Host.currentHost(), "scheduling", event, node);
						try {
							computation.start();
						} catch (HostNotFoundException e) {
							e.printStackTrace();
						}
					}
					
					else
						Logger.log(node.getName() + ": ERROR - NODE AUTO RESERVATION MUST NOT FAIL");
				}
			}
			break;
		
		case VALIDITY_CHECKING://If someone wants to check whether the event is still valid
			boolean eventStillValid = node.checkEventValidity(event);
			Logger.log(node.getName() + ": " + (eventStillValid ? "event still valid" : "event not valid anymore"));
			sendBooleanResponseAndCloseConnection(eventStillValid, replyBox);
			break;
		
		case SOLVED://If a reconfiguration has been completed
			node.removeEvent(event);
			sendBooleanResponseAndCloseConnection(true, replyBox);
			monitor.notifyThatISPEnds(event);
			break;
		}
	}
	
	//Cancel reservations associated to the solving of an event
	private void cancelReservations(EventMessage event) {
		NodeReservationMessage cancelMessage;
	
		for(Server currentServer : event.getInformationVector()){
			cancelMessage = new NodeReservationMessage(server, currentServer, event);
			cancelMessage.prepareToDeletion();
			try {
				new DVMSClientForSG(cancelMessage).call();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}	
	}
	
	//Forward event without enriching information vector (as VM/PM mapping and resources consumed may vary quickly)
	private void forwardEvent(EventMessage event) {
		event.setDestination(node.getNeighbor());	
		try {
			new DVMSClientForSG(event).call();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void processReservationMessage(ReservationMessage reservation, String replyBox){
		//If a node wants to reserve resources
		if(ReservationOperation.ADD.equals(reservation.getReservationOperation())){
			boolean reservationAccepted = node.addReservation(reservation);
			Logger.log(node.getName() + ": reservation " + (reservationAccepted ? "succeeded" : "failed"));
		    SendMsgForSG msg1 = new SendMsgForSG(new Boolean(reservationAccepted),
		    										replyBox, 
		    											null); 
			msg1.send();
			
			if(reservation instanceof NodeReservationMessage){
				if(reservationAccepted){
					//TODO consider using monitor.updateMonitoringInformation() to send up-to-date monitoring information
				//	synchronized (node) {
						  SendMsgForSG msg2 = new SendMsgForSG(node,
									replyBox, 
										null); 
						  msg2.send();
					//}
				}
				
				else{
					  SendMsgForSG msg2 = new SendMsgForSG(node.getNodeReservation().getOrigin(),
								replyBox, 
									null); 
					  msg2.send();
				}
			}
			
			else if(!reservationAccepted){
				//TODO consider using monitor.updateMonitoringInformation() to send up-to-date monitoring information
				//synchronized (node) {
					 SendMsgForSG msg2 = new SendMsgForSG(node,
								replyBox, 
									null);
					 msg2.send();
				//}
			}
		}
		
		//If a node wants to delete a reservation
		else{
			node.removeReservation(reservation);
			Msg.info("Freed the node "+node.getName());
			Trace.hostSetState(node.getName(), "SERVICE", "free");
		}
	}
	
	@Deprecated
	private void processNegotiationOnReservationMessage(NegotiationOnReservationMessage negotiationMessage, String replyBox) {
		NodeReservationMessage result = node.getKnowledgeBase().processNegotiationRequest(negotiationMessage);
		Logger.log(node.getName() + ": negotiation won by " + (result == null ? node.getAssociatedServer() : negotiationMessage.getOrigin()));
		SendMsgForSG msg = new SendMsgForSG(result,
					replyBox, 
						null);
		msg.send();
	}
	
	private void processMigrationMessage(MigrationMessage migrationMessage, String replyBox) {	
		switch(migrationMessage.getMigrationOperation()){
		case SEND://If the node sends a VM...
			// TODO ICI Il faut mettre � jour la conf global
			node.removeVirtualMachine(migrationMessage.getVirtualMachine()); 
			break;
		case RECEIVE://If the node receives a VM...
			// TODO ICI il faut mettre � jour la conf global
			node.addVirtualMachine(migrationMessage.getVirtualMachine()); 
			DVMSVirtualMachine dvmsVM = migrationMessage.getVirtualMachine();
			VirtualMachine relocatedVM=new SimpleVirtualMachine(dvmsVM.getName(), dvmsVM.getNbOfCPUs(), dvmsVM.getCPUConsumption(), dvmsVM.getMemoryConsumption());  
			ConfigurationManager.relocateVM(Main.getCurrentConfig(), relocatedVM.getName(), node.getName());
			break;
		default: Logger.log(node.getName() + ": Unsupported migration operation"); break;
		}
		
		sendBooleanResponseAndCloseConnection(true, replyBox);
	}
	
	private void sendBooleanResponseAndCloseConnection(boolean booleanValue, String replyBox) {
		 SendMsgForSG msg = new SendMsgForSG(new Boolean(booleanValue),
					replyBox, 
						null);
		 msg.send();
	}
}

