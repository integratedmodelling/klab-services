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

    /**
     * Return the direct parent of this asset.
     *
     * @return the asset's parent. If this is the root asset, return null.
     */
    NavigableAsset parent();

    /**
     * Return the first parent of the requested class.
     *
     * @param parentClass
     * @param <T>
     * @return the requested parent or null.
     */
    <T extends NavigableAsset> T parent(Class<T> parentClass);

    /**
     * Return the root container for this asset, normally a workspace or worldview. If this is the root
     * container, return this.
     *
     * @return the root container.
     */
    NavigableContainer root();

    /**
     * View-side metadata that can be added and removed on the local instance and do not affect the delegate
     * object. Can be used by the UI to store local info about each asset.
     *
     * @return
     */
    Metadata localMetadata();

    /**
     * Find an asset recursively. The assetClass is used for matching; the result class isn't checked and is
     * only to avoid casting, so a {@link ClassCastException} may be thrown if used incorrectly.
     *
     * @param resourceUrn
     * @param assetClass
     * @param resultClass
     * @return
     * @param <T>
     */
    <T extends KlabAsset> T findAsset(String resourceUrn, KnowledgeClass assetClass, Class<T> resultClass);
}
