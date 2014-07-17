package scheduling.snooze;

import configuration.XHost;
import entropy.configuration.Configuration;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;
import scheduling.Scheduler;
import scheduling.entropyBased.entropy2.Entropy2RP;
import scheduling.snooze.msg.*;
import simulation.SimulatorManager;

import java.util.*;

/**
 * Created by sudholt on 25/05/2014.
 */
public class GroupManager extends Process {
    static int noAllManagedLCs = 0;
    private String name;
    private Host host;
    private boolean thisGMToBeStopped = false;
    private String glHostname = "";
    private double glTimestamp;
    private Hashtable<String, LCInfo> lcInfo = new Hashtable<String, LCInfo>();  // ConcurrentHashMap more efficient?
    // one mailbox per LC: lcHostname+"beat"
    private double procSum;
    private int memSum;
    private String glSummary = "glSummary";
    private String inbox;
    private String gmHeartbeatNew = "gmHeartbeatNew";
    private String gmHeartbeatBeat = "gmHeartbeatBeat";
    private Collection<XHost> managedLCs;

    public GroupManager(Host host, String name) {
        super(host, name);
        this.host = host;
        this.name = name;
        this.inbox = AUX.gmInbox(host.getName());
    }

    @Override
    public void main(String[] strings) throws MsgException {
        join();
        startBeats();
        startSummaryInfoToGL();
//        startScheduling();
        while (true) {
            SnoozeMsg m = AUX.arecv(inbox);
            if (m != null) handle(m);
            glDead();
            if (thisGMToBeStopped) {
                Logger.err("[GM.main] GM stops: " + m);
                break;
            }
            deadLCs();
            sleep(AUX.DefaultComputeInterval);
        }
        Logger.err("GM stopped: " + host.getName());
    }

    void handle(SnoozeMsg m) {
//        Logger.info("[GM.handle] GMIn: " + m);
        String cs = m.getClass().getSimpleName();

        switch (cs) {
            case "BeatLCMsg":   handleBeatLC(m);   break;
            case "GMElecMsg":   handleGMElec(m);   break;
            case "LCChargeMsg": handleLCCharge(m); break;
            case "NewLCMsg":    handleNewLC(m);    break;
            case "RBeatGLMsg":  handleRBeatGL(m);  break;
            case "SnoozeMsg":
                Logger.err("[GM(SnoozeMsg)] Unknown message" + m + " on " + host);
                break;
        }
    }

    /**
     * Listen asynchronously for heartbeats from all known LCs
     */
    void handleBeatLC(SnoozeMsg m) {
//        Logger.info("[GM(BeatLC)] " + m);
        String lc = (String) m.getMessage();
        lcInfo.put(lc, new LCInfo(lcInfo.get(lc).charge, Msg.getClock()));
//        Logger.info("[GM(BeatLC)] " + lc + ", " + lcInfo.get(lc).charge + ", " + new Date());
    }

    void handleGMElec(SnoozeMsg m) {
        // Ex-nihilo GL creation
        GroupLeader gl = new GroupLeader(Host.currentHost(), "groupLeader");
        try {
            gl.start();
        } catch (HostNotFoundException e) {
            e.printStackTrace();
        }
        glHostname = gl.getHost().getName();
        Logger.info("[GM(GMElec)] New leader created on: " + glHostname);

        // Notify LCs and Multicast, stop this GM
        for (String lc : lcInfo.keySet()) new GMStopMsg(host.getName(), AUX.lcInbox(lc), null, null).send();
        m = new GMStopMsg(glHostname, AUX.glElection, null, null);
        m.send();
        Logger.info("[GM(GMElec)] Stop msg: " + m);
        try {
            sleep(AUX.GLCreationTimeout); // TODO: should be replaced by a sync. with LCs and MUL
        } catch (HostFailureException e) {
            e.printStackTrace();
        }
        thisGMToBeStopped = true;
    }

