package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** One live request's classifier-to-concrete-dependency planning hop. No parent fanout binding. */
public final class ClassifierDependencyContinuation {
  private ClassifierDependencyContinuation() {}
  public record Prerequisite(Actuator operation, Observation producer) {}
  public record ResolvedDependency(Observable observable, Observation observation, Dataflow dataflow) {}
  public record Outcome(DependencyExpansion.Status status, List<ResolvedDependency> dependencies, String diagnostic) {
    public Outcome { dependencies=List.copyOf(dependencies); }
  }
  private record Generation(String service, long revision) {}
  private static Generation generation(ServiceContextScope scope) {
    var caps=scope.getService(Reasoner.class).capabilities(scope);
    if(!(caps instanceof Reasoner.Capabilities semantic) || semantic.getServiceId()==null || semantic.getKnowledgeRevision()<0)
      throw new IllegalStateException("A known semantic commitment is required");
    return new Generation(semantic.getServiceId(),semantic.getKnowledgeRevision());
  }
  /** Qualified membership needs a distinct domain proof; this pilot admits only named leaves. */
  private static boolean namedLeaf(KimConcept c,boolean predicateSlots) {
    return c!=null && c.getName()!=null && c.getObservable()==null && c.getAbout().isEmpty()
        && c.getInherent()==null && c.getGoal()==null && c.getCausant()==null && c.getCaused()==null
        && c.getCompresent()==null && c.getComparisonConcept()==null && c.getAuthorityTerm()==null
        && c.getAuthority()==null && c.getSemanticModifier()==null && c.getRelationshipSource()==null
        && c.getRelationshipTarget()==null && !c.isNegated() && !c.isCollective()
        && c.getOperands().isEmpty() && c.getModifiers().isEmpty() && c.getCooccurrent()==null
        && c.getAdjacent()==null && c.getParent()==null && c.getValueOperators().isEmpty()
        && !c.isPattern() && c.getPatternVariables().isEmpty()
        && (predicateSlots || c.getTraits().isEmpty() && c.getRoles().isEmpty());
  }
  enum BindingMode { PLAIN_NATIVE_DOMAIN, CURRENT_MEMBER, DIRECT_QUALITY_DOMAIN }

