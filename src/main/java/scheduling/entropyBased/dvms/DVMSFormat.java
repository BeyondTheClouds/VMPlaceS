package scheduling.entropyBased.dvms;

import configuration.XVM;


import dvms.configuration.DVMSManagedElementSet;
import dvms.configuration.DVMSVirtualMachine;
import entropy.configuration.Node;
import entropy.configuration.SimpleNode;
import simulation.SimulatorManager;

public class DVMSFormat {

	/**
	 * Return the VMs (in the DVMS format) hosted by a particular node
	 * @return
	 */
	public static DVMSManagedElementSet<DVMSVirtualMachine> getHostedVMs(String hostName) {
		
		DVMSManagedElementSet<DVMSVirtualMachine> vms = new DVMSManagedElementSet<DVMSVirtualMachine>();

		for (XVM vm: SimulatorManager.getXHostByName(hostName).getRunnings())
            vms.add(new DVMSVirtualMachine(vm.getName(), vm.getCoreNumber(), vm.getCPUDemand(), vm.getMemSize()));
		return vms ;
	}

	public static Node convertToSimpleNode(DVMSNode tmpNode) {
		// TODO Auto-generated method stub
		return new SimpleNode(tmpNode.getName(), tmpNode.getNbOfCPUs(), 
							tmpNode.getCPUCapacity(), tmpNode.getMemoryTotal());
	}
	
	}
