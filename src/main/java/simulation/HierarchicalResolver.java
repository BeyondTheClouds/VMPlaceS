package simulation;


import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.snooze.EntryPoint;

/**
 * Created by sudholt on 25/05/2014.
 */
public class HierarchicalResolver extends Process {

    private EntryPoint ep;

    HierarchicalResolver(Host host, String name, String[] args) throws HostNotFoundException, NativeException {
        super(host, name, args);
        ep = null;
    }

    /**
     * @param args
     */
    public void main(String[] args) throws MsgException {
        Msg.info("Start the entry point on " + Host.currentHost()+ "");
        ep = new EntryPoint(Host.currentHost(), "entryPoint");
        ep.start();
        while (!SimulatorManager.isEndOfInjection()) {
            waitFor(3);

        }
        waitFor(3);
    }
}
