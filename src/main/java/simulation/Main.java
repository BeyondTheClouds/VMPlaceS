/*
 * Copyright 2006,2007,2010. The SimGrid Team. All rights reserved. 
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package. 
 */

package simulation;

// SG related import


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

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
   
	/* **** SIMULATOR LAUNCHER **** */
	public static void main(String[] args) throws NativeException {


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

       // Init. internal values
		Msg.init(args);
		
       // Automatically generate deployment file. 
       try {
       
    	   if(SimulatorProperties.getAlgo().equals("dvms")){
    		   Msg.info("DVMS selected (generating deployment file)"); 
    		   String[] cmd = {"/bin/sh", "-c", "python generate.py "+SimulatorProperties.getNbOfNodes()+" "+
    			   				SimulatorProperties.getNbOfCPUs()+ " "+
    			   					SimulatorProperties.getCPUCapacity()+ " "+ 
    			   						SimulatorProperties.getMemoryTotal()+" 23000 > config/generated_deploy.xml"};
    		   //"Usage: python generate.py nb_nodes nb_cpu total_cpu_cap ram port >
    		   Runtime.getRuntime().exec(cmd);
    	   }
    	   else {//(SimulatorProperties.getAlgo().equals("entropy"))
    		   Msg.info("Default selected (generating deployment file for centralized approach)"); 
    		 
    		   //"Usage: python generate.py nb_nodes
    		   String[] cmd = {"/bin/sh", "-c","python generate.py "+SimulatorProperties.getNbOfNodes()+" > config/generated_deploy.xml"};
    		   Runtime.getRuntime().exec(cmd);   
    		   
    		   // TODO: AL -> JP why are you calling these two methods here (and only here ?)
    		   //erasePreviousRawFile();
    		   //writeRawFile(getHeader());
       		}
       	} catch (IOException e) {
       		e.printStackTrace();
   		}
       

    // assume SLF4J is bound to logback in the current environment
       LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
       // print logback's internal status
       StatusPrinter.print(lc);
     
      if (args.length < 2) {
    	   Msg.info("Usage   : Basic platform_file deployment_file");
    	   Msg.info("example : Basic basic_platform.xml basic_deployment.xml");
    	   System.exit(1);
      } else {
		
    	   /* construct the platform and deploy the application */
    	   Msg.createEnvironment(args[0]);
    	   Msg.deployApplication(args[1]);

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
      }



       System.out.println("Configure simulation" + new Date().toString());
       SimulatorManager.instanciateVMs(SimulatorProperties.getNbOfNodes(), SimulatorProperties.getNbOfVMs());

	/*  execute the simulation. */
        System.out.println("Begin simulation" + new Date().toString());
        Msg.run();
        System.out.println("End simulation" + new Date().toString());
        Msg.info("End of run");
  	    Process.killAll(-1);
    }
	
	
	
//	// TODO This code is not relevant in general but Entropy devoted....
//	// It should not appear here.
//	private static void erasePreviousRawFile() {
//		File file = new File("raw_results_instances.txt");
//		if(file.exists()) {
//			file.delete();
//		}
//	}
//
//	private static void writeRawFile(String line) {
//
//		try {
//
//			File file = new File("raw_results_instances.txt");
//			if(!file.exists()) {
//				file.createNewFile();
//			}
//
//			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("raw_results_instances.txt", true)));
//			out.println(line);
//			out.close();
//
//		} catch (FileNotFoundException e) {
//			dvms.log.Logger.log(e);
//		} catch (IOException e) {
//			dvms.log.Logger.log(e);
//		}
//	}
//
//	public static String getHeader(){
//		return "computing_state" + "\t" +
//				"iteration_length_(ms)" + "\t" +
//				"time_computing_VMPP_(ms)" + "\t" +
//				"time_computing_VMRP_(ms)" + "\t" +
//				"time_applying_plan_(ms)" + "\t" +
//				"cost" + "\t" +
//				"nb_migrations" + "\t" +
//				"depth_reconf_graph" + "\t" +
//				"nb_of_nodes_used" + "\t" +
//				"nb_of_active_VMs" + "\t";
//	}
}
