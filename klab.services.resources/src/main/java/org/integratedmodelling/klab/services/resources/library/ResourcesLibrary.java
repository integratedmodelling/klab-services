package org.integratedmodelling.klab.services.resources.library;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.adapters.Importer;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

import java.io.File;
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
  public static String importLegacyResourceJson(
      File archive, ResourcesProvider service, UserScope scope) {
    return null;
  }

  @Importer(
      schema = "legacy.zip",
      knowledgeClass = KlabAsset.KnowledgeClass.PROJECT,
      description = "Register a new resource by importing a zip file with legacy content",
      mediaType = "application/zip",
      fileExtensions = {"zip"})
  public static String importLegacyResourceZip(
      File archive, ResourcesProvider service, UserScope scope) {
    return null;
  }

  @Importer(
      schema = "json",
      knowledgeClass = KlabAsset.KnowledgeClass.PROJECT,
      description =
          "Register a new resource without content by importing a compliant json manifest",
      mediaType = "application/json",
      fileExtensions = {"json"})
  public static String importResourceJson(
      File archive, ResourcesProvider service, UserScope scope) {
    var definition = Utils.Json.load(archive, Map.class);
    if (definition != null) {
      return loadLegacyResource(definition, service, scope);
    }
    return null;
  }

  @Importer(
      schema = "zip",
      knowledgeClass = KlabAsset.KnowledgeClass.PROJECT,
      description =
          "Register a new resource by importing a zip file with full content, including the json manifest",
      mediaType = "application/zip",
      fileExtensions = {"zip"})
  public static String importResourceZip(File archive, ResourcesProvider service, UserScope scope) {
    return null;
  }


  private static String loadLegacyResource(
          Map definition, ResourcesProvider service, UserScope scope) {
    return null;
  }


}
