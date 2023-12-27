package org.integratedmodelling.klab.services.resources.storage;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.services.resources.assets.ProjectImpl;
import org.integratedmodelling.klab.services.resources.assets.WorkspaceImpl;
import org.integratedmodelling.klab.services.resources.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.languages.ObservationStrategySyntax;
import org.integratedmodelling.languages.api.NamespaceSyntax;
import org.integratedmodelling.languages.api.OntologySyntax;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Singleton that separates out all the logics in managing workspaces up to and not including the loading of
 * the actual knowledge into k.LAB beans.
 */
public class WorkspaceManager {

    private final ResourcesConfiguration configuration;

    public WorkspaceManager(ResourcesConfiguration configuration) {
        initializeLanguageServices();
        this.configuration = configuration;
    }

    public ProjectStorage getProject(String projectName) {
        return null;
    }

    /**
     * Return all the projects in all workspaces in order of dependency. If projects refer to others that are
     * unavailable locally, those dependencies remain unresolved.
     *
     * @return
     */
    public List<ProjectStorage> getProjects() {
        return null;
    }

    /**
     * Return all known ontologies in order of dependency, ready for loading. Their requirements will only be
     * resolved internally, i.e. some may remain unsatisfied and will need to be resolved from other resource
     * services.
     *
     * @return
     */
    List<OntologySyntax> getOntologies() {
        return null;
    }

    /**
     * Return all the namespaces in order of dependency. Resolution is internal like in
     * {@link #getOntologies()}.
     *
     * @return
     */
    List<NamespaceSyntax> getNamespaces() {
        return null;
    }

    List<ObservationStrategySyntax> getStrategies() {
        return null;
    }

    /**
     * Import a project from a URL into the given workspace and return the associated storage. Project must
     * not exist already.
     *
     * @param projectUrl
     * @param workspaceName
     * @return
     */
    public ProjectStorage importProject(String projectUrl, String workspaceName) {

//        updateLock.writeLock().lock();
        ProjectStorage ret = null;

        try {

            String projectName = Utils.URLs.getURLBaseName(projectUrl);
            ResourcesConfiguration.ProjectConfiguration config =
                    this.configuration.getProjectConfiguration().get(projectName);
            if (config != null) {
                // throw exception
            }

            File workspace = Configuration.INSTANCE.getDataPath(
                    configuration.getServicePath() + File.separator + "workspaces" + File.separator + workspaceName);
            workspace.mkdirs();

            if (Utils.Git.isRemoteGitURL(projectUrl)) {

                try {

                    /**
                     * This should be the main use of project resources. TODO handle credentials
                     */

                    projectName = Utils.Git.clone(projectUrl, workspace, false);
//                    ProjectConfiguration configuration = new ProjectConfiguration();
//                    configuration.setLocalPath(new File(workspace + File.separator + projectName));
//                    configuration.setSourceUrl(projectUrl);
//                    configuration.setWorkspaceName(workspaceName);
//                    configuration.setSyncIntervalMs(DEFAULT_GIT_SYNC_INTERVAL);
//                    /*
//                     * Default privileges are exclusive to the service
//                     */
//                    configuration.setPrivileges(ResourcePrivileges.empty());
//                    // this must happen before loadProject is called.
//                    this.configuration.getProjectConfiguration().put(projectName, configuration);
//
//                    Set<String> projects = this.configuration.getWorkspaces().get(workspaceName);
//                    if (projects == null) {
//                        projects = new LinkedHashSet<>();
//                        this.configuration.getWorkspaces().put(workspaceName, projects);
//                    }
//                    projects.add(projectName);
//                    Project project = importProject(projectName, configuration);
//                    configuration.setWorldview(project.getManifest().getDefinedWorldview() != null);
//                    saveConfiguration();
//                    return true;
                    File ws = new File(workspace + File.separator + projectName);
                    if (ws.exists()) {
                        ret = new FileProjectStorage(ws);
                    }

                } catch (Throwable t) {
                    // just make the return value null
                    File ws = new File(workspace + File.separator + projectName);
                    if (ws.exists()) {
                        Utils.Files.deleteQuietly(ws);
                    }
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

                var file = Utils.URLs.getFileForURL(projectUrl);
                if (file.isDirectory()) {
                    ret = new FileProjectStorage(file);
                } else if (Utils.Files.JAVA_ARCHIVE_EXTENSIONS.contains(Utils.Files.getFileExtension(file))) {
                    // TODO ret = read from archive
                }
            }
        } finally {
//            updateLock.writeLock().unlock();
        }

        return ret;
    }

    /**
     * Read, validate and resolve dependencies for all namespaces and other assets into the workspace
     * descriptors, resolving any missing required projects from the network (if not resolved, log an error
     * and don't load the project). Do not load anything yet: the workspaces are loaded as a unit, starting
     * with any that has a root worldview, then any with any worldview, then the others.
     *
     * @param storage
     * @return
     */
    private boolean importProject(ProjectStorage storage, WorkspaceImpl workspace) {

        /*
        Start with the worldview files. Ensure that the entire
         */

        return false;
    }

    private ProjectStorage newProject(String projectName, String workspaceName) {
        return null;
    }

    private boolean removeProject(String projectName) {
        return false;
    }

    private Project.Manifest readManifest(ProjectStorage project) {
//        File manifestFile = new File(
//                project.getRoot() + File.separator + "META-INF" + File.separator + "manifest.json");
//        if (manifestFile.exists()) {
//            return Utils.Json.load(manifestFile, ProjectImpl.ManifestImpl.class);
//        }
        ProjectImpl.ManifestImpl ret = new ProjectImpl.ManifestImpl();
//        ret.setWorldview(project.getWorldview());
//        ret.setDefinedWorldview(project.getDefinedWorldview());
//        for (String proj : project.getRequiredProjectNames()) {
//            ret.getPrerequisiteProjects().add(Pair.of(proj, Version.EMPTY_VERSION));
//        }
//        Utils.Json.save(ret, manifestFile);
        return ret;
    }


    private void initializeLanguageServices() {

//        if (!languagesInitialized) {

            /*
             * set up access to the k.IM grammar
             */
//            IInjectorProvider kimInjectorProvider = new KimInjectorProvider();
//            Injector kimInjector = kimInjectorProvider.getInjector();
//            if (kimInjector != null) {
//                Kim.INSTANCE.setup(kimInjector);
//            }
//
//            this.kimLoader = new KimLoader((nss) -> loadNamespaces(nss),
//                    (behaviors) -> loadBehaviors(behaviors));
//
//            /*
//             * k.DL....
//             */
//            IInjectorProvider kdlInjectorProvider = new KdlInjectorProvider();
//            Injector kdlInjector = kdlInjectorProvider.getInjector();
//            if (kdlInjector != null) {
//                Kdl.INSTANCE.setup(kdlInjector);
//            }
//
//            /*
//             * ...and k.Actors
//             */
//            IInjectorProvider kActorsInjectorProvider = new KactorsInjectorProvider();
//            Injector kActorsInjector = kActorsInjectorProvider.getInjector();
//            if (kActorsInjector != null) {
//                KActors.INSTANCE.setup(kActorsInjector);
//            }

//            languagesInitialized = true;
        }

    public Collection<Workspace> getWorkspaces() {
        List<Workspace> ret = new ArrayList<>();
        // TODO
        return ret;
    }

}
