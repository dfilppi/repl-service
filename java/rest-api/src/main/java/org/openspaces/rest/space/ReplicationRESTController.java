/*
 * Copyright 2011 GigaSpaces Technologies Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at *
 *     
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License."
 */
package org.openspaces.rest.space;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.cloudifysource.domain.Application;
import org.cloudifysource.dsl.internal.CloudifyConstants.DeploymentState;
import org.cloudifysource.dsl.internal.DSLReader;
import org.cloudifysource.dsl.internal.DSLUtils;
import org.cloudifysource.dsl.internal.packaging.Packager;
import org.cloudifysource.dsl.rest.request.InstallApplicationRequest;
import org.cloudifysource.dsl.rest.response.ApplicationDescription;
import org.cloudifysource.dsl.rest.response.InstallApplicationResponse;
import org.cloudifysource.dsl.rest.response.UploadResponse;
import org.cloudifysource.restclient.RestClient;
import org.cloudifysource.restclient.exceptions.RestClientResponseException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.openspaces.admin.Admin;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Spring MVC controller for a RESTful Replication API
 * 
 * API:
 * 
 * Create topology -------
 * 
 * Manipulates multiple Cloudify installations to configure replication topologies.
 * Sequence of operations:
 * 
 * - Validate request body
 * -- make sure all target Cloudify REST API are accessible.
 * -- check whether repl services are running at all locations.
 * --- if not running, start them
 * --- if running, check if this topology is already running
 * ---- if topology running, exit with warning
 * -- deploy repl spaces
 * -- deploy gateways
 * 
 * 
 */
@Controller
@RequestMapping(value = "/rest/repl/*")
public class ReplicationRESTController {
	private static final Logger log=Logger.getLogger(ReplicationRESTController.class.getName());
	private static final String APPLICATION_PATH="",CLOUDIFY_VERSION="2.7.0";
	private static final String DEFAULT_DATA_PORT="10000";
	private static final String DEFAULT_DISCOVERY_PORT="10001";
	private Admin admin=null;
	private HttpClient client=HttpClientBuilder.create().build();
	private boolean test=true;

	@RequestMapping(value="/topology/{name}",method=RequestMethod.POST)
	@ResponseStatus(HttpStatus.OK)
	public @ResponseBody String deployTopology(
			@PathVariable String name,
			@RequestBody HashMap body){
		log.info("deployTopology called");

		//Parse and validate body
		log.info("validating request");
		DeployRequest req=new DeployRequest(body);
		
		//Check participant sites.
		log.info("validating participants");
		req.tspec.name=name;
		validateSites(req);
		
		//deploy repl services
		log.info("deploying replication services at all sites");
		deployReplApps(req);

		//wait for completion
		log.info("deployment initiated, waiting for completion");
		if(!waitForDeployments(req,300000)){
			log.info("deployment timed out, rolling back deployment");
			rollbackApps(req);
			throw new RuntimeException("repl application deployment failed");
		}
		log.info("deployment complete"); 

		//xap running everywhere, deploy spaces
		log.info("deploying replication spaces on all sites");
		deploySpaces(req);
		log.info("spaces deployed");

		//deploy gateways
		log.info("deploying gateways on all sites");
		deployGateways(req);
		log.info("gateways deployed");

		return "done";
	}

	private Map<String,List<String>> makeGatewayAddressMap(DeployRequest req){
		Map<String,List<String>> result=new HashMap<String,List<String>>();
		HttpClient client=HttpClientBuilder.create().build();

		for(CloudifyRestEndpoint ep:req.endpoints){
			//get gateway host (assumes 1 per site) public ip
			String publicIp="";
			String privateIp="";
			HttpGet get=new HttpGet("http://"+ep.address+":"+ep.port+"/"+CLOUDIFY_VERSION+"/deployments/"+req.tspec.name+"/service/repl-gateway/instances/1/metadata");
			try{
				HttpResponse resp=client.execute(get);
				String resptext=IOUtils.toString(resp.getEntity().getContent());
				log.info("got ip request response:"+resptext);
				JsonNode json=new ObjectMapper().readTree(resptext);
				publicIp=json.get("response").get("processDetails").get("Cloud Public IP").getTextValue();
				privateIp=json.get("response").get("processDetails").get("Cloud Private IP").getTextValue();
				log.info(String.format("%s returned gateway private ip:%s public ip:%s",ep.address,privateIp,publicIp));
			}
			catch(Exception e){
				throw new RuntimeException("failed to get public ip of gateway",e);
			}

			result.put(ep.siteId,Arrays.asList(privateIp,publicIp));
		}
		return result;
	}

