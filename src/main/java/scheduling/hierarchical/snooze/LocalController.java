package scheduling.hierarchical.snooze;

/**
 * Created by sudholt on 25/05/2014.
 */

import configuration.XHost;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.hierarchical.snooze.msg.LCAssMsg;
import scheduling.hierarchical.snooze.msg.LCChargeMsg;
import scheduling.hierarchical.snooze.msg.NewLCMsg;
import scheduling.hierarchical.snooze.msg.SnoozeMsg;
import simulation.SimulatorManager;

public class LocalController extends Process {
    private String name;
    XHost host; //@ Make private
    private boolean thisLCToBeStopped = false;
    private String gmHostname = "";
    private double gmTimestamp;
    private int procCharge = 0;
    private String inbox, joinMBox;
    private String lcCharge; // GM mbox
    private boolean joining = true;

    public LocalController (Host host, String name, String[] args) throws HostNotFoundException {
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
        int n=0;

        try {
            // Let LCs wait for GM initialization
//            sleep(3000);
            Test.lcsCreated.remove(this);
            Logger.debug("Start LC " + args[0] + ", " + args[1]);
            init(SimulatorManager.getXHostByName(args[0]), args[1]);
            Test.lcsCreated.put(this.host.getName(), this);
            join();
            procSendLCChargeToHandleDeadGM();
            while (!stopThisLC()) {
                try {
                    SnoozeMsg m = (SnoozeMsg) Task.receive(inbox, AUX.durationToEnd());
                    handle(m);
//                    if (Task.listen(inbox)) handle((SnoozeMsg) Task.receive());
                    gmDead();
                    if (SnoozeProperties.shouldISleep()) sleep(AUX.DefaultComputeInterval);
                } catch (HostFailureException e) {
                    thisLCToBeStopped = true;
                    Logger.exc("[LC.main] HostFailureException");
                    break;
                } catch (TimeoutException e) {
                    gmDead();
                } catch (Exception e) {
                    String cause = e.getClass().getName();
                    Logger.err("[LC.main] PROBLEM? Exception: " + host.getName() + ": " + cause);
                    e.printStackTrace();
                    Logger.err("[LC.main] PROBLEM? Exception, " + host.getName() + ": " + e.getClass().getName());
                }
            }
            thisLCToBeStopped = true;
        } catch (HostFailureException e) {
            Logger.exc("[LC.main] HostFailureException");
            thisLCToBeStopped = true;
        }
        gmHostname = "";
        Logger.debug("[LC.main] LC stopped");
    }

    boolean stopThisLC() { return thisLCToBeStopped || SimulatorManager.isEndOfInjection(); }

