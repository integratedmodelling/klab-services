package org.integratedmodelling.klab.modeler.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.data.RepositoryMetadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public abstract class NavigableKlabDocument<E extends Statement, T extends KlabDocument<E>>
		extends NavigableKlabAsset<T> implements KlabDocument<E> {

	private static final long serialVersionUID = 7741675272275189813L;

	public NavigableKlabDocument(T document, NavigableKlabAsset<?> parent) {
		super(document, parent);
//		if (parent instanceof NavigableKlabAsset asset && asset.getResource() instanceof IContainer container) {
//			this.resource = Eclipse.INSTANCE.findFileInContainer(container, getFileName());
//		}
	}

	/**
	 * Get a filename with the proper file name based on the URN, document type and
	 * location.
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

	public List<NavigableKlabAsset<?>> getAssetsAt(int offset) {

		List<NavigableKlabAsset<?>> ret = new ArrayList<>();

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
	public Object[] children() {
		return getStatements().toArray();
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
	public RepositoryMetadata getRepositoryMetadata() {
		return delegate.getRepositoryMetadata();
	}

}
