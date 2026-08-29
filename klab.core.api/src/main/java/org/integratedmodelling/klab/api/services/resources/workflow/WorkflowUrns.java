package org.integratedmodelling.klab.api.services.resources.workflow;

import java.util.regex.Pattern;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;

/** Stable URN construction and parsing for workflow assets transported through Resources CRUD. */
public final class WorkflowUrns {

  private static final String ROOT = "urn:klab:";
  private static final Pattern COMPONENT = Pattern.compile("[A-Za-z0-9._~-]+");

  private WorkflowUrns() {}

  public record Coordinates(String ownerId, String version, String artifactId) {}

  public static String workflow(String id, String version) {
    return ROOT + "workflow:" + component(id) + "@" + component(version);
  }

  public static String workflowState(String workflowId, String version, String stateId) {
    return ROOT + "workflow-state:" + component(workflowId) + "@" + component(version) + ":" + component(stateId);
  }

  public static String workflowTransition(String workflowId, String version, String transitionId) {
    return ROOT + "workflow-transition:" + component(workflowId) + "@" + component(version) + ":" + component(transitionId);
  }

  public static String flow(String id) {
    return ROOT + "flow:" + component(id);
  }

  public static String flowState(String flowId, String stateId) {
    return ROOT + "flow-state:" + component(flowId) + ":" + component(stateId);
  }

  public static String flowTransition(String flowId, String transactionId) {
    return ROOT + "flow-transition:" + component(flowId) + ":" + component(transactionId);
  }

  public static String flowAttachment(String flowId, String attachmentId) {
    return ROOT + "flow-attachment:" + component(flowId) + ":" + component(attachmentId);
  }

  public static Coordinates parse(String urn, KlabAsset.KnowledgeClass knowledgeClass) {
    if (urn == null) throw invalid(urn);
    String prefix = ROOT + switch (knowledgeClass) {
      case WORKFLOW -> "workflow:";
      case WORKFLOW_STATE -> "workflow-state:";
      case WORKFLOW_TRANSITION -> "workflow-transition:";
      case FLOW -> "flow:";
      case FLOW_STATE -> "flow-state:";
      case FLOW_TRANSITION -> "flow-transition:";
      case FLOW_ATTACHMENT -> "flow-attachment:";
      default -> throw new KlabIllegalArgumentException(knowledgeClass + " is not a workflow asset class");
    };
    if (!urn.startsWith(prefix)) throw invalid(urn);
    String body = urn.substring(prefix.length());
    if (knowledgeClass == KlabAsset.KnowledgeClass.WORKFLOW) {
      int at = body.lastIndexOf('@');
      if (at < 1 || at == body.length() - 1) throw invalid(urn);
      return checked(body.substring(0, at), body.substring(at + 1), null, urn);
    }
    if (knowledgeClass == KlabAsset.KnowledgeClass.FLOW) {
      return checked(body, null, null, urn);
    }
    int separator = body.lastIndexOf(':');
    if (separator < 1 || separator == body.length() - 1) throw invalid(urn);
    String owner = body.substring(0, separator);
    String artifact = body.substring(separator + 1);
    if (knowledgeClass == KlabAsset.KnowledgeClass.WORKFLOW_STATE
        || knowledgeClass == KlabAsset.KnowledgeClass.WORKFLOW_TRANSITION) {
      int at = owner.lastIndexOf('@');
      if (at < 1 || at == owner.length() - 1) throw invalid(urn);
      return checked(owner.substring(0, at), owner.substring(at + 1), artifact, urn);
    }
    return checked(owner, null, artifact, urn);
  }

  private static Coordinates checked(String owner, String version, String artifact, String urn) {
    component(owner);
    if (version != null) component(version);
    if (artifact != null) component(artifact);
    return new Coordinates(owner, version, artifact);
  }

  private static String component(String value) {
    if (value == null || !COMPONENT.matcher(value).matches()) {
      throw new KlabIllegalArgumentException(
          "Workflow URN components must match " + COMPONENT.pattern() + ": " + value);
    }
    return value;
  }

  private static KlabIllegalArgumentException invalid(String urn) {
    return new KlabIllegalArgumentException("Invalid workflow asset URN " + urn);
  }
}
