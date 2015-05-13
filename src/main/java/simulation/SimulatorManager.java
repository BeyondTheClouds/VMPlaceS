/**
 * Copyright 2012-2013-2014. The SimGrid Team. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 *
 * This class aims at controlling the interactions between the different components of the injector simulator.
 * It is mainly composed of static methods. Although it is rather ugly, this is the direct way to make a kind of
 * singleton ;)
 *
 * @author adrien.lebre@inria.fr
 * @contributor jsimao@cc.isel.ipl.pt
 */

package simulation;

import configuration.SimulatorProperties;
import configuration.VMClasses;
import configuration.XHost;
import configuration.XVM;
import injector.LoadEvent;
import org.simgrid.msg.Host;
import org.simgrid.msg.HostNotFoundException;
import org.simgrid.msg.Msg;
import scheduling.snooze.LocalController;
import scheduling.snooze.Logger;
import trace.Trace;

import java.io.*;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: alebre
 * Date: 14/01/14
 * Time: 10:49
 * To change this template use File | Settings | File Templates.
 */
public class SimulatorManager {

    /**
     * Stupid variable to monitor the duration of the simulation
     */
    private static double beginTimeOfSimulation = -1;
    /**
     * Stupid variable to monitor the duration of the simulation
     */
    private static double endTimeOfSimulation = -1;

    /**
     * The list of XVMs that are considered as off (i.e. the hosting machine is off)
     * @see configuration.XVM
     */
    private static HashMap<String,XVM> sgVMsOff = null;
    /**
     * The list of XVMs that run
     * @see configuration.XVM
     */
    private static HashMap<String,XVM> sgVMsOn = null;
    /**
     * The list of XHosts that are off
     * @see configuration.XHost
     */
    private static HashMap<String,XHost> sgHostsOff= null;
    /**
     * The list of Xhosts  that are running
     */
    private static HashMap<String,XHost> sgHostsOn= null;

    /**
     * The list of all XHosts
     * @see configuration.XHost
     */
    private static HashMap<String,XHost> sgHostingHosts= null;
    /**
     * The list of Xhosts  that are running
     */
    private static HashMap<String, XHost> sgServiceHosts= null;
    /**
     * Just a stupid sorted table to have a reference toward each host
     * Used by the injector when generating the different event queues.
     */
    private static XHost[] xhosts = null;
    /**
     * Average CPU demand of the infrastructure (just a hack to avoid to compute the CPUDemand each time (computing the CPU demand is O(n)
     */
    // TODO Adrien the currentCPUDemand is currently not correctly assigned (this should be done in the update function)
    private static double currentCPUDemand = 0;

    /**
     * When the injection is complete, we turn the endOfInjection boolean to true and kill the running daemon inside each VM
     */
	public static void setEndOfInjection(){
		endTimeOfSimulation = System.currentTimeMillis();

        Msg.info(sgHostsOn.size()+"/"+ getSGHosts().size()+"are up");
        Msg.info(sgVMsOn.size()+"/"+getSGVMs().size()+" are up");

        for (XHost host : SimulatorManager.getSGHosts()) {
            Msg.info(host.getName() + " has been turned off "+host.getTurnOffNb()+" times and violated "+host.getNbOfViolations());
        }

        // Kill all VMs daemons in order to finalize the simulation correctly
        for (XVM vm : SimulatorManager.getSGVMs()) {
            Msg.info(vm.getName() + " load changes: "+vm.getNbOfLoadChanges());
            vm.getDaemon().kill();
        }
        Msg.info("Duration of the simulation in ms: "+(endTimeOfSimulation - beginTimeOfSimulation));
    }

    /**
     * @return whether the injection is completed or not
     */
	public static boolean isEndOfInjection(){
		return (endTimeOfSimulation != -1);
	}


