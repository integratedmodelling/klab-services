package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableFolder;

public abstract class NavigableFolderImpl<T extends KlabAsset> extends NavigableKlabAsset<T> implements NavigableFolder {

    private String name;
    public Metadata metadata = Metadata.create();

    public NavigableFolderImpl(String name, NavigableKlabAsset<?> parent) {
        super(name, parent);
        this.name = name;
    }

    @Override
    public String getUrn() {
        return name;
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }
}
