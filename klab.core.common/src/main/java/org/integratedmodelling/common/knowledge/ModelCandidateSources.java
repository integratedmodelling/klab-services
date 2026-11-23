package org.integratedmodelling.common.knowledge;

import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.common.utils.Utils;

/** Immutable provider source snapshots. These carry no publication or execution authority. */
public final class ModelCandidateSources {
  public static final String SOURCE = "klab.model.candidate.source.v1";
  public static final String HASH = "klab.model.candidate.sourceHash.v1";
  public static final String INCOMPLETE = "MODEL_CANDIDATE_SUPPLIER_UNAVAILABLE";
  private ModelCandidateSources() {}

  private static final ThreadLocal<Session> SESSION = new ThreadLocal<>();
  private record SupplierQuery(String provider, ModelCandidateRequest request) {}

  /** Resolver-owned cache of raw supplier replies, partitioned by the authenticated cycle. */
  public static final class SharedCatalogs {
    private record Key(Object cycleOwner, SupplierQuery query) {}

    private final com.github.benmanes.caffeine.cache.Cache<Key, Snapshot> replies =
        Caffeine.newBuilder()
            .maximumWeight(64 * 1024 * 1024)
            .weigher((Key key, Snapshot snapshot) ->
                snapshot.encoded() == null ? 1024 : 1024 + 2 * snapshot.encoded().length())
            .expireAfter(new com.github.benmanes.caffeine.cache.Expiry<Key, Snapshot>() {
              public long expireAfterCreate(Key key, Snapshot reply, long time) {
                return reply.failure() == null
                    ? Duration.ofMinutes(20).toNanos() : Duration.ofSeconds(2).toNanos();
              }

              public long expireAfterUpdate(Key key, Snapshot reply, long time, long duration) {
                return expireAfterCreate(key, reply, time);
              }

              public long expireAfterRead(Key key, Snapshot reply, long time, long duration) {
                return reply.failure() == null ? expireAfterCreate(key, reply, time) : duration;
              }
            })
            .build();
    private final com.github.benmanes.caffeine.cache.Cache<SharedProjectionKey, SharedProjection>
        projections = Caffeine.newBuilder()
            .maximumWeight(64 * 1024 * 1024)
            .weigher((SharedProjectionKey key, SharedProjection value) ->
                1024 + 2 * key.source().length()
                    + (value.encoded() == null ? 0 : 2 * value.encoded().length()))
            .expireAfterAccess(Duration.ofMinutes(20))
            .build();

    public Session openSession(Object cycleOwner) {
      return new Session(this, Objects.requireNonNull(cycleOwner));
    }
  }

  public static final class Session implements AutoCloseable {
    private final Session previous = SESSION.get();
    private final SharedCatalogs shared;
    private final Object cycleOwner;
    private final Map<SupplierQuery, Snapshot> responses = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, RuntimeException> failedSuppliers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<ProjectionKey, String> projections = new java.util.concurrent.ConcurrentHashMap<>();
    private Session(SharedCatalogs shared, Object cycleOwner) {
      this.shared = shared;
      this.cycleOwner = cycleOwner;
      SESSION.set(this);
    }
    public void close() { if (previous == null) SESSION.remove(); else SESSION.set(previous); }
  }
  public static Session openSession() { return new Session(null, null); }
  public static Session currentSession() { return SESSION.get(); }
  public static <T> T inSession(Session session, java.util.function.Supplier<T> operation) {
    var previous = SESSION.get();
    if (session == null) SESSION.remove(); else SESSION.set(session);
    try { return operation.get(); }
    finally { if (previous == null) SESSION.remove(); else SESSION.set(previous); }
  }
  public static ResourceSet freeze(String provider, ModelCandidateRequest request,
      java.util.function.Supplier<ResourceSet> fetch) {
    var session = SESSION.get();
    if (session == null) return fetch.get();
    var unavailable = session.failedSuppliers.get(provider);
    if (unavailable != null) throw unavailable;
    var snapshot = session.responses.computeIfAbsent(new SupplierQuery(provider, request), query ->
        session.shared == null ? captureReply(fetch)
            : session.shared.replies.get(
                new SharedCatalogs.Key(session.cycleOwner, query), ignored -> captureReply(fetch)));
    if (snapshot.failure() != null) {
      // Preserve the attempt's failure even when a later sibling retries the supplier.
      session.failedSuppliers.putIfAbsent(provider, snapshot.failure());
      throw snapshot.failure();
    }
    return Utils.Json.parseObject(snapshot.encoded(), ResourceSet.class);
  }

