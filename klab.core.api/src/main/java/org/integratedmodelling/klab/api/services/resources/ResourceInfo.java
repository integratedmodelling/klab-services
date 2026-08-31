package org.integratedmodelling.klab.api.services.resources;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;

/**
 * Resource status reports on availability (overall and/or in scope) and review status of any
 * resource. It should be stored and maintained as a secure, persistent catalog indexed by resource
 * URN in the resource service. While all other information is stored reflecting the status for the
 * service at the time of last update, the availability ({@link #getType()} should always be
 * assessed in scope and in realtime when this is retrieved from the API ({@link
 * ResourcesService#resourceInfo(String, Scope)}), reflecting permissions, server status, adapter
 * status, availability of dependencies and any other needed factor. The stored type should be
 * AUTHORIZED or DEPRECATED, OFFLINE only if it starts its lifecycle with compilation errors, and
 * never UNAUTHORIZED or DELAYED.
 *
 * <p>Much of the information here comes from the persistent ResourceConfiguration in ResourceSet,
 * although it could change based on context, scope or contingencies. In turn, that information
 * could come from the manifest in the resources themselves or be added later, possibly to override
 * the former.
 *
 * @author Ferd
 */
public class ResourceInfo implements Serializable {

  /** Compact catalog entry for one workflow instance associated with this asset. */
  public static class FlowReference implements Serializable {
    private String flowUrn;
    private String workflowUrn;
    private Flow.Status status;
    private Set<String> currentStateUrns = new LinkedHashSet<>();
    private Stage stage = Stage.REVIEWING;
    private int reviewStatus;
    private long updatedAt;

    public String getFlowUrn() {
      return flowUrn;
    }

    public void setFlowUrn(String flowUrn) {
      this.flowUrn = flowUrn;
    }

    public String getWorkflowUrn() {
      return workflowUrn;
    }

    public void setWorkflowUrn(String workflowUrn) {
      this.workflowUrn = workflowUrn;
    }

    public Flow.Status getStatus() {
      return status;
    }

    public void setStatus(Flow.Status status) {
      this.status = status;
    }

    public Set<String> getCurrentStateUrns() {
      return currentStateUrns;
    }

    public void setCurrentStateUrns(Set<String> value) {
      currentStateUrns = value == null ? new LinkedHashSet<>() : value;
    }

    public Stage getStage() {
      return stage;
    }

    public void setStage(Stage stage) {
      this.stage = stage;
    }

    public int getReviewStatus() {
      return reviewStatus;
    }

    public void setReviewStatus(int reviewStatus) {
      this.reviewStatus = reviewStatus;
    }

    public long getUpdatedAt() {
      return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
      this.updatedAt = updatedAt;
    }
  }

  public enum Type {
    AVAILABLE(true),
    DELAYED(true),
    UNAUTHORIZED(false),
    OFFLINE(false),
    /**
     * Deprecated also implies AVAILABLE. Delayed or Partial status resources that are deprecated
     * are considered unavailable for now.
     */
    DEPRECATED(true);

    private boolean usable;

    Type(boolean usable) {
      this.usable = usable;
    }

    public boolean isUsable() {
      return usable;
    }
  }

  public enum Stage {
    /** Staging, not reviewed, findable by submitter and admins only. */
    STAGING(0),
    /**
     * Staging, under review, findable by submitter, admins, reviewers, editors and whoever the
     * original user granted special access to.
     */
    REVIEWING(0),
    /** Level 1 of peer-review. Published and findable, priority 3 */
    CONFIRMED(1),
    /**
     * Level 2 of peer-review. Published and findable, has a DOI and is in public catalogs; priority
     * 2
     */
    REVIEWED(2),
    /**
     * Level 3 of peer-review. Published and findable, has DOI, institutional endorsement; priority
     * 1
     */
    ENDORSED(3),
    /**
     * Level 4 of peer-review. All attributes of ENDORSED with special mention, priority 0, should
     * be used sparingly or on group-restricted resources to force community use
     */
    EMPHASIZED(4),
    /** For the records. Not findable except by original submitter and admins. */
    REJECTED(-1),
    /**
     * For the records, with rationale for revocation. Not findable except by original submitter and
     * admins who have opted for use of revoked assets, with warning upon use.
     */
    REVOKED(-1),
    /** For situations where records are kept for deleted assets. Should not be used by anyone. */
    DELETED(-1);

    /** Positive status means usable; 0 means usable by submitter only; -1 means unusable */
    public final int status;

    Stage(int status) {
      this.status = status;
    }
  }

  private String urn;
  private Type type;
  private int retryTimeSeconds;
  // Must be Impl to keep it serializable without issues.
  private List<NotificationImpl> notifications = new ArrayList<>();
  private Stage stage = Stage.STAGING;
  private int reviewStatus;
  private ResourcePrivileges rights = ResourcePrivileges.empty();

  /**
   * URN of the first-class asset that owns permissions, or null when this record owns them.
   * Second-class records created for workflows normally point to their containing project.
   */
  private String permissionsOwnerUrn;

  private String owner;
  private File fileLocation;
  private boolean legacy;
  private KnowledgeClass knowledgeClass;
  private Metadata metadata = Metadata.create();
  private String serviceId;
  private Set<CRUDOperation> permissions = new HashSet<>();
  private boolean local;

  /** True only on the retained local source record after a successful publication. */
  private boolean published;

  /** Service that accepted the published copy and is authoritative for subsequent operations. */
  private String authoritativeServiceId;

