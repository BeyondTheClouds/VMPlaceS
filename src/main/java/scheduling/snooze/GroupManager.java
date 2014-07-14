package scheduling.snooze;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.snooze.msg.*;

import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;

/**
 * Created by sudholt on 25/05/2014.
 */
public class GroupManager extends Process {
    private String name;
    private Host host;
    private boolean thisGMToBeStopped = false;
    private String glHostname = "";
    private Date glTimestamp;
    private Hashtable<String, LCInfo> lcInfo = new Hashtable<String, LCInfo>();  // ConcurrentHashMap more efficient?
    // one mailbox per LC: lcHostname+"beat"
    private double procSum;
    private int memSum;
    private String glSummary = "glSummary";
    private String inbox;
    private String gmHeartbeatNew = "gmHeartbeatNew";
    private String gmHeartbeatBeat = "gmHeartbeatBeat";

    public GroupManager(Host host, String name) {
        super(host, name);
        this.host = host;
        this.name = name;
        this.inbox = AUX.gmInbox(host.getName());
    }

    @Override
    public void main(String[] strings) throws MsgException {
        join();
        while (true) {
            SnoozeMsg m = (SnoozeMsg) Task.receive(inbox);
            handle(m);
            glDead();
            if (thisGMToBeStopped) break;
            deadLCs();
            summaryInfoToGL();
            beat();
//            sleep(AUX.HeartbeatInterval);
        }
        Logger.info("GM stopped: " + host.getName());
    }

    void handle(SnoozeMsg m) {
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
        // TODO: get all beat msgs on extra mbox
        String lc = (String) m.getMessage();
        lcInfo.put(lc, new LCInfo(lcInfo.get(lc).charge, new Date()));
//        Logger.info("[GM(BeatLC)] " + lc);
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
        // TODO: get all charge msgs on extra mbox
        try {
            String lcHostname = (String) m.getOrigin();
            LCChargeMsg.LCCharge cs = (LCChargeMsg.LCCharge) m.getMessage();
            LCCharge newCharge = new LCCharge(cs.getProcCharge(), cs.getMemUsed(), new Date());
            Date oldBeat = lcInfo.get(lcHostname).heartbeatTimestamp;
            lcInfo.put(lcHostname, new LCInfo(newCharge, oldBeat));
//            Logger.info("[GM(LCCharge] Charge updated: " + lcHostname);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void handleNewLC(SnoozeMsg m) {
        String lcHostname = (String) m.getMessage();
        Date   ts  = new Date();
        // Init LC charge and heartbeat
        LCInfo    lci = new LCInfo(new LCCharge(0, 0, ts), ts);
        lcInfo.put(lcHostname, lci);
        // Send acknowledgment
        m = new NewLCMsg(host.getName(), AUX.lcInbox(lcHostname), null, null);
        m.send();
//        Logger.info("[GM(NewLCMsg)] LC stored: " + m);
    }

    void handleRBeatGL(SnoozeMsg m) {
        String gl = (String) m.getOrigin();
//        Logger.info("[GM(RBeatGL)] Old, new GL: " + glHostname + ", " + gl);
        if (glHostname.equals("")) {
            glHostname = gl;
            join();
            Logger.info("[GM(RBeatGL)] GL initialized: " + gl + " on " + host);
        }
        else if (glHostname != gl) Logger.err("[GM(RBeatGLMsg)] Multiple GLs: " + glHostname + ", " + gl);
        else glTimestamp = (Date) m.getMessage();
    }

    /**
     * Sends beats to multicast group
     */
    void beat() {
        BeatGMMsg m = new BeatGMMsg(host.getName(), AUX.multicast, null, null);
        m.send();
//        Logger.info("[GM.beat] " + m);
    }

    /**
     * Identify dead GL, request election (not: wait for new GL)
     */
    void glDead() {
        if (AUX.timeDiff(glTimestamp) > AUX.HeartbeatTimeout) {
            glHostname = "";
            SnoozeMsg m = new GLElecMsg(host.getName(), AUX.multicast, null, null);
            m.send();
            // New GL will be initialized via BeatGLMsg
        }
    }


    /**
     * Identify and handle dead LCs
     */
    void deadLCs() {
        // Identify dead LCs
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
//            Logger.info("[GM.join] GL ack.: " + m);
            glHostname = (String) m.getMessage();
        } catch (TimeoutException e) {
            Logger.err("[GM.join] No joining" + host.getName());
            e.printStackTrace();
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

    }

    void sendVMCommandsLC() {

    }

    /**
     * LC charge info (proc, mem, timestamp)
     */
    class LCCharge {
        double procCharge;
        int memUsed;
        Date timeStamp;

        LCCharge(double proc, int mem, Date ts) {
            this.procCharge = proc; this.memUsed = mem; this.timeStamp = ts;
        }
    }

    /**
     * LC-related info (charge info, heartbeat, timestamps)
     */
    class LCInfo {
        LCCharge charge;
        Date heartbeatTimestamp;

        LCInfo(LCCharge c, Date ts) {
            this.charge = c; this.heartbeatTimestamp = ts;
        }
    }
}
