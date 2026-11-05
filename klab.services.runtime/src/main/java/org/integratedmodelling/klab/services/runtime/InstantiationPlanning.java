package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.digitaltwin.ProcessPlan;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.InstantiationResolution;
import org.integratedmodelling.klab.api.knowledge.InstantiationResolution.Mode;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.RuntimeService.ContextualizationScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** One batch owns its shared selection; each invocation still owns its bound executable objects. */
final class InstantiationPlanning {
  static final String MODE = "klab.instantiation.resolution.mode";
  static final String ACKNOWLEDGMENT = "klab.instantiation.acknowledgment";
  interface Planner {
    Mode mode();
    CompletableFuture<Dataflow> plan(Observation child, ServiceContextScope scope);
  }
  private record Group(Object contribution, String constraints) {}
  private record Prepared(Observation child, ObservationImpl effective, Planner planner) {}

  static Map<Observation, Planner> prepare(ContextualizationScope outcomes, ContextScope scope) {
    var result = new IdentityHashMap<Observation, Planner>();
    var groups = new HashMap<Group, Shared>();
    var prepared = new ArrayList<Prepared>();
    var origin = InstantiationResolution.annotation(outcomes.getTarget().getAnnotations());
    if (origin != null && outcomes.getTarget() instanceof ObservationImpl implementation
        && implementation.getAnnotationPriorities().getOrDefault(InstantiationResolution.ANNOTATION,
            ObservationImpl.DEFINITION_ANNOTATIONS) < ObservationImpl.DEFINITION_ANNOTATIONS)
      origin = null; // A merged model annotation is not an originating-observation override.
    for (var child : outcomes.getOutcomes()) {
      var contribution = outcomes.getInstantiationResolution(child);
      var authored = InstantiationResolution.annotation(child.getAnnotations());
      if (authored == null && origin == null
          && (contribution == null || contribution.declaration().annotation() == null)) continue;
      if (!(child instanceof ObservationImpl))
        throw new IllegalArgumentException("Instantiation policy requires mutable child observations");
      var effective = (ObservationImpl) Observation.forTransport(child);
      if (contribution != null && contribution.declaration().annotation() != null)
        effective.mergeAnnotations(List.of(contribution.declaration().annotation()), ObservationImpl.MODEL_ANNOTATIONS);
      if (origin != null) effective.mergeAnnotations(List.of(origin), ObservationImpl.DEFINITION_ANNOTATIONS);
      var mode = InstantiationResolution.mode(InstantiationResolution.annotation(effective.getAnnotations()));
      if (mode != Mode.INDIVIDUAL && (effective.getObservable() == null
          || effective.getObservable().getSemantics().isCollective()
          || effective.getGeometry() == null || effective.getGeometry().isEmpty()))
        throw new IllegalArgumentException("Instantiation resolution policies require singular children");
      Planner planner = null;
      if (mode == Mode.NONE) planner = new Planner() {
        public Mode mode() { return Mode.NONE; }
        public CompletableFuture<Dataflow> plan(Observation member, ServiceContextScope execution) {
          return CompletableFuture.completedFuture(acknowledgment(member));
        }
      };
      else if (mode == Mode.COLLECTIVE) {
        var support = contribution == null || contribution.support() == null
            ? outcomes.getTarget().getGeometry()
            : Geometry.create(contribution.support());
        if (support == null || support.isEmpty())
          throw new IllegalArgumentException("Collective resolution requires contextualized instantiator support");
        var key = new Group(contribution == null ? outcomes.getTarget() : contribution,
            Utils.Json.asString(outcomes.getResolutionConstraints(child)));
        var shared = groups.computeIfAbsent(key, ignored -> new Shared(support, effective.getObservable().getUrn()));
        if (!shared.meaning.equals(effective.getObservable().getUrn()))
          throw new IllegalArgumentException("One collective instantiator contribution must have one member meaning");
        requireCoverage(support, effective.getGeometry());
        planner = shared;
      }
      effective.getMetadata().put(MODE, mode.name().toLowerCase(Locale.ROOT));
      prepared.add(new Prepared(child, effective, planner));
    }
    // All declarations/supports are checked before mutating outputs or starting a child submission.
    for (var item : prepared) {
      var mutable = (ObservationImpl) item.child();
      var before = (ObservationImpl) Observation.forTransport(mutable);
      if (scope.getCurrentTransaction() != null) scope.getCurrentTransaction().afterRollback(() -> {
        mutable.setAnnotations(before.getAnnotations());
        mutable.setAnnotationPriorities(before.getAnnotationPriorities());
        if (before.getMetadata().containsKey(MODE)) mutable.getMetadata().put(MODE, before.getMetadata().get(MODE));
        else mutable.getMetadata().remove(MODE);
      });
      mutable.setAnnotations(item.effective().getAnnotations());
      mutable.setAnnotationPriorities(item.effective().getAnnotationPriorities());
      mutable.getMetadata().put(MODE, item.effective().getMetadata().get(MODE));
      if (item.planner() != null) result.put(mutable, item.planner());
    }
    return result;
  }

