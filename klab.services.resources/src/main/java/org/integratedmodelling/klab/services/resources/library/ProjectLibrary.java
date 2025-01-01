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

@Library(
    name = "project",
    description = "Import and export of k.IM projects",
    version = Version.CURRENT)
public class ProjectLibrary {

    @Importer(schema = "git", knowledgeClass = KlabAsset.KnowledgeClass.COMPONENT,
              description = "Register a component using the archive's Git coordinates",
              properties = {
                      @KlabFunction.Argument(name = "url", type = Artifact.Type.TEXT,
                                             description = "Git https:// URL"),
                      @KlabFunction.Argument(name = "username", type = Artifact.Type.TEXT,
                                             description = "Maven artifact ID", optional = true),
                      @KlabFunction.Argument(name = "password", type = Artifact.Type.TEXT,
                                             description = "Maven version", optional = true),
                      @KlabFunction.Argument(name = "accessToken", type = Artifact.Type.TEXT,
                                             description = "Non-standard Maven repository", optional = true)
              })
    public static String importComponentMaven(Parameters<String> properties, BaseService service,
                                              Scope scope) {
        return null;
    }

}
