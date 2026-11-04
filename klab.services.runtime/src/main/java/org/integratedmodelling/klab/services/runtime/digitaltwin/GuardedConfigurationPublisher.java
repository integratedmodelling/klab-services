package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/**
 * Concrete atomic publisher. Semantic admission runs before native locking; source Backend owns
 * exact retained rule/model proof checks. Actual recomputation and revision publication share the
 * normal DT transaction. Missing executors are deferred, never invented no-model evidence.
 */
public final class GuardedConfigurationPublisher {
  @FunctionalInterface
  public interface Admission {
    void admit(ConfigurationConsequenceRuntime.Update update, ServiceContextScope scope);
  }

  @FunctionalInterface
  public interface BirthAdmission {
    void admit(Birth birth, ServiceContextScope scope);
  }

  public record Birth(
      ObservationImpl observation,
      RuntimeAsset placement,
      List<Long> participants,
      List<KnowledgeGraph.ConsequenceReadSet> reads,
      List<? extends ConfigurationConsequenceRuntime.AuthorityReceipt> authorities,
      String lineageJson,
      List<KnowledgeGraph.ContextCohortCatalogue> catalogues) {
    public Birth(
        ObservationImpl observation,
        RuntimeAsset placement,
        List<Long> participants,
        List<KnowledgeGraph.ConsequenceReadSet> reads,
        List<? extends ConfigurationConsequenceRuntime.AuthorityReceipt> authorities,
        String lineageJson) {
      this(observation, placement, participants, reads, authorities, lineageJson, List.of());
    }

    public Birth {
      Objects.requireNonNull(observation);
      Objects.requireNonNull(placement);
      participants = List.copyOf(participants);
      reads = List.copyOf(reads);
      authorities = List.copyOf(authorities);
      catalogues = List.copyOf(catalogues);
      Objects.requireNonNull(lineageJson);
      if (authorities.isEmpty() || reads.isEmpty())
        throw new IllegalArgumentException("Configuration birth requires source/native authority");
      if (!(placement instanceof Cohort) && !(placement instanceof Observation))
        throw new IllegalArgumentException(
            "Configuration requires a real catalogue or substantial bearer");
    }
  }

  private final Admission admission;
  private final BirthAdmission birthAdmission;

  public enum Preparation {
    READY,
    NO_MODEL,
    DEFERRED
  }

  public record Prepared(
      Preparation status,
      org.integratedmodelling.klab.api.geometry.Geometry support,
      Object receipt) {
    public Prepared(
        Preparation status, org.integratedmodelling.klab.api.geometry.Geometry support) {
      this(status, support, null);
    }

    public Prepared {
      Objects.requireNonNull(status);
    }
  }

  @FunctionalInterface
  public interface Explanation {
    Prepared prepare(Observation observation, ServiceContextScope execution) throws Exception;
  }

  @FunctionalInterface
  public interface Completion {
    void complete(
        DigitalTwinImpl.TransactionImpl transaction,
        List<Observation> configurations,
        List<Prepared> preparations,
        List<ConfigurationConsequenceRuntime.Update> updates,
        List<Birth> births,
        ServiceContextScope execution)
        throws Exception;
  }

  private final Explanation explanation;
  private final Completion completion;

  public GuardedConfigurationPublisher(Admission admission, BirthAdmission birthAdmission) {
    this.admission = Objects.requireNonNull(admission);
    this.birthAdmission = Objects.requireNonNull(birthAdmission);
    this.explanation =
        (observation, scope) -> new Prepared(Preparation.READY, observation.getGeometry());
    this.completion = (tx, outputs, prepared, updates, births, scope) -> {};
  }

  public GuardedConfigurationPublisher(
      Admission admission, BirthAdmission birthAdmission, Explanation explanation) {
    this.admission = Objects.requireNonNull(admission);
    this.birthAdmission = Objects.requireNonNull(birthAdmission);
    this.explanation = Objects.requireNonNull(explanation);
    this.completion = (tx, outputs, prepared, updates, births, scope) -> {};
  }

  public GuardedConfigurationPublisher(
      Admission admission,
      BirthAdmission birthAdmission,
      Explanation explanation,
      Completion completion) {
    this.admission = Objects.requireNonNull(admission);
    this.birthAdmission = Objects.requireNonNull(birthAdmission);
    this.explanation = Objects.requireNonNull(explanation);
    this.completion = Objects.requireNonNull(completion);
  }

  public ConfigurationConsequenceRuntime.Effect publish(
      ConfigurationConsequenceRuntime.Update update,
      Consequence.Cause cause,
      ServiceContextScope scope)
      throws Exception {
    return publishBatch(List.of(update), List.of(), cause, scope);
  }

