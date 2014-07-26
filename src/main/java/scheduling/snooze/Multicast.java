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
    private boolean glDead = false;
    private double lastPromotionOrElection;
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
        procRelayGLBeats();
        while (true) {
            try {
                SnoozeMsg m = (SnoozeMsg) Task.receive(inbox, AUX.ReceiveTimeout);
                handle(m);
                glDead();
                gmDead();
                sleep(AUX.DefaultComputeInterval);
            } catch (Exception e) {
                Logger.exc("[MUL.main] PROBLEM? Exception: " + e.getClass().getName());
                glDead();
                gmDead();
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
//            case "BeatGLMsg": handleBeatGL(m); break;
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

    void handleBeatGM(SnoozeMsg m) {
        String gm = m.getOrigin();
        if (gmInfo.get(gm) == null) {
            Logger.err("[MUL(BeatGM)] GM unknown: " + gm);
            return;
        }
        GMInfo oldGI = gmInfo.get(gm);
        gmInfo.put(gm, new GMInfo(oldGI.replyBox, (double) m.getMessage(), oldGI.joining));
//        Logger.info("[MUL(BeatGM)] To be relayed: " + m);
        relayGMBeats(gm);
    }

    void handleGLElec(SnoozeMsg m) {
//        Logger.info("[MUL(GLElecMsg)] " + m);

        if (AUX.timeDiff(lastPromotionOrElection) > AUX.HeartbeatTimeout || lastPromotionOrElection == 0
                || AUX.GLElectionForEachNewGM) {
           // No recent leaderElection
            leaderElection();
        } else {
            // Leader recently elected
            Logger.info("[MUL(GLElecMsg)] GL election on-going or recent: " + m);
        }
    }

    void handleNewGM(SnoozeMsg m) {
        String gm = (String) m.getMessage();
        gmInfo.put(gm, new GMInfo(AUX.gmInbox(gm), Msg.getClock(), true));
        if (!glHostname.isEmpty() ||
                (AUX.timeDiff(lastPromotionOrElection) >= AUX.HeartbeatTimeout
                        && lastPromotionOrElection == 0))
            return;
        boolean success = gmPromotion(gm);
        if (!success) Logger.err("[MUL(NewGM)] GM Promotion FAILED: " + gm);
        else {
            lastPromotionOrElection = Msg.getClock();
            Logger.info("[MUL(NewGM)] GM Promotion succeeded: " + gm);
        }
//        Logger.info("[MUL(NewGMMsg)] GM stored: " + m);
    }

    void handleNewLC(SnoozeMsg m) {
//        Logger.info("[MUL(NewLCMsg)] " + m);
        if (m.getMessage() == null) {
            // Add LC
            lcInfo.put(m.getOrigin(), new LCInfo(m.getOrigin(), "", Msg.getClock(), true));
            Logger.info("[MUL(NewLCMsg)] LC temp. joined: " + m);
        } else {
            // End LC join phase
            String lc = m.getOrigin();
            String gm = (String) m.getMessage();
            lcInfo.put(lc, new LCInfo(lc, gm, Msg.getClock(), false));
            m = new NewLCMsg(gm, m.getReplyBox(), null, null);
            m.send();
            Logger.info("[MUL(NewLCMsg)] LC integrated: " + m);
        }
    }

    void handleTermGM(SnoozeMsg m) {
        ArrayList<String> orphanLCs = new ArrayList<String>();

        String gm = (String) m.getMessage();
//        Logger.info("[MUL(TermGM)] GM, gmInfo: " + gm + ", " + gmInfo.get(gm));
        gmInfo.remove(gm);
        for (String lc: lcInfo.keySet()) {
            if (lcInfo.get(lc).equals(gm)) orphanLCs.add(lc);
        }
//        for (String lc: orphanLCs) {
//            lcInfo.remove(lc);
//            Logger.err("[MUL(TermGM)] LC: " + lc);
//        }
    }

    /**
     * GL dead
     */
    void glDead() {
        if (!glDead) return;
        Logger.err("[MUL.glDead] GL dead, trigger leader election: " + glHostname);
        leaderElection();
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
                    if (lcInfo.get(lc).gmHost.equals(gm)) orphanLCs.add(lc);
                }
            }
        }

        // Remove dead GMs and associated LCs
        for (String gm: deadGMs) {
            gmInfo.remove(gm);
            Logger.err("[MUL.gmDead] GM removed: " + gm);
//            leaderElection();
            Test.dispInfo();
        }
