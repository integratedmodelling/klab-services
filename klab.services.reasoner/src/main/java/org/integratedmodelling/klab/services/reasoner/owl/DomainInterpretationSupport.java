package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;

/** The currently specified domain interpretation of the extensible ABOUT modifier. */
public final class DomainInterpretationSupport {
  public static final String PROPERTY = "odo:interpretsPhysicalDomain";

  private DomainInterpretationSupport() {}


  /** Validate a domain selected by an ontology, without inventing a new authored link. */
  public static void requireEffectiveGrounding(OWL owl, Concept domain) {
    if (domain != null)
      WorldviewDomainSupport.interpretations(owl, domain,
          new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl(), ignored -> null);
  }

  public static Concept compose(OWL owl, Concept base, KimConcept syntax,
      Function<KimConcept, Concept> resolve) {
    if (syntax.getAbout().isEmpty()) return base;
    var support = new OWLSemanticClauseSupport(owl);
    var knowledge = owl.getConcept("odo:KnowledgeDomain");
    var physical = owl.getConcept("odo:PhysicalDomain");
    if (knowledge == null || physical == null || owl.getProperty(PROPERTY) == null)
      throw new KlabValidationException("DOMAIN_VOCABULARY_UNAVAILABLE: ODO domain kinds are required");
    if (base == null || !base.is(SemanticType.DOMAIN) || !support.specializes(base, knowledge))
      throw new KlabValidationException("INTERPRETATION_SOURCE_INVALID: about currently requires a KnowledgeDomain");
    var targets = new ArrayList<Concept>();
    for (var expression : syntax.getAbout()) {
      var target = resolve.apply(expression);
      if (target == null || target.is(SemanticType.NOTHING)
          || !target.is(SemanticType.DOMAIN) || !support.specializes(target, physical))
        throw new KlabValidationException("INTERPRETATION_TARGET_INVALID: about currently requires a resolved PhysicalDomain");
      targets.add(target);
    }
    // Never attach a composition's restrictions to its named/imported base.
    var result = owl.makeSubclass(base, syntax.getUrn());
    var ontology = owl.getOntology(result.getNamespace());
    for (var target : targets)
      WorldviewDomainSupport.markInterpretation(owl, ontology, result, target);
    return result;
  }

}
