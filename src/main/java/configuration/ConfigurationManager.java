package configuration;

//import java.io.BufferedReader;
//import java.io.FileNotFoundException;
//import java.io.FileReader;
import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Random;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;
import org.simgrid.msg.VM;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;

import configuration.VMClasses.VMClass;

import scheduling.EntropyProperties;
import simulation.*;

import entropy.configuration.Configuration;
import entropy.configuration.ManagedElementSet;
import entropy.configuration.Node;
import entropy.configuration.SimpleManagedElementSet;
import entropy.configuration.SimpleNode;
import entropy.configuration.SimpleVirtualMachine;
import entropy.configuration.VirtualMachine;
import entropy.configuration.parser.FileConfigurationSerializerFactory;
import entropy.plan.action.Migration;

//Generate the initial configuration file used by the simulator
public class ConfigurationManager {


    private static boolean skipOverlappingEvent = true ;

	public static XSimpleConfiguration generateConfigurationFile(
			String outputConfigurationFileName,
			int nbOfNodes, int nbOfCPUsPerNode, int cpuCapacityPerNode, int memoryTotalPerNode,
			int nbOfVMs){
		System.out.println("Generating initial configuration file");

		XSimpleConfiguration initialConfiguration = new XSimpleConfiguration();
		ManagedElementSet<Node> nodes = makeNodes(nbOfNodes, nbOfCPUsPerNode, cpuCapacityPerNode, memoryTotalPerNode);
        System.out.println("MakeNode done");
		Deque<VirtualMachine> vms = makeVMs(nbOfVMs);
        System.out.println("makeVMs done");
		addNodesAndVMs(initialConfiguration, nodes, vms);
        System.out.println("MakeVMonNode done");

		// write the initialConfiguration to an output file. 
		try {
			FileConfigurationSerializerFactory.getInstance().write(initialConfiguration, outputConfigurationFileName);
		} catch (IOException e) {
			e.printStackTrace();
		}
				
		return initialConfiguration; 
	}

	private static Deque<VirtualMachine> makeVMs(int nbOfVMs){
		Deque<VirtualMachine> vms = new LinkedList<VirtualMachine>();
		String formatNbOfVMs = "%0" + String.valueOf(nbOfVMs).length() + "d";
		Random r = new Random(SimulatorProperties.getSeed());
		int nbOfVMClasses = VMClasses.CLASSES.size();
		VMClass vmClass;
		
		for(int i = 0 ; i < nbOfVMs ; i++){
			vmClass = VMClasses.CLASSES.get(r.nextInt(nbOfVMClasses));
			// CPU and memory consumption of VMs has been initialized to 0 to bypass Entropy limitations (in terms of packing) - Adrien Nov 18 2011
			//vms.add(new SimpleVirtualMachine("vm" + String.format(formatNbOfVMs, i),
            vms.add(new SimpleVirtualMachine("vm-" + i,
                     vmClass.getNbOfCPUs(), 0, (int) vmClass.getMemSize(), 0, (int) vmClass.getMemSize()));

		}
		
		return vms;
	}
	
	private static ManagedElementSet<Node> makeNodes(int nbOfNodes, int nbOfCPUsPerNode, int cpuCapacityPerNode, int memoryTotalPerNode){
		ManagedElementSet<Node> nodes = new SimpleManagedElementSet<Node>();
		String formatNbOfNodes = "%0" + String.valueOf(nbOfNodes).length() + "d";
		
		if(SimulatorProperties.getVirtualNodesNamesFile() == null){
			for(int i = 1 ; i <= nbOfNodes ; i++){
				//	nodes.add(new SimpleNode("node" + String.format(formatNbOfNodes, i), nbOfCPUsPerNode, cpuCapacityPerNode, memoryTotalPerNode));
				nodes.add(new SimpleNode("node" + i, nbOfCPUsPerNode, cpuCapacityPerNode, memoryTotalPerNode));
			}
		}

		else{
			System.err.println("ERROR VirtualNodesNamesFile property is currently disabled");
			//			try {
			//				BufferedReader reader = new BufferedReader(new FileReader(SimulatorProperties.getVirtualNodesNamesFile()));
			//				String nodeName;
			//				int nodeCount = 0;
			//				
			//				while((nodeName = reader.readLine()) != null){
			//					nodes.add(new SimpleNode(nodeName,
			//							G5kNodes.getNbOfCPUs(nodeName),
			//							G5kNodes.getCPUCapacity(nodeName),
			//							G5kNodes.getMemoryTotal(nodeName)));
			//					nodeCount++;
			//					totalCPUs += G5kNodes.getNbOfCPUs(nodeName);
			//				}
			//				
			//				nbOfVMs = (int) (SimulatorProperties.getNbOfCPUConsumptionSlots() == 2 ?
			//						totalCPUs / (float)(SimulatorProperties.getMaxPercentageOfActiveVMs() / (float)100) :
			//							totalCPUs * 1.6);
			//				SimulatorProperties.INSTANCE.put(SimulatorProperties.NB_OF_VMS, "" + nbOfVMs);
			//				
			//				if(nodeCount != nbOfNodes)
			//					System.err.println("ERROR in node file: " + nbOfNodes + " names expected; " + nodeCount + " found");
			//				
			//			} catch (FileNotFoundException e) {
			//				e.printStackTrace();
			//			} catch (IOException e) {
			//				e.printStackTrace();
			//			}
		}
		
		return nodes;
	}
	
