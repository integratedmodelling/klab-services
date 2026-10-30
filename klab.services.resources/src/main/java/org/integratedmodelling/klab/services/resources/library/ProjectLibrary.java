package org.integratedmodelling.klab.services.resources.library;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.adapters.Importer;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
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
            name = "project",
            type = Artifact.Type.TEXT,
            description = "Expected project name; checked against the repository name",
            optional = true),
        @KlabFunction.Argument(
            name = "credentialId",
            type = Artifact.Type.TEXT,
            description = "ID of an authorized credential set in this service's vault",
            optional = true)
      })
  public static ResourceSet importProjectGit(
      Parameters<String> properties, ResourcesProvider service, UserScope scope) {

    var url = properties.get("url").toString();
    var uri = java.net.URI.create(url);
    if (uri.getUserInfo() != null || uri.getQuery() != null
        || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
      throw new IllegalArgumentException("Git imports require HTTP(S) coordinates without embedded credentials");
    var expected = properties.get("project");
    if (expected != null && !expected.toString().equals(org.integratedmodelling.klab.utilities.Utils.Git.repositoryName(url)))
      throw new IllegalArgumentException("Repository name does not match the project name");

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
        service.importProject(workspace.getUrn(), url, false, scope,
            properties.get("credentialId") == null ? null : properties.get("credentialId").toString());

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

      return org.integratedmodelling.klab.utilities.Utils.Resources.merge(ret.toArray(ResourceSet[]::new));
    }

    return null;
  }

  @Importer(
      schema = "zip.import",
      knowledgeClass = KlabAsset.KnowledgeClass.PROJECT,
      description = "Register a project using a zip archive",
      mediaType = "application/zip",
      fileExtensions = {"zip"})
  public static ResourceSet importProjectZip(File archive, Parameters<String> parameters,
      ResourcesProvider service, UserScope scope) {
    var expected = parameters == null ? null : parameters.get("URN");
    if (expected != null) {
      try {
        if (!expected.toString().equals(org.integratedmodelling.klab.resources.ProjectArchiveIO.projectName(archive.toPath())))
          throw new IllegalArgumentException("Archive does not contain the requested project");
      } catch (java.io.IOException error) {
        throw new org.integratedmodelling.klab.api.exceptions.KlabIOException(error);
      }
    }
    var workspace = "local";
    if (service.retrieveWorkspace(workspace, scope) == null
        && !service.createWorkspace(workspace, Metadata.create(), scope))
      throw new IllegalStateException("Cannot create local workspace");
    var results = service.importProject(workspace, archive.toURI().toString(), false, scope);
    var result = org.integratedmodelling.klab.utilities.Utils.Resources.merge(results.toArray(ResourceSet[]::new));
    result.setWorkspace(workspace);
    return result;
  }

  @org.integratedmodelling.klab.api.services.resources.adapters.Exporter(
      schema = "zip.export", knowledgeClass = KlabAsset.KnowledgeClass.PROJECT,
      description = "Export project files without service credentials or Git configuration",
      mediaType = "application/zip", fileExtensions = {"zip"})
  public static InputStream exportProjectZip(String project, ResourcesProvider service, UserScope scope) {
    return service.exportProjectArchive(project, scope);
  }
}
