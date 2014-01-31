package Injector;
import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.Host;
import org.simgrid.msg.HostNotFoundException;
import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.NativeException;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;

import scheduling.EntropyProperties;
import simulation.*;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Random;


public class Injector extends Process {

    private Deque<InjectorEvent> evtQueue = null ;
    private Deque<LoadEvent> loadQueue = null ;
    private Deque<FaultEvent> faultQueue = null ;

	Injector(Host host, String name, String[] args) throws HostNotFoundException, NativeException  {
	    super(host, name, args);
        System.out.println("Create the event queues");
        loadQueue = generateLoadQueue(SimulatorManager.getSGVMs().toArray(new XVM[SimulatorManager.getSGVMs().size()]), SimulatorProperties.getDuration(), SimulatorProperties.getLoadPeriod());
        System.out.println("Size of getCPUDemand queue:"+loadQueue.size());
        faultQueue =generateFaultQueue(SimulatorManager.getSGHosts().toArray(new XHost[SimulatorManager.getSGHosts().size()]), SimulatorProperties.getDuration(), SimulatorProperties.getCrashPeriod());
        System.out.println("Size of fault queue:"+faultQueue.size());
        evtQueue = mergeQueues(loadQueue,faultQueue);
        System.out.println("Size of event queue:"+evtQueue.size());
    }


    /**
     *
     * @param vms, Simgrid VMs that have been instanciated
     * @param duration int, duration of the simulated time in minutes
     * @param injectionPeriod int,  frequency of event occurrence in seconds
     * @return the queue of the VM changes
     */
    public static Deque<LoadEvent> generateLoadQueue(XVM[] vms, long duration, int injectionPeriod) {

        LinkedList<LoadEvent> eventQueue = new LinkedList<LoadEvent>();
        Random randExpDis=new Random(SimulatorProperties.getSeed());
        double currentTime = 0 ;
        double lambdaPerVM=1.0/injectionPeriod ; // Nb Evt per VM (average)

        Random randExpDis2=new Random(SimulatorProperties.getSeed());

        double mean = SimulatorProperties.getMeanLoad();
        double sigma = SimulatorProperties.getStandardDeviationLoad();

        double gLoad = 0;

        double lambda=lambdaPerVM*vms.length;

        int maxCPUDemand = SimulatorProperties.getCPUCapacity()/SimulatorProperties.getNbOfCPUs();
        int nbOfCPUDemandSlots = SimulatorProperties.getNbOfCPUConsumptionSlots();
        int vmCPUDemand;
        long id=0;
        XVM tempVM;

        //        currentTime+=Math.round(exponentialDis(randExpDis, lambda));
        currentTime+=exponentialDis(randExpDis, lambda);

        Random randVMPicker = new Random(SimulatorProperties.getSeed());
        int nbOfVMs = vms.length;

        while(currentTime < duration){
            //   if( !skipOverlappingEvent || ((int)currentTime) % EntropyProperties.getEntropyPeriodicity() != 0){
            // select a VM
            tempVM = vms[randVMPicker.nextInt(nbOfVMs)];
            // and change its state

            int cpuConsumptionSlot = maxCPUDemand/nbOfCPUDemandSlots;
                /*Uniform assignment of VM getCPUDemand */
            //int slot=(int) (Math.random()*(nbOfCPUDemandSlots+1));
                /* Gaussian law for the getCPUDemand assignment */
            gLoad = Math.max((randExpDis2.nextGaussian()*sigma)+mean, 0);
            int slot= (int) Math.round(Math.min(100,gLoad)*nbOfCPUDemandSlots/100);

            vmCPUDemand = slot*cpuConsumptionSlot*(int)tempVM.getCoreNumber();

            // Add a new event queue
            eventQueue.add(new LoadEvent(id++, currentTime,tempVM, vmCPUDemand));
            //  }
            currentTime+=exponentialDis(randExpDis, lambda);
            //        System.err.println(eventQueue.size());
        }
        Msg.info("Number of events:"+eventQueue.size());
        return eventQueue;
    }

    /* Compute the next exponential value for rand */
    private static double exponentialDis(Random rand, double lambda) {
        return -Math.log(1 - rand.nextDouble()) / lambda;
    }

    public static Deque<FaultEvent> generateFaultQueue(XHost[] xhosts,  long duration, int faultPeriod){
        LinkedList<FaultEvent> faultQueue = new LinkedList<FaultEvent>();
        Random randExpDis=new Random(SimulatorProperties.getSeed());
        double currentTime = 0 ;
        double lambdaPerHost=1.0/faultPeriod ; // Nb crash per host (average)

        int nbOfHosts=xhosts.length;

        double lambda=lambdaPerHost*nbOfHosts;
        long id=0;
        XHost tempHost;

        currentTime+=exponentialDis(randExpDis, lambda);

        Random randHostPicker = new Random(SimulatorProperties.getSeed());

        while(currentTime < duration){
            // select a host
            tempHost = xhosts[randHostPicker.nextInt(nbOfHosts)];
            // and change its state
            // false = off , on = true
            // Add a new event queue
            faultQueue.add(new FaultEvent(id++, currentTime,tempHost, false));
            //For the moment, downtime of a node is arbitrarily set to 5 min
            faultQueue.add(new FaultEvent(id++, currentTime+(300*1000),tempHost, true));
            currentTime+=exponentialDis(randExpDis, lambda);
            //        System.err.println(eventQueue.size());
        }
        Msg.info("Number of events:"+faultQueue.size());
        return faultQueue;
    }


    public static Deque<InjectorEvent> mergeQueues(Deque<LoadEvent> loadQueue, Deque<FaultEvent> faultQueue) {
        LinkedList<InjectorEvent> queue = new LinkedList<InjectorEvent>();
        FaultEvent crashEvt;
        if (faultQueue != null)
            crashEvt = faultQueue.pollFirst();
        else
            crashEvt = null;
        LoadEvent loadEvt = loadQueue.pollFirst();
        // Here we are considering that the getCPUDemand event queue cannot be empty
        do{

            while (crashEvt != null && loadEvt.getTime()>crashEvt.getTime()){
                queue.addLast(crashEvt);
                crashEvt = faultQueue.pollFirst();
            }
            queue.addLast(loadEvt);
            loadEvt = loadQueue.pollFirst();
        }while(loadEvt != null);

        while(crashEvt != null){
            queue.addLast(crashEvt);
            crashEvt = faultQueue.pollFirst();
        }
        return queue;
    }

    /* Args : nbPMs nbVMs eventFile */
	public void main(String[] args) throws MsgException {

        for(InjectorEvent evt: this.evtQueue){
            System.out.println(evt);
        }

		/* Initialization is done in Main */
   
		if(!SimulatorManager.isViable()){
		   System.err.println("Initial Configuration should be viable !");
    	   System.exit(1);
       }
		
	   Trace.hostVariableSet("node0", "NB_MIG", 0); 
	   Trace.hostVariableSet("node0", "NB_MC", 0); 
	   
	   InjectorEvent evt = nextEvent();
	   while(evt!=null){
		   if(evt.getTime() - Msg.getClock()>0)
			   waitFor(evt.getTime() - Msg.getClock());
	       evt.play();
	       evt=nextEvent();
       }
	  Msg.info("End of Injection");   
	  SimulatorManager.setEndOfInjection();
	  
		// Wait for termination of On going scheduling
		waitFor(EntropyProperties.getEntropyPlanTimeout());
    }

	private InjectorEvent nextEvent() {
		return this.evtQueue.pollFirst();
	}
}