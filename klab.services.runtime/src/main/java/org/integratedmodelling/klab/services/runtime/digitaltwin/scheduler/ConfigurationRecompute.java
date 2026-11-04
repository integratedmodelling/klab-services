package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod;
import org.integratedmodelling.klab.runtime.storage.LocalTemporalWriteSet;
import org.integratedmodelling.klab.services.runtime.TemporalGeometry;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Input-revision execution inside the caller's guarded transaction. No clock/cursor mutation. */
final class ConfigurationRecompute {
  static final String RECEIPTS = "klab.scheduler.configurationRecomputations";
  static final String PROVENANCE = "klab.scheduler.configurationInputChange";

  private ConfigurationRecompute() {}

  static void diagnose(
      String stage,
      Observation target,
      long trigger,
      long revision,
      ServiceContextScope scope,
      OptionalLong instant,
      boolean initial,
      Throwable failure) {
    if (!Boolean.getBoolean("klab.diagnostics.configurationRecompute")) return;
    try {
      var data = new LinkedHashMap<String, Object>();
      data.put("stage", stage);
      data.put("target", target == null ? null : target.getId());
      data.put(
          "meaning",
          target == null || target.getObservable() == null
              ? null
              : target.getObservable().getUrn());
      data.put("trigger", trigger);
      data.put("revision", revision);
      data.put("initial", initial);
      data.put("modeledInstant", instant.isPresent() ? instant.getAsLong() : null);
      data.put("context", scope.getId());
      var buffered = scope.getConsequenceFailure();
      var warning = scope.getConsequenceWarning();
      data.put(
          "bufferedFailure",
          buffered == null ? null : buffered.replaceAll("https?://[^\\s]+", "[redacted-url]"));
      data.put(
          "bufferedWarning",
          warning == null ? null : warning.replaceAll("https?://[^\\s]+", "[redacted-url]"));
      data.put(
          "targetErrors",
          target == null
              ? List.of()
              : target.getNotifications().stream()
                  .filter(
                      n ->
                          n.getLevel()
                              == org.integratedmodelling.klab.api.services.runtime.Notification
                                  .Level.Error)
                  .limit(8)
                  .map(n -> n.getMessage().replaceAll("https?://[^\\s]+", "[redacted-url]"))
                  .toList());
      var tx = scope.getCurrentTransaction();
      data.put("transaction", tx == null ? null : System.identityHashCode(tx));
      data.put("transactionFailed", tx == null ? null : tx.isFailed());
      data.put(
          "activity", tx == null || tx.getActivity() == null ? null : tx.getActivity().getId());
      data.put(
          "cycle",
          scope.getResolutionCycleBinding() == null
              ? null
              : scope.getResolutionCycleBinding().cycleId());
      var chain = new ArrayList<Map<String, Object>>();
      var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
      for (var cause = failure;
          cause != null && chain.size() < 8 && seen.add(cause);
          cause = cause.getCause()) {
        var row = new LinkedHashMap<String, Object>();
        row.put("type", cause.getClass().getName());
        var message = cause.getMessage();
        row.put(
            "message",
            message == null
                ? null
                : message
                    .replaceAll("https?://[^\\s]+", "[redacted-url]")
                    .substring(
                        0,
                        Math.min(
                            500,
                            message.replaceAll("https?://[^\\s]+", "[redacted-url]").length())));
        row.put(
            "frames",
            Arrays.stream(cause.getStackTrace()).limit(12).map(Object::toString).toList());
        chain.add(row);
      }
      data.put("causes", chain);
      org.integratedmodelling.common.logging.Logging.INSTANCE.info(
          "CONFIGURATION_RECOMPUTE_DIAGNOSTIC "
              + org.integratedmodelling.klab.utilities.Utils.Json.asString(data));
    } catch (RuntimeException ignored) {
      /* Diagnostics cannot change execution or admission. */
    }
  }

  record InputChange(String id, OptionalLong instant) implements Scheduler.Event {
    public boolean hasTime() {
      return instant.isPresent();
    }

