package org.integratedmodelling.klab.modeler.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

//import org.eclipse.core.resources.IContainer;
//import org.eclipse.core.resources.IFile;
//import org.eclipse.core.resources.IFolder;
//import org.eclipse.core.resources.IProject;
//import org.eclipse.core.resources.IResource;
//import org.eclipse.core.resources.IWorkspace;
//import org.eclipse.core.resources.IWorkspaceRoot;
//import org.eclipse.core.runtime.IAdaptable;
//import org.eclipse.ui.model.IWorkbenchAdapter;
//import org.eclipse.ui.model.IWorkbenchAdapter2;
//import org.eclipse.ui.model.IWorkbenchAdapter3;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
//import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
//import org.integratedmodelling.klab.ide.Activator;
//import org.integratedmodelling.klab.ide.KlabAdapterFactory;
//import org.integratedmodelling.klab.ide.KlabAdapterFactory;

/**
 * Adaptable wrappers for the knowledge tree. Their equality is assessed in
 * terms of their navigational position and URN, not the contents of the
 * delegate asset.
 * 
 * @param <T>
 */
public abstract class NavigableKlabAsset<T extends KlabAsset> implements /*IAdaptable,*/ NavigableAsset {

	private static final long serialVersionUID = -2326835089185461220L;

	protected T delegate;
	protected NavigableAsset parent;
//	protected IResource resource;
	protected String path;

//	public IResource getResource() {
//		return resource;
//	}

	public NavigableKlabAsset(T asset, NavigableKlabAsset<?> parent) {
		this.delegate = asset;
		this.parent = parent;
		this.path = (parent == null ? "" : (parent.path + ":")) + asset.getUrn();
	}

	public T getDelegate() {
		return delegate;
	}

	@Override
	public Metadata getMetadata() {
		return delegate.getMetadata();
	}

	@Override
	public String getUrn() {
		return delegate.getUrn();
	}

	public List<? extends NavigableAsset> children() {
		return Collections.emptyList();
	}

	public NavigableContainer root() {
		NavigableAsset ret = this;
		while (((NavigableKlabAsset)ret).parent != null) {
			ret = ((NavigableKlabAsset)ret).parent;
		}
		return (NavigableContainer)ret;
	}
	
	public NavigableAsset parent() {
		return parent;
	}

	@Override
	public String toString() {
		return "<" + path + ">";
	}

//	@SuppressWarnings({ "hiding", "unchecked" })
//	@Override
//	public <T> T getAdapter(Class<T> adapter) {
//		if (IWorkbenchAdapter.class.isAssignableFrom(adapter) || IWorkbenchAdapter2.class.isAssignableFrom(adapter)
//				|| IWorkbenchAdapter3.class.isAssignableFrom(adapter)) {
//			return (T) KlabAdapterFactory.worldviewAdapter;
//		} else if (IWorkspaceRoot.class.isAssignableFrom(adapter) && resource instanceof IWorkspace) {
//			return (T) ((IWorkspace) resource).getRoot();
//		} else if (IWorkspace.class.isAssignableFrom(adapter) && resource instanceof IWorkspace) {
//			return (T) resource;
//		} else if (IFolder.class.isAssignableFrom(adapter) && resource instanceof IFolder) {
//			return (T) resource;
//		} else if (IProject.class.isAssignableFrom(adapter) && resource instanceof IProject) {
//			return (T) resource;
//		} else if (IFile.class.isAssignableFrom(adapter) && resource instanceof IFile) {
//			return (T) resource;
//		} else if (IContainer.class.isAssignableFrom(adapter) && resource instanceof IContainer) {
//			return (T) resource;
//		} else if (IResource.class.isAssignableFrom(adapter) && resource instanceof IResource) {
//			return (T) resource;
//		}
//		return null;
//	}

	public String getPath() {
		return this.path;
	}

	@Override
	public int hashCode() {
		return Objects.hash(path);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NavigableKlabAsset<?> other = (NavigableKlabAsset<?>) obj;
		return Objects.equals(path, other.path);
	}

	/**
	 * First-class assets only for now.
	 * 
	 * @param asset
	 * @return
	 */
	public static NavigableKlabAsset<?> adapt(KlabAsset asset) {
		return switch (asset) {
		case NavigableKlabAsset<?> a -> a;
		case Worldview worldview -> new NavigableWorldview(worldview);
//		case Workspace workspace ->
//			Activator.get().getNavigableWorkspace(workspace, Activator.engine().canEditWorkspace());
		default -> null;
		};
	}

}
