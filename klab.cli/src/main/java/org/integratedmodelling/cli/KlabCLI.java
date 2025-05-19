package org.integratedmodelling.cli;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import org.integratedmodelling.cli.views.CLIObservationView;
import org.integratedmodelling.cli.views.CLIReasonerView;
import org.integratedmodelling.cli.views.CLIResourcesView;
import org.integratedmodelling.cli.views.CLIServicesView;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.Scope.Status;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.modeler.ModelerImpl;
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

/**
 * Command line for the next k.LAB. Provides a Modeler controlled by command line instructions.
 *
 * <p>Commands can be bare Runnables or the specialized FunctionalCommand, which manages a stack of
 * values that the command execution can push. Any pushed values are matched into a global stack,
 * and they can be referred as $ (equivalent to $0) or $n (n = depth into stack) by commands that
 * are prepared to receive them. Commands that push variables into the stack should notify that to
 * the user.
 *
 * <p>TESTING SETUP
 * ==============================================================================================
 *
 * <p>Run in terminal from the project dir after "mvn install" as <code> java -cp
 * "target/kcli-0.11.0-SNAPSHOT.jar;target/lib/*" org.integratedmodelling.kcli.KlabCLI
 * </code>.
 *
 * <p>A useful alias for bash is <code>
 *  alias klab="java -cp "target/kcli-0.11.0-SNAPSHOT.jar;target/lib/*"
 * -Xmx4096M -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000
 * org.integratedmodelling.kcli.KlabCLI"</code>
 *
 * <p>TODO revise around the {@link org.integratedmodelling.klab.api.view.modeler.Modeler} and
 * provide CLI-versions of each view instead of making up commands. Should be
 *
 * <p>resources, services, statistics, report, distribution, knowledge, events, debug and context.
 */
public enum KlabCLI {

  INSTANCE;

  private String prompt = "k.LAB> ";
  private ModelerImpl modeler;
  private LineReader reader;
  private CLIStartupOptions options;
  private CommandLine commandLine;

  public Engine engine() {
    return modeler.engine();
  }

  public UserScope user() {
    return modeler.user();
  }

  public ModelerImpl modeler() {
    return this.modeler;
  }

  private String getContextPrompt() {
    String ret = null;
    if (modeler.getCurrentContext() != null) {
      ret = modeler.getCurrentSession().getName() + "/" + modeler.getCurrentContext().getName();
      if (modeler.getCurrentContext().getContextObservation() != null) {
        ret += "/" + modeler.getCurrentContext().getContextObservation().getName();
      }
      if (modeler.getCurrentContext().getObserver() != null) {
        ret = modeler.getCurrentContext().getObserver().getName() + "@" + ret;
      }
    } else if (modeler.getCurrentSession() != null) {
      ret = modeler.getCurrentSession().getName();
    }
    return ret;
  }

  public void exportWithSchema(KlabService service, List<String> arguments) {}

