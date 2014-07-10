package scheduling.snooze.msg;

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

        public LCCharge(double proc, int mem) {
            this.setProcCharge(proc); this.setMemUsed(mem);
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
