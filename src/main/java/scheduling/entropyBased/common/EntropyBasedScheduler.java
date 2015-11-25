package scheduling.entropyBased.common;

import configuration.XHost;
import scheduling.Scheduler;

import java.util.Collection;

/**
 * Interface EntropyBasedScheduler : Représentent un scheduler basé sur Entropy
 *
 * @author Hadrien Gerard
 * @version 1.0
 */
public interface EntropyBasedScheduler extends Scheduler {

    void initialise(Collection<XHost> hostsToCheck);

    void initialise(Collection<XHost> hostsToCheck, int loopID);

    SchedulerResult checkAndReconfigure(Collection<XHost> hostsToCheck);
}
