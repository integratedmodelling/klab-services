package org.integratedmodelling.klab.api.view;

/**
 * Base class for all views in the modeler. These are shown in the UI permanently and reconfigure themselves
 * based on the UI events they receive, unlike the "panels" that are shown one-off to edit a specific object.
 *
 * Provides common methods to initialize and react to UI events.
 */
public interface View extends UIReactor {

}
