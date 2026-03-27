package org.integratedmodelling.klab.api.cli;

import org.integratedmodelling.klab.api.collections.Parameters;

import java.util.Map;

/** Object passed to the command handler. */
public class CommandLine {

  public Parameters<String> options = Parameters.create();
  public Parameters<String> parameters = Parameters.create();
  public String commandLine;

  public String ask() {
    return null;
  }
}
