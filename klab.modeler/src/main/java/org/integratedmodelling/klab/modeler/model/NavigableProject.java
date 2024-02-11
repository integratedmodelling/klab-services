package org.integratedmodelling.klab.modeler.model;

import java.util.ArrayList;
import java.util.List;

//import org.eclipse.core.resources.IProject;
import org.integratedmodelling.klab.api.data.RepositoryMetadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class NavigableProject extends NavigableKlabAsset<Project> implements Project {

	public NavigableProject(Project asset, NavigableKlabAsset<?> parent/*, IProject resource*/) {
		super(asset, parent);
//		this.resource = resource;
	}

	private static final long serialVersionUID = -6759189347982834877L;

	@Override
	public List<KActorsBehavior> getApps() {
		return delegate.getApps();
	}

	@Override
	public List<KActorsBehavior> getBehaviors() {
		return delegate.getBehaviors();
	}

	@Override
	public Manifest getManifest() {
		return delegate.getManifest();
	}

	@Override
	public List<KimNamespace> getNamespaces() {
		return delegate.getNamespaces();
	}

	@Override
	public List<Notification> getNotifications() {
		return delegate.getNotifications();
	}

	@Override
	public List<KimObservationStrategyDocument> getObservationStrategies() {
		return delegate.getObservationStrategies();
	}

	@Override
	public List<KimOntology> getOntologies() {
		return delegate.getOntologies();
	}

	@Override
	public List<String> getResourceUrns() {
		return delegate.getResourceUrns();
	}

	@Override
	public List<KActorsBehavior> getTestCases() {
		return delegate.getTestCases();
	}

	public List<NavigableKlabDocument<?, ?>> documents() {
		List<NavigableKlabDocument<?, ?>> ret = new ArrayList<>();
		for (var child : children()) {
			if (child instanceof NavigableKlabDocument doc) {
				ret.add(doc);
			}
		}
		return ret;
	}

	@Override
	public List<? extends NavigableAsset> children() {
		// TODO add everything else, including intermediate containers
		return getOntologies().stream().map(p -> new NavigableKimOntology(p, this)).toList();
	}

	@Override
	public RepositoryMetadata getRepositoryMetadata() {
		return delegate.getRepositoryMetadata();
	}

	public NavigableKlabDocument<?, ?> findDocument(String documentUrn, KnowledgeClass documentType) {
		// TODO Auto-generated method stub
		for (Object child : children()) {
			// TODO handle the containers!
			if (child instanceof NavigableKlabDocument document && KlabAsset.classify(document) == documentType
					&& document.getUrn().equals(documentUrn)) {
				return document;
			}
		}
		return null;
	}

}
