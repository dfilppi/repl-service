/*******************************************************************************
* Copyright (c) 2011 GigaSpaces Technologies Ltd. All rights reserved
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
import groovy.util.ConfigSlurper;

if(ServiceUtils.isWindows())assert false,"NOT IMPLEMENTED FOR WINDOWS"


context=ServiceContextFactory.serviceContext
config = new ConfigSlurper().parse(new File(context.serviceName+"-service.properties").toURL())

def hostsline=context.attributes.thisInstance["update-hosts-hostsline"]
assert hostsline!=null, "no etc hosts entry supplied"

def address=hostsline[0]
def lines=[]
new File("/etc/hosts").eachLine{ line->
	def toks=line.split()
	if(toks[0]==address)return
	def found=false
	toks[1..toks.size()-1].each{ tok->
		hostsline[1..hostsline.size()-1].each{ h->
			if(tok==h)found=true
		}
		if(found)return
	}
	if(found)return
	lines.add(line)  //strip any old defs of same address
}
new File("/etc/hosts").withWriter{out->
	lines.each{ line->
		out.writeLine(line)
	}
	out.writeLine(hostsline.join(" "))
}