  public void importWithSchema(KlabService service, String suggestedUrn, List<String> arguments) {

    ResourceTransport.Schema schema = null;
    String result = null;
    if (arguments == null || arguments.isEmpty()) {
      schema = chooseSchemaInteractively(service.capabilities(user()).getImportSchemata());
      if (schema != null) {
        arguments = defineInteractively(service, schema);
        arguments.addFirst("");
      }
    } else {
      schema =
          ResourceTransport.INSTANCE.findSchema(
              arguments.getFirst(), service.capabilities(user()).getImportSchemata(), user());
    }

    if (schema == null) {
      commandLine.getErr().println("No schema found with ID " + arguments.getFirst());
    } else if (schema.getType() == ResourceTransport.Schema.Type.STREAM && arguments.size() == 2) {

      if (arguments.get(1).contains("://")) {
        try {
          var url = new URI(arguments.get(1)).toURL();
          result = service.importAsset(schema, schema.asset(url), suggestedUrn, user());
        } catch (Exception e) {
          commandLine
              .getErr()
              .println(
                  "Import failed with exception:"
                      + org.integratedmodelling.klab.api.utils.Utils.Exceptions.stackTrace(e));
          return;
        }
      } else {
        File file = new File(arguments.get(1));
        if (file.exists()) {
          try {
            result = service.importAsset(schema, schema.asset(file), suggestedUrn, user());
          } catch (Exception e) {
            commandLine
                .getErr()
                .println("Import failed with exception:" + Utils.Exceptions.stackTrace(e));
            return;
          }
        }
      }
    } else {
      if (schema.getType() == ResourceTransport.Schema.Type.PROPERTIES
          && arguments.size() == schema.getProperties().size() + 1) {
        var params = org.integratedmodelling.klab.api.collections.Parameters.create();
        int i = 0;
        for (var property : schema.getProperties().keySet()) {
          var descriptor = schema.getProperties().get(property);
          var argValue = arguments.get(i + 1);
          if (!"_".equals(argValue)) {
            params.put(property, Utils.Data.convertValue(argValue, descriptor.type()));
          } else if (descriptor.defaultValue() != null) {
            params.put(property, descriptor.defaultValue());
          } else if (!descriptor.optional()) {
            commandLine
                .getErr()
                .println("Property " + property + " is mandatory and must be " + "supplied");
            return;
          }
          i++;
        }
        try {
          result = service.importAsset(schema, schema.asset(params), suggestedUrn, user());
        } catch (Throwable t) {
          commandLine
              .getErr()
              .println("Import failed with exception:" + Utils.Exceptions.stackTrace(t));
          return;
        }
      } else {
        commandLine
            .getErr()
            .println(
                "Import call with schema ID "
                    + arguments.getFirst()
                    + " "
                    + "failed: argument mismatch");
        return;
      }
    }

    if (result == null || result.isEmpty()) {
      commandLine.getErr().println("Import was rejected by service (URN is null)");
    } else {
      commandLine.getOut().println("Import to service succeeded: URN is " + result);
    }
  }

  public ResourceTransport.Schema chooseSchemaInteractively(
      Map<String, List<ResourceTransport.Schema>> schemata) {

    List<Pair<String, ResourceTransport.Schema>> choices = new ArrayList<>();
    int n = 1;
    commandLine.getOut().println(Ansi.AUTO.string("Choose a transport schema:"));
    for (String key : schemata.keySet()) {
      for (var schema : schemata.get(key)) {
        commandLine
            .getOut()
            .println(
                Ansi.AUTO.string(
                    "   "
                        + (n++)
                        + ": @|green "
                        + schema.getSchemaId()
                        + "|@ "
                        + "@|yellow "
                        + schema.getDescription()
                        + "|@"));
        choices.add(Pair.of(key, schema));
      }
    }
    var line =
        reader.readLine(
            Ansi.AUTO.string("@|yellow Schema #:|@ "), "", (MaskingCallback) null, null);
    if (Utils.Numbers.encodesInteger(line.trim())) {
      var index = Integer.parseInt(line.trim()) - 1;
      if (index >= 0 && index < choices.size()) {
        return choices.get(index).getSecond();
      }
    }
    return null;
  }

  public List<String> defineInteractively(KlabService service, ResourceTransport.Schema schema) {

    List<String> ret = new ArrayList<>();

    if (schema.getType() == ResourceTransport.Schema.Type.STREAM) {
      var line =
          reader.readLine(
              Ansi.AUTO.string("@|yellow Enter file path or URL:|@ "),
              "",
              (MaskingCallback) null,
              null);
      if (line != null && !line.trim().isEmpty()) {
        ret.add(line.trim());
      }
    } else if (schema.getType() == ResourceTransport.Schema.Type.PROPERTIES) {
      for (var property : schema.getProperties().values()) {
        var line =
            reader.readLine(
                Ansi.AUTO.string(
                    "Value for @|yellow "
                        + property.name()
                        + "|@ "
                        + "["
                        + (property.defaultValue() == null
                            ? (property.optional() ? "optional" : "mandatory")
                            : property.defaultValue())
                        + "]: "),
                "",
                (MaskingCallback) null,
                null);
        if (line.trim().isEmpty()) {
          ret.add("_");
        } else {
          ret.add(line.trim());
        }
      }
    }
    return ret;
  }

  public <T extends KlabService> T service(String service, Class<T> serviceClass) {
    if (service == null || "local".equals(service)) {
      return user().getService(serviceClass);
    } // TODO
    return null;
  }

  public boolean confirm(String prompt) {
    commandLine.getOut().println(Ansi.AUTO.string("@|yellow " + prompt + "|@ (Y/n)?"));
    var line =
        reader.readLine(Ansi.AUTO.string("@|cyan Y/n:|@ "), "", (MaskingCallback) null, null);
    return line == null || line.isEmpty() || line.trim().equalsIgnoreCase("y");
  }

