package scheduling.dvms;

import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;



import dvms.clientserver.Server;
import dvms.configuration.AbstractManagedElement;
import dvms.configuration.DVMSManagedElementSet;
import dvms.configuration.DVMSVirtualMachine;

import dvms.log.Logger;
import dvms.message.CapacityReservationMessage;
import dvms.message.EventMessage;
import dvms.message.NodeReservationMessage;
import dvms.message.ReservationMessage;
import dvms.message.VMReservationMessage;
import dvms.message.EventMessage.EventType;
import dvms.tool.DVMSProperties;

//Represents a node (= physical machine)
public class DVMSNode  extends AbstractManagedElement {
	/**
	 * 
	 */
	private static final long serialVersionUID = 189253296492127075L;
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Class variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Identifier for the number of CPUs.
	 */
	public static final String CPU_NB = "cpu#nb";
	
	/**
	 * Identifier for the capacity of each CPU.
	 */
	public static final String CPU_CAPACITY = "cpu#capacity";

	public static final String CPU_CAPACITY_USED_BY_VMS = "cpu#usedbyvms";

	public static final String CPU_CAPACITY_USED_BY_RESERVATIONS = "cpu#usedbyreservations";
	
	/**
	 * Identifier for the total amount of memory.
	 */
	public static final String MEMORY_TOTAL = "memory#total";
	
	public static final String MEMORY_USED_BY_VMS = "memory#usedbyvms";
	
	public static final String MEMORY_USED_BY_RESERVATIONS = "memory#usedbyreservations";
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	//The server running on this node
	private ServerForSG associatedServer;
	
	//The server running on the neighbor of this node
	private transient ServerForSG neighbor;
	
	//The VMs hosted on this node
	private DVMSManagedElementSet<DVMSVirtualMachine> virtualMachines;
	
	//The object that holds the (node) reservations
	private transient DVMSKnowledgeBase knowledgeBase;
	
	//Indicates whether this node is reserved by a scheduler
	private transient NodeReservationMessage nodeReservation;
	
	//The VMs reserved by schedulers
	private List<VMReservationMessage> vmReservations;
	
	//The capacity reservations accepted by this node
	private transient List<CapacityReservationMessage> capacityReservations;
	
	//The event messages sent by this node
	private transient Set<EventMessage> eventsSent;
	
	//The event messages sent by this node, which were solved
	private transient Set<EventMessage> eventsProcessed;//TODO to delete for big simulations or real utilization
	
	//Is the node's scheduler started?
	private transient boolean computingReconfigurationPlan;
	
	//The current simulation iteration
	private transient int currentIteration;
	
	// SG Code
	private SGSynchronized lock; 
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructors
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public DVMSNode(String id, int nbCPUs, int cpuCapacity, int memoryTotal, ServerForSG neighbor){
		super(id);
		// make default values
		updateValue(CPU_NB, nbCPUs);
		updateValue(CPU_CAPACITY, cpuCapacity);
		updateValue(MEMORY_TOTAL, memoryTotal);
		
		updateValue(CPU_CAPACITY_USED_BY_VMS, 0);
		updateValue(CPU_CAPACITY_USED_BY_RESERVATIONS, 0);
		updateValue(MEMORY_USED_BY_VMS, 0);
		updateValue(MEMORY_USED_BY_RESERVATIONS, 0);
		
		this.associatedServer = null;
		this.neighbor = neighbor;
		virtualMachines = new DVMSManagedElementSet<DVMSVirtualMachine>();
		knowledgeBase = new DVMSKnowledgeBase();
		vmReservations = new LinkedList<VMReservationMessage>();
		capacityReservations = new ArrayList<CapacityReservationMessage>();
		eventsSent = new HashSet<EventMessage>();
		eventsProcessed = new HashSet<EventMessage>();
		computingReconfigurationPlan = false;
		currentIteration = 0;
		
		// SG Code
		lock = new SGSynchronized(); 
		
	}
	
