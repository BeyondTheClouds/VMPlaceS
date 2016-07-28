package injector;

import configuration.XVM;
import org.simgrid.msg.*;
import simulation.SimulatorManager;
import trace.Trace;

public class VMSuspendResumeEvent implements InjectorEvent{
    private long id ;
    private double time;
    private XVM vm;
    private boolean state; // suspend = 0 ; resume = 1

    public VMSuspendResumeEvent(long id, double time, XVM vm, boolean state) {
        this.id = id;
        this.time = time;
        this.vm = vm;
        this.state = state ;

    }

    public long getId(){
        return this.id;
    }
    public double getTime() {
        return this.time;
    }

    public XVM getVM(){
        return this.vm;
    }

    public void play(){
            if (this.state) {
                SimulatorManager.resumeVM(vm.getName(), vm.getLocation().getName());

                Trace.hostVariableAdd(SimulatorManager.getInjectorNodeName(), "NB_VM_TRUE", 1);

            } else {

                SimulatorManager.suspendVM(vm.getName(), vm.getLocation().getName());
                Trace.hostVariableSub(SimulatorManager.getInjectorNodeName(), "NB_VM_TRUE", 1);
            }
    }

    public String toString(){
        return this.getTime() + "/" + this.getVM().getName() + "/" + this.state;
    }

    public boolean getState() {
        return state;
    }
}
