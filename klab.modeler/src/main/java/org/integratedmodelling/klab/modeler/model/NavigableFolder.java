package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;

import java.util.List;

public class NavigableFolder implements NavigableContainer {

    @Override
    public boolean mergeChanges(ResourceSet changes, Scope scope) {
        return false;
    }

    @Override
    public List<? extends NavigableAsset> children() {
        return List.of();
    }

    @Override
    public NavigableAsset parent() {
        return null;
    }

    @Override
    public <T extends NavigableAsset> T parent(Class<T> parentClass) {
        return null;
    }

    @Override
    public NavigableContainer root() {
        return null;
    }

    @Override
    public Metadata localMetadata() {
        return null;
    }

    @Override
    public <T extends KlabAsset> T findAsset(String resourceUrn, Class<T> assetClass) {
        return null;
    }

    @Override
    public String getUrn() {
        return "";
    }

    @Override
    public Metadata getMetadata() {
        return null;
    }
}