  private static void requireOccurrence(DependencyExpansion.Request request, Prerequisite prerequisite, ServiceContextScope scope, BindingMode mode) {
    boolean bindCurrentMember=mode!=BindingMode.PLAIN_NATIVE_DOMAIN;
    var path=request.identity().path();
    // First capability: independent traits/roles on one explicitly named native inherent bearer.
    // Endpoint-, nested- and head-predicate attribution require their own typed binding proof.
    boolean directAttribute=mode==BindingMode.DIRECT_QUALITY_DOMAIN && path.size()==3 && path.get(0).equals("semantics")
        && path.get(1).equals("traits") && request.declaration().is(SemanticType.ATTRIBUTE)
        && namedLeaf(request.predicate(),false);
    boolean bearerAttribute=path.size()==4 && path.get(0).equals("semantics") && path.get(1).equals("inherent")
        && (path.get(2).equals("traits") || path.get(2).equals("roles"));
    if(!directAttribute && !bearerAttribute)
      throw new IllegalArgumentException("Unsupported occurrence binding path "+path);
    var bearer=request.originalObservable().getSemantics().getInherent();
    if(!namedLeaf(bearer,true))
      throw new IllegalArgumentException("An explicit named native bearer is required");
    var resources=scope.getService(ResourcesService.class);
    for(var predicate:java.util.stream.Stream.concat(bearer.getTraits().stream(),bearer.getRoles().stream()).toList()) {
      var declaration=namedLeaf(predicate,false)?resources.declareConcept(predicate.getName()):null;
      if(declaration==null || !declaration.is(SemanticType.ABSTRACT) || !declaration.is(SemanticType.PREDICATE))
        throw new IllegalArgumentException("Qualified or concrete bearer filters need an explicit member-domain proof");
    }
    var reasoner=scope.getService(Reasoner.class);
    var producerBase=reasoner.baseSubstantialType(prerequisite.producer().getObservable(),scope);
    if(producerBase!=null)producerBase=producerBase.singular();
    var context=scope.getContextObservation();
    var contextBase=context==null?null:reasoner.baseSubstantialType(context.getObservable(),scope);
    if(producerBase==null || contextBase==null || !bearer.getName().equals(producerBase.getUrn())
        || !bearer.getName().equals(contextBase.getUrn())
        || !bearer.getName().equals(prerequisite.producer().getObservable().getSemantics().singular().getUrn())
        || !bindCurrentMember && !bearer.getName().equals(context.getObservable().getSemantics().getUrn())
        || bindCurrentMember && !reasoner.is(context.getObservable().getSemantics(),producerBase))
      throw new IllegalArgumentException("Occurrence producer and native context do not match the named bearer");
    for(var child:prerequisite.operation().getChildren()) {
      if(child.getActuatorType()!=Actuator.Type.REFERENCE || child.getObservation()==null
          || child.getObservation().getId()!=prerequisite.producer().getId())
        throw new IllegalArgumentException("Unexecuted classifier prerequisites are unsupported by this entry point");
    }
    var original=prerequisite.operation().getModelDependency();
    var declaredSource=reasoner.declareObservable(request.originalObservable());
    if(mode==BindingMode.DIRECT_QUALITY_DOMAIN && (declaredSource==null || !declaredSource.is(SemanticType.QUALITY)))
      throw new IllegalArgumentException("A direct classifier family requires a native quality dependency");
    if(original==null || declaredSource==null || !Objects.equals(original.getUrn(),declaredSource.getUrn())
        || original.isOptional()!=request.originalObservable().isOptional()
        || declaredSource.isOptional()!=request.originalObservable().isOptional()
        || !org.integratedmodelling.klab.api.data.StorageScan.semantics(original).equals(
            org.integratedmodelling.klab.api.data.StorageScan.semantics(declaredSource)))
      throw new IllegalArgumentException("Classifier prerequisite does not preserve the original dependency contract");
    var nativeDirective=reasoner.inherent(prerequisite.operation().getOperationObservable());
    var nativeBase=nativeDirective==null?null:reasoner.baseSubstantialType(nativeDirective,scope);
    if(nativeBase!=null)nativeBase=nativeBase.singular();
    if(nativeBase==null || !bearer.getName().equals(nativeBase.getUrn()) || !bearer.getName().equals(nativeDirective.singular().getUrn()))
      throw new IllegalArgumentException("Classifier directive has another bearer");
  }
  private record Capture(UUID identity, Cohort cohort, Observation producer, KnowledgeGraph.ClassificationReadSet persisted,
      List<Observation> members, List<Observation> persistedMembers, List<String> local, List<String> projected) {}

