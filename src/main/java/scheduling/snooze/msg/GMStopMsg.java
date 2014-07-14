package scheduling.snooze.msg;

/**
 * Created by sudholt on 04/07/2014.
 */
public class GMStopMsg extends SnoozeMsg {
    public GMStopMsg(String gm, String sendBox, String origin, String replyBox) {
        super(gm, sendBox, origin, replyBox);
    }
}
