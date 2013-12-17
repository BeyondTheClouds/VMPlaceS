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
import java.util.Deque;

import configuration.XSimpleConfiguration;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;

import java.util.Date;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

// Entropy related import

// DVMS related import
import configuration.ConfigurationManager;
import configuration.SimulatorProperties;
import configuration.XVM;



public class Main {
   
	/* **** GLOBAL VARIABLES **** */
	// Adrien - Please note that we do not need to synchronized 
	// the methods since the SG model implies that all threads are run sequentially.
	private static XSimpleConfiguration currentConfig = null;
	private static Deque<InjectorEvent> evtQueue = null ;
    private static Deque<LoadEvent> loadQueue = null ;
    private static Deque<FaultEvent> faultQueue = null ;
    private static XVM[] sgVMs = null;
    private static Host[] sgHosts = null;
    private static boolean endOfInjection = false;

	
	public static void setConfiguration(XSimpleConfiguration newConfiguration) {
		currentConfig=newConfiguration; 		
	}
	
	public static XSimpleConfiguration getCurrentConfig() {
	  return currentConfig;
	}
   
	public static Deque<InjectorEvent> getEvtQueue(){
		return evtQueue;
	}
    
	public static void setEndOfInjection(){
		endOfInjection=true; 
	}
	
	public static boolean isEndOfInjection(){
		return endOfInjection;
	}
	
	
	/* **** SIMULATOR LAUNCHER **** */
	public static void main(String[] args) throws NativeException {


        JarFile jf = null;
        try {
            jf = new JarFile("sg-injector.jar");
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        ZipEntry manifest = jf.getEntry("META-INF/MANIFEST.MF");
        long manifestTime = manifest.getTime();  //in standard millis
        System.out.println("Compilation time: "+new Date(manifestTime));

       /* Init. internal values */
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
    		   erasePreviousRawFile();
    		   writeRawFile(getHeader());
       		}
       	} catch (IOException e) {
		// TODO Auto-generated catch block
       		e.printStackTrace();
   		}
       
       // Generation of the Injection file
       currentConfig=ConfigurationManager.generateConfigurationFile();


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


       /* Since SG does not make any distinction between Host and Virtual Host (VMs and Hosts belong to the Host SG table)
          we should retrieve first the real host in a separeted table */
       sgHosts = new Host[SimulatorProperties.getNbOfNodes()+1];
       for(int i = 0 ; i <= SimulatorProperties.getNbOfNodes() ; i ++){
           try {
               sgHosts[i]=Host.getByName("node"+i);
           } catch (HostNotFoundException e) {
               e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
           }
       }

       System.out.println("Configure simulation" + new Date().toString());
       sgVMs=ConfigurationManager.instanciateVMs(currentConfig);

       // Nb PMs, Nb VMs, Max load, frequency of event occurrence

        loadQueue = ConfigurationManager.generateLoadQueue(sgVMs, SimulatorProperties.getDuration(), SimulatorProperties.getLoadPeriod());
        System.out.println("Size of load queue:"+loadQueue.size());
        //faultQueue = ConfigurationManager.generateFaultQueue(sgHosts, SimulatorProperties.getDuration(), SimulatorProperties.getCrashPeriod());
        //System.out.println("Size of fault queue:"+faultQueue.size());
        evtQueue = ConfigurationManager.mergeQueues(loadQueue,faultQueue);

        System.out.println("Size of event queue:"+evtQueue.size());
        System.out.println("Size of load queue:"+loadQueue.size());
        //System.out.println("Size of fault queue:"+faultQueue.size());

	/*  execute the simulation. */
        System.out.println("Begin simulation" + new Date().toString());
        Msg.run();
        System.out.println("End simulation" + new Date().toString());
        Msg.info("End of run");
  	  Process.killAll(-1); 
  	  Msg.clean(); 
       
    }
	
	
	
	// TODO This code is not relevant in general but Entropy devoted.... 
	// It should not appear here. 
	private static void erasePreviousRawFile() {
		File file = new File("raw_results_instances.txt");
		if(file.exists()) {
			file.delete();
		}
	}

	private static void writeRawFile(String line) {

		try {

			File file = new File("raw_results_instances.txt");
			if(!file.exists()) {
				file.createNewFile();
			}

			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("raw_results_instances.txt", true)));
			out.println(line);
			out.close();

		} catch (FileNotFoundException e) {
			dvms.log.Logger.log(e);
		} catch (IOException e) {
			dvms.log.Logger.log(e);
		}		
	}

	public static String getHeader(){
		return "computing_state" + "\t" +
				"iteration_length_(ms)" + "\t" +
				"time_computing_VMPP_(ms)" + "\t" +
				"time_computing_VMRP_(ms)" + "\t" +
				"time_applying_plan_(ms)" + "\t" +
				"cost" + "\t" +
				"nb_migrations" + "\t" +
				"depth_reconf_graph" + "\t" +
				"nb_of_nodes_used" + "\t" +
				"nb_of_active_VMs" + "\t";
	}
}
