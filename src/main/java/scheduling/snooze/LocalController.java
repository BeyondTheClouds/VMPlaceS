package scheduling.snooze;

/**
 * Created by sudholt on 25/05/2014.
 */

import configuration.XHost;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.snooze.msg.*;
import simulation.SimulatorManager;

public class LocalController extends Process {
    private String name;
    XHost host; //@ Make private
    private String gmHostname = "";
    private double gmTimestamp;
    private int procCharge = 0;
    private String inbox, joinMBox;
    private String lcCharge; // GM mbox
    private boolean joining = true;

    public LocalController (Host host, String name, String[] args) throws HostNotFoundException, NativeException  {
        super(host, name, args);
    }

    public void init(XHost host, String name)  {
        this.host = host;
        this.name = name;
        this.inbox = AUX.lcInbox(host.getName());
        this.joinMBox = inbox + "-join";
    }

    @Override
    public void main(String[] args) {
        Test.lcs.remove(this);
        Logger.debug("Start LC " + args[0] + ", " + args[1]);
        init(SimulatorManager.getXHostByName(args[0]), args[1]);
        join();
        Test.lcs.add(this);
        procSendMyBeats();
        procGMBeats();
        procLCChargeToGM();
        while (true) {
            try {
                SnoozeMsg m = (SnoozeMsg) Task.receive(inbox, AUX.ReceiveTimeout);
                handle(m);
                gmDead();
                sleep(AUX.DefaultComputeInterval);
            } catch (Exception e) {
                Logger.err("[LC.main] PROBLEM? Exception, " + host.getName() + ": " + e.getClass().getName());
                gmDead();
            }
        }
    }

    void handle(SnoozeMsg m) {
//        Logger.info("[LC.handle] LCIn: " + m);
        String cs = m.getClass().getSimpleName();
        switch (cs) {
            case "TermGMMsg" : handleTermGM(m); break;
            case "SnoozeMsg" :
                Logger.err("[GM(SnoozeMsg)] Unknown message" + m + " on " + host);
                break;
        }
    }

    /**
     * Stop LC activity and rejoin
     */
    void handleTermGM(SnoozeMsg m) {
        // TODO: stop LC activity
        Logger.err("[LC(TermGM)] GM DEAD, LC rejoins: " + m);
        join();
    }


    /**
     * GM dead: rejoin
     */
    void gmDead() {
        if (AUX.timeDiff(gmTimestamp) < AUX.HeartbeatTimeout || joining) return;
        Logger.err("[LC.gmDead] GM dead: " + gmHostname + ", " + gmTimestamp);
        join();
    }

