package configuration;

import entropy.configuration.Node;
import entropy.configuration.VirtualMachine;
import org.simgrid.msg.*;
import org.simgrid.trace.Trace;
import simulation.Main;

/**
 * A stupid VM extension to associate a daemon to the VM
 */
public class XVM extends VM{

    private int dpIntensity;
    private int netBW;
    private int ramsize;
    private int currentLoadDemand;
    private Daemon daemon;
    private XHost host;

    public XVM(XHost host, String name,
            int nbCores, int ramsize, int netBW, String diskPath, int diskSize, int migNetBW, int dpIntensity){
        super(host.getSGHost(), name, nbCores, ramsize, netBW, diskPath, diskSize, (int)(migNetBW*0.9), dpIntensity);
        this.currentLoadDemand = 0;
        this.netBW = netBW ;
        this. dpIntensity = dpIntensity ;
        this.ramsize= ramsize;
        this.daemon = new Daemon(this, 100);
        this.host = host;
   }

    public void setLoad(int expectedLoad){
        if (expectedLoad >0) {
            this.setBound(expectedLoad);
            daemon.resume();
        }
        else{
            daemon.suspend();
        }
        currentLoadDemand = expectedLoad ;
    }

    public Daemon getDaemon(){
        return this.daemon;
    }

    /**
     *  Override start method in order to start the daemon at the same time that should run inside the VM.
     */
    public void start(){
        super.start();
        try {
            daemon.start();
        } catch (HostNotFoundException e) {
            e.printStackTrace();
        }
        this.setLoad(currentLoadDemand);
    }
  /*  public int getLoad(){
        System.out.println("Remaining comp:" + this.daemon.getRemaining());
        return this.currentLoadDemand;
    }
    */
    public void migrate(XHost host){
        Msg.info("Start migration of VM " + this.getName() + " to " + host.getName());
        Msg.info("    currentLoadDemand:"+this.currentLoadDemand +"/ramSize:"+this.ramsize+"/dpIntensity:"+this.dpIntensity+"/remaining:"+this.daemon.getRemaining());
        super.migrate(host.getSGHost());
        this.host = host;
        this.setLoad(this.currentLoadDemand); //TODO temporary fixed (setBound is not correctly propagated to the new node at the surf level)
                                        //The dummy cpu action is not bounded.
        Msg.info("End of migration of VM " + this.getName() + " to node " + host.getName());
    }

    /**
     * @return RAMSIZE in MBytes
     */
    public int getMemSize(){
        return this.ramsize;
    }

    public double getCPUDemand() {
        return this.currentLoadDemand;
    }


    public XHost getLocation() {
        return this.host;
    }

    public long getNetBW() {
        return this.netBW;
    }
}
