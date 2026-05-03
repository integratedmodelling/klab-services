package org.integratedmodelling.common.commandline;

import java.util.Collection;
import org.integratedmodelling.common.data.Tree;
import org.integratedmodelling.klab.api.cli.CommandLine;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.services.Reasoner;

public class ReasonCommands {

  /**
   * TODO table if --all, tree otherwise
   *
   * @param commandLine
   * @return
   */
  public static Tree<Concept> parents(CommandLine commandLine) {
    var concept = commandLine.getAs(Concept.class);
    if (concept == null) {
      commandLine.setError(true);
      commandLine.setErrorMessage("Concept '" + commandLine.getCommandLine() + "' not found");
      return null;
    }
    return fillParents(new Tree<>(), commandLine.getScope().getService(Reasoner.class), concept);
    //    return commandLine.getScope().getService(Reasoner.class).allParents(concept);
  }

  private static Tree<Concept> fillChildren(
      Tree<Concept> conceptTree, Reasoner service, Concept concept) {
    conceptTree.addVertex(concept);
    for (var child : service.children(concept)) {
      fillChildren(conceptTree, service, child);
      conceptTree.addEdge(concept, child);
    }
    return conceptTree;
  }

  private static Tree<Concept> fillParents(
      Tree<Concept> conceptTree, Reasoner service, Concept concept) {
    conceptTree.addVertex(concept);
    for (var child : service.parents(concept)) {
      fillParents(conceptTree, service, child);
      conceptTree.addEdge(concept, child);
    }
    return conceptTree;
  }

  public static Collection<Concept> resolving(CommandLine commandLine) {
    var concept = commandLine.getAs(Concept.class);
    if (concept == null) {
      commandLine.setError(true);
      commandLine.setErrorMessage("Concept '" + commandLine.getCommandLine() + "' not found");
      return null;
    }
    return commandLine.getScope().getService(Reasoner.class).resolving(concept);
  }

  public static Concept base(CommandLine commandLine) {
    var concept = commandLine.getAs(Concept.class);
    if (concept == null) {
      commandLine.setError(true);
      commandLine.setErrorMessage("Concept '" + commandLine.getCommandLine() + "' not found");
      return null;
    }
    return concept.is(SemanticType.COUNTABLE)
        ? commandLine
            .getScope()
            .getService(Reasoner.class)
            .baseSubstantialType(concept, commandLine.getScope())
        : commandLine.getScope().getService(Reasoner.class).baseObservable(concept);
  }

  public static int distance(CommandLine commandLine) {
    var concept = commandLine.getAs(String.class);
    var split = concept.split(",");
    if (split.length >= 2) {
      var candidate =
          commandLine.getScope().getService(Reasoner.class).resolveConcept(split[0].trim());
      var observable =
          commandLine.getScope().getService(Reasoner.class).resolveConcept(split[1].trim());
      var context =
          split.length == 3
              ? commandLine.getScope().getService(Reasoner.class).resolveConcept(split[2].trim())
              : null;
      if (candidate != null && observable != null) {
        return commandLine
            .getScope()
            .getService(Reasoner.class)
            .semanticDistance(candidate, observable, context);
      }
    }
    commandLine.setError(true);
    commandLine.setErrorMessage(
        "One or more concepts not found in  '"
            + commandLine.getCommandLine()
            + "': needs to specify at least two comma-separated concepts");
    return -1;
  }

  /**
   * TODO table if --all, tree otherwise
   *
   * @param commandLine
   * @return
   */
  public static Tree<Concept> children(CommandLine commandLine) {
    var concept = commandLine.getAs(Concept.class);
    if (concept == null) {
      commandLine.setError(true);
      commandLine.setErrorMessage("Concept '" + commandLine.getCommandLine() + "' not found");
      return null;
    }
    return fillChildren(new Tree<>(), commandLine.getScope().getService(Reasoner.class), concept);
  }
}
