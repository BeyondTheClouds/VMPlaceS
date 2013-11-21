package scheduling.dvms2;

import org.simgrid.msg.Host;
import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.Task;
import org.simgrid.msg.TimeoutException;
import org.simgrid.msg.TransferFailureException;

import org.simgrid.msg.Process;

import scheduling.dvms2.MsgForSG;

public class SGActor extends Process {


    public SGNodeRef ref = null;

    public SGActor(SGNodeRef ref) {
        this.ref = ref;
    }

    public SGNodeRef self() {
        return ref;
    }

	public void send(SGNodeRef node, Object message){
		MsgForSG msg = new MsgForSG(message, 
				node+"", ref.getName() ,null);
		msg.send();
	} 
	public void forward(SGNodeRef dest, SGNodeRef origin, Object message){
		MsgForSG msg = new MsgForSG(message, dest+"",
				origin+"",null);
		msg.send();
	}
	public Object ask(SGNodeRef node, Object message){
		MsgForSG msg = new MsgForSG(message, 
				node+"", node+"",Host.currentHost().getName()+":"+ Math.random());
		msg.send(); 
		MsgForSG reply;
		try {
			reply = (MsgForSG) Task.receive(msg.getReplyBox());
			return reply.getMessage();
		} catch (TransferFailureException e) {
			e.printStackTrace();
		} catch (HostFailureException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}
		return null; 
	}

    public void main(String[] args) {

        try {

            while(true) {

                waitFor(0.01);
            }

        } catch(Exception e) {

            e.printStackTrace();
        }
    }
}	

