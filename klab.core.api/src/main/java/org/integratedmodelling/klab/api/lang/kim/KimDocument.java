package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A KimDocument is a k.LAB asset that originates as an individual document edited by a contributor and may be
 * incarnated into a file resource. It will contain a sequence of statements in an editing order that should
 * be reconstructed as needed, although it may not represent the "natural" order. It also has a source code in
 * some k.LAB-supported language, and can be used to reconstruct the source code of the document or of the
 * contained statements. It holds creation and last access dates and is always part of a project. Namespaces,
 * ontologies, observation strategy specifications and serialized dataflows are documents.
 *
 * @author ferdinando.villa
 */
public abstract interface KimDocument<T extends KimStatement> extends KlabAsset {

    /**
     * The timestamp of creation of the namespace object, set at creation and immutable after that.
     *
     * @return time of creation
     */
    long getCreationTimestamp();

    /**
     * Timestamp of the last update.
     *
     * @return
     */
    long getLastUpdateTimestamp();

    /**
     * True if declared as void through any means of the underlying specs or after unsuccessful validation.
     *
     * @return
     */
    boolean isInactive();

    /**
     * Return all the top-level statements in order of definition.
     *
     * @return
     */
    List<T> getStatements();

    /**
     * Notifications during compilation. If any of the contained objects have errors, there should be one
     * overall notification that ensures errors are visible at the namespace level.
     *
     * @return
     */
    Collection<Notification> getNotifications();

    /**
     * Most documents are part of projects although some may not, so the returned project name may be null.
     *
     * @return the project name or null.
     */
    String getProjectName();

    /**
     * Produce the source code that generated this document.
     *
     * @return
     */
    String getSourceCode();

}
