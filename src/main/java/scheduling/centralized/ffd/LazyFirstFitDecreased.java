package scheduling.centralized.ffd;

import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import simulation.SimulatorManager;

import java.util.*;

public class LazyFirstFitDecreased extends FirstFitDecreased {

    public LazyFirstFitDecreased(Collection<XHost> hosts) {
        this(hosts, new Random(SimulatorProperties.getSeed()).nextInt());
    }

    public LazyFirstFitDecreased(Collection<XHost> hosts, Integer id) {
        super(hosts, id);
    }

    @Override
    protected void manageOverloadedHost(List<XHost> overloadedHosts, ComputingResult result) {
        // The VMs are sorted by decreasing size of CPU and RAM capacity
        TreeSet<XVM> toSchedule = new TreeSet<>(new XVMComparator(true, useLoad));
        Map<XVM, XHost> sources = new HashMap<>();

        for(XHost host: SimulatorManager.getSGHostingHosts()) {
            predictedCPUDemand.put(host, host.getCPUDemand());
            predictedMemDemand.put(host, host.getMemDemand());
        }

        // Remove enough VMs so the overloaded hosts are no longer overloaded
        for(XHost host : overloadedHosts) {
            Iterator<XVM> vms = host.getRunnings().iterator();

            while((host.getCPUCapacity() < predictedCPUDemand.get(host) ||
                    host.getMemSize() < host.getMemDemand()) && vms.hasNext()) {
                XVM vm = vms.next();
                toSchedule.add(vm);
                sources.put(vm, host);
                predictedCPUDemand.put(host, predictedCPUDemand.get(host) - vm.getCPUDemand());
                predictedMemDemand.put(host, predictedMemDemand.get(host) - vm.getMemSize());
            }
        }

        for(XVM vm: toSchedule) {
            XHost dest = null;

            // Try find a new host for the VMs (saneHosts is not sorted)
            for(XHost host: SimulatorManager.getSGHostingHosts()) {
                if(host.getCPUCapacity() >= predictedCPUDemand.get(host) + vm.getCPUDemand() &&
                        host.getMemSize() >= predictedMemDemand.get(host) + vm.getMemSize()) {
                    dest = host;
                    break;
                }
            }

            if(dest == null) {
                result.state = ComputingResult.State.RECONFIGURATION_FAILED;
                return;
            }

            // Schedule the migration
            predictedCPUDemand.put(dest, predictedCPUDemand.get(dest) + vm.getCPUDemand());
            predictedMemDemand.put(dest, predictedMemDemand.get(dest) + vm.getMemSize());
            XHost source = sources.get(vm);
            if(!source.getName().equals(dest.getName())) {
                migrations.add(new Migration(vm, source, dest));
            }
        }
    }
}
