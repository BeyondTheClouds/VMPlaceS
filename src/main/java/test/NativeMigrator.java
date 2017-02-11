package test;

import configuration.VMClasses;
import configuration.XVM;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;

import java.util.*;

public class NativeMigrator extends Process {

    private static final int SIMULATION_TIME = 3600 * 1;
    private static final int N_HOSTS = 64;
    private static final int N_VMS = N_HOSTS * 10;
    private static final float MIG_RATE = .8F;
    // host info
    private static final int HOST_CPU_CAP = 700;
    private static final int HOST_MEM_CAP = 32768;

    private static final int VM_CPU_CAP = 100;
    private static final int VM_CPU_MEAN = 60;

    private int nMigrations = 0;

    private Random r = new Random(42);

    static public ExtVM vms[] = new ExtVM[N_VMS];

    static public Collection<ExtVM> getRunnings(Host host) {
        LinkedList<ExtVM> list = new LinkedList<ExtVM>();
        for (ExtVM tVM : vms) { // this is in O(N2) but this is for test so I don't care.
            if (tVM.getHost() == host)
                list.add(tVM);
        }
        return list;
    }

    NativeMigrator(Host host, String name, String[] args) throws HostNotFoundException {
        super(host, name, args);
    }

    @Override
    public void main(String[] strings) throws MsgException {
        // Get the hosts
        int nodeMemCons[] = new int[N_HOSTS];
        int nodeCpuCons[] = new int[N_HOSTS];


        // Construct and assign VMs
        int iVM = 0;
        int iHost = 0;
        Random r = new Random(42);

        for (int i = 0; i < N_VMS; i++) {
            VMClasses.VMClass vmClass = VMClasses.CLASSES.get(r.nextInt(VMClasses.CLASSES.size()));

            // Move on to next host?
            try {
                while ((nodeMemCons[iHost] + vmClass.getMemSize() > HOST_MEM_CAP)
                        || (nodeCpuCons[iHost] + VM_CPU_MEAN > HOST_CPU_CAP)) {
                    iHost++;
                    nodeMemCons[iHost] = 0;
                    nodeCpuCons[iHost] = 0;
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                Msg.error("Not enough resources to place all the VMs");
                System.exit(2);
            }

            // Creation of the VM
            ExtVM vm = new ExtVM(Host.getByName("node" + iHost), "vm-" + iVM, vmClass.getMemSize(),
                    vmClass.getNetBW(), vmClass.getMemIntensity());

            vms[iVM] = vm;
            iVM++;

            // Assign the new VM to the current host.
            vm.start();
            nodeMemCons[iHost] += vmClass.getMemSize();
            nodeCpuCons[iHost] += VM_CPU_MEAN;
        }

        // Print the initial placement
        System.out.println("Initial placement:");
        for (Host h : Host.all()) {
            String vmList = "";
            for (ExtVM tVM : getRunnings(h)) // Ok O(N2) but this is a test so I don't care
                vmList += tVM.getName() + ' ';
            Msg.info(h.getName() + ": " + vmList);
        }


        // Start the big loop
        int round = 0;
        while (Msg.getClock() < SIMULATION_TIME) {
            Msg.info("Round " + round++);
            List<Migration> migrations = new ArrayList<>();

            // Decide what and where to migrate
            for (int i = 0; i < N_HOSTS; i++) {
                Host h = Host.getByName("node" + i);

                int nMigrations = 0;
                for (ExtVM vm : getRunnings(h)) {
                    if (++nMigrations > MIG_RATE * getRunnings(h).size())
                        break;

                    vm.setLoad(r.nextInt(100));
                    migrations.add(new Migration(vm, h, Host.getByName("node" + ((i + 1) % N_HOSTS))));
                }
            }

            if (Msg.getClock() > SIMULATION_TIME)
                break;

            // Perform the migrations
            for (Migration m : migrations) {
                try {
                    String[] args = new String[]{
                            m.vm.getName(),
                            m.src.getName(),
                            m.dest.getName()
                    };

                    new Process(m.src, "Migrate-" + m.vm.getName() + "-" + r.nextInt(), args) {
                        public void main(String[] args) {

                            Msg.info(String.format("Migrating %s from %s to %s", m.vm.getName(), m.src.getName(), m.dest.getName()));
                            nMigrations++;
                            m.vm.extMigrate(m.dest); //Exception caught at low level.
                            nMigrations--;
                        }
                    }.start();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            while (nMigrations > 0) {
                waitFor(10);
            }
            waitFor(50);
        }

        // TODO we should destroy VMs but let's see whether this is mandatory or not.
        Msg.info("End of test");
        return;
    }

    // Inner Class
    class ExtVM extends VM {

        Host host;

        ExtVM(Host assignToHost, String name, int memCap, int NetBW, int dPageRate) {
            super(assignToHost, name, memCap, NetBW, dPageRate);
            host = assignToHost;
        }

        Host getHost() {
            return host;
        }

        void extMigrate(Host dstHost) {
            try {
                super.migrate(dstHost);
            } catch (HostFailureException e) {
                Msg.info("Something was wrong during the migration");
                e.printStackTrace();
                System.exit(-1);
            }
            host = dstHost;
        }

        public void setLoad(int load) {
            this.setBound(load); //TODO confirm whether this really fix the load or not.
        }
    }

    class Migration {
        ExtVM vm;
        Host src;
        Host dest;

        public Migration(ExtVM vm, Host source, Host destination) {
            this.vm = vm;
            this.src = source;
            this.dest = destination;
        }

        public String toString() {
            return String.format("[Migration %s: %s -> %s]", vm.getName(), src.getName(), dest.getName());
        }
        // End of Inner Class
    }
}

