package org.integratedmodelling.klab.api.knowledge.organization;

import java.io.Serializable;
import java.net.URL;
import java.util.Collection;

public interface Workspace extends Serializable {
    
    /**
     * Name of the workspace. May or may not be linked to the name of the root directory.
     * 
     * @return workspace name.
     */
    String getName();

    /**
     * All projects managed under this workspace.
     * 
     * @return all project names
     */
    Collection<Project> getProjects();

    /**
     * The URL for the workspace. With content type JSON and proper authorization it should return
     * the parsed projects.
     * 
     * @return the workspace URL.
     */
    URL getURL();

}
