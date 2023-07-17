package org.integratedmodelling.klab.api.services;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.exceptions.KIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimModelStatement;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;

/**
 * Resource managements. Assets handled include projects with all their assets
 * (namespaces, models, behaviors and local resources) plus any independently
 * submitted resource and component plug-ins as jar/zips. All assets are
 * versioned and history is maintained. Permissions and review-driven ranking
 * are enabled for all primary assets, i.e. projects, components and independent
 * resources. Assets that are parts of projects get their permissions and ranks
 * from the project they are part of.
 * <p>
 * The resource manager can also turn a behavior specification in k.Actors
 * located at a given URL into its correspondent serialized, executable
 * {@link KActorsBehavior}.
 * <p>
 * Endpoints are part of three main families:
 * <dl>
 * <dt>get..()</dt>
 * <dd>endpoints retrieve assets in their serialized form. The <code>get</code>
 * prefix is omitted in this implementation.</dd>
 * <dt>resolve..()</dt>
 * <dd>endpoints retrieve {@link ResourceSet}s that contain all the information
 * needed to reconstruct and use the asset requested at the requesting end,
 * including any dependent assets and their sources;</dd>
 * <dt>{list|add|remove|update}..()</dt>
 * <dd>endpoints manage inquiry and CRUD operations, part of the
 * {@link ResourcesServices.Admin} API</dd>
 * </dl>
 * 
 * In addition, the resource manager exposes querying methods, either based on
 * semantics and context ({@link #queryModels(Observable, ContextScope)}) or on
 * textual search ({@link #queryResources(String, KnowledgeClass...)}). The
 * semantic query model uses the connected reasoner and will only return a
 * ResourceSet listing {@link KimModelStatement}s and their requirements,
 * leaving ranking and prioritization to the caller.
 * 
 * @author Ferd
 *
 */
public interface ResourcesService extends KlabService {

	public static final int DEFAULT_PORT = 8092;

	/**
	 * All services publish capabilities and have a call to obtain them.
	 * 
	 * @author Ferd
	 *
	 */
	interface Capabilities extends ServiceCapabilities {

		boolean isWorldviewProvider();

		String getAdoptedWorldview();

	}

	default String getServiceName() {
		return "klab.resources.service";
	}

	Capabilities capabilities();

	/**
	 * Get the contents of a set of projects. Assumes that the capabilities have
	 * been consulted and have suggested that this is a sensible request.
	 * 
	 * @param scope could be null (defaulting to the service scope) for the entire
	 *              worldview, but a user scope should include the optional parts
	 *              due to the user's group selection.
	 * @return an entire worldview managed by this service, or an empty resource set
	 *         if not available.
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
	 * Request the namespaces containing a given model along with anything else
	 * required to run it properly. This would normally include all the project
	 * namespaces and resources unless scoping has been analyzed and determined it's
	 * not necessary.
	 * 
	 * @param modelName
	 * @param scope
	 * @return
	 */
	ResourceSet model(String modelName, Scope scope);

	/**
	 * Return the parsed contents of a namespace. This only be called after a
	 * request that returned a ResourceSet to ensure correct dependency handling.
	 * 
	 * @param urn
	 * @param scope
	 * @return
	 */
	KimNamespace resolveNamespace(String urn, Scope scope);

	/**
	 * Return the parsed contents of a behavior. This only be called after a request
	 * that returned a ResourceSet to ensure correct dependency handling.
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
	 * Inquire about resource availability for the passed urn and scope. Should work
	 * for all types of assets.
	 * 
	 * @param urn
	 * @param scope
	 * @return
	 */
	ResourceStatus resourceStatus(String urn, Scope scope);

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
	Resource contextualizeResource(Resource originalResource, ContextScope scope);

	/**
	 * 
	 * @param contextualizedResource
	 * @param scope
	 * @return
	 */
	KlabData contextualize(Resource contextualizedResource, Scope scope);

	/**
	 * 
	 * @param urn
	 * @param scope
	 * @return
	 */
	KdlDataflow resolveDataflow(String urn, Scope scope);

	/**
	 * Return all the namespaces that depend on the passed namespace.
	 * 
	 * @param namespaceId
	 * @return
	 */
	List<KimNamespace> dependents(String namespaceId);

	/**
	 * Return all the namespaces that the passed namespace depends on. These must be
	 * available to the resolver prior to loading any namespace. The closure of the
	 * namespace must be complete, no matter if they come from this service or
	 * others: a service cannot serve a namespace unless it's prepared to serve its
	 * entire closure under the same scope.
	 * 
	 * @param namespaceId
	 * @return
	 */
	List<KimNamespace> precursors(String namespaceId);

