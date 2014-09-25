package simulation;


import dvms.log.Logger;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.entropyBased.dvms2.DVMSProcess;
import scheduling.entropyBased.dvms2.MonitorProcess;
import scheduling.entropyBased.dvms2.TimeoutProcess;
import scheduling.entropyBased.dvms2.overlay.SimpleOverlay;


/** This class is in charge of launching the latest version of DVMs (currently DVMS V2 implemented in SCALA)
* @author Jonathan Pastor
*/
public class DistributedResolver extends Process {

    DistributedResolver(Host host, String name, String[] args) throws HostNotFoundException, NativeException  {
        super(host, name, args);
    }

    private void launchInstance(
            String nodeId, int nbCPUs, int cpuCapacity, int memoryTotal,//Information for DVMSNode
            int port,//Information for associated DVMSServer
            String neighborHostname, int neighborPort){//Information for neighbor DVMSServer

        try {

            DVMSProcess dmvsProcess = new DVMSProcess(this.getHost(), name, nodeId, port, neighborHostname, neighborPort);
            dmvsProcess.start();

            MonitorProcess monitorProcess = new MonitorProcess(SimulatorManager.getXHostByName(host.getName()), nodeId, port, dmvsProcess.self(), dmvsProcess);
            monitorProcess.start();

            TimeoutProcess timeoutProcess = new TimeoutProcess(SimulatorManager.getXHostByName(host.getName()), nodeId, port, dmvsProcess.self(), dmvsProcess);
            timeoutProcess.start();

            Msg.info("Agent "+nodeId+" started");

            // Register in the Overlay the current nodeRef
            SimpleOverlay.register(nodeId, dmvsProcess.self(), this);

            while (!SimulatorManager.isEndOfInjection()) {

                waitFor(3);

            }
            waitFor(3);

        } catch (Exception e){
            Logger.flushAndClose();
//            e.printStackTrace();
        }

    }


    /**
     * @param args
     */
    public void main(String[] args) {
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
