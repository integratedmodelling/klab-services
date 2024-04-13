package org.integratedmodelling.klab.modeler.model;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

//import org.eclipse.core.resources.IFile;
//import org.eclipse.core.resources.IWorkspace;
//import org.eclipse.core.resources.IWorkspaceRoot;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
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

    public NavigableWorkspace(Workspace delegate) {
        super(delegate, null);
    }

    public Collection<Project> getProjects() {
        return new Utils.Casts<NavigableAsset, Project>().cast((Collection<NavigableAsset>) children());
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
    protected List<? extends NavigableAsset> createChildren() {
        return delegate.getProjects().stream()
                       .map(p -> new NavigableProject(p, this))
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

    public ResourcePrivileges getPrivileges() {
        return delegate.getPrivileges();
    }

}
