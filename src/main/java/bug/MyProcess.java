package bug;

import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import simulation.SimulatorManager;

public class MyProcess extends Process {
    public MyProcess(Host host, String name, String[] args) throws NativeException, HostNotFoundException {
        super(host, name, args);
    }

    boolean end = false;

    @Override
    public void main(String[] args) throws MsgException {
        // Start a bunch of processes on node1
        Host node1 = Host.getByName("node1");
        Msg.info("Got " + node1.getName());

        Process p = new Process(node1, "p") {
            @Override
            public void main(String[] strings) throws MsgException {
                while(!end) {
                    Task t = new Task("t1", node1.getSpeed() * 100, 0);
                    try {
                        t.execute();
                    } catch (TaskCancelledException e) {
                        e.printStackTrace();
                        suspend();
                    }
                }
            }
        };

        p.start();
        p.suspend();
        waitFor(10);
        end = true;
    }
}