    public Time getTime() {
      if (!hasTime())
        throw new IllegalStateException("Current-state input change has no modeled time");
      return TimePeriod.create(instant.getAsLong(), instant.getAsLong());
    }

    public Type getType() {
      return Type.INPUT_CHANGE;
    }

    public String toKey() {
      return id;
    }

    public Observation getEvent() {
      return null;
    }
  }

  static Scheduler.ConfigurationRecomputeOutcome execute(
      SchedulerImpl scheduler,
      Observation configuration,
      Geometry support,
      long trigger,
      long revision,
      ServiceContextScope scope,
      OptionalLong instant,
      KnowledgeGraph graph) {
    return execute(
        scheduler,
        configuration,
        support,
        trigger,
        revision,
        scope,
        instant,
        graph,
        false,
        false,
        (geometry, event) -> {},
        null);
  }

  static Scheduler.ConfigurationRecomputeOutcome executePrepared(
      SchedulerImpl scheduler,
      Observation configuration,
      Geometry support,
      long trigger,
      long revision,
      ServiceContextScope scope,
      OptionalLong instant,
      KnowledgeGraph graph,
      boolean initial,
      java.util.function.BiConsumer<Geometry, Scheduler.Event> executionSupport,
      org.integratedmodelling.klab.api.lang.TriFunction<
              Geometry,
              Scheduler.Event,
              org.integratedmodelling.klab.api.scope.ContextScope,
              Boolean>
          preparedRoot) {
    return execute(
        scheduler,
        configuration,
        support,
        trigger,
        revision,
        scope,
        instant,
        graph,
        true,
        initial,
        Objects.requireNonNull(executionSupport),
        Objects.requireNonNull(preparedRoot));
  }

