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
import scala.xml.Xhtml;
import scheduling.Scheduler;
import scheduling.SchedulerRes;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
    public Model sourceModel;

    /**
     * Map to link BtrPlace nodes ids to XHosts
     */
    private Map<Integer, String> nodesMap;

    /**
     * Map to link BtrPlace vm ids to XVMs
     */
    private Map<Integer, String> vmMap;

    /**
     * The BtrPlace scheduler
     */
    private ChocoScheduler btrSolver;
    private int loopID;

    public BtrPlaceRP(Collection<XHost> xHosts, int loopID) {
        this.loopID = loopID;
        this.btrSolver = new DefaultChocoScheduler();
        this.nodesMap = new HashMap<>();
        this.vmMap = new HashMap<>();
        this.sourceModel = this.extractModel(xHosts);

    }

    public BtrPlaceRP(Collection<XHost> xHosts) {
        this(xHosts, new Random().nextInt());
    }

    /**
     * Creates a Model for BtrPlace
     * @param xHosts Collection of Xhosts declared as hosting nodes and that are turned on
     * @return A model representing the infrastructure
     */
    public Model extractModel(Collection<XHost> xHosts) {

        Model model = new DefaultModel();
        Mapping mapping = model.getMapping();

        // Creation of a view for defining CPU & Memory resources
        ShareableResource rcCPU = new ShareableResource("cpu", SimulatorProperties.DEFAULT_CPU_CAPACITY, 0);
        ShareableResource rcMem = new ShareableResource("mem", SimulatorProperties.DEFAULT_MEMORY_TOTAL, 0);

        // Add nodes
        for (XHost tmpH : xHosts) {
            // Consider only hosts that are turned on
            if (tmpH.isOff()) {
                System.err.println("WTF, you are asking me to analyze a dead node (" + tmpH.getName() + ")");
            }

            // Creates a physical node
            Node n = model.newNode();
            this.nodesMap.put(n.id(), tmpH.getName());

            // Ajout de la machine physique au mapping
            mapping.addOnlineNode(n);

            // Node's resources are explicitly set
            rcCPU.setCapacity(n, tmpH.getCPUCapacity());
            rcMem.setCapacity(n, tmpH.getMemSize());


            // Declare running VMs mapping
            for (XVM tmpVM : tmpH.getRunnings()) {
                VM v = model.newVM();
                mapping.addRunningVM(v, n);
                this.vmMap.put(v.id(), tmpVM.getName());
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
