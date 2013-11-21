package simulation;


import dvms.log.Logger;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;
import scheduling.dvms.DVMSMonitor;
import scheduling.dvms.DVMSNode;
import scheduling.dvms.DVMSServerForSG;
import scheduling.dvms.ServerForSG;

public class DVMSResolverV1 extends Process {

    DVMSResolverV1(Host host, String name, String[] args) throws HostNotFoundException, NativeException  {
        super(host, name, args);
    }

    private void launchInstance(
            String nodeId, int nbCPUs, int cpuCapacity, int memoryTotal,//Information for DVMSNode
            int port,//Information for associated DVMSServer
            String neighborHostname, int neighborPort){//Information for neighbor DVMSServer
	
		/* BEGIN DVMS V1 */

        Trace.hostVariableSet(Host.currentHost().getName(), "NB_MIG", 0);
        Trace.hostVariableSet(Host.currentHost().getName(), "NB_MC", 0);

        ServerForSG neighbor = new ServerForSG(neighborHostname, neighborPort);

        DVMSNode node = new DVMSNode(nodeId, nbCPUs, cpuCapacity, memoryTotal, neighbor);
        try {
            DVMSMonitor monitorTask = new DVMSMonitor(this.getHost(), "monitor-"+nodeId, node);
            monitorTask.start() ;

            DVMSServerForSG serverTask = new DVMSServerForSG( this.getHost(), "communicator-"+nodeId, port, node, monitorTask);
            serverTask.start();
            Msg.info("Agent "+nodeId+" started");

            while (!Main.isEndOfInjection()) {
                //	Msg.info("Resolver started and wait");
                waitFor(3);

            }
            waitFor(3);

        } catch (Exception e){
            Logger.flushAndClose();
            e.printStackTrace();
        }

        /* END DVMS V1 */
    }


    /**
     * @param args
     */
    public void main(String[] args) throws MsgException{
        if(args.length != 7){
            System.out.println("7 parameters required:");
            System.out.println("String nodeId, int nbCPUs, int cpuCapacity, int memoryTotal,\n" +
                    "int port,\n" +
                    "String neighborHostname, int neighborPort,\n");
            System.exit(1);
        }

        else{
            //Information for DVMSNode
            String nodeId = args[0];
            int nbCPUs = Integer.parseInt(args[1]);
            int cpuCapacity = Integer.parseInt(args[2]);
            int memoryTotal = Integer.parseInt(args[3]);

            //Information for associated DVMSServer
            int port = Integer.parseInt(args[4]);

            //Information for neighbor DVMSServer
            String neighborHostname = args[5];
            int neighborPort = Integer.parseInt(args[6]);

            //Create the msg Processes: the monitor and the communicator.
            launchInstance(
                    nodeId, nbCPUs, cpuCapacity, memoryTotal,
                    port,
                    neighborHostname, neighborPort);
        }
    }
}
