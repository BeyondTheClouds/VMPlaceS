package configuration;

import org.simgrid.msg.Host;
import org.simgrid.msg.Msg;
import org.simgrid.msg.Process;
import trace.Trace;
import simulation.SimulatorManager;
import java.util.Locale;

/**
 * Created by alebre on 25/07/16.
 */
public class AsyncProcess extends Process{

    private boolean completionOk = true;
    private String vmName;
    private String sourceName;
    private String destName;

    public AsyncProcess(Host host, String procName, String args[]){
        super(host, procName , args);
        vmName = args[0];
        sourceName = args[1];
        destName = args[2];
    }
    public boolean getResult(){
        return completionOk ;
    }

    public void main(String[] args) {
        XHost destHost = null;
        XHost sourceHost = null;
        try {
            sourceHost = SimulatorManager.getXHostByName(args[1]);
            destHost = SimulatorManager.getXHostByName(args[2]);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("You are trying to migrate from/to a non existing node");
        }

        if (destHost != null) {
            if (!sourceHost.isOff()) {

                double timeStartingMigration = Msg.getClock();

                Trace.hostPushState(vmName, "SERVICE", "migrate", String.format("{\"vm_name\": \"%s\", \"from\": \"%s\", \"to\": \"%s\"}", vmName, sourceName, destName));
                int res = sourceHost.migrate(args[0], destHost);
                // TODO, we should record the res of the migration operation in order to count for instance how many times a migration crashes ?
                // To this aim, please extend the hostPopState API to add meta data information
                Trace.hostPopState(vmName, "SERVICE", String.format("{\"vm_name\": \"%s\", \"state\": %d}", vmName, res));
                double migrationDuration = Msg.getClock() - timeStartingMigration;

                if (res == 0) {
                    Msg.info("End of migration of VM " + args[0] + " from " + args[1] + " to " + args[2]);

                    if (!destHost.isViable()) {
                        Msg.info("ARTIFICIAL VIOLATION ON " + destHost.getName() + "\n");
                        // If Trace.hostGetState(destHost.getName(), "PM").equals("normal")
                        Trace.hostSetState(destHost.getName(), "PM", "violation-out");
                    }
                    if (sourceHost.isViable()) {
                        Msg.info("END OF VIOLATION ON " + sourceHost.getName() + "\n");
                        Trace.hostSetState(sourceHost.getName(), "PM", "normal");
                    }

                                    /* Export that the migration has finished */
                    Trace.hostSetState(vmName, "migration", "finished", String.format(Locale.US, "{\"vm_name\": \"%s\", \"from\": \"%s\", \"to\": \"%s\", \"duration\": %f}", vmName, sourceName, destName, migrationDuration));
                    Trace.hostPopState(vmName, "migration");

                    // Patch to handle postponed supsend that may have been requested during the migration.
                    if (SimulatorManager.sgVMsToSuspend.remove(args[0]) != null) { // The VM has been marked to be suspended, so do it
                        Msg.info("The VM " + args[0] + "has been marked to be suspended after migration");
                        SimulatorManager.suspendVM(vmName, destName);
                    }

                } else {

                    Trace.hostSetState(vmName, "migration", "failed", String.format(Locale.US, "{\"vm_name\": \"%s\", \"from\": \"%s\", \"to\": \"%s\", \"duration\": %f}", vmName, sourceName, destName, migrationDuration));
                    Trace.hostPopState(vmName, "migration");

                    Msg.info("Something was wrong during the migration of  " + args[0] + " from " + args[1] + " to " + args[2]);
                    Msg.info("Reconfiguration plan cannot be completely applied so abort it");
                    completionOk = false;
                }

            }
        }

    }
}