  /** Top-level command that just prints help. */
  @Command(
      name = "",
      description = {
        "k.LAB interactive shell with completion and autosuggestions. "
            + "Hit @|magenta <TAB>|@ to see available commands.",
        "Hit" + " " + "@|magenta ALT-S|@ to toggle tailtips.",
        ""
      },
      footer = {"", "Press Ctrl-D to exit."},
      subcommands = {
        Auth.class,
        Expressions.class,
        CLIReasonerView.class, /*Report.class, Resolver
             .class,*/
        Shutdown.class,
        Credentials.class,
        CLIServicesView.class,
        Run.class,
        PicocliCommands.ClearScreen.class,
        CommandLine.HelpCommand.class,
        Set.class, /*Session.class,
              */
        CLIObservationView.class,
        CLIResourcesView.class,
        Components.class,
        Test.class,
        Run.Alias.class,
        Run.Unalias.class
      })
  static class CliCommands implements Runnable {

    PrintWriter out;

    public void setReader(LineReader reader) {
      out = reader.getTerminal().writer();
    }

    public void run() {
      out.println(new CommandLine(this).getUsageMessage());
    }
  }

  @Command(
      name = "run",
      mixinStandardHelpOptions = true,
      description = {
        "Run scripts, test cases and " + "applications.",
        "Uses autocompletion for " + "behavior " + "and " + "test case " + "names.",
        ""
      },
      subcommands = {Run.List.class, Run.Purge.class})
  static class Run /* extends Monitor */ implements Runnable {

    java.util.Set<SessionScope> running = new LinkedHashSet<>();

    static Map<String, String> aliases = new LinkedHashMap<>();

    @Spec CommandSpec commandSpec;

    @Option(
        names = {"-s", "--synchronous"},
        defaultValue = "false",
        description = {
          "Run in synchronous"
              + " mode,"
              + " "
              + "returning "
              + "to the"
              + " "
              + "prompt"
              + " "
              + "when "
              + "the "
              + "script"
              + " has"
              + " finished "
              + "running."
        },
        required = false)
    boolean synchronous;

    @Parameters(
        description = {
          "The full name of one or more script, test case or application.",
          "If " + "not present locally, resolve through the k.LAB network."
        })
    java.util.List<String> scriptNames = new ArrayList<>();

    public Run() {}

