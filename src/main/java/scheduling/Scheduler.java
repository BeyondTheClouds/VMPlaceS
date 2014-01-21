package scheduling;

import configuration.XHost;

import java.util.Collection;

/**
 * Created with IntelliJ IDEA.
 * User: alebre
 * Date: 14/01/14
 * Time: 18:27
 * To change this template use File | Settings | File Templates.
 */
public interface Scheduler {

    public enum ComputingState {
        NO_RECONFIGURATION_NEEDED("NO_RECONFIGURATION_NEEDED"), PLACEMENT_FAILED("PLACEMENT_FAILED"), RECONFIGURATION_FAILED("RECONFIGURATION_FAILED"), SUCCESS("SUCCESS");
        private String name;

        private ComputingState(String name){
            this.name = name;
        }
        public String toString(){
            return name;
        }
    };

    ComputingState computeReconfigurationPlan();
    int getReconfigurationPlanCost();
    void applyReconfigurationPlan();
}
