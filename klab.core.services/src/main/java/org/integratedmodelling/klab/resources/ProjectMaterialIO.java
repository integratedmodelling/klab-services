package org.integratedmodelling.klab.resources;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.services.ResourcesService.SubmissionMode;

/** Binary-safe material storage. Each mutation stages only its own Git path, never commits. */
public final class ProjectMaterialIO {
  public static final int MAX_BYTES = 32 * 1024 * 1024;
  public static final String REVIEW_DECISIONS = "review/decisions.jsonl";

  private ProjectMaterialIO() {}

  /** Shallow authoring listing. Directory discovery is authorized by the Resources provider. */
  public static ProjectMaterial retrieve(FileProjectStorage storage, ProjectMaterial material)
      throws IOException {
    if (!material.isDirectory()) {
      byte[] content = read(storage, material.getPath());
      if (content == null) return null;
      material.setContent(content);
      material.setSize(content.length);
      return material;
    }
    Path directory = material.getPath().isEmpty()
        ? storage.getRootFolder().toPath().toRealPath() : target(storage, material.getPath());
    if (!Files.exists(directory)) return null;
    if (!Files.isDirectory(directory)) throw new IOException("Material is not a directory");
    var entries = new java.util.ArrayList<ProjectMaterial>();
    try (var stream = Files.newDirectoryStream(directory)) {
      for (var entry : stream) {
        String relative = material.getPath().isEmpty() ? entry.getFileName().toString()
            : material.getPath() + "/" + entry.getFileName();
        try {
          target(storage, relative);
        } catch (IllegalArgumentException | IOException excluded) { continue; }
        if (!Files.isDirectory(entry) && !Files.isRegularFile(entry)) continue;
        if (entries.size() == 10000) throw new IOException("Material directory exceeds 10000 entries");
        var child = new ProjectMaterial(material.getProjectName(), relative, null);
        child.setDirectory(Files.isDirectory(entry));
        child.setSize(child.isDirectory() ? 0 : Files.size(entry));
        entries.add(child);
      }
    }
    entries.sort(java.util.Comparator.comparing(ProjectMaterial::isDirectory).reversed()
        .thenComparing(ProjectMaterial::getPath));
    material.setContent(null);
    material.setEntries(entries);
    return material;
  }

  /** Only ADD creates directories. Existing directories and the listing root cannot be overwritten. */
  public static void createDirectory(FileProjectStorage storage, ProjectMaterial material,
      SubmissionMode mode) throws IOException {
    ReviewCycleArchive.requireMutableMaterial(material.getPath());
    if (storage.isReadOnly()) throw new IOException("Read-only project");
    if (!material.isDirectory() || material.getContent() != null || mode != SubmissionMode.ADD)
      throw new IllegalArgumentException("Directories require ADD and no content");
    Path directory = target(storage, material.getPath());
    validateGit(storage, material.getPath());
    Files.createDirectory(directory);
  }

  public static boolean exists(FileProjectStorage storage, String path) throws IOException {
    return Files.exists(target(storage, path));
  }

  /** Empty-directory deletion is deliberate: never recursively remove unlisted project content. */
  public static boolean deleteDirectory(FileProjectStorage storage, String path) throws IOException {
    ReviewCycleArchive.requireMutableMaterial(path);
    if (storage.isReadOnly()) throw new IOException("Read-only project");
    Path directory = target(storage, path);
    if (!Files.exists(directory)) return false;
    if (!Files.isDirectory(directory)) throw new IOException("Material is not a directory");
    validateGit(storage, path);
    Files.delete(directory);
    return true;
  }

  private static Path target(FileProjectStorage storage, String relative) throws IOException {
    ProjectMaterial.canonicalPath(relative);
    Path root = storage.getRootFolder().toPath().toRealPath();
    Path target = root.resolve(relative).normalize();
    if (!target.startsWith(root) || target.equals(root))
      throw new IOException("Path escapes project");
    Path part = root;
    for (Path segment : root.relativize(target)) {
      part = part.resolve(segment);
      if (Files.isSymbolicLink(part))
        throw new IOException("Material paths cannot traverse symbolic links");
      if (Files.exists(part, LinkOption.NOFOLLOW_LINKS)
          && (!part.toRealPath().startsWith(root) || !part.toRealPath().equals(part)))
        throw new IOException("Material path traverses an alias or escapes project");
    }
    return target;
  }

