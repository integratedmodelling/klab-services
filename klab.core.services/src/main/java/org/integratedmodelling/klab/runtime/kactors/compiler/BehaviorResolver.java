package org.integratedmodelling.klab.runtime.kactors.compiler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;

/** Resources-backed semantic beans, checked against their source before every use. */
final class BehaviorResolver {

  // Scope isolation prevents beans from crossing user/workspace visibility boundaries. Weak keys
  // allow the cache to disappear with its scope; entries must not retain the scope itself.
  private static final Map<UserScope, Map<String, CachedBehavior>> caches = new WeakHashMap<>();

  private record CachedBehavior(
      String serviceId, String urn, Version version, long timestamp, KActorsBehavior behavior) {}

  private BehaviorResolver() {}

  static KActorsBehavior resolve(String urn, UserScope scope) {
    Map<String, CachedBehavior> cache;
    synchronized (caches) {
      cache = caches.computeIfAbsent(scope, ignored -> new HashMap<>());
    }
    // Serialize refreshes within a scope without holding the global cache lock during network I/O.
    synchronized (cache) {
      var resources = scope.getService(ResourcesService.class);
      if (resources == null) {
        cache.remove(urn);
        return null;
      }
      var resolved = resources.resolve(urn, KnowledgeClass.BEHAVIOR, scope);
      if (resolved == null || resolved.isEmpty()) {
        cache.remove(urn);
        return null;
      }
      // Dependencies are not the requested behavior. Use only the focus of the resolve result,
      // whose canonical URN may differ from a workspace/project-qualified request.
      var candidates =
          resolved.getResults().stream()
              .filter(resource -> resource.getKnowledgeClass() == KnowledgeClass.BEHAVIOR)
              .toList();
      var resource =
          candidates.stream()
              .filter(candidate -> urn.equals(candidate.getResourceUrn()))
              .findFirst()
              .orElse(candidates.size() == 1 ? candidates.getFirst() : null);
      if (resource == null || resource.getResourceUrn() == null) {
        cache.remove(urn);
        return null;
      }
      var cached = cache.get(urn);
      if (cached != null
          && Objects.equals(cached.serviceId(), resource.getServiceId())
          && Objects.equals(cached.urn(), resource.getResourceUrn())
          && Objects.equals(cached.version(), resource.getResourceVersion())
          && cached.timestamp() == resource.getTimestamp()) {
        return cached.behavior();
      }
      // Never leave an old bean available if retrieval of its replacement fails.
      cache.remove(urn);
      var owner =
          resource.getServiceId() == null
                  || Objects.equals(resources.serviceId(), resource.getServiceId())
              ? resources
              : scope
                  .findService(
                      ResourcesService.class,
                      service -> Objects.equals(service.serviceId(), resource.getServiceId()))
                  .orElse(resources);
      var behavior = owner.retrieve(resource.getResourceUrn(), KActorsBehavior.class, scope);
      if (behavior != null) {
        cache.put(
            urn,
            new CachedBehavior(
                resource.getServiceId(),
                resource.getResourceUrn(),
                resource.getResourceVersion(),
                resource.getTimestamp(),
                behavior));
      }
      return behavior;
    }
  }
}
