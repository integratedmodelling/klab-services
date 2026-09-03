package org.integratedmodelling.klab.resources;

import static org.dizitart.no2.filters.FluentFilter.*;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.common.mapper.JacksonMapper;
import org.dizitart.no2.common.module.NitriteModule;
import org.dizitart.no2.index.IndexType;
import org.dizitart.no2.repository.EntityDecorator;
import org.dizitart.no2.repository.EntityId;
import org.dizitart.no2.repository.EntityIndex;
import org.dizitart.no2.repository.ObjectRepository;
import org.dizitart.no2.rocksdb.RocksDBModule;
import org.dizitart.no2.spatial.SpatialModule;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.services.resources.workflow.FlowImpl;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowUrns;
import org.integratedmodelling.klab.indexing.ResourceIndexer;
import org.integratedmodelling.klab.services.base.BaseService;

/**
 * Nitrite-based noSQL embedded storage for observables, resources, models and permissions. The URN
 * is always the primary key. Disk-based with automatic backup. Can navigate semantics and
 * spatial/temporal queries.
 */
public class ResourcesKBox implements WorkflowStore {

  private final ResourceIndexer index;

  private final Nitrite db;
  private final File databaseFile;
  private ObjectRepository<ResourceInfo> resourceMetadata;
  private ObjectRepository<ResourceImpl> resources;
  private ObjectRepository<WorkflowRecord> workflows;
  private ObjectRepository<FlowImpl> flows;
  private ObjectRepository<WorkflowAttachmentPayload> workflowAttachments;
  private boolean local;

  /** Take over the mapper so we can use interfaces */
  private static class KlabJacksonMapper extends JacksonMapper {

    ObjectMapper om;

