package trace;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Copyright 2012-2013-2014. The SimGrid Team. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 *
 * This file is the launcher on the Simgrid VM injector
 * The main is composed of three part:
 * 1./ Generate the deployment file according to the number of nodes and the algorithm you want to evaluate
 * 2./ Configure, instanciate and assign each VM on the different PMs
 * 3./ Launch the injector and the other simgrid processes in order to run the simulation.
 *
 * Please note that all parameters of the simulation are given in the ''simulator.properties'' file available
 * in the ''config'' directory
 *
 * @author: adrien.lebre@inria.fr
 * @author: jonathan.pastor@inria.fr
 *
 */
public class Trace {


    class TState {

        private String value;
        private double datetime;

        public TState (String value, double datetime) {
            this.value = value;
            this.datetime = datetime;
        }
    }

    /**
     * Hashmap for host states: key : name of the host, value hashMap of Linkedlist of states
     */
    HashMap<String, HashMap<String, LinkedList<TState>>> hostStates;

    /**
     * Hashmap for host variables: key : name of the host, value hashMap of variables
     */
    HashMap<String, HashMap<String, Double> hostVariables;

    private Trace instance;

    public Trace getInstance() {
        if(instance == null) {
            instance = new Trace();
        }
        return instance;
    }

    public Trace(){
        hostStates = new HashMap<String, HashMap<String,LinkedList<TState>>>();
        hostVariables = new HashMap<String, HashMap<String, Double>>;
    }


    /**
     *     Declare a user state that will be associated to hosts.
     */
    public static void hostStateDeclare(String name) {
         }

    /**
     *  Declare a new value for a user state associated to hosts.
     * @param state
     * @param value
     * @param color
     */
    public static void    hostStateDeclareValue(String state, String value, String color){
           //usefull only for MSG api
        org.simgrid.trace.Trace.hostStateDeclareValue(state, value, color);
    }

    /**
     *  Set the user state to the given value.
     * @param host
     * @param state
     * @param value
     */
    void hostSetState(String host, String state, String value){

        HashMap<String, LinkedList<TState>> currentHostStates;
        if(! hostStates.containsKey(host)) {
            currentHostStates = hostStates.get(host);
        } else {
            currentHostStates = new HashMap<String, LinkedList<TState>>();
        }

        LinkedList<TState> listOfStates = new LinkedList<TState>();
        listOfStates.add(new TState(state, Msg.getClock()));

        currentHostStates.put(state, listOfStates);

        hostStates.put(host, currentHostStates);

        // TODO MSG.Trace code
        // TODO JSON code
    }

    /**
     *   Pop the last value of a state of a given host.
     * @param host
     * @param state
     */
    static void hostPopState(String host, String state){


    }

    /**
     *  Push a new value for a state of a given host.
     * @param host
     * @param state
     * @param value
     */
    static void hostPushState(java.lang.String host, java.lang.String state, java.lang.String value){

    }


    /**
     *   Declare a new user variable associated to hosts.
     * @param variable
     */
    static void    hostVariableDeclare(String variable){

    }

    /**
     *  Set the value of a variable of a host.
     * @param host
     * @param variable
     * @param value
     */
    static void  hostVariableSet(java.lang.String host, java.lang.String variable, double value) {

    }

    /**
     *  Subtract a value from a variable of a host.
     * @param host
     * @param variable
     * @param value
     */
    static void     hostVariableSub(java.lang.String host, java.lang.String variable, double value) {

    }

    /**
     *     Add a value to a variable of a host.
     * @param host
     * @param variable
     * @param value
     */
    static void     hostVariableAdd(java.lang.String host, java.lang.String variable, double value) {

    }
}
