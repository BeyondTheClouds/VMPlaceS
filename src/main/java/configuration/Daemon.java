
package configuration;


import org.simgrid.msg.*;
import org.simgrid.msg.Process;
import simulation.SimulatorManager;

public class Daemon extends Process {
	private Task currentTask;
    private int load;
    public Daemon(Host host, int load) {
		super(host,"Daemon");
        this.load = load ;
        currentTask = new Task(this.getHost().getName()+"-daemon-0", this.getHost().getSpeed()*100, 0);
        //   currentTask.setBound(getCPUDemand);
    }
    public void main(String[] args) throws MsgException {
        int i = 1;

        while(!SimulatorManager.isEndOfInjection()) {
            // TODO the binding is not yet available
            try {
                currentTask.execute();
            } catch (HostFailureException e) {
                e.printStackTrace();
            } catch (TaskCancelledException e) {
                System.out.println("task cancelled");
                suspend(); // Suspend the process
            }
            currentTask = new Task(this.getHost().getName()+"-daemon-"+(i++), this.getHost().getSpeed()*100, 0);
            //currentTask.setBound(getCPUDemand);
        }
    }

    public double getRemaining(){
        return this.currentTask.getRemainingDuration();
    }
    public void updateLoad(int load){
       if(currentTask != null)
         currentTask.cancel();
       setBound(load);
       resume();
    }

    public void setBound(int load) {
        this.load = load;
    }
}
