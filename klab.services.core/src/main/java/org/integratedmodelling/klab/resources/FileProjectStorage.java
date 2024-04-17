package org.integratedmodelling.klab.resources;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class FileProjectStorage implements ProjectStorage {

    @FunctionalInterface
    public interface ChangeNotifier {
        void apply(String projectName, ProjectStorage.ResourceType resourceType, CRUDOperation changeType,
                   URL newContent);
    }

    private FileWatcher watcher;


    private final File rootFolder;
    private final String projectName;
    private AtomicBoolean locked = new AtomicBoolean(false);

    public FileProjectStorage(File rootFolder, ChangeNotifier notifier) {
        this.rootFolder = rootFolder;
        this.projectName = Utils.Files.getFileBaseName(rootFolder);
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
                        notifier.apply(projectName, type, operation, changedFile.toURI().toURL());
                    } catch (MalformedURLException e) {
                        throw new KlabIOException(e);
                    }
                }
            });
            this.watcher.start();
        }
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

    public void lock(boolean locked) {
        this.locked.set(locked);
    }

    public boolean isLocked() {
        return this.locked.get();
    }

    public Repository getRepository() {
        var gitDir = new File(rootFolder + File.separator + ".git");
        if (gitDir.isDirectory()) {
            try {
                return new FileRepository(gitDir);
            } catch (IOException e) {
                //
            }
        }
        return null;
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
                    collectResources(".kactors", "tests", false, ret);
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
        // TODO overwrite the file. The file monitor does the rest
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
        private AtomicBoolean stop = new AtomicBoolean(false);
        private static Map<WatchKey, Path> keyPathMap = new HashMap<>();

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
