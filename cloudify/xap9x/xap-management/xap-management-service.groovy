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

Manages XAP management node(s).  Also starts Web UI.

**/
service {

	name "xap-management"
	type "APP_SERVER"
	icon "xap.png"
	elastic false
	numInstances (context.isLocalCloud()?1:2 )
	minAllowedInstances 1
	maxAllowedInstances 2

	compute {
       	 	template "${template}"
    	}

	lifecycle{


		install "xap_install.groovy"

		start "xap_start.groovy"

	        startDetectionTimeoutSecs 60
        	startDetection {
            		ServiceUtils.isPortOccupied(uiPort)
        	}

		details {
			def currPublicIP
			
			if (  context.isLocalCloud()  ) {
				currPublicIP = InetAddress.localHost.hostAddress
			}
			else {
				currPublicIP =context.getPublicAddress()
			}
	
			def applicationURL = "http://${currPublicIP}:${uiPort}"
		
				return [
					"Management UI":"<a href=\"${applicationURL}\" target=\"_blank\">${applicationURL}</a>"
				]
		}
		
		monitors {
			def admin = new AdminFactory()
				.addLocators("127.0.0.1:"+lusPort)
				.create();
			def gscs=admin.gridServiceContainers
			gscs.waitFor(200,1,TimeUnit.MILLISECONDS)
			def pus=admin.processingUnits

			//memory & cpu
			def vmstats=admin.virtualMachines.statistics

			return [
				"Container Count":gscs.size,
				"PU Count":pus.size,
				"Heap Used %":vmstats.memoryHeapUsedPerc,
				"Heap Used GB":vmstats.memoryHeapUsedInGB,
				"GC Collection Time":vmstats.gcCollectionTime
				]
		}
	}

	customCommands ([
//Public entry points

		"deploy-pu": {puname, puurl,schema,partitions,backups,maxpervm,maxpermachine ->
			util.invokeLocal(context,"_deploy-pu", [
				"deploy-pu-puurl":puurl,
				"deploy-pu-schema":schema,
				"deploy-pu-partitions":partitions,
				"deploy-pu-backups":backups,
				"deploy-pu-maxpervm":maxpervm,
				"deploy-pu-maxpermachine":maxpermachine,
				"deploy-pu-puname":puname
			])
		 },
		"deploy-epu": {puname, puurl,schema,partitions,backups,maxmemmb,maxcores,memcapacity,numcores ->
			util.invokeLocal(context,"_deploy-epu", [
				"deploy-epu-puurl":puurl,
				"deploy-epu-schema":schema,
				"deploy-epu-partitions":partitions,
				"deploy-epu-backups":backups,
				"deploy-epu-maxmemmb":maxmemmb,
				"deploy-epu-maxcores":maxcores,
				"deploy-epu-memcapacity":memcapacity,
				"deploy-epu-numcores":numcores
			])
		 },
		"deploy-pu-basic": {puurl->
			util.invokeLocal(context,"_deploy-pu", [
				"deploy-pu-puurl":puurl,
				"deploy-pu-schema":"none",
				"deploy-pu-partitions":1,
				"deploy-pu-backups":0,
				"deploy-pu-maxpervm":1,
				"deploy-pu-maxpermachine":1,
				"deploy-pu-puname":(new File(puurl).name)
			])
		},
					
		"deploy-grid"	: {name,schema,partitions,backups,maxpervm,maxpermachine->
			util.invokeLocal(context,"_deploy-grid", [
				"deploy-grid-name":name,
				"deploy-grid-schema":schema,
				"deploy-grid-partitions":partitions,
				"deploy-grid-backups":backups,
				"deploy-grid-maxpervm":maxpervm,
				"deploy-grid-maxpermachine":maxpermachine
			])
		},
		"undeploy-grid" : { name ->
			util.invokeLocal(context,"_undeploy-grid", [
				"undeploy-grid":name
			])
		},
		"deploy-gateway" : { puname,spacename,localgwname,targets,sources,String...lookups->
			def gwargs=["deploy-gateway-puname":puname,"deploy-gateway-spacename":spacename,"deploy-gateway-localgwname":localgwname]
			gwargs["deploy-gateway-targets"]=targets
			gwargs["deploy-gateway-sources"]=sources
			lookups.eachWithIndex(){lookup,i->
				gwargs.put("deploy-gateway-lookup"+i,lookup)
			}	
			util.invokeLocal(context,"_deploy-gateway",gwargs)
			},



		//Actual parameterized calls
		"_deploy-pu"	: "commands/deploy-pu.groovy",
		"_deploy-epu"	: "commands/deploy-epu.groovy",
		"_deploy-grid"	: "commands/deploy-grid.groovy",
		"_undeploy-grid": "commands/undeploy-grid.groovy",
		"_deploy-gateway": "commands/deploy-gateway.groovy"

	])


	userInterface {
		metricGroups = ([
			metricGroup {

				name "Containers"

				metrics([
					"Container Count",
				])
			},
			metricGroup {

				name "Processing Units"

				metrics([
					"PU Count",
				])
			},
			metricGroup {

				name "Java VM Stats"

				metrics([
					"Heap Used %",
					"Heap Used GB",
					"GC Collection Time"
				])
			}
		]
		)

		widgetGroups = ([
			widgetGroup {
				name "Container Count"
				widgets ([
					barLineChart{
						metric "Container Count"
						axisYUnit Unit.REGULAR
					},
				])
			},
			widgetGroup {
				name "PU Count"
				widgets ([
					barLineChart{
						metric "PU Count"
						axisYUnit Unit.REGULAR
					},
				])

			},
			widgetGroup {
				name "VM Heap Used %"
				widgets ([
					balanceGauge{metric = "Heap Used %"},
					barLineChart{
						metric "Heap Used %"
						axisYUnit Unit.PERCENTAGE
					}
				])
			},
			widgetGroup {
				name "VM Heap Used GB"
				widgets ([
					balanceGauge{metric = "Heap Used GB"},
					barLineChart{
						metric "Heap Used GB"
						axisYUnit Unit.MEMORY
					},
				])
			},
			widgetGroup {
				name "VM GC Collection Time"
				widgets ([
					balanceGauge{metric = "GC Collection Time"},
					barLineChart{
						metric "GC Collection Time"
						axisYUnit Unit.DURATION
					},
				])
			}
		])
	}
}


