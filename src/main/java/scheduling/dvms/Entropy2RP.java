package scheduling.dvms;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// TODO why DVMSCLientForSG here ! 
import scheduling.EntropyProperties;
import scheduling.dvms.DVMSClientForSG;

import dvms.clientserver.Server;
import dvms.configuration.DVMSManagedElementSet;
import dvms.configuration.DVMSVirtualMachine;
import dvms.log.Logger;
import dvms.message.MigrationMessage;
import dvms.message.ReservationMessage;
import dvms.message.MigrationMessage.MigrationOperation;
import scheduling.dvms.AbstractScheduler;
import dvms.scheduling.ComputingState;
import dvms.tool.DVMSProperties;
import entropy.PropertiesHelper;
import entropy.configuration.Configuration;
import entropy.configuration.ManagedElementSet;
import entropy.configuration.Node;
import entropy.configuration.SimpleConfiguration;
import entropy.configuration.SimpleManagedElementSet;
import entropy.configuration.SimpleNode;
import entropy.configuration.SimpleVirtualMachine;
import entropy.configuration.VirtualMachine;
import entropy.execution.Dependencies;
import entropy.execution.TimedExecutionGraph;
import entropy.execution.TimedReconfigurationExecuter;
import entropy.execution.driver.DriverFactory;
import entropy.plan.PlanException;
import entropy.plan.TimedReconfigurationPlan;
import entropy.plan.action.Action;
import entropy.plan.action.Migration;
import entropy.plan.choco.ChocoCustomRP;
import entropy.plan.durationEvaluator.MockDurationEvaluator;
import entropy.vjob.DefaultVJob;
import entropy.vjob.VJob;

//An implementation of scheduler which is based on the default algorithm of Entropy 2
public class Entropy2RP extends AbstractScheduler {
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Class variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Maximum number of threads the scheduler is allowed to run simultaneously
	public static final int MAX_NUMBER_OF_THREADS = 5;
	

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private ChocoCustomRP planner;
	
	//The initial configuration
	private final Configuration initialConfiguration;
	
	//The new/final configuration
	private Configuration newConfiguration;
	
	//The reconfiguration plan
	private TimedReconfigurationPlan reconfigurationPlan;
	
