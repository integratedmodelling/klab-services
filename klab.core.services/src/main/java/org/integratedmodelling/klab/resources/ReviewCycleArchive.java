package org.integratedmodelling.klab.resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Local, append-only export of an authorized, persisted closed review cycle into project material.
 * This is deliberately separate from the general project commit/push helpers: it never pushes,
 * invokes hooks, stages another writer's files, resets, or overwrites an existing archive.
 * Callers must resolve the ontology's hosting project, authorize the editor and supply every
 * retained candidate/contribution revision and the closed workflow/event snapshot. Do not export
 * private drafts to a project with broader readership. Closure persistence and retry scheduling
 * belong to the workflow transaction/outbox, not this filesystem helper.
 */
public final class ReviewCycleArchive {
  public static final String SECTION = "review-history";
  public static final int MAX_ENTRIES = 1000;
  public static final int MAX_BYTES = 32 * 1024 * 1024;

  private ReviewCycleArchive() {}

  /** Project material CRUD may read/list this section, but only the archive writer may create it. */
  static void requireMutableMaterial(String path) throws IOException {
    if (path != null && (path.equalsIgnoreCase(SECTION)
        || path.regionMatches(true, 0, SECTION + "/", 0, SECTION.length() + 1)))
      throw new IOException("Review history is immutable; only closed-cycle archiving may write this section");
  }

  /** Stable entry ID identifies a proposal, comments, candidate, or history document across revisions. */
  public record Entry(String id, String kind, String contributor, String proposalId, long revision,
      String sourceRevision, String mediaType, byte[] content) {
    public Entry { content = content == null ? null : content.clone(); }
    @Override public byte[] content() { return content == null ? null : content.clone(); }
  }

  /** closedAt and revision must come from the frozen persisted cycle, never the time of export. */
  public record ClosedCycle(String projectName, String ontologyUrn, String workflowId, String cycleId,
      long revision, Instant closedAt, String sourceRevision, List<Entry> entries) {
    public ClosedCycle { entries = entries == null ? null : List.copyOf(entries); }
  }

  public enum Status { COMMITTED, ALREADY_COMMITTED }
  /** commitId is the actual reachable commit introducing this archive, including on retries. */
  public record Receipt(Status status, String relativePath, String manifestSha256, String commitId) {}

