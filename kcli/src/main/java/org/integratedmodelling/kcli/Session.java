package org.integratedmodelling.kcli;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.api.authentication.scope.SessionScope;
import org.integratedmodelling.klab.utils.NameGenerator;

import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "session", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Commands to find, access and manipulate resources.",
		"" }, subcommands = { Session.List.class, Session.New.class, Session.Connect.class })
public class Session {

	Map<String, SessionScope> sessions = new LinkedHashMap<>();
	SessionScope current;

	@Command(name = "new", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Create a new session and make it current.", "" }, subcommands = {})
	public static class New implements Runnable {

		@ParentCommand
		Session parent;

		@Spec
		CommandSpec commandSpec;

		@Parameters(description = { "Name of the session being created.",
				"If not passed, a new name will be created." }, defaultValue = Parameters.NULL_VALUE)
		String name;

		@Override
		public void run() {

			PrintWriter out = commandSpec.commandLine().getOut();

			if (name == null) {
				name = NameGenerator.shortUUID();
			}

			if (parent.sessions.containsKey(name)) {
				out.println(Ansi.AUTO.string("Session @|ret " + name + "|@ already exists!"));
			}
			
			out.println(Ansi.AUTO.string("Session @|yellow " + name + "|@ created and selected."));
		}

	}

	@Command(name = "connect", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Connect to an existing session.", "" }, subcommands = {})
	public static class Connect implements Runnable {

		@ParentCommand
		Session parent;

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}

	@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List and describe currently active sessions.", "" }, subcommands = {})
	public static class List implements Runnable {

		@ParentCommand
		Session parent;

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}

}
