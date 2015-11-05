package scheduling.btrplace;

import configuration.SimulatorProperties;
import configuration.XHost;
import configuration.XVM;
import org.btrplace.model.DefaultModel;
import org.btrplace.model.Mapping;
import org.btrplace.model.Model;
import org.btrplace.model.VM;
import org.btrplace.model.Node;
import org.btrplace.model.view.ShareableResource;
import org.btrplace.scheduler.choco.ChocoScheduler;
import org.btrplace.scheduler.choco.DefaultChocoScheduler;
import scheduling.Scheduler;
import scheduling.SchedulerRes;

import java.util.Collection;
import java.util.Random;

/**
 * @author Adrian Fraisse
 *
 * Implementation of the Scheduler interface using the BtrPlace API
 */
public class BtrPlaceRP implements Scheduler {

    /**
     * The initial configuration
     */
    private Model sourceModel;

    /**
     * The BtrPlace scheduler
     */
    private ChocoScheduler btrSolver;
    private int loopID;

    public BtrPlaceRP(Model sourceModel, int loopID) {
        this.sourceModel = sourceModel;
        this.loopID = loopID;
        this.btrSolver = new DefaultChocoScheduler();

    }

    public BtrPlaceRP(Model sourceModel) {
        this(sourceModel, new Random().nextInt());
    }

    /**
     * Creates a Model for BtrPlace
     * @param xHosts Collection of Xhosts declared as hosting nodes and that are turned on
     * @return A model representing the infrastructure
     */
    public static Model ExtractModel(Collection<XHost> xHosts) {

        Model model = new DefaultModel();
        Mapping mapping = model.getMapping();

        // Creation of a view for defining CPU & Memory resources
        ShareableResource rcCPU = new ShareableResource("cpu", SimulatorProperties.DEFAULT_CPU_CAPACITY, 0);
        ShareableResource rcMem = new ShareableResource("mem", SimulatorProperties.DEFAULT_MEMORY_TOTAL, 0);

        // Add nodes
        for (XHost tmpH:xHosts){
            // Consider only hosts that are turned on
            if (tmpH.isOff()) {
                System.err.println("WTF, you are asking me to analyze a dead node (" + tmpH.getName() + ")");
            }

            // Creates a physical node
            Node n = model.newNode();

            // Ajout de la machine physique au mapping
            mapping.addOnlineNode(n);

            // Node's resources are explicitly set
            rcCPU.setCapacity(n, tmpH.getCPUCapacity());
            rcMem.setCapacity(n, tmpH.getMemSize());


            // Declare running VMs mapping
            for (XVM tmpVM : tmpH.getRunnings()) {
                VM v = model.newVM();
                mapping.addRunningVM(v, n);
                rcCPU.setConsumption(v, (int) tmpVM.getCPUDemand());
                rcMem.setConsumption(v, tmpVM.getMemSize());
            }

        }

        model.attach(rcCPU);
        model.attach(rcMem);

        return model;
    }

    public ComputingState computeReconfigurationPlan() {
        return null;
    }

    @Override
    public SchedulerRes checkAndReconfigure(Collection<XHost> hostsToCheck) {
        return null;
    }

    public void applyReconfigurationPlan() {

    }

}
