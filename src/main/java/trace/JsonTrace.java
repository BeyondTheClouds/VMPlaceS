package trace;

/**
 * Created by jonathan on 17/09/14.
 */
class JsonTrace extends Trace {

    /**
     * Declare a user state that will be associated to a given host.
     */
    void hostStateDeclare(String host, String state) {
        super.hostStateDeclare(host, state);
    }

    /**
     * Declare a user state that will be associated to hosts.
     */
    void hostStateDeclare(String state) {
        super.hostStateDeclare(state);
    }

    /**
     * Declare a new value for a user state associated to hosts.
     *
     * @param state
     * @param value
     * @param color
     */
    void hostStateDeclareValue(String state, String value, String color) {
        super.hostStateDeclareValue(state, value, color);
    }

    /**
     * Set the user state to the given value.
     *
     * @param host
     * @param state
     * @param value
     */
    void hostSetState(String host, String state, String value) {
        super.hostSetState(host, state, value);
    }

    /**
     * Pop the last value of a state of a given host.
     *
     * @param host
     * @param state
     */
    void hostPopState(String host, String state) {
        super.hostPopState(host, state);
    }

    /**
     * Push a new value for a state of a given host.
     *
     * @param host
     * @param state
     * @param value
     */
    void hostPushState(String host, String state, String value) {
        super.hostPushState(host, state, value);
    }


    /**
     * Declare a new user variable associated to hosts.
     *
     * @param variable
     */
    void hostVariableDeclare(String variable) {
        super.hostVariableDeclare(variable);
    }

    /**
     * Set the value of a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    void hostVariableSet(String host, String variable, double value) {
        super.hostVariableSet(host, variable, value);
    }

    /**
     * Subtract a value from a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    void hostVariableSub(String host, String variable, double value) {
        super.hostVariableSub(host, variable, value);
    }

    /**
     * Add a value to a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    void hostVariableAdd(String host, String variable, double value) {
        super.hostVariableAdd(host, variable, value);
    }
}
