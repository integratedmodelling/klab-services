package org.integratedmodelling.common.commandline;

import java.util.Collection;
import org.integratedmodelling.klab.api.cli.CommandLine;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.services.Reasoner;

public class ReasonCommands {

  public static Collection<Concept> parents(CommandLine commandLine) {
    var concept = commandLine.getAs(Concept.class);
    return commandLine.getScope().getService(Reasoner.class).parents(concept);
  }

  public static Collection<Concept> resolving(CommandLine commandLine) {
    var concept = commandLine.getAs(Concept.class);
    return commandLine.getScope().getService(Reasoner.class).resolving(concept);
  }

  public static Collection<Concept> children(CommandLine commandLine) {
    var concept = commandLine.getAs(Concept.class);
    return commandLine.getScope().getService(Reasoner.class).children(concept);
  }
}
