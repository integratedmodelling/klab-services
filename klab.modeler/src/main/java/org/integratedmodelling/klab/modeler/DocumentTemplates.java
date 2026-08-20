package org.integratedmodelling.klab.modeler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;

/** Client-side source templates used to create semantic documents through a resources service. */
final class DocumentTemplates {

  private static final String TEMPLATE_ROOT = "/org/integratedmodelling/klab/modeler/templates/";

  private static final Map<ProjectStorage.ResourceType, String> TEMPLATES =
      Map.of(
          ProjectStorage.ResourceType.ONTOLOGY, "ontology.kwv",
          ProjectStorage.ResourceType.MODEL_NAMESPACE, "namespace.kim",
          ProjectStorage.ResourceType.STRATEGY, "strategies.obs",
          ProjectStorage.ResourceType.BEHAVIOR, "behavior.kactors",
          ProjectStorage.ResourceType.BEHAVIOR_COMPONENT, "component.kactors",
          ProjectStorage.ResourceType.APPLICATION, "application.kactors",
          ProjectStorage.ResourceType.SCRIPT, "script.kactors",
          ProjectStorage.ResourceType.TESTCASE, "testcase.kactors");

  private DocumentTemplates() {}

  static String render(ProjectStorage.ResourceType resourceType, String urn) {
    var templateName = TEMPLATES.get(resourceType);
    if (templateName == null) {
      throw new KlabUnimplementedException(
          "No client document template for resource type " + resourceType);
    }
    try (var input = DocumentTemplates.class.getResourceAsStream(TEMPLATE_ROOT + templateName)) {
      if (input == null) {
        throw new KlabIOException("Missing document template " + templateName);
      }
      return Utils.Templates.substitute(
          new String(input.readAllBytes(), StandardCharsets.UTF_8), "urn", urn);
    } catch (IOException e) {
      throw new KlabIOException("Cannot read document template " + templateName, e);
    }
  }

  /**
   * ResourcesService.parseAsset accepts a URL. This keeps generated source client-local and avoids
   * creating a temporary file just to pass source text to the service client.
   */
  static URL renderUrl(ProjectStorage.ResourceType resourceType, String urn) {
    return sourceUrl(resourceType, urn, render(resourceType, urn));
  }

  /** Expose edited source to {@link org.integratedmodelling.klab.api.services.ResourcesService}. */
  static URL sourceUrl(ProjectStorage.ResourceType resourceType, String urn, String source) {
    var contents = source.getBytes(StandardCharsets.UTF_8);
    try {
      return new URL(
          null,
          "memory:/" + resourceType.name().toLowerCase() + "/" + urn,
          new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
              return new URLConnection(url) {
                @Override
                public void connect() {}

                @Override
                public InputStream getInputStream() {
                  return new ByteArrayInputStream(contents);
                }
              };
            }
          });
    } catch (IOException e) {
      throw new KlabIOException("Cannot expose generated source for " + urn, e);
    }
  }
}
