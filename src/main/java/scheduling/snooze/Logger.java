package scheduling.snooze;

/**
 * Created by sudholt on 29/06/2014.
 */
public class Logger {
    public static void log(String s) {
        System.err.println("ERROR: " + s);
    }

    public static void logInfo(String s) {
        System.err.println("INFO: " + s);
    }

    public static void log(Exception e) {
        e.printStackTrace(System.err);
    }
}