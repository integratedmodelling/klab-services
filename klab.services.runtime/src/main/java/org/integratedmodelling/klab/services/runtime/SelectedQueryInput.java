package org.integratedmodelling.klab.services.runtime;

import java.util.Objects;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** A compiled selected-query input association. Portable binding bytes are not query authority. */
public final class SelectedQueryInput {
  public static final String DATA_KEY = "runtime.selected.query.input";

  public record Binding(int child, String alias, String observable, String support) {}

  private final DigitalTwinImpl.TransactionImpl owner;
  private final Cohort cohort;
  private final Observation consumer;
  private final Observation view;
  private final Actuator plan;
  private final Binding binding;

  private SelectedQueryInput(
      DigitalTwinImpl.TransactionImpl owner, Cohort cohort, Observation consumer,
      Observation view, Actuator plan, Binding binding) {
    this.owner = owner;
    this.cohort = cohort;
    this.consumer = consumer;
    this.view = view;
    this.plan = plan;
    this.binding = binding;
  }

  static SelectedQueryInput capture(
      DigitalTwinImpl.TransactionImpl owner, Cohort cohort, Observation consumer,
      Observation view, Actuator plan, int index, ServiceContextScope scope) {
    if (scope.getCurrentTransaction() != owner || owner.isFailed())
      throw new IllegalStateException("Selected input requires its active compiled transaction");
    var proof = SemanticExecutionEvidence.issuedForSelectedInput(view, scope);
    if (proof == null || !SemanticExecutionEvidence.verified(proof, scope))
      throw new IllegalStateException("Selected input requires an original issued query receipt");
    var binding = binding(plan, index);
    if (plan.getObservation() != consumer || plan.getChildren().get(index).getObservation() != view)
      throw new IllegalStateException("Selected input differs from its compiled child");
    var cohortId = view.getMetadata().get(Metadata.IM_QUERY_COHORT_ID, Number.class);
    if (cohortId == null || cohortId.longValue() != cohort.getId())
      throw new IllegalStateException("Selected input belongs to another native cohort");
    return new SelectedQueryInput(owner, cohort, consumer, view, plan, binding);
  }

  /** Called only by transaction storage; callers cannot construct an issuance capability. */
  public String require(
      DigitalTwinImpl.TransactionImpl transaction, Cohort actualCohort,
      Observation actualConsumer, ServiceContextScope scope) {
    if (transaction != owner || scope.getCurrentTransaction() != owner || owner.isFailed()
        || actualCohort != cohort || actualConsumer != consumer)
      throw new IllegalStateException("Selected input association belongs to another transaction");
    if (plan.getObservation() != consumer || !binding.equals(binding(plan, binding.child()))
        || plan.getChildren().get(binding.child()).getObservation() != view)
      throw new IllegalStateException("Selected input compiled port changed before storage");
    var proof = SemanticExecutionEvidence.issuedForSelectedInput(view, scope);
    if (proof == null || !SemanticExecutionEvidence.verified(proof, scope))
      throw new IllegalStateException("Selected input receipt changed before storage");
    return Utils.Json.asString(binding);
  }

  /** Structural scheduler check only. Execution must separately revalidate the query receipt. */
  public static Observation requireBinding(Object encoded, Actuator plan) {
    if (!(encoded instanceof String text))
      throw new IllegalStateException("Missing selected-query input binding");
    var expected = Utils.Json.parseObject(text, Binding.class);
    if (expected == null || !expected.equals(binding(plan, expected.child())))
      throw new IllegalStateException("Selected-query edge differs from its compiled input");
    return plan.getChildren().get(expected.child()).getObservation();
  }

  private static Binding binding(Actuator plan, int index) {
    if (index < 0 || index >= plan.getChildren().size())
      throw new IllegalStateException("Selected-query child is absent");
    var child = plan.getChildren().get(index);
    var view = child.getObservation();
    if (child.getActuatorType() != Actuator.Type.REFERENCE || view == null
        || view.getId() != Observation.QUERY_ID || view.getObservable() == null
        || !view.getObservable().getSemantics().isCollective()
        || !view.getMetadata().containsKey(CollectiveSelectionView.DATA_KEY))
      throw new IllegalStateException("Selected-query association requires a selected reference");
    var selected = Utils.Json.parseObject(
        view.getMetadata().get(CollectiveSelectionView.DATA_KEY).toString(), CollectiveSelectionView.class);
    if (child.getName() == null || plan.getChildren().stream()
        .filter(candidate -> Objects.equals(candidate.getName(), child.getName())).count() != 1)
      throw new IllegalStateException("Selected input requires an unambiguous alias");
    if (selected == null || selected.geometry() == null
        || !Objects.equals(selected.requested(), view.getObservable().getUrn()))
      throw new IllegalStateException("Selected-query declaration differs from its receipt");
    return new Binding(index, child.getName(), view.getObservable().getUrn(), selected.geometry());
  }

  static void requireCurrent(RuntimeService runtime, Observation original, ServiceContextScope scope) {
    var evidence = SemanticExecutionEvidence.issuedForSelectedInput(original, scope);
    if (evidence != null && SemanticExecutionEvidence.verified(evidence, scope)) return;
    // A retained/transported reference is read-only revalidated, never treated as issuance.
    var checked = CollectiveSelectionExecution.query(runtime, Observation.forTransport(original), scope);
    if (checked == null || checked.isEmpty()
        || !SemanticExecutionEvidence.sameCheckedPayload(original, checked))
      throw new IllegalStateException("Selected input no longer matches its authorized query");
    evidence = SemanticExecutionEvidence.issuedForConsequence(checked, scope);
    if (evidence == null || !SemanticExecutionEvidence.verified(evidence, scope))
      throw new IllegalStateException("Selected input revalidation did not issue complete evidence");
    SemanticExecutionEvidence.issue(original, evidence, scope);
  }
}
