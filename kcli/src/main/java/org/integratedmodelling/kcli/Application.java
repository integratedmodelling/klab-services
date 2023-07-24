package org.integratedmodelling.kcli;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.fusesource.jansi.AnsiConsole;
import org.integratedmodelling.kcli.engine.Engine;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.Scope.Status;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.configuration.Configuration;
import org.jline.builtins.ConfigurationPath;
import org.jline.console.SystemRegistry;
import org.jline.console.impl.Builtins;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.EndOfFileException;
import org.jline.reader.History;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.MaskingCallback;
import org.jline.reader.Parser;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.widget.TailTipWidgets;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.shell.jline3.PicocliCommands;
import picocli.shell.jline3.PicocliCommands.PicocliCommandsFactory;

/**
 * Command line for the next k.LAB. No longer tied to an engine.
 * <p>
 * Commands can be bare Runnables or the specialized FunctionalCommand, which
 * manages a stack of values that the command execution can push. Any pushed
 * values are matched into a global stack, and they can be referred as $
 * (equivalent to $0) or $n (n = depth into stack) by commands that are prepared
 * to receive them. Commands that push variables into the stack should notify
 * that to the user.
 * <p>
 * TESTING SETUP
 * ==============================================================================================
 * 
 * Run in terminal from the project dir after "mvn install" as <code>
 * java -cp "target/kcli-0.11.0-SNAPSHOT.jar;target/lib/*" org.integratedmodelling.kcli.Application
 * </code>.
 * 
 * A useful alias is
 * 
 * <code> alias klab="java -cp "target/kcli-0.11.0-SNAPSHOT.jar;target/lib/*"
 * -Xmx4096M -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000
 * org.integratedmodelling.kcli.Application"
 * 
 */
public class Application {

	/**
	 * Top-level command that just prints help.
	 */
	@Command(name = "", description = {
			"k.LAB interactive shell with completion and autosuggestions. "
					+ "Hit @|magenta <TAB>|@ to see available commands.",
			"Hit @|magenta ALT-S|@ to toggle tailtips.", "" }, footer = { "", "Press Ctrl-D to exit." }, subcommands = {
					Auth.class, Expressions.class, Reasoner.class, Report.class, Resolver.class, Resources.class,
					Services.class, Run.class, PicocliCommands.ClearScreen.class, CommandLine.HelpCommand.class,
					Session.class, Context.class, Components.class, Stack.class })
	static class CliCommands implements Runnable {

		PrintWriter out;

		public void setReader(LineReader reader) {
			out = reader.getTerminal().writer();
		}

		public void run() {
			out.println(new CommandLine(this).getUsageMessage());
		}
	}

	@Command(name = "run", mixinStandardHelpOptions = true, description = { "Run scripts, test cases and applications.",
			"Uses autocompletion for behavior and test case names.",
			"" }, subcommands = { Run.List.class, Run.Purge.class })
	static class Run extends Monitor implements Runnable {

		Set<SessionScope> running = new LinkedHashSet<>();

		@Spec
		CommandSpec commandSpec;

		@Option(names = { "-s", "--synchronous" }, defaultValue = "false", description = {
				"Run in synchronous mode, returning to the prompt when the script has finished running." }, required = false)
		boolean synchronous;

		@Parameters(description = { "The full name of one or more script, test case or application.",
				"If not present locally, resolve through the k.LAB network." })
		java.util.List<String> scriptNames = new ArrayList<>();

		public Run() {
		}

		@Override
		public void run() {

			PrintWriter out = commandSpec.commandLine().getOut();

			if (scriptNames.isEmpty()) {
				list();
			} else {

				for (String scriptName : scriptNames) {

					KActorsBehavior behavior = Engine.INSTANCE.getCurrentUser(true, null)
							.getService(ResourcesService.class)
							.resolveBehavior(scriptName, Engine.INSTANCE.getCurrentUser());

					if (behavior == null) {
						out.println(Ansi.AUTO.string("Behavior @|red " + scriptName + "|@ unknown or not available"));
					} else {
						out.println(Ansi.AUTO.string("Running @|green " + scriptName + "|@..."));
						running.add(Engine.INSTANCE.getCurrentUser().run(scriptName, behavior.getType()));
					}
				}
			}
		}

