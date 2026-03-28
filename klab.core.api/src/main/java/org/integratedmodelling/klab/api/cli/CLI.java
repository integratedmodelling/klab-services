package org.integratedmodelling.klab.api.cli;

import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CLI {

  List<Command.Builder> commands = new ArrayList<>();
  Map<Command.Builder, Command> commandsMap = new HashMap<>();

  public static void main(String[] args) {}

  public void run() {}

  public Command.Builder add(String name) {
    var ret = new Command.Builder();

    commands.add(ret);
    return ret;
  }

  public abstract CommandLine parse(String commandLine);

  public Object submit(String commandLine) {
    var cl = parse(commandLine);
    // locate command
    // execute handler, return value
    return null;
  }
}
