package org.integratedmodelling.kcli;

import org.integratedmodelling.klab.Version;

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

		@Parameters(description = "A query with wildcards. If not passed, all matches are returned.")
		String query;

		@Override
		public void run() {
			if (applications) {
//				Engine.INSTANCE.getResources().query(query == null ? "*" : query);
			}
		}

	}

	@Command(name = "workspace", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Workspace operations", "" }, subcommands = { Resources.Workspace.List.class, Resources.Workspace.Add.class,
					Resources.Workspace.Remove.class })
	public static class Workspace {

		@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"List and describe local workspaces.", "" }, subcommands = {})
		public static class List implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

			@Override
			public void run() {
				// TODO Auto-generated method stub
				System.out.println("list workspace");
			}

		}

		@Command(name = "add", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"Add a new workspace to the scope of this service.", "" }, subcommands = {})
		public static class Add implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

			@Override
			public void run() {
				// TODO Auto-generated method stub
				System.out.println("list project");
			}

		}

		@Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"Remove a workspace from this service.", "" }, subcommands = {})
		public static class Remove implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

			@Override
			public void run() {
				// TODO Auto-generated method stub
				System.out.println("list project");
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
				// TODO Auto-generated method stub
				System.out.println("list project");
			}

		}

		@Command(name = "add", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"Add a new project to the scope of this service.", "" }, subcommands = {})
		public static class Add implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

			@Override
			public void run() {
				// TODO Auto-generated method stub
				System.out.println("list project");
			}

		}

		@Command(name = "remove", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"Remove a project from this service.", "" }, subcommands = {})
		public static class Remove implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

			@Override
			public void run() {
				// TODO Auto-generated method stub
				System.out.println("list project");
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
				// TODO Auto-generated method stub
				System.out.println("list project");
			}

		}

		@Command(name = "add", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
				"Add a new component to the scope of a service.", "" }, subcommands = {})
		public static class Add implements Runnable {

			@Option(names = { "-s", "--service" }, defaultValue = "local", description = {
					"Resource service to connect to" }, required = false)
			private String service;

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

			@Override
			public void run() {
				// TODO Auto-generated method stub
				System.out.println("list project");
			}

		}
	}

}
