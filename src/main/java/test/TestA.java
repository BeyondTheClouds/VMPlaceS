package test;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;

public class TestA extends Process {
    private static final int ITERATIONS = 3000;

    public TestA(Host host, String name, String[] args) throws HostNotFoundException {
        super(host, name, args);
    }

    @Override
    public void main(String[] strings) throws MsgException {
        waitFor(5000);

        /*
        for(int i = 0; i < ITERATIONS; i++) {
            Task t = new Task("task-" + i, getHost().getSpeed() * 100, 0);
            Msg.info("A: about to execute");
            t.execute();
            Msg.info("A: done");
        }
        */

        Msg.info("A: about to sleep");
        waitFor(400);
        Msg.info("A: I'm dying...");
    }
}
