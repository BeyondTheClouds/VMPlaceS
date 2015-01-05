package scheduling.entropyBased.dvms2;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.entropyBased.dvms2.dvms.timeout.TimeoutSnoozerActor;
import simulation.SimulatorManager;

import java.net.UnknownHostException;

/**
 * Created by jonathan on 24/11/14.
 */
public class TimeoutSnoozerProcess extends Process {

    private SGActor timeoutSnoozerActor;

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Constructor
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public TimeoutSnoozerProcess(Host host, String name, String hostname, int port) throws UnknownHostException {
        super(host, String.format("%s-timeoutsnoozer", hostname, port));

        this.timeoutSnoozerActor = new TimeoutSnoozerActor(new SGNodeRef(String.format("%s-timeoutsnoozer", hostname, port), id),  host);
    }

    public SGNodeRef self() {
        return this.timeoutSnoozerActor.self();
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

                timeoutSnoozerActor.receive(req.getMessage(), new SGNodeRef(req.getOrigin(), reqId), new SGNodeRef(req.getReplyBox(), -1L));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Msg.info("End of server");
    }
}
