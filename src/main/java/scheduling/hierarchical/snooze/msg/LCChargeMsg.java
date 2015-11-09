package scheduling.hierarchical.snooze.msg;

/**
 * Created by sudholt on 04/07/2014.
 */
public class LCChargeMsg extends SnoozeMsg {
    public LCChargeMsg(LCCharge lc, String sendBox, String origin, String replyBox) {
        super(lc, sendBox, origin, replyBox);
    }

    public static class LCCharge {
        private double procCharge;
        private int memUsed;
        private double timestamp;

        public LCCharge(double proc, int mem, double ts) {
            this.setProcCharge(proc); this.setMemUsed(mem); this.setTimestamp(ts);
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

        public double getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(double timestamp) {
            this.timestamp = timestamp;
        }
    }
}
