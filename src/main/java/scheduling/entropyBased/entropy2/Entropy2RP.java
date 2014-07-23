package scheduling.entropyBased.entropy2;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import entropy.configuration.*;
import entropy.configuration.parser.FileConfigurationSerializerFactory;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;
import scheduling.entropyBased.EntropyProperties;
import scheduling.Scheduler;
import simulation.CentralizedResolver;

import dvms.log.Logger;

import entropy.execution.Dependencies;
import entropy.execution.TimedExecutionGraph;
import entropy.plan.PlanException;
import entropy.plan.action.Action;
import entropy.plan.action.Migration;
import entropy.plan.choco.ChocoCustomRP;
import entropy.plan.durationEvaluator.MockDurationEvaluator;
import entropy.vjob.DefaultVJob;
import entropy.vjob.VJob;
import simulation.SimulatorManager;

public class Entropy2RP extends AbstractScheduler implements Scheduler {
	
	private ChocoCustomRP planner;//Entropy2.1
//	private ChocoCustomPowerRP planner;//Entropy2.0
    private int loopID; //Adrien, just a hack to serialize configuration and reconfiguration into a particular file name

    public Entropy2RP(Configuration initialConfiguration) {
        this(initialConfiguration, -1);
    }

    public Entropy2RP(Configuration initialConfiguration, int loopID) {
		super(initialConfiguration);
		planner =  new ChocoCustomRP(new MockDurationEvaluator(2, 5, 1, 1, 7, 14, 7, 2, 4));//Entropy2.1
//		planner = new ChocoCustomPowerRP(new MockDurationEvaluator(2, 2, 2, 3, 6, 3, 1, 1));//Entropy2.0
		planner.setRepairMode(true); //true by default for ChocoCustomRP/Entropy2.1; false by default for ChocoCustomPowerRP/Entrop2.0
		planner.setTimeLimit(EntropyProperties.getEntropyPlanTimeout());
        this.loopID = loopID;
        //Log the current Configuration
        try {
            String fileName = "logs/entropy/configuration/" + loopID + "-"+ System.currentTimeMillis() + ".txt";
            /*File file = new File("logs/entropy/configuration/" + loopID + "-"+ System.currentTimeMillis() + ".txt");
            file.getParentFile().mkdirs();
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            pw.write(initialConfiguration.toString());
            pw.flush();
            pw.close();*/
            FileConfigurationSerializerFactory.getInstance().write(initialConfiguration, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }

	}
	
	@Override
	public ComputingState computeReconfigurationPlan() {
		ComputingState res = ComputingState.SUCCESS;
		
		// All VMs are encapsulated into the same vjob for the moment - Adrien, Nov 18 2011
		List<VJob> vjobs = new ArrayList<VJob>();
		VJob v = new DefaultVJob("v1");//Entropy2.1
//		VJob v = new BasicVJob("v1");//Entropy2.0
		/*for(VirtualMachine vm : initialConfiguration.getRunnings()){
			v.addVirtualMachine(vm);
		}
		
		for(Node n : initialConfiguration.getAllNodes()){
			n.setPowerBase(100);
			n.setPowerMax(200);
		}*///Entropy2.0 Power
		v.addVirtualMachines(initialConfiguration.getRunnings());//Entropy2.1
		vjobs.add(v);
		try {
			timeToComputeVMRP = System.currentTimeMillis();
			reconfigurationPlan = planner.compute(initialConfiguration, 
					initialConfiguration.getRunnings(),
					initialConfiguration.getWaitings(),
					initialConfiguration.getSleepings(),
					new SimpleManagedElementSet<VirtualMachine>(), 
					initialConfiguration.getOnlines(), 
					initialConfiguration.getOfflines(), vjobs);
			timeToComputeVMRP = System.currentTimeMillis() - timeToComputeVMRP;
		} catch (PlanException e) {
			e.printStackTrace();
			res = ComputingState.RECONFIGURATION_FAILED ;
			timeToComputeVMRP = System.currentTimeMillis() - timeToComputeVMRP;
			reconfigurationPlan = null;
		}
		
		if(reconfigurationPlan != null){
			if(reconfigurationPlan.getActions().isEmpty())
				res = ComputingState.NO_RECONFIGURATION_NEEDED;
			
			reconfigurationPlanCost = reconfigurationPlan.getDuration();
			newConfiguration = reconfigurationPlan.getDestination();
			nbMigrations = computeNbMigrations();
			reconfigurationGraphDepth = computeReconfigurationGraphDepth();
		}
		
		return res; 
	}
		
	//Get the number of migrations
	private int computeNbMigrations(){
		int nbMigrations = 0;

		for (Action a : reconfigurationPlan.getActions()){
			if(a instanceof Migration){
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
	
	@Override
	public void applyReconfigurationPlan() {
		if(reconfigurationPlan != null && !reconfigurationPlan.getActions().isEmpty()){
			//Log the reconfiguration plan
            // Flavien / Adrien - In order to prevent random iterations due to the type of reconfiguration Plan (i.e. HashSet see Javadoc)
            LinkedList<Action> sortedActions = new LinkedList<Action>(reconfigurationPlan.getActions());
            Collections.sort(sortedActions, new Comparator<Action>() {
                @Override
                public int compare(Action a1, Action a2) {
                    if ((a1 instanceof Migration) && (a2 instanceof Migration)){
                        return ((Migration)a1).getVirtualMachine().getName().compareTo(((Migration)a2).getVirtualMachine().getName());
                    }
                    return 0;  //To change body of implemented methods use File | Settings | File Templates.
                }
            });

			try {
				File file = new File("logs/entropy/reconfigurationplan/" + loopID + "-" + System.currentTimeMillis() + ".txt");
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
			
			//Apply the reconfiguration plan physically if running on a real system (i.e. migrate real VMs)
//			if(!SimulatorProperties.getSimulation()){
//				TimedReconfigurationExecuter exec;
//				
//				try {
//					timeToApplyReconfigurationPlan = System.currentTimeMillis();
//					exec = new TimedReconfigurationExecuter(new DriverFactory(new PropertiesHelper()));
//					exec.start(reconfigurationPlan);
//					timeToApplyReconfigurationPlan = System.currentTimeMillis() - timeToApplyReconfigurationPlan;
//				} catch (IOException e) {
//					e.printStackTrace();
//					timeToApplyReconfigurationPlan = System.currentTimeMillis() - timeToApplyReconfigurationPlan;
//				}
//			}
			// Apply the reconfiguration plan logicaly (ie. on the current configuration).
			try {
				applyReconfigurationPlanLogically(sortedActions);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} 
		}
	}


    //Apply the reconfiguration plan logically (i.e. create/delete Java objects)
    private void applyReconfigurationPlanLogically(LinkedList<Action> sortedActions) throws InterruptedException{
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
        for (Action a : sortedActions) {
            if (a.getStartMoment() == 0) {
                instantiateAndStart(a);
            }

            if (revDependencies.containsKey(a)) {
                //Get the associated depenencies and update it
                for (Dependencies dep : revDependencies.get(a)) {
                    dep.removeDependency(a);
                    //Launch new feasible actions.
                    if (dep.isFeasible()) {
                        instantiateAndStart(dep.getAction());
                    }
                }
            }
        }

        // Wait for completion of all migrations
        while(this.ongoingMigration()){
            try {
                org.simgrid.msg.Process.currentProcess().waitFor(1);
            } catch (HostFailureException e) {
                e.printStackTrace();
            }
        }
    }

    private void instantiateAndStart(Action a) throws InterruptedException{
        if(a instanceof Migration){
            Migration migration = (Migration)a;
            this.relocateVM(migration.getVirtualMachine().getName(), migration.getHost().getName(), migration.getDestination().getName());
        } else{
            Logger.log("UNRECOGNIZED ACTION WHEN APPLYING THE RECONFIGURATION PLAN");
        }
    }

    /**
     * @param hostsToCheck
     * @return the duration of the reconfiguration (i.e. > 0), -1 there is no viable reconfiguration, -2 the reconfiguration crash
     */
    public Entropy2RPRes checkAndReconfigure(Collection<XHost> hostsToCheck) {

        long beginTimeOfCompute;
        long endTimeOfCompute;
        long computationTime;
        ComputingState computingState;
        long reconfigurationTime = 0;
        Entropy2RPRes enRes = new Entropy2RPRes();

		/* Tracing code */
        Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "compute");
        int i;
        for (XHost h : hostsToCheck) {
            if (!h.isViable())
                Trace.hostPushState(h.getName(), "PM", "violation-det");
            Trace.hostSetState(h.getName(), "SERVICE", "booked");
        }

        Msg.info("Launching scheduler (loopId = " + loopID + ") - start to compute");

        beginTimeOfCompute = System.currentTimeMillis();
        computingState = this.computeReconfigurationPlan();
        endTimeOfCompute = System.currentTimeMillis();
        computationTime = (endTimeOfCompute - beginTimeOfCompute);

        try {
            org.simgrid.msg.Process.sleep(computationTime); // instead of waitFor that takes into account only seconds
        } catch (HostFailureException e) {
            e.printStackTrace();
        }

        Msg.info("Computation time (in ms):" + computationTime);
        enRes.setDuration(computationTime);

        if (computingState.equals(ComputingState.NO_RECONFIGURATION_NEEDED)) {
            Msg.info("Configuration remains unchanged");
        } else if (computingState.equals(ComputingState.SUCCESS)) {
            int cost = this.getReconfigurationPlanCost();

				/* Tracing code */
            // TODO Adrien -> Adrien, try to consider only the nodes that are impacted by the reconfiguration plan
            for (XHost h : hostsToCheck)
                Trace.hostSetState(h.getName(), "SERVICE", "reconfigure");

            Msg.info("Starting reconfiguration");
            double startReconfigurationTime = Msg.getClock();
            this.applyReconfigurationPlan();
            double endReconfigurationTime = Msg.getClock();
            reconfigurationTime = ((long) (endReconfigurationTime - startReconfigurationTime) * 1000);
            Msg.info("Reconfiguration time (in ms): " + reconfigurationTime);
            enRes.setDuration(enRes.getDuration() + reconfigurationTime);
            Msg.info("Number of nodes used: " + SimulatorManager.getNbOfUsedHosts());
            enRes.setRes(0);
        } else {
            Msg.info("Entropy did not find any viable solution");
            enRes.setRes(-1);
        }

		/* Tracing code */
        for (XHost h : hostsToCheck)
            Trace.hostSetState(h.getName(), "SERVICE", "free");

        Trace.hostSetState(Host.currentHost().getName(), "SERVICE", "free");
        return enRes;
    }

    // Create configuration for Entropy
    public static Object ExtractConfiguration(Collection<XHost> xhosts) {
        Configuration currConf = new SimpleConfiguration();

        // Add nodes
        for (XHost tmpH:xhosts){
            // Consider only hosts that are turned on
            if (tmpH.isOff()) {
                System.err.println("WTF, you are asking me to analyze a dead node (" + tmpH.getName() + ")");
                System.exit(-1);
            }

            Node tmpENode = new SimpleNode(tmpH.getName(), tmpH.getNbCores(), tmpH.getCPUCapacity(), tmpH.getMemSize());
            currConf.addOnline(tmpENode);
            for (XVM tmpVM : tmpH.getRunnings()) {
                currConf.setRunOn(new SimpleVirtualMachine(tmpVM.getName(), (int) tmpVM.getCoreNumber(), 0,
                                tmpVM.getMemSize(), (int) tmpVM.getCPUDemand(), tmpVM.getMemSize()),
                        tmpENode
                );
            }

        }

        return currConf;
    }

    private int ongoingMigration = 0 ;

    private void incMig(){
        Trace.hostVariableAdd(SimulatorManager.getInjectorNodeName(), "NB_MIG", 1);
        this.ongoingMigration++ ;
    }
    private void decMig() {
        this.ongoingMigration-- ;
    }

    private boolean ongoingMigration() {
        return (this.ongoingMigration != 0);
    }

    public void relocateVM(String VMName, String sourceName, String destName) {
        Random rand = new Random(SimulatorProperties.getSeed());

        Msg.info("Relocate VM "+VMName+" (from "+sourceName+" to "+destName+")");

        if(destName != null){
            String[] args = new String[3];

            args[0] = VMName;
            args[1] = sourceName;
            args[2] = destName;
            // Asynchronous migration
            // The process is launched on the source node
            try {
                incMig();
                new Process(Host.getByName(sourceName),"Migrate-"+rand.nextDouble(),args) {
                    public void main(String[] args){
                        XHost destHost = null;
                        XHost sourceHost = null;
                        try {
                            sourceHost = SimulatorManager.getXHostByName(args[1]);
                            destHost = SimulatorManager.getXHostByName(args[2]);
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.err.println("You are trying to migrate from/to a non existing node");
                        }
                        if(destHost != null){
                            if( !sourceHost.isOff() && !destHost.isOff() && sourceHost.migrate(args[0],destHost) == 0 ) {
                                Msg.info("End of migration of VM " + args[0] + " from " + args[1] + " to " + args[2]);
                                // Decrement the number of on-going migrating process
                                decMig();
                                if (!destHost.isViable()) {
                                    Msg.info("ARTIFICIAL VIOLATION ON " + destHost.getName() + "\n");
                                    Trace.hostSetState(destHost.getName(), "PM", "violation-out");
                                }
                                if (sourceHost.isViable()) {
                                    Msg.info("SOLVED VIOLATION ON " + sourceHost.getName() + "\n");
                                    Trace.hostSetState(sourceHost.getName(), "PM", "normal");
                                }
                            }else{
                                // TODO raise an exception since the migration crash and thus the reconfigurationPlan is not valid anymore
                                //CentralizedResolved.setRPDeprecated();
                                Msg.info("Reconfiguration plan cannot be completely applied so abort it");
                                System.exit(-1);
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


    public class Entropy2RPRes{
        private int res;
        private long duration;

        Entropy2RPRes(){
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

}
