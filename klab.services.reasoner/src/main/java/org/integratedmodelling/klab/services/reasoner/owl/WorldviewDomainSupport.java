package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.function.Function;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.semanticweb.owlapi.model.*;

/** Declaration ownership, independent of semantic inheritance or imported references. */
public final class WorldviewDomainSupport {
  private static final IRI DOMAIN = IRI.create("urn:klab:worldview:declaring-domain");
  public static final IRI AUTHORED_INTERPRETATION = IRI.create("urn:klab:worldview:authored-interpretation");
  private WorldviewDomainSupport() {}

  public static Concept validate(KimOntology source, Function<KimConcept, Concept> resolve) {
    if (source.getDomain() == KimOntology.rootDomain) return null;
    var domain = resolve.apply(source.getDomain());
    if (domain == null || domain.is(SemanticType.NOTHING) || !domain.is(SemanticType.DOMAIN))
      throw new KlabValidationException("ONTOLOGY_DOMAIN_INVALID: " + source.getUrn()
          + " requires a resolved domain");
    return domain;
  }

  public static void bind(OWL owl, KimOntology source, Concept domain) {
    if (domain == null) return;
    for (var statement : source.getStatements()) bind(owl, source.getUrn(), statement, domain);
  }

  public static void bind(OWL owl, String namespace, KimConceptStatement statement, Concept domain) {
    if (domain == null) return;
    // Delegates and core aliases retain the original concept's defining domain.
    if (!statement.isAlias() && statement.getUpperConceptDefined() == null
        && namespace.equals(statement.getNamespace())) {
      var owner = owl.getConcept(namespace + ":" + statement.getUrn());
      if (owner != null) {
        var ontology = owl.getOntology(namespace).getOWLOntology();
        var factory = owl.manager.getOWLDataFactory();
        var subject = owl.getOWLClass(owner).getIRI();
        // Replace ownership on reload, rather than accumulating conflicting annotations.
        owl.manager.removeAxioms(ontology, ontology.getAnnotationAssertionAxioms(subject).stream()
            .filter(a -> a.getProperty().getIRI().equals(DOMAIN)).toList());
        owl.manager.addAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(
            factory.getOWLAnnotationProperty(DOMAIN), subject, owl.getOWLClass(domain).getIRI()));
      }
    }
    for (var child : statement.getChildren()) bind(owl, namespace, child, domain);
  }

  public static java.util.List<Concept> interpretations(OWL owl, Concept owner,
      KimConceptStatement statement, Function<KimConcept, Concept> resolve) {
    var support = new OWLSemanticClauseSupport(owl);
    var knowledge = owl.getConcept("odo:KnowledgeDomain");
    var physical = owl.getConcept("odo:PhysicalDomain");
    boolean knowledgeDomain = knowledge != null && support.specializes(owner, knowledge);
    var links = statement.getInterpretedPhysicalDomains();
    var effective = effectiveInterpretations(owl, owner);
    if (knowledgeDomain && links.isEmpty() && effective.isEmpty())
      throw new KlabValidationException("KNOWLEDGE_DOMAIN_INTERPRETATION_REQUIRED: " + owner.getUrn()
          + " requires an explicitly authored or inherited PhysicalDomain link; anonymous OWL inference is insufficient");
    if (links.isEmpty() && effective.isEmpty()) return java.util.List.of();
    if (knowledge == null || physical == null)
      throw new KlabValidationException("DOMAIN_VOCABULARY_UNAVAILABLE: ODO 0.1.1 domain kinds are required");
    if (!knowledgeDomain)
      throw new KlabValidationException("INTERPRETATION_SOURCE_INVALID: expected a KnowledgeDomain");
    for (var inherited : effective)
      if (inherited.target() == null || !support.specializes(inherited.target(), physical))
        throw new KlabValidationException("INTERPRETATION_TARGET_INVALID: inherited authored link from "
            + inherited.declaringConcept() + " requires a resolved PhysicalDomain");
    var targets = new java.util.ArrayList<Concept>();
    for (var link : links) {
      var target = resolve.apply(link);
      if (target == null || target.is(SemanticType.NOTHING) || !support.specializes(target, physical))
        throw new KlabValidationException("INTERPRETATION_TARGET_INVALID: expected a resolved PhysicalDomain");
      targets.add(target);
    }
    return targets;
  }

  /** Durable evidence, distinct from an anonymous existential entailed by the upper ontology. */
  public record InterpretationGrounding(Concept target, String declaringConcept, boolean inherited) {}

  public static void markInterpretation(OWL owl, Ontology ontology, Concept owner, Concept target) {
    var f = owl.manager.getOWLDataFactory();
    var property = owl.getProperty("odo:interpretsPhysicalDomain").getOWLEntity().asOWLObjectProperty();
    var marker = f.getOWLAnnotation(f.getOWLAnnotationProperty(AUTHORED_INTERPRETATION), f.getOWLLiteral(true));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(owl.getOWLClass(owner),
        f.getOWLObjectSomeValuesFrom(property, owl.getOWLClass(target)), java.util.Set.of(marker)));
  }

  public static java.util.List<InterpretationGrounding> effectiveInterpretations(OWL owl, Concept owner) {
    var result = new java.util.LinkedHashSet<InterpretationGrounding>();
    var root = owl.getOWLClass(owner);
    if (root == null) return java.util.List.of();
    var visited = new java.util.HashSet<OWLClassExpression>();
    collectInterpretations(owl, root, root, visited, result);
    if (owl.hasReasoner()) {
      owl.flushReasoner();
      java.util.stream.Stream.concat(owl.getSuperClasses(root, false).entities(),
          owl.getEquivalentClasses(root).entities())
          .forEach(c -> collectInterpretations(owl, root, c, visited, result));
    }
    return java.util.List.copyOf(result);
  }

  private static void collectInterpretations(OWL owl, OWLClass root, OWLClassExpression expression,
      java.util.Set<OWLClassExpression> visited, java.util.Set<InterpretationGrounding> result) {
    if (!visited.add(expression)) return;
    if (expression instanceof OWLClass named) {
      for (var ontology : owl.manager.getOntologies()) {
        for (var axiom : ontology.getSubClassAxiomsForSubClass(named)) {
          if (axiom.annotations().anyMatch(a -> a.getProperty().getIRI().equals(AUTHORED_INTERPRETATION)
              && a.getValue() instanceof OWLLiteral literal && literal.isBoolean() && literal.parseBoolean())
              && axiom.getSuperClass() instanceof OWLObjectSomeValuesFrom restriction
              && restriction.getFiller() instanceof OWLClass target
              && owl.getProperty("odo:interpretsPhysicalDomain") != null
              && restriction.getProperty().equals(owl.getProperty("odo:interpretsPhysicalDomain").getOWLEntity()))
            result.add(new InterpretationGrounding(owl.getConceptFor(target),
                named.getIRI().toString(), !named.equals(root)));
          collectInterpretations(owl, root, axiom.getSuperClass(), visited, result);
        }
        for (var axiom : ontology.getEquivalentClassesAxioms(named))
          axiom.classExpressions().forEach(c -> collectInterpretations(owl, root, c, visited, result));
      }
    } else if (expression instanceof OWLObjectIntersectionOf intersection)
      intersection.operands().forEach(c -> collectInterpretations(owl, root, c, visited, result));
    // A union does not establish either branch's explicit obligation independently.
  }

  public static void validateGrounding(OWL owl, Concept owner, KimConceptStatement statement) {
    if (!owner.is(SemanticType.QUALITY) || statement.isAbstract()) return;
    var knowledge = owl.getConcept("odo:KnowledgeDomain");
    var domain = domain(owl, owner);
    var support = new OWLSemanticClauseSupport(owl);
    if (knowledge != null && domain != null && support.specializes(domain, knowledge)
        && statement.getObservablesDescribed().isEmpty() && !support.hasDescription(owner))
      throw new KlabValidationException("KNOWLEDGE_QUALITY_GROUNDING_REQUIRED: concrete quality "
          + owner.getUrn() + " requires effective describes grounding; inherited grounding counts");
  }

  public static Concept domain(OWL owl, Concept concept) {
    var type = owl.getOWLClass(concept);
    if (type == null) return null;
    for (var ontology : owl.manager.getOntologies())
      for (var annotation : ontology.getAnnotationAssertionAxioms(type.getIRI()))
        if (annotation.getProperty().getIRI().equals(DOMAIN)
            && annotation.getValue() instanceof IRI iri)
          return owl.getConceptFor(owl.manager.getOWLDataFactory().getOWLClass(iri));
    return null;
  }
}
