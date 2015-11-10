package simulation;

import configuration.SimulatorProperties;
import configuration.XHost;
import entropy.configuration.Configuration;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.CentralizedResolverProperties;
import scheduling.Scheduler;
import scheduling.SchedulerFactory;
import scheduling.SchedulerRes;
import scheduling.btrplace.BtrPlaceRP;
import scheduling.btrplace.ConfigBtrPlace;
import scheduling.entropy2.Entropy2RP;
import trace.Trace;

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
//        Entropy2RP scheduler;
//        Entropy2RP.Entropy2RPRes entropyRes;
//        BtrPlaceRP scheduler;
//        BtrPlaceRP.Btr_PlaceRPRes entropyRes;
        Scheduler scheduler;
        SchedulerRes entropyRes;

        try{
            while (!SimulatorManager.isEndOfInjection()) {

                long wait = ((long) (period * 1000)) - previousDuration;
                if (wait > 0)
                    Process.sleep(wait); // instead of waitFor that takes into account only seconds

			    /* Compute and apply the plan */
                Collection<XHost> hostsToCheck = SimulatorManager.getSGTurnOnHostingHosts();
//                scheduler = new Entropy2RP(hostsToCheck, loopID++);
//                scheduler = new BtrPlaceRP(hostsToCheck, loopID++);
                scheduler = SchedulerFactory.getScheduler(hostsToCheck, loopID++);
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
        String strat = SimulatorProperties.getStrategy();
        strat = strat.substring(0, 1).toUpperCase() + strat.substring(1);
        Msg.info(strat+" has been invoked "+loopID+" times (success:"+ numberOfSucess+", failed: "+numberOfCrash+", brokenplan:"+numberOfBrokenPlan+")");

    }

}
