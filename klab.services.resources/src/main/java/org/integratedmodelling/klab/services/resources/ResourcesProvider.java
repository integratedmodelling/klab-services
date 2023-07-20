package org.integratedmodelling.klab.services.resources;

import java.io.File;
import java.io.FileFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.xtext.testing.IInjectorProvider;
import org.integratedmodelling.contrib.jgrapht.Graph;
import org.integratedmodelling.contrib.jgrapht.graph.DefaultDirectedGraph;
import org.integratedmodelling.contrib.jgrapht.graph.DefaultEdge;
import org.integratedmodelling.contrib.jgrapht.traverse.TopologicalOrderIterator;
import org.integratedmodelling.kactors.model.KActors;
import org.integratedmodelling.kdl.model.Kdl;
import org.integratedmodelling.kim.api.IKimObservable;
import org.integratedmodelling.kim.api.IKimProject;
import org.integratedmodelling.kim.model.Kim;
import org.integratedmodelling.kim.model.KimLoader;
import org.integratedmodelling.kim.model.KimLoader.NamespaceDescriptor;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Project.Manifest;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.impl.kim.KimNamespaceImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus.Type;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.configuration.Services;
import org.integratedmodelling.klab.rest.ResourceReference;
import org.integratedmodelling.klab.services.authentication.impl.LocalServiceScope;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resources.assets.ProjectImpl;
import org.integratedmodelling.klab.services.resources.assets.ProjectImpl.ManifestImpl;
import org.integratedmodelling.klab.services.resources.assets.WorkspaceImpl;
import org.integratedmodelling.klab.services.resources.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.services.resources.configuration.ResourcesConfiguration.ProjectConfiguration;
import org.integratedmodelling.klab.services.resources.lang.KActorsAdapter;
import org.integratedmodelling.klab.services.resources.lang.KactorsInjectorProvider;
import org.integratedmodelling.klab.services.resources.lang.KdlInjectorProvider;
import org.integratedmodelling.klab.services.resources.lang.KimAdapter;
import org.integratedmodelling.klab.services.resources.lang.KimInjectorProvider;
import org.integratedmodelling.klab.services.resources.persistence.ModelKbox;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.klab.utilities.Utils.Git;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.serializer.GroupSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.inject.Injector;

@Service
public class ResourcesProvider extends BaseService implements ResourcesService, ResourcesService.Admin {

	private static final long serialVersionUID = 6589150530995037678L;

	private static boolean languagesInitialized;

	private String url;
	private String localName;

	transient private KimLoader kimLoader;
	transient private ResourcesConfiguration configuration = new ResourcesConfiguration();
	transient private Authentication authenticationService;
	transient private ServiceScope scope;

	transient Map<String, Project> localProjects = Collections.synchronizedMap(new HashMap<>());
	transient Map<String, Workspace> localWorkspaces = Collections.synchronizedMap(new HashMap<>());
	transient Map<String, KimNamespace> localNamespaces = Collections.synchronizedMap(new HashMap<>());
	transient Map<String, KActorsBehavior> localBehaviors = Collections.synchronizedMap(new HashMap<>());

	/**
	 * We keep a hash of all the resource URNs we serve for quick reference and
	 * search
	 */
	transient Set<String> localResources = new HashSet<>();

	/**
	 * the only persistent info in this implementation is the catalog of resource
	 * status info. This is used for individual resources and whole projects. It
	 * also holds and maintains the review status, which in the case of projects
	 * propagates to the namespaces and models. Reviews and the rest of the
	 * editorial material should be part of the provenance info associated to the
	 * items.
	 */
	transient DB db = null;
	transient ConcurrentNavigableMap<String, ResourceStatus> catalog = null;
	transient ModelKbox kbox;

	/*
	 * "fair" read/write lock to ensure no reading during updates
	 */
	ReadWriteLock updateLock = new ReentrantReadWriteLock(true);