  /**
   * Whole local partition publication avoids treating earlier writes in this same batch as foreign
   * changes to a captured empty catalogue. All plans are staged before one guarded root commit.
   */
  public ConfigurationConsequenceRuntime.Effect publishBatch(
      List<ConfigurationConsequenceRuntime.Update> updates,
      List<Birth> births,
      Consequence.Cause cause,
      ServiceContextScope scope)
      throws Exception {
    if (updates.isEmpty() && births.isEmpty())
      return new ConfigurationConsequenceRuntime.Effect(
          Consequence.Outcome.NO_ACTION, List.of(), "No configuration changes");
    if (cause.kind() != Consequence.Kind.CONFIGURATION_DETECTION
        || !Objects.equals(scope.getId(), cause.contextId()))
      throw new IllegalArgumentException("Configuration publication cause/context mismatch");
    // Use the normal activity scope factory: opening a transaction directly only registers it;
    // explanation preparation must see that exact transaction as its current execution root.
    var nested = scope.forConsequence(cause);
    if (nested.isInterrupted()) throw new InterruptedException("Configuration publication cancelled");
    var execution = nested.executing(Activity.of(Activity.Type.DETECTION));
    var transaction = execution.getCurrentTransaction();
    if (!(transaction instanceof DigitalTwinImpl.TransactionImpl nativeTransaction)) {
      var failure = new IllegalStateException("Guarded configuration transaction unavailable");
      try {
        execution.fail(failure);
      } catch (Exception completionFailure) {
        if (completionFailure != failure) failure.addSuppressed(completionFailure);
      }
      throw failure;
    }
    boolean terminalAttempted = false;
    try {
      var ids = new HashSet<Long>();
      for (var update : updates)
        if (!ids.add(update.previous().observationId()))
          throw new IllegalArgumentException(
              "Configuration update must be coalesced before publication");
      for (var update : updates) {
        admission.admit(update, execution);
        for (var catalogue : update.catalogues())
          nativeTransaction.guardContextCohortCatalogue(catalogue);
        for (var read : update.reads()) nativeTransaction.guardConsequenceBaseline(read, execution);
      }
      for (var birth : births) {
        birthAdmission.admit(birth, execution);
        for (var catalogue : birth.catalogues())
          nativeTransaction.guardContextCohortCatalogue(catalogue);
        for (var read : birth.reads()) nativeTransaction.guardConsequenceBaseline(read, execution);
      }
      var changed = new ArrayList<Observation>();
      for (var update : updates)
        changed.add(
            nativeTransaction.stageConfiguration(
                update, execution, () -> admission.admit(update, execution)));
      for (var birth : births)
        changed.add(
            nativeTransaction.stageConfigurationBirth(
                birth, execution, () -> birthAdmission.admit(birth, execution)));
      var scheduler = execution.getDigitalTwin().getScheduler();
      var preparations = new ArrayList<Prepared>();
      for (int n = 0; n < changed.size(); n++) {
        var observation = changed.get(n);
        long revision = n < updates.size() ? updates.get(n).next().number() : 1;
        var support = observation.getGeometry();
        var prepared = explanation.prepare(observation, execution);
        preparations.add(prepared);
        if (prepared.status() == Preparation.DEFERRED) {
          terminalAttempted = true;
          execution.fail(
              new IllegalStateException("Configuration explanatory model is unavailable"));
          return new ConfigurationConsequenceRuntime.Effect(
              Consequence.Outcome.DEFERRED,
              List.of(),
              "Configuration explanation admission deferred");
        }
        if (prepared.status() == Preparation.NO_MODEL) continue;
        support =
            Objects.requireNonNull(prepared.support(), "Prepared model actual support required");
        var outcome =
            prepared.receipt() == null
                ? scheduler.recomputeConfiguration(
                    observation, support, cause.triggeringCommitId(), revision, execution)
                : scheduler.recomputeConfiguration(
                    observation,
                    support,
                    cause.triggeringCommitId(),
                    revision,
                    execution,
                    prepared.receipt());
        if (outcome != Scheduler.ConfigurationRecomputeOutcome.RESOLVED
            && outcome != Scheduler.ConfigurationRecomputeOutcome.ALREADY_EXECUTED) {
          terminalAttempted = true;
          execution.fail(new IllegalStateException("Configuration explanation " + outcome));
          return new ConfigurationConsequenceRuntime.Effect(
              outcome == Scheduler.ConfigurationRecomputeOutcome.FAILED
                  ? Consequence.Outcome.FAILED
                  : Consequence.Outcome.DEFERRED,
              List.of(),
              "Configuration revision was not published: explanation " + outcome);
        }
      }
      completion.complete(
          nativeTransaction,
          List.copyOf(changed),
          List.copyOf(preparations),
          List.copyOf(updates),
          List.copyOf(births),
          execution);
      terminalAttempted = true;
      long committed = execution.commit();
      if (committed <= 0)
        throw new IllegalStateException("Configuration atomic publication failed");
      return new ConfigurationConsequenceRuntime.Effect(
          Consequence.Outcome.APPLIED,
          changed.stream().map(Observation::getId).toList(),
          "Configuration revisions and explanatory computations committed atomically");
    } catch (Exception failure) {
      try {
        if (!terminalAttempted) execution.fail(failure);
        else
          nativeTransaction.fail(
              failure); // Idempotent native cleanup cannot relabel a durable commit or send another
                        // terminal event.
      } catch (Exception completionFailure) {
        if (completionFailure != failure) failure.addSuppressed(completionFailure);
      }
      throw failure;
    }
  }
}
