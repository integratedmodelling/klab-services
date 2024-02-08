package org.integratedmodelling.kcli;

import org.integratedmodelling.klab.api.data.Version;
import picocli.CommandLine.Command;

@Command(name = "components", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Commands to find, access and manipulate plug-in components.", "" }, subcommands = { Components.List.class,
				Components.Add.class, Components.Load.class, Components.Info.class, Components.Unload.class })
public class Components {

	@Command(name = "add", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Add a local or remote component.", "" }, subcommands = {})
	public static class Add implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}

	@Command(name = "load", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Load a component.", "" }, subcommands = {})
	public static class Load implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}

	@Command(name = "unload", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Unload a component.", "" }, subcommands = {})
	public static class Unload implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}

	@Command(name = "info", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"Detailed info on a component.", "" }, subcommands = {})
	public static class Info implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}

	@Command(name = "list", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
			"List and describe local or remote components.", "" }, subcommands = {})
	public static class List implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			System.out.println("Hola");
		}

	}

}