		public void list() {

			int n = 1;
			for (SessionScope scope : running) {
				commandSpec.commandLine().getOut()
						.println("   " + n++ + ". " + scope.getName() + " [" + scope.getStatus() + "]");
			}

		}

		@Command(name = "list", mixinStandardHelpOptions = true, description = { "List all running behaviors." })
		static class List implements Runnable {

			@ParentCommand
			Run parent;

			@Override
			public void run() {
				parent.list();
			}

		}

		@Command(name = "purge", mixinStandardHelpOptions = true, description = {
				"Remove finished or aborted behaviors from the list." })
		static class Purge implements Runnable {

			@Parameters(description = {
					"The numeric ID of the scripts we want to purge. No argument removes all that have finished.",
					"Run \"run list\" to know the IDs." })
			java.util.List<Integer> appIds = new ArrayList<>();

			@ParentCommand
			Run parent;

			@Override
			public void run() {
				if (appIds.isEmpty()) {
					Set<SessionScope> removed = new HashSet<>();
					for (SessionScope s : parent.running) {
						if (s.getStatus() != Status.STARTED && s.getStatus() != Status.WAITING) {
							s.stop();
							removed.add(s);
						}
					}
					parent.running.removeAll(removed);
				} else {
					java.util.List<SessionScope> scopes = new ArrayList<>(parent.running);
					for (int appId : appIds) {
						SessionScope s = scopes.get(appId + 1);
						s.stop();
						parent.running.remove(s);
					}
				}
				parent.list();
			}

		}

	}

	// /**
	// * A command with some options to demonstrate completion.
	// */
	// @Command(name = "cmd", mixinStandardHelpOptions = true, version = "1.0",
	// description = {
	// "Command with some options to demonstrate TAB-completion.",
	// " (Note that enum values also get completed.)" }, subcommands = {
	// Nested.class,
	// CommandLine.HelpCommand.class })
	// static class MyCommand implements Runnable {
	// @Option(names = { "-v", "--verbose" }, description = { "Specify multiple -v
	// options to
	// increase verbosity.",
	// "For example, `-v -v -v` or `-vvv`" })
	// private boolean[] verbosity = {};
	//
	// @ArgGroup(exclusive = false)
	// private MyDuration myDuration = new MyDuration();
	//
	// static class MyDuration {
	// @Option(names = { "-d", "--duration" }, description = "The duration
	// quantity.", required =
	// true)
	// private int amount;
	//
	// @Option(names = { "-u", "--timeUnit" }, description = "The duration time
	// unit.", required =
	// true)
	// private TimeUnit unit;
	// }
	//
	// @ParentCommand
	// CliCommands parent;
	//
	// public void run() {
	// if (verbosity.length > 0) {
	// parent.out.printf("Hi there. You asked for %d %s.%n", myDuration.amount,
	// myDuration.unit);
	// } else {
	// parent.out.println("hi!");
	// }
	// }
	// }

	// @Command(name = "nested", mixinStandardHelpOptions = true, subcommands = {
	// CommandLine.HelpCommand.class }, description = "Hosts more sub-subcommands")
	// static class Nested implements Runnable {
	// public void run() {
	// System.out.println("I'm a nested subcommand. I don't do much, but I have
	// sub-subcommands!");
	// }
	//
	// @Command(mixinStandardHelpOptions = true, subcommands = {
	// CommandLine.HelpCommand.class }, description = "Multiplies two numbers.")
	// public void multiply(@Option(names = { "-l", "--left" }, required = true) int
	// left,
	// @Option(names = { "-r", "--right" }, required = true) int right) {
	// System.out.printf("%d * %d = %d%n", left, right, left * right);
	// }
	//
	// @Command(mixinStandardHelpOptions = true, subcommands = {
	// CommandLine.HelpCommand.class }, description = "Adds two numbers.")
	// public void add(@Option(names = { "-l", "--left" }, required = true) int
	// left,
	// @Option(names = { "-r", "--right" }, required = true) int right) {
	// System.out.printf("%d + %d = %d%n", left, right, left + right);
	// }
	//
	// @Command(mixinStandardHelpOptions = true, subcommands = {
	// CommandLine.HelpCommand.class }, description = "Subtracts two numbers.")
	// public void subtract(@Option(names = { "-l", "--left" }, required = true) int
	// left,
	// @Option(names = { "-r", "--right" }, required = true) int right) {
	// System.out.printf("%d - %d = %d%n", left, right, left - right);
	// }
	// }

