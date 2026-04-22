package org.integratedmodelling.klab.api.cli;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import org.integratedmodelling.klab.api.exceptions.KlabCommandLineError;
import org.integratedmodelling.klab.api.utils.Utils;

public abstract class CLI {

  List<Command> commands = new ArrayList<>();
  Map<String, Command> commandMap = new HashMap<>();

  private Function<Object, Object> resultHandler = Function.identity();

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

  /**
   * The result handler will be used to reprocess the result of any command sent through #execute or
   * #submit. The default is the identity function.
   *
   * @param resultHandler
   */
  public void setResultHandler(Function<Object, Object> resultHandler) {
    this.resultHandler = resultHandler;
  }

  public abstract CommandLine parse(String commandLine);

  /**
   * Submit a command line to the CLI and return the result of the command handler. In case of
   * errors, the result will be a {@link Throwable}. The specific case of a command not recognized
   * will result in a {@link KlabCommandLineError} being returned. No exceptions will ever be
   * thrown.
   *
   * <p>The result will be filtered through the installed result handler before being returned. The
   * default result handler simply returns the result.
   *
   * <p>This blocks until the command is executed. Wrap it in a {@link java.util.concurrent.Future}
   * or thread to process asynchronously.
   *
   * @param commandLine
   * @return
   */
  public Object submit(String commandLine) {
    var cl = parse(commandLine);
    if (cl == null) {
      return resultHandler.apply(
          new KlabCommandLineError("Command line parsing failed for: " + commandLine));
    }
    if (cl.isError()) {
      return resultHandler.apply(new KlabCommandLineError(cl.getErrorMessage()));
    } else if (cl.getCommand() != null && cl.getCommand().getHandler() != null) {
      try {
        return resultHandler.apply(cl.getCommand().getHandler().apply(cl));
      } catch (Throwable t) {
        return resultHandler.apply(t);
      }
    }
    return resultHandler.apply(null);
  }

  /**
   * Submit a command line to the CLI and return the result wrapped in a {@link CommandLine} object
   * containing all the parsed info. The command result will be available through {@link
   * CommandLine#getResult()}. Check {@link #submit(String)} for the logic in case of errors and the
   * use of a custom result handler.
   *
   * <p>This blocks until the command is executed. Wrap it in a {@link java.util.concurrent.Future}
   * or thread to process asynchronously.
   *
   * @param commandLine
   * @return
   */
  public CommandLine execute(String commandLine) {
    var cl = parse(commandLine);
    if (cl == null) {
      cl = CommandLine.error(commandLine, "Command line parsing failed for: " + commandLine);
      cl.setResult(resultHandler.apply(null));
    }
    if (cl.isError()) {
      return cl;
    } else if (cl.getCommand() != null && cl.getCommand().getHandler() != null) {
      try {
        var ret = cl.getCommand().getHandler().apply(cl);
        cl.setResult(resultHandler.apply(ret));
      } catch (Throwable t) {
        cl.setResult(resultHandler.apply(t));
        cl.setError(true);
        cl.setErrorMessage(t.getMessage());
      }
    }
    return cl;
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

  // TODO add subcommands, options, arguments
  private void documentCommand(Command command, FormattedString output, int level, int cmdLength) {
    var spacer = " ".repeat(level);
    output.add(
        spacer + Utils.Strings.fillUpLeftAligned(command.getName(), " ", cmdLength),
        new Color(153, 0, 0), // dark red
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
