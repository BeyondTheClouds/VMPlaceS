package scheduling.snooze.msg;

import scheduling.snooze.GroupManager;

/**
 * Created by sudholt on 14/07/2014.
 */
public class RBeatGMMsg extends SnoozeMsg {
    /**
     * Relay GL heartbeats to EPs, GMs and LCs
     * @param timestamp Timestamp
     * @param sendBox   Target mbox
     * @param origin    GL host
     * @param replyBox  null
     */
    public RBeatGMMsg(GroupManager gm, String sendBox, String origin, String replyBox) {
        super(gm, sendBox, origin, replyBox);
    }
}
