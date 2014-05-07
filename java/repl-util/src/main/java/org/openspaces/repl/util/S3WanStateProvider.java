package org.openspaces.repl.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openspaces.repl.model.SiteDetails;
import org.openspaces.repl.model.TopologySpec;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.DeleteBucketRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class S3WanStateProvider implements WanStateProvider {
	AmazonS3 _s3=null;
	AWSCredentialsProvider cp=null;
	Region region=null;
	String prefix=null;

	public static final String CONFIG_ACCESS_KEY="accessKey";
	public static final String CONFIG_SECRET_KEY="secretKey";
	public static final String CONFIG_REGION="region";
	public static final String CONFIG_BUCKET_PREFIX="prefix";
	
	private static final String DEFAULT_BUCKET_PREFIX="xap-replstate-";
	private static final String DETAILS_PREFIX="details-";
	private static final String SPEC_OBJECT="spec";

	public S3WanStateProvider(){
	}
	
	public S3WanStateProvider(Map<String,Object> cfg){
		this.setConfig(cfg);
	}

	@Override
	public void setConfig(final Map<String,Object> config){
		String rgn=(String)config.get(CONFIG_REGION);
		if(rgn!=null)region=Region.getRegion(Regions.fromName(rgn));
		prefix=DEFAULT_BUCKET_PREFIX;
		if((String)config.get(CONFIG_BUCKET_PREFIX)!=null)prefix=(String)config.get(CONFIG_BUCKET_PREFIX);
		cp=new AWSCredentialsProvider(){
			@Override
			public AWSCredentials getCredentials() {
				return new BasicAWSCredentials((String)config.get(CONFIG_ACCESS_KEY),
						(String)config.get(CONFIG_SECRET_KEY));
			}
			@Override
			public void refresh() {
			}
		};
	}

	@Override
	public TopologySpec getTopology(String name) {
		if(tooShort(name))throw new IllegalArgumentException("null or zero length topo name");
		if(!topologyExists(name))return null;
		TopologySpec spec=null;
		List<SiteDetails> details=new ArrayList<SiteDetails>();
		
		ObjectListing objs=s3().listObjects(nameToBucket(name));
		if(objs==null)return null;
		
		for(S3ObjectSummary sum:objs.getObjectSummaries()){
			String key=sum.getKey();
			if(key.equals(SPEC_OBJECT)){
				S3Object obj=s3().getObject(new GetObjectRequest(nameToBucket(name),key));
				try{
					spec=(TopologySpec)new ObjectInputStream(obj.getObjectContent()).readObject();
				}
				catch(Exception e){
					throw new RuntimeException(e);
				}
			}
			else if(key.startsWith(DETAILS_PREFIX)){
				S3Object obj=s3().getObject(new GetObjectRequest(nameToBucket(name),key));
				String site=key.substring(DETAILS_PREFIX.length());
				try {
					details.add((SiteDetails)new ObjectInputStream(obj.getObjectContent()).readObject());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		spec.sites.clear();
		for(SiteDetails dtls:details){
			spec.sites.add(dtls);
		}
		return spec;
	}

	@Override
	public boolean topologyExists(String name){
		if(tooShort(name))throw new IllegalArgumentException("null or zero length name");
		return s3().doesBucketExist(nameToBucket(name));
	}
	
	@Override
	public void updateDetails(String topoName, String siteId, SiteDetails details) {
		if(tooShort(topoName))throw new IllegalArgumentException("null or zero length topoName");
		if(tooShort(siteId))throw new IllegalArgumentException("null or zero length siteId");
		if(details==null)throw new IllegalArgumentException("null details");
		if(!topologyExists(topoName))return ; //ignore
		
		putObject(topoName, DETAILS_PREFIX+siteId, details);

	}

	@Override
	public boolean createTopology(TopologySpec spec, boolean overwrite) {
		if(spec==null)throw new IllegalArgumentException("null or zero length topo name");
		boolean exists=topologyExists(spec.name);
		if(!overwrite && exists){
			return false;
		}
		else if(exists){
			deleteBucket(nameToBucket(spec.name));
		}
		//create
		Bucket b=s3().createBucket(new CreateBucketRequest(nameToBucket(spec.name)));
		if(b==null)return false;

		PutObjectResult res=putObject(spec.name,SPEC_OBJECT,spec);
		if(res==null)return false;
		return true;
	}
	
	private PutObjectResult putObject(String topoName,String key,Serializable obj){
		ByteArrayOutputStream baos=new ByteArrayOutputStream();
		try {
			new ObjectOutputStream(baos).writeObject(obj);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		ObjectMetadata od=new ObjectMetadata();
		od.setContentType("application/octet-stream");
		od.setContentLength(baos.size());
		PutObjectResult res=s3().putObject(new PutObjectRequest(nameToBucket(topoName),key,new ByteArrayInputStream(baos.toByteArray()),od));
		return res;
	}
	
	/**
	 * Delete S3 bucket, including any contents
	 * @param name
	 */
	private void deleteBucket(String name){
		ObjectListing objs=s3().listObjects(name);
		for(S3ObjectSummary obj:objs.getObjectSummaries()){
			s3().deleteObject(name,obj.getKey());
		}
		s3().deleteBucket(name);
	}

	//lazy creation
	private AmazonS3 s3(){
		if(cp==null)throw new RuntimeException("no config set");
		if(_s3==null){
			_s3=new AmazonS3Client(cp);
			if(region!=null)_s3.setRegion(region);
		}
		return _s3;
	}
	@Override
	public void deleteTopology(String name) {
		if(tooShort(name))throw new IllegalArgumentException("null or zero length topology name");
		s3().deleteBucket(new DeleteBucketRequest(nameToBucket(name)));
	}
	
	private String nameToBucket(String name){
		return String.format("%s%s",prefix,name);
	}

	private boolean tooShort(String val) {
		if(val==null || val.length()==0)return true;
		return false;
	}
	
	public static void main(String[] args){
		Map<String,Object> cfg=new HashMap<String,Object>();
		cfg.put(S3WanStateProvider.CONFIG_ACCESS_KEY,"");
		cfg.put(S3WanStateProvider.CONFIG_SECRET_KEY,"");
		S3WanStateProvider p=new S3WanStateProvider(cfg);
		
		TopologySpec s=new TopologySpec();
		s.setName("test");
		p.createTopology(s, true);
		

/*		SiteDetails d=new SiteDetails();
		d.setGatewayPublicAddress("xxxxxxxxxx");
		d.setCommport(999);
		d.setDiscoport(233);
		d.setSiteId("B");
		p.updateDetails("mytopo","B",d);
		
		TopologySpec s2=p.getTopology("mytopo");
		System.out.println(s2.getName());
	*/	
	}
}