	public static void main(String[] args) {
		AnsiConsole.systemInstall();
		try {
			Supplier<Path> workDir = () -> Paths
					.get(System.getProperty("user.dir") + File.pathSeparator + ".klab" + File.pathSeparator + "kcli");
			// set up JLine built-in commands
			workDir.get().toFile().mkdirs();
			ConfigurationPath configPath = new ConfigurationPath(workDir.get(), workDir.get());
			Builtins builtins = new Builtins(workDir, configPath, null);
			builtins.rename(Builtins.Command.TTOP, "top");
			builtins.alias("zle", "widget");
			builtins.alias("bindkey", "keymap");
			// set up picocli commands
			CliCommands commands = new CliCommands();
			PicocliCommandsFactory factory = new PicocliCommandsFactory();
			// Or, if you have your own factory, you can chain them like this:
			// MyCustomFactory customFactory = createCustomFactory(); // your application
			// custom
			// factory
			// PicocliCommandsFactory factory = new PicocliCommandsFactory(customFactory);
			// // chain
			// the factories

			CommandLine cmd = new CommandLine(commands, factory);
			PicocliCommands picocliCommands = new PicocliCommands(cmd);
			File historyFile = new File(Configuration.INSTANCE.getDataPath() + File.separator + "kcli.history");
			Parser parser = new DefaultParser();
			try (Terminal terminal = TerminalBuilder.builder().build()) {

				SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, workDir, null);
				systemRegistry.setCommandRegistries(builtins, picocliCommands);
				systemRegistry.register("help", picocliCommands);
				KlabCompleter completer = new KlabCompleter(systemRegistry.completer());
				History history = new DefaultHistory();
				LineReader reader = LineReaderBuilder.builder().terminal(terminal).completer(completer).parser(parser)
						.variable(LineReader.LIST_MAX, 50) // candidates
						.history(history).build();

				builtins.setLineReader(reader);
				commands.setReader(reader);
				factory.setTerminal(terminal);
				history.attach(reader);

				TailTipWidgets widgets = new TailTipWidgets(reader, systemRegistry::commandDescription, 5,
						TailTipWidgets.TipType.COMPLETER);
				widgets.enable();
				KeyMap<Binding> keyMap = reader.getKeyMaps().get("main");
				keyMap.bind(new Reference("tailtip-toggle"), KeyMap.alt("s"));

				String prompt = "k.LAB> ";
				String rightPrompt = null;

				if (historyFile.exists()) {
					history.read(historyFile.toPath(), true);
				}

				// start the shell and process input until the user quits with Ctrl-D
				String line;
				while (true) {
					try {
						systemRegistry.cleanUp();
						line = reader.readLine(prompt, rightPrompt, (MaskingCallback) null, null);
						completer.resetSemanticSearch();
						systemRegistry.execute(line);
						history.write(historyFile.toPath(), true);
					} catch (UserInterruptException e) {
						// Ignore
					} catch (EndOfFileException e) {
						return;
					} catch (Exception e) {
						systemRegistry.trace(e);
					}
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			AnsiConsole.systemUninstall();
		}
	}

	public static void printResourceSet(ResourceSet resourceSet, PrintWriter out) {

		if (resourceSet == null) {
			out.println("Null resource set");
		} else if (resourceSet.isEmpty()) {
			out.println("Empty resource set");
		} else {

		}
	}

}
