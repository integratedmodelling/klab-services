package org.integratedmodelling.klab.extension;

import org.codehaus.janino.Java;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.utilities.Utils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

public class MavenComponentCache {

    private final File componentCache;
    private final Utils.FileCatalog<ArtifactInfo> cacheCatalog;

    public MavenComponentCache(File dataDirectory) {
        this.componentCache = dataDirectory;
        this.cacheCatalog =
                new Utils.FileCatalog<>(
                        new File(componentCache + File.separator + "catalog.json"),
                        ArtifactInfo.class,
                        ArtifactInfo.class);
    }

    public enum Status {
        UP_TO_DATE,
        NEEDS_UPDATE,
        UNKNOWN
    }

    // descriptor saved in the catalog
    public static class ArtifactInfo {
        private String coordinates;
        private String md5hash;
        private String localtimeSignature;
        private File cachedFile;
        private LocalDateTime lastModified;

        public String getCoordinates() {
            return coordinates;
        }

        public void setCoordinates(String coordinates) {
            this.coordinates = coordinates;
        }

        public String getMd5hash() {
            return md5hash;
        }

        public void setMd5hash(String md5hash) {
            this.md5hash = md5hash;
        }

        public String getLocaltimeSignature() {
            return localtimeSignature;
        }

        public void setLocaltimeSignature(String localtimeSignature) {
            this.localtimeSignature = localtimeSignature;
        }

        public File getCachedFile() {
            return cachedFile;
        }

        public void setCachedFile(File cachedFile) {
            this.cachedFile = cachedFile;
        }

        public LocalDateTime getLastModified() {
            return lastModified;
        }

        public void setLastModified(LocalDateTime lastModified) {
            this.lastModified = lastModified;
        }
    }

    /**
     * If {@link #getAvailability(String, String, String, String, String)} has returned UP_TO_DATE or
     * NEEDS_UPDATE, update if necessary and return the local file.
     *
     * <p>This method updates the cache as needed if anything must be downloaded.
     *
     * @param groupId
     * @param artifactId
     * @param version
     * @param classifier
     * @param suffix
     * @return the local updated file, or null if nothing can be found.
     */
    public File synchronizeArtifact(
            String groupId, String artifactId, String version, String classifier, String suffix) {

        AtomicReference<String> artifactIdRef = new AtomicReference<>();
        AtomicReference<File> fileRef = new AtomicReference<>();
        var status =
                getAvailability(groupId, artifactId, version, classifier, suffix, artifactIdRef, fileRef);
        if (status == Status.UNKNOWN) {
            return null;
        }

        if (status == Status.UP_TO_DATE && fileRef.get() != null) {
            return fileRef.get();
        }

        // must download the file and update the cache

        var signature = groupId + ":" + artifactId + ":" + version + ":" + suffix;
        var current = cacheCatalog.get(artifactIdRef.get());

        if (current != null && current.getCachedFile() != null) {
            if (status == Status.NEEDS_UPDATE) {
                // update and save the new info in catalog, keep old if errors
                var download = Utils.Maven.downloadArtifactFile(groupId, artifactId, version, classifier, suffix, componentCache);
                if (download != null && download.isFile()) {
                    current.setLastModified(LocalDateTime.now());
                    current.setCachedFile(download);
                    cacheCatalog.put(signature, current);
                    cacheCatalog.write();
                }
            }
            return current.getCachedFile();
        } else {
            current = new ArtifactInfo();
            var download = Utils.Maven.downloadArtifactFile(groupId, artifactId, version, classifier, suffix, componentCache);
            if (download != null && download.isFile()) {
                current.setLastModified(LocalDateTime.now());
                current.setCachedFile(download);
                if (!version.endsWith("-SNAPSHOT")) {
                    current.setMd5hash(artifactIdRef.get());
                }
                cacheCatalog.put(signature, current);
                cacheCatalog.write();
            }
        }
        return current.cachedFile;
    }

    /**
     * Establish the availability of an artifact. If the artifact is available in the local .m2
     * repository, look no further. Otherwise check its availability w.r.t. any cached files and
     * configured remote Maven repos. No files are downloaded (except for possibly the MD5 hash).
     *
     * @param groupId
     * @param artifactId
     * @param version
     * @param classifier
     * @param suffix
     * @returns UP_TO_DATE if the file is available locally in the latest version, NEEDS_UPDATE if
     * it's not available or not updated but it's been found on remote or is available in an older
     * version, and UNKNOWN if nothing can be established.
     */
    public Status getAvailability(
            String groupId, String artifactId, String version, String classifier, String suffix) {
        return getAvailability(
                groupId,
                artifactId,
                version,
                classifier,
                suffix,
                new AtomicReference<>(),
                new AtomicReference<>());
    }

    /**
     * Internal version that recovers the MD5 hash if it's downloaded, so we don't have to get it
     * again.
     *
     * @param groupId
     * @param artifactId
     * @param version
     * @param classifier
     * @param suffix
     * @param artifactIdRef set to the MD5 if it must be downloaded
     * @param fileRef       set if the file is available locally without having to be registered in cache
     * @return
     */
    private Status getAvailability(
            String groupId,
            String artifactId,
            String version,
            String classifier,
            String suffix,
            AtomicReference<String> artifactIdRef,
            AtomicReference<File> fileRef) {

        var signature = groupId + ":" + artifactId + ":" + version + ":" + suffix;
        var current = cacheCatalog.get(signature);

        // file isn't there. Must put it there.
        var local = Utils.Maven.findLocalArtifactFile(groupId, artifactId, version, classifier, suffix);
        if (local != null) {
            // in local repo counts as up to date
            fileRef.set(local);
            return Status.UP_TO_DATE;
        } else if (version.endsWith("-SNAPSHOT")) {
            // check out if available on remote repositories
            var latest = Utils.Maven.getLatestSnapshotDate(groupId, artifactId, version);
            if (latest != null) {
                if (current != null && !current.getLastModified().isBefore(latest.getLastModified())) {
                    return Status.UP_TO_DATE;
                } else {
                    return Status.NEEDS_UPDATE;
                }
            }
        }

        // if we get here, we don't have it in the cache or in the local repo, and it's not a SNAPSHOT
        if (current != null) {
            // download the MD5; its existence will tell us if the file is available
            var md5file =
                    Utils.Maven.findOrDownloadArtifactFile(
                            groupId,
                            artifactId,
                            null,
                            null,
                            "md5",
                            Configuration.INSTANCE.getTemporaryDataPath());
            if (md5file != null && md5file.isFile()) {
                var hash = Utils.Files.readFileIntoString(md5file);
                if (current != null) {
                    if (hash.equals(current.getMd5hash())) {
                        return Status.UP_TO_DATE;
                    }
                }
                return Status.NEEDS_UPDATE;
            }
        }
        return Status.UNKNOWN;
    }

    public static void main(String[] args) {

        var cache = new MavenComponentCache(Configuration.INSTANCE.getDataPath("test-component-cache"));
        var result =
                cache.synchronizeArtifact(
                        "org.integratedmodelling",
                        "klab.component.geospatial",
                        "1.0-SNAPSHOT",
                        "component",
                        "kar");

        System.out.println("File is: " + result);
    }
}
