package injector;
import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.Host;
import org.simgrid.msg.HostNotFoundException;
import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;
import scheduling.hierarchical.snooze.SnoozeProperties;
import trace.Trace;

import scheduling.centralized.entropy2.EntropyProperties;
import simulation.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Injector extends Process {

    private Deque<InjectorEvent> evtQueue = null ;
    private Deque<LoadEvent> loadQueue = null ;
    private Deque<FaultEvent> faultQueue = null ;
    private Deque<VMSuspendResumeEvent> vmSuspendResumeQueue = null ;

    private Set<Period> loadPeriods = null;
    private Period defaultPeriod = null;

    public Injector(Host host, String name) throws HostNotFoundException {
        super(host, name);

        // if there is a period file for load
        if(SimulatorProperties.getLoadFile() != null) {
            try {
                loadPeriods = new HashSet<Period>();
                BufferedReader reader = new BufferedReader(new FileReader(SimulatorProperties.getLoadFile()));
                String line = null;
                Pattern p = Pattern.compile("\\[(\\w+)\\s+begin:\\s*(\\d{2}:\\d{2}|\\d+),\\s*end:\\s*(\\d{2}:\\d{2}|\\d+),\\s*mean:\\s*(\\d+),\\s*stddev:\\s*(\\d+)\\]");

                while((line = reader.readLine()) != null) {
                    Matcher m = p.matcher(line);
                    if(!m.matches())
                        throw new IllegalArgumentException("Could not parse this line:\n" + line);

                    Period period = new Period();
                    period.name = m.group(1);
                    period.begin = timeToSeconds(m.group(2));
                    period.end = timeToSeconds(m.group(3));
                    period.mean = Double.parseDouble(m.group(4));
                    period.stddev = Double.parseDouble(m.group(5));
                    loadPeriods.add(period);
                }
            } catch (Exception e) {
                Msg.error("Injector error while reading load periods");
                Msg.error(e.toString());
                e.printStackTrace();
                System.exit(1);
            }

            if(loadPeriods.size() == 0)
                Msg.warn("The load periods file you provided does not contain any value");
            else {
                Msg.info("Loaded " + loadPeriods.size() + " periods from " + SimulatorProperties.getLoadFile());
                for(Period p: loadPeriods)
                    Msg.info(p.toString());
            }
        }

        defaultPeriod = new Period();
        defaultPeriod.name = "default";
        defaultPeriod.mean = SimulatorProperties.getMeanLoad();
        defaultPeriod.stddev = SimulatorProperties.getStandardDeviationLoad();

        // System.out.println("Create the event queues");
        loadQueue = generateLoadQueue(SimulatorManager.getSGVMs().toArray(new XVM[SimulatorManager.getSGVMs().size()]), SimulatorProperties.getDuration(), SimulatorProperties.getLoadPeriod());
        //System.out.println("Size of getCPUDemand queue:"+loadQueue.size());
        // Stupid code to stress Snooze service nodes - Used for the paper submission
        if(SimulatorProperties.getAlgo().equals("hierarchical") && SnoozeProperties.faultMode())
            faultQueue = generateSnoozeFaultQueue(SimulatorManager.getSGHostsToArray(), SimulatorProperties.getDuration());
        else
            faultQueue = generateFaultQueue(SimulatorManager.getSGHostsToArray(), SimulatorProperties.getDuration(), SimulatorProperties.getCrashPeriod());

        if(SimulatorProperties.getSuspendVMs())
            vmSuspendResumeQueue = generateVMFluctuationQueue(SimulatorManager.getSGVMsToArray(), SimulatorProperties.getDuration(), SimulatorProperties.getVMSuspendPeriod());
        else // Create an empty list.
            vmSuspendResumeQueue = new LinkedList<VMSuspendResumeEvent>();
        System.out.println(String.format("Size of event queues: load: %d, faults: %d, vm suspend: %d", loadQueue.size(), faultQueue.size(), vmSuspendResumeQueue.size()));
        evtQueue = mergeQueues(loadQueue,faultQueue, vmSuspendResumeQueue);
        // System.out.println("Size of event queue:"+evtQueue.size());

    }


    /**
     *
     * @param vms, Simgrid VMs that have been instanciated
     * @param duration int, duration of the simulated time in second
     * @param injectionPeriod int,  frequency of event occurrence in seconds
     * @return the queue of the VM changes
     */
    public Deque<LoadEvent> generateLoadQueue(XVM[] vms, long duration, int injectionPeriod) {

        LinkedList<LoadEvent> eventQueue = new LinkedList<LoadEvent>();
        Random randExpDis=new Random(SimulatorProperties.getSeed());
        double currentTime = 0 ;
        double lambdaPerVM=1.0/injectionPeriod ; // Nb Evt per VM (average)

        Random randGaussian=new Random(SimulatorProperties.getSeed());

        double gLoad = 0;

        double lambda=lambdaPerVM*vms.length;

        // int maxCPUDemand = SimulatorProperties.getCPUCapacity()/SimulatorProperties.getNbOfCPUs();
        int maxCPUDemand = SimulatorProperties.getVMMAXCPUConsumption();
        int nbOfCPUDemandSlots = SimulatorProperties.getNbOfCPUConsumptionSlots();
        int vmCPUDemand;
        long id=0;
        XVM tempVM;

        currentTime+=exponentialDis(randExpDis, lambda);

        Random randVMPicker = new Random(SimulatorProperties.getSeed());
        int nbOfVMs = vms.length;

        // DEBUG
        try {
            Files.deleteIfExists(Paths.get("logs/load-events.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        // END DEBUG

        try(BufferedWriter writer = new BufferedWriter(new FileWriter("logs/load-events.txt", true)))
        {

        while(currentTime < duration){
            Period p = getPeriodAt(currentTime);
            // select a VM
            tempVM = vms[randVMPicker.nextInt(nbOfVMs)];

            int cpuConsumptionSlot = maxCPUDemand/nbOfCPUDemandSlots;

            /* Gaussian law for the getCPUDemand assignment */
            gLoad = Math.max((randGaussian.nextGaussian() * p.stddev) + p.mean, 0);
            int slot= (int) Math.round(Math.min(100,gLoad)*nbOfCPUDemandSlots/100);

            vmCPUDemand = slot*cpuConsumptionSlot*(int)tempVM.getCoreNumber();

            // Add a new event to the queue
            LoadEvent le = new LoadEvent(id++, currentTime,tempVM, vmCPUDemand);
            eventQueue.add(le);

            // DEBUG
            writer.write(le.toString() + " (" + p.name + ")\n");
            // END DEBUG

            currentTime+=exponentialDis(randExpDis, lambda);
        }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(34);
        }

        Msg.info("Number of events:"+eventQueue.size());
        return eventQueue;
    }

    /**
     * Returns a time in seconds from two possible formats:
     * <ul>
     *     <li>A number of seconds from the beginning of the simulation</li>
     *     <li>A string of in the 'hh:mm' format</li>
     * </ul>
     * @param str either a number of seconds or 'hh:mm'
     * @return the time as a number of seconds
     */
    private double timeToSeconds(String str) {
        try {
            return Double.parseDouble(str);
        } catch(NumberFormatException nfe) {
            String[] ar = str.split(":");
            return Integer.parseInt(ar[0]) * 3600 + Integer.parseInt(ar[1]) * 60;
        }
    }

    private Period getPeriodAt(double time) {
        Period current = null;

        for(Period p: loadPeriods) {
            if(time >= p.begin && time < p.end) {
                current = p;
                break;
            }
        }

        return (current != null)? current:defaultPeriod;
    }

    /* Compute the next exponential value for rand */
    private double exponentialDis(Random rand, double lambda) {
        return -Math.log(1 - rand.nextDouble()) / lambda;
    }


    public Deque<FaultEvent> generateSnoozeFaultQueue(XHost[] xhosts,  long duration) {
        LinkedList<FaultEvent> faultQueue = new LinkedList<FaultEvent>();
        long id=0;
        XHost tempHost;
        double currentTime = 0;
        double crashDuration = SimulatorProperties.getCrashDuration();
        long GLFaultPeriod = SnoozeProperties.getGLFaultPeriodicity();
        long GMFaultPeriod = SnoozeProperties.getGMFaultPeriodicity();

        if (GLFaultPeriod != 0) {

            // Kill GL in a specific way
            currentTime = GLFaultPeriod;
            do {

                tempHost = xhosts[SimulatorManager.getSGHostingHosts().size()];

                if (!ifStillOffUpdate(tempHost, faultQueue, currentTime)) {
                    // and change its state
                    // false = off , on = true
                    // Add a new event queue
                    faultQueue.add(new FaultEvent(id++, currentTime, tempHost, false));
                }
                if (currentTime + crashDuration < duration) {
                    //For the moment, downtime of a node is arbitrarily set to crashDuration
                    faultQueue.add(new FaultEvent(id++, currentTime + (crashDuration), tempHost, true));
                    //        System.err.println(eventQueue.size());
                }
                currentTime += GLFaultPeriod;
            } while (currentTime < duration);
        }

        // Random kill GM
        Random randHostPicker = new Random(SimulatorProperties.getSeed());
        currentTime = GMFaultPeriod;
        do {
            // Random select of one GM
            int index = -1;
            if (GLFaultPeriod == 0)
                index = randHostPicker.nextInt(SimulatorManager.getSGServiceHosts().size());
            else // GL faults have been already treated, so only consider GMs
                index = randHostPicker.nextInt(SimulatorManager.getSGServiceHosts().size()-1);

            // Please remind that node0 hosts VMs, so the first service node is Simulator.Manager.getSGHostingHosts().
            tempHost = xhosts[SimulatorManager.getSGHostingHosts().size()+index];

            if(!ifStillOffUpdate(tempHost, faultQueue, currentTime)) {
                // and change its state
                // false = off , on = true
                // Add a new event queue
                faultQueue.add(new FaultEvent(id++, currentTime, tempHost, false));
            }
            if (currentTime + crashDuration < duration) {
                //For the moment, downtime of a node is arbitrarily set to crashDuration
                faultQueue.add(new FaultEvent(id++, currentTime + (crashDuration), tempHost, true));
                //        System.err.println(eventQueue.size());
            }
            currentTime += GMFaultPeriod;
        }while(currentTime < duration);


        Msg.info("Number of events:"+faultQueue.size());
        for (InjectorEvent evt: faultQueue){
            Msg.info(evt.toString());
        }

        // Sort the list for the merge:
        Collections.sort(faultQueue, new Comparator<FaultEvent>() {
            @Override
            public int compare(FaultEvent o1, FaultEvent o2) {
                if (o1.getTime() > o2.getTime())
                    return 1 ;
                else if (o1.getTime() == o2.getTime())
                    return 0 ;
                else // o1.getTime() < o2.getTime()
                    return -1;
            }
        });

        return faultQueue;

    }

    public Deque<FaultEvent> generateFaultQueue(XHost[] xhosts,  long duration, int faultPeriod){
        LinkedList<FaultEvent> faultQueue = new LinkedList<FaultEvent>();
        Random randExpDis=new Random(SimulatorProperties.getSeed());
        double currentTime = 0 ;
        double lambdaPerHost=1.0/faultPeriod ; // Nb crash per host (average)
        double crashDuration = SimulatorProperties.getCrashDuration();
        int nbOfHosts=xhosts.length;

        double lambda=lambdaPerHost*nbOfHosts;
        long id=0;
        XHost tempHost;

        currentTime+=exponentialDis(randExpDis, lambda);

        Random randHostPicker = new Random(SimulatorProperties.getSeed());

        while(currentTime < duration){
            // select a host
            int index = randHostPicker.nextInt(nbOfHosts);
            tempHost = xhosts[index];

            if(!ifStillOffUpdate(tempHost, faultQueue, currentTime)) {

                // and change its state
                // false = off , on = true
                // Add a new event queue
                faultQueue.add(new FaultEvent(id++, currentTime, tempHost, false));
            }
            if (currentTime + crashDuration < duration) {
                //For the moment, downtime of a node is arbitrarily set to crashDuration
                faultQueue.add(new FaultEvent(id++, currentTime + (crashDuration), tempHost, true));
                //        System.err.println(eventQueue.size());
            }
            currentTime += exponentialDis(randExpDis, lambda);
        }

        Msg.info("Number of events:"+faultQueue.size());
        for (InjectorEvent evt: faultQueue){
            Msg.info(evt.toString());
        }

        // Sort the list for the merge:
        Collections.sort(faultQueue, new Comparator<FaultEvent>() {
            @Override
            public int compare(FaultEvent o1, FaultEvent o2) {
                if (o1.getTime() > o2.getTime())
                    return 1 ;
                else if (o1.getTime() == o2.getTime())
                    return 0 ;
                else // o1.getTime() < o2.getTime()
                    return -1;
            }
        });

        return faultQueue;
    }


    public Deque<VMSuspendResumeEvent> generateVMFluctuationQueue(XVM[] xvms,  long duration, int faultPeriod){
        LinkedList<VMSuspendResumeEvent> vmQueue = new LinkedList<VMSuspendResumeEvent>();
        Random randExpDis=new Random(SimulatorProperties.getSeed());
        double currentTime = 0 ;
        double lambdaPerHost=1.0/faultPeriod ; // Nb crash per host (average)
        double crashDuration = SimulatorProperties.getCrashDuration();
        int nbOfVMs= xvms.length;

        double lambda=lambdaPerHost* nbOfVMs;
        long id=0;
        XVM tempVM;

        currentTime+=exponentialDis(randExpDis, lambda);

        Random randHostPicker = new Random(SimulatorProperties.getSeed());

        while(currentTime < duration){
            // select a VM
            int index = randHostPicker.nextInt(nbOfVMs);
            tempVM = xvms[index];

            if(!ifStillSuspendedUpdate(tempVM, vmQueue, currentTime)) {
                // and change its state
                // false = suspend, true = resume
                // Add a new event queue
                vmQueue.add(new VMSuspendResumeEvent(id++, currentTime, tempVM, false));
            }

            vmQueue.add(new VMSuspendResumeEvent(id++, currentTime + (crashDuration), tempVM, true));
            currentTime += exponentialDis(randExpDis, lambda);
        }

        /*
        Msg.info("Number of VM suspend-resume events:" + vmQueue.size());
        for (InjectorEvent evt: vmQueue){
            Msg.info(evt.toString());
        }
        */

        // Sort the list for the merge:
        Collections.sort(vmQueue, new Comparator<VMSuspendResumeEvent>() {
            @Override
            public int compare(VMSuspendResumeEvent o1, VMSuspendResumeEvent o2) {
                return (int) Math.round(o1.getTime() - o2.getTime());
            }
        });

        return vmQueue;
    }


    public boolean isStillOff(XHost tmp, LinkedList<FaultEvent> queue, double currentTime, double crashDuration){
        ListIterator<FaultEvent> iterator = queue.listIterator(queue.size());
        while(iterator.hasPrevious()){
            FaultEvent evt = iterator.previous();
            if(evt.getState() == false){
                if (evt.getTime() + crashDuration  >= currentTime) {
                    if (evt.getHost()== tmp)
                        return true;
                }
                else
                    break;
            }
        }
        return false;
    }

    // if the node is off, we should remove the next On event and postpone it at currenttime +crashDuration
    // Note that the update is performed in the upper function.
    private boolean ifStillOffUpdate(XHost tmp, LinkedList<FaultEvent> queue, double currentTime){
        ListIterator<FaultEvent> iterator = queue.listIterator(queue.size());
        while(iterator.hasPrevious()){
            FaultEvent evt = iterator.previous();
            if(evt.getState() == true){
                if (evt.getTime()  >= currentTime) {
                    if (evt.getHost()== tmp) {
                        iterator.remove();
                        return true;
                    }
                }
                else
                    break;
            }
        }
        return false;
    }

    // If the VM is suspended, we should remove the next resume event and postpone it at currenttime +crashDuration
    // Note that the update is performed in the upper function.
    private boolean ifStillSuspendedUpdate(XVM tmp, LinkedList<VMSuspendResumeEvent> queue, double currentTime){
        ListIterator<VMSuspendResumeEvent> iterator = queue.listIterator(queue.size());

        while(iterator.hasPrevious()) {
            VMSuspendResumeEvent evt = iterator.previous();

            if(evt.getState() == true){
                if (evt.getTime()  >= currentTime) {
                    if (evt.getVM() == tmp) {
                        iterator.remove();
                        return true;
                    }
                }
                else
                    break;
            }
        }
        return false;
    }

    public Deque<InjectorEvent> mergeQueues(Deque<LoadEvent> loadQueue,
                                                   Deque<FaultEvent> faultQueue,
                                                   Deque<VMSuspendResumeEvent> vmEvents) {
        LinkedList<InjectorEvent> queue = new LinkedList<InjectorEvent>();
        queue.addAll(loadQueue);
        queue.addAll(faultQueue);
        queue.addAll(vmEvents);

        queue.sort(new Comparator<InjectorEvent>() {
            @Override
            public int compare(InjectorEvent o1, InjectorEvent o2) {
                return (int) Math.round(o1.getTime() - o2.getTime());
            }
        });

        writeEventQueue(queue);

        return queue;
    }

    private void writeEventQueue(LinkedList<InjectorEvent> queue) {

        try {
            File file = new File("logs/events-queue.txt");
            file.getParentFile().mkdirs();
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            for (InjectorEvent evt: queue){
                bw.write(evt.toString());
                bw.write("\n");
                bw.flush();
            }

            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void main(String[] args) throws MsgException{
        //main2(args);
    }

    /* Args : nbPMs nbVMs eventFile */
    public void main2(String[] args) throws MsgException {
		/* Initialization is done in Main */

        Msg.info("Start of Injection");
        if(!SimulatorManager.isViable()){
            System.err.println("Initial Configuration should be viable !");
            System.exit(1);
        }


        if(SimulatorProperties.goToStationaryStatus()) {
            // Uggly but efficient to reach a kind of stationary state
            for (XVM vm : SimulatorManager.getSGVMsOn())
                SimulatorManager.updateVM(vm, SimulatorProperties.getMeanLoad());
        }

        InjectorEvent evt = nextEvent();
        while(evt!=null && evt.getTime() < SimulatorProperties.getDuration()){
            if(evt.getTime() - Msg.getClock()>0)
                waitFor(evt.getTime() - Msg.getClock());
            evt.play();
            evt=nextEvent();
        }
        waitFor(SimulatorProperties.getDuration() - Msg.getClock());
        Msg.info("End of Injection");
        SimulatorManager.setEndOfInjection();

    }

    private InjectorEvent nextEvent() {
        return this.evtQueue.pollFirst();
    }
}

class Period {
    String name = null;
    double begin = 0.0D;
    double end = 0.0D;
    double mean = 0.0D;
    double stddev = 0.0D;

    public int hashCode() {
        return name.hashCode();
    }

    public String toString() {
        return String.format("[period name: %s, begin: %.0f, end: %.0f, mean: %.2f, stddev: %.2f]",
                name,
                begin,
                end,
                mean,
                stddev
        );
    }
}