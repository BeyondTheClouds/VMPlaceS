package scheduling.entropyBased.dvms2;

import configuration.XHost;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.Process;
import simulation.SimulatorManager;

import java.util.concurrent.TimeoutException;

public class TimeoutCheckerProcess extends Process {

    public TimeoutCheckerActor timeoutActor;

    public TimeoutCheckerProcess(XHost xhost, String name, int port, SGNodeRef ref, DVMSProcess process) {
        super(xhost.getSGHost(), String.format("%s-checkout-checker", name, port));

        this.timeoutActor = new TimeoutCheckerActor(ref, xhost, process);
    }

    public class TimeoutCheckerActor extends SGActor {

        SGNodeRef ref;
        DVMSProcess process;
        XHost xhost;

        public TimeoutCheckerActor(SGNodeRef ref, XHost xhost, DVMSProcess process) {
            super(ref);

            this.ref = ref;
            this.xhost = xhost;
            this.process = process;
        }

        public void doCheckTimeout() throws HostFailureException {

            send(ref, "checkTimeout");
            waitFor(1);
        }

        public void receive(Object message, SGNodeRef sender, SGNodeRef returnCanal) {

        }
    }

    public void main(String args[]) {

        try {
            while (!SimulatorManager.isEndOfInjection()) {

                timeoutActor.doCheckTimeout();

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}


