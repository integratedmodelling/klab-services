package org.integratedmodelling.klab.api.services;

import java.io.File;
import java.util.List;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.KKlabData;
import org.integratedmodelling.klab.api.knowledge.KResource;
import org.integratedmodelling.klab.api.knowledge.observation.scope.KContextScope;
import org.integratedmodelling.klab.api.knowledge.observation.scope.KScope;
import org.integratedmodelling.klab.api.knowledge.organization.KProject;
import org.integratedmodelling.klab.api.knowledge.organization.KWorkspace;
import org.integratedmodelling.klab.api.lang.kactors.KKActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KKdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.KKimConcept;
import org.integratedmodelling.klab.api.lang.kim.KKimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KKimObservable;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

public interface KResources {

    /**
     * All services publish capabilities and have a call to obtain them.
     * 
     * @author Ferd
     *
     */
    interface Capabilities {

        boolean isWorldviewProvider();

        String getAdoptedWorldview();

    }

    Capabilities getCapabilities();

    ResourceSet getWorldview();

    ResourceSet getProject(String projectName, KScope scope);

    ResourceSet getModel(String modelName, KScope scope);

    /**
     * 
     * @param urn
     * @param scope
     * @return
     */
    KProject resolveNamespace(String urn, KScope scope);

    /**
     * 
     * @param urn
     * @param scope
     * @return
     */
    KKActorsBehavior resolveBehavior(String urn, KScope scope);

    /**
     * 
     * @param urn
     * @param scope
     * @return
     */
    KResource resolveResource(String urn, KScope scope);

    /**
     * 
     * @param definition
     * @return
     */
    KKimObservable resolveObservable(String definition);

    /**
     * 
     * @param definition
     * @return
     */
    KKimConcept resolveConcept(String definition);

    /**
     * 
     * @param originalResource
     * @param scope
     * @return
     */
    KResource contextualizeResource(KResource originalResource, KContextScope scope);

    /**
     * 
     * @param contextualizedResource
     * @param scope
     * @return
     */
    KKlabData contextualize(KResource contextualizedResource, KScope scope);

    /**
     * 
     * @param urn
     * @param scope
     * @return
     */
    KKdlDataflow resolveDataflow(String urn, KScope scope);

    /**
     * Return all the namespaces that depend on the passed namespace.
     * 
     * @param namespaceId
     * @return
     */
    List<KKimNamespace> dependents(String namespaceId);

    /**
     * Return all the namespaces that the passed namespace depends on. These must be available to
     * the resolver prior to loading any namespace. The closure of the namespace must be complete,
     * no matter if they come from this service or others: a service cannot serve a namespace unless
     * it's prepared to serve its entire closure under the same scope.
     * 
     * @param namespaceId
     * @return
     */
    List<KKimNamespace> precursors(String namespaceId);

    /**
     * Admin interface to submit/remove projects and configure the service.
     * 
     * @author Ferd
     *
     */
    interface Admin {

        /**
         * Add or update a project from an external source to the local repository.
         * 
         * @param workspaceName
         * @param projectUrl can be a file (zip or existing folder), a git URL (with a potential
         *        branch name after a # sign) or a http URL from another resource manager.
         * @param overwriteIfExisting self-explanatory. If the project is remote, reload if true.
         * @return true if operation succeeded and anything was done (false if project existed and
         *         wasn't overwritten)
         */
        boolean addProjectToLocalWorkspace(String workspaceName, String projectUrl, boolean overwriteIfExisting);

        /**
         * Publish a project with the passed privileges. The project must has been added before this
         * is called. If the project is already published, update the permissions.
         * 
         * @param projectUrl
         * @param permissions
         * @return
         */
        boolean publishProject(String projectUrl, ResourcePrivileges permissions);

        /**
         * Unpublish a previously published project.
         * 
         * @param projectUrl
         * @return
         */
        boolean unpublishProject(String projectUrl);

        /**
         * Add a resource fully specified by a resource object to those managed by this service.
         * Resource is invisible from the outside until published. The resource adapter must be
         * available to the service.
         * 
         * @param resource
         * @return the resource URN, potentially modified w.r.t. the one in the request.
         */
        String addResourceToLocalWorkspace(KResource resource);

        /**
         * Add a resource with file content to those managed by this service. Resource is invisible
         * from the outside until published. The resource adapter must be available to the service.
         * 
         * @param resourcePath the directory or zip file that contains the resource files. A
         *        resource.json file must be present, along with anything else required by the
         *        adapter.
         * @return the resource URN, potentially modified w.r.t. the one in the request.
         */
        String addResourceToLocalWorkspace(File resourcePath);

        /**
         * 
         * @param resource
         * @param permissions
         * @return
         */
        boolean publishResource(String resourceUrn, ResourcePrivileges permissions);

        /**
         * 
         * @param resourceUrn
         * @return
         */
        boolean unpublishResource(String resourceUrn);

        /**
         * 
         * @param workspaceName
         * @param projectName
         * @return true if operation was carried out
         */
        void removeProjectFromLocalWorkspace(String workspaceName, String projectName);

        /**
         * Remove an entire workspace and all the projects and resources in it.
         * 
         * @param workspaceName
         */
        void removeWorkspace(String workspaceName);

        /**
         * Return a list of all the workspaces available with their contents.
         * 
         * @return
         */
        List<KWorkspace> getWorkspaces();

    }

}
