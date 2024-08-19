package org.integratedmodelling.klab.modeler.model;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Repository;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;

public abstract class NavigableKlabDocument<E extends Statement, T extends KlabDocument<E>>
        extends NavigableKlabAsset<T> implements KlabDocument<E>, NavigableDocument {

    @Serial
    private static final long serialVersionUID = 7741675272275189813L;

    /**
     * Use to inject implementation-specific instrumentation
     *
     * @return
     */
    public Parameters<String> getParameters() {
        return parameters;
    }

    public void setParameters(Parameters<String> parameters) {
        this.parameters = parameters;
    }

    protected Parameters<String> parameters = Parameters.create();


    public NavigableKlabDocument(T document, NavigableKlabAsset<?> parent) {
        super(document, parent);
        //		if (parent instanceof NavigableKlabAsset asset && asset.getResource() instanceof IContainer
        //		container) {
        //			this.resource = Eclipse.INSTANCE.findFileInContainer(container, getFileName());
        //		}
    }


    public String getFileExtension() {
        return switch (getDelegate()) {
            case KimOntology ontology -> "kwv";
            case KimNamespace ontology -> "kim";
            case KimObservationStrategyDocument ontology -> "obs";
            case KActorsBehavior ontology -> "kactors";
            default ->
                    throw new KlabUnimplementedException("file extension for document of class " + getDelegate().getClass().getCanonicalName());
        };
    }

    @Override
    protected List<? extends NavigableAsset> createChildren() {
        return (List<? extends NavigableKlabStatement>) getStatements();
    }

    /**
     * Get a filename with the proper file name based on the URN, document type and location.
     *
     * @return
     */
    public String getFileName() {
        return delegate.getUrn() + switch (delegate) {
            case KimOntology ontology -> ".kwv";
            case KimNamespace namespace -> ".kim";
            case KActorsBehavior behavior -> ".kactors";
            case KimObservationStrategy strategy -> ".obs";
            default -> throw new KlabIllegalStateException("poh");
        };
    }

    @Override
    public long getCreationTimestamp() {
        return delegate.getCreationTimestamp();
    }

    @Override
    public long getLastUpdateTimestamp() {
        return delegate.getLastUpdateTimestamp();
    }

    @Override
    public Collection<Notification> getNotifications() {
        return delegate.getNotifications();
    }

    @Override
    public String getProjectName() {
        return delegate.getProjectName();
    }

    @Override
    public String getSourceCode() {
        return delegate.getSourceCode();
    }

    @Override
    public List<E> getStatements() {
        return delegate.getStatements();
    }

    @Override
    public Version getVersion() {
        return delegate.getVersion();
    }

    @Override
    public List<NavigableAsset> getClosestAsset(int offset) {
        // TODO
        return getAssetsAt(offset);
    }

    @Override
    public List<NavigableAsset> getAssetsAt(int offset) {

        List<NavigableAsset> ret = new ArrayList<>();

        if (offset == 0) {
            ret.add(this);
            return ret;
        }

        for (var statement : children()) {
            var path = getStatementsAt((NavigableKlabStatement) statement, offset, new ArrayList<>());
            if (!path.isEmpty()) {
                ret.add(this);
                ret.addAll(path);
            }
        }
        return ret;
    }

    private List<NavigableKlabAsset<?>> getStatementsAt(NavigableKlabStatement statement, int offset,
                                                        List<NavigableKlabAsset<?>> ret) {

        int start = statement.getOffsetInDocument();
        int end = start + statement.getLength();
        if (offset >= start && offset < end) {
            ret.add(statement);
            for (var child : statement.children()) {
                getStatementsAt((NavigableKlabStatement) child, offset, ret);
            }
        }

        return ret;
    }

    @Override
    public boolean isInactive() {
        return delegate.isInactive();
    }

    @Override
    public List<Statement> getStatementPath(int offset) {

        List<Statement> path = new ArrayList<>();
        if (offset >= 0 && offset < getSourceCode().length()) {
            for (var statement : this.getStatements()) {
                if (getStatementAt(offset, statement, path)) {
                    return path;
                }
            }
        }
        return path;
    }


    private boolean getStatementAt(int offset, Statement statement, List<Statement> path) {

        if (statement.getOffsetInDocument() >= offset && offset < (statement.getOffsetInDocument() + statement.getLength())) {
            path.add(statement);
            statement.visit(new Statement.Visitor() {

                boolean stop = false;

                @Override
                public void visitAnnotation(Annotation annotation) {

                }

                @Override
                public void visitStatement(Statement statement) {
                    if (!stop) {
                        stop = getStatementAt(offset, statement, path);
                    }
                }
            });
            return true;
        }

        /*
         * If statement is visitable, use visitor to refine the choice going through all sub-assets;
         * if one matches, add append the result of the lower-level call on it to the result and
         * break.
         */

        return false;
    }


    public NavigableProject project() {
        var parent = this.parent();
        while (parent instanceof NavigableKlabAsset asset && !(parent instanceof NavigableProject)) {
            parent = asset.parent();
        }
        return (NavigableProject) parent;
    }

    public KnowledgeClass getType() {
        return switch (this) {
            case NavigableKimOntology doc -> KnowledgeClass.ONTOLOGY;
            case NavigableKimNamespace doc -> KnowledgeClass.NAMESPACE;
            // TODO behaviors 		case NavigableKimOntology doc -> KlabAsset.KnowledgeClass.BEHAVIOR;
            default -> throw new UnsupportedOperationException("unexpected resource type");
        };

    }

    @Override
    public Repository.Status getRepositoryStatus() {
        return delegate.getRepositoryStatus();
    }

    @Override
    public boolean mergeMetadata(Metadata metadata, List<Notification> notifications) {

        boolean ret = !metadata.isEmpty();

        this.localMetadata.putAll(metadata);

        for (var notification : notifications) {
            this.getNotifications().add(notification);
            if (notification.getLexicalContext() != null) {
                var key = switch(notification.getLevel()) {
                    case Info -> INFO_NOTIFICATION_COUNT_KEY;
                    case Warning -> WARNING_NOTIFICATION_COUNT_KEY;
                    case Error, SystemError -> ERROR_NOTIFICATION_COUNT_KEY;
                    default -> null;
                };
                if (key != null) {
                    var path = getAssetsAt(notification.getLexicalContext().getOffsetInDocument());
                    for (var asset : path) {
                        // update n. of notifications per level
                        var count = asset.localMetadata().computeIfAbsent(key, k -> 0);
                        asset.localMetadata().put(key, (Integer)count + 1);
                        ret = true;
                    }
                }
            }
        }

        return ret;
    }
}
