package org.integratedmodelling.klab.runtime.libraries;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.utilities.Utils;

/** Bounded read-only inspection of real committed configuration effects; never creates facts. */
public final class ConfigurationInspector {
  private ConfigurationInspector() {}

  public static Observation await(
      ContextScope scope,
      String definition,
      long minimumRevision,
      boolean modeled,
      long timeoutMillis)
      throws InterruptedException {
    if (timeoutMillis < 1 || timeoutMillis > 60_000 || minimumRevision < 1)
      throw new IllegalArgumentException(
          "Configuration inspection requires revision>=1 and wait1..60000ms");
    long deadline =
        System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    do {
      if (scope.isInterrupted() || Thread.currentThread().isInterrupted())
        throw new InterruptedException("Configuration inspection cancelled");
      var expected = scope.getService(Reasoner.class).resolveObservable(definition);
      if (expected == null || !expected.is(SemanticType.CONFIGURATION))
        throw new IllegalArgumentException("Expected a configuration semantic literal");
      for (var candidate :
          scope
              .getDigitalTwin()
              .getKnowledgeGraph()
              .query(Observation.class, scope)
              .where(
                  GraphModel.Fields.OBSERVABLE,
                  KnowledgeGraph.Query.Operator.EQUALS,
                  expected.getUrn())
              .order(GraphModel.Fields.ID)
              .run(scope))
        if (check(scope, candidate, definition, minimumRevision, modeled)) return candidate;
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) return null;
      Thread.sleep(
          Math.min(
              100, Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining))));
    } while (true);
  }

  public static boolean check(
      ContextScope scope,
      Observation supplied,
      String definition,
      long minimumRevision,
      boolean modeled) {
    if (supplied == null || supplied.getId() <= 0 || minimumRevision < 1) return false;
    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    var configuration = graph.getAsset(supplied.getId(), scope, Observation.class);
    var reasoner = scope.getService(Reasoner.class);
    var expected = reasoner.resolveObservable(definition);
    if (expected == null || !expected.is(SemanticType.CONFIGURATION))
      throw new IllegalArgumentException("Expected a configuration semantic literal");
    if (configuration == null
        || configuration.isEmpty()
        || configuration.getObservable() == null
        || !configuration.getObservable().is(SemanticType.CONFIGURATION)
        || !Objects.equals(configuration.getObservable().getUrn(), expected.getUrn())) return false;
    var perspective = perspective(scope);
    if (perspective == null) return false;
    var recorded = map(configuration.getMetadata().get("klab.configuration.perspective"));
    if (recorded == null
        || !Objects.equals(recorded.get("contextId"), perspective.contextId())
        || !Objects.equals(integer(recorded.get("observerId")), perspective.observerId())
        || !Objects.equals(integer(recorded.get("focusId")), perspective.focusId())) return false;
    var current = map(configuration.getMetadata().get("klab.configuration.current"));
    if (current == null
        || !Objects.equals(integer(current.get("observationId")), configuration.getId())
        || integer(current.get("triggeringCommitId")) == null
        || integer(current.get("triggeringCommitId")) <= 0
        || configuration.getGeometry() == null
        || !Objects.equals(current.get("support"), configuration.getGeometry().encode())
        || integer(current.get("number")) == null
        || integer(current.get("number")) < minimumRevision
        || !(current.get("participants") instanceof List<?> participants)
        || participants.isEmpty()) return false;
    long revision = integer(current.get("number"));
    var ids = new TreeSet<Long>();
    for (var value : participants)
      if (integer(value) != null && integer(value) > 0) ids.add(integer(value));
      else return false;
    if (ids.size() != participants.size()) return false;
    var ingredients = new TreeSet<Long>();
    for (var link :
        graph.getLinks(
            configuration,
            GraphModel.Relationship.Direction.OUTGOING,
            scope,
            GraphModel.Relationship.EMERGED_FROM)) {
      if (link.source().getId() != configuration.getId()
          || !(link.target() instanceof Observation target)
          || target.getId() <= 0) return false;
      ingredients.add(target.getId());
    }
    if (!ids.equals(ingredients)) return false;
    if (!placement(scope, configuration, expected, ids, perspective)) return false;
    if (modeled) {
      var explanation = map(configuration.getMetadata().get("klab.configuration.explanation"));
      if (explanation == null
          || !"RESOLVED".equals(explanation.get("outcome"))
          || !"configuration-scientific-initialization-v1"
              .equals(
                  configuration.getMetadata().get("klab.configuration.scientificInitialization")))
        return false;
    }
    for (var link :
        graph.getLinks(
            configuration,
            GraphModel.Relationship.Direction.INCOMING,
            scope,
            GraphModel.Relationship.DETECTED)) {
      if (!(link.source() instanceof Activity activity)
          || activity.getId() <= 0
          || activity.getType() != Activity.Type.DETECTION
          || activity.getOutcome() != Activity.Outcome.SUCCESS
          || link.target().getId() != configuration.getId()
          || !Objects.equals(integer(link.properties().get("configurationRevision")), revision))
        continue;
      var encoded = activity.getMetadata().get(SemanticEvidenceBinding.DATA_KEY);
      if (!(encoded instanceof String text)) continue;
      try {
        var binding = Utils.Json.parseObject(text, SemanticEvidenceBinding.class);
        if (!binding.complete() || binding.witness() == null) continue;
        var caps = reasoner.capabilities(scope);
        if (caps == null) continue;
        var request =
            new SemanticDependencyRequest(
                caps.getServiceId(),
                caps.getKnowledgeRevision(),
                caps.getSemanticContentFingerprint(),
                binding.obligations());
        var service = caps.getServiceId();
        long generation = caps.getKnowledgeRevision();
        var fingerprint = caps.getSemanticContentFingerprint();
        var session = caps.getSemanticSessionId();
        var actual = reasoner.semanticDependencies(request, scope);
        var after = reasoner.capabilities(scope);
        if (after != null
            && Objects.equals(service, after.getServiceId())
            && generation == after.getKnowledgeRevision()
            && Objects.equals(fingerprint, after.getSemanticContentFingerprint())
            && Objects.equals(session, after.getSemanticSessionId())
            && actual != null
            && Objects.equals(service, actual.serviceId())
            && generation == actual.knowledgeRevision()
            && Objects.equals(fingerprint, actual.baseFingerprint())
            && request.inputHash().equals(actual.inputHash())
            && request.inputHash().equals(binding.witness().inputHash())
            && SemanticDependencyWitness.revalidates(binding.witness(), actual)
            && perspective.equals(perspective(scope))) return true;
      } catch (RuntimeException malformed) {
        return false;
      }
    }
    return false;
  }

  private record Perspective(String contextId, Long observerId, Long focusId) {}

  /** Mirrors the persisted Runtime perspective without depending on a Runtime implementation. */
  private static Perspective perspective(ContextScope scope) {
    var context = scope.getId();
    if (context == null || context.isBlank()) return null;
    var selected = scope.getObserverId();
    long observer = selected == null ? 0 : selected;
    if (observer < 0) return null;
    if (observer > 0) {
      var agent = scope.getObserver();
      if (agent == null
          || agent.getId() != observer
          || agent.getObservable() == null
          || !agent.getObservable().is(SemanticType.AGENT)) return null;
    }
    var focus = scope.getContextObservation();
    if (focus != null && focus.getId() <= 0) return null;
    return new Perspective(context, observer, focus == null ? 0 : focus.getId());
  }

  private static boolean placement(
      ContextScope scope,
      Observation configuration,
      Observable expected,
      Set<Long> ingredients,
      Perspective perspective) {
    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    var cohorts =
        graph.getLinks(
            configuration,
            GraphModel.Relationship.Direction.INCOMING,
            scope,
            GraphModel.Relationship.HAS_MEMBER);
    var parents = parents(scope, configuration);
    if (parents == null) return false;
    var rawApplication = configuration.getMetadata().get("klab.configuration.applicationContext");
    Long application = rawApplication == null ? 0 : integer(rawApplication);
    if (application == null || application < 0) return false;
    if (!parents.isEmpty()) {
      if (!cohorts.isEmpty() || parents.size() != 1) return false;
      var bearer = parents.values().iterator().next();
      if (!substantial(bearer)
          || application != bearer.getId()
          || configuration.getParentId() != bearer.getId()) return false;
      for (var id : ingredients) {
        var ingredient = graph.getAsset(id, scope, Observation.class);
        if (ingredient == null
            || ingredient.isEmpty()
            || ingredient.getObservable() == null
            || !SemanticType.isDependent(ingredient.getObservable().getSemantics().getType()))
          return false;
        var owners = parents(scope, ingredient);
        if (owners == null || !owners.keySet().equals(parents.keySet())) return false;
      }
      return true;
    }
    if (cohorts.size() != 1 || application.longValue() != perspective.focusId()) return false;
    var link = cohorts.iterator().next();
    if (!(link.source() instanceof Cohort owner)
        || owner.getId() <= 0
        || link.target().getId() != configuration.getId()
        || configuration.getParentId() != owner.getId()
        || owner.getObservable() == null
        || !Objects.equals(owner.getObservable().getUrn(), expected.getUrn())) return false;
    boolean relationship = false;
    for (var id : ingredients) {
      var ingredient = graph.getAsset(id, scope, Observation.class);
      if (ingredient == null || ingredient.isEmpty() || ingredient.getObservable() == null)
        return false;
      relationship |= ingredient.getObservable().is(SemanticType.RELATIONSHIP);
    }
    return relationship;
  }

  private static Map<Long, Observation> parents(ContextScope scope, Observation child) {
    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    var result = new TreeMap<Long, Observation>();
    for (var link :
        graph.getLinks(
            child,
            GraphModel.Relationship.Direction.INCOMING,
            scope,
            GraphModel.Relationship.HAS_CHILD)) {
      if (link.target().getId() != child.getId()
          || !(link.source() instanceof Observation parent)
          || parent.getId() <= 0) return null;
      var actual = graph.getAsset(parent.getId(), scope, Observation.class);
      if (actual == null
          || actual.getId() != parent.getId()
          || !substantial(actual)
          || result.put(parent.getId(), actual) != null) return null;
    }
    return result;
  }

  private static boolean substantial(Observation observation) {
    return observation != null
        && !observation.isEmpty()
        && observation.getObservable() != null
        && !observation.getObservable().getSemantics().isCollective()
        && SemanticType.isEnumerableSubstantial(
            observation.getObservable().getSemantics().getType());
  }

  private static Long integer(Object value) {
    return value instanceof Byte
            || value instanceof Short
            || value instanceof Integer
            || value instanceof Long
        ? ((Number) value).longValue()
        : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    if (!(value instanceof String text)) return null;
    try {
      return Utils.Json.parseObject(text, Map.class);
    } catch (RuntimeException malformed) {
      return null;
    }
  }
}
