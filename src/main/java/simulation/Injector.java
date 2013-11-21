package simulation;
import org.simgrid.msg.Host;
import org.simgrid.msg.HostNotFoundException;
import org.simgrid.msg.Msg;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.NativeException;
import org.simgrid.msg.Process;
import org.simgrid.trace.Trace;

import scheduling.EntropyProperties;


public class Injector extends Process {
	
	Injector(Host host, String name, String[] args) throws HostNotFoundException, NativeException  {
	      super(host, name, args);
	}

    /* Args : nbPMs nbVMs eventFile */
	public void main(String[] args) throws MsgException {

        for(InjectorEvent evt: Main.getEvtQueue()){
            System.out.println(evt);
        }

		/* Initialization is done in Main */
   
		if(!Main.getCurrentConfig().isViable()){
		   System.err.println("Initial Configuration should be viable !");
    	   System.exit(1);
       }
		
	   Trace.hostVariableSet("node0", "NB_MIG", 0); 
	   Trace.hostVariableSet("node0", "NB_MC", 0); 
	   
	   InjectorEvent evt = nextEvent();
	   while(evt!=null){
		   if(evt.getTime() - Msg.getClock()>0)
			   waitFor(evt.getTime() - Msg.getClock());
	       evt.play();
	       evt=nextEvent();
       }
	  Msg.info("End of Injection");   
	  Main.setEndOfInjection();
	  
		// Wait for termination of On going scheduling
		waitFor(EntropyProperties.getEntropyPlanTimeout());
    }

	private InjectorEvent nextEvent() {
		return Main.getEvtQueue().pollFirst();
	}
}