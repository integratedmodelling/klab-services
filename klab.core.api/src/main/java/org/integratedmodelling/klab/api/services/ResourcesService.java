package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Management of all {@link KlabAsset}s, collectively called "resources" (although this conflicts
 * with {@link Resource}, which is a specific type of KlabAsset). Assets handled include projects
 * with all their contents (namespaces with {@link KimConceptStatement}, {@link KimModel} and other
 * definitions, {@link KActorsBehavior} behaviors, and local {@link Resource}s), plus any published,
 * independently managed {@link Resource}s, and any component plug-ins, managed directly as
 * jar/zips. All assets are versioned and history is maintained. Permissions and review-driven
 * ranking are enabled for all primary assets, i.e. projects, components and published resources.
 * Assets that come as content of projects get their permissions and ranks from the project they are
 * part of.
 *
 * <p>The resource manager holds all language parsers and can also turn a behavior specification in
 * k.Actors located at a given URL into its correspondent serialized, executable {@link
 * KActorsBehavior}. This should normally only happen for scripts, applications and user behaviors,
 * which can exist independent of projects.
 *
 * <p>If a community service is available in the service scope, the resource manager initiates, and
 * reacts to, events that create the review history of an asset. Primary assets (projects,
 * components and published resources) are subject to review and the resulting rank and history is
 * held by the resources service.
 *
 * <p>Endpoints are part of three main families:
 *
 * <dl>
 *   <dt>get..()
 *   <dd>endpoints retrieve URN-named assets in their serialized form. The <code>get</code> prefix
 *       is omitted in this implementation.
 *   <dt>resolve..()
 *   <dd>endpoints retrieve {@link ResourceSet}s that contain all the information needed to
 *       operationalize the asset requested at the requesting end, including any dependent assets
 *       and their sources. For example, retrieval of a {@link KimModel} is used in the {@link
 *       Resolver} to build a {@link Model} with all its dependencies satisfied.
 *   <dt>{list|add|remove|update}..()
 *   <dd>endpoints manage inquiry and CRUD operations, part of the {@link ResourcesService.Admin}
 *       API
 * </dl>
 *
 * <p>In addition, the resource manager exposes querying methods, either based on semantics and
 * context ({@link #resolveModels(Observable, ContextScope)}) or on textual search ({@link
 * #queryResources(String, KnowledgeClass...)}). The semantic query model uses the connected
 * reasoner and will only return a ResourceSet listing {@link KimModel}s and their requirements,
 * leaving ranking and prioritization to the caller.
 *
 * @author Ferd
 */
public interface ResourcesService extends KlabService {

  /**
   * All services publish capabilities and have a call to obtain them.
   *
   * @author Ferd
   */
  interface Capabilities extends ServiceCapabilities {

    boolean isWorldviewProvider();

    /**
     * If true, the service is connected to an operational reasoner and can support semantically
     * aware calls such as {@link #resolveModels(Observable, ContextScope)}.
     *
     * @return true if semantic search is supported, false otherwise
     */
    boolean isSemanticSearchCapable();

    String getAdoptedWorldview();

    /**
     * Return the workspace IDs handled by this service and accessible to the requesting scope. All
     * workspaces should be editable according to the permissions.
     *
     * @return list of workspace names accessible to the requesting scope
     */
    List<String> getWorkspaceNames();

    /**
     * Return the current CRUD permissions for the authenticated user. These are used as a mask when
     * accessing resources, combined with the resource's own permissions so that resource
     * permissions not available to the requesting identity on a service base are not returned. Only
     * READ should be returned if the service does not implement ResourceService.Admin.
     *
     * @return
     */
    Set<CRUDOperation> getPermissions();
  }

  default String getServiceName() {
    return "klab.resources.service";
  }

  /**
   * Scope CAN be null for generic public capabilities.
   *
   * @param scope the requesting scope for permission validation, can be null for public
   *     capabilities
   * @return the capabilities available to the requesting scope
   */
  Capabilities capabilities(Scope scope);

  /**
   * Get the contents of a set of projects. Assumes that the capabilities have been consulted and
   * have suggested that this is a sensible request.
   *
   * @param scope could be null (defaulting to the service scope) for the entire worldview, but a
   *     user scope should include the optional parts due to the user's group selection.
   * @return an entire worldview managed by this service, or an empty resource set if not available.
   */
  List<ResourceSet> resolveProjects(Collection<String> projects, Scope scope);

  /**
   * Request the namespaces containing a given model along with anything else required to run it
   * properly. This would normally include all the project namespaces and resources, unless the
   * scope has been analyzed and the service has determined what is necessary. Only errors on the
   * side of caution are allowed in determining what's included in the result.
   *
   * @param modelName
   * @param scope
   * @return
   */
  ResourceSet resolveModel(String modelName, Scope scope);

  /**
   * Resolve a specific URN to the object that is represented by it, which must be returned in
   * {@link ResourceSet#setResults(java.util.Set)}. The result must be self-consistent and complete.
   * Return an empty resultset if not found.
   */
  ResourceSet resolve(String urn, Scope scope);

  /**
   * Return the parsed contents of a namespace. This should only be called after a request that
   * returned a ResourceSet to ensure correct dependency handling.
   *
   * @param urn
   * @param urn the URN identifier of the workspace to retrieve
   * @param scope the requesting scope for permission validation
   * @return the workspace corresponding to the URN, or null if not found
   */
  KimNamespace retrieveNamespace(String urn, Scope scope);

  KimOntology retrieveOntology(String urn, Scope scope);

  KimObservationStrategyDocument retrieveObservationStrategyDocument(String urn, Scope scope);

  /**
   * Return a list of all the workspaces available with their contents, filtering according to the
   * requesting identity.
   *
   * @return collection of accessible workspaces with their contents
   */
  Collection<Workspace> listWorkspaces();

  /**
   * Return the parsed contents of a behavior. This only be called after a request that returned a
   * ResourceSet to ensure correct dependency handling.
   *
   * @param urn the URN identifier of the behavior to retrieve
   * @param scope the requesting scope for permission validation
   * @return the parsed behavior, or null if not found or not accessible
   */
  KActorsBehavior retrieveBehavior(String urn, Scope scope);

  /**
   * Return the parsed contents of a resource. One or more resource URNs can be passed; if multiple,
   * the result will be a validated multi-resource container.
   *
   * @param urns list of URN identifiers for the resources to retrieve
   * @param scope the requesting scope for permission validation
   * @return the parsed resource or multi-resource container, or null if not found
   */
  Resource retrieveResource(List<String> urns, Scope scope);

  Workspace retrieveWorkspace(String urn, Scope scope);

  /**
   * Resolve a component (and possibly its dependencies) that provides the passed service call.
   *
   * @param name URN of the service call
   * @param version can be null, in which case the results will reflect the latest available
   * @param scope requesting identity
   * @return
   */
  ResourceSet resolveServiceCall(String name, Version version, Scope scope);

  /**
   * Check if a resource with the passed URN is available in this service and enabled within the
   * passed scope. If so, ensure we have the adapters needed to contextualize it. Multiple URNs may
   * be passed, in which case all will be validated both for accessibility and composability in a
   * multi-URN resource (which should be cached in an internal catalog so it can be accessed with
   * its own URN later).
   *
   * @param urn one or more URNs, possibly containing a version
   * @param scope
   * @return
   */
  ResourceSet resolveResource(List<String> urn, Scope scope);

  /**
   * Return a version of the passed resource that is primed to be used in the passed geometry. Not
   * all adapters require this step before use; in this case the {@link
   * Extensions.AdapterDescriptor#contextualizing()} relative to the adapter will return true.
   *
   * @param resource
   * @param geometry
   * @param scope
   * @return
   */
  Resource contextualizeResource(Resource resource, Geometry geometry, Scope scope);

  /**
   * Inquire about resource availability for the passed urn and scope. Should work for all types of
   * assets.
   *
   * @param urn
   * @param scope
   * @return
   */
  ResourceInfo resourceInfo(String urn, Scope scope);

  /**
   * Set all the asset metadata in one shot. Applies to all kinds of assets that deserve their own
   * resource metadata - i.e. workspaces, projects, components and resources.
   *
   * @param urn
   * @param info the new resource status from now on
   * @param scope
   * @return
   */
  boolean setResourceInfo(String urn, ResourceInfo info, Scope scope);

  /**
   * @param definition the observable definition string to parse
   * @return the parsed KimObservable object, or null if definition is invalid
   */
  KimObservable retrieveObservable(String definition);

  /**
   * Produce the descriptor for the passed concept URN if known.
   *
   * @param conceptUrn a fully specified concept URN, such as "namespace:Concept". Not usable with
   *     concept expressions.
   * @return a valid descriptor or null.
   */
  KimConcept.Descriptor describeConcept(String conceptUrn);

  /**
   * @param definition the concept definition string to parse
   * @return the parsed KimConcept object, or null if definition is invalid
   */
  KimConcept retrieveConcept(String definition);

  /**
   * @param contextualizedResource the resource that needs to be contextualized
   * @param observation must have a geometry set
   * @param event the scheduler event that triggered this contextualization
   * @param input may be null, pass if the resource requires inputs
   * @param scope the scope under which contextualization happens
   * @return the contextualized data object
   */
  Data contextualize(
      Resource contextualizedResource,
      Observation observation,
      Scheduler.Event event,
      Data input,
      Scope scope);

  /**
   * @param urn the URN of the dataflow document to retrieve
   * @param scope the requesting scope for permission validation
   * @return the parsed dataflow document, or null if not found
   */
  KimObservationStrategyDocument retrieveDataflow(String urn, Scope scope);

  /**
   * THe worldview is required to be whole and consistent, including and starting with a root
   * domain. If this service provides a worldview, its capabilities will advertise the worldview ID,
   * which may contain projects not served by this server but resolved at initialization if a
   * worldview is served. The projects that compose the worldview are listed in the worldview's
   * metadata. The projects' versions must be internally consistent and the worldview's version will
   * be the merge of the composing projects' versions.
   *
   * <p>TODO given this paradigm it's possible to serve multiple worldviews, which may require this
   * function to take an ID as parameter.
   *
   * @return the served worldview, possibly empty
   */
  Worldview retrieveWorldview();

  /**
   * Return all the namespaces that depend on the passed namespace.
   *
   * @param namespaceId the ID of the namespace to find dependents for
   * @return list of namespaces that depend on the specified namespace
   */
  List<KimNamespace> dependents(String namespaceId);

  /**
   * Return all the namespaces that the passed namespace depends on. These must be available to the
   * resolver prior to loading any namespace. The closure of the namespace must be complete, no
   * matter if they come from this service or others: a service cannot serve a namespace unless it's
   * prepared to serve its entire closure under the same scope.
   *
   * @param namespaceId the ID of the namespace to find precursors for
   * @return list of namespaces that are dependencies of the specified namespace
   */
  List<KimNamespace> precursors(String namespaceId);

  /**
   * Return the URNs of any locally hosted resources whose URN matches the passed pattern. If any
   * resource types are passed, only return URNs for those. The pattern should allow simple
   * wildcards like * and .
   *
   * @param urnPattern the pattern to match resource URNs against
   * @param resourceTypes optional types to filter the results by
   * @return list of matching resource URNs
   */
  List<String> queryResources(String urnPattern, KlabAsset.KnowledgeClass... resourceTypes);

  /**
   * Return the actual (local) project with its contents. Used mostly internally with a likely heavy
   * result payload, should not resolve the project beyond the local workspaces.
   *
   * @param projectName name of the project to retrieve
   * @param scope the requesting scope for permission validation
   * @return the project with its complete contents, or null if not found
   */
  Project retrieveProject(String projectName, Scope scope);

  /**
   * Return the candidate models resolving the passed observation in the passed scope (which will
   * provide the reasoner service). The result should contain an unordered list of candidate model
   * URNs (in {@link ResourceSet#getResults()}) along with the needed resources, namespaces and
   * behaviors needed to run them. Prioritization and final resource access happens in the resolver.
   *
   * @param observable the observable to find matching models for
   * @param scope the context scope containing the reasoner service
   * @return resource set containing matching models and their dependencies
   */
  ResourceSet resolveModels(Observable observable, ContextScope scope);

  /**
   * Compute and return the "native" geometry for the model identified by this URN. The geometry
   * comes from the namespace coverage merged with the model's own, which in turn is the intersected
   * coverage of its resources plus any model scale constraints, if any are specified. Models may
   * also restrict their geometry to specific representations of space/time using annotations or
   * generic specifications (for example request a grid or a temporal range without specifying a
   * resolution, or specifying only a range of them, or a resolution without extent). This
   * information is needed at resolution, so computations should be cached to avoid wasting CPU in
   * complex operations on repeated calls.
   *
   * <p>The coverage should honor any constraints expressed in the coverage specifications or
   * annotations, and report a coverage percentage == 0 when {@link Coverage#getCoverage()} is
   * called whenever any of those aren't met.
   *
   * @param modelUrn a known model URN. If the URN is unknown a {@link KlabIllegalArgumentException}
   *     should be thrown.
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
   * After importing any kind of resource, call this to register its permissions and ownership with
   * the resources service. The import should always happen through the {@link
   * KlabService#importAsset(ResourceTransport.Schema, ResourceTransport.Schema.Asset, String,
   * Scope)} mechanism; the resources service adds management of distribution, ownership and review.
   *
   * @param urn the URN identifier for the resource
   * @param knowledgeClass the knowledge class of the resource
   * @param fileLocation local file if any, or null. Should also build hash and backup file for
   *     unburdened copying if the file is opened.
   * @param submittingScope the scope requesting the registration
   * @deprecated should be non-API, part of the import mechanism
   * @return resource info for the registered resource
   */
  ResourceInfo registerResource(
      String urn, KnowledgeClass knowledgeClass, File fileLocation, Scope submittingScope);

  /**
   * Admin interface to submit/remove projects and configure the service.
   *
   * @author Ferd
   */
  interface Admin {

    /**
     * Create a workspace with the passed ID and metadata. While workspaces can still be created
     * automatically on demand, calling this is the proper workflow and automatic creation may be
     * disabled in the future. The workspace will initially be accessible only to the owning user,
     * and the rights management system can be used to change that.
     *
     * @param workspace
     * @param metadata
     * @param scope
     * @return true if the workspace creation succeeded. False will be returned if there were errors
     *     or the workspace already existed.
     */
    boolean createWorkspace(String workspace, Metadata metadata, UserScope scope);

    /**
     * Create a new empty project. Use the update function to configure the manifest and the
     * create/update content functions to define the content.
     *
     * @param workspaceName name of the workspace to create the project in
     * @param projectName name of the project to create
     * @param scope the user scope for permission validation
     * @return resource set containing the created project
     */
    ResourceSet createProject(String workspaceName, String projectName, UserScope scope);

    /**
     * Update project manifest and metadata. Project must exist.
     *
     * @param projectName
     * @param manifest
     * @param metadata
     * @param scope a scope that must have previously locked the project
     * @return the updated project with the new metadata and manifest.
     */
    ResourceSet updateProject(
        String projectName, Project.Manifest manifest, Metadata metadata, UserScope scope);

    /**
     * Project must exist; namespace must not. Namespace content is parsed and the results are
     * returned. Errors are reported with the namespace itself; fatal errors will cause an
     * unparseable namespace exception (TODO).
     *
     * @param projectName name of the existing project
     * @param documentUrn URN of the document to create
     * @param documentType type of the document to create
     * @param scope a scope that must have previously locked the project
     * @return list of resource sets containing the created document and any dependencies
     */
    List<ResourceSet> createDocument(
        String projectName,
        String documentUrn,
        ProjectStorage.ResourceType documentType,
        UserScope scope);

    /**
     * Resource must exist in project and be part of a file-based project. This operation makes the
     * change in the filesystem: the service will react by readjusting the knowledge and sending any
     * changes through the listening scopes. If a file is modified by an external process, the
     * method will not need to be called as the adjustment is consequent to the change, not the API
     * call.
     *
     * @param projectName
     * @param documentType
     * @param content
     * @param scope a scope that must have previously locked the project
     * @return a {@link ResourceSet} per affected namespace
     */
    List<ResourceSet> updateDocument(
        String projectName,
        ProjectStorage.ResourceType documentType,
        String content,
        UserScope scope);

    /**
     * Apply the passed operation to the remote repository associated with a project and return
     * whatever has changed. If nothing has changed, the resulting {@link ResourceSet} will be
     * {@link ResourceSet#isEmpty() empty}. If that happened because of errors, the errors will be
     * in the associated {@link ResourceSet#getNotifications() notifications}.
     *
     * <p>The repository operations are (for now) limited to Git repositories and result in 1+
     * atomic Git operations, treating the various steps safely.
     *
     * @param projectName
     * @param operation
     * @param arguments
     * @return a descriptor of what happened and what needs to be reloaded.
     */
    List<ResourceSet> manageRepository(
        String projectName, RepositoryState.Operation operation, String... arguments);

    /**
     * Remove a document in a project. May be a resource when it's exclusive to the project.
     *
     * @param projectName name of the project containing the document to delete
     * @param assetUrn URN of the document/asset to delete
     * @param scope the user scope for permission validation
     * @return list of resource sets affected by the deletion
     */
    List<ResourceSet> deleteDocument(String projectName, String assetUrn, UserScope scope);

    /**
     * Publish an observation from the passed context scope into a persistent resource. The resource
     * will be published at tier 0 with rights restricted to the published.
     *
     * @param observation
     * @param scope
     * @return a future for the completed resource
     */
    CompletableFuture<Resource> publishObservation(Observation observation, ContextScope scope);

    /**
     * @param projectName name of the project to delete
     * @param scope the user scope for permission validation
     * @return list of resource sets affected by the project deletion
     */
    List<ResourceSet> deleteProject(String projectName, UserScope scope);

    /**
     * Remove an entire workspace and all the projects and resources in it.
     *
     * @param workspaceName name of the workspace to delete
     * @param scope the user scope for permission validation
     * @return list of resource sets affected by the workspace deletion
     */
    List<ResourceSet> deleteWorkspace(String workspaceName, UserScope scope);

    /**
     * Return a list of all the projects available with their contents. Bound to produce a large
     * payload.
     *
     * @param scope the requesting scope for permission validation
     * @return collection of all available projects with their contents
     */
    Collection<Project> listProjects(Scope scope);

    /**
     * Return the URNs of all the resources available locally.
     *
     * @param scope the requesting scope for permission validation
     * @return collection of URNs for all locally available resources
     */
    Collection<String> listResourceUrns(Scope scope);

    /**
     * Lock a project so that changes to it can be made exclusively through the explicit CRUD calls
     * on its contents. User must be a privileged administrator.
     *
     * @param urn the URN of the project to lock
     * @throws org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException if the
     *     project is already locked or isn't accessible for any other reason
     * @return true if lock was successful
     */
    boolean lockProject(String urn, UserScope scope);

    /**
     * Unlock a previously locked project.
     *
     * @param urn the URN of the project to lock
     * @param scope the scope that originally locked it
     * @return false if the project wasn't locked or wasn't locked by the same scope
     */
    boolean unlockProject(String urn, UserScope scope);
  }
}
