/*******************************************************************************
* Copyright (c) 2013 GigaSpaces Technologies Ltd. All rights reserved
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/
import java.util.concurrent.TimeUnit
import java.util.UUID
import org.cloudifysource.dsl.utils.ServiceUtils
import org.cloudifysource.dsl.context.ServiceContextFactory
import org.openspaces.admin.AdminFactory
import org.openspaces.admin.application.config.ApplicationConfig
import org.openspaces.admin.pu.config.ProcessingUnitConfig
import org.openspaces.admin.space.SpaceDeployment
import groovy.util.ConfigSlurper;
import groovy.text.SimpleTemplateEngine
import org.openspaces.core.gateway.GatewayTarget
import org.openspaces.admin.space.Space
import util


def context=ServiceContextFactory.serviceContext
def config = new ConfigSlurper().parse(new File(context.serviceName+"-service.properties").toURL())

def puname=context.serviceName
def spacename=config.spaceName
def localgwname=config.localGatewayName
def targets=config.targets
def sources=config.sources
def lookups=config.lookups


println "deploy-gateway called: mgmt service=${config.managementService} puname='${puname}' spacename='${spacename}' localgwname='${localgwname}' targets='${targets}' sources='${sources}'" 

assert (spacename!=null),"space name must not be null"
assert (localgwname!=null),"local gateway name must not be null"

thisService=util.getThisService(context)

//Get locator(s)
mgmt=context.waitForService(config.managementService,1,TimeUnit.MINUTES)
assert mgmt!=null,"No management services found"
locators=""
lusnum=0
println "found ${mgmt.instances.length} mgmt instances"
mgmt.instances.each{
	def lusname="lus${it.instanceId}"
	thisService.invoke("update-hosts",it.hostAddress,lusname as String)
	locators+="${lusname}:${config.lusPort},"
}
println "locators = ${locators}"
assert locators!="","failed to get locators"

//CREATE PU
pudir=config.installDir+"/gwpu/META-INF/spring"
new AntBuilder().sequential(){
	delete(dir:pudir)
	mkdir(dir:pudir)
}

def binding=[:]
binding['localgwname']=localgwname
binding['localspaceurl']="jini://${context.privateAddress}:${config.lusPort}/${spacename}"
binding['targets']=targets
binding['sources']=sources
binding['lookups']=lookups	

def engine = new SimpleTemplateEngine()
def putemplate = new File('templates/gateway-pu.xml')
def template = engine.createTemplate(putemplate).make(binding)
new File("${pudir}/pu.xml").withWriter{ out->
	out.write(template.toString())
}

//DEPLOY

// find gsm
def admin=new AdminFactory().useDaemonThreads(true).addLocators(locators).createAdmin();
def gsm=admin.gridServiceManagers.waitForAtLeastOne(1,TimeUnit.MINUTES)
assert gsm!=null

// Make sure the space exists
Space space=admin.getSpaces().waitFor(spacename,1,TimeUnit.MINUTES)
assert space!=null,"failed to locate space ${spacename}"

//deploy
def pucfg=new ProcessingUnitConfig()
pucfg.setProcessingUnit("${config.installDir}/gwpu")
pucfg.setName(puname)
pucfg.setZones(["${context.applicationName}.${context.serviceName}" as String] as String[]) //only deploy to this gsc
def pu=gsm.deploy(pucfg)
def i=0
while(pu.instances.length==0){
	Thread.sleep(1000)
	i++;
	assert i<11,"timed out waiting for gateway deployment"
}

// add gateway to space
if(targets!=null){
	targets.each{ target->
		println "adding target ${target}"
		GatewayTarget gwTarget = new GatewayTarget(target)
		space.getReplicationManager().addGatewayTarget(gwTarget)
	}
}


return true


