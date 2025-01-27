package org.integratedmodelling.klab.services.reasoner.functors;

import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;

import java.util.List;

/** Functor family for type checking and inspection, used by filters in observation strategies */
@Library(name = "type")
public class TypeFunctors {

  private final Reasoner reasoner;

  public TypeFunctors(ReasonerService reasoner) {
    this.reasoner = reasoner;
  }

  @KlabFunction(
      name = "concrete",
      description = "Check if an observable is concrete",
      type = {Artifact.Type.BOOLEAN})
  public boolean isConcrete(Semantics semantics) {
    return !semantics.isAbstract();
  }

  @KlabFunction(
      name = "abstract",
      description = "Check if an observable is abstract",
      type = {Artifact.Type.BOOLEAN})
  public boolean isAbstract(Semantics semantics) {
    return semantics.isAbstract();
  }

  @KlabFunction(
      name = "collective",
      description = "Check if an observable is collective",
      type = {Artifact.Type.BOOLEAN})
  public boolean isCollective(Semantics semantics) {
    return semantics.asConcept().isCollective();
  }

  @KlabFunction(
          name = "predicates.count",
          description = "Return the number of predicates in the expression",
          type = {Artifact.Type.BOOLEAN})
  public int countPredicates(Semantics semantics) {
    return reasoner.traits(semantics).size() + reasoner.roles(semantics).size();
  }

  @KlabFunction(
      name = "operator.splitfirst",
      description = "Remove the first predicate from an observable and return the two parts",
      type = {Artifact.Type.CONCEPT})
  public List<Concept> splitFirst(Semantics semantics) {
    /** TODO TODO TODO */
    return List.of(semantics.asConcept(), semantics.asConcept());
  }

  @KlabFunction(
          name = "lexicalroot",
          description = "Return the lexical root of a predicate",
          type = {Artifact.Type.CONCEPT})
  public Concept lexicalRoot(Semantics semantics) {
    return reasoner.lexicalRoot(semantics);
  }

  @KlabFunction(
      name = "arity.single",
      description = "Return the singular counterpart of a collective observable",
      type = {Artifact.Type.CONCEPT})
  public Semantics changeArityToSingle(Semantics semantics) {

    if (semantics.asConcept().isCollective()) {
      semantics = SemanticsBuilder.create(semantics.asConcept(), (ReasonerService) reasoner)
                      .collective(false)
                      .buildObservable();
    }

    return semantics;
  }

  @KlabFunction(
      name = "arity.collective",
      description = "Return the collective counterpart of a singular observable",
      type = {Artifact.Type.CONCEPT})
  public Semantics changeArityToCollective(Semantics semantics) {

    if (!semantics.asConcept().isCollective()) {
      semantics = SemanticsBuilder.create(semantics.asConcept(), (ReasonerService) reasoner)
          .collective(true)
          .buildObservable();
    }

    return semantics;
  }
}
