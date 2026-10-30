package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.*;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.semanticweb.owlapi.model.*;

/** Owner-approved directed refinement of otherwise unchanged positive attribute expressions. */
public final class MonotoneRefinementSupport {
  public static final IRI PROVENANCE = IRI.create("urn:klab:construction:attribute-refinement");
  private record Profile(OWLClass type, String shape, List<String> attributes) {}
  private final OWL owl;
  private final Map<String,Profile> profiles = new LinkedHashMap<>();
  private final Set<OWLSubClassOfAxiom> owned = new LinkedHashSet<>();
  private final Set<String> pending = new LinkedHashSet<>();
  private long epoch;

  MonotoneRefinementSupport(OWL owl) { this.owl=owl; }

  /** Retract only this helper's derived axioms. Authority refresh retains syntax for re-proof. */
  public void reset(boolean discardProfiles) {
    synchronized(owl) {
      synchronized(this) {
        boolean retracted=false;
        try {
          var ontology=owl.getOntology(OWL.INTERNAL_ONTOLOGY_ID);
          if(ontology!=null && !owned.isEmpty()) {
            owl.manager.removeAxioms(ontology.getOWLOntology(),owned);
            owl.flushReasoner();
          }
          retracted=true;
        } finally {
          // Invalidate every captured proof even when the native manager or flush fails.
          owned.clear();pending.clear();
          if(discardProfiles) profiles.clear();
          else pending.addAll(profiles.keySet());
          epoch++;
          if(!retracted)owl.quarantineDerivedClosure();
        }
      }
    }
  }

  public void register(Concept concept,KimConcept syntax,ReasonerService reasoner) {
    var profile=profile(concept,syntax,reasoner);
    if(profile==null)return;
    synchronized(this) {
      var previous=profiles.put(concept.getUrn(),profile);
      if(!profile.equals(previous)){pending.add(concept.getUrn());epoch++;}
    }
    refresh(reasoner);
  }

  private Profile profile(Concept concept,KimConcept syntax,ReasonerService reasoner) {
    if(concept==null || concept.is(SemanticType.NOTHING)
        || org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(concept.getNotifications()) || syntax==null
        || syntax.getName()==null || syntax.getObservable()!=null || syntax.getTraits().isEmpty()
        || !(syntax instanceof KimConceptImpl implementation))return null;
    var attributes=new ArrayList<String>();
    var replacedNames=new HashSet<String>();
    for(var trait:syntax.getTraits()) {
      var declared=trait.getName()==null?null:reasoner.resolveConcept(trait.getName());
      if(declared==null)return null;
      if(declared.is(SemanticType.ATTRIBUTE) && !declared.is(SemanticType.IDENTITY)
          && !declared.is(SemanticType.REALM) && !declared.is(SemanticType.ROLE)) {
        if(trait.getObservable()!=null || !trait.getTraits().isEmpty()
            || !trait.getRoles().isEmpty() || !trait.getModifiers().isEmpty())return null;
        attributes.add(declared.getUrn());replacedNames.add(trait.getName());
      }
      // Non-attribute predicates are retained verbatim in the unchanged-shape key.
    }
    if(attributes.isEmpty() || new HashSet<>(attributes).size()!=attributes.size())return null;
    boolean[] allowed={true};
    var shape=implementation.mapSyntax(List.of(),(path,node)->{
      if(node.isNegated() || node.getSemanticModifier()!=null || node.getComparisonConcept()!=null || !node.getOperands().isEmpty()
          || node.getRelationshipSource()!=null || node.getRelationshipTarget()!=null
          || node.isPattern() || !node.getPatternVariables().isEmpty()
          || node.is(SemanticType.ANY) || node.is(SemanticType.ALL) || node.is(SemanticType.NONE)
          || !node.getValueOperators().isEmpty())allowed[0]=false;
      if(path.isEmpty())node.getTraits().removeIf(trait->replacedNames.contains(trait.getName()));
    });
    if(!allowed[0])return null;
    shape.finalizeDefinition();
    return new Profile(owl.getOWLClass(concept),shape.getUrn(),List.copyOf(attributes));
  }

  private boolean refines(Profile child,Profile parent,ReasonerService reasoner) {
    if(child.type().equals(parent.type()) || !child.shape().equals(parent.shape())
        || child.attributes().size()!=parent.attributes().size()
        || new HashSet<>(child.attributes()).equals(new HashSet<>(parent.attributes())))return false;
    var used=new boolean[child.attributes().size()];
    return match(0,child.attributes(),parent.attributes(),used,reasoner);
  }

