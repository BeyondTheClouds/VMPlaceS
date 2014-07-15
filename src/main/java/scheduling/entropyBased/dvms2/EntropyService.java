package scheduling.entropyBased.dvms2;

/* ============================================================
 * Discovery Project - DVMS
 * http://beyondtheclouds.github.io/
 * ============================================================
 * Copyright 2013 Discovery Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============================================================ */

//import akka.actor.ActorRef;
import dvms.scheduling.ComputingState;
import entropy.configuration.Configuration;
import entropy.configuration.SimpleManagedElementSet;
import entropy.execution.Dependencies;
import entropy.execution.TimedExecutionGraph;
import entropy.plan.PlanException;
import entropy.plan.TimedReconfigurationPlan;
import entropy.plan.action.Action;
import entropy.plan.action.Migration;
import entropy.plan.choco.ChocoCustomRP;
import entropy.plan.durationEvaluator.MockDurationEvaluator;
import entropy.vjob.DefaultVJob;
import entropy.vjob.VJob;
import org.discovery.DiscoveryModel.model.ReconfigurationModel.*;
import org.discovery.dvms.dvms.DvmsModel.*;
import java.util.*;

public class EntropyService {

//    public ActorRef loggingActorRef = null;

    private static EntropyService instance = null;

    private ChocoCustomRP planner = null;

    private EntropyService() {
        planner = new ChocoCustomRP(new MockDurationEvaluator(2, 5, 1, 1, 7, 14, 7, 2, 4));
        planner.setTimeLimit(2);
    }

    public static EntropyService getInstance() {
        if (instance == null) {
            instance = new EntropyService();
        }

        return instance;
    }

//    public static void setLoggingActorRef(ActorRef loggingActorRef) {
//        getInstance().loggingActorRef = loggingActorRef;
//    }

    public ChocoCustomRP getPlanner() {
        return planner;
    }

    public static ReconfigurationResult computeReconfigurationPlan(Configuration configuration, List<PhysicalNode> machines) {

        Map<String, List<ReconfigurationAction>> actions = new HashMap<String, List<ReconfigurationAction>>();

        // Alert LoggingActor that EntropyService begin a computation
//        if (DvmsConfiguration.IS_G5K_MODE()) {
//            getInstance().loggingActorRef.tell(
//                    new LoggingProtocol.ComputingSomeReconfigurationPlan(ExperimentConfiguration.getCurrentTime()),
//                    null
//            );
//        }


        for (entropy.configuration.Node node : configuration.getAllNodes()) {

            System.out.println(String.format("{ name: \"%s\", cpuCount: %d, cpuCapacity: %d, memory: %d, vms: [",
                    node.getName(),
                    node.getNbOfCPUs(),
                    node.getCPUCapacity(),
                    node.getMemoryCapacity()
            ));

            for (entropy.configuration.VirtualMachine virtualMachine : configuration.getRunnings(node)) {
                System.out.println(String.format("  { name: \"%s\", cpuCount: %d, cpuCapacity: {consumption: %d, demand: %d, max: %d}, memory: {consumption: %d, demand: %d} }",
                        virtualMachine.getName(),
                        virtualMachine.getNbOfCPUs(),
                        virtualMachine.getCPUConsumption(),
                        virtualMachine.getCPUDemand(),
                        virtualMachine.getCPUMax(),
                        virtualMachine.getMemoryConsumption(),
                        virtualMachine.getMemoryDemand()
                ));
            }

            System.out.println("]}");
        }


        ComputingState res = ComputingState.VMRP_SUCCESS;

        List<VJob> vjobs = new ArrayList<VJob>();
        DefaultVJob v = new DefaultVJob("v1");

        v.addVirtualMachines(configuration.getRunnings());
        vjobs.add(v);

        TimedReconfigurationPlan reconfigurationPlan = null;

        try {
            reconfigurationPlan = getInstance().getPlanner().compute(configuration,
                    configuration.getRunnings(),
                    configuration.getWaitings(),
                    configuration.getSleepings(),
                    new SimpleManagedElementSet(),
                    configuration.getOnlines(),
                    configuration.getOfflines(),
                    vjobs
            );
        } catch (PlanException e) {
            e.printStackTrace();
            e.printStackTrace(System.out);
            System.out.println("Entropy: No solution :(");
            System.err.println("Entropy: No solution :(");
            res = ComputingState.VMRP_FAILED;
        }

        int nbMigrations = 0;

        if (reconfigurationPlan != null) {
            if (reconfigurationPlan.getActions().isEmpty())
                res = ComputingState.NO_RECONFIGURATION_NEEDED;

            nbMigrations = computeNbMigrations(reconfigurationPlan, machines);


            try {
                // Alert LoggingActor that EntropyService apply a reconfiguration plan
//                if (DvmsConfiguration.IS_G5K_MODE()) {
//                    getInstance().loggingActorRef.tell(
//                            new LoggingProtocol.ApplyingSomeReconfigurationPlan(ExperimentConfiguration.getCurrentTime()),
//                            null
//                    );
//                }

                actions = applyReconfigurationPlanLogically(reconfigurationPlan, configuration, machines);

            } catch (Exception e) {

                e.printStackTrace();
            } finally {

                // Alert LoggingActor that migrationCount has changed
//                if (DvmsConfiguration.IS_G5K_MODE()) {
//                    ExperimentConfiguration.incrementMigrationCount(nbMigrations);
//                    getInstance().loggingActorRef.tell(
//                            new LoggingProtocol.UpdateMigrationCount(
//                                    ExperimentConfiguration.getCurrentTime(),
//                                    ExperimentConfiguration.getMigrationCount()
//                            ),
//                            null
//                    );
//                }
            }

        }

        if(res != ComputingState.VMRP_FAILED) {
            return new ReconfigurationSolution(actions);
        } else {
            return new ReconfigurationlNoSolution();
        }
    }

