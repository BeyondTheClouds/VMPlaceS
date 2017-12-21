package simulation;

import configuration.SimulatorProperties;
import configuration.XHost;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import scheduling.SchedulerBuilder;
import scheduling.centralized.CentralizedResolverProperties;
import scheduling.Scheduler;
import scheduling.Scheduler.SchedulerResult;
import trace.Trace;

import java.util.Collection;


public class CentralizedResolver extends Process {


    static int loopID = 0 ;

    CentralizedResolver(Host host, String name, String[] args) throws HostNotFoundException {
		super(host, name, args);
	}

    /**
     * @param args
     */
    public void main(String[] args) throws MsgException{
        double period = CentralizedResolverProperties.getSchedulingPeriodicity();
        int numberOfCrash = 0;
        int numberOfBrokenPlan = 0;
        int numberOfSuccess = 0;

        Trace.hostSetState(SimulatorManager.getInjectorNodeName(), "SERVICE", "free");

        long previousDuration = 0;
        Scheduler scheduler;
        SchedulerResult schedulerResult;

        try{

            SimulatorManager.setSchedulerActive(true);
            int i = 0;

            long wait = ((long) (period * 1000)) - previousDuration;
            if (wait > 0) {
                Msg.info("Resolver going to sleep for " + wait + " milliseconds");
                Process.sleep(wait); // instead of waitFor that takes into account only seconds
                Msg.info("Resolver woke up");
            }

            while (!SimulatorManager.isEndOfInjection()) {

                Msg.info("Centralized resolver. Pass " + (++i));
			    /* Compute and apply the plan */
                Collection<XHost> hostsToCheck = SimulatorManager.getSGHostingHosts();


                scheduler = SchedulerBuilder.getInstance().build(hostsToCheck, ++loopID);
                schedulerResult = scheduler.checkAndReconfigure(hostsToCheck);
                previousDuration = schedulerResult.duration;
                if (schedulerResult.state == SchedulerResult.State.NO_RECONFIGURATION_NEEDED) {
                    Msg.info("No Reconfiguration needed (duration: " + previousDuration + ")");
                } else if (schedulerResult.state== SchedulerResult.State.NO_VIABLE_CONFIGURATION) {
                    Msg.info("No viable solution (duration: " + previousDuration + ")");
                    numberOfCrash++;
                } else if (schedulerResult.state == SchedulerResult.State.RECONFIGURATION_PLAN_ABORTED) {
                    Msg.info("Reconfiguration plan has been broken (duration: " + previousDuration + ")");
                    numberOfBrokenPlan++;
                } else {
                    Msg.info("Reconfiguration OK (duration: " + previousDuration + ")");
                    numberOfSuccess++;
                }

               wait = ((long) (period * 1000)) - previousDuration;
                if (wait > 0) {
                    Msg.info("Resolver going to sleep for " + wait + " milliseconds");
                    Process.sleep(wait); // instead of waitFor that takes into account only seconds
                    Msg.info("Resolver woke up");
                }

            }
        } catch(HostFailureException e){
            System.err.println(e);
            System.exit(-1);
        }
        Msg.info(SimulatorProperties.getImplementation() + " has been invoked "+loopID+" times (success:"+ numberOfSuccess+", failed: "+numberOfCrash+", brokenplan:"+numberOfBrokenPlan+")");
        SimulatorManager.setSchedulerActive(false);
    }

}
