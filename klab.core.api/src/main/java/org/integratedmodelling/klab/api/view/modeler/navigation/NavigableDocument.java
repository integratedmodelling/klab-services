package org.integratedmodelling.klab.api.view.modeler.navigation;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.List;

public interface NavigableDocument extends NavigableAsset {

    /**
     * Metadata keys for local metadata, changed by interacting with the services.
     */
    String ERROR_NOTIFICATION_COUNT_KEY = "klab.error.notifications.count";
    String WARNING_NOTIFICATION_COUNT_KEY = "klab.error.notifications.count";
    String INFO_NOTIFICATION_COUNT_KEY = "klab.error.notifications.count";

    /**
     * Any document can live in a file which should have the extension returned here.
     */
    String getFileExtension();


    /**
     * Given a position, return the list of assets that leads there. If the position is in blank space between
     * root-level assets, return an empty list. This should only return up to the most inner "navigable" leaf,
     * i.e. the statement, as it's used to locate the position in a navigable tree.
     *
     * @param offset offset in document
     * @return the navigable assets leading to either the offset or the empty list.
     */
    List<NavigableAsset> getAssetsAt(int offset);

    /**
     * This one returns the closest asset to the position, i.e. works like {@link #getAssetsAt(int)} unless
     * the position is in blank space.
     *
     * @param offset offset in document
     * @return the navigable assets leading to either the offset or the closest asset.
     */
    List<NavigableAsset> getClosestAsset(int offset);

    /**
     * Use a visitor to return the hierarchy of statements that include the passed offset in the
     * document, from the outermost to the innermost.
     *
     * @param offset
     * @return
     */
    List<Statement> getStatementPath(int offset);

    /**
     * Merge in new metadata or notifications. Won't change the structure of the contents but may change
     * errors, warnings, and other info associated with them.
     *
     * @param metadata
     * @param notifications
     */
    void mergeMetadata(Metadata metadata, List<Notification> notifications);

}
