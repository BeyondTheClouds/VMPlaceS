package scheduling.centralized.ffd;

import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.Msg;
import simulation.SimulatorManager;

import java.util.*;

public class OptimisticFirstFitDecreased extends FirstFitDecreased {

    public OptimisticFirstFitDecreased(Collection<XHost> hosts) {
        this(hosts, new Random(SimulatorProperties.getSeed()).nextInt());
    }

    public OptimisticFirstFitDecreased(Collection<XHost> hosts, Integer id) {
        super(hosts, id);
    }

    @Override
    protected void manageOverloadedHost(List<XHost> overloadedHosts, ComputingResult result) {
        TreeSet<XVM> toSchedule = new TreeSet<>(new XVMComparator(true, useLoad));
        Map<XVM, XHost> sources = new HashMap<>();

        for(XHost host: SimulatorManager.getSGHostingHosts()) {
            predictedCPUDemand.put(host, host.getCPUDemand());
            predictedMemDemand.put(host, host.getMemDemand());
        }

        // Remove all VMs from the overloaded hosts
        for(XHost host: overloadedHosts) {
            for(XVM vm: host.getRunnings()) {
                toSchedule.add(vm);
                sources.put(vm, host);
            }

            predictedCPUDemand.put(host, 0D);
            predictedMemDemand.put(host, 0);
        }

        for(XVM vm: toSchedule) {
            XHost dest = null;

            // Try find a new host for the VMs
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

            if(predictedCPUDemand.get(dest) >= dest.getCPUCapacity())
                System.out.println("!!");
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
