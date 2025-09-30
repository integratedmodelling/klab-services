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
import org.integratedmodelling.klab.services.base.BaseService;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Library(
    name = "component",
    description = "Importers for components shared by all services",
    version = Version.CURRENT)
public class ComponentIOLibrary {

  @Importer(
          schema = "kar.import",
          knowledgeClass = KlabAsset.KnowledgeClass.COMPONENT,
          description = "Import a component by directly uploading a kar file",
          mediaType = "application/java-archive",
          fileExtensions = {"kar"})
  public static ResourceSet importComponentDirect(Parameters<String> properties, File file, BaseService service, Scope scope) {

    // TODO this should load the plug-in, and if successful, copy the kar in the component registry,
    //  without setting it into the Maven cache at all. This can only be updated by uploading a new
    //  kar for the same component.
    if (file == null || !file.exists()) {
      return ResourceSet.empty(Notification.error("Non existing .kar file."));
    }

    String groupId = "org.integratedmodelling";
    String artifactId = "";
    String version = Version.EMPTY_VERSION.toString();

    String manifestPath = "META-INF/MANIFEST.MF";

    try {
      ZipFile zipFile = new ZipFile(file.getAbsolutePath());
      ZipEntry entry = zipFile.getEntry(manifestPath);

      if (entry == null) {
        System.err.println("Entry not found: " + manifestPath);
        return ResourceSet.empty(Notification.error("Cannot find definition of .kar file."));
      }

      InputStream inputStream = zipFile.getInputStream(entry);
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      String line;

      while ((line = reader.readLine()) != null) {
        if (line.startsWith("Plugin-Id:")) {
          artifactId = line.substring(line.indexOf(":") + 1).trim();
          continue;
        }
        if (line.startsWith("Plugin-Vendor-Id:")) {
          groupId = line.substring(line.indexOf(":") + 1).trim();
          continue;
        }
        if (line.startsWith("Plugin-Version:")) {
          version = line.substring(line.indexOf(":") + 1).trim();
        }
      }

      reader.close();
      zipFile.close();
    } catch (IOException e) {
      System.err.println("Exception while reading kar file: " + e.getMessage());
    }

    var mavenCoordinates = groupId + ":" + artifactId + ":" + version;
    var componentRegistry = service.getComponentRegistry();
    var result = componentRegistry.installComponent(file, mavenCoordinates);
    if (result != null) {
      return result.getSecond();
    }

    return ResourceSet.empty(Notification.error("Kar import not yet implemented"));
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
      var componentRegistry = service.getComponentRegistry();
      var ret =
          componentRegistry.installMavenComponent(
              properties.get("groupId", String.class),
              properties.get("artifactId", String.class),
              properties.get("version", String.class));

      if (ret != null && service instanceof ResourcesService resourcesService) {

        var component = ret.getFirst();
        // TODO record the rights in the ResourcesKBox
        var info =
            resourcesService.registerResource(
                component.id(),
                KlabAsset.KnowledgeClass.COMPONENT,
                component.sourceArchive(),
                component.usageRights(),
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
      if (ret == null) {
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
      mediaType = "application/java-archive",
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
