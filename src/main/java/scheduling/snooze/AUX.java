package scheduling.snooze;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;

/**
 * Created by sudholt on 06/07/2014.
 */
public class AUX {
    static final String epInbox = "epInbox";                  // EP mbox

    static final String multicast = "multicast";              // GL/GM multicast mbox
    static Host multicastHost = null;

    static final String glElection = "glElection";            // HeartbeatGroup mbox

    static final long DefaultComputeInterval = 1;

    static final long HeartbeatInterval = SnoozeProperties.getHeartBeatPeriodicity();
    static final long HeartbeatTimeout = SnoozeProperties.getHeartBeatTimeout();

    static final long ReceiveTimeout = SnoozeProperties.getHeartBeatTimeout();
    static final double MessageReceptionTimeout = 0.2;

    static final long PoolingTimeout = SnoozeProperties.getHeartBeatTimeout(); // Timeout for worker tasks

    // constants for variants of Snooze alg.
    static final boolean GLElectionForEachNewGM = false;
    static final boolean GLElectionStopGM = true;
//    static final GroupLeader.AssignmentAlg assignmentAlg = GroupLeader.AssignmentAlg.BESTFIT;
    static final GroupLeader.AssignmentAlg assignmentAlg = GroupLeader.AssignmentAlg.ROUNDROBIN;

    static String glInbox(String glHost) { return glHost + "-glInbox"; }
    static String gmInbox(String gmHost) { return gmHost + "-gmInbox"; }
    static String lcInbox(String lcHost) { return lcHost + "-lcInbox"; }

    static double timeDiff(double oldTime) {
        return Msg.getClock()-oldTime;
    }
}