  private static Snapshot captureReply(java.util.function.Supplier<ResourceSet> fetch) {
    try {
      return new Snapshot(Utils.Json.asString(
          Objects.requireNonNull(fetch.get(), "Missing candidate response")), null);
    } catch (RuntimeException failure) {
      return new Snapshot(null, failure);
    }
  }
  private record Snapshot(String encoded, RuntimeException failure) {}

  private record ProjectionKey(Object actor, Object caller, Object cycle,
      String provider, String sourceHash, String member) {}
  private record SharedProjectionKey(Object owner, Object cycle, String provider, String project,
      long timestamp, String member, String hash, String source) {}
  private record SharedProjection(String encoded) {}

  /** An authority-free, unadmitted projection can be reused within the authenticated cycle. */
  public record CandidateProjection(Model model, boolean reusable) {}

  /** Cache only an unadmitted projection; every consumer receives its own detached model. */
  public static Model project(
      ResourceSet.Resource descriptor, Scope scope, Function<KimNamespace, Model> mapping) {
    return project(descriptor, scope,
        (source, prove) -> new CandidateProjection(mapping.apply(source), false), false);
  }

  public static Model project(
      ResourceSet.Resource descriptor, Scope scope,
      BiFunction<KimNamespace, Boolean, CandidateProjection> mapping) {
    return project(descriptor, scope, mapping, true);
  }