  public static byte[] read(FileProjectStorage storage, String path) throws IOException {
    Path target = target(storage, path);
    if (!Files.exists(target)) return null;
    if (!Files.isRegularFile(target) || Files.size(target) > MAX_BYTES)
      throw new IOException("Invalid material size or file");
    try (var input = Files.newInputStream(target)) {
      byte[] bytes = input.readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) throw new IOException("Material exceeds 32 MiB");
      return bytes;
    }
  }

  public static void write(
      FileProjectStorage storage, String path, byte[] content, SubmissionMode mode)
      throws IOException {
    ReviewCycleArchive.requireMutableMaterial(path);
    writeMaterial(storage, path, content, mode);
  }

  /** Prepare an append under the workspace manager's project lock. */
  public static byte[] readReviewDecisions(FileProjectStorage storage) throws IOException {
    if (storage.isReadOnly() || !storage.isTracked())
      throw new IOException("Review decisions require a writable Git project");
    validateGit(storage, REVIEW_DECISIONS);
    return read(storage, REVIEW_DECISIONS);
  }

  public static void appendReviewDecision(FileProjectStorage storage, byte[] previous, byte[] decision,
      String directory, Map<String, byte[]> snapshots)
      throws IOException {
    if (storage.isReadOnly() || !storage.isTracked() || !Arrays.equals(previous, read(storage, REVIEW_DECISIONS)))
      throw new IOException("Review history changed during the source save");
    int size = previous == null ? 0 : previous.length;
    int separator = size > 0 && previous[size - 1] != '\n' ? 1 : 0;
    if (size + separator + decision.length + 1 > MAX_BYTES)
      throw new IOException("Review history exceeds 32 MiB");
    byte[] next = previous == null ? new byte[decision.length + 1]
        : Arrays.copyOf(previous, size + separator + decision.length + 1);
    if (separator != 0) next[size] = '\n';
    System.arraycopy(decision, 0, next, size + separator, decision.length);
    next[next.length - 1] = '\n';
    Path ledger = target(storage, REVIEW_DECISIONS);
    Path destination = target(storage, directory);
    var files = new LinkedHashMap<String, byte[]>();
    for (var snapshot : snapshots.entrySet()) {
      if (!snapshot.getKey().matches("(?:base|result)\\.kwv") || snapshot.getValue().length > MAX_BYTES)
        throw new IOException("Invalid decision snapshot");
      files.put(directory + "/" + snapshot.getKey(), snapshot.getValue());
    }
    Files.createDirectories(destination.getParent());
    Path temporary = Files.createTempDirectory(destination.getParent(), ".decision-");
    try {
      for (var snapshot : snapshots.entrySet())
        Files.write(temporary.resolve(snapshot.getKey()), snapshot.getValue());
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      for (var name : snapshots.keySet()) Files.deleteIfExists(temporary.resolve(name));
      Files.deleteIfExists(temporary);
    }
    writeMaterialFile(ledger, next);
    files.put(REVIEW_DECISIONS, next);
    // Keep both snapshots and the ledger on disk if Git staging fails after the source save.
    validateGit(storage, files.keySet().toArray(String[]::new));
    try (var git = Git.open(storage.getRootFolder())) {
      stageReviewFiles(git, files, true);
    }
  }

  private static void writeMaterial(
      FileProjectStorage storage, String path, byte[] content, SubmissionMode mode)
      throws IOException {
    if (storage.isReadOnly()) throw new IOException("Read-only project");
    if (content == null || content.length > MAX_BYTES)
      throw new IOException("Material content is required and limited to 32 MiB");
    Path target = target(storage, path);
    boolean exists = Files.exists(target);
    if (mode != SubmissionMode.ADD
        && mode != SubmissionMode.UPDATE
        && mode != SubmissionMode.CREATE_OR_UPDATE)
      throw new IllegalArgumentException("Material supports ADD, UPDATE and CREATE_OR_UPDATE");
    if (mode == SubmissionMode.ADD && exists) throw new IOException("Material already exists");
    if (mode == SubmissionMode.UPDATE && !exists) throw new IOException("Material does not exist");
    if (exists && !Files.isRegularFile(target))
      throw new IOException("Material is not a regular file");
    byte[] previous = exists ? read(storage, path) : null;
    validateGit(storage, path);
    replaceMaterial(storage, path, target, content, previous);
  }

  private static void replaceMaterial(FileProjectStorage storage, String path, Path target,
      byte[] content, byte[] previous) throws IOException {
    writeMaterialFile(target, content);
    try {
      validateGit(storage, path);
      if (storage.isTracked())
        try (var git = Git.open(storage.getRootFolder())) {
          git.add().addFilepattern(path).call();
        }
    } catch (Exception failure) {
      if (previous == null) Files.deleteIfExists(target);
      else Files.write(target, previous);
      throw new IOException("Cannot stage material contribution", failure);
    }
  }

  private static void writeMaterialFile(Path target, byte[] content) throws IOException {
    Files.createDirectories(target.getParent());
    Path temporary = Files.createTempFile(target.getParent(), ".material-", ".tmp");
    try {
      Files.write(temporary, content);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  public static boolean delete(FileProjectStorage storage, String path) throws IOException {
    ReviewCycleArchive.requireMutableMaterial(path);
    if (storage.isReadOnly()) throw new IOException("Read-only project");
    Path target = target(storage, path);
    if (!Files.exists(target)) return false;
    if (!Files.isRegularFile(target)) throw new IOException("Material is not a regular file");
    validateGit(storage, path);
    try {
      if (storage.isTracked())
        try (var git = Git.open(storage.getRootFolder())) {
          if (git.getRepository().readDirCache().getEntry(path) != null) {
            git.rm().addFilepattern(path).call();
            return true;
          }
        }
      Files.delete(target);
      return true;
    } catch (Exception failure) {
      throw new IOException("Cannot delete material contribution", failure);
    }
  }

  /** Publish an immutable review bundle and stage its original byte streams together. */
  public static void addReviewBundle(FileProjectStorage storage, String directory,
      Map<String, byte[]> files) throws IOException {
    if (storage.isReadOnly() || !storage.isTracked())
      throw new IOException("Review bundle submission requires a writable Git project");
    for (var name : files.keySet()) {
      if (!name.matches("[a-z0-9][a-z0-9.-]*"))
        throw new IOException("Review bundles require flat, canonical filenames");
      target(storage, directory + "/" + name);
    }
    var destination = target(storage, directory);
    if (Files.exists(destination)) {
      try (var children = Files.list(destination); var git = Git.open(storage.getRootFolder())) {
        if (children.count() == files.size()) {
          var repository = git.getRepository();
          var index = repository.readDirCache();
          boolean identical = true, tracked = true, untracked = true;
          for (var file : files.entrySet()) {
            String relative = directory + "/" + file.getKey();
            var entry = index.getEntry(relative);
            identical &= Arrays.equals(file.getValue(), read(storage, relative));
            untracked &= entry == null;
            tracked &= entry != null && entry.getStage() == 0
                && Arrays.equals(file.getValue(),
                    repository.open(entry.getObjectId(), Constants.OBJ_BLOB).getCachedBytes(MAX_BYTES));
          }
          if (identical && tracked) return;
          if (identical && untracked) {
            stageReviewBundle(storage, git, directory, files);
            return;
          }
        }
      }
      throw new IOException("Review bundle already exists with different content or index state");
    }

    Files.createDirectories(destination.getParent());
    Path temporary = Files.createTempDirectory(destination.getParent(), ".review-");
    boolean published = false;
    boolean staged = false;
    try (var git = Git.open(storage.getRootFolder())) {
      for (var file : files.entrySet())
        Files.write(temporary.resolve(file.getKey()), file.getValue());
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      published = true;
      stageReviewBundle(storage, git, directory, files);
      staged = true;
    } finally {
      if (!staged) {
        var incomplete = published ? destination : temporary;
        for (var name : files.keySet()) Files.deleteIfExists(incomplete.resolve(name));
        Files.deleteIfExists(incomplete);
      }
    }
  }

  private static void stageReviewBundle(FileProjectStorage storage, Git git, String directory,
      Map<String, byte[]> files) throws IOException {
    var paths = new LinkedHashMap<String, byte[]>();
    for (var file : files.entrySet()) {
      var path = directory + "/" + file.getKey();
      validateGit(storage, path);
      paths.put(path, file.getValue());
    }
    stageReviewFiles(git, paths, false);
  }

  private static void stageReviewFiles(Git git, Map<String, byte[]> files, boolean replace)
      throws IOException {
    try (var inserter = git.getRepository().newObjectInserter()) {
      var index = git.getRepository().lockDirCache();
      try {
        var editor = index.editor();
        for (var file : files.entrySet()) {
          var relative = file.getKey();
          var existing = index.getEntry(relative);
          if (existing != null && (!replace || existing.getStage() != 0))
            throw new IOException("Review material collides with a Git index entry");
          var blob = inserter.insert(Constants.OBJ_BLOB, file.getValue());
          editor.add(new DirCacheEditor.PathEdit(relative) {
            @Override public void apply(DirCacheEntry entry) {
              entry.setFileMode(FileMode.REGULAR_FILE);
              entry.setObjectId(blob);
              entry.setLength(file.getValue().length);
            }
          });
        }
        inserter.flush();
        if (!editor.commit()) throw new IOException("Cannot stage review material");
      } finally {
        index.unlock();
      }
    }
  }

  private static void validateGit(FileProjectStorage storage, String... paths) throws IOException {
    if (!storage.isTracked()) return;
    try (var git = Git.open(storage.getRootFolder())) {
      var status = git.status().call();
      for (var path : paths)
        if (status.getConflicting().contains(path) || status.getIgnoredNotInIndex().stream()
            .anyMatch(ignored -> path.equals(ignored) || path.startsWith(ignored + "/")))
          throw new IOException("Material path is ignored by Git or conflicted");
    } catch (org.eclipse.jgit.api.errors.GitAPIException e) {
      throw new IOException(e);
    }
  }
}
