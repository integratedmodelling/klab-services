package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.runtime.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Internal continuation reached exclusively from ordinary submission, before eager compilation. */
final class SelectedModelClassifierContinuation {
  private final RuntimeService runtime;
  private final List<Runnable> validationChecks;
  private final Set<String> active=new HashSet<>();
  private SelectedModelClassifierContinuation(RuntimeService runtime,List<Runnable> validationChecks){this.runtime=runtime;this.validationChecks=validationChecks;}

  static CompletableFuture<Dataflow> resume(RuntimeService runtime,Dataflow dataflow,ServiceContextScope scope,List<Runnable> validationChecks) {
    if(!hasDeferred(dataflow)) {
      if(dataflow.getResolutionOutcome()==Dataflow.ResolutionOutcome.DEFERRED)
        return CompletableFuture.failedFuture(new IllegalStateException("Deferred dataflow has no obligation"));
      return CompletableFuture.completedFuture(dataflow);
    }
    if(dataflow.getResolutionOutcome()!=Dataflow.ResolutionOutcome.DEFERRED)
      return CompletableFuture.failedFuture(new IllegalStateException("Unannounced classifier obligation"));
    return new SelectedModelClassifierContinuation(runtime,validationChecks).resume(dataflow,scope);
  }
  private static boolean hasDeferred(Dataflow dataflow){return dataflow.getComputation().stream().anyMatch(SelectedModelClassifierContinuation::hasDeferred);}
  private static boolean hasDeferred(Actuator plan){return !plan.getClassifierDependencies().isEmpty() || plan.getChildren().stream().anyMatch(SelectedModelClassifierContinuation::hasDeferred);}
  private static DigitalTwinImpl.TransactionImpl transaction(ServiceContextScope scope) {
    if(scope.isInterrupted() || Thread.currentThread().isInterrupted())throw new CancellationException("Selected model continuation cancelled");
    if(!(scope.getCurrentTransaction() instanceof DigitalTwinImpl.TransactionImpl tx))throw new IllegalStateException("Live native attempt required");
    if(tx.isFailed())throw new IllegalStateException("Selected model attempt already failed");return tx;
  }
  private CompletableFuture<Dataflow> resume(Dataflow incoming,ServiceContextScope scope) {
    try {
      transaction(scope);
      var mapper=JacksonConfiguration.newObjectMapper();
      var dataflow=mapper.readValue(mapper.writeValueAsString(incoming),Dataflow.class);
      CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);
      for(var actuator:dataflow.getComputation())chain=chain.thenCompose(ignored->expand(actuator,scope));
      return chain.thenApply(ignored->{transaction(scope);((DataflowImpl)dataflow).setResolutionOutcome(Dataflow.ResolutionOutcome.RESOLVED);return dataflow;});
    } catch(Exception failure){return CompletableFuture.failedFuture(failure);}
  }
  private CompletableFuture<Void> expand(Actuator actuator,ServiceContextScope scope) {
    CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);
    for(var child:List.copyOf(actuator.getChildren()))chain=chain.thenCompose(ignored->expand(child,scope));
    var obligations=List.copyOf(actuator.getClassifierDependencies());
    if(obligations.size()>1)return CompletableFuture.failedFuture(new UnsupportedOperationException("Only one classifier-expanded dependency per selected model is supported in this singleton increment"));
    for(var obligation:obligations)chain=chain.thenCompose(ignored->expand(actuator,obligation,scope));
    return chain.thenRun(()->actuator.getClassifierDependencies().clear());
  }
  private CompletableFuture<Void> expand(Actuator parent,ClassifierDependencyPlan plan,ServiceContextScope scope) {
    final String key=plan.modelUrn()+"/"+plan.dependencyIndex()+"/"+plan.contextId()+"/"+plan.support();
    synchronized(active){if(!active.add(key))return CompletableFuture.failedFuture(new IllegalStateException("Recursive classifier dependency cycle: "+key));}
    try {
      var tx=transaction(scope);
      if(parent.getExecutionRole()!=Actuator.ExecutionRole.INITIALIZATION)
        throw new UnsupportedOperationException("Classifier dependency continuation supports INIT only");
      var context=scope.getContextObservation();
      if(context==null || context.getId()!=plan.contextId() || !Objects.equals(context.getObservable().getUrn(),plan.contextObservable()))
        throw new IllegalStateException("Selected model native context identity or meaning changed");
      var reasoner=scope.getService(Reasoner.class);
      var caps=reasoner.capabilities(scope);
      if(!(caps instanceof Reasoner.Capabilities semantic) || !Objects.equals(semantic.getServiceId(),plan.reasonerId())
          || semantic.getKnowledgeRevision()!=plan.knowledgeRevision())throw new IllegalStateException("Selected model semantic commitment changed");
      validationChecks.add(()->{
        var current=reasoner.capabilities(scope);
        if(!(current instanceof Reasoner.Capabilities accepted) || !Objects.equals(accepted.getServiceId(),plan.reasonerId())
            || accepted.getKnowledgeRevision()!=plan.knowledgeRevision())throw new IllegalStateException("Selected model semantic commitment changed before publication");
        var saved=scope.getService(ResourcesService.class).retrieve(plan.namespace(),KimNamespace.class,scope);
        if(saved==null || saved.getLastUpdateTimestamp()!=plan.sourceVersion() || !Objects.equals(Utils.Json.asString(saved),plan.sourceFingerprint()))
          throw new IllegalStateException("Selected model source changed before publication");
      });
      if(!(plan.source() instanceof KimObservableImpl implementation))throw new IllegalArgumentException("Missing frozen source syntax");
      var source=new KimObservableImpl(implementation);
      var sourceSnapshot=Utils.Json.asString(source);
      var resources=scope.getService(ResourcesService.class);
      if(plan.namespace()!=null && !plan.namespace().isBlank()) {
        var saved=resources.retrieve(plan.namespace(),KimNamespace.class,scope);
        if(saved==null || saved.getLastUpdateTimestamp()!=plan.sourceVersion() || !Objects.equals(Utils.Json.asString(saved),plan.sourceFingerprint()))
          throw new IllegalStateException("Selected model source changed");
        tx.beforeCommit(()->{if(saved.getLastUpdateTimestamp()!=plan.sourceVersion() || !Objects.equals(Utils.Json.asString(saved),plan.sourceFingerprint()))throw new IllegalStateException("Selected model source changed before commit");});
      }
      tx.beforeCommit(()->{if(!sourceSnapshot.equals(Utils.Json.asString(source)))throw new IllegalStateException("Frozen dependency source changed");});
      var prepared=DependencyExpansion.prepare(source,resources::declareConcept);
      if(prepared.requests().isEmpty())throw new IllegalArgumentException("Classifier obligation has no abstract predicate occurrence");
      boolean directDomain=prepared.requests().stream().allMatch(request->request.identity().path().size()==3);
      if(!directDomain && prepared.requests().size()>1)throw new UnsupportedOperationException("Automatic multi-occurrence planning currently requires direct quality attributes");
      if(prepared.requests().stream().anyMatch(request->request.identity().path().size()==3)
          && !reasoner.declareObservable(source).is(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY))
        throw new IllegalArgumentException("Direct classifier family requires a native quality dependency");
      var supplied=new LinkedHashMap<DependencyExpansion.RequestIdentity,ClassifierDependencyContinuation.Prerequisite>();
      var byPath=new HashMap<List<String>,Actuator>();
      for(var occurrence:plan.occurrences())if(byPath.put(occurrence.path(),occurrence.classifier())!=null)throw new IllegalArgumentException("Duplicate classifier occurrence");
      if(byPath.size()!=prepared.requests().size())throw new IllegalArgumentException("Incomplete classifier occurrence plan");
      for(var occurrence:plan.occurrences())if(hasDeferred(occurrence.classifier()))
        throw new UnsupportedOperationException("Nested classifier producer obligations are not supported by this increment");
      var support=Geometry.create(plan.support());
      var producers=new LinkedHashMap<com.fasterxml.jackson.databind.JsonNode,org.integratedmodelling.klab.api.knowledge.observation.Observation>();
      var producerKeys=new LinkedHashMap<DependencyExpansion.RequestIdentity,com.fasterxml.jackson.databind.JsonNode>();
      var shareable=new LinkedHashMap<DependencyExpansion.RequestIdentity,Boolean>();
      for(var request:prepared.requests()) {
        var operation=byPath.get(request.identity().path());
        if(operation==null || operation.getChildren().size()!=1)throw new UnsupportedOperationException("Exactly one native member producer per occurrence required");
        var producerPlan=operation.getChildren().getFirst();
        if(producerPlan.getObservation()!=null && producerPlan.getObservation().getId()<-1 && tx.assets().stream().anyMatch(asset->asset.getId()==producerPlan.getObservation().getId()))
          throw new IllegalStateException("Provisional producer output is already owned by this attempt");
        producerKeys.put(request.identity(),producerIdentity(producerPlan));
        shareable.put(request.identity(),isUnownedLeafProducerQuery(producerPlan));
      }
      if(prepared.requests().size()>1 && (shareable.values().stream().anyMatch(eligible->!eligible)
          || new HashSet<>(producerKeys.values()).size()!=1))
        throw new UnsupportedOperationException("Automatic predicate product requires one identical unowned leaf member producer");
      for(var request:prepared.requests()) {
        transaction(scope);
        var operation=byPath.get(request.identity().path());
        if(operation==null || operation.getChildren().size()!=1)throw new UnsupportedOperationException("Exactly one native member producer per occurrence required");
        var producerPlan=operation.getChildren().getFirst();
        // Share only an identical frozen executable plan within this dependency/context/attempt.
        // Occurrence identities and classification/substitution outcomes remain independent.
        var producerKey=producerKeys.get(request.identity());
        var producer=shareable.get(request.identity())?producers.get(producerKey):null;
        if(producer==null) { producer=runtime.executeClassifierProducer(producerPlan,support,scope);if(shareable.get(request.identity()))producers.put(producerKey,producer); }
        var reference=new ActuatorImpl();reference.setActuatorType(Actuator.Type.REFERENCE);
        reference.setName(operation.getChildren().getFirst().getName());reference.setId(producer.getId());reference.setObservation(org.integratedmodelling.klab.api.knowledge.observation.Observation.forTransport(producer));reference.setCoverage(support);
        operation.getChildren().clear();operation.getChildren().add(reference);
        supplied.put(request.identity(),new ClassifierDependencyContinuation.Prerequisite(operation,producer));
      }
      var work=ClassifierDependencyContinuation.resolve(runtime,prepared,supplied,support,Scheduler.Event.initialization(),scope,prepared.requests().stream().allMatch(request->request.identity().path().size()==3)
          ? ClassifierDependencyContinuation.BindingMode.DIRECT_QUALITY_DOMAIN : ClassifierDependencyContinuation.BindingMode.CURRENT_MEMBER).toCompletableFuture()
          .thenCompose(outcome->{
            if(outcome.status()!=DependencyExpansion.Status.SUCCESS)throw new CompletionException(new IllegalStateException("Classifier dependency "+outcome.status()+": "+outcome.diagnostic()));
            transaction(scope);
            boolean familyMode=prepared.requests().stream().anyMatch(request->request.identity().path().size()==3)
                || outcome.dependencies().size()>1;
            if(familyMode)return attachFamily(parent,plan,prepared,outcome,context,scope);
            // Successful empty expansion and optional generated NO_MODEL omit the input exactly
            // like ordinary optional resolution. No value or zero is fabricated for the consumer.
            if(outcome.dependencies().isEmpty())return CompletableFuture.<Void>completedFuture(null);
            var dependency=outcome.dependencies().getFirst();
            if(dependency.dataflow().getResolutionOutcome()==Dataflow.ResolutionOutcome.NO_MODEL) {
              if(!dependency.observable().isOptional())throw new IllegalStateException("Required concrete dependency has no model");
              return CompletableFuture.<Void>completedFuture(null);
            }
            if(dependency.dataflow().isEmpty())throw new IllegalStateException("Concrete dependency failed");
            var nativeScope=(ServiceContextScope)scope.within(tx.readObservation(context));
            var completed=hasDeferred(dependency.dataflow())?resume(dependency.dataflow(),nativeScope):CompletableFuture.completedFuture(dependency.dataflow());
            return completed.thenAccept(concrete->{
              transaction(scope);
              if(concrete.getComputation().size()!=1)throw new UnsupportedOperationException("Singleton dependency requires one executable root");
              var child=concrete.getComputation().getFirst();
              if(plan.localName()!=null)((ActuatorImpl)child).setName(plan.localName());
              if(Objects.equals(child.getName(),Dataflow.SELF_ID) || parent.getChildren().stream().anyMatch(existing->Objects.equals(existing.getName(),child.getName())))
                throw new IllegalArgumentException("Duplicate or reserved concrete dependency alias: "+child.getName());
              parent.getChildren().add(child);
            });
          });
      return work.whenComplete((ignored,failure)->{synchronized(active){active.remove(key);}if(failure!=null)tx.fail(failure);});
    } catch(Throwable failure) {
      synchronized(active){active.remove(key);}
      if(scope.getCurrentTransaction()!=null)scope.getCurrentTransaction().fail(failure);
      return CompletableFuture.failedFuture(failure);
    }
  }
  /** Plan-instance IDs are not scientific producer inputs. Retain every authored field and durable reference. */
  static com.fasterxml.jackson.databind.JsonNode producerIdentity(Actuator plan) {
    var tree=JacksonConfiguration.newObjectMapper().valueToTree(plan);normalizeProducerNode(tree);
    if(isUnownedLeafProducerQuery(plan)) {
      ((com.fasterxml.jackson.databind.node.ObjectNode)tree).put("id",-1);
      ((com.fasterxml.jackson.databind.node.ObjectNode)tree.get("observation")).put("id",-1);
    }
    return tree;
  }
  private static void normalizeObservableTypes(com.fasterxml.jackson.databind.JsonNode observable) {
    if(observable==null)return;
    var concept=observable.get("semantics");
    // Only the direct, typed semantic position is a Set. Never scan authored payload maps.
    if(concept instanceof com.fasterxml.jackson.databind.node.ObjectNode object
        && object.get("type") instanceof com.fasterxml.jackson.databind.node.ArrayNode types) {
      var sorted=new java.util.TreeSet<String>();types.forEach(type->sorted.add(type.asText()));types.removeAll();sorted.forEach(types::add);
    }
  }
  private static boolean knownProducingModels(Actuator plan) {
    return plan.getData().get(Actuator.PRODUCING_MODELS_KEY) instanceof List<?> entries && !entries.isEmpty()
        && entries.stream().allMatch(entry->entry instanceof String text && !text.isBlank());
  }
  private static boolean isUnownedLeafProducerQuery(Actuator plan) {
    var observation=plan.getObservation();
    return knownProducingModels(plan) && plan.getActuatorType()==Actuator.Type.OBSERVE && plan.getExecutionRole()==Actuator.ExecutionRole.INITIALIZATION
        && plan.getContextualization()==org.integratedmodelling.klab.api.knowledge.Contextualization.INSTANTIATION
        && plan.getChildren().isEmpty() && plan.getTargetBindings().isEmpty() && observation!=null
        && plan.getId()<-1 && plan.getId()==observation.getId() && observation.getUrn()==null
        && observation.getParentId()==org.integratedmodelling.klab.api.knowledge.observation.Observation.UNASSIGNED_ID && observation.getParentTransientId()==0 && plan.getParentId()==org.integratedmodelling.klab.api.knowledge.observation.Observation.UNASSIGNED_ID && plan.getParentTransientId()==0 && observation.getParticipants().isEmpty()
        && observation.getObservable()!=null && observation.getObservable().getSemantics().isCollective();
  }
  private static void normalizeProducerNode(com.fasterxml.jackson.databind.JsonNode node) {
    if(!(node instanceof com.fasterxml.jackson.databind.node.ObjectNode object))throw new IllegalArgumentException("Missing producer node");
    object.remove("transientId");
    normalizeUnassignedObservation(object.get("observation"));
    normalizeObservableTypes(object.get("operationObservable"));normalizeObservableTypes(object.get("modelDependency"));
    for(var binding:object.path("targetBindings"))normalizeUnassignedObservation(binding.get("target"));
    for(var child:object.path("children"))normalizeProducerNode(child);
  }
  private static void normalizeUnassignedObservation(com.fasterxml.jackson.databind.JsonNode node) {
    if(node instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
      if(object.path("id").asLong(-1)<=0)object.remove("transientId");
      normalizeObservableTypes(object.get("observable"));
    }
  }
  private CompletableFuture<Void> attachFamily(Actuator parent,ClassifierDependencyPlan plan,
      DependencyExpansion.Prepared prepared,ClassifierDependencyContinuation.Outcome outcome,
      org.integratedmodelling.klab.api.knowledge.observation.Observation context,ServiceContextScope scope) {
    var tx=transaction(scope);org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Transaction root=tx;
    while(root.getParent()!=null)root=root.getParent();
    final String attempt=root.getId(),familyId=prepared.attemptId().toString();
    var branches=new ArrayList<DependencyFamily.Branch>();
    CompletableFuture<Void> work=CompletableFuture.completedFuture(null);
    for(int index=0;index<outcome.dependencies().size();index++) {
      var dependency=outcome.dependencies().get(index);final int branchIndex=index;
      final String bindingId=familyId+"/"+index;
      work=work.thenCompose(ignored->{
        transaction(scope);
        if(dependency.dataflow().getResolutionOutcome()==Dataflow.ResolutionOutcome.NO_MODEL) {
          if(!dependency.observable().isOptional())throw new IllegalStateException("Required concrete dependency has no model");
          branches.add(new DependencyFamily.Branch(bindingId,dependency.observable().getUrn(),org.integratedmodelling.klab.api.data.StorageScan.semantics(dependency.observable()),true,DependencyFamily.Outcome.OPTIONAL_NO_MODEL));
          return CompletableFuture.completedFuture(null);
        }
        if(dependency.dataflow().isEmpty())throw new IllegalStateException("Concrete family dependency failed");
        var nativeScope=(ServiceContextScope)scope.within(tx.readObservation(context));
        var completed=hasDeferred(dependency.dataflow())?resume(dependency.dataflow(),nativeScope):CompletableFuture.completedFuture(dependency.dataflow());
        return completed.thenAccept(concrete->{
          transaction(scope);
          if(concrete.getComputation().size()!=1)throw new UnsupportedOperationException("A concrete branch requires one executable root");
          var child=(ActuatorImpl)concrete.getComputation().getFirst();
          var name=Objects.toString(plan.localName(),"dependency_"+plan.dependencyIndex())+"__branch_"+branchIndex;
          if(name.equals(Dataflow.SELF_ID) || parent.getChildren().stream().anyMatch(existing->name.equals(existing.getName())))
            throw new IllegalArgumentException("Duplicate concrete branch binding: "+name);
          child.setName(name);child.setDependencyFamilyBinding(new DependencyFamily.Binding(familyId,bindingId,attempt,dependency.observable().getUrn()));
          parent.getChildren().add(child);
          branches.add(new DependencyFamily.Branch(bindingId,dependency.observable().getUrn(),org.integratedmodelling.klab.api.data.StorageScan.semantics(dependency.observable()),dependency.observable().isOptional(),DependencyFamily.Outcome.RESOLVED));
        });
      });
    }
    return work.thenRun(()->{
      transaction(scope);
      ((ActuatorImpl)parent).getDependencyFamilies().add(new DependencyFamily(familyId,attempt,plan.modelUrn(),plan.dependencyIndex(),plan.localName(),
          plan.source().getUrn(),prepared.requests().stream().map(request->request.identity().path()).toList(),branches));
    });
  }

}
