package scheduling.snooze.msg;

/**
 * Created by sudholt on 24/06/2014.
 */

public class NewLCMsg extends SnoozeMsg {
    public NewLCMsg(String hostName, String sendBox, String origin, String replyBox) {
        super(hostName, sendBox, origin, replyBox);
    }
}