  private boolean match(int index,List<String> child,List<String> parent,boolean[] used,ReasonerService reasoner) {
    if(index==parent.size())return true;
    var required=reasoner.resolveConcept(parent.get(index));
    if(required==null || required.is(SemanticType.NOTHING))return false;
    for(int i=0;i<child.size();i++)if(!used[i]) {
      var candidate=reasoner.resolveConcept(child.get(i));
      // Public authoritative subsumption delegates authority terms before consulting OWL.
      if(candidate!=null && !candidate.is(SemanticType.NOTHING) && reasoner.is(candidate,required)) {
        used[i]=true;
        if(match(index+1,child,parent,used,reasoner))return true;
        used[i]=false;
      }
    }
    return false;
  }

  /** Prove outside the OWL monitor; publish a checked batch under the same accepted actor. */
  public void refresh(ReasonerService reasoner) {
    if(!owl.hasReasoner())return; // Declaration staging must not query partial knowledge.
    final long capturedEpoch;final List<Profile> snapshot;final Set<OWLSubClassOfAxiom> existing;
    final Set<String> capturedPending;final List<Profile> changed;
    synchronized(this){
      if(pending.isEmpty())return;
      capturedEpoch=epoch;snapshot=List.copyOf(profiles.values());existing=Set.copyOf(owned);
      capturedPending=Set.copyOf(pending);changed=pending.stream().map(profiles::get).toList();
    }
    long generation=reasoner.knowledgeRevision();
    var proposed=new LinkedHashSet<OWLSubClassOfAxiom>();
    var f=owl.manager.getOWLDataFactory();
    var annotation=f.getOWLAnnotation(f.getOWLAnnotationProperty(PROVENANCE),f.getOWLLiteral("derived:positive-attribute-refinement;generation="+generation));
    // Compare only newly completed/re-proofed profiles with peers of the same unchanged shape.
    // Repeated cached declarations do no hierarchy work; staging/authority refresh visits each pair once.
    var compared=new HashSet<List<OWLClass>>();
    for(var updated:changed)for(var peer:snapshot)for(boolean forward:List.of(true,false)) {
      var child=forward?updated:peer;var parent=forward?peer:updated;
      if(!compared.add(List.of(child.type(),parent.type())))continue;
      var axiom=f.getOWLSubClassOfAxiom(child.type(),parent.type(),Set.of(annotation));
      if(!existing.contains(axiom) && refines(child,parent,reasoner))proposed.add(axiom);
    }
    if(proposed.isEmpty()){
      synchronized(this){if(epoch==capturedEpoch && generation==reasoner.knowledgeRevision())pending.removeAll(capturedPending);}
      return;
    }
    // OWL lifecycle reset also owns this monitor first. Never invert that lock order.
    synchronized(owl) {
      synchronized(this) {
        if(epoch!=capturedEpoch || generation!=reasoner.knowledgeRevision() || !owl.hasReasoner())return;
        var ontology=owl.requireOntology(OWL.INTERNAL_ONTOLOGY_ID);
        ontology.setInternal(true);
        for(var axiom:proposed)if(!owl.isSatisfiable(axiom.getSubClass()) || !owl.isSatisfiable(axiom.getSuperClass()))
          throw new KlabValidationException("Unsatisfiable attribute-refinement endpoint");
        boolean published=false;
        // Track ownership before the first mutation, including partially failing insert/import.
        owned.addAll(proposed);
        try {
          owl.manager.addAxioms(ontology.getOWLOntology(),proposed);
          owl.registerWithReasoner(ontology);owl.flushReasoner();
          boolean valid=owl.isConsistent();
          for(var axiom:proposed)valid &= owl.isSatisfiable(axiom.getSubClass()) && owl.isSatisfiable(axiom.getSuperClass());
          if(!valid)throw new KlabValidationException("Attribute refinement contradicts existing semantic constraints");
          pending.removeAll(capturedPending);epoch++;published=true;
        } finally {
          try {
            if(!published){
              try {
                owl.manager.removeAxioms(ontology.getOWLOntology(),proposed);
                owned.removeAll(proposed);owl.flushReasoner();
              } catch(RuntimeException | Error failure){owl.quarantineDerivedClosure();throw failure;}
            }
          } finally { reasoner.derivedRefinementsChanged(); }
        }
      }
    }
  }
}
