package test;

import org.simgrid.msg.Msg;
import org.simgrid.msg.Process;

import java.util.Date;


public class Main {
    public static void main(String[] args) {
        Msg.energyInit();
        Msg.init(args);

        /* construct the platform and deploy the application */
        Msg.createEnvironment("config/test_platform.xml");
        Msg.deployApplication("config/test_deploy.xml");

	    /*  execute the simulation. */
        System.out.println("Launcher: begin Msg.run()" + new Date().toString());

        Msg.run();

        System.out.println("Launcher: end of Msg.run()" + new Date().toString());
        Msg.info("End of run");

        Process.killAll(-1);
        System.exit(0);
    }
}
