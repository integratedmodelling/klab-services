package org.integratedmodelling.klab.api.cli;

import java.util.*;
import org.integratedmodelling.klab.api.exceptions.KlabCommandLineError;
import org.integratedmodelling.klab.api.utils.Utils;

public abstract class CLI {

  List<Command> commands = new ArrayList<>();
  Map<String, Command> commandMap = new HashMap<>();

  public static void main(String[] args) {}

  public List<Command> getCommands() {
    return commands;
  }

  public void run() {}

  /**
   * Creates a new command builder. After finishing the command, call {@link
   * Command.Builder#build()} to register it with the CLI.
   *
   * @param name
   * @param shortDescription
   * @param longDescription
   * @return
   */
  public Command.Builder command(String name, String shortDescription, String longDescription) {
    var ret = new Command.Builder();
    ret.name(name);
    ret.cli = this;
    ret.shortDescription(shortDescription);
    ret.description(longDescription);
    return ret;
  }

  public abstract CommandLine parse(String commandLine);

  public Object submit(String commandLine) {
    var cl = parse(commandLine);
    if (cl == null) {
      return new KlabCommandLineError("Command line parsing failed for: " + commandLine);
    }
    if (cl.isError()) {
      return new KlabCommandLineError(cl.getErrorMessage());
    } else if (cl.getCommand() != null && cl.getCommand().getHandler() != null) {
      try {
        return cl.getCommand().getHandler().apply(cl);
      } catch (Throwable t) {
        return t;
      }
    }
    return null;
  }

  public void registerCommand(Command ret) {
    if (ret.parent == null) {
      commands.add(ret);
    }
    commandMap.put(ret.getPath(), ret);
    ret.subcommands.forEach(this::registerCommand);
  }

  protected Command findCommand(String[] args) {
    if (args.length > 0) {
      var command = commandMap.get(args[0]);
      int next = 1;
      var current = command;
      while (current != null && args.length > next) {
        var nextLevel = command.getPath() + "." + args[next];
        current = commandMap.get(nextLevel);
        if (current == null) {
          break;
        }
        command = current;
        next++;
      }
      return command;
    }
    return null;
  }

  public FormattedString help(CommandLine commandLine) {

    var ret = new FormattedString();
    var cmdLength =
        commandMap.values().stream().mapToInt(c -> c.getName().length()).max().orElse(0) + 3;

    ret.addLine("Commands: \n");

    for (var command : commands) {
      documentCommand(command, ret, 2, cmdLength);
    }

    return ret;
  }

  private void documentCommand(Command command, FormattedString output, int level, int cmdLength) {
    var spacer = " ".repeat(level);
    output.add(
        spacer + Utils.Strings.fillUpLeftAligned(command.getName(), " ", cmdLength),
        FormattedString.Style.BOLD);
    output.add(command.getLongDescription(), FormattedString.Style.ITALIC);
    output.addLine();
    for (var option : command.getOptions()) {
      // TODO
    }
    for (var subcommand : command.getSubcommands()) {
      documentCommand(subcommand, output, level + 2, cmdLength);
    }
  }
}
