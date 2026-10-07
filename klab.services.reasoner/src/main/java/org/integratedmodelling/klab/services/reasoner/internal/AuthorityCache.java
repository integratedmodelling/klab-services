package org.integratedmodelling.klab.services.reasoner.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;

/** Bounded, atomic JSON cache. Cache loss or corruption never prevents provider access. */
final class AuthorityCache {
  private static final int MAX_BYTES = 4 * 1024 * 1024;
  private static final long MAX_DISK_BYTES = 256L * 1024 * 1024;
  private static final int MAX_DISK_ENTRIES = 10000;
  private static final ObjectMapper JSON = new ObjectMapper();
  private final Path directory;
  private final Clock clock;
  private boolean warned;
  private long writes;
  private final Map<String, JsonNode> memory = new LinkedHashMap<>(16, .75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<String, JsonNode> entry) { return size() > 512; }
  };

  AuthorityCache(Path directory, Clock clock) { this.directory = directory; this.clock = clock; }

  synchronized JsonNode get(String key) {
    var entry = memory.get(key);
    if (entry == null) {
      try {
        var file = file(key);
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES) return null;
        entry = JSON.readTree(Files.readAllBytes(file));
      } catch (IOException | RuntimeException e) { return null; }
    }
    if (entry == null || !entry.has("value") || !entry.path("expires").isIntegralNumber()
        || entry.path("expires").asLong() <= clock.millis()) {
      memory.remove(key);
      return null;
    }
    memory.put(key, entry);
    return entry.get("value").deepCopy();
  }

  synchronized void put(String key, JsonNode value, long seconds) {
    if (seconds == 0) return;
    var entry = JSON.createObjectNode();
    long now = clock.millis();
    long expires = seconds > (Long.MAX_VALUE - now) / 1000 ? Long.MAX_VALUE : now + seconds * 1000;
    entry.put("expires", expires);
    entry.set("value", value.deepCopy());
    Path temporary = null;
    try {
      byte[] bytes = JSON.writeValueAsBytes(entry);
      if (bytes.length > MAX_BYTES) return;
      memory.put(key, entry);
      Files.createDirectories(directory);
      temporary = Files.createTempFile(directory, "entry-", ".tmp");
      Files.write(temporary, bytes);
      try { Files.move(temporary, file(key), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
      catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file(key), StandardCopyOption.REPLACE_EXISTING); }
      temporary = null;
      // Scan on first write after restart, then amortize housekeeping across writes.
      if (writes++ % 64 == 0) prune();
    } catch (IOException | RuntimeException e) {
      if (!warned) {
        System.getLogger(AuthorityCache.class.getName()).log(System.Logger.Level.WARNING,
            "Persistent authority cache unavailable; using memory/provider access: " + directory, e);
        warned = true;
      }
    } finally {
      if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
    }
  }

  private Path file(String key) { return directory.resolve(CachedAuthority.digest(key) + ".json"); }

  private void prune() throws IOException {
    record FileInfo(Path path, long size, long modified) {}
    var files = new ArrayList<FileInfo>();
    long bytes = 0;
    try (var stream = Files.newDirectoryStream(directory, "*.json")) {
      for (var path : stream) {
        long size = Files.size(path);
        bytes += size;
        files.add(new FileInfo(path, size, Files.getLastModifiedTime(path).toMillis()));
      }
    }
    files.sort(Comparator.comparingLong(FileInfo::modified));
    int count = files.size();
    for (var file : files) {
      if (count <= MAX_DISK_ENTRIES && bytes <= MAX_DISK_BYTES) break;
      Files.deleteIfExists(file.path());
      count--;
      bytes -= file.size();
    }
  }
}
