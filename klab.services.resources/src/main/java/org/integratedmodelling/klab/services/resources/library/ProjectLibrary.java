package org.integratedmodelling.klab.services.resources.library;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.adapters.Importer;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

import java.io.File;
import java.io.InputStream;

@Library(
    name = "project",
    description = "Import and export of k.IM projects",
    version = Version.CURRENT)
public class ProjectLibrary {

  @Importer(
      schema = "git.import",
      knowledgeClass = KlabAsset.KnowledgeClass.PROJECT,
      description = "Register a project using its Git coordinates",
      properties = {
        @KlabFunction.Argument(
            name = "url",
            type = Artifact.Type.TEXT,
            description = "Git https:// URL for the project to import"),
        @KlabFunction.Argument(
            name = "workspace",
            type = Artifact.Type.TEXT,
            description = "The workspace in which to import (will be created if not existing)",
            optional = false),
        @KlabFunction.Argument(
            name = "username",
            type = Artifact.Type.TEXT,
            description = "Git username if needed",
            optional = true),
        @KlabFunction.Argument(
            name = "password",
            type = Artifact.Type.TEXT,
            description = "Git password for the username",
            optional = true),
        @KlabFunction.Argument(
            name = "accessToken",
            type = Artifact.Type.TEXT,
            description = "Git access token",
            optional = true)
      })
  public static String importProjectGit(
      Parameters<String> properties, ResourcesProvider service, UserScope scope) {

    Logging.INSTANCE.info(
        "Importing project from Git repository: "
            + properties.get("url")
            + " on service "
            + service.getUrl()
            + " as user "
            + scope.getUser().getUsername());

    var workspace = service.retrieveWorkspace(properties.get("workspace").toString(), scope);
    if (workspace == null) {
      Logging.INSTANCE.info("Creating new workspace " + properties.get("workspace"));
      if (!service.createWorkspace(
          properties.get("workspace").toString(), Metadata.create(), scope)) {
        Logging.INSTANCE.error("Could not create workspace " + properties.get("workspace"));
        return null;
      }
      workspace = service.retrieveWorkspace(properties.get("workspace").toString(), scope);
    }

    var ret =
        service.importProject(workspace.getUrn(), properties.get("url").toString(), true, scope);

    if (ret.isEmpty()) {
      Logging.INSTANCE.error(
          "Project import failed: Git repository: "
              + properties.get("url")
              + " on service "
              + service.getUrl()
              + " as user "
              + scope.getUser().getUsername());
    } else {
      Logging.INSTANCE.info(
          "Project import successful: Git repository: "
              + properties.get("url")
              + " on service "
              + service.getUrl()
              + " as user "
              + scope.getUser().getUsername());

      return ret.getFirst().getResults().isEmpty()
          ? null // should never happen
          : ret.getFirst().getResults().iterator().next().getResourceUrn();
    }

    return null;
  }

  @Importer(
      schema = "zip.import",
      knowledgeClass = KlabAsset.KnowledgeClass.PROJECT,
      description = "Register a project using a zip archive",
      mediaType = "application/zip",
      fileExtensions = {"zip"})
  public static String importProjectZip(File archive, BaseService service, Scope scope) {
    return null;
  }
}
