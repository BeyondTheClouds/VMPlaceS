package scheduling.snooze.msg;

/**
 * Created by sudholt on 29/06/2014.
 */
public class BeatGMMsg extends SnoozeMsg {
    public BeatGMMsg(double ts, String sendBox, String origin, String replyBox) {
        super(ts, sendBox, origin, replyBox);
    }
}
