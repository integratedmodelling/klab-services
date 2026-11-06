package org.integratedmodelling.klab.resources;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Service-private, bounded digest jobs and durable Git receipts. Feed only committed closure
 * facts. This class never discovers projects or trusts request paths. No scheduler is installed.
 * A trusted access adapter must reauthorize and hold project mutation ownership during each call.
 * A crash after Git commit is recovered by the writer's exact-export/original-commit checks. */
public final class ReviewArchiveOutbox implements AutoCloseable {
  public record Intent(String flowId, String cycleId, String ontologyUrn, long closedRevision) {
    public Intent {
      if (flowId == null || cycleId == null || !UUID.fromString(flowId).toString().equals(flowId)
          || !UUID.fromString(cycleId).toString().equals(cycleId) || ontologyUrn == null
          || ontologyUrn.isBlank() || ontologyUrn.length() > 4096 || closedRevision < 1)
        throw new IllegalArgumentException("A frozen opaque closure coordinate is required");
    }
    public String id() { return flowId + "/" + cycleId; }
  }
  public enum Status { PENDING, BLOCKED, COMMITTED }
  public record Item(Intent intent, Status status, int attempts, String project, String root,
      String resourcesBase, ReviewCycleArchive.Receipt receipt) {}

  /** The adapter uses server-owned ontology hosting data and fresh authority, never a submitted
   * project name/path. It must fail closed on unknown/revoked identity, moved ontology, read-only
   * storage or another mutation owner. Its lock must cover the entire operation callback. */
  @FunctionalInterface public interface ProjectAccess {
    void authorized(Intent intent, Operation operation) throws IOException;
  }
  @FunctionalInterface public interface Operation {
    void archive(String actualProject, Path serviceOwnedRoot, String trustedResourcesBase) throws IOException;
  }
  private static final int MAGIC = 0x52414431;
  private final Path file;
  private final FileChannel channel;
  private final FileLock lock;
  private final int capacity;
  private Map<String, Item> state = new LinkedHashMap<>();
  private boolean closed, poisoned;

