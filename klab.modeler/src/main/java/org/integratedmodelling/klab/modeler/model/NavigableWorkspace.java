package org.integratedmodelling.klab.modeler.model;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

//import org.eclipse.core.resources.IFile;
//import org.eclipse.core.resources.IWorkspace;
//import org.eclipse.core.resources.IWorkspaceRoot;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;

//import com.google.common.collect.BiMap;
//import com.google.common.collect.HashBiMap;

/**
 * Workspaces should never be modified after creation: any change to the namespaces should create a new
 * workspace.
 */
public class NavigableWorkspace extends NavigableKlabAsset<Workspace> implements Workspace,
        NavigableContainer {

    @Serial
    private static final long serialVersionUID = -6967097462644821300L;

    // we keep the list of projects so that we can manage the locking status
    List<NavigableProject> projects = new ArrayList<>();

    public NavigableWorkspace(Workspace delegate) {
        super(delegate, null);
        //		this.resource = eclipseWorkspace.getRoot();
        this.projects.addAll(delegate.getProjects().stream()
                                          .map(p -> new NavigableProject(p, this))
                                          .toList());
    }

    public Collection<Project> getProjects() {
        return new Utils.Casts<NavigableProject, Project>().cast(projects);
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
        return projects;
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