	private void deploySpaces(final DeployRequest req){
		ExecutorService esvc=Executors.newFixedThreadPool(req.endpoints.size());
		List<Callable<DeployResult>> tasks=new ArrayList<Callable<DeployResult>>();

		try{

			for(final CloudifyRestEndpoint ep:req.endpoints){
				tasks.add(new Callable<DeployResult>(){
					@Override
					public DeployResult call() throws Exception {
						HttpClient client=HttpClientBuilder.create().build();
						HttpPost post=new HttpPost("http://"+ep.address+":"+ep.port+"/"+CLOUDIFY_VERSION+"/deployments/applications/"+req.tspec.name+"/services/repl-management/invoke");
						log.fine("invoking custom command via REST :"+"http://"+ep.address+":"+ep.port+"/"+CLOUDIFY_VERSION+"/deployments/applications/"+req.tspec.name+"/services/repl-management/invoke");
						post.setEntity(new StringEntity(String.format("{\"commandName\":\"add-repl-space\",\"parameters\":[\"%s\",%d,%d,\"%s\"]}",req.tspec.name,1,0,ep.siteId),ContentType.APPLICATION_JSON));
						log.fine("  post entity:"+String.format("{\"commandName\":\"add-repl-space\",\"parameters\":[\"%s\",%d,%d,\"%s\"]}",req.tspec.name,1,0,ep.siteId));
						HttpResponse resp=client.execute(post);
						return new DeployResult(resp,ep);
					}
				});
			}

			List<Future<DeployResult>> results=new ArrayList<Future<DeployResult>>();
			try{
				results=esvc.invokeAll(tasks);
			}
			catch(InterruptedException e){
				throw new RuntimeException(e);
			}

			StringBuilder sb=new StringBuilder("Repl space deploy failed:");
			int initlength=sb.length();
			DeployResult dr=null;
			for(Future<DeployResult> future:results){
				try {
					dr=future.get();
				} catch (Exception e) {
					sb.append(dr.ep.address).append(" caught exception - ").append(e.getMessage()).append(",");
				}
				try{
					if(dr.response.getStatusLine().getStatusCode()!=200){
						String body=IOUtils.toString(dr.response.getEntity().getContent());
						sb.append(dr.ep.address).append(" returned status ").append(dr.response.getStatusLine().getStatusCode()).
						append(",reason=").append(dr.response.getStatusLine().getReasonPhrase()).append(",").append(body);
					}
					else{
						log.info("got response 200: "+dr.response.getStatusLine().toString());
					}
				}
				catch(Exception e){
					sb.append(dr.ep.address).append(" caught exception processing response body- ").append(e.getMessage()).append(",");
				}
			}

			if(sb.length()>initlength){//error caught
				throw new RuntimeException(sb.toString());
			}

		}
		finally{
			esvc.shutdown();
		}

	}


