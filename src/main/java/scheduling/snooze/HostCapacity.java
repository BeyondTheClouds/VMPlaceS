package scheduling.snooze;

/**
 * Created by sudholt on 03/07/2014.
 */
public class HostCapacity {
    private double proc;
    private int mem;

    HostCapacity(double proc, int mem) {
        this.proc = proc; this.mem = mem;
    }

    public double getProc() {
        return proc;
    }

    public void setProc(int proc) {
        this.proc = proc;
    }

    public int getMem() {
        return mem;
    }

    public void setMem(int mem) {
        this.mem = mem;
    }
}
