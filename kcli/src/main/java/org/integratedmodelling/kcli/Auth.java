package org.integratedmodelling.kcli;

import org.integratedmodelling.kcli.engine.Engine;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.scope.UserScope;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;

@Command(name = "auth", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Commands to find, access and manipulate resources.",
		"" }, subcommands = { Auth.List.class, Auth.Who.class, Auth.Login.class, Credentials.class })
public class Auth {

	@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List all available identities.", "" }, subcommands = {})
	public static class List implements Runnable {

		@Spec
		CommandSpec commandSpec;

		@Override
		public void run() {

			PrintWriter out = commandSpec.commandLine().getOut();

			for (UserScope user : Engine.INSTANCE.getUsers()) {
				out.println("  " + user);
			}
		}

	}

	@Command(name = "login", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Login as a given user.", "" }, subcommands = {})
	public static class Login implements Runnable {

		@Option(names = { "-s", "--synchronous" }, defaultValue = "false", description = {
				"Remember the credentials for next login." }, required = false)
		boolean rememberCredentials;

		@Spec
		CommandSpec commandSpec;

		@Parameters(description = { "User credentials. Just username if remembered." }, defaultValue = Parameters.NULL_VALUE)
		String[] users;

		@Override
		public void run() {

			PrintWriter out = commandSpec.commandLine().getOut();

			for (UserScope user : Engine.INSTANCE.getUsers()) {
				out.println("  " + user);
			}
		}

	}

	@Command(name = "who", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List and describe connected users on the selected services.",
			"Use 'who am i' to describe your own user." }, subcommands = {})
	public static class Who implements Runnable {

		@Spec
		CommandSpec commandSpec;

		@Parameters(description = {
				"A list of user names or the words 'am' 'i'" }, defaultValue = Parameters.NULL_VALUE)
		String[] users;

		@Override
		public void run() {
			PrintWriter out = commandSpec.commandLine().getOut();
			if (users != null && users.length == 2 && "am".equals(users[0]) && "i".equalsIgnoreCase(users[1])) {
				out.println(Engine.INSTANCE.getCurrentUser());
			} else if (users == null) {
				for (UserScope user : Engine.INSTANCE.getUsers()) {
					/*
					 * check if user is connected
					 */
				}
			} else {
				// TODO
			}
		}

	}

}