	private void deployGateways(final DeployRequest req) {
		ExecutorService esvc=Executors.newFixedThreadPool(req.endpoints.size());
		List<Callable<DeployResult>> tasks=new ArrayList<Callable<DeployResult>>();

		try{
			final Map<String,List<String>> gwaddresses=makeGatewayAddressMap(req);
			final String natArg=addressesToNat(gwaddresses);
			for(final CloudifyRestEndpoint ep:req.endpoints){
				tasks.add(new Callable<DeployResult>(){
					@Override
					public DeployResult call() throws Exception {
						HttpClient client=HttpClientBuilder.create().build();
						HttpPost post=new HttpPost("http://"+ep.address+":"+ep.port+"/"+CLOUDIFY_VERSION+"/deployments/applications/"+req.tspec.name+"/services/repl-gateway/invoke");
						log.info("invoking custom command via REST :"+"http://"+ep.address+":"+ep.port+"/"+CLOUDIFY_VERSION+"/deployments/applications/"+req.tspec.name+"/services/repl-gateway/invoke");
						String entityString=String.format("{\"commandName\":\"install-gateway\",\"parameters\":[\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"]}",req.tspec.name,ep.siteId,tspecToPairsArg(req.tspec),tspecToLookups(ep,req.tspec,gwaddresses),natArg);
						post.setEntity(new StringEntity(entityString,ContentType.APPLICATION_JSON));			
						log.info("  post entity:"+entityString);
						HttpResponse resp=client.execute(post);
						return new DeployResult(resp,ep);
					}

				});
			}

			List<Future<DeployResult>> results=new ArrayList<Future<DeployResult>>();
			try{
				results=esvc.invokeAll(tasks);
			}
			catch(InterruptedException e){
				throw new RuntimeException(e);
			}

			StringBuilder sb=new StringBuilder("Gateway deploy failed:");
			int initlength=sb.length();
			DeployResult dr=null;
			for(Future<DeployResult> future:results){
				try {
					dr=future.get();
				} catch (Exception e) {
					sb.append(dr.ep.address).append(" caught exception - ").append(e.getMessage()).append(",");
				}
				try{
					if(dr.response.getStatusLine().getStatusCode()!=200){
						String body=IOUtils.toString(dr.response.getEntity().getContent());
						sb.append(dr.ep.address).append(" returned status ").append(dr.response.getStatusLine().getStatusCode()).
						append(",reason=").append(dr.response.getStatusLine().getReasonPhrase()).append(",").append(body);
					}
					else{
						log.info("got response 200: "+dr.response.getStatusLine().toString());
					}
				}
				catch(Exception e){
					sb.append(dr.ep.address).append(" caught exception processing response body- ").append(e.getMessage()).append(",");
				}
			}

			if(sb.length()>initlength){//error caught
				throw new RuntimeException(sb.toString());
			}

		}
		finally{
			esvc.shutdown();
		}

	}

	//Make public to private map for gateway clients
	private String addressesToNat(Map<String, List<String>> gwaddresses) {
		StringBuilder sb=new StringBuilder("[");
		for(Map.Entry<String,List<String>> entry:gwaddresses.entrySet()){
			sb.append(entry.getValue().get(1)).append(":").append(entry.getValue().get(0)).append(",");
		}
		sb.deleteCharAt(sb.length()-1);
		sb.append("]");
		log.info("addressesToNat returning:"+sb.toString());
		return sb.toString();
	}

	private boolean waitForDeployments(DeployRequest req,long timeout){
		Map<CloudifyRestEndpoint,RestClient> clients=new HashMap<CloudifyRestEndpoint,RestClient>();
		Set<CloudifyRestEndpoint> deployed=new HashSet<CloudifyRestEndpoint>();

		for(CloudifyRestEndpoint ep:req.endpoints){
			try{
				clients.put(ep,new RestClient(new URL("http://"+ep.address+":"+ep.port),"","",CLOUDIFY_VERSION));
			}catch(Exception e){
				throw new RuntimeException(e);
			}
		}

		long start=System.currentTimeMillis();

		try{
			while(true){
				for(CloudifyRestEndpoint ep:req.endpoints){
					ApplicationDescription desc=clients.get(ep).getApplicationDescription(req.tspec.name);
					if(desc.getApplicationState()==DeploymentState.STARTED){
						deployed.add(ep);
						if(deployed.size()==req.endpoints.size())return true;
					}
					else if(desc.getApplicationState()==DeploymentState.FAILED){
						log.warning("deployment failed at ip:"+ep.address);
						return false;
					}
					if(start-System.currentTimeMillis()>timeout-2000){
						log.warning("deployment timeout");
						return false;
					}
				}
				Thread.sleep(2000);
			}
		}
		catch(Exception e){
			//rollback installs
			log.warning("exception on repl installation: rolling back -->"+e.getMessage());
			e.printStackTrace();
			rollbackApps(req);
			return false;
		}
	}

	/*
	 * Initiates deployment of the replication infrastructure.  Returns
	 * deployment ids for each application
	 */
	private void deployReplApps(DeployRequest req) {
		try{
			int gscCnt=req.maxMemorySize/req.maxVmSize;

			//launch deployments of repl-service app
			Map<CloudifyRestEndpoint,String> ids=new HashMap<CloudifyRestEndpoint,String>();
			for(CloudifyRestEndpoint endpoint:req.endpoints){
				final RestClient restClient=new RestClient(new URL("http://"+endpoint.address+":"+endpoint.port),"","",CLOUDIFY_VERSION);
				restClient.connect();
				//upload app
				File packed=Packager.packApplication(createDslReader(new File("C:\\gigaspaces\\src\\github\\repl-service\\cloudify\\repl")).readDslEntity(Application.class),new File("C:\\gigaspaces\\src\\github\\repl-service\\cloudify\\repl"));
				UploadResponse uploadResponse = restClient.upload(null, packed);

				//upload overrides
				String appUploadKey = uploadResponse.getUploadKey();
				File props=File.createTempFile("repl","properties");
				BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(props)));
				writer.write(String.format("containerCount=%d",gscCnt));
				writer.newLine();
				writer.write(String.format("gscMinSize=\"-Xms%dm\"",req.maxVmSize));
				writer.newLine();
				writer.write(String.format("gscMaxSize=\"-Xms%dm\"",req.maxVmSize));
				writer.newLine();
				writer.write(String.format("gscOtherOptions=\""+
						" -Dcom.gs.transport_protocol.lrmi.network-mapper=org.openspaces.repl.natmapper.ReplNatMapper" +
						" -Dcom.gs.transport_protocol.lrmi.network-mapping-file=/tmp/network_mapping.config\""));
				writer.newLine();
				writer.write(String.format("zones=\"%s-datazone\"",req.tspec.name));
				writer.newLine();
				writer.close();
				uploadResponse=restClient.upload(null,props);
				props.delete();

				//Install
				InstallApplicationRequest installreq=new InstallApplicationRequest();
				installreq.setApplcationFileUploadKey(appUploadKey);
				installreq.setApplicationOverridesUploadKey(uploadResponse.getUploadKey());
				installreq.setApplicationName(req.tspec.name);
				installreq.setDebugAll(false);
				installreq.setAuthGroups(""); // for now
				InstallApplicationResponse response=restClient.installApplication(req.tspec.name, installreq);
				ids.put(endpoint,response.getDeploymentID());
			}
		}
		catch(RestClientResponseException re){
			log.severe("caught exception installing apps:"+re.getReasonPhrase()+":"+re.getMessageFormattedText());
			log.severe("rolling back and failing");
			rollbackApps(req);
			throw new RuntimeException(re);
		}
		catch(Exception e){
			log.severe("caught exception installing apps:"+e.getMessage());
			log.severe("rolling back and failing");
			rollbackApps(req);
			if(e instanceof RuntimeException)throw (RuntimeException)e;
			throw new RuntimeException(e);
		}
	}

	private void rollbackApps(DeployRequest req){
		for(CloudifyRestEndpoint ep:req.endpoints){
			try{
				RestClient rc=new RestClient(new URL("http://"+ep.address+":"+ep.port), "","", CLOUDIFY_VERSION);
				rc.uninstallApplication(req.tspec.name,5);
			}
			catch(Exception e2){
				log.warning("caught exception rolling back "+ep.address);
				e2.printStackTrace();
			}
		}
	}


	public Admin getAdmin() {
		return admin;
	}

	public void setAdmin(Admin admin) {
		this.admin = admin;
	}


	protected void validateSites(DeployRequest req){
		//verify basic connectivity between sites
		for(CloudifyRestEndpoint ep:req.endpoints){
			if(!checkSite(req.tspec.name,"http://"+ep.address+":"+ep.port).equals("success"))throw new RuntimeException("failed to contact rest api at "+ep.toString());
		}
	}

	// Produces "pairs" arg from topo spec
	protected String tspecToPairsArg(TopologySpec tspec){
		if(tspec==null)return "[]";
		if(tspec.edges==null || tspec.edges.size()==0)return "[]";

		StringBuilder sb=new StringBuilder("[");
		for(TopologyEdge edge:tspec.edges){
			sb.append("[");
			sb.append("").append(edge.fromSite).append("").append(",");
			sb.append(edge.toSite);
			sb.append("],");
		}
		sb.deleteCharAt(sb.length()-1);
		sb.append("]");
		return sb.toString();
	}

	// Produces "lookups" arg from topo spec
	protected String tspecToLookups(CloudifyRestEndpoint ep,TopologySpec tspec,Map<String,List<String>> addresses){
		if(tspec==null || addresses==null)return "[]";
		StringBuilder sb=new StringBuilder("[");
		Set<String> done=new HashSet<String>();
		for(TopologyEdge edge:tspec.edges){
			for(String site:Arrays.asList(edge.fromSite,edge.toSite)){
				if(done.contains(site))continue;
				done.add(site);
				sb.append("[");
				sb.append("gwname:").append(site).append(",");
				if(ep.siteId.equals(edge.fromSite)){
					sb.append("address:").append(addresses.get(site).get(0)).append(",");
				}
				else{
					sb.append("address:").append(addresses.get(site).get(1)).append(",");
				}
				sb.append("discoport:").append(tspec.ports.get(site).get(0)).append(",");
				sb.append("commport:").append(tspec.ports.get(site).get(1));
				sb.append("],");
			}
		}
		sb.deleteCharAt(sb.length()-1); //trim comma
		sb.append("]");
		return sb.toString();
	}

	protected String checkSite(String appName,String url){
		try{
			HttpGet get=new HttpGet(url+"/service/applications");
			log.info("pinging "+get.getURI());
			HttpResponse response=client.execute(get);
			ObjectMapper om=new ObjectMapper();
			JsonNode node=om.readTree(response.getEntity().getContent());
			String status=node.get("status").getValueAsText();
			log.info("got response "+(status==null?"null":status));
			for(Iterator<JsonNode> it=node.get("response").getElements();it.hasNext();){
				if(it.next().equals(appName))throw new RuntimeException("topology already running");
			}
			return status;
		}
		catch(Exception e){
			if(e instanceof RuntimeException)throw (RuntimeException)e;
			throw new RuntimeException(e);
		}
	}

	private static DSLReader createDslReader(final File applicationFile) {
		final DSLReader dslReader = new DSLReader();
		final File dslFile = DSLReader.findDefaultDSLFile(DSLUtils.APPLICATION_DSL_FILE_NAME_SUFFIX, applicationFile);
		dslReader.setDslFile(dslFile);
		dslReader.setCreateServiceContext(false);
		dslReader.addProperty(DSLUtils.APPLICATION_DIR, dslFile.getParentFile().getAbsolutePath());
		return dslReader;
	}

	public static void main(String[] args)throws Exception{

		ReplicationRESTController ctl=new ReplicationRESTController();

		HashMap body=new HashMap();
		Map site1=new HashMap(); site1.put("rest-api", "ec2-54-221-106-51.compute-1.amazonaws.com:8100");site1.put("site-id","EC2");
		Map site2=new HashMap(); site2.put("rest-api", "15.185.186.251:8100");site2.put("site-id","HPCS");
		List sites=new ArrayList();sites.add(site1);sites.add(site2);
		body.put("cloudify-instances",sites);

		HashMap tspec=new HashMap();tspec.put("name","my-topo");
		List edges=new ArrayList();
		HashMap edge1=new HashMap();edge1.put("fromSite","EC2");edge1.put("fromSpace","test");edge1.put("toSite","HPCS");edge1.put("toSpace","test");
		edges.add(edge1);
		tspec.put("edges",edges);

		HashMap ports=new HashMap();
		HashMap port1=new HashMap();port1.put("discovery", DEFAULT_DISCOVERY_PORT);port1.put("data",DEFAULT_DATA_PORT);
		ports.put("EC2",port1);
		HashMap port2=new HashMap();port2.put("discovery", DEFAULT_DISCOVERY_PORT);port2.put("data",DEFAULT_DATA_PORT);
		ports.put("HPCS",port2);
		tspec.put("ports",ports);

		body.put("topology-spec",tspec);
		body.put("initial-memory-size", "256m");
		body.put("max-memory-size", "512m");
		body.put("highly-available","false");
		body.put("max-vm-size","512");
		body.put("batch-size","1024");
		body.put("send-interval","1000");

		ctl.deployTopology("my-topo",body);


		//deploy service test
		/*		RestClient rc=new RestClient(new URL("http://localhost:8100"),"","",CLOUDIFY_VERSION);
		rc.connect();
		InstallServiceRequest r=new InstallServiceRequest();
		File packed=Packager.pack(new File("c:\\gigaspaces\\src\\github\\cloudify-recipes\\services\\tomcat"));
		final UploadResponse uploadResponse = rc.upload(null, packed);
		final String uploadKey = uploadResponse.getUploadKey();
		r.setServiceFolderUploadKey(uploadKey);
		r.setAuthGroups("");
		r.setServiceFileName("tomcat-service.groovy");
		InstallServiceResponse installServiceRespone = rc.installService("test","yomama", r);        
		System.out.println(uploadKey);
		//r.set
		//rc.installService("default","test",);
		 */
		/*
		HttpClient client=HttpClientBuilder.create().build();
		HttpGet get=new HttpGet("http://ec2-54-197-25-132.compute-1.amazonaws.com:8100/");
		HttpResponse response=client.execute(get);
		InputStream is=response.getEntity().getContent();
		BufferedReader rdr=new BufferedReader(new InputStreamReader(is));
		String line="";
		while((line=rdr.readLine())!=null){
			System.out.println(line);
		}
		 */

		/*ObjectMapper om=new ObjectMapper();
		JsonNode node=om.readTree(is);
		System.out.println(node.get("response").get(1));*/



		/*
		ObjectMapper om=new ObjectMapper();
		JsonNode node=om.readTree("{\"response\":[\"app1\",\"app2\"]}");
		for(Iterator<JsonNode> it=node.get("response").getElements();it.hasNext();){
			System.out.println(it.next().getValueAsText());
		}
		 */
	}

}