	private static void addNodesAndVMs(Configuration cfg, ManagedElementSet<Node> nodes, Deque<VirtualMachine> vms){
		try{
			int totalNbOfVCPUs = 0;
			int totalNbOfCPUs = 0;
			
			for(VirtualMachine vm : vms)
				totalNbOfVCPUs += vm.getNbOfCPUs();
			
			// Add nodes
			for(Node node : nodes){
				cfg.addOnline(node);
				totalNbOfCPUs += node.getNbOfCPUs();
			}
			
			ListIterator<Node> nodeIter = nodes.listIterator();
			Node node;
			int nodeIndex;
			int[] nodeMemCons = new int[nodes.size()];
			int nodeNbOfVCPUs;
			VirtualMachine vm;
			
			//Add VMs to each node according to the nb of CPUs of that node
			while(nodeIter.hasNext()){
				nodeIndex = nodeIter.nextIndex();
				node = nodeIter.next();
				
				nodeMemCons[nodeIndex] = 0;
				nodeNbOfVCPUs = 0;
				

				//Placement that takes into account NB of CPUs/cores and memory size constraints
				/*
				while (100*nodeNbOfVCPUs/node.getNbOfCPUs() < 100*totalNbOfVCPUs/totalNbOfCPUs

						&& nodeMemCons[nodeIndex] + vms.getFirst().getMemoryConsumption() <= node.getMemoryCapacity()){
					vm = vms.removeFirst();
					cfg.setRunOn(vm, node);
					nodeMemCons[nodeIndex] += vm.getMemoryConsumption();
					nodeNbOfVCPUs += vm.getNbOfCPUs();
				}
				*/
                // Placement pour la validation du modele (temporaire)
                int i = 0;
                while (i <6 && nodeMemCons[nodeIndex] + vms.getFirst().getMemoryConsumption() <= node.getMemoryCapacity()){
					i++;
                    vm = vms.removeFirst();
					cfg.setRunOn(vm, node);
					nodeMemCons[nodeIndex] += vm.getMemoryConsumption();
					nodeNbOfVCPUs += vm.getNbOfCPUs();
				}

			}

			//Affect the remaining VMs in a round robin way
			//This code may be useless
            boolean bVMPlaced ;
			while(!vms.isEmpty()){
				nodeIter = nodes.listIterator();
                bVMPlaced = false ;
                while(nodeIter.hasNext()){
					nodeIndex = nodeIter.nextIndex();
					node = nodeIter.next();
					
					if(nodeMemCons[nodeIndex] + vms.getFirst().getMemoryConsumption() <= node.getMemoryCapacity()){
						bVMPlaced = true ;
                        vm = vms.removeFirst();
						cfg.setRunOn(vm, node);
						nodeMemCons[nodeIndex] += vm.getMemoryConsumption();
					}
				}
                if (!bVMPlaced){
                    System.err.println("It is impossible to position all VMs (vm id: " + vms.getFirst().getName()+")");
                    System.err.println("Current affectation :"+nodeMemCons.toString() );
                    System.exit(-1);
                }
			}
		}catch(NoSuchElementException e){}
	}

