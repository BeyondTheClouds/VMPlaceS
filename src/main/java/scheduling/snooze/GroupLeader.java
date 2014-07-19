package scheduling.snooze;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.snooze.msg.*;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * Created by sudholt on 25/05/2014.
 */
public class GroupLeader extends Process {
    private Host host;
    private Hashtable<String, GMSum> gmInfo = new Hashtable<String, GMSum>(); // ConcurrentHashMap more efficient
    private String inbox;
    private boolean thisGLToBeTerminated = false;

    static enum AssignmentAlg { BESTFIT, ROUNDROBIN };
    private int roundRobin = 0;

    public GroupLeader(Host host, String name) {
        super(host, name);
        this.host = host;
        this.inbox = AUX.glInbox(host.getName());
    }

    @Override
    public void main(String[] strings) throws MsgException {
        startBeats();
        while (true) {
            SnoozeMsg m = AUX.arecv(inbox);
            if (m != null) handle(m);
            if (thisGLToBeTerminated) {
                Logger.err("[GL.main] TBTerminated: " + host.getName());
                break;
            }
            sleep(AUX.DefaultComputeInterval);
        }
    }

    void handle(SnoozeMsg m) {
//        Logger.info("[GL.handle] GLIn: " + m);
        String cs = m.getClass().getSimpleName();
        switch (cs) {
            case "LCAssMsg" : handleLCAss(m); break;
            case "GMSumMsg" : handleGMSum(m); break;
            case "NewGMMsg" : handleNewGM(m); break;
            case "TermGLMsg": thisGLToBeTerminated = true; break;
            case "SnoozeMsg":
                Logger.err("[GL(SnoozeMsg)] Unknown message" + m);
                break;
        }
    }

    void handleLCAss(SnoozeMsg m) {
        String gm = lcAssignment((String) m.getMessage());
        if (gm.equals("")) return;
        m = new LCAssMsg(gm, m.getReplyBox(), host.getName(), null);
        m.send();
//        Logger.info("[GL(LCAssMsg)] GM assigned: " + m);
    }

    void handleGMSum(SnoozeMsg m) {
        try {
            GMSumMsg.GMSum s = (GMSumMsg.GMSum) m.getMessage();
            GMSum sum = new GMSum(s.getProcCharge(), s.getMemUsed(), Msg.getClock());
            gmInfo.put(m.getOrigin(), sum);
//            Logger.info("[GL(GMSum)] " + m.getOrigin() + ": " + sum + ", " + m);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void handleNewGM(SnoozeMsg m) {
        String gmHostname = (String) m.getMessage();
        if (gmInfo.containsKey(gmHostname)) Logger.err("[GL.handle] GM " + gmHostname + " exists already");
        // Add GM
        GMSum gi = new GMSum(0, 0, Msg.getClock());
        gmInfo.put(gmHostname, gi);
        // Acknowledge integration
        m = new NewGMMsg((String) host.getName(), m.getReplyBox(), null, null);
//        Logger.info("[GL(NewGMMsg)] GM added: " + m);
        m.send();
    }

    /**
     * Beats to multicast group
     */
    String lcAssignment(String lc) {
        if (gmInfo.size()==0) return "";
        String gm = "";
        switch (AUX.assignmentAlg) {
            case BESTFIT:
                double minCharge = 2, curCharge;
                GMSum cs;
                for (String s : gmInfo.keySet()) {
                    cs = gmInfo.get(s);
                    curCharge = cs.procCharge;
                    if (minCharge > curCharge) {
                        minCharge = curCharge;
                        gm = s;
                    }
                }
//                Logger.info("[GL.lcAssignment] GM selected (BESTFIT): " + gm);
                break;
            case ROUNDROBIN:
                roundRobin = roundRobin % gmInfo.size(); // GMs may have died in the meantime
                ArrayList<String> gms = new ArrayList<>(gmInfo.keySet());
                gm = gms.get(roundRobin);
                roundRobin++;
//                Logger.info("[GL.lcAssignment] GM selected (ROUNDROBIN): " + gm);
                break;
        }
        return gm;
    }


    /**
     * Sends beats to multicast group
     */
    void startBeats() throws HostNotFoundException {
        new Process(host, host.getName()+"-glBeats") {
            public void main(String[] args) throws HostFailureException {
                String glHostname = host.getName();
                while (!thisGLToBeTerminated) {
                    BeatGLMsg m = new BeatGLMsg(glHostname, AUX.multicast, null, null);
                    m.send();
//                    Logger.info("[GL.beat] " + m);
                    sleep(AUX.HeartbeatInterval);
                }
            }
        }.start();
    }

    void dispatchVMRequest() {

    }

    void assignLCToGM() {

    }

    /**
     * GM charge summary info
     */
    public class GMSum {
        double procCharge;
        int    memUsed;
        double   timestamp;

        GMSum(double p, int m, double ts) {
            this.procCharge = p; this.memUsed = m; this.timestamp = ts;
        }
    }
}
