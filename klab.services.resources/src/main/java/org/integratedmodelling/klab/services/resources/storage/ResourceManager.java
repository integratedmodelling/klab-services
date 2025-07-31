package org.integratedmodelling.klab.services.resources.storage;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.indexing.ResourceIndexer;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.persistence.ResourcesKBox;

import java.util.List;

public class ResourceManager {

  private final ResourcesKBox resourcesKbox;
  private final ResourcesProvider service;
  private final UrnManager urnManager;

  public ResourceManager(ResourcesKBox resourcesKbox, ResourcesProvider service) {
    this.resourcesKbox = resourcesKbox;
    this.service = service;
    this.urnManager = new UrnManager();
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

    var ret = new ResourceSet();

    // validate mandatory parameters for the adapter
    var notifications =
        Utils.Resources.validateParameters(adapter.getParameters(), resource.getParameters());
    ret.getNotifications().addAll(notifications);

    if (Utils.Notifications.hasErrors(notifications)) {
      return ret;
    }

    try {

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

        Notification adapterNotification = null;
        boolean invalid =
            switch (validationResult) {
              case Notification notification -> {
                adapterNotification = notification;
                yield notification.getLevel() == Notification.Level.Error;
              }
              case Boolean booleanResult -> booleanResult;
              case Resource resourceResult -> {
                // substitute a 'fixed' resource
                resource = resourceResult;
                if (!resource.getNotifications().isEmpty()) {
                  ret.getNotifications().addAll(resource.getNotifications());
                }
                yield Utils.Notifications.hasErrors(resource.getNotifications());
              }
              default -> false;
            };

        if (invalid) {
          return ResourceSet.empty(
              adapterNotification == null
                  ? Notification.error(
                      "Adapter " + adapter.getName() + " did not validate resource " + resource)
                  : adapterNotification);
        }

        if (adapterNotification != null) {
          ret.getNotifications().add(adapterNotification);
        } else if (ret.getNotifications().isEmpty()) {
          ret.getNotifications()
              .add(
                  Notification.info(
                      "Adapter "
                          + adapter.getName()
                          + " successfully validated resource "
                          + resource));
        }
      }

      // Establish the proper local name and URN for the new resource
      var currentName = resource.getLocalName();
      var currentUrn = resource.getUrn();

      // Create or sanitize the URN using the UrnManager
      String sanitizedUrn =
          urnManager.createOrSanitizeUrn(
              resource,
              service.serviceId(),
              // Uniqueness checker callback
              urn -> resourcesKbox.getResource(urn, Version.ANY_VERSION) == null);

      // Update the resource with the sanitized URN
      ((org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl) resource)
          .setUrn(sanitizedUrn);

      // If the resource doesn't have a local name, extract it from the URN
      if (currentName == null || currentName.isEmpty()) {
        String[] components = sanitizedUrn.split(":");
        if (components.length == 4) {
          String resourceName = components[3];
          ((org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl) resource)
              .setLocalName(resourceName);
        }
      }

      // Store the resource
      boolean stored = resourcesKbox.putResource(resource);

      if (!stored) {
        return ResourceSet.empty(
            Notification.error(
                "Failed to store resource " + sanitizedUrn, Notification.Outcome.Failure));
      }

      // TODO add the ResourceInfo with the original rights and the rest. A decent short label should be
      //  extracted for the UI if not present. One day maybe even a thumbnail and machine-learned added
      //  info, computed in a slower thread.


      // Add success notification
      ret.getNotifications()
          .add(
              Notification.info(
                  "Resource " + sanitizedUrn + " successfully stored",
                  Notification.Outcome.Success));

      // Create a ResourceSet.Resource from the Resource and add it to the result set
      org.integratedmodelling.klab.api.services.resources.ResourceSet.Resource resourceSetResource =
          new ResourceSet.Resource(
              service.serviceId(),
              sanitizedUrn,
              resource.getLocalProjectName(),
              resource.getVersion(),
              KlabAsset.KnowledgeClass.RESOURCE);

      ret.getResources().add(resourceSetResource);

    } catch (Exception e) {

      return ResourceSet.empty(
          Notification.error(
              "Ingestion of resource "
                  + resource.getUrn()
                  + " failed with a server error: "
                  + e.getMessage(),
              e,
              Notification.Outcome.Failure));
    }

    return ret;
  }

  List<ResourceInfo> queryResources(String query) {
    return resourcesKbox.queryResources(query);
  }
}
