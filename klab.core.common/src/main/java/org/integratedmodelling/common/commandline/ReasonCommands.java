package org.integratedmodelling.common.commandline;

import org.integratedmodelling.klab.api.cli.CommandLine;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.services.Reasoner;

import java.util.Collection;

public class ReasonCommands {

  public static Collection<Concept> parents(CommandLine commandLine) {
    var concept = commandLine.get(0, Concept.class);
    return commandLine.scope().getService(Reasoner.class).parents(concept);
  }
}
