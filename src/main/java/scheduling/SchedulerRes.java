package scheduling;

public class SchedulerRes {

    private int res; // 0 no reconfiguration needed, -1 no viable configuration, -2 reconfiguration plan aborted, 1 everything was ok
    private long duration; // in ms

    public SchedulerRes(){
        this.res = 0;
        this.duration = 0;
    }

    public void setRes(int res) {
        this.res = res;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public int getRes() {
        return res;
    }

}
