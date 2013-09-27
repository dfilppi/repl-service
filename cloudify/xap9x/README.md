xap9x  --- EXPERIMENTAL ---
=================

##XAP 9.x Recipe

This folder contains service recipes that run GigaSpace XAP version 9.x.  The recipes consist of a management and container recipe.  When composed in an application recipe, the container recipe should start after the management recipe.  

#####Recipe #1 : xap-management
The <i>xap-management</i> recipe launches the XAP management processes: the GSM and LUS, as well as the Web UI.  The LUS port is configurable in the recipe properties, and effectively identifies the cluster.  All clusters (including Cloudify itself) must have unique LUS ports.  

<i>xap-management</i> can support up to 2 instances.  Containers (GSCs) are deployed via the <i>xap-container</i> recipe.  <i>xap-management</i>, on starting, updates the hosts table on the containers (if any) via a custom command.  Containers likewise update their hosts tables on startup to avoid static IP configuration in the recipes.

The recipe provides a link to the XAP Web UI in the details section of the Cloudify UI.

###### Custom Commands

The recipe provides several custom commands:

* deploy-pu: deploys a processing unit
* deploy-pu-basic: a convenience command that provides default to deploy-pu
* deploy-epu: deploy an elastic pu --- EXPERIMENTAL
* deploy-grid: deploys a space
* undeploy-grid:  undeploys a grid
* deploy-gateway: creates and deploys a WAN gateway

#####Recipe #2: xap-container

The <i>xap-container</i> recipe starts a single GSC.  When it starts it locates the management nodes and updates the /etc/hosts file (no Windows support yet).  The recipe has effectively no upper limit on instances and is elastic.  It has no custom commands intended for public use.
