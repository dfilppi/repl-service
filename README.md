repl-service  --- EXPERIMENTAL ---
=================

##Replication as a service for Cloudify

This repo contains projects related to the effort to provide a replication service to Cloudify by leveraging XAP's WAN replication capability.

####Recipes
#####repl-management
An extension of xap9x/xap-management. The extension consists of a custom command "add-repl-space" that adds a space for replicating to/from.
#####repl-container
An extension of xap9x/xap-container. Currently adds no new capabilities.
#####xap9x
A copy of the xap9x recipes is included for convenience and enhancement.  For example, initial versions of the xap-gateway recipe were created and tested here prior to insertion in the main cloudify-recipes repo.
####XAP Components
#####rest-api
The rest-api project implements a REST API that manages replication deployments.  It's goal is to process a single JSON replication descriptor that defines an entire replication network, and then contacts all sites, installs and starts the entire network in one step.


