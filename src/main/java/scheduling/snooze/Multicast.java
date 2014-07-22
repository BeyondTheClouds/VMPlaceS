package scheduling.snooze;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.snooze.msg.*;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * Created by sudholt on 13/07/2014.
 */
public class Multicast extends Process {
    private String name;
    private Host host;
    private String inbox;
    String glHostname;  //@ Make private
    private double glTimestamp;
    Hashtable<String, GMInfo> gmInfo = new Hashtable<String, GMInfo>();  //@ Make private
    Hashtable<String, LCInfo> lcInfo = new Hashtable<String, LCInfo>();  //@ Make private

    public Multicast(Host host, String name) {
        super(host, name);
        this.host = host;
        this.name = name;
        this.inbox = AUX.multicast;
        this.glHostname = "";
        AUX.multicastHost = host;
    }

    @Override
    public void main(String[] strings) {
        Test.multicast = this;
        while (true) {
            try {
                SnoozeMsg m = (SnoozeMsg) Task.receive(inbox);
                handle(m);
                glDead();
                gmDead();
                sleep(AUX.DefaultComputeInterval);
            } catch (Exception e) {
                e.printStackTrace();
            }


        }
    }

    /**
     * Receive and relay GL heartbeats
     * @param m
     */
    public void handle(SnoozeMsg m) {
     //   Logger.info("New message :" + m);
        String cs = m.getClass().getSimpleName();
        switch (cs) {
            case "BeatGLMsg": handleBeatGL(m); break;
            case "BeatGMMsg": handleBeatGM(m); break;
            case "GLElecMsg": handleGLElec(m); break;
            case "NewLCMsg" : handleNewLC(m);  break;
            case "NewGMMsg" : handleNewGM(m);  break;
            case "TermGMMsg": handleTermGM(m); break;
            case "SnoozeMsg":
                Logger.err("[MUL(SnoozeMsg)] Unknown message" + m);
                break;
        }
    }

