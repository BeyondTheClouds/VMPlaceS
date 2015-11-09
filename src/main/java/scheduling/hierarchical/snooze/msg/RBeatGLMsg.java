package scheduling.hierarchical.snooze.msg;

/**
 * Created by sudholt on 13/07/2014.
 */
public class RBeatGLMsg extends SnoozeMsg {
    /**
     * Relay GL heartbeats to EPs, GMs and LCs
     * @param timestamp Timestamp
     * @param sendBox   Target mbox
     * @param origin    GL host
     * @param replyBox  null
     */
    public RBeatGLMsg(double ts, String sendBox, String origin, String replyBox) {
        super(ts, sendBox, origin, replyBox);
    }
}
