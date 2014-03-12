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
import org.openspaces.repl.model.SiteDetails;
import org.openspaces.repl.model.TopologySpec
import groovy.util.ConfigSlurper;
import groovy.text.SimpleTemplateEngine
import org.openspaces.core.gateway.GatewayTarget
import org.openspaces.admin.space.Space
import util


def context=ServiceContextFactory.serviceContext
def config = new ConfigSlurper().parse(new File(context.serviceName+"-service.properties").toURL())

def puname=context.serviceName
def spacename=args[0]
def localgwname=args[1]
def pairs=Eval.me(util.quoteAlnum(args[2]))
def lookups=Eval.me(util.quoteAlnum(args[3]))
def natmappings=Eval.me(util.quoteAlnum(args[4])) //map from public to private ip
def saveargs=args

//Put remote hosts in hosts file and lookups
lookups.each{
        if(localgwname!=it['gwname']){
                context.attributes.thisInstance["update-hosts-hostsline"]=["${it['address']}","xaprepl-${spacename}-${it['gwname']}"]
                run(new File("commands/update-hosts.groovy"))
        }
}

//Edit lookups to use synthetic host names.  This so lookups aren't
//tied to IPs in case of relocation
def mycommport=0
def mydiscoport=0
def l2="["
lookups.each{
		l2+="[gwname:${it['gwname']}"
		if(localgwname==it['gwname']){
			l2+=",address:localhost"
			mycommport=it['commport']
			mydiscoport=it['discoport']
		}
		else{
			l2+=",address:xaprepl-${spacename}-${it['gwname']}"
		}
		l2+=",discoport:${it['discoport']}"
		l2+=",commport:${it['commport']}],"
}
l2+="]"
println l2
saveargs[3]=l2
	
//call inherited command
run( new File('install_gateway.groovy') 
   , saveargs as String[] ) 

//now handle repl persistence if configured
//publish to state provider

if(config.stateProvider!=null){
	def state=Class.forName(config.stateProvider)
	state=state.newInstance()
	state.setConfig(config.stateConfig)

   //if topo state not there , fail
	assert state.topologyExists(spacename),"topology state must be predefined"
	def details=new SiteDetails(localgwname,context.publicAddress,context.privateAddress,mycommport.toInteger(),mydiscoport.toInteger())
	state.updateDetails(spacename,localgwname,details)
}
return true
