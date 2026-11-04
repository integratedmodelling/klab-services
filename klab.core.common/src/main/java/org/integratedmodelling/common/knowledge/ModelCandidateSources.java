package org.integratedmodelling.common.knowledge;

import java.net.URL;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.lang.kim.*;
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
  public static final class Session implements AutoCloseable {
    private final Session previous = SESSION.get();
    private final Map<String, Snapshot> responses = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, RuntimeException> failedSuppliers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<ProjectionKey, String> projections = new java.util.concurrent.ConcurrentHashMap<>();
    private Session() { SESSION.set(this); }
    public void close() { if (previous == null) SESSION.remove(); else SESSION.set(previous); }
  }
  public static Session openSession() { return new Session(); }
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
    var snapshot = session.responses.computeIfAbsent(provider + "\n" + Utils.Json.asString(request),
        key -> {
          try {
            return new Snapshot(Utils.Json.asString(
                Objects.requireNonNull(fetch.get(), "Missing candidate response")), null);
          } catch (RuntimeException failure) {
            // An unavailable supplier remains unavailable for this attempt. Retrying for every
            // dependency adds latency and can change the candidate universe mid-resolution.
            session.failedSuppliers.putIfAbsent(provider, failure);
            return new Snapshot(null, failure);
          }
        });
    if (snapshot.failure() != null) throw snapshot.failure();
    return Utils.Json.parseObject(snapshot.encoded(), ResourceSet.class);
  }
  private record Snapshot(String encoded, RuntimeException failure) {}

  private record ProjectionKey(Object actor, Object caller, Object cycle,
      String provider, String sourceHash, String member) {}

  /** Cache only an unadmitted projection; every consumer receives its own detached model. */
  public static org.integratedmodelling.klab.api.knowledge.Model project(
      ResourceSet.Resource descriptor, org.integratedmodelling.klab.api.scope.Scope scope,
      java.util.function.Function<KimNamespace, org.integratedmodelling.klab.api.knowledge.Model> mapping) {
    var source = read(descriptor); // Verify source and member identity even on a cache hit.
    var session = SESSION.get();
    if (session == null || !(scope instanceof org.integratedmodelling.klab.api.scope.ContextScope context)
        || context.getResolutionCycleBinding() == null
        || context.getResolutionCycleBinding().semanticBinding() == null) return mapping.apply(source);
    Object caller = scope instanceof org.integratedmodelling.klab.api.scope.UserScope user
        && user.getUser() != null ? user.getUser() : scope;
    var key = new ProjectionKey(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class),
        caller, context.getResolutionCycleBinding(), descriptor.getServiceId(),
        Objects.toString(descriptor.getMetadata().get(HASH), null), descriptor.getResourceUrn());
    var encoded = session.projections.computeIfAbsent(key, ignored -> {
      var model = mapping.apply(source);
      if (!(model instanceof ModelImpl concrete))
        throw new IllegalArgumentException("Candidate projection must be a detached model bean");
      // Runtime scales carry local caches; only the equivalent portable geometry belongs here.
      if (concrete.getCoverage() != null)
        concrete.setCoverage(org.integratedmodelling.klab.api.geometry.Geometry.forTransport(concrete.getCoverage()));
      return Utils.Json.asString(concrete);
    });
    return Utils.Json.parseObject(encoded, org.integratedmodelling.klab.api.knowledge.Model.class);
  }

  /** Inputs must already be filtered by the supplier's authenticated project READ permissions. */
  public static ResourceSet capture(Collection<KimNamespace> namespaces, ModelCandidateRequest request,
      String serviceId, URL serviceUrl) {
    var result = new ResourceSet();
    if (serviceUrl != null) result.getServices().put(serviceId, serviceUrl);
    for (var original : namespaces.stream().sorted(Comparator.comparing(KimNamespace::getUrn)).toList()) {
      var source = Utils.Json.parseObject(Utils.Json.asString(original), KimNamespace.class);
      // Admission belongs to the receiving semantic actor. Keep parser diagnostics and inactive
      // source flags, but never import a provider's semantic publication receipt as a grant.
      source.getMetadata().remove(ResourcesService.NAMESPACE_ADMISSION_METADATA);
      source.getMetadata().remove(ResourcesService.VALIDATION_DEFERRED_METADATA);
      String encoded = Utils.Json.asString(source);
      String hash = SemanticValidationRequest.sourceHash(encoded);
      var candidates = source.getStatements().stream().filter(KimModel.class::isInstance)
          .map(KimModel.class::cast).filter(model -> !source.isInactive()
              && request.visible(source.getUrn(), source.getProjectName(), model.getScope(), source.isScenario()))
          .toList();
      if (candidates.isEmpty()) continue;
      var descriptor = descriptor(source, source.getUrn(), KnowledgeClass.NAMESPACE, serviceId);
      descriptor.getMetadata().put(SOURCE, encoded);
      descriptor.getMetadata().put(HASH, hash);
      result.getNamespaces().add(descriptor);
      for (var model : candidates) {
        var candidate = descriptor(source, model.getUrn(), KnowledgeClass.MODEL, serviceId);
        candidate.getMetadata().put(SOURCE, encoded);
        candidate.getMetadata().put(HASH, hash);
        result.getResults().add(candidate);
      }
    }
    result.setEmpty(result.getResults().isEmpty());
    return result;
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
