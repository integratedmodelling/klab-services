package org.integratedmodelling.klab.api.knowledge.organization;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;

import java.util.Collection;

public interface Workspace extends KlabAsset {

    String EXTERNAL_WORKSPACE_URN = "__EXTERNAL_WORKSPACE__";

    /**
     * All projects managed under this workspace.
     * 
     * @return all project names
     */
    Collection<Project> getProjects();

}
