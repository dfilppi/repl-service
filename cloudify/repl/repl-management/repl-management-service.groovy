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
	extend "../../xap9x/xap-management"
	name "repl-management"

	compute {
       	 	template "MEDIUM_LINUX"
    	}

	lifecycle{

	}


	customCommands ([
//Public entry points

		"add-repl-space": {name, primaries, backups, gwname ->
			util.invokeLocal(context,"_add-repl-space", [
				"repl-space-name":name,
				"repl-space-primaries":primaries,
				"repl-space-backups":backups,
				"repl-space-gwname":gwname
			])
		 },

		//Actual parameterized calls
		"_add-repl-space"	: "commands/add_repl_space.groovy",

	])

	userInterface {
		metricGroups = ([
		])

		widgetGroups = ([
		])
	}
}


