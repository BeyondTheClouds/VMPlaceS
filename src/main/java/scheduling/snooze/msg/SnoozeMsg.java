package scheduling.snooze.msg;


import org.simgrid.msg.Task;

/**
 * Created by sudholt on 24/06/2014.
 */
public class SnoozeMsg extends Task {
    // copied from scheduling.dvms2.MSGForSG

    static private int ARBITRARY_MSG_SIZE = 1024;
    //Message to send to the destination node
    private final Object message;
    private final String sendBox;
    private final String replyBox;
    private final String origin;

    public SnoozeMsg(Object message, String sendBox , String origin, String replyBox) {
        super("",0,ARBITRARY_MSG_SIZE);
        this.message = message ;
        this.sendBox = sendBox ;
        this.replyBox = replyBox ;
        this.origin = origin;
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
    public String getOrigin() {
        return this.origin;
    }

    public void send() {

//        try {
//            this.send(this.getSendBox());
//        } catch (TransferFailureException e) {
//            e.printStackTrace();
//        } catch (HostFailureException e) {
//            e.printStackTrace();
//        } catch (TimeoutException e) {
//            e.printStackTrace();
//        }
        this.isend(this.getSendBox());
//        this.dsend(this.getSendBox());
    }
}

