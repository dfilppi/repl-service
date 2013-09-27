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
import java.io.InputStream
import java.io.BufferedReader
import java.util.Arrays
import groovy.util.ConfigSlurper;
import org.cloudifysource.dsl.context.ServiceContextFactory

context=ServiceContextFactory.serviceContext
config = new ConfigSlurper().parse(new File(context.serviceName+"-service.properties").toURL())
ip=InetAddress.getLocalHost().getHostAddress()
esm=false

//Below not needed (i think). Cloudify always starts ESM
//if(!context.isLocalCloud() && context.instanceId==1)esm=true

new AntBuilder().sequential {
	exec(executable:"runxap.bat", osfamily:"windows",
		output:"runxap.${System.currentTimeMillis()}.out",
		error:"runxap.${System.currentTimeMillis()}.err"
	){
		env(key:"ESM",value:esm.toString())
		env(key:"XAPDIR", value:"${config.installDir}\\${config.xapDir}")
		env(key:"GSM_JAVA_OPTIONS",value:"-Dcom.sun.jini.reggie.initialUnicastDiscoveryPort=${config.lusPort} -Dcom.gs.multicast.enabled=false")
		env(key:"LUS_JAVA_OPTIONS",value:"-Dcom.sun.jini.reggie.initialUnicastDiscoveryPort=${config.lusPort} -Dcom.gs.multicast.enabled=false")
		env(key:"WEBUI_JAVA_OPTIONS",value:"-Dcom.gs.multicast.enabled=false")
		env(key:"LOOKUPLOCATORS",value:"${ip}:${config.lusPort}")
		env(key:"NIC_ADDR",value:"${ip}")
		env(key:"WEBUI_PORT",value:"${config.uiPort}")
	} 

	chmod(dir:"${context.serviceDirectory}",perm:"+x",includes:"*.sh")
	chmod(dir:"${context.serviceDirectory}/${config.installDir}/${config.xapDir}",perm:"+x",includes:"*.sh")
	chmod(dir:"${context.serviceDirectory}/${config.installDir}/${config.xapDir}/tools/gs-webui",perm:"+x",includes:"*.sh")
	
	exec(executable:"./runxap.sh", osfamily:"unix",
		output:"runxap.${System.currentTimeMillis()}.out",
		error:"runxap.${System.currentTimeMillis()}.err"
	){
		env(key:"ESM",value:esm.toString())
		env(key:"XAPDIR", value:"${context.serviceDirectory}/${config.installDir}/${config.xapDir}")
		env(key:"LUS_JAVA_OPTIONS",value:"-Dcom.sun.jini.reggie.initialUnicastDiscoveryPort=${config.lusPort} -Dcom.gs.multicast.enabled=false")
		env(key:"GSM_JAVA_OPTIONS",value:"-Dcom.sun.jini.reggie.initialUnicastDiscoveryPort=${config.lusPort} -Dcom.gs.multicast.enabled=false")
		env(key:"WEBUI_JAVA_OPTIONS",value:"-Dcom.gs.multicast.enabled=false")
		env(key:"LOOKUPLOCATORS",value:"${ip}:${config.lusPort}")
		env(key:"NIC_ADDR",value:"${ip}")
		env(key:"WEBUI_PORT",value:"${config.uiPort}")
	}

}

//update container nodes if any (restart scenario)
def containerService=context.waitForService(config.containerServiceName,5,TimeUnit.SECONDS)
if(containerService!=null){
	println "invoking update hosts"
  containerService.invoke("update-hosts",ip,"lus${context.instanceId}" as String)
}
else{
	println "no service ${config.containerServiceName} found"
}
