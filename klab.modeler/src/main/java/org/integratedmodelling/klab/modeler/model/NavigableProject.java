package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;

import java.io.File;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NavigableProject extends NavigableKlabAsset<Project> implements Project {

	@Serial
	private static final long serialVersionUID = -6759189347982834877L;

	private boolean locked;
	private File rootDirectory;

	public NavigableProject(Project asset, NavigableKlabAsset<?> parent) {
		super(asset, parent);
	}

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
	public Collection<Annotation> getAnnotations() { return delegate.getAnnotations(); }

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
	protected List<? extends NavigableAsset> createChildren() {

		// TODO add everything else, including intermediate containers
		var ret = new ArrayList<NavigableAsset>(getOntologies().stream().map(p -> new NavigableKimOntology(p, this)).toList());

		final Project project = getDelegate();

		ret.addAll(getNamespaces().stream().map(n -> new NavigableKimNamespace(n, this)).toList());

		// TODO apps, tests, scripts in their folders
		if (!delegate.getApps().isEmpty()) {

		}

		if (!delegate.getTestCases().isEmpty()) {

		}


		// observation strategies
		if (!delegate.getObservationStrategies().isEmpty()) {
			ret.add(new NavigableFolderImpl<NavigableDocument>("Observation strategies", this) {

				@Override
				protected List<? extends NavigableAsset> createChildren() {
					return project.getObservationStrategies().stream().map(s -> new NavigableObservationStrategies(s,
							this)).toList();
				}
			});
		}

		// TODO local project resources

		// TODO settings if editable

		return ret;
	}

	@Override
	public RepositoryState getRepositoryState() {
		return delegate.getRepositoryState();
	}

	public RepositoryState.Status computeStatus(KlabDocument<?> document) {
		// Compute the passed document's status re: the repository
		return RepositoryState.Status.UNTRACKED;
	}

	//	@Override
//	public Repository getRepository() {
//		return delegate.getRepository();
//	}

    public boolean isLocked() {
		return this.locked;
    }

	public void setLocked(boolean locked) {
		this.locked = locked;
	}

	/**
	 * Local filesystem directory. Only available if project is locked, either by interacting with the
	 * very files in the filesystem or through a mirror copy from the service.
	 * @return
	 */
	public File getRootDirectory() {
		return rootDirectory;
	}

	public void setRootDirectory(File rootDirectory) {
		this.rootDirectory = rootDirectory;
	}

}
