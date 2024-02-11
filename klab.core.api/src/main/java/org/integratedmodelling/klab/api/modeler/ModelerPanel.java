package org.integratedmodelling.klab.api.modeler;

/**
 * Panels are used to configure one specific object and are brought up and removed as needed, normally
 * occupying the central part of the UI or a modal window.
 */
public interface ModelerPanel<T> extends UIReactor {

    /**
     * Loading the object handled must also show the panel and bring it in focus, which should have an OK/Save
     * action associated that hides the panel if it's implemented as a modal.
     *
     * @param payload the object handled by this panel. If it's a creation, it should be null.
     */
    void load(T payload);

    /**
     * Submit the object after successful editing/saving. Not called if the panel is canceled.
     *
     * @return
     */
    T submitOnSave();

}