	public static XSimpleConfiguration generateConfigurationFile(String outputConfigurationFileName){
		return generateConfigurationFile(outputConfigurationFileName,
				//Capacity of each node
				SimulatorProperties.getNbOfNodes(), SimulatorProperties.getNbOfCPUs(), SimulatorProperties.getCPUCapacity(), SimulatorProperties.getMemoryTotal(),

				//Maximum resource consumption of each VM
				SimulatorProperties.getNbOfVMs());
	}

	public static XSimpleConfiguration generateConfigurationFile(){
		return generateConfigurationFile(SimulatorProperties.getConfigurationFile());
	}



    public static Deque<FaultEvent> generateFaultQueue(Host[] hosts,  long duration, int faultPeriod){
        LinkedList<FaultEvent> faultQueue = new LinkedList<FaultEvent>();
        Random randExpDis=new Random(SimulatorProperties.getSeed());
        double currentTime = 0 ;
        double lambdaPerHost=1.0/faultPeriod ; // Nb crash per host (average)

        int nbOfHosts=hosts.length;

        double lambda=lambdaPerHost*nbOfHosts;
    	long id=0;
		Host tempHost;

		currentTime+=exponentialDis(randExpDis, lambda);

		Random randHostPicker = new Random(SimulatorProperties.getSeed());

		while(currentTime < duration){
			// select a host
			tempHost = hosts[randHostPicker.nextInt(nbOfHosts)];
			// and change its state
            // false = off , on = true
			// Add a new event queue
			faultQueue.add(new FaultEvent(id++, currentTime,tempHost, false));
            //For the moment, downtime of a node is arbitrarily set to 5 min
            faultQueue.add(new FaultEvent(id++, currentTime+(300*1000),tempHost, true));
			currentTime+=exponentialDis(randExpDis, lambda);
			//        System.err.println(eventQueue.size());
		}
		Msg.info("Number of events:"+faultQueue.size());
		return faultQueue;
	}


    /**
	 * 
	 * @param vms, Simgrid VMs that have been instanciated
	 * @param duration int, duration of the simulated time in minutes
	 * @param injectionPeriod int,  frequency of event occurrence in seconds
	 * @return the queue of the VM changes
	 * @see simulation.LoadEvent
	 */
	public static Deque<LoadEvent> generateLoadQueue(XVM[] vms, long duration, int injectionPeriod) {

		LinkedList<LoadEvent> eventQueue = new LinkedList<LoadEvent>();
		Random randExpDis=new Random(SimulatorProperties.getSeed());
		double currentTime = 0 ; 
		double lambdaPerVM=1.0/injectionPeriod ; // Nb Evt per VM (average)

		Random randExpDis2=new Random(SimulatorProperties.getSeed());

		double mean = SimulatorProperties.getMeanLoad();
		double sigma = SimulatorProperties.getStandardDeviationLoad();
		
		double gLoad = 0;

		double lambda=lambdaPerVM*vms.length;

		int maxCPUDemand = SimulatorProperties.getCPUCapacity()/SimulatorProperties.getNbOfCPUs();
		int nbOfCPUDemandSlots = SimulatorProperties.getNbOfCPUConsumptionSlots();
		int vmCPUDemand;
		long id=0; 
		XVM tempVM;

		//        currentTime+=Math.round(exponentialDis(randExpDis, lambda)); 
		currentTime+=exponentialDis(randExpDis, lambda); 

		Random randVMPicker = new Random(SimulatorProperties.getSeed());
		int nbOfVMs = vms.length;
		
		while(currentTime < duration){
            if( !skipOverlappingEvent || ((int)currentTime) % EntropyProperties.getEntropyPeriodicity() != 0){
                // select a VM
                tempVM = vms[randVMPicker.nextInt(nbOfVMs)];
                // and change its state

                int cpuConsumptionSlot = maxCPUDemand/nbOfCPUDemandSlots;
                /*Uniform assignment of VM load */
                //int slot=(int) (Math.random()*(nbOfCPUDemandSlots+1));
                /* Gaussian law for the load assignment */
                gLoad = Math.max((randExpDis2.nextGaussian()*sigma)+mean, 0);
                int slot= (int) Math.round(Math.min(100,gLoad)*nbOfCPUDemandSlots/100);

                vmCPUDemand = slot*cpuConsumptionSlot*(int)tempVM.getCore();

                // Add a new event queue
                eventQueue.add(new LoadEvent(id++, currentTime,tempVM, vmCPUDemand));
            }
			currentTime+=exponentialDis(randExpDis, lambda);     
			//        System.err.println(eventQueue.size());
		}
		Msg.info("Number of events:"+eventQueue.size());
		return eventQueue;
	}

