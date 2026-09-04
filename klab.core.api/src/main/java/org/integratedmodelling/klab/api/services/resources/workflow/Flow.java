package org.integratedmodelling.klab.api.services.resources.workflow;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.FlowImpl;

/** Persistent active or closed instance of a {@link Workflow}. */
public interface Flow extends KlabAsset {

  /** Create an empty client-side flow bean, suitable for a provisional first stage. */
  static Flow create() {
    return new FlowImpl();
  }

  enum Status {
    ACTIVE,
    CLOSED
  }

  enum StateStatus {
    OPEN,
    CLOSED
  }

  interface Attachment extends KlabAsset {
    String getId();

    void setId(String id);

    String getFlowId();

    void setFlowId(String flowId);

    String getStateId();

    void setStateId(String stateId);

    String getType();

    void setType(String type);

    String getFileName();

    void setFileName(String fileName);

    String getMediaType();

    void setMediaType(String mediaType);

    KlabAsset.KnowledgeClass getAssetType();

    void setAssetType(KlabAsset.KnowledgeClass assetType);

    long getSize();

    void setSize(long size);

    void setServiceId(String serviceId);

    String getChecksum();

    void setChecksum(String checksum);

    String getCreatedBy();

    void setCreatedBy(String createdBy);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    void setMetadata(Map<String, Object> metadata);
  }

  interface State extends KlabAsset {
    String getId();

    void setId(String id);

    String getFlowId();

    void setFlowId(String flowId);

    String getSchemaId();

    void setSchemaId(String schemaId);

    String getAssetUrn();

    void setAssetUrn(String assetUrn);

    KlabAsset.KnowledgeClass getAssetType();

    void setAssetType(KlabAsset.KnowledgeClass assetType);

    String getPermissionsOwnerUrn();

    void setPermissionsOwnerUrn(String permissionsOwnerUrn);

    String getOwner();

    void setOwner(String owner);

    String getTitle();

    void setTitle(String title);

    String getDescription();

    void setDescription(String description);

    StateStatus getStatus();

    void setStatus(StateStatus status);

    Set<String> getAssignees();

    void setAssignees(Set<String> assignees);

    List<Attachment> getAttachments();

    void setAttachments(List<Attachment> attachments);

    void setMetadata(Map<String, Object> metadata);

    Instant getCreatedAt();

    void setCreatedAt(Instant createdAt);

    Instant getUpdatedAt();

    void setUpdatedAt(Instant updatedAt);

    void setServiceId(String serviceId);

    static Flow.State create() {
      return new FlowImpl.StateImpl();
    }
  }

  interface Transaction extends KlabAsset {
    String getId();

    void setId(String id);

    String getFlowId();

    void setFlowId(String flowId);

    String getTransitionId();

    void setTransitionId(String transitionId);

    String getSourceStateId();

    void setSourceStateId(String sourceStateId);

    String getTargetStateId();

    void setTargetStateId(String targetStateId);

    String getActor();

    void setActor(String actor);

    Instant getTimestamp();

    void setTimestamp(Instant timestamp);

    void setMetadata(Map<String, Object> metadata);

    void setServiceId(String serviceId);
  }

  interface TransitionRequest extends Serializable {
    String getTransactionId();

    void setTransactionId(String transactionId);

    String getTransitionId();

    void setTransitionId(String transitionId);

    String getSourceStateId();

    void setSourceStateId(String sourceStateId);

    State getTargetState();

    void setTargetState(State targetState);

    long getExpectedRevision();

    void setExpectedRevision(long expectedRevision);

    Map<String, Object> getMetadata();

    void setMetadata(Map<String, Object> metadata);

    static TransitionRequest create() {
      return new FlowImpl.TransitionRequestImpl();
    }
  }

  interface AttachmentUpload extends Serializable {
    String getType();

    void setType(String type);

    String getFileName();

    void setFileName(String fileName);

    String getMediaType();

    void setMediaType(String mediaType);

    KlabAsset.KnowledgeClass getAssetType();

    void setAssetType(KlabAsset.KnowledgeClass assetType);

    byte[] getContent();

    void setContent(byte[] content);

    static AttachmentUpload create() {
      return new FlowImpl.AttachmentUploadImpl();
    }
  }

  /**
   * Atomic first-stage submission. The flow does not exist persistently until the initial state,
   * its attachments, and the requested transition have all passed validation.
   */
  interface InitializationRequest extends Serializable {
    State getInitialState();

    void setInitialState(State initialState);

    List<AttachmentUpload> getAttachments();

    void setAttachments(List<AttachmentUpload> attachments);

    TransitionRequest getTransition();

    void setTransition(TransitionRequest transition);

    boolean isPublicRead();

    void setPublicRead(boolean publicRead);

    static InitializationRequest create() {
      return new FlowImpl.InitializationRequestImpl();
    }
  }

  String getId();

  void setId(String id);

  String getWorkflowId();

  void setWorkflowId(String workflowId);

  String getWorkflowVersion();

  void setWorkflowVersion(String workflowVersion);

  String getAssetUrn();

  void setAssetUrn(String assetUrn);

  KlabAsset.KnowledgeClass getAssetType();

  void setAssetType(KlabAsset.KnowledgeClass assetType);

  String getPermissionsOwnerUrn();

  void setPermissionsOwnerUrn(String permissionsOwnerUrn);

  String getOwner();

  void setOwner(String owner);

  boolean isPublicRead();

  void setPublicRead(boolean publicRead);

  Status getStatus();

  void setStatus(Status status);

  long getRevision();

  void setRevision(long revision);

  Instant getCreatedAt();

  void setCreatedAt(Instant createdAt);

  Instant getUpdatedAt();

  void setUpdatedAt(Instant updatedAt);

  Map<String, State> getStates();

  void setStates(Map<String, State> states);

  Set<String> getCurrentStateIds();

  void setCurrentStateIds(Set<String> stateIds);

  List<Transaction> getHistory();

  void setHistory(List<Transaction> history);

  void setMetadata(Map<String, Object> metadata);

  void setServiceId(String serviceId);
}
