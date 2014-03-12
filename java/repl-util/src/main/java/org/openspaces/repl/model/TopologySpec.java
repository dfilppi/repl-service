package org.openspaces.repl.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TopologySpec implements Serializable{
	public String name;
	public Set<TopologyEdge> edges=new HashSet<TopologyEdge>();
	public Map<String,List<Integer>> ports=new HashMap<String,List<Integer>>();
	public Set<SiteDetails> sites=new HashSet<SiteDetails>();

	public TopologySpec(){}

	/**
	 * Construct a topology spec from its JSON representation
	 * 
	 * @param ts
	 */
	public TopologySpec(Map ts) {
		this.name=(String)ts.get("name");

		List<Map<String,String>> inputedges=(List<Map<String,String>>)ts.get("edges");
		if(inputedges==null||inputedges.size()==0)throw new RuntimeException("no replication pairs defined");
		for(Map<String,String> edge:inputedges){
			TopologyEdge newedge=new TopologyEdge(edge.get("fromSite"),edge.get("fromSpace"),edge.get("toSite"),edge.get("toSpace"));
			edges.add(newedge);
		}

		Map<String,Map<String,String>> portmap=(Map<String,Map<String,String>>)ts.get("ports");
		if(portmap==null)portmap=new HashMap<String,Map<String,String>>();
		for(Map.Entry<String,Map<String,String>> entry:portmap.entrySet()){
			List<Integer> plist=new ArrayList<Integer>();
			plist.add(Integer.parseInt(entry.getValue().get("discovery")));
			plist.add(Integer.parseInt(entry.getValue().get("data")));
			//add to details TODO rework this, same info in two places
			sites.add(new SiteDetails(entry.getKey(),null,null,plist.get(1),plist.get(0)));  //no address yet
			ports.put(entry.getKey(),plist);
		}
	}
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Set<TopologyEdge> getEdges() {
		return edges;
	}

	public void setEdges(Set<TopologyEdge> edges) {
		this.edges = edges;
	}

	public Map<String, List<Integer>> getPorts() {
		return ports;
	}

	public void setPorts(Map<String, List<Integer>> ports) {
		this.ports = ports;
	}

	public Set<SiteDetails> getSites() {
		return sites;
	}

	public void setSites(Set<SiteDetails> sites) {
		this.sites = sites;
	}

}

