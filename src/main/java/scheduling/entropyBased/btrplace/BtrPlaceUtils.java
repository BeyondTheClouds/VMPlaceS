package scheduling.entropyBased.btrplace;

import org.btrplace.plan.ReconfigurationPlan;
import scheduling.entropyBased.btrplace.configuration.Configuration;

import java.io.*;

/**
 * Helper class for the Btrplace scheduler.
 *
 * @author Hadrien Gerard
 * @version 1.0
 */
public class BtrPlaceUtils {

    public static final String LOG_PATH_BASE = BtrPlaceProperties.getLogBasePath();

    public static final int MIN_TIME_LIMIT = BtrPlaceProperties.getMinTimeLimit();

    public static final int MAX_TIME_LIMIT = BtrPlaceProperties.getMaxTimeLimit();

    public static final boolean DO_OPTIMIZE = BtrPlaceProperties.doOptimize();

    public static final boolean DO_REPAIR = BtrPlaceProperties.doRepair();

    /**
     * Compute the timeout value for the solving process.
     * @param nbNodes the number of nodes (Hosts).
     * @return the timeout value
     */
    public static int getTimeLimit(int nbNodes)  {
        int timeLimit = Math.max(nbNodes / 8, MIN_TIME_LIMIT);
        timeLimit = Math.min(timeLimit, MAX_TIME_LIMIT);
        return timeLimit;
    }

    /**
     * Log the configuration in the given file.
     * @param configuration the Configuration instance
     * @param filePath the path to save the file
     * @throws IOException
     */
    public static void log(Configuration configuration, String filePath) throws IOException {
        log (configuration.toString(), filePath);
    }

    /**
     * Log the ReconfigurationPlan in the given file.
     * @param reconfigurationPlan the Configuration instance
     * @param filePath the path to save the file
     * @throws IOException
     */
    public static void log(ReconfigurationPlan reconfigurationPlan, String filePath) throws IOException {
        log (reconfigurationPlan.toString(), filePath);
    }

    /**
     * Print the configuration in the console
     * @param configuration the configuration instance
     */
    public static void print(Configuration configuration) {
        print("Configuration (BtrPlace): ");
        print(configuration.toString());
    }

    /**
     * Print the reconfiguration plan in the console
     * @param reconfigurationPlan the reconfiguration plan instance
     */
    public static void print(ReconfigurationPlan reconfigurationPlan) {
        print("ReconfigurationPlan (BtrPlace): ");
        print(reconfigurationPlan.toString());
       /*System.out.println("Time-based plan:");
        System.out.println(new TimeBasedPlanApplier().toString(reconfigurationPlan));
        System.out.println("\nDependency based plan:");
        System.out.println(new DependencyBasedPlanApplier().toString(reconfigurationPlan));*/
    }

    private static void print(String content) {
        System.out.println(content);
    }

    private static void log(String content, String filePath) throws IOException {
        File file = new File(filePath);
        file.getParentFile().mkdirs();
        PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
        pw.write(content.toString());
        pw.flush();
        pw.close();
    }


}
