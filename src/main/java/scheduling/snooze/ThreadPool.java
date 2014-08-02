package scheduling.snooze;

import org.simgrid.msg.Host;
import org.simgrid.msg.HostNotFoundException;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;
import scheduling.snooze.msg.SnoozeMsg;

import java.lang.reflect.Constructor;

/**
 * Created by sudholt on 31/07/2014.
 */
public class ThreadPool {
    private final Process[] workers;
    private final int numThreads;
    private String runClass;

    ThreadPool(Object owner, String runClass, int numThreads) {
        this.numThreads = numThreads;
        workers = new Process[numThreads];

        int i = 0;
        for (Process w: workers) {
            i++;
            w = new Worker(Host.currentHost(), "PoolProcess-" + i, owner, runClass);
            try {
                w.start();
                Logger.tmp("[ThreadPool] Worker created: " + i + ", " + Host.currentHost()
                        + ", " + owner.getClass().getSimpleName() + ", " + runClass);
            } catch (HostNotFoundException e) {
                Logger.exc("[ThreadPool] HostNoFound");
//                e.printStackTrace();
            }
        }
    }

    private class Worker extends Process {
        Host host;
        String name;
        SnoozeMsg m;
        String mbox;
        Object owner;
        String runClassName;

        Worker(Host host, String name, Object owner, String runClass) {
            super(host, name);
            this.host = host;
            this.name = name;
            this.owner = owner;
            this.runClassName = runClass;
        }

        @Override
        public void main(String[] strings) throws MsgException {
            while (true) {
                try {
                    Class runClass = Class.forName(runClassName);
                    Constructor<?> constructor = runClass.getDeclaredConstructors()[0];
                    constructor.setAccessible(true);
                    Runnable r = (Runnable) constructor.newInstance(owner);
                    Logger.tmp("[ThreadPool.Worker.main] : " + r);
                    r.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}