	/**
	 * Default interval to check for changes in Git (15 minutes in milliseconds)
	 */
	private long DEFAULT_GIT_SYNC_INTERVAL = 15l * 60l * 60l * 1000l;

	/**
	 * If this is used, {@link #loadWorkspaces()} must be called explicitly after
	 * the service scope is set.
	 */
	@SuppressWarnings("unchecked")
	public ResourcesProvider() {
		this.localName = "klab.services.resources." + UUID.randomUUID();
		Services.INSTANCE.setResources(this);
		initializeLanguageServices();

		this.db = DBMaker
				.fileDB(Configuration.INSTANCE.getDataPath("resources/catalog") + File.separator + "gbif_ids.db")
				.transactionEnable().closeOnJvmShutdown().make();
		this.catalog = db.treeMap("resourcesCatalog", GroupSerializer.STRING, GroupSerializer.JAVA).createOrOpen();

		File config = new File(Configuration.INSTANCE.getDataPath() + File.separator + "resources.yaml");
		if (config.exists()) {
			configuration = Utils.YAML.load(config, ResourcesConfiguration.class);
		}
	}

	@Autowired
	public ResourcesProvider(Authentication authenticationService) {
		this();
		this.authenticationService = authenticationService;
//		this.scope = authenticationService.authorizeService(this);
	}
	

	@Override
	public void initializeService(Scope scope) {
		this.scope = new LocalServiceScope(this, scope) {

			@Override
			public Ref getAgent() {
				return null;
			}

			@Override
			public void stop() {
			}
			
		};
		this.kbox = ModelKbox.create(localName, this.scope);
		loadWorkspaces();
	}

	/**
	 * A singleton in nature, this is only to fork the same service in a different
	 * exclusive or dedicated mode.
	 * 
	 * @param self
	 */
	public ResourcesProvider(ResourcesProvider self) {
		this.localName = self.localName;
		this.url = self.url;
		this.db = self.db;
		this.scope = self.scope;
		this.DEFAULT_GIT_SYNC_INTERVAL = self.DEFAULT_GIT_SYNC_INTERVAL;
		this.authenticationService = self.authenticationService;
		this.catalog = self.catalog;
		this.localResources = self.localResources;
		this.localProjects = self.localProjects;
		this.configuration = self.configuration;
		this.localNamespaces = self.localNamespaces;
		this.localWorkspaces = self.localWorkspaces;
		this.kimLoader = self.kimLoader;
	}

	private void saveConfiguration() {
		File config = new File(Configuration.INSTANCE.getDataPath() + File.separator + "resources.yaml");
		Utils.YAML.save(this.configuration, config);
	}

	public void loadWorkspaces() {
		for (String workspaceName : configuration.getWorkspaces().keySet()) {
			Workspace workspace = getWorkspace(workspaceName);
			for (String project : configuration.getWorkspaces().get(workspaceName)) {
				workspace.getProjects().add(loadProject(project, configuration.getProjectConfiguration().get(project)));
			}
		}
	}

	private Workspace getWorkspace(String workspaceName) {
		Workspace ret = localWorkspaces.get(workspaceName);
		if (ret == null) {
			ret = new WorkspaceImpl();
			((WorkspaceImpl) ret).setName(workspaceName);
//			((WorkspaceImpl)ret).setUrl(this.url + "/workspaces/" + workspaceName);
			this.localWorkspaces.put(workspaceName, ret);
		}
		return ret;
	}

