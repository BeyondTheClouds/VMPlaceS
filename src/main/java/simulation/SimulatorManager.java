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
import org.simgrid.msg.Host;
import org.simgrid.msg.HostNotFoundException;
import org.simgrid.msg.Msg;
import org.simgrid.trace.Trace;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;

/**
 * Created with IntelliJ IDEA.
 * User: alebre
 * Date: 14/01/14
 * Time: 10:49
 * To change this template use File | Settings | File Templates.
 */
public class SimulatorManager {

    /**
     * The list of all XVMs
     * @see configuration.XVM
     */
    private static LinkedList<XVM> sgVMs = null;
    /**
     * The list of all XHosts
     * @see configuration.XHost
     */
    private static LinkedList<XHost> sgHosts= null;
    /**
     * Just a stupid boolean to stop the simulation when the injector is finishing to consume events from its queue
     */
    private static boolean endOfInjection = false;

    /**
     * When the injection is complete, we turn the endOfInjection boolean to true and kill the running daemon inside each VM
     */
	public static void setEndOfInjection(){
		endOfInjection=true;

        // Kill all VMs in order to finalize the simulation correctly
        for (XVM vm : SimulatorManager.getSGVMs()) {
            Msg.info(vm.getName() + " load changes: "+vm.getNbOfLoadChanges());
            vm.getDaemon().kill();
        }
	}

    /**
     * @return whether the injection is completed or not
     */
	public static boolean isEndOfInjection(){
		return endOfInjection;
	}


    /**
     * @return the collection of XVMs (i.e. the VMs running on the different hosts)
     */
    public static Collection<XVM> getSGVMs(){
        return sgVMs;
    }

    /**
     * @return the collection of XHosts (i.e. the Hosts composing the infrastructures and on wich VMs are running)
     * Please note that the service node (i.e. the host on wich the injector is running) is not included in the return collection.
     * The service node is not extended as an XHost. If you need to retrieve the MSG Host instance corresponding to the service node,
     * you should invoke the msg Host.getByName(SimulatorManager.getServiceNodeName()).
     */
    public static Collection<XHost> getSGHosts(){
        return sgHosts;

    }

    /**
     * @return the name of the service node (generally node0, if you do not change the first part of the main regarding the generation
     * of the deployment file).
     * If you change it, please note that you should then update the getServiceNodeName code.
     */
    public static String getServiceNodeName() {
        return "node0";
    }

