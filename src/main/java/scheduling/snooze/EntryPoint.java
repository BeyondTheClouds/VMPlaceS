package scheduling.snooze;

import org.simgrid.msg.Host;
import org.simgrid.msg.Process;
import org.simgrid.msg.Task;
import scheduling.snooze.msg.*;

import java.util.Date;

/**
 * Created by sudholt on 22/06/2014.
 */
public class EntryPoint extends Process {
    private Host host;
    private String glHostname;
    private Date   glTimestamp;

    public EntryPoint(Host host, String name) {
        super(host, name);
        this.host = host;
    }

    public void main(String args[]) {
        while (true) {
            try {
                SnoozeMsg m = (SnoozeMsg) Task.receive(AUX.epInbox);
//                Logger.info("[EP.main] " + m);
                handle(m);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void handle(SnoozeMsg m) {
        String cs = m.getClass().getSimpleName();
        switch (cs) {
            case "RBeatGLMsg":
                String gm = (String) m.getOrigin();
                if (glHostname == null) {
                    glHostname = gm;
                    Logger.info("[EP(RBeatGLMsg)] GL initialized: " + gm);
                }
                else if (glHostname != gm) Logger.err("[EP(RBeatGLMsg)] Multiple GLs: " + glHostname + ", " + gm);
                else glTimestamp = (Date) m.getMessage();
                break;
            case "SnoozeMsg":
                Logger.err("[EP(SnoozeMsg)] Unknown message" + m);
                break;
        }
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
        } else Logger.err("[EP.handle] New LC without GroupLeader");
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