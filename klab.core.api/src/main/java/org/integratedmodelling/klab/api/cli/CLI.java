package org.integratedmodelling.klab.api.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    // locate command
    // execute handler, return value
    return null;
  }

  public void registerCommand(Command ret) {
    if (ret.parent == null) {
      commands.add(ret);
    }
    commandMap.put(ret.getPath(), ret);
    ret.subcommands.forEach(this::registerCommand);
  }
}
