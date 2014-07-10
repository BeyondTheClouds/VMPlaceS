package scheduling.snooze;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;
import org.simgrid.msg.Process;
import org.simgrid.msg.Task;
import scheduling.snooze.msg.*;

import java.util.Date;

/**
 * Created by sudholt on 22/06/2014.
 */
public class EntryPoint extends Process {
    private Host host;
    private String glHostname = "";
    private Date   glTimestamp;

    public EntryPoint(Host host, String processName) {
        super(host, processName);
        this.host = host;
    }

    public void main(String args[]) {
        while (true) {
            try {
                SnoozeMsg m = (SnoozeMsg) Task.receive(AUX.epInbox);
                Msg.info("I got a message or I'm stupid");
                handle(m);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void handle(SnoozeMsg m) {
        Logger.log("[EP.handle] Unknown message" + m);
    }

    void handle(NewGLMsg m) {
        glHostname = (String) m.getMessage();
    }

    void handle (NewGMMsg m) {
        if (glHostname != "") {
            NewGMMsg mGl = new NewGMMsg((String) m.getMessage(), AUX.glInbox, m.getOrigin(), m.getReplyBox());
            mGl.send();
        } else {
            leaderElection();
        }
    }

    void handle(NewLCMsg m) { // Join/rejoin LC
        if (glHostname != "") {
            NewLCMsg mGl = new NewLCMsg((String) m.getMessage(), AUX.glInbox, m.getOrigin(), m.getReplyBox());
            mGl.send();
        } else Logger.log("[EP.handle] New LC without GroupLeader");
    }

    void handle(BeatGMMsg m) {
        String gm = (String) m.getMessage();
        if      (glHostname == null) Logger.log("[EP.handle(BeatGLMsg)] No GL");
        else if (glHostname != gm)   Logger.log("[EP.handle(BeatGLMsg)] Multiple GLs");
        else glTimestamp = new Date();
    }

    void glDead() {
        if (AUX.timeDiff(glTimestamp) > AUX.HeartbeatTimeout) leaderElection();
    }

    void leaderElection() {
        GLElecMsg m = new GLElecMsg(null, AUX.glElection, null, AUX.epGLElection);
        m.send();
        try {
            m = (GLElecMsg) Task.receive(AUX.epGLElection, AUX.GLCreationTimeout);
            glHostname = (String) m.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}