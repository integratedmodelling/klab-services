package org.integratedmodelling.cli;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.engine.client.EngineClient;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.Scope.Status;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.jline.builtins.ConfigurationPath;
import org.jline.console.SystemRegistry;
import org.jline.console.impl.Builtins;
import org.jline.console.impl.SystemRegistryImpl;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.widget.TailTipWidgets;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.shell.jline3.PicocliCommands;
import picocli.shell.jline3.PicocliCommands.PicocliCommandsFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Stack;
import java.util.*;
import java.util.function.Supplier;

/**
 * Command line for the next k.LAB. No longer tied to an engine.
 * <p>
 * Commands can be bare Runnables or the specialized FunctionalCommand, which manages a stack of values that
 * the command execution can push. Any pushed values are matched into a global stack, and they can be referred
 * as $ (equivalent to $0) or $n (n = depth into stack) by commands that are prepared to receive them.
 * Commands that push variables into the stack should notify that to the user.
 * <p>
 * TESTING SETUP
 * ==============================================================================================
 * <p>
 * Run in terminal from the project dir after "mvn install" as <code> java -cp
 * "target/kcli-0.11.0-SNAPSHOT.jar;target/lib/*" org.integratedmodelling.kcli.KlabCLI
 * </code>.
 * <p>
 * A useful alias for bash is
 *
 * <code> alias klab="java -cp "target/kcli-0.11.0-SNAPSHOT.jar;target/lib/*"
 * -Xmx4096M -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000
 * org.integratedmodelling.kcli.KlabCLI"
 */
public enum KlabCLI {

    INSTANCE;

    EngineClient engine = new EngineClient();

    public Engine engine() {
        return this.engine;
    }

    public UserScope currentUser() {
        // TODO
        return null;
    }

    public SessionScope currentSession() {
        // TODO
        return null;
    }

    public ContextScope currentContext() {
        // TODO
        return null;
    }

    public ContextScope context(String context) {
        // TODO named context
        return null;
    }

    public UserScope user(String username) {
        // TODO named user
        return null;
    }

    public SessionScope session(String session) {
        // TODO named session
        return null;
    }

    public <T extends KlabService> T service(String service, Class<T> serviceClass) {
        return null;
    }

    /**
     * Top-level command that just prints help.
     */
    @Command(name = "", description = {
            "k.LAB interactive shell with completion and autosuggestions. "
                    + "Hit @|magenta <TAB>|@ to see available commands.",
            "Hit @|magenta ALT-S|@ to toggle tailtips.", ""}, footer = {"", "Press Ctrl-D to exit."},
             subcommands = {
                     Auth.class, Expressions.class, Reasoner.class, Report.class, Resolver.class,
                     Resources.class,
                     Services.class, Run.class, PicocliCommands.ClearScreen.class,
                     CommandLine.HelpCommand.class,
                     Session.class, Context.class, Components.class, Stack.class, Test.class, Run.Alias.class,
                     Run.Unalias.class})
    static class CliCommands implements Runnable {

        PrintWriter out;

        public void setReader(LineReader reader) {
            out = reader.getTerminal().writer();
        }

        public void run() {
            out.println(new CommandLine(this).getUsageMessage());
        }
    }

    @Command(name = "run", mixinStandardHelpOptions = true, description = {"Run scripts, test cases and " +
                                                                                   "applications.",
                                                                           "Uses autocompletion for " +
                                                                                   "behavior and test case " +
                                                                                   "names.",
                                                                           ""}, subcommands =
                     {Run.List.class, Run.Purge.class})
    static class Run /* extends Monitor */ implements Runnable {

        Set<SessionScope> running = new LinkedHashSet<>();

        static Map<String, String> aliases = new LinkedHashMap<>();

        @Spec
        CommandSpec commandSpec;

        @Option(names = {"-s", "--synchronous"}, defaultValue = "false", description = {
                "Run in synchronous mode, returning to the prompt when the script has finished running."},
                required =
                        false)
        boolean synchronous;

        @Parameters(description = {"The full name of one or more script, test case or application.",
                                   "If not present locally, resolve through the k.LAB network."})
        java.util.List<String> scriptNames = new ArrayList<>();

        public Run() {
        }

        public static void loadAliases() {
            File aliasFile =
                    new File(System.getProperty("user.home") + File.separator + ".klab" + File.separator +
                            "kcli" + File.separator + "aliases.txt");
            if (!aliasFile.exists()) {
//                Utils.Files.touch(aliasFile);
            }
            try (InputStream input = new FileInputStream(aliasFile)) {
                Properties properties = new Properties();
                properties.load(input);
                for (String property : properties.stringPropertyNames()) {
                    Run.aliases.put(property, properties.getProperty(property));
                }
            } catch (Exception e) {
                throw new KlabIOException(e);
            }
        }

