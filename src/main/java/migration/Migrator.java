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
    private static int N_HOSTS = 50;
    private static int N_VMS = 500;

    public static boolean isEnd = false;
    private Random r = new Random(42);

    public Migrator(Host host, String name, String[] args) {
        super(host, name, args);
    }

    @Override
    public void main(String[] args) throws MsgException {
        int load = 90;
        int dpIntensity = 90;

        // Prepare the hosts
        Host hosts[] = new Host[N_HOSTS];
        try {
            for(int i = 0; i < hosts.length; i++) {
                hosts[i] = Host.getByName("node" + i);
            }
        } catch (HostNotFoundException e) {
            Msg.critical(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // Start a VM
        XVM[] xvms = new XVM[N_VMS];
        for(int i = 0 ; i < xvms.length; i++) {
            Host host = Host.getByName("node" + (i % N_HOSTS));
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
        int i = 0;
        for(XVM vm: xvms) {
            //Host

        }
        waitFor(10);
        isEnd = true;
        waitFor(1000);
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
