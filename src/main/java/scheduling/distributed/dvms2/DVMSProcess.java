package scheduling.distributed.dvms2;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.distributed.dvms2.dvms.dvms2.DvmsActor;
import scheduling.distributed.dvms2.dvms.dvms3.LocalityBasedScheduler;
import simulation.SimulatorManager;

import java.net.UnknownHostException;

//Represents a server running on a worker node
//Currently, this server can only process on request at a time -> less concurrent access to the node object
public class DVMSProcess extends Process {

    private SGActor dvms;

    Long id;
    String name;

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Constructor
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public DVMSProcess(Host host, String name, String hostname, int port, SGNodeRef entropyActorRef, SGNodeRef snoozerActorRef) throws UnknownHostException  {
        super(host, String.format("%s", hostname, port));

        this.name = String.format("%s", hostname, port);
        this.id = nameToId(hostname);


        if(DvmsProperties.isLocalityBasedScheduler()) {
            this.dvms = new LocalityBasedScheduler(new SGNodeRef(String.format("%s", hostname, port), id), this, entropyActorRef, snoozerActorRef);
        } else {
            this.dvms = new DvmsActor(new SGNodeRef(String.format("%s", hostname, port), id), this, entropyActorRef, snoozerActorRef);
        }
    }

    public SGNodeRef self() {

        return this.dvms.self();
    }

    public static Long nameToId(String name) {

        Long result = -1L;
        try {
            result = Long.parseLong(name.substring(4, name.length()));
        } catch(Exception e) {
            e.printStackTrace();
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

        while(!SimulatorManager.isEndOfInjection()){

            try{

                MsgForSG req=(MsgForSG) Task.receive(mBox);
                Long reqId = nameToId(req.getSender().getHost().getName());

                dvms.receive(req.getMessage(), new SGNodeRef(req.getOrigin(), reqId), new SGNodeRef(req.getReplyBox(), -1L));
            } catch (Exception e) {
                Msg.info(String.format("Failure on %s", mBox));
                e.printStackTrace();
            }
        }

        Msg.info("End of server");
    }
}

