package org.integratedmodelling.kcli;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.integratedmodelling.kcli.engine.Engine;
import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.SessionScope;
import org.integratedmodelling.klab.utils.NameGenerator;

import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "context", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Commands to create, access and manipulate contexts.",
		"" }, subcommands = { Context.List.class, Context.New.class, Context.Connect.class, Context.Observe.class })
public class Context {

	Map<String, ContextScope> contexts = new LinkedHashMap<>();
	ContextScope current;

	@Command(name = "new", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Create a new context and make it current.", "" }, subcommands = {})
	public static class New implements Runnable {

		@ParentCommand
		Context parent;

		@Spec
		CommandSpec commandSpec;

		@Parameters(description = { "Name of the context being created.",
				"If not passed, a new name will be created." }, defaultValue = Parameters.NULL_VALUE)
		String name;

		// TODO add geometry option and instance parameters
		
		@Override
		public void run() {

			PrintWriter out = commandSpec.commandLine().getOut();

			SessionScope session = Engine.INSTANCE.getCurrentSession(true, null);
			
			if (name == null) {
				name = NameGenerator.shortUUID();
			}

			if (parent.contexts.containsKey(name)) {
				out.println(Ansi.AUTO.string("Session @|ret " + name + "|@ already exists!"));
			}
			
			out.println(Ansi.AUTO.string("Session @|yellow " + name + "|@ created and selected."));
		}

	}

	@Command(name = "connect", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Connect to an existing context.", "" }, subcommands = {})
	public static class Connect implements Runnable {

		@ParentCommand
		Context parent;

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}
	@Command(name = "observe", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Make an observation of the passed resolvable URN.", "" }, subcommands = {})
	public static class Observe implements Runnable {

		@ParentCommand
		Context parent;

		// TODO option to observe in a sub-context
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}

	@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List and describe currently active contexts.", "" }, subcommands = {})
	public static class List implements Runnable {

		@ParentCommand
		Context parent;

		// TODO option to list the context tree for the current context
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}

}