    void handleLCCharge(SnoozeMsg m) {
        try {
            String lcHostname = (String) m.getOrigin();
            LCChargeMsg.LCCharge cs = (LCChargeMsg.LCCharge) m.getMessage();
            LCCharge newCharge = new LCCharge(cs.getProcCharge(), cs.getMemUsed(), Msg.getClock());
            double oldBeat = lcInfo.get(lcHostname).heartbeatTimestamp;
            lcInfo.put(lcHostname, new LCInfo(newCharge, oldBeat));
//            Logger.info("[GM(LCCharge)] Charge updated: " + lcHostname + ", " + m);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void handleNewLC(SnoozeMsg m) {
        String lcHostname = (String) m.getMessage();
        double   ts  = Msg.getClock();
        // Init LC charge and heartbeat
        LCInfo    lci = new LCInfo(new LCCharge(0, 0, ts), ts);
        lcInfo.put(lcHostname, lci);
        noAllManagedLCs++;
        // Send acknowledgment
        m = new NewLCMsg(host.getName(), AUX.lcInbox(lcHostname), null, null);
        m.send();
//        Logger.info("[GM(NewLCMsg)] LC stored: " + m);
    }

    void handleRBeatGL(SnoozeMsg m) {
        String gl = (String) m.getOrigin();
//        Logger.info("[GM(RBeatGL)] Old, new ts: " + glTimestamp + ", " + (double) m.getMessage());
        if (!glHostname.equals("") && glHostname != gl) Logger.err("[GM(RBeatGLMsg)] Multiple GLs: " + glHostname + ", " + gl);
        else {
            glTimestamp = (double) m.getMessage();
//            Logger.info("[GM(RBeatGL)] TS updated: " + glTimestamp);
            if (glHostname.equals("")) {
                glHostname = gl;
                Logger.err("[GM(RBeatGL)] GL initialized: " + gl + " on " + host);
            }
        }
    }

    /**
     * Identify and handle dead LCs
     */
    void deadLCs() {
        if (lcInfo.isEmpty()) return;
        // Identify dead LCs
        int no = lcInfo.size();
        HashSet<String> deadLCs = new HashSet<String>();
        for (String lcHostname: lcInfo.keySet()) {
            if (AUX.timeDiff(lcInfo.get(lcHostname).heartbeatTimestamp) > AUX.HeartbeatTimeout) {
                noAllManagedLCs--;
                deadLCs.add(lcHostname);
                Logger.err("[GM.deadLCs] " + lcHostname);
            }
        }

        if (noAllManagedLCs != 39)
            Logger.info("[GM.deadLCs] No all GMs: " + noAllManagedLCs + " | This GM, initial: " + no + ", dead: "
                    + deadLCs.size());

        // Remove dead LCs
        for (String lcHostname: deadLCs) lcInfo.remove(lcHostname);
    }

    /**
     * Identify dead GL, request election (not: wait for new GL)
     */
    void glDead() {
        if (glHostname.equals("")) {
//            Logger.err("[GM.glDead] glHostname == \"\"");
            return;
        }
        if (glTimestamp == 0) {
//            Logger.err("[GM.glDead] glTimestamp == null");
            return;
        }
        if (AUX.timeDiff(glTimestamp) > AUX.HeartbeatTimeout) {
            Logger.info("[GM.glDead] GL dead: " + glTimestamp + " => " + Msg.getClock());
            glHostname = "";
            SnoozeMsg m = new GLElecMsg(host.getName(), AUX.multicast, null, null);
            m.send();
            // New GL will be initialized via BeatGLMsg
        }
    }


    /**
     * Send join request to Multicast
     */
    void join() {
        try {
            SnoozeMsg m = new NewGMMsg(host.getName(), AUX.multicast, null, inbox);
            m.send();
            do {
                m = (SnoozeMsg) Task.receive(inbox);
            } while (!m.getClass().getSimpleName().equals("RBeatGLMsg"));
            glHostname = m.getOrigin();
//            Logger.info("[GM.join] GL beat: " + m);
            // Wait for GroupLeader acknowledgement
            m = new NewGMMsg(host.getName(), AUX.glInbox(glHostname), null, inbox);
            m.send();
            do {
                m = (SnoozeMsg) Task.receive(inbox);
            } while (!m.getClass().getSimpleName().equals("NewGMMsg"));
            glHostname = (String) m.getMessage();
            Logger.info("[GM.join] Finished: " + m);
        } catch (TimeoutException e) {
            Logger.err("[GM.join] No joining" + host.getName());
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends beats to multicast group
     */
    void startBeats() throws HostNotFoundException {
        new Process(host, host.getName()+"-gmBeats") {
            public void main(String[] args) throws HostFailureException {
                while (true) {
                    BeatGMMsg m = new BeatGMMsg(host.getName(), AUX.multicast, null, null);
                    m.send();
//                    Logger.info("[GL.beat] " + m);
                    sleep(AUX.HeartbeatInterval);
                }
            }
        }.start();
    }

    /**
     * Sends beats to multicast group
     */
    void startScheduling() throws HostNotFoundException {
        new Process(host, host.getName()+"-gmScheduling") {
            public void main(String[] args) throws HostFailureException {
                while (true) {
                    scheduleVMs();
                    sleep(AUX.DefaultComputeInterval);
                }
            }
        }.start();
    }


    /**
     * Sends GM charge summary info to GL
     */
    void startSummaryInfoToGL() throws HostNotFoundException {
        new Process(host, host.getName()+"-gmSummaryInfoToGL") {
            public void main(String[] args) throws HostFailureException {
                while (true) {
                    summaryInfoToGL();
                    sleep(AUX.HeartbeatInterval);
                }
            }
        }.start();
    }

    /**
     * Sends GM charge summary info to GL
     */
    void summaryInfoToGL() {
        if (lcInfo.isEmpty()) return;
        updateChargeSummary();
        GMSumMsg.GMSum c = new GMSumMsg.GMSum(procSum, memSum);
        GMSumMsg m = new GMSumMsg(c, AUX.glInbox, host.getName(), null);
        m.send();
//        Logger.info("[GM.summaryInfoToGL] " + m);
    }

    /**
     * Updates charge summary based on local LC charge info
     */
    void updateChargeSummary() {
        int proc = 0;
        int mem = 0;
        int s = lcInfo.size();
        for(String lcHostname: lcInfo.keySet()) {
            LCInfo lci = lcInfo.get(lcHostname);
            proc += lci.charge.procCharge;
            mem += lci.charge.memUsed;
        }
        proc /= s; mem /= s;
        procSum = proc; memSum = mem;
//        Logger.info("[GM.updateChargeSummary] " + proc + ", " + mem);
    }



    void receiveHostQuery() {

    }

    void answerHostQuery() {

    }

    void receiveVMQuery() {

    }

    void answerVMQuery() {

    }

    void scheduleVMs() {

        Scheduler scheduler;
        long beginTimeOfCompute;
        long endTimeOfCompute;
        long computationTime;
        Scheduler.ComputingState computingState;
        double reconfigurationTime = 0;

        scheduler = new Entropy2RP((Configuration) Entropy2RP.ExtractConfiguration(this.getManagedXHosts()), -1);

        Logger.info("Size of the GM: "+this.getManagedXHosts().size());
        beginTimeOfCompute = System.currentTimeMillis();
        computingState = scheduler.computeReconfigurationPlan();
        endTimeOfCompute = System.currentTimeMillis();
//        computationTime = (endTimeOfCompute - beginTimeOfCompute);
        computationTime = AUX.EntropyComputationTime;

        try {
            Process.sleep(computationTime); // instead of waitFor that takes into account only seconds
        } catch (HostFailureException e) {
            e.printStackTrace();
        }

        Logger.info("Computation time (in ms):" + computationTime);
       // TODO Adrien
       // SimulatorManager.incEntropyComputationTime(computationTime;);

        if (computingState.equals(Scheduler.ComputingState.NO_RECONFIGURATION_NEEDED)) {
            Logger.info("Configuration remains unchanged");
            Trace.hostSetState(SimulatorManager.getServiceNodeName(), "SERVICE", "free");
        } else if (computingState.equals(Scheduler.ComputingState.SUCCESS)) {
            int cost = scheduler.getReconfigurationPlanCost();

			/* Tracing code */
            // TODO Adrien -> Adrien, try to consider only the nodes that are impacted by the reconfiguration plan
            for (XHost h : this.getManagedXHosts())
                Trace.hostSetState(h.getName(), "SERVICE", "reconfigure");

            Logger.info("Starting reconfiguration");
            double startReconfigurationTime = Msg.getClock() * 1000;
            scheduler.applyReconfigurationPlan();
            double endReconfigurationTime = Msg.getClock() * 1000;
            reconfigurationTime = endReconfigurationTime - startReconfigurationTime;
            Logger.info("Reconfiguration time (in ms): " + reconfigurationTime);
            // TODO Adrien
            //SimulatorManager.incEntropyReconfigurationTime(reconfigurationTime);

            Logger.info("Number of nodes used: " + SimulatorManager.getNbOfUsedHosts());

        } else {
            System.err.println("The resolver does not find any solutions - EXIT");
            // TODO Adrien
            //SimulatorManager.incEntropyNotFound();
            // TODO Adrien
            //Logger.info("Entropy has encountered an error (nb: " + SimulatorManager.getEntropyNotFound() + ")");
        }

		/* Tracing code */
        for (XHost h : SimulatorManager.getSGHosts())
            Trace.hostSetState(h.getName(), "SERVICE", "free");

        Trace.hostSetState(SimulatorManager.getServiceNodeName(), "SERVICE", "free");

    }


    void sendVMCommandsLC() {

    }

    /**
     * @return the collection of XHost managed by the GM
     */
    public Collection<XHost> getManagedXHosts() {
        Set<String> xhostNames =  lcInfo.keySet();
        LinkedList<XHost> xhosts = new LinkedList<XHost>();
        for (String xName: xhostNames){
            xhosts.add(SimulatorManager.getXHostByName(xName));
        }
        return xhosts;
    }

    /**
     * LC charge info (proc, mem, timestamp)
     */
    class LCCharge {
        double procCharge;
        int memUsed;
        double timeStamp;

        LCCharge(double proc, int mem, double ts) {
            this.procCharge = proc; this.memUsed = mem; this.timeStamp = ts;
        }
    }

    /**
     * LC-related info (charge info, heartbeat, timestamps)
     */
    class LCInfo {
        LCCharge charge;
        double heartbeatTimestamp;

        LCInfo(LCCharge c, double ts) {
            this.charge = c; this.heartbeatTimestamp = ts;
        }
    }
}
