package scheduling.snooze;

import org.simgrid.msg.Host;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;
import org.simgrid.msg.Task;
import scheduling.snooze.msg.*;

import java.util.Date;
import java.util.Hashtable;

/**
 * Created by sudholt on 25/05/2014.
 */
public class GroupLeader extends Process {
    private Host host;
    private Hashtable<String, GMSum> gmInfo = new Hashtable<String, GMSum>(); // ConcurrentHashMap more efficient
    private String glSummary = "glSummary";

    GroupLeader() {
        this.host = Host.currentHost();
    }

    @Override
    public void main(String[] strings) throws MsgException {
        SnoozeMsg m;
        m = new NewGLMsg(host.getName(), AUX.glHeartbeatNew, null, null);
        m.send();

        while (true) {
            handleInbox();
            updateSummaryInfo();
            beat();
            sleep(AUX.HeartbeatInterval);
        }
    }

    void handleInbox() {
        try {
            while (Task.listen(AUX.glInbox)) {
                SnoozeMsg m = (SnoozeMsg) Task.receive(AUX.glInbox);
                handle(m);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    void handle(SnoozeMsg m) { Logger.log("[GL.handle] Unknown message" + m); }

    /**
     * Join/rejoin LC: assign LC to least charged GroupManager
     */
    void handle(NewLCMsg m) {
        // identify least charged GroupManager
        String gmHost = "";
        double minCharge = 2, curCharge;
        GMSum cs;
        for (String s: gmInfo.keySet()) {
            cs        = gmInfo.get(s);
            curCharge = cs.procCharge + cs.memUsed;
            if (minCharge > curCharge) { minCharge = curCharge; gmHost = s; }
        };
        // relay message
        m = new NewLCMsg(gmHost, gmHost+"gmInbox", m.getOrigin(), m.getReplyBox());
        m.send();
    }

    /**
     * Join GM
     */
    void handle(NewGMMsg m) {
        String gmHostname = (String) m.getMessage();
        if (gmInfo.containsKey(gmHostname)) Logger.log("[GL.handle] GM " + gmHostname + " exists already");
        // Add GM
        GMSum cs = new GMSum(0, 0, new Date());
        gmInfo.put(gmHostname, cs);
        // Acknowledge integration
        m = new NewGMMsg((String) m.getMessage(), m.getReplyBox(), null, null);
        m.send();
    }

    void updateSummaryInfo() {
        // accepts all pending summary messages, adds time stamps and stores the entries in gmInfo
        while (Task.listen(glSummary)) {
            try {
                SnoozeMsg m = (SnoozeMsg) Task.receive(glSummary);
                m = (GMSumMsg) m;
                GMSum cs = (GMSum) m.getMessage();
                cs.timestamp = new Date();
                gmInfo.put(m.getOrigin(), cs);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void dispatchVMRequest() {

    }

    void assignLCToGM() {

    }

    /**
     * Beats to heartbeat group and EP
     */
    void beat() {
        String glHostname = host.getName();
        // Beat to HB
        BeatGLMsg m = new BeatGLMsg(glHostname, AUX.glHeartbeatBeat, null, null);
        m.send();
        // Beat to EP
        m = new BeatGLMsg(glHostname, AUX.epInbox, null, null);
        m.send();
        // Beat to GMs
        for (String gm: gmInfo.keySet()) {
            m = new BeatGLMsg(glHostname, gm + "gmInbox", null, null);
            m.send();
        }

    }

    /**
     * GM charge summary info
     */
    public class GMSum {
        double procCharge;
        int    memUsed;
        Date   timestamp;

        GMSum(double p, int m, Date ts) {
            this.procCharge = p; this.memUsed = m; this.timestamp = ts;
        }
    }
}
