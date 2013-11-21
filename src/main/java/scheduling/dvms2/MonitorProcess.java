package scheduling.dvms2;

import dvms_scala.CpuViolationDetected;
import entropy.configuration.Node;
import entropy.configuration.VirtualMachine;
import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;
import simulation.Main;

/**
 * Created with IntelliJ IDEA.
 * User: jonathan
 * Date: 5/20/13
 * Time: 7:27 PM
 * To change this template use File | Settings | File Templates.
 */


public class MonitorProcess extends Process {

    public MonitorActor monitorActor;

    public MonitorProcess(Host host, String name, int port, SGNodeRef ref, Node configurationNode, DVMSProcess process) {
        super(host, String.format("%s-monitoring", name, port));

        this.monitorActor = new MonitorActor(ref, configurationNode, process);
    }

    public class MonitorActor extends SGActor {

        SGNodeRef ref;
        DVMSProcess process;
        Node configurationNode;
        boolean violation_detected = false ;

        public MonitorActor(SGNodeRef ref, Node configurationNode, DVMSProcess process) {
            super(ref);

            this.ref = ref;
            this.configurationNode = configurationNode;
            this.process = process;
        }

        public void doMonitoring() {

            int cpuConsumption = 0;

            for(VirtualMachine vm: Main.getCurrentConfig().getRunnings(configurationNode)){
                cpuConsumption += vm.getCPUDemand();
            }

//            Msg.info(String.format("%s's CPU consumption: %d/%d", name, cpuConsumption, configurationNode.getCPUCapacity()));

            if(cpuConsumption > configurationNode.getCPUCapacity()) {
                if (!violation_detected){
                    // Monitor is considering that the node is overloaded
                    Msg.info(ref.getName()+" monitoring service: node is overloaded");
                    Trace.hostPushState(Host.currentHost().getName(), "PM", "violation-det");
                    violation_detected = true;
                }
                send(ref, new CpuViolationDetected());
            }
            else if(cpuConsumption <= configurationNode.getCPUCapacity()) {
                Trace.hostPushState(Host.currentHost().getName(), "PM", "normal");
            }

        }
    }

    public void main(String args[]) {

        try {
            while(! Main.isEndOfInjection()) {

                monitorActor.doMonitoring();
                waitFor(1);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}