    /**
     * @return the collection of XVMs: all VMs, the running and the ones that are considered as dead
     * (i.e. hosted on hosts that have been turned off)
     */
    public static Collection<XVM> getSGVMs(){
        LinkedList<XVM> tmp = new LinkedList<>(sgVMsOn.values());
        tmp.addAll(sgVMsOff.values());
        return tmp;
    }

    /**
     * @return the collection of running XVMs
     */
    public static Collection<XVM> getSGVMsOn(){
        return sgVMsOn.values();
    }

    /**
     * @return the collection of the XVMs considered as dead
     */
    public static Collection<XVM> getSGVMsOff(){
        return sgVMsOff.values();
    }


    /**
     * @return the collection of XHosts (i.e. the hosts that composed the infrastructure).
     * Please note that the returned collection is not sorted. If you need a sorted structure, you should call getSGHostsToArray() that returns an simple array
     */
    public static Collection<XHost> getSGHosts(){
        LinkedList<XHost> tmp = new LinkedList<XHost>(sgHostingHosts.values());
        tmp.addAll(sgServiceHosts.values());

        return tmp;
    }

    /**
     * @return the collection of XHosts (i.e. the hosts that composed the infrastructure).
     * Please note that the returned collection is not sorted. If you need a sorted structure, you should call getSGHosts() that returns an simple array
     */
    public static XHost[] getSGHostsToArray(){
        return xhosts;
    }


    /**
     * @return the collection of XHosts that have been declared as hosting nodes (i.e. that can host VMs)
     * Please note that all HostingHosts are returned (without making any distinctions between on and off hosts)
     */
    public static Collection<XHost> getSGHostingHosts(){
        return sgHostingHosts.values();
    }

    /**
     * @return the collection of XHosts that have been declared as hosting nodes (i.e. that can host VMs) and that are turned on.
     */
    public static Collection<XHost> getSGTurnOnHostingHosts() {
        LinkedList<XHost> tmp = new LinkedList<XHost>();
        for (XHost h: sgHostingHosts.values()){
            if (!h.isOff())
                tmp.add(h);
        }
        return tmp;
    }

    /**
     * @return the collection of XHosts that have been declared as services nodes (i.e. that cannot host VMs)
     */
    public static Collection<XHost> getSGServiceHosts(){
        return sgServiceHosts.values();
    }


    /**
     * @return the name of the service node (generally node0, if you do not change the first part of the main regarding the generation
     * of the deployment file).
     * If you change it, please note that you should then update the getInjectorNodeName code.
     */
    public static String getInjectorNodeName() {
        return "node"+(SimulatorProperties.getNbOfHostingNodes()+SimulatorProperties.getNbOfServiceNodes());
    }

