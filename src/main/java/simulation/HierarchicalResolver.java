package simulation;


import configuration.SimulatorProperties;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.hierarchical.snooze.*;

/**
 * Created by sudholt on 25/05/2014.
 */
public class HierarchicalResolver extends Process {


    HierarchicalResolver(Host host, String name, String[] args) throws HostNotFoundException, NativeException {
        super(host, name, args);
    }

    /**
     * @param args
     */
    public void main(String[] args) throws MsgException {

        Msg.info("Hierarchical algorithm variant: " + SnoozeProperties.getAlgVariant());

        // TODO what is the interest of the ep ?
        Msg.info("Start the entry point on " + Host.currentHost()+ "");
        new EntryPoint(Host.currentHost(), "entryPoint").start();

        // Start the mutlicast service
        Msg.info("Start the multicast entry point on " + Host.currentHost()+ "");
//        Multicast multicast = new Multicast(Host.getByName("node55"), "multicast");
        Multicast multicast = new Multicast(Host.currentHost(), "multicast");
        multicast.start();

        Msg.info("Start the Test process on " + Host.currentHost()+ "");
        new Test(Host.currentHost(), "test").start();

        // Wait for GMs to be registered and then start LCs to ensure completely balanced distributed of LCs
        //  (code copied from Test.procAddLCs())
        // GMs are started by means of the generation script generate.py
        waitFor(2);
        int lcNo = 0; // no. of statically allocated LCs
        for (int i = 0; i < SimulatorProperties.getNbOfHostingNodes(); i++) {
            String[] lcArgs = new String[]{"node" + lcNo, "dynLocalController-" + lcNo};
            LocalController lc =
                    new LocalController(Host.getByName("node" + lcNo), "dynLocalController-" + lcNo, lcArgs);
            lc.start();
            Logger.info("[Test.addLCs] Dyn. LC added: " + lcArgs[1]);
            lcNo++;
//            sleep(5);
        }


//        Start the group leadear (by default it is started on the first node of the infrastructure
//        ne¡w GroupLeader(Host.getByName("node1"), "groupLeader").start();

        // Start as many GMs as expected and assign them randomly (please note that for reproductibility reasons, we are
        // leveraging a specific seed (see SimulatorProperties class file)
        /*
        Random randHostPicker = new Random(SimulatorProperties.getSeed());
        int hostIndex;
        ArrayList<Integer> initialGMs = new ArrayList<Integer>();
        for (int i=0; i< SnoozeProperties.getGMNumber() ; i++){
            // Select the next hosting node for the GM and prevent to get one that has been already selected
            do {
                hostIndex = randHostPicker.nextInt(SimulatorProperties.getNbOfNodes());
            } while (initialGMs.contains(new Integer(hostIndex)));

            GroupManager gm = new GroupManager(Host.getByName("node"+hostIndex), "gm"+hostIndex);
            gm.start();
            Msg.info("GM "+i+" has been created");
            initialGMs.add(i);
        }
        */

        while (!SimulatorManager.isEndOfInjection()) {
            waitFor(3);
        }
        waitFor(3);
    }

}
