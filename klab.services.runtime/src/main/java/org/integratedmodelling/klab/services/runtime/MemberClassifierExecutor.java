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
import org.integratedmodelling.klab.api.services.runtime.objects.ClassificationMemberResult;

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

  private record Generation(String reasonerId, long revision, String semanticFingerprint, String semanticSessionId) {}
  private record Invocation(boolean durable, long member, String inputObservable,
      Scheduler.Event event, String support, Generation generation) {}
  private record InvocationResult(ClassificationMemberResult member, PendingAttribution pending) {}

  /** Every supplied member's result, plus the separate effects to stage. Not a certified closure. */
  public record DetailedResult(List<ClassificationMemberResult> members,
      List<PendingAttribution> pendingAttributions, String reasonerId, long knowledgeRevision) {
    public DetailedResult {
      members = List.copyOf(members);
      pendingAttributions = List.copyOf(pendingAttributions);
    }
  }

  private final ConcurrentMap<Invocation, CompletableFuture<InvocationResult>> results =
      new ConcurrentHashMap<>();
  private final Actuator actuator;
  private final Method method;
  private final Object receiver;
  private final ServiceCall call;
  private final Reasoner reasoner;
  private final Concept predicate;
  private final Concept family;
  private final boolean optional;
  private final Generation preparedGeneration;

  /**
   * Select exactly one supported local implementation, without using an observation-shaped target.
   */
  public static MemberClassifierExecutor compile(
      Actuator actuator, ComponentRegistry registry, ContextScope scope) {
    if (Boolean.TRUE.equals(actuator.getData().get(Actuator.CLASSIFICATION_CHECK_ONLY)))
      return new MemberClassifierExecutor(actuator, null, null, scope);
    if (actuator.getComputation().size() != 1)
      throw new IllegalArgumentException(
          "Classification requires one local Concept-returning contextualizer");
    var call = actuator.getComputation().getFirst();
    var candidates = new ArrayList<MemberClassifierExecutor>();
    for (var descriptor : registry.getFunctionDescriptor(call, scope)) {
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

  /** Each root transaction attempt owns a fresh invocation cache. */
  MemberClassifierExecutor forAttempt(ContextScope scope) {
    return new MemberClassifierExecutor(actuator, method, receiver, scope);
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
        || (Boolean.TRUE.equals(actuator.getData().get(Actuator.CLASSIFICATION_CHECK_ONLY))
            ? !actuator.getComputation().isEmpty() || method != null || receiver != null
            : actuator.getComputation().size() != 1 || !supports(method)))
      throw new IllegalArgumentException("Invalid classification plan or contextualizer signature");
    if (method != null && !Modifier.isStatic(method.getModifiers())
        && !method.getDeclaringClass().isInstance(receiver))
      throw new IllegalArgumentException("Classifier has no local receiver");
    if (actuator.getTargetBindings().size() != 1
        || actuator.getTargetBindings().getFirst().getKind()
            != Actuator.TargetBinding.Kind.COHORT_MEMBERS)
      throw new IllegalArgumentException("Classification requires one cohort-member binding");
    this.actuator = actuator;
    this.method = method;
    this.receiver = receiver;
    this.call = method == null ? null : actuator.getComputation().getFirst();
    this.reasoner = Objects.requireNonNull(scope.getService(Reasoner.class));
    this.preparedGeneration = generation(scope);
    this.predicate =
        actuator
            .getOperationObservable()
            .builder(scope)
            .without(SemanticRole.INHERENT)
            .buildConcept();
    if (predicate == null
        || predicate.is(SemanticType.NOTHING)
        || !predicate.is(SemanticType.PREDICATE)
        || !reasoner.satisfiable(predicate))
      throw new IllegalArgumentException("Classification requires a consistent predicate");
    this.family = reasoner.lexicalRoot(predicate);
    if (family == null || family.is(SemanticType.NOTHING)
        || !family.is(SemanticType.PREDICATE) || !reasoner.satisfiable(family)
        || !(Objects.equals(predicate.getUrn(), family.getUrn()) || reasoner.is(predicate, family)))
      throw new IllegalArgumentException("Classification requires a valid lexical predicate family");
    this.optional =
        actuator.getModelDependency() != null && actuator.getModelDependency().isOptional();
    requireGeneration(scope, preparedGeneration);
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
    return executeDetailed(completedMembers, support, event, scope).pendingAttributions();
  }

  public DetailedResult executeDetailed(Map<String, List<Observation>> completedMembers,
      Geometry support, Scheduler.Event event, ContextScope scope) {
    requireActive(scope);
    requireGeneration(scope, preparedGeneration);
    Objects.requireNonNull(support, "Missing classified support");
    Objects.requireNonNull(event, "Missing event");
    var pending = new ArrayList<PendingAttribution>();
    var outcomes = new ArrayList<ClassificationMemberResult>();
    for (var candidate :
        SemanticUpdateTargets.bind(actuator.getTargetBindings().getFirst(), completedMembers)) {
      requireActive(scope);
      var member = candidate;
      if (scope instanceof org.integratedmodelling.klab.services.scopes.ServiceContextScope serviceScope
          && serviceScope.getCurrentTransaction() != null)
        member = serviceScope.getCurrentTransaction().readObservation(candidate);
      var classifiedMember = member;
      var key =
          new Invocation(
              member.getId() > 0,
              member.getId() > 0 ? member.getId() : member.getTransientId(),
              member.getObservable().getUrn(), event, support.encode(), preparedGeneration);
      var future =
          results.computeIfAbsent(
              key,
              ignored -> {
                var result = new CompletableFuture<InvocationResult>();
                try {
                  result.complete(invoke(classifiedMember, support, event, scope));
                } catch (Throwable failure) {
                  result.completeExceptionally(failure);
                }
                return result;
              });
      try {
        var result = future.join();
        outcomes.add(result.member());
        if (result.pending() != null) pending.add(result.pending());
      } catch (CompletionException failure) {
        throw new IllegalStateException(
            "Classifier failed for member " + member.getUrn(), failure.getCause());
      }
    }
    requireActive(scope);
    requireGeneration(scope, preparedGeneration);
    return new DetailedResult(outcomes, pending,
        preparedGeneration == null ? null : preparedGeneration.reasonerId(),
        preparedGeneration == null ? -1 : preparedGeneration.revision());
  }

  private InvocationResult invoke(
      Observation member, Geometry support, Scheduler.Event event, ContextScope scope)
      throws ReflectiveOperationException {
    var existing = new ArrayList<Concept>(reasoner.directTraits(member.getObservable()));
    existing.addAll(reasoner.directRoles(member.getObservable()));
    var classified = new LinkedHashMap<String, Concept>();
    for (var attribution : existing) {
      if (!(Objects.equals(attribution.getUrn(), family.getUrn()) || reasoner.is(attribution, family))) continue;
      if (!validSpecialization(attribution, family)) {
        throw new IllegalArgumentException("Member has an invalid attribution in this predicate family");
      }
      classified.putIfAbsent(attribution.getUrn(), attribution);
    }
    // Existing family knowledge completes classification, even when it is a different class.
    // Final selection checks the requested predicate separately. Never reclassify a known member
    // to obtain a desired answer, stage another attribution or repeat its characterization.
    if (!classified.isEmpty()) return new InvocationResult(
        memberResult(member, classified.values(), ClassificationMemberResult.Origin.EXISTING, support, event), null);

    if (method == null)
      throw new IllegalArgumentException("Unclassified member requires a classifier model: " + member.getUrn());
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
    Concept value;
    try { value = (Concept) method.invoke(receiver, arguments.toArray()); }
    catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof CancellationException cancelled) throw cancelled;
      throw failure;
    }
    requireActive(scope);
    requireGeneration(scope, preparedGeneration);
    if (value == null) {
      if (optional) return new InvocationResult(
          memberResult(member, List.of(), ClassificationMemberResult.Origin.OPTIONAL_ABSENCE, support, event), null);
      throw new IllegalArgumentException("Required classifier returned no concept");
    }
    // NOTHING is inconsistency, never absence, even for an optional dependency.
    if (!validSpecialization(value))
      throw new IllegalArgumentException(
          "Classifier result is not a consistent concrete specialization: " + value);
    return new InvocationResult(
        memberResult(member, List.of(value), ClassificationMemberResult.Origin.CLASSIFIER, support, event),
        new PendingAttribution(member, member.getObservable(), predicate, value, support, event));
  }
  private ClassificationMemberResult memberResult(Observation member, Collection<Concept> values,
      ClassificationMemberResult.Origin origin, Geometry support, Scheduler.Event event) {
    return new ClassificationMemberResult(member.getId(), member.getTransientId(), member.getUrn(),
        member.getObservable().getUrn(), predicate.getUrn(),
        values.stream().map(Concept::getUrn).toList(), origin, org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.sanitize(support).encode(), event.toKey());
  }

  private Generation generation(ContextScope scope) {
    var capabilities = reasoner.capabilities(scope);
    if (!(capabilities instanceof Reasoner.Capabilities semantic)
        || semantic.getServiceId() == null || semantic.getKnowledgeRevision() < 0) return null;
    return new Generation(semantic.getServiceId(), semantic.getKnowledgeRevision(), semantic.getSemanticContentFingerprint(), semantic.getSemanticSessionId());
  }

  boolean semanticFootprint(ComponentRegistry registry,
      org.integratedmodelling.klab.services.scopes.ServiceContextScope scope) {
    return method == null && Boolean.TRUE.equals(actuator.getData().get(Actuator.CLASSIFICATION_CHECK_ONLY))
        || SemanticExecutionFootprints.function(registry, method, receiver, call, scope);
  }

  String requestedPredicateUrn() { return predicate.getUrn(); }

  void verifyGeneration(ContextScope scope) {
    requireActive(scope);
    requireGeneration(scope, preparedGeneration);
  }

  private void requireGeneration(ContextScope scope, Generation expected) {
    if (!Objects.equals(expected, generation(scope)))
      throw new IllegalStateException("Classifier semantic generation changed during invocation");
  }

  private static void requireActive(ContextScope scope) {
    if (scope.isInterrupted()) throw new CancellationException("Classification was cancelled");
    var transaction = scope.getCurrentTransaction();
    if (transaction != null && transaction.isFailed())
      throw new IllegalStateException("Classification attempt has failed");
  }

  private boolean validSpecialization(Concept value) {
    return validSpecialization(value, predicate);
  }

  private boolean validSpecialization(Concept value, Concept boundary) {
    return !value.is(SemanticType.NOTHING)
        && !value.isAbstract() && !value.is(SemanticType.ABSTRACT)
        && value.is(SemanticType.PREDICATE) && reasoner.satisfiable(value)
        && (Objects.equals(value.getUrn(), boundary.getUrn()) || reasoner.is(value, boundary));
  }

}