  private static DigitalTwinImpl.TransactionImpl transaction(ServiceContextScope scope) {
    if(scope.isInterrupted()) throw new CancellationException("Classification continuation cancelled");
    if(!(scope.getCurrentTransaction() instanceof DigitalTwinImpl.TransactionImpl tx) || tx.isFailed())
      throw new IllegalStateException("Classification continuation requires a live parent attempt");
    return tx;
  }
  private static com.fasterxml.jackson.databind.JsonNode planSnapshot(
      com.fasterxml.jackson.databind.ObjectMapper mapper,Actuator plan) {
    var tree=mapper.valueToTree(plan);
    removeDerivedLabels(tree);
    return tree;
  }
  private static void removeDerivedLabels(com.fasterxml.jackson.databind.JsonNode node) {
    // displayLabel/getMetadata populate this presentation cache lazily. It is not source
    // semantics, occurrence identity, execution support, or a result/attempt certificate.
    if(node instanceof com.fasterxml.jackson.databind.node.ObjectNode object
        && object.path("@CLASS").asText().equals("org.integratedmodelling.klab.api.collections.impl.MetadataImpl")
        && object.get("delegate") instanceof com.fasterxml.jackson.databind.node.ObjectNode metadata)
      metadata.remove(Metadata.SUGGESTED_NAME);
    if(node.isContainerNode())node.elements().forEachRemaining(ClassifierDependencyContinuation::removeDerivedLabels);
  }
  private static String support(Geometry geometry) {
    return GeometryRepository.INSTANCE.sanitize(Objects.requireNonNull(geometry)).encode();
  }
  private static boolean producerSucceeded(DigitalTwinImpl.TransactionImpl tx, Observation producer, Geometry requested) {
    var contextualization=producer.getObservable().getContextualization();
    if(!support(producer.getGeometry()).equals(support(requested))) return false;
    if(contextualization!=Contextualization.INSTANTIATION) return false;
    return tx.incoming(producer).stream().anyMatch(link->
        link.type()==GraphModel.Relationship.forContextualization(contextualization)
        && link.source() instanceof Activity a && a.getType()==Activity.Type.forContextualization(contextualization)
        && a.getOutcome()==Activity.Outcome.SUCCESS
        && Objects.equals(a.getMetadata().get(Metadata.IM_EXECUTION_GEOMETRY),support(requested)));
  }

  private static Capture capture(RuntimeService runtime, Observation producer, Geometry requested, ServiceContextScope scope) throws Exception {
    var tx=transaction(scope);
    var suppliedProducer=producer;
    producer=tx.assets().stream().filter(Observation.class::isInstance).map(Observation.class::cast)
        .filter(candidate->suppliedProducer.getId()>0?candidate.getId()==suppliedProducer.getId():candidate.getTransientId()==suppliedProducer.getTransientId()).findFirst()
        .orElseThrow(()->new IllegalArgumentException("Producer is not owned by this live attempt"));
    if(!Objects.equals(producer.getObservable().getUrn(),suppliedProducer.getObservable().getUrn())
        || !support(producer.getGeometry()).equals(support(suppliedProducer.getGeometry())))
      throw new IllegalArgumentException("Producer identity/meaning/support mismatch");
    if(!producerSucceeded(tx,producer,requested)) {
      for(var link:tx.incoming(producer)) if(link.source() instanceof Activity a
          && link.type()==GraphModel.Relationship.forContextualization(producer.getObservable().getContextualization())
          && a.getOutcome()!=null && a.getOutcome()!=Activity.Outcome.SUCCESS)
        throw new IllegalStateException("Member producer failed");
      return null;
    }
    // The bounded entry certifies one actual live-attempt producer invocation with exact support.
    // Durable/replayed producer certificates are deliberately not inferred from cohort existence.
    var cohort=runtime.getCohortFor(producer.getObservable().getSemantics().singular(),scope,false);
    if(cohort==null || cohort.getId()<=0) throw new IllegalStateException("A persisted owning cohort is required");
    long localStamp=tx.classificationMutationStamp();
    var context=scope.getContextObservation();
    if(context==null || tx.incoming(producer).stream().noneMatch(link->link.type()==GraphModel.Relationship.HAS_CHILD && link.source().getId()==context.getId())
        || tx.outgoing(producer).stream().noneMatch(link->link.type()==GraphModel.Relationship.CONTRIBUTED_TO && link.target().getId()==cohort.getId()))
      throw new IllegalArgumentException("Producer lacks exact native context/cohort contribution witnesses");
    var before=tx.classificationOverlay(cohort,producer);
    KnowledgeGraph.ClassificationReadSet read;
    try(var nativeTx=scope.getDigitalTwin().getKnowledgeGraph().createTransaction(scope)) {
      read=nativeTx.captureClassificationMembers(cohort.getId());
    }
    if(!Objects.equals(read.cohortObservable(),cohort.getObservable().getUrn()))
      throw new IllegalStateException("Captured cohort meaning is unavailable or changed");
    var after=tx.classificationOverlay(cohort,producer);
    if(localStamp!=tx.classificationMutationStamp() || !before.state().equals(after.state()) || !producerSucceeded(tx,producer,requested))
      throw new IllegalStateException("Local input changed during classification capture");
    var reasoner=scope.getService(Reasoner.class);
    var members=new LinkedHashMap<String,Observation>();
    for(var item:read.members()) {
      if(item.geometry()==null) throw new IllegalStateException("Missing persisted member support");
      var member=scope.getDigitalTwin().getKnowledgeGraph().materializeClassificationMember(item,scope);
      if(member==null || member.getId()!=item.id() || !Objects.equals(member.getObservable().getUrn(),item.observable())
          || !support(member.getGeometry()).equals(support(Geometry.create(item.geometry()))))
        throw new IllegalStateException("Captured observation materialization does not match its read set");
      members.put("id:"+item.id(),member);
    }
    var persisted=List.copyOf(members.values());
    var completeOverlay=tx.classificationOverlay(cohort,producer,persisted);
    if(localStamp!=tx.classificationMutationStamp()) throw new IllegalStateException("Local classification input changed");
    for(var member:completeOverlay.members()) members.put(member.getId()>0?"id:"+member.getId():"transient:"+member.getTransientId(),member);
    var selected=new ArrayList<Observation>();
    for(var member:members.values()) {
      if(member.getObservable()==null || member.getObservable().is(SemanticType.NOTHING))
        throw new IllegalStateException("Unavailable captured member meaning");
      if(RuntimeService.hasMemberSupport(RuntimeService.intersection(member.getGeometry(),requested))) selected.add(member);
    }
    return new Capture(UUID.randomUUID(),cohort,producer,read,List.copyOf(selected),persisted,completeOverlay.rawState(),completeOverlay.state());
  }

