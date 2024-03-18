package org.integratedmodelling.klab.modeler.model;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

//import org.eclipse.core.resources.IFile;
//import org.eclipse.core.resources.IWorkspace;
//import org.eclipse.core.resources.IWorkspaceRoot;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;

//import com.google.common.collect.BiMap;
//import com.google.common.collect.HashBiMap;

/**
 * Workspaces should never be modified after creation: any change to the namespaces should create a new
 * workspace.
 */
public class NavigableWorkspace extends NavigableKlabAsset<Workspace> implements Workspace {

    @Serial
    private static final long serialVersionUID = -6967097462644821300L;

    //	private BiMap<IFile, NavigableKlabDocument<?, ?>> fileResources = null;

    public NavigableWorkspace(Workspace delegate/*, IWorkspace eclipseWorkspace*/) {
        super(delegate, null);
        //		this.resource = eclipseWorkspace.getRoot();
    }

    //	public Map<IFile, NavigableKlabDocument<?, ?>> getFileResources() {
    //		if (fileResources == null) {
    //			fileResources = HashBiMap.create();
    //			for (var child : children()) {
    //				if (child instanceof NavigableProject project) {
    //					for (var doc : project.documents()) {
    //						if (doc.getResource() instanceof IFile ifile) {
    //							fileResources.put(ifile, doc);
    //						}
    //					}
    //				}
    //			}
    //		}
    //		return fileResources;
    //	}

    public Collection<Project> getProjects() {
        return delegate.getProjects();
    }

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

    @Override
    public List<? extends NavigableAsset> children() {
        return getProjects().stream()
                            .map(p -> new NavigableProject(p, this/*,
						this.resource instanceof IWorkspaceRoot wsroot ? wsroot.getProject(p.getUrn()) :
						null*/))
                            .toList();
    }

    /**
     * Find the asset with the passed path.
     *
     * @param path
     * @return the asset or null
     */
    public NavigableKlabAsset<?> findAsset(String path) {
        return null;
    }

    //	public NavigableKlabDocument<?, ?> findDocument(IFile file) {
    //		return getFileResources().get(file);
    //	}

    /**
     * Find a document of the passed type by its URN
     *
     * @param documentUrn
     * @param documentType
     * @return
     */
    public NavigableKlabDocument<?, ?> findAsset(String documentUrn, KnowledgeClass documentType) {
        for (Object o : children()) {
            if (o instanceof NavigableProject project) {
                var doc = project.findDocument(documentUrn, documentType);
                if (doc != null) {
                    return doc;
                }
            }
        }
        return null;
    }

}