class DeployRequest{
	public List<CloudifyRestEndpoint> endpoints;
	public TopologySpec tspec;

	public int initMemorySize;
	public int maxMemorySize;
	public boolean highlyAvailable=true;
	public int maxVmSize;
	public int batchSize;
	public int sendInterval;

	public DeployRequest(Map body){
		List<Map> inst=(List<Map>)body.get("cloudify-instances");
		if(inst==null || inst.size()==0)throw new RuntimeException("validation error:no cloudify instances provided");
		for(Map<String,String> map:inst){
			String api=map.get("rest-api");
			String id=map.get("site-id");
			if(endpoints==null)endpoints=new ArrayList<CloudifyRestEndpoint>();
			endpoints.add(new CloudifyRestEndpoint(id,api));
		}

		Map ts=(Map)body.get("topology-spec");
		if(ts==null)throw new RuntimeException("validation error: no topology spec");
		tspec=new TopologySpec(ts);

		initMemorySize=parseMemoryString((String)body.get("initial-memory-size"));
		maxMemorySize=parseMemoryString((String)body.get("max-memory-size"));
		Boolean ha=Boolean.parseBoolean((String)body.get("highly-available"));
		if(ha!=null)highlyAvailable=ha;
		maxVmSize=Integer.parseInt((String)body.get("max-vm-size"));  // in mb units
		Integer bs=Integer.parseInt((String)body.get("batch-size"));
		if(bs!=null)batchSize=bs;
		Integer si=Integer.parseInt((String)body.get("send-interval"));
		if(si!=null)sendInterval=si;
	}

