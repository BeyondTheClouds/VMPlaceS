package scheduling.entropyBased.btrplace.comparators;

import org.btrplace.plan.event.Action;
import org.btrplace.plan.event.MigrateVM;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Map;

/**
 * Comparateur d'action "MigrateVM" des BtrPlace selon le nom des VMs.
 *
 * @author Hadrien Gerard
 * @version 1.0
 */
public class VmNamesBasedActionComparator implements Comparator<Action>, Serializable {

    protected Map<Integer, String> names;

    /**
     * Constructeur de comparateur.
     * @param names les noms des VMs associés à chaques Id.
     */
    public VmNamesBasedActionComparator(Map<Integer, String> names) {
        this.names = names;
    }

    @Override
    public int compare(Action a1, Action a2) {
        if ((a1 instanceof MigrateVM) && (a2 instanceof MigrateVM)) {
            String vm1Name = names.get(((MigrateVM) a1).getVM().id());
            String vm2Name = names.get(((MigrateVM) a2).getVM().id());
            return vm1Name.compareTo(vm2Name);
        }
        return 0;
    }

    public Map<Integer, String> getNames() {
        return names;
    }

    public void setNames(Map<Integer, String> names) {
        this.names = names;
    }
}
