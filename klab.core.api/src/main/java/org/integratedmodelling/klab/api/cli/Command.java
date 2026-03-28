package org.integratedmodelling.klab.api.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class Command {

  List<Option> options = new ArrayList<>();
  List<Command> subCommands = new ArrayList<>();
  private Function<CommandLine, Object> handler;

  public static class Builder {

    private Function<CommandLine, Object> handler;
    private List<Option> options = new ArrayList<>();
    private List<Builder> subCommands = new ArrayList<>();
    private Function<CommandLine, Object> commandConsumer;
    private String commandName;
    private String commandDescription;
    private String commandSyntax;
    private String commandHelp;

    public Builder handler(Function<CommandLine, Object> handler) {
      this.handler = handler;
      return this;
    }

    public Builder name(String name) {
      this.commandName = name;
      return this;
    }

    public Builder description(String description) {
      this.commandDescription = description;
      return this;
    }

    public Builder syntax(String syntax) {
      this.commandSyntax = syntax;
      return this;
    }

    public Builder option(
        String name,
        String shortName,
        String description,
        Class<?> valueClass,
        Object defaultValue) {
      var option = new Option(name, shortName, description, valueClass, defaultValue);
      options.add(option);
      return this;
    }

    public Builder subCommand(String name) {
      Builder builder = new Builder();
      builder.commandName = name;
      subCommands.add(builder);
      return builder;
    }

    public Command build() {
      var ret = new Command();
      ret.handler = handler;
      ret.options.addAll(options);
      subCommands.forEach(b -> ret.subCommands.add(b.build()));
      return ret;
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
