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
    private Host host;
    private boolean thisGMStopped = false;
    private String glHostname;
    private Date   glTimestamp;
    private Hashtable<String, LCInfo> lcInfo;  // ConcurrentHashMap more efficient?
    // one mailbox per LC: lcHostname+"beat"
    private double procSum;
    private int    memSum;
    private String glSummary = "glSummary";
    private String inbox;
    private String gmHeartbeatNew = "gmHeartbeatNew";
    private String gmHeartbeatBeat = "gmHeartbeatBeat";
    private String lcCharge;
    private String myHeartbeat;

    GroupManager() {
        this.host = Host.currentHost();
        this.inbox = host.getName() + "gmInbox";
        this.myHeartbeat = host.getName() + "myHeartbeat";
        this.lcCharge = host.getName() + "lcCharge";
    }

    @Override
    public void main(String[] strings) throws MsgException {
        SnoozeMsg m = new NewGMMsg(host.getName(), AUX.gmHeartbeatNew, null, myHeartbeat);
        m.send();

        while (true) {
            handleInbox();
            if (glDead())      continue;
            if (thisGMStopped) break;
            updateLCCharge();
            recvLCBeats();
            deadLCs();
            summaryInfoToGL();
            beat();
            sleep(AUX.HeartbeatInterval);
        }
    }

    void handleInbox() {
        try {
            while (Task.listen(inbox)) {
                SnoozeMsg m = (SnoozeMsg) Task.receive(inbox);
                handle(m);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    void handle(SnoozeMsg m) { Logger.log("[GM.handle] Unknown message" + m); }

    /**
     * Integrate new LCs
     */
    void handle(NewLCMsg m) {
        String lcHostname = (String) m.getMessage();
        Date   ts  = new Date();
        // Init LC charge and heartbeat
        LCInfo    lci = new LCInfo(new LCCharge(0, 0, ts), ts);
        lcInfo.put(lcHostname, lci);
        // Send acknowledgment
        m = new NewLCMsg(host.getName(), m.getReplyBox(), null, null);
        m.send();
    }

    void handle(GMElecMsg m) {
        // Instantiate new GL
        GroupLeader gl = new GroupLeader();
        // Terminate GM
        thisGMStopped = true;
        // Acknowledge GL creation/GM termination
        m = new GMElecMsg(host.currentHost().getName(), AUX.glElection, null, null);
        m.send();
    }

    void handle(BeatGLMsg m) {
        if (glHostname != "" && glHostname != m.getMessage())
            Logger.log("[BM.handle(BeatGLMsg)] Different GLs: " + glHostname + ", " + m.getMessage());
        if (glHostname == "") glHostname = (String) m.getMessage(); // New GL
        glTimestamp = new Date();
    }

    boolean glDead() { return AUX.timeDiff(glTimestamp) > AUX.HeartbeatTimeout; }

    /**
     * Send join request to EP and wait for GroupLeader acknowledgement
     */
    void join() {
        // Send join request to EP
        NewGMMsg m = new NewGMMsg(host.getName(), AUX.epInbox, name, inbox);
        m.send();
        try {
            // Wait for GroupLeader acknowledgement
            m = (NewGMMsg) Task.receive(inbox, 2);
            glHostname = (String) m.getMessage();
        } catch (TimeoutException e) {
            Logger.log("[GM.join] No joining" + host.getName());
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Listen asynchronously for heartbeats from all known LCs
     */
    void recvLCBeats() {
        BeatLCMsg m = null;
        for (String lcHostname: lcInfo.keySet()) {
            String lcBeatBox = lcHostname+"lcBeat";
            m = (BeatLCMsg) AUX.arecv(lcBeatBox);
            lcInfo.put(lcHostname, new LCInfo(lcInfo.get(lcHostname).charge, new Date()));
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
                Logger.log("[deadLCs] LC " + lcHostname + "is dead");
            }
        }

        // Remove dead LCs
        for (String lcHostname: deadLCs) lcInfo.remove(lcHostname);
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
     * Sends GM charge summary info to GL
     */
    void summaryInfoToGL() {
        updateChargeSummary();
        GMSumMsg.GMSum c = new GMSumMsg.GMSum(procSum, memSum);
        GMSumMsg m = new GMSumMsg(c, glSummary, host.getName(), null);
        m.send();
    }

    /**
     * Sends beats to heartbeat group and LCs
     */
    void beat() {
        // Beat to heartbeat group
        BeatGMMsg m = new BeatGMMsg(host.getName(), gmHeartbeatBeat, null, null);
        m.send();

        // Beat to LCs
        for (String lc: lcInfo.keySet()) {
            m = new BeatGMMsg(host.getName(), gmHeartbeatBeat, null, null);
            m.send();
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
    }

    /**
     * Accepts asynchronously all pending LC charge messages, adds time stamps and updates lcInfo
     */
    void updateLCCharge() {
        while (Task.listen(glSummary)) {
            try {
                SnoozeMsg m = (SnoozeMsg) Task.receive(glSummary);
                m = (GMSumMsg) m;
                String lcHostname = (String) m.getOrigin();
                LCChargeMsg.LCCharge cs = (LCChargeMsg.LCCharge) m.getMessage();
                LCCharge newCharge = new LCCharge(cs.getProcCharge(), cs.getMemUsed(), new Date());
                Date oldBeat = lcInfo.get(lcHostname).heartbeatTimestamp;
                lcInfo.put(lcHostname, new LCInfo(newCharge, oldBeat));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

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