	//Copy constructor (shallow copy)
	public DVMSNode(DVMSNode node){
		this(node.getName(), node.getNbOfCPUs(), node.getCPUCapacity(), node.getMemoryTotal(), node.getNeighbor());
		associatedServer = node.getAssociatedServer();
		virtualMachines = node.getVirtualMachines();
		knowledgeBase = node.getKnowledgeBase();
		nodeReservation = node.getNodeReservation();
		vmReservations = node.getVmReservations();
		capacityReservations = node.getCapacityReservations();
		eventsSent = node.getEventsSent();
		eventsProcessed = node.getEventsProcessed();
		computingReconfigurationPlan = node.isComputingReconfigurationPlan();
		currentIteration = node.getCurrentIteration();
		copyValues(node);
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Accessors
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//public synchronized Server getAssociatedServer(){
	public ServerForSG getAssociatedServer(){
		return this.associatedServer;
	}
	
	//public synchronized void setAssociatedServer(Server associatedServer){
	public void setAssociatedServer(ServerForSG associatedServer){
			
		this.lock.lock();
		this.associatedServer = associatedServer;
		this.lock.unlock(); 
	}

	//public synchronized Server getNeighbor() {
	public ServerForSG getNeighbor() {
		return neighbor;
	}
	
//	public synchronized DVMSManagedElementSet<DVMSVirtualMachine> getVirtualMachines(){
	public DVMSManagedElementSet<DVMSVirtualMachine> getVirtualMachines(){
		DVMSManagedElementSet<DVMSVirtualMachine> tmp;
		this.lock.lock(); 
		tmp=new DVMSManagedElementSet<DVMSVirtualMachine>(virtualMachines);
		this.lock.unlock(); 
		return tmp; 
	}
	
//	public synchronized void setVirtualMachines(DVMSManagedElementSet<DVMSVirtualMachine> hostedVms){
	public void setVirtualMachines(DVMSManagedElementSet<DVMSVirtualMachine> hostedVms){

		this.lock.lock(); 
		this.virtualMachines = hostedVms;
		updateResourcesUsedByVirtualMachines();
		this.lock.unlock(); 
	}
	
	public DVMSKnowledgeBase getKnowledgeBase(){
		return knowledgeBase;
	}
	
//	public synchronized NodeReservationMessage getNodeReservation(){
	public NodeReservationMessage getNodeReservation(){
		return nodeReservation;
	}
	
	@Deprecated
//	public synchronized List<VMReservationMessage> getVmReservations(){
	public synchronized List<VMReservationMessage> getVmReservations(){
		List<VMReservationMessage> tmp; 
		this.lock.lock(); 
		try{
			tmp=new LinkedList<VMReservationMessage>(vmReservations);
		} catch (NullPointerException e){
			tmp=null;
		}
		this.lock.unlock(); 
		return tmp;
	}
	
	@Deprecated
//	public synchronized List<CapacityReservationMessage> getCapacityReservations(){
	public List<CapacityReservationMessage> getCapacityReservations(){

		List<CapacityReservationMessage> tmp; 
		this.lock.lock(); 
		try{
			tmp=new ArrayList<CapacityReservationMessage>(capacityReservations);
		} catch (NullPointerException e){
			tmp=null;
		}
		this.lock.unlock(); 
		return tmp; 
	}
	
	//Synchronization and copy required (concurrent access by DVMSServer and DVMSMonitor)
	//public synchronized Set<EventMessage> getEventsSent(){
	public Set<EventMessage> getEventsSent(){
		Set<EventMessage> tmp; 
		this.lock.lock(); 
		try{
			tmp=new HashSet<EventMessage>(eventsSent);
		} catch (NullPointerException e){
			tmp=null;
		}
		this.lock.unlock(); 
		return tmp; 
	}
	
	//public synchronized Set<EventMessage> getEventsProcessed()
	public Set<EventMessage> getEventsProcessed(){
		Set<EventMessage> tmp; 
		this.lock.lock(); 
		try{
			tmp=new HashSet<EventMessage>(eventsProcessed);
		} catch (NullPointerException e){
			tmp=null;
		}
		this.lock.unlock();
		return tmp; 
	}
		
	//Synchronization required (concurrent accesses by DVMSServer and DVMSScheduling)
	//	public synchronized boolean isComputingReconfigurationPlan(){
	public boolean isComputingReconfigurationPlan(){
		return computingReconfigurationPlan;
	}
		
	//Synchronization required (concurrent accesses by DVMSServer and DVMSScheduling)
	//	public synchronized void setComputingReconfigurationPlan(boolean schedulerStarted){
	public void setComputingReconfigurationPlan(boolean schedulerStarted){
		this.lock.unlock(); 
		this.computingReconfigurationPlan = schedulerStarted;
		this.lock.unlock(); 
	}
	
//	public synchronized int getCurrentIteration(){
	public int getCurrentIteration(){

		return currentIteration;
	}
	
//	public synchronized void setCurrentIteration(int currentIteration){
	public  void setCurrentIteration(int currentIteration){
		this.lock.lock(); 
		this.currentIteration = currentIteration;
		this.lock.unlock(); 
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods related to node resources (total and consumed)
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Return the number of CPUs dedicated for Virtual Machines.
	 * 
	 * @return a positive integer
	 */
	public int getNbOfCPUs() {
		return ((Integer) getValue(CPU_NB)).intValue();
	}
	
	/**
	 * Return the capacity of each CPU.
	 * 
	 * @return a positive integer
	 */
	public int getCPUCapacity() {
		return ((Integer) getValue(CPU_CAPACITY)).intValue();
	}
	
//	public synchronized int getCPUUsedByVMs(){
	public int getCPUUsedByVMs(){
		return ((Integer) getValue(CPU_CAPACITY_USED_BY_VMS)).intValue();
	}
	
	@Deprecated
//	public synchronized int getCPUUsedByReservations(){
	public int getCPUUsedByReservations(){

		return ((Integer) getValue(CPU_CAPACITY_USED_BY_RESERVATIONS)).intValue();
	}

	/**
	 * Return the amount of memory dedicated for Virtual Machines.
	 * 
	 * @return the amount of memory in MB
	 */
	public int getMemoryTotal() {
		return ((Integer) getValue(MEMORY_TOTAL)).intValue();
	}
	
	//public synchronized int getMemoryUsedByVMs(){
	public int getMemoryUsedByVMs(){

		return ((Integer) getValue(MEMORY_USED_BY_VMS)).intValue();
	}
	
	@Deprecated
//	public synchronized int getMemoryUsedByReservations(){
	public  int getMemoryUsedByReservations(){
		return ((Integer) getValue(MEMORY_USED_BY_RESERVATIONS)).intValue();
	}
	
//	private synchronized void updateResourcesUsedByVirtualMachines(){
	private void updateResourcesUsedByVirtualMachines(){
	
		int totalCpuCapacityUsedByVMs = 0;
		int totalMemoryUsedByVMs = 0;
		this.lock.lock(); 
		
		for(DVMSVirtualMachine vm : virtualMachines){
			totalCpuCapacityUsedByVMs += vm.getCPUConsumption()*vm.getNbOfCPUs();
			totalMemoryUsedByVMs += vm.getMemoryConsumption();
		}
		
		updateValue(CPU_CAPACITY_USED_BY_VMS, totalCpuCapacityUsedByVMs);
		updateValue(MEMORY_USED_BY_VMS, totalMemoryUsedByVMs);
		this.lock.unlock();
	}
	
	//Update vm resource usage with additional usage
	//Synchronization required for calls to be consistent (map accessed by DVMSServer through reservations and DVMSMonitor through VMs hosted)
//	private synchronized void updateResourcesUsedByVirtualMachines(int cpuCapacityUsed, int memoryUsed){
	private void updateResourcesUsedByVirtualMachines(int cpuCapacityUsed, int memoryUsed){
		this.lock.lock(); 
		updateValue(CPU_CAPACITY_USED_BY_VMS, (Integer)getValue(CPU_CAPACITY_USED_BY_VMS) + cpuCapacityUsed);
		updateValue(MEMORY_USED_BY_VMS, (Integer)getValue(MEMORY_USED_BY_VMS) + memoryUsed);
		this.lock.unlock(); 
	}
	
	//Update reservation resource usage with additional usage
	//Synchronization required for calls to be consistent (map accessed by DVMSServer through reservations and DVMSMonitor through VMs hosted)
	@Deprecated
	//private synchronized void updateResourcesUsedByReservations(int cpuCapacityUsed, int memoryUsed){
	private void updateResourcesUsedByReservations(int cpuCapacityUsed, int memoryUsed){

		this.lock.lock(); 
		updateValue(CPU_CAPACITY_USED_BY_RESERVATIONS, (Integer)getValue(CPU_CAPACITY_USED_BY_RESERVATIONS) + cpuCapacityUsed);
		updateValue(MEMORY_USED_BY_RESERVATIONS, (Integer)getValue(MEMORY_USED_BY_RESERVATIONS) + memoryUsed);
		this.lock.unlock(); 
	}
	
	//Tells whether the node is overloaded or not
	//Synchronized for data (cpu and memory) to be consistent
// public synchronized boolean isOverloaded() {
	public boolean isOverloaded() {

		boolean res=false; 
		this.lock.lock(); 
		res= this.getCPUUsedByVMs() > this.getCPUCapacity()
			|| this.getMemoryUsedByVMs() > this.getMemoryTotal()
			|| this.getCPUUsedByVMs() > DVMSProperties.getCPUOverloadThreshold()
			|| this.getMemoryUsedByVMs() > DVMSProperties.getMemoryOverloadThreshold();
//		return 100*this.getCPUUsedByVMs()/(this.getCPUCapacity()/* *this.getNbOfCPUs()*/) > DEFAULT_CPU_OVERLOAD_THRESHOLD
//			|| 100*this.getMemoryUsedByVMs()/this.getMemoryTotal() > DEFAULT_MEMORY_OVERLOAD_THRESHOLD;
		this.lock.unlock(); 
		return res; 
	}

	//Tells whether the node is underloaded or not (note that an empty node is NOT underloaded)
	//Synchronized for data (cpu and memory) to be consistent
	//public synchronized boolean isUnderloaded() {
	public boolean isUnderloaded()  {
		boolean res=false; 
		this.lock.unlock(); 
		res=  (this.getCPUUsedByVMs() > 0 || this.getMemoryUsedByVMs() > 0)
			&& ((this.getCPUUsedByVMs() <= DVMSProperties.getCPUUnderloadThreshold() && this.getMemoryUsedByVMs() < DVMSProperties.getMemoryOverloadThreshold())
					|| (this.getMemoryUsedByVMs() <= DVMSProperties.getMemoryUnderloadThreshold()) && this.getCPUUsedByVMs() < DVMSProperties.getCPUOverloadThreshold());
//		return (this.getCPUUsedByVMs() > 0 || this.getMemoryUsedByVMs() > 0)
//			&& this.getCPUUsedByVMs() < DVMSProperties.getCPUUnderloadThreshold()
//			&& this.getMemoryUsedByVMs() < DVMSProperties.getMemoryUnderloadThreshold();
//		return (this.getCPUUsedByVMs() > 0 && this.getCPUUsedByVMs() < DVMSProperties.getCPUUnderloadThreshold())
//		|| (this.getMemoryUsedByVMs() > 0 && this.getMemoryUsedByVMs() < DVMSProperties.getMemoryUnderloadThreshold());
//		return 100*this.getCPUUsedByVMs()/(this.getCPUCapacity()/* *this.getNbOfCPUs()*/) < DEFAULT_CPU_UNDERLOAD_THRESHOLD
//			|| 100*this.getMemoryUsedByVMs()/this.getMemoryTotal() < DEFAULT_MEMORY_UNDERLOAD_THRESHOLD;
	
		this.lock.unlock(); 
		return res; 
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods related to VMs
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Add a virtual machine on the node
	//public synchronized void addVirtualMachine(DVMSVirtualMachine vm){
	public void addVirtualMachine(DVMSVirtualMachine vm){
		this.lock.lock();
		virtualMachines.add(vm);
		this.lock.unlock();
		
		updateResourcesUsedByVirtualMachines(vm.getNbOfCPUs()*vm.getCPUConsumption(), vm.getMemoryConsumption());
	}
	
	//Remove a virtual machine from the node
//	public synchronized void removeVirtualMachine(DVMSVirtualMachine vm){
	public  void removeVirtualMachine(DVMSVirtualMachine vm){
		this.lock.lock();
		virtualMachines.remove(vm);
		updateResourcesUsedByVirtualMachines(-vm.getNbOfCPUs()*vm.getCPUConsumption(), -vm.getMemoryConsumption());
		this.lock.unlock(); 
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods related to reservations
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Add a reservation
	 * No need for synchronization until DVMSServer can process several requests in parallel
	 * @param reservation
	 * @return true if the reservation can be added, false otherwise
	 */
	//public synchronized boolean addReservation(ReservationMessage reservation){
	public boolean addReservation(ReservationMessage reservation){
		boolean res=false; 
		this.lock.lock(); 
		//Reserve this node
		if(reservation instanceof NodeReservationMessage)
			res=addNodeReservation((NodeReservationMessage)reservation);
		
		//Add a capacity reservation
		else if(reservation instanceof CapacityReservationMessage)
			res=addCapacityReservation((CapacityReservationMessage)reservation);	
		
		//Add a VM reservation
		else if (reservation instanceof VMReservationMessage)
			res= addVMReservation((VMReservationMessage)reservation);
		
		this.lock.unlock(); 
		return res;
	}
	
	private boolean addNodeReservation(NodeReservationMessage nodeReservation){
		if(this.nodeReservation == null
				|| this.nodeReservation.getEventProcessed().equals(nodeReservation.getEventProcessed())){
			this.nodeReservation = nodeReservation;
			return true;
		}
		
		else 
			return false;
	}
	
	@Deprecated
	private boolean addCapacityReservation(CapacityReservationMessage capacityReservation){
		boolean cpuOverloaded;
		boolean memoryOverloaded;
		
		cpuOverloaded = (this.getCPUUsedByVMs()
				+ this.getCPUUsedByReservations()
				+ capacityReservation.getCPUConsumption())
				> DVMSProperties.getCPUOverloadThreshold();
				
		memoryOverloaded = (this.getMemoryUsedByVMs()
				+ this.getMemoryUsedByReservations()
				+ capacityReservation.getMemoryConsumption())
				> DVMSProperties.getMemoryOverloadThreshold();
		
		if(!cpuOverloaded && !memoryOverloaded){
			capacityReservations.add(capacityReservation);
			
			updateResourcesUsedByReservations(capacityReservation.getCPUConsumption(),
					capacityReservation.getMemoryConsumption());
			
			return true;
		}
		
		else{
			Logger.log(this.getName() + ": reservation failed " + capacityReservation);
			return false;
		}
	}
	
	@Deprecated
	private boolean addVMReservation(VMReservationMessage vmReservation){
		List<String> nameOfVmsToLock = vmReservation.getVmsToLock();
		DVMSVirtualMachine vmToLock;
		
		for(String nameOfVmToLock : nameOfVmsToLock){
			vmToLock = virtualMachines.get(nameOfVmToLock);
			
			if(vmToLock == null || getVmLocker(nameOfVmToLock) != null){
				Logger.log(this.getName() + ": reservation failed " + vmReservation);
				return false;
			}
		}
		
		vmReservations.add(vmReservation);
		return true;
	}
	
	//Get the dvms server locking a given VM for scheduling purposes
	@Deprecated
//	public synchronized Server getVmLocker(String vmName){
	public synchronized Server getVmLocker(String vmName){
		Server tmp=null; 
		this.lock.lock(); 
		for(VMReservationMessage vmReservation : vmReservations){
			if(vmReservation.getVmsToLock().contains(vmName)){
				tmp=vmReservation.getOrigin();
			}
		}
		this.lock.unlock();
		return tmp;
	}
	
	//No need for synchronization until DVMSServer can process several requests in parallel
	//public synchronized void removeReservation(ReservationMessage reservation){
	public void removeReservation(ReservationMessage reservation){
		this.lock.lock();
		if(reservation instanceof NodeReservationMessage
				&& ((NodeReservationMessage)reservation).getEventProcessed().equals(nodeReservation.getEventProcessed()))
			this.nodeReservation = null;
		
		//Remove a capacity reservation
		else if(reservation instanceof CapacityReservationMessage)
			removeCapacityReservation((CapacityReservationMessage)reservation);
		
		//Remove a VM reservation
		else if (reservation instanceof VMReservationMessage)
			removeVMReservation((VMReservationMessage)reservation);
		this.lock.unlock();
	}
	
	@Deprecated
	private void removeCapacityReservation(CapacityReservationMessage reservation){
		int indexOfReservation = capacityReservations.indexOf(reservation);
		
		if(indexOfReservation > -1){
			CapacityReservationMessage capacityReservation = capacityReservations.get(indexOfReservation);			
			capacityReservations.remove(capacityReservation);
			updateResourcesUsedByReservations(-capacityReservation.getCPUConsumption(),
					-capacityReservation.getMemoryConsumption());
		}
	}
	
	@Deprecated
	private void removeVMReservation(VMReservationMessage vmReservation){		
		vmReservations.remove(vmReservation);
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods related to events
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Synchronization required (concurrent access by DVMSServer and DVMSMonitor)
//	public synchronized boolean addEvent(EventMessage event){
	public boolean addEvent(EventMessage event){
		return eventsSent.add(event);
	}
	
	//Synchronization required (concurrent access by DVMSServer and DVMSMonitor)
	//public synchronized void removeEvent(EventMessage event){
	public void removeEvent(EventMessage event){
		this.lock.lock();
		eventsSent.remove(event);
		eventsProcessed.add(event);
		this.lock.unlock(); 
	}
	
	//Clear events sent
	//public synchronized void clearEvents(){
	public void clearEvents(){
		this.lock.lock();
		eventsSent.clear();
		this.lock.unlock(); 
	}
	
	//Check whether the event is still valid
	//It only takes into account the type of event (not where it is from nor the timestamp nor the information vector)
	//public synchronized boolean checkEventValidity(EventMessage event) {
	public  boolean checkEventValidity(EventMessage event) {
		boolean res=false;
		this.lock.lock();
		res= (event.getType().equals(EventType.NODE_OVERLOAD)
				&& this.isOverloaded())
				||(event.getType().equals(EventType.NODE_UNDERLOAD)
						&& this.isUnderloaded());
		this.lock.unlock();
		return res; 
	}

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * Compare this instance to another.
	 * 
	 * @param o
	 *            The instance to compare with
	 * @return true if o is an instance of Node and has the same name
	 */
	@Override
	//public synchronized boolean equals(Object o) {
	public boolean equals(Object o) {
		
		if (o instanceof DVMSNode) {
			return super.equals(o);
		}
		return false;
	}

	/**
	 * Get the hashcode.
	 * 
	 * @return the hashcode of the hostname
	 */
	@Override
	//public synchronized int hashCode() {
	public int hashCode() {
		return super.hashCode();
	}
	
	@Override
//	public synchronized String toString(){
	public String toString(){
		this.lock.lock();
		
		StringBuilder result = new StringBuilder();
		result.append(super.toString());
		result.append(" (");
		
		for(DVMSVirtualMachine vm : virtualMachines){
			result.append(vm.getName());
			result.append(" ");
		}
		
		result.append(")");
		this.lock.unlock();
		return result.toString();
	}
	
	private void readObject(ObjectInputStream stream) throws Exception{
		stream.defaultReadObject();
		neighbor = null;
		knowledgeBase = null;
		nodeReservation = null;
		capacityReservations = null;
		eventsSent = null;
		eventsProcessed = null;
		computingReconfigurationPlan = false;
	}
	
	public static void main(String[] args){
		DVMSNode node = new DVMSNode("node0", 2, 2000, 4096, null);
		DVMSManagedElementSet<DVMSVirtualMachine> vms = new DVMSManagedElementSet<DVMSVirtualMachine>();
		vms.add(new DVMSVirtualMachine("vm0", 1, 2000, 1024));
		vms.add(new DVMSVirtualMachine("vm1", 1, 0, 1024));
		node.setVirtualMachines(vms);
		
		System.out.println(node.isOverloaded());
		System.out.println(node.isUnderloaded());
		
//		CapacityReservationMessage resa = new CapacityReservationMessage("node1", null, null, 1, 2000, 2048);
//		System.out.println(node.addReservation(resa));
//		
//		resa = new CapacityReservationMessage("node1", null, null, 1, 2000, 0);
//		System.out.println(node.addReservation(resa));
	}
}
