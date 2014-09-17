package trace;

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
public class Trace {


    private Trace instance;

    public Trace getInstance() {
        if (instance == null) {
            instance = new Trace();
        }
        return instance;
    }


    class TState {

        private String value;
        private double datetime;

        public TState(String value, double datetime) {
            this.value = value;
            this.datetime = datetime;
        }
    }

    void writeJson(double time, String origin, String state, double duration) {
        LoggingActor.write(new LoggingProtocol.PopState(time, origin, state, duration));
    }

    /**
     * Hashmap: key : name of the host, value hashMap of Linkedlist of states
     */
    static HashMap<String, HashMap<String, LinkedList<TState>>> hostStates = new HashMap<String, HashMap<String, LinkedList<TState>>>();

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
    void hostStateDeclare(String state) {

        for (String key : hostStates.keySet()) {

            hostStateDeclare(key, state);
        }
    }

    /**
     * Declare a new value for a user state associated to hosts.
     *
     * @param state
     * @param value
     * @param color
     */
    void hostStateDeclareValue(String state, String value, String color) {


    }

    /**
     * Set the user state to the given value.
     *
     * @param host
     * @param state
     * @param value
     */
    void hostSetState(String host, String state, String value) {
        // TODO write in the json file: name of the host, name of the state + name of the value + timestamp


        if (!hostStates.containsKey(host)) {
            hostStateDeclare(host, state);
        }
        HashMap<String, LinkedList<TState>> currentHostStates = hostStates.get(host);

        while(!currentHostStates.get(state).isEmpty()) {
            hostPopState(host, state);
        }


        LinkedList<TState> listOfStates = new LinkedList<TState>();
        listOfStates.add(new TState(value, Msg.getClock()));


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

        double now = Msg.getClock();

        if (!hostStates.containsKey(host)) {
            hostStateDeclare(host, state);
        }
        HashMap<String, LinkedList<TState>> currentHostStates = hostStates.get(host);

        LinkedList<TState> listOfStates;
        if (currentHostStates.containsKey(state)) {
            listOfStates = currentHostStates.get(host);

            if(listOfStates.size() > 0) {
                TState lastState = listOfStates.removeLast();

                double duration = now - lastState.datetime;

                writeJson(now, host, state, duration);
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
    void hostPushState(java.lang.String host, java.lang.String state, java.lang.String value) {

        if (!hostStates.containsKey(host)) {
            hostStateDeclare(host, state);
        }
        HashMap<String, LinkedList<TState>> currentHostStates = hostStates.get(host);

        LinkedList<TState> listOfStates;
        if (currentHostStates.containsKey(state)) {
            listOfStates = currentHostStates.get(host);
        } else {
            listOfStates = new LinkedList<TState>();
        }

        listOfStates.add(new TState(state, Msg.getClock()));

        currentHostStates.put(state, listOfStates);

        hostStates.put(host, currentHostStates);

    }


    /**
     * Declare a new user variable associated to hosts.
     *
     * @param variable
     */
    void hostVariableDeclare(String variable) {

    }

    /**
     * Set the value of a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    void hostVariableSet(java.lang.String host, java.lang.String variable, double value) {

    }

    /**
     * Subtract a value from a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    void hostVariableSub(java.lang.String host, java.lang.String variable, double value) {

    }

    /**
     * Add a value to a variable of a host.
     *
     * @param host
     * @param variable
     * @param value
     */
    void hostVariableAdd(java.lang.String host, java.lang.String variable, double value) {

    }
}
