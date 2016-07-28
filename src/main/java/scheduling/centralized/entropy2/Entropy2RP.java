package scheduling.centralized.entropy2;

import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import entropy.configuration.*;
import entropy.configuration.parser.FileConfigurationSerializerFactory;
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
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.Msg;
import scheduling.AbstractScheduler;
import simulation.SimulatorManager;

import java.io.*;
import java.util.*;

public class Entropy2RP extends AbstractScheduler {

    private ChocoCustomRP planner;

    private Configuration source;

    private Configuration destination;

    private TimedReconfigurationPlan reconfigurationPlan;

    public Entropy2RP(Collection<XHost> xhosts) {
        this(xhosts, new Random(SimulatorProperties.getSeed()).nextInt());
    }

    public Entropy2RP(Collection<XHost> xhosts, Integer id) {
        super();
        this.source = this.extractConfiguration(xhosts);
        planner =  new ChocoCustomRP(new MockDurationEvaluator(2, 5, 1, 1, 7, 14, 7, 2, 4));//Entropy2.1
        planner.setRepairMode(true); //true by default for ChocoCustomRP/Entropy2.1; false by default for ChocoCustomPowerRP/Entrop2.0
        planner.setTimeLimit(Math.min(30, source.getAllNodes().size()/8));
        this.id = id;
        super.rpAborted = false;
        //Log the current Configuration
        try {
            String fileName = "logs/entropy/configuration/" + id + "-"+ System.currentTimeMillis() + ".txt";
            /*File file = new File("logs/entropy/configuration/" + id + "-"+ System.currentTimeMillis() + ".txt");
            file.getParentFile().mkdirs();
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));
            pw.write(source.toString());
            pw.flush();
            pw.close();*/
            FileConfigurationSerializerFactory.getInstance().write(source, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public ComputingResult computeReconfigurationPlan() {

        // All VMs are encapsulated into the same vjob for the moment - Adrien, Nov 18 2011
        List<VJob> vjobs = new ArrayList<>();
        VJob v = new DefaultVJob("v1");//Entropy2.1
//		VJob v = new BasicVJob("v1");//Entropy2.0
		/*for(VirtualMachine vm : source.getRunnings()){
			v.addVirtualMachine(vm);
		}
		
		for(Node n : source.getAllNodes()){
			n.setPowerBase(100);
			n.setPowerMax(200);
		}*///Entropy2.0 Power
        v.addVirtualMachines(source.getRunnings());//Entropy2.1
        vjobs.add(v);
        long timeToComputeVMRP = System.currentTimeMillis();
        try {
            reconfigurationPlan = planner.compute(source,
                    source.getRunnings(),
                    source.getWaitings(),
                    source.getSleepings(),
                    new SimpleManagedElementSet<VirtualMachine>(),
                    source.getOnlines(),
                    source.getOfflines(), vjobs);
            timeToComputeVMRP = System.currentTimeMillis() - timeToComputeVMRP;
        } catch (PlanException e) {
            e.printStackTrace();
            timeToComputeVMRP = System.currentTimeMillis() - timeToComputeVMRP;
            reconfigurationPlan = null;
            return new ComputingResult(ComputingResult.State.RECONFIGURATION_FAILED, timeToComputeVMRP);
        }

        if(reconfigurationPlan != null){
            if(reconfigurationPlan.getActions().isEmpty())
                return new ComputingResult(ComputingResult.State.NO_RECONFIGURATION_NEEDED, timeToComputeVMRP);

            destination = reconfigurationPlan.getDestination();
            planGraphDepth = computeReconfigurationGraphDepth();
            return new ComputingResult(ComputingResult.State.SUCCESS, timeToComputeVMRP, computeNbMigrations(), reconfigurationPlan.getDuration());
        } else {
            return new ComputingResult(ComputingResult.State.RECONFIGURATION_FAILED, timeToComputeVMRP);
        }

    }

    /**
     * Get the number of migrations
     */
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

    public void applyReconfigurationPlan() {
        if(reconfigurationPlan != null && !reconfigurationPlan.getActions().isEmpty()){
            //Log the reconfiguration plan
            // Flavien / Adrien - In order to prevent random iterations due to the type of reconfiguration Plan (i.e. HashSet see Javadoc)
            LinkedList<Action> sortedActions = new LinkedList<>(reconfigurationPlan.getActions());
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
                File file = new File("logs/entropy/reconfigurationplan/" + id + "-" + System.currentTimeMillis() + ".txt");
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
        Map<Action, List<Dependencies>> revDependencies = new HashMap<>();
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
            if ((a.getStartMoment() == 0)  && !this.rpAborted) {
                instantiateAndStart(a);
            }

            if (revDependencies.containsKey(a)) {
                //Get the associated depenencies and update it
                for (Dependencies dep : revDependencies.get(a)) {
                    dep.removeDependency(a);
                    //Launch new feasible actions.
                    if (dep.isFeasible() && !this.rpAborted) {
                        instantiateAndStart(dep.getAction());
                    }
                }
            }
        }

        // If you reach that line, it means that either the execution of the plan has been completely launched or the
        // plan has been aborted. In both cases, we should wait for the completion of on-going migrations

        // Add a watch dog to determine infinite loop
        int watchDog = 0;

        while(this.ongoingMigrations()){
            //while(this.ongoingMigrations() && !SimulatorManager.isEndOfInjection()){
            try {
                org.simgrid.msg.Process.getCurrentProcess().waitFor(1);
                watchDog ++;
                if (watchDog%100==0){
                    Msg.info(String.format("You've already been waiting for %d seconds for migrations to end:", watchDog, getMigratingVMs()));
                    for(XVM vm: getMigratingVMs()) {
                        Msg.info("\t- " + vm);
                    }

                    if(SimulatorManager.isEndOfInjection()){
                        Msg.info("Something wrong we are waiting too much, bye bye");
                        System.exit(131);
                    }
                }
            } catch (HostFailureException e) {
                e.printStackTrace();
            }
        }
    }


    private void instantiateAndStart(Action a) throws InterruptedException{
        if(a instanceof Migration){
            Migration migration = (Migration)a;
            XHost src = SimulatorManager.getXHostByName(migration.getHost().getName());
            XHost dst = SimulatorManager.getXHostByName(migration.getDestination().getName());

            if(SimulatorManager.getXVMByName(migration.getVirtualMachine().getName()).isRunning()) {
                if(dst.isOff())
                    SimulatorManager.turnOn(dst);

                super.relocateVM(migration.getVirtualMachine().getName(), migration.getHost().getName(), migration.getDestination().getName());
            }

        } else{
            System.err.println("UNRECOGNIZED ACTION WHEN APPLYING THE RECONFIGURATION PLAN");
        }
    }


    // Create configuration for Entropy
    protected Configuration extractConfiguration(Collection<XHost> xhosts) {
        Configuration currConf = new SimpleConfiguration();

        // Add nodes
        for (XHost tmpH:xhosts){
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


}
