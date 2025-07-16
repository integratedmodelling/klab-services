package org.integratedmodelling.klab.services.resources.library;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.adapters.Importer;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

import java.io.File;
import java.util.Collection;
import java.util.Map;

@Library(
    name = "resources",
    description = "Import and export of k.LAB resources",
    version = Version.CURRENT)
public class ResourcesLibrary {

  @Importer(
      schema = "legacy.json",
      knowledgeClass = KlabAsset.KnowledgeClass.PROJECT,
      description = "Register a new resource by importing a legacy json manifest",
      mediaType = "application/json",
      fileExtensions = {"json"})
  public static ResourceSet importLegacyResourceJson(
      File archive, ResourcesProvider service, UserScope scope) {
    var definition = Utils.Json.load(archive, Map.class);
    if (definition != null) {
      return loadLegacyResource(definition, service, scope, null);
    }
    return ResourceSet.empty(
        Notification.error("Legacy resource ingestion: submitted resource data cannot be parsed"));
  }

  @Importer(
      schema = "legacy.zip",
      knowledgeClass = KlabAsset.KnowledgeClass.PROJECT,
      description = "Register a new resource by importing a zip file with legacy content",
      mediaType = "application/zip",
      fileExtensions = {"zip"})
  public static ResourceSet importLegacyResourceZip(
      File archive, ResourcesProvider service, UserScope scope) {
    return ResourceSet.empty(Notification.error("Legacy resource ingestion: please implement me"));
  }

  @Importer(
      schema = "json",
      knowledgeClass = KlabAsset.KnowledgeClass.PROJECT,
      description =
          "Register a new resource without content by importing a compliant json manifest",
      mediaType = "application/json",
      fileExtensions = {"json"})
  public static ResourceSet importResourceJson(
      File archive, ResourcesProvider service, UserScope scope) {
    return ResourceSet.empty(Notification.error("Resource ingestion: please implement me"));
  }

  @Importer(
      schema = "zip",
      knowledgeClass = KlabAsset.KnowledgeClass.PROJECT,
      description =
          "Register a new resource by importing a zip file with full content, including the json manifest",
      mediaType = "application/zip",
      fileExtensions = {"zip"})
  public static ResourceSet importResourceZip(
      File archive, ResourcesProvider service, UserScope scope) {
    return ResourceSet.empty(Notification.error("Resource ingestion: please implement me"));
  }

  private static ResourceSet loadLegacyResource(
      Map definition, ResourcesProvider service, UserScope scope, File dataContentDirectory) {

    try {
      Utils.Maps.ensureContains(definition, "urn", "type", "geometry", "adapterType", "version");
    } catch (KlabIllegalStateException e) {
      return ResourceSet.empty(
          Notification.error(
              "Legacy resource ingestion: submitted resource data are incomplete "
                  + definition.get("urn")));
    }
    var builder =
        Resource.builder(definition.get("urn").toString())
            .withAdapterType(definition.get("adapterType").toString())
            .withType(Artifact.Type.valueOf(definition.get("type").toString()));

    if (definition.containsKey("localName")) {
      builder.withLocalName(definition.get("localName").toString());
    }
    var geometry = Geometry.create(definition.get("geometry").toString());
    if (geometry != null) {
      builder.withGeometry(geometry);
    } else {
      return ResourceSet.empty(
          Notification.error("Could not load geometry for resource " + definition.get("urn")));
    }

    if (definition.get("metadata") instanceof Map metadata) {
      for (var key : metadata.keySet()) {
        builder.withMetadata(key.toString(), metadata.get(key));
      }
    }

    if (definition.get("parameters") instanceof Map parameters) {
      for (var key : parameters.keySet()) {
        builder.withParameter(key.toString(), parameters.get(key));
      }
    }
    if (definition.get("resourceTimestamp") instanceof Number timestamp) {
      builder.withResourceTimestamp(timestamp.longValue());
    }
    if (definition.get("version") instanceof String version) {
      builder.withResourceVersion(Version.create(version));
    }
    if (definition.containsKey("notifications")) {}
    if (definition.containsKey("spatialExtent")) {}
    if (definition.containsKey("attributes")) {}
    if (definition.containsKey("inputs")) {}
    if (definition.containsKey("outputs")) {}
    if (definition.containsKey("exportFormats")) {}
    if (dataContentDirectory == null
        && definition.get("localPaths") instanceof Collection<?> localPaths
        && localPaths.size() > 1) {
      builder.withNotifications(
          Notification.warning(
              "Legacy resource ingested as metadata only has non-empty local data"));
    }
    // ignore local paths, we submit whole zips if there are local data
    // ignore history for now. We will treat that differently in 1.0

    return service.ingestResource(builder.build(), scope);
  }
}
