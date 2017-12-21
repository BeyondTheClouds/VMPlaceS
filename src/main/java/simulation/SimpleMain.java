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
import choco.cp.solver.constraints.global.geost.geometricPrim.Obj;
import com.hubspot.jinjava.Jinjava;
import configuration.SimulatorProperties;
import configuration.XHost;
import org.docopt.Docopt;
import org.json.JSONObject;
import org.simgrid.msg.Msg;
import org.simgrid.msg.Process;
import org.slf4j.LoggerFactory;
import scheduling.hierarchical.snooze.AUX;
import trace.Trace;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class SimpleMain {

    private static final String doc =
        "VMPlaceS simulation launcher (simulation.Main).\n"
        + "\n"
        + "Usage:\n"
        + "  simplemain [--platform=FILE] [--deployment=FILE] --algo=<value> [--impl=<value>] [--duration=<value>] [--loadperiod=<value>] [--nb_hosts=<value>] [--nb_vms=<value>] [--netbw=<value>] [--vm_maxcpuconsumption=<value>] [--vm_nbcpuconsumptionslots=<value>] [--load_mean=<value>] [--load_std=<value>] [--dry-run]\n"
        + "  simplemain --list-algos\n"
        + "\n"
        + "Options:\n"
        + "  -h --help          Show this screen.\n"
        + "\n";

    private static JSONObject load_json_file(String filename) {
        String jsonContent;
        try {
            jsonContent = new Scanner(new File(filename)).useDelimiter("\\Z").next();
        } catch (FileNotFoundException e) {
            jsonContent = "{}";
        }
        return new JSONObject(jsonContent);
    }

    private static String get_template_content(String template_path) {
        String template_content;
        try {
            template_content = new Scanner(new File(template_path)).useDelimiter("\\Z").next();
        } catch (FileNotFoundException e) {
            template_content = "";
        }
        return template_content;
    }

    private static String get_deployment_template(String algorithm_name) {
        JSONObject algorithms_description = load_json_file("config/algorithms.json");
        String template_path = algorithms_description.getJSONObject("algorithms").getJSONObject(algorithm_name).getString("deploy_xml_template");

        return get_template_content(template_path);
    }

    private static Map<String, Object> build_context(Map<String, Object> opts) {
        JSONObject algorithms_description = load_json_file("config/algorithms.json");
        Map<String, Object> context = new HashMap<String, Object>();

        Object selected_algo = opts.get("--algo") != null ? opts.get("--algo") : SimulatorProperties.getAlgo();
        context.put("algo", selected_algo);

        String defaultImpl = algorithms_description.getJSONObject("algorithms").
                                                    getJSONObject(selected_algo.toString()).
                                                    getJSONArray("scheduling_algorithm").
                                                    get(0)
                                                    .toString();

        Object selected_impl = opts.get("--impl") != null? opts.get("--impl") : defaultImpl;
        context.put("impl", selected_impl);

        Object nb_hosts = opts.get("--nb_hosts") != null ? opts.get("--nb_hosts") : SimulatorProperties.getNbOfHostingNodes();
        context.put("nb_hosts", nb_hosts);

        Object nb_vms = opts.get("--nb_vms") != null ? opts.get("--nb_vms") : SimulatorProperties.getNbOfVMs();
        context.put("nb_vms", nb_vms);

        Object nb_service_nodes = opts.get("--nb_service_nodes") != null ? opts.get("--nb_service_nodes") : SimulatorProperties.getNbOfServiceNodes();
        context.put("nb_service_nodes", nb_service_nodes);

        Object nb_cpus = opts.get("--nb_cpus") != null ? opts.get("--nb_cpus") : SimulatorProperties.getNbOfCPUs();
        context.put("nb_cpus", nb_cpus);

        Object cpu_capacity = opts.get("--cpu_capacity") != null ? opts.get("--cpu_capacity") : SimulatorProperties.getCPUCapacity();
        context.put("cpu_capacity", cpu_capacity);

        Object ram_capacity = opts.get("--ram_capacity") != null ? opts.get("--ram_capacity") : SimulatorProperties.getMemoryTotal();
        context.put("ram_capacity", ram_capacity);

        Object netbw = opts.get("--netbw") != null ? opts.get("--netbw") : SimulatorProperties.getNetCapacity();
        context.put("netbw", netbw);

        Object vm_maxcpuconsumption = opts.get("--vm_maxcpuconsumption") != null ? opts.get("--vm_maxcpuconsumption") : SimulatorProperties.getVMMAXCPUConsumption();
        context.put("vm_maxcpuconsumption", vm_maxcpuconsumption);

        Object vm_nbcpuconsumptionslots = opts.get("--vm_nbcpuconsumptionslots") != null ? opts.get("--vm_nbcpuconsumptionslots") : SimulatorProperties.getNbOfCPUConsumptionSlots();
        context.put("vm_nbcpuconsumptionslots", vm_nbcpuconsumptionslots);

        Object load_mean = opts.get("--load_mean") != null ? opts.get("--load_mean") : SimulatorProperties.getMeanLoad();
        context.put("load_mean", load_mean);

        Object load_std = opts.get("--load_std") != null ? opts.get("--load_std") : SimulatorProperties.getStandardDeviationLoad();
        context.put("load_std", load_std);

        Object duration = opts.get("--duration") != null ? opts.get("--duration") : SimulatorProperties.getDuration();
        context.put("duration", duration);

        Object loadperiod = opts.get("--loadperiod") != null ? opts.get("--loadperiod") : SimulatorProperties.getLoadPeriod();
        context.put("loadperiod", loadperiod);

        Object port = opts.get("--port") != null ? opts.get("--port") : 23000;
        context.put("port", port);

        List<Integer> range = IntStream.range(0, Integer.parseInt(nb_hosts.toString())).boxed().collect(Collectors.toList());
        context.put("node_range", range);

        return context;
    }

    private static boolean generate_deployment_file(String output_path, Map<String, Object> context) {
        Jinjava jinjava = new Jinjava ();
        String templateContent = get_deployment_template(SimulatorProperties.getAlgo());

        String renderedTemplate = jinjava.render(templateContent, context);

        try(  PrintWriter out = new PrintWriter(output_path)  ){
            out.println(renderedTemplate);
        } catch (FileNotFoundException e) {
            return false;
        }

        return true;
    }

    private static boolean generate_platform_file(String output_path, Map<String, Object> context) {
        Jinjava jinjava = new Jinjava ();
        String templateContent = get_template_content("./templates/cluster_platform.xml");

        String renderedTemplate = jinjava.render(templateContent, context);

        try(  PrintWriter out = new PrintWriter(output_path)  ){
            out.println(renderedTemplate);
        } catch (FileNotFoundException e) {
            return false;
        }

        return true;
    }

    private static boolean generate_simulation_config_file(String output_path, Map<String, Object> context) {
        Jinjava jinjava = new Jinjava ();
        String templateContent = get_template_content("./templates/simulator.properties");

        String renderedTemplate = jinjava.render(templateContent, context);

        try(  PrintWriter out = new PrintWriter(output_path)  ){
            out.println(renderedTemplate);
        } catch (FileNotFoundException e) {
            return false;
        }

        return true;
    }

    /**
     * The Simulator launcher
     *
     * @param args
     */
    public static void main(String[] args) throws Exception {

        Map<String, Object> opts =
                new Docopt(doc).withVersion("VMPlaceS 1.0").parse(args);
        System.out.println(opts);

        Map<String, Object> context = build_context(opts);

        // Historical fix to get the internal logs of Entropy correctly
        // assume SLF4J is bound to logback in the current environment
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        // print logback's internal status
        StatusPrinter.print(lc);

        // Save the begining time
        SimulatorManager.setBeginTimeOfSimulation(System.currentTimeMillis());

        // Create temporary folder
        File file = new File("./tmp/config");
        if (!file.exists()) {
            file.mkdirs();
        }

        // Generate configuration file
        String generated_config_path = "./tmp/config/simulator.properties";
        boolean configuration_is_generated = generate_simulation_config_file(generated_config_path, context);
        if (! configuration_is_generated) {
            throw new Exception(String.format("Could not generate '%s'", generated_config_path));
        }
        SimulatorProperties.setInstance(new SimulatorProperties(generated_config_path));

        // Generate platform file
        String generated_platform_path = "./tmp/config/cluster_platform.xml";
        boolean platform_is_generated = generate_platform_file(generated_platform_path, context);
        if (! platform_is_generated) {
            throw new Exception(String.format("Could not generate '%s'", generated_platform_path));
        }

        // Generate a deployment file
        String generated_deploy_path = "./tmp/config/generated_deploy.xml";
        boolean deployment_is_generated = generate_deployment_file(generated_deploy_path, context);
        if (! deployment_is_generated) {
            throw new Exception(String.format("Could not generate '%s'", generated_deploy_path));
        }

        String platformConfigurationLocation = "./tmp/config/cluster_platform.xml";
        String deploymentConfigurationLocation = "./tmp/config/generated_deploy.xml";

        if (opts.containsKey("--dry-run") && opts.get("--dry-run").toString().equals("true")) {
            return;
        }

        // Init. internal values
        Msg.energyInit();
        String[] classpathOptions = {
                platformConfigurationLocation,
                deploymentConfigurationLocation,
                "--cfg=cpu/optim:Full",
                "--cfg=tracing:1",
                "--cfg=tracing/filename:simu.trace",
                "--cfg=tracing/platform:1"
        };
        Msg.init(classpathOptions);

        Msg.createEnvironment(platformConfigurationLocation);
        Msg.deployApplication(deploymentConfigurationLocation);

        /* Create all VM instances and assign them on the PMs */
        /* The initial deployment is based on a round robin fashion */
        System.out.println("Configure simulation" + new Date().toString());
        SimulatorManager.cleanLog();
        // True means round robin placement.
        SimulatorManager.configureHostsAndVMs(SimulatorProperties.getNbOfHostingNodes(), SimulatorProperties.getNbOfServiceNodes(), SimulatorProperties.getNbOfVMs(), true);
        SimulatorManager.writeCurrentConfiguration();

        String algorithmName = SimulatorProperties.getAlgo();
        String algorithmDetails = "{}";
        if (algorithmName.equals("hierarchical")) {
            int lcsRatio = SimulatorProperties.getNbOfHostingNodes() / (SimulatorProperties.getNbOfServiceNodes() -1 );
            algorithmDetails = String.format("{\"assignmentAlgorithm\": \"%s\", \"lcsRatio\": %d}", AUX.assignmentAlg, lcsRatio);
        }

        if(algorithmName.equals("centralized"))
            algorithmName = SimulatorProperties.getImplementation().substring(SimulatorProperties.getImplementation().lastIndexOf('.') + 1);

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
        Trace.hostVariableDeclare("NB_VM"); //To follow number of VMs.
        Trace.hostVariableDeclare("NB_VM_TRUE"); //To follow the true number of VMs.

        Trace.hostVariableDeclare("ENERGY");
        Trace.hostVariableDeclare("NB_OFF"); //Nb of hosts turned off
        Trace.hostVariableDeclare("NB_ON"); //Nb of hosts turned on

        for(XHost host: SimulatorManager.getSGHosts()) {
            Trace.hostVariableSet(host.getName(), "NB_ON", 1);
            Trace.hostVariableSet(host.getName(), "NB_OFF", 0);
        }

        // Turn off the hosts that we don't need
        int nOff = 0;
        if(SimulatorProperties.getHostsTurnoff()) {
            for (XHost h : SimulatorManager.getSGHostingHosts())
                if (h.getRunnings().size() <= 0) {
                    SimulatorManager.turnOff(h);
                    nOff++;
                }
            Msg.info(String.format("Turned off unused %d nodes before starting", nOff));
        }

	    /*  execute the simulation. */
        System.out.println("Launcher: begin Msg.run()" + new Date().toString());
        notify(String.format("Started %s with %d hosts and %d VMs", SimulatorProperties.getImplementation(), SimulatorProperties.getNbOfHostingNodes(), SimulatorProperties.getNbOfVMs()));

        Msg.run();

        System.out.println("Launcher: end of Msg.run()" + new Date().toString());
        Trace.close();
        Msg.info("End of run");

        notify(String.format("End of simulation %s", SimulatorProperties.getImplementation()));

        Process.killAll(-1);
        Msg.info(String.format("There are still %d processes running", Process.getCount()));
    }

    private static void notify(String message) {
        Msg.info(message);
    }
}
