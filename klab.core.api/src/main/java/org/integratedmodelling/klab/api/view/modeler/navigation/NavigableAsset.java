package org.integratedmodelling.klab.api.view.modeler.navigation;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;

import java.util.List;

/**
 * A {@link KlabAsset} that makes it possible to navigate back and forth in a hierarchy of containment. Meant
 * for knowledge organization within a visual interface to resource sets.
 */
public interface NavigableAsset extends KlabAsset {

    /**
     * A list of all child assets, or an empty list if no children exist.
     *
     * @return
     */
    List<? extends NavigableAsset> children();

    NavigableAsset parent();

    NavigableContainer root();

    /**
     * View-side metadata that can be added and removed on the local instance and do not affect the delegate
     * object. Can be used by the UI to store local info about each asset.
     *
     * @return
     */
    Metadata localMetadata();

}
