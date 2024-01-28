package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.Collection;
import java.util.List;
import java.util.Set;

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
public abstract interface KlabDocument<T extends Statement> extends KlabAsset {

    interface DocumentVisitor<T extends Statement> extends KlabStatement.KlabStatementVisitor {

        /**
         * Visit the preamble of the document (not the statements, handled separately).
         *
         * @param document
         */
        void visitDocument(KlabDocument<T> document);

        /**
         * Visit an individual statement.
         *
         * @param statement
         */
        void visitStatement(T statement);
    }

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
     * This should never be null.
     *
     * @return
     */
    Version getVersion();

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

    /**
     * Visit the document and return all the imported namespace URNs, either explicit (like in ontologies) or
     * implicit (like in namespaces, strategies or behaviors using concept expressions).
     *
     * @param withinType if true, limit the dependency analysis to the imported documents of the same type.
     *                   Otherwise, add all dependencies including the ontologies from any concepts
     *                   mentioned.
     * @return
     */
    Set<String> importedNamespaces(boolean withinType);

    /**
     * Visit the document and each statement.
     *
     * @param visitor
     */
    void visit(DocumentVisitor<T> visitor);
}
