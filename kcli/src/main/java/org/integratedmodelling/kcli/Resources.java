package org.integratedmodelling.kcli;

import org.integratedmodelling.klab.Version;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "resources", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Commands to find, access and manipulate resources.", "" }, subcommands = { Resources.List.class })
public class Resources {

	@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List and describe local or remote resources.", "" }, subcommands = {})
	public static class List implements Runnable {

		@Option(names = { "-n", "--namespaces" }, defaultValue = "false", description = {
				"List namespaces" }, required = false)
		boolean namespaces;

		@Option(names = { "-b", "--behaviors" }, defaultValue = "false", description = {
				"List behaviors" }, required = false)
		boolean behaviors;

		@Option(names = { "-t", "--tests" }, defaultValue = "false", description = {
				"List test cases" }, required = false)
		boolean tests;

		@Option(names = { "-s", "--scripts" }, defaultValue = "false", description = {
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

}