    void join() {
        joining = true;
        Logger.tmp("[LC.join] Entry: " + gmHostname + ", TS: " + gmTimestamp);
        String gl, gm;
        boolean success = false;
        do {
            try {
                int i = 0;
                gl = getGL();
                if (gl.isEmpty()) continue;
                do {
                    gm = getGM(gl);
                    if (gm.isEmpty()) continue;
                    success = joinGM(gm);
                    i++;
                } while (!success && i<2);
                if (!success) continue;
                i=0;
                do { success = joinFinalize(gm); i++; } while (!success && i<4);
                if (!success) continue;
            } catch(Exception e) {
                Logger.err("[LC.join] Exception");
                e.printStackTrace();
                success = false;
            }
        } while (!success);
        joining = false;
        Logger.tmp("[LC.join] Success, GM: " + gmHostname + ", TS: " + gmTimestamp);
    }

    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    String getGL() {
        try {
            boolean success = false;
            SnoozeMsg m = null;
            String gl = "";
            // Join GL multicast group
            m = new NewLCMsg(null, AUX.multicast, host.getName(), joinMBox);
            m.send();
//            Logger.info("[LC.getGL] 1 Request sent: " + m);
            // Wait for GL beat
            int i = 1;
            do {
                m = (SnoozeMsg) Task.receive(inbox, AUX.HeartbeatTimeout);
                Logger.info("[LC.getGL] Round " + i + ": " + m);
                gl = (String) m.getOrigin();
                success = m.getClass().getSimpleName().equals("RBeatGLMsg") && !m.getOrigin().isEmpty();
            } while (!success);
            return gl;
//            Logger.info("[LC.getGL] 1 Got GL: " + m);
        } catch (Exception e) {
            Logger.exc("[LC.getGL] PROBLEM? Exception " + host.getName() + ": " + e.getClass().getName());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    String getGM(String gl) {
        try {
            // Send GM assignment request
            SnoozeMsg m = new LCAssMsg(host.getName(), AUX.glInbox(gl), host.getName(), joinMBox);
            m.send();
            Logger.info("[LC.getGM] Assignment message sent: " + m);

            // Wait for GM assignment
            m = (SnoozeMsg) Task.receive(joinMBox, AUX.MessageReceptionTimeout);
            if (!m.getClass().getSimpleName().equals("LCAssMsg")) return "";
            String gm = (String) m.getMessage();
            if (gm.isEmpty()) {
                Logger.err("[LC.getGM] Empty GM: " + m);
                return "";
            }
            Logger.info("[LC.getGM] GM assigned: " + m);
            return gm;
        } catch (Exception e) {
            Logger.exc("[LC.getGM] Exception " + host.getName() + ": " + e.getClass().getName());
            e.printStackTrace();
            return "";
        }
    }


    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    boolean joinGM(String gm) {
        try {
            // GM integration request
            SnoozeMsg m = new NewLCMsg(host.getName(), AUX.gmInbox(gm), name, joinMBox);
            m.send();
            Logger.info("[LC.joinGM] Integration message sent: " + m);
            m = (SnoozeMsg) Task.receive(joinMBox, AUX.MessageReceptionTimeout);
            if (!m.getClass().getSimpleName().equals("NewLCMsg")) {
                Logger.err("[LC.joinGM] No NewLC msg.: " + m);
                return false;
            }
            Logger.info("[LC.joinGM] Integrated by GM: " + m);
            return true;
        } catch (Exception e) {
            Logger.exc("[LC.joinGM] Exception " + host.getName() + ": " + e.getClass().getName());
            e.printStackTrace();
            return false;
        }
    }


    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    boolean joinFinalize(String gm) {
        try {
            // Leave GL multicast, join GM multicast group
            SnoozeMsg m = new NewLCMsg(gm, AUX.multicast, host.getName(), joinMBox);
            m.send();
            Logger.info("[LC.joinFinalize] GL->GM multicast: " + m);
            m = (SnoozeMsg) Task.receive(joinMBox, AUX.HeartbeatTimeout);
            if (!m.getClass().getSimpleName().equals("NewLCMsg")) return false;
            gm = (String) m.getMessage();
            if (gm.isEmpty()) {
                Logger.err("[LC.joinFinalize] 4 Empty GM: " + m);
                return false;
            }
            Logger.info("[LC.tryJoin] Ok GL->GM multicast: " + m);

            gmHostname = gm;
            gmTimestamp = Msg.getClock();

            Logger.info("[LC.joinFinalize] Finished, GM: " + gm + ", " + gmTimestamp);
            return true;
        } catch (Exception e) {
            Logger.exc("[LC.joinFinalize] PROBLEM? Exception " + host.getName() + ": " + e.getClass().getName());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Send LC beat to GM
     */
    void procGMBeats() {
        try {
            new Process(host.getSGHost(), host.getSGHost().getName() + "-gmBeats") {
                public void main(String[] args) throws HostFailureException {
                    SnoozeMsg m;
                    String gm;
                    while (true) {
                        try {
                            m = (SnoozeMsg) Task.receive(inbox + "-gmBeats", AUX.HeartbeatTimeout);
                            gm = (String) m.getOrigin();
                            if (gmHostname.isEmpty()) {
                                Logger.err("[LC.procGMBeats] No GM");
                                continue;
                            }
                            if (!gmHostname.equals(gm))   {
                                Logger.err("[LC.procGMBeats] Multiple GMs" + gmHostname + ", " + gm);
                                continue;  // Could be used for change of GM
                            }
                            gmTimestamp = (double) m.getMessage();
                            Logger.info("[LC.procGMBeats] " + gmHostname + ", TS: " + gmTimestamp);

                            sleep(AUX.HeartbeatInterval);
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            }.start();
        } catch (Exception e) { e.printStackTrace(); }
    }


    /**
     * Send LC beat to GM
     */
    void procSendMyBeats() {
        try {
            new Process(host.getSGHost(), host.getSGHost().getName() + "-lcBeats") {
                public void main(String[] args) throws HostFailureException {
                    String glHostname = host.getName();
                    while (true) {
                        try {
                            BeatLCMsg m = new BeatLCMsg(Msg.getClock(), AUX.gmInbox(gmHostname), host.getName(), null);
                            m.send();
                            Logger.info("[LC.beat] " + m);
                            sleep(AUX.HeartbeatInterval);
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            }.start();
        } catch (Exception e) { e.printStackTrace(); }
    }


    /**
     * Send LC beats to GM
     */
    void procLCChargeToGM() {
        try {
            final XHost h = host;
            new Process(host.getSGHost(), host.getSGHost().getName() + "-lcCharge") {
                public void main(String[] args) throws HostFailureException {
                    while (true) {
                        try {
                            LCChargeMsg.LCCharge lc = new LCChargeMsg.LCCharge(h.getCPUDemand(), h.getMemDemand());
                            LCChargeMsg m = new LCChargeMsg(lc, AUX.gmInbox(gmHostname), h.getName(), null);
                            m.send();
//                            Logger.info("[LC.startLCChargeToGM] Charge sent: " + m);
                            sleep(AUX.HeartbeatInterval);
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
            }.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    void totalHostCapacity() {
        HostCapacity hc = new HostCapacity(host.getCPUCapacity(), host.getMemSize());
    }

    void startVM() {

    }

    void shutdownVM() {

    }

    void migrateVM() {

    }

}
