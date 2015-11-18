package scheduling.centralized.btrplace;

import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import org.btrplace.model.DefaultModel;
import org.btrplace.model.Mapping;
import org.btrplace.model.Model;
import org.btrplace.model.VM;
import org.btrplace.model.Node;
import org.btrplace.model.view.ShareableResource;
import org.btrplace.plan.ReconfigurationPlan;
import org.btrplace.plan.event.*;
import org.btrplace.scheduler.SchedulerException;
import org.btrplace.scheduler.choco.ChocoScheduler;
import org.btrplace.scheduler.choco.DefaultChocoScheduler;
import org.simgrid.msg.Host;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.Msg;
import scheduling.AbstractScheduler;
import simulation.SimulatorManager;
import trace.Trace;

import java.io.*;
import java.util.*;

/**
 * @author Adrian Fraisse
 *
 * Implementation of the Scheduler interface using the BtrPlace API
 */
public class BtrPlaceRP extends AbstractScheduler<ChocoScheduler, Model, ReconfigurationPlan> {

    /**
     * Map to link BtrPlace nodes ids to XHosts
     */
    private Map<Integer, String> nodesMap;

    /**
     * Map to link BtrPlace vm ids to XVMs
     */
    private Map<Integer, String> vmMap;

    public BtrPlaceRP(Collection<XHost> xHosts, Integer id) {
        super(xHosts);
        this.id = id;
        this.planner = new DefaultChocoScheduler();

        // log the model
        try {
            File file = new File("logs/btrplace/configuration/" + id + "-" + System.currentTimeMillis() + ".txt");
            file.getParentFile().mkdirs();
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            pw.write(this.source.toString());
            pw.flush();
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public BtrPlaceRP(Collection<XHost> xHosts) {
        this(xHosts, new Random().nextInt());
    }

    /**
     * Creates a Model for BtrPlace
     * @param xHosts Collection of Xhosts declared as hosting nodes and that are turned on
     * @return A model representing the infrastructure
     */
    protected Model extractConfiguration(Collection<XHost> xHosts) {

        // Initialization
        Model model = new DefaultModel();
        Mapping mapping = model.getMapping();

        this.nodesMap = new HashMap<>();
        this.vmMap = new HashMap<>();

        // Creation of a view for defining CPU & Memory resources
        ShareableResource rcCPU = new ShareableResource("cpu", SimulatorProperties.DEFAULT_CPU_CAPACITY, 0);
        ShareableResource rcMem = new ShareableResource("mem", SimulatorProperties.DEFAULT_MEMORY_TOTAL, 0);

        // Add nodes
        for (XHost tmpH : xHosts) {
            // Consider only hosts that are turned on
            if (tmpH.isOff()) {
                System.err.println("WTF, you are asking me to analyze a dead node (" + tmpH.getName() + ")");
            }

            // Creates a physical node
            Node n = model.newNode();
            this.nodesMap.put(n.id(), tmpH.getName());

            // Ajout de la machine physique au mapping
            mapping.addOnlineNode(n);

            // Node's resources are explicitly set
            rcCPU.setCapacity(n, tmpH.getCPUCapacity());
            rcMem.setCapacity(n, tmpH.getMemSize());


            // Declare running VMs mapping
            for (XVM tmpVM : tmpH.getRunnings()) {
                VM v = model.newVM();
                mapping.addRunningVM(v, n);
                this.vmMap.put(v.id(), tmpVM.getName());
                rcCPU.setConsumption(v, (int) tmpVM.getCPUDemand());
                rcMem.setConsumption(v, tmpVM.getMemSize());
            }

        }

        model.attach(rcCPU);
        model.attach(rcMem);

        return model;
    }

    public ComputingState computeReconfigurationPlan() {
        ComputingState res = ComputingState.SUCCESS;

        try {
            timeToComputeVMRP = System.currentTimeMillis();
            /**
             * Adrian - From BtrPlace doc :
             * By default, BtrPlace considers every VMs when it solves a model.
             * This may lead to a non-reasonnable solving process duration when
             * a few number of constraints are violated.
             * The repair approach addresses that problem by trying to reduce as possible
             * the number of VMs to consider in the model.
             */
            //this.planner.doRepair();
            // As for now, constraints are not implemented - Adrian, Nov 5 2015
            reconfigurationPlan = this.planner.solve(source, new ArrayList<>());
            timeToComputeVMRP = System.currentTimeMillis() - timeToComputeVMRP;
        } catch (SchedulerException e) {
            e.printStackTrace();
            res = ComputingState.RECONFIGURATION_FAILED ;
            timeToComputeVMRP = System.currentTimeMillis() - timeToComputeVMRP;
            reconfigurationPlan = null;
        }

        if(reconfigurationPlan != null){
            if(reconfigurationPlan.getActions().isEmpty())
                res = ComputingState.NO_RECONFIGURATION_NEEDED;
            if (!reconfigurationPlan.isApplyable())
                res = ComputingState.RECONFIGURATION_FAILED;

            planCost = reconfigurationPlan.getDuration();
            // TODO Adrian : Compute graphDepth & nbMigrations - if it's meaningful
        } else {
            res = ComputingState.RECONFIGURATION_FAILED;
        }

        return res;
    }


    /**
     * @param hostsToCheck
     * @return the duration of the reconfiguration (i.e. > 0), -1 there is no viable reconfiguration, -2 the reconfiguration crash
     */
    public SchedulerResult checkAndReconfigure(Collection<XHost> hostsToCheck) {

        long beginTimeOfCompute;
        long endTimeOfCompute;
        long computationTime;
        ComputingState computingState;
        long reconfigurationTime;
        SchedulerResult enRes = new SchedulerResult();

		/* Tracing code */
        for (XHost h : hostsToCheck) {
            if (!h.isViable())
                Trace.hostPushState(h.getName(), "PM", "violation-det");
            Trace.hostSetState(h.getName(), "SERVICE", "booked");
        }

        Msg.info("Launching scheduler (id = " + id + ") - start to compute");
        Msg.info("Nodes considered: " + source.getMapping().getAllNodes().toString());

        /** PLEASE NOTE THAT ALL COMPUTATIONS BELOW DOES NOT MOVE FORWARD THE MSG CLOCK ***/
        beginTimeOfCompute = System.currentTimeMillis();
        computingState = this.computeReconfigurationPlan();
        endTimeOfCompute = System.currentTimeMillis();
        computationTime = (endTimeOfCompute - beginTimeOfCompute);

        /* Tracing code */
        double computationTimeAsDouble = ((double) computationTime) / 1000;

        int migrationCount = 0;
        if (computingState.equals(ComputingState.SUCCESS)) {
            migrationCount = this.reconfigurationPlan.getSize();
        }

        int partitionSize = hostsToCheck.size();

        /** **** NOW LET'S GO BACK TO THE SIMGRID WORLD **** */

        Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "compute", String.format("{\"duration\" : %f, \"result\" : \"%s\", \"migration_count\": %d, \"psize\": %d}", computationTimeAsDouble, computingState, migrationCount, partitionSize));


        try {
            org.simgrid.msg.Process.sleep(computationTime); // instead of waitFor that takes into account only seconds
        } catch (HostFailureException e) {
            e.printStackTrace();
        }

        Msg.info("Computation time (in ms):" + computationTime);
        enRes.setDuration(computationTime);

        if (computingState.equals(ComputingState.NO_RECONFIGURATION_NEEDED)) {
            Msg.info("Configuration remains unchanged"); //res is already set to 0.
        } else if (computingState.equals(ComputingState.SUCCESS)) {

			/* Tracing code */
            // TODO Adrien -> Adrien, try to consider only the nodes that are impacted by the reconfiguration plan
            for (XHost h : hostsToCheck)
                Trace.hostSetState(h.getName(), "SERVICE", "reconfigure");

            Trace.hostPushState(Host.currentHost().getName(), "SERVICE", "reconfigure");

            // Applying reconfiguration plan
            Msg.info("Starting reconfiguration");
            double startReconfigurationTime = Msg.getClock();
            this.applyReconfigurationPlan();
            double endReconfigurationTime = Msg.getClock();
            reconfigurationTime = ((long) (endReconfigurationTime - startReconfigurationTime) * 1000);
            Msg.info("Reconfiguration time (in ms): " + reconfigurationTime);
            enRes.setDuration(enRes.getDuration() + reconfigurationTime);
            Msg.info("Number of nodes used: " + hostsToCheck.size());

            if (this.rpAborted)
                enRes.setResult(SchedulerResult.State.RECONFIGURATION_PLAN_ABORTED);
            else
                enRes.setResult(SchedulerResult.State.SUCCESS);

            Trace.hostPopState(Host.currentHost().getName(), "SERVICE"); //PoP reconfigure;
        } else {
            Msg.info("BtrPlace did not find any viable solution");
            enRes.setResult(SchedulerResult.State.NO_VIABLE_CONFIGURATION);
        }

		/* Tracing code */
        for (XHost h : hostsToCheck)
            Trace.hostSetState(h.getName(), "SERVICE", "free");

        Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "free");
        return enRes;
    }

