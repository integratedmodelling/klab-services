package org.integratedmodelling.klab.api.view.modeler.navigation;

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

}
