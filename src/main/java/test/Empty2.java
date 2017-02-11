/**
 * Copyright 2012-2013-2014. The SimGrid Team. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the license (GNU LGPL) which comes with this package.
 *
 * This class aims at controlling the interactions between the different components of the injector simulator.
 * It is mainly composed of static methods. Although it is rather ugly, this is the direct way to make a kind of
 * singleton ;)
 *
 * @author adrien.lebre@inria.fr
 * @contributor jsimao@cc.isel.ipl.pt
 */

package test;


import injector.FaultEvent;
import injector.InjectorEvent;
import injector.LoadEvent;
import injector.VMSuspendResumeEvent;
import org.simgrid.msg.Host;
import org.simgrid.msg.MsgException;
import org.simgrid.msg.Process;

import java.util.Deque;


/**
 * Created with IntelliJ IDEA.
 * User: alebre
 * Date: 14/01/14
 * Time: 10:49
 * To change this template use File | Settings | File Templates.
 */
public class Empty2 extends Process{

    private Deque<InjectorEvent> evtQueue = null ;
    private Deque<LoadEvent> loadQueue = null ;
    private Deque<FaultEvent> faultQueue = null ;
    private Deque<VMSuspendResumeEvent> vmSuspendResumeQueue = null ;

    public Empty2(Host host, String name){
        super(host, name);

    }
    @Override
    public void main(String[] strings) throws MsgException {

    }

}
