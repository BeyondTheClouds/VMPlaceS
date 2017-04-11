package bug;

import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;

public class MyProcess extends Process {
    public MyProcess(Host host, String name, String[] args) throws HostNotFoundException {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) throws MsgException {
        // Start a bunch of processes on node1
        Host node1 = Host.getByName("node1");
        XHost host = new XHost(node1, 4096, 8, 800,1000, null);
        Msg.info("Got " + node1.getName());

        XVM vm =  new XVM(host, "vm", 1, 1024, 125, null, 0, 125, 40);
        vm.start();
        vm.setLoad(20);

        waitFor(1000);
        vm.suspend();
        vm.setLoad(80);

        waitFor(10);
        vm.resume();
        waitFor(500);

        vm.shutdown();

        Msg.info("This is the end");
    }
}

