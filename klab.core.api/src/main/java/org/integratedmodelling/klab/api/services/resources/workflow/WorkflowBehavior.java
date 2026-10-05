package org.integratedmodelling.klab.api.services.resources.workflow;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** Transport contracts for checkpointed workflow instrumentation and editor buttons. */
public final class WorkflowBehavior {
  private WorkflowBehavior() {}

  /** Ordered action binding. Parameters provide defaults; automatic context names are reserved. */
  public record Action(String id, String label, String action, Map<String, Object> parameters)
      implements Serializable {
    public Action { parameters = parameters == null ? Map.of() : parameters; }
  }

  public record Parameter(String name, String javaType, String behaviorType) implements Serializable {}

  public record AvailableAction(String id, String label, String action, List<Parameter> missing,
      long revision) implements Serializable {}

  public record ActionRequest(long expectedRevision, Map<String, Object> parameters)
      implements Serializable {
    public ActionRequest { parameters = parameters == null ? Map.of() : parameters; }
  }

  /** Server-owned portable globals. Never accepted from clients or exposed by flow projections. */
  public record Checkpoint(String behaviorUrn, String version, Map<String, Object> globals)
      implements Serializable {}
}
