package org.integratedmodelling.klab.api.services.resources;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
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

    private Type(boolean usable) {
      this.usable = usable;
    }

    public boolean isUsable() {
      return usable;
    }
  }

  private String urn;
  private Type type;
  private int retryTimeSeconds;
  // Must be Impl to keep it serializable without issues.
  private List<NotificationImpl> notifications = new ArrayList<>();
  private int reviewStatus;
  private ResourcePrivileges rights = ResourcePrivileges.empty();
  private String owner;
  private File fileLocation;
  private boolean legacy;
  private KnowledgeClass knowledgeClass;
  private Metadata metadata = Metadata.create();
  private String serviceId;

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

  public List<NotificationImpl> getNotifications() {
    return notifications;
  }

  public void setNotifications(List<NotificationImpl> notifications) {
    this.notifications = notifications;
  }

  /**
   * This ranges from 0 (unreviewed) through 1 (staging if local, in review if public) to 2
   * (reviewed and accepted, with a DOI) and up. Resources at level higher than 2 may move down in
   * level as well as up but not go below 2 unless retracted. Level -1 is rejected or retracted;
   * lower negative rankings may indicate special infamy such as fake resources, at the discretion
   * of the implementation. Resources with negative rankings should not be used in any circumstance,
   * and all normal operation APIs should not return them.
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
}
