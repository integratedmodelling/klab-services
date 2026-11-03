package org.integratedmodelling.klab.runtime.libraries;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/** Read-only diagnostic data and comparisons, never semantic or execution authority. */
public final class ConfigurationRecomputeInspector {
  private ConfigurationRecomputeInspector() {}

  public record Detection(long activityId, long revision, String payload) {}

  public record Dependency(long sourceId, long revision, String authorities, String explanation) {}

  public record Snapshot(
      String context,
      long observer,
      long focus,
      long id,
      String definition,
      long revision,
      long trigger,
      String current,
      String perspective,
      String support,
      String authorities,
      String explanation,
      List<String> history,
      Set<Long> participants,
      List<Dependency> dependencies,
      Map<Long, Detection> detections,
      Long currentInstant) {
    public Snapshot {
      history = List.copyOf(history);
      participants = Set.copyOf(participants);
      dependencies = List.copyOf(dependencies);
      detections = Map.copyOf(detections);
    }
  }

  private static long number(Object value) {
    if (!(value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long))
      throw new IllegalStateException("Expected an exact configuration integer");
    return ((Number) value).longValue();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(String value) {
    return Utils.Json.parseObject(value, Map.class);
  }

  private static String text(Map<String, ?> values, String key) {
    if (!(values.get(key) instanceof String value))
      throw new IllegalStateException("Missing configuration field " + key);
    return value;
  }

  static Long managedDetectionRevision(
      Activity activity, long configurationId, Map<String, ?> properties) {
    var rows = new ArrayList<Map<String, Object>>();
    boolean publication = false;
    for (var entry : activity.getMetadata().entrySet())
      if (entry.getKey().startsWith("klab.configuration.birth.")) {
        publication = true;
        if (!(entry.getValue() instanceof String value))
          throw new IllegalStateException("Malformed native configuration birth lineage");
        rows.add(map(value));
      }
    if (activity.getMetadata().containsKey("klab.configuration.revisions")) {
      publication = true;
      var values = activity.getMetadata().get("klab.configuration.revisions");
      if (!(values instanceof List<?> list))
        throw new IllegalStateException("Malformed native configuration revision lineage");
      for (var value : list) {
        if (!(value instanceof String text))
          throw new IllegalStateException("Malformed native configuration revision row");
        rows.add(map(text));
      }
    }
    if (!publication && properties.get("configurationRevision") == null) return null;
    var matching = new ArrayList<Long>();
    for (var row : rows) {
      if (row == null || !(row.get("next") instanceof Map<?, ?> next))
        throw new IllegalStateException("Native publication has no next revision");
      long id = number(next.get("observationId")), revision = number(next.get("number"));
      if (revision < 1)
        throw new IllegalStateException("Native publication has an invalid revision");
      if (id == configurationId) matching.add(revision);
    }
    if (matching.size() != 1
        || number(properties.get("configurationRevision")) != matching.getFirst()) {
      if (Boolean.getBoolean("klab.diagnostics.configurationRecompute"))
        try {
          var facts = new LinkedHashMap<String, Object>();
          facts.put("stage", "managed-detection-stamp");
          facts.put("activity", activity.getId());
          facts.put("configuration", configurationId);
          var stamp = properties.get("configurationRevision");
          facts.put(
              "edgeRevision",
              stamp instanceof Byte
                      || stamp instanceof Short
                      || stamp instanceof Integer
                      || stamp instanceof Long
                  ? stamp
                  : null);
          facts.put("edgeRevisionType", stamp == null ? null : stamp.getClass().getName());
          facts.put("matchingRevisions", List.copyOf(matching));
          facts.put(
              "publications",
              rows.stream()
                  .map(
                      row -> {
                        var next = (Map<?, ?>) row.get("next");
                        return Map.of(
                            "configuration",
                            next.get("observationId"),
                            "revision",
                            next.get("number"));
                      })
                  .toList());
          emit(facts);
        } catch (RuntimeException ignored) {
          /* Preserve original lineage guard. */
        }
      throw new IllegalStateException(
          "Managed detection stamp differs from its native publication");
    }
    return matching.getFirst();
  }

  private static Long instant(ContextScope scope) {
    var value =
        Objects.requireNonNull(scope.getDigitalTwin().getScheduler(), "Scheduler unavailable")
            .currentModeledInstant();
    return value.isPresent() ? value.getAsLong() : null;
  }

  private static Observation current(ContextScope scope, long id) {
    var results =
        scope
            .getDigitalTwin()
            .getKnowledgeGraph()
            .query(Observation.class, scope)
            .where(GraphModel.Fields.ID, KnowledgeGraph.Query.Operator.EQUALS, id)
            .run(scope);
    if (results.size() != 1)
      throw new IllegalStateException("Configuration is missing or ambiguous");
    return results.getFirst();
  }

  public static Snapshot snapshot(ContextScope scope, Observation supplied) {
    if (supplied == null || supplied.getId() <= 0 || scope.getCurrentTransaction() != null)
      throw new IllegalArgumentException(
          "A committed configuration and transaction-free scope are required");
    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    var before = instant(scope);
    var configuration = current(scope, supplied.getId());
    var definition = configuration.getObservable().getUrn();
    if (!ConfigurationInspector.check(scope, configuration, definition, 1, true))
      throw new IllegalStateException("Configuration has no currently verified modeled detection");
    var metadata = configuration.getMetadata();
    var encoded = text(metadata, "klab.configuration.current");
    var revision = map(encoded);
    var participants = new TreeSet<Long>();
    if (!(revision.get("participants") instanceof List<?> ids))
      throw new IllegalStateException("Missing configuration participants");
    ids.forEach(id -> participants.add(number(id)));
    var dependencies = new ArrayList<Dependency>();
    for (var link :
        graph.getLinks(
            configuration,
            GraphModel.Relationship.Direction.INCOMING,
            scope,
            GraphModel.Relationship.AFFECTS)) {
      if (!"configuration-input".equals(link.properties().get("role"))) continue;
      if (link.target().getId() != configuration.getId())
        throw new IllegalStateException("Foreign configuration input edge");
      dependencies.add(
          new Dependency(
              link.source().getId(),
              number(link.properties().get("revision")),
              Objects.toString(link.properties().get("authorities"), null),
              Objects.toString(link.properties().get("explanation"), null)));
    }
    dependencies.sort(Comparator.comparingLong(Dependency::sourceId));
    var detections = new TreeMap<Long, Detection>();
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
          || link.target().getId() != configuration.getId())
        throw new IllegalStateException("Invalid native detection lineage");
      var managedRevision =
          managedDetectionRevision(activity, configuration.getId(), link.properties());
      if (managedRevision == null)
        continue; // Ordinary scientific detection is not a participant publication.
      var payload = new LinkedHashMap<String, Object>();
      payload.put("type", activity.getType().name());
      payload.put("outcome", activity.getOutcome().name());
      payload.put("metadata", new TreeMap<>(activity.getMetadata()));
      var detection =
          new Detection(activity.getId(), managedRevision, Utils.Json.asString(payload));
      if (detections.putIfAbsent(activity.getId(), detection) != null)
        throw new IllegalStateException("Duplicate detection lineage");
    }
    if (detections.values().stream().noneMatch(d -> d.revision() == number(revision.get("number"))))
      throw new IllegalStateException("Current configuration has no managed publication lineage");
    var history = graph.getConfigurationHistory(configuration, scope);
    if (history.isEmpty() || !Objects.equals(map(history.getLast()).get("next"), revision))
      throw new IllegalStateException("Native configuration history differs from current revision");
    var again = current(scope, configuration.getId());
    if (!Objects.equals(encoded, again.getMetadata().get("klab.configuration.current"))
        || !Objects.equals(
            metadata.get("klab.configuration.perspective"),
            again.getMetadata().get("klab.configuration.perspective"))
        || !Objects.equals(before, instant(scope)))
      throw new IllegalStateException("Configuration changed while taking its diagnostic snapshot");
    var observer = scope.getObserverId();
    var focus = scope.getContextObservation();
    return new Snapshot(
        scope.getId(),
        observer == null ? 0 : observer,
        focus == null ? 0 : focus.getId(),
        configuration.getId(),
        definition,
        number(revision.get("number")),
        number(revision.get("triggeringCommitId")),
        encoded,
        text(metadata, "klab.configuration.perspective"),
        text(revision, "support"),
        text(metadata, "klab.configuration.authorities"),
        text(metadata, "klab.configuration.explanation"),
        history,
        participants,
        dependencies,
        detections,
        before);
  }

  public static boolean recomputed(
      ContextScope scope,
      Snapshot before,
      Observation configuration,
      Observation process,
      Observation height,
      Observation width) {
    entry(before, configuration, process, height, width);
    if (before == null
        || process == null
        || height == null
        || width == null
        || configuration == null) return false;
    final Snapshot after;
    try {
      after = snapshot(scope, configuration);
    } catch (RuntimeException failure) {
      try {
        if (Boolean.getBoolean("klab.diagnostics.configurationRecompute")) {
          var facts = new LinkedHashMap<String, Object>();
          facts.put("stage", "snapshot-failure");
          facts.put("type", failure.getClass().getName());
          facts.put(
              "frames",
              Arrays.stream(failure.getStackTrace()).limit(10).map(Object::toString).toList());
          emit(facts);
        }
      } catch (RuntimeException ignored) {
        /* Preserve the original snapshot failure even if diagnostics fail. */
      }
      throw failure;
    }
    if (!before.context().equals(after.context())
        || before.observer() != after.observer()
        || before.focus() != after.focus()
        || before.id() != after.id()
        || after.revision() != before.revision() + 1
        || !before.definition().equals(after.definition())
        || !before.perspective().equals(after.perspective())
        || !before.support().equals(after.support())
        || !before.participants().equals(Set.of(height.getId(), width.getId()))
        || !before.participants().equals(after.participants())
        || !historyExtended(before, after)
        || !after.detections().entrySet().containsAll(before.detections().entrySet())
        || after.detections().values().stream()
            .noneMatch(
                d ->
                    d.revision() == after.revision()
                        && !before.detections().containsKey(d.activityId()))) {
      diagnose("snapshot-comparison", before, after, height, width, null, List.of());
      return false;
    }
    var expectedInputs = new TreeSet<>(before.participants());
    var actual = current(scope, configuration.getId());
    if (actual == null) return false;
    var application = actual.getMetadata().get("klab.configuration.applicationContext");
    if (application instanceof Number id && id.longValue() > 0) expectedInputs.add(id.longValue());
    if (!managed(
            before.dependencies(),
            expectedInputs,
            before.revision(),
            before.authorities(),
            before.explanation())
        || !managed(
            after.dependencies(),
            expectedInputs,
            after.revision(),
            after.authorities(),
            after.explanation())) {
      diagnose("managed-inputs", before, after, height, width, expectedInputs, List.of());
      return false;
    }
    var activities =
        scope
            .getDigitalTwin()
            .getKnowledgeGraph()
            .query(Activity.class, scope)
            .target(process)
            .along(GraphModel.Relationship.CREATED)
            .run(scope);
    var matches =
        transitionMatches(
            scope.getId(),
            after.trigger(),
            after.currentInstant(),
            height.getId(),
            width.getId(),
            activities);
    if (!matches)
      diagnose("modeled-transition", before, after, height, width, expectedInputs, activities);
    return matches;
  }

  private static void entry(
      Snapshot before,
      Observation configuration,
      Observation process,
      Observation height,
      Observation width) {
    if (!Boolean.getBoolean("klab.diagnostics.configurationRecompute")) return;
    var facts = new LinkedHashMap<String, Object>();
    facts.put("stage", "entry-v2");
    facts.put("beforePresent", before != null);
    facts.put("configurationPresent", configuration != null);
    facts.put("processPresent", process != null);
    facts.put("heightPresent", height != null);
    facts.put("widthPresent", width != null);
    emit(facts);
  }

  private static void emit(Map<String, ?> facts) {
    try {
      System.err.println("CONFIGURATION_FINAL_CHECK_DIAGNOSTIC " + Utils.Json.asString(facts));
    } catch (RuntimeException ignored) {
      /* Explicit local opt-in, no payload values or effect on predicate. */
    }
  }

  /** Pure comparisons of already captured operands, no authority or extra native reads. */
  static Map<String, Boolean> snapshotChecks(
      Snapshot before, Snapshot after, long height, long width) {
    var facts = new LinkedHashMap<String, Boolean>();
    facts.put("identity.context", before.context().equals(after.context()));
    facts.put("identity.observer", before.observer() == after.observer());
    facts.put("identity.focus", before.focus() == after.focus());
    facts.put("identity.configuration", before.id() == after.id());
    facts.put("identity.definition", before.definition().equals(after.definition()));
    facts.put("identity.perspective", before.perspective().equals(after.perspective()));
    facts.put("value.support", before.support().equals(after.support()));
    facts.put("value.initialParticipants", before.participants().equals(Set.of(height, width)));
    facts.put("value.participantsPreserved", before.participants().equals(after.participants()));
    facts.put("history.oneRevision", after.revision() == before.revision() + 1);
    try {
      facts.put("history.exactExtension", historyExtended(before, after));
      facts.put("history.parseAvailable", true);
    } catch (RuntimeException unavailable) {
      facts.put("history.exactExtension", null);
      facts.put("history.parseAvailable", false);
    }
    facts.put(
        "history.detectionsPreserved",
        after.detections().entrySet().containsAll(before.detections().entrySet()));
    facts.put(
        "execution.newDetection",
        after.detections().values().stream()
            .anyMatch(
                d ->
                    d.revision() == after.revision()
                        && !before.detections().containsKey(d.activityId())));
    return Collections.unmodifiableMap(facts);
  }

  private static List<Map<String, Object>> dependencyFacts(Snapshot snapshot) {
    return snapshot.dependencies().stream()
        .map(
            d -> {
              var row = new LinkedHashMap<String, Object>();
              row.put("source", d.sourceId());
              row.put("revision", d.revision());
              row.put("revisionMatches", d.revision() == snapshot.revision());
              row.put("authorityMatches", Objects.equals(d.authorities(), snapshot.authorities()));
              row.put(
                  "explanationMatches", Objects.equals(d.explanation(), snapshot.explanation()));
              return (Map<String, Object>) row;
            })
        .toList();
  }

  private static void diagnose(
      String stage,
      Snapshot before,
      Snapshot after,
      Observation height,
      Observation width,
      Set<Long> expected,
      List<Activity> activities) {
    if (!Boolean.getBoolean("klab.diagnostics.configurationRecompute")) return;
    var facts = new LinkedHashMap<String, Object>();
    facts.put("stage", stage);
    try {
      facts.put("checks", snapshotChecks(before, after, height.getId(), width.getId()));
      facts.put("configuration", after.id());
      facts.put("height", height.getId());
      facts.put("width", width.getId());
      facts.put("beforeRevision", before.revision());
      facts.put("afterRevision", after.revision());
      facts.put("beforeTrigger", before.trigger());
      facts.put("afterTrigger", after.trigger());
      facts.put("beforeInstant", before.currentInstant());
      facts.put("afterInstant", after.currentInstant());
      facts.put("beforeHistoryCount", before.history().size());
      facts.put("afterHistoryCount", after.history().size());
      if (after.history().size() > before.history().size()) {
        var row = map(after.history().getLast());
        var prior = map(before.current());
        var next = map(after.current());
        facts.put("historyPreviousMatches", Objects.equals(row.get("previous"), prior));
        facts.put("historyNextMatches", Objects.equals(row.get("next"), next));
        if (row.get("previous") instanceof Map<?, ?> old) {
          var keys = new TreeSet<String>();
          prior.keySet().forEach(keys::add);
          old.keySet()
              .forEach(
                  k -> {
                    if (k instanceof String key) keys.add(key);
                  });
          facts.put(
              "historyPreviousDifferingFields",
              keys.stream()
                  .filter(k -> !Objects.equals(prior.get(k), old.get(k)))
                  .map(k -> k.matches("[A-Za-z0-9_.:-]{1,120}") ? k : "[redacted-key]")
                  .limit(32)
                  .toList());
        }
      }
      facts.put("supportEncodingEqual", before.support().equals(after.support()));
      facts.put(
          "supportCanonicalEqual",
          org.integratedmodelling.klab.api.geometry.Geometry.create(before.support())
              .encode()
              .equals(
                  org.integratedmodelling.klab.api.geometry.Geometry.create(after.support())
                      .encode()));
      if (expected != null) {
        facts.put("expectedInputs", expected);
        facts.put(
            "beforeManaged",
            managed(
                before.dependencies(),
                expected,
                before.revision(),
                before.authorities(),
                before.explanation()));
        facts.put(
            "afterManaged",
            managed(
                after.dependencies(),
                expected,
                after.revision(),
                after.authorities(),
                after.explanation()));
        facts.put("beforeDependencyFacts", dependencyFacts(before));
        facts.put("afterDependencyFacts", dependencyFacts(after));
      }
      var transitions = new ArrayList<Map<String, Object>>();
      for (var activity : activities) {
        var item = new LinkedHashMap<String, Object>();
        item.put("activity", activity.getId());
        item.put("type", activity.getType());
        item.put("outcome", activity.getOutcome());
        var raw = activity.getMetadata().get(TransitionCommit.METADATA_KEY);
        if (raw instanceof String encoded) {
          var receipt = Utils.Json.parseObject(encoded, TransitionCommit.class);
          item.put("commit", receipt.commit().getId());
          item.put("contextMatches", before.context().equals(receipt.contextId()));
          item.put(
              "journals",
              receipt.journals().stream()
                  .map(
                      j ->
                          Map.of(
                              "kind",
                              j.kind(),
                              "commit",
                              j.commitId(),
                              "end",
                              j.end(),
                              "heightChanged",
                              j.changedAssets().contains(height.getId()),
                              "widthChanged",
                              j.changedAssets().contains(width.getId())))
                  .toList());
        }
        transitions.add(item);
      }
      facts.put("executionTransitions", transitions);
    } catch (RuntimeException unavailable) {
      facts.put("diagnosticFailureType", unavailable.getClass().getName());
    }
    emit(facts);
  }

  /** Structural comparison only; the public check separately requires real modeled detection. */
  static boolean transitionMatches(
      String context,
      long trigger,
      Long current,
      long changed,
      long unchanged,
      List<Activity> activities) {
    var transitions = new ArrayList<SchedulerJournal>();
    for (var activity : activities) {
      if (activity.getType() != Activity.Type.SIMULATION
          || activity.getOutcome() != Activity.Outcome.SUCCESS) continue;
      var raw = activity.getMetadata().get(TransitionCommit.METADATA_KEY);
      if (!(raw instanceof String encoded)) continue;
      var receipt = Utils.Json.parseObject(encoded, TransitionCommit.class);
      if (!context.equals(receipt.contextId()) || receipt.commit().getId() != trigger) continue;
      for (var journal : receipt.journals())
        if (journal.kind() == Scheduler.Event.Type.TEMPORAL_TRANSITION
            && journal.commitId() == trigger
            && journal.changedAssets().contains(changed)
            && !journal.changedAssets().contains(unchanged)) transitions.add(journal);
    }
    return transitions.size() == 1 && current != null && current == transitions.getFirst().end();
  }

  static boolean historyExtended(Snapshot before, Snapshot after) {
    if (after.history().size() != before.history().size() + 1
        || !after.history().subList(0, before.history().size()).equals(before.history()))
      return false;
    var appended = map(after.history().getLast());
    return Objects.equals(appended.get("previous"), map(before.current()))
        && Objects.equals(appended.get("next"), map(after.current()));
  }

  static boolean managed(
      List<Dependency> links,
      Set<Long> expected,
      long revision,
      String authorities,
      String explanation) {
    if (!receipts(authorities, explanation)) return false;
    var actual = new TreeSet<Long>();
    for (var dependency : links)
      if (dependency.revision() != revision
          || !actual.add(dependency.sourceId())
          || !Objects.equals(authorities, dependency.authorities())
          || !Objects.equals(explanation, dependency.explanation())) return false;
    return actual.equals(expected);
  }

  private static boolean receipts(String authorities, String explanation) {
    if (authorities == null
        || authorities.isBlank()
        || explanation == null
        || explanation.isBlank()) return false;
    try {
      var mapper =
          org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
      var sources = mapper.readTree(authorities);
      var model = mapper.readTree(explanation);
      if (sources == null
          || !sources.isArray()
          || sources.isEmpty()
          || model == null
          || !model.isObject()
          || !"RESOLVED".equals(model.path("outcome").asText())) return false;
      for (var source : sources) if (!source.isObject() || source.isEmpty()) return false;
      // These values are diagnostic data, not authority. Existing ConfigurationInspector
      // validates actual scientific detection; here each native edge must retain exactly
      // its own revision's configuration receipts. New revisions may legitimately differ.
      return true;
    } catch (java.io.IOException | RuntimeException malformed) {
      return false;
    }
  }
}