    /**
     * For each MSG host (but the service node), the function creates an associated XHost.
     * As a reminder, the XHost class extends the Host one by aggregation.
     * At the end, all created hosts have been inserted into the sgHosts collection (see getSGHosts function)
     * @param nbOfHosts the number of hosts that has been created by SimGrid (e.g. 10 = 1 service node + 9 hosting nodes)
     */
    public static void initHosts(int nbOfHosts){
        // Since SG does not make any distinction between Host and Virtual Host (VMs and Hosts belong to the Host SG table)
        // we should retrieve first the real host in a separated table
        // Please remind that node0 does not host VMs (it is a service node) and hence, it is managed separately (getServiceNodeName())
        sgHosts = new LinkedList<XHost>();
        for(int i = 1 ; i <= nbOfHosts  ; i ++){
            try {
                Host tmp = Host.getByName("node" + i);
                // The SimulatorProperties.getCPUCapacity returns the value indicated by nodes.cpucapacity in the simulator.properties file
                sgHosts.add(new XHost (tmp, SimulatorProperties.getMemoryTotal(), SimulatorProperties.getNbOfCPUs(), SimulatorProperties.getCPUCapacity(), SimulatorProperties.getNetCapacity(), "127.0.0.1"));
            } catch (HostNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Create and assign the VMs on the different hosts.
     * For the moment, the initial placement follows a simple round robin strategy
     *  The algorithm fill the first host with the n first VMs until it reaches either a CPU or a memory limit, then it switches to the second host and so on.
     * @param nbOfHosts
     * @param nbOfVMs
     */
    public static void instanciateVMs(int nbOfHosts, int nbOfVMs) {

        int nodeIndex = 0;
        int[] nodeMemCons = new int[nbOfHosts+1]; // +1 since Node0 is used for the injector and does not host any vm.
        int vmIndex= 0;
        int i = 0 ;
        Random r = new Random(SimulatorProperties.getSeed());
        int nbOfVMClasses = VMClasses.CLASSES.size();
        VMClasses.VMClass vmClass;

        initHosts(nbOfHosts);
        sgVMs = new LinkedList<XVM>();

        XVM sgVMTmp = null;

        //Add VMs to each node, preventing memory over provisioning
        nodeIndex = 1;
        for (XHost sgHostTmp: SimulatorManager.getSGHosts()){
            nodeMemCons[nodeIndex] = 0;

            //The initialPlacement algorithm is stupid yet. It simply divides nb of VMs/nb of PMs
        /*    for ( i=0 ; i < nbOfVMs/nbOfHosts ; i++, vmIndex++ ){
                vmClass = VMClasses.CLASSES.get(r.nextInt(nbOfVMClasses));  vmClass = VMClasses.CLASSES.get(r.nextInt(nbOfVMClasses));
                if(nodeMemCons[nodeIndex] + vmClass.getMemSize() <= sgHostTmp.getMemSize()) {

                    // Creation of the VM
                    sgVMTmp = new XVM(sgHostTmp,"vm-" + vmIndex,
                            vmClass.getNbOfCPUs(), vmClass.getMemSize(), vmClass.getNetBW(), null, -1, vmClass.getMigNetBW(), vmClass.getMemIntensity());
                    sgVMs.add(sgVMTmp);

                    Msg.info(String.format("vm: %s, %d, %d, %s",
                            sgVMTmp.getName(),
                            vmClass.getMemSize(),
                            vmClass.getNbOfCPUs(),
                            "NO IPs defined"
                    ));
                    Msg.info("vm " + sgVMTmp.getName() + " is " + vmClass.getName() + ", dp is " + vmClass.getMemIntensity());
                    sgHostTmp.start(sgVMTmp);     // When the VM starts, its getCPUDemand equals 0
                    nodeMemCons[nodeIndex] += sgVMTmp.getMemSize();

                } else {
                    System.err.println("There is not enough memory on the physical host "+sgHostTmp.getName());
                    System.exit(1);
                }
            }*/

            do{
                    // Select the class for the VM
                    vmClass = VMClasses.CLASSES.get(r.nextInt(nbOfVMClasses));  vmClass = VMClasses.CLASSES.get(r.nextInt(nbOfVMClasses));

                    // Creation of the VM
                    sgVMTmp = new XVM(sgHostTmp,"vm-" + vmIndex,
                           vmClass.getNbOfCPUs(), vmClass.getMemSize(), vmClass.getNetBW(), null, -1, vmClass.getMigNetBW(), vmClass.getMemIntensity());
                    sgVMs.add(sgVMTmp);

                    Msg.info(String.format("vm: %s, %d, %d, %s",
                                sgVMTmp.getName(),
                                vmClass.getMemSize(),
                                vmClass.getNbOfCPUs(),
                                "NO IPs defined"
                        ));
                    Msg.info("vm " + sgVMTmp.getName() + " is " + vmClass.getName() + ", dp is " + vmClass.getMemIntensity());
                    sgHostTmp.start(sgVMTmp);     // When the VM starts, its getCPUDemand equals 0
                    nodeMemCons[nodeIndex] += sgVMTmp.getMemSize();


             }while(nodeMemCons[nodeIndex]+vmClass.getMemSize() <= sgHostTmp.getMemSize())

            nodeIndex++;
        }
    }

    public static boolean isViable() {
        for (XHost h: sgHosts){
                  if(!h.isViable())
                return false;
        }
        return true;
    }

    /**
     * Return the average getCPUDemand of the configuration
     * @return
     */
    public static double getCPUDemand() {
        double cons=0;
        double tmpLoad = 0 ;
        for(XHost h: sgHosts){
            tmpLoad = h.getCPUDemand()*100/h.getCPUCapacity();
            cons+= tmpLoad ;
        }
        return cons/sgHosts.size();
    }

    /**
     * Return the number of hosts that are active (i.e. that host at least one VM)
     */
    public static int getNbOfActiveHosts() {
        int i=0;
        for (XHost h: sgHosts){
            if(h.getNbVMs()>0)
                i++;
        }
        return i;
    }

    public static XHost getXHostByName(String name) {
        for (XHost tmp:sgHosts) {
            if(tmp.getName().equals(name))
                return tmp;
        }
        return null;
    }

    public static XVM getXVMByName(String name) {
        for (XVM tmp:sgVMs) {
            if(tmp.getName().equals(name))
                return tmp;
        }
        return null;
    }

    public static void updateVM(XVM sgVM, int load) {
        XHost tmpHost = sgVM.getLocation();
        boolean previouslyViable = tmpHost.isViable();
        sgVM.setLoad(load);
        Msg.info("Current getCPUDemand "+SimulatorManager.getCPUDemand()+"\n");

        if(previouslyViable) {
            if (!tmpHost.isViable()) {
                Msg.info("STARTING VIOLATION ON "+tmpHost.getName()+"\n");
                Trace.hostSetState(tmpHost.getName(), "PM", "violation");
            }
            else if(!previouslyViable){
                if (tmpHost.isViable()) {
                    Msg.info("ENDING VIOLATION ON "+tmpHost.getName()+"\n");
                    Trace.hostSetState (tmpHost.getName(), "PM", "normal");
                }
            }
            // Update getCPUDemand of the host
            Trace.hostVariableSet(tmpHost.getName(), "LOAD", tmpHost.getCPUDemand());

            //Update global getCPUDemand
            Trace.hostVariableSet(SimulatorManager.getServiceNodeName(),  "LOAD", tmpHost.getCPUDemand());
        }
    }

    public static void turnOn(XHost host) {
        host.turnOn();

    }

    public static void turnOff(XHost host) {
        host.turnOff();

    }
}
