package migration;

import org.simgrid.msg.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import configuration.XVM;
import org.simgrid.msg.Process;

public class Migrator extends Process {
    private static boolean isEnd = false;

    public Migrator(Host host, String name, String[] args) {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) throws MsgException {
        int load = Integer.parseInt(args[0]);
        int dpIntensity = Integer.parseInt(args[1]);

        // Prepare the hosts
        Host host1 = null;
        Host host2 = null;
        try {
            host1 = Host.getByName("node0");
            host2 = Host.getByName("node1");
        } catch (HostNotFoundException e) {
            Msg.critical(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // Start a VM
        XVM vm = new XVM(
                host1,          // destination
                "vm-1",         // name
                1,              // # of VCPU
                4 * 1024,       // RAM
                1000,           // BW
                null,           // disk path
                -1,             // disk size
                1000,           // migration BW
                dpIntensity);   // dirty page rate

        vm.start();
        vm.setLoad(load);

        // REPLACE WITH PROCESS
        class Monitor extends Process {
            public Monitor(String host) throws HostNotFoundException, NativeException {
                super(host, "monitor", new String[] {});
            }

            @Override
            public void main(String[] args) throws MsgException {
                try {
                    Host host1 = null;
                    Host host2 = null;
                    try {
                        host1 = Host.getByName("node0");
                        host2 = Host.getByName("node1");
                    } catch (HostNotFoundException e) {
                        Msg.critical(e.getMessage());
                        e.printStackTrace();
                        System.exit(1);
                    }

                    File out = new File("migration_energy.dat");
                    FileWriter writer = new FileWriter(out, true);
                    writer.write("# time\thost 1\thost 2\n");

                    double prev1 = host1.getConsumedEnergy();
                    double prev2 = host2.getConsumedEnergy();
                    double period = 0.5;
                    int s = 0;

                    Msg.info("Monitor started");
                    do {
                        try {
                            Migrator.this.waitFor(period);
                        } catch (HostFailureException e) {
                            e.printStackTrace();
                        }

                        double curr1 = host1.getConsumedEnergy();
                        double curr2 = host2.getConsumedEnergy();
                        double watt1 = (curr1 - prev1) / period;
                        double watt2 = (curr2 - prev2) / period;
                        writer.write(String.format("%d\t%.2f\t%.2f\n", s++, watt1, watt2));
                        prev1 = curr1;
                        prev2 = curr2;
                    } while(!Migrator.isEnd());

                    Msg.info("Monitor interrupted");

                    writer.close();
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        };

        Monitor monitor = new Monitor("node2");
        monitor.start();

        Msg.info(vm.getName() + " started on host " + host1.getName());

        // Migrate the VM
        double host1Before = host1.getConsumedEnergy();
        double host2Before = host2.getConsumedEnergy();
        double start = Msg.getClock();
        vm.migrate(host2);
        waitFor(15);
        vm.migrate(host1);
        waitFor(5);
        double duration = Msg.getClock() - start;
        double host1After = host1.getConsumedEnergy();
        double host2After = host2.getConsumedEnergy();

        isEnd = true;

        host1.off();
        host2.off();

        double watt1 = (host1After - host1Before) / duration;
        double watt2 = (host2After - host2Before) / duration;
        Msg.info(String.format("End of migration\nConsumed energy:\nHost 1: %.2f\nHost 2: %.2f", watt1, watt2));

        /*try {
            File out = new File("migration_energy.dat");
            boolean empty = !out.exists();
            FileWriter writer = new FileWriter(out, true);

            if(empty)
                writer.write("# Load\tdpIntensity\ttime\thost 1\thost 2\n");

            writer.write(String.format("%d\t%d\t\t%.2f\t%.2f\t%.2f\n", load, dpIntensity, duration, watt1, watt2));

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        exit();
    }

    public static boolean isEnd() {
        return isEnd;
    }
}
