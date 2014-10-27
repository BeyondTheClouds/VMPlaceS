package trace;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;
import scheduling.entropyBased.dvms2.dvms.LoggingActor;
import scheduling.entropyBased.dvms2.dvms.LoggingProtocol;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Copyright 2012-2013-2014. The SimGrid Team. All rights reserved.
 * <p/>
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 * <p/>
 * This file is the launcher on the Simgrid VM injector
 * The main is composed of three part:
 * 1./ Generate the deployment file according to the number of nodes and the algorithm you want to evaluate
 * 2./ Configure, instanciate and assign each VM on the different PMs
 * 3./ Launch the injector and the other simgrid processes in order to run the simulation.
 * <p/>
 * Please note that all parameters of the simulation are given in the ''simulator.properties'' file available
 * in the ''config'' directory
 *
 * @author: adrien.lebre@inria.fr
 * @author: jonathan.pastor@inria.fr
 */
public class TraceImpl {

    class TState {

        private String value;
        private double datetime;
        private String data;

        public TState(String value, String data, double datetime) {
            this.value = value;
            this.data = data;
            this.datetime = datetime;
        }

        public String getValue() {
            return value;
        }
        public String getData() { return data; }
    }

    class TValue{
        private double value;
        private double datetime;

        public TValue(double value, double datetime) {
            this.value = value;
            this.datetime = datetime;
        }

        public double getValue() {
            return value;
        }
    }
    /**
     * Hashmap for host states: key : name of the host, value hashMap of Linkedlist of states
     */
    HashMap<String, HashMap<String, LinkedList<TState>>> hostStates;

    protected double now() {
        return Msg.getClock();
    }

    void writeJson(double time, String origin, String state, String value, String data, double duration) {
        LoggingActor.write(new LoggingProtocol.PopState(time, origin, state, value, data, duration));
    }

    /**
     * Hashmap for host variables: key : name of the host, value hashMap of variables
     */
    private HashMap<String, HashMap<String, TValue>> hostVariables;

    private static TraceImpl instance;

    public static TraceImpl getInstance() {
        if(instance == null) {
            instance = new TraceImpl();
        }
        return instance;
    }

    public TraceImpl() {
        hostStates = new HashMap<String, HashMap<String, LinkedList<TState>>>();
        hostVariables = new HashMap<String, HashMap<String, TValue>>();
        for (int i=0 ; i< Host.getCount(); i++){
            hostStates.put(Host.all()[i].getName(),  new HashMap<String, LinkedList<TState>>());
            hostVariables.put(Host.all()[i].getName(),  new HashMap<String, TValue>());
        }
    }



    public void flush(){
        // Retrieve all hosts
        for (String host : hostStates.keySet()) {
            // Retrieve all states
            HashMap<String, LinkedList<TState>> currentHostStates = hostStates.get(host);
            for (String state : hostStates.get(host).keySet()) {
                // For each state, retrieve the states stack and pop them
                while(currentHostStates.containsKey(state) && !currentHostStates.get(state).isEmpty()) {
                    hostPopState(host, state);
                }
            }
        }

    }

    /**
     * Declare information about the simulation.
     */
    public void simulationDeclare(String algorithm, int serverCount, int serviceNodeCount, int vmCount) {

        String simulationDescriptionAsJson = String.format("{\"algorithm\": \"%s\", \"server_count\": %d, \"service_node_count\": %d, \"vm_count\": %d}", algorithm, serverCount, serviceNodeCount, vmCount);

        writeJson(Msg.getClock(), "simulator", "SIMULATION", "START", simulationDescriptionAsJson, 0);
    }
    
    /**
     * Declare a user state that will be associated to a given host.
     */
    void hostStateDeclare(String host, String state) {

        HashMap<String, LinkedList<TState>> currentHostStates;
        if (hostStates.containsKey(host)) {
            currentHostStates = hostStates.get(host);
        } else {
            currentHostStates = new HashMap<String, LinkedList<TState>>();
        }


        if (!currentHostStates.containsKey(state)) {
            LinkedList<TState> listOfStates = new LinkedList<TState>();
            currentHostStates.put(state, listOfStates);

            hostStates.put(host, currentHostStates);
        }

    }

    /**
     * Declare a user state that will be associated to hosts.
     */
    public void hostStateDeclare(String state) {
        for (String key : hostStates.keySet()) {
            hostStateDeclare(key, state);
        }
    }


    /**
     * Declare a new value for a user state associated to hosts.
     * @param state
     * @param value
     * @param color
     */
    public static void hostStateDeclareValue(String state, String value, String color){
        //usefull only for MSG api
        org.simgrid.trace.Trace.hostStateDeclareValue(state, value, color);
    }

    /**
     * Set the user state to the given value.
     *
     * @param host
     * @param state
     * @param value
     */
    void hostSetState(String host, String state, String value) {
        hostSetState(host, state, value, "");
    }

