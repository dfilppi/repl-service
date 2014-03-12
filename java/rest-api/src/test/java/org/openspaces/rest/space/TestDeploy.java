package org.openspaces.rest.space;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.HashSet;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;

public class TestDeploy{

	@Test
	public void testParse() throws Exception{
		String json="{ \"cloudify-instances\":[ "+
                "{"+
                  "\"rest-api\":\"15.5.22.6:8100\","+
                  "\"site-id\":\"NEW_YORK\""+
                "},"+
                "{"+
                   "\"rest-api\":\"15.35.33.3:8100\","+
                   "\"site-id\":\"LONDON\""+
                "}"+
            "],"+
            "\"topology-spec\":{"+
                "\"name\":\"my-topo\","+
                "\"edges\":["+
                     "{\"fromSite\":\"NEW_YORK\",\"fromSpace\":\"test\",\"toSite\":\"LONDON\",\"toSpace\":\"test2\"}"+
                    "],"+
                "\"ports\":{"+
                	 "\"NEW_YORK\":{\"discovery\":\"10000\",\"data\":\"10001\"}"+
                    "}"+
            "},"+
           "\"initial-memory-size\":\"10G\","+
           "\"max-memory-size\":\"50G\","+
           "\"highly-available\":\"true\","+
           "\"max-vm-size\":\"4096\","+
           "\"batch-size\":\"100\","+
           "\"send-interval\":\"1000\""+
          "}";		

		ObjectMapper om=new ObjectMapper();
		HashMap map=om.readValue(json,HashMap.class);

		DeployRequest req=new DeployRequest(map);
		assertEquals(50*1024,req.maxMemorySize);
		assertEquals(100,req.batchSize);
		assertEquals(1000,req.sendInterval);
		assertEquals(true,req.highlyAvailable);
		assertEquals(10*1024,req.initMemorySize);
		
		assertEquals(2,req.endpoints.size());
		assertEquals("15.5.22.6",req.endpoints.get(0).address);
		assertEquals(8100,req.endpoints.get(0).port);
		assertEquals("NEW_YORK",req.endpoints.get(0).siteId);
		assertEquals(new Integer(10000),req.tspec.ports.get("NEW_YORK").get(0));
	}
	
	@Test
	public void testArgCreation(){
		ReplicationRESTController controller=new ReplicationRESTController();

		TopologyEdge edge=new TopologyEdge("A","test","B","test");
		TopologySpec tspec=new TopologySpec();
		tspec.edges=new HashSet<TopologyEdge>();
		tspec.edges.add(edge);
		assertEquals("[[A,B]]",controller.tspecToPairsArg(tspec));
		
		edge=new TopologyEdge("B","test","A","test");
		tspec.edges.add(edge);
		assertTrue(controller.tspecToPairsArg(tspec).contains("[A,B]"));
		assertTrue(controller.tspecToPairsArg(tspec).contains("[B,A]"));
	}
}
