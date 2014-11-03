package scheduling.snooze;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;
import org.simgrid.msg.Task;
import scheduling.snooze.msg.SnoozeMsg;

/**
 * Created by sudholt on 06/07/2014.
 */
public class AUX {
    static final String epInbox = "epInbox";                  // EP mbox
//    static final String epGLElection = "epGLElection";        // EP mbox
    static final String multicast = "multicast";              // GL/GM multicast mbox
    static Host multicastHost = null;
//    static final String glHeartbeatNew = "glHeartbeatNew";    // HeartbeatGroup mbox
//    static final String glHeartbeatBeat = "glHeartbeatBeat";  // HeartbeatGroup mbox
    static final String glElection = "glElection";            // HeartbeatGroup mbox
//    static final String gmHeartbeatNew = "gmHeartbeatNew";    // HeartbeatGroup mbox
//    static final String gmHeartbeatBeat = "gmHeartbeatBeat";  // HeartbeatGroup mbox
    static final long DefaultComputeInterval = 1;
  //  static final long EntropyComputationTime = 30000;
    static final long HeartbeatInterval = SnoozeProperties.getHeartBeatPeriodicity();
    static final long ReceiveTimeout = SnoozeProperties.getHeartBeatTimeout();
//    static final long ReceiveTimeout = SnoozeProperties.getHeartBeatPeriodicity()/2;
    static final long HeartbeatTimeout = SnoozeProperties.getHeartBeatTimeout();
    //static final long SchedulingPeriodicity = 1000*SnoozeProperties.getSchedulingPeriodicity();
//    static final long JoinAcknowledgementTimeout = 5000;
//    static final long GLCreationTimeout = 1000;
    static final double MessageReceptionTimeout = 0.2;

    static final GroupLeader.AssignmentAlg assignmentAlg = GroupLeader.AssignmentAlg.ROUNDROBIN;
    static final boolean GLElectionForEachNewGM = false;

    static SnoozeMsg arecv(String mbox) {
        if (Task.listen(mbox))
            try {
                return (SnoozeMsg) Task.receive(mbox);
            } catch (Exception e) {
                e.printStackTrace();
            }
        return null;
    }

    static String glInbox(String glHost) { return glHost + "-glInbox"; }
    static String gmInbox(String gmHost) { return gmHost + "-gmInbox"; }
    static String lcInbox(String lcHost) { return lcHost + "-lcInbox"; }

    static double timeDiff(double oldTime) {
        return Msg.getClock()-oldTime;
    }
}
