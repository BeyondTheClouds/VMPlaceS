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


import org.simgrid.msg.*;
import org.simgrid.msg.Process;


/**
 * Created with IntelliJ IDEA.
 * User: alebre
 * Date: 14/01/14
 * Time: 10:49
 * To change this template use File | Settings | File Templates.
 */
public class Empty extends Process{

    public Empty(Host host, String name, String[] args){
        super(host, name, args);

    }
    @Override
    public void main(String[] strings) throws MsgException {


        Empty2 empty2=new Empty2(Host.getByName("node0"),"empty2");
        empty2.start();

        waitFor(100);

    }

}