    public static Deque<InjectorEvent> mergeQueues(Deque<LoadEvent> loadQueue, Deque<FaultEvent> faultQueue) {
        LinkedList<InjectorEvent> queue = new LinkedList<InjectorEvent>();
        FaultEvent crashEvt;
        if (faultQueue != null)
            crashEvt = faultQueue.pollFirst();
        else
            crashEvt = null;
        LoadEvent loadEvt = loadQueue.pollFirst();
        // Here we are considering that the load event queue cannot be empty
        do{

            while (crashEvt != null && loadEvt.getTime()>crashEvt.getTime()){
                queue.addLast(crashEvt);
                crashEvt = faultQueue.pollFirst();
            }
            queue.addLast(loadEvt);
            loadEvt = loadQueue.pollFirst();
        }while(loadEvt != null);

        while(crashEvt != null){
            queue.addLast(crashEvt);
            crashEvt = faultQueue.pollFirst();
        }
        return queue;
    }

    /* Compute the next exponential value for rand */
	private static double exponentialDis(Random rand, double lambda) {
		return -Math.log(1 - rand.nextDouble()) / lambda;
	}



	public static void relocateVM(Configuration c, Migration m){
		relocateVM(c, m.getVirtualMachine(), m.getHost(), m.getDestination());
	}
	
	public static void relocateVM(Configuration c, String vmName, String nameOfNewNode){
		Node destinationNode = Main.getCurrentConfig().getNodeByName(nameOfNewNode);
        VirtualMachine vm=Main.getCurrentConfig().getVMByName(vmName);
		relocateVM(c, vm, c.getLocation(vm), destinationNode);
	}
	
    public static void relocateVM(Configuration currentConfig, final VirtualMachine vm, final Node sourceNode, final Node destinationNode) {
    	Random rand = new Random(SimulatorProperties.getSeed());
    	
        if(destinationNode != null){
            String[] args = new String[2];

            args[0] = vm.getName();
            args[1] = destinationNode.getName();
            // Asynchronous migration
            try {
                new Process(currentConfig.getLocation(vm).getName(),"Migrate-"+rand.nextDouble(),args) {
                    public void main(String[] args){
                        VM sgVM = null;
                        sgVM = VM.getVMByName(args[0]);
                        if(sgVM != null){
                            Host sgHost = null;
                            try {
                                sgHost = Host.getByName(args[1]);
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.err.println("You are trying to migrate to a non existing node");
                            }
                            if(sgHost != null){
                                sgVM.migrate(sgHost);
                                Configuration c = Main.getCurrentConfig();
                                c.setRunOn(vm, destinationNode);
                				Msg.info("End of migration of VM " +args[0] + " from "+sourceNode.getName() + " to "+ destinationNode.getName());
            				    // Decrement the number of on-going migrating process
                                CentralizedResolver.decMig();
            					if(!Main.getCurrentConfig().isViable(destinationNode)){
            		    			Msg.info("ARTIFICIAL VIOLATION ON "+destinationNode.getName()+"\n");
            						Trace.hostSetState(destinationNode.getName(), "PM", "violation-out");
            					}
            					if(Main.getCurrentConfig().isViable(sourceNode)){
            		    			Msg.info("SOLVED VIOLATION ON "+sourceNode.getName()+"\n");
            						Trace.hostSetState(sourceNode.getName(), "PM", "normal");
            					}
                            }
                        }
                    }
                }.start();

            } catch (Exception e) {
                e.printStackTrace();
            }

            // Synchronoous migration call
            /* VM sgVM = null;
            sgVM = VM.getVMByName(args[0]);
            if (sgVM != null) {
                Host sgHost = null;
                try {
                    sgHost = Host.getByName(args[1]);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("You are trying to migrate to a non existing node");
                }
                if (sgHost != null)
                    sgVM.migrate(sgHost);
                currentConfig.setRunOn(vm, destinationNode);
            } else {
                System.err.println("You are trying to relocate a VM on a non existing node");
                System.exit(-1);
            }
            */
        } else {
            System.err.println("You are trying to relocate a VM on a non existing node");
            System.exit(-1);
        }
    }

	

