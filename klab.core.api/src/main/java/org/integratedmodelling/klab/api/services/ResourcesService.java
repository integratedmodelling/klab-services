package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.authentication.CRUDPermission;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Management of all {@link KlabAsset}s, collectively called "resources" (although this conflicts with
 * {@link Resource}, which is a specific type of KlabAsset). Assets handled include projects with all their
 * contents (namespaces with {@link KimConceptStatement}, {@link KimModel} and other definitions,
 * {@link KActorsBehavior} behaviors, and local {@link Resource}s), plus any published, independently managed
 * {@link Resource}s, and any component plug-ins, managed directly as jar/zips. All assets are versioned and
 * history is maintained. Permissions and review-driven ranking are enabled for all primary assets, i.e.
 * projects, components and published resources. Assets that come as content of projects get their permissions
 * and ranks from the project they are part of.
 * <p>
 * The resource manager holds all language parsers and can also turn a behavior specification in k.Actors
 * located at a given URL into its correspondent serialized, executable {@link KActorsBehavior}. This should
 * normally only happen for scripts, applications and user behaviors, which can exist independent of
 * projects.
 * <p>
 * If a community service is available in the service scope, the resource manager initiates, and reacts to,
 * events that create the review history of an asset. Primary assets (projects, components and published
 * resources) are subject to review and the resulting rank and history is held by the resources service.
 * <p>
 * Endpoints are part of three main families:
 * <dl>
 * <dt>get..()</dt>
 * <dd>endpoints retrieve URN-named assets in their serialized form. The <code>get</code> prefix is
 * omitted in this implementation.</dd>
 * <dt>resolve..()</dt>
 * <dd>endpoints retrieve {@link ResourceSet}s that contain all the information needed to
 * operationalize the asset requested at the requesting end, including any dependent assets and
 * their sources. For example, retrieval of a {@link KimModel} is used in the
 * {@link Resolver} to build a {@link Model} with all its dependencies satisfied.</dd>
 * <dt>{list|add|remove|update}..()</dt>
 * <dd>endpoints manage inquiry and CRUD operations, part of the {@link ResourcesService.Admin}
 * API</dd>
 * </dl>
 * <p>
 * In addition, the resource manager exposes querying methods, either based on semantics and context
 * ({@link #queryModels(Observable, ContextScope)}) or on textual search
 * ({@link #queryResources(String, KnowledgeClass...)}). The semantic query model uses the connected
 * reasoner and will only return a ResourceSet listing {@link KimModel}s and their
 * requirements, leaving ranking and prioritization to the caller.
 *
 * @author Ferd
 */
public interface ResourcesService extends KlabService {

    public static final int DEFAULT_PORT = 8092;


    /**
     * All services publish capabilities and have a call to obtain them.
     *
     * @author Ferd
     */
    interface Capabilities extends ServiceCapabilities {

        boolean isWorldviewProvider();

        String getAdoptedWorldview();

        /**
         * Return the current CRUD permissions for the authenticated user. None of these should be returned if
         * the service does not implement ResourceService.Admin.
         *
         * @return
         */
        Set<CRUDPermission> getPermissions();
    }

    default String getServiceName() {
        return "klab.resources.service";
    }

    Capabilities capabilities();

    /**
     * Get the contents of a set of projects. Assumes that the capabilities have been consulted and have
     * suggested that this is a sensible request.
     *
     * @param scope could be null (defaulting to the service scope) for the entire worldview, but a user scope
     *              should include the optional parts due to the user's group selection.
     * @return an entire worldview managed by this service, or an empty resource set if not available.
     */
    ResourceSet projects(Collection<String> projects, Scope scope);

    /**
     * Request all the contents of a given project.
     *
     * @param projectName
     * @param scope
     * @return
     */
    ResourceSet project(String projectName, Scope scope);

    /**
     * Request the namespaces containing a given model along with anything else required to run it properly.
     * This would normally include all the project namespaces and resources, unless the scope has been
     * analyzed and the service has determined what is necessary. Only errors on the side of caution are
     * allowed in determining what's included in the result.
     *
     * @param modelName
     * @param scope
     * @return
     */
    ResourceSet model(String modelName, Scope scope);

    /**
     * Resolve a specific URN to the object that is represented by it, which must be returned in
     * {@link ResourceSet#setResults(java.util.Set)}. The result must be self-consistent and complete. Return
     * an empty resultset if not found.
     */
    ResourceSet resolve(String urn, Scope scope);

    /**
     * Return the parsed contents of a namespace. This should only be called after a request that returned a
     * ResourceSet to ensure correct dependency handling.
     *
     * @param urn
     * @param scope
     * @return
     */
    KimNamespace resolveNamespace(String urn, Scope scope);

    /**
     * Return a list of all the workspaces available with their contents, filtering according to the
     * requesting identity.
     *
     * @return
     */
    Collection<Workspace> listWorkspaces();

    /**
     * Return the parsed contents of a behavior. This only be called after a request that returned a
     * ResourceSet to ensure correct dependency handling.
     *
     * @param urn
     * @param scope
     * @return
     */
    KActorsBehavior resolveBehavior(String urn, Scope scope);

    /**
     * Return the parsed contents of a resource.
     *
     * @param urn
     * @param scope
     * @return
     */
    Resource resolveResource(String urn, Scope scope);

    /**
     * Resolve a component (and possibly its dependencies) that provides the passed service call.
     *
     * @param name
     * @param scope
     * @return
     */
    ResourceSet resolveServiceCall(String name, Scope scope);

    /**
     * Inquire about resource availability for the passed urn and scope. Should work for all types of assets.
     *
     * @param urn
     * @param scope
     * @return
     */
    ResourceStatus resourceStatus(String urn, Scope scope);

    /**
     * @param definition
     * @return
     */
    KimObservable resolveObservable(String definition);

    /**
     * @param definition
     * @return
     */
    KimConcept resolveConcept(String definition);

    /**
     * @param originalResource
     * @param scope
     * @return
     */
    Resource contextualizeResource(Resource originalResource, ContextScope scope);

    /**
     * @param contextualizedResource
     * @param scope
     * @return
     */
    KlabData contextualize(Resource contextualizedResource, Scope scope);

    /**
     * @param urn
     * @param scope
     * @return
     */
    KdlDataflow resolveDataflow(String urn, Scope scope);

    /**
     * THe worldview is required to be whole and consistent, including and starting with a root domain. If
     * this service provides a worldview, its capabilities will advertise the worldview ID, which may contain
     * projects not served by this server but resolved at initialization if a worldview is served. The
     * projects that compose the worldview are not essential here but will be listed in the worldview's
     * metadata.
     * <p>
     * TODO given this paradigm it's possible to serve multiple worldviews, which may require this function to
     *  take an ID as parameter.
     *
     * @return
     */
    Worldview getWorldview();

    /**
     * Return all the namespaces that depend on the passed namespace.
     *
     * @param namespaceId
     * @return
     */
    List<KimNamespace> dependents(String namespaceId);

    /**
     * Return all the namespaces that the passed namespace depends on. These must be available to the resolver
     * prior to loading any namespace. The closure of the namespace must be complete, no matter if they come
     * from this service or others: a service cannot serve a namespace unless it's prepared to serve its
     * entire closure under the same scope.
     *
     * @param namespaceId
     * @return
     */
    List<KimNamespace> precursors(String namespaceId);

    /**
     * Return the URNs of any locally hosted resources whose URN matches the passed pattern. If any resource
     * types are passed, only return URNs for those. The pattern should allow simple wildcards like * and .
     *
     * @param urnPattern
     * @param resourceTypes
     * @return
     */
    List<String> queryResources(String urnPattern, KlabAsset.KnowledgeClass... resourceTypes);

    /**
     * Return the actual (local) project with its contents. Used mostly internally with a likely heavy result
     * payload, should not resolve the project beyond the local workspaces.
     *
     * @param projectName
     * @param scope
     * @return
     */
    Project resolveProject(String projectName, Scope scope);

    /**
     * Return the candidate models for the passed observables in the passed scope (which will provide the
     * reasoner service). The result should contain an unordered list of candidate model URNs (in
     * {@link ResourceSet#getResults()}) along with the needed resources, namespaces and behaviors needed to
     * run them. Prioritization happens in the resolver.
     *
     * @param observable
     * @param scope
     * @return
     */
    ResourceSet queryModels(Observable observable, ContextScope scope);

    /**
     * Compute and return the "native" geometry for the model identified by this URN. The geometry comes from
     * the namespace coverage merged with the model's own, which in turn is the intersected coverage of its
     * resources plus any model scale constraints, if any are specified. Models may also restrict their
     * geometry to specific representations of space/time using annotations or generic specifications (for
     * example request a grid or a temporal range without specifying a resolution, or specifying only a range
     * of them, or a resolution without extent). This information is needed at resolution, so computations
     * should be cached to avoid wasting CPU in complex operations on repeated calls.
     * <p>
     * The coverage should honor any constraints expressed in the coverage specifications or annotations, and
     * report a coverage percentage == 0 when {@link Coverage#getCoverage()} is called whenever any of those
     * aren't met.
     *
     * @param modelUrn a known model URN. If the URN is unknown a {@link KlabIllegalArgumentException} should
     *                 be thrown.
     * @return the coverage of the model, reporting coverage == 1 unless constraints are not met.
     * @throws KlabIllegalArgumentException if the URN isn't recognized or does not specify a model.
     */
    Coverage modelGeometry(String modelUrn) throws KlabIllegalArgumentException;

    /**
     * Read a behavior from the passed URL and return the parsed behavior.
     *
     * @param url
     */
    KActorsBehavior readBehavior(URL url);

    /**
     * Admin interface to submit/remove projects and configure the service.
     *
     * @author Ferd
     */
    interface Admin {

//        /**
//         * Separate and explicit loading of the worldviews and any other workspaces is necessary if local
//         * resources must be coordinated with a local reasoner.
//         *
//         * @return
//         */
//        ResourceSet loadWorldview();
//
//        /**
//         * Separate and explicit loading of the worldviews and any other workspaces is necessary if local
//         * resources must be coordinated with a local reasoner.
//         *
//         * @return
//         */
//        ResourceSet loadWorkspaces();

        /**
         * Add or update a project from an external source to the local repository.
         *
         * @param workspaceName
         * @param projectUrl          can be a file (zip or existing folder), a git URL (with a potential
         *                            branch name after a # sign) or a http URL from another resource
         *                            manager.
         * @param overwriteIfExisting self-explanatory. If the project is remote, reload if true.
         * @return true if operation succeeded and anything was done (false if project existed and wasn't
         * overwritten)
         */
        boolean importProject(String workspaceName, String projectUrl, boolean overwriteIfExisting);

        /**
         * Create a new empty project. Use the update function to configure the manifest and the create/update
         * content functions to define the content.
         *
         * @param workspaceName
         * @param projectName
         * @return
         */
        Project createProject(String workspaceName, String projectName);

        /**
         * Update project manifest and metadata. Project must exist.
         *
         * @param projectName
         * @param manifest
         * @param metadata
         * @return the updated project with the new metadata and manifest.
         */
        Project updateProject(String projectName, Project.Manifest manifest, Metadata metadata);

        /**
         * Project must exist; namespace must not. Namespace content is parsed and the results are returned.
         * Errors are reported with the namespace itself; fatal errors will cause an unparseable namespace
         * exception (TODO).
         *
         * @param projectName
         * @param namespaceContent
         * @return
         */
        KimNamespace createNamespace(String projectName, String namespaceContent);

        /**
         * Namespace must exist in project. Return parsed namespace. The dependency structure may have changed
         * and should be reloaded if dependencies are of interest.  Errors are reported with the namespace
         * itself; fatal errors will cause an unparseable namespace * exception (TODO).
         *
         * @param projectName
         * @param namespaceContent
         * @return
         */
        KimNamespace updateNamespace(String projectName, String namespaceContent);

        /**
         * Project must exist; behavior must not (throws TODO). Namespace content is parsed and the results
         * are returned. Errors are reported with the namespace itself; fatal errors will cause an unparseable
         * namespace exception (TODO).
         *
         * @param projectName
         * @param behaviorContent
         * @return
         */
        KActorsBehavior createBehavior(String projectName, String behaviorContent);

        /**
         * Behavior must exist in project. Return parsed namespace. The dependency structure may have changed
         * and should be reloaded if dependencies are of interest.  Errors are reported with the namespace
         * itself; fatal errors will cause an unparseable namespace * exception (TODO).
         *
         * @param projectName
         * @param behaviorContent
         * @return
         */
        KActorsBehavior updateBehavior(String projectName, String behaviorContent);

        /**
         * Project must exist; ontology must not (throws TODO). Namespace content is parsed and the results
         * are returned. Errors are reported with the namespace itself; fatal errors will cause an unparseable
         * namespace exception (TODO).
         *
         * @param projectName
         * @param ontologyContent
         * @return
         */
        KimOntology createOntology(String projectName, String ontologyContent);

        /**
         * Ontology must exist in project. Return parsed namespace. The dependency structure may have changed
         * and should be reloaded if dependencies are of interest.  Errors are reported with the namespace
         * itself; fatal errors will cause an unparseable namespace * exception (TODO).
         *
         * @param projectName
         * @param ontologyContent
         * @return
         */
        KimOntology updateOntology(String projectName, String ontologyContent);

        /**
         * Publish a project with the passed privileges. The project must have been added before this is
         * called. If the project is already published, update the permissions.
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
         * Add a resource fully specified by a resource object to those managed by this service. Resource is
         * invisible from the outside until published. The resource adapter must be available to the service.
         *
         * @param resource
         * @return the resource URN, potentially modified w.r.t. the one in the request.
         */
        String createResource(Resource resource);

        /**
         * Add a resource with file content to those managed by this service. Resource is invisible from the
         * outside until published. The resource adapter must be available to the service.
         *
         * @param resourcePath the directory or zip file that contains the resource files. A resource.json
         *                     file must be present, along with anything else required by the adapter.
         * @return the resource URN, potentially modified w.r.t. the one in the request.
         */
        String createResource(File resourcePath);

        Resource createResource(String projectName, String urnId, String adapter,
                                Parameters<String> resourceData);

        /**
         * @param resourceUrn
         * @param permissions
         * @return
         */
        boolean publishResource(String resourceUrn, ResourcePrivileges permissions);

        /**
         * @param resourceUrn
         * @return
         */
        boolean unpublishResource(String resourceUrn);

        /**
         * @param projectName
         * @return true if operation was carried out
         */
        void removeProject(String projectName);

        /**
         * Remove an entire workspace and all the projects and resources in it.
         *
         * @param workspaceName
         */
        void removeWorkspace(String workspaceName);

        /**
         * Return a list of all the projects available with their contents. Bound to produce a large payload.
         *
         * @return
         */
        Collection<Project> listProjects();

        /**
         * Return the URNs of all the resources available locally.
         *
         * @return
         */
        Collection<String> listResourceUrns();

    }

}
