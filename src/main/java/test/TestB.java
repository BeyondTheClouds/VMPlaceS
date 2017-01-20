package test;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;

import java.util.Random;

public class TestB extends Process {
    private static final int ITERATIONS = 50;

    private Random rand;

    public TestB(Host host, String name, String[] args) throws HostNotFoundException {
        super(host, name, args);
        rand = new Random();
    }

    @Override
    public void main(String[] strings) throws MsgException {
        for(int i = 0; i < ITERATIONS; i++) {
            Task t = new Task("task-" + i, getHost().getSpeed() * rand.nextInt(500), 0);
            System.out.println("A: about to execute");
            t.execute();
            System.out.println(String.format("B: about to go to sleep (%d)", i));
            waitFor(500);
            System.out.println("B: woke up");
        }

        System.out.println("B: last sleep");
        waitFor(800);
        System.out.println("B: I'm dying...");
    }
}