  static CompletionStage<Outcome> resolve(RuntimeService runtime, DependencyExpansion.Prepared prepared,
      Map<DependencyExpansion.RequestIdentity,Prerequisite> supplied, Geometry requested, Scheduler.Event event,
      ServiceContextScope scope) {
    return resolve(runtime,prepared,supplied,requested,event,scope,false);
  }

  static CompletionStage<Outcome> resolve(RuntimeService runtime, DependencyExpansion.Prepared prepared,
      Map<DependencyExpansion.RequestIdentity,Prerequisite> supplied, Geometry requested, Scheduler.Event event,
      ServiceContextScope scope, boolean bindCurrentMember) {
    return resolve(runtime,prepared,supplied,requested,event,scope,bindCurrentMember?BindingMode.CURRENT_MEMBER:BindingMode.PLAIN_NATIVE_DOMAIN);
  }

  static CompletionStage<Outcome> resolve(RuntimeService runtime, DependencyExpansion.Prepared prepared,
      Map<DependencyExpansion.RequestIdentity,Prerequisite> supplied, Geometry requested, Scheduler.Event event,
      ServiceContextScope scope, BindingMode mode) {
    boolean bindCurrentMember=mode!=BindingMode.PLAIN_NATIVE_DOMAIN;
    var pending=new LinkedHashMap<DependencyExpansion.RequestIdentity,DependencyExpansion.Classification>();
    var captures=new ArrayList<Capture>();
    var sourceChecks=new ArrayList<Runnable>();
    DigitalTwinImpl.TransactionImpl parent;
    try {
      parent=transaction(scope);
      requested=Geometry.create(support(requested));
      final Geometry frozenSupport=requested;
      final String encodedSupport=support(frozenSupport);
      Runnable supportCheck=()->{if(!encodedSupport.equals(support(frozenSupport))) throw new IllegalStateException("Captured support changed");};
      sourceChecks.add(supportCheck);
      var commitment=generation(scope);
      Runnable semanticCheck=()->{if(!commitment.equals(generation(scope))) throw new IllegalStateException("Semantic commitment changed");};
      var requests=prepared.requests();var expected=new LinkedHashSet<DependencyExpansion.RequestIdentity>();
      for(var request:requests) expected.add(request.identity());
      if(!expected.equals(supplied.keySet())) throw new IllegalArgumentException("Prerequisites must match exact occurrence identities");
      var mapper=JacksonConfiguration.newObjectMapper();
      // Establish every producer's affirmative completion before invoking any classifier.
      var plans=new LinkedHashMap<DependencyExpansion.RequestIdentity,Actuator>();
      for(var request:requests) {
        var prerequisite=Objects.requireNonNull(supplied.get(request.identity()));
        requireOccurrence(request,prerequisite,scope,mode);
        var encoded=mapper.writeValueAsString(prerequisite.operation());
        var sourcePlan=planSnapshot(mapper,prerequisite.operation());
        sourceChecks.add(()->{
          if(!sourcePlan.equals(planSnapshot(mapper,prerequisite.operation())))
            throw new IllegalStateException("Classifier source plan changed");
        });
        plans.put(request.identity(),mapper.readValue(encoded,Actuator.class));
        var captured=capture(runtime,prerequisite.producer(),requested,scope);
        if(captured==null) return CompletableFuture.completedFuture(new Outcome(DependencyExpansion.Status.UNKNOWN,List.of(),"Producer completion is unresolved or unsupported"));
        if(mode==BindingMode.CURRENT_MEMBER && (captured.members().size()!=1
            || captured.members().getFirst().getId()!=scope.getContextObservation().getId()))
          throw new IllegalArgumentException("Singleton submission requires the actual current context as its only classified member");
        captures.add(captured);
      }
      for(var captured:captures) {
        if(captured.producer()!=captures.getFirst().producer() || captured.cohort().getId()!=captures.getFirst().cohort().getId()
            || !captured.projected().equals(captures.getFirst().projected()))
          throw new IllegalArgumentException("This capability requires one frozen native member domain");
        parent.guardClassificationReadSet(captured.persisted());
      }
      List<String> expectedProjection=captures.isEmpty()?List.of():captures.getFirst().projected();
      int index=0;
      for(var request:requests) {
        transaction(scope);semanticCheck.run();sourceChecks.forEach(Runnable::run);
        var capture=captures.get(index++);var plan=plans.get(request.identity());
        var activity=Activity.of(Activity.Type.CLASSIFICATION,scope.getActivity(),"Concrete dependency prerequisite");
        var execution=scope.executing(activity);
        var classifier=MemberClassifierExecutor.compile(plan,runtime.getComponentRegistry(),execution);
        if(!Objects.equals(request.declaration().getName(),classifier.requestedPredicateUrn()))
          throw new IllegalArgumentException("Classifier family does not match occurrence declaration");
        var detail=classifier.executeDetailed(Map.of(plan.getTargetBindings().getFirst().getSources().getFirst(),capture.members()),requested,event,execution);
        var family=request.declaration().getName();
        if(detail.members().stream().anyMatch(result->!Objects.equals(family,result.requestedPredicate())))
          throw new IllegalArgumentException("Classifier family does not match occurrence declaration");
        var effects=detail.pendingAttributions();
        transaction(scope);semanticCheck.run();sourceChecks.forEach(Runnable::run);
        expectedProjection=((DigitalTwinImpl.TransactionImpl)execution.getCurrentTransaction()).stageClassificationSnapshot(effects,execution,capture.cohort(),capture.producer(),capture.persistedMembers(),expectedProjection).state();
        runtime.characterizePending(effects,execution);classifier.verifyGeneration(execution);
        if(!parent.classificationOverlay(capture.cohort(),capture.producer(),capture.persistedMembers()).state().equals(expectedProjection))
          throw new IllegalStateException("Characterization changed the frozen member input");
        var envelope=new LinkedHashMap<String,Object>();envelope.put("version",1);envelope.put("rootAttempt",parent.getId());
        envelope.put("context",scope.getId());envelope.put("support",support(requested));envelope.put("event",event.toKey());
        envelope.put("reasonerId",commitment.service());envelope.put("knowledgeRevision",commitment.revision());
        envelope.put("capture",capture.identity().toString());
        envelope.put("sourceSnapshot",org.integratedmodelling.common.utils.Utils.Json.asString(request.originalObservable()));
        envelope.put("syntaxAttempt",prepared.attemptId().toString());envelope.put("occurrence",request.identity().path());
        envelope.put("memberResults",detail.members().stream().map(org.integratedmodelling.klab.api.services.runtime.objects.ClassificationMemberResult::metadata).toList());
        activity.getMetadata().put(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS,envelope);
        if(execution.commit()<0) throw new IllegalStateException("Classification child failed");
        var closure=new LinkedHashMap<String,KimConcept>();
        for(var member:detail.members()) for(var predicate:member.concretePredicates())
          closure.putIfAbsent(predicate,scope.getService(ResourcesService.class).declareConcept(predicate));
        pending.put(request.identity(),new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS,List.copyOf(closure.values())));
      }
      // Preserve original required/optional syntax when no expansion occurs; only generated branches
      // have the optionality specified by the existing expansion contract.
      semanticCheck.run();
      for(var captured:captures) try(var nativeTx=scope.getDigitalTwin().getKnowledgeGraph().createTransaction(scope)) {
        nativeTx.validateClassificationMembers(captured.persisted());
      }
      var syntax=prepared.apply(pending);var reasoner=scope.getService(Reasoner.class);
      var concrete=new ArrayList<Observable>();
      for(var branch:syntax) {
        var declared=reasoner.declareObservable(branch);
        var canonical=declared==null?null:reasoner.resolveConcept(declared.getSemantics().getUrn());
        if(canonical==null || canonical.isAbstract() || canonical.isGeneric() || canonical.is(SemanticType.NOTHING) || canonical.is(SemanticType.ANY)
            || !reasoner.satisfiable(canonical) || declared==null || declared.isAbstract() || !declared.getGenericComponents().isEmpty()
            || declared.is(SemanticType.NOTHING) || !reasoner.satisfiable(declared))
          throw new IllegalArgumentException("Expanded dependency is not executable");
        if(declared.isOptional()!=branch.isOptional()) throw new IllegalStateException("Declaration changed dependency optionality");
        var frozenDeclaration=mapper.writeValueAsString(declared);
        sourceChecks.add(()->{
          try {if(!frozenDeclaration.equals(mapper.writeValueAsString(declared))) throw new IllegalStateException("Concrete dependency meaning changed");}
          catch(java.io.IOException e){throw new IllegalStateException(e);}
        });
        concrete.add(declared);
      }
      for(var captured:captures) {
        var finalState=parent.classificationOverlay(captured.cohort(),captured.producer(),captured.persistedMembers());
        if(!producerSucceeded(parent,captured.producer(),requested)) throw new IllegalStateException("Member producer completion changed");
        if(!finalState.state().equals(expectedProjection)) throw new IllegalStateException("Projected member input changed");
        if(!finalState.rawState().equals(captured.local())) throw new IllegalStateException("Captured member or producer input changed");
        parent.guardClassificationOverlay(captured.cohort(),captured.producer(),captured.persistedMembers(),finalState.state());
      }
      // Source identity is checked again at root commit; no remote calls are made by this guard.
      parent.beforeCommit(()->sourceChecks.forEach(Runnable::run));
      var completion=new CompletableFuture<Outcome>();
      completion.whenComplete((outcome,failure)->{if(completion.isCancelled()) parent.fail(new CancellationException("Classification continuation cancelled"));});
      var resolutions=new ArrayList<CompletableFuture<ResolvedDependency>>();
      for(var dependency:concrete) {
        if(completion.isCancelled()) return completion;
        transaction(scope);semanticCheck.run();sourceChecks.forEach(Runnable::run);
        var dependencyScope=scope;
        if(bindCurrentMember) {
          var nativeContext=parent.readObservation(scope.getContextObservation());
          // Transaction read views expose defensive concept proxies. Canonicalize their exact
          // frozen meaning at this accepted revision before an authoritative subtype query.
          var nativeMeaning=reasoner.resolveConcept(nativeContext.getObservable().getSemantics().getUrn());
          var nativeBearer=reasoner.inherent(dependency);
          if(nativeMeaning==null || nativeMeaning.is(SemanticType.NOTHING) || nativeBearer==null || !reasoner.is(nativeMeaning,nativeBearer))
            throw new IllegalArgumentException("Concrete dependency has no proof in the original native context");
          dependencyScope=(ServiceContextScope)scope.within(nativeContext);
        }
        final ServiceContextScope nativeScope=dependencyScope;
        var observation=new ObservationImpl();observation.setObservable(dependency);observation.setGeometry(requested);
        if(scope.getContextObservation()!=null) observation.setParentId(scope.getContextObservation().getId());
        var registered=runtime.register(observation,nativeScope);
        if(registered.getId()==Observation.UNASSIGNED_ID || registered.getId()==Observation.QUERY_ID)
          throw new IllegalStateException("Concrete dependency has no unique runtime identity");
        resolutions.add(nativeScope.getService(Resolver.class).resolve(registered,nativeScope).thenApply(dataflow->{
          transaction(scope);semanticCheck.run();sourceChecks.forEach(Runnable::run);
          if(dataflow==null || dataflow.getResolutionOutcome()==Dataflow.ResolutionOutcome.FAILED
              || dataflow.getResolutionOutcome()==Dataflow.ResolutionOutcome.NO_MODEL && !dependency.isOptional())
            throw new IllegalStateException("Concrete dependency resolution failed");
          return new ResolvedDependency(dependency,registered,dataflow);
        }).whenComplete((value,failure)->{
          Throwable cause=failure;while(cause instanceof CompletionException && cause.getCause()!=null) cause=cause.getCause();
          if(cause instanceof CancellationException) completion.cancel(false);
        }));
      }
      var work=CompletableFuture.allOf(resolutions.toArray(CompletableFuture[]::new)).thenApply(ignored->{
        transaction(scope);semanticCheck.run();sourceChecks.forEach(Runnable::run);
        return new Outcome(DependencyExpansion.Status.SUCCESS,resolutions.stream().map(CompletableFuture::join).toList(),null);
      });
      work.whenComplete((outcome,failure)->{
        if(failure==null) completion.complete(outcome);
        else {
          Throwable cause=failure;while(cause instanceof CompletionException && cause.getCause()!=null) cause=cause.getCause();
          parent.fail(cause);
          if(cause instanceof CancellationException) completion.cancel(false);
          else completion.complete(new Outcome(DependencyExpansion.Status.FAILED,List.of(),cause.getMessage()));
        }
      });
      return completion;
    } catch(CancellationException cancelled) {
      if(scope.getCurrentTransaction()!=null) scope.getCurrentTransaction().fail(cancelled);
      var result=new CompletableFuture<Outcome>();result.cancel(false);return result;
    } catch(Exception failure) {
      if(scope.getCurrentTransaction()!=null) scope.getCurrentTransaction().fail(failure);
      return CompletableFuture.completedFuture(new Outcome(DependencyExpansion.Status.FAILED,List.of(),failure.getMessage()));
    }
  }
}
