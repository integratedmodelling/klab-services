package org.integratedmodelling.klab.runtime.libraries;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.resources.adapters.Exporter;
import org.integratedmodelling.klab.api.services.resources.adapters.Importer;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;

import java.io.InputStream;

@Library(name = "component", description = "Importers for components shared by all services" +
        "extents.")
public class ComponentIOLibrary {

    @Importer(schema = "jar", knowledgeClass = KlabAsset.KnowledgeClass.COMPONENT, description = "")
    public static String importComponentDirect() {
        return null;
    }

    @Importer(schema = "maven", knowledgeClass = KlabAsset.KnowledgeClass.COMPONENT, description = "",
              properties = {
                    @KlabFunction.Argument(name = "groupId", type = Artifact.Type.TEXT, description = ""),
                    @KlabFunction.Argument(name = "artifactId", type = Artifact.Type.TEXT, description = ""),
                    @KlabFunction.Argument(name = "version", type = Artifact.Type.TEXT, description = "")
              })
    public static String importComponentMaven(Parameters<String> properties) {
        return null;
    }

    @Exporter(schema = "jar", description = "", mediaType = "", knowledgeClass =
            KlabAsset.KnowledgeClass.COMPONENT)
    public static InputStream exportComponentDirect(String componentId) {
        return null;
    }
}
