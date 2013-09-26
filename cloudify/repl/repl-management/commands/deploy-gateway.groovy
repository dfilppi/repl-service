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

import org.cloudifysource.dsl.utils.ServiceUtils
import org.cloudifysource.dsl.context.ServiceContextFactory
import org.openspaces.admin.AdminFactory
import org.openspaces.admin.application.config.ApplicationConfig
import org.openspaces.admin.pu.config.ProcessingUnitConfig
import org.openspaces.admin.space.SpaceDeployment
import groovy.util.ConfigSlurper;


context=ServiceContextFactory.serviceContext
config = new ConfigSlurper().parse(new File(context.serviceName+"-service.properties").toURL())

spacename=context.attributes.thisInstance["spacename"]
localgwname=context.attributes.thisInstance["localgwname"]
targets=context.attributes.thisInstance["targets"]
sources=context.attributes.thisInstance["sources"]
lookups=[]
i=0
while (true){
	lookup=context.attributes.thisInstance["lookup"+i]
	if(lookup==null)break
	lookups.add(lookup)
}

assert (spacename!=null),"space name must not be null"
assert (locagwname!=null),"local gateway name must not be null"

//CREATE PU

//DEPLOY

// find gsm
admin=new AdminFactory().addLocators("127.0.0.1:${config.lusPort}").createAdmin();
gsm=admin.gridServiceManagers.waitForAtLeastOne(10,TimeUnit.SECONDS)
assert gsm!=null

// make sure there are GSCs
gscs=admin.gridServiceContainers
gscs.waitFor(1,5,TimeUnit.SECONDS)
assert (gscs.size!=0),"no containers found"

//deploy
sd=new SpaceDeployment(name)
sd.clusterSchema("partitioned-sync2backup")
sd.numberOfInstances(partitions.toInteger())
sd.numberOfBackups(backups.toInteger())
sd.maxInstancesPerMachine(1)
gsm.deploy(sd)

admin.close()
return true
