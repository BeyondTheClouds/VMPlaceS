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
     * is the MSG host off (1) or on (0)
     */
    private boolean off;

    /**
     * A counter to check how many times a host has been turn off
     */
    private int turnOffNb;

    /**
     * A counter to check how many times a node has been violated
     */
    private int nbOfViolations;

    /**
     * current CPU Demand (a simple hack to avoid computing the CPU demand by going throughout all hosting VMs
     */
    private double currentCPUDemand;

    /**
     * Stupid boolean to prevent turning off a node that is performing migrations (this is an ugly way to prevent the migration crash bug
     * TODO fix the migration crash bug - Adrien
     */
    private boolean onGoingMigration;

    /**
     * Constructor
     * Please note that by default a XHOST is off (you should invoke turnOn)
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
       this.off = true;
       this.turnOffNb = 0;
       this.nbOfViolations = 0;
       this.currentCPUDemand = 0;

       this.onGoingMigration = false ;
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
    public double computeCPUDemand(){
        double cons=0;
        for (XVM vm: this.getRunnings())
            cons+=vm.getCPUDemand();
        return cons;
    }

    public double getCPUDemand(){
        return this.currentCPUDemand;
    }

    public void setCPUDemand(double newDemand){
        this.currentCPUDemand = newDemand;
    }

    /**
     * @return the sum of all memory demands of the hosted VMs
     */
    public int getMemDemand(){
        int cons=0;
        for (XVM vm: this.getRunnings())
            cons+=vm.getMemSize();
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
     * @return 0 if the migration succeeded -1 if it crashed
     */
    public int migrate(String vmName, XHost dest) {

        // Ugly patch to prevent migration crash when a node is turned off.
        this.onGoingMigration = true;
        dest.setOnGoingMigration(true);

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
        try {
            vm.migrate(dest.sgHost);
        } catch (Exception e){
            Msg.info("Host failure exception");
            this.onGoingMigration = false;
            dest.setOnGoingMigration(false);
            return -1;
        }
        if(this.isOff() || dest.isOff()) {
            // TODO, it is strange to return -1, because if the nodes have been turned off after the migration, the migration is correct. so returning -1 here is erroneous.
            System.err.println("Dammed the migration may have crash");
            return -1;
        }
        hostedVMs.remove(vm);
        this.setCPUDemand(computeCPUDemand());
        dest.hostedVMs.add(vm);
        dest.setCPUDemand(dest.computeCPUDemand());
        this.onGoingMigration = false;
        dest.setOnGoingMigration(false);
        return 0;
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
     *  turnOff a host (the host should be off, otherwise nothing happens)
     */
    public void turnOff() {

        if (this.onGoingMigration){
            System.err.println("This should not be possible. You probably invoked turnOff without passing through the simulatorManager... bye bye");
            System.exit(-1);
        }
        if(!this.off) {
         //   Msg.info("Turn off " + this.sgHost.getName());
            this.off=true;
            this.turnOffNb++;
            this.sgHost.off();
        }
    }

    /**
     * Turn on a host (the host should have been turn off previously), otherwise nothing happens
     */
    public void turnOn() {
        if (this.off){
            Msg.info("Turn on "+this.getName());
            this.off=false;
            this.sgHost.on();
        }
    }

    /**
     * @return whether the MSG host is off
     */
    public boolean isOff(){
        return this.off;
    }

    /**
     * @return whether the MSG host is on
     */
    public boolean isOn() {
        return !this.off ;
    }
    /**
     * @return the number of times the host has been turned off since the beginning of the simulation
     */
    public int getTurnOffNb() {
        return turnOffNb;
    }

    /**
     * @return the number of times the host has turn to a violation state since the beginning of the simulation
     */
    public int getNbOfViolations() {
        return nbOfViolations;
    }

    /**
     * Increment the number of violations
     */
    public void incViolation() {
        nbOfViolations++;
    }

    /**
     * Ugly patch to prevent migration crash when a node is switched off
     * TODO this code should be removed and the migration should be robust
     */
    public boolean isOnGoingMigration(){
        return this.onGoingMigration;
    }
    /**
     * Ugly patch to prevent migration crash when a node is switched off
     * TODO this code should be removed and the migration should be robust
     */
    public void setOnGoingMigration(boolean onGoingMigration) {
        this.onGoingMigration = onGoingMigration;
    }
}