	private ExecutorService executor;

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public Entropy2RP(DVMSManagedElementSet<DVMSNode> nodesConsidered,
			DVMSNode node) {
		super(nodesConsidered, node);
		
		initialConfiguration = buildInitialConfiguration();
		
		planner =  new ChocoCustomRP(new MockDurationEvaluator(2, 5, 1, 1, 7, 14, 7, 2, 4));
		planner.setTimeLimit(EntropyProperties.getEntropyPlanTimeout());
		
//		executor = Executors.newFixedThreadPool(MAX_NUMBER_OF_THREADS);
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Methods used in constructor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	private Configuration buildInitialConfiguration(){
		Configuration initialConfiguration = new SimpleConfiguration();
		
		Node entropyNode;
		DVMSManagedElementSet<DVMSVirtualMachine> vmsHosted;
		VirtualMachine entropyVm;
		
		for(DVMSNode node : getNodesConsidered()){
			entropyNode = new SimpleNode(node.getName(),
					node.getNbOfCPUs(), 
					node.getCPUCapacity(), 
					node.getMemoryTotal());
			initialConfiguration.addOnline(entropyNode);
			
			vmsHosted = node.getVirtualMachines();
			
			for(DVMSVirtualMachine vm : vmsHosted){
				entropyVm = new SimpleVirtualMachine(vm.getName(), 
						vm.getNbOfCPUs(), 
						0, 
						vm.getMemoryConsumption(), 
						vm.getCPUConsumption(), 
						vm.getMemoryConsumption());
				initialConfiguration.setRunOn(entropyVm, entropyNode);
			}
		}
		
		return initialConfiguration;
	}

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public ComputingState computeReconfigurationPlan() {
		ComputingState res = ComputingState.VMRP_SUCCESS; 
		
		// All VMs are encapsulated into the same vjob for the moment - Adrien, Nov 18 2011
		List<VJob> vjobs = new ArrayList<VJob>();
		VJob v = new DefaultVJob("v1");
		v.addVirtualMachines(initialConfiguration.getRunnings());
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
			//e.printStackTrace();
			res = ComputingState.VMRP_FAILED ; 
			timeToComputeVMRP = System.currentTimeMillis() - timeToComputeVMRP;
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
	public void applyReconfigurationPlan() throws InterruptedException {
		if(reconfigurationPlan != null && !reconfigurationPlan.getActions().isEmpty()){
			//Log the reconfiguration plan
			try {
				File file = new File(DVMSProperties.RECONFIGURATION_PLANS_DIRECTORY + File.separator 
						+ System.currentTimeMillis() + "-" + node.getName() + ".txt");
				file.getParentFile().mkdirs();
				PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
				pw.write(reconfigurationPlan.toString());
				pw.flush();
				pw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			//Apply the reconfiguration plan physically if running on a real system (i.e. migrate real VMs)
			if(!DVMSProperties.getSimulation()){
				TimedReconfigurationExecuter exec;
				
				try {
					timeToApplyReconfigurationPlan = System.currentTimeMillis();
					exec = new TimedReconfigurationExecuter(new DriverFactory(new PropertiesHelper()));
					exec.start(reconfigurationPlan);
					timeToApplyReconfigurationPlan = System.currentTimeMillis() - timeToApplyReconfigurationPlan;
				} catch (IOException e) {
					e.printStackTrace();
					timeToApplyReconfigurationPlan = System.currentTimeMillis() - timeToApplyReconfigurationPlan;
				}
			}
			
			//Apply the reconfiguration plan logically (i.e. create/delete Java objects)
			applyReconfigurationPlanLogically();
		}
	}
	
	//Apply the reconfiguration plan logically (i.e. create/delete Java objects)
	private void applyReconfigurationPlanLogically() throws InterruptedException{
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
	}
	
	private void instantiateAndStart(Action a) throws InterruptedException{
		if(a instanceof Migration){
			Migration migration = (Migration)a;
			
			MigrationMessage migrationMessage;
			Object response;
			List<Object> migrationResponses = new LinkedList<Object>();
			
			try{
			VirtualMachine vm = migration.getVirtualMachine();
			//DVMSVirtualMachine dvmsVm = new DVMSVirtualMachine(vm.getName(), vm.getNbOfCPUs(), vm.getCPUConsumption(), vm.getMemoryConsumption());
			DVMSVirtualMachine dvmsVm = new DVMSVirtualMachine(vm.getName(), vm.getNbOfCPUs(), vm.getCPUDemand(), vm.getMemoryDemand());
			
			//Create a migration message for the node sending the VM
			migrationMessage = new MigrationMessage(
					getNode().getAssociatedServer(), 
					getServerFromNode(migration.getHost()), 
					MigrationOperation.SEND, 
					dvmsVm);
			//response = executor.submit(new DVMSClientForSG(migrationMessage));
			//migrationResponses.add(response);
			migrationResponses.add(new DVMSClientForSG(migrationMessage).call()); 
			
			//Create a migration message for the node receiving the VM
			migrationMessage = new MigrationMessage(
					getNode().getAssociatedServer(), 
					getServerFromNode(migration.getDestination()), 
					MigrationOperation.RECEIVE,
					dvmsVm);
			//response = executor.submit(new DVMSClientForSG(migrationMessage));
			//migrationResponses.add(response);
			migrationResponses.add(new DVMSClientForSG(migrationMessage).call()); 
			} catch (Exception e) {
				e.printStackTrace();
				Logger.log(e);
			}
			
// Useless now since requests are synchronous.
//			//Wait for all migration messages to be sent before processing the next ActionsPool
//			for(Future<Object> anAnswer : migrationResponses){
//				try {
//					anAnswer.get();
//				} catch (ExecutionException e) {
//					Logger.log(e);
//				}
//			}
		}
		
		else{
			Logger.log("UNRECOGNIZED ACTION WHEN APPLYING THE RECONFIGURATION PLAN");
		}
	}

	@Override
	@Deprecated
	public List<ReservationMessage> computeReservations() {
		return new LinkedList<ReservationMessage>();
	}

	@Override
	public Map<String, List<String>> getNewConfiguration() {
		Map<String, List<String>> result = new HashMap<String, List<String>>();
		ManagedElementSet<VirtualMachine> vmsHosted;
		List<String> vmsHostedString;
		
		for(Node node : newConfiguration.getAllNodes()){
			vmsHosted = new SimpleManagedElementSet<VirtualMachine>(); 
			vmsHosted.addAll(newConfiguration.getRunnings(node));
			vmsHosted.addAll(newConfiguration.getSleepings(node));
			
			vmsHostedString = new LinkedList<String>();
			
			for(VirtualMachine vm : vmsHosted){
				vmsHostedString.add(vm.getName());
			}
			
			result.put(node.getName(), vmsHostedString);
		}

		return result;
	}
	
	//Returns the server associated to a given node, based on information found
	//in the event message
	private Server getServerFromNode(Node entropyNode){
		
		for(DVMSNode node : getNodesConsidered()){
			if(entropyNode.getName().equals(node.getName())){
				return node.getAssociatedServer();
			}
		}
		
		return null;
	}

}
