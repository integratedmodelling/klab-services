package org.integratedmodelling.klab.services.resources.storage;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.persistence.ResourcesKBox;

public class ResourceManager {

  private final ResourcesKBox resourcesKbox;
  private final ResourcesProvider service;

  public ResourceManager(ResourcesKBox resourcesKbox, ResourcesProvider service) {
    this.resourcesKbox = resourcesKbox;
    this.service = service;
  }

  /**
   * Record the passed resource, handling the adapter validation and ingestion if any
   * @param resource
   * @param adapter
   * @param rights
   * @return
   */
  public ResourceSet ingestResource(Resource resource, Adapter adapter, ResourcePrivileges rights) {
    return ResourceSet.empty(Notification.error("Not yet implemented"));
  }
}