  private static Model project(
      ResourceSet.Resource descriptor, Scope scope,
      BiFunction<KimNamespace, Boolean, CandidateProjection> mapping, boolean share) {
    var session = SESSION.get();
    if (session == null || !(scope instanceof ContextScope context)
        || context.getResolutionCycleBinding() == null
        || context.getResolutionCycleBinding().semanticBinding() == null)
      return mapping.apply(read(descriptor), false).model();
    var hash = Objects.toString(descriptor.getMetadata().get(HASH), null);
    var sharedKey = share && session.shared != null && hash != null
        && descriptor.getMetadata().get(SOURCE) instanceof String text
        ? new SharedProjectionKey(session.cycleOwner, context.getResolutionCycleBinding(),
            descriptor.getServiceId(), descriptor.getProjectUrn(), descriptor.getTimestamp(),
            descriptor.getResourceUrn(), hash, text)
        : null;
    // A hit matches the exact source bytes and descriptor identity validated on insertion.
    var shared = sharedKey == null ? null : session.shared.projections.getIfPresent(sharedKey);
    if (shared != null && shared.encoded() != null)
      return Utils.Json.parseObject(shared.encoded(), Model.class);
    var source = read(descriptor);
    Object caller = scope instanceof org.integratedmodelling.klab.api.scope.UserScope user
        && user.getUser() != null ? user.getUser() : scope;
    var key = new ProjectionKey(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class),
        caller, context.getResolutionCycleBinding(), descriptor.getServiceId(),
        hash, descriptor.getResourceUrn());
    var encoded = session.projections.computeIfAbsent(key, ignored -> {
      var projection = mapping.apply(source, sharedKey != null && shared == null);
      if (!(projection.model() instanceof ModelImpl concrete))
        throw new IllegalArgumentException("Candidate projection must be a detached model bean");
      // Runtime scales carry local caches; only the equivalent portable geometry belongs here.
      if (concrete.getCoverage() != null)
        concrete.setCoverage(org.integratedmodelling.klab.api.geometry.Geometry.forTransport(concrete.getCoverage()));
      var json = Utils.Json.asString(concrete);
      if (sharedKey != null && shared == null)
        session.shared.projections.asMap().putIfAbsent(
            sharedKey, new SharedProjection(projection.reusable() ? json : null));
      return json;
    });
    return Utils.Json.parseObject(encoded, Model.class);
  }

  /** Inputs must already be filtered by the supplier's authenticated project READ permissions. */
  public static ResourceSet capture(Collection<KimNamespace> namespaces, ModelCandidateRequest request,
      String serviceId, URL serviceUrl) {
    var result = new ResourceSet();
    if (serviceUrl != null) result.getServices().put(serviceId, serviceUrl);
    for (var original : namespaces.stream().sorted(Comparator.comparing(KimNamespace::getUrn)).toList()) {
      // Skip namespaces that cannot contribute before copying and encoding their entire AST.
      if (original.isInactive() || original.getStatements().stream().noneMatch(
          statement -> statement instanceof KimModel model && visible(original, model, request))) continue;
      var source = Utils.Json.parseObject(Utils.Json.asString(original), KimNamespace.class);
      // Admission belongs to the receiving semantic actor. Keep parser diagnostics and inactive
      // source flags, but never import a provider's semantic publication receipt as a grant.
      source.getMetadata().remove(ResourcesService.NAMESPACE_ADMISSION_METADATA);
      source.getMetadata().remove(ResourcesService.VALIDATION_DEFERRED_METADATA);
      String encoded = Utils.Json.asString(source);
      String hash = SemanticValidationRequest.sourceHash(encoded);
      var candidates = source.getStatements().stream().filter(KimModel.class::isInstance)
          .map(KimModel.class::cast).filter(model -> !source.isInactive() && visible(source, model, request))
          .toList();
      if (candidates.isEmpty()) continue;
      var descriptor = descriptor(source, source.getUrn(), KnowledgeClass.NAMESPACE, serviceId);
      descriptor.getMetadata().put(SOURCE, encoded);
      descriptor.getMetadata().put(HASH, hash);
      result.getNamespaces().add(descriptor);
      for (var model : candidates) {
        var candidate = descriptor(source, model.getUrn(), KnowledgeClass.MODEL, serviceId);
        if (request.version() == 1) candidate.getMetadata().put(SOURCE, encoded);
        candidate.getMetadata().put(HASH, hash);
        result.getResults().add(candidate);
      }
    }
    result.setEmpty(result.getResults().isEmpty());
    return result;
  }

  private record SourceReference(String provider, Object hash) {}

  /** Protocol 2 carries each namespace source once. Restore the existing in-process descriptors. */
  public static ResourceSet expand(ResourceSet response) {
    var sources = new HashMap<SourceReference, String>();
    for (var namespace : response.getNamespaces()) {
      if (namespace.getMetadata().get(SOURCE) instanceof String text)
        sources.put(new SourceReference(namespace.getServiceId(), namespace.getMetadata().get(HASH)), text);
    }
    for (var candidate : response.getResults()) {
      if (candidate.getKnowledgeClass() != KnowledgeClass.MODEL)
        throw new IllegalArgumentException("Invalid model candidate descriptor");
      if (candidate.getMetadata().get(SOURCE) instanceof String) continue;
      var key = new SourceReference(candidate.getServiceId(), candidate.getMetadata().get(HASH));
      var source = sources.get(key);
      if (key.hash() == null || source == null)
        throw new IllegalArgumentException("Model candidate has no matching namespace source");
      candidate.getMetadata().put(SOURCE, source);
    }
    return response;
  }

  private static boolean visible(KimNamespace source, KimModel model, ModelCandidateRequest request) {
    return !model.isLearningModel()
        && request.visible(source.getUrn(), source.getProjectName(), model.getScope(), source.isScenario());
  }

  private static ResourceSet.Resource descriptor(KimNamespace source, String urn,
      KnowledgeClass type, String serviceId) {
    return new ResourceSet.Resource(serviceId, urn, source.getProjectName(), source.getVersion(),
        type, source.getLastUpdateTimestamp(), false);
  }

  public static KimNamespace read(org.integratedmodelling.klab.api.knowledge.Model model) {
    Object value = model.getMetadata() == null ? null : model.getMetadata().get(SOURCE);
    if (!(value instanceof String text)) return null;
    if (!Objects.equals(SemanticValidationRequest.sourceHash(text), model.getMetadata().get(HASH)))
      throw new IllegalArgumentException("Candidate model source changed after capture");
    var source = Utils.Json.parseObject(text, KimNamespace.class);
    if (!Objects.equals(source.getUrn(), model.getNamespace())
        || !Objects.equals(source.getProjectName(), model.getProjectName())
        || source.getStatements().stream().noneMatch(s -> s instanceof KimModel && Objects.equals(s.getUrn(), model.getUrn())))
      throw new IllegalArgumentException("Candidate model differs from its captured source");
    return source;
  }

  public static KimNamespace read(ResourceSet.Resource descriptor) {
    Object encoded = descriptor.getMetadata().get(SOURCE);
    if (!(encoded instanceof String text)
        || !Objects.equals(SemanticValidationRequest.sourceHash(text), descriptor.getMetadata().get(HASH)))
      throw new IllegalArgumentException("Invalid candidate source snapshot for " + descriptor.getResourceUrn());
    var source = Utils.Json.parseObject(text, KimNamespace.class);
    if (!Objects.equals(source.getProjectName(), descriptor.getProjectUrn())
        || source.getLastUpdateTimestamp() != descriptor.getTimestamp()
        || !(Objects.equals(source.getUrn(), descriptor.getResourceUrn())
            || source.getStatements().stream().anyMatch(s -> s instanceof KimModel
                && Objects.equals(s.getUrn(), descriptor.getResourceUrn()))))
      throw new IllegalArgumentException("Candidate identity differs from its source snapshot");
    return source;
  }
}
