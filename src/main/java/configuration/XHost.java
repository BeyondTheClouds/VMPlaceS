/**
 *
 * Copyright 2012-2013-2014. The SimGrid Team. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 *
 * This class is an extension of the usual Host of the Simgrid MSG abstraction
 * Note that the extension is done by aggregation instead of inheritance. This is due to the fact that Simgrid is
 * creating the hosts and not the injection simulator.
 * @author: adrien.lebre@inria.fr
 */

package configuration;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;

import java.util.ArrayList;
import java.util.Collection;

public class XHost{

    /**
     * The VMs currently hosted on the VMs. Please note that a VM that is currently migrated to the host does not appear
     * in that list (i.e. this list contains only the VMs that are really hosted on the node).
     */
    private ArrayList<XVM> hostedVMs = null;

    /**
     * The MSG Host to extend (extension by aggregation)
     */
    private Host sgHost = null;
    /**
     * the size of the host
     */
    private int memSize;
    /**
     * the number of cores available on the host
     */
    private int ncores;
    /**
     * The total CPU capacity of the host (totalCPUCapa=ncores*capacity of one core)
     */
    private int totalCPUCapa;
    /**
     * the network bandwidth of the host NIC
     */
    private int netBW; //NetBandWidth
    /**
     * IP of the machine
     */
    private String ip;

    /**
     * Consrtuctor
     * @param h MSG host to extend
     * @param memSize the size of the memory of the host (rigid value, once it has been assigned it does not change)
     * @param ncores the number of cores available on the host
     * @param totalCPUCapa the total cpu capacity of the host
     * @param netBW the network bandwidth of the host
     * @param ip the ip of the host
     */
    public XHost(Host h, int memSize, int ncores,  int totalCPUCapa, int netBW, String ip) {
       this.sgHost = h ;
       this.memSize = memSize;
       this.ncores = ncores;
       this.totalCPUCapa = totalCPUCapa;
       this.netBW = netBW;
       this.ip = ip;
       this.hostedVMs = new ArrayList<XVM>();
    }

    /**
     * @return the MSG host abstraction
     */
    public Host getSGHost(){
        return this.sgHost;
    }
    /**
     * @return the size of the memory in MBytes (rigid value)
     */
    public int getMemSize(){
        return this.memSize;
    }

    /**
     * @return total CPU capacity
     */
    public int getCPUCapacity(){
        return this.totalCPUCapa;
    }

    /**
     * @return the nb of cores available on the node
     */
    public int getNbCores(){
        return this.ncores;
    }


    /**
     * @return the NIC capability (i.e. the bandwidth expressed in MBytes)
     */
    public int getNetBW(){
      return this.netBW;
    }

    /**
     * @return the IP of the host
     */
    public String getIP(){
        return this.ip;
    }

    /**
     * check whether a pm is viable or not (currently only for the CPU dimension)
     * @return boolean true if the PM is non viable (i.e. overloaded from the CPU viewpoint)
     */
     public boolean isViable(){
        return (this.getCPUDemand()<=this.getCPUCapacity());
     }

    /**
     * @return the sum of all CPU demands of the hosted VMs
     */
    public double getCPUDemand(){
        double cons=0;
        for (XVM vm: this.getRunnings())
            cons+=vm.getCPUDemand();
        return cons;
    }

    /**
     * Link the VM to the host and start it
     * @param sgVM
     */
    public void start(XVM sgVM) {
       hostedVMs.add(sgVM);
       sgVM.start();
    }

    /**
     * Migrate the vm vmName from this host to the dest one.
     * @param vmName
     * @param dest
     */
    public void migrate(String vmName, XHost dest) {
        XVM vm = null ;
        for(XVM tmp : getRunnings()){
            if (tmp.getName().equals(vmName))
                vm = tmp ;
        }
        if (vm == null){
            System.err.println("You are trying to manipulate a wrong  object (VM "+vmName+" is not on node "+this.getName());
            System.exit(-1);
        }
        // migrate the VM and reassign correctly to the corresponding host
        vm.migrate(dest);
        hostedVMs.remove(vm);
        dest.hostedVMs.add(vm);
    }

    /**
     *  @return the name that has been assigned to the sg Host abstraction
     */
    public String getName() {
        return this.getSGHost().getName();
    }

    /**
     * @return the vm hosted on the host (i.e. the collection of XVMs)
     */
    public Collection<XVM> getRunnings(){
        return hostedVMs;
    }

    /**
     * @return the current number of hosted VMs
     */
    public int getNbVMs() {
        return hostedVMs.size();

    }

    /**
     *  turnOff a host,
     *  All the hosted VMs are relocating (no migration, direct assignement) to other hosts randomly selected.
     */
    public void turnOff() {
        Msg.info("Turn off " + this.sgHost.getName());
        /* Before shutting down the nodes we should remove the VMs and the node from the configuration */

        // First remove all VMs hosted on the node
        XVM[] vms = new XVM[getRunnings().size()];
        int i = 0 ;
        for (XVM vm: getRunnings()){
            vms[i++]= vm ;
        }
        // Second, ugly hack for the moment
        // Just relocate VM on other nodes (no migration direct assignment)
        // TODO

        this.sgHost.off();
    }


    /**
     * Turn on a host (the host should have been turn off previously)
     */
    public void turnOn() {

        Msg.info("Turn on "+this.getName());
        this.sgHost.on();
    }
}