	/**
	 * Return the URNs of any locally hosted resources whose URN matches the passed
	 * pattern. If any resource types are passed, only return URNs for those. The
	 * pattern should allow simple wildcards like * and .
	 * 
	 * @param urnPattern
	 * @param resourceTypes
	 * @return
	 */
	List<String> queryResources(String urnPattern, KlabAsset.KnowledgeClass... resourceTypes);

	/**
	 * Return the actual (local) project with its contents. Used mostly internally
	 * with a likely heavy result payload, should not resolve the project beyond the
	 * local workspaces.
	 * 
	 * @param projectName
	 * @param scope
	 * @return
	 */
	Project resolveProject(String projectName, Scope scope);

	/**
	 * Return the candidate models for the passed observables in the passed scope
	 * (which will provide the reasoner service). The result should contain an
	 * unordered list of candidate model URNs (in {@link ResourceSet#getUrns()})
	 * along with the needed resources, namespaces and behaviors needed to run them.
	 * Prioritization happens in the resolver.
	 * 
	 * @param observable
	 * @param scope
	 * @return
	 */
	ResourceSet queryModels(Observable observable, ContextScope scope);

	/**
	 * Compute and return the geometry for the model identified by this URN. The
	 * geometry comes from the namespace coverage merged with the model's own, which
	 * in turn is the intersected coverage of its resources plus any model scale
	 * constraints, if any are specified. Models may also restrict their geometry to
	 * specific representations of space/time using annotations or generic
	 * specifications (for example request a grid or a temporal range without
	 * specifying a resolution, or specifying only a range of them, or a resolution
	 * without extent). This information is needed at resolution, so computations
	 * should be cached to avoid wasting CPU in complex operations on repeated
	 * calls.
	 * <p>
	 * The coverage should honor any constraints expressed in the coverage
	 * specifications or annotations, and report a coverage percentage == 0 when
	 * {@link Coverage#getCoverage()} is called whenever any of those aren't met.
	 * 
	 * @param model a known model URN. If the URN is unknown a
	 *              {@link KIllegalArgumentException} should be thrown.
	 * @return the coverage of the model, reporting coverage == 1 unless constraints
	 *         are not met.
	 * @throws KIllegalArgumentException if the URN isn't recognized or does not
	 *                                   specify a model.
	 */
	Coverage modelGeometry(String modelUrn) throws KIllegalArgumentException;

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
	 *
	 */
	interface Admin {

		/**
		 * Add or update a project from an external source to the local repository.
		 * 
		 * @param workspaceName
		 * @param projectUrl          can be a file (zip or existing folder), a git URL
		 *                            (with a potential branch name after a # sign) or a
		 *                            http URL from another resource manager.
		 * @param overwriteIfExisting self-explanatory. If the project is remote, reload
		 *                            if true.
		 * @return true if operation succeeded and anything was done (false if project
		 *         existed and wasn't overwritten)
		 */
		boolean addProject(String workspaceName, String projectUrl, boolean overwriteIfExisting);

		/**
		 * Publish a project with the passed privileges. The project must has been added
		 * before this is called. If the project is already published, update the
		 * permissions.
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
		 * Add a resource fully specified by a resource object to those managed by this
		 * service. Resource is invisible from the outside until published. The resource
		 * adapter must be available to the service.
		 * 
		 * @param resource
		 * @return the resource URN, potentially modified w.r.t. the one in the request.
		 */
		String addResource(Resource resource);

		/**
		 * Add a resource with file content to those managed by this service. Resource
		 * is invisible from the outside until published. The resource adapter must be
		 * available to the service.
		 * 
		 * @param resourcePath the directory or zip file that contains the resource
		 *                     files. A resource.json file must be present, along with
		 *                     anything else required by the adapter.
		 * @return the resource URN, potentially modified w.r.t. the one in the request.
		 */
		String addResource(File resourcePath);

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
		void removeProject(String projectName);

		/**
		 * Remove an entire workspace and all the projects and resources in it.
		 * 
		 * @param workspaceName
		 */
		void removeWorkspace(String workspaceName);

		/**
		 * Return a list of all the workspaces available with their contents. Bound to
		 * produce a large payload.
		 * 
		 * @return
		 */
		Collection<Workspace> listWorkspaces();

		/**
		 * Return a list of all the projects available with their contents. Bound to
		 * produce a large payload.
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