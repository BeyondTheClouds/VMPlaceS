package scheduling.hubis;

import java.text.SimpleDateFormat;


import scheduling.entropy.AbstractScheduler;

import entropy.configuration.Configuration;
import entropy.configuration.ManagedElementSet;
import entropy.configuration.Node;
import entropy.configuration.SimpleManagedElementSet;
import entropy.configuration.VirtualMachine;
import entropy.plan.TimedReconfigurationPlan;

import entropy.plan.action.Migration;


//An implementation of scheduler which is based on the dynamic consolidation algorithm of Entropy
public class Hubis extends AbstractScheduler {

	//<BEGIN> Code from DynamicConsolidation
	public static final String BASE_LOG_DIR = "logs/";

	/**
	 * Prefix for source configuration log files.
	 */
	public static final String SOURCE_CONF_SUFFIX = "source";

	/**
	 * Prefix for the result configuration log files.
	 */
	public static final String RESULT_CONF_SUFFIX = "result";

	/**
	 * Prefix for the packed configuration log files.
	 */
	public static final String PACKED_CONF_SUFFIX = "packed";

	/**
	 * Prefix for the pure configuration log files.
	 */
	public static final String PURE_CONF_SUFFIX = "pure";

	/**
	 * THe date format for configuration logging.
	 */
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
			"yyyy-MM-dd");

	/**
	 * The hour format for configuration logging.
	 */
	public static final SimpleDateFormat HOUR_FORMAT = new SimpleDateFormat(
			"hh:mm:ss");

	/**
	 * The factory to transform all the actions.
	 */

	//<END> Code from DynamicConsolidation


	//<BEGIN> Code from DynamicConsolidation

	private int nbmouv;
	private int memtotbougee=0;
	private int max=0;
	private int modifpm=0;
	private int nbT=0;
	private int nbS=0;
	private int nbL=0;
	private int nbB=0;
	private ManagedElementSet<Node> UnusedNodes=null;
	private ManagedElementSet<Node> UsedNodes=null;

	public int getnbmouv(){
		return nbmouv;
	}

	public int getmemtotbougee(){
		return memtotbougee;
	}

	public int getmax(){
		return max;
	}

	public int getmodifpm(){
		return modifpm;
	}

	public int getnbT(){
		return nbT;
	}

	public int getnbS(){
		return nbS;
	}
	public int getnbL(){
		return nbL;
	}
	public int getnbB(){
		return nbB;
	}

	public Hubis(Configuration initialConfiguration){
		super(initialConfiguration);
	}

	public TimedReconfigurationPlan getreconfigurationPlan(){
		return reconfigurationPlan;
	}
	private int getUnusedNodes(Configuration c){
		for (Node n : c.getAllNodes())
			if (c.getRunnings(n).size()==0)
				this.UnusedNodes.add(n);
		return 1;
	}

	private int getUsedNodes(Configuration c){
		for (Node n : c.getAllNodes())
			if (c.getRunnings(n).size()>0)
				this.UsedNodes.add(n);
		return 1;
	}


	private int calculrepartvm(Configuration c){
		nbT=0;
		nbS=0;
		nbL=0;
		nbB=0;
		float capa=c.getAllNodes().get(0).getCPUCapacity();
		for (VirtualMachine vm : c.getRunnings()){
			if (vm.getCPUConsumption()>0){
				if (vm.getCPUConsumption()>=2*capa/3)
					nbB+=1;
				else if (vm.getCPUConsumption()>=capa/2)
					nbL+=1;
				else if (vm.getCPUConsumption()>=capa/3)
					nbS+=1;
				else nbT+=1;	
			}
		}
		return 1;
	}

	private int testsurcharge(Configuration c, Node pm){
		int cpu_cons=0;
		ManagedElementSet<VirtualMachine> vms=c.getRunnings(pm);	
		for (VirtualMachine vm : vms){
			cpu_cons+=vm.getCPUDemand();
		}
		if (cpu_cons>pm.getCPUCapacity())
			return 1;
		else return 0;
	}

	private int testsouscharge(Configuration c, Node pm){
		int cpu_cons=0;
		ManagedElementSet<VirtualMachine> vms=c.getRunnings(pm);	
		for (VirtualMachine vm : vms){
			cpu_cons+=vm.getCPUDemand();
		}
		if (cpu_cons<2*pm.getCPUCapacity()/3)
			return 1;
		else return 0;
	}

	private int identifiertypesurchargepm(Configuration c, Node pm){
		int b,l,s,t;
		b=0; s=0; l=0; t=0;
		int capa=pm.getCPUCapacity();
		ManagedElementSet<VirtualMachine> vms=c.getRunnings(pm);	
		for (VirtualMachine vm : vms){
			if (vm.getCPUDemand()>=2*capa/3)
				b++;
			else if (vm.getCPUDemand()>=capa/2)
				l++;
			else if (vm.getCPUDemand()>=capa/3)
				s++;
			else t++;
		}	
		System.out.println("compo pm : "+pm.getName()+" b= "+b+" l= "+l+" s= "+s+" t= "+t);
		//Identifier duquel des 15 differents types de pms est la pm consideree
		if (b>=1){
			if (b>=2)
				//Type BBT*
				return 1;
			if (l>=1){
				if (s>=1)
					//Type BLST*
					return 2;

				else return 3;	//Type BLT*
			}
			else if (s>=1){
				if (s>=2)
					//Type BSST*
					return 4;

				else return 5; //Type BST*
			}
			else return 6; //Type BT*
		}
		else if (l>=1)
			if (l>=2)
				if(s>=1)
					//Type LLST*
					return 7;
				else return 8; // Type LLT*
			else if (s>=1)
				if (s>=2)
					//Type LSST*
					return 9;
				else return 10; //Type LST*
			else return 11; //Type LT*
		else if (s>=1)
			if (s>=2)
				if (s>=3)
					//Type SSST*
					return 12;
				else return 13; //Type SST*
			else return 14; //Type ST*
		else return 15; //Type T*
	}

	private int identifiertypesouschargepm(Configuration c, Node pm){
		int l,s,t;
		l=0; s=0; t=0;
		int capa=pm.getCPUCapacity();
		if (testsouscharge(c,pm)==1){
			ManagedElementSet<VirtualMachine> vms=c.getRunnings(pm);	
			for (VirtualMachine vm : vms){
				if (vm.getCPUDemand()>=capa/2)
					l++;
				else if (vm.getCPUDemand()>=capa/3)
					s++;
				else t++;
			}	
			if (l==1)
				//Type LT*
				return 1;
			else if (s==1)
				//Type ST*
				return 2;
			else return 3;  //Type T*
		}
		else return 0;
	}

	private int getCPUDemand(Configuration c, Node pm) {
		int cons=0;
		for (VirtualMachine v: c.getRunnings(pm))
			cons+=v.getCPUDemand();
		return cons;
	}

	private Node trouverUT(Configuration c, Node pmaeviter)
	{
		int a=0;
		int capa=0;
		for (Node pm : UsedNodes){
			if (capa==0)
				capa=pm.getCPUCapacity();
			if (getCPUDemand(c,pm) < 2*capa/3){
				a=0;
				for (VirtualMachine v : c.getRunnings(pm))
					if (v.getCPUDemand()>capa/3)
					{ 
						a=1;	
						break;
					}
				if (a==0 && pm != pmaeviter)
					return pm;
			}
		}
		Node pm= UnusedNodes.get(0);
		UnusedNodes.remove(0);
		return pm;
	}

	private int traiterT(Configuration c, Node pm)
	{	
		//On fusionne que deux UTs entre elles, pas plus : quand on aura corrigé tout le monde, il restera au plus une UT
		//	System.out.println("intraiterT");
		//System.out.println("avant fusion concernant "+pm.getName());
		Node ut=trouverUT(c,pm);
		//		int nbmouv=0;
		Migration m;
		int capa=pm.getCPUCapacity();
		int a=getCPUDemand(c,pm);
		int b=getCPUDemand(c,ut);
		if (a==0 || b==0)
			return 0;
		int nbut=c.getRunnings(ut).size();
		int nbpm=c.getRunnings(pm).size();
		VirtualMachine vm;
		Node pmfusion;
		if (nbut > nbpm) {
			pmfusion=ut;
			ut=pm;
			pm=pmfusion;
			a=b;
			nbut=nbpm;
		}
		while (a<=2*capa/3 && nbut >=1){
			vm=c.getRunnings(ut).get(0);
			m=new Migration(vm,ut,pm);
			reconfigurationPlan.add(m);
			nbut-=1;
			nbmouv++;
			memtotbougee+=vm.getMemoryDemand();
			a+=vm.getCPUDemand();
		}
		UsedNodes.remove(UsedNodes.indexOf(ut));
		UnusedNodes.add(ut);
		//System.out.println("apres fusion entre "+pm.getName()+" et "+ut.getName());
		return 1;
	}

	private int insertS(Configuration c, VirtualMachine vm, int param)
	{	
		int nbmouvc=0;
		Migration m;
		int capa=UsedNodes.get(0).getCPUCapacity();
		//System.out.println("avant insert "+vm.getName());
		for (Node pm : UsedNodes){
			if (c.getRunnings(pm).size()==1 && pm != c.getLocation(vm))
				for (VirtualMachine v : c.getRunnings(pm))
					if (v.getCPUDemand()>=capa/3 && v.getCPUDemand()<capa/2)
					{
						m=new Migration(vm,c.getLocation(vm),pm);
						reconfigurationPlan.add(m);
						nbmouv++;
						nbmouvc++;
						memtotbougee+=vm.getMemoryDemand();
						break;
					}
		}
		//Si param = 1, on insere a tout prix, sinon, on prefere laisser le S tout seul la ou il est
		if (nbmouvc==0 && param==1){
			m=new Migration(vm,c.getLocation(vm),UnusedNodes.get(0));
			reconfigurationPlan.add(m);
			UnusedNodes.remove(0);
			nbmouv++;
		}
		//System.out.println("apres insert dans "+c.getLocation(vm).getName());
		return 1;
	}	

	private int fillLT(Configuration c, Node pm)
	{	
		Migration m;
		//System.out.println("avant fill LT "+pm.getName());
		Node ut;
		int capa=pm.getCPUCapacity();
		VirtualMachine vm;
		int conspm=getCPUDemand(c, pm);
		while (conspm<2*capa/3)
		{
			ut=trouverUT(c,pm);
			if (c.getRunnings(ut).size()==0)
				return 0;
			while (c.getRunnings(ut).size()>0 && conspm<2*capa/3){
				vm=c.getRunnings(ut).get(0);
				m=new Migration(vm,c.getLocation(vm),pm);
				reconfigurationPlan.add(m);	
				nbmouv++;
				memtotbougee+=vm.getMemoryDemand();

				//	System.out.println("on y ajoute "+vm.getName());
				conspm+=vm.getCPUDemand();
			}
		}
		return 1;
	}	

	private int fillL(Configuration c, Node pm)
	{		
		Migration m;
		//		System.out.println("avant fill L "+pm.getName());
		Node ut;
		int capa=pm.getCPUCapacity();
		VirtualMachine vm;
		int conspm=getCPUDemand(c, pm);
		while (conspm<2*capa/3)
		{
			ut=trouverUT(c,pm);
			if (c.getRunnings(ut).size()==0)
				return 0;
			while (c.getRunnings(ut).size()>0 && conspm<2*capa/3){
				vm=c.getRunnings(ut).get(0);
				m=new Migration(vm,c.getLocation(vm),pm);
				reconfigurationPlan.add(m);
				nbmouv++;
				memtotbougee+=vm.getMemoryDemand();
				//			System.out.println("on y ajoute "+vm.getName());
				conspm+=vm.getCPUDemand();
			}
		}
		return 1;
	}	

	private ManagedElementSet<VirtualMachine> passagebidim(Configuration c){
		ManagedElementSet<VirtualMachine> vmsave=new SimpleManagedElementSet<VirtualMachine>();
		for (Node pm : UsedNodes)
			for (VirtualMachine vm : c.getRunnings(pm)){
				if ((float)(vm.getMemoryDemand())/pm.getMemoryCapacity() > (float)(vm.getCPUDemand())/pm.getCPUCapacity())
				{
					//			System.out.println("ratio Mem : "+(float)(vm.getMemoryDemand())/pm.getMemoryCapacity()+" ratio CPU : "+(float)(vm.getCPUDemand())/pm.getCPUCapacity()+" pour "+vm);
					vmsave.add(vm.clone());
					vm.setCPUDemand(vm.getMemoryDemand()*pm.getCPUCapacity()/pm.getMemoryCapacity());
					//			System.out.println("valeur modifiee pour "+vm);
				}
			}
		return vmsave;
	}

	public ComputingState computeReconfigurationPlan() {	
		Configuration curConf = initialConfiguration;
		//System.out.println("nb de pm libres : "+getUnusedNodes(curConf).size());
		ComputingState result = ComputingState.VMPP_FAILED;
		getUsedNodes(curConf);
		getUnusedNodes(curConf);
		System.out.println("nb de pm libres : "+UnusedNodes.size());
		ManagedElementSet<VirtualMachine> vmsave=passagebidim(curConf);
		int type=0;
		int modifpmtemp=0;
		modifpm=0;
		ManagedElementSet<VirtualMachine> vms;
		int capa=curConf.getAllNodes().get(0).getCPUCapacity();
		for (Node pm : UsedNodes) {
			//		System.out.print(pm.getName());System.out.println("\n");
			if (UnusedNodes == null)
				//C'est la merde, aucune pm de libre pour decharger
				return result;
			while (testsurcharge(curConf, pm)==1) {
				modifpm+=1;
				modifpmtemp=1;
				//	 System.out.println("dans sur charge "+pm.getName()+"\n");
				vms=curConf.getRunnings(pm);	
				type=identifiertypesurchargepm(curConf, pm);
				switch(type){
				//Type BBT*
				case 1: {
					//	 System.out.println("Cas 1");
					VirtualMachine vmB1=null;
					VirtualMachine vmB2=null;
					int a=0;
					for (VirtualMachine vm : vms){
						if (vm.getCPUDemand()>=2*capa/3){
							if (a==0){
								vmB1=vm;
								a++;
							}
							else vmB2=vm;
						}
					}
					// System.out.println("on a bougé "+vmB1+" dans "+getUnusedNodes(curConf).get(0).getName());
					reconfigurationPlan.add(new Migration(vmB1,pm,UnusedNodes.get(0)));
					UnusedNodes.remove(0);
					nbmouv++;
					memtotbougee+=vmB1.getMemoryDemand();
					//Ne bouger le deuxième que s'il y a d'autres gens, des T*, dans la pm actuelle
					if (curConf.getRunnings(pm).size()>1){
						// System.out.println("on a bougé "+vmB2+" dans "+getUnusedNodes(curConf).get(0).getName());
						reconfigurationPlan.add(new Migration(vmB2,pm,UnusedNodes.get(0)));
						UnusedNodes.remove(0);
						nbmouv++;
						memtotbougee+=vmB2.getMemoryDemand();
					}
					//On peut se retrouver en sous charge a ce moment la, on le traitera plus tard, lors du traitement des sous charges
				};break;
				//Type BLST*
				case 2: {
					// System.out.println("Cas 2");
					VirtualMachine vmB=null;
					VirtualMachine vmS=null;
					for (VirtualMachine vm : vms){
						if (vm.getCPUDemand()>=2*capa/3){
							vmB=vm;						}
						if (vm.getCPUDemand()>=capa/3 && vm.getCPUDemand()<capa/2)
							vmS=vm;
					} 
					insertS(curConf, vmS,1);
					// System.out.println("on a bougé "+vmB+" dans "+getUnusedNodes(curConf).get(0).getName());
					reconfigurationPlan.add(new Migration(vmB,pm,UnusedNodes.get(0)));
					UnusedNodes.remove(0);		
					nbmouv++;
					memtotbougee+=vmB.getMemoryDemand();	
					fillLT(curConf, pm);
				};break; 
				//Type BLT*
				case 3: {
					// System.out.println("Cas 3");
					VirtualMachine vmB=null;
					for (VirtualMachine vm : vms){
						if (vm.getCPUDemand()>=2*capa/3){
							vmB=vm;
						}
					} 	
					// System.out.println("on a bougé "+vmB+" dans "+getUnusedNodes(curConf).get(0).getName());
					reconfigurationPlan.add(new Migration(vmB,pm,UnusedNodes.get(0)));
					UnusedNodes.remove(0);
					nbmouv++;
					memtotbougee+=vmB.getMemoryDemand();
					fillLT(curConf, pm);
				};break;  
				//Type BSST*
				//Attention si newpms contient qu'une seule pm, FAIRE GAFFE PARTOUT
				case 4: {
					// System.out.println("Cas 4");
					Node pmSS=UnusedNodes.get(0);
					UnusedNodes.remove(0);
					VirtualMachine vmB=null;
					VirtualMachine vmS1=null;
					VirtualMachine vmS2=null;
					int a=0;
					for (VirtualMachine vm : vms){
						if (vm.getCPUDemand()>=2*capa/3){
							vmB=vm;
						}
						if (vm.getCPUDemand()>=capa/3 && vm.getCPUDemand()<capa/2){
							if (a==0){
								vmS1=vm;
								a++;
							}	
							else vmS2=vm;
						}
					} 
					// System.out.println("on a bougé "+vmS1+" dans "+pmSS.getName());
					reconfigurationPlan.add(new Migration(vmS1,pm,pmSS));
					// System.out.println("on a bougé "+vmS2+" dans "+pmSS.getName());
					reconfigurationPlan.add(new Migration(vmS2,pm,pmSS));
					nbmouv+=2;
					memtotbougee+=vmS1.getMemoryDemand();
					memtotbougee+=vmS2.getMemoryDemand();
					//Ne bouger le deuxième que s'il y a d'autres gens, des T*, dans la pm actuelle
					if (curConf.getRunnings(pm).size()>1){
						// System.out.println("on a bougé "+vmB+" dans "+getUnusedNodes(curConf).get(0).getName());
						reconfigurationPlan.add(new Migration(vmB,pm,UnusedNodes.get(0)));
						UnusedNodes.remove(0);
						nbmouv++;
						memtotbougee+=vmB.getMemoryDemand();
					}
					//On peut se retrouver en sous charge a ce moment la, on le traitera plus tard, lors du traitement des sous charges
				};break;  
				//Type BST*
				case 5: {
					// System.out.println("Cas 5");
					VirtualMachine vmB=null;
					VirtualMachine vmS=null;
					for (VirtualMachine vm : vms){
						if (vm.getCPUDemand()>=2*capa/3){
							vmB=vm;						 }
						if (vm.getCPUDemand()>=capa/3 && vm.getCPUDemand()<capa/2)
							vmS=vm;
					} 
					insertS(curConf, vmS,1);			
					//Ne bouger le deuxième que s'il y a d'autres gens, des T*, dans la pm actuelle
					if (curConf.getRunnings(pm).size()>1){
						// System.out.println("on a bougé "+vmB+" dans "+getUnusedNodes(curConf).get(0).getName());
						reconfigurationPlan.add(new Migration(vmB,pm,UnusedNodes.get(0)));
						UnusedNodes.remove(0);
						nbmouv++;
						memtotbougee+=vmB.getMemoryDemand();
					}
					//On peut se retrouver en sous charge a ce moment la, on le traitera plus tard, lors du traitement des sous charges
				};break;  
				//Type BT* mm code que BBT*
				case 6: {
					// System.out.println("Cas 6");
					//Ne bouger le B que s'il y a d'autres gens, des T*, dans la pm actuelle
					if (curConf.getRunnings(pm).size()>1){
						VirtualMachine vmB=null;
						for (VirtualMachine vm : vms){
							if (vm.getCPUDemand()>=2*capa/3){
								vmB=vm;
							}
						} 
						// System.out.println("on a bougé "+vmB+" dans "+getUnusedNodes(curConf).get(0).getName());
						reconfigurationPlan.add(new Migration(vmB,pm,UnusedNodes.get(0)));
						UnusedNodes.remove(0);
						nbmouv++;
						memtotbougee+=vmB.getMemoryDemand();
						//On peut se retrouver en sous charge a ce moment la, on le traitera plus tard, lors du traitement des sous charges
					}
				};break; 
				//Type LLST*
				case 7:{
					// System.out.println("Cas 7");
					int a=0;
					VirtualMachine vmL=null;
					VirtualMachine vmS=null;
					for (VirtualMachine vm : vms){
						if (vm.getCPUDemand()>=capa/2 && a==0){
							a=1;
							vmL=vm;
						}
						if (vm.getCPUDemand()>=capa/3 && vm.getCPUDemand()<capa/2)
							vmS=vm;
					}
					insertS(curConf, vmS,1);
					Node pmL =UnusedNodes.get(0);
					UnusedNodes.remove(0);
					//	System.out.println("on a bougé "+vmL+" dans "+pmL.getName());
					reconfigurationPlan.add(new Migration(vmL,pm,pmL));
					nbmouv++;
					memtotbougee+=vmL.getMemoryDemand();
					fillL(curConf,pmL);
					fillLT(curConf,pm);
				};break; 
				//Type LLT*
				case 8:{
					// System.out.println("Cas 8");
					int a=0;
					VirtualMachine vmL=null;
					for (VirtualMachine vm : vms){
						if (vm.getCPUDemand()>=capa/2 && a==0){
							a=1;
							vmL=vm;
						}
					}
					Node pmL =UnusedNodes.get(0);
					UnusedNodes.remove(0);
					// System.out.println("on a bougé "+vmL+" dans "+pmL.getName());
					reconfigurationPlan.add(new Migration(vmL,pm,pmL));	
					nbmouv++;
					memtotbougee+=vmL.getMemoryDemand();
					fillL(curConf,pmL);
					fillLT(curConf,pm);
				} ;break; 
				//Type LSST*
				case 9:{
					// System.out.println("Cas 9");
					Node pmSS=UnusedNodes.get(0);
					UnusedNodes.remove(0);
					VirtualMachine vmS1=null;
					VirtualMachine vmS2=null;
					int a=0;	
					for (VirtualMachine vm : vms){
						if (vm.getCPUDemand()>=capa/3 && vm.getCPUDemand()<capa/2){
							if (a==0){
								vmS1=vm;
								a++;
							}
							else if (a==1)
								vmS2=vm;
						}
					}
					// System.out.println("on a bougé "+vmS1+" dans "+pmSS.getName());
					 reconfigurationPlan.add(new Migration(vmS1,pm,pmSS));	
					// System.out.println("on a bougé "+vmS2+" dans "+pmSS.getName());
					 reconfigurationPlan.add(new Migration(vmS2,pm,pmSS));
					nbmouv+=2;
					 memtotbougee+=vmS1.getMemoryDemand();
					 memtotbougee+=vmS2.getMemoryDemand();
					fillLT(curConf,pm);
				} ;break; 
				//Type LST*
				case 10:{
					// System.out.println("Cas 10");
					VirtualMachine vmS=null;
					for (VirtualMachine vm : vms){
						if (vm.getCPUDemand()>=capa/3 && vm.getCPUDemand()<capa/2)
							vmS=vm;
					}
					insertS(curConf, vmS,1);
					fillLT(curConf,pm);
				};break; 
				//Type LT*
				case 11: {
					// System.out.println("Cas 11");
					Node ut=trouverUT(curConf,pm);
					int conspm=getCPUDemand(curConf,pm);
					VirtualMachine vm=curConf.getRunnings(pm).get(0);
					if (vm.getCPUDemand()>=capa/2)
						vm=curConf.getRunnings(pm).get(curConf.getRunnings(pm).size()-1);
					while (conspm >capa){
						 reconfigurationPlan.add(new Migration(vm,pm,ut));
						 // System.out.println("on a bougé "+vm+" dans "+ut.getName());
						nbmouv++;
						 memtotbougee+=vm.getMemoryDemand();
						 conspm-=vm.getCPUDemand();
						vm=curConf.getRunnings(pm).get(0);
						if (vm.getCPUDemand()>=capa/2)
							vm=curConf.getRunnings(pm).get(curConf.getRunnings(pm).size()-1);
					}
				};break; 
				//Type SSST*
				case 12:{
					//					 System.out.println("Cas 12");
					int a=0;
					VirtualMachine vmS1=null;
					VirtualMachine vmS2=null;
					VirtualMachine vmS3=null;
					Node pmSS=UnusedNodes.get(0);
					UnusedNodes.remove(0);
					for (VirtualMachine vm : vms){
						if (vm.getCPUDemand()>=capa/3 && vm.getCPUDemand()<capa/2)
							if (a==0){
								vmS1=vm;
								a++;	
							}
							else if (a==1){
								vmS2=vm;
								a++;
							}
							else vmS3=vm;
					}
					//			 System.out.println("on a bougé "+vmS1+" dans "+pmSS.getName());
					 reconfigurationPlan.add(new Migration(vmS1,pm,pmSS));
								 
					//				 System.out.println("on a bougé "+vmS2+" dans "+pmSS.getName());
					 reconfigurationPlan.add(new Migration(vmS2,pm,pmSS));							 
					nbmouv+=2;
					 memtotbougee+=vmS1.getMemoryDemand();
					 memtotbougee+=vmS2.getMemoryDemand();
					//Ne bouger le dernier S que s'il y a d'autres gens, des T*, dans la pm actuelle
					if (curConf.getRunnings(pm).size()>1){
						insertS(curConf, vmS3,1);
					}
					else insertS(curConf, vmS3,0);
					//On peut se retrouver en sous charge a ce moment la, on le traitera plus tard, lors du traitement des sous charges
				};break; 
				//Type SST*
				case 13:{
					// System.out.println("Cas 13");
					//Ne bouger les SS que s'il y a d'autres gens, des T*, dans la pm actuelle
					if (curConf.getRunnings(pm).size()>2){
						Node pmSS=UnusedNodes.get(0);
						UnusedNodes.remove(0);
						VirtualMachine vmS1=null;
						VirtualMachine vmS2=null;
						int a=0;
						for (VirtualMachine vm : vms){
							if (vm.getCPUDemand()>=capa/3 && vm.getCPUDemand()<capa/2){
								if (a==0){
									vmS1=vm;
									a++;
								}
								else if (a==1){
									vmS2=vm;
								}		 
							}
						}
						//		 System.out.println("on a bougé "+vmS1+" dans "+pmSS.getName());
						 reconfigurationPlan.add(new Migration(vmS1,pm,pmSS));
					//			 System.out.println("on a bougé "+vmS2+" dans "+pmSS.getName());
						 reconfigurationPlan.add(new Migration(vmS2,pm,pmSS));
							nbmouv+=2;
							 memtotbougee+=vmS1.getMemoryDemand();
							 memtotbougee+=vmS2.getMemoryDemand();

					}
					//On peut se retrouver en sous charge a ce moment la, on le traitera plus tard, lors du traitement des sous charges
				};break; 
				//Type ST*
				case 14:{
					//	 System.out.println("Cas 14");
					//Ne bouger le S que s'il y a d'autres gens, des T*, dans la pm actuelle
					VirtualMachine vmS=null;
					for (VirtualMachine vm : vms){
						if (vm.getCPUDemand()>=capa/3 && vm.getCPUDemand()<capa/2)
							vmS=vm;
					}
					if (curConf.getRunnings(pm).size()>1)
						insertS(curConf, vmS,1);
					else insertS(curConf, vmS,0);
					//On peut se retrouver en sous charge a ce moment la, on le traitera plus tard, lors du traitement des sous charges	 
				};break; 
				//Type T*
				case 15: {
					//	 System.out.println("Cas 15");
					Node ut=trouverUT(curConf,pm);
					int conspm=getCPUDemand(curConf,pm);
					VirtualMachine vm=curConf.getRunnings(pm).get(0);
					while (conspm >capa){
						 reconfigurationPlan.add(new Migration(vm,pm,ut));
						//			 System.out.println("on a bougé "+vm+" dans "+ut.getName());
						nbmouv++;
						conspm-=vm.getCPUDemand();
						vm=curConf.getRunnings(pm).get(0);
					}
				};break;
				}
			}
			if (testsouscharge(curConf, pm)==1) {
				//		 System.out.println("dans sous charge "+pm.getName()+" \n");
				vms=curConf.getRunnings(pm);	
				type=identifiertypesouschargepm(curConf, pm);
				switch(type){
				//Type LT*
				case 1: {
					//			 System.out.println("Cas 1");
					fillLT(curConf,pm);
				};break;
				//Type ST*
				case 2: {
					//		 System.out.println("Cas 2");
					VirtualMachine vmS=null;
					for (VirtualMachine vm :curConf.getRunnings(pm))
						if (vm.getCPUDemand()>=capa/3 && vm.getCPUDemand()<capa/2)
							vmS=vm; 
					//Ne bouger le S que s'il y a d'autres gens, des T*, dans la pm actuelle
					if (curConf.getRunnings(pm).size()>1){
						insertS(curConf, vmS,1);				 
						traiterT(curConf,pm);
					}
					else insertS(curConf, vmS,0);			
				};break;
				//Type T*
				case 3: {
					//	 System.out.println("Cas 3");
					traiterT(curConf,pm);
				};break;
				}
			}
		}
		System.out.println("En tout, on a effectué "+nbmouv+" déplacements de VMs et deplacé une mem totale de "+memtotbougee+"\n");
		reconfigurationPlanCost=memtotbougee;
		getUsedNodes(curConf);
		System.out.println("On utilise "+UsedNodes.size()+" PMs\n");
		calculrepartvm(curConf);
		int conso=0;
		for (VirtualMachine vm:vmsave)
			curConf.getAllVirtualMachines().get(vm.getName()).setCPUDemand(vm.getCPUDemand());
		for (Node pm : UsedNodes)
			conso+=getCPUDemand(curConf,pm);
		System.out.println("Utilisation en moyenne des PMs : "+conso/UsedNodes.size());
		this.newConfiguration=curConf;
		if (type==0)
			result = ComputingState.NO_RECONFIGURATION_NEEDED;//ADDED
		else
			result = ComputingState.VMRP_SUCCESS;
		return result;
	}

	public void applyReconfigurationPlan() {	
		System.out.println("\nApply reconf plan \n");
	}	

}