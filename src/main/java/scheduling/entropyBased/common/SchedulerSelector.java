package scheduling.entropyBased.common;

import configuration.SimulatorProperties;
import configuration.XHost;

import java.util.Collection;

/**
 * Classe permettant de charger automatiquement et "à chaud" le bon scheduler (en fonction de la classe precisée dans le
 * fichier de properties) et d'initialiser sa configuration.
 *
 * @author Hadrien Gerard
 * @version 1.0
 */
public class SchedulerSelector {

    private static final String requiredClassName = EntropyBasedScheduler.class.getCanonicalName();

    private SchedulerSelector() { }

    private static EntropyBasedScheduler createScheduler() {

        String schedulerClassName = SimulatorProperties.getScheduler();
        EntropyBasedScheduler scheduler = null;

        if (schedulerClassName.isEmpty()) {
            System.err.println("Expecting a class that inherit from " + requiredClassName + " in the simulator properties file.");
            System.exit(1);
        }

        try {

            Class c = Class.forName(schedulerClassName);

            if (!EntropyBasedScheduler.class.isAssignableFrom(c)) {
                System.err.println("The simulator class must implement '" + requiredClassName + "'");
                System.exit(1);
            }

            scheduler = (EntropyBasedScheduler) c.newInstance();

        } catch (ClassNotFoundException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (InstantiationException e) {
            System.err.println("Unable to instantiate Scheduler class:" + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

        System.out.println("Loaded Scheduler: " + schedulerClassName);
        return scheduler;
    }

    public static EntropyBasedScheduler createAndInitScheduler(Collection<XHost> hostsToCheck) {
        EntropyBasedScheduler scheduler = createScheduler();
        scheduler.initialize(hostsToCheck);
        return scheduler;
    }

    public static EntropyBasedScheduler createAndInitScheduler(Collection<XHost> hostsToCheck, int loopID) {
        EntropyBasedScheduler scheduler = createScheduler();
        scheduler.initialize(hostsToCheck, loopID);
        return scheduler;
    }

}
