package org.integratedmodelling.klab.api.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Command {

  private String name;
  private String shortDescription;
  private String longDescription;
  List<Option> options = new ArrayList<>();
  List<Command> subcommands = new ArrayList<>();
  private Function<CommandLine, Object> handler;
  protected Command parent;

  /**
   * Return the depth of this command in the command tree. Root is 1.
   *
   * @return
   */
  public int depth() {
    return parent == null ? 1 : parent.depth() + 1;
  }

  public String getName() {
    return name;
  }

  public String getShortDescription() {
    return shortDescription;
  }

  public String getLongDescription() {
    return longDescription;
  }

  public String getPath() {
    return parent == null ? name : parent.getPath() + "." + name;
  }

  public String getDescription() {
    return shortDescription;
  }

  public List<Option> getOptions() {
    return options;
  }

  public List<Command> getSubcommands() {
    return subcommands;
  }

  public void addOption(Option option) {
    this.options.add(option);
  }

  public void addSubcommand(Command subcommand) {
    this.subcommands.add(subcommand);
  }

  public Function<CommandLine, Object> getHandler() {
    return handler;
  }

  public static class Builder {

    private Function<CommandLine, Object> handler;
    private List<Option> options = new ArrayList<>();
    private List<Builder> subCommands = new ArrayList<>();
    private Function<CommandLine, Object> commandConsumer;
    private String commandName;
    private String commandDescription;
    private String commandShortDescription;
    private String commandHelp;
    private Builder parent;

    CLI cli;

    public Builder() {}

    Builder(CLI cli) {
      this.cli = cli;
    }

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

    /**
     * Add an option with an argument
     *
     * @param name
     * @param shortName
     * @param description
     * @param shortDescription
     * @param valueClass
     * @param defaultValue
     * @return
     */
    public Builder option(
        String name,
        String shortName,
        String description,
        String shortDescription,
        Class<?> valueClass,
        Object defaultValue) {

      var option =
          new Option(name, shortName, description, shortDescription, valueClass, defaultValue);
      options.add(option);
      return this;
    }

    /**
     * Add an option without an argument
     *
     * @param name
     * @param shortName
     * @param description
     * @param shortDescription
     * @return
     */
    public Builder option(
        String name, String shortName, String description, String shortDescription) {

      var option = new Option(name, shortName, description, shortDescription, Void.class, null);
      options.add(option);
      return this;
    }

    /**
     * Add a sub-command. When done, call parent() to return to building the parent command.
     *
     * @param commandName
     * @param commandShortDescription
     * @param commandDescription
     * @return
     */
    public Builder subCommand(
        String commandName, String commandShortDescription, String commandDescription) {
      // No CLI in this one
      var ret = Command.builder(commandName, commandDescription, commandShortDescription);
      ret.parent = this;
      subCommands.add(ret);
      return ret;
    }

    public Builder parent() {
      return this.parent;
    }

    public Command build() {
      var ret = new Command();
      ret.name = commandName;
      ret.shortDescription = commandShortDescription;
      ret.longDescription = commandDescription;
      ret.handler = handler;
      ret.options.addAll(options);
      ret.subcommands.addAll(subCommands.stream().map(Builder::build).toList());
      for (var sub : ret.subcommands) {
        sub.parent = ret;
      }
      if (cli != null) {
        cli.registerCommand(ret);
      }
      return ret;
    }

    public Builder shortDescription(String shortDescription) {
      this.commandShortDescription = shortDescription;
      return this;
    }
  }

  public static Builder builder(
      String commandName, String commandDescription, String commandShortDescription) {
    var ret = new Builder();
    ret.commandName = commandName;
    ret.commandDescription = commandDescription;
    ret.commandShortDescription = commandShortDescription;
    return ret;
  }
}
