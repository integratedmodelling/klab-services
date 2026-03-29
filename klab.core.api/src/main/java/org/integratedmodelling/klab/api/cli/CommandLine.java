package org.integratedmodelling.klab.api.cli;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.scope.Scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Object passed to the command handler. */
public class CommandLine {

  private Parameters<String> options = Parameters.create();
  private List<String> parameters = new ArrayList<>();
  private String commandLine;
  private Scope scope;
  private String errorMessage;
  private boolean error;
  private Command command;

  public static CommandLine error(String commandLine, String message) {
    var result = new CommandLine();
    result.errorMessage = message;
    result.error = true;
    result.commandLine = commandLine;
    return result;
  }

  public Scope scope() {
    return null;
  }

  public Parameters<String> getOptions() {
    return options;
  }

  public void setOptions(Parameters<String> options) {
    this.options = options;
  }

  public List<String> getParameters() {
    return parameters;
  }

  public void setParameters(List<String> parameters) {
    this.parameters = parameters;
  }

  public String getCommandLine() {
    return commandLine;
  }

  public void setCommandLine(String commandLine) {
    this.commandLine = commandLine;
  }

  public Scope getScope() {
    return scope;
  }

  public void setScope(Scope scope) {
    this.scope = scope;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public boolean isError() {
    return error;
  }

  public void setError(boolean error) {
    this.error = error;
  }

  public Command getCommand() {
    return command;
  }

  /**
   * Return the value of the nth parameter turned into the given class with any admitted conversion.
   *
   * @param n
   * @param tClass
   * @return
   * @param <T>
   */
  public <T> T get(int n, Class<T> tClass) {
    return null;
  }

  public String ask(String prompt) {
    return null;
  }

  public void setCommand(Command command) {
    this.command = command;
  }
}
