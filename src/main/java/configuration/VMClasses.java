package configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility class to get VM classes from a file
 * @author fquesnel
 *
 */
public class VMClasses{

	///////////////////////////////////////////////////////////////////////////
    // Class variables
    ///////////////////////////////////////////////////////////////////////////
	
	private final static String CLASS_FILE = "config" + File.separator + "vm_classes.txt";
	
	/**
	 * The list of VM classes
	 */
	public final static List<VMClass> CLASSES = new ArrayList<VMClass>();
	
	
	///////////////////////////////////////////////////////////////////////////
    // Class initializer
    ///////////////////////////////////////////////////////////////////////////
	
	static{
		try {
			BufferedReader reader = new BufferedReader(new FileReader(CLASS_FILE));
			String line;
			String[] tokens;
			
			while((line = reader.readLine()) != null){
				if(line.contains(":") && !line.startsWith("//")){
					tokens = line.split(":");
					
					CLASSES.add(new VMClass(tokens[0],
							Integer.parseInt(tokens[1]),
                            Integer.parseInt(tokens[2]),
                            Integer.parseInt(tokens[3]),
                            Integer.parseInt(tokens[4]),
							Integer.parseInt(tokens[5])));
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * A VM class
	 * @author fquesnel
	 *
	 */
	public static class VMClass{
		///////////////////////////////////////////////////////////////////////////
	    // Instance variables
	    ///////////////////////////////////////////////////////////////////////////
		
		private final String name;
		private final int nbOfCPUs;
		private final int memSize;
        private final int netBW;
        private final int migNetBW;
        private final int memIntensity;


        ///////////////////////////////////////////////////////////////////////////
	    // Constructor
	    ///////////////////////////////////////////////////////////////////////////
		
		/**
		 * Constructs a new VM class
		 */
		public VMClass(String name, int nbOfCPUs, int memSize, int netBW, int migNetBW, int memIntensity) {
			super();
			this.name = name;
			this.nbOfCPUs = nbOfCPUs;
			this.memSize = memSize;
            this.netBW = netBW;
            this.migNetBW = migNetBW;
            this.memIntensity = memIntensity;
		}
		
		
		///////////////////////////////////////////////////////////////////////////
	    // Accessors
	    ///////////////////////////////////////////////////////////////////////////

		/**
		 * Returns the name of the class
		 */
		public String getName() {
			return name;
		}

		/**
		 * Returns the number of vCPUs (or ECUs for AWS EC2)
		 */
		public int getNbOfCPUs() {
			return nbOfCPUs;
		}

		/**
		 * Returns the memory allocated to the VM
		 */
		public int getMemSize() {
			return memSize;
		}

        /**
         * @return the network bandwith defined at the VM creation
         */
        public int getNetBW() {
            return netBW;
        }

        /**
         * @return the network bandwith used by the hypervizor for the migration (defined at the creation)
         */
        public int getMigNetBW() {
            return migNetBW;
        }

        /**
         * @return the memory intensity (expressed as a percentage according to MigNetBW)
         */
        public int getMemIntensity() {
            return memIntensity;
        }
		///////////////////////////////////////////////////////////////////////////
	    // Other methods
	    ///////////////////////////////////////////////////////////////////////////
		
		@Override
		public String toString(){
			return name + ":" + nbOfCPUs + ":" + memSize;
		}
	}
}