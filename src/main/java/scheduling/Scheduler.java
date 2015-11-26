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

        /**
         * Number of migrations in the computed plan
         */
        public int nbMigrations;

        /**
         * Duration of the computing process
         */
        public long duration;

        /**
         * 	The cost of the reconfiguration plan.
         */
        protected int planCost;

        public ComputingResult(State state, long duration, int nbMigrations, int planCost) {
            this.state = state;
            this.duration = duration;
            this.nbMigrations = nbMigrations;
            this.planCost = planCost;
        }

        public ComputingResult(State state, long duration) {
            this(state, duration, 0, 0);
        }

        public ComputingResult() { this(State.SUCCESS, 0, 0, 0); }

    }


    /**
     * Result of the reconfiguration.
     */
    class SchedulerResult {

        /**
         * Result of the Scheduling process.
         */
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
