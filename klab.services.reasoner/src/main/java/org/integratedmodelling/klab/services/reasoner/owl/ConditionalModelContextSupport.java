package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.semanticweb.owlapi.search.EntitySearcher;

/** Joint source obligations, never bearer reassignment, redistribution or an existence proof. */
public final class ConditionalModelContextSupport {
  private ConditionalModelContextSupport() {}

  /** A bound context must prove the whole conjunction, not merely overlap a bearer alternative. */
  public static boolean admits(OWL owl, Concept output, List<Concept> inputs, Concept context) {
    if (context == null || output == null
        || !(output.is(SemanticType.QUALITY) || output.is(SemanticType.PROCESS))) return false;
    var clauses = new OWLSemanticClauseSupport(owl);
    // This route specifically requires an authored container/member contract. Native requests
    // retain their independent existing path, and unrestricted heads cannot invent a route.
    if (clauses.bearerConstraints(output).isEmpty()
        || clauses.clauseCompatibility(output, SemanticRole.INHERENT, context)
            == OWLSemanticClauseSupport.Compatibility.COMPATIBLE
        || !ContextualRequestAdmission.admits(owl, output, context)) return false;
    for (var input : inputs)
      if (clauses.clauseCompatibility(input, SemanticRole.INHERENT, context)
          != OWLSemanticClauseSupport.Compatibility.COMPATIBLE) return false;
    return true;
  }

  /** Each returned type is a REQUIRED conditional context, not a universal container promise. */
  public static List<Concept> contexts(OWL owl, Concept output, List<Concept> inputs) {
    var candidates = new TreeMap<String, Concept>();
    owl.manager.ontologies().forEach(ontology ->
        ObservationalContainmentSupport.declarations(owl, ontology).forEach(axiom -> {
          var container=owl.getConceptFor(axiom.getSubClass().asOWLClass());
          if (container != null) candidates.put(container.getUrn(), container);
        }));
    var clauses=new OWLSemanticClauseSupport(owl);
    for (var input:inputs) for (var bound:clauses.bearerConstraints(input))
      if (!bound.filler().isAnonymous()) {
        var candidate=owl.getConceptFor(bound.filler().asOWLClass());
        if(candidate!=null)candidates.put(candidate.getUrn(),candidate);
      }
    return candidates.values().stream().filter(c -> admits(owl,output,inputs,c)).toList();
  }

  /** A satisfiable required conjunction is an authoring possibility only, never an actual
   * context proof or an assertion that any observation/member exists. Preserve full OWL fillers,
   * including unions inside independent conjunctive clauses; export only semantic references. */
  public static List<Concept> possibleContainers(OWL owl, Concept output, List<Concept> inputs) {
    if (output==null || !(output.is(SemanticType.QUALITY) || output.is(SemanticType.PROCESS))
        || !owl.hasReasoner() || inputs.stream().anyMatch(input ->
        !(input.is(SemanticType.QUALITY) || input.is(SemanticType.PROCESS)))) return List.of();
    var candidates=new TreeMap<String,Concept>();
    owl.manager.ontologies().forEach(ontology ->
        ObservationalContainmentSupport.declarations(owl,ontology).forEach(axiom -> {
          var container=owl.getConceptFor(axiom.getSubClass().asOWLClass());
          if(container!=null)candidates.put(container.getUrn(),container);
        }));
    var clauses=new OWLSemanticClauseSupport(owl);
    var result=new ArrayList<Concept>();
    for(var container:candidates.values()) {
      if(clauses.bearerConstraints(output).isEmpty()
          || clauses.clauseCompatibility(output,SemanticRole.INHERENT,container)==OWLSemanticClauseSupport.Compatibility.COMPATIBLE
          || !ContextualRequestAdmission.admits(owl,output,container))continue;
      var required=new LinkedHashSet<org.semanticweb.owlapi.model.OWLClassExpression>();
      required.add(owl.getOWLClass(container));
      for(var input:inputs)clauses.bearerConstraints(input).forEach(bound -> required.add(bound.filler()));
      var conjunction=owl.manager.getOWLDataFactory().getOWLObjectIntersectionOf(required);
      if(owl.isSatisfiable(conjunction))result.add(container);
    }
    return List.copyOf(result);
  }

  /** Explicit nearest canonical unit metadata only. Missing/ambiguous branches fail closed.
   * Ratio ancestry and physical dimensional compatibility are insufficient (angles may be
   * dimensionless to a unit library, and ratios can themselves be dimensionful). */
  public static boolean explicitlyUnitless(OWL owl, Concept concept) {
    var pending=new ArrayDeque<Concept>();pending.add(concept);
    var visited=new HashSet<String>();var units=new HashSet<String>();
    while(!pending.isEmpty()) {
      var current=pending.removeFirst();
      if(!visited.add(current.getUrn()))continue;
      if(visited.size()>1024)return false;
      var declared=new HashSet<String>();
      var value=current.getMetadata().get(org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.SI_UNIT_PROPERTY);
      if(value!=null) {
        if(!(value instanceof String text) || text.isBlank())return false;
        declared.add(text);
      }
      owl.manager.ontologies().flatMap(o -> EntitySearcher.getAnnotations(owl.getOWLClass(current),o))
          .filter(a -> a.getProperty().getIRI().toString().equals("http://integratedmodelling.org/odo#unit"))
          .forEach(a -> a.getValue().asLiteral().ifPresent(l -> declared.add(l.getLiteral())));
      if(!declared.isEmpty())units.addAll(declared);
      else {
        var parents=owl.getParents(current);
        if(parents.isEmpty())return false;
        pending.addAll(parents);
      }
    }
    return units.equals(Set.of("1"));
  }
}
