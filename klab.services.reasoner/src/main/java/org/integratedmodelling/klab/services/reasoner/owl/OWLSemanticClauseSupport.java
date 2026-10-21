package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.indexing.SemanticClauseSupport;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;

/** Preserves complete OWL fillers: a union is one bound, multiple restrictions are conjunctive. */
public final class OWLSemanticClauseSupport extends SemanticClauseSupport {
  private final OWL owl;
  static final Runnable NO_CHECKPOINT = () -> {};
  public static final IRI UNIVERSAL_BEARER = IRI.create("urn:klab:validation:universal-bearer");
  /** Authored bearer bounds remain distinguishable from description-derived intersections. */
  public static final IRI AUTHORED_BEARER = IRI.create("urn:klab:validation:authored-bearer");

  private record Bound(
      SemanticRole role, OWLClassExpression filler, boolean inherited, boolean applicability, String source,
      OWLObjectPropertyExpression property, OWLClassExpression restriction, boolean semantic) {}

  private final Map<String, List<Bound>> cache =
      new LinkedHashMap<>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Bound>> entry) {
          return size() > 512;
        }
      };
  private static final Map<SemanticRole, String> PROPERTIES =
      Map.ofEntries(
          Map.entry(SemanticRole.INHERENT, NS.IS_INHERENT_TO_PROPERTY),
          Map.entry(SemanticRole.GOAL, NS.HAS_PURPOSE_PROPERTY),
          Map.entry(SemanticRole.COMPRESENT, NS.HAS_COMPRESENT_PROPERTY),
          Map.entry(SemanticRole.CAUSANT, NS.HAS_CAUSANT_PROPERTY),
          Map.entry(SemanticRole.CAUSED, NS.HAS_CAUSED_PROPERTY),
          Map.entry(SemanticRole.ADJACENT, NS.IS_ADJACENT_TO_PROPERTY),
          Map.entry(SemanticRole.COOCCURRENT, NS.OCCURS_DURING_PROPERTY),
          Map.entry(SemanticRole.RELATIONSHIP_SOURCE, NS.IMPLIES_SOURCE_PROPERTY),
          Map.entry(SemanticRole.RELATIONSHIP_TARGET, NS.IMPLIES_DESTINATION_PROPERTY));

  public OWLSemanticClauseSupport(OWL owl) {
    super(owl.reasoner());
    this.owl = owl;
  }

  /** Complete bearer constraints; unions stay intact and independent bounds are conjunctive. */
  public record BearerConstraint(OWLClassExpression filler, String source, boolean inherited,
      OWLObjectPropertyExpression property, OWLClassExpression restriction,
      boolean semantic, boolean applicability) {}

  public List<BearerConstraint> bearerConstraints(Concept concept) {
    return bearerConstraints(concept,NO_CHECKPOINT);
  }

  List<BearerConstraint> bearerConstraints(Concept concept,Runnable checkpoint) {
    checkpoint.run();
    return bounds(concept,checkpoint).stream()
        .filter(b -> constrains(b, concept, SemanticRole.INHERENT))
        .map(b -> new BearerConstraint(b.filler(), b.source(), b.inherited(), b.property(),
            b.restriction(), b.semantic(), b.applicability()))
        .toList();
  }

  /** Authored application domains of a configuration, never native-bearer or emergence evidence. */
  List<BearerConstraint> configurationApplicationConstraints(Concept concept,Runnable checkpoint) {
    if (!concept.is(SemanticType.CONFIGURATION)) return List.of();
    return bounds(concept,checkpoint).stream().filter(b -> b.applicability() && b.semantic())
        .map(b -> new BearerConstraint(b.filler(),b.source(),b.inherited(),b.property(),
            b.restriction(),true,true)).toList();
  }

  /** Called only by k.LAB semantic translation, never inferred from arbitrary imported existentials. */
  public static void markUniversalBearer(OWL owl, Concept owner, Property property,
      Concept filler, Ontology ontology) {
    markUniversalBearer(owl, owner, property, owl.getOWLClass(filler), ontology);
  }

  public static void markUniversalBearer(OWL owl, Concept owner, Property property,
      OWLClassExpression filler, Ontology ontology) {
    var f = owl.manager.getOWLDataFactory();
    var marker = f.getOWLAnnotation(f.getOWLAnnotationProperty(UNIVERSAL_BEARER), f.getOWLLiteral(true));
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(owl.getOWLClass(owner),
        f.getOWLObjectSomeValuesFrom(property.getOWLEntity().asOWLObjectProperty(), filler), Set.of(marker)));
  }

  public boolean subPropertyOf(OWLObjectPropertyExpression property, OWLObjectPropertyExpression parent) {
    return subPropertyOf(property,parent,NO_CHECKPOINT);
  }

  boolean subPropertyOf(OWLObjectPropertyExpression property, OWLObjectPropertyExpression parent,Runnable checkpoint) {
    checkpoint.run();
    var supers = new HashSet<OWLObjectPropertyExpression>();
    collectSuperProperties(property, supers,checkpoint);
    return supers.contains(parent) || (owl.hasReasoner() && entails(
        owl.manager.getOWLDataFactory().getOWLSubObjectPropertyOfAxiom(property, parent)));
  }

  public boolean specializes(Concept concept, Concept parent) {
    return specializes(concept,parent,NO_CHECKPOINT);
  }

  boolean specializes(Concept concept, Concept parent,Runnable checkpoint) {
    checkpoint.run();
    return concept != null && parent != null
        && specializes(owl.getOWLClass(concept), owl.getOWLClass(parent), new HashSet<>(),checkpoint);
  }

  public boolean hasDescription(Concept concept) {
    return hasDescription(concept,NO_CHECKPOINT);
  }

  boolean hasDescription(Concept concept,Runnable checkpoint) {
    return hasDescription(owl.getOWLClass(concept), new HashSet<>(),checkpoint);
  }

  private boolean hasDescription(OWLClassExpression expression, Set<OWLClassExpression> visited,Runnable checkpoint) {
    checkpoint.run();
    if (expression == null || !visited.add(expression)) return false;
    if (expression instanceof OWLClass named) {
      if (java.util.stream.Stream.concat(
          EntitySearcher.getSuperClasses(named, owl.manager.ontologies()),
          EntitySearcher.getEquivalentClasses(named, owl.manager.ontologies()))
          .anyMatch(c -> hasDescription(c, visited,checkpoint))) return true;
      if (owl.hasReasoner()) {
        owl.flushReasoner();
        return java.util.stream.Stream.concat(owl.getSuperClasses(named, false).entities(),
            owl.getEquivalentClasses(named).entities()).anyMatch(c -> hasDescription(c, visited,checkpoint));
      }
    } else if (expression instanceof OWLObjectIntersectionOf intersection)
      return intersection.operands().anyMatch(c -> hasDescription(c, visited,checkpoint));
    else if (expression instanceof OWLObjectSomeValuesFrom restriction) {
      var properties = new HashSet<OWLObjectPropertyExpression>();
      collectSuperProperties(restriction.getProperty(), properties);
      if (matchesProperty(properties, "odo:describes"))
        return observableDescriptionTarget(restriction.getFiller(),checkpoint);
      return List.of("describesQuality", "marksQuality", "classifiesQuality", "discretizesQuality",
          "increasesWith", "decreasesWith").stream().anyMatch(p -> matchesProperty(properties, "odo:" + p));
    }
    return false;
  }

  private boolean observableDescriptionTarget(OWLClassExpression expression,Runnable checkpoint) {
    checkpoint.run();
    var observable = owl.getConcept("odo:Observable");
    if (observable != null && specializes(expression, owl.getOWLClass(observable), new HashSet<>(),checkpoint))
      return true;
    if (expression instanceof OWLClass named) {
      var concept = owl.getConceptFor(named);
      return WorldviewDeclarationSupport.isObservableDescriptionTarget(concept);
    }
    if (expression instanceof OWLObjectIntersectionOf intersection)
      return intersection.operands().anyMatch(c -> observableDescriptionTarget(c,checkpoint));
    if (expression instanceof OWLObjectUnionOf union)
      return union.operands().allMatch(c -> observableDescriptionTarget(c,checkpoint));
    return false;
  }

  /** Unknown compatibility is retained, never mistaken for disjointness or established overlap. */
  public enum Compatibility { COMPATIBLE, IMPOSSIBLE, UNRESOLVED }

  // Reuse only DL answers within one pinned, buffering reasoner computation.
  // Asserted traversal remains uncached and false entailment never means incompatible.
  private final ThreadLocal<Map<OWLAxiom, Boolean>> compatibilityEntailments = new ThreadLocal<>();

  public Compatibility bearerCompatibility(Collection<OWLClassExpression> constraints) {
    return bearerCompatibility(constraints,NO_CHECKPOINT);
  }

  Compatibility bearerCompatibility(Collection<OWLClassExpression> constraints,Runnable checkpoint) {
    checkpoint.run();
    synchronized (owl) {
      if (owl.hasPinnedBufferingReasoner()) compatibilityEntailments.set(
          new LinkedHashMap<>(128, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<OWLAxiom, Boolean> eldest) {
              return size() > 8192;
            }
          });
      try {
        return checkBearerCompatibility(constraints,checkpoint);
      } finally {
        compatibilityEntailments.remove();
      }
    }
  }

  private Compatibility checkBearerCompatibility(Collection<OWLClassExpression> constraints,Runnable checkpoint) {
    checkpoint.run();
    if (constraints.isEmpty()) return Compatibility.UNRESOLVED;
    if (impossible(new LinkedHashSet<>(constraints), new int[] {4096},checkpoint))
      return Compatibility.IMPOSSIBLE;
    var intersection = owl.manager.getOWLDataFactory()
        .getOWLObjectIntersectionOf(new HashSet<>(constraints));
    if (owl.hasReasoner()) {
      // A flush can change DL meaning. Nothing is reused across it or the next call.
      compatibilityEntailments.remove();
      owl.flushReasoner();
      return owl.isSatisfiable(intersection) ? Compatibility.COMPATIBLE : Compatibility.IMPOSSIBLE;
    }
    return Compatibility.UNRESOLVED;
  }

  private boolean entails(OWLAxiom axiom) {
    var memo = compatibilityEntailments.get();
    return memo == null ? owl.isEntailed(axiom) : memo.computeIfAbsent(axiom, owl::isEntailed);
  }

  private boolean impossible(Set<OWLClassExpression> constraints, int[] budget,Runnable checkpoint) {
    checkpoint.run();
    var expanded = new LinkedHashSet<OWLClassExpression>();
    for (var constraint : constraints) expandTypes(constraint, expanded,checkpoint);
    return impossibleExpanded(expanded, budget,checkpoint);
  }
  private boolean impossibleExpanded(Set<OWLClassExpression> expanded, int[] budget,Runnable checkpoint) {
    checkpoint.run();
    if (--budget[0] < 0) return false;
    if (expanded.stream().anyMatch(OWLClassExpression::isOWLNothing) || disjoint(expanded,checkpoint)
        || cardinalityConflict(expanded,checkpoint)) return true;
    for (var expression : expanded) {
      if (expression instanceof OWLObjectComplementOf complement
          && expanded.stream().anyMatch(c -> specializes(c, complement.getOperand(), new HashSet<>(),checkpoint)))
        return true;
      if (expression instanceof OWLObjectUnionOf union)
        return union.operands().allMatch(branch -> {
          var next = new LinkedHashSet<>(expanded);
          next.remove(expression);
          expandTypes(branch, next,checkpoint);
          next.remove(expression);
          return impossibleExpanded(next, budget,checkpoint);
        });
    }
    return false;
  }

  /** Sound bootstrap bounds only: never sum independent witnesses or assume their identity. */
  private boolean cardinalityConflict(Set<OWLClassExpression> constraints,Runnable checkpoint) {
    checkpoint.run();
    for (var expression : constraints) {
      checkpoint.run();
      if (!(expression instanceof OWLObjectMaxCardinality
          || expression instanceof OWLObjectExactCardinality)) continue;
      var maximum = (OWLObjectCardinalityRestriction) expression;
      for (var required : constraints) {
        checkpoint.run();
        int count;
        OWLObjectPropertyExpression property;
        OWLClassExpression filler;
        if (required instanceof OWLObjectSomeValuesFrom some) {
          count = 1; property = some.getProperty(); filler = some.getFiller();
        } else if (required instanceof OWLObjectMinCardinality
            || required instanceof OWLObjectExactCardinality) {
          var minimum = (OWLObjectCardinalityRestriction) required;
          count = minimum.getCardinality(); property = minimum.getProperty(); filler = minimum.getFiller();
        } else continue;
        if (count <= maximum.getCardinality() || !subPropertyOf(property, maximum.getProperty(),checkpoint)) continue;
        var effective = new LinkedHashSet<OWLClassExpression>();
        effective.add(filler);
        for (var restriction : constraints)
          if (restriction instanceof OWLObjectAllValuesFrom all
              && subPropertyOf(property, all.getProperty(),checkpoint)) effective.add(all.getFiller());
        var witnessType = effective.size() == 1 ? filler
            : owl.manager.getOWLDataFactory().getOWLObjectIntersectionOf(effective);
        if (specializes(witnessType, maximum.getFiller(), new HashSet<>(),checkpoint)) return true;
      }
    }
    return false;
  }

  private void expandTypes(OWLClassExpression expression, Set<OWLClassExpression> result,Runnable checkpoint) {
    checkpoint.run();
    if (!result.add(expression)) return;
    if (expression instanceof OWLObjectIntersectionOf intersection)
      intersection.operands().forEach(c -> expandTypes(c, result,checkpoint));
    else if (expression instanceof OWLClass named) {
      EntitySearcher.getSuperClasses(named, owl.manager.ontologies()).forEach(c -> expandTypes(c, result,checkpoint));
      EntitySearcher.getEquivalentClasses(named, owl.manager.ontologies()).forEach(c -> expandTypes(c, result,checkpoint));
    }
  }

  private boolean disjoint(Set<OWLClassExpression> constraints,Runnable checkpoint) {
    checkpoint.run();
    for (var ontology : owl.manager.getOntologies())
      for (var axiom : ontology.getAxioms(AxiomType.DISJOINT_CLASSES)) {
        checkpoint.run();
        long matched = axiom.classExpressions().filter(bound -> constraints.stream()
            .anyMatch(c -> specializes(c, bound, new HashSet<>(),checkpoint))).count();
        if (matched > 1) return true;
      }
    return false;
  }

  @Override
  public boolean predicatesCompatible(Collection<Concept> predicates) {
    if (predicates.size() < 2) return true;
    var classes = predicates.stream().map(owl::getOWLClass).toList();
    if (classes.stream().anyMatch(Objects::isNull)) return false;
    if (owl.hasReasoner()) {
      owl.flushReasoner();
      return owl.isSatisfiable(
          owl.manager.getOWLDataFactory().getOWLObjectIntersectionOf(new HashSet<>(classes)));
    }
    // Also support asserted disjointness during bootstrap, before the DL reasoner exists.
    for (var ontology : owl.manager.getOntologies()) {
      for (var axiom : ontology.getAxioms(AxiomType.DISJOINT_CLASSES)) {
        int matched = 0;
        for (var alternative : axiom.getClassExpressions())
          if (classes.stream()
              .anyMatch(candidate -> specializes(candidate, alternative, new HashSet<>())))
            matched++;
        if (matched > 1) return false;
      }
    }
    return true;
  }

  private List<Bound> bounds(Concept concept) {
    return bounds(concept,NO_CHECKPOINT);
  }

  private List<Bound> bounds(Concept concept,Runnable checkpoint) {
    checkpoint.run();
    synchronized (owl) {
      return cache.computeIfAbsent(
          concept.getUrn(),
          key -> {
            var result = new ArrayList<Bound>();
            var root = owl.getOWLClass(concept);
            if (root == null) throw new IllegalArgumentException("No ontology class for " + key);
            visit(root, false, new HashSet<>(), result,checkpoint);
            // Include inferred superclasses as well as asserted/equivalent class definitions.
            if (owl.hasReasoner()) {
              owl.flushReasoner();
              for (var parent : owl.getSuperClasses(root, false).getFlattened())
                if (!parent.isOWLThing() && !parent.equals(root))
                  visit(parent, true, new HashSet<>(), result,checkpoint);
              for (var equivalent : owl.getEquivalentClasses(root).getEntities())
                if (!equivalent.isOWLThing() && !equivalent.equals(root))
                  visit(equivalent, true, new HashSet<>(), result,checkpoint);
            }
            return result.stream()
                .distinct()
                .sorted(
                    Comparator.comparing((Bound b) -> b.role().name())
                        .thenComparing(Bound::inherited)
                        .thenComparing(b -> b.filler().toString()))
                .toList();
          });
    }
  }

  private void visit(
      OWLClassExpression expression, boolean inherited, Set<OWLClass> visited, List<Bound> result,Runnable checkpoint) {
    checkpoint.run();
    visit(expression, inherited, visited, result, "unknown", false,checkpoint);
  }

  private void visit(OWLClassExpression expression, boolean inherited, Set<OWLClass> visited,
      List<Bound> result, String source, boolean semantic,Runnable checkpoint) {
    checkpoint.run();
    if (expression instanceof OWLClass named) {
      if (!visited.add(named)) return;
      source = named.getIRI().toString();
      for (var ontology : owl.manager.getOntologies()) {
        for (var axiom : ontology.getSubClassAxiomsForSubClass(named)) {
          var parent = axiom.getSuperClass();
          visit(parent, inherited || parent instanceof OWLClass, visited, result, source, marked(axiom),checkpoint);
        }
        for (var axiom : ontology.getEquivalentClassesAxioms(named))
          for (var equivalent : axiom.getClassExpressionsMinus(named))
            visit(equivalent, inherited || equivalent instanceof OWLClass, visited, result, source, marked(axiom),checkpoint);
      }
    } else if (expression instanceof OWLObjectIntersectionOf intersection) {
      for (var operand : intersection.getOperands())
        visit(operand, inherited || operand instanceof OWLClass, visited, result, source, semantic,checkpoint);
    } else if (expression instanceof OWLQuantifiedRestriction<?> restriction
        && restriction.getFiller() instanceof OWLClassExpression filler) {
      if (!(restriction.getProperty() instanceof OWLObjectPropertyExpression property)) return;
      // Imported vocabularies (e.g. PROV) need not have a k.LAB namespace. Match OWL
      // properties directly, retaining semantic subproperties without resolving unrelated IRIs.
      var properties = new HashSet<OWLObjectPropertyExpression>();
      collectSuperProperties(property, properties,checkpoint);
      for (var entry : PROPERTIES.entrySet())
        if (matchesProperty(properties, entry.getValue()))
          result.add(new Bound(entry.getKey(), filler, inherited, false, source, property, expression,
              semantic && property.equals(owl.getProperty(entry.getValue()).getOWLEntity())));
      if (matchesProperty(properties, NS.APPLIES_TO_PROPERTY))
        result.add(new Bound(SemanticRole.INHERENT, filler, inherited, true, source, property, expression,
            semantic && property.equals(owl.getProperty(NS.APPLIES_TO_PROPERTY).getOWLEntity())));
      var inherent = owl.getProperty(NS.IS_INHERENT_TO_PROPERTY);
      if (expression instanceof OWLObjectAllValuesFrom && inherent != null
          && !matchesProperty(properties, NS.IS_INHERENT_TO_PROPERTY)
          && subPropertyOf(inherent.getOWLEntity().asOWLObjectProperty(), property,checkpoint))
        result.add(new Bound(SemanticRole.INHERENT, filler, inherited, false, source, property, expression, false));
    }
    // A union in the superclass hierarchy does not assert either branch individually.
  }

  private boolean marked(OWLAxiom axiom) {
    return axiom.annotations().anyMatch(a -> a.getProperty().getIRI().equals(UNIVERSAL_BEARER)
        && a.getValue() instanceof OWLLiteral value && value.isBoolean() && value.parseBoolean());
  }

  private void collectSuperProperties(
      OWLObjectPropertyExpression property, Set<OWLObjectPropertyExpression> properties) {
    collectSuperProperties(property,properties,NO_CHECKPOINT);
  }

  private void collectSuperProperties(
      OWLObjectPropertyExpression property, Set<OWLObjectPropertyExpression> properties,Runnable checkpoint) {
    checkpoint.run();
    if (!properties.add(property)) return;
    EntitySearcher.getSuperProperties(property, owl.manager.ontologies())
        .forEach(parent -> collectSuperProperties(parent, properties,checkpoint));
  }

  private boolean matchesProperty(Set<OWLObjectPropertyExpression> properties, String name) {
    var semanticProperty = owl.getProperty(name);
    return semanticProperty != null && properties.contains(semanticProperty.getOWLEntity());
  }

  @Override
  public boolean hasBounds(Concept owner, SemanticRole role) {
    return bounds(owner).stream().anyMatch(b -> constrains(b, owner, role));
  }

  private boolean constrains(Bound bound, Concept owner, SemanticRole role) {
    return bound.role() == role
        && (!bound.applicability() || SemanticType.isDependent(owner.getType()));
  }

  @Override
  public boolean applicableTo(Concept concept, Concept target) {
    var candidate = owl.getOWLClass(target.isCollective() ? target.singular() : target);
    return candidate != null
        && bounds(concept).stream()
            .filter(Bound::applicability)
            .allMatch(bound -> specializes(candidate, bound.filler(), new HashSet<>()));
  }

  @Override
  public boolean accepts(Concept owner, SemanticRole role, Concept operand) {
    var candidate = owl.getOWLClass(operand.isCollective() ? operand.singular() : operand);
    if (candidate == null) return false;
    return bounds(owner).stream()
        .filter(b -> constrains(b, owner, role))
        .allMatch(b -> specializes(candidate, b.filler(), new HashSet<>()));
  }

  /**
   * Source-clause membership is proved specialization, rather than possible overlap.
   * A predicate applied to a substantial requests classification/characterization of that
   * substantial; its described quality can have a separate bearer (e.g. a configuration).
   */
  public Compatibility clauseCompatibility(Concept owner, SemanticRole role, Concept operand) {
    var candidate = owl.getOWLClass(operand.isCollective() ? operand.singular() : operand);
    if (candidate == null) return Compatibility.UNRESOLVED;
    synchronized (owl) {
      var required = new LinkedHashSet<OWLClassExpression>();
      if (role == SemanticRole.INHERENT && owner.is(SemanticType.PREDICATE)
          && SemanticType.isEnumerableSubstantial(operand.getType())) {
        var visited = new HashSet<OWLClass>();
        var root = owl.getOWLClass(owner);
        applicationBounds(root, visited, required);
        if (owl.hasReasoner()) {
          owl.flushReasoner();
          var necessary = new LinkedHashSet<>(owl.getSuperClasses(root, false).getFlattened());
          necessary.addAll(owl.getEquivalentClasses(root).getEntities());
          for (var parent : necessary)
            if (!parent.isOWLThing() && !parent.isOWLNothing()) applicationBounds(parent, visited, required);
        }
      } else {
        bounds(owner).stream().filter(b -> constrains(b, owner, role))
            .map(Bound::filler).forEach(required::add);
      }
      var conjunction = new LinkedHashSet<>(required);
      conjunction.add(candidate);
      if (bearerCompatibility(conjunction) == Compatibility.IMPOSSIBLE) return Compatibility.IMPOSSIBLE;
      return required.stream().allMatch(bound -> specializes(candidate, bound, new HashSet<>()))
          ? Compatibility.COMPATIBLE : Compatibility.UNRESOLVED;
    }
  }

  public static void markAuthoredBearer(OWL owl, Concept owner, Concept bearer, Ontology ontology) {
    var f = owl.manager.getOWLDataFactory();
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLAnnotationAssertionAxiom(
        f.getOWLAnnotationProperty(AUTHORED_BEARER), owl.getOWLClass(owner).getIRI(),
        owl.getOWLClass(bearer).getIRI()));
  }

  private void applicationBounds(OWLClassExpression expression, Set<OWLClass> visited,
      Set<OWLClassExpression> result) {
    if (expression instanceof OWLClass named) {
      if (!visited.add(named)) return;
      for (var ontology : owl.manager.getOntologies()) {
        for (var annotation : ontology.getAnnotationAssertionAxioms(named.getIRI()))
          if (annotation.getProperty().getIRI().equals(AUTHORED_BEARER)
              && annotation.getValue() instanceof IRI iri)
            result.add(owl.manager.getOWLDataFactory().getOWLClass(iri));
        for (var axiom : ontology.getSubClassAxiomsForSubClass(named))
          if (!descriptionBearer(axiom)) applicationBounds(axiom.getSuperClass(), visited, result);
        for (var axiom : ontology.getEquivalentClassesAxioms(named))
          if (!descriptionBearer(axiom))
            for (var equivalent : axiom.getClassExpressionsMinus(named))
              applicationBounds(equivalent, visited, result);
      }
    } else if (expression instanceof OWLObjectIntersectionOf intersection) {
      for (var operand : intersection.getOperands()) applicationBounds(operand, visited, result);
    } else if (expression instanceof OWLQuantifiedRestriction<?> restriction
        && restriction.getFiller() instanceof OWLClassExpression filler
        && restriction.getProperty() instanceof OWLObjectPropertyExpression property) {
      var properties = new HashSet<OWLObjectPropertyExpression>();
      collectSuperProperties(property, properties);
      if (matchesProperty(properties, NS.APPLIES_TO_PROPERTY)
          || matchesProperty(properties, NS.IS_INHERENT_TO_PROPERTY)) result.add(filler);
    }
    // A superclass union does not establish either branch's individual bounds.
  }

  private static boolean descriptionBearer(OWLAxiom axiom) {
    return axiom.annotations().anyMatch(a -> a.getProperty().getIRI().equals(DescriptionBearerSupport.PROVENANCE)
        && a.getValue() instanceof OWLLiteral literal
        && literal.getLiteral().equals("derived:description-bearer-intersection"));
  }

  private boolean specializes(
      OWLClassExpression candidate, OWLClassExpression bound, Set<OWLClassExpression> visited) {
    return specializes(candidate,bound,visited,NO_CHECKPOINT);
  }

  private boolean specializes(
      OWLClassExpression candidate, OWLClassExpression bound, Set<OWLClassExpression> visited,Runnable checkpoint) {
    checkpoint.run();
    if (candidate.equals(bound) || bound.isOWLThing()) return true;
    if (owl.hasReasoner()
        && entails(owl.manager.getOWLDataFactory().getOWLSubClassOfAxiom(candidate, bound)))
      return true;
    // During worldview loading, the DL reasoner may not yet exist. Check asserted
    // inheritance without weakening unions or multiple independent restrictions.
    if (bound instanceof OWLObjectUnionOf union)
      return union
          .operands()
          .anyMatch(value -> specializes(candidate, value, new HashSet<>(visited),checkpoint));
    if (bound instanceof OWLObjectIntersectionOf intersection)
      return intersection
          .operands()
          .allMatch(value -> specializes(candidate, value, new HashSet<>(visited),checkpoint));
    if (!visited.add(candidate)) return false;
    if (candidate instanceof OWLClass named)
      return java.util.stream.Stream.concat(
              EntitySearcher.getSuperClasses(named, owl.manager.ontologies()),
              EntitySearcher.getEquivalentClasses(named, owl.manager.ontologies()))
          .anyMatch(value -> specializes(value, bound, new HashSet<>(visited),checkpoint));
    if (candidate instanceof OWLObjectIntersectionOf intersection)
      return intersection
          .operands()
          .anyMatch(value -> specializes(value, bound, new HashSet<>(visited),checkpoint));
    if (candidate instanceof OWLObjectUnionOf union)
      return union.operands().allMatch(value -> specializes(value, bound, new HashSet<>(visited),checkpoint));
    return false;
  }

  @Override
  public List<SemanticClauseRestriction> clauses(Concept concept) {
    var result = new ArrayList<SemanticClauseRestriction>();
    var projected = new HashSet<String>();
    for (var bound : bounds(concept)) {
      if (bound.applicability() && !SemanticType.isDependent(concept.getType())) continue;
      if (!projected.add(bound.role() + ":" + bound.filler() + ":" + bound.inherited() + ":" + bound.applicability())) continue;
      var clause =
          new SemanticClauseRestriction(
              bound.role(),
              bound.filler() instanceof OWLClass named ? owl.getConceptFor(named) : null,
              bound.inherited());
      var code = new ArrayList<StyledKimToken>();
      if (bound.applicability()) {
        var token = new StyledKimToken();
        token.setValue("applies to");
        code.add(token);
      } else
        Arrays.stream(SemanticLexicalElement.values())
            .filter(m -> m.role == bound.role())
            .findFirst()
            .ifPresent(m -> code.add(StyledKimToken.create(m)));
      style(bound.filler(), code);
      clause.setCode(code);
      result.add(clause);
    }
    return result;
  }

  private void style(OWLClassExpression expression, List<StyledKimToken> code) {
    if (expression instanceof OWLClass named && owl.getConceptFor(named) != null) {
      code.add(StyledKimToken.create(owl.getConceptFor(named)));
    } else if (expression instanceof OWLNaryBooleanClassExpression group) {
      code.add(StyledKimToken.create("("));
      boolean first = true;
      for (var operand : group.operands().sorted(Comparator.comparing(Object::toString)).toList()) {
        if (!first)
          code.add(
              StyledKimToken.create(
                  expression instanceof OWLObjectUnionOf
                      ? BinarySemanticOperator.UNION
                      : BinarySemanticOperator.INTERSECTION));
        style(operand, code);
        first = false;
      }
      code.add(StyledKimToken.create(")"));
    } else {
      var token = new StyledKimToken();
      token.setValue(expression.toString());
      code.add(token);
    }
  }
}
