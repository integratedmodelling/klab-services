package org.integratedmodelling.klab.api.cli;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.scope.Scope;

import java.util.Map;

/** Object passed to the command handler. */
public class CommandLine {

  private Parameters<String> options = Parameters.create();
  private Parameters<String> parameters = Parameters.create();
  private String commandLine;
  private Scope scope;

  public Scope scope() {
    return null;
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
}
