package org.openspaces.repl.model;

import java.io.Serializable;

public class SiteDetails implements Serializable {
	private String siteId;
	private String gatewayPublicAddress;
	private String gatewayPrivateAddress;
	private int commport,discoport;
	
	public SiteDetails(){}
	
	public SiteDetails(String siteId,String publicAddr,String privateAddr){
		this.siteId=siteId;
		this.gatewayPublicAddress=publicAddr;
		this.setGatewayPrivateAddress(publicAddr);
	}
	
	public SiteDetails(String siteId,String publicAddr,String privateAddr, int comm, int disc){
		this.siteId=siteId;
		this.gatewayPublicAddress=publicAddr;
		this.setGatewayPrivateAddress(privateAddr);
		this.commport=comm;
		this.discoport=disc;
	}
	
	public String getSiteId() {
		return siteId;
	}
	public void setSiteId(String siteId) {
		this.siteId = siteId;
	}
	public String getGatewayPublicAddress() {
		return gatewayPublicAddress;
	}
	public void setGatewayPublicAddress(String gatewayAddress) {
		this.gatewayPublicAddress = gatewayAddress;
	}
	public String getGatewayPrivateAddress() {
		return gatewayPrivateAddress;
	}

	public void setGatewayPrivateAddress(String gatewayPrivateAddress) {
		this.gatewayPrivateAddress = gatewayPrivateAddress;
	}
	public int getCommport() {
		return commport;
	}
	public void setCommport(int commport) {
		this.commport = commport;
	}
	public int getDiscoport() {
		return discoport;
	}
	public void setDiscoport(int discoport) {
		this.discoport = discoport;
	}

}
