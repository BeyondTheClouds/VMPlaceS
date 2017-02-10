package test;

import org.simgrid.msg.Msg;
import org.simgrid.msg.NativeException;
import org.simgrid.msg.Process;

import java.util.Date;


public class Main {
    public static void main(String[] args) throws NativeException {
        Msg.energyInit();
        Msg.init(args);

        /* construct the platform and deploy the application */
        Msg.createEnvironment("config/cluster_platform.xml");
        Msg.deployApplication("config/test_deploy.xml");

	    /*  execute the simulation. */
        Msg.info("Launcher: begin Msg.run()" + new Date().toString());

        Msg.run();

        Msg.info("Launcher: end of Msg.run()" + new Date().toString());
        Msg.info("End of run");

        Process.killAll(-1);
    }
}
