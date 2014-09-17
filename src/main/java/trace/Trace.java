package trace;

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



    class TState{
        String Value;
        double timestamp;
    }

    /**
     * Hashmap: key : name of the host, value hashMap of Linkedlist of states
     */
    HashMap<String, HashMap<String,LinkedList<TState>>> hostStates;
  
    /**
     *     Declare a user state that will be associated to hosts.
     */
    static void hostStateDeclare(String name) {

    }

    /**
     *  Declare a new value for a user state associated to hosts.
     * @param state
     * @param value
     * @param color
     */
    static void    hostStateDeclareValue(String state, String value, String color){


    }

    /**
     *  Set the user state to the given value.
     * @param host
     * @param state
     * @param value
     */
    static void    hostSetState(String host, String state, String value){
        // TODO write in the json file: name of the host, name of the state + name of the value + timestamp

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
    static void     hostVariableSet(java.lang.String host, java.lang.String variable, double value)

    /**
     *  Subtract a value from a variable of a host.
     * @param host
     * @param variable
     * @param value
     */
    static void     hostVariableSub(java.lang.String host, java.lang.String variable, double value)

    /**
     *     Add a value to a variable of a host.
     * @param host
     * @param variable
     * @param value
     */
    static void     hostVariableAdd(java.lang.String host, java.lang.String variable, double value)
}
