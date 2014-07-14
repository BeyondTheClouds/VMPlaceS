package scheduling.snooze.msg;

/**
 * Created by sudholt on 04/07/2014.
 */
public class GMSumMsg extends SnoozeMsg {
    public GMSumMsg(GMSum gms, String sendBox, String origin, String replyBox) {
        super(gms, sendBox, origin, replyBox);
    }

    public static class GMSum {
        private double procCharge;
        private int memUsed;

        public GMSum(double p, int m) {
            this.setProcCharge(p); this.setMemUsed(m);
        }

        public double getProcCharge() {
            return procCharge;
        }

        public void setProcCharge(double procCharge) {
            this.procCharge = procCharge;
        }

        public int getMemUsed() {
            return memUsed;
        }

        public void setMemUsed(int memUsed) {
            this.memUsed = memUsed;
        }
    }
}
