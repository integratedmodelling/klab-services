package org.integratedmodelling.klab.resources;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.UI;
import org.integratedmodelling.klab.utilities.Utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class FileProjectStorage implements ProjectStorage {

    private File file;

    public RepositoryState getRepositoryState() {

        RepositoryState ret = new RepositoryState();

        String currentBranch;
        RepositoryState.Status overallStatus = RepositoryState.Status.UNTRACKED;

        if (isTracked()) {

            try (var repository = new FileRepository(rootFolder + File.separator + ".git")) {

                try (var git = Git.wrap(repository)) {

                    overallStatus = RepositoryState.Status.CLEAN;
                    ret.setCurrentBranch(repository.getBranch());

                    var status = git.status().call();

                    var branches = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();

                    Set<String> branchNames = new HashSet<>();
                    for (var branchName : branches.stream().map(Ref::getName).toList()) {
                        branchName = Utils.Paths.getLast(branchName, '/');
                        branchNames.add(branchName);
                    }

                    ret.getBranchNames().addAll(branchNames);

                    for (var remote : git.remoteList().call()) {
                        if ("origin".equals(remote.getName()) && !remote.getURIs().isEmpty()) {
                            ret.setRepositoryUrl(new URI(remote.getURIs().getFirst().toASCIIString()).toURL());
                        }
                    }

                    for (var changed : status.getModified()) {
                        ret.getModifiedPaths().add(changed);
                        overallStatus = RepositoryState.Status.MODIFIED;
                    }
                    for (var changed : status.getUntracked()) {
                        ret.getUntrackedPaths().add(changed);
                        if (overallStatus == RepositoryState.Status.CLEAN) {
                            overallStatus = RepositoryState.Status.MODIFIED;
                        }
                    }
                    for (var changed : status.getUntrackedFolders()) {
                        ret.getUntrackedFolders().add(changed);
                        if (overallStatus == RepositoryState.Status.CLEAN) {
                            overallStatus = RepositoryState.Status.MODIFIED;
                        }
                    }
                    for (var changed : status.getAdded()) {
                        ret.getAddedPaths().add(changed);
                        if (overallStatus == RepositoryState.Status.CLEAN) {
                            overallStatus = RepositoryState.Status.MODIFIED;
                        }
                    }
                    for (var changed : status.getConflicting()) {
                        ret.getConflictingPaths().add(changed);
                        overallStatus = RepositoryState.Status.CONFLICTED;
                    }
                    for (var changed : status.getUncommittedChanges()) {
                        ret.getUncommittedPaths().add(changed);
                        if (overallStatus == RepositoryState.Status.CLEAN) {
                            overallStatus = RepositoryState.Status.MODIFIED;
                        }
                    }
                    for (var changed : status.getRemoved()) {
                        ret.getRemovedPaths().add(changed);
                        if (overallStatus == RepositoryState.Status.CLEAN) {
                            overallStatus = RepositoryState.Status.MODIFIED;
                        }
                    }
                }
            } catch (Exception e) {
                ret.getNotifications().add(Notification.error(e, UI.Interactivity.DISPLAY));
            }

            ret.setOverallStatus(overallStatus);

        } else {
            ret.getNotifications().add(Notification.info("Project " + projectName + " is not shared on a " +
                    "repository"));
        }
        return ret;
    }

    //    public void updateMetadata(Project project, KlabDocument<?> resource, Scope scope) {
    //
    //        if (isTracked() && project instanceof ProjectImpl pimpl && project.getRepository() instanceof
    //        RepositoryImpl projectMetadata) {
    //
    //            try (var repository = new FileRepository(rootFolder + File.separator + ".git")) {
    //
    //                try (var git = Git.wrap(repository)) {
    //
    //                    projectMetadata.setStatus(Repository.Status.TRACKED);
    //                    projectMetadata.setCurrentBranch(repository.getBranch());
    //
    //                    var status = git.status().call();
    //
    //                    if (projectMetadata.getBranches().isEmpty()) {
    //                        // TODO not doing this unless all branches are empty. When we add/remove
    //                         branches
    //                        //  we must change them manually
    //                        var branches =
    //                                git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
    //
    //                        Set<String> branchNames = new HashSet<>();
    //                        for (var branchName : branches.stream().map(Ref::getName).toList()) {
    //                            branchName = Utils.Paths.getLast(branchName, '/');
    //                            branchNames.add(branchName);
    //                        }
    //
    //                        projectMetadata.getBranches().addAll(branchNames);
    //
    //                        for (var remote : git.remoteList().call()) {
    //                            if ("origin".equals(remote.getName()) && !remote.getURIs().isEmpty()) {
    //                                projectMetadata.setRepositoryUrl(new URI(remote.getURIs().getFirst()
    //                                .toASCIIString()).toURL());
    //                            }
    //                        }
    //                    }
    //
    //                    if (resource != null) {
    //                        if (resource instanceof KlabDocumentImpl<?> document) {
    //                            var resourceType = ProjectStorage.ResourceType.classify(resource);
    //                            if (resourceType != null) {
    //
    //                                var filePath = ProjectStorage.getRelativeFilePath(resource.getUrn(),
    //                                        resourceType, "/");
    //
    //                                if (status.getModified().contains(filePath)) {
    //                                    document.setRepositoryStatus(Repository.Status.MODIFIED);
    //                                } else if (status.getUntracked().contains(filePath)) {
    //                                    document.setRepositoryStatus(Repository.Status.UNTRACKED);
    //                                } else {
    //                                    document.setRepositoryStatus(Repository.Status.CLEAN);
    //                                }
    //                            }
    //                        }
    //
    //                    } else {
    //
    //
    //                        for (var changed : status.getModified()) {
    //                            var document = pimpl.findDocument(changed);
    //                            if (document instanceof KlabDocumentImpl<?> doc) {
    //                                doc.setRepositoryStatus(Repository.Status.MODIFIED);
    //                            }
    //                        }
    //                        for (var changed : status.getUntracked()) {
    //                            var document = pimpl.findDocument(changed);
    //                            if (document instanceof KlabDocumentImpl<?> doc) {
    //                                doc.setRepositoryStatus(Repository.Status.UNTRACKED);
    //                            }
    //                        }
    //                        for (var changed : status.getUntrackedFolders()) {
    //                            var document = pimpl.findDocument(changed);
    //                            if (document instanceof KlabDocumentImpl<?> doc) {
    //                                doc.setRepositoryStatus(Repository.Status.UNTRACKED);
    //                            }
    //                        }
    //                        for (var changed : status.getAdded()) {
    //                            var document = pimpl.findDocument(changed);
    //                            if (document instanceof KlabDocumentImpl<?> doc) {
    //                                doc.setRepositoryStatus(Repository.Status.UNTRACKED);
    //                            }
    //                        }
    //                        for (var changed : status.getConflicting()) {
    //                            var document = pimpl.findDocument(changed);
    //                            if (document instanceof KlabDocumentImpl<?> doc) {
    //                                doc.setRepositoryStatus(Repository.Status.UNTRACKED);
    //                            }
    //                        }
    //                        for (var changed : status.getUncommittedChanges()) {
    //                            var document = pimpl.findDocument(changed);
    //                            if (document instanceof KlabDocumentImpl<?> doc) {
    //                                doc.setRepositoryStatus(Repository.Status.UNTRACKED);
    //                            }
    //                        }
    //                        for (var changed : status.getRemoved()) {
    //                            var document = pimpl.findDocument(changed);
    //                            if (document instanceof KlabDocumentImpl<?> doc) {
    //                                doc.setRepositoryStatus(Repository.Status.UNTRACKED);
    //                            }
    //                        }
    //
    //                    }
    //                }
    //            } catch (Exception e) {
    //                scope.error(e);
    //            }
    //        }
    //    }

    public String getDocumentUrn(ResourceType type, URL url) {

        File file = new File(url.getFile());
        if (file.exists()) {
            /*
            relative path
             */
            var relativePath = file.toPath().relativize(rootFolder.toPath());
            var data = ProjectStorage.getDocumentData(relativePath.toString(), File.separator);
            return data != null && data.getFirst() == type ? data.getSecond() : null;
        }

        return null;
    }

    /**
     * Return the file URL from the document path using the passed separator
     *
     * @param path
     * @param separator
     * @return a valid file URL or null
     */
    public URL getDocumentUrl(String path, String separator) {
        File file = new File(rootFolder + File.separator + (path.startsWith(separator) ?
                                                            path.substring(separator.length())
                                                                                       : path).replace(separator,
                File.separator));
        try {
            return file.exists() ? file.toURI().toURL() : null;
        } catch (MalformedURLException e) {
        }
        return null;
    }

    @FunctionalInterface
    public interface ChangeNotifier {
        void apply(String projectName, List<Triple<ResourceType, CRUDOperation, URL>> changes);
    }

    private FileWatcher watcher;

    private final File rootFolder;
    private final String projectName;
    private final AtomicBoolean locked = new AtomicBoolean(false);

    public FileProjectStorage(File rootFolder, String projectName, ChangeNotifier notifier) {
        this.rootFolder = rootFolder;
        this.projectName = projectName;
        // install file monitor
        if (notifier != null) {
            this.watcher = new FileWatcher(this.rootFolder, (changedFile, action) -> {
                var extension = Utils.Files.getFileExtension(changedFile);
                ResourceType type = switch (extension) {
                    case "kwv" -> ResourceType.ONTOLOGY;
                    case "kim" -> ResourceType.MODEL_NAMESPACE;
                    case "kactors" -> ResourceType.BEHAVIOR;
                    case "obs" -> ResourceType.STRATEGY;
                    // TODO handle all other files, whose role depends on location
                    default -> null;
                };
                CRUDOperation operation = null;
                if (action == StandardWatchEventKinds.ENTRY_MODIFY) {
                    operation = CRUDOperation.UPDATE;
                } else if (action == StandardWatchEventKinds.ENTRY_CREATE) {
                    operation = CRUDOperation.CREATE;
                } else if (action == StandardWatchEventKinds.ENTRY_DELETE) {
                    operation = CRUDOperation.DELETE;
                }

                if (operation != null && type != null && !locked.get()) {
                    try {
                        notifier.apply(projectName, List.of(Triple.of(type, operation,
                                changedFile.toURI().toURL())));
                    } catch (MalformedURLException e) {
                        throw new KlabIOException(e);
                    }
                }
            });
            this.watcher.start();
        }
    }

    @Override
    public Type getType() {
        return Type.FILE;
    }

    @Override
    public String getProjectName() {
        return projectName;
    }

    @Override
    public URL getUrl() {
        try {
            return rootFolder.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new KlabIOException(e);
        }
    }

    //    public Repository.Modifications pullChanges(Scope scope) {
    //        if (isTracked()) {
    //            return Utils.Git.fetchAndMerge(rootFolder, scope);
    //        }
    //        return new Repository.Modifications();
    //    }

    public void lock(boolean locked) {
        this.locked.set(locked);
    }

    public boolean isLocked() {
        return this.locked.get();
    }

    public boolean isTracked() {
        var gitDir = new File(rootFolder + File.separator + ".git");
        return gitDir.isDirectory();
    }

    public File getRootFolder() {
        return rootFolder;
    }

    @Override
    public List<URL> listResources(ResourceType... types) {
        List<URL> ret = new ArrayList<>();
        for (var type : types) {
            switch (type) {
                case ONTOLOGY -> {
                    collectResources(".kwv", "src", true, ret);
                }
                case MODEL_NAMESPACE -> {
                    collectResources(".kim", "src", true, ret);
                }
                case MANIFEST -> {
                    collectResources("manifest.json", "META-INF", false, ret);
                }
                case DOCUMENTATION_NAMESPACE -> {
                    collectResources(".json", "docs", false, ret);
                }
                case STRATEGY -> {
                    collectResources(".obs", "strategies", false, ret);
                }
                case BEHAVIOR -> {
                    collectResources(".kactors", "src", false, ret);
                }
                case APPLICATION -> {
                    collectResources(".kactors", "apps", false, ret);
                }
                case SCRIPT -> {
                    collectResources(".kactors", "scripts", false, ret);
                }
                case TESTCASE -> {
                    collectResources(".kactors", "testcases", false, ret);
                }
                case BEHAVIOR_COMPONENT -> {
                    collectResources(".kactors", "components", false, ret);
                }
                case RESOURCE -> {
                    collectResources("resource.json", "resources", false, ret);
                }
                case RESOURCE_ASSET -> {
                    // ehm - requires the resource name, then pass * as extension
                }
            }
        }
        return ret;
    }

    /**
     * Redefine to implement different storage strategies.
     *
     * @param extension    extension w/o dot, or * for all files
     * @param sourceFolder will be recursed into unless recurse is false
     * @param recurse
     * @param resultUrls
     */
    protected void collectResources(String extension, String sourceFolder, boolean recurse,
                                    List<URL> resultUrls) {

        File root = new File(this.rootFolder + File.separator + sourceFolder.replaceAll("/", File.separator));
        if (root.isDirectory()) {
            scanDirectory(root, extension, recurse, resultUrls);
        }
    }

    private void scanDirectory(File root, String extension, boolean recurse, List<URL> resultUrls) {
        var files = root.listFiles();
        if (files != null) {
            for (var file : files) {
                if (file.isDirectory() && recurse) {
                    scanDirectory(file, extension, true, resultUrls);
                } else if (extension.equals("*") || file.toString().endsWith(extension)) {
                    try {
                        resultUrls.add(file.toURI().toURL());
                    } catch (MalformedURLException e) {
                        throw new KlabIOException(e);
                    }
                }
            }
        }
    }


    @Override
    public URL create(String resourceId, ResourceType resourceType) {

        if (!rootFolder.exists()) {
            rootFolder.mkdirs();
        }

        var relativePath = ProjectStorage.getRelativeFilePath(resourceId, resourceType);
        File file = new File(rootFolder + File.separator + relativePath);

        if (file.exists()) {
            throw new KlabIOException("Cannot overwrite existing document " + file);
        }

        try {

            URL ret = file.toURI().toURL();
            Templates.createDocument(resourceType, resourceId, file);
            return ret;

        } catch (Throwable t) {
            Logging.INSTANCE.error(t);
        }

        return null;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isFilesystemBased() {
        return true;
    }

    public URL update(ResourceType resourceType, String urn, String content) {
        try {
            File resourceFile = new File(rootFolder + File.separator + ProjectStorage.getRelativeFilePath(urn,
                    resourceType, File.separator));
            Utils.Files.writeStringToFile(content, resourceFile);
            return resourceFile.toURI().toURL();
        } catch (Exception e) {
            throw new KlabIOException(e);
        }
    }

    public class FileWatcher extends Thread {
        private final File rootDirectory;
        private final BiConsumer<File, WatchEvent.Kind<?>> action;
        private final AtomicBoolean stop = new AtomicBoolean(false);
        private static final Map<WatchKey, Path> keyPathMap = new HashMap<>();

        public FileWatcher(File rootDirectory, BiConsumer<File, WatchEvent.Kind<?>> actionOnChange) {
            this.rootDirectory = rootDirectory;
            this.action = actionOnChange;
        }

        public boolean isStopped() {
            return stop.get();
        }

        public void stopThread() {
            stop.set(true);
        }

        Set<File> timestamp = new HashSet<>();

        @Override
        public void run() {
            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                Path path = rootDirectory.toPath();
                registerDirectory(watcher, path);
                while (!isStopped()) {

                    // intercepts double events within same cycle
                    Set<Pair<File, WatchEvent.Kind<?>>> events = new HashSet<>();

                    WatchKey key = watcher.take();
                    for (WatchEvent<?> event : key.pollEvents()) {

                        WatchEvent.Kind<?> kind = event.kind();

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path filename = ev.context();

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            Thread.yield();
                            continue;
                        } else {
                            var file = new File(keyPathMap.get(key) + File.separator + filename.toFile());
                            // modification affect both content and timestamp, so we ignore the first
                            if (kind != StandardWatchEventKinds.ENTRY_MODIFY || timestamp.contains(file)) {
                                events.add(Pair.of(file, kind));
                                timestamp.remove(file);
                            } else {
                                // somehow this gives it enough time to intercept the second event every time
                                Thread.sleep(50);
                                timestamp.add(file);
                            }
                        }
                        boolean valid = key.reset();
                        if (!valid) {
                            break;
                        }
                    }
                    Thread.sleep(250);

                    for (var event : events) {
                        action.accept(event.getFirst(), event.getSecond());
                    }
                }
            } catch (Throwable e) {
                // Log or rethrow the error
                throw new KlabIOException(e);
            }
        }

        private void registerDirectory(WatchService watcher, Path path) throws IOException {

            // TODO remove aux directories like .git
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            WatchKey key = path.register(watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            keyPathMap.put(key, path);


            for (File f : path.toFile().listFiles()) {
                registerDirectory(watcher, f.toPath());
            }
        }
    }
}
