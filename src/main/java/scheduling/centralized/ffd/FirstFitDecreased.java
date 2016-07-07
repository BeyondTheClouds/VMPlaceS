package scheduling.centralized.ffd;

import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.Msg;
import scheduling.AbstractScheduler;
import simulation.SimulatorManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Random;
import java.util.TreeSet;

public abstract class FirstFitDecreased extends AbstractScheduler {
    private static int iteration = 0;
    protected int nMigrations = 0;

    protected boolean useLoad;

    public FirstFitDecreased(Collection<XHost> hosts) {
        this(hosts, new Random().nextInt());
    }

    public FirstFitDecreased(Collection<XHost> hosts, Integer id) {
        useLoad = SimulatorProperties.getUseLoad();
    }

    @Override
    protected void applyReconfigurationPlan() {

    }

    @Override
    public ComputingResult computeReconfigurationPlan() {
        return null;
    }

    public SchedulerResult checkAndReconfigure(Collection<XHost> hostsToCheck) {
        SchedulerResult result = new SchedulerResult();
        long start = System.currentTimeMillis();

        TreeSet<XHost> overloaded = new TreeSet<>(new XHostComparator(true));

        // Find the overloaded hosts
        for(XHost host: hostsToCheck) {
            double demand = host.computeCPUDemand();
            if(host.getCPUCapacity() < demand)
                overloaded.add(host);
        }

        nMigrations = 0;
        manageOverloadedHost(overloaded, result);

        if(nMigrations > 0)
            result.state = SchedulerResult.State.SUCCESS;
        else if(result.state != SchedulerResult.State.NO_VIABLE_CONFIGURATION)
            result.state = SchedulerResult.State.NO_RECONFIGURATION_NEEDED;
        result.duration = System.currentTimeMillis() - start;

        // Wait for all the migrations to terminate
        int watchDog = 0;

        while(this.ongoingMigrations()) {
            try {
                org.simgrid.msg.Process.getCurrentProcess().waitFor(1);
                watchDog ++;
                if (watchDog%100==0){
                    Msg.info(String.format("You're waiting for %d migrations to complete (already %d seconds)", getOngoingMigrations(), watchDog));
                    if(SimulatorManager.isEndOfInjection()){
                        Msg.info("Something wrong we are waiting too much, bye bye");
                        System.exit(131);
                    }
                }
            } catch (HostFailureException e) {
                e.printStackTrace();
            }
        }

        // Turn off unused hosts
        if(SimulatorProperties.getHostsTurnoff()) {
            for (XHost host : SimulatorManager.getSGHostingHosts()) {
                if (host.isOn() && host.getRunnings().size() <= 0)
                    SimulatorManager.turnOff(host);
            }
        }

        // Log the new configuration
        try {
            File file = new File("logs/ffd/configuration/" + (++iteration) + "-" + System.currentTimeMillis() + ".txt");
            file.getParentFile().mkdirs();
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));

            for(XHost host: SimulatorManager.getSGHostingHosts()) {
                writer.write(host.getName() + ':');

                for(XVM vm: host.getRunnings()) {
                    writer.write(' ' + vm.getName());
                }
                writer.write('\n');
            }

            writer.flush();
            writer.close();
        } catch (IOException e) {
            System.err.println("Could not write FFD log");
            e.printStackTrace();
            System.exit(5);
        }


        return result;
    }

    protected abstract void manageOverloadedHost(TreeSet<XHost> overloadedHosts, SchedulerResult result);


    class XHostComparator implements Comparator<XHost> {
        private int factor = 1;

        public XHostComparator() {
            this(false);
        }

        public XHostComparator(boolean decreasing) {
            if(decreasing)
                this.factor = -1;
        }

        @Override
        public int compare(XHost h1, XHost h2) {
            if(h1.getCPUCapacity() != h2.getCPUCapacity()) {
                return factor * (h1.getCPUCapacity() - h2.getCPUCapacity());
            }

            if(h1.getMemSize() != h2.getMemSize())
                return factor = (h1.getMemSize() - h2.getMemSize());

            if(h1.getNetBW() != h2.getNetBW())
                return factor = (h1.getNetBW() - h2.getNetBW());

            return 0;
        }
    }

    class XVMComparator implements Comparator<XVM> {
        private int factor = 1;
        private boolean useLoad = false;

        public XVMComparator(boolean useLoad) {
            this(false, useLoad);
        }

        public XVMComparator(boolean decreasing, boolean useLoad) {
            if(decreasing)
                this.factor = -1;

            this.useLoad = useLoad;
        }

        @Override
        public int compare(XVM h1, XVM h2) {
            if(useLoad && h1.getLoad() != h2.getLoad()) {
                return factor * h1.getLoad() - h2.getLoad();
            }

            if(h1.getCPUDemand() != h2.getCPUDemand()) {
                return factor * (int) Math.round((h1.getCPUDemand() - h2.getCPUDemand()));
            }

            if(h1.getMemSize() != h2.getMemSize())
                return factor = (h1.getMemSize() - h2.getMemSize());


            return(h1.getName().compareTo(h2.getName()));
        }
    }
}