        public static void storeAliases() {
            File aliasFile =
                    new File(System.getProperty("user.home") + File.separator + ".klab" + File.separator +
                            "kcli" + File.separator + "aliases.txt");
            try (OutputStream output = new FileOutputStream(aliasFile)) {
                Properties properties = new Properties();
                for (String key : Run.aliases.keySet()) {
                    properties.setProperty(key, Run.aliases.get(key));
                }
                properties.store(output, "k.CLI alias file");
            } catch (Exception e) {
                throw new KlabIOException(e);
            }
        }

        @Override
        public void run() {

            PrintWriter out = commandSpec.commandLine().getOut();

            if (scriptNames.isEmpty()) {
                list();
            } else {

                for (String scriptName : scriptNames) {

//                    KActorsBehavior behavior = Engine.INSTANCE.getCurrentUser(true, null)
//                            .getService(ResourcesService.class)
//                            .resolveBehavior(scriptName, Engine.INSTANCE.getCurrentUser());
//
//                    if (behavior == null) {
//                        out.println(Ansi.AUTO.string("Behavior @|red " + scriptName + "|@ unknown or not " +
//                                "available"));
//                    } else {
//                        out.println(Ansi.AUTO.string("Running @|green " + scriptName + "|@..."));
//                        running.add(Engine.INSTANCE.getCurrentUser().run(scriptName, behavior.getType()));
//                    }
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

        @Command(name = "list", mixinStandardHelpOptions = true, description = {"List all running behaviors" +
                                                                                        "."})
        static class List implements Runnable {

            @ParentCommand
            Run parent;

            @Override
            public void run() {
                parent.list();
            }

        }

        @Command(name = "alias", mixinStandardHelpOptions = true, description = {"Define an alias for a " +
                                                                                         "command.",
                                                                                 "Use @x to store option -x"},
                 subcommands = {Alias.List.class, Alias.Clear.class})
        static class Alias implements Runnable {

            @Command(name = "list", mixinStandardHelpOptions = true, description = {"List all aliases."})
            static class List implements Runnable {
                @Spec
                CommandSpec commandSpec;

                @Override
                public void run() {
                    for (String alias : Run.aliases.keySet()) {
                        commandSpec.commandLine().getOut().println(Ansi.AUTO.string("@|bold " + alias +
                                "|@: " +
                                "@|green " + Run.aliases.get(alias) + "|@"));
                    }
                }
            }

            @Command(name = "clear", mixinStandardHelpOptions = true, description = {"Remove all aliases."})
            static class Clear implements Runnable {
                @Spec
                CommandSpec commandSpec;

                @Override
                public void run() {
                    int nal = Run.aliases.size();
                    Run.aliases.clear();
                    Run.storeAliases();
                    commandSpec.commandLine().getOut().println(nal + " aliases removed");
                }
            }

            @Parameters(defaultValue = Parameters.NULL_VALUE)
            java.util.List<String> arguments;
            @Spec
            CommandSpec commandSpec;

            @Override
            public void run() {

                if (arguments == null || arguments.size() == 0) {
                    for (String alias : Run.aliases.keySet()) {
                        commandSpec.commandLine().getOut().println(Ansi.AUTO.string("@|bold " + alias +
                                "|@: " +
                                "@|green " + Run.aliases.get(alias) + "|@"));
                    }
                    return;
                }

                if (arguments.size() < 2) {
                    throw new KlabIllegalStateException("Must name an alias and its value");
                }
                String alias = arguments.get(0);
                for (int i = 1; i < arguments.size(); i++) {
                    if (arguments.get(i).startsWith("@")) {
                        arguments.set(i, "-" + arguments.get(i).substring(1));
                    }
                }
//                String value = Utils.Strings.join(arguments.subList(1, arguments.size()), " ");
//                Run.aliases.put(alias, value);
//                Run.storeAliases();
            }
        }

        @Command(name = "unalias", mixinStandardHelpOptions = true, description = {"Remove a command alias."})
        static class Unalias implements Runnable {

            @Parameters
            String alias;

            @Override
            public void run() {
                Run.aliases.remove(alias);
                Run.storeAliases();
            }

        }

        @Command(name = "purge", mixinStandardHelpOptions = true, description = {
                "Remove finished or aborted behaviors from the list."})
        static class Purge implements Runnable {

            @Parameters(description = {
                    "The numeric ID of the scripts we want to purge. No argument removes all that have " +
                            "finished.",
                    "Run \"run list\" to know the IDs."})
            java.util.List<Integer> appIds = new ArrayList<>();

            @ParentCommand
            Run parent;

            @Override
            public void run() {
                if (appIds.isEmpty()) {
                    Set<SessionScope> removed = new HashSet<>();
                    for (SessionScope s : parent.running) {
                        if (s.getStatus() != Status.STARTED && s.getStatus() != Status.WAITING) {
                            s.logout();
                            removed.add(s);
                        }
                    }
                    parent.running.removeAll(removed);
                } else {
                    java.util.List<SessionScope> scopes = new ArrayList<>(parent.running);
                    for (int appId : appIds) {
                        SessionScope s = scopes.get(appId + 1);
                        s.logout();
                        parent.running.remove(s);
                    }
                }
                parent.list();
            }
        }
    }

    public static void main(String[] args) {
//        AnsiConsole.systemInstall();
        try {
            Supplier<Path> workDir = () -> Paths
                    .get(System.getProperty("user.home") + File.separator + ".klab" + File.separator +
                            "kcli");

            // jline built-in commands
            workDir.get().toFile().mkdirs();
            ConfigurationPath configPath = new ConfigurationPath(workDir.get(), workDir.get());
            Builtins builtins = new Builtins(workDir, configPath, null);
            builtins.rename(Builtins.Command.TTOP, "top");
            builtins.alias("zle", "widget");
            builtins.alias("bindkey", "keymap");

            // picocli
            CliCommands commands = new CliCommands();
            PicocliCommandsFactory factory = new PicocliCommandsFactory();
            CommandLine cmd = new CommandLine(commands, factory);
            PicocliCommands picocliCommands = new PicocliCommands(cmd);
            File historyFile = new File(Configuration.INSTANCE.getDataPath() + File.separator + "kcli" +
                    ".history");
            Parser parser = new DefaultParser();
            try (Terminal terminal = TerminalBuilder.builder().build()) {

                SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, workDir, null);
                systemRegistry.setCommandRegistries(builtins, picocliCommands);
                systemRegistry.register("help", picocliCommands);
                KlabCompleter completer = new KlabCompleter(systemRegistry.completer());
                History history = new DefaultHistory();
                LineReader reader =
                        LineReaderBuilder.builder().terminal(terminal).completer(completer).parser(parser)
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

                /**
                 * If we have a command, run it and exit
                 */
                if (args != null && args.length > 0) {
                    String line = Utils.Strings.join(args, ' ');
                    try {
                        systemRegistry.execute(line);
                    } catch (Throwable t) {
                        t.printStackTrace();
                        System.exit(0xff);
                    }
                    System.exit(0);
                }

                String prompt = "k.LAB> ";
                String rightPrompt = null;

                if (historyFile.exists()) {
                    history.read(historyFile.toPath(), true);
                }

                Run.loadAliases();

                // start the shell and process input until the user quits with Ctrl-D
                String line;
                while (true) {
                    try {

                        systemRegistry.cleanUp();
                        line = reader.readLine(prompt, rightPrompt, (MaskingCallback) null, null);
                        completer.resetSemanticSearch();
                        boolean aliased = false;

                        if (line.trim().startsWith("-")) {
                            if (line.trim().equals("-") && history.size() > 0) {
                                line = history.get(history.last() - 1);
                                aliased = true;
                            } else if (org.integratedmodelling.klab.api.utils.Utils.Numbers.encodesInteger(line.trim().substring(1))) {
                                int n = Integer.parseInt(line.trim().substring(1));
                                if (history.size() > n) {
                                    line = history.get(history.last() - n);
                                    aliased = true;
                                }
                            }
                        } else if (Run.aliases.containsKey(line.trim())) {
                            line = Run.aliases.get(line.trim());
                        }

                        if (aliased) {
                            // print the actual line in grey + italic
                            cmd.getOut().println(Ansi.AUTO.string("@|gray" + line
                                    + "|@"));
                        }

                        systemRegistry.execute(line);

                        if (!aliased) {
                            history.write(historyFile.toPath(), false);
                        }

                    } catch (UserInterruptException e) {
                        // TODO send interrupt signal to running tasks
                    } catch (EndOfFileException e) {
                        System.exit(0);
                    } catch (Exception e) {
                        systemRegistry.trace(e);
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
//            AnsiConsole.systemUninstall();
        }
    }

    public static void printResourceSet(ResourceSet resourceSet, PrintStream out, int indent) {

        if (resourceSet == null) {
            out.println(Utils.Strings.spaces(indent) + "Null resource set");
        } else if (resourceSet.isEmpty()) {
            out.println(Utils.Strings.spaces(indent) + "Empty resource set");
        } else {

            out.println("Namespaces:");
            for (ResourceSet.Resource namespace : resourceSet.getNamespaces()) {
                out.println("   " + namespace);
            }

        }
    }

}
