package org.openspaces.repl.util;

import java.util.Map;

import org.openspaces.repl.model.SiteDetails;
import org.openspaces.repl.model.TopologySpec;

public interface WanStateProvider {
	/**
	 * Get the current complete topology definition,
	 * including addresses
	 * 
	 * @param name topology name
	 * @return complete spec
	 */
	TopologySpec getTopology(String name);
	
	boolean topologyExists(String name);
	
	/**
	 * Implementation specific config map for configuring client
	 * 
	 * @param config
	 */
	void setConfig(Map<String,Object> config);
	
	/**
	 * Updates information for a particular site.  Meant for updating
	 * runtime info (addresses)
	 * @param topoName the topology name
	 * @param siteId the site id of the sender
	 * @param details the details to update
	 */
	void updateDetails(String topoName,String siteId,SiteDetails details);
	
	/**
	 * Destructive create.  Define complete topology.
	 * 
	 * @param spec the topology spec
	 * @returns true if topology was created
	 */
	boolean createTopology(TopologySpec spec, boolean overwrite);
	
	/**
	 * Delete topology
	 *  
	 * @param name the topology name
	 */
	void deleteTopology(String name);
}
