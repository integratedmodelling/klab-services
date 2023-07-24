package org.integratedmodelling.kcli;

import org.integratedmodelling.klab.Version;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "resolver", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Commands to access resolution services.", "" }, subcommands = { Resolver.List.class, Resolver.Resolve.class })
public class Resolver {

	@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List and describe models known to the resolver.", "" }, subcommands = {})
	public static class List implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}

	@Command(name = "resolve", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Compute and optionally visualize a resolution graph.",
			"Resolve any URN in the current context." }, subcommands = {})
	public static class Resolve implements Runnable {

		@Option(names = { "-s", "--show" }, defaultValue = "false", description = {
				"Show the resolution graph after computing it." }, required = false)
		boolean show;

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}

}
