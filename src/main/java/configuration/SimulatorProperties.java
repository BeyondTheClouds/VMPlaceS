package configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class SimulatorProperties extends Properties {

	/**
	 * 
	 */
	private static final long serialVersionUID = -103113318411928500L;
	
	//Default location of the properties file
	public static final String DEFAULT_PROP_FILE = "config" + File.separator + "simulator.properties";
	
	//Singleton
	public final static SimulatorProperties INSTANCE = new SimulatorProperties();
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Property keys
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Keys related to nodes
	public final static String NB_OF_NODES = "nodes.number";	
	public final static String NB_OF_CPUS = "nodes.cpunumber";	
	public final static String CPU_CAPACITY = "nodes.cpucapacity";	
	public final static String MEMORY_TOTAL = "nodes.memorytotal";
    public final static String NET_CAPACITY = "nodes.netbw";
	//Keys related to VMs
	public final static String NB_OF_VMS = "vm.number";		
	public final static String NB_OF_VCPUS = "vm.vcpunumber";	
	public final static String CPU_CONSUMPTION = "vm.cpuconsumption";
	public final static String MEMORY_CONSUMPTION = "vm.memoryconsumption";
	
	//Other keys
	public final static String CONFIGURATION_FILE = "config.file";
	public final static String DURATION = "simulator.duration";
	public final static String LOAD_PERIOD = "simulator.loadperiod";
    public final static String CRASH_PERIOD = "simulator.crashperiod";

	public final static String MEAN_LOAD = "load.mean";
	public final static String STD_LOAD = "load.std";
	
	
	public final static String SIMULATION = "simulation";
	public final static String MONITORING = "monitoring";
	public final static String WAIT_FOR_USER_INPUT = "simulator.waitforuserinput";
	public final static String WORKER_NODES_FILE = "simulator.workernodesfile";
	
	public final static String VIRTUAL_NODES_NAMES_FILE = "configgenerator.virtualnodesnamesfile";
	
	public final static String SEED = "loadinjector.seed";
	public final static String NB_OF_CPU_CONSUMPTION_SLOTS = "loadinjector.nbcpuconsumptionslots";
	public final static String MIN_PERCENTAGE_OF_ACTIVE_VMS = "loadinjector.minimumpercentageactive";
	public final static String MAX_PERCENTAGE_OF_ACTIVE_VMS = "loadinjector.maximumpercentageactive";
	public final static String STEP_BY_STEP = "loadinjector.stepbystep";
	
	//Keys related to scripts used when the simulator is deployed on a real system
	public final static String SCRIPT_CREATE_VMS = "script.createvms";
	public final static String SCRIPT_INJECT_LOAD = "script.injectload";
	
	private static final String SIMU_ALGO = "simulator.algorithm";
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Property default values
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//Default values related to nodes
	public final static int DEFAULT_NB_OF_NODES = 50;
	public final static int DEFAULT_NB_OF_CPUS = 4;
	public final static int DEFAULT_CPU_CAPACITY = 8000;
    public final static int DEFAULT_NET_CAPACITY = 125;
	public final static int DEFAULT_MEMORY_TOTAL = 8192;
	
	//Default values related to VMs
	public final static int DEFAULT_NB_OF_VMS = 200;
	public final static int DEFAULT_MIN_PERCENTAGE_OF_ACTIVE_VMS = 40;
	public final static int DEFAULT_MAX_PERCENTAGE_OF_ACTIVE_VMS = 70;
	public final static int DEFAULT_NB_OF_VCPUS = 1;
	public final static int DEFAULT_CPU_CONSUMPTION = 2000;
	public final static int DEFAULT_MEMORY_CONSUMPTION = 1024;
	public final static int DEFAULT_NB_OF_CPU_CONSUMPTION_SLOTS = 2;
	
	//Other default values
	public final static String DEFAULT_CONFIGURATION_FILE = "config" + File.separator + "initialConfiguration.txt";
	public final static int DEFAULT_DURATION = 1800; // in sec (default is 30min)
	public final static int DEFAULT_LOAD_PERIOD = 10; // in sec
    public final static int DEFAULT_CRASH_PERIOD = 300; // in sec

	public final static String DEFAULT_MEAN_LOAD = "50.0";
	public final static String DEFAULT_STD_LOAD = "50.0";	
	
	public static final long DEFAULT_SEED = 23;
	public final static boolean DEFAULT_STEP_BY_STEP = false;
	public final static String DEFAULT_VIRTUAL_NODES_NAMES_FILE = null;
	public final static boolean DEFAULT_SIMULATION = true;
	public final static boolean DEFAULT_MONITORING = false;
	public final static boolean DEFAULT_WAIT_FOR_USER_INPUT = false;
	public final static String DEFAULT_WORKER_NODES_FILE = null;
	
	//Default values related to scripts used when the simulator is deployed on a real system
	public final static String DEFAULT_SCRIPT_CREATE_VMS = null;
	public final static String DEFAULT_SCRIPT_INJECT_LOAD = null;

	private static final String DEFAULT_SIMU_ALGO = "entropy";


    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructors
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public SimulatorProperties(String file){
		super();
		
		try {
			BufferedReader reader = new BufferedReader(new FileReader(file));
			this.load(reader);
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public SimulatorProperties(){
		this(DEFAULT_PROP_FILE);
	}
	
	@Override
	public String getProperty(String key){
		String result = super.getProperty(key);
		
		if(result != null)
			return result.trim();
		
		else
			return result;
	}
	
	public static int getPropertyAsInt(String key, int defaultValue){
		String value = INSTANCE.getProperty(key);
		
		if(value != null)
			return Integer.parseInt(value);
		
		else
			return defaultValue;
	}
	
	public static long getPropertyAsLong(String key, long defaultValue){
		String value = INSTANCE.getProperty(key);
		
		if(value != null)
			return Long.parseLong(value);
		
		else
			return defaultValue;
	}
	
	public static boolean getPropertyAsBoolean(String key, boolean defaultValue){
		String value = INSTANCE.getProperty(key);
		
		if(value != null)
			return Boolean.parseBoolean(value);
		
		else
			return defaultValue;
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Methods related to nodes
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public static int getNbOfNodes(){
		return getPropertyAsInt(NB_OF_NODES, DEFAULT_NB_OF_NODES);
	}
	
	public static int getNbOfCPUs(){
		return getPropertyAsInt(NB_OF_CPUS, DEFAULT_NB_OF_CPUS);
	}
	
	public static int getCPUCapacity(){
		return getPropertyAsInt(CPU_CAPACITY, DEFAULT_CPU_CAPACITY);
	}

    public static int getNetCapacity() {
        return getPropertyAsInt(NET_CAPACITY, DEFAULT_NET_CAPACITY);
    }
	public static int getMemoryTotal(){
		return getPropertyAsInt(MEMORY_TOTAL, DEFAULT_MEMORY_TOTAL);
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Methods related to VMs
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public static int getNbOfVMs(){
		return getPropertyAsInt(NB_OF_VMS, DEFAULT_NB_OF_VMS);
	}
	
	public static int getMinPercentageOfActiveVMs(){
		return getPropertyAsInt(MIN_PERCENTAGE_OF_ACTIVE_VMS, DEFAULT_MIN_PERCENTAGE_OF_ACTIVE_VMS);
	}
	
	public static int getMaxPercentageOfActiveVMs(){
		return getPropertyAsInt(MAX_PERCENTAGE_OF_ACTIVE_VMS, DEFAULT_MAX_PERCENTAGE_OF_ACTIVE_VMS);
	}
	
	@Deprecated
	public static int getNbOfVCPUs(){
		return getPropertyAsInt(NB_OF_VCPUS, DEFAULT_NB_OF_VCPUS);
	}
	
	@Deprecated
	public static int getCPUConsumption(){
		return getPropertyAsInt(CPU_CONSUMPTION, DEFAULT_CPU_CONSUMPTION);
	}

	@Deprecated
	public static int getMemoryConsumption(){
		return getPropertyAsInt(MEMORY_CONSUMPTION, DEFAULT_MEMORY_CONSUMPTION);
	}
	
	public static int getNbOfCPUConsumptionSlots(){
		return getPropertyAsInt(NB_OF_CPU_CONSUMPTION_SLOTS, DEFAULT_NB_OF_CPU_CONSUMPTION_SLOTS);
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public static String getConfigurationFile(){
		return INSTANCE.getProperty(CONFIGURATION_FILE, DEFAULT_CONFIGURATION_FILE);
	}
	
	public static long getDuration() {
		return getPropertyAsInt(DURATION, DEFAULT_DURATION);
	}
	
	public static int getLoadPeriod(){
		return getPropertyAsInt(LOAD_PERIOD, DEFAULT_LOAD_PERIOD);
	}

	public static int getCrashPeriod(){
		return getPropertyAsInt(CRASH_PERIOD, DEFAULT_CRASH_PERIOD);
	}

	public static long getSeed(){
		return getPropertyAsLong(SEED, DEFAULT_SEED);
	}
	
	public static boolean getStepByStep(){
		return getPropertyAsBoolean(STEP_BY_STEP, DEFAULT_STEP_BY_STEP);
	}

	public static double getMeanLoad(){
		return Double.parseDouble(INSTANCE.getProperty(MEAN_LOAD, DEFAULT_MEAN_LOAD));
	}
	
	public static double getStandardDeviationLoad(){
		return Double.parseDouble(INSTANCE.getProperty(STD_LOAD, DEFAULT_STD_LOAD));
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	public static String getVirtualNodesNamesFile(){
		return INSTANCE.getProperty(VIRTUAL_NODES_NAMES_FILE, DEFAULT_VIRTUAL_NODES_NAMES_FILE);
	}
	
	public static boolean getSimulation(){
		return getPropertyAsBoolean(SIMULATION, DEFAULT_SIMULATION);
	}
	
	public static boolean getMonitoring(){
		return getPropertyAsBoolean(MONITORING, DEFAULT_MONITORING)
				&& !getSimulation();
	}
	
	public static boolean getWaitForUserInput(){
		return getPropertyAsBoolean(WAIT_FOR_USER_INPUT, DEFAULT_WAIT_FOR_USER_INPUT);
	}
	
	public static String getWorkerNodesFile(){
		return INSTANCE.getProperty(WORKER_NODES_FILE, DEFAULT_WORKER_NODES_FILE);
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Methods related to scripts used when the simulator is deployed on a real system
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public static String getScriptCreateVMs(){
		return INSTANCE.getProperty(SCRIPT_CREATE_VMS, DEFAULT_SCRIPT_CREATE_VMS);
	}
	
	public static String getScriptInjectLoad(){
		return INSTANCE.getProperty(SCRIPT_INJECT_LOAD, DEFAULT_SCRIPT_INJECT_LOAD);
	}
	public static String getAlgo() {
		return INSTANCE.getProperty(SIMU_ALGO, DEFAULT_SIMU_ALGO);
	}
	
	public static void main(String[] args){
		System.out.println(SimulatorProperties.INSTANCE);
		System.out.println("configuration file: " + SimulatorProperties.getConfigurationFile());
		System.out.println("number of nodes: " + SimulatorProperties.getNbOfNodes());
		System.out.println("number of cpus: " + SimulatorProperties.getNbOfCPUs());
		System.out.println("cpu capacity: " + SimulatorProperties.getCPUCapacity());
		System.out.println("memory total: " + SimulatorProperties.getMemoryTotal());
		System.out.println("nb of vms: " + SimulatorProperties.getNbOfVMs());
		System.out.println("min percentage of active vms: " + SimulatorProperties.getMinPercentageOfActiveVMs());
		System.out.println("nb of vcpus: " + SimulatorProperties.getNbOfVCPUs());
		System.out.println("cpu consumption: " + SimulatorProperties.getCPUConsumption());
		System.out.println("memory consumption: " + SimulatorProperties.getMemoryConsumption());
		System.out.println("Simuation duration: " + SimulatorProperties.getDuration());
		System.out.println("Load period: " + SimulatorProperties.getLoadPeriod());
        System.out.println("Crash period: " + SimulatorProperties.getCrashPeriod());
		System.out.println("seed: " + SimulatorProperties.getSeed());
		System.out.println("nb slots: " + SimulatorProperties.getNbOfCPUConsumptionSlots());
		System.out.println("step by step: " + SimulatorProperties.getStepByStep());
		System.out.println("nodes names file: " + SimulatorProperties.getVirtualNodesNamesFile());
		System.out.println("simulation: " + SimulatorProperties.getSimulation());
		System.out.println("monitoring: " + SimulatorProperties.getMonitoring());
		System.out.println("wait for user input: " + SimulatorProperties.getWaitForUserInput());
		System.out.println("worker nodes file: " + SimulatorProperties.getWorkerNodesFile());
		System.out.println("script to create vms: " + SimulatorProperties.getScriptCreateVMs());
		System.out.println("script to inject getCPUDemand: " + SimulatorProperties.getScriptInjectLoad());
	}



}
