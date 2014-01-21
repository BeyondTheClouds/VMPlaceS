package Injector;

/**
 * Created with IntelliJ IDEA.
 * User: alebre
 * Date: 07/10/13
 * Time: 14:42
 * To change this template use File | Settings | File Templates.
 */

import configuration.XHost;
import simulation.SimulatorManager;

public class FaultEvent implements InjectorEvent{

    private long id ;
    private double time;
    private XHost host;
    private boolean state; //off = 0 ; on = 1

    public FaultEvent(long id, double time, XHost h, boolean state) {
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

    public XHost getHost(){
        return this.host;
    }

    public void play(){
        if(this.state){
            SimulatorManager.turnOn(this.host);

        } else {
            SimulatorManager.turnOff(this.host);
        }
    }

    public String toString(){
        return this.getTime()+"/"+this.getHost().getName()+"/"+this.state;
    }
}
