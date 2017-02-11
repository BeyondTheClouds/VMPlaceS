package simulation;

import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import org.simgrid.msg.*;
import org.simgrid.msg.File;
import org.simgrid.msg.Process;
import scheduling.SchedulerBuilder;
import scheduling.centralized.CentralizedResolverProperties;
import scheduling.Scheduler;
import scheduling.Scheduler.SchedulerResult;
import trace.Trace;

import java.io.*;
import java.util.Collection;


public class CentralizedResolver extends Process {


    static int loopID = 0 ;

    CentralizedResolver(Host host, String name) throws HostNotFoundException, NativeException  {
		super(host, name);
	}

	/**
	 * @param args
     * A stupid main to easily comment main2 ;)
	 */
    public void main(String[] args) throws MsgException{
       //main2(args);
    }
    public void main2(String[] args) throws MsgException {
        double period = CentralizedResolverProperties.getSchedulingPeriodicity();
        int numberOfCrash = 0;
        int numberOfBrokenPlan = 0;
        int numberOfSucess = 0;

        Trace.hostSetState(SimulatorManager.getInjectorNodeName(), "SERVICE", "free");

        double previousDuration = 0.0D;
        Scheduler scheduler;
        SchedulerResult schedulerResult;

        try{

            SimulatorManager.setSchedulerActive(true);
            int i = 0;


            double wait = period - previousDuration;
            if (wait > 0)
                waitFor(wait);

            while (!SimulatorManager.isEndOfInjection()) {
                Msg.info("Centralized resolver. Pass " + (++i));
			    /* Compute and apply the plan */
                Collection<XHost> hostsToCheck = SimulatorManager.getSGHostingHosts();
                scheduler = SchedulerBuilder.getInstance().build(hostsToCheck, ++loopID);
                schedulerResult = scheduler.checkAndReconfigure(hostsToCheck);
                previousDuration = schedulerResult.duration / 1000;
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
                    numberOfSucess++;
                }


               wait = period - previousDuration;
                if (wait > 0)
                    waitFor(wait);

            }
        } catch(HostFailureException e){
            System.err.println(e);
            System.exit(-1);
        }
        Msg.info(SimulatorProperties.getImplementation() + " has been invoked "+loopID+" times (success:"+ numberOfSucess+", failed: "+numberOfCrash+", brokenplan:"+numberOfBrokenPlan+")");
        SimulatorManager.setSchedulerActive(false);
    }

}
