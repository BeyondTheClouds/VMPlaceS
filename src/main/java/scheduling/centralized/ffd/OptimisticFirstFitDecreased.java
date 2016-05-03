package scheduling.centralized.ffd;

import configuration.XHost;
import configuration.XVM;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

public class OptimisticFirstFitDecreased extends FirstFitDecreased {
    public OptimisticFirstFitDecreased(Collection<XHost> hosts, Integer id) {
        super(hosts, id);
    }

    @Override
    protected void manageOverloadedHost(TreeSet<XHost> overloadedHosts, Collection<XHost> saneHosts, SchedulerResult result) {
        TreeSet<XVM> toSchedule = new TreeSet<>(new XVMComparator(true, useLoad));
        Map<XVM, XHost> sources = new HashMap<>();

        // Remove all VMs from the overloaded hosts
        for(XHost host: overloadedHosts) {
            for(XVM vm: host.getRunnings()) {
                toSchedule.add(vm);
                sources.put(vm, host);
            }

            host.setCPUDemand(0);
            saneHosts.add(host);
        }

        for(XVM vm: toSchedule) {
            XHost dest = null;

            // Try find a new host for the VMs
            for(XHost host: saneHosts) {
                if(host.getCPUCapacity() >= host.getCPUDemand() - vm.getCPUDemand()) {
                    dest = host;
                    break;
                }
            }

            if(dest == null) {
                result.state = SchedulerResult.State.NO_VIABLE_CONFIGURATION;
                return;
            }

            // Migrate the VM
            dest.setCPUDemand(dest.getCPUDemand() + vm.getCPUDemand());
            XHost source = sources.get(vm);
            if(!source.getName().equals(dest.getName())) {
                relocateVM(vm.getName(), source.getName(), dest.getName());
                nMigrations++;
            }
        }
    }
}
