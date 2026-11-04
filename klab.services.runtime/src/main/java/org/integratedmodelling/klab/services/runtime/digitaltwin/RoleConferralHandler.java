package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.ArrayList;
import java.util.Objects;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Born processes/relationships can confer roles independently of characterization availability. */
public final class RoleConferralHandler implements ConsequenceEngine.Handler {
  private final RoleConsequenceRuntime runtime;

  public RoleConferralHandler(RoleConsequenceRuntime runtime) {
    this.runtime = Objects.requireNonNull(runtime);
  }

  @Override
  public ConsequenceEngine.Result evaluate(
      Consequence.Cause cause, KnowledgeGraph.Commit changes, ServiceContextScope scope)
      throws Exception {
    var evidence = new ArrayList<Long>();
    var diagnostics = new ArrayList<String>();
    int failed = 0, deferred = 0, applied = 0;
    for (var id : changes.getAddedObservations().stream().sorted().toList()) {
      cancelled(scope);
      var source = scope.getObservation(id);
      if (source == null || source.isEmpty() || source.getObservable() == null) {
        deferred++;
        diagnostics.add(location(id, "rules", null) + ": source unavailable");
        continue;
      }
      if (!source.getObservable().is(SemanticType.PROCESS)
          && !source.getObservable().is(SemanticType.RELATIONSHIP)) continue;
      final RoleConsequenceRuntime.Rules rules;
      try {
        rules = runtime.conferrals(source, scope);
      } catch (InterruptedException cancellation) {
        throw cancellation;
      } catch (Exception failure) {
        cancelled(scope);
        failed++;
        failure(scope, diagnostics, id, "rules", null, failure);
        continue;
      }
      if (rules.status() != ConsequenceRecipients.Status.COMPLETE) {
        if (rules.status() == ConsequenceRecipients.Status.FAILED) failed++;
        else deferred++;
        diagnostics.add(
            location(id, "rules", null) + ": " + rules.status() + " (" + rules.reason() + ")");
        continue;
      }
      for (var rule : rules.values()) {
        cancelled(scope);
        String stage = "recipient";
        try {
          var recipients =
              ConsequenceRecipients.select(
                  source,
                  rule.selector(),
                  rule.recipient(),
                  scope,
                  (expression, execution) -> runtime.resolveRecipients(source, rule, execution));
          if (recipients.status() != ConsequenceRecipients.Status.COMPLETE) {
            if (recipients.status() == ConsequenceRecipients.Status.FAILED) failed++;
            else deferred++;
            diagnostics.add(
                location(id, stage, rule.id())
                    + ": "
                    + recipients.status()
                    + " ("
                    + recipients.reason()
                    + ")");
            continue;
          }
          if (recipients.recipients().isEmpty()) {
            diagnostics.add(
                location(id, stage, rule.id()) + ": empty (" + recipients.reason() + ")");
            continue;
          }
          stage = "attribution";
          var effect = runtime.attributeSelected(cause, source, rule, recipients, scope);
          diagnostics.add(
              location(id, stage, rule.id())
                  + ": "
                  + effect.outcome()
                  + "/"
                  + effect.resolution()
                  + " ("
                  + effect.reason()
                  + ")");
          evidence.addAll(effect.observations());
          switch (effect.outcome()) {
            case APPLIED -> applied++;
            case DEFERRED -> deferred++;
            case FAILED -> failed++;
            default -> {}
          }
        } catch (InterruptedException cancellation) {
          throw cancellation;
        } catch (Exception failure) {
          cancelled(scope);
          failed++;
          failure(scope, diagnostics, id, stage, rule.id(), failure);
        }
        // The resulting role commit queues its own pass. Its implication handler, not this
        // source pass, owns implications followed by optional characterization.
      }
    }
    return new ConsequenceEngine.Result(
        outcome(applied, deferred, failed),
        "Role conferral: "
            + applied
            + " applied, "
            + deferred
            + " deferred, "
            + failed
            + " failed"
            + (diagnostics.isEmpty() ? "" : "; " + String.join("; ", diagnostics)),
        evidence.stream().distinct().toList());
  }

  private static String location(long source, String stage, String rule) {
    return "source=" + source + " stage=" + stage + (rule == null ? "" : " rule=" + rule);
  }

  private static void failure(
      ServiceContextScope scope,
      ArrayList<String> diagnostics,
      long source,
      String stage,
      String rule,
      Exception failure) {
    var location = location(source, stage, rule);
    // Sanitize before scope warning listeners as well as logging; retain stack/cause types.
    // Authored outcome reasons remain separate from arbitrary exception messages.
    diagnostics.add(location + ": FAILED (" + failure.getClass().getSimpleName() + ")");
    // Consequence scopes retain only the warning's short text. Preserve the safe frames
    // directly as well, without turning an optional failure into a scope error.
    try {
      org.integratedmodelling.common.logging.Logging.INSTANCE.error(
          org.integratedmodelling.common.logging.Logging.safeFailure(failure),
          "Role conferral failed: " + location);
    } catch (RuntimeException ignored) {
      /* Diagnostic writers cannot stop other conferrals. */
    }
    try {
      scope.warn(
          "Role conferral failed: " + location,
          org.integratedmodelling.common.logging.Logging.safeFailure(failure));
    } catch (RuntimeException ignored) {
      /* Diagnostic listeners cannot stop other conferrals. */
    }
  }

  static Consequence.Outcome outcome(int applied, int deferred, int failed) {
    return failed > 0
        ? Consequence.Outcome.FAILED
        : deferred > 0
            ? Consequence.Outcome.DEFERRED
            : applied > 0 ? Consequence.Outcome.APPLIED : Consequence.Outcome.NO_ACTION;
  }

  static void cancelled(ServiceContextScope scope) throws InterruptedException {
    if (scope.isInterrupted() || Thread.currentThread().isInterrupted())
      throw new InterruptedException("Role consequence cancelled");
  }
}
