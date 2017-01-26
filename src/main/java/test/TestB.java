package test;

import org.simgrid.msg.*;
import org.simgrid.msg.Process;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class TestB extends Process {
    private static final int ITERATIONS = 50;

    private Random rand;

    public TestB(Host host, String name, String[] args) throws HostNotFoundException {
        super(host, name, args);
        rand = new Random(42);
    }

    @Override
    public void main(String[] strings) throws MsgException {
        for(int i = 0; i < ITERATIONS; i++) {
            Task t = new Task("task-" + i, getHost().getSpeed() * rand.nextInt(500), 0);
            Msg.info("A: about to execute");
            t.execute();
            Msg.info(String.format("B: about to go to sleep (%d)", i));
            waitFor(500);
            Msg.info("B: woke up");
        }

        Msg.info("B: last sleep");
        waitFor(800);
        Msg.info("B: I'm dying...");
    }
}
