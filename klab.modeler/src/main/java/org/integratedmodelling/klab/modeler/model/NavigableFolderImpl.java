package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;

public abstract class NavigableFolderImpl<T extends KlabAsset> extends NavigableKlabAsset<T> implements NavigableContainer {

    public NavigableFolderImpl(String name, NavigableKlabAsset<?> parent) {
        super(name, parent);
    }
}
