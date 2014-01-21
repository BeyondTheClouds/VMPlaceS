package Injector;

import configuration.XVM;
import simulation.SimulatorManager;

public class LoadEvent implements InjectorEvent {

	private long id ;
	private double time;
	private XVM vm;
	private int newCPULoad;
	
	public LoadEvent(long id, double time, XVM vm, int newCPULoad) {
		this.id=id; 
		this.time= time;
		this.vm=vm;
		this.newCPULoad = newCPULoad;
	}
	
	public long getId(){
		return this.id; 
	}
	public double getTime() {
		return this.time;
	}
	
	public XVM getVm(){
	  return this.vm;
	}
    public int getCPULoad(){
      return this.newCPULoad;
    }
	
    public String toString(){
    	return this.getTime()+"/"+this.getVm().getName()+"/"+this.getCPULoad();
    }

    public void play(){
        SimulatorManager.updateVM(this.getVm(), this.getCPULoad());
    }

}
