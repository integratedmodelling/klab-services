package org.integratedmodelling.klab.services.reasoner.internal;

import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;

/** Production publication owner: one complete actor per accepted generation, one lease per call. */
public final class AcceptedSemanticRuntime implements AutoCloseable {
  private final SemanticEnvironmentFactory factory;
  private final CandidatePublicationCoordinator<Worldview, SemanticEnvironment> coordinator;
  private final Object lifecycle = new Object();
  private volatile boolean closed;
  private void requireOpen() { if (closed) throw new IllegalStateException("Semantic runtime is closed"); }
  private volatile Map<String, String> submittedSources = Map.of();
  private record SearchLease(Object owner, int actorId,
      CandidatePublicationCoordinator<Worldview, SemanticEnvironment>.Lease lease, Scope scope) {}
  private final java.util.concurrent.atomic.AtomicInteger searchIds = new java.util.concurrent.atomic.AtomicInteger();
  private final com.github.benmanes.caffeine.cache.Cache<Integer, SearchLease> searches =
      com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(1000)
          .expireAfterAccess(Duration.ofMinutes(10)).executor(Runnable::run)
          .removalListener((Integer key, SearchLease session, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
            if (session != null) session.lease().close();
          }).build();

  public AcceptedSemanticRuntime(SemanticEnvironmentFactory factory) {
    this.factory = Objects.requireNonNull(factory);
    coordinator = new CandidatePublicationCoordinator<>(factory, input -> true);
  }
  public long submit(Worldview snapshot) {
    var input = factory.capture(snapshot);
    synchronized (lifecycle) { requireOpen(); submittedSources = input.sourceHashes(); return coordinator.submit(input); }
  }
  public Map<String, String> submittedSources() { return submittedSources; }
  public record Metadata(long generation, SemanticCapabilitiesSnapshot publication) {}
  public Metadata metadata() {
    try (var lease = lease()) { return new Metadata(lease.generation(), lease.state().actor().semanticCapabilitiesSnapshot()); }
    catch (IllegalStateException unavailable) { return new Metadata(0, SemanticCapabilitiesSnapshot.initial()); }
  }
  public void invalidate() { coordinator.invalidate(); }
  public void rejectPreflight(List<org.integratedmodelling.klab.api.services.runtime.Notification> failures) {
    coordinator.rejectPreflight(failures.stream().map(org.integratedmodelling.klab.api.services.runtime.Notification::getMessage).toList());
  }
  public CandidatePublicationCoordinator.Status status() { return coordinator.status(); }
  public boolean awaitIdle(Duration timeout) throws InterruptedException { return coordinator.awaitIdle(timeout); }
  public CandidatePublicationCoordinator<Worldview, SemanticEnvironment>.Lease lease() {
    synchronized (lifecycle) {
      requireOpen();
      return coordinator.accepted().orElseThrow(() -> new IllegalStateException("Accepted semantic knowledge is unavailable"));
    }
  }
  public SemanticCapabilitiesSnapshot capabilities() {
    try (var lease = lease()) {
      return lease.state().actor().semanticCapabilitiesSnapshot();
    } catch (IllegalStateException unavailable) { return SemanticCapabilitiesSnapshot.initial(); }
  }
  public org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse validate(
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest request,
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse response) {
    try (var lease = lease()) {
      response.setKnowledgeRevision(lease.generation());
      if (request.getKnowledgeRevision() >= 0 && request.getKnowledgeRevision() != lease.generation()) {
        response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.STALE_KNOWLEDGE);
        response.setReason("The accepted semantic generation changed"); return response;
      }
      if (request.document() instanceof org.integratedmodelling.klab.api.lang.kim.KimOntology
          && !Objects.equals(response.getSourceHash(), lease.sourceHashes().get(SemanticEnvironmentFactory.sourceKey(request.document())))) {
        response.setReason("Saved source logical validation is pending or rejected; the prior accepted snapshot remains readable");
        return response;
      }
      synchronized (lease.state()) {
        response.getNotifications().addAll(new DocumentSemanticValidator(lease.state().actor()).validate(request.document()));
        response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.COMPLETE);
        return response;
      }
    } catch (IllegalStateException unavailable) {
      response.setReason("Accepted semantic knowledge is unavailable while whole-worldview validation is pending or rejected");
      return response;
    }
  }

  public <T> T invoke(String name, Class<?>[] signature, Object[] supplied) {
    try (var lease = lease()) {
      var environment = lease.state();
      synchronized (environment) {
        var actor = environment.actor();
        var arguments = new Object[supplied.length];
        for (int i = 0; i < arguments.length; i++) arguments[i] = bind(supplied[i], actor);
        var method = ReasonerService.class.getMethod(name, signature);
        var result = method.invoke(actor, arguments);
        // Only match exposes an output binding map; other maps remain caller-owned inputs.
        for (int i = 0; i < arguments.length; i++)
          if (name.equals("match") && supplied[i] instanceof Map original && arguments[i] instanceof Map mapped
              && !original.equals(mapped)) {
            original.clear(); original.putAll((Map)copy(mapped));
          }
        return (T)copy(result);
      }
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
      if (failure.getCause() instanceof Error error) throw error;
      throw new IllegalStateException(failure.getCause());
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Semantic routing signature mismatch: " + name, failure);
    }
  }
  public org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse search(
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest request, Scope originalScope) {
    requireOpen();
    if (originalScope == null) throw new SecurityException("Semantic search requires an authorized scope");
    Object owner = originalScope instanceof org.integratedmodelling.klab.api.scope.UserScope user
        ? user.getUser().getUsername() : originalScope;
    var existing = request.getSearchId() == 0 ? null : searches.getIfPresent(request.getSearchId());
    if (existing != null && !Objects.equals(owner, existing.owner())) throw new SecurityException("Semantic search belongs to another user");
    if (request.getSearchId() != 0 && existing == null) {
      var expired = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse(0, request.getRequestId());
      expired.getErrors().add("This search has expired. Start a new expression."); return expired;
    }
    var persistent = existing == null ? lease() : existing.lease();
    boolean retained = existing != null;
    try (var operation = persistent.retain()) {
      synchronized (operation.state()) {
        var actor = operation.state().actor();
        var scoped = existing == null ? scope(originalScope, actor) : existing.scope();
        var ownedRequest = JacksonConfiguration.newObjectMapper().convertValue(request,
            org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest.class);
        ownedRequest.setSearchId(existing == null ? 0 : existing.actorId());
        var response = (org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse)
            copy(actor.semanticSearch(ownedRequest, scoped));
        if (request.isCancelSearch() || response.getSearchId() == 0) {
          if (existing != null) searches.invalidate(request.getSearchId());
          response.setSearchId(0); return response;
        }
        synchronized (lifecycle) {
          if (closed) { response.setSearchId(0); return response; }
          int externalId = request.getSearchId();
          if (existing == null) {
            var session = new SearchLease(owner, response.getSearchId(), persistent, scoped);
            do { externalId = searchIds.updateAndGet(previous -> previous == Integer.MAX_VALUE ? 1 : previous + 1); }
            while (searches.asMap().putIfAbsent(externalId, session) != null);
            retained = true;
          }
          response.setSearchId(externalId); return response;
        }
      }
    } finally { if (!retained) persistent.close(); }
  }

  public Scope scope(Scope original, ReasonerService actor) {
    var interfaces = new LinkedHashSet<Class<?>>();
    for (Class<?> type = original.getClass(); type != null; type = type.getSuperclass())
      for (var implemented : type.getInterfaces()) if (Scope.class.isAssignableFrom(implemented)) interfaces.add(implemented);
    interfaces.add(Scope.class);
    return (Scope)Proxy.newProxyInstance(Scope.class.getClassLoader(), interfaces.toArray(Class<?>[]::new),
        (proxy, method, arguments) -> {
          if (method.getName().equals("getService") && arguments != null && arguments.length >= 1) {
            if (arguments[0] == Reasoner.class || arguments[0] == org.integratedmodelling.klab.api.services.KlabService.Type.REASONER) return actor;
            if (arguments[0] == ResourcesService.class || arguments[0] == org.integratedmodelling.klab.api.services.KlabService.Type.RESOURCES)
              return actor.serviceScope().getService(ResourcesService.class);
          }
          if (method.getName().equals("getServices") && arguments != null && arguments.length >= 1) {
            if (arguments[0] == Reasoner.class) return List.of(actor);
            if (arguments[0] == ResourcesService.class) return List.of(actor.serviceScope().getService(ResourcesService.class));
          }
          if (method.getName().equals("findService") && arguments != null && arguments.length >= 2
              && (arguments[0] == Reasoner.class || arguments[0] == ResourcesService.class)) {
            var service = arguments[0] == Reasoner.class ? actor : actor.serviceScope().getService(ResourcesService.class);
            return ((java.util.function.Predicate)arguments[1]).test(service) ? Optional.of(service) : Optional.empty();
          }
          try {
            var result = method.invoke(original, arguments);
            if (result instanceof Scope nested) return nested == original ? proxy : scope(nested, actor);
            if (result instanceof Reasoner) return actor;
            if (result instanceof ResourcesService) return actor.serviceScope().getService(ResourcesService.class);
            return bind(result, actor);
          }
          catch (InvocationTargetException failure) { throw failure.getCause(); }
        });
  }

  private Object bind(Object value, ReasonerService actor) {
    if (value == null) return null;
    if (value instanceof Reasoner) return actor;
    if (value instanceof ResourcesService) return actor.serviceScope().getService(ResourcesService.class);
    if (value instanceof Scope scoped) return scope(scoped, actor);
    if (value instanceof org.integratedmodelling.klab.api.lang.kim.KimConcept)
      return JacksonConfiguration.newObjectMapper().convertValue(value, org.integratedmodelling.klab.api.lang.kim.KimConcept.class);
    if (value instanceof org.integratedmodelling.klab.api.lang.kim.KimObservable)
      return JacksonConfiguration.newObjectMapper().convertValue(value, org.integratedmodelling.klab.api.lang.kim.KimObservable.class);
    if (value instanceof org.integratedmodelling.klab.api.knowledge.observation.Observation observation) {
      return Proxy.newProxyInstance(org.integratedmodelling.klab.api.knowledge.observation.Observation.class.getClassLoader(),
          new Class<?>[]{org.integratedmodelling.klab.api.knowledge.observation.Observation.class},
          (proxy, method, arguments) -> {
            try {
              var result = method.invoke(observation, arguments);
              return bind(result, actor);
            } catch (InvocationTargetException failure) { throw failure.getCause(); }
          });
    }
    if (value instanceof Concept concept) {
      var owned = actor.resolveConcept(concept.getUrn());
      // Bottom is a lawful semantic value; unresolved named identities must still be rejected.
      boolean canonicalBottom = "owl:Nothing".equals(concept.getUrn())
          && owned != null && "owl:Nothing".equals(owned.getUrn());
      if (owned == null || (owned.is(SemanticType.NOTHING) && !canonicalBottom)) throw new IllegalArgumentException(
          "Semantic identity is unavailable in the accepted generation: " + concept.getUrn());
      return owned;
    }
    if (value instanceof Observable observable) {
      var owned = JacksonConfiguration.newObjectMapper().convertValue(observable, Observable.class);
      ((ObservableImpl)owned).setSemantics((Concept)bind(observable.getSemantics(), actor));
      return owned;
    }
    if (value instanceof ObservableBuildStrategy strategy) {
      var owned = JacksonConfiguration.newObjectMapper().convertValue(strategy, ObservableBuildStrategy.class);
      owned.setBaseConcept((Concept)bind(strategy.getBaseConcept(), actor));
      owned.setBaseObservable((Observable)bind(strategy.getBaseObservable(), actor));
      for (int i = 0; i < owned.getOperations().size(); i++) {
        var operation = owned.getOperations().get(i);
        var source = strategy.getOperations().get(i);
        operation.setConcepts((List<Concept>)bind(source.getConcepts(), actor));
        operation.setPod(bind(source.getPod(), actor));
        operation.setValueOperation((org.integratedmodelling.klab.api.collections.Pair)bind(source.getValueOperation(), actor));
      }
      return owned;
    }
    if (value instanceof org.integratedmodelling.klab.api.collections.Pair<?, ?> pair)
      return org.integratedmodelling.klab.api.collections.Pair.of(bind(pair.getFirst(), actor), bind(pair.getSecond(), actor));
    if (value instanceof Optional<?> optional) return optional.map(item -> bind(item, actor));
    if (value instanceof Map<?, ?> map) {
      var result = new LinkedHashMap<>(); map.forEach((k, v) -> result.put(bind(k, actor), bind(v, actor))); return result;
    }
    if (value instanceof Collection<?> collection) {
      Collection<Object> result = value instanceof Set ? new LinkedHashSet<>() : new ArrayList<>();
      collection.forEach(item -> result.add(bind(item, actor))); return result;
    }
    if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
      var result = Array.newInstance(value.getClass().getComponentType(), Array.getLength(value));
      for (int i = 0; i < Array.getLength(value); i++) Array.set(result, i, bind(Array.get(value, i), actor));
      return result;
    }
    return value;
  }

  public static Object copy(Object value) {
    if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) return value;
    if (value instanceof org.integratedmodelling.klab.api.knowledge.observation.Observation)
      return JacksonConfiguration.newObjectMapper().convertValue(value, org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
    if (value instanceof Concept) return JacksonConfiguration.newObjectMapper().convertValue(value, Concept.class);
    if (value instanceof Observable) return JacksonConfiguration.newObjectMapper().convertValue(value, Observable.class);
    if (value instanceof Map<?, ?> map) {
      var result = new LinkedHashMap<>(); map.forEach((k, v) -> result.put(copy(k), copy(v))); return result;
    }
    if (value instanceof Collection<?> collection) {
      Collection<Object> result = value instanceof Set ? new LinkedHashSet<>() : new ArrayList<>();
      collection.forEach(item -> result.add(copy(item))); return result;
    }
    if (value instanceof org.integratedmodelling.klab.api.collections.Pair<?, ?> pair)
      return org.integratedmodelling.klab.api.collections.Pair.of(copy(pair.getFirst()), copy(pair.getSecond()));
    return JacksonConfiguration.newObjectMapper()
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .convertValue(value, value.getClass());
  }
  @Override public void close() {
    synchronized (lifecycle) {
      if (closed) return;
      closed = true; searches.invalidateAll(); searches.cleanUp();
    }
    coordinator.close();
  }
}
