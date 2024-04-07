package org.integratedmodelling.klab.api.view;

/**
 * Tag interface for the top-level, view-side UI object. The main {@link UIController} representing the
 * top-level application will take the UI as parameter and dispatch any events that are registered by methods
 * in its interface.
 */
public interface UI {

    void cleanWorkspace();

}
