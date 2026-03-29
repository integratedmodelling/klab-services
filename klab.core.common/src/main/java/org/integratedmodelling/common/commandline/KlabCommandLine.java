package org.integratedmodelling.common.commandline;

import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.integratedmodelling.klab.api.cli.CLI;
import org.integratedmodelling.klab.api.cli.Command;
import org.integratedmodelling.klab.api.cli.CommandLine;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class KlabCommandLine extends CLI {

  Supplier<Scope> scopeSupplier;

  // commands indexed by path
  Map<String, Command> commands = new HashMap<>();
  Map<String, Options> commandOptions = new HashMap<>();
  CommandLineParser parser = new DefaultParser();

  public KlabCommandLine() {
    this(null);
  }

  public KlabCommandLine(Supplier<Scope> scopeSupplier) {
    super();
    this.scopeSupplier = scopeSupplier;
    command("reason", "Reasoner commands", "Reasoner commands")
        .subCommand("parents", "Parent hierarchy", "List parents of a concept")
        .handler(ReasonCommands::parents)
        .parent()
        .build();
  }

  @Override
  public CommandLine parse(String commandLine) {
    var args = commandLine.split("\\s+");
    var command = findCommand(args);
    if (command == null) {
      return CommandLine.error(commandLine, "Unknown command: " + args[0]);
    }
    var options = commandOptions.computeIfAbsent(command.getPath(), k -> computeOptions(command));
    try {
      parser.parse(options, args);
    } catch (ParseException e) {
      return CommandLine.error(commandLine, e.getMessage());
    }

    CommandLine ret = new CommandLine();
    ret.setCommandLine(commandLine);
    ret.setCommand(command);
    if (scopeSupplier != null) {
      ret.setScope(scopeSupplier.get());
    }

    return ret;
  }

  private Options computeOptions(Command command) {
    var options = new Options();
    // TODO
    return options;
  }

  private Command findCommand(String[] args) {
    return null;
  }
}
