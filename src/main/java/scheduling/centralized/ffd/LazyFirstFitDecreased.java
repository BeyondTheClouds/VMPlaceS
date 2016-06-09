package scheduling.centralized.ffd;

import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.Msg;
import simulation.SimulatorManager;

import java.util.*;

public class LazyFirstFitDecreased extends FirstFitDecreased {

    public LazyFirstFitDecreased(Collection<XHost> hosts) {
        this(hosts, new Random().nextInt());
    }

    public LazyFirstFitDecreased(Collection<XHost> hosts, Integer id) {
        super(hosts, id);
    }

    @Override
    protected void manageOverloadedHost(TreeSet<XHost> overloadedHosts, SchedulerResult result) {
        // The VMs are sorted by decreasing size of CPU and RAM capacity
        TreeSet<XVM> toSchedule = new TreeSet<>(new XVMComparator(true, useLoad));
        Map<XVM, XHost> sources = new HashMap<>();

        // Store the load of each host
        Map<XHost, Double> predictedCPUDemand = new HashMap<>();
        for(XHost host: SimulatorManager.getSGHostingHosts())
            predictedCPUDemand.put(host, host.getCPUDemand());

        // Remove enough VMs so the overloaded hosts are no longer overloaded
        for(XHost host: overloadedHosts) {
            Iterator<XVM> vms = host.getRunnings().iterator();

            while(host.getCPUCapacity() < predictedCPUDemand.get(host)) {
                XVM vm = vms.next();
                toSchedule.add(vm);
                sources.put(vm, host);
                predictedCPUDemand.put(host, predictedCPUDemand.get(host) - vm.getCPUDemand());
            }
        }

        for(XVM vm: toSchedule) {
            XHost dest = null;

            // Try find a new host for the VMs (saneHosts is not sorted)
            for(XHost host: SimulatorManager.getSGHostingHosts()) {
                if(host.getCPUCapacity() >= predictedCPUDemand.get(host) + vm.getCPUDemand()) {
                    dest = host;
                    break;
                }
            }

            if(dest == null) {
                result.state = SchedulerResult.State.NO_VIABLE_CONFIGURATION;
                return;
            }

            // Migrate the VM
            predictedCPUDemand.put(dest, predictedCPUDemand.get(dest) + vm.getCPUDemand());
            XHost source = sources.get(vm);
            if(!source.getName().equals(dest.getName())) {
                relocateVM(vm.getName(), source.getName(), dest.getName());
                nMigrations++;
            }
        }
    }
}
