package scheduling.dvms2;

import scheduling.GeneralProperties;

import java.io.File;

/**
 * Created by jonathan on 27/11/14.
 */
public class DvmsProperties extends GeneralProperties {


    private static final long serialVersionUID = 7229931356566105645L;

    //Default location of the properties file
    public static final String DEFAULT_PROP_FILE = "config" + File.separator + "dvms.properties";

    //Singleton
    public final static DvmsProperties INSTANCE = new DvmsProperties();


    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Property keys
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public final static String IS_LOCALITY_BASED_SCHEDULER = "is_locality_based_scheduler";
    public final static String MINIMUM_PARTITION_SIZE = "minimum_partition_size";

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Property default values
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public final static boolean DEFAULT_IS_LOCALITY_BASED_SCHEDULER = false;
    public final static int DEFAULT_MINIMUM_PARTITION_SIZE = -1;



    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Constructors
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public DvmsProperties(String file) {
        super(file);
    }

    public DvmsProperties() {
        this(DEFAULT_PROP_FILE);
    }


    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Class methods
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static boolean isLocalityBasedScheduler() {
        return INSTANCE.getPropertyAsBoolean(IS_LOCALITY_BASED_SCHEDULER, DEFAULT_IS_LOCALITY_BASED_SCHEDULER);

    }

    public static int getMinimumPartitionSize(){
        return INSTANCE.getPropertyAsInt(MINIMUM_PARTITION_SIZE, DEFAULT_MINIMUM_PARTITION_SIZE);
    }

}