package org.integratedmodelling.kcli;

import java.io.PrintStream;
import java.net.URL;

import org.integratedmodelling.kcli.engine.Engine;
import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.services.ResourcesService;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "resources", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Commands to find, access and manipulate resources.", "" }, subcommands = { Resources.List.class,
				Resources.Services.class, Resources.Workspace.class, Resources.Project.class,
				Resources.Components.class })
public class Resources {

	@Command(name = "services", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List and describe all the available resource services.", "" }, subcommands = {})
	public static class Services implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("list services");
		}
	}

	@Command(name = "resolve", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Resolve a URN and describe the resulting resource set.", "" }, subcommands = {})
	public static class Resolve implements Runnable {

		@Option(names = { "-s", "--service" }, defaultValue = "local" /* TODO initialize at null */, description = {
				"Resource service to connect to" }, required = false)
		private String service;

		@Parameters
		String urn;

		@Override
		public void run() {

			/**
			 * TODO if service is not specified, it should lookup the URN in all available
			 * services
			 */

			switch (Urn.classify(urn)) {
			case KIM_OBJECT:

				break;
			case OBSERVABLE:
				break;
			case REMOTE_URL:
				break;
			case RESOURCE:
				break;
			case UNKNOWN:
				break;
			}
		}

	}

	@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List and describe local or remote resources.", "" }, subcommands = {})
	public static class List implements Runnable {

		@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
				"Resource service to connect to" }, required = false)
		private String service;

		@Option(names = { "-n", "--namespaces" }, defaultValue = "false", description = {
				"List namespaces" }, required = false)
		boolean namespaces;

		@Option(names = { "-b", "--behaviors" }, defaultValue = "false", description = {
				"List behaviors" }, required = false)
		boolean behaviors;

		@Option(names = { "-t", "--tests" }, defaultValue = "false", description = {
				"List test cases" }, required = false)
		boolean tests;

		@Option(names = { "-sc", "--scripts" }, defaultValue = "false", description = {
				"List scripts" }, required = false)
		boolean scripts;

		@Option(names = { "-a", "--applications" }, defaultValue = "false", description = {
				"List applications" }, required = false)
		boolean applications;

		@Option(names = { "-r", "--resources" }, defaultValue = "false", description = {
				"List resources" }, required = false)
		boolean resources;

		@Option(names = { "-c", "--components" }, defaultValue = "false", description = {
				"List components" }, required = false)
		boolean components;

		@Option(names = { "-v", "--verbose" }, defaultValue = "false", description = {
				"List projects in each workspace" }, required = false)
		private boolean verbose;

		@Parameters(description = "A query with wildcards. If not passed, all matches are returned.", defaultValue = "__ALL__")
		String query;

		// TODO
		PrintStream out = System.out;

		@Override
		public void run() {
			if (namespaces) {
				out.println("Namespaces:");
				listNamespaces();
			}
			if (resources) {
				out.println("Resources:");
				listResources();
			}
			if (scripts) {
				out.println("Scripts:");
				listScripts();
			}
			if (tests) {
				out.println("Test cases:");
				listTestCases();
			}
			if (behaviors) {
				out.println("Behaviors:");
				listBehaviors();
			}
			if (applications) {
				out.println("Applications:");
				listApplications();
			}
			if (components) {
				out.println("Components:");
				listComponents();
			}
		}

		private void listNamespaces() {
			// TODO Auto-generated method stub
			
		}

		private void listComponents() {
			// TODO Auto-generated method stub
			
		}

		private void listBehaviors() {
			// TODO Auto-generated method stub

		}

		private void listTestCases() {
			// TODO Auto-generated method stub

		}

		private void listScripts() {
			// TODO Auto-generated method stub

		}

		private void listApplications() {
			// TODO Auto-generated method stub

		}

		public void listResources() {
			var service = Engine.INSTANCE.getServiceNamed(this.service, ResourcesService.class);
			if (service instanceof ResourcesService.Admin) {
				for (var urn : ((ResourcesService.Admin) service).listResourceUrns()) {
					System.out.println("   " + urn);
				}
			}
		}

	}

	@Command(name = "workspace", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Workspace operations",
			"" }, subcommands = { Resources.Workspace.List.class, Resources.Workspace.Remove.class })
	public static class Workspace {

		@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"List and describe local workspaces.", "" }, subcommands = {})
		public static class List implements Runnable {

			@Option(names = { "-v", "--verbose" }, defaultValue = "false", description = {
					"List projects in each workspace" }, required = false)
			private boolean verbose;

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

			@Override
			public void run() {
				var service = Engine.INSTANCE.getServiceNamed(this.service, ResourcesService.class);
				if (service instanceof ResourcesService.Admin) {
					for (var workspace : ((ResourcesService.Admin) service).listWorkspaces()) {
						System.out.println("   " + workspace.getName());
						if (verbose) {
							for (var project : workspace.getProjects()) {
								System.out.println("      " + project.getName());
							}
						}
					}
				}
			}

		}

		@Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"Remove a workspace from this service.", "" }, subcommands = {})
		public static class Remove implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;
			@Parameters
			private String workspace;

			@Override
			public void run() {
				var service = Engine.INSTANCE.getServiceNamed(this.service, ResourcesService.class);
				if (service instanceof ResourcesService.Admin) {
					((ResourcesService.Admin) service).removeWorkspace(workspace);
				}
			}
		}

	}

	@Command(name = "project", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Workspace operations", "" }, subcommands = { Resources.Project.List.class, Resources.Project.Add.class,
					Resources.Project.Remove.class })
	public static class Project {

		@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"List and describe local projects.", "" }, subcommands = {})
		public static class List implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

			@Override
			public void run() {
				var service = Engine.INSTANCE.getServiceNamed(this.service, ResourcesService.class);
				if (service instanceof ResourcesService.Admin) {
					for (var project : ((ResourcesService.Admin) service).listProjects()) {
						System.out.println("   " + project.getName());
					}
				}
			}

		}

		@Command(name = "add", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"Add a new project to the scope of this service.", "" }, subcommands = {})
		public static class Add implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

			@Option(names = { "-w", "--workspace" }, defaultValue = "local", description = {
					"Workspace for the imported project" }, required = false)
			private String workspace;

			@Parameters
			private URL projectUrl;

			@Override
			public void run() {
				var service = Engine.INSTANCE.getServiceNamed(this.service, ResourcesService.class);
				if (service instanceof ResourcesService.Admin) {
					if (!((ResourcesService.Admin) service).addProject(workspace, projectUrl.toString(), false)) {
						System.out.println("project " + projectUrl + " was present or in error, not added");
					} else {
						System.out.println("project " + projectUrl + " added to workspace " + workspace);
					}
				} else {
					System.out.println("service " + this.service + " does not have admin permissions in this scope");
				}
			}

		}

		@Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"Remove a project from this service.", "" }, subcommands = {})
		public static class Remove implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;
			@Parameters
			private String project;

			@Override
			public void run() {
				var service = Engine.INSTANCE.getServiceNamed(this.service, ResourcesService.class);
				if (service instanceof ResourcesService.Admin) {
					((ResourcesService.Admin) service).removeProject(project);
				}
			}

		}
	}

	@Command(name = "components", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Components operations", "" }, subcommands = { Resources.Components.List.class,
					Resources.Components.Add.class, Resources.Components.Remove.class })
	public static class Components {

		@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"List and describe service components.", "" }, subcommands = {})
		public static class List implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

			@Override
			public void run() {

			}

		}

		@Command(name = "add", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"Add a new component to the scope of a service.", "" }, subcommands = {})
		public static class Add implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

			@Parameters
			String componentUrl;

			@Override
			public void run() {
				// TODO Auto-generated method stub
				System.out.println("list project");
			}

		}

		@Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"Remove a component from a service.", "" }, subcommands = {})
		public static class Remove implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

			@Parameters
			String componentName;

			@Override
			public void run() {
			}

		}
	}

}
