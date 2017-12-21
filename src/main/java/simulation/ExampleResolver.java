package simulation;

import org.simgrid.msg.Host;
import org.simgrid.msg.HostNotFoundException;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;
import scheduling.Scheduler;
import scheduling.example.ExampleReconfigurationPlanner;

public class ExampleResolver extends Process {

    private ExampleReconfigurationPlanner planner = new ExampleReconfigurationPlanner();

    ExampleResolver(Host host, String name, String[] args) throws HostNotFoundException {
        super(host, name, args);
    }

    public void main(String[] args) throws MsgException {
        while (!SimulatorManager.isEndOfInjection()) {
            System.out.println("Checking");

            Scheduler.ComputingResult configurationResult = planner.computeReconfigurationPlan();

            if (configurationResult.state != Scheduler.ComputingResult.State.NO_RECONFIGURATION_NEEDED) {
                planner.applyReconfigurationPlan();
            }

            waitFor(30);
        }
    }
}
