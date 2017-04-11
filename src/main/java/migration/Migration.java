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
 * @author: anthony.simonet@inria.fr
 */

package migration;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.util.StatusPrinter;
import configuration.SimulatorProperties;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import org.slf4j.LoggerFactory;
import trace.Trace;

import java.util.Date;

public class Migration {
    /**
     * The Simulator launcher
     *
     * @param args
     */
    public static void main(String[] args) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        StatusPrinter.print(lc);

        // Init. internal values
        Msg.energyInit();
        Msg.init(args);

        /* construct the platform and deploy the application */
        Msg.createEnvironment(args[0]);
        Msg.deployApplication(args[1]);

        /* Create all VM instances and assign them on the PMs */
        /* The initial deployment is based on a round robin fashion */

        /* Prepare TRACE variables */
        Trace.hostVariableDeclare("ENERGY");

	    /*  execute the simulation. */
        Msg.info("Launcher: begin Msg.run()" + new Date().toString());
        Msg.info(String.format("Started %s with %d hosts and %d VMs",
                SimulatorProperties.getImplementation(),
                SimulatorProperties.getNbOfHostingNodes(),
                SimulatorProperties.getNbOfVMs()));

        Msg.run();

        Trace.close();
        Msg.info("End of run");

        Process.killAll(-1);
    }
}
