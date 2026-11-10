package org.integratedmodelling.klab.runtime.language;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.LearnedResourceSubmission;
import org.integratedmodelling.klab.api.services.runtime.extension.Learning;
import org.integratedmodelling.klab.api.services.runtime.extension.LearningContextualizer;

/**
 * In-process execution kernel for an admitted, prepared learning operation. The host authenticates
 * observation handles, admits source, prepares targets, resolves the observer and acquires a plan
 * before calling this kernel. Component selection and graph publication are host operations,
 * explicitly supplied here rather than executed through a sharded contextualization loop.
 */
public final class LearningExecution {
  private LearningExecution() {}

  public enum PreviewStatus { NOT_REQUESTED, COMPLETE, FAILED }

  public record Result(
      Resource resource, boolean commitActivityRecorded, PreviewStatus previewStatus,
      List<Observation> previews, List<Notification> diagnostics) {
    public Result {
      Objects.requireNonNull(resource);
      previews = List.copyOf(previews);
      diagnostics = List.copyOf(diagnostics);
    }
  }

  /**
   * Fit exactly once per call, commit before preview, and preserve commit success if graph recording
   * or preview generation fails. A host retrying after a commit must recover its Resource receipt
   * and retry those later steps rather than call this fitting kernel again. The preview callback
   * must publish atomically to the actual learning observer, preserving previous previews on failure
   * and rejecting replacement by an older version. A null preview explicitly disables inspection.
   */
  public static Result train(
      String resolverName, String attemptId, Model model, LearningPlan plan,
      LearningContextualizer learner, ContextScope scope,
      Function<LearnedResourceSubmission, Resource> commit,
      Consumer<Resource> recordCommit, Function<Resource, List<Observation>> preview) {
    if (!model.isLearningModel() || model.getObservables().isEmpty())
      throw new IllegalArgumentException("Training requires a learning model with outputs");
    if (learner.requiresTargets() && plan.targets().isEmpty())
      throw new IllegalArgumentException("Supervised learner requires resolved targets");
    Objects.requireNonNull(commit, "resolver commit");
    Objects.requireNonNull(recordCommit, "commit activity recording");
    var urn = Learning.resourceUrn(resolverName, model.getNamespace(), model.getLearnedResourceId());
    var builder = Resource.builder(urn).withType(model.getObservables().getFirst().getArtifactType());
    interrupted(scope);
    learner.learn(plan.predictors(), plan.targets(), builder, scope);
    interrupted(scope);
    if (!urn.equals(builder.getUrn()))
      throw new IllegalArgumentException("Learner changed the resource identity");
    // Keep source semantics independently from any differently described explicit evidence.
    builder.withMetadata("klab.learning.predictors", plan.predictors().stream().map(input ->
        java.util.Map.of("name", input.name(), "observation", input.observation().getUrn(),
            "parameters", input.parameters())).toList());
    builder.withMetadata("klab.learning.outputs", model.getObservables().stream().map(output ->
        java.util.Map.of("name", Learning.name(output), "observable", output.getUrn())).toList());
    builder.withMetadata("klab.learning.dependencies", model.getDependencies().stream().map(input ->
        java.util.Map.of("name", Learning.name(input), "observable", input.getUrn())).toList());
    builder.withMetadata("klab.learning.targets", plan.targets().stream().map(input ->
        java.util.Map.of("name", input.name(), "observation", input.observation().getUrn())).toList());
    var committed = Objects.requireNonNull(commit.apply(new LearnedResourceSubmission(
        attemptId, model.getNamespace(), model.getUrn(), model.getLearnedResourceId(), builder.build())),
        "resolver commit receipt");
    return complete(committed, recordCommit, preview);
  }

  /** Retry graph recording/preview from a recovered exact receipt without fitting or committing. */
  public static Result complete(Resource committed, Consumer<Resource> recordCommit,
      Function<Resource, List<Observation>> preview) {
    org.integratedmodelling.klab.api.services.runtime.extension.LearningCommitActivity.of(committed, null);
    Objects.requireNonNull(recordCommit, "commit activity recording");
    var diagnostics = new java.util.ArrayList<Notification>();
    boolean recorded = false;
    try {
      recordCommit.accept(committed);
      recorded = true;
    } catch (RuntimeException failure) {
      diagnostics.add(Notification.error("Resource committed; commit activity recording failed", failure));
    }
    var status = PreviewStatus.NOT_REQUESTED;
    List<Observation> previews = List.of();
    if (preview != null) {
      // A preview must reference a recorded commit activity; graph reconciliation comes first.
      if (!recorded) status = PreviewStatus.FAILED;
      else try {
        previews = List.copyOf(preview.apply(committed));
        status = PreviewStatus.COMPLETE;
      } catch (RuntimeException failure) {
        status = PreviewStatus.FAILED;
        diagnostics.add(Notification.error("Resource committed; preview generation failed", failure));
      }
    }
    return new Result(committed, recorded, status, previews, diagnostics);
  }

  private static void interrupted(ContextScope scope) {
    if (scope.isInterrupted()) throw new java.util.concurrent.CancellationException("Learning cancelled before commit");
  }
}
