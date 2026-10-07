package org.integratedmodelling.klab.resources;

import java.io.IOException;
import java.nio.file.*;
import org.eclipse.jgit.api.Git;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.services.ResourcesService.SubmissionMode;

/** Binary-safe material storage. Each mutation stages only its own Git path, never commits. */
public final class ProjectMaterialIO {
  public static final int MAX_BYTES = 32 * 1024 * 1024;

  private ProjectMaterialIO() {}

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
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  public static boolean delete(FileProjectStorage storage, String path) throws IOException {
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

  private static void validateGit(FileProjectStorage storage, String path) throws IOException {
    if (!storage.isTracked()) return;
    try (var git = Git.open(storage.getRootFolder())) {
      var status = git.status().call();
      if (status.getConflicting().contains(path)
          || status.getIgnoredNotInIndex().stream()
              .anyMatch(ignored -> path.equals(ignored) || path.startsWith(ignored + "/")))
        throw new IOException("Material path is ignored by Git or conflicted");
    } catch (org.eclipse.jgit.api.errors.GitAPIException e) {
      throw new IOException(e);
    }
  }
}