	private int parseMemoryString(String string) {
		if(string.length()<2)throw new RuntimeException("invalid memory string:"+string);
		if(Character.isDigit(string.charAt(string.length()-1)))throw new RuntimeException("invalid memory string: units required (m or g)");
		int base=Integer.parseInt(string.substring(0,string.length()-1));
		if(string.substring(string.length()-1).toLowerCase().equals("g"))base*=1024;
		//default is mb
		return base;
	}
}

class CloudifyRestEndpoint{
	public String address;
	public int port=8100;
	public String siteId;

	public CloudifyRestEndpoint(String id,String address_in){
		String[] toks=address_in.split(":");
		this.siteId=id;
		this.address=toks[0];
		if(toks.length>1)this.port=Integer.parseInt(toks[1]);
	}
	public String toString(){
		return siteId+"@"+address+":"+port;
	}
}

class TopologySpec{
	public String name;
	public Set<TopologyEdge> edges=new HashSet<TopologyEdge>();
	public Map<String,List<Integer>> ports=new HashMap<String,List<Integer>>();

	public TopologySpec(){}

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
			ports.put(entry.getKey(),plist);
		}
	}
}

class TopologyEdge{
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

class DeployResult{
	HttpResponse response;
	CloudifyRestEndpoint ep;

	public DeployResult(HttpResponse response, CloudifyRestEndpoint ep) {
		super();
		this.response = response;
		this.ep = ep;
	}
}