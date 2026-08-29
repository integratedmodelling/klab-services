package org.integratedmodelling.klab.resources;

import java.util.List;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;

/**
 * Storage port for workflow aggregates and attachment payloads.
 *
 * <p>The bundled implementation is {@link ResourcesKBox}; production deployments may bind an
 * implementation backed by MongoDB without changing workflow or transport code.
 */
public interface WorkflowStore {
  boolean putWorkflow(Workflow workflow);
  Workflow getWorkflow(String id);
  Workflow getWorkflow(String id, String version);
  List<Workflow> listWorkflows();
  boolean putFlow(Flow flow);
  Flow getFlow(String id);
  List<Flow> listFlows();
  boolean putWorkflowAttachment(String id, String flowId, String stateId, byte[] content);
  byte[] getWorkflowAttachment(String id);
  boolean deleteWorkflowAttachment(String id);
}
