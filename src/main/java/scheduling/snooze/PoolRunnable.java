package scheduling.snooze;

import scheduling.snooze.msg.SnoozeMsg;

/**
 * Created by sudholt on 01/08/2014.
 */
abstract public class PoolRunnable implements Runnable {
    SnoozeMsg m;
    PoolRunnable(SnoozeMsg m) {
        this.m = m;
    }
}
