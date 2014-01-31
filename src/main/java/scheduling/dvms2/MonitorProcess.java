package scheduling.dvms2;

import configuration.XHost;
import configuration.XVM;
import dvms_scala.CpuViolationDetected;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;
import simulation.SimulatorManager;

/**
 * Created with IntelliJ IDEA.
 * User: Adrien Lebre
 * Date: 5/20/13
 * Time: 7:27 PM
 */


public class MonitorProcess extends Process {

    public MonitorActor monitorActor;

    public MonitorProcess(XHost xhost, String name, int port, SGNodeRef ref, DVMSProcess process) {
        super(xhost.getSGHost(), String.format("%s-monitoring", name, port));

        this.monitorActor = new MonitorActor(ref, xhost, process);
    }

    public class MonitorActor extends SGActor {

        SGNodeRef ref;
        DVMSProcess process;
        XHost xhost;
        boolean violation_detected = false ;

        public MonitorActor(SGNodeRef ref, XHost xhost, DVMSProcess process) {
            super(ref);

            this.ref = ref;
            this.xhost = xhost;
            this.process = process;
        }

        public void doMonitoring() {

            int cpuConsumption = 0;

            for(XVM vm: this.xhost.getRunnings()){
                cpuConsumption += vm.getCPUDemand();
            }

//            Msg.info(String.format("%s's CPU consumption: %d/%d", name, cpuConsumption, configurationNode.getCPUCapacity()));

            if(cpuConsumption > this.xhost.getCPUCapacity()) {
                if (!violation_detected){
                    // Monitor is considering that the node is overloaded
                    Msg.info(ref.getName()+" monitoring service: node is overloaded");
                    Trace.hostPushState(xhost.getName(), "PM", "violation-det");
                    violation_detected = true;
                }
                send(ref, new CpuViolationDetected());
            }
            else if(cpuConsumption <= this.xhost.getCPUCapacity()) {
                Trace.hostPushState(Host.currentHost().getName(), "PM", "normal");
            }

        }
    }

    public void main(String args[]) {

        try {
            while(! SimulatorManager.isEndOfInjection()) {

                monitorActor.doMonitoring();
                waitFor(1);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}


