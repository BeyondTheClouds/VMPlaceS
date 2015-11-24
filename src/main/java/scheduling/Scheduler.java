package scheduling;

import configuration.XHost;

import java.util.Collection;

/**
 * Contract that must be followed by the implemented schedulers.
 */
public interface Scheduler {

    ComputingResult computeReconfigurationPlan();
    SchedulerResult checkAndReconfigure(Collection<XHost> hostsToCheck);

    /**
     * Result of the reconfiguration plan computation.
     */
    class ComputingResult {

        public enum State {

            NO_RECONFIGURATION_NEEDED("NO_RECONFIGURATION_NEEDED"),
            PLACEMENT_FAILED("PLACEMENT_FAILED"),
            RECONFIGURATION_FAILED("RECONFIGURATION_FAILED"),
            SUCCESS("SUCCESS");

            private String name;

            State(String name){
                this.name = name;
            }

            public String toString(){
                return name;
            }

        }

        public State state;

        public int actionCount;

        public long duration;

        public ComputingResult(State state, long duration, int actionCount) {
            this.state = state;
            this.duration = duration;
            this.actionCount = actionCount;
        }

        public ComputingResult(State state, long duration) {
            this(state, duration, 0);
        }

        public ComputingResult() {
            this.state = State.SUCCESS;
        }

    }


    /**
     * Result of the reconfiguration.
     */
    class SchedulerResult {

        public enum State {

            SUCCESS("SUCCESS"),
            RECONFIGURATION_PLAN_ABORTED("RECONFIGURATION_PLAN_ABORTED"),
            NO_VIABLE_CONFIGURATION("NO_VIABLE_CONFIGURATION"),
            NO_RECONFIGURATION_NEEDED("NO_RECONFIGURATION_NEEDED");

            private String name;

            State(String name){
                this.name = name;
            }

            public String toString(){
                return name;
            }

        }

        /**
         * Result of the reconfiguration.
         */
        public State state;

        /**
         * Duration in ms of the reconfiguration.
         */
        public long duration;

    }

}
