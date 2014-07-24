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
        Logger.info("Start LC " + args[0] + ", " + args[1]);
        init(SimulatorManager.getXHostByName(args[0]), args[1]);
        join();
        Test.lcs.add(this);
        startBeats();
        startLCChargeToGM();
        while (true) {
            try {
                Msg.info(Host.currentHost().getName() + " not dead");
                SnoozeMsg m = (SnoozeMsg) Task.receive(inbox);
                handle(m);
                gmDead();
                sleep(AUX.DefaultComputeInterval);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void handle(SnoozeMsg m) {
//        Logger.info("[LC.handle] LCIn: " + m);
        String cs = m.getClass().getSimpleName();
        switch (cs) {
            case "RBeatGMMsg": handleBeatGM(m); break;
            case "TermGMMSg" : handleTermGM(m); break;
            case "SnoozeMsg" :
                Logger.err("[GM(SnoozeMsg)] Unknown message" + m + " on " + host);
                break;
        }
    }

    void handleBeatGM(SnoozeMsg m) {
        String gm = (String) m.getOrigin();
        if      (gmHostname.equals("")) Logger.err("[LC(BeatGMMsg)] No GM");
        else if (gmHostname != gm)   {
            Logger.err("[LC(BeatGMMsg)] Multiple GMs" + gmHostname + ", " + gm);
            gmHostname = gm;
        }
        else gmTimestamp = (double) m.getMessage();
//        Logger.info("[LC(BeatGMMsg)] GM " + gmHostname + " TS: " + gmTimestamp);
    }

    /**
     * Stop LC activity and rejoin
     */
    void handleTermGM(SnoozeMsg m) {
        // TODO: stop LC activity
        Logger.err("[LC(TermGM)] GM stopped, LC rejoins: " + m);
        join();
    }


    /**
     * GM dead: rejoin
     */
    void gmDead() {
        if (AUX.timeDiff(gmTimestamp) > AUX.HeartbeatTimeout) join();
    }

    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    void join() {
        try {
            boolean success = false;
            String joinMBox = AUX.lcInbox(host.getName()) + "-join";
            SnoozeMsg m = null;
            String gl = "";
            // Join GL multicast group
            m = new NewLCMsg(host.getName(), AUX.multicast, name, joinMBox);
            m.send();
            Logger.info("[LC.join] 1 Request sent: " + m);
            // Wait for GL beat
            m = (SnoozeMsg) Task.receive(joinMBox);
            gl = (String) m.getOrigin();
            Logger.info("[LC.join] 2 Got GL: " + gl);
            if (gl.equals("")) {
                Logger.err("[LC.join] Empty GL: " + m);
//                sleep(AUX.GLCreationTimeout);
                return;
            }
            gmHostname = "";
            // Send GM assignment req.
            m = new LCAssMsg(host.getName(), AUX.glInbox(gl), host.getName(), joinMBox);
            Logger.info("[LC.join] 4 GM ass. request: " + m);
            m.send();
            // Wait for GM assignment

            m = (SnoozeMsg) Task.receive(joinMBox, AUX.MessageReceptionTimeout);
            Logger.info("[LC.join] 5 Ass. msg.: " + m);
            if (m.getClass().getSimpleName().equals("LCAssMsg")) {
                String gm = (String) m.getMessage();
                if (gm.equals("")) {
                    Logger.err("[LC.join] Empty GM: " + m);
//                    sleep(AUX.GLCreationTimeout);
                    return;
                }
                gmHostname = gm;
                gmTimestamp = Msg.getClock();

                // Send GM integration request
                m = new NewLCMsg(host.getName(), AUX.gmInbox(gmHostname), name, joinMBox);
                Logger.info("[LC.join] 7 GM int.: " + m);
                m.send();

                // Leave GL multicast, join GM multicast group
                m = new NewLCMsg(host.getName(), AUX.multicast, "removeLCjoinGM", gmHostname);
                m.send();
                Logger.info("[LC.join] Finished: " + m);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Send LC beat to GM
     */
    void startBeats() {
        try {
            new Process(host.getSGHost(), host.getSGHost().getName() + "-lcBeats") {
                public void main(String[] args) throws HostFailureException {
                    String glHostname = host.getName();
                    while (true) {
                        try {
                            BeatLCMsg m = new BeatLCMsg(host.getName(), AUX.gmInbox(gmHostname), null, null);
                            m.send();
//                        Logger.info("[LC.beat] " + m);
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
    void startLCChargeToGM() {
        try {
            final XHost h = host;
            new Process(host.getSGHost(), host.getSGHost().getName() + "-lcCharge") {
                public void main(String[] args) throws HostFailureException {
                    while (true) {
                        try {
                            LCChargeMsg.LCCharge lc = new LCChargeMsg.LCCharge(h.getCPUDemand(), h.getMemDemand());
                            LCChargeMsg m = new LCChargeMsg(lc, AUX.gmInbox(gmHostname), h.getName(), null);
                            m.send();
//                    Logger.info("[LC.startLCChargeToGM] Charge sent: " + m);
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
