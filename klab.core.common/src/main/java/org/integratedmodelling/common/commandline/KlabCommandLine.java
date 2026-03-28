package org.integratedmodelling.common.commandline;

import org.integratedmodelling.klab.api.cli.CLI;
import org.integratedmodelling.klab.api.cli.CommandLine;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.services.Reasoner;

public class KlabCommandLine extends CLI {

  public KlabCommandLine() {
    super();
    add("reason")
        .description("Reasoner commands")
        .subCommand("parents")
        .handler(cl -> cl.scope().getService(Reasoner.class).parents(cl.get(0, Concept.class)));
  }

  @Override
  public CommandLine parse(String commandLine) {
    return null;
  }
}