    public static void loadAliases() {
      File aliasFile =
          new File(
              System.getProperty("user.home")
                  + File.separator
                  + ".klab"
                  + File.separator
                  + "kcli"
                  + File.separator
                  + "aliases.txt");
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
          new File(
              System.getProperty("user.home")
                  + File.separator
                  + ".klab"
                  + File.separator
                  + "kcli"
                  + File.separator
                  + "aliases.txt");
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

          //                    KActorsBehavior behavior = Engine.INSTANCE.getCurrentUser(true,
          //                    null)
          //                            .getService(ResourcesService.class)
          //                            .resolveBehavior(scriptName, Engine.INSTANCE
          //                            .getCurrentUser());
          //
          //                    if (behavior == null) {
          //                        out.println(Ansi.AUTO.string("Behavior @|red " +
          //                        scriptName +
          //                        "|@ unknown or not " +
          //                                "available"));
          //                    } else {
          //                        out.println(Ansi.AUTO.string("Running @|green " + scriptName
          //                        + "|@..."));
          //                        running.add(Engine.INSTANCE.getCurrentUser().run(scriptName,
          //                        behavior.getType()));
          //                    }
        }
      }
    }

    public void list() {

      int n = 1;
      for (SessionScope scope : running) {
        commandSpec
            .commandLine()
            .getOut()
            .println("   " + n++ + ". " + scope.getName() + " [" + scope.getStatus() + "]");
      }
    }

    @Command(
        name = "list",
        mixinStandardHelpOptions = true,
        description = {"List all running " + "behaviors" + "."})
    static class List implements Runnable {

      @ParentCommand Run parent;

      @Override
      public void run() {
        parent.list();
      }
    }

    @Command(
        name = "alias",
        mixinStandardHelpOptions = true,
        description = {"Define an alias for a " + "command.", "Use @x to store option -x"},
        subcommands = {Alias.List.class, Alias.Clear.class})
    static class Alias implements Runnable {

      @Command(
          name = "list",
          mixinStandardHelpOptions = true,
          description = {"List all aliases."})
      static class List implements Runnable {
        @Spec CommandSpec commandSpec;

        @Override
        public void run() {
          for (String alias : Run.aliases.keySet()) {
            commandSpec
                .commandLine()
                .getOut()
                .println(
                    Ansi.AUTO.string(
                        "@|bold " + alias + "|@: " + "@|green " + Run.aliases.get(alias) + "|@"));
          }
        }
      }

      @Command(
          name = "clear",
          mixinStandardHelpOptions = true,
          description = {"Remove all aliases."})
      static class Clear implements Runnable {
        @Spec CommandSpec commandSpec;

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

      @Spec CommandSpec commandSpec;

      @Override
      public void run() {

        if (arguments == null || arguments.size() == 0) {
          for (String alias : Run.aliases.keySet()) {
            commandSpec
                .commandLine()
                .getOut()
                .println(
                    Ansi.AUTO.string(
                        "@|bold " + alias + "|@: " + "@|green " + Run.aliases.get(alias) + "|@"));
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
        String value = Utils.Strings.join(arguments.subList(1, arguments.size()), " ");
        Run.aliases.put(alias, value);
        Run.storeAliases();
      }
    }

    @Command(
        name = "unalias",
        mixinStandardHelpOptions = true,
        description = {"Remove a command " + "alias."})
    static class Unalias implements Runnable {

      @Parameters String alias;

      @Override
      public void run() {
        Run.aliases.remove(alias);
        Run.storeAliases();
      }
    }

    @Command(
        name = "purge",
        mixinStandardHelpOptions = true,
        description = {"Remove finished or " + "aborted " + "behaviors" + " from the " + "list."})
    static class Purge implements Runnable {

      @Parameters(
          description = {
            "The numeric ID of the scripts we want to purge. No argument "
                + "removes"
                + " all that have "
                + "finished.",
            "Run \"run list\" to " + "know " + "the IDs."
          })
      java.util.List<Integer> appIds = new ArrayList<>();

      @ParentCommand Run parent;

      @Override
      public void run() {
        if (appIds.isEmpty()) {
          java.util.Set<SessionScope> removed = new HashSet<>();
          for (SessionScope s : parent.running) {
            if (s.getStatus() != Status.STARTED && s.getStatus() != Status.WAITING) {
              s.close();
              removed.add(s);
            }
          }
          parent.running.removeAll(removed);
        } else {
          java.util.List<SessionScope> scopes = new ArrayList<>(parent.running);
          for (int appId : appIds) {
            SessionScope s = scopes.get(appId + 1);
            s.close();
            parent.running.remove(s);
          }
        }
        parent.list();
      }
    }
  }

  public static void main(String[] args) {
    //        AnsiConsole.systemInstall();

    INSTANCE.options = CLIStartupOptions.create(args);

    try {

      // create the modeler
      INSTANCE.modeler = new CommandLineModeler();
      // Configure messages for CLI use
      INSTANCE.modeler.setOption(ModelerImpl.Option.UseAnsiEscapeSequences, true);

      Supplier<Path> workDir =
          () ->
              Paths.get(
                  System.getProperty("user.home")
                      + File.separator
                      + ".klab"
                      + File.separator
                      + "kcli");

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
      INSTANCE.commandLine = new CommandLine(commands, factory);
      PicocliCommands picocliCommands = new PicocliCommands(INSTANCE.commandLine);
      File historyFile =
          new File(Configuration.INSTANCE.getDataPath() + File.separator + "kcli" + ".history");
      Parser parser = new DefaultParser();
      try (Terminal terminal = TerminalBuilder.builder().build()) {

        SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, workDir, null);
        systemRegistry.setCommandRegistries(builtins, picocliCommands);
        systemRegistry.register("help", picocliCommands);
        KlabCompleter completer = new KlabCompleter(systemRegistry.completer());
        History history = new DefaultHistory();
        INSTANCE.reader =
            LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .parser(parser)
                .variable(LineReader.LIST_MAX, 50) // candidates
                .history(history)
                .build();

        builtins.setLineReader(INSTANCE.reader);
        commands.setReader(INSTANCE.reader);
        factory.setTerminal(terminal);
        history.attach(INSTANCE.reader);

        TailTipWidgets widgets =
            new TailTipWidgets(
                INSTANCE.reader,
                systemRegistry::commandDescription,
                5,
                TailTipWidgets.TipType.COMPLETER);
        widgets.enable();
        KeyMap<Binding> keyMap = INSTANCE.reader.getKeyMaps().get("main");
        keyMap.bind(new Reference("tailtip-toggle"), KeyMap.alt("s"));

        /** If we have a command, run it and exit FIXME use options field */
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

        if (historyFile.exists()) {
          history.read(historyFile.toPath(), true);
        }

        Run.loadAliases();

        // boot the engine. This will schedule processes so it wont'delay startup.
        INSTANCE.modeler.boot();

        // start the shell and process input until the user quits with Ctrl-D
        String line;
        while (true) {
          try {

            systemRegistry.cleanUp();
            line =
                INSTANCE.reader.readLine(
                    INSTANCE.prompt, INSTANCE.getContextPrompt(), (MaskingCallback) null, null);
            completer.resetSemanticSearch();
            boolean aliased = false;

            /*
             * Use <, >, .. to move through context observations, @ to set/reset the
             * observer and
             * ./? to inquire about the current context in  detail. The right prompt
             * summarizes
             * the current context focus.
             */
            if (line.trim().startsWith(".")
                || line.trim().startsWith("<")
                || line.trim().startsWith("@")
                || line.trim().startsWith(">")
                || line.trim().startsWith("?")) {
              INSTANCE.setFocalScope(line.trim());
              continue;
            } else if (line.trim().startsWith("-")) {
              if (line.trim().equals("-") && history.size() > 0) {
                line = history.get(history.last() - 1);
                aliased = true;
              } else if (org.integratedmodelling.klab.api.utils.Utils.Numbers.encodesInteger(
                  line.trim().substring(1))) {
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
              INSTANCE.commandLine.getOut().println(Ansi.AUTO.string("@|gray" + line + "|@"));
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

  /**
   * Parse the string for context navigation operators and set the current context to whatever has
   * been asked for.
   *
   * @param line
   */
  private void setFocalScope(String line) {
    if (line.trim().equals(".") || line.trim().startsWith(".?")) {

      int depth = 0;
      if (line.trim().startsWith(".?")) {
        depth = 1;
        for (int i = 2; i < line.trim().length(); i++) {
          if (line.trim().charAt(i) == '?') {
            depth++;
          } else {
            if (line.trim().charAt(i) == '*') {
              depth = -1;
            }
            break;
          }
        }
      }
      printContextInfo(depth);

    } else if (line.trim().equals("..") || line.trim().equals("<")) {

      Scope scope =
          modeler == null
              ? null
              : (modeler.getCurrentSession() == null
                  ? modeler.user()
                  : (modeler.getCurrentContext() == null
                      ? modeler.getCurrentSession()
                      : modeler.getCurrentContext()));
      // context setting
      if (scope == null) {
        INSTANCE.commandLine.getOut().println("No current scope");
      } else if (scope.getType() == Scope.Type.CONTEXT) {

        var parent = scope.getParentScope();
        if (parent != null && parent.getType() == Scope.Type.CONTEXT) {
          modeler.setCurrentContext((ContextScope) parent);
        } else if (parent != null && parent.getType() == Scope.Type.SESSION) {
          modeler.setCurrentContext(null);
        }
        printContextInfo(1);

      } else if (scope.getType() == Scope.Type.SESSION) {
        modeler.setCurrentContext(null);
        modeler.setCurrentSession(null);
        printContextInfo(1);
      }

    } else if (line.startsWith("<<")) {
      this.modeler.setCurrentContext(null);
      this.modeler.setCurrentSession(null);
    } else if (line.startsWith("<")) {
      // must have something after the <
    } else if (line.startsWith(">")) {
      // show list of potential downstream observations or choose the one after the >
    } else if (line.startsWith("@")) {
      // show list of observers or choose the one after the @
    }

    /*
     * TODO
     * < goes back one level of context observation (if any)
     * << goes back to the userscope level
     * >> goes to the innermost non-ambiguous scope and shows what's under it
     * > obsId sets the ID'd context observation as the current context or resets it if no obsId is
     *  given
     *   (equivalent to <)
     * @ obsId sets the observer or resets if no obsId is given
     * ? n prints out the currently known observations (at level n, 1 if not given, full tree if n ==
     *   'all')
     * ?? prints the same info as ? but much more in detail
     */
    var currentContext = user();
    if (modeler.getCurrentContext() != null) {
      currentContext = modeler.getCurrentContext();
    } else if (modeler.getCurrentSession() != null) {
      currentContext = modeler.getCurrentSession();
    }

    if (currentContext == null) {
      INSTANCE.commandLine.getOut().println("No context");
      return;
    }

    var runtime = currentContext.getService(RuntimeService.class);
    if (runtime == null) {
      INSTANCE.commandLine.getOut().println("No runtime service connected");
      return;
    }

    boolean verbose = line.startsWith("??");
    var sessionInfo = runtime.getSessionInfo(currentContext);

    if (line.startsWith("?")) {

      int n = 0;
      for (var session : sessionInfo) {
        listSession(session, verbose, ++n);
      }
    }
  }

  private void printObservationTree(int depth, boolean verbose) {
    var scope = modeler.getCurrentScope();
    if (scope instanceof ContextScope contextScope) {
      if (contextScope.getContextObservation() == null) {
        for (var observation : contextScope.getObservations()) {
          printObservation(observation, 0, depth, verbose, contextScope);
        }
      } else {
        printObservation(contextScope.getContextObservation(), 0, depth, verbose, contextScope);
      }
    }
  }

  private void printObservation(
      Observation observation, int indent, int depth, boolean verbose, ContextScope scope) {
    var spacer = Utils.Strings.spaces(indent * 2);
    INSTANCE
        .commandLine
        .getOut()
        .println(Ansi.AUTO.string(spacer + "@|green " + observation + "|@"));
    if (depth < 0 || depth > 1) {
      for (var obs : scope.getChildrenOf(observation)) {
        printObservation(obs, indent + 1, depth < 0 ? depth : depth - 1, verbose, scope);
      }
    }
  }

  private void printContextInfo(int depth) {
    if (modeler != null && modeler.getCurrentSession() != null) {
      INSTANCE
          .commandLine
          .getOut()
          .println(
              Ansi.AUTO.string("Session: @|green " + modeler.getCurrentSession().getName() + "|@"));
      if (modeler.getCurrentContext() != null) {
        INSTANCE
            .commandLine
            .getOut()
            .println(
                Ansi.AUTO.string(
                    "   Context: @|green " + modeler.getCurrentContext().getName() + "|@"));
        if (modeler.getCurrentContext().getObserver() != null) {
          INSTANCE
              .commandLine
              .getOut()
              .println(
                  Ansi.AUTO.string(
                      "      Observer: @|green "
                          + modeler.getCurrentContext().getObserver()
                          + "|@"));
        }
        if (modeler.getCurrentContext().getContextObservation() != null) {
          INSTANCE
              .commandLine
              .getOut()
              .println(
                  Ansi.AUTO.string(
                      "      Within: @|green "
                          + modeler.getCurrentContext().getContextObservation()
                          + "|@"));
        }
        if (depth != 0) {
          INSTANCE.commandLine.getOut().println(Ansi.AUTO.string("\nObservation structure:"));
          printObservationTree(depth, true);
        }
      }
    }
  }

  private void listSession(SessionInfo session, boolean verbose, int index) {

    INSTANCE
        .commandLine
        .getOut()
        .println(
            Ansi.AUTO.string(
                "@|green Session "
                    + index
                    + "|@. "
                    + session.getName()
                    + " ["
                    + session.getId()
                    + "]"));
    int n = 0;
    for (var context : session.getContexts()) {
      INSTANCE
          .commandLine
          .getOut()
          .println(
              Ansi.AUTO.string(
                  "   @|yellow Context "
                      + index
                      + "."
                      + (++n)
                      + "|@. "
                      + context.getName()
                      + " ["
                      + context.getId()
                      + "]"));
      if (verbose) {}
    }
  }

  public static void printResourceSet(ResourceSet resourceSet, PrintStream out, int indent) {

    if (resourceSet == null) {
      out.println(Utils.Strings.spaces(indent) + "Null resource set");
    } else if (resourceSet.isEmpty()) {
      out.println(Utils.Strings.spaces(indent) + "Empty resource set");
    } else {
      // TODO
      out.println("Namespaces:");
      for (ResourceSet.Resource namespace : resourceSet.getNamespaces()) {
        out.println("   " + namespace);
      }
    }
  }
}
