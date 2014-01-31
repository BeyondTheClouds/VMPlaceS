package scheduling.dvms2;

import java.net.UnknownHostException;

import dvms_scala.DvmsActor;
import dvms_scala.ThisIsYourNeighbor;
import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;
import org.simgrid.msg.Task;

import dvms.log.Logger;
import simulation.SimulatorManager;

//Represents a server running on a worker node
//Currently, this server can only process on request at a time -> less concurrent access to the node object
public class DVMSProcess extends Process {

    private DvmsActor dvms;
    Long id;
    Long neighborId;

    String name;
    String neighborName;

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Constructor
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public DVMSProcess(Host host, String name, int port, String neighborHostname, int neighborPort) throws UnknownHostException  {
        super(host, String.format("%s", name, port));

        this.name = String.format("%s", name, port);
        this.neighborName = String.format("%s", neighborHostname, port);

        this.id = nameToId(name);
        this.neighborId = nameToId(neighborHostname);


        this.dvms = new DvmsActor(new SGNodeRef(String.format("%s", name, port), id));
    }

    public SGNodeRef self() {

        return this.dvms.self();
    }

    public static Long nameToId(String name) {

        Long result = -1L;
        try {
            result = Long.parseLong(name.substring(4, name.length()));
        } catch(Exception e) {

        }

        return result;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Other methods
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    String mBox = "";

    @Override
    public void main(String[] args) throws MsgException {

        mBox = this.name;

        Long nextId = nameToId(neighborName);
        this.updateNextNode(new SGNodeRef(neighborName, nextId));

        while(!SimulatorManager.isEndOfInjection()){

            try{

                MsgForSG req=(MsgForSG) Task.receive(mBox);
                Logger.log(Host.currentHost().getName() + ": received " + req.getMessage());

                Long reqId = nameToId(req.getSender().getHost().getName());

                dvms.receive(req.getMessage(), new SGNodeRef(req.getOrigin(), reqId), new SGNodeRef(req.getReplyBox(), -1L));
            } catch (Exception e) {
                Logger.log(e);
            }
        }

        Msg.info("End of server");
    }

    private void updateNextNode(SGNodeRef next) {
        // WARNING this is just a simple string
        // pattern: Host.currentHost().getName()+"-dvms"
        // ex: node1-dvms
        this.dvms.send(dvms.self(), new ThisIsYourNeighbor(next));

    }
}

