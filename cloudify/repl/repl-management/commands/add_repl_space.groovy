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
import org.cloudifysource.utilitydomain.context.ServiceContextFactory
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

def spacename=context.attributes.thisInstance["repl-space-name"]
def primaries=context.attributes.thisInstance["repl-space-primaries"]
def backups=context.attributes.thisInstance["repl-space-backups"]
def gwname=context.attributes.thisInstance["repl-space-gwname"]

assert (spacename!=null),"space name must not be null"

thisService=util.getThisService(context)
instanceId=context.instanceId

mgmt=util.getThisService(context)
assert mgmt!=null,"No management services found"

//CREATE PU
pudir=config.installDir+"/deploy/repl-space-pu/META-INF/spring"
new AntBuilder().sequential(){
	delete(dir:pudir)
	mkdir(dir:pudir)
}

def binding=[:]
binding['spacename']=spacename
binding['lname']=gwname

def engine = new SimpleTemplateEngine()
def putemplate = new File('templates/pu.xml')
def template = engine.createTemplate(putemplate).make(binding)
new File("${pudir}/pu.xml").withWriter{ out->
	out.write(template.toString())
}

//DEPLOY

// find gsm
def admin=new AdminFactory().useDaemonThreads(true).addLocators("${mgmt.hostAddress}:${config.lusPort}").createAdmin();
def gsm=admin.gridServiceManagers.waitForAtLeastOne(1,TimeUnit.MINUTES)
assert gsm!=null

//deploy -- only basics for now
def pucfg=new ProcessingUnitConfig()
pucfg.setProcessingUnit("${config.installDir}/deploy/repl-space-pu")
pucfg.setClusterSchema("partitioned-sync2backup")
pucfg.setNumberOfInstances(primaries.toInteger())
pucfg.setNumberOfBackups(backups.toInteger())
pucfg.setName(spacename)
pucfg.addZone("${context.applicationName}.${config.containerServiceName}.gsc")
def pu=gsm.deploy(pucfg)
def i=0
while(pu.instances.length==0){
        Thread.sleep(1000)
        i++;
        if(i>120){
                gsm.undeploy(spacename)
                throw new RuntimeException("timed out waiting for space deployment")
        }
}

return true


