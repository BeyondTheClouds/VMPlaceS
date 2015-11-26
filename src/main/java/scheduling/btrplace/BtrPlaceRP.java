package scheduling.btrplace;

import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import org.btrplace.json.JSONConverterException;
import org.btrplace.json.model.InstanceConverter;
import org.btrplace.model.VM;
import org.btrplace.model.constraint.*;
import org.btrplace.model.view.ShareableResource;
import org.btrplace.plan.ReconfigurationPlan;
import org.btrplace.plan.event.*;
import org.btrplace.scheduler.SchedulerException;
import org.btrplace.scheduler.choco.ChocoScheduler;
import org.btrplace.scheduler.choco.DefaultChocoScheduler;
import org.btrplace.model.*;
import org.simgrid.msg.Host;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.Msg;
import org.simgrid.msg.Process;
import scheduling.AbstractScheduler;
import scheduling.Scheduler;
import scheduling.SchedulerRes;
import simulation.SimulatorManager;
import trace.Trace;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BtrPlaceRP extends AbstractScheduler<ConfigBtrPlace, ReconfigurationPlan> implements Scheduler {

	private ChocoScheduler planner;
    private int loopID; //Adrien, just a hack to serialize configuration and reconfiguration into a particular file name
    private boolean abortRP;
    private int timeLimit = 15;

    public BtrPlaceRP(Collection<XHost> xhosts) {

        this(xhosts,new Random().nextInt());
    }

    public BtrPlaceRP(Collection<XHost> xhosts, int loopID) {
        super(BtrPlaceRP.ExtractConfiguration(xhosts));
        planner =  new DefaultChocoScheduler();
        planner.doRepair(true);
        planner.doOptimize(true);
        int time = initialConfiguration.getModel().getMapping().getAllNodes().size()/8;
        int limit = (time < timeLimit) ? timeLimit : time;
        planner.setTimeLimit(limit);
        this.loopID = loopID;
        this.abortRP = false;
        
        //Log the current Configuration
        try {
            String path = "logs/btrplace/configuration/" + loopID + "-"+ System.currentTimeMillis() + ".txt";
            Path pathToFile = Paths.get(path);
            Files.createDirectories(pathToFile.getParent());
            Files.createFile(pathToFile);
            FileWriter fw = new FileWriter(path);
            fw.write(initialConfiguration.getModel().toString());
            fw.flush();
            fw.close();
        } catch ( Exception e) {
            e.printStackTrace();
        }
	}

	@Override
	public ComputingState computeReconfigurationPlan() {
		ComputingState res = ComputingState.SUCCESS;

		try {
            Instance i = new Instance(initialConfiguration.getModel(), new ArrayList<>(), new MinMTTR());
            InstanceConverter conv = new InstanceConverter();
            String path = "logs/JSON-BtrPlace" + ".txt";
            FileWriter fw = new FileWriter(path, true);
            BufferedWriter bufWriter = new BufferedWriter(fw);
            bufWriter.newLine();
            bufWriter.write(conv.toJSON(i).toString());
            bufWriter.close();
            fw.close();
            timeToComputeVMRP = System.currentTimeMillis();
            reconfigurationPlan = planner.solve(initialConfiguration.getModel(), initialConfiguration.getCstrs());
			timeToComputeVMRP = System.currentTimeMillis() - timeToComputeVMRP;
            if(reconfigurationPlan == null)
                res = ComputingState.NO_RECONFIGURATION_NEEDED;
		} catch (SchedulerException e) {
			e.printStackTrace();
            Msg.error("Scheduler has failed to compute !");
            res = ComputingState.RECONFIGURATION_FAILED ;
            timeToComputeVMRP = System.currentTimeMillis() - timeToComputeVMRP;
			reconfigurationPlan = null;
		} catch (JSONConverterException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(reconfigurationPlan != null){
			if(reconfigurationPlan.getActions().isEmpty())
				res = ComputingState.NO_RECONFIGURATION_NEEDED;

			reconfigurationPlanCost = reconfigurationPlan.getDuration();
			newConfiguration = new ConfigBtrPlace(reconfigurationPlan.getResult(),
                    initialConfiguration.getCstrs(),
                    initialConfiguration.getVmNames(),
                    initialConfiguration.getNodeNames());
			nbMigrations = computeNbMigrations();
			reconfigurationGraphDepth = computeReconfigurationGraphDepth();
		}

		return res;
	}

	//Get the number of migrations
	private int computeNbMigrations(){
		int nbMigrations = 0;

		for (Action a : reconfigurationPlan.getActions()){
			if(a instanceof MigrateVM){
				nbMigrations++;
			}
		}

		return nbMigrations;
	}

	//Get the depth of the reconfiguration graph
	//May be compared to the number of steps in Entropy 1.1.1
	//Return 0 if there is no action, and (1 + maximum number of dependencies) otherwise
	private int computeReconfigurationGraphDepth(){
		if(reconfigurationPlan.getActions().isEmpty()){
			return 0;
		}

		else{
			int maxNbDeps = 0;
			int nbDeps;

			//Set the reverse dependencies map
			for (Action a : reconfigurationPlan.getActions()) {
				nbDeps = reconfigurationPlan.getDirectDependencies(a).size();

				if (nbDeps > maxNbDeps)
					maxNbDeps = nbDeps;
			}

			return 1 + maxNbDeps;
		}
	}

	@Override
	public void applyReconfigurationPlan() {
		if(reconfigurationPlan != null && !reconfigurationPlan.getActions().isEmpty()){

			//Log the reconfiguration plan
            // Flavien / Adrien - In order to prevent random iterations due to the type of reconfiguration Plan (i.e. HashSet see Javadoc)
            LinkedList<Action> sortedActions = new LinkedList<Action>(reconfigurationPlan.getActions());
            Collections.sort(sortedActions, new Comparator<Action>() {
                @Override
                public int compare(Action a1, Action a2) {
                    if ((a1 instanceof MigrateVM) && (a2 instanceof MigrateVM)){
                        return initialConfiguration.getVmNames().get(((MigrateVM)a1).getVM()).compareTo(initialConfiguration.getVmNames().get(((MigrateVM) a2).getVM()));
                    }
                    return 0;  //To change body of implemented methods use File | Settings | File Templates.
                }
            });

            try {
                File file = new File("logs/btrplace/reconfigurationplan/" + loopID + "-" + System.currentTimeMillis() + ".txt");
                file.getParentFile().mkdirs();
                PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
                //pw.write(reconfigurationPlan.toString());
                for (Action a : sortedActions) {
                    pw.write(a.toString()+"\n");
                }
                pw.flush();
                pw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Apply the reconfiguration plan.
			try {
				applyReconfigurationPlanLogically(sortedActions);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}


    //Apply the reconfiguration plan logically (i.e. create/delete Java objects)
    private void applyReconfigurationPlanLogically(LinkedList<Action> sortedActions) throws InterruptedException{
        //Start the feasible actions
        // ie, actions with a start moment equals to 0.
        for (Action a : sortedActions) {
            if ((a.getStart() == 0)  && !isReconfigurationPlanAborted()) {
                instantiateAndStart(a);
            }
        }

        // If you reach that line, it means that either the execution of the plan has been completely launched or the
        // plan has been aborted. In both cases, we should wait for the completion of on-going migrations

        // Add a watch dog to determine infinite loop
        int watchDog = 0;

        while(this.ongoingMigration()){
            try {
                Process.getCurrentProcess().waitFor(1);
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


    private void instantiateAndStart(Action a) throws InterruptedException{
        if(a instanceof MigrateVM){
            MigrateVM migration = (MigrateVM)a;
            String vmName = initialConfiguration.getVmNames().get(migration.getVM());
            String nodeName = initialConfiguration.getNodeNames().get(migration.getSourceNode());
            String destNodeName = initialConfiguration.getNodeNames().get(migration.getDestinationNode());
            this.relocateVM(vmName, nodeName, destNodeName);
        } else if (a instanceof Allocate){
//            Msg.info("ALLOCATION");
        }else {
            System.err.println("UNRECOGNIZED ACTION WHEN APPLYING THE RECONFIGURATION PLAN");
        }
    }

    /**
     * @param hostsToCheck
     * @return the duration of the reconfiguration (i.e. > 0), -1 there is no viable reconfiguration, -2 the reconfiguration crash
     */
    public SchedulerRes checkAndReconfigure(Collection<XHost> hostsToCheck) {

        long beginTimeOfCompute;
        long endTimeOfCompute;
        long computationTime;
        ComputingState computingState;
        long reconfigurationTime = 0;
        Btr_PlaceRPRes enRes = new Btr_PlaceRPRes();

		/* Tracing code */
        int i;
        for (XHost h : hostsToCheck) {
            if (!h.isViable())
                Trace.hostPushState(h.getName(), "PM", "violation-det");
            Trace.hostSetState(h.getName(), "SERVICE", "booked");
        }

        Msg.info("Launching scheduler (loopId = " + loopID + ") - start to compute");
        Msg.info("Nodes considered: "+initialConfiguration.getModel().getMapping().getAllNodes().toString());

        /** PLEASE NOTE THAT ALL COMPUTATIONS BELOW DOES NOT MOVE FORWARD THE MSG CLOCK ***/
        beginTimeOfCompute = System.currentTimeMillis();
        computingState = this.computeReconfigurationPlan();
        endTimeOfCompute = System.currentTimeMillis();
        computationTime = (endTimeOfCompute - beginTimeOfCompute);

        /* Tracing code */
        double computationTimeAsDouble = ((double) computationTime) / 1000;

        int migrationCount = 0;
        if(computingState.equals(ComputingState.SUCCESS)) {
            migrationCount = this.reconfigurationPlan.getSize();
        }

        int partitionSize = hostsToCheck.size();

        /** **** NOW LET'S GO BACK TO THE SIMGRID WORLD **** */

        Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "compute", String.format("{\"duration\" : %f, \"result\" : \"%s\", \"migration_count\": %d, \"psize\": %d}", computationTimeAsDouble, computingState, migrationCount, partitionSize));


        try {
            Process.sleep(computationTime); // instead of waitFor that takes into account only seconds
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


            Msg.info("Starting reconfiguration");
            double startReconfigurationTime = Msg.getClock();
            this.applyReconfigurationPlan();
            double endReconfigurationTime = Msg.getClock();
            reconfigurationTime = ((long) (endReconfigurationTime - startReconfigurationTime) * 1000);
            Msg.info("Reconfiguration time (in ms): " + reconfigurationTime);
            enRes.setDuration(enRes.getDuration() + reconfigurationTime);
            Msg.info("Number of nodes used: " + hostsToCheck.size());
            if (isReconfigurationPlanAborted())
                enRes.setRes(-2);
            else
                enRes.setRes(1);

            Trace.hostPopState(Host.currentHost().getName(), "SERVICE"); //PoP reconfigure;
        } else {
            Msg.info("BtrPlace did not find any viable solution");
            enRes.setRes(-1);
        }

		/* Tracing code */
        for (XHost h : hostsToCheck)
            Trace.hostSetState(h.getName(), "SERVICE", "free");

        Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "free");
        return enRes;
    }

    // Create configuration for Entropy
    public static ConfigBtrPlace ExtractConfiguration(Collection<XHost> xhosts) {

        Model model = new DefaultModel();
        Mapping map = model.getMapping();
        Map<VM, String> vmNames = new HashMap<VM, String>();
        Map<Node, String> nodeNames = new HashMap<Node, String>();
        List<SatConstraint> cstrs = new ArrayList<SatConstraint>();

        ShareableResource rcCPU = new ShareableResource("cpu");
        ShareableResource rcMem = new ShareableResource("mem");

        // Add nodes
        for (XHost tmpH:xhosts){
            // Consider only hosts that are turned on
            if (tmpH.isOff()) {
                System.err.println("WTF, you are asking me to analyze a dead node (" + tmpH.getName() + ")");
                //System.exit(-1);
            }
            Node n = model.newNode();
            map.addOnlineNode(n);
            rcCPU.setCapacity(n, tmpH.getCPUCapacity());
            rcMem.setCapacity(n, tmpH.getMemSize());
            nodeNames.put(n, tmpH.getName());
            int cpu = (int)tmpH.computeCPUDemand();

            for (XVM tmpVM : tmpH.getRunnings()) {
                VM v = model.newVM();
                if(cpu > tmpH.getCPUCapacity()){
                    int arbitraryCpu = tmpH.getCPUCapacity()/tmpH.getRunnings().size();
                    rcCPU.setConsumption(v, arbitraryCpu);
                    cstrs.add(new Online(n));
                    cstrs.add(new Running(v));
                    cstrs.add(new Preserve(v, "cpu", (int) tmpVM.getCPUDemand()));
                    cstrs.add(new Preserve(v, "mem", tmpVM.getMemSize()));
                } else {
                    rcCPU.setConsumption(v, (int) tmpVM.getCPUDemand());
                }
                rcMem.setConsumption(v,tmpVM.getMemSize());
                map.addRunningVM(v, n);
                vmNames.put(v,tmpVM.getName());
            }
            model.attach(rcCPU);
            model.attach(rcMem);
        }
        return new ConfigBtrPlace(model, cstrs, vmNames, nodeNames);
    }

    private int ongoingMigration = 0 ;

    private void incMig(){
        this.ongoingMigration++ ;
        Trace.hostVariableAdd(SimulatorManager.getInjectorNodeName(), "NB_MIG", 1);
    }
    private void decMig() {
        this.ongoingMigration-- ;
    }

    private boolean ongoingMigration() {
        return (this.ongoingMigration != 0);
    }

    private void abortReconfigurationPlan() {this.abortRP = true;}

    private boolean isReconfigurationPlanAborted() {
        return this.abortRP;
    }

    public void relocateVM(final String vmName, final String sourceName, final String destName) {
        Random rand = new Random(SimulatorProperties.getSeed());

        Msg.info("Relocate VM " + vmName + " (from " + sourceName + " to " + destName + ")");

        if (destName != null) {
            String[] args = new String[3];

            args[0] = vmName;
            args[1] = sourceName;
            args[2] = destName;
            // Asynchronous migration
            // The process is launched on the source node
            try {
                new Process(Host.getByName(sourceName), "Migrate-" + rand.nextDouble(), args) {
                    public void main(String[] args) {
                        XHost destHost = null;
                        XHost sourceHost = null;

                        try {
                            sourceHost = SimulatorManager.getXHostByName(args[1]);
                            destHost = SimulatorManager.getXHostByName(args[2]);
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.err.println("You are trying to migrate from/to a non existing node");
                        }

                        if (destHost != null) {
                            if (!sourceHost.isOff() && !destHost.isOff()) {
                                incMig();

                                double timeStartingMigration = Msg.getClock();
                                Trace.hostPushState(vmName, "SERVICE", "migrate", String.format("{\"vm_name\": \"%s\", \"from\": \"%s\", \"to\": \"%s\"}", vmName, sourceName, destName));
                                int res = sourceHost.migrate(args[0], destHost);
                                // TODO, we should record the res of the migration operation in order to count for instance how many times a migration crashes ?
                                // To this aim, please extend the hostPopState API to add meta data information
                                Trace.hostPopState(vmName, "SERVICE", String.format("{\"vm_name\": \"%s\", \"result\": %d}", vmName, res));
                                double migrationDuration = Msg.getClock() - timeStartingMigration;

                                if (res == 0) {
                                    Msg.info("End of migration of VM " + args[0] + " from " + args[1] + " to " + args[2]);

                                    if (!destHost.isViable()) {
                                        Msg.info("ARTIFICIAL VIOLATION ON " + destHost.getName() + "\n");
                                        // If Trace.hostGetState(destHost.getName(), "PM").equals("normal")
                                        Trace.hostSetState(destHost.getName(), "PM", "violation-out");
                                    }
                                    if (sourceHost.isViable()) {
                                        Msg.info("SOLVED VIOLATION ON " + sourceHost.getName() + "\n");
                                        Trace.hostSetState(sourceHost.getName(), "PM", "normal");
                                    }

                                    /* Export that the migration has finished */
                                    Trace.hostSetState(vmName, "migration", "finished", String.format("{\"vm_name\": \"%s\", \"from\": \"%s\", \"to\": \"%s\", \"duration\": %f}", vmName, sourceName, destName, migrationDuration));
                                    Trace.hostPopState(vmName, "migration");
                                } else {

                                    Trace.hostSetState(vmName, "migration", "failed", String.format("{\"vm_name\": \"%s\", \"from\": \"%s\", \"to\": \"%s\", \"duration\": %f}", vmName, sourceName, destName, migrationDuration));
                                    Trace.hostPopState(vmName, "migration");

                                    Msg.info("Something was wrong during the migration of  " + args[0] + " from " + args[1] + " to " + args[2]);
                                    Msg.info("Reconfiguration plan cannot be completely applied so abort it");
                                    abortReconfigurationPlan();
                                }
                                decMig();
                            }
                        }

                    }
                }.start();

            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            System.err.println("You are trying to relocate a VM on a non existing node");
            System.exit(-1);
        }
    }

    public class Btr_PlaceRPRes implements SchedulerRes {



        private int res; // 0 no reconfiguration needed, -1 no viable configuration, -2 reconfiguration plan aborted, 1 everything was ok
        private long duration; // in ms

        Btr_PlaceRPRes(){
            this.res = 0;
            this.duration = 0;
        }

        public void setRes(int res) {
            this.res = res;
        }

        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public int getRes() {
            return res;
        }
    }
    public int getReconfigurationPlanCost(){
     return 0;
    }


}
