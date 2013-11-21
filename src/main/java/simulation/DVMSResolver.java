package simulation;


import dvms.log.Logger;
import entropy.configuration.Node;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.dvms2.DVMSProcess;
import scheduling.dvms2.MonitorProcess;


/** This class is in charge of launching the latest version of DVMs (currently DVMS V2 implemented in SCALA)
 * @author Jonathan Pastor
 */
public class DVMSResolver extends Process {

    DVMSResolver(Host host, String name, String[] args) throws HostNotFoundException, NativeException  {
        super(host, name, args);
    }

    private void launchInstance(
            String nodeId, int nbCPUs, int cpuCapacity, int memoryTotal,//Information for DVMSNode
            int port,//Information for associated DVMSServer
            String neighborHostname, int neighborPort){//Information for neighbor DVMSServer
	
        try {


            // link the configuration.node reference to actors: doing this will enable monitoring actor to get cpu load
            Node currentConfigurationNode = Main.getCurrentConfig().getAllNodes().get(nodeId);


            DVMSProcess dmvsProcess = new DVMSProcess( this.getHost(), nodeId, port, neighborHostname, neighborPort);
            dmvsProcess.start();

            MonitorProcess monitorProcess = new MonitorProcess( this.getHost(), nodeId, port, dmvsProcess.self(), currentConfigurationNode, dmvsProcess);
            monitorProcess.start();

            Msg.info("Agent "+nodeId+" started");

//            this.getHost().getLoad();

            while (!Main.isEndOfInjection()) {
                //	Msg.info("Resolver started and wait");
//                System.out.println(String.format("toto: %s", nodeId));
                waitFor(3);

            }
            waitFor(3);

        } catch (Exception e){
            Logger.flushAndClose();
            e.printStackTrace();
        }

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
