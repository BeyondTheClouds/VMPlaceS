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
  */

package simulation;

import java.io.IOException;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;

import java.util.Date;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import configuration.SimulatorProperties;



public class Main {

    /**
     * The Simulator launcher
     * @param args
     * @throws NativeException
     */
	public static void main(String[] args) throws NativeException {


        /*
        // Just a way to get the compilation time (useful to differentiate experiments)
        JarFile jf = null;
        try {
            jf = new JarFile("sg-injector.jar");
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        ZipEntry manifest = jf.getEntry("META-INF/MANIFEST.MF");
        long manifestTime = manifest.getTime();  //in standard millis
        System.out.println("Compilation time: "+new Date(manifestTime));
        */


        // Historical fix to get the internal logs of Entropy correctly
        // assume SLF4J is bound to logback in the current environment
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        // print logback's internal status
        StatusPrinter.print(lc);

        // Init. internal values
	    Msg.init(args);
		
        // Automatically generate deployment file that is mandatory for launching the simgrid simulation.
        try {
       
    	    if(SimulatorProperties.getAlgo().equals("distributed")){
    		    Msg.info("Distributed scheduling selected (generating deployment file)");
    		    String[] cmd = {"/bin/sh", "-c", "python generate.py "+SimulatorProperties.getNbOfNodes()+" "+
    			   				SimulatorProperties.getNbOfCPUs()+ " "+
    			   					SimulatorProperties.getCPUCapacity()+ " "+ 
    			   						SimulatorProperties.getMemoryTotal()+" 23000 > config/generated_deploy.xml"};
    		    //"Usage: python generate.py nb_nodes nb_cpu total_cpu_cap ram port >
    		    Runtime.getRuntime().exec(cmd);
    	    }
    	    else {//(SimulatorProperties.getAlgo().equals("centralized"))
    		    Msg.info("Default selected (generating deployment file for centralized approach)");
    		 
    		    //"Usage: python generate.py nb_nodes
    		    String[] cmd = {"/bin/sh", "-c","python generate.py "+SimulatorProperties.getNbOfNodes()+" > config/generated_deploy.xml"};
    		    Runtime.getRuntime().exec(cmd);
       		}
       	} catch (IOException e) {
       		e.printStackTrace();
   		}


        /* construct the platform and deploy the application */
        Msg.createEnvironment(args[0]);
        Msg.deployApplication(args[1]);

        /* Prepare TRACE variables */
        // A node can be underloaded
        Trace.hostStateDeclare ("PM");
        Trace.hostStateDeclareValue ("PM", "underloaded", "0 1 1");
        Trace.hostStateDeclareValue ("PM", "normal", "1 1 1");
        Trace.hostStateDeclareValue ("PM", "violation", "1 0 0");
        Trace.hostStateDeclareValue ("PM", "violation-det", "0 1 0");
        Trace.hostStateDeclareValue ("PM", "violation-out", "1 0 0");

        Trace.hostStateDeclare ("SERVICE");
        Trace.hostStateDeclareValue ("SERVICE","free", "1 1 1");
        Trace.hostStateDeclareValue ("SERVICE","booked", "0 0 1");
        Trace.hostStateDeclareValue ("SERVICE","compute", "1 0 1");
        Trace.hostStateDeclareValue ("SERVICE", "reconfigure", "1 1 0");

        Trace.hostVariableDeclare("LOAD");
        Trace.hostVariableDeclare("NB_MC");  // Nb of microcosms (only for DVMS)
        Trace.hostVariableDeclare("NB_MIG"); //Nb of migration

        /* Create all VM instances and assign them on the PMs */
        /* The initial deployment is based on a round robin fashion */
        System.out.println("Configure simulation" + new Date().toString());
        SimulatorManager.cleanLog();
        SimulatorManager.instanciateVMs(SimulatorProperties.getNbOfNodes(), SimulatorProperties.getNbOfVMs(),true);
        SimulatorManager.writeCurrentConfiguration();

	    /*  execute the simulation. */
        System.out.println("Begin simulation" + new Date().toString());
        Msg.run();
        System.out.println("End simulation" + new Date().toString());
        Msg.info("End of run");
  	    Process.killAll(-1);
    }
}
