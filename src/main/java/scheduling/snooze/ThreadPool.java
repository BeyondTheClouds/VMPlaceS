package scheduling.snooze;

import org.simgrid.msg.Host;
import org.simgrid.msg.HostNotFoundException;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;
import scheduling.snooze.msg.SnoozeMsg;

import java.util.LinkedList;

/**
 * Created by sudholt on 31/07/2014.
 */
public class ThreadPool {
    private final Process[] workerThreads;
    private final LinkedList<SnoozeMsg> workerQueue;
    private final String node;
    private final int numThreads;
    private final PoolRunnable runnable;

    ThreadPool(String node, PoolRunnable runnable, int numThreads) {
        this.node = node;
        this.numThreads = numThreads;
        this.runnable = runnable;
        workerQueue = new LinkedList<SnoozeMsg>();
        workerThreads = new Process[numThreads];

        int i = 0;
        for (Process p : workerThreads) {
            i++;
            p = new Worker(Host.currentHost(), "PoolProcess-" + i);
            try {
                p.start();
            } catch (HostNotFoundException e) {
                Logger.exc("[ThreadPool] HostNoFound");
//                e.printStackTrace();
            }
        }
    }

    public void addTask(SnoozeMsg m) {
        workerQueue.add(m);
    }

    private class Worker extends Process {
        Host host;
        String name;

        Worker(Host host, String name) {
            super(host, name);
            this.host = host;
            this.name = name;
        }

        @Override
        public void main(String[] strings) throws MsgException {
            while (true) {
                try {
                    SnoozeMsg m = workerQueue.remove();
                    runnable.run();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}