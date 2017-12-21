package scheduling.example;

import configuration.XHost;
import configuration.XVM;
import migration.MigrationPlan;
import scheduling.AbstractScheduler;
import simulation.SimulatorManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ExampleReconfigurationPlanner extends AbstractScheduler {

    private ComputingResult lastComputingResult = null;
    private List<MigrationPlan> migrationsPlans = new ArrayList<MigrationPlan>();

    public ComputingResult computeReconfigurationPlan(){
        ComputingResult result = new ComputingResult();
        lastComputingResult = result;

        Collection<XHost> hosts = SimulatorManager.getSGHostingHosts();
        List<XHost> overloadedHosts = new ArrayList<>();
        List<XHost> underloadedHosts = new ArrayList<>();

        // Find the overloaded hosts
        for(XHost host : hosts) {
            double demand = host.computeCPUDemand();
            if (host.getCPUCapacity() < demand)
                overloadedHosts.add(host);
            else if(demand < 0.5 * host.getCPUCapacity()) {
                underloadedHosts.add(host);
            }
        }

        if (underloadedHosts.size() > 0) {
            // Migrate some VMs from overloaded hosts to underloaded hosts
            int index = 0;
            for (XHost overloadedHost : overloadedHosts) {
                // Prepare a future migration
                MigrationPlan migrationPlan = new MigrationPlan();
                // Pick an underloaded host
                XHost underloadedHost = underloadedHosts.get(index % underloadedHosts.size());
                // Pick a VM
                XVM vm = overloadedHost.getVMs().iterator().next();
                migrationPlan.origin = overloadedHost;
                migrationPlan.destination = underloadedHost;
                migrationPlan.vm = vm;
                // Add the migration order to order
                migrationsPlans.add(migrationPlan);
                index += 1;
            }
        }

        return result;
    }

    public void applyReconfigurationPlan() {
        System.out.println("Applying reconfiguration plan");
        // For each migration decided by the "computeReconfigurationPlan",
        // the migration will be started in the following block
        for(MigrationPlan migrationPlan: this.migrationsPlans) {

            // Check if the destination node is turned off. If it is
            // the case, the destination node is turned on
            if(migrationPlan.destination.isOff())
                SimulatorManager.turnOn(migrationPlan.destination);

            relocateVM(migrationPlan.vm.getName(),
                       migrationPlan.origin.getName(),
                       migrationPlan.destination.getName());
        }
        // Reset the count of migrations
        this.migrationsPlans = new ArrayList<MigrationPlan>();
    }
}
