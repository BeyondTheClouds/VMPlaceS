package scheduling.snooze;

import org.simgrid.msg.Host;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;
import org.simgrid.msg.Task;
import scheduling.snooze.msg.*;

import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;

/**
 * Created by sudholt on 22/06/2014.
 */
public class HeartbeatGroup extends Process {
    private String glHostname = null;
    private Date   glTimestamp = null;
    private Hashtable<String, GMInfo> gmInfo = new Hashtable<String, GMInfo>();

    public void setGl(String gl) {
        glHostname = gl;
    }

    public void main(String[] args) throws MsgException {
        while (true) {
            newGL();
            recvGLbeat();
            leaderElection();
            relayGLbeat();
            newGMs();
            recvGMsBeat();
            deadGMs();
        }
    }

    protected HeartbeatGroup() {}

    /**
     * Register new GroupLeader and notify EP.
     */
    void newGL() {
        try {
            // Store GroupLeader
            NewGLMsg m = (NewGLMsg) Task.receive(AUX.glHeartbeatNew);
            if (glHostname == null) setGl((String) m.getMessage());
            else Logger.log("HeartbeatGroup:newGL, ERROR: 2nd GroupLeader" + m);
            // Notify EP
            m = new NewGLMsg((String) m.getMessage(), AUX.epInbox, null, null);
            m.send();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    void recvGLbeat() {
        try{
            BeatGLMsg m = (BeatGLMsg) AUX.arecv(AUX.glHeartbeatBeat);
            Logger.log(Host.currentHost().getName() + ": received " + m.getMessage());
            String gl = (String) m.getMessage();

            if (glHostname != null && glHostname != gl)
                Logger.log("[HeartbeatGroup] Err: multiple GLs");
        } catch (Exception e) {
            Logger.log(e);
        }
    }

    void relayGLbeat() {
        if (glHostname != "") {
            BeatGLMsg m;
            // Beat to EP
            m = new BeatGLMsg(glHostname, AUX.epInbox, null, null);
            m.send();
            // Beat to GMs
            for (String gm: gmInfo.keySet()) {
                m = new BeatGLMsg(glHostname, gm + "gmInbox", null, null);
            }
        }
    }

    void newGMs(){
        NewGMMsg m = (NewGMMsg) AUX.arecv(AUX.gmHeartbeatNew);
        gmInfo.put((String) m.getMessage(), new GMInfo(m.getReplyBox(), new Date()));
    }

    void recvGMsBeat() {
        try{
            while (Task.listen(AUX.gmHeartbeatBeat)) {
                BeatGMMsg m = (BeatGMMsg) Task.receive(AUX.gmHeartbeatBeat);
                String gm = (String) m.getMessage();
                gmInfo.put(gm, new GMInfo(gmInfo.get(gm).replyBox, new Date()));
            }
        } catch (Exception e) {
            Logger.log(e);
        }
    }

    /**
     * Identify dead GMs, remove them from gmInfo
     */
    void deadGMs() {
        HashSet<String> deadGMs = new HashSet<String>();
        for (String gmHostname : gmInfo.keySet()) {
            if (AUX.timeDiff(gmInfo.get(gmHostname).timestamp) > AUX.HeartbeatTimeout) {
                deadGMs.add(gmHostname);
                Logger.log("[HB.deadGMs] GM " + gmHostname + "is dead");
            }

            // Remove dead LCs
            for (String deadGM : deadGMs) gmInfo.remove(deadGM);
        }
    }

    /**
     * Election of a new GL: promote a GM, new GL instance
     */
    void leaderElection() {
        boolean electLeader = false;
        SnoozeMsg m = (GLElecMsg) AUX.arecv(AUX.glElection);
        if (m != null) {
            electLeader = true;
            Logger.logInfo("[HB.leaderElection] LGElecMsg arrived: " + m);
        }
        if (AUX.timeDiff(glTimestamp) > AUX.HeartbeatTimeout) {
            electLeader = true;
            Logger.logInfo("[HB.leaderElection] HB timeout for GL " + glHostname + "after " + glTimestamp);
        }

        if (!electLeader) return;

        if (gmInfo.isEmpty()) {
            GroupLeader gl = new GroupLeader();
            // TODO: deployment on the Heartbeat node! Where should it be deployed?
            glHostname = gl.getHost().getName();
        } else {
            // Leader election: take first GM, send promotion message
            String gm = null;
            for (String gk: gmInfo.keySet()) {
                gm = gk;
                break;
            }
            // Send GL creation request to GM
            m = new GMElecMsg(null, gm + "gmInbox", null, null);
            m.send();
            m = (SnoozeMsg) AUX.arecv(AUX.glElection);
            if (m instanceof GMElecMsg) {
                glHostname = (String) m.getMessage();   // glHostname == gm
                gmInfo.remove(gm);
            } else {
                // Ignore other messages on mbox AUX.glElection
                Logger.log("[HB.leaderElection] Waited for GMElecMsg, message ignored: " + m);
            }
        }
        m = new GLElecMsg(glHostname, AUX.epGLElection, null, null);
        m.send();
    }

    class GMInfo {
        String replyBox;
        Date timestamp;

        GMInfo(String rb, Date ts) {
            this.replyBox = rb; this.timestamp = ts;
        }
    }
}
