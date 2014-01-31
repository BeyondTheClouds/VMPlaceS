package configuration;

import entropy.configuration.Node;
import entropy.configuration.VirtualMachine;
import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;
import simulation.Main;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: alebre
 * Date: 14/01/14
 * Time: 13:44
 * To change this template use File | Settings | File Templates.
 */
public class XHost{

    private ArrayList<XVM> hostedVMs = null;
    private Host sgHost = null;
    private int memSize;
    private int ncores;
    private int totalCPUCapa;
    private int netBW; //NetBandWidth
    private String ip;

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
     * @return sg host abstraction
     */
    public Host getSGHost(){
        return this.sgHost;
    }
    /**
     * @return size in MBytes
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
     * @return nb of cores
     */
    public int getNbCores(){
        return this.ncores;
    }


    public int getNetBW(){
      return this.netBW;
    }


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
     * @return the vm hosted on the host (equivalent to 'virsh list')
     */
    public Collection<XVM> getRunnings(){
        return hostedVMs;
    }

    public int getNbVMs() {
        return hostedVMs.size();

    }

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
    public void turnOn() {

        Msg.info("Turn on "+this.getName());
        this.sgHost.on();
    }
}
