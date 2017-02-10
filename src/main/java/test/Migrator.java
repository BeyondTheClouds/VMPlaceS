package test;

import configuration.SimulatorProperties;
import configuration.VMClasses;
import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import simulation.SimulatorManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Migrator extends Process {
    private static final int SIMULATION_TIME = 360000 * 1;
    private static final int N_HOSTS = 64;
    private static final int N_VMS = N_HOSTS * 10;
    private static final float MIG_RATE = 0.2F;

    private Random r = new Random(42);

    public Migrator(Host host, String name, String[] args) throws HostNotFoundException {
        super(host, name, args);
    }

    @Override
    public void main(String[] strings) throws MsgException {
        // Get the hosts
        int nodeMemCons[] = new int[N_HOSTS];
        int nodeCpuCons[] = new int[N_HOSTS];
        XHost hosts[] = new XHost[N_HOSTS];

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

        // Start the big loop
        int round = 0;
        while(Msg.getClock() < SIMULATION_TIME) {
            Msg.info("Round " + round++);
            List<Migration> migrations = new ArrayList<>();

            // Decide what and where to migrate
            for(int i = 0; i < N_HOSTS; i++) {
                XHost h = hosts[i];

                int nMigrations = 0;
                for(XVM vm: h.getRunnings()) {
                    if(++nMigrations > MIG_RATE * h.getRunnings().size())
                        break;

                    vm.setLoad(r.nextInt(100));
                    migrations.add(new Migration(vm, h, hosts[(i+1) % N_HOSTS]));
                }
            }

            if(Msg.getClock() > SIMULATION_TIME)
                break;

            // Perform the migrations
            for(Migration m: migrations) {
                m.src.migrate(m.vm.getName(), m.dest);
            }

            waitFor(10);
        }

        Msg.info("End of injection");
        SimulatorManager.setEndOfInjection();
        //Process.killAll(-1);
        waitFor(500);
        Msg.info("Migrator exitting");
        return;
    }
}

class Migration {
    XVM vm;
    XHost src;
    XHost dest;

    public Migration(XVM vm, XHost source, XHost destination) {
        this.vm = vm;
        this.src = source;
        this.dest = destination;
    }

    public String toString() {
        return String.format("[Migration %s: %s -> %s]", vm.getName(), src.getName(), dest.getName());
    }
}