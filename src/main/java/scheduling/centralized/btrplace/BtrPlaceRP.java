package scheduling.centralized.btrplace;

import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import org.btrplace.json.JSONConverterException;
import org.btrplace.json.model.InstanceConverter;
import org.btrplace.model.*;
import org.btrplace.model.constraint.MinMTTR;
import org.btrplace.model.constraint.Preserve;
import org.btrplace.model.constraint.SatConstraint;
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
public class BtrPlaceRP extends AbstractScheduler {

    /**
     * The BtrPlace scheduler
     */
    private ChocoScheduler btrSolver;

    /**
     * Map to link BtrPlace nodes ids to XHosts
     */
    private Map<Integer, String> nodesMap;

    /**
     * Map to link BtrPlace vm ids to XVMs
     */
    private Map<Integer, String> vmMap;

    /**
     * The initial configuration.
     */
    private Model source;

    /**
     * The configuration's constraints
     */
    private Set<SatConstraint> constraints;

    /**
     * The computed reconfiguration plan
     */
    private ReconfigurationPlan reconfigurationPlan;


    public BtrPlaceRP(Collection<XHost> xHosts, Integer id) {
        super();
        this.id = id;
        this.btrSolver = new DefaultChocoScheduler();

        /**
         * Adrian - From BtrPlace doc :
         * By default, BtrPlace considers every VMs when it solves a model.
         * This may lead to a non-reasonable solving process duration when
         * a few number of constraints are violated.
         * The repair approach addresses that problem by trying to reduce as possible
         * the number of VMs to consider in the model.
         */

        this.btrSolver.doRepair(true);
        this.btrSolver.doOptimize(true);
        this.btrSolver.setTimeLimit(15);

        this.extractConfiguration(xHosts);

        // log the model
        try {
            File file = new File("logs/btrplace/configuration/" + id + "-" + System.currentTimeMillis() + ".txt");
            file.getParentFile().mkdirs();

            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            Instance i = new Instance(source, new ArrayList<>(), new MinMTTR());
            InstanceConverter conv = new InstanceConverter();
            pw.write(conv.toJSON(i).toJSONString());
            pw.flush();
            pw.close();
        } catch (IOException | JSONConverterException e) {
            Msg.critical("BtrPlace : could not log the source model");
        }

    }

    public BtrPlaceRP(Collection<XHost> xHosts) {
        this(xHosts, new Random().nextInt());
    }

    /**
     * Creates a Model and constraints for BtrPlace
     *
     * @param xHosts Collection of Xhosts declared as hosting nodes and that are turned on
     */
    private void extractConfiguration(Collection<XHost> xHosts) {

        // Initialization
        this.source = new DefaultModel();
        Mapping mapping = this.source.getMapping();

        this.nodesMap = new HashMap<>();
        this.vmMap = new HashMap<>();

        // Creation of a view for defining CPU & Memory resources
        ShareableResource rcCPU = new ShareableResource("cpu", SimulatorProperties.DEFAULT_CPU_CAPACITY, 0);
        ShareableResource rcMem = new ShareableResource("mem", SimulatorProperties.DEFAULT_MEMORY_TOTAL, 0);

        this.constraints = new HashSet<>();

        // Add nodes
        for (XHost tmpH : xHosts) {
            // Consider only hosts that are turned on
            if (tmpH.isOff()) {
                 Msg.critical("Trying to analyze a dead node (" + tmpH.getName() + ")");
            }

            // Creates a physical node
            Node n = this.source.newNode();
            this.nodesMap.put(n.id(), tmpH.getName());

            // Add physical node to mapping
            mapping.addOnlineNode(n);

            // Node's resources are explicitly set
            rcCPU.setCapacity(n, tmpH.getCPUCapacity());
            rcMem.setCapacity(n, tmpH.getMemSize());

            if (tmpH.isViable()) {
                // If the host if viable, the model is exactly has the VM demand regarding cpu and memory usage
                // Declare running VMs mapping
                for (XVM tmpVM : tmpH.getRunnings()) {
                    VM v = this.source.newVM();
                    mapping.addRunningVM(v, n);
                    this.vmMap.put(v.id(), tmpVM.getName());
                    rcCPU.setConsumption(v, (int) tmpVM.getCPUDemand());
                    rcMem.setConsumption(v, tmpVM.getMemSize());

                }
            } else {
                // The host is not viable : we create a model based on a fair share of the host resources

                int cpuFairShare = tmpH.getCPUCapacity() / tmpH.getNbVMs();
                int memFairShare = tmpH.getMemSize() / tmpH.getNbVMs();

                for (XVM tmpVM : tmpH.getRunnings()) {
                    VM v = this.source.newVM();
                    mapping.addRunningVM(v, n);
                    this.vmMap.put(v.id(), tmpVM.getName());

                    rcCPU.setConsumption(v, cpuFairShare);
                    rcMem.setConsumption(v, memFairShare);

                    this.constraints.add(new Preserve(v, "cpu", (int) tmpVM.getCPUDemand()));
                    this.constraints.add(new Preserve(v, "mem", tmpVM.getMemSize()));
                }

            }

        }

        this.source.attach(rcCPU);
        this.source.attach(rcMem);

    }