    /**
     * This is a prototype to test out the possibility to
     * override BtrPlace EventListeners with the relocateVM behavior
     */
    public void applyReconfigurationPlan() {
        if (this.reconfigurationPlan != null && reconfigurationPlan.isApplyable()) {

            // We log the reconfiguration plan
            try {
                File file = new File("logs/btrplace/reconfigurationplan/" + id + "-" + System.currentTimeMillis() + ".txt");
                file.getParentFile().mkdirs();
                PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
                pw.write(this.reconfigurationPlan.toString());
                pw.flush();
                pw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            /**
             * Adrian - We add a ActionCommitedListener for it to
             * also execute business code for VMPlaces
             * Todo refactor the Listener into a non-anonymous class
             */
            reconfigurationPlan.getReconfigurationApplier().addEventCommittedListener(new EventCommittedListener() {
                /*
                 * Those methods will be called upon completion of an action.
                 * It's just the right time for us to actually do the action in SimGrid !
                 */

                @Override
                public void committed(Allocate allocate) {
                    // Todo - is that necessary ?
                }

                @Override
                public void committed(AllocateEvent allocateEvent) {
                    // Todo - is that necessary ?
                }

                @Override
                public void committed(SubstitutedVMEvent substitutedVMEvent) {
                    // Todo - is that necessary ?
                }

                @Override
                public void committed(BootNode bootNode) {
                    // Todo - is that necessary ?
                }

                @Override
                public void committed(BootVM bootVM) {
                    // Todo - is that necessary ?
                }

                @Override
                public void committed(ForgeVM forgeVM) {
                    // Todo - is that necessary ?
                }

                @Override
                public void committed(KillVM killVM) {
                    // Todo - is that necessary ?
                }

                @Override
                public void committed(MigrateVM migrateVM) {
                    // Adrian - Naive implementation using the existing relocateVM method
                    relocateVM(
                            vmMap.get(migrateVM.getVM().id()),
                            nodesMap.get(migrateVM.getSourceNode().id()),
                            nodesMap.get(migrateVM.getDestinationNode().id())
                    );
                    // TODO Adrian : I'm unsure if this will block execution and/or induce launching migrations without the proper dependencies.
                }

                @Override
                public void committed(ResumeVM resumeVM) {
                    resumeVM(
                            vmMap.get(resumeVM.getVM().id()),
                            nodesMap.get(resumeVM.getSourceNode().id())
                    );

                }

                @Override
                public void committed(ShutdownNode shutdownNode) {
                    // Todo - is that necessary ?
                }

                @Override
                public void committed(ShutdownVM shutdownVM) {
                    // Todo - is that necessary ?
                }

                @Override
                public void committed(SuspendVM suspendVM) {
                    suspendVM(
                            vmMap.get(suspendVM.getVM().id()),
                            nodesMap.get(suspendVM.getSourceNode().id())
                    );

                }
            });

            // We now roll out the reconfiguration plan
            this.destination = reconfigurationPlan.getResult();

            // If you reach that line, it means that either the execution of the plan has been completely launched or the
            // plan has been aborted. In both cases, we should wait for the completion of on-going migrations

            // Add a watch dog to determine infinite loop
            int watchDog = 0;

            while(this.ongoingMigrations()){
                try {
                    org.simgrid.msg.Process.getCurrentProcess().waitFor(1);
                    watchDog ++;
                    if (watchDog%100==0){
                        Msg.info("You're are waiting for a couple of seconds (already "+watchDog+" seconds)");
                        if(SimulatorManager.isEndOfInjection()){
                            Msg.info("Something wrong we are waiting too much, bye bye");
                            System.exit(-1);
                        }
                    }
                } catch (HostFailureException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean instantiateAndStartRecursively(Action a) {

        // Iterate over the dependencies
        reconfigurationPlan.getDirectDependencies(a).forEach(this::instantiateAndStartRecursively);

        // The dependencies have been rolled out - we can execute the action
        if (a instanceof MigrateVM) {

            MigrateVM migration = (MigrateVM) a;
            // Adrian - Naive implementation using the existing relocateVM method
            super.relocateVM(
                    this.vmMap.get(migration.getVM().id()),
                    this.nodesMap.get(migration.getSourceNode().id()),
                    this.nodesMap.get(migration.getDestinationNode().id())
            );
            a.applyAction(reconfigurationPlan.getOrigin());
            // TODO Adrian - How do we update the dependencies ?
        } else {
            System.err.println("UNRECOGNIZED ACTION WHEN APPLYING THE RECONFIGURATION PLAN : " + a.pretty());
        }
        return true;
    }

}
