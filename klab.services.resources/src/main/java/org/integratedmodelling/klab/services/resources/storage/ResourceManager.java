package org.integratedmodelling.klab.services.resources.storage;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.resources.ResourcesKBox;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

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
    return ingestResource(resource, adapter, scope, null);
  }

  /**
   * Ingest a resource, optionally as a publication. A publication validator may replace the
   * resource and change its catalog or namespace; after validation, the host segment is forced to
   * the receiving service name and the resulting authoritative URN must be new.
   */
  public ResourceSet ingestResource(
      Resource resource, Adapter adapter, UserScope scope, String publicationHost) {

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
      var currentUrn =
          publicationHost == null
              ? resource.getUrn()
              : ResourcesService.publicationUrn(resource.getUrn(), publicationHost);
      boolean replacingExisting =
          resourcesKbox.getResource(currentUrn, Version.ANY_VERSION) != null;

      if (publicationHost != null && replacingExisting) {
        return ResourceSet.empty(
            Notification.error("Resource " + currentUrn + " is already present"));
      }

      // Create or sanitize the URN using the UrnManager
      String sanitizedUrn =
          publicationHost != null || replacingExisting
              ? currentUrn
              : urnManager.createOrSanitizeUrn(
                  resource,
                  service,
                  ResourceInfo.Stage.STAGING,
                  scope,
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

      // add the ResourceInfo with the original rights and the rest.
      var resourceInfo = createInitialMetadata(resource, scope);
      if (resourceInfo != null) {
        boolean storedInfo = resourcesKbox.putStatus(resourceInfo);
        if (!storedInfo) {
          return ResourceSet.empty(
              Notification.error(
                  "Failed to store metadata for resource " + sanitizedUrn,
                  Notification.Outcome.Failure));
        }
      }

      // Store the resource
      boolean stored = resourcesKbox.putResource(resource);

      if (!stored) {
        resourcesKbox.deleteMetadata(resourceInfo.getUrn());
        return ResourceSet.empty(
            Notification.error(
                "Failed to store resource " + sanitizedUrn, Notification.Outcome.Failure));
      }

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
              KlabAsset.KnowledgeClass.RESOURCE,
              System.currentTimeMillis(),
              false);

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

  /**
   * Create resource metadata with initial, scope-only rights. If we get here the resource has been
   * deemed available.
   *
   * <p>TODO a decent short label should be extracted for the UI if not present. One day maybe even
   * a thumbnail and whatever machine learning support we want, computed in a slower thread.
   */
  private ResourceInfo createInitialMetadata(Resource resource, UserScope scope) {
    ResourceInfo ret = resourcesKbox.getStatus(resource.getUrn(), null);
    boolean existing = ret != null;
    if (!existing) ret = new ResourceInfo();
    ret.setUrn(resource.getUrn());
    ret.setType(ResourceInfo.Type.AVAILABLE);
    if (!existing) {
      ret.setRights(ResourcePrivileges.create(scope));
      ret.setOwner(scope.getUser().getUsername());
      ret.setReviewStatus(0);
    }
    ret.setMetadata(Metadata.create(resource.getMetadata()));
    ret.setKnowledgeClass(KlabAsset.KnowledgeClass.RESOURCE);
    ret.setServiceId(service.serviceId());
    ret.setNotifications(
        new ArrayList<>(
            resource.getNotifications().stream().map(n -> (NotificationImpl) n).toList()));
    ret.setRetryTimeSeconds(180); // TODO configure
    return ret;
  }

  List<ResourceInfo> queryResources(String query) {
    return resourcesKbox.queryResources(query);
  }
}
