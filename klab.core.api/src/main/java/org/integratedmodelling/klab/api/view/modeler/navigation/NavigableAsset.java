package org.integratedmodelling.klab.api.view.modeler.navigation;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;

import java.util.List;

/**
 * A {@link KlabAsset} that makes it possible to navigate back and forth in a hierarchy of containment. Meant
 * for knowledge organization within a visual interface to resource sets.
 */
public interface NavigableAsset extends KlabAsset {

    /*
     * Metadata keys for local metadata, changed by interacting with the services. These are propagated
     * across the asset hierarchy by explicitly calling functions  on
     * the navigable objects after wrapping or updating the original assets. The original data from the
     * Resources service only contain info (notifications and repository state) within the individual
     * objects affected.
     */

    /**
     * Key for metadata available through {@link #localMetadata()}. If null, assume 0. Count of error
     * notifications in asset, cumulated along containment hierarchy.
     */
    String ERROR_NOTIFICATION_COUNT_KEY = "klab.error.notifications.count";

    /**
     * Key for metadata available through {@link #localMetadata()}. If null, assume 0. Count of warning
     * notifications in asset, cumulated along containment hierarchy.
     */
    String WARNING_NOTIFICATION_COUNT_KEY = "klab.warning.notifications.count";

    /**
     * Key for metadata available through {@link #localMetadata()}. If null, assume 0. Count of informational
     * notifications in asset, cumulated along containment hierarchy.
     */
    String INFO_NOTIFICATION_COUNT_KEY = "klab.info.notifications.count";

    /**
     * Key for metadata available through {@link #localMetadata()}. If null, assume no status because there is
     * no official remote repository.
     * <p>
     * Status of asset re: the official remote repository (origin). For containers, reflects the status of the
     * contained assets.
     */
    String REPOSITORY_STATUS_KEY = "klab.repository.status";

    /**
     * In navigable containers representing projects that have a repository, this will be set in
     * {@link #localMetadata()} to the name of the branch currently represented in the assets.
     */
    String REPOSITORY_CURRENT_BRANCH_KEY = "klab.repository.current.branch";

    /**
     * In navigable containers representing projects that have a repository, this will be set in
     * {@link #localMetadata()} to the name of all branches currently available in the repository.
     *
     * @return
     */
    String REPOSITORY_AVAILABLE_BRANCHES_KEY = "klab.repository.available.branches";
    
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
     * @param resultClass
     * @param assetType   one or more types to match
     * @param <T>
     * @return
     */
    <T extends KlabAsset> T findAsset(String resourceUrn, Class<T> resultClass, KnowledgeClass... assetType);
}
