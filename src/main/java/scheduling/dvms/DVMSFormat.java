package scheduling.dvms;

import org.simgrid.msg.Msg;

import dvms.configuration.DVMSManagedElementSet;
import dvms.configuration.DVMSVirtualMachine;
import entropy.configuration.Configuration;
import entropy.configuration.Node;
import entropy.configuration.SimpleNode;
import entropy.configuration.VirtualMachine;

public class DVMSFormat {

	/**
	 * Return the VMs hosted by a particular node
	 * @param sgConfig the current configuration managed by SG
	 * @return
	 */
	public static DVMSManagedElementSet<DVMSVirtualMachine> getHostedVMs(
			Configuration sgConfig, DVMSNode node) {
		
		DVMSManagedElementSet<DVMSVirtualMachine> vms = new DVMSManagedElementSet<DVMSVirtualMachine>();
		if(sgConfig==null)
			Msg.info(node.getName() + "sgConfig null"); 
		if(sgConfig.getRunnings(new SimpleNode(node.getName()))==null)
			Msg.info(node.getName() + "getRunnings null"); 
		
		for (VirtualMachine vm: sgConfig.getRunnings(new SimpleNode(node.getName())))
			vms.add(new DVMSVirtualMachine(vm.getName(), vm.getNbOfCPUs(), vm.getCPUDemand(), vm.getMemoryDemand()));
		return vms ;
	}

	public static Node convertToSimpleNode(DVMSNode tmpNode) {
		// TODO Auto-generated method stub
		return new SimpleNode(tmpNode.getName(), tmpNode.getNbOfCPUs(), 
							tmpNode.getCPUCapacity(), tmpNode.getMemoryTotal());
	}
	
	}
