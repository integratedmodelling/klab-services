package org.integratedmodelling.klab.api.services;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
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
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;

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
 *   <dd>endpoints manage inquiry and CRUD operations
 * </dl>
 *
 * <p>In addition, the resource manager exposes querying methods, either based on semantics and
 * context ({@link #resolveModels(Observable, ContextScope)}) or on textual search ({@link
 * #queryResources(String, Scope, KnowledgeClass...)}). The semantic query model uses the connected
 * reasoner and will only return a ResourceSet listing {@link KimModel}s and their requirements,
 * leaving ranking and prioritization to the caller.
 *
 * @author Ferd
 */
public interface ResourcesService extends KlabService {

  /** For the SUBMIT endpoint, specifying the modality of submission of an asset. */
  enum SubmissionMode {
    /**
     * Override any asset of the same type with the same URN already present. Must have override
     * permissions.
     */
    REPLACE,
    /**
     * Update any asset of the same type with the same URN already present, creating a new version
     * of the asset and storing the previous. Must have update permissions.
     */
    UPDATE,
    /**
     * Merge the contents of the submitted asset into the existing one, creating a new version of
     * the asset and storing the previous. Must have update permissions and the adapter must support
     * merging.
     */
    MERGE,
    /**
     * Just add the asset if it's not already present, ignoring the request if the asset URN is
     * already known.
     */
    ADD,
    /**
     * Add the asset if it's not already present, update it otherwise. Must have create and update
     * permissions.
     */
    CREATE_OR_UPDATE
  }

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

  /**
   * Scope CAN be null for generic public capabilities.
   *
   * @param scope the requesting scope for permission validation, can be null for public
   *     capabilities
   * @return the capabilities available to the requesting scope
   */
  Capabilities capabilities(Scope scope);

  /**
   * Main retrieve endpoint.
   *
   * @param urn the urn of the asset. When appropriate and in some instances mandatorily, the urn
   *     can be prefixed with workspaceId and projectId, separated by forward slashes.
   * @param assetClass
   * @param scope
   * @return
   * @param <T>
   */
  <T extends KlabAsset> T retrieve(String urn, Class<T> assetClass, UserScope scope);

  /**
   * Main delete endpoint.
   *
   * @param urn the urn of the asset. When appropriate and in some instances mandatorily, the urn
   *     can be prefixed with workspaceId and projectId, separated by forward slashes.
   * @param knowledgeClass
   * @param scope
   * @return Changesets for all affected workspaces.
   */
  List<ResourceSet> delete(String urn, KnowledgeClass knowledgeClass, UserScope scope);

  /**
   * Main list endpoint.
   *
   * @param assetClass
   * @param scope
   * @return
   * @param <T>
   */
  <T extends KlabAsset> List<T> list(Class<T> assetClass, UserScope scope);

  /**
   * Main resolve endpoint.
   *
   * @param urn the urn of the asset. When appropriate and in some instances mandatorily, the urn
   *     can be prefixed with workspaceId and projectId, separated by forward slashes.
   * @param assetClass
   * @param scope
   * @return
   */
  ResourceSet resolve(String urn, KnowledgeClass assetClass, UserScope scope);

  /**
   * Submit an asset for the operations specified in #SubmissionMode.
   *
   * @param asset the asset to submit. Use Project.create(), Workspace.create(), etc. to create new
   *     empty containers and documents. Document assets must have their source code set or
   *     exceptions will be thrown.
   * @param submissionMode
   * @param scope
   * @return resource sets for all workspaces affected by the change.
   * @param <T> an asset compatible with the submission mode
   */
  <T extends KlabAsset> List<ResourceSet> submit(
      T asset, SubmissionMode submissionMode, UserScope scope);

  /*
   * Resource management is intentionally limited to the seven generic methods above. Operations
   * below this point are runtime facilities exposed by the resources service, not asset-specific
   * CRUD shortcuts. Keep asset creation, lookup, update and removal in submit/retrieve/list/query/
   * info/resolve/delete so every asset kind follows the same authorization and transport contract.
   */

  /**
   * Return a version of the passed resource that is primed to be used in the given geometry. Not
   * all adapters require this step before use; in this case the {@link Adapter#hasContextualizer()}
   * relative to the adapter will return true.
   *
   * @param resource
   * @param geometry
   * @param scope
   * @return
   */
  Resource contextualizeResource(Resource resource, Geometry geometry, Scope scope);

  /**
   * @param definition the observable definition string to parse
   * @return the parsed KimObservable object, or null if definition is invalid
   */
  KimObservable declareObservable(String definition);

  /**
   * @param definition the concept definition string to parse
   * @return the parsed KimConcept object, or null if definition is invalid
   */
  KimConcept declareConcept(String definition);

  /**
   * Extract data from the passed resource to contextualize the passed observation, whose semantics
   * must be compatible with the type of data extracted.
   *
   * @param contextualizedResource the resource that needs to be contextualized
   * @param observation provides semantics and metadata for the contextualization. The geometry is
   *     ignored, using the explicit parameter instead.
   * @param geometry the geometry of the contextualization. Not necessarily equal to the
   *     observation's, because of possible upstream sharding strategies.
   * @param event the scheduler event that triggered this contextualization
   * @param input data that contains the state relevant to the contextualization. This is null
   *     unless the resource requires inputs.
   * @param scope the scope under which contextualization happens. Normally a ContextScope but it
   *     may be a UserScope in testing situations.
   * @return a completable future for the contextualized data object
   */
  CompletableFuture<Data> contextualize(
      Resource contextualizedResource,
      Observation observation,
      Geometry geometry,
      Scheduler.Event event,
      Data input,
      Scope scope);

  /**
   * Import a new resource, honoring any URN settings (and creating a suitable URN in case the
   * {@link Urn#UNDEFINED_URN} URN is in the submitted resource). If the resource already exists,
   * evaluate if the submission is suitable to represent a newer version. If data are associated
   * with the resource, the submitted resource should contain the full local temporary paths where
   * they are found.
   *
   * <p>Resource validation and ingestion may take time, so the service returns a Future
   *
   * @param resource
   * @param scope
   * @return the {@link ResourceSet} resulting from the submission. May include error notifications
   *     from the import process (not from rights issues, which should throw an exception).
   * @throw KlabResourceAccessException if the scope doesn't have the right to create or modify a
   *     resource.
   */
  Future<ResourceSet> importResource(Resource resource, UserScope scope);

  /**
   * Parse a standalone language document without adding it to a managed workspace. The supported
   * document classes are {@link KActorsBehavior}, {@link KimOntology}, {@link KimNamespace}, and
   * {@link KimObservationStrategyDocument}.
   */
  <T extends KlabDocument<?>> T parseAsset(URL url, Class<T> assetClass, UserScope scope);

  /**
   * Apply the passed operation to the remote repository associated with a project and return
   * whatever has changed. If nothing has changed, the resulting {@link ResourceSet} will be {@link
   * ResourceSet#isEmpty() empty}. If that happened because of errors, the errors will be in the
   * associated {@link ResourceSet#getNotifications() notifications}.
   *
   * <p>The repository operations are (for now) limited to Git repositories and result in 1+ atomic
   * Git operations, treating the various steps safely.
   *
   * @param projectName
   * @param operation
   * @param arguments
   * @return a descriptor of what happened and what needs to be reloaded.
   */
  List<ResourceSet> manageRepository(
      String projectName, RepositoryState.Operation operation, String... arguments);

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
   * Lock a project so that changes to it can be made exclusively through the explicit CRUD calls on
   * its contents. User must be a privileged administrator.
   *
   * @param urn the URN of the project to lock
   * @throws org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException if the project
   *     is already locked or isn't accessible for any other reason
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
