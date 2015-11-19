package scheduling.entropyBased.btrplace.configuration;

import org.btrplace.model.DefaultModel;
import org.btrplace.model.Instance;
import org.btrplace.model.Model;
import org.btrplace.model.constraint.OptConstraint;
import org.btrplace.model.constraint.SatConstraint;

import java.util.*;

/**
 * Classe de configuration (modèle et contraintes)pour l'implémentation de BtrPlace
 *
 * @author Hadrien Gerard
 * @version 1.0
 */
public class Configuration {

    protected Map<Integer, String> vmNames;

    protected Map<Integer, String> nodeNames;

    protected Model model;

    protected List<SatConstraint> constraints;

    protected OptConstraint optConstraint;


    public Configuration () {
        this(new DefaultModel(), Collections.<SatConstraint>emptyList(), null);
    }

    public Configuration(Model model, Collection<SatConstraint> constraints, OptConstraint optConstraint) {
        this.model = model;
        this.constraints = new ArrayList<SatConstraint>(constraints);
        this.optConstraint = optConstraint;
        this.vmNames = new HashMap<Integer, String>();
        this.nodeNames = new HashMap<Integer, String>();
    }

    public Instance asInstance() {
        return new Instance(model, constraints, optConstraint);
    }

    public Model getModel() {
        return this.model;
    }

    public Collection<SatConstraint> getSatConstraints() {
        return this.constraints;
    }

    public OptConstraint getOptConstraint() {
        return this.optConstraint;
    }

    public Map<Integer, String> getVmNames() {
        return vmNames;
    }

    public void setVmNames(Map<Integer, String> vmNames) {
        this.vmNames = vmNames;
    }

    public String getVmName(int id) {
        return vmNames.get(id);
    }

    public void setVmName(int id, String name) {
        this.vmNames.put(id, name);
    }

    public Map<Integer, String> getNodeNames() {
        return nodeNames;
    }

    public void setNodeNames(Map<Integer, String> nodeNames) {
        this.nodeNames = nodeNames;
    }

    public String getNodeName(int id) {
        return this.nodeNames.get(id);
    }

    public void setNodeName(int id, String name) {
        this.nodeNames.put(id, name);
    }

}
