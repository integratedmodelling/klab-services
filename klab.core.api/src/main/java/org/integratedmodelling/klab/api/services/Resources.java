package org.integratedmodelling.klab.api.services;

import java.io.File;
import java.io.Serializable;
import java.util.List;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.scope.KContextScope;
import org.integratedmodelling.klab.api.knowledge.observation.scope.KScope;
import org.integratedmodelling.klab.api.knowledge.organization.KWorkspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

public interface Resources extends KlabService {

    /**
     * All services publish capabilities and have a call to obtain them.
     * 
     * @author Ferd
     *
     */
    interface Capabilities extends Serializable {

        boolean isWorldviewProvider();

        String getAdoptedWorldview();

    }

    Capabilities capabilities();

    /**
     * Get the managed worldview. Assumes that the capabilities have been consulted and have
     * suggested that this is a sensible request.
     * 
     * @return an entire worldview managed by this service, or an empty resource set if not
     *         available.
     */
    ResourceSet requestWorldview();

    /**
     * Request all the contents of a given project.
     * 
     * @param projectName
     * @param scope
     * @return
     */
    ResourceSet requestProject(String projectName, KScope scope);

    /**
     * Request the namespaces containing a given model along with anything else required to run it
     * properly. This would normally include all the project namespaces and resources unless scoping
     * has been analyzed and determined it's not necessary.
     * 
     * @param modelName
     * @param scope
     * @return
     */
    ResourceSet requestModel(String modelName, KScope scope);

    /**
     * Return the parsed contents of a namespace. This only be called after a request that returned
     * a ResourceSet to ensure correct dependency handling.
     * 
     * @param urn
     * @param scope
     * @return
     */
    KimNamespace resolveNamespace(String urn, KScope scope);

    /**
     * Return the parsed contents of a behavior. This only be called after a request that returned
     * a ResourceSet to ensure correct dependency handling.
     * 
     * @param urn
     * @param scope
     * @return
     */
    KActorsBehavior resolveBehavior(String urn, KScope scope);

    /**
     * Return the parsed contents of a resource.
     * 
     * @param urn
     * @param scope
     * @return
     */
    Resource resolveResource(String urn, KScope scope);

    /**
     * 
     * @param definition
     * @return
     */
    KimObservable resolveObservable(String definition);

    /**
     * 
     * @param definition
     * @return
     */
    KimConcept resolveConcept(String definition);

    /**
     * 
     * @param originalResource
     * @param scope
     * @return
     */
    Resource contextualizeResource(Resource originalResource, KContextScope scope);

    /**
     * 
     * @param contextualizedResource
     * @param scope
     * @return
     */
    KlabData contextualize(Resource contextualizedResource, KScope scope);

    /**
     * 
     * @param urn
     * @param scope
     * @return
     */
    KdlDataflow resolveDataflow(String urn, KScope scope);

    /**
     * Return all the namespaces that depend on the passed namespace.
     * 
     * @param namespaceId
     * @return
     */
    List<KimNamespace> dependents(String namespaceId);

    /**
     * Return all the namespaces that the passed namespace depends on. These must be available to
     * the resolver prior to loading any namespace. The closure of the namespace must be complete,
     * no matter if they come from this service or others: a service cannot serve a namespace unless
     * it's prepared to serve its entire closure under the same scope.
     * 
     * @param namespaceId
     * @return
     */
    List<KimNamespace> precursors(String namespaceId);

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
        String addResourceToLocalWorkspace(Resource resource);

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
