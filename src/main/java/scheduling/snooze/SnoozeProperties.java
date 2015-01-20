package scheduling.snooze;

import configuration.SimulatorProperties;
import scheduling.GeneralProperties;

import java.io.File;

/**
 * Created by alebre on 16/07/14.
 */
public class SnoozeProperties extends GeneralProperties {


        private static final long serialVersionUID = 7229931356566105645L;

        //Default location of the properties file
        public static final String DEFAULT_PROP_FILE = "config" + File.separator + "snooze.properties";

        //Singleton
        public final static SnoozeProperties INSTANCE = new SnoozeProperties();


        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Property keys
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        public final static String ALG_VARIANT = "snooze.alg-variant";
        public final static String GM_NUMBER = "snooze.gm-number";
        public final static String HEARTBEAT_PERIODICITY = "snooze.hb-periodicity";
        public final static String HEARTBEAT_TIMEOUT = "snooze.hb-timeout";
        public final static String SCHEDULING_PERIODIC = "snooze.scheduling-periodic";
        public final static String SCHEDULING_PERIODICITY = "snooze.scheduling-periodicity";
        public final static String INFO_LEVEL = "snooze.info-level";
        public final static String INFO_PERIODICITY = "snooze.info-periodicity";
        public final static String SIMULATE_LOCALCOMPUTATION = "snooze.simulate-localcomputation";

        public final static String FAULT_MODE = "snooze.faultmode";
        public final static String FAULT_MODE_GLPERIOD = "snooze.glcrash-period";
         public final static String FAULT_MODE_GMPERIOD = "snooze.gmcrash-period";

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Property default values
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        public final static int DEFAULT_GM_NUMBER = SimulatorProperties.getNbOfServiceNodes();
        public final static long DEFAULT_HEARTBEAT_PERIODICITY = 2;
        public final static boolean DEFAULT_SCHEDULING_PERIODIC = true;
        public final static long DEFAULT_SCHEDULING_PERIODICITY = 30;
        public final static long DEFAULT_HEARTBEAT_TIMEOUT = 5;


        public final static int DEFAULT_INFO_LEVEL = 2;
        public final static int DEFAULT_INFO_PERIODICITY = 5;

        public static boolean DEFAULT_SIMULATE_LOCALCOMPUTATION = false;
        public static boolean DEFAULT_FAULT_MODE = false ;
        public static long DEFAULT_FAULT_MODE_GLPERIOD = SimulatorProperties.getDuration()/2 ;
        public static long DEFAULT_FAULT_MODE_GMPERIOD = 300 ;

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Constructors
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        public SnoozeProperties(String file) {
            super(file);
        }

        public SnoozeProperties() {
            this(DEFAULT_PROP_FILE);
        }


        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Class methods
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static String getAlgVariant() {
        String algVariant = "";
        algVariant = INSTANCE.getProperty(ALG_VARIANT);
        if (algVariant.equals(""))
            algVariant = "hierarchical-" +
                    (getSchedulingPeriodic() ? "periodic" + getSchedulingPeriodicity() + "s-" : "reactive-") +
                    (SimulatorProperties.getNbOfServiceNodes()-1) + "GMs-" +
                    SimulatorProperties.getNbOfHostingNodes() + "LCs-" +
                    "beatFreq" + getHeartBeatPeriodicity() +"s";
        return algVariant;
    }

    public static int getGMNumber() {
            return INSTANCE.getPropertyAsInt(GM_NUMBER, DEFAULT_GM_NUMBER);

        }

        public static long getHeartBeatPeriodicity(){
            return INSTANCE.getPropertyAsLong(HEARTBEAT_PERIODICITY, DEFAULT_HEARTBEAT_PERIODICITY);
        }

        public static boolean getSchedulingPeriodic(){
           return INSTANCE.getPropertyAsBoolean(SCHEDULING_PERIODIC, DEFAULT_SCHEDULING_PERIODIC);
        }

        public static long getSchedulingPeriodicity(){
            return INSTANCE.getPropertyAsLong(SCHEDULING_PERIODICITY, DEFAULT_SCHEDULING_PERIODICITY);
        }

        public static long getHeartBeatTimeout() {
         return INSTANCE.getPropertyAsLong(HEARTBEAT_TIMEOUT, DEFAULT_HEARTBEAT_TIMEOUT);
        }

        public static int getInfoLevel() {
            return INSTANCE.getPropertyAsInt(INFO_LEVEL, DEFAULT_INFO_LEVEL);
        }

        public static int getInfoPeriodicity() {
            return INSTANCE.getPropertyAsInt(INFO_PERIODICITY, DEFAULT_INFO_PERIODICITY);
        }

    public static boolean shouldISleep() {
        return INSTANCE.getPropertyAsBoolean(SIMULATE_LOCALCOMPUTATION, DEFAULT_SIMULATE_LOCALCOMPUTATION);
    }

    public static boolean faultMode() {
        return INSTANCE.getPropertyAsBoolean(FAULT_MODE, DEFAULT_FAULT_MODE);
    }

    public static long getGLFaultPeriodicity(){
        return INSTANCE.getPropertyAsLong(FAULT_MODE_GLPERIOD, DEFAULT_FAULT_MODE_GLPERIOD);
    }

    public static long getGMFaultPeriodicity(){
        return INSTANCE.getPropertyAsLong(FAULT_MODE_GMPERIOD, DEFAULT_FAULT_MODE_GMPERIOD);
    }



    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Methods for properties currently not stored in the properties file
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////


        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Other methods
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

}