  private static Scheduler.ConfigurationRecomputeOutcome execute(
      SchedulerImpl scheduler,
      Observation configuration,
      Geometry support,
      long trigger,
      long revision,
      ServiceContextScope scope,
      OptionalLong instant,
      KnowledgeGraph graph,
      boolean prepared,
      boolean initial,
      java.util.function.BiConsumer<Geometry, Scheduler.Event> executionSupport,
      org.integratedmodelling.klab.api.lang.TriFunction<
              Geometry,
              Scheduler.Event,
              org.integratedmodelling.klab.api.scope.ContextScope,
              Boolean>
          preparedRoot) {
    var transaction = scope.getCurrentTransaction();
    diagnose("entry", configuration, trigger, revision, scope, instant, initial, null);
    if (configuration == null
        || configuration.getId() == Observation.UNASSIGNED_ID
        || support == null
        || trigger <= 0
        || revision <= 0
        || transaction == null
        || scope.getResolutionCycleBinding() == null)
      return Scheduler.ConfigurationRecomputeOutcome.DEFERRED;
    if (!graph.isObservationCurrent(configuration, scope))
      return Scheduler.ConfigurationRecomputeOutcome.DEFERRED;
    if (!configuration.getObservable().is(SemanticType.CONFIGURATION)
        || configuration.getMetadata().containsKey(OccurrenceRegistration.METADATA_KEY)) {
      diagnose(
          "invalid-configuration-or-occurrence",
          configuration,
          trigger,
          revision,
          scope,
          instant,
          initial,
          null);
      return Scheduler.ConfigurationRecomputeOutcome.FAILED;
    }
    String key = "configuration-input:" + configuration.getId() + ":" + revision + ":" + trigger;
    // Metadata and native state are staged until commit. Another transaction must not copy them,
    // even when it carries another trigger/revision for the same configuration.
    var configurationOwner = scheduler.configurationsInFlight.get(configuration.getId());
    if (configurationOwner != null
        && (configurationOwner != transaction || !scheduler.inputChangesInFlight.containsKey(key)))
      return Scheduler.ConfigurationRecomputeOutcome.DEFERRED;
    var pending = scheduler.inputChangesInFlight.get(key);
    if (pending != null)
      return pending == transaction
          ? Scheduler.ConfigurationRecomputeOutcome.ALREADY_EXECUTED
          : Scheduler.ConfigurationRecomputeOutcome.DEFERRED;
    var previous = configuration.getMetadata().get(RECEIPTS);
    var receipts = readReceipts(previous);
    if (receipts.containsKey(key)) return Scheduler.ConfigurationRecomputeOutcome.ALREADY_EXECUTED;
    if (transaction.getTemporalWrites() != null)
      return Scheduler.ConfigurationRecomputeOutcome.DEFERRED;
    String stage = "scope";
    try {
      var scoped = prepared ? scope : scope.of(configuration);
      if (initial) {
        // An unmarked legacy INIT is not proof that the prepared scientific model ran.
        if (!configuration.getEventTimestamps().isEmpty())
          return Scheduler.ConfigurationRecomputeOutcome.DEFERRED;
        var initialization = Scheduler.Event.initialization();
        stage = "initial-support";
        executionSupport.accept(support, initialization);
        stage = "initial-execution";
        if (!scheduler.executePreparedConfiguration(
            configuration, support, scoped, initialization, preparedRoot))
          throw new IllegalStateException("Configuration initial explanation failed");
        recordReceipt(
            scheduler,
            configuration,
            scope,
            previous,
            receipts,
            key,
            revision,
            trigger,
            OptionalLong.empty(),
            Scheduler.ConfigurationRecomputeOutcome.RESOLVED);
        return Scheduler.ConfigurationRecomputeOutcome.RESOLVED;
      }
      var executor = prepared ? preparedRoot : scheduler.recomputeExecutor(configuration, scoped);
      // Absence of a cached/restorable executor is not a resolver NO_MODEL certificate.
      if (executor == null) return Scheduler.ConfigurationRecomputeOutcome.DEFERRED;
      if (instant.isEmpty()
          && support.dimension(Geometry.Dimension.Type.TIME) != null
          && !org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE
              .scale(support)
              .getTime()
              .is(Time.Type.INITIALIZATION))
        return Scheduler.ConfigurationRecomputeOutcome.DEFERRED;
      var event = new InputChange(key, instant);
      var result = Scheduler.ConfigurationRecomputeOutcome.RESOLVED;
      stage = "input-change-write-set";
      var writes = new LocalTemporalWriteSet(event, scoped);
      if (executor != null) {
        stage = "input-change-localize";
        var geometry = TemporalGeometry.localize(support, event);
        stage = "input-change-support";
        executionSupport.accept(geometry, event);
        stage = "input-change-root";
        if (!(prepared
            ? scheduler.executePreparedConfiguration(
                configuration, geometry, scoped, event, executor)
            : executor.apply(geometry, event, scoped)))
          throw new IllegalStateException("Configuration explanation failed");
        stage = "dependent-closure";
        for (var dependent :
            ComputationalClosure.ordered(
                configuration, configuration.getParentId(), geometry, graph, scoped)) {
          var execution = scoped.of(dependent);
          var next = scheduler.recomputeExecutor(dependent, execution);
          if (next == null
              || !next.apply(
                  TemporalGeometry.localize(dependent.getGeometry(), event), event, execution))
            throw new IllegalStateException("Configuration-dependent explanation failed");
        }
      }
      stage = "prepare-writes";
      writes.prepare();
      stage = "record-receipt";
      recordReceipt(
          scheduler,
          configuration,
          scope,
          previous,
          receipts,
          key,
          revision,
          trigger,
          instant,
          result);
      return result;
    } catch (Throwable failure) {
      diagnose(stage, configuration, trigger, revision, scope, instant, initial, failure);
      transaction.fail(failure);
      scope.error(failure);
      return Scheduler.ConfigurationRecomputeOutcome.FAILED;
    }
  }

