package scheduling.snooze;

import configuration.XHost;
import entropy.configuration.Configuration;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.entropyBased.entropy2.Entropy2RP;
import scheduling.snooze.msg.*;
import simulation.SimulatorManager;

import java.util.*;

/**
 * Created by sudholt on 25/05/2014.
 */
public class GroupManager extends Process {
    private GroupManager thisGM;
    private String name;
    Host host;
    private boolean glDead = false;
    private boolean joining = true;
    private boolean scheduling = false;
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
    private ThreadPool newLCPool;

    public GroupManager(Host host, String name, String[] args) {
        super(host, name, args);
        this.host = host;
        this.name = name;
        this.inbox = AUX.gmInbox(host.getName());
    }

    @Override
    public void main(String[] strings) {
        thisGM = this;
        SnoozeMsg m = null;
        boolean success = false;
        int n = 1;

        Logger.info("[GM.main] GM started: " + host.getName());
        Test.gms.remove(this);

        procGLBeats();
        newJoin();
        while (true) {
            try {
                if (!thisGMToBeStopped) {
                    m = (SnoozeMsg) Task.receive(inbox, AUX.ReceiveTimeout);
                    handle(m);
                    deadLCs();
                    sleep(AUX.DefaultComputeInterval);
                } else break;
            } catch (HostFailureException e) {
                thisGMToBeStopped = true;
                break;
            } catch (Exception e) {
                String cause = e.getClass().getName();
                if (cause.equals("org.simgrid.msg.TimeoutException")) {
                    if (n % 10 == 0)
                        Logger.err("[GM.main] PROBLEM? 10 Timeout exceptions: " + host.getName() + ": " + cause);
                    n++;
                } else {
                    Logger.err("[GM.main] PROBLEM? Exception: " + host.getName() + ": " + cause);
                    e.printStackTrace();
                }
                deadLCs();
            }
        }
        Logger.err("[GM.main] GM stopped: " + host.getName() + ", " + m);
        Test.gms.remove(this);
    }

