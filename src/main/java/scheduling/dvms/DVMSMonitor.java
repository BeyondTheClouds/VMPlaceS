package scheduling.dvms;


import java.util.Random;
import java.util.Set;

import org.simgrid.msg.Host;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;

import configuration.ConfigurationManager;

import simulation.Main;

import scheduling.dvms.DVMSClientForSG;
import scheduling.dvms.DVMSNode;
import dvms.configuration.DVMSManagedElementSet;
import dvms.configuration.DVMSVirtualMachine;
import dvms.log.Logger;
import dvms.message.EventMessage;
import dvms.message.NodeReservationMessage;
import dvms.message.EventMessage.EventType;
import dvms.tool.DVMSProperties;

//Represents the monitoring system
public class DVMSMonitor extends Process{
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Class variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		
	//Monitor resource consumption every ...
	private final static long MONITORING_PERIODICITY = DVMSProperties.MONITORING_PERIODICITY;
	
	//Maximum time to wait before sending an event; used only for low priority events such as 'underloaded node' events
	private final static long MAX_DELAY_BEFORE_SENDING_EVENT = DVMSProperties.MAX_DELAY_BEFORE_SENDING_EVENT;
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Node on which the monitor runs
	private final DVMSNode node;
	

	
	//Randomizer used to delay the sending of low priority events
	private final Random randomizerEventDelay;
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public DVMSMonitor(Host host, String name, DVMSNode node) {

		super (host, name); 
		
		// DVMSMonitor Codes
		this.node = node;	

		// A way to shift the send of underloaded events for each node
		// IOW, the seed is different on each node. So the delay will be also different
		long seed = (long)node.getName().hashCode();//Compute seed from node name hashcode
		randomizerEventDelay = new Random(seed);
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Accessors
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public void main(String[] args) throws MsgException {
	
		try{
			while(node.getAssociatedServer() == null){
			
				waitFor(1); //time in second;
			}
		}catch(Exception e){e.printStackTrace();}
		
		
		long delayBeforeEventWasSent=0;

		while(!Main.isEndOfInjection()){
		//	Msg.info("Update Monitoring values");
			updateMonitoringInformation();
			try {
				delayBeforeEventWasSent = sendEventIfRequired();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} 
								
			//Wait for a given period before restarting the loop
			try {
				//waitFor(Math.max(MONITORING_PERIODICITY-delayBeforeEventWasSent, 0));
				waitFor(0.3);
			} catch (HostFailureException e) {
				e.printStackTrace();
			}
		}
		Msg.info("End of monitor process");
	}
	
	//Update monitoring information
	//Synchronization required in real life (concurrent accesses by DVMSServer and DVMSScheduling)
	//public synchronized void updateMonitoringInformation() throws InterruptedException {
	public void updateMonitoringInformation() {
		DVMSManagedElementSet<DVMSVirtualMachine> hostedVms;
		Logger.log(node.getName() + ": checking current consumption");
		
		hostedVms = DVMSFormat.getHostedVMs(Main.getCurrentConfig(), this.node );
		node.setVirtualMachines(hostedVms);	
				
	}
	
	//Send an event if necessary
	private long sendEventIfRequired() throws InterruptedException {
		//Logger.log(node.getName() + ": need to send event?");
		EventType eventType = null;
		long delayBeforeSendingEvent = 0;
		
		//Send an event message if required
		if(node.getNodeReservation() == null /*&& !node.isComputingReconfigurationPlan()*/){
			if(node.isOverloaded()
					&& !similarEventAlreadySent(EventType.NODE_OVERLOAD)){
				eventType = EventType.NODE_OVERLOAD;
				Msg.info(this.node.getName()+" monitoring service: node is overloaded");
				Trace.hostPushState(Host.currentHost().getName(), "PM", "violation-det"); 
			}
			
			else if(node.isUnderloaded()
					&& !similarEventAlreadySent(EventType.NODE_UNDERLOAD)){				
				delayBeforeSendingEvent = (long)(MAX_DELAY_BEFORE_SENDING_EVENT*randomizerEventDelay.nextDouble());
				try {
					waitFor(delayBeforeSendingEvent);
				} catch (HostFailureException e) {
					e.printStackTrace();
				}
				
				//If node is still not reserved and underloaded
				if(node.getNodeReservation() == null && node.isUnderloaded()){
					eventType = EventType.NODE_UNDERLOAD;
					Msg.info(this.node.getName()+" monitoring service: node is underloaded");
					Trace.hostPushState(Host.currentHost().getName(), "PM", "underloaded"); 
				}
			}
		}
		
		if(eventType != null){
			boolean schedulingCanStart = notifyThatSchedulingStarts(eventType);
			
			if(schedulingCanStart){
				EventMessage event = new EventMessage(node.getAssociatedServer(), node.getNeighbor(), eventType);
				event.enrichInformationVector(node.getAssociatedServer());//Add information related to current node in the vector
				
				//Reserve the node
				
				NodeReservationMessage nodeReservation = new NodeReservationMessage(node.getAssociatedServer(), node.getAssociatedServer(), event);
				boolean reservationSuccessful = false;
				
				try {
						//executor.submit(.....)
						reservationSuccessful = (new DVMSClientForSG(nodeReservation).call() instanceof DVMSNode);
				} catch (Exception e) {
					e.printStackTrace();
					Logger.log(e);
				}
				
				//Send the event
				if(reservationSuccessful){
			
					switch(event.getType()){
					case NODE_OVERLOAD:
						Logger.log(node.getName() + " is overloaded (CPU: " + node.getCPUUsedByVMs() + " ; RAM: " + node.getMemoryUsedByVMs() + ") -> sending event");
						break;
					case NODE_UNDERLOAD:
						Logger.log(node.getName() + " is underloaded (CPU: " + node.getCPUUsedByVMs() + " ; RAM: " + node.getMemoryUsedByVMs() + ") sending event");
						break;
					}
					
					//executor.submit(new DVMSClientForSG(event));
					try {
						new DVMSClientForSG(event).call();
					} catch (Exception e) {
						e.printStackTrace();
					}
					node.addEvent(event);
				}
				
				else{
					notifyThatISPEnds(event);
				}
			}
		}
		
		return delayBeforeSendingEvent;
	}
	
	//Check whether a similar event has already been sent
	private boolean similarEventAlreadySent(EventType eventType){
		Set<EventMessage> eventsSent = node.getEventsSent();
		
		for(EventMessage event : eventsSent){
			if(event.getType().equals(eventType)){
				Logger.log(node.getName() + ": similar event already sent");
				return true;
			}
		}
		
		return false;
	}
	
	//Notify that scheduling starts
	//Returns true if the scheduling can start, false otherwise
	private boolean notifyThatSchedulingStarts(EventType eventType){
		Logger.log(node.getName() + ": starting scheduling");
		
		Msg.info(this.node.getName()+": ISP starts");		
		Trace.hostVariableAdd(ConfigurationManager.getServiceNodeName(), "NB_MC", 1);
		Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "booked"); 

		return true; 
	}
	
	//Notify that scheduling ends
	public void notifyThatISPEnds(EventMessage event){
		Logger.log(node.getName() + ": stopping scheduling");

		Msg.info(this.node.getName()+": ISP ends");
	//	Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "free");
		Trace.hostVariableSub(ConfigurationManager.getServiceNodeName(), "NB_MC", 1);
		

	}

}
