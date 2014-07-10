package scheduling.snooze;

import org.simgrid.msg.Task;
import scheduling.snooze.msg.SnoozeMsg;

import java.util.Date;

/**
 * Created by sudholt on 06/07/2014.
 */
public class AUX {
    static final String epInbox = "epInbox";                  // EP mbox
    static final String epGLElection = "epGLElection";        // EP mbox
    static final String glInbox = "glInbox";
    static final String glHeartbeatNew = "glHeartbeatNew";    // HeartbeatGroup mbox
    static final String glHeartbeatBeat = "glHeartbeatBeat";  // HeartbeatGroup mbox
    static final String glElection = "glElection";            // HeartbeatGroup mbox
    static final String gmHeartbeatNew = "gmHeartbeatNew";    // HeartbeatGroup mbox
    static final String gmHeartbeatBeat = "gmHeartbeatBeat";  // HeartbeatGroup mbox
    static final long HeartbeatInterval = 2500;
    static final long HeartbeatTimeout = 5000;
    static final long JoinAcknowledgementTimeout = 5000;
    static final long GLCreationTimeout = 2000;

    static SnoozeMsg arecv(String mbox) {
        if (Task.listen(mbox))
            try {
                return (SnoozeMsg) Task.receive(mbox);
            } catch (Exception e) {
                e.printStackTrace();
            }
        return null;
    }

    static long timeDiff(Date d) {
        long curTime = new Date().getTime();
        long oldTime = d.getTime();
        return curTime-oldTime;
    }
}
