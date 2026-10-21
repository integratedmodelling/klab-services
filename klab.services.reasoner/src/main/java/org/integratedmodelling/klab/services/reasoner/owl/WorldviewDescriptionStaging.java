package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.semanticweb.owlapi.model.*;

/** Finite work-list closure of universal bearer bounds, without partial-graph DL or OWL rewrites. */
public final class WorldviewDescriptionStaging {
  public record Entry(Ontology ontology,KimOntology source,Concept owner,KimConceptStatement statement,
      Function<KimConcept,Concept> resolve,Scope scope) {}
  public static final IRI BEARER_ORIGIN=IRI.create("urn:klab:validation:bearer-origin");
  private record OriginDefinition(Ontology ontology,OWLClass owner,String value) {}
  private WorldviewDescriptionStaging() {}

  public static Map<String,Long> reconcile(OWL owl,List<Entry> entries) {
    return reconcile(owl,entries,OWLSemanticClauseSupport.NO_CHECKPOINT);
  }

  /** The callback belongs only to this private compilation's stack; never to accepted state. */
  public static Map<String,Long> reconcile(OWL owl,List<Entry> entries,Runnable checkpoint) {
    Objects.requireNonNull(checkpoint).run();
    var support=new OWLSemanticClauseSupport(owl);
    var nodes=new LinkedHashMap<OWLClass,Concept>();
    var bounds=new LinkedHashMap<OWLClass,Set<OWLClassExpression>>();
    var origins=new LinkedHashMap<OWLClass,Map<OWLClassExpression,Set<String>>>();
    var definitions=new LinkedHashMap<String,OriginDefinition>();
    var sourceEntries=new HashMap<OWLClass,Entry>();
    var outgoing=new HashMap<OWLClass,Set<OWLClass>>();
    var inherent=owl.getProperty("odo:isInherentTo").getOWLEntity().asOWLObjectProperty();
    for(var entry:entries) {
      checkpoint.run();
      addNode(owl,entry.owner(),nodes,bounds);
      sourceEntries.put(owl.getOWLClass(entry.owner()),entry);
      for(var description:entry.statement().getObservablesDescribed()) {
        checkpoint.run();
        var target=entry.resolve().apply(description.getFirst());
        addNode(owl,target,nodes,bounds);
      }
    }
    // Only named superclass/intersection edges propagate inherited universal bounds.
    // Fillers, union alternatives and semantic reference edges are not ancestry.
    var pending=new ArrayList<>(nodes.keySet());
    for(int i=0;i<pending.size();i++) {
      checkpoint.run();
      var child=pending.get(i);var parents=new LinkedHashSet<OWLClass>();
      for(var ontology:owl.manager.getOntologies()) {
        checkpoint.run();
        for(var axiom:ontology.getSubClassAxiomsForSubClass(child))namedHeads(axiom.getSuperClass(),parents,checkpoint);
        for(var axiom:ontology.getEquivalentClassesAxioms(child))axiom.classExpressions()
            .filter(e->!e.equals(child)).forEach(e->namedHeads(e,parents,checkpoint));
      }
      for(var parent:parents) {
        checkpoint.run();
        var concept=owl.getConceptFor(parent);if(concept==null)continue;
        if(!nodes.containsKey(parent)) {addNode(owl,concept,nodes,bounds);pending.add(parent);}
        outgoing.computeIfAbsent(parent,k->new LinkedHashSet<>()).add(child);
      }
    }
    for(var node:nodes.entrySet()) {
      checkpoint.run();
      for(var bound:support.bearerConstraints(node.getValue(),checkpoint)) {
        checkpoint.run();
        if(bound.semantic()&&(bound.property().equals(inherent)||bound.applicability())) {
          checkpoint.run();
          var owner=owl.manager.getOWLDataFactory().getOWLClass(IRI.create(bound.source()));
          var entry=sourceEntries.get(owner);
          String origin;
          if(entry!=null)origin=origin(owl,entry,"owl-bound",bound.filler(),definitions);
          else {
            var declaring=owl.getConceptFor(owner);
            String namespace=declaring==null?"":declaring.getNamespace();
            var ontology=owl.getOntology(namespace);
            if(ontology==null)ontology=owl.getOntology(node.getValue().getNamespace());
            String metadata="source="+bound.source()+"|document="+namespace
                +"|clause=owl-bound|bound="+bound.filler()+"|";
            origin="origin:"+org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash(metadata);
            definitions.putIfAbsent(origin,new OriginDefinition(ontology,owner,origin+"|"+metadata));
          }
          var flattened=new LinkedHashSet<OWLClassExpression>();flatten(bound.filler(),flattened,checkpoint);
          for(var filler:flattened)seed(node.getKey(),filler,origin,bounds,origins);
        }
      }
    }
    // Keep authored seeds in the finite closure rather than running expensive
    // compatibility/provenance reconciliation once here and again when complete.
    var factory=owl.manager.getOWLDataFactory();
    for(var entry:entries) {
      checkpoint.run();
      var owner=owl.getOWLClass(entry.owner());
      if(entry.statement().getDeclaredInherent()!=null) {
        var filler=owl.getOWLClass(entry.resolve().apply(entry.statement().getDeclaredInherent()));
        seed(owner,filler,origin(owl,entry,"within",filler,definitions),bounds,origins);
      }
      if(!entry.statement().getAppliesTo().isEmpty()&&SemanticType.isDependent(entry.owner().getType())) {
        var fillers=entry.statement().getAppliesTo().stream()
            .map(a->{checkpoint.run();return owl.getOWLClass(entry.resolve().apply(a.getTarget()));})
            .collect(java.util.stream.Collectors.toSet());
        var filler=fillers.size()==1?fillers.iterator().next():factory.getOWLObjectUnionOf(fillers);
        seed(owner,filler,origin(owl,entry,"applies-to",filler,definitions),bounds,origins);
      }
    }
    for(var entry:entries) {
      checkpoint.run();
      var source=owl.getOWLClass(entry.owner());
      for(var description:entry.statement().getObservablesDescribed()) {
        checkpoint.run();
        var target=entry.resolve().apply(description.getFirst());
        if(entry.owner().is(SemanticType.DOMAIN)&&target.is(SemanticType.CONFIGURATION)
            &&description.getSecond()==KimConceptStatement.DescriptionType.DESCRIBES)continue;
        if(target.is(SemanticType.SUBJECT)||target.is(SemanticType.AGENT)) {
          var filler=owl.getOWLClass(target);
          seed(source,filler,origin(owl,entry,"describes",filler,definitions),bounds,origins);
        }
        else {
          // A named application specialization scopes its describing quality without making
          // the configuration dependent. Emergence ingredients are never bearer seeds.
          var applications=entry.owner().is(SemanticType.QUALITY)
              ? support.configurationApplicationConstraints(target,checkpoint)
              : List.<OWLSemanticClauseSupport.BearerConstraint>of();
          for(var application:applications) {
            var filler=application.filler();
            seed(source,filler,origin(owl,entry,"describes-application",filler,definitions),bounds,origins);
          }
          outgoing.computeIfAbsent(owl.getOWLClass(target),k->new LinkedHashSet<>()).add(source);
        }
      }
    }
    var queue=new ArrayDeque<>(nodes.keySet());var queued=new HashSet<>(nodes.keySet());long visits=0;
    while(!queue.isEmpty()) {
      checkpoint.run();
      var target=queue.remove();queued.remove(target);visits++;
      for(var source:outgoing.getOrDefault(target,Set.of())) {
        checkpoint.run();
        boolean changed=bounds.get(source).addAll(bounds.get(target));
        for(var evidence:origins.getOrDefault(target,Map.of()).entrySet()) {
          checkpoint.run();
          changed|=origins.computeIfAbsent(source,k->new LinkedHashMap<>())
              .computeIfAbsent(evidence.getKey(),k->new LinkedHashSet<>()).addAll(evidence.getValue());
        }
        if(changed&&queued.add(source))queue.add(source);
      }
    }
    var completed=new LinkedHashMap<OWLClass,Set<OWLClassExpression>>();
    bounds.forEach((name,values)->{checkpoint.run();completed.put(name,Set.copyOf(values));});
    var prepared=new LinkedHashMap<Ontology,Set<OWLAxiom>>();
    for(var entry:entries) try {
      checkpoint.run();
      var described=new ArrayList<Concept>();var independent=new ArrayList<Concept>();
      for(var description:entry.statement().getObservablesDescribed()) {
        checkpoint.run();
        var target=entry.resolve().apply(description.getFirst());
        if(entry.owner().is(SemanticType.DOMAIN)&&target.is(SemanticType.CONFIGURATION)
            &&description.getSecond()==KimConceptStatement.DescriptionType.DESCRIBES)independent.add(target);
        else described.add(target);
      }
      var authored=entry.statement().getDeclaredInherent()==null?null:entry.resolve().apply(entry.statement().getDeclaredInherent());
      var applies=entry.statement().getAppliesTo().stream().map(a->{checkpoint.run();return entry.resolve().apply(a.getTarget());}).toList();
      var axioms=DescriptionBearerSupport.prepare(owl,entry.owner(),authored,described,applies,independent,completed,checkpoint);
      checkpoint.run();
      var references=new LinkedHashSet<String>();
      origins.getOrDefault(owl.getOWLClass(entry.owner()),Map.of()).values().forEach(references::addAll);
      var destination=prepared.computeIfAbsent(entry.ontology(),k->new LinkedHashSet<>());
      for(var axiom:axioms) {
        checkpoint.run();
        if(axiom instanceof OWLSubClassOfAxiom subclass&&!references.isEmpty()) {
          var annotations=new LinkedHashSet<>(axiom.getAnnotations());
          for(var reference:references) {
            checkpoint.run();
            annotations.add(factory.getOWLAnnotation(
              factory.getOWLAnnotationProperty(DescriptionBearerSupport.PROVENANCE),factory.getOWLLiteral(reference)));
          }
          destination.add(factory.getOWLSubClassOfAxiom(subclass.getSubClass(),subclass.getSuperClass(),annotations));
        } else destination.add(axiom);
      }
    } catch(java.util.concurrent.CancellationException cancelled) {throw cancelled;}
      catch(RuntimeException error) {entry.scope().error(error,entry.statement());}
    prepared.forEach((ontology,axioms)->{
      checkpoint.run();
      owl.manager.addAxioms(ontology.getOWLOntology(),axioms);
    });
    for(var definition:definitions.values()) {
      checkpoint.run();
      owl.manager.addAxiom(definition.ontology().getOWLOntology(),
        factory.getOWLAnnotationAssertionAxiom(factory.getOWLAnnotationProperty(BEARER_ORIGIN),
            definition.owner().getIRI(),factory.getOWLLiteral(definition.value())));
    }
    for(var entry:entries) try {
      checkpoint.run();
      WorldviewDomainSupport.interpretations(owl,entry.owner(),entry.statement(),entry.resolve(),checkpoint);
      WorldviewDomainSupport.validateGrounding(owl,entry.owner(),entry.statement(),checkpoint);
    } catch(java.util.concurrent.CancellationException cancelled) {throw cancelled;}
      catch(RuntimeException error) {entry.scope().error(error,entry.statement());}
    checkpoint.run();
    return Map.of("nodes",(long)nodes.size(),"edges",outgoing.values().stream().mapToLong(Set::size).sum(),
        "queueVisits",visits,"universalBounds",bounds.values().stream().mapToLong(Set::size).sum(),
        "originDefinitions",(long)definitions.size());
  }

