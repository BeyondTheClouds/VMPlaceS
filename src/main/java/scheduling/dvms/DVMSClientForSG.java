package scheduling.dvms;

import java.util.concurrent.Callable;

import org.simgrid.msg.Msg;
import org.simgrid.msg.Task;


import dvms.log.Logger;
import dvms.message.AbstractMessage;
import dvms.message.EventMessage;
import dvms.message.MigrationMessage;
import dvms.message.NegotiationOnReservationMessage;
import dvms.message.NodeReservationMessage;
import dvms.message.PingMessage;
import dvms.message.ReservationMessage;
import dvms.message.UpdateNodeReservationMessage;
import dvms.message.EventMessage.EventState;
import dvms.message.ReservationMessage.ReservationOperation;
import dvms.message.simulation.SchedulingNotificationMessage;

//Used each time a node needs to contact another node
public class DVMSClientForSG implements Callable<Object> {

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variable
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Message to send to the destination node
	private final SendMsgForSG sgMSG; 
	private final AbstractMessage message; 
	private String sender;
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public DVMSClientForSG(AbstractMessage message) {
		this(message, "not defined");
	}
	
	public DVMSClientForSG(AbstractMessage message, String sender) {
		this.sgMSG = new SendMsgForSG(message, 
						message.getDestination().getHost()+":"+message.getDestination().getPort(),
							message.getOrigin().getHost()+":"+message.getOrigin().getPort()+":"+ Math.random());
		this.message = message ;  // TODO Fix ME a bit ugly since message is already embedded in sgMSG
		this.sender = sender; 
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public Object call() throws Exception {
		Object valueToReturn = null;
		SendMsgForSG reply = null; 
		try {	
			
		//	Msg.info("Send a message from "+this.sgMSG.getReplyBox() +" to "+this.sgMSG.getSendBox());
	
			this.sgMSG.send();
			
			if((message instanceof EventMessage 
					&& (EventState.VALIDITY_CHECKING.equals(((EventMessage)message).getState())
							|| EventState.SOLVED.equals(((EventMessage)message).getState())))
					|| message instanceof MigrationMessage
					|| message instanceof SchedulingNotificationMessage){
				reply=(SendMsgForSG) Task.receive(this.sgMSG.getReplyBox());
				valueToReturn = reply.getMessage();
			}
			
			else if(message instanceof UpdateNodeReservationMessage
					|| (message instanceof ReservationMessage 
							&& ReservationOperation.ADD.equals(((ReservationMessage)message).getReservationOperation()))){
				reply=(SendMsgForSG) Task.receive(this.sgMSG.getReplyBox());
				boolean reservationAccepted = ((Boolean)reply.getMessage()).booleanValue();
				
				//If a node accepted a NodeReservation, get updated information on its resource consumption
				//If a node rejected a reservation (which is NOT a NodeReservation), also get updated information
				if( (message instanceof UpdateNodeReservationMessage) 
						|| (message instanceof NodeReservationMessage)
						|| !reservationAccepted){
					reply=(SendMsgForSG) Task.receive(this.sgMSG.getReplyBox());
					valueToReturn = reply.getMessage();
				}
				
			}
			
			else if(message instanceof NegotiationOnReservationMessage){
				reply=(SendMsgForSG) Task.receive(this.sgMSG.getReplyBox());
				valueToReturn = reply.getMessage();
			}
			
			else if(message instanceof PingMessage){
				reply=(SendMsgForSG) Task.receive(this.sgMSG.getReplyBox());
				valueToReturn = reply.getMessage();
			}
			
		} catch (Exception e){
			Logger.log(e);
		}
		
		return valueToReturn;
	}
}