    @Override
    public ObjectMapper getObjectMapper() {
      if (om == null) {
        this.om = new ObjectMapper();
        JacksonConfiguration.configureObjectMapperForKlabTypes(this.om);
        this.om.setVisibility(
            this.om
                .getSerializationConfig()
                .getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE));
        this.om.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        this.om.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        this.om.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        this.om.configure(JsonParser.Feature.IGNORE_UNDEFINED, true);
        this.om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      }
      return this.om;
    }
  }

  public ResourcesKBox(Scope scope, ServiceStartupOptions options, BaseService service) {

    this.databaseFile =
        BaseService.getFileInConfigurationSubdirectory(options, "data", "resources.db");
    RocksDBModule storeModule = RocksDBModule.withConfig().filePath(databaseFile.getPath()).build();
    this.local = service.isLocal();

    this.db =
        Nitrite.builder()
            .loadModule(storeModule)
            .loadModule(new SpatialModule())
            .loadModule(NitriteModule.module(new KlabJacksonMapper()))
            .openOrCreate();

    this.resourceMetadata = db.getRepository(new ResourceMetadataDecorator());
    this.resources = db.getRepository(new ResourceDecorator());
    this.workflows = db.getRepository(new WorkflowDecorator());
    this.flows = db.getRepository(new FlowDecorator());
    this.workflowAttachments = db.getRepository(new WorkflowAttachmentDecorator());
    this.index =
        ResourceIndexer.create(
            Configuration.INSTANCE.getDataPath(
                "services/" + service.serviceType().name().toLowerCase() + "/index/resources"));
    if (!index.hasCurrentSchema()) {
      rebuildResourceIndex();
    }
  }

  public void shutdown() {
    index.ensureClosed();
    if (this.db != null && !this.db.isClosed()) {
      this.db.close();
    }
  }

  private void rebuildResourceIndex() {
    index.clear();
    for (var resource : resources.find()) {
      index.index(resource, resourceMetadata.getById(resource.getUrn()));
    }
    index.commitChanges();
  }

  /**
   * Find the resource with the passed URN and version and return it.
   *
   * @param urn can have a @version segment, in which case the <code>version</code> parameter can be
   *     null or empty.
   * @param version Use {@link Version#ANY_VERSION} to obtain the latest resource revision.
   * @return the resource or null
   */
  public Resource getResource(String urn, Version version) {
    var split = Version.splitVersion(urn);
    String resourceUrn = split.getFirst();
    Version requested =
        version == null || Version.ANY_VERSION.equals(version) ? split.getSecond() : version;
    return selectVersion(resources.getById(resourceUrn), requested);
  }

  static Resource selectVersion(Resource current, Version requested) {
    if (current == null
        || requested == null
        || Version.ANY_VERSION.equals(requested)
        || requested.equals(current.getVersion())) {
      return current;
    }
    return current.getHistory().stream()
        .filter(previous -> requested.equals(previous.getVersion()))
        .findFirst()
        .orElse(null);
  }

  public List<Resource> getResourcesByUrnMatch(String regex) {
    var ret = new ArrayList<Resource>();
    for (var result : resources.find(where("urn").regex(regex))) {
      ret.add(result);
    }
    return ret;
  }

  public List<String> listResourcesUrns() {
    return resources.find().toList().stream().map(ResourceImpl::getUrn).toList();
  }

  /**
   * Store the passed resource with its version. Return true if this was an update of a previously
   * stored resource or this is new.
   *
   * @param resource
   * @return
   */
  public boolean putResource(Resource resource) {
    if (resource instanceof ResourceImpl resource1) {
      var result = resources.update(resource1, true);
      if (result.getAffectedCount() == 1) {
        index.index(resource1, resourceMetadata.getById(resource1.getUrn()));
        index.commitChanges();
        return true;
      }
    }
    return false;
  }

  public boolean deleteResource(String urn) {
    var resource = resources.getById(urn);
    if (resource == null) {
      return false;
    }
    resources.remove(resource);
    index.delete(urn);
    index.commitChanges();
    return true;
  }

  public boolean deleteMetadata(String urn) {
    var resource = resourceMetadata.getById(urn);
    if (resource == null) {
      return false;
    }
    resourceMetadata.remove(resource);
    var storedResource = resources.getById(urn);
    if (storedResource != null) {
      index.index(storedResource, null);
      index.commitChanges();
    }
    return true;
  }

  public List<ResourceInfo> queryResources(String query) {
    return queryResources(query, ResourceIndexer.MAX_RESULT_COUNT);
  }

  public List<ResourceInfo> queryResources(String query, int maxResults) {
    var ret = new ArrayList<ResourceInfo>();
    var seenUrns = new HashSet<String>();
    for (var document : index.query(query, maxResults)) {
      // The URN is the resource primary key. A stale/legacy index may still contain more than one
      // Lucene document for it, but a catalogue query must expose only the current resource.
      if (!seenUrns.add(document.getId())) {
        continue;
      }
      var info = getStatus(document.getId(), Version.ANY_VERSION);
      if (info != null) {
        var resource = resources.getById(document.getId());
        if (resource != null) {
          // Resource metadata are authoritative and make the result useful without another request.
          info.getMetadata().putAll(resource.getMetadata());
          info.getMetadata().put("im:adapter", resource.getAdapterType());
          info.getMetadata().put("im:version", resource.getVersion());
        }
        info.getMetadata().put(Metadata.IM_SEARCH_SCORE, document.getScore());
        ret.add(info);
      }
    }
    return ret;
  }

  /**
   * Return the status for the passed URN and version.
   *
   * @param urn same as in {@link #getResource(String, Version)}
   * @param version same as in {@link #getResource(String, Version)}
   * @return status or null
   */
  public ResourceInfo getStatus(String urn, Version version) {
    // TODO handle version
    var ret = resourceMetadata.getById(urn);
    if (ret != null) {
      ret.setLocal(this.local);
    }
    return ret;
  }

  public boolean putStatus(ResourceInfo status) {
    var result = resourceMetadata.update(status, true);
    var resource = resources.getById(status.getUrn());
    if (result.getAffectedCount() == 1 && resource != null) {
      index.index(resource, status);
      index.commitChanges();
    }
    return result.getAffectedCount() == 1;
  }

  /** Persist or replace a validated workflow definition. */
  public boolean putWorkflow(Workflow workflow) {
    var record = new WorkflowRecord();
    record.storageId = workflow.getId() + "@" + workflow.getVersion();
    record.workflow = workflow;
    return workflows.update(record, true).getAffectedCount() == 1;
  }

  public Workflow getWorkflow(String id) {
    return workflows.find().toList().stream()
        .map(WorkflowRecord::getWorkflow)
        .filter(workflow -> id.equals(workflow.getId()))
        .max(java.util.Comparator.comparing(workflow -> Version.create(workflow.getVersion())))
        .orElse(null);
  }

  public Workflow getWorkflow(String id, String version) {
    var record = workflows.getById(id + "@" + version);
    return record == null ? null : record.workflow;
  }

  public List<Workflow> listWorkflows() {
    return workflows.find().toList().stream().map(WorkflowRecord::getWorkflow).toList();
  }

  /** Persist the complete flow aggregate atomically in Nitrite. */
  public boolean putFlow(Flow flow) {
    if (flow instanceof FlowImpl flow1) {
      return flows.update(flow1, true).getAffectedCount() == 1;
    }
    throw new KlabIllegalStateException("unexpected flow type");
  }

  public Flow getFlow(String id) {
    return flows.getById(id);
  }

  public List<Flow> listFlows() {
    return flows.find().toList().stream().map(f -> (Flow) f).toList();
  }

  /**
   * Synchronize the compact, one-to-many flow catalog stored with an asset's status record.
   *
   * <p>Second-class assets normally have no {@link ResourceInfo}. Opening their first flow creates
   * one; subsequent flow changes update only the entry keyed by that flow's permanent URN. Existing
   * permissions are deliberately preserved because they belong to the containing first-class asset.
   * The top-level stage is the most recently updated flow's summary, while every individual flow
   * summary remains available in {@link ResourceInfo#getFlows()}.
   */
  @Override
  public synchronized boolean updateResourceInfoForFlow(
      Flow flow, ResourceInfo.Stage stage, int reviewStatus) {
    if (flow == null
        || flow.getAssetUrn() == null
        || flow.getAssetUrn().isBlank()
        || flow.getAssetType() == null) {
      return false;
    }
    var info = resourceMetadata.getById(flow.getAssetUrn());
    if (info == null) {
      info = ResourceInfo.immediate();
      info.setUrn(flow.getAssetUrn());
      info.setKnowledgeClass(flow.getAssetType());
      info.setOwner(flow.getOwner());
      info.setPermissionsOwnerUrn(flow.getPermissionsOwnerUrn());
      info.setStage(ResourceInfo.Stage.STAGING);
      info.setReviewStatus(0);
    } else if (info.getPermissionsOwnerUrn() == null
        && flow.getPermissionsOwnerUrn() != null
        && !flow.getAssetUrn().equals(flow.getPermissionsOwnerUrn())) {
      info.setPermissionsOwnerUrn(flow.getPermissionsOwnerUrn());
    }
    var reference = new ResourceInfo.FlowReference();
    reference.setFlowUrn(flow.getUrn());
    reference.setWorkflowUrn(
        WorkflowUrns.workflow(flow.getWorkflowId(), flow.getWorkflowVersion()));
    reference.setStatus(flow.getStatus());
    reference.setCurrentStateUrns(
        flow.getCurrentStateIds().stream()
            .map(stateId -> WorkflowUrns.flowState(flow.getId(), stateId))
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
    reference.setStage(stage == null ? ResourceInfo.Stage.REVIEWING : stage);
    reference.setReviewStatus(reviewStatus);
    reference.setUpdatedAt(
        (flow.getUpdatedAt() == null ? java.time.Instant.now() : flow.getUpdatedAt())
            .toEpochMilli());
    info.getFlows().put(reference.getFlowUrn(), reference);

    // The aggregate status is intentionally a latest-update summary; the map above is authoritative
    // when more than one independent flow is open on the same asset.
    var latest =
        info.getFlows().values().stream()
            .max(java.util.Comparator.comparingLong(ResourceInfo.FlowReference::getUpdatedAt))
            .orElse(reference);
    info.setStage(latest.getStage());
    info.setReviewStatus(latest.getReviewStatus());
    return putStatus(info);
  }

  /** Store opaque bytes separately so flow retrieval remains cheap. */
  public boolean putWorkflowAttachment(String id, String flowId, String stateId, byte[] content) {
    var payload = new WorkflowAttachmentPayload();
    payload.id = id;
    payload.flowId = flowId;
    payload.stateId = stateId;
    payload.content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    return workflowAttachments.update(payload, true).getAffectedCount() == 1;
  }

  public byte[] getWorkflowAttachment(String id) {
    var payload = workflowAttachments.getById(id);
    return payload == null || payload.content == null
        ? null
        : Arrays.copyOf(payload.content, payload.content.length);
  }

  public boolean deleteWorkflowAttachment(String id) {
    var payload = workflowAttachments.getById(id);
    if (payload == null) return false;
    return workflowAttachments.remove(payload).getAffectedCount() == 1;
  }

  private static class ResourceMetadataDecorator implements EntityDecorator<ResourceInfo> {

    @Override
    public Class<ResourceInfo> getEntityType() {
      return ResourceInfo.class;
    }

    @Override
    public EntityId getIdField() {
      return new EntityId("urn");
    }

    @Override
    public List<EntityIndex> getIndexFields() {
      return List.of(new EntityIndex(IndexType.UNIQUE, "urn"));
    }

    @Override
    public String getEntityName() {
      return "resourceInfo";
    }
  }

  private static class ResourceDecorator implements EntityDecorator<ResourceImpl> {

    @Override
    public Class<ResourceImpl> getEntityType() {
      return ResourceImpl.class;
    }

    @Override
    public EntityId getIdField() {
      return new EntityId("urn");
    }

    @Override
    public List<EntityIndex> getIndexFields() {
      return List.of(new EntityIndex(IndexType.UNIQUE, "urn"));
    }

    @Override
    public String getEntityName() {
      return "resources";
    }
  }

  public static class WorkflowRecord {
    private String storageId;
    private Workflow workflow;

    public WorkflowRecord() {}

    public String getStorageId() {
      return storageId;
    }

    public void setStorageId(String storageId) {
      this.storageId = storageId;
    }

    public Workflow getWorkflow() {
      return workflow;
    }

    public void setWorkflow(Workflow workflow) {
      this.workflow = workflow;
    }
  }

  private static class WorkflowDecorator implements EntityDecorator<WorkflowRecord> {
    public Class<WorkflowRecord> getEntityType() {
      return WorkflowRecord.class;
    }

    public EntityId getIdField() {
      return new EntityId("storageId");
    }

    public List<EntityIndex> getIndexFields() {
      return List.of(new EntityIndex(IndexType.UNIQUE, "storageId"));
    }

    public String getEntityName() {
      return "workflows";
    }
  }

  private static class FlowDecorator implements EntityDecorator<FlowImpl> {
    public Class<FlowImpl> getEntityType() {
      return FlowImpl.class;
    }

    public EntityId getIdField() {
      return new EntityId("id");
    }

    public List<EntityIndex> getIndexFields() {
      return List.of(
          new EntityIndex(IndexType.UNIQUE, "id"),
          new EntityIndex(IndexType.NON_UNIQUE, "workflowId"),
          new EntityIndex(IndexType.NON_UNIQUE, "status"));
    }

    public String getEntityName() {
      return "workflowFlows";
    }
  }

  /** Internal blob record. It is intentionally absent from the public API. */
  public static class WorkflowAttachmentPayload {
    private String id;
    private String flowId;
    private String stateId;
    private byte[] content;

    public WorkflowAttachmentPayload() {}

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

    public byte[] getContent() {
      return content;
    }

    public void setContent(byte[] content) {
      this.content = content;
    }
  }

  private static class WorkflowAttachmentDecorator
      implements EntityDecorator<WorkflowAttachmentPayload> {
    public Class<WorkflowAttachmentPayload> getEntityType() {
      return WorkflowAttachmentPayload.class;
    }

    public EntityId getIdField() {
      return new EntityId("id");
    }

    public List<EntityIndex> getIndexFields() {
      return List.of(
          new EntityIndex(IndexType.UNIQUE, "id"),
          new EntityIndex(IndexType.NON_UNIQUE, "flowId"),
          new EntityIndex(IndexType.NON_UNIQUE, "stateId"));
    }

    public String getEntityName() {
      return "workflowAttachments";
    }
  }
}
