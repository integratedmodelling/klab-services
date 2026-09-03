package org.integratedmodelling.common.services.resources.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowUrns;

/** Persistent active or closed instance of a {@link Workflow}. */
public class FlowImpl implements Flow {

  @SuppressWarnings("unchecked")
  public static Metadata metadata(Map<String, Object> value) {
    if (value instanceof Metadata metadata) return metadata;
    if (value != null) {
      Object delegate = value.get("delegate");
      return Metadata.create(
          (Map<String, Object>) (delegate instanceof Map<?, ?> nested ? nested : value));
    }
    return Metadata.create();
  }

  public static class AttachmentImpl implements Flow.Attachment {
    private String id;
    private String flowId;
    private String stateId;
    private String type;
    private String fileName;
    private String mediaType;
    private KlabAsset.KnowledgeClass assetType;
    private long size;
    private String checksum;
    private String createdBy;
    private Instant createdAt;
    private Metadata metadata = Metadata.create();
    private String serviceId;

    @Override
    public String getUrn() {
      return WorkflowUrns.flowAttachment(flowId, id);
    }

    @Override
    public Collection<Annotation> getAnnotations() {
      return List.of();
    }

    @Override
    public Metadata getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
      this.metadata = FlowImpl.metadata(metadata);
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getFlowId() {
      return flowId;
    }

    public void setFlowId(String flowId) {
      this.flowId = flowId;
    }

    public String getStateId() {
      return stateId;
    }

    public void setStateId(String stateId) {
      this.stateId = stateId;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getFileName() {
      return fileName;
    }

    public void setFileName(String fileName) {
      this.fileName = fileName;
    }

    public String getMediaType() {
      return mediaType;
    }

    public void setMediaType(String mediaType) {
      this.mediaType = mediaType;
    }

    public KlabAsset.KnowledgeClass getAssetType() {
      return assetType;
    }

    public void setAssetType(KlabAsset.KnowledgeClass assetType) {
      this.assetType = assetType;
    }

    public long getSize() {
      return size;
    }

    @Override
    public String getServiceId() {
      return serviceId;
    }

    public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
    }

    public void setSize(long size) {
      this.size = size;
    }

    public String getChecksum() {
      return checksum;
    }

    public void setChecksum(String checksum) {
      this.checksum = checksum;
    }

    public String getCreatedBy() {
      return createdBy;
    }

