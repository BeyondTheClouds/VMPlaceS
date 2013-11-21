package simulation;

import configuration.ConfigurationManager;
import configuration.XVM;
import org.simgrid.msg.Msg;

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
        Msg.info("Event " + getId() + ": VM " + getVm().getName() + "load becomes " + getCPULoad());
        ConfigurationManager.updateVM(this.getVm(), this.getCPULoad());
    }

}
