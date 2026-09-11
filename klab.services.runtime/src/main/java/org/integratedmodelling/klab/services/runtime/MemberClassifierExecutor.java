package org.integratedmodelling.klab.services.runtime;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.components.ComponentRegistry;

/**
 * C2 execution stage: local invocation and pending results only. No graph or observation writes.
 */
public final class MemberClassifierExecutor {
  public record PendingAttribution(
      Observation member,
      Observable originalObservable,
      Concept abstractPredicate,
      Concept predicate,
      Geometry support,
      Scheduler.Event event) {}

  private record Invocation(boolean durable, long member, Scheduler.Event event, String support) {}

  private final ConcurrentMap<Invocation, CompletableFuture<Optional<PendingAttribution>>> results =
      new ConcurrentHashMap<>();
  private final Actuator actuator;
  private final Method method;
  private final Object receiver;
  private final ServiceCall call;
  private final Reasoner reasoner;
  private final Concept predicate;
  private final boolean optional;

  /**
   * Select exactly one supported local implementation, without using an observation-shaped target.
   */
  public static MemberClassifierExecutor compile(
      Actuator actuator, ComponentRegistry registry, ContextScope scope) {
    if (actuator.getComputation().size() != 1)
      throw new IllegalArgumentException(
          "Classification requires one local Concept-returning contextualizer");
    var call = actuator.getComputation().getFirst();
    var candidates = new ArrayList<MemberClassifierExecutor>();
    for (var descriptor : registry.getFunctionDescriptor(call)) {
      var implementation = registry.implementation(descriptor);
      if (implementation != null && supports(implementation.method)) {
        candidates.add(
            new MemberClassifierExecutor(
                actuator, implementation.method, implementation.mainClassInstance, scope));
      }
    }
    if (candidates.size() != 1)
      throw new IllegalArgumentException(
          "Expected one unambiguous local classifier implementation for " + call.getUrn());
    return candidates.getFirst();
  }

  static boolean supports(Method method) {
    if (method == null
        || !Modifier.isPublic(method.getModifiers())
        || !Concept.class.isAssignableFrom(method.getReturnType())) return false;
    Set<Class<?>> types = new HashSet<>();
    int scopes = 0;
    for (var type : method.getParameterTypes()) {
      if (!types.add(type)) return false;
      if (type == Scope.class || type == ContextScope.class) scopes++;
      else if (type != Observable.class
          && type != Observation.class
          && type != ServiceCall.class
          && type != Geometry.class
          && type != Scheduler.Event.class) return false;
    }
    return scopes == 1 && types.contains(Observable.class);
  }

  MemberClassifierExecutor(Actuator actuator, Method method, Object receiver, ContextScope scope) {
    if (actuator.getActuatorType() != Actuator.Type.UPDATE
        || actuator.getEffect() != Actuator.Effect.SEMANTIC_UPDATE
        || actuator.getContextualization() != Contextualization.CLASSIFICATION
        || actuator.getOperationObservable() == null
        || actuator.getObservation() != null
        || actuator.getOperationObservable().getContextualization()
            != Contextualization.CLASSIFICATION
        || actuator.getComputation().size() != 1
        || !supports(method))
      throw new IllegalArgumentException("Invalid classification plan or contextualizer signature");
    if (!Modifier.isStatic(method.getModifiers())
        && !method.getDeclaringClass().isInstance(receiver))
      throw new IllegalArgumentException("Classifier has no local receiver");
    if (actuator.getTargetBindings().size() != 1
        || actuator.getTargetBindings().getFirst().getKind()
            != Actuator.TargetBinding.Kind.COHORT_MEMBERS)
      throw new IllegalArgumentException("Classification requires one cohort-member binding");
    this.actuator = actuator;
    this.method = method;
    this.receiver = receiver;
    this.call = actuator.getComputation().getFirst();
    this.reasoner = Objects.requireNonNull(scope.getService(Reasoner.class));
    this.predicate =
        actuator
            .getOperationObservable()
            .builder(scope)
            .without(SemanticRole.INHERENT)
            .buildConcept();
    if (predicate == null
        || !predicate.isAbstract()
        || predicate.is(SemanticType.NOTHING)
        || !predicate.is(SemanticType.PREDICATE)
        || !reasoner.satisfiable(predicate))
      throw new IllegalArgumentException("Classification requires a consistent abstract predicate");
    this.optional =
        actuator.getModelDependency() != null && actuator.getModelDependency().isOptional();
  }

  /**
   * Supply member sets only after the corresponding cohort and member lifecycle work completed.
   * Empty sets are successful. A missing producer is never an optional classifier result.
   */
  public List<PendingAttribution> execute(
      Map<String, List<Observation>> completedMembers,
      Geometry support,
      Scheduler.Event event,
      ContextScope scope) {
    Objects.requireNonNull(support, "Missing classified support");
    Objects.requireNonNull(event, "Missing event");
    var pending = new ArrayList<PendingAttribution>();
    for (var member :
        SemanticUpdateTargets.bind(actuator.getTargetBindings().getFirst(), completedMembers)) {
      var key =
          new Invocation(
              member.getId() > 0,
              member.getId() > 0 ? member.getId() : member.getTransientId(),
              event,
              support.encode());
      var future =
          results.computeIfAbsent(
              key,
              ignored -> {
                var result = new CompletableFuture<Optional<PendingAttribution>>();
                try {
                  result.complete(invoke(member, support, event, scope));
                } catch (Throwable failure) {
                  result.completeExceptionally(failure);
                }
                return result;
              });
      try {
        future.join().ifPresent(pending::add);
      } catch (CompletionException failure) {
        throw new IllegalStateException(
            "Classifier failed for member " + member.getUrn(), failure.getCause());
      }
    }
    return List.copyOf(pending);
  }

  private Optional<PendingAttribution> invoke(
      Observation member, Geometry support, Scheduler.Event event, ContextScope scope)
      throws ReflectiveOperationException {
    var memberScope = Objects.requireNonNull(scope.within(member), "Missing member context");
    var arguments = new ArrayList<Object>();
    for (var type : method.getParameterTypes()) {
      arguments.add(
          type == Observable.class
              ? actuator.getOperationObservable()
              : type == Observation.class
                  ? member
                  : type == ServiceCall.class
                      ? call
                      : type == Geometry.class
                          ? support
                          : type == Scheduler.Event.class ? event : memberScope);
    }
    var value = (Concept) method.invoke(receiver, arguments.toArray());
    if (value == null) {
      if (optional) return Optional.empty();
      throw new IllegalArgumentException("Required classifier returned no concept");
    }
    // NOTHING is inconsistency, never absence, even for an optional dependency.
    if (value.is(SemanticType.NOTHING)
        || value.isAbstract()
        || value.is(SemanticType.ABSTRACT)
        || !value.is(SemanticType.PREDICATE)
        || !reasoner.satisfiable(value)
        || !reasoner.is(value, predicate)
        || reasoner.is(predicate, value)
        || Objects.equals(value.getUrn(), predicate.getUrn()))
      throw new IllegalArgumentException(
          "Classifier result is not a consistent concrete strict specialization: " + value);
    // Reject reclassification until C3 defines transactional replacement. Never drop unrelated
    // predicates.
    var existing = new ArrayList<Concept>(reasoner.directTraits(member.getObservable()));
    existing.addAll(reasoner.directRoles(member.getObservable()));
    if (existing.stream().anyMatch(c -> reasoner.is(c, predicate)))
      throw new IllegalArgumentException(
          "Member already has an attribution in this predicate family");
    return Optional.of(
        new PendingAttribution(member, member.getObservable(), predicate, value, support, event));
  }
}
