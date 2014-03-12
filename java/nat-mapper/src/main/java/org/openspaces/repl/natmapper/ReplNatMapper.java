package org.openspaces.repl.natmapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.gigaspaces.logger.Constants;
import com.gigaspaces.lrmi.INetworkMapper;
import com.gigaspaces.lrmi.ServerAddress;
import com.j_spaces.kernel.SystemProperties;

// A non-cached version of the nat mapper (so it can be updated at runtime)
// Delegates to the default mapper, because it was not designed to be subclassed
//
// @author dewayne
//
public class ReplNatMapper implements INetworkMapper
{
	private static final Logger _logger = Logger.getLogger(Constants.LOGGER_LRMI);	
	private static final String NETWORK_MAPPING_FILE = System.getProperty(SystemProperties.LRMI_NETWORK_MAPPING_FILE,"/tmp/network_mapping.config");
    private static final String MALFORMED_FORMAT_MSG = "Unsupported format of network mapping file, " +
            "expected format is seperated lines each contains a seperate mapping: " +
            "<original ip>:<original port>,<mapped ip>:<mapped port> for instance 10.0.0.1:4162,212.321.1.1:3000";
    private final Map<ServerAddress, ServerAddress> _mapping = new HashMap<ServerAddress, ServerAddress>();
	private static long mapFileModifiedTime=0L;
	private static File mappingFile=null;
	private static Object lock=new Object();

	public ReplNatMapper(){
        if (_logger.isLoggable(Level.FINE))
        	_logger.fine("constructing");
        
		if(ReplNatMapper.mappingFile==null)ReplNatMapper.mappingFile=new File(NETWORK_MAPPING_FILE);
		ReplNatMapper.mapFileModifiedTime=mappingFile.lastModified();
		loadFile();
	}

	public ServerAddress map(ServerAddress addr) {
        if (_logger.isLoggable(Level.FINE))
        	_logger.fine("map called for "+addr.getHost()+":"+addr.getPort());
        
		synchronized(lock){
			long lm=mappingFile.lastModified();
			if(mapFileModifiedTime!=lm){
				_logger.fine("rereading map config");
				mapFileModifiedTime=lm;
				loadFile();
			}
		}
		ServerAddress mapped=oldmap(addr);

        if (_logger.isLoggable(Level.FINE))
        	_logger.fine("mapped to "+mapped.getHost()+":"+mapped.getPort());
        
		return mapped;
	}

	private void loadFile(){
		InputStream is=null;
		try{
			is=new FileInputStream(new File(NETWORK_MAPPING_FILE));
		}
		catch(FileNotFoundException e){
			if (_logger.isLoggable(Level.FINE))
				_logger.fine("Could not locate networking mapping file " + NETWORK_MAPPING_FILE + ", no mapping created");
			return;
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		String line = null;
		try
		{
			while ((line = reader.readLine()) != null)
			{
				//Rermarked line
				if (line.startsWith("#"))
					continue;
				if (line.trim().length()==0)continue;//blank
				String[] split = line.split(",");
				if (split.length != 2)
					throw new IllegalArgumentException(MALFORMED_FORMAT_MSG);
				ServerAddress original = getAddress(split[0]);
				ServerAddress mapped = getAddress(split[1]);
				if (_mapping.containsKey(original))
					throw new IllegalArgumentException("Address " + original + " is already mapped to " + _mapping.get(original));
				if (_logger.isLoggable(Level.FINE))
					_logger.fine("Adding mapping of " + original + " to " + mapped);
				_mapping.put(original, mapped);
			}
	        if (_logger.isLoggable(Level.FINE))
	        	_logger.fine("found "+_mapping.size()+" entries");
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Error while parsing the network mapping file " + NETWORK_MAPPING_FILE, e);
		}
		finally{
			try{
				if(is!=null)is.close();
			}catch(Exception e){}
		}

	}
	
    private ServerAddress getAddress(String string)
    {
        String[] split = string.split(":");
        if (split.length != 2)
            throw new IllegalArgumentException(MALFORMED_FORMAT_MSG);

        try
        {
            return new ServerAddress(split[0], Integer.parseInt(split[1]));
        }
        catch(NumberFormatException e)
        {
            throw new IllegalArgumentException(MALFORMED_FORMAT_MSG);
        }
    }


    public ServerAddress oldmap(ServerAddress serverAddress)
    {
        ServerAddress transformed = _mapping.get(serverAddress);
        //No mapping, return original
        if (transformed == null)
        {
            if (_logger.isLoggable(Level.FINE))
                _logger.fine("No mapping exists for provided address " + serverAddress + " returning original address");
            return serverAddress;
        }
        if (_logger.isLoggable(Level.FINE))
            _logger.fine("Mapping  address " + serverAddress + " to " + transformed);
        return transformed;
    }

}