  public ReviewArchiveOutbox(Path directory, int capacity) throws IOException {
    if (capacity < 1 || capacity > 100000) throw new IllegalArgumentException("Invalid archive capacity");
    this.capacity = capacity;
    Files.createDirectories(directory);
    Path real = directory.toRealPath();
    if (!directory.toAbsolutePath().normalize().equals(real)) throw new IOException("Archive queue path alias");
    file = real.resolve("review-archives.bin");
    Path lockFile = real.resolve("review-archives.lock");
    for (var path : List.of(file, lockFile)) if (Files.isSymbolicLink(path)
        || Files.exists(path) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      throw new IOException("Archive queue file alias/type");
    channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    try {
      lock = channel.tryLock();
      if (lock == null) throw new IOException("Archive queue already owned");
    } catch (IOException | RuntimeException failure) { channel.close(); throw failure; }
    try { if (Files.exists(file)) state = read(); }
    catch (IOException | RuntimeException failure) { close(); throw failure; }
  }

  public synchronized boolean enqueueCommitted(Intent intent) throws IOException {
    open(); Objects.requireNonNull(intent);
    var existing = state.get(intent.id());
    if (existing != null) {
      if (!existing.intent().equals(intent)) throw new IOException("Conflicting archive closure identity");
      return false;
    }
    if (state.size() >= capacity) throw new IOException("Archive queue capacity reached; retain source intent");
    update(new Item(intent, Status.PENDING, 0, null, null, null, null));
    return true;
  }

  /** Explicit bounded attempts; no automatic tight retry loop and no email/remote Git operation.
   * Git errors may mean a commit exists: the next authorized attempt verifies it before writing.
   * Receipt persistence failure poisons this instance; reopen and replay before any next attempt. */
  public synchronized int dispatch(int limit, ProjectAccess access) throws IOException {
    return dispatchSelected(null, limit, access);
  }
  /** A fresh editor request authorizes one closure only, never other queued flows. */
  public synchronized int dispatchOnly(Intent intent, ProjectAccess access) throws IOException {
    open();
    if (!Objects.equals(state.get(intent.id()) == null ? null : state.get(intent.id()).intent(), intent))
      throw new IOException("Unknown committed archive intent");
    return dispatchSelected(intent.id(), 1, access);
  }
  private int dispatchSelected(String selected, int limit, ProjectAccess access) throws IOException {
    open(); Objects.requireNonNull(access);
    if (limit < 1 || limit > 256) throw new IllegalArgumentException("Invalid archive batch size");
    int processed = 0;
    for (var original : List.copyOf(state.values())) {
      if (selected != null && !selected.equals(original.intent().id())) continue;
      if (processed == limit) break;
      if (original.status() == Status.COMMITTED) continue;
      processed++;
      var attempted = new Item(original.intent(), Status.BLOCKED, Math.min(1000000, original.attempts() + 1),
          original.project(), original.root(), original.resourcesBase(), null);
      update(attempted); // durable before resolving or touching any project
      boolean[] invoked = {false};
      try {
        access.authorized(original.intent(), (project, root, base) -> {
          if (invoked[0]) throw new IOException("Archive access adapter invoked twice");
          invoked[0] = true;
          String actualRoot = root.toRealPath().toString();
          var digest = new ReviewCycleArchive.Digest(project, original.intent().ontologyUrn(),
              original.intent().flowId(), original.intent().cycleId(), base);
          if (attempted.project() != null && (!attempted.project().equals(project)
              || !attempted.root().equals(actualRoot) || !attempted.resourcesBase().equals(digest.resourcesBase())))
            throw new IOException("Frozen archive project/link binding changed; inspect before retry");
          var bound = new Item(original.intent(), Status.BLOCKED, attempted.attempts(), project,
              actualRoot, digest.resourcesBase(), null);
          update(bound); // pins host and link before the first possible Git commit
          var receipt = ReviewCycleArchive.archiveDigest(root, digest);
          update(new Item(original.intent(), Status.COMMITTED, attempted.attempts(), project,
              actualRoot, digest.resourcesBase(), receipt));
        });
      } catch (IOException | RuntimeException failure) {
        if (poisoned) throw new IOException("Archive receipt persistence failed; reopen queue", failure);
        // Raw exceptions/paths/identities do not enter status. BLOCKED is durable and retryable.
      }
    }
    return processed;
  }

  /** Internal operational data contains private host paths; never expose this list as a user DTO. */
  public synchronized List<Item> items() { open(); return List.copyOf(state.values()); }
  private void open() { if (closed || poisoned) throw new IllegalStateException("Archive queue unavailable"); }
  private void update(Item value) throws IOException {
    var next = new LinkedHashMap<>(state); next.put(value.intent().id(), value);
    Path temporary = file.resolveSibling("review-archives-" + UUID.randomUUID() + ".tmp");
    try {
      try (var output = new DataOutputStream(Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW))) {
        output.writeInt(MAGIC); output.writeInt(next.size());
        for (var item : next.values()) {
          var intent = item.intent(); output.writeUTF(intent.flowId()); output.writeUTF(intent.cycleId());
          output.writeUTF(intent.ontologyUrn()); output.writeLong(intent.closedRevision());
          output.writeUTF(item.status().name()); output.writeInt(item.attempts());
          output.writeBoolean(item.project() != null);
          if (item.project() != null) { output.writeUTF(item.project()); output.writeUTF(item.root()); output.writeUTF(item.resourcesBase()); }
          output.writeBoolean(item.receipt() != null);
          if (item.receipt() != null) {
            var receipt = item.receipt(); output.writeUTF(receipt.status().name()); output.writeUTF(receipt.relativePath());
            output.writeUTF(receipt.manifestSha256()); output.writeUTF(receipt.commitId());
          }
        }
      }
      if (Files.size(temporary) > 128 * 1024 * 1024) throw new IOException("Archive queue byte budget reached; retain source intent");
      try (var sync = FileChannel.open(temporary, StandardOpenOption.WRITE)) { sync.force(true); }
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      state = next;
    } catch (IOException | RuntimeException failure) { poisoned = true; throw failure; }
    finally { Files.deleteIfExists(temporary); }
  }
  private Map<String, Item> read() throws IOException {
    if (Files.size(file) > 128 * 1024 * 1024) throw new IOException("Archive queue exceeds byte budget");
    var loaded = new LinkedHashMap<String, Item>();
    try (var input = new DataInputStream(Files.newInputStream(file))) {
      if (input.readInt() != MAGIC) throw new IOException("Unknown archive queue format");
      int count = input.readInt();
      if (count < 0 || count > capacity) throw new IOException("Invalid archive queue size");
      for (int i = 0; i < count; i++) {
        var intent = new Intent(input.readUTF(), input.readUTF(), input.readUTF(), input.readLong());
        var status = Status.valueOf(input.readUTF()); int attempts = input.readInt();
        String project = null, root = null, base = null;
        if (input.readBoolean()) { project = input.readUTF(); root = input.readUTF(); base = input.readUTF(); }
        ReviewCycleArchive.Receipt receipt = null;
        if (input.readBoolean()) receipt = new ReviewCycleArchive.Receipt(ReviewCycleArchive.Status.valueOf(input.readUTF()),
            input.readUTF(), input.readUTF(), input.readUTF());
        if (attempts < 0 || attempts > 1000000 || (status == Status.COMMITTED) != (receipt != null)
            || receipt != null && project == null || loaded.putIfAbsent(intent.id(),
            new Item(intent, status, attempts, project, root, base, receipt)) != null)
          throw new IOException("Invalid archive queue record");
        if (project != null) {
          var digest = new ReviewCycleArchive.Digest(project, intent.ontologyUrn(), intent.flowId(), intent.cycleId(), base);
          if (!Path.of(root).isAbsolute() || receipt != null && (!receipt.relativePath().equals(ReviewCycleArchive.relativePath(digest))
              || !receipt.manifestSha256().matches("[a-f0-9]{64}") || !receipt.commitId().matches("[a-f0-9]{40}")))
            throw new IOException("Invalid archive receipt binding");
        }
      }
      if (input.read() != -1) throw new IOException("Trailing archive queue data");
    } catch (RuntimeException failure) { throw new IOException("Invalid archive queue", failure); }
    return loaded;
  }
  @Override public synchronized void close() throws IOException {
    if (!closed) { closed = true; try { lock.release(); } finally { channel.close(); } }
  }
}
