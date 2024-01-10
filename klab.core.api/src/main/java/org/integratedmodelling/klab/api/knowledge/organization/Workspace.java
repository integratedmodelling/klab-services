package org.integratedmodelling.klab.api.knowledge.organization;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;

import java.io.Serializable;
import java.util.Collection;

public interface Workspace extends KlabAsset {

    /**
     * All projects managed under this workspace.
     * 
     * @return all project names
     */
    Collection<Project> getProjects();

}
