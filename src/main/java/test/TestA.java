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
        for(int i = 0; i < ITERATIONS; i++) {
            Task t = new Task("task-" + i, getHost().getSpeed() * 100, 0);
            System.out.println("A: about to execute");
            t.execute();
            System.out.println("A: done");
        }

        System.out.println("A: about to sleep");
        waitFor(400);
        System.out.println("A: I'm dying...");
    }
}