    void handle(SnoozeMsg m) throws HostFailureException {
//        Logger.info("[GM.handle] GMIn: " + m);
        String cs = m.getClass().getSimpleName();

        switch (cs) {
            case "BeatLCMsg":
                handleBeatLC(m);
                break;
            case "GMElecMsg":
                handleGMElec(m);
                break;
            case "LCChargeMsg":
                handleLCCharge(m);
                break;
            case "TermGMMsg":
                stopThisGM();
                break;
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
        Logger.info("[GM(BeatLC)] " + m);
        String lc = m.getOrigin();
        if (lcInfo.containsKey(lc)) {
            lcInfo.put(lc, new LCInfo(lcInfo.get(lc).charge, (double) m.getMessage()));
            Logger.info("[GM(BeatLC)] " + lc + ", " + lcInfo.get(lc).charge + ", " + lcInfo.get(lc).timestamp);
        } else Logger.err("[GM(BeatLC) Unknown LC] " + m);
    }

    void handleGMElec(SnoozeMsg m) throws HostFailureException {
        // This GM will be new leader
        String oldGL = glHostname;
        try {
            // Notify LCs and Multicast, stop this GM
            m = new GLElecStopGMMsg(host.getName(), m.getReplyBox(), null, null);
            m.send();
            Logger.debug("[GM(GMElec)] Stop msg: " + m);
            do {
                m = (SnoozeMsg) Task.receive(inbox);
            } while (!m.getClass().getSimpleName().equals("GLElecStopGMMsg"));
//            try {
//                m = (SnoozeMsg) Task.receive(inbox, AUX.GLCreationTimeout);
//            } catch (TimeoutException e) {
//                Logger.info("[GM(GMElec)] No confirmation from MUL");
//            }

            if (!oldGL.isEmpty()) {
                m = new TermGLMsg(host.getName(), AUX.glInbox(glHostname), null, null);
                m.send();
                Logger.info("[GM(GMElec)] Old GL to be terminated: " + m);
            }
            GroupLeader gl = new GroupLeader(Host.currentHost(), "groupLeader");
            gl.start();
            Test.gl = gl;
            glHostname = gl.getHost().getName();
            Logger.info("[GM(GMElec)] New leader created on: " + glHostname);

            stopThisGM();
        } catch (HostFailureException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void handleLCCharge(SnoozeMsg m) {
        try {
            String lcHostname = (String) m.getOrigin();
            if (lcHostname.equals("") || !lcInfo.containsKey(lcHostname)) return;
            LCChargeMsg.LCCharge cs = (LCChargeMsg.LCCharge) m.getMessage();
            LCCharge newCharge = new LCCharge(cs.getProcCharge(), cs.getMemUsed(), Msg.getClock());
            double oldBeat = lcInfo.get(lcHostname).timestamp;
            lcInfo.put(lcHostname, new LCInfo(newCharge, oldBeat));
//            Logger.info("[GM(LCCharge)] Charge updated: " + lcHostname + ", " + m);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class RunNewLC implements Runnable {
        public RunNewLC() {};

        @Override
        public void run() {
            NewLCMsg m;
            try {
                m = (NewLCMsg) Task.receive(inbox + "-newLC", AUX.HeartbeatTimeout);
//                            Logger.info("[GM.procRelayGLBeats] " + m);
                Logger.info("[GM.RunNewLC] " + m);
                String lc = (String) m.getMessage();
                double   ts  = Msg.getClock();
                // Init LC charge and heartbeat
                LCInfo    lci = new LCInfo(new LCCharge(0, 0, ts), ts);
                lcInfo.put(lc, lci);
                Logger.info("[GM.RunNewLC] LC stored: " + m);

                // Send acknowledgment
                m = new NewLCMsg(host.getName(), m.getReplyBox(), null, null);
                m.send();
            } catch (TimeoutException e) {
                Logger.exc("[GM.RunNewLC] PROBLEM? Timeout Exception");
            } catch (HostFailureException e) {
                return;
            } catch (Exception e) {
                Logger.exc("[GM.RunNewLC] Exception");
            }
        }
    }


//    void handleNewLC(SnoozeMsg m) {
//        String lc = (String) m.getMessage();
//        double   ts  = Msg.getClock();
//        // Init LC charge and heartbeat
//        LCInfo    lci = new LCInfo(new LCCharge(0, 0, ts), ts);
//        lcInfo.put(lc, lci);
//        Logger.info("[GM(NewLCMsg)] LC stored: " + m);
//
//        // Send acknowledgment
//        m = new NewLCMsg(host.getName(), m.getReplyBox(), null, null);
//        m.send();
//    }

    /**
     * Identify and handle dead LCs
     */
    void deadLCs() {
        if (lcInfo.isEmpty() || joining) return;
        // Identify dead LCs
        int no = lcInfo.size();
        HashSet<String> deadLCs = new HashSet<String>();
        for (String lcHostname: lcInfo.keySet()) {
            if (AUX.timeDiff(lcInfo.get(lcHostname).timestamp) > AUX.HeartbeatTimeout) {
                deadLCs.add(lcHostname);
                Logger.err("[GM.deadLCs] Identified: " + lcHostname);
            }
        }
        // Remove dead LCs
        lcInfo.keySet().removeAll(deadLCs);
//        for (String lcHostname: deadLCs) {
//            lcInfo.remove(lcHostname);
//            Logger.info("[GM.deadLCs] Removed: " + lcHostname);
//        }
    }

    void glBeats(SnoozeMsg m) {
        String gl = (String) m.getOrigin();
//        Logger.info("[GM.glBeats] " + glHostname + ", " + gl);
        if (!glHostname.equals(gl)) {
//            Logger.info("[GM.glBeats] GL initialized or changed: " + glHostname + ", " + gl);
            if (!gl.isEmpty()) {
                glHostname = m.getOrigin();
                Logger.info("[GM.glBeats] Updated: " + glHostname + ", " + m);
                NewGMMsg ms = new NewGMMsg(this, AUX.glInbox(glHostname) + "-newGM", null, null);
                ms.send();

                if (joining) {
                    procSendMyBeats();
                    procSendMyCharge();
                    procScheduling();
                    newLCPool = new ThreadPool(this, RunNewLC.class.getName(), 1);
                    Logger.info("[GM.glBeats] GM Join finished: " + m);
                    joining = false;
                    Test.gms.add(this);
                }
            }
        }
        glTimestamp = (double) m.getMessage();
    }

    void glDead() {
        if (glDead && !joining) {
            Logger.err("[GM.glDead] GL DEAD, promotion: " + glHostname + ", " + glTimestamp + ", " + Msg.getClock());
            glHostname = "";
            triggerGLPromotion();
        }
    }

    void newJoin() {
        SnoozeMsg m;
        joining = true;
        try {
            // Register at Multicast
            m = new NewGMMsg(this, AUX.multicast, null, null);
            m.send();

            // Trigger leader election
            if (AUX.GLElectionForEachNewGM) {
                String glElecMBox = inbox + "-glElec";
                m = new GLElecMsg(false, AUX.multicast, host.getName(), glElecMBox);
                m.send();
                Logger.debug("[GM.newJoin] GL election triggered: " + m);
            }
            Logger.debug("[GM.newJoin] Finished: " + m);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends beats to multicast group
     */
    void procGLBeats() {
        try {
            new Process(host, host.getName() + "-glBeats") {
                public void main(String[] args) throws HostFailureException {
                    while (!thisGMToBeStopped) {
                        try {
                            SnoozeMsg m = (SnoozeMsg)
                                    Task.receive(inbox + "-glBeats", AUX.HeartbeatTimeout);
                            glBeats(m);
                            sleep(AUX.DefaultComputeInterval);
                        } catch (TimeoutException e) {
                            if (!joining) {
                                glDead = true;
                                glDead();
                                Logger.exc("[GM.procGLBeats] Timeout: "
                                        + glHostname + ": " + glTimestamp + ", " + Msg.getClock());
                            }
                        } catch (HostFailureException e) {
                            thisGMToBeStopped = true;
                            break;
                        } catch (Exception e) {
                            Logger.exc("[GM.procGLBeats] Exception, " + host.getName() + ": " + e.getClass().getName());
//                            e.printStackTrace();
                        }
                    }
                }
            }.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends beats to multicast group
     */
    void procSendMyBeats() {
        try {
            new Process(host, host.getName() + "-relayGMBeats") {
                public void main(String[] args) throws HostFailureException {
                    while (!thisGMToBeStopped) {
                        try {
                            BeatGMMsg m = new BeatGMMsg(thisGM, AUX.multicast + "-relayGMBeats", host.getName(), null);
                            m.send();
                            Logger.info("[GM.procSendMyBeats] " + m);
                            sleep(AUX.HeartbeatInterval);
                        } catch (HostFailureException e) {
                            thisGMToBeStopped = true;
                            break;
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            }.start();
        } catch (Exception e) {e.printStackTrace(); }
    }

    /**
     * Sends GM charge summary info to GL
     */
    void procSendMyCharge() {
        try {
            new Process(host, host.getName() + "-gmCharge") {
                public void main(String[] args) throws HostFailureException {
                    while (!thisGMToBeStopped) {
                        try {
                            summaryInfoToGL();
                            sleep(AUX.HeartbeatInterval);
                        } catch (HostFailureException e) {
                            thisGMToBeStopped = true;
                            break;
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            }.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    static public boolean isOverloaded(Host host) {
//
//        int cpuConsumption = 0;
//
//        for (XVM vm : host.xhost.getRunnings()) {
//            cpuConsumption += vm.getCPUDemand();
//        }
//
////            LoggingActor.write(new CurrentLoadIs(Msg.getClock(), ref.getId()+"", cpuConsumption));
//
//        if (cpuConsumption > this.xhost.getCPUCapacity()) {
//            if (!violation_detected) {
//                // Monitor is considering that the node is overloaded
//                Msg.info(ref.getName() + " monitoring service: node is overloaded");
//                Trace.hostPushState(xhost.getName(), "PM", "violation-det");
//                violation_detected = true;
//            }
//
//            // Replace CpuViolationDetected() by a string
//            send(ref, "overloadingDetected");
//        } else if (cpuConsumption <= this.xhost.getCPUCapacity()) {
//            Trace.hostPushState(Host.currentHost().getName(), "PM", "normal");
//        }
//    }

    /**
     * Sends beats to multicast group
     */
    void procScheduling() {
        try {
            new Process(host, host.getName() + "-gmScheduling") {
                public void main(String[] args) throws HostFailureException {
                    long period = (SnoozeProperties.getSchedulingPeriodicity()*1000);
                    boolean periodicScheduling = SnoozeProperties.getSchedulingPeriodic();
                    Logger.info("[GM.procScheduling] periodicScheduling: " + periodicScheduling);

                    while (!thisGMToBeStopped) {
                        long wait = 0;
                        long previousDuration = 0;
                        boolean anyViolation = false;
                        try {
                            for (XHost h : getManagedXHosts()) if (!h.isViable()) anyViolation = true;
                            if ((periodicScheduling || (!periodicScheduling && anyViolation))
                                    && !scheduling && !glHostname.isEmpty() && !thisGMToBeStopped && !glDead) {
                                scheduling = true;
                                previousDuration = scheduleVMs();
                                wait = period - previousDuration;
                                scheduling = false;
                            }
                            if (periodicScheduling) { if (wait > 0) Process.sleep(wait); }
                            else Process.sleep(previousDuration+70); //
                        } catch (HostFailureException e) {
                            thisGMToBeStopped = true;
                            break;
                        } catch (Exception e) { e.printStackTrace(); }
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
            SnoozeMsg m = new TermGMMsg(host.getName(), AUX.multicast, null, null);
            Comm c = m.isend(AUX.multicast);
            c.waitCompletion();
            Test.gms.remove(this);
            Logger.info("[GM.stopThisGM] MUL notified: " + m);
            for (String lc : lcInfo.keySet()) {
                m = new TermGMMsg(host.getName(), AUX.lcInbox(lc), null, null);
                Logger.info("[GM.stopThisGM] LC to be notified: " + m);
                c = m.isend(AUX.lcInbox(lc));
                c.waitCompletion();
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
        if (lcInfo.isEmpty() || glHostname.isEmpty()) return;
        updateChargeSummary();
        GMSumMsg.GMSum c = new GMSumMsg.GMSum(procSum, memSum);
        if (!glHostname.isEmpty()) {
            GMSumMsg m = new GMSumMsg(c, AUX.glInbox(glHostname)+"-gmPeriodic", host.getName(), null);
            m.send();
//        Logger.info("[GM.summaryInfoToGL] " + m);
        }
    }

    void triggerGLPromotion() {
        String glElecMBox = inbox + "-glElec";
        SnoozeMsg m = new GLElecMsg(false, AUX.multicast, host.getName(), glElecMBox);
        m.send();
        Logger.debug("[GM.triggerGLPromotion] Finished: " + m);
    }

    /**
     * Updates charge summary based on local LC charge info
     */
    void updateChargeSummary() {
        int proc = 0;
        int mem = 0;
        int s = lcInfo.size();
        for (String lcHostname : lcInfo.keySet()) {
            LCInfo lci = lcInfo.get(lcHostname);
            if (lci == null) {
                proc += lci.charge.procCharge;
                mem += lci.charge.memUsed;
            }
        }
        proc /= s;
        mem /= s;
        procSum = proc;
        memSum = mem;
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

    long scheduleVMs() {

        /* Compute and apply the plan */
        Collection<XHost> hostsToCheck = this.getManagedXHosts();
        Entropy2RP scheduler = new Entropy2RP((Configuration) Entropy2RP.ExtractConfiguration(hostsToCheck));
        Entropy2RP.Entropy2RPRes entropyRes = scheduler.checkAndReconfigure(hostsToCheck);
        long previousDuration = entropyRes.getDuration();
        if (entropyRes.getRes() == 0) {
            Msg.info("Reconfiguration ok (duration: " + previousDuration + ")");
        } else if (entropyRes.getRes() == -1) {
            Msg.info("No viable solution (duration: " + previousDuration + ")");
            // TODO Mario, Please check where/how do you want to store numberOfCrash (i.e. when Entropy did not found a solution)
            // numberOfCrash++;
        } else { // res == -2 Reconfiguration has not been correctly performed
            Msg.info("Reconfiguration plan has been broken (duration: " + previousDuration + ")");
            // TODO Mario, please check where/how do you want to store numberOfBrokenPlan (i.e. when some nodes failures prevent to complete tha reconfiguration plan)
            //numberOfBrokenPlan++;
        }
        return previousDuration;
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
        double timestamp;

        LCInfo(LCCharge c, double ts) {
            this.charge = c; this.timestamp = ts;
        }
    }
}
