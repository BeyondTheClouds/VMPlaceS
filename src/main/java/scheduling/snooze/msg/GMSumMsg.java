package scheduling.snooze.msg;

/**
 * Created by sudholt on 04/07/2014.
 */
public class GMSumMsg extends SnoozeMsg {
    public GMSumMsg(GMSum gms, String sendBox, String origin, String replyBox) {
        super(gms, sendBox, origin, replyBox);
    }

    public static class GMSum {
        double procCharge;
        int memUsed;

        public GMSum(double p, int m) {
            this.procCharge = p; this.memUsed = m;
        }
    }
}
