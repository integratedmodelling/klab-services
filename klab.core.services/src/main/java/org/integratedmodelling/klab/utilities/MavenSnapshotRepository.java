package org.integratedmodelling.klab.utilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import java.util.regex.Pattern;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.utilities.Utils.Maven.MavenCoordinates;
import org.integratedmodelling.klab.utilities.Utils.Maven.SnapshotInfo;

/** Per-process snapshot metadata cache shared by update checks and artifact downloads. */
final class MavenSnapshotRepository {
  private static final String SEARCH = "https://central.sonatype.com/solrsearch/select";
  private static final String REPOSITORY = "https://central.sonatype.com/repository/maven-snapshots";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern UPDATED = Pattern.compile("<lastUpdated>(\\d{14})</lastUpdated>");
  private static final Pattern TIMESTAMP = Pattern.compile("<timestamp>([\\d.]+)</timestamp>");
  private static final Pattern BUILD = Pattern.compile("<buildNumber>(\\d+)</buildNumber>");
  private final HttpClient client;
  private final Cache<SnapshotInfo> dates;
  private final Cache<RepositoryMetadata> metadata;

  MavenSnapshotRepository(HttpClient client, LongSupplier clock, Duration positiveTtl) {
    this.client = client;
    dates = new Cache<>(clock, positiveTtl, SnapshotInfo::isFound);
    metadata = new Cache<>(clock, positiveTtl, value -> value.info().isFound());
  }

  SnapshotInfo latest(MavenCoordinates coordinates) {
    if (!coordinates.getVersion().endsWith("-SNAPSHOT"))
      throw new KlabIllegalArgumentException("Version must be a SNAPSHOT version");
    return dates.get(coordinates.toString(), () -> {
      SnapshotInfo search = search(coordinates);
      return search != null ? search : repository(coordinates, false).info();
    });
  }

  /** The same repository response supplies the date and timestamped download version. */
  String downloadVersion(MavenCoordinates coordinates) {
    if (!latest(coordinates).isFound()) return null;
    RepositoryMetadata result = repository(coordinates, true);
    if (result.xml() == null) return null;
    var timestamp = TIMESTAMP.matcher(result.xml());
    var build = BUILD.matcher(result.xml());
    return timestamp.find() && build.find()
        ? coordinates.getVersion().replace("-SNAPSHOT", "-" + timestamp.group(1) + "-" + build.group(1))
        : coordinates.getVersion();
  }

  private SnapshotInfo search(MavenCoordinates coordinates) {
    String query = String.format("q=g:%s+AND+a:%s+AND+v:%s&core=gav&rows=1&wt=json",
        coordinates.getGroupId(), coordinates.getArtifactId(), coordinates.getVersion());
    try {
      var response = send(SEARCH + "?" + query, 60);
      // Search and repository are independent endpoints. A search 429 must still allow fallback.
      if (response.statusCode() != 200) {
        Logging.INSTANCE.warn("Maven Central search returned status " + response.statusCode());
        return null;
      }
      var docs = JSON.readTree(response.body()).path("response").path("docs");
      if (docs.isArray() && !docs.isEmpty()) {
        var doc = docs.get(0);
        return new SnapshotInfo(LocalDateTime.ofInstant(
            Instant.ofEpochMilli(doc.path("timestamp").asLong()), ZoneId.systemDefault()),
            doc.path("v").asText(), true);
      }
      return null;
    } catch (IOException | RuntimeException failure) {
      if (Thread.currentThread().isInterrupted()) throw new KlabIOException("Snapshot search interrupted", failure);
      Logging.INSTANCE.warn("Maven snapshot search unavailable; checking repository", failure);
      return null;
    }
  }

  private RepositoryMetadata repository(MavenCoordinates coordinates, boolean allowStale) {
    return metadata.get(coordinates.toString(), () -> {
      String path = String.format("%s/%s/%s/%s/maven-metadata.xml", REPOSITORY,
          coordinates.getGroupId().replace('.', '/'), coordinates.getArtifactId(), coordinates.getVersion());
      var response = send(path, 30);
      if (response.statusCode() == 404)
        return new RepositoryMetadata(new SnapshotInfo(null, null, false), null);
      if (response.statusCode() != 200)
        throw new KlabIOException("Failed to fetch snapshot metadata: HTTP " + response.statusCode());
      var updated = UPDATED.matcher(response.body());
      LocalDateTime date;
      try {
        date = updated.find() ? LocalDateTime.parse(updated.group(1),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) : null;
      } catch (RuntimeException malformed) {
        throw new KlabIOException("Invalid snapshot metadata date", malformed);
      }
      return new RepositoryMetadata(new SnapshotInfo(date, coordinates.getVersion(), date != null), response.body());
    }, allowStale);
  }

  private HttpResponse<String> send(String uri, int timeoutSeconds) {
    try {
      return client.send(HttpRequest.newBuilder(URI.create(uri))
          .timeout(Duration.ofSeconds(timeoutSeconds)).GET().build(), HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new KlabIOException("Snapshot metadata request was interrupted", interrupted);
    } catch (IOException failure) {
      throw new KlabIOException("Snapshot metadata request failed", failure);
    }
  }

  private record RepositoryMetadata(SnapshotInfo info, String xml) {}

  private record Entry<T>(T value, RuntimeException failure, long expiresAt) {
    boolean valid(long now) { return now - expiresAt < 0; }
    T result(boolean allowStale) {
      if (failure != null && (!allowStale || value == null)) throw failure;
      return value;
    }
  }

  private static final class Cache<T> {
    private final ConcurrentHashMap<String, Entry<T>> entries = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final long positiveNanos;
    private final Predicate<T> found;

    Cache(LongSupplier clock, Duration positiveTtl, Predicate<T> found) {
      this.clock = clock;
      this.positiveNanos = positiveTtl.toNanos();
      if (positiveNanos <= 0) throw new IllegalArgumentException("Snapshot cache TTL must be positive");
      this.found = found;
    }

    T get(String key, Supplier<T> fetch) {
      return get(key, fetch, true);
    }

    T get(String key, Supplier<T> fetch, boolean allowStale) {
      var cached = entries.get(key);
      if (cached != null && cached.valid(clock.getAsLong())) return cached.result(allowStale);
      return entries.compute(key, (ignored, previous) -> {
        if (previous != null && previous.valid(clock.getAsLong())) return previous;
        try {
          T value = fetch.get();
          long ttl = found.test(value) ? positiveNanos : Duration.ofMinutes(2).toNanos();
          return new Entry<>(value, null, clock.getAsLong() + ttl);
        } catch (RuntimeException failure) {
          // Cancellation belongs to the caller; never cache it or hide it behind stale metadata.
          if (Thread.currentThread().isInterrupted()) throw failure;
          Logging.INSTANCE.warn("Unable to refresh Maven snapshot " + key, failure);
          return previous != null && previous.value() != null
              ? new Entry<>(previous.value(), failure, clock.getAsLong() + Duration.ofMinutes(1).toNanos())
              : new Entry<T>(null, failure, clock.getAsLong() + Duration.ofMinutes(1).toNanos());
        }
      }).result(allowStale);
    }
  }
}
