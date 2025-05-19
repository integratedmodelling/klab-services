package org.integratedmodelling.klab.runtime.libraries;

import org.apache.commons.codec.binary.Base16InputStream;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.adapters.Exporter;
import org.integratedmodelling.klab.api.services.resources.adapters.Importer;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.utilities.Utils;

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
      description = "Import a component by directly uploading a jar file",
      mediaType = "application/java-archive",
      fileExtensions = {"jar"})
  public static String importComponentDirect(File file, BaseService service, Scope scope) {

    if (file != null && file.exists()) {
      var componentRegistry = service.getComponentRegistry();
    }

    return null;
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
  public static String importComponentMaven(
      Parameters<String> properties, BaseService service, Scope scope) {

    var file =
        Utils.Maven.synchronizeArtifact(
            properties.get("groupId", String.class),
            properties.get("artifactId", String.class),
            properties.get("version", String.class),
            true);

    if (file != null && file.exists()) {
      var componentRegistry = service.getComponentRegistry();
      var ret =
          componentRegistry.registerComponent(
              file,
              properties.get("groupId")
                  + ":"
                  + properties.get("artifactId")
                  + ":"
                  + properties.get("version"),
              scope);

      if (ret != null && service instanceof ResourcesService resourcesService) {
        Version version =
            properties.containsKey("version")
                ? Version.create(properties.get("version", String.class))
                : Version.ANY_VERSION;
        var component = componentRegistry.getComponent(ret, version);
        // TODO if the component comes with explicit access rights, record them
        resourcesService.registerResource(
            component.id(), KlabAsset.KnowledgeClass.COMPONENT, component.sourceArchive(), scope);
        return ret;
      }
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
        System.out.println("SUCA DIO CAN, CANNOT STREAM " + component.sourceArchive());
        // just return null;
      }
    }
    return null;
  }
}
