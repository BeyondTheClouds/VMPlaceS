package scheduling;

import configuration.SimulatorProperties;
import configuration.XHost;
import org.simgrid.msg.Msg;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

/**
 * Singleton used to build the scheduler given in simulator.properties.
 */
public enum SchedulerBuilder {

    /**
     * Builder instance.
     */
    INSTANCE;

    /**
     * Scheduler class to instantiate.
     */
    private Class<?> schedulerClass = null;

    /**
     * Gets the scheduler class to instantiate later.
     */
    SchedulerBuilder() {
        try {
            schedulerClass = Class.forName(SimulatorProperties.getImplementation());
        } catch (ClassNotFoundException e) {
            Msg.critical("Scheduler class not found. Check the value simulator.implementation in the simulator properties file.");
            System.err.println(e);
            System.exit(-1);
        }
    }

    /**
     * Gets the builder instance.
     * @return instance
     */
    public static SchedulerBuilder getInstance() {
        return INSTANCE;
    }

    /**
     * Instantiates the scheduler.
     * @param xHosts xHosts
     * @return instantiated scheduler
     */
    public Scheduler build(Collection<XHost> xHosts) {
        Constructor<?> schedulerConstructor;
        try {
            schedulerConstructor = schedulerClass.getConstructor(Collection.class);
            return (Scheduler) schedulerConstructor.newInstance(xHosts);
        } catch (Exception e) {
            handleExceptions(e);
        } 

        return null;
    }

    /**
     * Instantiates the scheduler.
     * @param xHosts xHosts
     * @param id id
     * @return instantiated scheduler
     */
    public Scheduler build(Collection<XHost> xHosts, Integer id) {
        Constructor<?> schedulerConstructor;
        try {
            schedulerClass = Class.forName(SimulatorProperties.getImplementation());
            schedulerConstructor = schedulerClass.getConstructor(Collection.class, Integer.class);
            return (Scheduler) schedulerConstructor.newInstance(xHosts, id);
        } catch (Exception e) {
            handleExceptions(e);
        }
        return null;
    }

    /**
     * Handles builder methods exceptions. This will stop the program.
     * @param e thrown exception
     */
    private void handleExceptions(Exception e) {
       if (e instanceof NoSuchMethodException) {
            Msg.critical("Scheduler constructor not found. This should never happen!!");
            System.err.println(e);
            System.exit(-1);
       } else if (e instanceof InstantiationException) {
            Msg.critical("Scheduler instantiation issue");
            System.err.println(e);
            System.exit(-1);
       } else if (e instanceof IllegalAccessException) {
            Msg.critical("Scheduler constructor could not be accessed");
            System.err.println(e);
            System.exit(-1);
       } else if (e instanceof InvocationTargetException) {
            Msg.critical("Invocation target exception while instantiating the scheduler");
            System.err.println(e);
            System.exit(-1);
        } else {
            Msg.critical("Unhandled exception");
            System.err.println(e);
            System.exit(-1);
        }
    }
    
}
