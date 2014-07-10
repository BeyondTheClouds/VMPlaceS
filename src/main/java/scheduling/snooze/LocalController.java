package scheduling.snooze;

/**
 * Created by sudholt on 25/05/2014.
 */

import configuration.XHost;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.snooze.msg.*;

import java.net.UnknownHostException;
import java.util.Date;

public class LocalController extends Process {

    private String name;
    private XHost host;
    private String gmHostname;
    private Date gmBeatTimestamp;
    private int procCharge = 0;
    private String inbox;
    private String lcCharge; // GM mbox
    private String lcBeat;   // GM mbox

    LocalController(String name, XHost host) throws UnknownHostException {
        this.name = name;
        this.host = host;
        this.inbox = host.getName() + "lcInbox";
    }

    @Override
    public void main(String[] args) throws MsgException {
        join();
        while (true) {
            try{
                handleInbox();
                gmDead();
                beat();
                sleep(AUX.HeartbeatInterval);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void handleInbox() {
        try {
            while (Task.listen(inbox)) {
                SnoozeMsg m = (SnoozeMsg) Task.receive(inbox);
                handle(m);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    void handle(SnoozeMsg m) { Logger.log("[GroupLeader.handle] Unknown message" + m); }

    void handle(BeatGMMsg m) {
        String gm = (String) m.getMessage();
        if      (gmHostname == null) Logger.log("[LC.handle(BeatGMMsg)] no GM");
        else if (gmHostname != gm)   Logger.log("[LC.handle(BeatGMMsg)] multiple GMs");
        else gmBeatTimestamp = new Date();
    }


    /**
     * Send join request to EP and wait for GroupManager acknowledgement
     */
    void join() {
        // Send join request to EP
        NewLCMsg m = new NewLCMsg(host.getName(), AUX.epInbox, name, inbox);
        m.send();
        try {
            // Wait for GroupManager acknowledgement
            m = (NewLCMsg) Task.receive(inbox, AUX.JoinAcknowledgementTimeout);
            gmHostname = (String) m.getMessage();
            lcCharge = gmHostname + "lcCharge";
            lcBeat = host.getName() + "lcBeat";
        } catch (TimeoutException e) {
            Logger.log("[LC.join] No joining" + host.getName());
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void rejoin() {
        join();
    }

    void totalHostCapacity() {
        HostCapacity hc = new HostCapacity(host.getCPUCapacity(), host.getMemSize());
    }

    void lcChargeToGM() {
        LCChargeMsg.LCCharge lc = new LCChargeMsg.LCCharge(host.getCPUDemand(), host.getMemDemand());
        LCChargeMsg m = new LCChargeMsg(lc, lcCharge, host.getName(), null);
        m.send();
    }

    void beat() {
        BeatLCMsg m = new BeatLCMsg(host.getName(), lcBeat, null, null);
        m.send();
    }

    void gmDead() {
        if (AUX.timeDiff(gmBeatTimestamp) > AUX.HeartbeatTimeout) rejoin();
    }

    void startVM() {

    }

    void shutdownVM() {

    }

    void migrateVM() {

    }

}
