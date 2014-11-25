package scheduling.entropyBased.dvms2;

import dvms.log.Logger;
import org.discovery.dvms.entropy.EntropyActor;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import simulation.SimulatorManager;

import java.net.UnknownHostException;

/**
 * Created by jonathan on 24/11/14.
 */
public class EntropyProcess extends Process {

    private SGActor entropyActor;

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Constructor
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public EntropyProcess(Host host, String name, String hostname, int port) throws UnknownHostException {
        super(host, String.format("%s-entropy", hostname, port));

        this.entropyActor = new EntropyActor(new SGNodeRef(String.format("%s-entropy", hostname, port), id));
    }

    public SGNodeRef self() {
        return this.entropyActor.self();
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

                entropyActor.receive(req.getMessage(), new SGNodeRef(req.getOrigin(), reqId), new SGNodeRef(req.getReplyBox(), -1L));
            } catch (Exception e) {
                Logger.log(e);
            }
        }

        Msg.info("End of server");
    }
}
