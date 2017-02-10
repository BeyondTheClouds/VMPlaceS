package bug;

import configuration.SimulatorProperties;
import configuration.VMClasses;
import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import test.Migrator;

import java.util.Random;

import static test.Migrator.N_HOSTS;
import static test.Migrator.N_VMS;

public class Main {
    public static XHost hosts[] = new XHost[N_HOSTS];

    public static void main(String[] args) throws NativeException, HostNotFoundException, HostFailureException, TaskCancelledException {
        Msg.init(args);
        Msg.createEnvironment(args[0]);
        Msg.info("Environment created");
        Msg.deployApplication(args[1]);

        // Get the hosts
        int nodeMemCons[] = new int[N_HOSTS];
        int nodeCpuCons[] = new int[N_HOSTS];

        // Hosting hosts
        for(int i = 0 ; i < N_HOSTS; i ++){
            try {
                Host tmp = Host.getByName("node" + i);
                XHost xtmp = new XHost(tmp, SimulatorProperties.getMemoryTotal(), SimulatorProperties.getNbOfCPUs(), SimulatorProperties.getCPUCapacity(), SimulatorProperties.getNetCapacity(), "127.0.0.1");
                xtmp.turnOn();
                hosts[i] = xtmp;
            } catch (HostNotFoundException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        // Construct some VMs
        XVM vms[] = new XVM[N_VMS];
        int iVM = 0;
        int iHost = 0;
        Random r = new Random(42);

        for(int i = 0; i < N_VMS; i++) {
            VMClasses.VMClass vmClass = VMClasses.CLASSES.get(r.nextInt(VMClasses.CLASSES.size()));

            // Move on to next host?
            try {
                while ((nodeMemCons[iHost] + vmClass.getMemSize() > hosts[iHost].getMemSize()
                        || nodeCpuCons[iHost] + SimulatorProperties.getMeanLoad() > hosts[iHost].getCPUCapacity())) {
                    iHost++;
                    nodeMemCons[iHost] = 0;
                    nodeCpuCons[iHost] = 0;
                }
            } catch(ArrayIndexOutOfBoundsException ex){
                Msg.error("Not enough resources to place all the VMs");
                System.exit(2);
            }

            // Creation of the VM
            XVM vm = new XVM(hosts[iHost], "vm-" + iVM, vmClass.getNbOfCPUs(), vmClass.getMemSize(),
                    vmClass.getNetBW(), null, -1, vmClass.getMigNetBW(), vmClass.getMemIntensity());

            vms[iVM] = vm;
            iVM++;

            // Assign the new VM to the current host.
            hosts[iHost].start(vm);
            nodeMemCons[iHost] += vm.getMemSize();
            nodeCpuCons[iHost] += SimulatorProperties.getMeanLoad();
        }

        // Print the initial placement
        System.out.println("Initial placement:");
        for(XHost h: hosts) {
            System.out.print(h.getName() + ": ");
            for(XVM vm: h.getRunnings())
                System.out.print(vm.getName() + ' ');
            System.out.println();
        }

        Msg.run();

        Msg.info("End of run");
    }
}
