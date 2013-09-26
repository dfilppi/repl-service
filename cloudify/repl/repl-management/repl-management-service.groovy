/*******************************************************************************
* Copyright (c) 2012 GigaSpaces Technologies Ltd. All rights reserved
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

import org.openspaces.admin.AdminFactory
import org.openspaces.admin.Admin
import java.util.concurrent.TimeUnit
import util

/**

 Replication as a service manager.  Requires the xap-container recipe
running to provide memory storage for replication.

**/
service {
	extend "../../xap9.x/xap-management"
	name "repl-management"

	compute {
       	 	template "MEDIUM_LINUX"
    	}

	lifecycle{


		//postStart "postStart.groovy"

	}

	customCommands ([
	//Public entry points
		"create-space"	: {name,partitions,backups->
			util.invokeLocal(context,"_create-space", [
				"deploy-space-name":name,
				"deploy-space-partitions":partitions,
				"deploy-space-backups":backups
				])
			},
		"destroy-space"	: {name->
			util.invokeLocal(context,"_destroy-space", [
				"deploy-space-name":name,
				])
			},
		"deploy-gateway" : { spacename,localgwname,targets,sources,String...lookups->
			def gwargs=["spacename":spacename,"localgwname":localgwname]
			gwargs["targets"]=targets
			gwargs["sources"]=sources
			lookups.eachWithIndex(){lookup,i->
				gwargs.put("lookup"+i,lookup)
			}	
			util.invokeLocal(context,"_deploy-gateway",gwargs)
			},


	//Private entry points

		"_create-space" : "commands/create-space.groovy",
		"_destroy-space" : "commands/destroy-space.groovy",
		"_deploy-gateway" : "commands/deploy-gateway.groovy",

	])


	userInterface {
		metricGroups = ([
		])

		widgetGroups = ([
		])
	}
}