  static Dataflow acknowledgment(Observation child) {
    var flow = new DataflowImpl();
    flow.setCoverage(Geometry.forTransport(child.getGeometry()));
    flow.setResolvedCoverage(1);
    var root = new ActuatorImpl();
    root.setId(child.getId()); root.setObservation(Observation.forTransport(child));
    root.setName(child.getObservable().getReferenceName());
    root.setType(child.getObservable().getArtifactType());
    root.setActuatorType(Actuator.Type.OBSERVE);
    root.setCoverage(Geometry.forTransport(child.getGeometry()));
    root.getData().put(ACKNOWLEDGMENT, true);
    root.setExecutionRole(child.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.EVENT)
        ? Actuator.ExecutionRole.EVENT : Actuator.ExecutionRole.INITIALIZATION);
    flow.getComputation().add(root);
    return flow;
  }

  static final class Shared implements Planner {
    private final Geometry support;
    private final String meaning;
    private CompletableFuture<String> frozen;
    Shared(Geometry support, String meaning) { this.support = Geometry.forTransport(support); this.meaning = meaning; }
    public Mode mode() { return Mode.COLLECTIVE; }
    public CompletableFuture<Dataflow> plan(Observation child, ServiceContextScope scope) {
      final CompletableFuture<String> selection;
      synchronized (this) {
        if (frozen == null) {
          frozen = new CompletableFuture<>();
          var completion = frozen;
          try {
            var prototype = (ObservationImpl) Observation.forTransport(child);
            prototype.setId(scope.getNextObservationId());
            prototype.setUrn("klab.instantiation.template:" + UUID.randomUUID());
            prototype.setGeometry(support);
            prototype.setResolvedCoverage(0);
            prototype.setEventTimestamps(List.of());
            scope.registerProvisionalObservation(prototype);
            var planning = scope.withResolutionConstraints(
                org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.of(
                    org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.Geometry, support));
            planning.getService(Resolver.class).resolve(prototype, planning).whenComplete((flow, failure) -> {
              if (failure != null) completion.completeExceptionally(failure);
              else if (flow == null) completion.completeExceptionally(new IllegalStateException("Missing shared explanation"));
              else try { completion.complete(Utils.Json.asString(flow)); }
              catch (Throwable invalid) { completion.completeExceptionally(invalid); }
            });
          } catch (Throwable failure) { completion.completeExceptionally(failure); }
        }
        selection = frozen;
      }
      return selection.thenApply(encoded -> bind(Utils.Json.parseObject(encoded, Dataflow.class), child, support, scope));
    }
  }

  static void requireCoverage(Geometry provided, Geometry requested) {
    if (provided == null || requested == null || requested.isEmpty())
      throw new IllegalArgumentException("Instantiation binding requires nonempty support");
    var coverage = org.integratedmodelling.klab.runtime.ObserverReuseSupport.coverage(provided, requested);
    if (coverage == null || !coverage.isComplete())
      throw new IllegalArgumentException("Instantiated child exceeds the shared explanation support");
  }

  static Dataflow bind(Dataflow template, Observation child, Geometry support, ServiceContextScope scope) {
    if (scope.isInterrupted() || scope.getCurrentTransaction() != null && scope.getCurrentTransaction().isFailed())
      throw new java.util.concurrent.CancellationException("Instantiation batch cancelled");
    requireCoverage(support, child.getGeometry());
    if (template.isEmpty() || template.getComputation().isEmpty()) {
      if (template.getResolutionOutcome() != Dataflow.ResolutionOutcome.NO_MODEL
          && !(template.getResolutionOutcome() == Dataflow.ResolutionOutcome.RESOLVED && !template.isEmpty()))
        return template; // FAILED/DEFERRED is not an exhaustive absence certificate.
      var ack = acknowledgment(child);
      ack.getNotifications().addAll(template.getNotifications());
      ack.getMetadata().put("klab.instantiation.explanationOutcome", template.getResolutionOutcome().name());
      return ack;
    }
    if (template.getCollectiveSelection() != null)
      throw new IllegalArgumentException("Collective member explanation cannot be a selection query");
    if (!(template instanceof DataflowImpl resolved) || resolved.getResolvedCoverage() < 1 - 1e-9)
      throw new IllegalArgumentException("Shared explanation did not establish full contextualized producer coverage");
    requireCoverage(template.getCoverage(), support);
    var roots = new ArrayList<>(template.getComputation());
    roots.removeIf(root -> {
      var covered = org.integratedmodelling.klab.runtime.ObserverReuseSupport.coverage(root.getCoverage(), child.getGeometry());
      return covered != null && covered.getCoverage() <= 0;
    });
    if (roots.isEmpty())
      throw new IllegalArgumentException("No shared explanation fragment covers the child");
    ((DataflowImpl) template).setComputation(roots);
    var observations = new HashMap<Long, Observation>();
    for (var root : template.getComputation()) {
      if (root.getObservation() == null || root.getActuatorType() == Actuator.Type.REFERENCE)
        throw new IllegalArgumentException("Shared explanation requires a bindable observation root");
      observations.put(root.getObservation().getId(), child);
    }
    for (var root : template.getComputation()) collect(root, observations, child.getGeometry(), scope);
    for (var root : template.getComputation()) relocate(root, observations, child, child.getGeometry(), scope);
    if (template instanceof DataflowImpl flow) {
      flow.setCoverage(Geometry.forTransport(child.getGeometry()));
      flow.getMetadata().remove(org.integratedmodelling.klab.api.data.Metadata.IM_RESOLUTION_GRAPH);
    }
    return template;
  }

  private static void collect(Actuator node, Map<Long, Observation> observations, Geometry support, ServiceContextScope scope) {
    if (node.getActuatorType() == Actuator.Type.UPDATE || !node.getClassifierDependencies().isEmpty()
        || !node.getDependencyFamilies().isEmpty() || !node.getMemberQualityInputs().isEmpty())
      throw new IllegalArgumentException("Shared explanation contains an instance-bound semantic/collection operation; use @resolution(individual)");
    var original = node.getObservation();
    if (original == null || original.getId() == Observation.QUERY_ID
        || original.getId() == Observation.UNASSIGNED_ID || node.getId() != original.getId())
      throw new IllegalArgumentException("Shared explanation has an unbound observation");
    if (original.getId() < 0 && !observations.containsKey(original.getId())) {
      if (!original.getEventTimestamps().isEmpty()
          || original.getContextualizationData() != null && original.getContextualizationData().getData() != null)
        throw new IllegalArgumentException("Shared explanation already owns initialized dependency data; use @resolution(individual)");
      var target = (ObservationImpl) Observation.forTransport(original);
      target.setId(scope.getNextObservationId()); target.setUrn(null);
      target.setTransientId(org.integratedmodelling.klab.api.Klab.getNextId());
      target.setGeometry(intersection(original.getGeometry(), support));
      target.setEventTimestamps(List.of()); target.setResolvedCoverage(0);
      observations.put(original.getId(), target);
    }
    for (var nested : node.getChildren()) collect(nested, observations, support, scope);
  }

  private static void relocate(Actuator node, Map<Long, Observation> observations, Observation rootChild, Geometry support, ServiceContextScope scope) {
    var mutable = (ActuatorImpl) node;
    mutable.setTransientId(org.integratedmodelling.klab.api.Klab.getNextId());
    var original = node.getObservation();
    var mapped = observations.get(original.getId());
    if (mapped != null) {
      if (mapped != rootChild && mapped instanceof ObservationImpl implementation) {
        var parent = observations.get(original.getParentId());
        if (parent != null) implementation.setParentId(parent.getId());
      }
      var projection = (ObservationImpl) Observation.forTransport(mapped);
      projection.mergeAnnotations(original);
      projection.setObservable(original.getObservable()); // Ordinary observer-refinement checks still run.
      mutable.setId(mapped.getId()); mutable.setObservation(projection);
      scope.registerProvisionalObservation(mapped);
    } else if (node.getActuatorType() != Actuator.Type.REFERENCE) {
      throw new IllegalArgumentException("Shared explanation cannot write an existing native observation");
    } else {
      var nativeInput = scope.getObservation(original.getId());
      if (nativeInput == null || !scope.getDigitalTwin().getKnowledgeGraph().isObservationCurrent(nativeInput, scope))
        throw new IllegalStateException("Shared explanation native input is stale or inaccessible");
      requireCoverage(nativeInput.getGeometry(), intersection(node.getCoverage(), support));
    }
    mutable.setCoverage(intersection(node.getCoverage(), support));
    if (node.getRequestedSupport() != null) mutable.setRequestedSupport(intersection(node.getRequestedSupport(), support));
    var process = node.getData().get(ProcessPlan.DATA_KEY);
    if (process != null) {
      var plan = Utils.Json.parseObject(process.toString(), ProcessPlan.class);
      var bearer = observations.get(plan.bearerId());
      if (bearer != null) node.getData().put(ProcessPlan.DATA_KEY, Utils.Json.asString(new ProcessPlan(
          plan.version(), bearer.getId(), plan.model(), plan.bindings(), plan.obligations(), plan.descriptiveLinks())));
    }
    var observer = node.getData().get("im:observer-contributions");
    if (observer != null) {
      try {
        var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
        if (!(observer instanceof List<?> entries)) throw new IllegalArgumentException("Malformed observer contributions");
        var relocated = new ArrayList<String>();
        for (var entry : entries) {
          if (!(entry instanceof String text)) throw new IllegalArgumentException("Malformed observer contribution");
          var row = mapper.readTree(text);
          if (row.hasNonNull("support")) {
            var applicable = org.integratedmodelling.klab.runtime.ObserverReuseSupport.coverage(
                Geometry.create(row.get("support").asText()), support);
            if (applicable != null && applicable.getCoverage() <= 0) continue;
            ((com.fasterxml.jackson.databind.node.ObjectNode) row).put("support",
                Geometry.forTransport(intersection(Geometry.create(row.get("support").asText()), support)).encode());
          }
          relocated.add(row.toString());
        }
        node.getData().put("im:observer-contributions", List.copyOf(relocated));
      } catch (java.io.IOException invalid) { throw new IllegalArgumentException("Malformed observer contribution", invalid); }
    }
    for (var nested : node.getChildren()) relocate(nested, observations, rootChild, support, scope);
  }

  private static Geometry intersection(Geometry original, Geometry support) {
    if (original == null || original.isUniversal()) return Geometry.forTransport(support);
    var covered = org.integratedmodelling.klab.runtime.ObserverReuseSupport.coverage(original, support);
    if (covered != null && covered.isComplete()) return Geometry.forTransport(support);
    var result = GeometryRepository.INSTANCE.scale(original).merge(GeometryRepository.INSTANCE.scale(support), LogicalConnector.INTERSECTION);
    if (result == null || result.isEmpty()) throw new IllegalArgumentException("Shared plan fragment does not intersect child support");
    return Geometry.forTransport(result);
  }
}
