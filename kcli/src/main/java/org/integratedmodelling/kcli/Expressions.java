package org.integratedmodelling.kcli;

import org.integratedmodelling.klab.api.data.Version;
import picocli.CommandLine.Command;

@Command(name = "expr", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Commands to find, access and manipulate resources.",
		"" }, subcommands = {Expressions.List.class})
public class Expressions {

	
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
