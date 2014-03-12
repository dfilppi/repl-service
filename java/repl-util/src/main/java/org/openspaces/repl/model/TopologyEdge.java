package org.openspaces.repl.model;

import java.io.Serializable;

public class TopologyEdge implements Serializable{
	String fromSite;
	String fromSpace;
	String toSite;
	String toSpace;

	public TopologyEdge(String fromSite,String fromSpace,String toSite,String toSpace) {
		this.fromSite=fromSite;
		this.fromSpace=fromSpace;
		this.toSite=toSite;
		this.toSpace=toSpace;
	}

	@Override
	public boolean equals(Object other){
		if(other==null)return false;
		if(!(this.getClass().isInstance(other)))return false;
		TopologyEdge otheredge=(TopologyEdge)other;
		if(otheredge.fromSite.equals(this.fromSite) &&
				otheredge.fromSpace.equals(this.fromSpace) &&
				otheredge.toSite.equals(this.toSite) &&
				otheredge.toSpace.equals(this.toSpace))return true;
		return false;
	}
}

