package org.integratedmodelling.klab.api.view.modeler.navigation;

/**
 * A {@code NavigableFolder} is an named organizer (the name is returned by {@link #getResourceUrn()}) that contains
 * other assets but has no relation with the actual resource structure. Folders can be inserted in navigable
 * hierarchies and named but {@link #findAsset(String, Class)} and related methods will simply skip them.
 */
public interface NavigableFolder extends NavigableContainer {
}
