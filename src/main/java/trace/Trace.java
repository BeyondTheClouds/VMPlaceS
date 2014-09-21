package trace;

/**
 * Created by jonathan on 17/09/14.
 */
public class Trace {

    /**
     * Declare information about the simulation.
     */
    public static void simulationDeclare(String algorithm, int serverCount, int vmCount) {
        TraceImpl.getInstance().simulationDeclare(algorithm, serverCount, vmCount);
    }

    /**
     * Declare a user state that will be associated to a given host.
     */
    public static void hostStateDeclare(String host, String state) {
        TraceImpl.getInstance().hostStateDeclare(host, state);
    }

    /**
     * Declare a user state that will be associated to hosts.
     */
    public static void hostStateDeclare(String state) {
        TraceImpl.getInstance().hostStateDeclare(state);
    }

    /**
     * Declare a new value for a user state associated to hosts.
     *
     * @param state
     * @param value
     * @param color
     */
    public static void hostStateDeclareValue(String state, String value, String color) {
        TraceImpl.getInstance().hostStateDeclareValue(state, value, color);
    }

    /**
     * Set the user state to the given value.
     *
     * @param host
     * @param state
     * @param value
     */
    public static void hostSetState(String host, String state, String value) {
        TraceImpl.getInstance().hostSetState(host, state, value);
    }


    /**
     * Set the state to the given value and data.
     *
     * @param host
     * @param state
     * @param value
     * @param data
     */
    public static void hostSetState(String host, String state, String value, String data) {
        TraceImpl.getInstance().hostSetState(host, state, value, data);
    }

    /**
     * Pop the last value of a state of a given host.
     *
     * @param host
     * @param state
     */
    public static void hostPopState(String host, String state) {
        TraceImpl.getInstance().hostPopState(host, state);
    }

    /**
     * Push a new value for a state of a given host.
     *
     * @param host
     * @param state
     * @param value
     */
    public static void hostPushState(String host, String state, String value) {
        TraceImpl.getInstance().hostPushState(host, state, value);
    }

    /**
     * Push a new value for a state of a given host.
     *
     * @param host
     * @param state
     * @param value
     */
    public static void hostPushState(String host, String state, String value, String data) {
        TraceImpl.getInstance().hostPushState(host, state, value, data);
    }


    /**
     * Declare a new user variable associated to hosts.
     *
     * @param variable
     */
    public static void hostVariableDeclare(String variable) {
        TraceImpl.getInstance().hostVariableDeclare(variable);
    }

    /**
     * Set the value of a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    public static void hostVariableSet(String host, String variable, double value) {
        TraceImpl.getInstance().hostVariableSet(host, variable, value);
    }

    /**
     * Subtract a value from a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    public static void hostVariableSub(String host, String variable, double value) {
        TraceImpl.getInstance().hostVariableSub(host, variable, value);
    }

    /**
     * Add a value to a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    public static void hostVariableAdd(String host, String variable, double value) {
        TraceImpl.getInstance().hostVariableAdd(host, variable, value);
    }

    /**
     * Flush all data that has not been flushed to the disk.
     * This function should be invoked at the end of your simulation !
     */
    public static void flush(){
        TraceImpl.getInstance().flush();
    }

}
