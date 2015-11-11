package scheduling;

import configuration.XHost;

import java.util.Collection;

public interface Scheduler {

    enum ComputingState {
        NO_RECONFIGURATION_NEEDED("NO_RECONFIGURATION_NEEDED"), PLACEMENT_FAILED("PLACEMENT_FAILED"), RECONFIGURATION_FAILED("RECONFIGURATION_FAILED"), SUCCESS("SUCCESS");
        private String name;
        private ComputingState(String name){
            this.name = name;
        }
        public String toString(){
            return name;
        }
    }

    ComputingState computeReconfigurationPlan();
    SchedulerRes checkAndReconfigure(Collection<XHost> hostsToCheck);
    void applyReconfigurationPlan();

}
