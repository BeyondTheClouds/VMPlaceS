package scheduling.hierarchical.snooze.msg;

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
        private int noLCs;
        private double ts;

        public GMSum(double p, int m, int noLCs, double ts) {
            this.setProcCharge(p); this.setMemUsed(m); this.setNoLCs(noLCs); this.ts = ts;
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

        public int getNoLCs() { return noLCs; }

        public void setNoLCs(int noLCs) { this.noLCs = noLCs; }

        public double getTs() { return ts; }

        public void setTs(double ts) { this.ts = ts; }
    }
}
