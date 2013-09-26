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

name=context.attributes.thisInstance["deploy-space-name"]
partitions=context.attributes.thisInstance["deploy-space-partitions"]
backups=context.attributes.thisInstance["deploy-space-backups"]

assert (name!=null),"name must not be null"
if(partitions==null||partitions<=0)partitions ="1"
if(backups==null||backups<0)backups="0"

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
