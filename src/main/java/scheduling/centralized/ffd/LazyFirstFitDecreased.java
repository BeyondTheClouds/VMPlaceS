package scheduling.centralized.ffd;

import configuration.XHost;
import configuration.XVM;

import java.util.*;

public class LazyFirstFitDecreased extends FirstFitDecreased {
    public LazyFirstFitDecreased(Collection<XHost> hosts, Integer id) {
        super(hosts, id);
    }

    @Override
    protected void manageOverloadedHost(TreeSet<XHost> overloadedHosts, Collection<XHost> saneHosts, SchedulerResult result) {
        TreeSet<XVM> toSchedule = new TreeSet<>(new XVMComparator(true));
        Map<XVM, XHost> sources = new HashMap<>();

        // Remove all VMs from the overloaded hosts
        for(XHost host: overloadedHosts) {
            Iterator<XVM> vms = host.getRunnings().iterator();
            host.setCPUDemand(host.computeCPUDemand());

            while(host.getCPUCapacity() < host.getCPUDemand()) {
                XVM vm = vms.next();
                toSchedule.add(vm);
                sources.put(vm, host);
                host.setCPUDemand(host.getCPUDemand() - vm.getCPUDemand());
            }

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
