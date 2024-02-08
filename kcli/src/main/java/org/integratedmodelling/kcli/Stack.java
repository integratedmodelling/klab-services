package org.integratedmodelling.kcli;

import org.integratedmodelling.kcli.functional.FunctionalCommand;
import org.integratedmodelling.klab.api.data.Version;
import picocli.CommandLine.Command;

@Command(name = "stack", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Management of the value stack.",
		"" }, subcommands = {Stack.List.class, Stack.Reset.class})
public class Stack {
	
	@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List the contents of the value stack.",
			"" }, subcommands = {})
	public static class List implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}
		
	}
	
	@Command(name = "clear", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Reset the value stack.",
			"" }, subcommands = {})
	public static class Reset implements Runnable {

		@Override
		public void run() {
			FunctionalCommand.resetStack();
		}
		
	}
}
