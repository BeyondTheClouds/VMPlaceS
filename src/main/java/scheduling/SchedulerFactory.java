package scheduling;

import configuration.SimulatorProperties;
import configuration.XHost;
import scheduling.btrplace.BtrPlaceRP;
import scheduling.entropy2.Entropy2RP;

import java.util.Collection;

/**
 * Created by mperocheau on 10/11/15.
 */
public class SchedulerFactory {

    public static Scheduler getScheduler(Collection<XHost> hostsToCheck, int loopID){
        switch (SimulatorProperties.getStrategy()) {
            case "btrplace":
                return new BtrPlaceRP(hostsToCheck, loopID);
            case "entropy2":
                return new Entropy2RP(hostsToCheck, loopID);
            default:
                return new BtrPlaceRP(hostsToCheck, loopID);
        }
    }

    public static Scheduler getScheduler(Collection<XHost> hostsToCheck){
        switch (SimulatorProperties.getStrategy()) {
            case "btrplace":
                return new BtrPlaceRP(hostsToCheck);
            case "configuration":
                return new Entropy2RP(hostsToCheck);
            default:
                return new BtrPlaceRP(hostsToCheck);
        }
    }
}
