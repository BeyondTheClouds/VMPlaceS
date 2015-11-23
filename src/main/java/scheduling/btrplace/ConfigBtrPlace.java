package scheduling.btrplace;

import org.btrplace.model.Model;
import org.btrplace.model.Node;
import org.btrplace.model.VM;
import org.btrplace.model.constraint.SatConstraint;

import java.util.List;
import java.util.Map;

/**
 * Created by Maxime Perocheau & Joris Pichard on 29/10/15.
 */
public class ConfigBtrPlace {

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Instance variables
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    //The model
    private Model model;

    //The contraints
    private List<SatConstraint> cstrs;

    //The VM's name
    private Map<VM, String> vmNames;

    //The node's name
    private Map<Node, String> nodeNames;

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Constructor
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public ConfigBtrPlace(Model model, List<SatConstraint> cstrs, Map<VM, String> vmNames, Map<Node, String> nodeNames) {
        this.model = model;
        this.cstrs = cstrs;
        this.vmNames = vmNames;
        this.nodeNames = nodeNames;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Accessors
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Map<Node, String> getNodeNames() {
        return nodeNames;
    }

    public void setNodeNames(Map<Node, String> nodeNames) {
        this.nodeNames = nodeNames;
    }

    public Map<VM, String> getVmNames() {
        return vmNames;
    }

    public void setVmNames(Map<VM, String> vmNames) {
        this.vmNames = vmNames;
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
    }

    public List<SatConstraint> getCstrs() {
        return cstrs;
    }

    public void setCstrs(List<SatConstraint> cstrs) {
        this.cstrs = cstrs;
    }

}
