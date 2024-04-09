package org.integratedmodelling.klab.api.view.modeler.navigation;

import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

public interface NavigableContainer extends NavigableAsset {

    /**
     * Incrementally apply all the changes in the passed resource set, modifying the container.
     * <p>
     * This one should reload any asset that is listed in the service. It sbould never be called on a
     * container whose URN is not the same as the workspace URN in the changeset.
     *
     * @param changes the changeset we need to incorporate
     * @param scope   this is needed to find the service in each change. If not found, the change should not
     *                be made and the scope should register a warning.
     * @return true if any changes were made
     */
    public boolean mergeChanges(ResourceSet changes, Scope scope);

}
