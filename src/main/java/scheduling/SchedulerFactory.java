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

    public static Scheduler getScheduler(Collection<XHost> hostsToCheck, int loopID) {
        String strategy = SimulatorProperties.getStrategy();
        if(hotSwap(strategy)){
            try {
                return (Scheduler) Class.forName(strategy).getConstructor(Collection.class, Integer.class).newInstance(hostsToCheck,loopID);
            } catch (ClassNotFoundException e) {
                System.err.println("Unable to find class " + e.getMessage());
            } catch (InstantiationException e) {
                System.err.println("Unable to instantiate " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Exception " + e.getMessage());
            }
            return new BtrPlaceRP(hostsToCheck, loopID);
        } else {
            switch (SimulatorProperties.getStrategy()) {
                case "btrplace":
                    return new BtrPlaceRP(hostsToCheck, loopID);
                case "entropy2":
                    return new Entropy2RP(hostsToCheck, loopID);
                default:
                    return new BtrPlaceRP(hostsToCheck, loopID);
            }
        }
    }

    public static Scheduler getScheduler(Collection<XHost> hostsToCheck){
        String strategy = SimulatorProperties.getStrategy();
        if(hotSwap(strategy)){
            try {
                return (Scheduler) Class.forName(strategy).getConstructor(Collection.class).newInstance(hostsToCheck);
            } catch (ClassNotFoundException e) {
                System.err.println(e.getMessage());
            } catch (InstantiationException e) {
                System.err.println("Unable to instantiate " + e.getMessage());
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
            return new BtrPlaceRP(hostsToCheck);
        } else {
            switch (strategy) {
                case "btrplace":
                    return new BtrPlaceRP(hostsToCheck);
                case "configuration":
                    return new Entropy2RP(hostsToCheck);
                default:
                    return new BtrPlaceRP(hostsToCheck);
            }
        }
    }

    private static boolean hotSwap(String pathClass) {
        try {
            Class c = Class.forName(pathClass);
            boolean validInterface = false;
            for (Class i : c.getInterfaces()) {
                if (i.equals(Scheduler.class)) {
                    validInterface = true;
                    break;
                }
            }
            boolean validParent = false;
            if (c.getSuperclass().equals(AbstractScheduler.class)) {
                validParent = true;
            }
            if (!validInterface || !validParent) {
                System.err.println("The class must implement '" + Scheduler.class.getName() + "'");
                return false;
            } else return true;

        } catch (ClassNotFoundException e) {
//            System.err.println(e.getMessage());
        } catch (Exception e) {
//            System.err.println(e.getMessage());
        }
        return false;
    }
}
