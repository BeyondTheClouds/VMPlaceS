package scheduling.entropyBased.common;

/**
 * Classe de résultat d'un Scheduler basée sur l'ancienne classe Entropy2RP.Entropy2RPRes
 *
 */
public class SchedulerResult {

    private int res; // 0 no reconfiguration needed, -1 no viable configuration, -2 reconfiguration plan aborted, 1 everything was ok
    private long duration; // in ms

    public SchedulerResult(){
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
