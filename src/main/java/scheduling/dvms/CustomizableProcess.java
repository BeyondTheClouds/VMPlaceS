package scheduling.dvms;

import org.simgrid.msg.Host;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;

public abstract class CustomizableProcess extends Process {
	
	public CustomizableProcess(){
		super(Host.currentHost(), "CustomizableProcess-"+Math.random()); 
	}
	
	public abstract void call(); 
	
	
	@Override
	public void main(String[] arg0) throws MsgException {
		call(); 
	}
}
