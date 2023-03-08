package org.integratedmodelling.klab.api.knowledge.organization;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;

public interface KProject extends Serializable {

    /**
     * 
     * @return
     */
    String getDefinedWorldview();

    /**
     * 
     * @return
     */
    String getName();

    /**
     * The URL for the project. With content type JSON and proper authorization it should return the
     * parsed projects.
     * 
     * @return the workspace URL.
     */
    URL getURL();

    /**
     * 
     * @return
     */
    String getWorldview();

    /**
     * 
     * @param id
     * @return
     */
    KimNamespace getNamespace(String id);

    /**
     * 
     * @return
     */
    Collection<String> getRequiredProjectNames();

    /**
     * 
     * @return
     */
    Properties getProperties();

    /**
     * 
     * @return
     */
    List<KimNamespace> getNamespaces();

    /**
     * All the legitimate behaviors (in the source files)
     * 
     * @return
     */
    List<KActorsBehavior> getBehaviors();

    /**
     * All the behaviors in the apps directory (which may also contain k.IM scripts). Includes apps
     * and components but not other types of declared behavior.
     * 
     * @return
     */
    List<KActorsBehavior> getApps();

    /**
     * All the behaviors in the tests directory (which may also contain k.IM scripts).
     * 
     * @return
     */
    List<KActorsBehavior> getTests();

    /**
     * 
     * @return
     */
    boolean isErrors();

    boolean isWarnings();

    boolean isOpen();
}
