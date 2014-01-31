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

    private static LinkedList<XVM> sgVMs = null;
    private static LinkedList<XHost> sgHosts= null;

    private static boolean endOfInjection = false;

	public static void setEndOfInjection(){
		endOfInjection=true;
	}

	public static boolean isEndOfInjection(){
		return endOfInjection;
	}


    public static Collection<XVM> getSGVMs(){
        return sgVMs;
    }

    public static Collection<XHost> getSGHosts(){
        return sgHosts;
    }

    public static String getServiceNodeName() {
        return "node0";
    }

    public static void initHosts(int nbOfHosts){
     /* Since SG does not make any distinction between Host and Virtual Host (VMs and Hosts belong to the Host SG table)
        we should retrieve first the real host in a separeted table */
       // Please remind that node0 does not host VMs (it is a service node) and hence, it is managed separately (getServiceNodeName())
        sgHosts = new LinkedList<XHost>();
        for(int i = 1 ; i <= nbOfHosts  ; i ++){
            try {
                Host tmp = Host.getByName("node" + i);
                sgHosts.add(new XHost (tmp, SimulatorProperties.getMemoryTotal(), SimulatorProperties.getNbOfCPUs(), SimulatorProperties.getCPUCapacity(), SimulatorProperties.getNetCapacity(), "127.0.0.1"));
            } catch (HostNotFoundException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }

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
            for ( i=0 ; i < nbOfVMs/nbOfHosts ; i++, vmIndex++ ){
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
            }
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
