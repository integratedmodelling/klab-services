package org.integratedmodelling.kcli;

import java.io.PrintWriter;

import org.integratedmodelling.kcli.engine.Engine;
import org.integratedmodelling.kcli.engine.Geometries;
import org.integratedmodelling.klab.Version;
import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.SessionScope;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.utils.NameGenerator;

import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "context", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
		"Commands to create, access and manipulate contexts.",
		"" }, subcommands = { Context.List.class, Context.New.class, Context.Connect.class, Context.Observe.class })
public class Context {

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
		@Parameters(description = { "A known geometry identifier or geometry specification.",
				"If not passed, the context will have an empty geometry." }, defaultValue = Parameters.NULL_VALUE)
		String geometry;

		@Override
		public void run() {

			PrintWriter out = commandSpec.commandLine().getOut();

			if (name == null) {
				name = NameGenerator.shortUUID();
			}

			Geometry geom = null;

			if (geometry != null) {
				geom = Geometries.getGeometry(geometry);
				if (geom == null) {
					try {
						geom = Geometry.create(geometry);
					} catch (Throwable t) {
						out.println(Ansi.AUTO.string("Invalid geometry specification: @|red " + geometry + "|@"));
					}
				}
			}

			boolean isnew = Engine.INSTANCE.getCurrentSession() == null;
			SessionScope session = Engine.INSTANCE.getCurrentSession(true, Engine.INSTANCE.getCurrentUser());
			if (isnew) {
				out.println(
						Ansi.AUTO.string("No active session: created new session @|green " + session.getName() + "|@"));
			}

			ContextScope context = session.getContext(name);

			if (context != null) {
				out.println(Ansi.AUTO.string("Context @|red " + name + "|@ already exists!"));
			} else {
				context = session.createContext(name, geom == null ? Geometry.EMPTY : geom);
				Engine.INSTANCE.setCurrentContext(context);
				out.println(Ansi.AUTO.string("Context @|green " + context.getName() + "|@ created and selected."));
			}

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

		@Spec
		CommandSpec commandSpec;

		@Option(names = { "-c", "--context" }, defaultValue = Parameters.NULL_VALUE, description = {
				"Choose a context for the observation (default is the current context)" }, required = false)
		private String context;

		@Option(names = { "-g", "--geometry" }, defaultValue = Parameters.NULL_VALUE, description = {
				"Specify a geometry for the new observation (must be a countable/substantial)." }, required = false)
		private String geometry;

		@Parameters
		java.util.List<String> observables;

		// TODO option to observe in a sub-context

		@Override
		public void run() {

			PrintWriter out = commandSpec.commandLine().getOut();
			ContextScope ctx = context == null ? Engine.INSTANCE.getCurrentContext(false)
					: Engine.INSTANCE.getContext(context);

			if (ctx == null) {
				out.println(Ansi.AUTO
						.string("No context for the observation! Create a context or choose among the existing."));

			}

			String urn = Utils.Strings.join(observables, " ");
			ctx.observe(urn);
			out.println(Ansi.AUTO.string("Observation of @|yellow " + urn + "|@ started in " + ctx.getName()));

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
