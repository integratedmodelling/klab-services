package org.integratedmodelling.kcli;

import org.integratedmodelling.klab.Version;

import picocli.CommandLine.Command;

@Command(name = "resolver", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Commands to access resolution services.",
		"" }, subcommands = {Resolver.List.class})
public class Resolver {

	
	@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List and describe models known to the resolver.",
			"" }, subcommands = {})
	public static class List implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}
		
	}
	
}