  private static void recordReceipt(
      SchedulerImpl scheduler,
      Observation configuration,
      ServiceContextScope scope,
      Object previous,
      Map<String, String> receipts,
      String key,
      long revision,
      long trigger,
      OptionalLong instant,
      Scheduler.ConfigurationRecomputeOutcome result) {
    var transaction = scope.getCurrentTransaction();
    long registrationId = configuration.getId();
    scheduler.inputChangesInFlight.put(key, transaction);
    scheduler.configurationsInFlight.put(registrationId, transaction);
    Runnable release =
        () -> {
          synchronized (scheduler) {
            scheduler.inputChangesInFlight.remove(key, transaction);
            scheduler.configurationsInFlight.remove(registrationId, transaction);
          }
        };
    transaction.afterCommit(release);
    transaction.afterRollback(release);
    receipts.put(key, result.name());
    configuration.getMetadata().put(RECEIPTS, encodeReceipts(receipts));
    transaction.afterRollback(
        () -> {
          if (previous == null) configuration.getMetadata().remove(RECEIPTS);
          else configuration.getMetadata().put(RECEIPTS, previous);
        });
    transaction.update(configuration);
    var evidence = new LinkedHashMap<String, Object>();
    evidence.put("version", 1);
    evidence.put("configuration", configuration.getId());
    evidence.put("revision", revision);
    evidence.put("triggeringCommit", trigger);
    instant.ifPresent(value -> evidence.put("modeledInstant", value));
    evidence.put("cycle", scope.getResolutionCycleBinding().cycleId());
    evidence.put("outcome", result.name());
    var activity = transaction.getActivity();
    var previousProvenance = activity.getMetadata().get(PROVENANCE);
    activity.getMetadata().put(PROVENANCE, org.integratedmodelling.klab.utilities.Utils.Json.asString(evidence));
    transaction.update(activity);
    // Native beforeCommit runs after allocation and before modified assets are encoded. Both
    // births and ordinary revisions must use the same durable key at the persistence boundary.
    transaction.beforeCommit(() -> {
      if (configuration.getId() != registrationId) {
        if (configuration.getId() <= 0)
          throw new IllegalStateException("Recomputed configuration was not allocated durably");
        receipts.remove(key);
        receipts.put("configuration-input:" + configuration.getId() + ":" + revision + ":" + trigger,
            result.name());
        configuration.getMetadata().put(RECEIPTS, encodeReceipts(receipts));
        evidence.put("configuration", configuration.getId());
        activity.getMetadata().put(PROVENANCE, org.integratedmodelling.klab.utilities.Utils.Json.asString(evidence));
      }
    });
    transaction.afterRollback(() -> {
      if (previousProvenance == null) activity.getMetadata().remove(PROVENANCE);
      else activity.getMetadata().put(PROVENANCE, previousProvenance);
    });
  }

  private static String encodeReceipts(Map<String, String> receipts) {
    return org.integratedmodelling.klab.utilities.Utils.Json.asString(
        Map.of("version", 1, "receipts", receipts));
  }

  /** Descriptive duplicate evidence, consulted only after native currentness/cycle admission. */
  static LinkedHashMap<String, String> readReceipts(Object value) {
    var receipts = new LinkedHashMap<String, String>();
    Object rows = value;
    if (value instanceof String encoded) {
      if (encoded.length() > 1_048_576) return receipts;
      try {
        var envelope = org.integratedmodelling.klab.utilities.Utils.Json.parseObject(encoded, Map.class);
        if (!(envelope.get("version") instanceof Number version) || version.intValue() != 1)
          return receipts;
        rows = envelope.get("receipts");
      } catch (RuntimeException invalid) { return receipts; }
    }
    // Bounded compatibility with the pre-versioned in-memory map. Unknown/malformed evidence
    // cannot certify a completed computation; it never relaxes native admission.
    if (rows instanceof Map<?, ?> map && map.size() <= 10_000)
      map.forEach((k, v) -> {
        if (k instanceof String key && key.matches("configuration-input:-?[0-9]+:[0-9]+:[0-9]+")
            && "RESOLVED".equals(v)) receipts.put(key, "RESOLVED");
      });
    return receipts;
  }
}
