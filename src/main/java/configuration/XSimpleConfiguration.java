package configuration;

import entropy.configuration.*;

import java.util.Collections;
import java.util.Comparator;

/**
 * Created with IntelliJ IDEA.
 * User: alebre
 * Date: 08/10/13
 * Time: 11:09
 * To change this template use File | Settings | File Templates.
 */
public class XSimpleConfiguration extends SimpleConfiguration{


	/**
	 * check whether a pm is viable or not (currently only for the CPU dimension)
	 * @param pm Node, the pm to test
	 * @return boolean true if the PM is non viable (i.e. overloaded from the CPU viewpoint)
	 */
	public boolean isViable(Node pm){
		return (this.load(pm)<=pm.getCPUCapacity());
	}

	public double load(Node pm){
		double cons=0;
		for (VirtualMachine v: this.getRunnings(pm))
			cons+=v.getCPUDemand();
		return cons;
	}

	/**
	 * Return the average load of the configuration
	 * @return
	 */
	public double load() {
        double cons=0;
        double tmpLoad = 0 ;
        for(Node pm: this.getAllNodes()){
            tmpLoad = load(pm)*100/pm.getCPUCapacity();
            cons+= tmpLoad ;
        }
        return cons/this.getAllNodes().size();
	}

	public boolean isViable() {
		for(Node node: this.getAllNodes()){
			if(!isViable(node))
				return false;
		}
		return true;
	}

	public Node getNodeByName(String name){
		Node tmpNode=null;
		for (Node n: this.getAllNodes()){
			if (n.getName().equals(name)){
				tmpNode=n;
				break ;
			}
		}
		return tmpNode;
	}

    public VirtualMachine getVMByName(String name) {
        for(VirtualMachine vm: this.getAllVirtualMachines()){
            if(vm.getName().equals(name))
                return vm;
        }
        return null;
    }

    // Just a way to pass the same configuration to Entropy (the VMs are sorted in asc order on a particular PM)
    // For the moment, only the running VMs are reordered
    // Strange: getAllVirtualMachines() does not return the same VMs as getRunnings(n). I mean the Virtual machine does not integrate the new load in getRunnings(n)
    public XSimpleConfiguration cloneSorted() {
        final XSimpleConfiguration c = new XSimpleConfiguration();
        for (Node n : getOfflines()) {
            c.addOffline(n.clone());
        }

        for (VirtualMachine vm : getWaitings()) {
            c.addWaiting(vm.clone());
        }

        for (Node n : getOnlines()) {
            c.addOnline(n.clone());

            // Retrieve the set and sort it
            ManagedElementSet<VirtualMachine> tmpSet = new SimpleManagedElementSet<VirtualMachine>();
            tmpSet.addAll(getRunnings(n));
            Collections.sort(tmpSet, new Comparator<VirtualMachine>() {
                @Override
                public int compare(VirtualMachine v1, VirtualMachine v2) {
                    return Integer.parseInt(v1.getName().split("-")[1]) - Integer.parseInt(v2.getName().split("-")[1]);
                }
            });

            for (VirtualMachine vm : tmpSet) {
                c.setRunOn(vm.clone(), n);
            }

            for (VirtualMachine vm : getSleepings(n)) {
                c.setSleepOn(vm.clone(), n);
            }
        }
        return c;
    }
    public XSimpleConfiguration cloneSorted2() {
        final XSimpleConfiguration c = new XSimpleConfiguration();
        for (Node n : getOfflines())
            c.addOffline(n.clone());

        for (Node n : getOnlines())
            c.addOnline(n.clone());

        for (VirtualMachine vm: getAllVirtualMachines()){
            c.setRunOn(vm, getLocation(vm));
        }

        return c;
    }

}
