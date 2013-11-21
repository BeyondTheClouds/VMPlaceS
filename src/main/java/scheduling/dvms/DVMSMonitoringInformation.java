package scheduling.dvms;

import java.util.HashMap;
import java.util.Map;

import dvms.clientserver.Server;
import dvms.configuration.DVMSManagedElementSet;


//Represents an object which keeps track of monitoring information
public class DVMSMonitoringInformation {
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Instance variables
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//The nodes on which we have monitoring information
	private final Map<Server, DVMSNode> monitoringInformation;
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructor
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public DVMSMonitoringInformation(){
		monitoringInformation = new HashMap<Server, DVMSNode>();
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Other methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public synchronized void clear(){
		monitoringInformation.clear();
	}
	
	//Update monitoring information related to a node
	public synchronized DVMSNode updateMonitoringInformation(DVMSNode node){
		return monitoringInformation.put(node.getAssociatedServer(), node);
	}
	
	//Remove monitoring information related to a node
	@Deprecated
	public synchronized void removeMonitoringInformation(Server node){
		monitoringInformation.remove(node);
	}
	
	//Get monitoring information
	public synchronized DVMSManagedElementSet<DVMSNode> getMonitoringInformation(){
		DVMSManagedElementSet<DVMSNode> result = new DVMSManagedElementSet<DVMSNode>();
		result.addAll(monitoringInformation.values());
		return result;
	}
}
