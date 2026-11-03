package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import java.lang.reflect.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.runtime.ObserverResolution;
import org.integratedmodelling.klab.runtime.ObserverSemanticUse;
import org.integratedmodelling.klab.runtime.consequence.*;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Normal observer compatibility over immutable native input beans. Complete cohort read guards
 * remain intact: filtering is a projection, never a smaller absence or phantom guard. */
final class ConfigurationObserverFilter {
 private ConfigurationObserverFilter() {}
 static final class Unavailable extends RuntimeException {Unavailable(String reason){super(reason);}}
 record Result(ConsequencePatternMatcher.Snapshot snapshot,Set<String> semanticTerms,SemanticDependencyRequest request,SemanticDependencyWitness witness) {
  Result {semanticTerms=Set.copyOf(semanticTerms);}
 }
 static Result filter(ConfigurationNativeInputs.Capture nativeInputs,ConsequencePatternMatcher.Snapshot snapshot,
     List<ConsequenceRuleResponse.Entry> recipes,ServiceContextScope execution) {
  if(!snapshot.contextId().equals(execution.getId()))throw new Unavailable("Observer input context differs from execution");
  var reasoner=execution.getService(Reasoner.class);if(reasoner==null)throw new Unavailable("Retained observer comparison service unavailable");
  var selected=snapshot.observerId()==0?null:nativeInputs.observations().get(snapshot.observerId());
  if(snapshot.observerId()>0&&(selected==null||selected.isEmpty()||selected.getObservable()==null
      ||!selected.getObservable().is(SemanticType.AGENT)||selected.getObservable().isAbstract()
      ||selected.getObservable().getSemantics().isCollective()))throw new Unavailable("Selected observer is not a captured accessible Agent");
  var scope=capturedScope(execution,selected,reasoner,snapshot.epoch());var terms=new TreeSet<String>(nativeInputs.domainTerms());
  if(selected!=null)terms.add(selected.getObservable().asConcept().getUrn());
  var expected=new LinkedHashMap<String,org.integratedmodelling.klab.api.knowledge.Observable>();
  for(var recipe:recipes)for(var leaf:ConsequencePatterns.positiveLeaves(recipe.rule().target())) {
   var observable=reasoner.resolveObservable(leaf.getUrn());
   if(observable==null||observable.getSemantics()==null)throw new Unavailable("Authored ingredient observer meaning unavailable");
   expected.putIfAbsent(leaf.getUrn(),observable);terms.add(observable.asConcept().getUrn());
   if(observable.getObserverSemantics()!=null)terms.add(observable.getObserverSemantics().getUrn());
  }
  var kept=new ArrayList<ConsequencePatternMatcher.Fact>();
  try(var trace=ObserverSemanticUse.capture(scope)) {
   for(var fact:snapshot.facts()) {
    var observation=nativeInputs.observations().get(fact.id());var actual=observation==null?null:observation.getObservable();
    if(actual==null||actual.getSemantics()==null)throw new Unavailable("Captured candidate observable unavailable");
    terms.add(actual.asConcept().getUrn());if(actual.getObserverSemantics()!=null)terms.add(actual.getObserverSemantics().getUrn());
    boolean matches=false,compatible=false;
    for(var request:expected.values()) {
     if(!reasoner.is(actual.asConcept(),request.asConcept()))continue;
     matches=true;
     if(actual.getObserverSemantics()!=null||request.getObserverSemantics()!=null)requireCapabilities(reasoner,scope,snapshot.epoch());
     // Preserve observer semantics on BOTH Observables; asConcept alone discards this contract.
     if(ObserverResolution.preference(actual,observerRequest(actual,request),scope)>=0)compatible=true;
    }
    if(!matches||compatible)kept.add(fact);
   }
   var use=ObserverSemanticUse.requirements(scope);
   if(!use.tracked()||!use.valid())throw new Unavailable("Captured observer comparison evidence unavailable");
   terms.addAll(use.terms());
  }catch(Unavailable failure){throw failure;}catch(RuntimeException missing){throw new Unavailable("Observer compatibility unavailable: "+missing.getMessage());}
  var epoch=snapshot.epoch();var request=new SemanticDependencyRequest(epoch.serviceId(),epoch.knowledgeRevision(),epoch.semanticFingerprint(),List.copyOf(terms));
  var witness=reasoner.semanticDependencies(request,scope);requireWitness(request,witness);
  return new Result(new ConsequencePatternMatcher.Snapshot(snapshot.contextId(),snapshot.observerId(),snapshot.epoch(),kept,
      snapshot.contexts(),snapshot.complete(),snapshot.reads(),snapshot.catalogue()),terms,request,witness);
 }
 static org.integratedmodelling.klab.api.knowledge.Observable observerRequest(
     org.integratedmodelling.klab.api.knowledge.Observable candidate,org.integratedmodelling.klab.api.knowledge.Observable authored) {
  if(!(candidate instanceof org.integratedmodelling.common.knowledge.ObservableImpl implementation))throw new Unavailable("Captured observable representation unavailable");
  var copy=new org.integratedmodelling.common.knowledge.ObservableImpl(implementation);
  var base=candidate.asConcept().getUrn();var full=candidate.getUrn();
  if(base==null||full==null||!full.startsWith(base)||full.length()>base.length()&&full.charAt(base.length())!=' ')throw new Unavailable("Captured observable identity is noncanonical");
  String tail=full.substring(base.length());var provided=candidate.getObserverSemantics();
  if(provided!=null&&tail.startsWith(" observed as "+provided.getUrn())
      &&(tail.length()==(" observed as "+provided.getUrn()).length()||tail.charAt((" observed as "+provided.getUrn()).length())==' '))tail=tail.substring((" observed as "+provided.getUrn()).length());
  else if(tail.contains(" observed as "))throw new Unavailable("Captured observer qualifier differs from its observable identity");
  // The abstract authored pattern was checked by is(actual,pattern) separately. Observer matching
  // is a concrete candidate request, retaining its representation/inherency and authored observer.
  var expected=authored.getObserverSemantics();copy.setObserverSemantics(expected);
  copy.setUrn(base+(expected==null?"":" observed as "+expected.getUrn())+tail);return copy;
 }
 static void requireWitness(SemanticDependencyRequest request,SemanticDependencyWitness witness) {
  if(witness==null||!witness.completeAt(System.currentTimeMillis())||!request.expectedServiceId().equals(witness.serviceId())
      ||request.expectedKnowledgeRevision()!=witness.knowledgeRevision()||!request.expectedBaseFingerprint().equals(witness.baseFingerprint())||!request.inputHash().equals(witness.inputHash()))
    throw new Unavailable("Observer comparison semantic proof unavailable");
 }
 static void requireUnion(SemanticDependencyWitness comparison,SemanticDependencyWitness union) {
  for(var use:comparison.authorities())if(union.authorities().stream().noneMatch(current->current.authority().equals(use.authority())&&current.kind()==use.kind()&&current.proof().equals(use.proof())))
    throw new Unavailable("Observer semantic authority changed before full input admission");
 }
 private static void requireCapabilities(Reasoner reasoner,ContextScope scope,ConsequenceRuleResponse.Epoch epoch) {
  validateCapabilities(reasoner.capabilities(scope),epoch);
 }
 private static void validateCapabilities(Reasoner.Capabilities capabilities,ConsequenceRuleResponse.Epoch epoch) {
  if(capabilities==null||!epoch.serviceId().equals(capabilities.getServiceId())||epoch.knowledgeRevision()!=capabilities.getKnowledgeRevision()
      ||!epoch.semanticFingerprint().equals(capabilities.getSemanticContentFingerprint())||!epoch.semanticSessionId().equals(capabilities.getSemanticSessionId()))
    throw new Unavailable("Observer compatibility does not have the retained semantic epoch");
 }
 /** No refresh can substitute a newer Agent while its older native bean is being certified. All
  * identity, context, cycle and service methods continue through the actual execution scope. */
 private static ContextScope capturedScope(ServiceContextScope execution,Observation selected,Reasoner retained,ConsequenceRuleResponse.Epoch epoch) {
  var checked=(Reasoner)Proxy.newProxyInstance(Reasoner.class.getClassLoader(),new Class<?>[]{Reasoner.class},(proxy,method,args)->{
   if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "equals"->proxy==args[0];case "hashCode"->System.identityHashCode(proxy);case "toString"->"Captured observer comparator";default->throw new IllegalStateException();};
   final Object result;try{result=method.invoke(retained,args);}catch(InvocationTargetException failure){throw failure.getCause();}
   if(method.getName().equals("capabilities"))validateCapabilities((Reasoner.Capabilities)result,epoch);
   if(method.getName().equals("semanticDistance")&&method.getParameterCount()==4&&result instanceof Number distance&&distance.intValue()<=-10000)
     throw new Unavailable("Observer semantic distance is unavailable or request is ineligible");
   return result;
  });
  return (ContextScope)Proxy.newProxyInstance(ContextScope.class.getClassLoader(),new Class<?>[]{ContextScope.class},(proxy,method,args)->{
   if(method.getDeclaringClass()==Object.class)return switch(method.getName()) {case "equals"->proxy==args[0];case "hashCode"->System.identityHashCode(proxy);case "toString"->"Captured configuration observer scope "+execution.getId();default->throw new IllegalStateException();};
   if(method.getName().equals("getService")&&args!=null&&args.length==1&&args[0]==Reasoner.class)return checked;
   if(method.getName().equals("getObserver")&&method.getParameterCount()==0)return selected;
   if(method.getName().equals("getObserverId")&&method.getParameterCount()==0)return selected==null?null:selected.getId();
   try{return method.invoke(execution,args);}catch(InvocationTargetException failure){throw failure.getCause();}
  });
 }
}
