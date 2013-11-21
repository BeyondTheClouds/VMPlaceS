package simulation;

/**
 * Created with IntelliJ IDEA.
 * User: alebre
 * Date: 07/10/13
 * Time: 14:42
 * To change this template use File | Settings | File Templates.
 */

import configuration.ConfigurationManager;
import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;

public class FaultEvent implements InjectorEvent{

    private long id ;
    private double time;
    private Host host;
    private boolean state; //off = 0 ; on = 1

    public FaultEvent(long id, double time, Host h, boolean state) {
        this.id=id;
        this.time= time;
        this.host=h;
        this.state = state ;

    }

    public long getId(){
        return this.id;
    }
    public double getTime() {
        return this.time;
    }

    public Host getHost(){
        return this.host;
    }

    public void play(){
        if(this.state){
            ConfigurationManager.turnOn(this.host);

        } else {
            ConfigurationManager.turnOff(this.host);
        }
    }

    public String toString(){
        return this.getTime()+"/"+this.getHost().getName()+"/"+this.state;
    }
}
