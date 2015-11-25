package scheduling.entropyBased.btrplace;


import com.sun.org.apache.xalan.internal.lib.ExsltStrings;
import org.btrplace.plan.ReconfigurationPlan;
import scheduling.entropyBased.btrplace.configuration.Configuration;

import java.io.*;

/**
 * Created by joris on 24/11/15.
 */
public class BtrPlaceUtils {

    public static final String LOG_PATH_BASE = "logs/entropy/btrplace/";

    public static void log(Configuration configuration, String filePath) throws IOException {
        log (configuration.toString(), filePath);
    }

    public static void log(ReconfigurationPlan reconfigurationPlan, String filePath) throws IOException {
        log (reconfigurationPlan.toString(), filePath);
    }

    public static void print(Configuration configuration) {
        print(configuration.toString());
    }

    public static void print(ReconfigurationPlan reconfigurationPlan) {
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
