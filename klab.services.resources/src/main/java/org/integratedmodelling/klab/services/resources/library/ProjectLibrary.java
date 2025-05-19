package org.integratedmodelling.klab.services.resources.library;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.adapters.Importer;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.services.base.BaseService;

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
            description = "Git https:// URL"),
        @KlabFunction.Argument(
            name = "username",
            type = Artifact.Type.TEXT,
            description = "Git username",
            optional = true),
        @KlabFunction.Argument(
            name = "password",
            type = Artifact.Type.TEXT,
            description = "Git password",
            optional = true),
        @KlabFunction.Argument(
            name = "accessToken",
            type = Artifact.Type.TEXT,
            description = "Git access token",
            optional = true)
      })
  public static String importProjectGit(
      Parameters<String> properties, BaseService service, Scope scope) {
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