    /**
     * For each MSG host (but the service node), the function creates an associated XHost.
     * As a reminder, the XHost class extends the Host one by aggregation.
     * At the end, all created hosts have been inserted into the sgHosts collection (see getSGHostingHosts function)
     * @param nbOfHostingHosts the number of hosts that will be used to host VMs
     * @param nbOfServiceHosts the number of hosts that will be used to host services
     */
    public static void initHosts(int nbOfHostingHosts, int nbOfServiceHosts){
        // Since SG does not make any distinction between Host and Virtual Host (VMs and Hosts belong to the Host SG table)
        // we should retrieve first the real host in a separated table
        // Please remind that node0 does not host VMs (it is a service node) and hence, it is managed separately (getInjectorNodeName())
        sgHostsOn = new HashMap<String,XHost>();
        sgHostsOff = new HashMap<String,XHost>();
        sgHostingHosts = new HashMap<String,XHost>();
        sgServiceHosts = new HashMap<String,XHost>();
        xhosts = new XHost[nbOfHostingHosts+nbOfServiceHosts];

        XHost xtmp;

        // Hosting hosts
        for(int i = 0 ; i < nbOfHostingHosts ; i ++){
            try {
                Host tmp = Host.getByName("node" + i);
                // The SimulatorProperties.getCPUCapacity returns the value indicated by nodes.cpucapacity in the simulator.properties file
                xtmp = new XHost (tmp, SimulatorProperties.getMemoryTotal(), SimulatorProperties.getNbOfCPUs(), SimulatorProperties.getCPUCapacity(), SimulatorProperties.getNetCapacity(), "127.0.0.1");
                xtmp.turnOn();
               sgHostsOn.put("node"+i, xtmp);
                sgHostingHosts.put("node" + i, xtmp);
                xhosts[i]=xtmp;
            } catch (HostNotFoundException e) {
                e.printStackTrace();
            }
        }

        //Service hosts
        for(int i = nbOfHostingHosts ; i < nbOfHostingHosts+nbOfServiceHosts ; i ++){
            try {
                Host tmp = Host.getByName("node" + i);
                // The SimulatorProperties.getCPUCapacity returns the value indicated by nodes.cpucapacity in the simulator.properties file
                xtmp = new XHost (tmp, SimulatorProperties.getMemoryTotal(), SimulatorProperties.getNbOfCPUs(), SimulatorProperties.getCPUCapacity(), SimulatorProperties.getNetCapacity(), "127.0.0.1");
                xtmp.turnOn();
                sgHostsOn.put("node" + i, xtmp);
                sgServiceHosts.put("node" + i, xtmp);
                xhosts[i]=xtmp;
            } catch (HostNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Create and assign the VMs on the different hosts.
     * For the moment, the initial placement follows a simple round robin strategy
     * The algorithm fill the first host with the n first VMs until it reaches either the memory limit, then it switches to the second host and so on.
     * Note that if the ''balance'' mode is enabled then the initial placement will put the same number of VMs on each node.
     * The function can crash if there are two many VMs for the physical resources.
     * At the end the collection SimulatorManager.getSGVMs() is filled.
     * @param nbOfHostingHosts the number of the hosts composing the infrastructure
     * @param nbOfServiceHosts the number of the hosts composing the infrastructure
     * @param nbOfVMs the number of the VMs to instanciate
     */
    public static void configureHostsAndVMs(int nbOfHostingHosts, int nbOfServiceHosts, int nbOfVMs, boolean balance) {

        int nodeIndex = 0;
        int[] nodeMemCons = new int[nbOfHostingHosts];
        int vmIndex= 0;
        int nbVMOnNode;
        Random r = new Random(SimulatorProperties.getSeed());
        int nbOfVMClasses = VMClasses.CLASSES.size();
        VMClasses.VMClass vmClass;

        initHosts(nbOfHostingHosts, nbOfServiceHosts);
        sgVMsOn = new HashMap<String,XVM>();
        sgVMsOff = new HashMap<String,XVM>();

        XVM sgVMTmp;

        Iterator<XHost> sgHostsIterator = SimulatorManager.getSGHostingHosts().iterator();

        XHost sgHostTmp = sgHostsIterator.next();
        nodeMemCons[nodeIndex]=0;
        nbVMOnNode =0;

        //Add VMs to each node, preventing memory over provisioning
        while(vmIndex < nbOfVMs){

            // Select the class for the VM
            vmClass = VMClasses.CLASSES.get(r.nextInt(nbOfVMClasses));

            //Check whether we can put this VM on the current node if not get the next one
            //The first condition controls the memory over provisioning issue while the second one enables to switch to
            // the next node if the ''balance'' mode is enabled.
            // If there is no more nodes, then we got an exception and the simulator.properties should be modified.

            double vmsPerNodeRatio = ((double) nbOfVMs)/nbOfHostingHosts;

            try {
                while ((nodeMemCons[nodeIndex] + vmClass.getMemSize() > sgHostTmp.getMemSize()) || (balance && nbVMOnNode >= vmsPerNodeRatio)) {
                    sgHostTmp = sgHostsIterator.next();
                    nodeMemCons[++nodeIndex] = 0;
                    nbVMOnNode = 0;
                }
            } catch(NoSuchElementException ex){
                System.err.println("There is not enough memory on the physical hosts to start all VMs");
                System.err.println("(Please fix simulator.properties parameters and you should dive in the SimulatorManager.configureHostsAndVMs() function");
                System.exit(1);
            }

            // Creation of the VM
            sgVMTmp = new XVM(sgHostTmp, "vm-" + vmIndex,
                        vmClass.getNbOfCPUs(), vmClass.getMemSize(), vmClass.getNetBW(), null, -1, vmClass.getMigNetBW(), vmClass.getMemIntensity());
            sgVMsOn.put("vm-"+vmIndex, sgVMTmp);
            vmIndex++;

            Msg.info(String.format("vm: %s, %d, %d, %s",
                        sgVMTmp.getName(),
                        vmClass.getMemSize(),
                        vmClass.getNbOfCPUs(),
                        "NO IPs defined"
            ));
            Msg.info("vm " + sgVMTmp.getName() + " is " + vmClass.getName() + ", dp is " + vmClass.getMemIntensity());

            // Assign the new VM to the current host.
            sgHostTmp.start(sgVMTmp);     // When the VM starts, its getCPUDemand equals 0
            nbVMOnNode ++;
            nodeMemCons[nodeIndex] += sgVMTmp.getMemSize();
        }
    }

    /**
     * write the current configuration in the ''logs/simulatorManager/'' directory
     */
    public static void writeCurrentConfiguration(){
        try {
            File file = new File("logs/simulatorManager/conf-"+ System.currentTimeMillis() + ".txt");
            file.getParentFile().mkdirs();
            BufferedWriter bw = new BufferedWriter(new FileWriter(file));
            for (XHost h: SimulatorManager.getSGHostingHosts()){
                bw.write(h.getName()+":");
                for (XVM vm: h.getRunnings()){
                    bw.write(" "+vm.getName());
                }
                bw.write("\n");
                bw.flush();
            }

            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Remove all logs from the previous run
     */
    public static void cleanLog(){
        try {
            Runtime.getRuntime().exec("rm -rf ./logs/simulatorManager");
            Runtime.getRuntime().exec("rm -rf ./logs/entropy");
            Runtime.getRuntime().exec("rm -rf ./logs/entropy.log");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return whether the current placement is viable or not (i.e. if every VM gets its expectations).
     * Please note that we are considering only the hosts that are running.
     * Complexity O(n)
     */
    public static boolean isViable() {
        for (XHost h: sgHostsOn.values()){
                  if(!h.isViable())
                return false;
        }
        return true;
    }

    /**
     * @return the average expected load at a particular moment (i.e. average load of each node)
     * Please note that we are considering only the hosts hosting VMs and that are up.
     */
    public static double computeCPUDemand() {

        double globalCpuDemand = 0.0;
        int globalCpuCapacity = 0;

        for(XHost h: sgHostingHosts.values()){
            if(h.isOn()) {
                globalCpuDemand += h.getCPUDemand();
                globalCpuCapacity += h.getCPUCapacity();
            }
        }
        return 100 * globalCpuDemand / globalCpuCapacity;
    }

    public static double getCPUDemand(){
        // TODO Adrien, maintain the current CPU Demand in order to avoid O(n)
        //return currentCPUDemand;
        return computeCPUDemand();
    }
    /**
     * @return the number of hosts that are active (i.e. that host at least one VM)
     * Complexity O(n)
     */
    public static int getNbOfUsedHosts() {
        int i=0;
        for (XHost h: sgHostsOn.values()){
            if(h.getNbVMs()>0)
                i++;
        }
        return i;
    }

    /**
     * Return the XHost entitled ''name'', if not return null (please note that the search is performed by considering
     * all hosts (i.e. On/Off and Hosting/Service ones)
     * @param name the name of the host requested
     * @return the corresponding XHost instance (null if there is no corresponding host in the sgHosts collection)
     */
    public static XHost getXHostByName(String name) {
        XHost tmp = sgHostingHosts.get(name);
        if (tmp == null)
            tmp = sgServiceHosts.get(name);
        return tmp;
    }

    /**
     * Return the XVM entitled ''name'', if not return null please note that the search is performed by considering
     * all VMs (i.e. event the off ones)
     * @param name the name of the vm requested
     * @return the corresponding XVM instance (null if there is no corresponding vm in the sgVMs collection)
     */
    public static XVM getXVMByName(String name) {
        XVM tmp = sgVMsOn.get(name);
        if (tmp == null)
            tmp = sgVMsOff.get(name);
        return tmp;
    }

    /**
     * Change the load of a VM.
     * Please note that we do not detect violations on off hosts (i.e. if the nodes that hosts the VM is off, we change
     * the load of the vm for consistency reasons but we do not consider the violation that may result from this change).
     * @param sgVM the VM that should be updated
     * @param load the new expected load
     */
    public static void updateVM(XVM sgVM, int load) {
        XHost tmpHost = sgVM.getLocation();
        boolean previouslyViable = tmpHost.isViable();

        // A simple hack to avoid computing on-the-fly the CPUDemand of each host
        double vmPreviousLoad = sgVM.getCPUDemand();
        double hostPreviousLoad = tmpHost.getCPUDemand();
       // Msg.info("Previous Load was" + hostPreviousLoad);

        tmpHost.setCPUDemand(hostPreviousLoad-vmPreviousLoad+load);
      //  Msg.info("New Load is "+ tmpHost.getCPUDemand());

        sgVM.setLoad(load);

        // If the node is off, we change the VM load but we do not consider it for possible violation and do not update
        // neither the global load of the node nor the global load of the cluster.
        // Violations are detected only on running node
        if (!tmpHost.isOff()){

        //    Msg.info("Current getCPUDemand "+SimulatorManager.getCPUDemand()+"\n");


            if(previouslyViable && (!tmpHost.isViable())) {
                  Msg.info("STARTING VIOLATION ON "+tmpHost.getName()+"\n");
                    tmpHost.incViolation();
                    Trace.hostSetState(tmpHost.getName(), "PM", "violation");

            } else if ((!previouslyViable) && (tmpHost.isViable())) {
                        Msg.info("ENDING VIOLATION ON "+tmpHost.getName()+"\n");
                        Trace.hostSetState (tmpHost.getName(), "PM", "normal");
            }
            // else Do nothing the state does not change.

            // Update getCPUDemand of the host
            Trace.hostVariableSet(tmpHost.getName(), "LOAD", tmpHost.getCPUDemand());

            // TODO this is costly O(HOST_NB) - SHOULD BE FIXED
            //Update global getCPUDemand
            Trace.hostVariableSet(SimulatorManager.getInjectorNodeName(),  "LOAD", SimulatorManager.getCPUDemand());

        }
    }

    public static boolean willItBeViableWith(XVM sgVM, int load){
        XHost tmpHost = sgVM.getLocation();
        double hostPreviousLoad = tmpHost.getCPUDemand();
        double vmPreviousLoad = sgVM.getCPUDemand();
        return ((hostPreviousLoad-vmPreviousLoad+load) <= tmpHost.getCPUCapacity());
    }

    /**
     * Turn on the XHost host
     * @param host the host to turn on
     */
    public static void turnOn(XHost host) {
        String name = host.getName();
        if(host.isOff()) {
            Msg.info("Turn on node "+name);
            host.turnOn();
            sgHostsOff.remove(name);
            sgHostsOn.put(name, host);

            // If your turn on an hosting node, then update the LOAD
            if(sgHostingHosts.containsKey(name)) {

                for (XVM vm: host.getRunnings()){
                    Msg.info("TURNING NODE "+name+"ON - ADD VM "+vm.getName());
                    sgVMsOff.remove(vm.getName());
                    sgVMsOn.put(vm.getName(), vm);
                }

                // Update getCPUDemand of the host
                Trace.hostVariableSet(name, "LOAD", host.getCPUDemand());

                // TODO test whether the node is violated or not (this can occur)

                //Update global getCPUDemand
                Trace.hostVariableSet(SimulatorManager.getInjectorNodeName(), "LOAD", SimulatorManager.getCPUDemand());
            }
            if (SimulatorProperties.getAlgo().equals("hierarchical")) {
                int hostNo = Integer.parseInt(name.replaceAll("\\D", ""));
                if (hostNo < SimulatorProperties.getNbOfHostingNodes()) {
                    try {
                        String[] lcArgs = new String[]{name, "dynLocalController-" + hostNo};
                        LocalController lc =
                                new LocalController(host.getSGHost(), "dynLocalController-" + hostNo, lcArgs);
                        lc.start();
                        Logger.info("[SimulatorManager.turnOn] Dyn. LC added: " + lcArgs[1]);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } else{
            Msg.info("Weird... you are asking to turn on a host that is already on !");
        }
    }

    /**
     * Turn off the XHost host
     * @param host the host to turn off
     */
    public static void turnOff(XHost host) {

        if(host.isOnGoingMigration()){
            Msg.info("WARNING = WE ARE NOT GOING TO TURN OFF HOST "+host.getName()+" BECAUSE THERE IS AN ON-GOING MIGRATION");
            return;
        }
        if(!host.isOff()) {
            Msg.info("Turn off "+host.getName());

            // if this is an hosting host, then you should deal with VM aspects
            if(sgHostingHosts.containsKey(host.getName())) {
                // First remove all VMs hosted on the node from the global collection
                // The VMs are still referenced on the node
                for (XVM vm : host.getRunnings()) {
                    Msg.info("TURNING NODE "+host.getName()+"OFF - REMOVE VM "+vm.getName());
                    sgVMsOn.remove(vm.getName());
                    sgVMsOff.put(vm.getName(), vm);
                }
                // Update getCPUDemand of the host
                Trace.hostVariableSet(host.getName(), "LOAD", 0);

                // TODO if the node is violated then it is no more violated
                //Update global getCPUDemand
                Trace.hostVariableSet(SimulatorManager.getInjectorNodeName(),  "LOAD", SimulatorManager.getCPUDemand());

            }


            int previousCount = org.simgrid.msg.Process.getCount();
            // Turn the node off
            host.turnOff();

            // Finally, remove the node from the collection of running host and add it to the collection of off ones
            sgHostsOn.remove(host.getName());
            sgHostsOff.put(host.getName(), host);

          //  Msg.info("Nb of remaining processes on " + host.getName() + ": " + (previousCount - org.simgrid.msg.Process.getCount()));


        }
        else{
            Msg.info("Weird... you are asking to turn off a host that is already off !");
        }
    }

    private static int getProcessCount(XHost host) {
        Msg.info ("TODO");
        System.exit(-1);
        return -1;

    }


    /**
     * Stupid variable to monitor the duration of the simulation
     */
    public static void setBeginTimeOfSimulation(double beginTimeOfSimulation) {
        SimulatorManager.beginTimeOfSimulation = beginTimeOfSimulation;
    }

    /**
     * Stupid variable to monitor the duration of the simulation
     */
    public static void setEndTimeOfSimulation(double endTimeOfSimulation) {
        SimulatorManager.endTimeOfSimulation = endTimeOfSimulation;
    }

    /**
     * Stupid variable to monitor the duration of the simulation
     */
    public static double getSimulationDuration() {
        return (endTimeOfSimulation != -1) ?  endTimeOfSimulation - beginTimeOfSimulation : endTimeOfSimulation;
    }

}
