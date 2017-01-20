package migration;

import configuration.XHost;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;

import configuration.XVM;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class Migrator extends Process {
    public static boolean isEnd = false;

    public Migrator(Host host, String name, String[] args) {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) throws MsgException {
        int load = 90;
        int dpIntensity = 90;

        // Prepare the hosts
        Host host1 = null;
        Host host2 = null;
        Host host3 = null;
        Host host4 = null;
        Host host5 = null;
        Host host6 = null;
        Host host7 = null;
        Host host8 = null;
        try {
            host1 = Host.getByName("node0");
            host2 = Host.getByName("node1");
            host3 = Host.getByName("node2");
            host4 = Host.getByName("node3");
            host5 = Host.getByName("node4");
            host6 = Host.getByName("node5");
            host7 = Host.getByName("node6");
            host8 = Host.getByName("node7");
        } catch (HostNotFoundException e) {
            Msg.critical(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // Start a VM
        XVM[] xvms = new XVM[78];
        for(int i = 0 ; i < xvms.length; i++) {
            Host host = Host.getByName("node" + (i%4));
            XHost xhost = new XHost(host, 32*1024, 8, 800, 1250, null);
            xvms[i] = new XVM(
                    xhost,          // destination
                    "vm-" + i,         // name
                    1,              // # of VCPU
                    1 * 1024,       // RAM
                    125,           // BW
                    null,           // disk path
                    -1,             // disk size
                    125,           // migration BW
                    dpIntensity);   // dirty page rate

            xvms[i].start();
            xvms[i].setLoad(load);
            Msg.info(xvms[i].getName() + " started on host " + host.getName());
        }



        // Migrate the VM
        double host1Before = host1.getConsumedEnergy();
        double host2Before = host2.getConsumedEnergy();
        double start = Msg.getClock();
        int i = 0;
        for(XVM vm: xvms) {
            if(i < xvms.length)
                asyncMigrate(vm, host5);
            else if (i < xvms.length * 2 / 4)
                asyncMigrate(vm, host6);
            else if (i < xvms.length * 3/ 4)
                asyncMigrate(vm, host7);
            else
                asyncMigrate(vm, host8);
            i++;
        }
        double duration = Msg.getClock() - start;
        double host1After = host1.getConsumedEnergy();
        double host2After = host2.getConsumedEnergy();
        waitFor(10);
        isEnd = true;
        host1.off();
        host2.off();
        host5.off();
        waitFor(1000);

        double watt1 = (host1After - host1Before) / duration;
        double watt2 = (host2After - host2Before) / duration;
        Msg.info(String.format("End of migration\nConsumed energy:\nHost 1: %.2f\nHost 2: %.2f", watt1, watt2));

        try {
            File out = new File("migration_energy.dat");
            boolean empty = !out.exists();
            FileWriter writer = new FileWriter(out, true);

            if(empty)
                writer.write("# Load\tdpIntensity\ttime\thost 1\thost 2\n");

            Msg.info(String.format("%f %f %f", duration, host1Before, host1After));
            writer.write(String.format("%d\t%d\t\t%.2f\t%.2f\t%.2f\n", load, dpIntensity, duration, watt1, watt2));

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        exit();
    }

    public static boolean isEnd() {
        return isEnd;
    }

    int nbThreads = 0;

    private void asyncMigrate(XVM vm, Host destHost) {
        try {
            String[] args = new String[2];

            args[0] = vm.getName();
            args[1] = destHost.getName();
            Random rand = new Random();
            Msg.info("Nbre Thread Inc: "+ (++nbThreads));
            new Process(Host.currentHost(),"Migrate-"+rand.nextDouble(),args) {
                public void main(String[] args) throws HostFailureException {
                    Host destHost = null;
                    VM vm = null;
                    try {
                        vm = VM.getVMByName(args[0]);
                        destHost = Host.getByName(args[1]);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("You are trying to migrate from/to a non existing node");
                    }
                    if(destHost != null){
                        vm.migrate(destHost);
                        //waitFor(10.0);
                    }
                    Msg.info("End of migration of VM " + args[0] + " to " + args[1]);
                    Msg.info("Nbre Thread Dec: "+ (--nbThreads));

                }
            }.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
