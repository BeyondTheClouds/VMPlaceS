package scheduling.centralized.ffd;

import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.Msg;
import scheduling.AbstractScheduler;
import simulation.SimulatorManager;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

public abstract class FirstFitDecreased extends AbstractScheduler {
    protected int nMigrations = 0;

    public FirstFitDecreased(Collection<XHost> hosts, Integer id) {
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
        TreeSet<XHost> sane = new TreeSet<>(new XHostComparator(true));

        // Find the overloaded hosts
        for(XHost host: hostsToCheck) {
            double demand = host.computeCPUDemand();
            if(host.getCPUCapacity() < demand)
                overloaded.add(host);
            else
                sane.add(host);
        }

        nMigrations = 0;
        manageOverloadedHost(overloaded, hostsToCheck, result);

        if(nMigrations > 0)
            result.state = SchedulerResult.State.SUCCESS;
        else
            result.state = SchedulerResult.State.NO_RECONFIGURATION_NEEDED;
        result.duration = System.currentTimeMillis() - start;

        // Wait for all the migrations to terminate
        int watchDog = 0;

        while(this.ongoingMigrations()){
            try {
                org.simgrid.msg.Process.getCurrentProcess().waitFor(1);
                watchDog ++;
                if (watchDog%100==0){
                    Msg.info("You're are waiting for a couple of seconds (already "+watchDog+" seconds)");
                    if(SimulatorManager.isEndOfInjection()){
                        Msg.info("Something wrong we are waiting too much, bye bye");
                        System.exit(-1);
                    }
                }
            } catch (HostFailureException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    protected abstract void manageOverloadedHost(TreeSet<XHost> overloadedHosts, Collection<XHost> saneHosts, SchedulerResult result);


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

        public XVMComparator() {
            this(false);
        }

        public XVMComparator(boolean decreasing) {
            if(decreasing)
                this.factor = -1;
        }

        @Override
        public int compare(XVM h1, XVM h2) {
            if(h1.getCPUDemand() != h2.getCPUDemand()) {
                return factor * (int) Math.round((h1.getCPUDemand() - h2.getCPUDemand()));
            }

            if(h1.getMemSize() != h2.getMemSize())
                return factor = (h1.getMemSize() - h2.getMemSize());

            return 0;
        }
    }
}
