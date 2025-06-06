package org.integratedmodelling.klab.rest;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The (legacy) hub sends a list of nodes. In the new structure we add a service type and a primary
 * flag so that we can use the same structure and authentication strategy for all services. Nodes
 * with the default type (Legacy node) will be discarded and the user scope built from the new node
 * types.
 */
public class ServiceReference {

  public static enum Permission {
    PUBLISH,
    QUERY
  }

  private boolean primary = false;
  private KlabService.Type identityType = KlabService.Type.LEGACY_NODE;
  private String id;
  private Set<Permission> permissions = new HashSet<>();
  private IdentityReference partner;
  private List<URL> urls = new ArrayList<>();
  private boolean online;
  private int retryPeriodMinutes;
  private int loadFactor;
  private Set<String> adapters = new HashSet<>();
  private Set<String> namespaces = new LinkedHashSet<>();
  private Set<String> catalogs = new LinkedHashSet<>();
  private List<String> incomingConnections = new ArrayList<>();
  private List<String> outgoingConnections = new ArrayList<>();

  public ServiceReference() {}

  public ServiceReference(NodeCapabilities capabilities) {
    for (AdapterInfo adapter : capabilities.getResourceAdapters()) {
      this.adapters.add(adapter.getName());
    }
    this.id = capabilities.getName();
    this.catalogs.addAll(capabilities.getResourceCatalogs());
    this.namespaces.addAll(capabilities.getResourceNamespaces());
    //		this.resources.addAll(capabilities.getResourceUrns());
    this.online = capabilities.isOnline();
    if (capabilities.isAcceptSubmission()) {
      this.permissions.add(Permission.PUBLISH);
    }
    if (capabilities.isAcceptQueries()) {
      this.permissions.add(Permission.QUERY);
    }
    // TODO authorities
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Set<Permission> getPermissions() {
    return permissions;
  }

  public void setPermissions(Set<Permission> permissions) {
    this.permissions = permissions;
  }

  public IdentityReference getPartner() {
    return partner;
  }

  public void setPartner(IdentityReference partner) {
    this.partner = partner;
  }

  public List<URL> getUrls() {
    return urls;
  }

  public void setUrls(List<URL> urls) {
    this.urls = urls;
  }

  public boolean isOnline() {
    return online;
  }

  public void setOnline(boolean online) {
    this.online = online;
  }

  public int getRetryPeriodMinutes() {
    return retryPeriodMinutes;
  }

  public void setRetryPeriodMinutes(int retryPeriodMinutes) {
    this.retryPeriodMinutes = retryPeriodMinutes;
  }

  public int getLoadFactor() {
    return loadFactor;
  }

  public void setLoadFactor(int loadFactor) {
    this.loadFactor = loadFactor;
  }

  public List<String> getIncomingConnections() {
    return incomingConnections;
  }

  public void setIncomingConnections(List<String> incomingConnections) {
    this.incomingConnections = incomingConnections;
  }

  public List<String> getOutgoingConnections() {
    return outgoingConnections;
  }

  public void setOutgoingConnections(List<String> outgoingConnections) {
    this.outgoingConnections = outgoingConnections;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + ((incomingConnections == null) ? 0 : incomingConnections.hashCode());
    result = prime * result + loadFactor;
    result = prime * result + (online ? 1231 : 1237);
    result = prime * result + ((outgoingConnections == null) ? 0 : outgoingConnections.hashCode());
    result = prime * result + ((partner == null) ? 0 : partner.hashCode());
    result = prime * result + ((permissions == null) ? 0 : permissions.hashCode());
    result = prime * result + retryPeriodMinutes;
    result = prime * result + ((urls == null) ? 0 : urls.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ServiceReference other = (ServiceReference) obj;
    if (id == null) {
      if (other.id != null) return false;
    } else if (!id.equals(other.id)) return false;
    if (incomingConnections == null) {
      if (other.incomingConnections != null) return false;
    } else if (!incomingConnections.equals(other.incomingConnections)) return false;
    if (loadFactor != other.loadFactor) return false;
    if (online != other.online) return false;
    if (outgoingConnections == null) {
      if (other.outgoingConnections != null) return false;
    } else if (!outgoingConnections.equals(other.outgoingConnections)) return false;
    if (partner == null) {
      if (other.partner != null) return false;
    } else if (!partner.equals(other.partner)) return false;
    if (permissions == null) {
      if (other.permissions != null) return false;
    } else if (!permissions.equals(other.permissions)) return false;
    if (retryPeriodMinutes != other.retryPeriodMinutes) return false;
    if (urls == null) {
      if (other.urls != null) return false;
    } else if (!urls.equals(other.urls)) return false;
    return true;
  }

  @Override
  public String toString() {
    return "NodeReference [id="
        + id
        + ", permissions="
        + permissions
        + ", partner="
        + partner
        + ", "
        + "urls="
        + urls
        + ", online="
        + online
        + ", retryPeriodMinutes="
        + retryPeriodMinutes
        + ", loadFactor="
        + loadFactor
        + ", incomingConnections="
        + incomingConnections
        + ", outgoingConnections="
        + outgoingConnections
        + "]";
  }

  public Set<String> getAdapters() {
    return adapters;
  }

  public void setAdapters(Set<String> adapters) {
    this.adapters = adapters;
  }

  public Set<String> getNamespaces() {
    return namespaces;
  }

  public void setNamespaces(Set<String> namespaces) {
    this.namespaces = namespaces;
  }

  public Set<String> getCatalogs() {
    return catalogs;
  }

  public void setCatalogs(Set<String> catalogs) {
    this.catalogs = catalogs;
  }

  public KlabService.Type getIdentityType() {
    return identityType;
  }

  public void setIdentityType(KlabService.Type serviceType) {
    this.identityType = serviceType;
  }

  /**
   * Primary means that the hub has decided that this is the default service of its type for the
   * authenticated user. There should be only one primary service per category. Legacy nodes cannot
   * be primary.
   *
   * @return
   */
  public boolean isPrimary() {
    return primary;
  }

  public void setPrimary(boolean primary) {
    this.primary = primary;
  }

  /**
   * Produce a descriptor for a local service running on the default local URL,
   *
   * @param serviceType the type of service
   * @param identity the identity owning it.
   * @return a new descriptor
   */
  public static ServiceReference local(KlabService.Type serviceType, UserScope identity) {
    var ret = new ServiceReference();
    ret.setIdentityType(serviceType);
    ret.setUrls(List.of(serviceType.localServiceUrl()));
    ret.setPartner(
        new IdentityReference(
            identity.getUser().getUsername(),
            identity.getUser().getUsername(),
            TimeInstant.create().toRFC3339String()));
    return ret;
  }
}
