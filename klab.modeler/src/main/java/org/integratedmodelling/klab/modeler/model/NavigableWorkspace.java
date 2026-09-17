package org.integratedmodelling.klab.modeler.model;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// import org.eclipse.core.resources.IFile;
// import org.eclipse.core.resources.IWorkspace;
// import org.eclipse.core.resources.IWorkspaceRoot;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;

// import com.google.common.collect.BiMap;
// import com.google.common.collect.HashBiMap;

/**
 * Workspaces should never be modified after creation: any change to the namespaces should create a
 * new workspace.
 */
public class NavigableWorkspace extends NavigableKlabAsset<Workspace>
    implements Workspace, NavigableContainer {

  @Serial private static final long serialVersionUID = -6967097462644821300L;

  public NavigableWorkspace(Workspace delegate) {
    super(delegate, null);
    computeStatistics();
  }

  public Collection<Project> getProjects() {
    return new Utils.Casts<NavigableAsset, Project>().cast((Collection<NavigableAsset>) children());
  }

  @Override
  public Collection<Annotation> getAnnotations() {
    return delegate.getAnnotations();
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
  protected List<NavigableAsset> createChildren() {
    return delegate.getProjects().stream()
        .map(p -> (NavigableAsset) (new NavigableProject(p, this)))
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

  /** Refresh catalog settings without rebuilding project navigation or losing tree state. */
  public void updateSettings(org.integratedmodelling.klab.api.data.Metadata metadata,
      ResourcePrivileges privileges) {
    var updated = new org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl();
    updated.setUrn(delegate.getUrn());
    updated.setServiceId(delegate.getServiceId());
    updated.setProjects(delegate.getProjects());
    updated.setAnnotations(new ArrayList<>(delegate.getAnnotations()));
    updated.setMetadata(org.integratedmodelling.klab.api.data.Metadata.create(metadata));
    updated.setPrivileges(privileges);
    delegate = updated;
  }
}