  private static void seed(OWLClass owner,OWLClassExpression filler,String origin,
      Map<OWLClass,Set<OWLClassExpression>> bounds,
      Map<OWLClass,Map<OWLClassExpression,Set<String>>> origins) {
    bounds.get(owner).add(filler);
    origins.computeIfAbsent(owner,k->new LinkedHashMap<>()).computeIfAbsent(filler,k->new LinkedHashSet<>()).add(origin);
  }

  private static String origin(OWL owl,Entry entry,String clause,OWLClassExpression filler,
      Map<String,OriginDefinition> definitions) {
    String metadata="source="+entry.owner().getUrn()+"|document="+entry.source().getUrn()
        +"|project="+Objects.toString(entry.source().getProjectName(),"")
        +"|sourceHash="+org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash(entry.source().getSourceCode())
        +"|offset="+entry.statement().getOffsetInDocument()+"|length="+entry.statement().getLength()
        +"|clause="+clause+"|bound="+filler+"|";
    String id="origin:"+org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash(metadata);
    definitions.putIfAbsent(id,new OriginDefinition(entry.ontology(),owl.getOWLClass(entry.owner()),id+"|"+metadata));
    return id;
  }

  private static void addNode(OWL owl,Concept concept,Map<OWLClass,Concept> nodes,
      Map<OWLClass,Set<OWLClassExpression>> bounds) {
    if(concept==null||concept.is(SemanticType.NOTHING))throw new IllegalArgumentException("Unresolved staged description endpoint");
    var type=owl.getOWLClass(concept);nodes.putIfAbsent(type,concept);bounds.computeIfAbsent(type,k->new LinkedHashSet<>());
  }
  private static void flatten(OWLClassExpression expression,Set<OWLClassExpression> result,Runnable checkpoint) {
    checkpoint.run();
    if(expression instanceof OWLObjectIntersectionOf intersection)intersection.operands().forEach(e->flatten(e,result,checkpoint));
    else result.add(expression);
  }
  private static void namedHeads(OWLClassExpression expression,Set<OWLClass> result,Runnable checkpoint) {
    checkpoint.run();
    if(expression instanceof OWLClass named)result.add(named);
    else if(expression instanceof OWLObjectIntersectionOf intersection)intersection.operands().forEach(e->namedHeads(e,result,checkpoint));
  }
}