    //Get the number of migrations
    private static int computeNbMigrations(TimedReconfigurationPlan reconfigurationPlan, List<PhysicalNode> machines) {
        int nbMigrations = 0;

        for (Action a : reconfigurationPlan.getActions()) {
            if (a instanceof Migration) {
                nbMigrations++;
            }
        }

        return nbMigrations;
    }

    //Get the depth of the reconfiguration graph
    //May be compared to the number of steps in Entropy 1.1.1
    //Return 0 if there is no action, and (1 + maximum number of dependencies) otherwise
    private static int computeReconfigurationGraphDepth(TimedReconfigurationPlan reconfigurationPlan, List<PhysicalNode> machines) {
        if (reconfigurationPlan.getActions().isEmpty()) {
            return 0;
        } else {
            int maxNbDeps = 0;
            TimedExecutionGraph g = reconfigurationPlan.extractExecutionGraph();
            int nbDeps;

            //Set the reverse dependencies map
            for (Dependencies dep : g.extractDependencies()) {
                nbDeps = dep.getUnsatisfiedDependencies().size();

                if (nbDeps > maxNbDeps)
                    maxNbDeps = nbDeps;
            }

            return 1 + maxNbDeps;
        }
    }

    private static boolean addReconfigurationActionsToPreviousActions(HashMap<String, List<ReconfigurationAction>> previousActions, HashMap<String, List<ReconfigurationAction>> newActions) {

        for(Map.Entry<String, List<ReconfigurationAction>> action : newActions.entrySet()) {
            if(!previousActions.containsKey(action.getKey())) {
                previousActions.put(action.getKey(), action.getValue());
            } else {
                List<ReconfigurationAction> existingActions = previousActions.get(action.getKey());
                existingActions.addAll(action.getValue());
                previousActions.put(action.getKey(), existingActions);
            }
        }

        return true;
    }

    //Apply the reconfiguration plan logically (i.e. create/delete Java objects)
    private static Map<String, List<ReconfigurationAction>> applyReconfigurationPlanLogically(TimedReconfigurationPlan reconfigurationPlan, Configuration conf, List<PhysicalNode> machines) throws InterruptedException {




        System.out.println("Computing reconfiguration plan");

        HashMap<String, List<ReconfigurationAction>> actions = new HashMap<String, List<ReconfigurationAction>>();

        Map<Action, List<Dependencies>> revDependencies = new HashMap<Action, List<Dependencies>>();
        TimedExecutionGraph g = reconfigurationPlan.extractExecutionGraph();

        //Set the reverse dependencies map
        for (Dependencies dep : g.extractDependencies()) {
            for (Action a : dep.getUnsatisfiedDependencies()) {
                if (!revDependencies.containsKey(a)) {
                    revDependencies.put(a, new LinkedList<Dependencies>());
                }
                revDependencies.get(a).add(dep);
            }
        }

        //Start the feasible actions
        // ie, actions with a start moment equals to 0.
        for (Action a : reconfigurationPlan) {

            if (a.getStartMoment() == 0) {

                HashMap<String, List<ReconfigurationAction>> someActions = getReconfigurationActions(a, conf, machines);
                addReconfigurationActionsToPreviousActions(actions, someActions);
            }

            if (revDependencies.containsKey(a)) {
                //Get the associated depenencies and update it
                for (Dependencies dep : revDependencies.get(a)) {
                    dep.removeDependency(a);
                    //Launch new feasible actions.
                    if (dep.isFeasible()) {

                        HashMap<String, List<ReconfigurationAction>> someActions = getReconfigurationActions(a, conf, machines);
                        addReconfigurationActionsToPreviousActions(actions, someActions);
                    }
                }
            }
        }

        return actions;
    }

    private static HashMap<String, List<ReconfigurationAction>> getReconfigurationActions(Action a, Configuration conf, List<PhysicalNode> machines) throws InterruptedException {


        HashMap<String, List<ReconfigurationAction>> actions = new HashMap<String, List<ReconfigurationAction>>();

        if (a instanceof Migration) {
            Migration migration = (Migration) a;

            String from = migration.getHost().getName();
            String to = migration.getDestination().getName();
            String vmName = migration.getVirtualMachine().getName();

            System.out.println(String.format("  * migration of %s from %s to %s",
                    vmName,
                    from,
                    to
            ));

            if (!actions.containsKey(from)) {
                actions.put(from, new ArrayList<ReconfigurationAction>());
            }

            List<ReconfigurationAction> vmsToBeMigrated = actions.get(from);
            vmsToBeMigrated.add(new MakeMigration(from, to, vmName));
            actions.put(from, vmsToBeMigrated);

        } else {
            System.err.println("UNRECOGNIZED ACTION WHEN APPLYING THE RECONFIGURATION PLAN");
        }

        return actions;
    }

    public static void main(String args[]) {


//        ExperimentConfiguration.startExperiment();
        try {
            Thread.sleep(1332);
        } catch (InterruptedException e) {

        }
//        System.out.println(ExperimentConfiguration.getCurrentTime());
    }
}
