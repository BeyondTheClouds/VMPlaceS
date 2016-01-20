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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import configuration.SimulatorProperties;
import org.simgrid.msg.Msg;
import org.simgrid.msg.NativeException;
import org.simgrid.msg.Process;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import scheduling.hierarchical.snooze.AUX;
import trace.Trace;


public class Main {

    /**
     * The Simulator launcher
     *
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

        // Save the begining time
        SimulatorManager.setBeginTimeOfSimulation(System.currentTimeMillis());

        // Init. internal values
        Msg.energyInit();
        Msg.init(args);

        // Automatically generate deployment file that is mandatory for launching the simgrid simulation.
        // TODO - implement a more generic way to generate the deployment file
        try {

            if (SimulatorProperties.getAlgo().equals("distributed")) {
                Msg.info("Distributed scheduling selected (generating deployment file)");
                String[] cmd = {"/bin/sh", "-c", "python generate.py " + SimulatorProperties.getAlgo() + " " +
                        SimulatorProperties.getNbOfHostingNodes() + " " +
                        SimulatorProperties.getNbOfCPUs() + " " +
                        SimulatorProperties.getCPUCapacity() + " " +
                        SimulatorProperties.getMemoryTotal() + " 23000 > config/generated_deploy.xml"};
                //"Usage: python generate.py nb_nodes nb_cpu total_cpu_cap ram port >
                Runtime.getRuntime().exec(cmd);
            } else if (SimulatorProperties.getAlgo().equals("hierarchical")) {
                Msg.info("Hierarchical scheduling selected (generating deployment file for hierarchical approach)");

                //"Usage: python generate.py nb_nodes
                String[] cmd = {"/bin/sh", "-c", "python generate.py " + SimulatorProperties.getAlgo() + " " + SimulatorProperties.getNbOfHostingNodes() + " " + SimulatorProperties.getNbOfServiceNodes() + " > config/generated_deploy.xml"};
                Runtime.getRuntime().exec(cmd);
            } else { //(SimulatorProperties.getAlgo().equals("centralized"))
                Msg.info("Default selected (generating deployment file for centralized approach)");

                //"Usage: python generate.py nb_nodes
                String[] cmd = {"/bin/sh", "-c", "python generate.py " + SimulatorProperties.getAlgo() + " " + SimulatorProperties.getNbOfHostingNodes() + " > config/generated_deploy.xml"};
                Runtime.getRuntime().exec(cmd);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        /* construct the platform and deploy the application */
        Msg.createEnvironment(args[0]);
        Msg.deployApplication(args[1]);


        /* Create all VM instances and assign them on the PMs */
        /* The initial deployment is based on a round robin fashion */
        System.out.println("Configure simulation" + new Date().toString());
        SimulatorManager.cleanLog();
        SimulatorManager.configureHostsAndVMs(SimulatorProperties.getNbOfHostingNodes(), SimulatorProperties.getNbOfServiceNodes(), SimulatorProperties.getNbOfVMs(), true);
        SimulatorManager.writeCurrentConfiguration();

        String algorithmName = SimulatorProperties.getAlgo();
        String algorithmDetails = "{}";
        if (algorithmName.equals("hierarchical")) {
            int lcsRatio = SimulatorProperties.getNbOfHostingNodes() / (SimulatorProperties.getNbOfServiceNodes() -1 );
            algorithmDetails = String.format("{\"assignmentAlgorithm\": \"%s\", \"lcsRatio\": %d}", AUX.assignmentAlg, lcsRatio);
        }
        Trace.simulationDeclare(algorithmName, SimulatorProperties.getNbOfHostingNodes(), SimulatorProperties.getNbOfServiceNodes(), SimulatorProperties.getNbOfVMs(), algorithmDetails);

        /* Prepare TRACE variables */
        System.out.println("Prepare TRACE module" + new Date().toString());
        // A node can be underloaded
        Trace.hostStateDeclare("PM");
        Trace.hostStateDeclareValue("PM", "underloaded", "0 1 1");
        Trace.hostStateDeclareValue("PM", "normal", "1 1 1");
        Trace.hostStateDeclareValue("PM", "violation", "1 0 0");
        Trace.hostStateDeclareValue("PM", "violation-det", "0 1 0");
        Trace.hostStateDeclareValue("PM", "violation-out", "1 0 0");

        Trace.hostStateDeclare("SERVICE");
        Trace.hostStateDeclareValue("SERVICE", "free", "1 1 1");
        Trace.hostStateDeclareValue("SERVICE", "booked", "0 0 1");
        Trace.hostStateDeclareValue("SERVICE", "compute", "1 0 1");
        Trace.hostStateDeclareValue("SERVICE", "reconfigure", "1 1 0");
        Trace.hostStateDeclareValue("SERVICE", "migrate", "1 0 0");

        Trace.hostVariableDeclare("LOAD");
        Trace.hostVariableDeclare("NB_MC");  // Nb of microcosms (only for DVMS)
        Trace.hostVariableDeclare("NB_MIG"); //Nb of migration

        Trace.hostVariableDeclare("ENERGY");

	    /*  execute the simulation. */
        System.out.println("Launcher: begin Msg.run()" + new Date().toString());
        notify(String.format("Started %s with %d hosts and %d VMs", SimulatorProperties.getImplementation(), SimulatorProperties.getNbOfHostingNodes(), SimulatorProperties.getNbOfVMs()));

        Msg.run();

        System.out.println("Launcher: end of Msg.run()" + new Date().toString());
        Trace.close();
        Msg.info("End of run");

        notify(String.format("End of simulation %s", SimulatorProperties.getImplementation()));

        Process.killAll(-1);
    }

    private static void notify(String message) {
        /*
        try {
            Runtime.getRuntime().exec(new String[]{
                    "terminal-notifier",
                    "-title", "VMPlaceS",
                    "-group", "SIMULATOR",
                    "-message", message
            });
        } catch (Exception e) {
            System.err.println(e);
        }
        */
    }
}
