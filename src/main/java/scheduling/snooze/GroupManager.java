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
    private String name;
    Host host;
    private boolean thisGMToBeStopped = false;
    String glHostname = "";   //@ Make private
    private double glTimestamp;
    Hashtable<String, LCInfo> lcInfo = new Hashtable<String, LCInfo>();  //@ Make private
    // one mailbox per LC: lcHostname+"beat"
    private double procSum;
    private int memSum;
    private String glSummary = "glSummary";
    private String inbox;
    private String gmHeartbeatNew = "gmHeartbeatNew";
    private String gmHeartbeatBeat = "gmHeartbeatBeat";
    private Collection<XHost> managedLCs;

    public GroupManager(Host host, String name, String[] args) {
        super(host, name, args);
        this.host = host;
        this.name = name;
        this.inbox = AUX.gmInbox(host.getName());
    }

    @Override
    public void main(String[] strings) {
            Test.gms.remove(this);
            join();
            Test.gms.add(this);
            startBeats();
            startSummaryInfoToGL();
//        startScheduling();
            while (true) {
                try {
                    SnoozeMsg m = AUX.arecv(inbox);
                    if (m != null) handle(m);
                    if (!thisGMToBeStopped) {
                        glDead();
                        deadLCs();
                        sleep(AUX.DefaultComputeInterval);
                    } else {
                        Logger.err("[GM.main] GM stops: " + m);
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Logger.err("[GM.main] GM stopped: " + host.getName());
            Test.gms.remove(this);
//        sleep(2000);
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
            case "TermGMMsg":   stopThisGM(); break;
            case "SnoozeMsg":
                Logger.err("[GM(SnoozeMsg)] Unknown message" + m + " on " + host);
                break;

            case "TestFailGMMsg":
                Logger.err("[GM.main] Failure exit: " + host.getName());
                thisGMToBeStopped = true;
                break;
        }
    }

    /**
     * Listen asynchronously for heartbeats from all known LCs
     */
    void handleBeatLC(SnoozeMsg m) {
//        Logger.info("[GM(BeatLC)] " + m);
        String lc = (String) m.getMessage();
        LCInfo li = lcInfo.get(lc);
        LCCharge lcc = li != null ? li.charge : null;
        lcInfo.put(lc, new LCInfo(lcc, Msg.getClock()));
//        Logger.info("[GM(BeatLC)] " + lc + ", " + lcInfo.get(lc).charge + ", " + new Date());
    }

    void handleGMElec(SnoozeMsg m) {
        try {
            // Notify LCs and Multicast, stop this GM
            m = new GLElecStopGMMsg(host.getName(), m.getReplyBox(), null, null);
            m.send();
            Logger.info("[GM(GMElec)] Stop msg: " + m);
            do {
                m = (SnoozeMsg) Task.receive(inbox);
            } while (!m.getClass().getSimpleName().equals("GLElecStopGMMsg"));
//            try {
//                m = (SnoozeMsg) Task.receive(inbox, AUX.GLCreationTimeout);
//            } catch (TimeoutException e) {
//                Logger.info("[GM(GMElec)] No confirmation from MUL");
//            }

            m = new TermGLMsg(host.getName(), AUX.glInbox(glHostname), null, null);
            m.send();
            Logger.info("[GM(GMElec)] Old GL to be terminated: " + m);
            GroupLeader gl = new GroupLeader(Host.currentHost(), "groupLeader");
            gl.start();
            Test.gl = gl;
            glHostname = gl.getHost().getName();
            Logger.info("[GM(GMElec)] New leader created on: " + glHostname);
            stopThisGM();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void handleLCCharge(SnoozeMsg m) {
        try {
            String lcHostname = (String) m.getOrigin();
            if (lcHostname.equals("") || !lcInfo.contains(lcHostname)) return;
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
//        // Send acknowledgment
//        m = new NewLCMsg(host.getName(), AUX.lcInbox(lcHostname), null, null);
//        m.send();
////        Logger.info("[GM(NewLCMsg)] LC stored: " + m);
    }

    void handleRBeatGL(SnoozeMsg m) {
        String gl = (String) m.getOrigin();
//        Logger.info("[GM(RBeatGL)] Old, new ts: " + glTimestamp + ", " + m);
        if (!glHostname.equals(gl)) {
            Logger.err("[GM(RBeatGLMsg)] GL initialized or changed, join: " + glHostname + ", " + gl);
            join();
        } else {
            glTimestamp = (double) m.getMessage();
//            Logger.info("[GM(RBeatGL)] Updated: " + m);
        }
        //            Logger.info("[GM(RBeatGL)] TS updated: " + glTimestamp);
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
                deadLCs.add(lcHostname);
                Logger.err("[GM.deadLCs] " + lcHostname);
            }
        }
        // Remove dead LCs
        for (String lcHostname: deadLCs) lcInfo.remove(lcHostname);
    }

    /**
     * Identify dead GL, request election (not: wait for new GL)
     */
    void glDead() {
        if (AUX.timeDiff(glTimestamp) > AUX.HeartbeatTimeout) {
            glHostname = "";
            String glElecMBox = inbox + "-glElec";
            SnoozeMsg m = new GLElecMsg(host.getName(), AUX.multicast, null, glElecMBox);
            m.send();
            Logger.info("[GM.glDead] GL dead: " + m + ", TS: " + glTimestamp);
            try {
                m = (RBeatGLMsg) Task.receive(glElecMBox, AUX.MessageReceptionTimeout);
                glTimestamp = (double) m.getMessage();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Send join request to Multicast
     */
    void join() {
        String joinMBox = AUX.gmInbox(host.getName()) + "-join";
        boolean success = false;
        try {
            SnoozeMsg m = new NewGMMsg(host.getName(), AUX.multicast, null, joinMBox);
            m.send();
            do {
                m = (SnoozeMsg) Task.receive(joinMBox, AUX.MessageReceptionTimeout);
            } while (m == null || !m.getClass().getSimpleName().equals("RBeatGLMsg"));
//                Logger.info("[GM.join] MUL resp.: " + joinMBox + ", " + m);
            glHostname = m.getOrigin();
            // Wait for GroupLeader acknowledgement
            if (glHostname.equals("")) return;
            m = new NewGMMsg(host.getName(), AUX.glInbox(glHostname), null, joinMBox);
            m.send();
            m = (SnoozeMsg) Task.receive(joinMBox, AUX.MessageReceptionTimeout);
            glTimestamp = Msg.getClock();
            Logger.info("[GM.join] Finished: " + m);
            success = true;
            if (AUX.GLElectionForEachNewGM) {
                m = new GLElecMsg(host.getName(), AUX.multicast, null, null);
                m.send();
                Logger.info("[GM.join] Leader election: " + m);
            }
        } catch (Exception e) {
            Logger.err("[GM.join] No joining");
            e.printStackTrace();
        }
    }

    /**
     * Sends beats to multicast group
     */
    void startBeats() {
        try {
            new Process(host, host.getName() + "-gmBeats") {
                public void main(String[] args) throws HostFailureException {
                    while (!thisGMToBeStopped) {
                        BeatGMMsg m = new BeatGMMsg(host.getName(), AUX.multicast, null, null);
                        m.send();
//                    Logger.info("[GM.startBeats] " + m);
                        sleep(AUX.HeartbeatInterval);
                    }
                }
            }.start();
        } catch (Exception e) {e.printStackTrace(); }
    }

    /**
     * Sends beats to multicast group
     */
    void startScheduling() {
        try {
            new Process(host, host.getName() + "-gmScheduling") {
                public void main(String[] args) throws HostFailureException {
                    while (!thisGMToBeStopped) {
                        scheduleVMs();
                        sleep(1);  // TODO: to be adapted (removed?)
                    }
                }
            }.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends GM charge summary info to GL
     */
    void startSummaryInfoToGL() {
        try {
            new Process(host, host.getName() + "-gmSummaryInfoToGL") {
                public void main(String[] args) throws HostFailureException {
                    while (!thisGMToBeStopped) {
                        summaryInfoToGL();
                        sleep(AUX.HeartbeatInterval);
                    }
                }
            }.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Stop this GM gracefully
     */
    void stopThisGM() {
        try {
            SnoozeMsg m = new TermGMMSg(host.getName(), AUX.multicast, null, null);
            m.send();
            Logger.info("[GM.stopThisGM] GL notified: " + glHostname);
            for (String lc : lcInfo.keySet()) {
                m = new TermGMMSg(host.getName(), AUX.lcInbox(lc), null, null);
                m.send();
                Logger.info("[GM.stopThisGM] LC to rejoin: " + m);
            }
            thisGMToBeStopped = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends GM charge summary info to GL
     */
    void summaryInfoToGL() {
        if (lcInfo.isEmpty()) return;
        updateChargeSummary();
        GMSumMsg.GMSum c = new GMSumMsg.GMSum(procSum, memSum);
        if (!glHostname.equals("")) {
            GMSumMsg m = new GMSumMsg(c, AUX.glInbox(glHostname), host.getName(), null);
            m.send();
//        Logger.info("[GM.summaryInfoToGL] " + m);
        }
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
       // TODO Adrien + Add trace calls
       // SimulatorManager.incEntropyComputationTime(computationTime;);

        if (computingState.equals(Scheduler.ComputingState.NO_RECONFIGURATION_NEEDED)) {
            Logger.info("Configuration remains unchanged");
            Trace.hostSetState(SimulatorManager.getInjectorNodeName(), "SERVICE", "free");
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
        for (XHost h : SimulatorManager.getSGHostingHosts())
            Trace.hostSetState(h.getName(), "SERVICE", "free");

        Trace.hostSetState(SimulatorManager.getInjectorNodeName(), "SERVICE", "free");

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