    void handle(SnoozeMsg m) throws HostFailureException {
//        Logger.debug("[LC.handle] LCIn: " + m);
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
    void handleTermGM(SnoozeMsg m) throws HostFailureException {
        // TODO: stop LC activity
        Logger.err("[LC(TermGM)] GM DEAD, LC rejoins: " + m);
        join();
    }


    /**
     * GM dead: rejoin
     */
    void gmDead() throws HostFailureException {
        if (AUX.timeDiff(gmTimestamp) < AUX.HeartbeatTimeout || joining) return;
        Logger.err("[LC.gmDead] GM dead: " + gmHostname + ", " + gmTimestamp);
        gmHostname = "";
        join();
    }

    void join() throws HostFailureException {
        joining = true;
        Logger.info("[LC.join] Entry: " + gmHostname + ", TS: " + gmTimestamp);
        String gl, gm;
        boolean success = false;
        do {
            try {
                gl = getGL();
                if (gl.isEmpty()) continue;
                int i = 0;
                do {
                    gm = getGM(gl);
                    if (!gm.isEmpty()) success = joinGM(gm);
//                    if (gm.isEmpty()) continue;
//                    success = joinGM(gm);
                    i++;
                } while (!success && i < 3);
                if (!success) continue;
                i = 0;
                do {
                    success = joinFinalize(gm);
                    i++;
                } while (!success && i < 3);
                if (!success) continue;
            } catch (HostFailureException e) {
                throw e;
            } catch(Exception e) {
                Logger.err("[LC.join] Exception");
                e.printStackTrace();
                success = false;
            }
        } while (!success && !stopThisLC());
        if (!stopThisLC()) {
            joining = false;
            Test.noLCJoins++;
//        Test.lcsJoined.remove(this.host.getName());
            Test.removeJoinedLC(this.host.getName(), gmHostname, "[LC.join]");  // Should be superfluous
//        Test.lcsJoined.put(this.host.getName(), this);
            Test.putJoinedLC(this.host.getName(), this, gmHostname, "[LC.join]");
            Logger.imp("[LC.join] Finished, GM: " + gmHostname + ", TS: " + gmTimestamp);
        }
    }

    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    String getGL() throws HostFailureException {
        try {
            boolean success = false;
            SnoozeMsg m = null;
            String gl = "";
            // Join GL multicast group
            m = new NewLCMsg(null, AUX.multicast + "-newLC", host.getName(), joinMBox);
            m.send();
//            Logger.debug("[LC.getGL] 1 Request sent: " + m);
            // Wait for GL beat
            int i = 0;
            do {
                m = (SnoozeMsg) Task.receive(inbox, AUX.durationToEnd());
//                m = (SnoozeMsg) Task.receive(inbox, AUX.HeartbeatTimeout);
                i++;
                Logger.info("[LC.getGL] Round " + i + ": " + m);
                gl = (String) m.getOrigin();
                success = m.getClass().getSimpleName().equals("RBeatGLMsg") && !m.getOrigin().isEmpty();
            } while (!success && !stopThisLC());
            return gl;
//            Logger.info("[LC.getGL] 1 Got GL: " + m);
        } catch (TimeoutException e) {
            Logger.exc("[LC.getGL] PROBLEM? Exception " + host.getName() + ": " + e.getClass().getName());
            e.printStackTrace();
            return "";
        } catch (HostFailureException e) {
            throw e;
        } catch (Exception e) {
            Logger.exc("[LC.getGL] PROBLEM? Exception " + host.getName() + ": " + e.getClass().getName());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    String getGM(String gl) throws HostFailureException {
        try {
            // Send GM assignment request
            SnoozeMsg m = new LCAssMsg(host.getName(), AUX.glInbox(gl) + "-lcAssign", host.getName(), joinMBox);
            m.send();
            Logger.info("[LC.getGM] Assignment message sent: " + m);

            // Wait for GM assignment
            m = (SnoozeMsg) Task.receive(joinMBox, 5*AUX.MessageReceptionTimeout);
            if (!m.getClass().getSimpleName().equals("LCAssMsg")) return "";
            String gm = (String) m.getMessage();
            if (gm.isEmpty()) {
                Logger.err("[LC.getGM] Empty GM: " + m);
                return "";
            }
            Logger.imp("[LC.getGM] GM assigned: " + m);
            return gm;
        } catch (TimeoutException e) {
            Logger.exc("[LC.getGM] Exception " + host.getName() + ": " + e.getClass().getName());
            e.printStackTrace();
            return "";
        } catch (HostFailureException e) {
            throw e;
        } catch (Exception e) {
            Logger.exc("[LC.getGM] PROBLEM? Exception " + host.getName() + ": " + e.getClass().getName());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    boolean joinGM(String gm) throws HostFailureException {
        try {
            // GM integration request
            SnoozeMsg m = new NewLCMsg(host.getName(), AUX.gmInbox(gm) + "-newLC", name, joinMBox);
            m.send();
            Logger.info("[LC.joinGM] Integration message sent: " + m);
            m = (SnoozeMsg) Task.receive(joinMBox, 5*AUX.MessageReceptionTimeout);
            if (!m.getClass().getSimpleName().equals("NewLCMsg")) {
                Logger.err("[LC.joinGM] No NewLC msg.: " + m);
                return false;
            }
            Logger.imp("[LC.joinGM] Integrated by GM: " + m);
            return true;
        } catch (TimeoutException e) {
            Logger.exc("[LC.joinGM] Exception " + host.getName() + ": " + e.getClass().getName());
            e.printStackTrace();
            return false;
        } catch (HostFailureException e) {
            throw e;
        } catch (Exception e) {
            Logger.exc("[LC.joinGM] PROBLEM? Exception " + host.getName() + ": " + e.getClass().getName());
            e.printStackTrace();
            return false;
        }
    }


    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    boolean joinFinalize(String gm) throws HostFailureException {
        try {
            // Leave GL multicast, join GM multicast group
            SnoozeMsg m = new NewLCMsg(gm, AUX.multicast + "-newLC", host.getName(), joinMBox);
            m.send();
            Logger.info("[LC.joinFinalize] GL->GM multicast: " + m);
            m = (SnoozeMsg) Task.receive(joinMBox, 5 * AUX.MessageReceptionTimeout);
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
        } catch (HostFailureException e) {
            throw e;
        } catch (Exception e) {
            Logger.exc("[LC.joinFinalize] PROBLEM? Exception " + host.getName() + ": " + e.getClass().getName());
            e.printStackTrace();
            return false;
        }
    }

    void handleGMBeats(SnoozeMsg m) {
        String gm = (String) m.getOrigin();
        if (gmHostname.isEmpty()) {
            Logger.err("[LC.handleGMBeats] No GM: " + host.getName());
        }
        if (!gmHostname.equals(gm)) {
            Logger.err("[LC.handleGMBeats] Multiple GMs: " + host.getName() + ", " + gmHostname + ", " + gm);
        } else {
            gmTimestamp = Msg.getClock();
            Logger.info("[LC.handleGMBeats] " + host.getName() + ", "  + gmHostname + ", TS: " + gmTimestamp);
        }
    }

    /**
     * Send LC beats to GM
     */
    void procSendLCChargeToHandleDeadGM() {
        try {
            final XHost h = host;
            new Process(host.getSGHost(), host.getSGHost().getName() + "-lcCharge") {
                public void main(String[] args) {
                    int chargeCounter = 0;
                    while (!stopThisLC()) {
                        chargeCounter++;
                        try {
                            if (chargeCounter%4 == 0) {
                                LCChargeMsg.LCCharge lc = new LCChargeMsg.LCCharge(h.getCPUDemand(), h.getMemDemand(), Msg.getClock());
                                LCChargeMsg m = new LCChargeMsg(lc, AUX.gmInbox(gmHostname), h.getName(), null);
                                m.send();
                                Logger.info("[LC.procSendLCChargeToGM] Charge sent: " + m);
                            }
                            gmDead();
                            sleep(AUX.HeartbeatInterval*1000/4);
                        } catch (HostFailureException e) {
                            Logger.exc("[LC.procSendLCChargeToGM] HostFailureException");
                            thisLCToBeStopped = true;
                            break;
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
