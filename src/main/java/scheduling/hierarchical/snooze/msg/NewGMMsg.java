package scheduling.hierarchical.snooze.msg;

import scheduling.hierarchical.snooze.GroupManager;

/**
 * Created by sudholt on 29/06/2014.
 */
public class NewGMMsg extends SnoozeMsg {
    public NewGMMsg(GroupManager gm, String sendBox, String origin, String replyBox) {
        super(gm, sendBox, origin, replyBox);
    }
}