  /** Default project disclosure: internal routing coordinates plus a trusted Resources base URL.
   * No caller-controlled text, contributor, proposal, count, timestamp or workflow revision is
   * accepted for publication. The two UUIDs locate a review; they are never bearer credentials. */
  public record Digest(String projectName, String ontologyUrn, String workflowId, String cycleId,
                       String resourcesBase) {
    public Digest {
      text(projectName); text(ontologyUrn);
      workflowId = uuid(workflowId); cycleId = uuid(cycleId);
      var base = java.net.URI.create(text(resourcesBase));
      if (!("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))
          || base.getHost() == null || base.getRawUserInfo() != null || base.getRawQuery() != null
          || base.getRawFragment() != null || !base.normalize().equals(base))
        throw new IllegalArgumentException("A trusted absolute Resources base without credentials/query/fragment is required");
      resourcesBase = base.toASCIIString().replaceAll("/+$", "");
    }
    public String link() { return resourcesBase + "/ui/review?flow=" + workflowId + "&cycle=" + cycleId; }
  }

  private static String uuid(String value) {
    if (value == null || !value.equals(UUID.fromString(value).toString()))
      throw new IllegalArgumentException("Canonical opaque review UUID required");
    return value;
  }

  public static String relativePath(Digest digest) {
    return SECTION + "/" + sha(digest.ontologyUrn()) + "/" + sha(digest.workflowId()) + "/" + sha(digest.cycleId());
  }

  /** Minimal default. Detailed provenance remains in the permission-enforced workflow service.
   * The fixed service identity prevents publication of an editor's Git identity by inference. */
  public static Receipt archiveDigest(Path projectRoot, Digest digest) throws IOException {
    var files = new TreeMap<String, byte[]>();
    var manifest = new StringBuilder("{\n  \"schema\": \"klab-review-link-digest/1\",");
    field(manifest, "review", digest.link());
    manifest.setLength(manifest.length() - 1);
    manifest.append("\n}\n");
    files.put("manifest.json", manifest.toString().getBytes(StandardCharsets.UTF_8));
    files.put("digest.md", ("# Ontology review\n\nA review cycle has closed.\n\n"
        + "[Consult the persistent review](<" + digest.link() + ">). Sign in to view the stages you are authorized to access.\n"
        + "The link does not grant access. Detailed contributions remain in the workflow service.\n")
        .getBytes(StandardCharsets.UTF_8));
    return write(projectRoot, relativePath(digest), files,
        new PersonIdent("Ontology review service", "review-service@example.invalid"));
  }

  public static String relativePath(ClosedCycle cycle) {
    return SECTION + "/" + sha(text(cycle.ontologyUrn())) + "/"
        + sha(text(cycle.workflowId())) + "/" + sha(text(cycle.cycleId()));
  }

  /**
   * Requires an initialized, attached-branch ordinary Git project. Dirty index/worktree, merge,
   * ignored archives, path aliases and lock contention fail closed. A complete exact untracked
   * archive left by a failed attempt is the only dirty-state exception. No files are removed on
   * failure; a partial archive requires operator inspection rather than speculative repair.
   */
  public static Receipt archive(Path projectRoot, ClosedCycle cycle, PersonIdent committer)
      throws IOException {
    Objects.requireNonNull(committer, "An explicit archive committer is required");
    var files = files(cycle);
    String relative = relativePath(cycle);
    return write(projectRoot, relative, files, committer);
  }

  private static Receipt write(Path projectRoot, String relative, SortedMap<String, byte[]> files,
      PersonIdent committer) throws IOException {
    String manifestHash = sha(files.get("manifest.json"));
    Path root = projectRoot.toRealPath();
    Path target = safePath(root, relative);
    Path gitDirectory = safePath(root, ".git");
    if (!Files.isDirectory(gitDirectory, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Archive requires an ordinary local Git project (.git directory)");
    try (var git = Git.open(root.toFile())) {
      var repository = git.getRepository();
      if (!repository.getWorkTree().toPath().toRealPath().equals(root)
          || !repository.getDirectory().toPath().toRealPath().equals(gitDirectory))
        throw new IOException("Git repository does not match hosting project");
      // Git's real index lock coordinates both archive calls and ordinary Git writers.
      DirCache index = repository.lockDirCache();
      try {
        if (repository.getRepositoryState() != RepositoryState.SAFE)
          throw new IOException("Project is in an unfinished Git operation");
        String branch = repository.getFullBranch();
        if (branch == null || !branch.startsWith(Constants.R_HEADS))
          throw new IOException("Archive requires an attached project branch");
        ObjectId parent = repository.resolve(Constants.HEAD);
        if (parent == null) throw new IOException("Project must have an initial commit");
        boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (exists) verifyFiles(root, target, files);
        try (var walk = new RevWalk(repository)) {
          var head = walk.parseCommit(parent);
          boolean committed = hasPath(repository, head.getTree(), relative);
          if (committed) {
            verifyTree(repository, head.getTree(), relative, files);
            if (!exists) throw new IOException("Committed archive is missing from worktree");
            verifyIndex(index, relative, files);
            return new Receipt(Status.ALREADY_COMMITTED, relative, manifestHash,
                introducingCommit(repository, parent, relative, files));
          }
        }
        var status = git.status().call();
        var dirty = new HashSet<>(status.getUncommittedChanges());
        dirty.addAll(status.getUntracked());
        // A retry may adopt only a complete byte-for-byte exact previous export.
        if (exists) for (String name : files.keySet()) dirty.remove(relative + "/" + name);
        if (!dirty.isEmpty() || !status.getConflicting().isEmpty())
          throw new IOException("Project has unrelated uncommitted changes; archive deferred");
        if (status.getIgnoredNotInIndex().stream().anyMatch(
            p -> relative.equals(p) || relative.startsWith(p + "/") || p.startsWith(relative + "/")))
          throw new IOException("Archive section is ignored by Git");
        if (index.getEntry(relative + "/manifest.json") != null)
          throw new IOException("Archive has uncommitted index entries");

        if (!exists) {
          // CREATE_NEW throughout: never replace editor files, even on an interrupted attempt.
          createParents(root, target);
          Files.createDirectory(target);
          for (var file : files.entrySet()) {
            Path destination = safePath(root, relative + "/" + file.getKey());
            createParents(root, destination);
            Files.write(destination, file.getValue(), StandardOpenOption.CREATE_NEW);
          }
        }
        verifyFiles(root, target, files);
        if (git.status().call().getIgnoredNotInIndex().stream().anyMatch(
            p -> relative.equals(p) || relative.startsWith(p + "/") || p.startsWith(relative + "/")))
          throw new IOException("Archive section is ignored by Git; exact export retained for inspection");
        try (var inserter = repository.newObjectInserter()) {
          var entries = new TreeMap<String, DirCacheEntry>();
          for (int i = 0; i < index.getEntryCount(); i++) {
            var entry = index.getEntry(i);
            entries.put(entry.getPathString(), new DirCacheEntry(entry));
          }
          for (var file : files.entrySet()) {
            String path = relative + "/" + file.getKey();
            if (entries.containsKey(path)) throw new IOException("Archive index collision");
            var entry = new DirCacheEntry(path);
            entry.setFileMode(FileMode.REGULAR_FILE);
            entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, file.getValue()));
            entry.setLength(file.getValue().length);
            entries.put(path, entry);
          }
          var builder = index.builder();
          entries.values().forEach(builder::add);
          builder.finish();
          var commit = new CommitBuilder();
          commit.setTreeId(index.writeTree(inserter));
          commit.setParentId(parent);
          commit.setAuthor(committer);
          commit.setCommitter(committer);
          commit.setMessage("Archive closed ontology review cycle\n\nPath: " + relative
              + "\nManifest-SHA256: " + manifestHash + "\n");
          ObjectId commitId = inserter.insert(commit);
          inserter.flush();
          index.write();
          if (!branch.equals(repository.getFullBranch()))
            throw new IOException("Project branch changed during archive; retry after inspection");
          var update = repository.updateRef(branch);
          update.setExpectedOldObjectId(parent);
          update.setNewObjectId(commitId);
          update.setRefLogIdent(committer);
          update.setRefLogMessage("archive closed review cycle", false);
          RefUpdate.Result result;
          try { result = update.update(); }
          catch (IOException | RuntimeException failure) {
            throw new IOException("Attempted archive commit " + commitId.name()
                + "; branch update outcome requires inspection before retrying", failure);
          }
          if (result != RefUpdate.Result.FAST_FORWARD)
            throw new IOException("Project HEAD changed or is locked; archive deferred: " + result);
          // A crash here can leave HEAD ahead of the index. Never report success until both persist.
          try {
            if (!index.commit()) throw new IOException("Cannot publish index lock file");
          } catch (IOException | RuntimeException failure) {
            throw new IOException("Archive commit " + commitId.name()
                + " exists, but Git index publication failed; inspect before retrying", failure);
          }
          return new Receipt(Status.COMMITTED, relative, manifestHash, commitId.name());
        }
      } finally {
        index.unlock();
      }
    } catch (org.eclipse.jgit.api.errors.GitAPIException e) {
      throw new IOException("Unable to inspect project Git status", e);
    }
  }

  private static SortedMap<String, byte[]> files(ClosedCycle cycle) {
    Objects.requireNonNull(cycle, "Closed cycle required");
    text(cycle.projectName()); text(cycle.ontologyUrn()); text(cycle.workflowId());
    text(cycle.cycleId()); text(cycle.sourceRevision());
    if (cycle.closedAt() == null || cycle.revision() < 1 || cycle.entries() == null
        || cycle.entries().isEmpty() || cycle.entries().size() > MAX_ENTRIES)
      throw new IllegalArgumentException("A frozen cycle revision, closure time and bounded history are required");
    var result = new TreeMap<String, byte[]>();
    var entries = new ArrayList<>(cycle.entries());
    entries.sort(Comparator.comparing(Entry::id).thenComparingLong(Entry::revision));
    long size = 0;
    var manifest = new StringBuilder("{\n  \"schema\": \"klab-review-cycle-archive/1\",");
    field(manifest, "project", cycle.projectName());
    field(manifest, "ontology", cycle.ontologyUrn());
    field(manifest, "workflow", cycle.workflowId());
    field(manifest, "cycle", cycle.cycleId());
    manifest.append("\n  \"closedRevision\": ").append(cycle.revision()).append(',');
    field(manifest, "closedAt", cycle.closedAt().toString());
    field(manifest, "sourceRevision", cycle.sourceRevision());
    manifest.append("\n  \"entries\": [");
    boolean first = true;
    for (var entry : entries) {
      text(entry.id()); text(entry.kind()); text(entry.contributor()); text(entry.proposalId());
      text(entry.sourceRevision()); text(entry.mediaType());
      if (entry.revision() < 1 || entry.content == null)
        throw new IllegalArgumentException("Every entry requires a revision and content");
      size += entry.content.length;
      if (size > MAX_BYTES) throw new IllegalArgumentException("Archive history exceeds 32 MiB");
      String path = "revisions/" + sha(text(entry.id())) + "/r" + entry.revision() + ".bin";
      if (result.putIfAbsent(path, entry.content) != null)
        throw new IllegalArgumentException("Duplicate history entry revision");
      if (!first) manifest.append(',');
      first = false;
      manifest.append("\n    {");
      field(manifest, "id", entry.id()); field(manifest, "kind", entry.kind());
      field(manifest, "contributor", entry.contributor()); field(manifest, "proposal", entry.proposalId());
      manifest.append("\n  \"revision\": ").append(entry.revision()).append(',');
      field(manifest, "sourceRevision", entry.sourceRevision());
      field(manifest, "mediaType", entry.mediaType()); field(manifest, "path", path);
      field(manifest, "sha256", sha(entry.content));
      manifest.append("\n  \"size\": ").append(entry.content.length).append("\n    }");
      if (manifest.length() + size > MAX_BYTES)
        throw new IllegalArgumentException("Archive manifest and history exceed 32 MiB");
    }
    manifest.append("\n  ]\n}\n");
    byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
    if (manifestBytes.length + size > MAX_BYTES)
      throw new IllegalArgumentException("Archive manifest and history exceed 32 MiB");
    result.put("manifest.json", manifestBytes);
    return result;
  }

  private static void field(StringBuilder output, String key, String value) {
    output.append("\n  \"").append(key).append("\": \"");
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> output.append("\\\"");
        case '\\' -> output.append("\\\\");
        default -> {
          if (c < 32 || Character.isSurrogate(c)) output.append(String.format("\\u%04x", (int) c));
          else output.append(c);
        }
      }
    }
    output.append("\",");
  }

  private static String text(String value) {
    if (value == null || value.isBlank() || value.length() > 4096)
      throw new IllegalArgumentException("Archive identifiers and provenance must be nonblank and bounded");
    return value;
  }

  private static String sha(String value) { return sha(value.getBytes(StandardCharsets.UTF_8)); }
  private static String sha(byte[] value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
    catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
  }

  private static Path safePath(Path root, String relative) throws IOException {
    Path target = root.resolve(relative).normalize();
    if (!target.startsWith(root) || target.equals(root)) throw new IOException("Archive escapes project");
    Path part = root;
    for (Path segment : root.relativize(target)) {
      part = part.resolve(segment);
      if (Files.isSymbolicLink(part) || (Files.exists(part, LinkOption.NOFOLLOW_LINKS)
          && !part.toRealPath().equals(part))) throw new IOException("Archive traverses a path alias");
    }
    return target;
  }

  private static void createParents(Path root, Path target) throws IOException {
    Path part = root;
    for (Path segment : root.relativize(target.getParent())) {
      part = part.resolve(segment);
      if (!Files.exists(part, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(part);
      safePath(root, root.relativize(part).toString());
      if (!Files.isDirectory(part, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Archive parent is not a directory");
    }
  }

  private static void verifyFiles(Path root, Path target, Map<String, byte[]> files) throws IOException {
    if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Archive path collision");
    var actual = new HashSet<String>();
    try (var paths = Files.walk(target)) {
      var bounded = paths.limit(MAX_ENTRIES * 4L + 10).toList();
      if (bounded.size() == MAX_ENTRIES * 4L + 10) throw new IOException("Archive has too many filesystem entries");
      for (Path path : bounded) {
        safePath(root, root.relativize(path).toString());
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
        String name = target.relativize(path).toString().replace('\\', '/');
        byte[] expected = files.get(name);
        if (expected == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            || Files.size(path) != expected.length || !Arrays.equals(Files.readAllBytes(path), expected))
          throw new IOException("Immutable archive differs from closed cycle: " + name);
        actual.add(name);
      }
    }
    if (!actual.equals(files.keySet())) throw new IOException("Archive is incomplete; inspect before retrying");
  }

  private static boolean hasPath(Repository repository, ObjectId tree, String path) throws IOException {
    try (var found = TreeWalk.forPath(repository, path, tree)) { return found != null; }
  }

  private static void verifyIndex(DirCache index, String relative, Map<String, byte[]> files)
      throws IOException {
    var actual = new HashSet<String>();
    for (int i = 0; i < index.getEntryCount(); i++) {
      var entry = index.getEntry(i);
      if (!entry.getPathString().startsWith(relative + "/")) continue;
      String name = entry.getPathString().substring(relative.length() + 1);
      byte[] expected = files.get(name);
      if (expected == null || entry.getStage() != 0 || entry.getFileMode() != FileMode.REGULAR_FILE
          || !entry.getObjectId().equals(new ObjectInserter.Formatter().idFor(Constants.OBJ_BLOB, expected)))
        throw new IOException("Committed archive index differs; inspect before retrying");
      actual.add(name);
    }
    if (!actual.equals(files.keySet())) throw new IOException("Committed archive index is incomplete; inspect before retrying");
  }

  private static void verifyTree(Repository repository, ObjectId tree, String relative,
      Map<String, byte[]> files) throws IOException {
    var actual = new HashSet<String>();
    try (var walk = new TreeWalk(repository)) {
      walk.addTree(tree); walk.setRecursive(true);
      walk.setFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(relative));
      while (walk.next()) {
        if (walk.getPathString().equals(relative)) throw new IOException("Archive Git path is not a directory");
        String name = walk.getPathString().substring(relative.length() + 1);
        byte[] expected = files.get(name);
        if (expected == null || walk.getFileMode(0) != FileMode.REGULAR_FILE
            || !walk.getObjectId(0).equals(new ObjectInserter.Formatter().idFor(Constants.OBJ_BLOB, expected)))
          throw new IOException("Immutable Git archive differs from closed cycle");
        actual.add(name);
      }
    }
    if (!actual.equals(files.keySet())) throw new IOException("Immutable Git archive is incomplete");
  }

  private static String introducingCommit(Repository repository, ObjectId head, String relative,
      Map<String, byte[]> files) throws IOException {
    try (var walk = new RevWalk(repository)) {
      var current = walk.parseCommit(head);
      while (current.getParentCount() > 0) {
        var parent = walk.parseCommit(current.getParent(0));
        if (!hasPath(repository, parent.getTree(), relative)) break;
        verifyTree(repository, parent.getTree(), relative, files);
        current = parent;
      }
      return current.name();
    }
  }
}
