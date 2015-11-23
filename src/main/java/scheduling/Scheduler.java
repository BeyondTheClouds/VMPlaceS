package scheduling;

import configuration.XHost;

import java.util.Collection;

/**
 * Contract that must be followed by the implemented schedulers.
 */
public interface Scheduler {

    ComputingState computeReconfigurationPlan();
    SchedulerResult checkAndReconfigure(Collection<XHost> hostsToCheck);

    /**
     * Result of the reconfiguration plan computation.
     */
    enum ComputingState {

        NO_RECONFIGURATION_NEEDED("NO_RECONFIGURATION_NEEDED"),
        PLACEMENT_FAILED("PLACEMENT_FAILED"),
        RECONFIGURATION_FAILED("RECONFIGURATION_FAILED"),
        SUCCESS("SUCCESS");

        private String name;

        private ComputingState(String name){
            this.name = name;
        }

        public String toString(){
            return name;
        }

    }

    /**
     * Result of the reconfiguration.
     */
    class SchedulerResult {

        public enum State {

            RECONFIGURATION_PLAN_ABORTED("RECONFIGURATION_PLAN_ABORTED"),
            NO_VIABLE_CONFIGURATION("NO_VIABLE_CONFIGURATION"),
            NO_RECONFIGURATION_NEEDED("NO_RECONFIGURATION_NEEDED"),
            SUCCESS("SUCCESS");

            private String name;

            private State(String name){
                this.name = name;
            }

            public String toString(){
                return name;
            }

        }

        /**
         * Result of the reconfiguration.
         */
        private State result;

        /**
         * Duration in ms of the reconfiguration.
         */
        private long duration;

        public SchedulerResult(){
            this.result = State.SUCCESS;
            this.duration = 0;
        }

        public void setResult(State result) {
            this.result = result;
        }

        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public State getResult() {
            return result;
        }

    }

}
