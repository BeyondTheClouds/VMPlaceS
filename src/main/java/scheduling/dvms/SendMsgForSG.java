package scheduling.dvms;

import org.simgrid.msg.HostFailureException;
import org.simgrid.msg.Task;
import org.simgrid.msg.TimeoutException;
import org.simgrid.msg.TransferFailureException;


public class SendMsgForSG extends Task {
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variable
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	static private int ARBITRARY_MSG_SIZE = 1024; 
	//Message to send to the destination node
	private final Object message;
	private final String sendBox; 
	private final String replyBox; 


	public SendMsgForSG(Object message, String sendBox , String replyBox) {
		super("",0,ARBITRARY_MSG_SIZE); 
		this.message = message ;
		this.sendBox = sendBox ;
		this.replyBox = replyBox ;
	}
	
	public Object getMessage(){
		return message; 
	}
	public String getReplyBox(){
		return replyBox; 
	}

	public String getSendBox() {
		return this.sendBox; 
	}

	public void send() {
//		try {
			this.dsend(this.getSendBox());
//		} catch (TransferFailureException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (HostFailureException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (TimeoutException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
	}
}
