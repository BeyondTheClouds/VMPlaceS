package simulation;
import static entropy.configuration.Configurations.State.Runnings;
import static entropy.configuration.Configurations.State.Sleepings;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.EnumSet;


import configuration.XSimpleConfiguration;
import org.simgrid.msg.Host;
import org.simgrid.msg.HostNotFoundException;
import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.NativeException;

import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;

import configuration.ConfigurationManager;

import scheduling.AbstractScheduler;
import scheduling.EntropyProperties;
import scheduling.AbstractScheduler.ComputingState;
import scheduling.entropy.Entropy2RP;
import entropy.configuration.Configurations;
import entropy.configuration.Node;



public class CentralizedResolver extends Process {

    static int ongoingMigration = 0 ;
    static int loopID = 0 ;

	CentralizedResolver(Host host, String name, String[] args) throws HostNotFoundException, NativeException  {
		super(host, name, args);
	}

	/**
	 * @param args
	 */
    public void main(String[] args) throws MsgException{
        main2(args);
    }
    public void main2(String[] args) throws MsgException{
        double period = EntropyProperties.getEntropyPeriodicity();
		int numberOfCrash = 0;

		Trace.hostSetState(ConfigurationManager.getServiceNodeName(), "SERVICE", "free");

        long previousDuration = 0;

        while(!Main.isEndOfInjection()) {

            long wait = ((long)(period*1000)) - previousDuration;
            if (wait > 0)
                Process.sleep(wait); // instead of waitFor that takes into account only seconds

            XSimpleConfiguration currConf = Main.getCurrentConfig();
			/* Compute and apply the plan */
			AbstractScheduler scheduler;
			long beginTimeOfIteration;
			long endTimeOfCompute;
			long computationTime;
			ComputingState computingState;
            double reconfigurationTime = 0;

			/* Tracing code */
			Trace.hostSetState(ConfigurationManager.getServiceNodeName(), "SERVICE", "compute");
			for (Node node: currConf.getAllNodes()){
				if(!currConf.isViable(node))
					Trace.hostPushState(node.getName(), "PM", "violation-det");
				Trace.hostSetState(node.getName(), "SERVICE", "booked");

            }

			Msg.info("Launching scheduler (loopId = "+loopID+") - start to compute");

			//Measure iteration length
			beginTimeOfIteration = System.currentTimeMillis();
			scheduler = new Entropy2RP(currConf.cloneSorted(), loopID ++);
			
			computingState = scheduler.computeReconfigurationPlan();		
			endTimeOfCompute = System.currentTimeMillis();
			computationTime = (endTimeOfCompute - beginTimeOfIteration);

            Process.sleep(computationTime); // instead of waitFor that takes into account only seconds

			Msg.info("Computation time (in ms):" + computationTime);
            previousDuration = computationTime ;

			String STATUS = "VMRP_SUCCESS";
			int numberOfNodesUsed = Configurations.usedNodes(Main.getCurrentConfig(), EnumSet.of(Runnings, Sleepings)).size();			
			
			
			if(computingState.equals(ComputingState.NO_RECONFIGURATION_NEEDED)){
				Msg.info("Configuration remains unchanged");
				Trace.hostSetState(ConfigurationManager.getServiceNodeName(), "SERVICE", "free");
			} else if(computingState.equals(ComputingState.VMRP_SUCCESS)){
				int cost = scheduler.getReconfigurationPlanCost();
				/* Tracing code */
				Trace.hostSetState(ConfigurationManager.getServiceNodeName(), "SERVICE", "reconfigure");
				// TODO Adrien -> Adrien, try to consider only the nodes that are impacted by the reconfiguration plan
				for (Node tmpNode: currConf.getAllNodes())
					Trace.hostSetState(tmpNode.getName(), "SERVICE", "reconfigure"); 

				Msg.info("Starting reconfiguration");
                double startReconfigurationTime =  Msg.getClock() * 1000;
				scheduler.applyReconfigurationPlan();
				double endReconfigurationTime =  Msg.getClock() * 1000;
                reconfigurationTime = endReconfigurationTime - startReconfigurationTime;
                Msg.info("Reconfiguration time (in ms): "+ reconfigurationTime);
                previousDuration += reconfigurationTime ;
				/* Tracing code */
			   // the following code is now directly performed in relocateVM() invocation
			   /*
			    for (Node tmpNode: currConf.getAllNodes()){
					if(!ConfigurationManager.isViable(currConf, tmpNode))
						Trace.hostSetState(tmpNode.getName(), "PM", "violation-out"); 
					else
						Trace.hostSetState(tmpNode.getName(), "PM", "normal"); 
				}
			    */
				
				Msg.info("Number of nodes used: " + numberOfNodesUsed);
				Trace.hostSetState(ConfigurationManager.getServiceNodeName(), "SERVICE", "free");

			} else { 
				System.err.println("The resolver does not find any solutions - EXIT");
				Trace.hostSetState(ConfigurationManager.getServiceNodeName(), "SERVICE", "free");
				numberOfCrash++;
				STATUS = "VMRP_FAILED";				
				Msg.info("Entropy has encountered an error (nb: " + numberOfCrash +")");
			}

			
			if(!computingState.equals(ComputingState.NO_RECONFIGURATION_NEEDED)){
				writeRawFile(getLineFromInformation(STATUS,				// computing_state	
						(computationTime*1000+reconfigurationTime*1000)+"", 		// iteration_length_(ms)
						"-1",											// time_computing_VMPP_(ms)
						computationTime*1000+"",								// time_computing_VMRP_(ms)
						reconfigurationTime*1000+"",							// time_applying_plan_(ms)
						scheduler.getReconfigurationPlanCost()+"",		// cost
						scheduler.getNbMigrations()+"",					// nb_migrations
						scheduler.getReconfigurationGraphDepth()+"",	// depth_reconf_graph
						numberOfNodesUsed+"",							// nb_of_nodes_used
						Main.getCurrentConfig().load()+"" // nb_of_active_VMs
						));
			}
			
			/* Tracing code */
			// Warning Node0 does not belong to the current configuration (this is a service node that does not host any vms).
			currConf=Main.getCurrentConfig();
			for (Node node: currConf.getAllNodes()){
				Trace.hostSetState(node.getName(), "SERVICE", "free"); 
			}



			// endTimeOfIteration = System.currentTimeMillis();
			// iterationLength = endTimeOfIteration - beginTimeOfIteration;


			//	writeIterationData(scheduler, computingState.toString(), iterationLength, numberOfNodesUsed, nbOfActiveVMs);
			//	finalConfigurationFile = "logs/entropy/configurations/config-entropy-" + currentIteration + "-final.txt";
			//	FileConfigurationSerializerFactory.getInstance().write(initialConfiguration, finalConfigurationFile);		

		}

	}
	
	private void writeRawFile(String line) {

		try {
			File file = new File("raw_results_instances.txt");
			PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
			pw.write(line+"\n");
			pw.flush();
			pw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}	
	}

	public String getLineFromInformation(
			String computing_state,
			String iteration_length,
			String time_computing_VMPP,
			String time_computing_VMRP,
			String time_applying_plan,
			String cost,
			String nb_migrations,
			String depth_reconf_graph,
			String nb_of_nodes_used,
			String nb_of_active_VMs){

		return computing_state + "\t" +
				iteration_length + "\t" +
				time_computing_VMPP + "\t" +
				time_computing_VMRP + "\t" +
				time_applying_plan + "\t" +
				cost + "\t" +
				nb_migrations + "\t" +
				depth_reconf_graph + "\t" +
				nb_of_nodes_used + "\t" +
				nb_of_active_VMs + "\t";
	}

    public static void incMig(){
        Trace.hostVariableAdd("node0", "NB_MIG", 1);
        ongoingMigration++ ;
    }
    public static void decMig() {
        ongoingMigration-- ;
    }

    public static boolean ongoingMigration() {
        return (ongoingMigration != 0);
    }
}
