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
    private String inbox;
    private String lcCharge; // GM mbox
    private boolean joining = true;

    public LocalController (Host host, String name, String[] args) throws HostNotFoundException, NativeException  {
        super(host, name, args);
    }

    public void init(XHost host, String name)  {
        this.host = host;
        this.name = name;
        this.inbox = AUX.lcInbox(host.getName());
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
        Logger.info("[LC.join] Entry: " + gmHostname + ", TS: " + gmTimestamp);
        boolean success;
        do {
            try {
                success = tryJoin();
                if (!success) sleep(200); // TODO: to adapt
            } catch(Exception e) {
                Logger.err("[LC.join] Exception");
                e.printStackTrace();
                success = false;
            }
        } while (!success);
        joining = false;
        Logger.info("[LC.join] Success, GM: " + gmHostname + ", TS: " + gmTimestamp);
    }

    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    boolean tryJoin() {
        try {
            boolean success = false;
            String joinMBox = AUX.lcInbox(host.getName()) + "-join";
            SnoozeMsg m = null;
            String gl = "";
            // Join GL multicast group
            m = new NewLCMsg(null, AUX.multicast, host.getName(), joinMBox);
            m.send();
//            Logger.info("[LC.tryJoin] 1 Request sent: " + m);
            // Wait for GL beat
            int i = 1;
            do {
                m = (SnoozeMsg) Task.receive(inbox);
//                m = (SnoozeMsg) Task.receive(inbox, AUX.MessageReceptionTimeout);
                Logger.tmp("[LC.tryJoin] Round " + i + ": " + m);
                gl = (String) m.getOrigin();
                success = m.getClass().getSimpleName().equals("RBeatGLMsg") && !m.getOrigin().isEmpty();
            } while (!success);
            gl = m.getOrigin();
//            Logger.info("[LC.tryJoin] 1 Got GL: " + m);

            gmHostname = "";
            // Send GM assignment request
            m = new LCAssMsg(host.getName(), AUX.glInbox(gl), host.getName(), joinMBox);
            m.send();

            // Wait for GM assignment
            m = (SnoozeMsg) Task.receive(joinMBox, AUX.MessageReceptionTimeout);
            if (!m.getClass().getSimpleName().equals("LCAssMsg")) return false;
            String gm = (String) m.getMessage();
            if (gm.isEmpty()) {
                Logger.err("[LC.tryJoin] 2 Empty GM: " + m);
                return false;
            }
//            Logger.info("[LC.tryJoin] 2 GM assigned: " + m);

            // GM integration request
            m = new NewLCMsg(host.getName(), AUX.gmInbox(gm), name, joinMBox);
            m.send();
            m = (SnoozeMsg) Task.receive(joinMBox, AUX.MessageReceptionTimeout);
            if (!m.getClass().getSimpleName().equals("NewLCMsg")) {
                Logger.err("[LC.tryJoin] 3 No NewLC msg.: " + m);
                return false;
            }
//            Logger.info("[LC.tryJoin] 3 Integrated by GM: " + m);


            // Leave GL multicast, join GM multicast group
            m = new NewLCMsg(gm, AUX.multicast, host.getName(), joinMBox);
            m.send();
//            Logger.info("[LC.tryJoin] 4.1 GL->GM multicast: " + m);
            m = (SnoozeMsg) Task.receive(joinMBox, AUX.MessageReceptionTimeout);
            if (!m.getClass().getSimpleName().equals("NewLCMsg")) return false;
            gm = (String) m.getMessage();
            if (gm.isEmpty()) {
                Logger.err("[LC.tryJoin] 4 Empty GM: " + m);
                return false;
            }
//            Logger.info("[LC.tryJoin] 4 Ok GL->GM multicast: " + m);

            gmHostname = gm;
            gmTimestamp = Msg.getClock();

            Logger.tmp("[LC.tryJoin] Finished, GM: " + gm + ", " + gmTimestamp);
            return true;
        } catch (Exception e) {
            Logger.exc("Exception [LC.tryJoin] " + host.getName() + ": " + e.getClass().getName());
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
                            Logger.tmp("[LC.procGMBeats] " + gmHostname + ", TS: " + gmTimestamp);

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
                            Logger.tmp("[LC.beat] " + m);
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
