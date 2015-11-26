package scheduling.entropyBased.btrplace;

import java.io.*;
import java.util.Properties;

/**
 * Represents properties mostly for the BtrPlace Scheduler implementation.
 *
 * @author Hadrien Gerard
 * @version 1.0
 */
public class BtrPlaceProperties extends Properties {

	private static final long serialVersionUID = 7229931356566105646L;

	//Default location of the properties file
	public static final String DEFAULT_PROP_FILE = "config" + File.separator + "btrplace.properties";

	//Singleton
	public final static BtrPlaceProperties INSTANCE = new BtrPlaceProperties();


	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Property keys
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * State if the algorithm must try to improve the first computed solution.
	 */
	public final static String BTRPLACE_SCHEDULER_OPTIMIZE = "btrplace.scheduler.doOptimize";


	/**
	 * State if the algorithm only have to repair the model instead of
	 * rebuilding a complete new solution.
	 */
	public final static String BTRPLACE_SCHEDULER_REPAIR = "btrplace.scheduler.doRepair";


	/**
	 * The the minimum timeout value for the solving process.
	 */
	public final static String BTRPLACE_SCHEDULER_TIMELIMIT_MIN = "btrplace.scheduler.minTimeLimit";


	/**
	 * The maximum timeout value for the solving process.
	 */
	public final static String BTRPLACE_SCHEDULER_TIMELIMIT_MAX = "btrplace.scheduler.maxTimeLimit";


	/**
	 * The root path for the logs of the BtrPlace scheduler.
	 */
	public final static String BTRPLACE_LOG_BASEPATH = "btrplace.log.basePath";

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Property default values
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public final static Boolean DEFAULT_BTRPLACE_SCHEDULER_OPTIMIZE = false;
	public final static Boolean DEFAULT_BTRPLACE_SCHEDULER_REPAIR = false;
	public final static Integer DEFAULT_BTRPLACE_SCHEDULER_TIMELIMIT_MIN = 8;
	public final static Integer DEFAULT_BTRPLACE_SCHEDULER_TIMELIMIT_MAX = 60;
	public final static String DEFAULT_BTRPLACE_LOG_BASEPATH = "logs/entropy/btrplace/";

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructors
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public BtrPlaceProperties(String file) {
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

	public BtrPlaceProperties() {
		this(DEFAULT_PROP_FILE);
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Class methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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

	public static boolean getPropertyAsBoolean(String key, boolean defaultValue){
		String value = INSTANCE.getProperty(key);

		if(value != null)
			return Boolean.parseBoolean(value);

		else
			return defaultValue;
	}

	/**
	 * State if the algorithm must try to improve the first computed solution.
	 */
	public static boolean doOptimize() {
		return getPropertyAsBoolean(BTRPLACE_SCHEDULER_OPTIMIZE, DEFAULT_BTRPLACE_SCHEDULER_OPTIMIZE);
	}

	/**
	 * State if the algorithm only have to repair the model instead of
	 * rebuilding a complete new solution.
	 */
	public static boolean doRepair() {
		return getPropertyAsBoolean(BTRPLACE_SCHEDULER_REPAIR, DEFAULT_BTRPLACE_SCHEDULER_REPAIR);
	}

	/**
	 * The the minimum timeout value for the solving process.
	 */
	public static int getMinTimeLimit(){
		return getPropertyAsInt(BTRPLACE_SCHEDULER_TIMELIMIT_MIN, DEFAULT_BTRPLACE_SCHEDULER_TIMELIMIT_MIN);
	}

	/**
	 * The maximum timeout value for the solving process.
	 */
	public static int getMaxTimeLimit(){
		return getPropertyAsInt(BTRPLACE_SCHEDULER_TIMELIMIT_MAX, DEFAULT_BTRPLACE_SCHEDULER_TIMELIMIT_MAX);
	}

	/**
	 * The root path for the logs of the BtrPlace scheduler.
	 */
	public static String getLogBasePath(){
		return INSTANCE.getProperty(BTRPLACE_LOG_BASEPATH, DEFAULT_BTRPLACE_LOG_BASEPATH);
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public static void main(String[] args){
		System.out.println(BtrPlaceProperties.INSTANCE);
	}
}