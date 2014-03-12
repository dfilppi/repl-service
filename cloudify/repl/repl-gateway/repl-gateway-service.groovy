
service {
	extend "../../xap9x/xap-gateway"

	name "repl-gateway"

	customCommands ([

		"install-gateway": "install_repl_gateway.groovy"

	])

}
