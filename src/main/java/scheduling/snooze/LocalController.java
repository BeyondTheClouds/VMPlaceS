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
    private XHost host;
    private String gmHostname = "";
    private double gmTimestamp;
    private int procCharge = 0;
    private String inbox;
    private String lcCharge; // GM mbox

    public LocalController (Host host, String name,String[] args) throws HostNotFoundException, NativeException  {
        super(host, name, args);
    }

    public void init(XHost host, String name)  {
        this.host = host;
        this.name = name;
        this.inbox = AUX.lcInbox(host.getName());
    }

    @Override
    public void main(String[] args) throws MsgException {
//        Logger.info("Start LC " + args[0] + ", " + args[1]);
        init(SimulatorManager.getXHostByName(args[0]), args[1]);
        join();
        startBeats();
        startLCChargeToGM();
        while (true) {
            try{
                SnoozeMsg m = AUX.arecv(inbox);
                if (m != null) handle(m);
                gmDead();
                sleep(AUX.DefaultComputeInterval);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void handle(SnoozeMsg m) {
        Logger.info("[LC.handle] LCIn: " + m);
        String cs = m.getClass().getSimpleName();
        switch (cs) {
            case "RBeatGMMsg": handleBeatGM(m); break;
            case "GMStopMsg" : handleGMStop(m); break;
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
        Logger.info("[LC(BeatGMMsg)] GM " + gmHostname + " TS: " + gmTimestamp);
    }

    /**
     * Stop LC activity and rejoin
     */
    void handleGMStop(SnoozeMsg m) {
        // TODO: stop LC activity
        Logger.err("[LC.handleGMStop] GM stopped, LC rejoins: " + m);
        rejoin();
    }


    /**
     * GM dead: rejoin
     */
    void gmDead() {
        if (gmHostname.equals("")) {
//            Logger.err("[LC.gmDead] gmHostname == \"\"");
            return;
        }
        if (gmTimestamp == 0) {
//            Logger.err("[LC.gmDead] gmTimestamp == null");
            return;
        }
        if (AUX.timeDiff(gmTimestamp) > AUX.HeartbeatTimeout) {
            Logger.err("[LC.gmDead] GM dead: " + gmHostname);
            rejoin();
        }
    }

    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    void join() {
        SnoozeMsg m;
        // Join GL multicast group
        m = new NewLCMsg(host.getName(), AUX.multicast, name, inbox);
        m.send();
//        Logger.info("[LC.join] 1 Request sent: " + m);
        // Wait for GL beat
        do {
            try {
                m = (SnoozeMsg) Task.receive(inbox);
            } catch (Exception e) {
                e.printStackTrace();
            }
//           Logger.info("[LC.join] 2 Ans: " + m);
        }
        while (!m.getClass().getSimpleName().equals("RBeatGLMsg"));
        String gl = m.getOrigin();
//       Logger.info("[LC.join] 3 Got GL: " + gl);
        do {
            gmHostname = "";
            // Send GM assignment req.
            m = new LCAssMsg(host.getName(), AUX.glInbox(gl), host.getName(), inbox);
//            Logger.info("[LC.join] 4 GM ass. request: " + m);
            m.send();
            // Wait for GM assignment

                 try {
                    m = (SnoozeMsg) Task.receive(inbox);
                } catch (Exception e) {
                    e.printStackTrace();
                }
//                Logger.info("[LC.join] 5 Ass. msg.: " + m);
            if (m.getClass().getSimpleName().equals("LCAssMsg")) {
                String gm = (String) m.getMessage();
                gmHostname = gm;
                gmTimestamp = Msg.getClock();
            }
        } while (gmHostname.equals(""));
//        Logger.info("[LC.join] 6 GM assigned: " + m);

        // Send GM integration request
        m = new NewLCMsg(host.getName(), AUX.gmInbox(gmHostname), name, inbox);
//        Logger.info("[LC.join] 7 GM int.: " + m);
        m.send();

//        // Wait for GM acknowledgement
//        do {
//            try {
//                m = (SnoozeMsg) Task.receive(inbox);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
////            Logger.info("[LC.join] 8 Int. msg.: " + m);
//        }
//        while (!m.getClass().getSimpleName().equals("NewLCMsg"));
////        Logger.info("[LC.join] Integration acknowledged: " + m);
        gmTimestamp = Msg.getClock();

        // Leave GL multicast, join GM multicast group
        m = new NewLCMsg(host.getName(), AUX.multicast, "removeLCjoinGM", gmHostname);
        m.send();
        Logger.info("[LC.join] Finished: " + m);
    }

    void rejoin() {
        join();
    }

    /**
     * Send LC beat to GM
     */
    void startBeats() throws HostNotFoundException {
        new Process(host.getSGHost(), host.getSGHost().getName()+"-lcBeats") {
            public void main(String[] args) throws HostFailureException {
                String glHostname = host.getName();
                while (true) {
                    BeatLCMsg m = new BeatLCMsg(host.getName(), AUX.gmInbox(gmHostname), null, null);
                    m.send();
//                    Logger.info("[LC.beat] " + m);
                    sleep(AUX.HeartbeatInterval);
                }
            }
        }.start();
    }


    /**
     * Send LC beats to GM
     */
    void startLCChargeToGM() throws HostNotFoundException {
        final XHost h = host;
        new Process(host.getSGHost(), host.getSGHost().getName() + "-lcCharge") {
            public void main(String[] args) throws HostFailureException {
                while (true) {
                    LCChargeMsg.LCCharge lc = new LCChargeMsg.LCCharge(h.getCPUDemand(), h.getMemDemand());
                    LCChargeMsg m = new LCChargeMsg(lc, AUX.gmInbox(gmHostname), h.getName(), null);
                    m.send();
//                    Logger.info("[LC.startLCChargeToGM] Charge sent: " + m);
                    sleep(AUX.HeartbeatInterval);
                }
            }
        }.start();
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
