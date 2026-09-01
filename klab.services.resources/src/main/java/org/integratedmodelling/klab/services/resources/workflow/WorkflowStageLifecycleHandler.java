package org.integratedmodelling.klab.services.resources.workflow;

import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;

/**
 * Service extension point for actions associated with the lifecycle of a workflow stage.
 *
 * <p>The current workflow API represents a stage as {@link Flow.State}. Implementations should
 * treat the supplied flow and stage as read-only lifecycle context and perform changes through
 * service APIs. Callbacks run synchronously within the lifecycle operation and should not retain
 * the context. The callback contract is deliberately independent of the implementation mechanism
 * so a handler can initially contain service code and later delegate to behavior loaded from
 * k.Actors resources.
 */
public interface WorkflowStageLifecycleHandler {

  WorkflowStageLifecycleHandler NO_OP = new WorkflowStageLifecycleHandler() {};

  /**
   * Called after the aggregate containing the new stage has been persisted and its resource-catalog
   * projection has been synchronized.
   *
   * <p>An exception is reported by the manager but cannot roll back or invalidate the committed
   * stage creation.
   */
  default void afterStageCreated(Context context) throws Exception {}

  /**
   * Called after deletion has been authorized and validated, but before attachments or aggregate
   * state are removed.
   *
   * <p>An exception aborts deletion and leaves the stage and its attachments unchanged.
   */
  default void beforeStageDeleted(Context context) throws Exception {}

  /** Immutable callback envelope. The contained API objects must be treated as read-only. */
  record Context(
      Flow flow,
      Flow.State stage,
      Workflow workflow,
      Workflow.StateSchema stageSchema,
      UserScope scope) {}
}