	public static String getServiceNodeName() {
		return "node0";
	}

    public static XVM[] instanciateVMs(Configuration currentConfig) {
        int i = 0;
        int nbOfVMs = currentConfig.getAllVirtualMachines().size();
        VirtualMachine avm = null ;
        Random r = new Random(SimulatorProperties.getSeed());
		int nbOfVMClasses = VMClasses.CLASSES.size();
		VMClass vmClass;

        XVM[] vms = new XVM[currentConfig.getAllVirtualMachines().size()];

        for(i = 0 ; i < nbOfVMs ; i++){
			vmClass = VMClasses.CLASSES.get(r.nextInt(nbOfVMClasses));
            avm = currentConfig.getAllVirtualMachines().get(i);
            try {
                vms[i] = new XVM(Host.getByName(currentConfig.getLocation(avm).getName()),avm.getName(),
                        vmClass.getNbOfCPUs(), vmClass.getMemSize(), vmClass.getNetBW(), null, -1, vmClass.getMigNetBW(), vmClass.getMemIntensity());

                Msg.info(String.format("vm: %s, %d, %d, %s",
                        vms[i].getName(),
                        vmClass.getMemSize(),
                        vmClass.getNbOfCPUs(),
                        "NO IPs defined"
                ));
                Msg.info("vm " + vms[i].getName() + " is " + vmClass.getName() + ", dp is " + vmClass.getMemIntensity());
                vms[i].start();     // When the VM starts, its load equals 0
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Unknown host (cannot instanciate VM "+avm.getName());
                System.exit(1);
            }
		}
        return vms;
    }

    public static void updateVM(XVM sgVM, int load) {
        VirtualMachine entropyVM = Main.getCurrentConfig().getVMByName(sgVM.getName());
        Node entropyHost = Main.getCurrentConfig().getLocation(entropyVM);
    	boolean previouslyViable = Main.getCurrentConfig().isViable(entropyHost);

        // InjectLoad into the Entropy VM
        entropyVM.setCPUConsumption(0);
    	entropyVM.setCPUDemand(load);

        /* Inject load into the Simgrid VM */
        sgVM.setLoad(load);
        Msg.info("Current load "+Main.getCurrentConfig().load()+"\n");

    	if(previouslyViable) {
    		if (!Main.getCurrentConfig().isViable(entropyHost)) {
    			Msg.info("STARTING VIOLATION ON "+entropyHost.getName()+"\n");
    			Trace.hostSetState (entropyHost.getName(), "PM", "violation");
            }
            else if(!previouslyViable){
                if (Main.getCurrentConfig().isViable(entropyHost)) {
                    Msg.info("ENDING VIOLATION ON "+entropyHost.getName()+"\n");
                    Trace.hostSetState (entropyHost.getName(), "PM", "normal");
                }
            }
            // Update load of the host
            Trace.hostVariableSet(entropyHost.getName(), "LOAD", Main.getCurrentConfig().load(entropyHost));

            //Update global load
            Trace.hostVariableSet("node0", "LOAD", Main.getCurrentConfig().load());
        }
    }


    public static void turnOff(Host host) {

        Msg.info("Turn off "+host.getName());
        /* Before shutting down the nodes we should remove the VMs and the node from the configuration */
        Node entropyHost = Main.getCurrentConfig().getNodeByName(host.getName());

        // First remove all VMs hosted on the node
        VirtualMachine[] vms = new VirtualMachine[Main.getCurrentConfig().getRunnings(entropyHost).size()];
        int i = 0 ;
        for (VirtualMachine vm: Main.getCurrentConfig().getRunnings(entropyHost)){
            vms[i++]= vm ;
        }

        for (i = i -1 ; i>=0 ; i--)
            Main.getCurrentConfig().remove(vms[i]);

        Main.getCurrentConfig().remove(entropyHost);

        System.out.println(Main.getCurrentConfig());
    }


    public static void turnOn(Host host) {

        Msg.info("Turn on "+host.getName());
    }
}
