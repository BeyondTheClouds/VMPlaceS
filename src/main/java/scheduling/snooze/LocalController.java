package scheduling.snooze;

/**
 * Created by sudholt on 25/05/2014.
 */

import configuration.XHost;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.snooze.msg.*;
import simulation.SimulatorManager;

import java.net.UnknownHostException;
import java.util.Date;

public class LocalController extends Process {
    private String name;
    private XHost host;
    private String gmHostname;
    private Date gmBeatTimestamp = null;
    private int procCharge = 0;
    private String inbox;
    private String lcCharge; // GM mbox

    public LocalController (Host host, String name,String[] args) throws HostNotFoundException, NativeException  {

        super(host, name, args);

    }

    public void init(XHost host, String name)  {
        this.host = host;
        this.name = name;
        this.inbox = host.getName() + "-lcInbox";
    }

    @Override
    public void main(String[] args) throws MsgException {
        Logger.info("Start LC "+args[1]);
        init(SimulatorManager.getXHostByName(args[0]), args[1]);
        join();
        while (true) {
            try{
                SnoozeMsg m = (SnoozeMsg) Task.receive(inbox);
                handle(m);
                gmDead();
                beat();
                lcChargeToGM();
                sleep(AUX.HeartbeatInterval);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void handle(SnoozeMsg m) {
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
        if      (gmHostname == null) Logger.err("[LC(BeatGMMsg)] No GM");
        else if (gmHostname != gm)   Logger.err("[LC(BeatGMMsg)] Multiple GMs");
        else gmBeatTimestamp = (Date) m.getMessage();
//        Logger.info("[LC(BeatGMMsg)] GM " + gmHostname + " TS: " + gmBeatTimestamp);
    }

    /**
     * Stop LC activity and rejoin
     */
    void handleGMStop(SnoozeMsg m) {
        // TODO: stop LC activity
        rejoin();
    }

    /**
     * Send LC beat to GM
     */
    void beat() {
        BeatLCMsg m = new BeatLCMsg(host.getName(), AUX.gmInbox(gmHostname), null, null);
        m.send();
//        Logger.info("[LC.beat] " + m);
    }

    /**
     * GM dead: rejoin
     */
    void gmDead() {
        if (AUX.timeDiff(gmBeatTimestamp) > AUX.HeartbeatTimeout) {
            Logger.info("[LC.gmDead] GM dead: " + gmHostname);
            rejoin();
        }
    }

    void lcChargeToGM() {
        LCChargeMsg.LCCharge lc = new LCChargeMsg.LCCharge(host.getCPUDemand(), host.getMemDemand());
        LCChargeMsg m = new LCChargeMsg(lc, AUX.gmInbox(gmHostname), host.getName(), null);
        m.send();
//        Logger.info("[LC.lcChargeToGM] Charge sent: " + m);
    }

    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    void join() {
        SnoozeMsg m;
        // Join GL multicast group
        m = new NewLCMsg(host.getName(), AUX.multicast, name, inbox);
        m.send();
        Logger.info("[LC.join] Request sent: " + m);
        // Wait for GL beat
        do {
            try {
                m = (SnoozeMsg) Task.receive(inbox);
            } catch (Exception e) {
                e.printStackTrace();
            }
           // Logger.info("[LC.join] Ans: " + m);
        }
        while (!m.getClass().getSimpleName().equals("RBeatGLMsg"));
        String gl = m.getOrigin();
       // Logger.info("[LC.join] Got GL: " + gl);
        do {
            gmHostname = "";
            // Send GM assignment req.
            m = new LCAssMsg(host.getName(), AUX.glInbox, host.getName(), inbox);
            // Logger.info("[LC.join] GM ass. request: " + m);
            m.send();
            // Wait for GM assignment

                 try {
                    m = (SnoozeMsg) Task.receive(inbox);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Logger.info("[LC.join] Ass. msg.: " + m);
            if (m.getClass().getSimpleName().equals("LCAssMsg")) {
                String gm = (String) m.getMessage();
                gmHostname = gm;
            }
        } while (gmHostname.equals(""));
        Logger.info("[LC.join] GM assigned: " + m);

        // Send GM integration request
        m = new NewLCMsg(host.getName(), AUX.gmInbox(gmHostname), name, inbox);
        Logger.info("[LC.join] GM int.: " + m);
        m.send();

        // Wait for GM acknowledgement
        do {
            try {
                m = (SnoozeMsg) Task.receive(inbox);
            } catch (Exception e) {
                e.printStackTrace();
            }
//            Logger.info("[LC.join] Int. msg.: " + m);
        }
        while (!m.getClass().getSimpleName().equals("NewLCMsg"));
        Logger.info("[LC.join] Integration acknowledged: " + m);
        gmBeatTimestamp = new Date();

        // Leave GL, join GM multicast group
        m = new NewLCMsg(host.getName(), AUX.multicast, "removeLCjoinGM", gmHostname);
        m.send();
        Logger.info("[LC.join] Finished: LC removed, GM multicast joined: " + m);
    }

    void rejoin() {
        join();
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