    public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }
  }

  public static class StateImpl implements Flow.State {
    private String id;
    private String flowId;
    private String schemaId;
    private String assetUrn;
    private KlabAsset.KnowledgeClass assetType;
    private String permissionsOwnerUrn;

    /** Identity that owns this stage and may edit it regardless of role-specific assignment. */
    private String owner;

    private String title;
    private Flow.StateStatus status = Flow.StateStatus.OPEN;
    private Set<String> assignees = new LinkedHashSet<>();
    private List<Flow.Attachment> attachments = new ArrayList<>();
    private Metadata metadata = Metadata.create();
    private Instant createdAt;
    private Instant updatedAt;
    private String serviceId;

    @Override
    public String getUrn() {
      return WorkflowUrns.flowState(flowId, id);
    }

    @Override
    public Collection<Annotation> getAnnotations() {
      return List.of();
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getFlowId() {
      return flowId;
    }

    public void setFlowId(String flowId) {
      this.flowId = flowId;
    }

    public String getSchemaId() {
      return schemaId;
    }

    public void setSchemaId(String schemaId) {
      this.schemaId = schemaId;
    }

    public String getAssetUrn() {
      return assetUrn;
    }

    public void setAssetUrn(String assetUrn) {
      this.assetUrn = assetUrn;
    }

    public KlabAsset.KnowledgeClass getAssetType() {
      return assetType;
    }

    public void setAssetType(KlabAsset.KnowledgeClass assetType) {
      this.assetType = assetType;
    }

    public String getPermissionsOwnerUrn() {
      return permissionsOwnerUrn;
    }

    public void setPermissionsOwnerUrn(String permissionsOwnerUrn) {
      this.permissionsOwnerUrn = permissionsOwnerUrn;
    }

    public String getOwner() {
      return owner;
    }

    public void setOwner(String owner) {
      this.owner = owner;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public Flow.StateStatus getStatus() {
      return status;
    }

    public void setStatus(Flow.StateStatus status) {
      this.status = status;
    }

    public Set<String> getAssignees() {
      return assignees;
    }

    public void setAssignees(Set<String> v) {
      assignees = v == null ? new LinkedHashSet<>() : v;
    }

    public List<Flow.Attachment> getAttachments() {
      return attachments;
    }

    public void setAttachments(List<Flow.Attachment> v) {
      attachments = v == null ? new ArrayList<>() : v;
    }

    @Override
    public Metadata getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, Object> v) {
      metadata = FlowImpl.metadata(v);
    }

    public Instant getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
      this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
    }

    @Override
    public String getServiceId() {
      return serviceId;
    }

    public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
    }
  }

  public static class TransactionImpl implements Flow.Transaction {
    private String id;
    private String flowId;
    private String transitionId;
    private String sourceStateId;
    private String targetStateId;
    private String actor;
    private Instant timestamp;
    private Metadata metadata = Metadata.create();
    private String serviceId;

    @Override
    public String getUrn() {
      return WorkflowUrns.flowTransition(flowId, id);
    }

    @Override
    public Collection<Annotation> getAnnotations() {
      return List.of();
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getFlowId() {
      return flowId;
    }

    public void setFlowId(String flowId) {
      this.flowId = flowId;
    }

    public String getTransitionId() {
      return transitionId;
    }

    public void setTransitionId(String transitionId) {
      this.transitionId = transitionId;
    }

    public String getSourceStateId() {
      return sourceStateId;
    }

    public void setSourceStateId(String sourceStateId) {
      this.sourceStateId = sourceStateId;
    }

    public String getTargetStateId() {
      return targetStateId;
    }

    public void setTargetStateId(String targetStateId) {
      this.targetStateId = targetStateId;
    }

    public String getActor() {
      return actor;
    }

    public void setActor(String actor) {
      this.actor = actor;
    }

    public Instant getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
      this.timestamp = timestamp;
    }

    @Override
    public Metadata getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, Object> v) {
      metadata = FlowImpl.metadata(v);
    }

    @Override
    public String getServiceId() {
      return serviceId;
    }

    public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
    }
  }

  public static class TransitionRequestImpl implements Flow.TransitionRequest {
    private String transactionId;
    private String transitionId;
    private String sourceStateId;
    private Flow.State targetState;
    private long expectedRevision = -1;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public String getTransactionId() {
      return transactionId;
    }

    public void setTransactionId(String transactionId) {
      this.transactionId = transactionId;
    }

    public String getTransitionId() {
      return transitionId;
    }

    public void setTransitionId(String transitionId) {
      this.transitionId = transitionId;
    }

    public String getSourceStateId() {
      return sourceStateId;
    }

    public void setSourceStateId(String sourceStateId) {
      this.sourceStateId = sourceStateId;
    }

    public Flow.State getTargetState() {
      return targetState;
    }

    public void setTargetState(Flow.State targetState) {
      this.targetState = targetState;
    }

    public long getExpectedRevision() {
      return expectedRevision;
    }

    public void setExpectedRevision(long expectedRevision) {
      this.expectedRevision = expectedRevision;
    }

    public Map<String, Object> getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, Object> v) {
      metadata = v == null ? new LinkedHashMap<>() : v;
    }
  }

  public static class AttachmentUploadImpl implements Flow.AttachmentUpload {
    private String type;
    private String fileName;
    private String mediaType;
    private KlabAsset.KnowledgeClass assetType;
    private byte[] content;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getFileName() {
      return fileName;
    }

    public void setFileName(String fileName) {
      this.fileName = fileName;
    }

    public String getMediaType() {
      return mediaType;
    }

    public void setMediaType(String mediaType) {
      this.mediaType = mediaType;
    }

    public KlabAsset.KnowledgeClass getAssetType() {
      return assetType;
    }

    public void setAssetType(KlabAsset.KnowledgeClass assetType) {
      this.assetType = assetType;
    }

    public byte[] getContent() {
      return content;
    }

    public void setContent(byte[] content) {
      this.content = content;
    }
  }

  private String id;
  private String workflowId;
  private String workflowVersion;
  private String assetUrn;
  private KlabAsset.KnowledgeClass assetType;
  private String permissionsOwnerUrn;
  private String owner;

  /** Whether every identified client may browse the complete flow in read-only mode. */
  private boolean publicRead;

  private Flow.Status status = Flow.Status.ACTIVE;
  private long revision;
  private Instant createdAt;
  private Instant updatedAt;
  private Map<String, Flow.State> states = new LinkedHashMap<>();
  private Set<String> currentStateIds = new LinkedHashSet<>();
  private List<Flow.Transaction> history = new ArrayList<>();
  private Metadata metadata = Metadata.create();
  private String serviceId;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getWorkflowId() {
    return workflowId;
  }

  public void setWorkflowId(String workflowId) {
    this.workflowId = workflowId;
  }

  public String getWorkflowVersion() {
    return workflowVersion;
  }

  public void setWorkflowVersion(String workflowVersion) {
    this.workflowVersion = workflowVersion;
  }

  public String getAssetUrn() {
    return assetUrn;
  }

  public void setAssetUrn(String assetUrn) {
    this.assetUrn = assetUrn;
  }

  public KlabAsset.KnowledgeClass getAssetType() {
    return assetType;
  }

  public void setAssetType(KlabAsset.KnowledgeClass assetType) {
    this.assetType = assetType;
  }

  public String getPermissionsOwnerUrn() {
    return permissionsOwnerUrn;
  }

  public void setPermissionsOwnerUrn(String permissionsOwnerUrn) {
    this.permissionsOwnerUrn = permissionsOwnerUrn;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public boolean isPublicRead() {
    return publicRead;
  }

  public void setPublicRead(boolean publicRead) {
    this.publicRead = publicRead;
  }

  public Flow.Status getStatus() {
    return status;
  }

  public void setStatus(Flow.Status status) {
    this.status = status;
  }

  public long getRevision() {
    return revision;
  }

  public void setRevision(long revision) {
    this.revision = revision;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Map<String, Flow.State> getStates() {
    return states;
  }

  public void setStates(Map<String, Flow.State> v) {
    states = v == null ? new LinkedHashMap<>() : v;
  }

  public Set<String> getCurrentStateIds() {
    return currentStateIds;
  }

  public void setCurrentStateIds(Set<String> v) {
    currentStateIds = v == null ? new LinkedHashSet<>() : v;
  }

  public List<Flow.Transaction> getHistory() {
    return history;
  }

  public void setHistory(List<Flow.Transaction> v) {
    history = v == null ? new ArrayList<>() : v;
  }

  @Override
  public String getUrn() {
    return WorkflowUrns.flow(id);
  }

  @Override
  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> v) {
    metadata = FlowImpl.metadata(v);
  }

  @Override
  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  @Override
  public Collection<Annotation> getAnnotations() {
    return List.of();
  }
}
