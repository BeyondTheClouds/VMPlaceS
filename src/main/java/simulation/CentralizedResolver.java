package simulation;

import configuration.XHost;
import entropy.configuration.Configuration;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.CentralizedResolverProperties;
import trace.Trace;
import scheduling.entropyBased.entropy2.EntropyProperties;
import scheduling.entropyBased.entropy2.Entropy2RP;

import java.util.Collection;


public class CentralizedResolver extends Process {


    static int loopID = 0 ;

    CentralizedResolver(Host host, String name, String[] args) throws HostNotFoundException, NativeException  {
		super(host, name, args);
	}

	/**
	 * @param args
     * A stupid main to easily comment main2 ;)
	 */
    public void main(String[] args) throws MsgException{
       main2(args);
    }
    public void main2(String[] args) throws MsgException {
        double period = CentralizedResolverProperties.getSchedulingPeriodicity();
        int numberOfCrash = 0;
        int numberOfBrokenPlan = 0;
        int numberOfSucess = 0;

        Trace.hostSetState(SimulatorManager.getInjectorNodeName(), "SERVICE", "free");

        long previousDuration = 0;
        Entropy2RP scheduler;
        Entropy2RP.Entropy2RPRes entropyRes;

        try{
            while (!SimulatorManager.isEndOfInjection()) {

                long wait = ((long) (period * 1000)) - previousDuration;
                if (wait > 0)
                    Process.sleep(wait); // instead of waitFor that takes into account only seconds

			    /* Compute and apply the plan */
                Collection<XHost> hostsToCheck = SimulatorManager.getSGTurnOnHostingHosts();
                scheduler = new Entropy2RP((Configuration) Entropy2RP.ExtractConfiguration(hostsToCheck), loopID++);
                entropyRes = scheduler.checkAndReconfigure(hostsToCheck);
                previousDuration = entropyRes.getDuration();
                if (entropyRes.getRes() == 0) {
                    Msg.info("No Reconfiguration needed (duration: " + previousDuration + ")");

                } else if (entropyRes.getRes() == -1) {
                    Msg.info("No viable solution (duration: " + previousDuration + ")");
                    numberOfCrash++;
                } else if (entropyRes.getRes() == -2) {
                    Msg.info("Reconfiguration plan has been broken (duration: " + previousDuration + ")");
                    numberOfBrokenPlan++;
                } else { // entropyRes.getRes() == 1
                    Msg.info("Reconfiguration OK (duration: " + previousDuration + ")");
                    numberOfSucess++;
                }
            }
        } catch(HostFailureException e){
            System.err.println(e);
            System.exit(-1);
        }
        Msg.info("Entropy has been invoked "+loopID+" times (success:"+ numberOfSucess+", failed: "+numberOfCrash+", brokenplan:"+numberOfBrokenPlan+")");

    }

}