    void handleBeatGL(SnoozeMsg m) {
        try {
            String gl = (String) m.getMessage();
            if (glHostname == "") {
                glHostname = gl;
                Logger.info("[MUL(BeatGL)] GL initialized: " + gl);
            }
            if (glHostname != gl) {
                Logger.err("[MUL(BeatGL)] Multiple GLs: " + glHostname + ", " + gl);
//                glHostname = gl;
            }
            glTimestamp = Msg.getClock();
            relayGLBeat();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void handleBeatGM(SnoozeMsg m) {
        String gm = (String) m.getMessage();
        if (gmInfo.get(gm) == null) {
            Logger.err("[MUL(BeatGM)] GM unknown: " + gm);
            return;
        }
        gmInfo.put(gm, new GMInfo(gmInfo.get(gm).replyBox, Msg.getClock()));
        relayGMBeats();
    }

    synchronized void handleGLElec(SnoozeMsg m) {
        if (AUX.timeDiff(glTimestamp) > AUX.HeartbeatTimeout || AUX.GLElectionForEachNewGM) {
           // GL dead
//            Logger.info("[MUL(GLElecMsg)] Start election: glTimestamp: " + glTimestamp + ", " + m);
            leaderElection();
        } else {
            // GL still alive
            m = new RBeatGLMsg(glTimestamp, m.getReplyBox(), glHostname, null);
            m.send();
            Logger.err("[MUL(GLElecMsg)] GL alive, resend: " + m);
        }
    }

    void handleNewGM(SnoozeMsg m) {
        String gm = (String) m.getMessage();
        gmInfo.put(gm, new GMInfo(AUX.gmInbox(gm), Msg.getClock()));
//        Logger.info("[MUL(NewGMMsg)] GM stored: " + m);
        m = new RBeatGLMsg(glTimestamp, m.getReplyBox(), glHostname, null);
        m.send();
//        Logger.info("[MUL(NewGMMsg)] GL beat sent: " + m);
    }

    void handleNewLC(SnoozeMsg m) {
//        Logger.info("[MUL(NewLCMsg)] " + m);
        if (!m.getOrigin().equals("removeLCjoinGM")) {
            // Add LC
            lcInfo.put((String) m.getMessage(), new LCInfo((String) m.getMessage(), "", Msg.getClock(), true));
            m = new RBeatGLMsg(glTimestamp, m.getReplyBox(), glHostname, null);
            m.send();
            Logger.info("[MUL(NewGMMsg)] LC stored, GL beat sent: " + m);
        } else {
            // End LC join phase
            lcInfo.put((String) m.getMessage(),
                    new LCInfo((String) m.getMessage(), m.getReplyBox(), Msg.getClock(), false));
            //            Logger.info("[MUL(NewLCMsg)] LC removed: " + m);
        }
    }

    void handleTermGM(SnoozeMsg m) {
        ArrayList<String> orphanLCs = new ArrayList<String>();

        String gm = (String) m.getMessage();
        gmInfo.remove(gm);
        Logger.err("[MUL(StopGM)] GM: " + gm + " stops");
        for (String lc: lcInfo.keySet()) {
            if (lcInfo.get(lc).equals(gm)) orphanLCs.add(lc);
        }
        for (String lc: orphanLCs) {
            lcInfo.remove(lc);
            Logger.err("[MUL(StopGM)] LC: " + lc);
        }
    }

    /**
     * GL dead
     */
    void glDead() {
        if (AUX.timeDiff(glTimestamp) > AUX.HeartbeatTimeout) {
            Logger.err("[MUL.glDead] GL dead, trigger leader election: " + glHostname);
            leaderElection();
        }
    }

    /**
     * GM dead
     */
    void gmDead() {
        ArrayList<String> deadGMs = new ArrayList<String>();
        ArrayList<String> orphanLCs = new ArrayList<String>();

        for (String gm: gmInfo.keySet()) {
            if (gmInfo.get(gm).timestamp == 0) {
//            Logger.err("[MUL.gmDead] GM: " + gm + " gmTimestamp == null");
                return;
            }
            if (AUX.timeDiff(gmInfo.get(gm).timestamp) > AUX.HeartbeatTimeout) {
                deadGMs.add(gm);

                // Identify LCs of dead GMs
                for (String lc: lcInfo.keySet()) {
                    if (lcInfo.get(lc).equals(gm)) orphanLCs.add(lc);
                }
            }
        }

        // Remove dead GMs and associated LCs
        for (String gm: deadGMs) {
            gmInfo.remove(gm);
            Logger.err("[MUL.gmDead] GM: " + gm + " dead; new leader to be elected");
            leaderElection();
            Test.dispInfo();
        }
        for (String lc: orphanLCs) {
            lcInfo.remove(lc);
            Logger.err("[MUL.gmDead] LC: " + lc);
        }
    }

    /**
     * Election of a new GL: promote a GM if possible, create new GL instance
     */
    void leaderElection() {
        if (gmInfo.isEmpty()) {
            // Ex-nihilo GL creation
            GroupLeader gl = new GroupLeader(Host.currentHost(), "groupLeader");
            try {
                gl.start();
            } catch (HostNotFoundException e) {
                e.printStackTrace();
            }
            Test.gl = gl;
            // Deployment on the Multicast node! Where should it be deployed?
            glHostname = gl.getHost().getName();
            Logger.err("[MUL.leaderElection] New leader ex-nihilo on: " + glHostname);
        } else {
            SnoozeMsg m = null;
            // Leader election: select GM, send promotion message
            ArrayList<String> gms = new ArrayList<String>(gmInfo.keySet());
            int i = 0;
            boolean success = false;
            String oldGL = "";
            String gm = "";
            do {
//                Logger.info("[MUL.leaderElection] Round: " + i);
                gm = gms.get(i % gms.size());
                String elecMBox = AUX.gmInbox(gm) + "-MulticastElection";

                // Send GL creation request to GM
                m = new GMElecMsg(null, AUX.gmInbox(gm), null, elecMBox);
                m.send();
//                Logger.info("[MUL.leaderElection] GM notified: " + m);

                boolean msgReceived = false;
                try {
                    m = (SnoozeMsg) Task.receive(elecMBox, AUX.MessageReceptionTimeout);
                    Logger.info("[MUL.leaderElection] Msg.received for GM: " + gm + ", " + m);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                msgReceived =
                        m.getClass().getSimpleName().equals("GLElecStopGMMsg") && gm.equals((String) m.getMessage());
//                Logger.info("[MUL.leaderElection] GM->GL: " + m);
                if (msgReceived) {
                    oldGL = glHostname;
                    glHostname = (String) m.getMessage();   // glHostname == gm
                    glTimestamp = Msg.getClock();
                    gmInfo.remove(gm);
                    success = true;
                    m = new GLElecStopGMMsg(name, AUX.gmInbox(gm), null, null);
                    m.send();
                    Logger.info("[MUL.leaderElection] New leader elected: " + m);
                } else {
                    // Ignore other messages on mbox AUX.glElection
                    Logger.err("[MUL.leaderElection] GM promotion failed: " + gm);
                }
                i++;
            } while (i<10 && !success);
            if (!success) {
                Logger.err("MUL(GLElec)] Leader election failed 10 times");
                return;
            }
        }
    }

    /**
     * Relay GL beats to EP, GMs and joining LCs
     */
    void relayGLBeat() {
        SnoozeMsg m = null;
        if (glHostname != "") {
            new RBeatGLMsg(glTimestamp, AUX.epInbox, glHostname, null).send();
//            Logger.info("[MUL.relayGLbeat] Beat relayed to: " + AUX.epInbox);
            for (String gm : gmInfo.keySet()) {
                m = new RBeatGLMsg(glTimestamp, gmInfo.get(gm).replyBox, glHostname, null);
                m.send();
//                Logger.info("[MUL.relayGLbeat] Beat relayed to GM: " + m);
            }
            for (String lc : lcInfo.keySet()) {
                LCInfo lv = lcInfo.get(lc);
                if (lv.join) {
                    m = new RBeatGLMsg(glTimestamp, AUX.lcInbox(lv.lcHost), glHostname, null);
                    m.send();
//                    Logger.info("[MUL.relayGLBeats] To LC: " + m);
                }
            }
        } else Logger.err("[MUL] No GL");
//        Logger.info("[MUL.relayGLbeat] GL beat received/relayed: " + glTimestamp);
    }

    /**
     * Relay GM beats to LCs
     */
    void relayGMBeats() {
        for (String lc: lcInfo.keySet()) {
            LCInfo lv = lcInfo.get(lc);
            String gm = lv.gmHost;
            GMInfo gv = gmInfo.get(gm);
            if (!lv.join && gv != null) {
                SnoozeMsg m = new RBeatGMMsg(gv.timestamp, AUX.lcInbox(lv.lcHost), gm, null);
                m.send();
//                Logger.info("[MUL.relayGMBeats] To LC: " + m);
            }
        }
    }

    class GMInfo {
        String replyBox;
        double timestamp;

        GMInfo(String rb, double ts) {
            this.replyBox = rb; this.timestamp = ts;
        }
    }

    class LCInfo {
        String lcHost;
        String gmHost;
        double timestamp;
        boolean join;

        LCInfo(String lc, String gm, double ts, boolean join) {
            this.lcHost = lc; this.gmHost = gm; this.timestamp = ts; this.join = join;
        }
    }
}
