package scheduling.plaOpt;

import configuration.XHost;
import configuration.XVM;
import org.discovery.DiscoveryModel.model.*;
import org.discovery.DiscoveryModel.model.Units;
import org.discovery.DiscoveryModel.model.VirtualMachineStates;
import scheduling.Scheduler;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: alebre
 * Date: 21/01/14
 * Time: 13:02
 * To change this template use File | Settings | File Templates.
 */
public class PlacementOptimizer implements Scheduler {



    Object extractConfiguration(Collection<XHost> xhosts){

        ArrayList<Node> nodes=new ArrayList<Node>();
        Node  node = null;

        // Add nodes
        for (XHost tmpH:xhosts){

            //Hardware Specification
            ArrayList<Cpu> cpus = new ArrayList<Cpu>();
            cpus.add(new Cpu(tmpH.getNbCores(), tmpH.getCPUCapacity()/tmpH.getNbCores()));

            ArrayList<NetworkInterface> nets =   new ArrayList<NetworkInterface>();
            nets.add(new NetworkInterface("eth0", tmpH.getNetBW()   * Units.GIGA()));

                    HardwareSpecification nodeHardwareSpecification = new HardwareSpecification(
                     cpus,
                     nets,
                    // StorageDevice are not yet implemented within the Simgrid framework
                    new ArrayList<StorageDevice>() {{
                        add(new StorageDevice("hd0", 512 * Units.GIGA()));
                    }},

                    new Memory(tmpH.getMemSize() * Units.MEGA())
            );

            Location nodeLocation = new Location(tmpH.getIP(), 3000);
            ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();
            node = new Node(tmpH.getName(), nodeHardwareSpecification, nodeLocation, vms);

            for(XVM tmpVM:tmpH.getRunnings()) {
                ArrayList<Cpu> cpusVM = new ArrayList<Cpu>();
                Cpu tmpCpu = new Cpu((int)tmpVM.getCoreNumber(), 100);
                tmpCpu.setUsage(tmpVM.getCPUDemand());
                cpusVM.add(tmpCpu);

                ArrayList<NetworkInterface> netsVM = new ArrayList<NetworkInterface>();
                nets.add(new NetworkInterface("eth0", tmpVM.getNetBW() * Units.MEGA()));

                HardwareSpecification vmHardwareSpecification = new HardwareSpecification(
                        cpusVM,
                        netsVM,
                        // Not used see above
                        new ArrayList<StorageDevice>() {{
                            add(new StorageDevice("hd0", 100 * Units.GIGA()));
                        }},
                        new Memory(tmpVM.getMemSize()* Units.MEGA())
                );


                // TODO 1./ Jonathan should add networkSpecification for a VM.
                // TODO 2./ Jonathan should encaspulates networkSpecification into HardwareSpecification (net should appear at
                // the same level than CPU/mem/...
                node.addVm(new VirtualMachine(tmpVM.getName(),  new VirtualMachineStates.Running(), vmHardwareSpecification));

            }
            nodes.add(node);
        }

        return nodes;
    }

    @Override
    public ComputingState computeReconfigurationPlan() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int getReconfigurationPlanCost() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void applyReconfigurationPlan() {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
