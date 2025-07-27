package org.integratedmodelling.klab.services.resources.storage;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.persistence.ResourcesKBox;

import java.util.List;

public class ResourceManager {

  private final ResourcesKBox resourcesKbox;
  private final ResourcesProvider service;

  public ResourceManager(ResourcesKBox resourcesKbox, ResourcesProvider service) {
    this.resourcesKbox = resourcesKbox;
    this.service = service;
  }

  /**
   * Record the passed resource, handling the adapter validation and ingestion if any
   *
   * @param resource
   * @param adapter
   * @param scope
   * @return
   */
  public ResourceSet ingestResource(Resource resource, Adapter adapter, UserScope scope) {

    if (adapter.hasValidator(ResourceAdapter.Validator.LifecyclePhase.LocalImport)) {

      var validationResult =
          ComponentRegistry.executeMethod(
              service
                  .getComponentRegistry()
                  .implementation(
                      adapter.getValidator(ResourceAdapter.Validator.LifecyclePhase.LocalImport)),
              resource,
              resource.getGeometry(),
              null,
              null,
              null,
              Urn.of(resource.getUrn()),
              resource.getParameters(),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              scope);

      // TODO result should be allowed to be null (void, exceptions thrown intercepted); boolean
      //  (valid/not); notification (error, info, warning); or Resource, which at this point
      //  substitutes the passed one and any notifications in it are used, with error notifications
      //  causing to abort the ingestion.
    }
    return ResourceSet.empty(Notification.error("Not yet implemented"));
  }
}
