package org.integratedmodelling.kcli;

import org.integratedmodelling.klab.Version;

import picocli.CommandLine.Command;

@Command(name = "reason", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Commands to find, access and manipulate semantic knowledge.",
		"" }, subcommands = {Reasoner.List.class})
public class Reasoner {

	
	@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List and describe local or remote resources.",
			"" }, subcommands = {})
	public static class List implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}
		
	}
	
}