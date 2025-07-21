package org.integratedmodelling.klab.runtime.libraries;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.adapters.Exporter;
import org.integratedmodelling.klab.api.services.resources.adapters.Importer;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.extension.MavenComponentCache;
import org.integratedmodelling.klab.services.base.BaseService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

@Library(
    name = "component",
    description = "Importers for components shared by all services",
    version = Version.CURRENT)
public class ComponentIOLibrary {

  @Importer(
      schema = "jar.import",
      knowledgeClass = KlabAsset.KnowledgeClass.COMPONENT,
      description = "Import a component by directly uploading a kar file",
      mediaType = "application/java-archive",
      fileExtensions = {"kar"})
  public static ResourceSet importComponentDirect(File file, BaseService service, Scope scope) {

    // TODO this should load the plug-in, and if successful, copy the kar in the component registry,
    //  without setting it into the Maven cache at all. This can only be updated by uploading a new
    //  kar for the same component.
    if (file != null && file.exists()) {
      var componentRegistry = service.getComponentRegistry();
    }

    return ResourceSet.empty(Notification.error("Jar import not yet implemented"));
  }

  /**
   * Maven import also registers the component's rights if the service it's registered with is a
   * resources service. For now direct file import does not.
   *
   * @param properties
   * @param service
   * @param scope
   * @return
   */
  @Importer(
      schema = "maven.import",
      knowledgeClass = KlabAsset.KnowledgeClass.COMPONENT,
      description = "Register a component using the archive's Maven coordinates",
      properties = {
        @KlabFunction.Argument(
            name = "groupId",
            type = Artifact.Type.TEXT,
            description = "Maven group ID"),
        @KlabFunction.Argument(
            name = "artifactId",
            type = Artifact.Type.TEXT,
            description = "Maven artifact ID"),
        @KlabFunction.Argument(
            name = "version",
            type = Artifact.Type.TEXT,
            description = "Maven version"),
        @KlabFunction.Argument(
            name = "repository",
            type = Artifact.Type.TEXT,
            description = "Non-standard Maven repository",
            optional = true)
      })
  public static ResourceSet importComponentMaven(
      Parameters<String> properties, BaseService service, Scope scope) {

    try {

      File file =
          service
              .getComponentRegistry()
              .getComponentCache()
              .synchronizeArtifact(
                  properties.get("groupId", String.class),
                  properties.get("artifactId", String.class),
                  properties.get("version", String.class),
                  "component",
                  "kar"); // TODO

      if (file != null && file.exists()) {
        var componentRegistry = service.getComponentRegistry();
        var ret =
            componentRegistry.registerComponent(
                file,
                properties.get("groupId")
                    + ":"
                    + properties.get("artifactId")
                    + ":"
                    + properties.get("version"));

        if (ret != null && service instanceof ResourcesService resourcesService) {
          Version version =
              properties.containsKey("version")
                  ? Version.create(properties.get("version", String.class))
                  : Version.ANY_VERSION;
          var component = componentRegistry.getComponent(ret, version);
          // TODO if the component comes with explicit access rights, record them
          var info =
              resourcesService.registerResource(
                  component.id(),
                  KlabAsset.KnowledgeClass.COMPONENT,
                  component.sourceArchive(),
                  scope);
          var result =
              ResourceSet.of(
                  info, component.version() != null ? component.version() : Version.ANY_VERSION);
          result
              .getNotifications()
              .add(
                  Notification.info(
                      "Import of component "
                          + component.id()
                          + " successful with version "
                          + component.version()));
          return result;
        }
      } else {
        return ResourceSet.empty(
            Notification.error(
                "Maven artifact "
                    + properties.get("groupId")
                    + ":"
                    + properties.get("artifactId")
                    + ":"
                    + properties.get("version")
                    + " not found in configured repositories"));
      }
    } catch (Throwable t) {
      return ResourceSet.empty(Notification.error("Component import failed: ", t.getMessage()));
    }

    return null;
  }

  @Exporter(
      schema = "jar.export",
      description = "Export a component as a jar archive",
      mediaType = "application" + "/java-archive",
      knowledgeClass = KlabAsset.KnowledgeClass.COMPONENT)
  public static InputStream exportComponentDirect(
      String componentId, BaseService service, Scope scope) {
    var componentRegistry = service.getComponentRegistry();
    var version = Version.splitVersion(componentId);
    var component = componentRegistry.getComponent(version.getFirst(), version.getSecond());
    if (component.sourceArchive() != null) {
      try {
        return new FileInputStream(component.sourceArchive());
      } catch (FileNotFoundException e) {
        Logging.INSTANCE.error(e.getMessage(), e);
        // just return null;
      }
    }
    return null;
  }
}
