package scheduling.dvms;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.channels.FileLock;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;


import configuration.XSimpleConfiguration;

import dvms.configuration.DVMSManagedElementSet;
import dvms.log.Logger;
import dvms.message.EventMessage;
import dvms.message.NodeReservationMessage;
import dvms.message.ReservationMessage;
import dvms.message.EventMessage.EventState;


import dvms.scheduling.ComputingState;
import dvms.scheduling.SchedulingInformation;
import simulation.Main;
import dvms.tool.DVMSProperties;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;

//Represents a scheduling, i.e. a computing + reconfiguring process
public class DVMSScheduling extends Process {
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Class variable
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Maximum number of threads that a scheduling can execute simultaneously
	private static final int MAX_NUMBER_OF_THREADS = 5;
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Event to be solved by the scheduling
	private final EventMessage event;
	
	//Node on which the scheduling occurs
	private final DVMSNode node;
	
	//Reservations to send to the nodes taking part in the scheduling
	//TODO to delete if deprecated methods are deleted
	private List<ReservationMessage> reservations;

	
	//Scheduler to use for the scheduling
	private AbstractScheduler scheduler;
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public DVMSScheduling(Host host, String name, EventMessage event, DVMSNode node) {
	
		super(host, name);
		this.event = event;
		this.event.enrichInformationVector(node.getAssociatedServer());//The scheduler works with monitoring info contained in the vector
												 //-> it should take into account the resources of the current node
		
		this.node = node;
		//TODO to delete if deprecated methods are deleted
		reservations = new LinkedList<ReservationMessage>();
		node.setComputingReconfigurationPlan(true);
				
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Accessor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Run method
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	public void main(String[] args) throws MsgException {
		try {
			int nbOfNodesConsidered;
			long schedulingStart = System.currentTimeMillis();
			long schedulingEnd;
			ComputingState schedulerResult = ComputingState.NO_COMPUTING;
			long timeToComputeVMPP = 0;
			long timeToComputeVMRP = 0;
			long timeToApplyReconfigurationPlan = 0;
			int reconfigurationPlanCost = 0;
			int nbMigrations = 0;
			int reconfigurationGraphDepth = 0;
						
			
			File schedulingStepInfoFile = new File(DVMSProperties.SCHEDULING_INFORMATION_DIRECTORY + File.separator
					+ node.getName() + "-" + schedulingStart + ".txt");
			
			//Apply node reservations
			Logger.log(node.getName() + ": reserving nodes");
			
		
			
			DVMSReservationMaker reservationMaker = new DVMSReservationMaker(node, event, null);
			DVMSManagedElementSet<DVMSNode> reservedNodes = reservationMaker.tryReserveNodes();
			nbOfNodesConsidered = reservedNodes.size();
						
			//If event has been solved while applying reservations, notify event sender and stop scheduling
			if (!checkEventValidity()) {
				Logger.log(node.getName() + ": event " + event + " not valid anymore; stopping scheduling");
				Msg.info("event not valid anymore");				
				notifyOriginalEventSender();
				cancelReservations();
			}
			
			else{
				//Create a new scheduler	
				Msg.info("Launching scheduler on "+Host.currentHost().getName()+" evt on "+event.getOrigin());
				Trace.hostPushState(Host.currentHost().getName(), "SERVICE", "compute"); 
				
				scheduler = new Entropy2RP(reservedNodes, node);
				
				
				//Compute reconfiguration plan			
				Logger.log(node.getName() + ": computing");
				
				
				
				schedulerResult = scheduler.computeReconfigurationPlan();
				timeToComputeVMPP = scheduler.getTimeToComputeVMPP();
				timeToComputeVMRP = scheduler.getTimeToComputeVMRP();

				// SG interaction
				waitFor( (timeToComputeVMRP/1000) > 0 ? timeToComputeVMRP/1000 : 0.5 );				
				Msg.info("Computation time: " + timeToComputeVMRP/1000);
				
				Trace.hostPopState(Host.currentHost().getName(), "SERVICE"); 
				
				reconfigurationPlanCost = scheduler.getReconfigurationPlanCost();	
				nbMigrations = scheduler.getNbMigrations();
				reconfigurationGraphDepth = scheduler.getReconfigurationGraphDepth();
				
				//Apply reconfiguration plan
				if(schedulerResult.equals(ComputingState.VMRP_SUCCESS)){//A reconfiguration plan exists
						int cost = scheduler.getReconfigurationPlanCost();
						Logger.log(node.getName() + ": applying reconfiguration plan");
						
						Msg.info("Cost of reconfiguration:" + cost + ", time: "+ (cost/10000));
						Trace.hostPushState(Host.currentHost().getName(), "SERVICE", "reconfigure"); 
						Trace.hostVariableAdd(host.currentHost().getName(), "NB_MIG", scheduler.getNbMigrations());	
						Trace.hostVariableAdd("node0", "NB_MIG", scheduler.getNbMigrations());
						waitFor((cost/10000>0) ? cost/10000 : 0.5);
					
						scheduler.applyReconfigurationPlan();
						timeToApplyReconfigurationPlan = scheduler.getTimeToApplyReconfigurationPlan();
						Logger.log(node.getName() + ": cancelling reservations");
						
						Trace.hostPopState(Host.currentHost().getName(), "SERVICE"); 
						
						DVMSManagedElementSet<DVMSNode> nodesInvolved= node.getKnowledgeBase().getMonitoringInformation();
						cancelReservations();
						
						/* Tracing code */;
						// FIXME here we should only go throught the booked nodes
						// See whether we can get the list of nodes through reservationMaker.cancelReservations().
						XSimpleConfiguration currConf = Main.getCurrentConfig();
						for (DVMSNode tmpNode:  nodesInvolved){	
							if(!currConf.isViable(DVMSFormat.convertToSimpleNode(tmpNode))){
								Msg.info(tmpNode.getName()+" switches to state violation-out");
								Trace.hostSetState(tmpNode.getName(), "PM", "violation-out"); 
							}
							else
								Trace.hostSetState(tmpNode.getName(), "PM", "normal"); 
						}
						
						Logger.log(node.getName() + ": notifying simulator node");
						notifySimulatorOfChanges();
						
						Logger.log(node.getName() + ": notifying original event sender");
						notifyOriginalEventSender();
				
				}	else{
					Logger.log(node.getName() + ": no reconfiguration plan found; cancelling reservations and forwarding " + event);
					Msg.info("No solution found, the event will be forwarded");
					//reservationMaker.cancelReservations();
					node.getKnowledgeBase().clear();
					forwardEvent();//wait for termination
				}
			}
		
			schedulingEnd = System.currentTimeMillis();
			
			SchedulingInformation schedInfo = new SchedulingInformation();
			schedInfo.setIteration(node.getCurrentIteration());
			schedInfo.setComputingResult(schedulerResult);
			schedInfo.setEventProcessed(event);
			schedInfo.setNbOfNodesConsidered(nbOfNodesConsidered);
			schedInfo.setReconfigurationPlanCost(reconfigurationPlanCost);
			schedInfo.setNbMigrations(nbMigrations);
			schedInfo.setReconfigurationGraphDepth(reconfigurationGraphDepth);
			schedInfo.setSchedulingEnd(schedulingEnd);
			schedInfo.setSchedulingStart(schedulingStart);
			schedInfo.setTimeToApplyReconfigurationPlan(timeToApplyReconfigurationPlan);
			schedInfo.setTimeToComputeVMPP(timeToComputeVMPP);
			schedInfo.setTimeToComputeVMRP(timeToComputeVMRP);
			
			writeSchedulingInfo(schedulingStepInfoFile, schedInfo);
		} catch (InterruptedException e) {
			Logger.log(e);
		} catch (Exception e) {
			Logger.log(e);
		}
		
		node.setComputingReconfigurationPlan(false);
		sleep(10000);
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Methods related to reservations
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Compute and apply reservations (on node resources and VMs)
	@Deprecated
	private boolean computeAndApplyReservations() throws InterruptedException {
		boolean rollbackRequired = false;
//		reservations = scheduler.computeReservations();
//		Future<Object> response;
//		List<Future<Object>> reservationResponses = new LinkedList<Future<Object>>();
//		boolean forwardRequired = false;
//		DVMSManagedElementSet<DVMSNode> updatedInfo = new DVMSManagedElementSet<DVMSNode>();
//		
//		//Send reservation requests
//		for(ReservationMessage reservation : reservations){
//			response = executor.submit(new DVMSClientForSG(reservation));
//			reservationResponses.add(response);
//		}
//		
//		//Process responses
//		for(Future<Object> anAnswer : reservationResponses){
//			try {
//				if(anAnswer.get() != null){
//					rollbackRequired = true;
//					updatedInfo.add((DVMSNode)anAnswer.get());
//				}
//			} catch (ExecutionException e) {
//				Logger.log(e);
//			}
//		}
//		
//		//If rollback is required
//		if(rollbackRequired){
//			Logger.log(node.getName() + ": at least a reservation failed; starting rollback");
//			cancelReservations();
//			
//			//Update information vector
//			for(DVMSNode node : updatedInfo){
//			//	event.updateInformationVector(node);//XXX This deprecated instruction was commented to avoid conflicts between dvms-core.dvms.configuration.DVMSNode and SG-INJECTOR.scheduling.dvms.DVMSNode
//			}
//			
//			EventMessage event = new EventMessage(this.event.getOrigin(), this.event.getOrigin(), this.event.getType());
//			event.setState(EventState.VALIDITY_CHECKING);
//			
//			try {
//				forwardRequired = (Boolean)executor.submit(new DVMSClientForSG(event)).get();
//			} catch (ExecutionException e) {
//				Logger.log(e);
//			}
//			
//			Logger.log(node.getName() + ": response received - " + (forwardRequired ? "valid event" : "event not valid anymore"));
//			
//			if(forwardRequired){
//				Logger.log(node.getName() + ": ending rollback; forwarding " + (event.getType().equals(EventType.NODE_OVERLOAD) ? "overloaded" : "underloaded") + " event");
//				forwardEvent();//wait for termination
//			}
//			
//			else{
//				Logger.log(node.getName() + ": ending rollback; event not valid anymore");
//			}
//		}
//		
		return !rollbackRequired;
	}
	
//Cancel reservations
	public void cancelReservations() throws InterruptedException {
		List<NodeReservationMessage> reservations = node.getKnowledgeBase().getHeldReservations();
	
		
		//Send dereservation requests
		for(ReservationMessage reservation : reservations){
			reservation.prepareToDeletion();
			try {
				new DVMSClientForSG(reservation).call();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
		//Empty reservation list and reopen negotiations
		node.getKnowledgeBase().clear();
	}
	

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Notification methods for the event sender and the simulator
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Notify event sender that event has been solved
	private void notifyOriginalEventSender() throws InterruptedException {
		EventMessage event = new EventMessage(this.event);
		event.setState(EventState.SOLVED);
		event.setDestination(event.getOrigin());
		
		try {
			new DVMSClientForSG(event).call() ;
		} catch (Exception e) {
			e.printStackTrace();
			Logger.log(e);
		} 
	}
	
	//Notify simulator of changes in the mapping vm <-> node
	private void notifySimulatorOfChanges() throws InterruptedException, ExecutionException {
//		if(simulator == null)
//			return;
//		
//		Map<String, List<String>> mapping = scheduler.getNewConfiguration();
//		File mappingUpdateFile = new File(DVMSProperties.MAPPING_UPDATE_DIRECTORY + File.separator 
//				+ System.currentTimeMillis() + "-" + node.getName());
//		ObjectOutputStream out;
//		
//		try{
//			if(mappingUpdateFile.getParentFile() != null
//					&& !mappingUpdateFile.getParentFile().exists()){
//				boolean mkdirSucceeded = mappingUpdateFile.getParentFile().mkdirs();
//				
//				if(!mkdirSucceeded && !mappingUpdateFile.getParentFile().exists())
//					throw new IOException("mkdirs() failed");
//			}
//			
//			FileOutputStream fos = new FileOutputStream(mappingUpdateFile);
//			FileLock lock = fos.getChannel().lock();
//			
//			out = new ObjectOutputStream(new BufferedOutputStream(fos));
//			
//			out.writeObject(mapping);
//			out.flush();
//			lock.release();
//			out.close();
//		} catch(IOException e){
//			Logger.log(e);
//		}
//		
//		//Notify the simulator if an overloaded node event has been solved
//		if(EventType.NODE_OVERLOAD.equals(event.getType())){
//			Future<Object> response = executor.submit(new DVMSClientForSG(
//					new OverloadedEvtSolvedMessage(node.getAssociatedServer(), simulator, node.getCurrentIteration())));
//			response.get();
//		}
	}
	
	//Write information about the scheduling
	private void writeSchedulingInfo(File schedulingStepInfoFile,
			SchedulingInformation schedulingInformation) {
		ObjectOutputStream out;
		
		try {
			if (schedulingStepInfoFile.getParentFile() != null 
					&& !schedulingStepInfoFile.getParentFile().exists()) {
				boolean mkdirsSucceeded = schedulingStepInfoFile.getParentFile().mkdirs();
				
				if(!mkdirsSucceeded && !schedulingStepInfoFile.getParentFile().exists())
					throw new IOException("mkdirs() failed");
			}
			
			FileOutputStream fos = new FileOutputStream(schedulingStepInfoFile);
			FileLock lock = fos.getChannel().lock();
			
			out = new ObjectOutputStream(new BufferedOutputStream(fos));
			
			out.writeObject(schedulingInformation);
			out.flush();
			lock.release();
			out.close();
		} catch (FileNotFoundException e) {
			Logger.log(e);
		} catch (IOException e) {
			Logger.log(e);
		}
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other method
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Forward event to the neighbor
	private void forwardEvent() throws InterruptedException {
		EventMessage event = new EventMessage(this.event);
		//No need to enrich event information vector: it is already done in the constructor
		event.setDestination(node.getNeighbor());
		
		try {
			new DVMSClientForSG(event).call();
		} catch (Exception e) {
			e.printStackTrace();
			Logger.log(e);
		}
	}

	//Check whether an event is still valid
	private boolean checkEventValidity() throws InterruptedException {
					
		EventMessage eventToCheckForValidity = new EventMessage(event);
		eventToCheckForValidity.setState(EventState.VALIDITY_CHECKING);
		eventToCheckForValidity.setDestination(event.getOrigin());
		
		boolean eventIsStillValid = false;
		
		try {
			eventIsStillValid = (Boolean) new DVMSClientForSG(eventToCheckForValidity).call();
		} catch (Exception e) {
			Logger.log(e);
		}
		Msg.info("State of the event ("+event.getOrigin()+"):"+eventIsStillValid);
		return eventIsStillValid;
	}
	

}
