/**
 *  Copyright 2012-2013-2014. The SimGrid Team. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 *
 * This class is an extension of the usual VM of the Simgrid MSG abstraction
 * Note that the extension is done by aggregation instead of inheritance. This enables to create/destroy the sg VM while
 * manipulating the same XVM at the java level.
 *
 * @author: adrien.lebre@inria.fr
 */

package configuration;

import org.simgrid.msg.*;

import java.sql.Timestamp;

public class XVM {

    /**
     * The MSG VM to extend (extension by aggregation)
     */
    private VM vm;
    /**
     * The dirty page intensity of the VM (currently determined by the class of the VM, see the configureHostsAndVMs method).
     * Expressed as a percentage of the netBW (i.e. an integer between 0 and 100)
     * @see simulation.SimulatorManager
     */
    private int dpIntensity;
    /**
     *  The bandwidth network capability of the VM (expressed as MBytes).
     */
    private int netBW;
    /**
     * The ramsize of the VM
     */
    private int ramsize;
    /**
     * The current load of the VM (i.e. how the CPU is loaded).
     * Please note that for the moment, one VM can have only have one vcpu.
     * Hence, this value represents the current load of the vcpu (i.e. between 0 and 100%)
     */
    private int currentLoadDemand;

    /**
     * The number of times the load has been changed during the simulation.
     * This metric is relevant to check whether one particular VM is more affected than the others
     */
    private int NbOfLoadChanges;

    /**
     * The daemon that runs inside the VM in order to simulate the load.
     */
    private Daemon daemon;
    /**
     * the XHost (i.e the physical machine) of the VM.
     * Similarly to the VM abstraction, the MSG Host abstraction has been extended in order to save/manipulate
     * additional states in an easier way.
     */
    private XHost host;

    /**
     * Temporary fix due to a simgrid issue
     * See https://gforge.inria.fr/tracker/index.php?func=detail&aid=17636&group_id=12&atid=165
     */
    private boolean vmIsMigrating; //Temporary fix to prevent migrating the same VM twice

    /**
     * Construcor
     * @param host the XHost (i.e. the PM where the VM is currently running)
     * @param name the name of the vm (as it is listed by virsh list for instance)
     * @param nbCores the nbCores of the VM, please note that right now the injector is able to correctly manage only
     *                one core VM.
     * @param ramsize the size of the RAM (rigid parameter, once it has been assigned this value should not evolve.
     * @param netBW the bandwidth of the NIC (expressed in MBytes per second, for instance for a 1Gb/s ethernet NIC, you
     *              should mention 125 MBytes.
     * @param diskPath the path to the disk image (not used for the moment)
     * @param diskSize the size of the disk image (not used for the moment)
     * @param migNetBW the network bandwidth available for performing the migration (i.e. rigid value, this is the
     *                 maximum value that the migration can expect). In the first version of KVM, the bandwidth for the
     *                 migration was limited to 32MBytes. Although now, kvm uses the whole bandwidth that can be offered
     *                 by the Host NIC, users can define a dedicated value for one VM y using the (virsh migrate_set_speed
     *                 command.
     * @param dpIntensity the dirty page intensity, i.e. the refresh rate of the memory as described in the cloudcom
     *                    2013 paper (Adding a Live Migration Model into SimGrid: One More Step Toward the Simulation of
     *                    Infrastructure-as-a-Service Concerns)
     *                    The parameter is expressed has a percentage of the network bandwidth.
     *
     */
     public XVM(XHost host, String name,
            int nbCores, int ramsize, int netBW, String diskPath, int diskSize, int migNetBW, int dpIntensity){
        // TODO, why should we reduce the migNetBW ? (i.e. interest of multiplying the value by 0.9)
        this.vm = new VM (host.getSGHost(), name, nbCores, ramsize, netBW, diskPath, diskSize, (int)(migNetBW*0.9), dpIntensity);
        this.currentLoadDemand = 0;
        this.netBW = netBW ;
        this. dpIntensity = dpIntensity ;
        this.ramsize= ramsize;
        this.daemon = new Daemon(this.vm, 100);
        this.host = host;
        this.NbOfLoadChanges = 0;
        this.vmIsMigrating = false;
   }

    /* Delegation method from MSG VM */

    /**
     * @return the name of the VM
     */
    public String getName() {
        return this.vm.getName();
    }

    /**
     * @return the number of core of the VM
     */
    public double getCoreNumber() {
        return this.vm.getCoreNumber();
    }
    /**
     * Change the load of the VM, please remind that the load of the VM is set to 0 at its beginning.
     * TODO, check whether it makes sense to set the load to a minimal load.
     * @param expectedLoad expressed as a percentage (i.e. between 0 and 100)
     */
    public void setLoad(int expectedLoad){
        if (expectedLoad >0) {
            this.vm.setBound(expectedLoad);
            daemon.resume();
        }
        else{
            daemon.suspend();
        }
        currentLoadDemand = expectedLoad ;
        NbOfLoadChanges++;
    }

    /**
     * @return the daemon process (i.e MSG Process) in charge of simulating the load of the VM
     */
    public Daemon getDaemon(){
        return this.daemon;
    }

    /**
     * @return the number of times the load has been changed since the begining of the simulation
     */
    public int getNbOfLoadChanges() {
        return NbOfLoadChanges;
    }


    /**
     *  Override start method in order to start the daemon at the same time that should run inside the VM.
     */
    public void start(){
        this.vm.start();
        try {
            daemon.start();
        } catch (HostNotFoundException e) {
            e.printStackTrace();
        }
        this.setLoad(currentLoadDemand);
    }

    /**
     * Migrate a VM from one XHost to another one.
     * @param host the host where to migrate the VM
     */
    public void migrate(XHost host) {
        if (!this.vmIsMigrating) {
            this.vmIsMigrating = true;
            Msg.info("Start migration of VM " + this.getName() + " to " + host.getName());
            Msg.info("    currentLoadDemand:" + this.currentLoadDemand + "/ramSize:" + this.ramsize + "/dpIntensity:" + this.dpIntensity + "/remaining:" + this.daemon.getRemaining());
            try {
                this.vm.migrate(host.getSGHost());
                this.host = host;
                this.setLoad(this.currentLoadDemand);   //TODO temporary fixed (setBound is not correctly propagated to the new node at the surf level)
                //The dummy cpu action is not bounded.
                Msg.info("End of migration of VM " + this.getName() + " to node " + host.getName());
                this.vmIsMigrating = false;
            } catch (Exception e){
                e.printStackTrace();
                Msg.info("Something strange occurs during the migration");
                Msg.info("TODO Adrien, migrate should return 0 or -1, -2, ... according to whether the migration succeeded or not.");
                System.exit(1);
                // TODO Adrien, migrate should return 0 or -1, -2, ... according to whether the migration succeeded or not.
                // This value can be then use at highler level to check whether the reconfiguration plan has been aborted or not.
            }
        } else {
            Msg.info("You are trying to migrate twice a VM... it is impossible ! Byebye");
            System.exit(-1);
        }
    }

    /**
     * @return the size of the RAM in MBytes
     */
    public int getMemSize(){
        return this.ramsize;
    }

    /**
     * @return the current load of the VM
     */
    public double getCPUDemand() {
        return this.currentLoadDemand;
    }

    /**
     * @return the current location of the VM (i.e. its XHost)
     */
    public XHost getLocation() {
        return this.host;
    }

    /**
     * @return the load of the network
     */
    public long getNetBW() {
        return this.netBW;
    }

}