  /** Final URN assigned by the authoritative service after validation and metadata analysis. */
  private String authoritativeResourceUrn;

  /** Time at which the local source was last published, as epoch milliseconds. */
  private long publicationTimestamp;

  /** Flow URN to current per-flow review summary. Persisted with the ResourceInfo record. */
  private Map<String, FlowReference> flows = new LinkedHashMap<>();

  public List<String> getChildResourceUrns() {
    return childResourceUrns;
  }

  public void setChildResourceUrns(List<String> childResourceUrns) {
    this.childResourceUrns = childResourceUrns;
  }

  private List<String> childResourceUrns = new ArrayList<>();

  public Type getType() {
    return type;
  }

  public void setType(Type availability) {
    this.type = availability;
  }

  /**
   * This will be different from 0 iif the status is {@link Type#DELAYED} and it should be taken as
   * an indication only.
   *
   * @return
   */
  public int getRetryTimeSeconds() {
    return retryTimeSeconds;
  }

  public void setRetryTimeSeconds(int retryTimeSeconds) {
    this.retryTimeSeconds = retryTimeSeconds;
  }

  public Stage getStage() {
    return stage;
  }

  public void setStage(Stage stage) {
    this.stage = stage;
  }

  public static ResourceInfo immediate() {
    ResourceInfo ret = new ResourceInfo();
    ret.setType(Type.AVAILABLE);
    return ret;
  }

  public static ResourceInfo offline() {
    ResourceInfo ret = new ResourceInfo();
    ret.setType(Type.OFFLINE);
    return ret;
  }

  public static ResourceInfo offline(String urn) {
    ResourceInfo ret = new ResourceInfo();
    ret.setType(Type.OFFLINE);
    ret.setUrn(urn);
    return ret;
  }

  public List<NotificationImpl> getNotifications() {
    return notifications;
  }

  public void setNotifications(List<NotificationImpl> notifications) {
    this.notifications = notifications;
  }

  /**
   * This ranges from 0 (unreviewed) through 1 (staging if local, in review if public) to 2
   * (reviewed and accepted, with a DOI) and up. One-to-one correspondence to review {@link Stage}
   * at asset creation, but may go above 4 if needed for prioritization and change independently of
   * {@link #getStage()} if needed. Resources at level higher than 2 may move down in level as well
   * as up but not go below 2 unless retracted. Level -1 is rejected or retracted; lower negative
   * rankings may indicate special infamy such as fake resources, at the discretion of the
   * implementation. Resources with negative rankings should not be used in any circumstance, and
   * all normal operation APIs should not return them.
   */
  public int getReviewStatus() {
    return reviewStatus;
  }

  public void setReviewStatus(int reviewStatus) {
    this.reviewStatus = reviewStatus;
  }

  /**
   * For now resources are owned uniquely by users, which may be institutional or personal, and
   * should always be in the form <code>hub:username</code>.
   *
   * @return
   */
  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public File getFileLocation() {
    return fileLocation;
  }

  public void setFileLocation(File fileLocation) {
    this.fileLocation = fileLocation;
  }

  /**
   * True if pre-k.LAB 12.0
   *
   * @return
   */
  public boolean isLegacy() {
    return legacy;
  }

  public void setLegacy(boolean legacy) {
    this.legacy = legacy;
  }

  public KnowledgeClass getKnowledgeClass() {
    return knowledgeClass;
  }

  public void setKnowledgeClass(KnowledgeClass knowledgeClass) {
    this.knowledgeClass = knowledgeClass;
  }

  public ResourcePrivileges getRights() {
    return rights;
  }

  public void setRights(ResourcePrivileges rights) {
    this.rights = rights;
  }

  public String getPermissionsOwnerUrn() {
    return permissionsOwnerUrn;
  }

  public void setPermissionsOwnerUrn(String permissionsOwnerUrn) {
    this.permissionsOwnerUrn = permissionsOwnerUrn;
  }

  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public String getUrn() {
    return urn;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  public boolean isLocal() {
    return local;
  }

  public void setLocal(boolean local) {
    this.local = local;
  }

  public boolean isPublished() {
    return published;
  }

  public void setPublished(boolean published) {
    this.published = published;
  }

  public String getAuthoritativeServiceId() {
    return authoritativeServiceId;
  }

  public void setAuthoritativeServiceId(String authoritativeServiceId) {
    this.authoritativeServiceId = authoritativeServiceId;
  }

  public String getAuthoritativeResourceUrn() {
    return authoritativeResourceUrn;
  }

  public void setAuthoritativeResourceUrn(String authoritativeResourceUrn) {
    this.authoritativeResourceUrn = authoritativeResourceUrn;
  }

  public long getPublicationTimestamp() {
    return publicationTimestamp;
  }

  public void setPublicationTimestamp(long publicationTimestamp) {
    this.publicationTimestamp = publicationTimestamp;
  }

  /**
   * These are NOT stored but added when sent as a response to a {@link
   * ResourcesService#queryResources(String, Scope, KnowledgeClass...)} call, communicating what the
   * requesting user can do with the resource.
   *
   * @return
   */
  public Set<CRUDOperation> getPermissions() {
    return permissions;
  }

  public void setPermissions(Set<CRUDOperation> permissions) {
    this.permissions = permissions;
  }

  public Map<String, FlowReference> getFlows() {
    return flows;
  }

  public void setFlows(Map<String, FlowReference> flows) {
    this.flows = flows == null ? new LinkedHashMap<>() : flows;
  }
}