	private synchronized Project loadProject(String projectName, final ProjectConfiguration projectConfiguration) {

		/*
		 * this automatically loads namespaces and behaviors through callbacks.
		 */
		ProjectImpl project = new ProjectImpl();
		project.setName(projectName);
//		project.setUrl(this.url + "/projects/" + kimProject.getName());
		localProjects.put(projectName, project);
		IKimProject kimProject = kimLoader.loadProject(projectConfiguration.getLocalPath());
		File resourceDir = new File(kimProject.getRoot() + File.separator + "resources");
		loadResources(resourceDir, project, 0, true);
		project.setManifest(readManifest(kimProject));
		project.getMetadata().putAll(readMetadata(kimProject, project.getManifest()));

		/*
		 * TODO check notifications in namespaces and behaviors and ensure the project
		 * reports errors in its components. Resources are managed indepenendently of
		 * the projects they belong to. FIXME probably the resource permissions could be
		 * intersected, but it's probably confusing although restrictions in projects
		 * should be the default for resources..
		 */
		/*
		 * TODO add a ResourceStatus record for the project
		 */

		db.commit();

		return project;

	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> readMetadata(IKimProject kimProject, Manifest manifest) {
		File manifestFile = new File(
				kimProject.getRoot() + File.separator + "META-INF" + File.separator + "metadata.json");
		if (manifestFile.exists()) {
			return Utils.Json.load(manifestFile, Map.class);
		}
		Map<String, Object> ret = new LinkedHashMap<>();
		for (Object key : kimProject.getProperties().keySet()) {
			ret.put(key.toString(), kimProject.getProperties().getProperty(key.toString()));
		}
		if (!ret.containsKey(Metadata.DC_COMMENT)) {
			ret.put(Metadata.DC_COMMENT, manifest.getDescription());
		}
		return ret;
	}

	private Manifest readManifest(IKimProject project) {
		File manifestFile = new File(
				project.getRoot() + File.separator + "META-INF" + File.separator + "manifest.json");
		if (manifestFile.exists()) {
			return Utils.Json.load(manifestFile, ManifestImpl.class);
		}
		ManifestImpl ret = new ManifestImpl();
		ret.setWorldview(project.getWorldview());
		ret.setDefinedWorldview(project.getDefinedWorldview());
		for (String proj : project.getRequiredProjectNames()) {
			ret.getPrerequisiteProjects().add(Pair.of(proj, Version.EMPTY_VERSION));
		}
		Utils.Json.save(ret, manifestFile);
		return ret;
	}

	private void loadResources(File resourceDir, ProjectImpl project, int level, boolean legacy) {

		/*
		 * load new and legacy resources. This thing returns null if the dir does not
		 * exist.
		 */
		File[] files = resourceDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory() && pathname.canRead();
			}
		});
		if (files != null) {
			for (File subdir : files) {
				Resource resource = null;
				if ("unreviewed".equals(Utils.Files.getFileBaseName(subdir))) {
					loadResources(subdir, project, 0, false);
				} else if ("staging".equals(Utils.Files.getFileBaseName(subdir))) {
					loadResources(subdir, project, 1, false);
				} else {
					resource = KimAdapter.adaptResource(Utils.Json
							.load(new File(subdir + File.separator + "resource.json"), ResourceReference.class));
				}
				if (resource != null) {
					localResources.add(resource.getUrn());
					ResourceStatus status = catalog.get(resource.getUrn());
					if (status == null) {
						status = new ResourceStatus();
						status.setReviewStatus(level);
						status.setFileLocation(subdir);
						status.setType(Utils.Notifications.hasErrors(resource.getNotifications()) ? Type.OFFLINE
								: Type.AVAILABLE);
						status.setLegacy(legacy);
						status.setKnowledgeClass(KnowledgeClass.RESOURCE);
						// TODO fill in the rest
						catalog.put(resource.getUrn(), status);
					}
				}
			}
		}
	}

	private void initializeLanguageServices() {

		if (!languagesInitialized) {

			/*
			 * set up access to the k.IM grammar
			 */
			IInjectorProvider kimInjectorProvider = new KimInjectorProvider();
			Injector kimInjector = kimInjectorProvider.getInjector();
			if (kimInjector != null) {
				Kim.INSTANCE.setup(kimInjector);
			}

			this.kimLoader = new KimLoader((nss) -> loadNamespaces(nss), (behaviors) -> loadBehaviors(behaviors));

			/*
			 * k.DL....
			 */
			IInjectorProvider kdlInjectorProvider = new KdlInjectorProvider();
			Injector kdlInjector = kdlInjectorProvider.getInjector();
			if (kdlInjector != null) {
				Kdl.INSTANCE.setup(kdlInjector);
			}

			/*
			 * ...and k.Actors
			 */
			IInjectorProvider kActorsInjectorProvider = new KactorsInjectorProvider();
			Injector kActorsInjector = kActorsInjectorProvider.getInjector();
			if (kActorsInjector != null) {
				KActors.INSTANCE.setup(kActorsInjector);
			}

			languagesInitialized = true;
		}

	}

	private void loadBehaviors(List<File> behaviors) {
		for (File behaviorFile : behaviors) {
//			projectLoader.execute(() -> {
			KActorsBehavior behavior = KActorsAdapter.INSTANCE.readBehavior(behaviorFile);
			if (behavior.getProjectId() != null) {
				localProjects.get(behavior.getProjectId()).getBehaviors().add(behavior);
			}
			this.localBehaviors.put(behavior.getUrn(), behavior);
//			});
		}
	}

	private void loadNamespaces(List<NamespaceDescriptor> namespaces) {
		for (NamespaceDescriptor ns : namespaces) {
//			projectLoader.execute(() -> {
			KimNamespaceImpl namespace = KimAdapter.adaptKimNamespace(ns);
			Project project = localProjects.get(namespace.getProjectName());
			project.getNamespaces().add(namespace);
			this.localNamespaces.put(namespace.getUrn(), namespace);
			this.kbox.store(namespace, this.scope);
//			});
		}
	}

	@Override
	public KimNamespace resolveNamespace(String urn, Scope scope) {

		KimNamespace ret = localNamespaces.get(urn);
		if (ret != null) {
			/*
			 * check permissions; if not allowed, log and set ret = null
			 */
			ProjectConfiguration pconf = this.configuration.getProjectConfiguration().get(ret.getProjectName());
			if (pconf == null || !authenticationService.checkPermissions(pconf.getPrivileges(), scope)) {
				// scope.info("");
				ret = null;
			}

		} else if (!this.scope.isExclusive()) {

			for (ResourcesService service : Services.INSTANCE.getFederatedResources(scope)) {
				KimNamespace candidate = service.resolveNamespace(urn, scope);
				if (candidate != null) {
					// FIXME this causes more traffic than we should cause - maybe limit full scan
					// to option
					if (ret == null || (ret != null && candidate.getVersion().greater(ret.getVersion()))) {
						ret = candidate;
					}
				}
			}
		}

		return ret;
	}

	@Override
	public KActorsBehavior resolveBehavior(String urn, Scope scope) {

		KActorsBehavior ret = localBehaviors.get(urn);
		if (ret != null) {
			if (ret.getProjectId() != null) {
				ProjectConfiguration pconf = this.configuration.getProjectConfiguration().get(ret.getProjectId());
				if (pconf == null || !authenticationService.checkPermissions(pconf.getPrivileges(), scope)) {
					// scope.info("");
					ret = null;
				}
			}
			return ret;
		} else if (!this.scope.isExclusive()) {

			for (ResourcesService service : Services.INSTANCE.getFederatedResources(scope)) {
				KActorsBehavior candidate = service.resolveBehavior(urn, scope);
				if (candidate != null) {
					// FIXME this causes more traffic than we should cause - maybe limit full scan
					// to option
					if (ret == null || (ret != null && candidate.getVersion().greater(ret.getVersion()))) {
						ret = candidate;
					}
				}
			}
		}
		// TODO
		return null;
	}

	@Override
	public Resource resolveResource(String urn, Scope scope) {
		if (localResources.contains(Urn.removeParameters(urn))) {

		}
		return null;
	}

	@Override
	public Resource contextualizeResource(Resource originalResource, ContextScope scope) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public KlabData contextualize(Resource contextualizedResource, Scope scope) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public KdlDataflow resolveDataflow(String urn, Scope scope) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<KimNamespace> dependents(String namespaceId) {
		return null;
	}

	@Override
	public List<KimNamespace> precursors(String namespaceId) {
		return null;
	}

	@Override
	public synchronized boolean addProject(String workspaceName, String projectUrl, boolean overwriteIfExisting) {

		boolean ret = false;

		updateLock.writeLock().lock();

		try {

			String projectName = Utils.URLs.getURLBaseName(projectUrl);
			ProjectConfiguration config = this.configuration.getProjectConfiguration().get(projectName);
			if (config != null) {
				if (overwriteIfExisting) {
					removeProject(projectName);
				} else {
					return false;
				}
			}

			File workspace = Configuration.INSTANCE.getDataPath(
					configuration.getServicePath() + File.separator + "workspaces" + File.separator + workspaceName);
			workspace.mkdirs();

			if (Utils.Git.isRemoteGitURL(projectUrl)) {

				try {

					/**
					 * This should be the main use of project resources. TODO handle credentials
					 */

					projectName = Git.clone(projectUrl, workspace, false);
					ProjectConfiguration configuration = new ProjectConfiguration();
					configuration.setLocalPath(new File(workspace + File.separator + projectName));
					configuration.setSourceUrl(projectUrl);
					configuration.setWorkspaceName(workspaceName);
					configuration.setSyncIntervalMs(DEFAULT_GIT_SYNC_INTERVAL);

					Set<String> projects = this.configuration.getWorkspaces().get(workspaceName);
					if (projects == null) {
						projects = new LinkedHashSet<>();
						this.configuration.getWorkspaces().put(workspaceName, projects);
					}
					projects.add(projectName);
					Project project = loadProject(projectName, configuration);
					configuration.setWorldview(project.getManifest().getDefinedWorldview() != null);
					this.configuration.getProjectConfiguration().put(projectName, configuration);
					saveConfiguration();

				} catch (Throwable t) {
					ret = false;
				}

			} else if (projectUrl.startsWith("http")) {

				/*
				 * TODO
				 * 
				 * Load from another service. These projects may be served as mirrors or just
				 * kept to meet dependencies, according to the 'served' bit in the
				 * configuration. The source of truth should remain the source code, hosted in a
				 * single place (the remote service); mechanisms should be in place to store the
				 * original server and check for changes and new versions.
				 */

			} else if (projectUrl.startsWith("file:") || new File(projectUrl).isFile()) {

				/*
				 * import a zipped project (from a publish operation) or connect a directory.
				 */

			}
		} finally {
			updateLock.writeLock().unlock();
		}

		return ret;
	}

	@Override
	public void removeProject(String projectName) {

		updateLock.writeLock().lock();

		try {
			ProjectConfiguration configuration = this.configuration.getProjectConfiguration().get(projectName);
			Project project = this.localProjects.get(projectName);
			String workspaceName = getWorkspace(project);
			Workspace workspace = this.localWorkspaces.get(workspaceName);
			Utils.Files.deleteQuietly(configuration.getLocalPath());
			if (this.configuration.getWorkspaces().get(workspaceName) != null) {
				this.configuration.getWorkspaces().get(workspaceName).remove(projectName);
			}
			// remove namespaces, behaviors and resources
			for (KimNamespace namespace : project.getNamespaces()) {
				this.localNamespaces.remove(namespace.getNamespace());
			}
			for (KActorsBehavior behavior : project.getBehaviors()) {
				this.localBehaviors.remove(behavior.getUrn());
			}
			for (String resource : project.getResourceUrns()) {
				localResources.remove(resource);
				catalog.remove(resource);
			}
			this.localProjects.remove(projectName);
			workspace.getProjects().remove(project);
			saveConfiguration();
			db.commit();
		} finally {
			updateLock.writeLock().unlock();
		}
	}

	private String getWorkspace(Project project) {
		for (String ret : this.configuration.getWorkspaces().keySet()) {
			if (this.configuration.getWorkspaces().get(ret).contains(project.getName())) {
				return ret;
			}
		}
		return null;
	}

	@Override
	public void removeWorkspace(String workspaceName) {
		Workspace workspace = this.localWorkspaces.get(workspaceName);
		for (Project project : workspace.getProjects()) {
			removeProject(project.getName());
		}
		try {
			updateLock.writeLock().lock();
			this.localWorkspaces.remove(workspaceName);
		} finally {
			updateLock.writeLock().unlock();
		}
	}

	@Override
	public Collection<Workspace> listWorkspaces() {
		return localWorkspaces.values();
	}

	@Override
	public boolean shutdown() {
		return shutdown(30);
	}

	public boolean shutdown(int secondsToWait) {
//		try {
//			projectLoader.awaitTermination(secondsToWait, TimeUnit.SECONDS);
		return true;
//		} catch (InterruptedException e) {
//			Logging.INSTANCE.error("Error during thread termination", e);
//		}
//		return false;
	}

	@Override
	public Capabilities capabilities() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public KimObservable resolveObservable(String definition) {
		IKimObservable parsed = Kim.INSTANCE.declare(definition);
		return parsed == null ? null : KimAdapter.adaptKimObservable(parsed);
	}

	@Override
	public KimConcept resolveConcept(String definition) {
		IKimObservable parsed = Kim.INSTANCE.declare(definition);
		if (parsed == null) {
			return null;
		}
		return KimAdapter.adaptKimObservable(parsed).getMain();

	}

	@Override
	public ResourceSet projects(Collection<String> projects, Scope scope) {

		ResourceSet ret = new ResourceSet();

		for (String projectName : this.configuration.getProjectConfiguration().keySet()) {
//			ProjectConfiguration project = this.configuration.getProjectConfiguration().get(projectName);
			if (projects.contains(projectName)) {
				ret = Utils.Resources.merge(ret, collectProject(projectName, scope));
			}
		}

		return sort(ret, scope);
	}

	private ResourceSet sort(ResourceSet ret, Scope scope) {

		Graph<String, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

		for (ResourceSet.Resource ns : ret.getNamespaces()) {

			// TODO use a recursive function to capture n-th level deps that aren't resolved
			// directly, although this doesn't apply if we have the whole workspace

			graph.addVertex(ns.getResourceUrn());
			KimNamespace namespace = resolveNamespace(ns.getResourceUrn(), scope);
			if (namespace == null) {
				ret.setEmpty(true);
				return ret;
			}
			for (String imp : namespace.getImports().keySet()) {
				KimNamespace imported = resolveNamespace(imp, scope);
				if (imported == null) {
					ret.setEmpty(true);
					return ret;
				}
				graph.addVertex(imported.getUrn());
				if (imported.getUrn().equals(namespace.getUrn())) {
					System.out.println("DIO ZAPPA");
				}
				graph.addEdge(imported.getUrn(), namespace.getUrn());
			}
		}

		TopologicalOrderIterator<String, DefaultEdge> order = new TopologicalOrderIterator<>(graph);
		Map<String, ResourceSet.Resource> toSort = new HashMap<>();
		ret.getNamespaces().forEach((ns) -> toSort.put(ns.getResourceUrn(), ns));
		ret.getNamespaces().clear();
		while (order.hasNext()) {
			ret.getNamespaces().add(toSort.get(order.next()));
		}

		return ret;
	}

	/**
	 * Collect all known project data, fulfilling any missing external dependencies
	 * but not sorting the results by dependency as this could be one step in a
	 * multiple-project setup. If external dependencies are needed and unsatisfied,
	 * return an empty resourceset.
	 * 
	 * @param projectName
	 * @param scope
	 * @return
	 */
	private ResourceSet collectProject(String projectName, Scope scope) {
		List<KimNamespace> namespaces = new ArrayList<>();
		for (String namespace : this.localNamespaces.keySet()) {
			KimNamespace ns = this.localNamespaces.get(namespace);
			if (projectName.equals(ns.getProjectName())) {
				namespaces.add(ns);
			}
		}
		List<KActorsBehavior> behaviors = new ArrayList<>();
		for (KActorsBehavior behavior : localBehaviors.values()) {
			if (projectName.equals(behavior.getProjectId())) {
				behaviors.add(behavior);
			}
		}

		// Resources work independently and do not come with the project data.

		return Utils.Resources.create(this,
				Utils.Collections.shallowCollection(namespaces, behaviors).toArray(new KlabAsset[namespaces.size()]));
	}

	@Override
	public ResourceSet project(String projectName, Scope scope) {
		return sort(collectProject(projectName, scope), scope);
	}

	@Override
	public ResourceSet model(String modelName, Scope scope) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean publishProject(String projectUrl, ResourcePrivileges permissions) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean unpublishProject(String projectUrl) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String addResource(Resource resource) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String addResource(File resourcePath) {
		// TODO Auto-generated method stub
		// Concept
		return null;
	}

	@Override
	public boolean publishResource(String resourceUrn, ResourcePrivileges permissions) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean unpublishResource(String resourceUrn) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	@Override
	public String getLocalName() {
		return localName;
	}

	public void setLocalName(String localName) {
		this.localName = localName;
	}

	@Override
	public ServiceScope scope() {
		return this.scope;
	}

	@Override
	public ResourceSet queryModels(Observable observable, ContextScope scope) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> queryResources(String urnPattern, KnowledgeClass... resourceTypes) {

		List<String> ret = new ArrayList<>();
		Set<KnowledgeClass> wanted = EnumSet.noneOf(KnowledgeClass.class);
		if (resourceTypes != null && resourceTypes.length > 0) {
			for (KnowledgeClass k : resourceTypes) {
				wanted.add(k);
			}
		} else {
			// we want them all
			for (KnowledgeClass k : KnowledgeClass.values()) {
				wanted.add(k);
			}
		}

		if (wanted.contains(KnowledgeClass.RESOURCE)) {

		}
		if (wanted.contains(KnowledgeClass.MODEL)) {

		}
		if (wanted.contains(KnowledgeClass.SCRIPT)) {

		}
		if (wanted.contains(KnowledgeClass.APPLICATION)) {

		}
		if (wanted.contains(KnowledgeClass.BEHAVIOR)) {

		}
		if (wanted.contains(KnowledgeClass.COMPONENT)) {

		}
		if (wanted.contains(KnowledgeClass.NAMESPACE)) {

		}
		if (wanted.contains(KnowledgeClass.PROJECT)) {

		}
		if (wanted.contains(KnowledgeClass.INSTANCE)) {

		}

		return ret;
	}

	@Override
	public ResourceStatus resourceStatus(String urn, Scope scope) {
		ResourceStatus ret = catalog.get(urn);
		if (ret != null && (ret.getType().isUsable())) {
			/*
			 * TODO check the resource status at this time and in this scope
			 */
		}
		return ret;
	}

	@Override
	public Project resolveProject(String projectName, Scope scope) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Coverage modelGeometry(String modelUrn) throws KIllegalArgumentException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public KActorsBehavior readBehavior(URL url) {
		return KActorsAdapter.INSTANCE.readBehavior(url);
	}

	@Override
	public Collection<Project> listProjects() {
		return localProjects.values();
	}

	@Override
	public Collection<String> listResourceUrns() {
		return localResources;
	}

}