    /**
     * Set the user state to the given value and data.
     *
     * @param host
     * @param state
     * @param value
     * @param data
     */
    void hostSetState(String host, String state, String value, String data) {
        if (!hostStates.containsKey(host)) {
            hostStateDeclare(host, state);
        }
        HashMap<String, LinkedList<TState>> currentHostStates = hostStates.get(host);

        while(currentHostStates.containsKey(state) && !currentHostStates.get(state).isEmpty()) {
            hostPopState(host, state);
        }

        LinkedList<TState> listOfStates = new LinkedList<TState>();
        listOfStates.add(new TState(value, data, now()));


        currentHostStates.put(state, listOfStates);
        hostStates.put(host, currentHostStates);

    }

    /**
     * Pop the last value of a state of a given host.
     *
     * @param host
     * @param state
     */
    void hostPopState(String host, String state) {

        double now = now();

        if (!hostStates.containsKey(host)) {
            hostStateDeclare(host, state);
        }
        HashMap<String, LinkedList<TState>> currentHostStates = hostStates.get(host);

        LinkedList<TState> listOfStates;
        if (currentHostStates.containsKey(state)) {
            listOfStates = currentHostStates.get(state);

            if(listOfStates.size() > 0) {
                TState lastState = listOfStates.removeLast();

                double duration = now() - lastState.datetime;

                writeJson(lastState.datetime, host, state, lastState.getValue(), lastState.getData(), duration);
            }

        } else {
            listOfStates = new LinkedList<TState>();
        }
        currentHostStates.put(state, listOfStates);

        hostStates.put(host, currentHostStates);
    }

    /**
     * Push a new value for a state of a given host.
     *
     * @param host
     * @param state
     * @param value
     */
    void hostPushState(String host, String state, String value) {
        hostPushState(host, state, value, "");
    }

    /**
     * Push a new couple of value and data for a state of a given host.
     *
     * @param host
     * @param state
     * @param value
     * @param data
     */
    void hostPushState(String host, String state, String value, String data) {

        if (!hostStates.containsKey(host)) {
            hostStateDeclare(host, state);
        }
        HashMap<String, LinkedList<TState>> currentHostStates = hostStates.get(host);

        LinkedList<TState> listOfStates;
        if (currentHostStates.containsKey(state)) {
            listOfStates = currentHostStates.get(state);
        } else {
            listOfStates = new LinkedList<TState>();
        }

        listOfStates.add(new TState(value, data, now()));

        currentHostStates.put(state, listOfStates);

        hostStates.put(host, currentHostStates);

    }




    void hostVariableDeclare(String host, String variable) {

        HashMap<String, TValue> currentHostVariables;
        if (hostVariables.containsKey(host)) {
            currentHostVariables = hostVariables.get(host);
        } else {
            currentHostVariables = new HashMap<String, TValue>();
        }

        if (!currentHostVariables.containsKey(variable)) {
            currentHostVariables.put(variable, new TValue(0, now()));

            hostVariables.put(host, currentHostVariables);
        }

    }

    /**
     * Declare a new user variable associated to hosts.
     *
     * @param variable
     */
    void hostVariableDeclare(String variable) {
        for (String key : hostVariables.keySet()) {
            hostVariableDeclare(key, variable);
        }
    }

    /**
     * Set the value of a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    void  hostVariableSet(String host, String variable, double value) {
        if (!hostVariables.containsKey(host)) {
            hostVariableDeclare(host, variable);
        }
        HashMap<String, TValue> currentHostVariable = hostVariables.get(host);

        currentHostVariable.put(variable, new TValue(value, now()));
        hostVariables.put(host, currentHostVariable);
    }

    /**
     * Subtract a value from a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    void hostVariableSub(java.lang.String host, java.lang.String variable, double value) {
        if (!hostVariables.containsKey(host)) {
            hostVariableDeclare(host, variable);
        }
        HashMap<String, TValue> currentHostVariable = hostVariables.get(host);

        double tmp = currentHostVariable.get(variable).getValue();
        currentHostVariable.put(variable, new TValue((tmp - value), now()));
        hostVariables.put(host, currentHostVariable);
    }

    /**
     * Add a value to a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    void hostVariableAdd(java.lang.String host, java.lang.String variable, double value) {
        if (!hostVariables.containsKey(host)) {
            hostVariableDeclare(host, variable);
        }
        HashMap<String, TValue> currentHostVariable = hostVariables.get(host);

        if(!currentHostVariable.containsKey(variable)) {
            hostVariableSet(host, variable, value);
        } else {
            double tmp = currentHostVariable.get(variable).getValue();
            currentHostVariable.put(variable, new TValue((tmp+value), now()));
            hostVariables.put(host, currentHostVariable);
        }

    }
}
