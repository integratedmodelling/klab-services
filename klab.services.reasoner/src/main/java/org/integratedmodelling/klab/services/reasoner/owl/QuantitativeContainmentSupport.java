package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.Objects;
import java.util.function.Function;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.semanticweb.owlapi.model.IRI;

/** Ordered semantic annotations: no physical parthood, ratio or extraction axiom. */
public final class QuantitativeContainmentSupport {
  public static final String CONTRACT_REVISION = "quantitative-containment-v3-odo-extensive-physical-property";
  public static final String PORTION = "urn:klab:semantics:quantitative-portion-identity";
  public static final String WHOLE = "urn:klab:semantics:quantitative-whole-identity";
  private QuantitativeContainmentSupport() {}

  /** A measurement-preserving specialization retains its parent's ordered semantic pair.
   * This is semantic declaration inheritance, never physical parthood or numerical extraction.
   * Unary transformations must retain their operand separately instead of calling this method. */
  public static void inherit(OWL owl, Concept specialization, Concept parent) {
    var portion = parent.getMetadata().get(PORTION);
    var whole = parent.getMetadata().get(WHOLE);
    if (portion == null && whole == null) return;
    if (!(portion instanceof String p) || !(whole instanceof String w))
      throw new KlabValidationException("Incomplete inherited quantitative containment pair");
    if ((specialization.getMetadata().get(PORTION) != null || specialization.getMetadata().get(WHOLE) != null)
        && !sameOperands(specialization, parent))
      throw new KlabValidationException("Conflicting inherited quantitative containment pairs");
    annotate(owl, specialization, PORTION, p);
    annotate(owl, specialization, WHOLE, w);
  }

  public static void apply(OWL owl, Concept quantity, KimConcept syntax,
      Function<KimConcept, Concept> resolve) {
    validate(owl, quantity, syntax, resolve, true);
    if (syntax.getPortionIdentity() == null) return;
    annotate(owl, quantity, PORTION, resolve.apply(syntax.getPortionIdentity()).getUrn());
    annotate(owl, quantity, WHOLE, resolve.apply(syntax.getWholeIdentity()).getUrn());
  }

  /** Validate before creating a generated class so rejected syntax cannot poison the registry. */
  public static void validate(OWL owl, Concept quantity, KimConcept syntax, Function<KimConcept, Concept> resolve) {
    validate(owl, quantity, syntax, resolve, false);
  }

  private static void validate(OWL owl, Concept quantity, KimConcept syntax, Function<KimConcept, Concept> resolve,
      boolean generatedTarget) {
    if (syntax.getPortionIdentity() == null && syntax.getWholeIdentity() == null) return;
    if (syntax.getPortionIdentity() == null || syntax.getWholeIdentity() == null)
      throw new KlabValidationException("Quantitative within requires both identity operands");
    var portion = resolve.apply(syntax.getPortionIdentity());
    var whole = resolve.apply(syntax.getWholeIdentity());
    if (!simpleIdentity(syntax.getPortionIdentity()) || !simpleIdentity(syntax.getWholeIdentity())
        || portion == null || whole == null || !portion.is(SemanticType.IDENTITY)
        || !whole.is(SemanticType.IDENTITY) || portion.is(SemanticType.NOTHING)
        || whole.is(SemanticType.NOTHING) || quantity == null || quantity.is(SemanticType.NOTHING)
        || !extensivePhysicalProperty(owl, quantity))
      throw new KlabValidationException("Quantitative within requires two identities and an extensive physical property");
    var existingPortion = quantity.getMetadata().get(PORTION);
    var existingWhole = quantity.getMetadata().get(WHOLE);
    if ((existingPortion != null || existingWhole != null)
        && (!generatedTarget || !Objects.equals(existingPortion, portion.getUrn())
            || !Objects.equals(existingWhole, whole.getUrn())))
      throw new KlabValidationException("Nested or conflicting quantitative within requires an explicit semantic contract");
  }

  /** Authoritative eligibility follows ODO, including inherited and newly defined subclasses.
   * Semantic flags alone cannot establish membership in this physical-property hierarchy. */
  private static boolean extensivePhysicalProperty(OWL owl, Concept quantity) {
    var root = owl.getConcept(org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.CORE_EXTENSIVE_PHYSICAL_PROPERTY);
    if (root == null || quantity == null) return false;
    var candidateClass = owl.getOWLClass(quantity);
    var rootClass = owl.getOWLClass(root);
    if (candidateClass == null || rootClass == null) return false;
    var pending = new java.util.ArrayDeque<org.semanticweb.owlapi.model.OWLClass>();
    var visited = new java.util.HashSet<org.semanticweb.owlapi.model.OWLClass>();
    pending.add(candidateClass);
    while (!pending.isEmpty()) {
      var current = pending.removeFirst();
      if (!visited.add(current)) continue;
      if (current.equals(rootClass) || owl.isEntailed(
          owl.manager.getOWLDataFactory().getOWLSubClassOfAxiom(current, rootClass))) return true;
      // Generated measurement subclasses may not yet be in the classified closure.
      // Follow their actual named superclass axioms to an authoritative ODO proof.
      org.semanticweb.owlapi.search.EntitySearcher.getSuperClasses(current, owl.manager.ontologies())
          .filter(parent -> !parent.isAnonymous()).map(parent -> parent.asOWLClass())
          .forEach(pending::addLast);
    }
    return false;
  }

  private static boolean simpleIdentity(KimConcept identity) {
    return identity.getName() != null && identity.getObservable() == null
        && identity.getSemanticModifier() == null && identity.getComparisonConcept() == null
        && identity.getModifiers().isEmpty() && identity.getTraits().isEmpty()
        && identity.getRoles().isEmpty() && identity.getAbout().isEmpty()
        && identity.getOperands().isEmpty() && identity.getValueOperators().isEmpty()
        && identity.getPortionIdentity() == null && identity.getWholeIdentity() == null
        && !identity.isCollective() && !identity.isNegated()
        && !identity.is(SemanticType.ANY) && !identity.is(SemanticType.ALL) && !identity.is(SemanticType.NONE);
  }

  private static void annotate(OWL owl, Concept quantity, String key, String value) {
    var factory = owl.manager.getOWLDataFactory();
    var ontology = owl.getOntology(quantity.getNamespace());
    owl.manager.addAxiom(ontology.ontology, factory.getOWLAnnotationAssertionAxiom(
        factory.getOWLAnnotationProperty(IRI.create(key)), owl.getOWLClass(quantity).getIRI(),
        factory.getOWLLiteral(value)));
    quantity.getMetadata().put(key, value);
  }

  /** Conservative matching: generalization or conversion between portions needs its own contract. */
  public static boolean sameOperands(Concept left, Concept right) {
    return Objects.equals(left.getMetadata().get(PORTION), right.getMetadata().get(PORTION))
        && Objects.equals(left.getMetadata().get(WHOLE), right.getMetadata().get(WHOLE));
  }
}
