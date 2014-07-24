package scheduling.snooze;

import org.simgrid.msg.Msg;

/**
 * Created by sudholt on 29/06/2014.
 */
public class Logger {
    public static void err(String s) {
        Msg.info("ERRSNOO: " + s);
    }

    public static void info(String s) {
        //Msg.info("INFSNOO: " + s);
    }

    public static void log(Exception e) {
        Msg.info("EXCSNOO: ");
        e.printStackTrace(System.err);
    }
}