package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.semanticweb.owlapi.model.*;

/** Additive authored contracts, not OWL existence/closure or executable route proofs. */
public final class ObservationalContainmentSupport {
  public static final String PROPERTY = "odo:observationallyContains";
  public static final IRI DECLARATION = IRI.create("urn:klab:validation:observational-containment");

  private ObservationalContainmentSupport() {}

  /** Prepare containment axioms only; this method adds none to the ontology.
   * Resolution callbacks may have their own effects. Candidate publication remains the transaction boundary. */
  static Set<OWLAxiom> prepare(OWL owl, Concept owner, KimConceptStatement statement,
      Function<KimConcept, Concept> resolve) {
    var result = new LinkedHashSet<OWLAxiom>();
    if (statement.getObservationalContainments().isEmpty()) return result;
    var property = owl.getProperty(PROPERTY);
    if (property == null) throw new KlabValidationException("Missing ontology property " + PROPERTY);
    var factory = owl.manager.getOWLDataFactory();
    for (var declaration : statement.getObservationalContainments()) {
      if (declaration == null || declaration.container() == null || declaration.contained() == null)
        throw new KlabValidationException("Observational containment requires both endpoints");
      var container = resolve.apply(declaration.container());
      var contained = resolve.apply(declaration.contained());
      if (!supportedPair(container, contained))
        throw new KlabValidationException("Observational containment requires resolved subject/agent "
            + "endpoints or resolved event/process endpoints in the same family");
      if (!owner.getUrn().equals(container.getUrn()) && !owner.getUrn().equals(contained.getUrn()))
        throw new KlabValidationException("Observational containment must include its declaring concept");
      var marker = factory.getOWLAnnotation(factory.getOWLAnnotationProperty(DECLARATION),
          factory.getOWLLiteral("ADDITIVE_DECLARATION"));
      // Zero is intentional. This asserted tautology carries an authored permission without
      // requiring a member or closing the allowed type list. Never use entailment to discover it:
      // all such minimum-zero restrictions are entailed, even for undeclared filler classes.
      result.add(factory.getOWLSubClassOfAxiom(owl.getOWLClass(container),
          factory.getOWLObjectMinCardinality(0, property.getOWLEntity().asOWLObjectProperty(),
              owl.getOWLClass(contained)), Set.of(marker)));
    }
    return result;
  }

  /** Exact asserted metadata only, not validated admission of arbitrary imported OWL.
   * No implicit inheritance or transitive route inference. */
  public static Set<OWLSubClassOfAxiom> declarations(OWL owl, OWLOntology ontology) {
    var result = new LinkedHashSet<OWLSubClassOfAxiom>();
    var property = owl.getProperty(PROPERTY);
    if (property == null) return Set.of();
    var propertyIri = property.getOWLEntity().asOWLObjectProperty().getIRI();
    ontology.axioms(AxiomType.SUBCLASS_OF).filter(axiom ->
        axiom.annotations().anyMatch(annotation -> annotation.getProperty().getIRI().equals(DECLARATION)
            && annotation.getValue().equals(ontology.getOWLOntologyManager().getOWLDataFactory()
                .getOWLLiteral("ADDITIVE_DECLARATION"))))
        .filter(axiom -> !axiom.getSubClass().isAnonymous()
            && axiom.getSuperClass() instanceof OWLObjectMinCardinality restriction
            && restriction.getCardinality() == 0
            && !restriction.getFiller().isAnonymous()
            && !restriction.getProperty().isAnonymous()
            && restriction.getProperty().asOWLObjectProperty().getIRI()
                .equals(propertyIri))
        .forEach(result::add);
    return Set.copyOf(result);
  }

  /** Authored contracts on a container and its asserted named parent closure.
   * Returns original axioms/provenance, not inferred minimum-zero permissions or executable routes. */
  public static Set<OWLSubClassOfAxiom> declarationsForContainer(OWL owl, Concept container) {
    var owners = new LinkedHashSet<OWLClass>();
    var queue = new java.util.ArrayDeque<Concept>();
    queue.add(container);
    while (!queue.isEmpty()) {
      var current = queue.removeFirst();
      if (owners.add(owl.getOWLClass(current))) queue.addAll(owl.getParents(current));
    }
    var result = new LinkedHashSet<OWLSubClassOfAxiom>();
    owl.manager.ontologies().forEach(ontology -> declarations(owl, ontology).stream()
        .filter(axiom -> owners.contains(axiom.getSubClass().asOWLClass())).forEach(result::add));
    return Set.copyOf(result);
  }

  /** The declaration endpoint matrix also applies when reading marked imported carriers. */
  public static boolean supportedPair(Concept container, Concept contained) {
    int family = family(container);
    return family != 0 && family == family(contained);
  }

  private static int family(Concept concept) {
    if (concept == null || concept.is(SemanticType.NOTHING)) return 0;
    if (concept.is(SemanticType.EVENT) || concept.is(SemanticType.PROCESS)) return 2;
    return concept.is(SemanticType.SUBJECT) || concept.is(SemanticType.AGENT) ? 1 : 0;
  }
}
