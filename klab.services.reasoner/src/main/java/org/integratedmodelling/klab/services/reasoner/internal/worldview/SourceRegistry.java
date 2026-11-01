package org.integratedmodelling.klab.services.reasoner.internal.worldview;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

/**
 * Immutable ingestion bookkeeping, not a scheduler, validator or publisher. All transitions return
 * a new value; callers must serialize/CAS installation of these values. No mutable API beans or
 * credentials are retained. Publication acknowledgement records an externally completed publication.
 */
public record SourceRegistry(long generation, Map<Key, Subscription> sources,
                             long publication, Map<Key, Revision> published) {
  public SourceRegistry {
    if (generation < 0 || publication < 0) throw new IllegalArgumentException("Negative generation");
    sources = Map.copyOf(sources);
    published = Map.copyOf(published);
    for (var entry : sources.entrySet()) {
      if (!entry.getKey().equals(entry.getValue().key()) || entry.getValue().generation() > generation)
        throw new IllegalArgumentException("Invalid source registry entry");
    }
  }

  public static SourceRegistry empty() { return new SourceRegistry(0, Map.of(), 0, Map.of()); }

  /** Existing Resources service identity and project URN; endpoint is deliberately not part of key. */
  public record Key(String serviceId, String projectUrn) {
    public Key { required(serviceId); required(projectUrn); }
    public static Key from(ResourceSet.Resource descriptor) {
      return new Key(descriptor.getServiceId(), descriptor.getProjectUrn());
    }
  }

  /** Provider token is opaque and optional; digest and encoding schema are mandatory. */
  public record Revision(String providerToken, String digest, String digestSchema) {
    public Revision { required(digest); required(digestSchema); }
  }

  public record Capabilities(boolean coherentCapture, boolean notifications,
                             boolean sequencedHints) {}

  /** Deterministic, length-delimited encoding of a complete capture, including manifest entries.
   * Document deletion is represented by absence from this complete map, never by unsubscription.
   * The adapter chooses and versions entry names; ordering of the supplied map has no meaning.
   */
  public static String encodeContent(Map<String, String> entries) {
    var encoded = new StringBuilder();
    new TreeMap<>(entries).forEach((key, value) -> {
      Objects.requireNonNull(key); Objects.requireNonNull(value);
      encoded.append(key.length()).append(':').append(key)
          .append(value.length()).append(':').append(value);
    });
    return encoded.toString();
  }

  public static Revision contentRevision(String envelope) {
    Objects.requireNonNull(envelope);
    try {
      return new Revision(null, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(envelope.getBytes(StandardCharsets.UTF_8))), "project-envelope-v1-sha256");
    } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }

  /**
   * Canonically encoded complete project envelope (manifest, source membership/content, strategies,
   * authorities and metadata). Adapter must verify its digest. Immutable strings avoid retaining
   * mutable Project/Worldview beans. Dependencies are exact captured requirements, not precedence.
   */
  public record Contribution(Key key, Revision revision, String envelope,
                             Map<Key, Revision> dependencies) {
    public Contribution {
      Objects.requireNonNull(key); Objects.requireNonNull(revision); Objects.requireNonNull(envelope);
      if (revision.digestSchema().equals("project-envelope-v1-sha256")
          && !revision.digest().equals(contentRevision(envelope).digest()))
        throw new IllegalArgumentException("Capture digest does not match payload");
      dependencies = Map.copyOf(dependencies);
    }
  }

  public enum Status {
    UNRESOLVED, DIRTY, CAPTURED, CURRENT, RESYNC_REQUIRED, CAPTURE_UNAVAILABLE,
    MISSING, PROVIDER_DELETED, AUTH_EXPIRED, REJECTED, REVISION_MISMATCH, REMOVED
  }

  public record Subscription(Key key, long generation, URI endpoint, Capabilities capabilities,
                             Status status, String epoch, Long sequence,
                             Contribution captured, String diagnostic) {
    public Subscription {
      Objects.requireNonNull(key); Objects.requireNonNull(endpoint);
      Objects.requireNonNull(capabilities); Objects.requireNonNull(status);
      if (generation < 1 || (sequence != null && sequence < 0))
        throw new IllegalArgumentException("Invalid generation/sequence");
      if (sequence != null) required(epoch);
      if (captured != null && !key.equals(captured.key()))
        throw new IllegalArgumentException("Capture source mismatch");
      if ((status == Status.CAPTURED || status == Status.CURRENT) && captured == null)
        throw new IllegalArgumentException("Resolved source requires capture");
    }
  }

  public record Hint(Key key, long subscriptionGeneration, String epoch, Long sequence,
                     CRUDOperation operation) {
    public Hint(Key key, long subscriptionGeneration, String epoch, Long sequence) {
      this(key, subscriptionGeneration, epoch, sequence, CRUDOperation.UPDATE);
    }
    public Hint {
      Objects.requireNonNull(key);
      Objects.requireNonNull(operation);
      if (sequence != null) { required(epoch); if (sequence < 0) throw new IllegalArgumentException(); }
    }
  }

  public sealed interface Capture permits Captured, Unavailable, Mismatch {}
  /** Complete coherent payload and revision are returned together. */
  public record Captured(Contribution contribution) implements Capture {
    public Captured { Objects.requireNonNull(contribution); }
  }
  public record Mismatch(Revision actual) implements Capture {
    public Mismatch { Objects.requireNonNull(actual); }
  }
  public record Unavailable(Status status, String diagnostic) implements Capture {
    public Unavailable {
      if (!Set.of(Status.CAPTURE_UNAVAILABLE, Status.MISSING, Status.PROVIDER_DELETED, Status.AUTH_EXPIRED,
          Status.UNRESOLVED, Status.REJECTED).contains(status)) throw new IllegalArgumentException();
      required(diagnostic);
    }
  }

  /** No implementation is attached to mutable ResourcesService.retrieve in this stage.
   * expected is a conditional current revision, not a historical retrieval request. A provider
   * returns Mismatch if it has changed; unavailable coherent capture returns Unavailable.
   * Implementations must authorize capture and must not activate providers or other persistent effects.
   */
  public interface Adapter {
    Capabilities capabilities(Key key, UserScope scope);
    Capture capture(Key key, Revision expected, UserScope scope);
  }

  /** Registration/replacement is explicit. Re-registering invalidates all previous delivery tokens. */
  public SourceRegistry register(Key key, URI endpoint, Capabilities capabilities) {
    if (sources.containsKey(key) && sources.get(key).status() != Status.REMOVED)
      throw new IllegalArgumentException("Source already registered; use relocate or remove first");
    return put(new Subscription(key, generation + 1, endpoint, capabilities, Status.UNRESOLVED,
        null, null, null, null));
  }

  public SourceRegistry relocate(Key key, URI endpoint) {
    var s = active(key);
    return put(new Subscription(key, generation + 1, endpoint, s.capabilities(), Status.DIRTY,
        null, null, s.captured(), "Endpoint changed; reconcile subscription"));
  }

  public SourceRegistry remove(Key key) {
    var s = active(key);
    return put(new Subscription(key, generation + 1, s.endpoint(), s.capabilities(), Status.REMOVED,
        null, null, s.captured(), "Explicit removal pending publication"));
  }

  public SourceRegistry hint(Hint hint) {
    var s = sources.get(hint.key());
    if (s == null || s.status() == Status.REMOVED || s.generation() != hint.subscriptionGeneration())
      return this;
    boolean ordered = s.capabilities().sequencedHints();
    if (ordered && hint.sequence() != null && Objects.equals(s.epoch(), hint.epoch())
        && s.sequence() != null && hint.sequence() <= s.sequence()) return this;
    boolean gap = ordered && (hint.sequence() == null || s.sequence() == null
        || !Objects.equals(s.epoch(), hint.epoch()) || hint.sequence() != s.sequence() + 1);
    Status status = gap || s.status() == Status.RESYNC_REQUIRED ? Status.RESYNC_REQUIRED : Status.DIRTY;
    return put(new Subscription(s.key(), s.generation(), s.endpoint(), s.capabilities(), status,
        ordered ? hint.epoch() : null, ordered ? hint.sequence() : null,
        s.captured(), gap ? "Hint sequence requires complete reconciliation" : s.diagnostic()));
  }

  /** Captures are complete reconciliations; stale asynchronous responses require both guards. */
  public SourceRegistry capture(Key key, long expectedRegistryGeneration,
                                long subscriptionGeneration, Capture result) {
    if (generation != expectedRegistryGeneration) throw new ConcurrentModificationException("Registry changed");
    var s = active(key);
    if (s.generation() != subscriptionGeneration) throw new ConcurrentModificationException("Subscription changed");
    Objects.requireNonNull(result);
    if (result instanceof Captured c) {
      if (!s.capabilities().coherentCapture())
        result = new Unavailable(Status.CAPTURE_UNAVAILABLE, "Provider does not guarantee coherent capture");
      else {
        if (!key.equals(c.contribution().key())) throw new IllegalArgumentException("Capture source mismatch");
        if (s.captured() != null && s.captured().revision().equals(c.contribution().revision())
            && !s.captured().equals(c.contribution())) throw new IllegalArgumentException("Revision content changed");
        Status status = c.contribution().revision().equals(published.get(key)) ? Status.CURRENT : Status.CAPTURED;
        var next = new Subscription(key, s.generation(), s.endpoint(), s.capabilities(), status,
            s.epoch(), s.sequence(), c.contribution(), null);
        return next.equals(s) ? this : put(next);
      }
    }
    if (result instanceof Mismatch mismatch) {
      return put(new Subscription(key, s.generation(), s.endpoint(), s.capabilities(), Status.REVISION_MISMATCH,
          s.epoch(), s.sequence(), s.captured(), "Conditional revision changed: " + mismatch.actual()));
    }
    var failure = (Unavailable) result;
    return put(new Subscription(key, s.generation(), s.endpoint(), s.capabilities(), failure.status(),
        s.epoch(), s.sequence(), s.captured(), failure.diagnostic()));
  }

  /** Complete desired vector only; never fills a missing project using provider precedence. */
  public Map<Key, Revision> capturedVector() {
    Map<Key, Revision> vector = new HashMap<>();
    for (var s : sources.values()) {
      if (s.status() == Status.REMOVED) continue;
      if (s.status() != Status.CAPTURED && s.status() != Status.CURRENT)
        throw new IllegalStateException("Source is not resolved: " + s.key() + " " + s.status());
      vector.put(s.key(), s.captured().revision());
    }
    for (var s : sources.values()) {
      if (s.status() == Status.REMOVED) continue;
      s.captured().dependencies().forEach((key, revision) -> {
        if (!revision.equals(vector.get(key))) throw new IllegalStateException("Unsatisfied dependency: " + key);
      });
    }
    return Map.copyOf(vector);
  }

  /**
   * Bookkeeping ONLY, after external validation and publication. Does not implement durable CAS,
   * graph validation, leases, or publication. Both expected counters and the entire vector must match.
   * Caller must freshly authorize publication and separately gate every read admission; retaining
   * these values or holding a future lifetime lease never grants authorization for stale reads.
   */
  public SourceRegistry acknowledgePublication(long expectedGeneration, long expectedPublication,
                                                Map<Key, Revision> vector) {
    if (generation != expectedGeneration || publication != expectedPublication)
      throw new ConcurrentModificationException("Superseded publication");
    if (!capturedVector().equals(vector)) throw new IllegalArgumentException("Incomplete or stale vector");
    if (published.equals(vector)) return this;
    Map<Key, Subscription> next = new HashMap<>(sources);
    sources.forEach((key, s) -> {
      if (s.status() != Status.REMOVED) next.put(key, new Subscription(key, s.generation(),
          s.endpoint(), s.capabilities(), Status.CURRENT, s.epoch(), s.sequence(), s.captured(), null));
    });
    return new SourceRegistry(generation + 1, next, publication + 1, vector);
  }

  private Subscription active(Key key) {
    var s = sources.get(key);
    if (s == null || s.status() == Status.REMOVED) throw new IllegalArgumentException("Source not active: " + key);
    return s;
  }
  private SourceRegistry put(Subscription subscription) {
    var next = new HashMap<>(sources); next.put(subscription.key(), subscription);
    return new SourceRegistry(generation + 1, next, publication, published);
  }
  private static void required(String text) {
    if (text == null || text.isBlank()) throw new IllegalArgumentException("Missing identifier/content");
  }
}
