package scheduling;


import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import dvms.log.Logger;

public class GeneralProperties extends Properties {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7049040142852715280L;

	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Constructors
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public GeneralProperties(String file){
		super();

		try {
			BufferedReader reader = new BufferedReader(new FileReader(file));
			this.load(reader);
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			Logger.log(e);
		} catch (IOException e) {
			e.printStackTrace();
			Logger.log(e);
		}
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//General methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public String getProperty(String key){
		String result = super.getProperty(key);
		
		if(result != null)
			return result.trim();
		
		else
			return result;
	}
	
	//Get the property as an integer
	public int getPropertyAsInt(String key, int defaultValue){
		String value = getProperty(key);
		
		if(value != null)
			return Integer.parseInt(value);
		
		else
			return defaultValue;
	}
	
	//Get the property as a long
	public long getPropertyAsLong(String key, long defaultValue){
		String value = getProperty(key);
		
		if(value != null)
			return Long.parseLong(value);
		
		else
			return defaultValue;
	}
	
	//Get the property as a boolean
	public boolean getPropertyAsBoolean(String key, boolean defaultValue){
		String value = getProperty(key);
		
		if(value != null)
			return Boolean.parseBoolean(value);
		
		else
			return defaultValue;
	}
}