    /**
     * Computes the reconfiguration plan and measure the duration of the computation.
     * @return the result of the reconfiguration (i.e. > 0), -1 there is no viable reconfiguration, -2 the reconfiguration crash
     */
    public ComputingState computeReconfigurationPlan() {
        try {
            timeToComputeVMRP = System.currentTimeMillis();
            reconfigurationPlan = this.btrSolver.solve(source, constraints);
            timeToComputeVMRP = System.currentTimeMillis() - timeToComputeVMRP;
        } catch (SchedulerException e) {
            timeToComputeVMRP = System.currentTimeMillis() - timeToComputeVMRP;
            reconfigurationPlan = null;
            Msg.critical("An error occurred while solving the model : " + e.getCause());
            return ComputingState.RECONFIGURATION_FAILED;
        }

        if (reconfigurationPlan == null)
            return ComputingState.RECONFIGURATION_FAILED;
        else if (reconfigurationPlan.getActions().isEmpty())
            return ComputingState.NO_RECONFIGURATION_NEEDED;
        else
            return ComputingState.SUCCESS;

    }

    /**
     * @param hostsToCheck The XHost to check for violations
     * @return A SchedulerResult representing the success or failure of the algorithm loop
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
            Msg.info("Configuration remains unchanged");
            enRes.setResult(SchedulerResult.State.NO_RECONFIGURATION_NEEDED);
        } else if (computingState.equals(ComputingState.SUCCESS)) {

			/* Tracing code */
            // Note Adrian : it is difficult with BtrPlace to isolate the impacted XHosts
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
     * Apply the reconfiguration plan using BtrPlace EventListeners
     */
    protected void applyReconfigurationPlan() {
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
             * Adrian - We add a Action Committed Listener for it to also execute business code for VMPlaces
             */
            reconfigurationPlan.getReconfigurationApplier().addEventCommittedListener(new EventCommittedListener() {
                /*
                 * Those methods will be called upon completion of an action.
                 * It's just the right time for us to actually do the action in SimGrid !
                 */

                @Override
                public void committed(Allocate allocate) {}

                @Override
                public void committed(AllocateEvent allocateEvent) {}

                @Override
                public void committed(SubstitutedVMEvent substitutedVMEvent) {}

                @Override
                public void committed(BootNode bootNode) {}

                @Override
                public void committed(BootVM bootVM) {}

                @Override
                public void committed(ForgeVM forgeVM) {
                }

                @Override
                public void committed(KillVM killVM) {}

                @Override
                public void committed(MigrateVM migrateVM) {
                    // Adrian - Naive implementation using the existing relocateVM method
                    relocateVM(
                            vmMap.get(migrateVM.getVM().id()),
                            nodesMap.get(migrateVM.getSourceNode().id()),
                            nodesMap.get(migrateVM.getDestinationNode().id())
                    );
                }

                @Override
                public void committed(ResumeVM resumeVM) {
                    resumeVM(
                            vmMap.get(resumeVM.getVM().id()),
                            nodesMap.get(resumeVM.getSourceNode().id())
                    );

                }

                @Override
                public void committed(ShutdownNode shutdownNode) {}

                @Override
                public void committed(ShutdownVM shutdownVM) {}

                @Override
                public void committed(SuspendVM suspendVM) {
                    suspendVM(
                            vmMap.get(suspendVM.getVM().id()),
                            nodesMap.get(suspendVM.getSourceNode().id())
                    );

                }
            });

            // We now roll out the reconfiguration plan
            reconfigurationPlan.getResult();

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
                            Msg.critical("The reconfiguration is taking too long - Forcing termination...");
                            System.exit(-1);
                        }
                    }
                } catch (HostFailureException e) {
                    Msg.critical("Host crashed while reconfiguring : " + e.getLocalizedMessage());
                }
            }
        }
    }

}
