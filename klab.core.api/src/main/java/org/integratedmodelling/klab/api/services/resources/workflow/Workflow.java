package org.integratedmodelling.klab.api.services.resources.workflow;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.UserScope;

/**
 * Versioned configuration contract for one kind of workflow.
 *
 * <p>Implementations are transport beans supplied by {@code klab.core.common}. The interface stays
 * in the public API so resources services and external clients share the normal k.LAB polymorphic
 * serialization convention.
 */
public interface Workflow extends KlabAsset {

  String INIT = "INIT";

  interface AttachmentRule extends Serializable {
    String getType();

    void setType(String type);

    String getMediaType();

    void setMediaType(String mediaType);

    KlabAsset.KnowledgeClass getAssetType();

    void setAssetType(KlabAsset.KnowledgeClass assetType);

    int getArity();

    void setArity(int arity);
  }

  interface StateSchema extends KlabAsset {
    String getId();

    void setId(String id);

    String getDescription();

    void setDescription(String description);

    String getCompletionCriteria();

    void setCompletionCriteria(String completionCriteria);

    void setServiceId(String serviceId);

    String getInstructions();

    void setInstructions(String instructions);

    Set<WorkflowRole> getManagerRoles();

    void setManagerRoles(Set<WorkflowRole> roles);

    Set<WorkflowRole> getContributorRoles();

    void setContributorRoles(Set<WorkflowRole> roles);

    Set<String> getAdmittedGroups();

    void setAdmittedGroups(Set<String> groups);

    List<AttachmentRule> getAttachments();

    void setAttachments(List<AttachmentRule> attachments);

    Set<KlabAsset.KnowledgeClass> getAssetTypes();

    void setAssetTypes(Set<KlabAsset.KnowledgeClass> assetTypes);

    boolean isOpen();

    void setOpen(boolean open);

    void setMetadata(Map<String, Object> metadata);

    String getWorkflowId();

    void setWorkflowId(String workflowId);

    String getWorkflowVersion();

    void setWorkflowVersion(String workflowVersion);
  }

  interface TransitionSchema extends KlabAsset {
    String getId();

    void setId(String id);

    void setServiceId(String serviceId);

    String getDescription();

    void setDescription(String description);

    Set<String> getSourceStates();

    void setSourceStates(Set<String> sourceStates);

    String getTargetState();

    void setTargetState(String targetState);

    Set<WorkflowRole> getRoles();

    void setRoles(Set<WorkflowRole> roles);

    Set<KlabAsset.KnowledgeClass> getSourceAssetTypes();

    void setSourceAssetTypes(Set<KlabAsset.KnowledgeClass> assetTypes);

    Set<String> getSourceMediaTypes();

    void setSourceMediaTypes(Set<String> mediaTypes);

    void setMetadata(Map<String, Object> metadata);

    String getWorkflowId();

    void setWorkflowId(String workflowId);

    String getWorkflowVersion();

    void setWorkflowVersion(String workflowVersion);
  }

  List<String> validate();

  List<TransitionSchema> admittedTransitions(
      Flow.State state, WorkflowParticipant participant);

  List<TransitionSchema> admittedTransitions(Flow flow, String stateId, UserScope scope);

  List<String> validateTransition(
      Flow flow, String stateId, String transitionId, WorkflowParticipant participant);

  boolean canAccess(StateSchema state, WorkflowParticipant participant);

  String getId();

  void setId(String id);

  String getVersion();

  void setVersion(String version);

  String getName();

  void setName(String name);

  String getDescription();

  void setDescription(String description);

  Map<String, StateSchema> getStates();

  void setStates(Map<String, StateSchema> states);

  Map<String, TransitionSchema> getTransitions();

  void setTransitions(Map<String, TransitionSchema> transitions);

  void setServiceId(String serviceId);

  void setMetadata(Map<String, Object> metadata);
}