//        for (String lc: orphanLCs) {
//            lcInfo.remove(lc);
//            Logger.err("[MUL.gmDead] LC: " + lc);
//        }
    }

    boolean gmPromotion(String gm) {
        SnoozeMsg m;
        String elecMBox = AUX.gmInbox(gm) + "-MulticastElection";
        boolean success = false;
        // Send GL creation request to GM
        m = new GMElecMsg(null, AUX.gmInbox(gm), null, elecMBox);
        m.send();
//                Logger.info("[MUL.leaderElection] GM notified: " + m);

        boolean msgReceived = false;
        try {
            m = (SnoozeMsg) Task.receive(elecMBox, AUX.MessageReceptionTimeout);
//                    Logger.debug("[MUL.leaderElection] Msg.received for GM: " + gm + ", " + m);
        } catch (Exception e) {
            e.printStackTrace();
        }
        success =
                m.getClass().getSimpleName().equals("GLElecStopGMMsg") && gm.equals((String) m.getMessage());
//                Logger.info("[MUL.leaderElection] GM->GL: " + m);
        if (success) {
            glHostname = (String) m.getMessage();   // optimization
            glTimestamp = Msg.getClock();
            glDead = false;
            m = new GLElecStopGMMsg(name, AUX.gmInbox(gm), null, null);
            m.send();
//                    Logger.info("[MUL.leaderElection] New leader elected: " + m);
        } else Logger.err("[MUL.leaderElection] GM promotion failed: " + gm);

        return success;
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
            glHostname = gl.getHost().getName();  // optimization
            glTimestamp = Msg.getClock();
            glDead = false;
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
                Logger.info("[MUL.leaderElection] Round: " + i);
                gm = gms.get(i % gms.size());
                success = gmPromotion(gm);
                i++;
            } while (i<10 && !success);
            if (!success) {
                Logger.err("MUL(GLElec)] Leader election failed 10 times");
                return;
            } else lastPromotionOrElection = Msg.getClock();
            Logger.info("[MUL.leaderElection] Finished: " + glHostname + ", " + m);
        }
    }

    /**
     * Relay GL beats to EP, GMs and joining LCs
     */
    void relayGLBeats(SnoozeMsg m) {
        // Get timestamp
        String gl = (String) m.getOrigin();
        if ((glHostname == "" || glDead) && !gl.isEmpty()) {
            glHostname = gl;
            glDead = false;
            Logger.info("[MUL.relayGLBeats] GL initialized: " + glHostname);
        }
        if (glHostname != gl) {
            Logger.err("[MUL.relayGLBeats] Multiple GLs: " + glHostname + ", " + gl);
            return;
        }
        glTimestamp = (double) m.getMessage();

        // Relay GL beat to EP, GMs and LCs
        if (!glHostname.isEmpty()) {
            new RBeatGLMsg(glTimestamp, AUX.epInbox, glHostname, null).send();
//            Logger.info("[MUL.relayGLbeat] Beat relayed to: " + AUX.epInbox);
            for (String gm : gmInfo.keySet()) {
                m = new RBeatGLMsg(glTimestamp, AUX.gmInbox(gm)+"-glBeats", glHostname, null);
                m.send();
//                Logger.info("[MUL.relayGLbeats] Beat relayed to GM: " + m);
            }
            for (String lc : lcInfo.keySet()) {
                LCInfo lv = lcInfo.get(lc);
                if (lv.joining) {
                    m = new RBeatGLMsg(glTimestamp, AUX.lcInbox(lv.lcHost), glHostname, null);
                    m.send();
//                    Logger.info("[MUL.relayGLBeats] To LC: " + m);
                }
            }
        } else Logger.err("[MUL] No GL");
        Logger.info("[MUL.relayGLBeats] GL beat received/relayed: " + glHostname + ", " + glTimestamp);
    }

    /**
     * Relay GM beats to LCs
     */
    void relayGMBeats(String gm) {
        // Send to GL
        SnoozeMsg m = new RBeatGMMsg(gmInfo.get(gm).timestamp, AUX.glInbox(glHostname)+"-gmPeriodic", gm, null);
        m.send();
        Logger.info("[MUL.relayGMBeats] GL: " + m);

        // Send to LCs
        for (String lc: lcInfo.keySet()) {
            LCInfo lci = lcInfo.get(lc);
            if (lci != null) {
                String gmLc = lci.gmHost;
//            Logger.info("[MUL.relayGMBeats] LC: " + lc + ", GM: " + gm);
                if (gm.equals(gmLc)) {
                    GMInfo gmi = gmInfo.get(gm);
                    m = new RBeatGMMsg(gmi.timestamp, AUX.lcInbox(lc), gm, null);
                    m.send();
//                    Logger.info("[MUL.relayGMBeats] To LC: " + m);
                }
            }
        }
    }

    /**
     * Relays GL beats
     */
    void procRelayGLBeats() {
        try {
            new Process(host, host.getName() + "-relayGLBeats") {
                public void main(String[] args) {
                    while (true) {
                        try {
                            SnoozeMsg m = (SnoozeMsg) Task.receive(inbox + "-relayGLBeats", AUX.HeartbeatTimeout);
//                            Logger.info("[MUL.procRelayGLBeats] " + m);
                            relayGLBeats(m);
                            sleep(AUX.DefaultComputeInterval);
                        } catch (TimeoutException e) {
                            glDead = true;
                        } catch (Exception e) {
                            Logger.exc("[MUL.procRelayGLBeats] Exception: " + e.getClass().getName());
//                            e.printStackTrace();
                        }
                    }
                }
            }.start();
        } catch (Exception e) { e.printStackTrace(); }
    }


    class GMInfo {
        String replyBox;
        double timestamp;
        boolean joining;

        GMInfo(String rb, double ts, boolean joining) {
            this.replyBox = rb; this.timestamp = ts; this.joining = joining;
        }
    }

    class LCInfo {
        String lcHost;
        String gmHost;
        double timestamp;
        boolean joining;

        LCInfo(String lc, String gm, double ts, boolean joining) {
            this.lcHost = lc; this.gmHost = gm; this.timestamp = ts; this.joining = joining;
        }
    }
}
