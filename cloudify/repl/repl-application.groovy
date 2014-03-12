application {
	name "repl"

	service {
		name="repl-management"
	}
	service {
		name="repl-gateway"
		dependsOn = ["repl-container","repl-management"]
	}
	service {
		name="repl-container"
		dependsOn = [ "repl-management" ]
	}
}
