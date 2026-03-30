package org.integratedmodelling.klab.api.cli;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.utils.Utils;

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
  public <T> T getAs(int n, Class<T> tClass) {
    var param = parameters.size() > n ? parameters.get(n) : null;
    return param == null ? null : convert(param, tClass);
  }

  public <T> T getAs(Class<T> tClass) {
    if (!parameters.isEmpty()) {
      return convert(Utils.Strings.join(parameters, " "), tClass);
    }
    return null;
  }

  private <T> T convert(String string, Class<T> tClass) {

    if (Utils.Data.isPODClass(tClass)) {
      return Utils.Data.asType(string, tClass);
    } else if (Concept.class.isAssignableFrom(tClass) && scope != null) {
      return (T) scope.getService(Reasoner.class).resolveConcept(string);
    } else if (Observable.class.isAssignableFrom(tClass) && scope != null) {
      return (T) scope.getService(Reasoner.class).resolveObservable(string);
    } else if (KimConcept.class.isAssignableFrom(tClass) && scope != null) {
      return (T) scope.getService(ResourcesService.class).declareConcept(string);
    } else if (KimObservable.class.isAssignableFrom(tClass) && scope != null) {
      return (T) scope.getService(ResourcesService.class).declareObservable(string);
    }

    this.errorMessage = "Cannot convert '" + string + "' to " + tClass.getSimpleName();
    this.error = true;

    return null;
  }

  public String ask(String prompt) {
    return null;
  }

  public void setCommand(Command command) {
    this.command = command;
  }
}
