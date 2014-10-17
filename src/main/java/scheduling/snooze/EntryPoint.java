package scheduling.snooze;

import org.simgrid.msg.Host;
import org.simgrid.msg.Process;
import org.simgrid.msg.Task;
import scheduling.snooze.msg.SnoozeMsg;

/**
 * Created by sudholt on 22/06/2014.
 */
public class EntryPoint extends Process {
    private String name;
    private Host host;
    private String glHostname;
    private double glTimestamp;

    public EntryPoint(Host host, String name) {
        super(host, name);
        this.host = host;
        this.name = name;
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
//            case "NewGMMsg" : handleNewGM(m); break;
//            case "NewLCMsg" : handleNewLC(m); break;
            case "RBeatMsg" : handleRBeatGL(m); break;
            case "SnoozeMsg":
                Logger.err("[EP(SnoozeMsg)] Unknown message" + m);
                break;
        }
    }

    void handleRBeatGL(SnoozeMsg m) {
        String gl = (String) m.getOrigin();
        if (glHostname.equals("")) {
            glHostname = gl;
            Logger.debug("[EP(RBeatGLMsg)] GL initialized: " + gl);
        }
        else if (glHostname != gl) {
            glHostname = gl;
            Logger.err("[EP(RBeatGLMsg)] Multiple GLs: " + glHostname + ", " + gl);
        }
        glTimestamp = (double) m.getMessage();
    }

//    void handleNewGM(SnoozeMsg m) {
//        if (!glHostname.equals(""))
//            new NewGMMsg((String) m.getMessage(), AUX.glInbox(glHostname), m.getOrigin(), m.getReplyBox()).send();
//        else {
//            // Send failure message back
//            new NewGMMsg((String) m.getMessage(), m.getReplyBox(), name, AUX.epInbox).send();
//            Logger.err("[EP.handle] New GM without GroupLeader");
//        }
//    }
//
//    void handleNewLC(SnoozeMsg m) { // Join/rejoin LC
//        if (!glHostname.equals("")) {
//            new NewLCMsg((String) m.getMessage(), AUX.glInbox(glHostname), m.getOrigin(), m.getReplyBox()).send();
//        } else {
//            // Send failure message back
//            new NewLCMsg((String) m.getMessage(), m.getReplyBox(), name, AUX.epInbox).send();
//            Logger.err("[EP.handle] New LC without GroupLeader");
//        }
//    }
}