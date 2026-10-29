package org.integratedmodelling.klab.services.reasoner.internal;

import org.integratedmodelling.common.diagnostics.DocumentValidationTrace;
import java.lang.reflect.*;
import java.time.Duration;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
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
  private final java.util.function.LongSupplier retryClock;
  private final String deploymentServiceId;
  private final String semanticSessionId=java.util.UUID.randomUUID().toString();
  public String semanticSessionId() {return semanticSessionId;}
  private String rejectedKey;
  private String submittedKey;
  private long submittedRetryAfter;

  private boolean suppressSubmitted(String key) {
    if (!Objects.equals(key, submittedKey)) return false;
    var status = coordinator.status().logicalStatus();
    return switch (status) {
      case FAILED, UNAVAILABLE, CANCELLED -> retryClock.getAsLong() - submittedRetryAfter < 0;
      default -> true;
    };
  }
  private long retryAfter;
  private int rejectionCount;
  public record PreflightAttempt(Worldview snapshot, String key, long generation, boolean retry) {}
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
    this(factory, System::nanoTime, null);
  }
  public AcceptedSemanticRuntime(SemanticEnvironmentFactory factory, String deploymentServiceId) {
    this(factory, System::nanoTime, Objects.requireNonNull(deploymentServiceId));
    if (deploymentServiceId.isBlank()) throw new IllegalArgumentException("Missing deployed Reasoner identity");
  }
  AcceptedSemanticRuntime(SemanticEnvironmentFactory factory, java.util.function.LongSupplier clock) {
    this(factory, clock, null);
  }
  private AcceptedSemanticRuntime(SemanticEnvironmentFactory factory, java.util.function.LongSupplier clock,
      String deploymentServiceId) {
    this.factory = Objects.requireNonNull(factory);
    this.retryClock = Objects.requireNonNull(clock);
    this.deploymentServiceId = deploymentServiceId;
    coordinator = new CandidatePublicationCoordinator<>(factory,
        input -> !factory.authorityBootstrapEnabled() || factory.publicationCurrent(input));
  }

  private ReasonerService.PortableCapabilityLease portableCapabilities(ReasonerService actor, long generation) {
    return deploymentServiceId == null ? () -> {} : actor.portableCapabilities(deploymentServiceId, generation,semanticSessionId);
  }

  /** A failed poll is not a new authoritative revision. Recheck transient failures with capped backoff. */
  public boolean shouldRetryPreflight(Worldview snapshot, Scope scope) {
    synchronized (lifecycle) { if (closed) return false; }
    final String key;
    try {
      var selection = factory.authorityBootstrapEnabled() && !snapshot.getAuthorityBindings().isEmpty()
          ? factory.authoritySelectionRevision(snapshot, scope)
          : snapshot.getMetadata().get(org.integratedmodelling.klab.components.AuthorityComponentResolver.SELECTION_METADATA);
      key = preflightKey(snapshot, scope, selection);
    }
    catch (IllegalArgumentException malformed) {
      // Let the normal preflight report malformed inputs; an unreadable key must not hide them.
      synchronized (lifecycle) { return !closed; }
    }
    synchronized (lifecycle) {
      if (suppressSubmitted(key)) return false;
      return !closed && (!Objects.equals(key, rejectedKey) || retryClock.getAsLong() - retryAfter >= 0);
    }
  }

  public PreflightAttempt beginPreflight(Worldview snapshot, Scope scope) {
    synchronized (lifecycle) {
      if (closed) return new PreflightAttempt(null, "", coordinator.status().inputGeneration(), false);
    }
    var owned = BeanIngestionPreflight.snapshot(refreshAuthoritySelection(snapshot, scope));
    var key = preflightKey(owned, scope);
    synchronized (lifecycle) {
      if (closed || suppressSubmitted(key)
          || (Objects.equals(key, rejectedKey) && retryClock.getAsLong() - retryAfter < 0))
        return new PreflightAttempt(owned, key, coordinator.status().inputGeneration(), false);
      traceInput(owned, scope, key);
      if (CandidateTrace.enabled()) CandidateTrace.emit(CandidateTrace.Event.PREFLIGHT_ALLOWED, coordinator.status().inputGeneration(),
          -1, -1, key, submittedKey, Objects.equals(key, submittedKey)
              ? CandidateTrace.Reason.SAME_KEY_RETRY : CandidateTrace.Reason.NEW_KEY);
      if (Objects.equals(key, submittedKey)) factory.invalidateBootstrap();
      coordinator.invalidate();
      return new PreflightAttempt(owned, key, coordinator.status().inputGeneration(), true);
    }
  }

  public boolean rejectPreflight(PreflightAttempt attempt,
      List<org.integratedmodelling.klab.api.services.runtime.Notification> failures, Scope scope) {
    synchronized (lifecycle) {
      if (!current(attempt, scope)) return false;
      rejectionCount = Objects.equals(rejectedKey, attempt.key()) ? Math.min(4, rejectionCount + 1) : 1;
      rejectedKey = attempt.key();
      if (CandidateTrace.enabled()) CandidateTrace.emit(CandidateTrace.Event.REJECTED, coordinator.status().inputGeneration(),
          -1, -1, attempt.key(), null, CandidateTrace.Reason.FAILURE);
      retryAfter = retryClock.getAsLong() + Duration.ofSeconds(Math.min(60, 10L << (rejectionCount - 1))).toNanos();
      coordinator.rejectPreflight(failures.stream()
          .map(org.integratedmodelling.klab.api.services.runtime.Notification::getMessage).toList());
      return true;
    }
  }

  public boolean submit(Worldview snapshot, PreflightAttempt attempt, Scope scope) {
    synchronized (lifecycle) { if (closed) return false; }
    var input = factory.authorityBootstrapEnabled() ? factory.capture(snapshot, scope) : factory.capture(snapshot);
    synchronized (lifecycle) {
      if (!current(attempt, scope) || !attempt.key().equals(preflightKey(snapshot, scope))) return false;
      clearRejected(); submittedKey = attempt.key();
      submittedRetryAfter = retryClock.getAsLong() + Duration.ofSeconds(60).toNanos();
      submittedSources = input.sourceHashes();
      long generation = coordinator.submit(input);
      if (CandidateTrace.enabled()) CandidateTrace.emit(CandidateTrace.Event.SUBMITTED, generation, generation, -1,
          attempt.key(), CandidateTrace.digest(input.dependencyRevision()), CandidateTrace.Reason.NONE);
      return true;
    }
  }

  private boolean current(PreflightAttempt attempt, Scope scope) {
    return !closed && attempt != null && attempt.retry()
        && coordinator.status().inputGeneration() == attempt.generation()
        && attempt.key().equals(preflightKey(attempt.snapshot(), scope));
  }

  private void traceInput(Worldview snapshot, Scope scope, String key) {
    if (!CandidateTrace.enabled()) return;
    try {
      long generation=coordinator.status().inputGeneration();
      Object[] fields={new TreeMap<>(SemanticEnvironmentFactory.sources(snapshot)),
          SemanticEnvironmentFactory.sourceOrder(snapshot), factory.dependencyRevision(),
          SemanticEnvironmentFactory.CONFIGURATION_REVISION,
          snapshot.getMetadata().get(org.integratedmodelling.klab.components.AuthorityComponentResolver.REVISION_METADATA),
          snapshot.getMetadata().get(org.integratedmodelling.klab.components.AuthorityComponentResolver.SELECTION_METADATA),
          snapshot.getAuthorityBindings().stream().sorted(Comparator.comparing(Worldview.AuthorityBinding::localId)).toList(),
          org.integratedmodelling.klab.components.AuthorityComponentResolver.ownerRevision(scope)};
      var events=List.of(CandidateTrace.Event.FIELD_SOURCES,CandidateTrace.Event.FIELD_ORDER,
          CandidateTrace.Event.FIELD_DEPENDENCY,CandidateTrace.Event.FIELD_CONFIGURATION,
          CandidateTrace.Event.FIELD_CATALOG,CandidateTrace.Event.FIELD_SELECTION,
          CandidateTrace.Event.FIELD_BINDINGS,CandidateTrace.Event.FIELD_OWNER);
      for(int i=0;i<fields.length;i++) CandidateTrace.emit(events.get(i),generation,-1,-1,
          key,CandidateTrace.digest(fields[i]),CandidateTrace.Reason.NONE);
    } catch(Throwable ignored) { /* Trace only; authoritative fingerprinting is unchanged. */ }
  }
  private void clearRejected() { rejectedKey = null; rejectionCount = 0; retryAfter = 0; }

  private Worldview refreshAuthoritySelection(Worldview source, Scope scope) {
    // Resolve outside the lifecycle lock: accepted generations remain readable during discovery.
    return factory.authorityBootstrapEnabled() && !source.getAuthorityBindings().isEmpty()
        ? factory.refreshAuthoritySelection(source, scope) : source;
  }

  private String preflightKey(Worldview snapshot, Scope scope) {
    return preflightKey(snapshot, scope, snapshot.getMetadata().get(
        org.integratedmodelling.klab.components.AuthorityComponentResolver.SELECTION_METADATA));
  }

  private String preflightKey(Worldview snapshot, Scope scope, Object selectionRevision) {
    try {
      var metadataRevision = snapshot.getMetadata().get(
          org.integratedmodelling.klab.components.AuthorityComponentResolver.REVISION_METADATA);
      var payload = Arrays.asList(new TreeMap<>(SemanticEnvironmentFactory.sources(snapshot)),
          SemanticEnvironmentFactory.sourceOrder(snapshot), factory.dependencyRevision(),
          SemanticEnvironmentFactory.CONFIGURATION_REVISION, metadataRevision,
          selectionRevision,
          snapshot.getAuthorityBindings().stream().sorted(Comparator.comparing(Worldview.AuthorityBinding::localId)).toList(),
          org.integratedmodelling.klab.components.AuthorityComponentResolver.ownerRevision(scope));
      var bytes = JacksonConfiguration.newObjectMapper()
          .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(payload);
      return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception failure) { throw new IllegalArgumentException("Cannot fingerprint preflight input", failure); }
  }
  public long submit(Worldview snapshot) {
    if(factory.authorityBootstrapEnabled()) throw new SecurityException("Bootstrap submission requires its requesting scope");
    var input = factory.capture(snapshot);
    synchronized (lifecycle) { requireOpen(); clearRejected(); submittedSources = input.sourceHashes(); return coordinator.submit(input); }
  }
  public long submit(Worldview snapshot, Scope owner) {
    var attempt=beginPreflight(snapshot,owner);
    if(attempt.retry()) submit(attempt.snapshot(),attempt,owner);
    return coordinator.status().inputGeneration();
  }
  public Map<String, String> submittedSources() { return submittedSources; }
  public record Metadata(long generation, SemanticCapabilitiesSnapshot publication,
      List<org.integratedmodelling.klab.api.services.runtime.extension.Extensions.ComponentDescriptor> components,
      String semanticContentFingerprint) {
    public Metadata(long generation, SemanticCapabilitiesSnapshot publication,
        List<org.integratedmodelling.klab.api.services.runtime.extension.Extensions.ComponentDescriptor> components) {
      this(generation, publication, components, null);
    }
  }
  public Metadata metadata() {
    try (var lease = lease()) { return new Metadata(lease.generation(), lease.state().actor().semanticCapabilitiesSnapshot(),
        lease.state().actor().capturedAuthorityComponents(), lease.state().semanticContentFingerprint()); }
    catch (IllegalStateException unavailable) { return new Metadata(0, SemanticCapabilitiesSnapshot.initial(), List.of(), null); }
  }
  public boolean authorityBootstrapEnabled() { return factory.authorityBootstrapEnabled(); }
  public void invalidate(CandidateTrace.Reason reason) {
    if (CandidateTrace.enabled()) CandidateTrace.emit(CandidateTrace.Event.INVALIDATED, coordinator.status().inputGeneration(),
        -1, -1, null, null, reason);
    invalidate();
  }
  public void invalidate() { synchronized (lifecycle) { if (!closed) { clearRejected(); submittedKey=null; factory.invalidateBootstrap(); coordinator.invalidate(); } } }
  public void rejectPreflight(List<org.integratedmodelling.klab.api.services.runtime.Notification> failures) {
    synchronized (lifecycle) {
      if (closed) return;
      clearRejected();
      coordinator.rejectPreflight(failures.stream().map(org.integratedmodelling.klab.api.services.runtime.Notification::getMessage).toList());
    }
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

  /** Pin admission and ranking to one accepted actor; stale evidence never invokes its matcher. */
  public int semanticDistanceAtRevision(Semantics target, Semantics requested, Semantics context,
      long expectedKnowledgeRevision) {
    if (expectedKnowledgeRevision < 0) return -50;
    try (var lease = lease()) {
      if (lease.generation() != expectedKnowledgeRevision) return -50;
      synchronized (lease.state()) {
        var actor = lease.state().actor();
        try (var portable = portableCapabilities(actor, lease.generation())) {
          return actor.semanticDistance(target, requested, context);
        }
      }
    } catch (IllegalStateException unavailable) { return -50; }
  }

  /** Observer hierarchy comparison belongs to the same generation as its metric score. */
  public int assertedDistanceAtRevision(Concept from, Concept to, long expectedKnowledgeRevision) {
    if (expectedKnowledgeRevision < 0) return -1;
    try (var lease = lease()) {
      if (lease.generation() != expectedKnowledgeRevision) return -1;
      synchronized (lease.state()) {
        var actor = lease.state().actor();
        try (var portable = portableCapabilities(actor, lease.generation())) {
          return actor.assertedDistance(from, to);
        }
      }
    } catch (IllegalStateException unavailable) { return -1; }
  }
  public org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse validate(
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest request,
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse response) {
    return validate(request,response,null);
  }
  public org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse validate(
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest request,
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse response,Scope requestingScope) {
    try (var lease = lease()) {
      response.setKnowledgeRevision(lease.generation());
      response.setSemanticSessionId(semanticSessionId);
      response.setSemanticContentFingerprint(lease.state().semanticContentFingerprint());
      if (request.getKnowledgeRevision() >= 0 && request.getKnowledgeRevision() != lease.generation()) {
        response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.STALE_KNOWLEDGE);
        response.setReason("The accepted semantic generation changed"); return response;
      }
      if (request.document() instanceof org.integratedmodelling.klab.api.lang.kim.KimOntology
          && !Objects.equals(response.getSourceHash(), lease.sourceHashes().get(SemanticEnvironmentFactory.sourceKey(request.document())))) {
        return unavailableValidation(request, response);
      }
      synchronized (lease.state()) {
        var actor = lease.state().actor();
        try (var portable = portableCapabilities(actor, lease.generation())) {
        var validator=new DocumentSemanticValidator(actor,request.isExecutableModels(),request.isClassifierPreparableDependencies(), request.getExecutionContext(), request.getPreparableMemberQualityDependencies());
        response.getNotifications().addAll(validator.validate(request.document()));
        response.getPendingClassifierDependencies().addAll(validator.getPendingClassifierDependencies());
        response.getConditionalModelContexts().addAll(validator.getConditionalModelContexts());
        response.getPendingModelContexts().addAll(validator.getPendingModelContexts());
        response.getPendingMemberQualityDependencies().addAll(validator.getPendingMemberQualityDependencies());
        response.setSemanticObligations(validator.getSemanticObligations());
        response.setSemanticObligationsComplete(validator.isSemanticObligationsComplete());
        response.setSourceSemanticsComplete(validator.isSourceSemanticsComplete());
        var dependencyRequest=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest(
            deploymentServiceId,lease.generation(),lease.state().semanticContentFingerprint(),validator.getSemanticObligations());
        try {
          response.setSemanticWitness(validator.isSemanticObligationsComplete()
              ?actor.semanticDependencies(dependencyRequest,requestingScope)
              :org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.unknown(dependencyRequest,
                  "Source contains unaccounted or invalid semantic expressions"));
        } catch(SecurityException unauthorized) {
          response.setSemanticWitness(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.unknown(
              dependencyRequest,"Authority dependency access is not authorized for this validation scope"));
        }
        response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.COMPLETE);
        return response;
        }
      }
    } catch (IllegalStateException unavailable) {
      if (DocumentValidationTrace.enabled())
        DocumentValidationTrace.exception(
            request,
            response.getKnowledgeRevision(),
            unavailable);
      return unavailableValidation(request, response);
    }
  }

  /** Stable, client-readable explanation; polling does not initiate validation. */
  public static boolean validationBlocked(CandidatePublicationCoordinator.Status status) {
    return status.beanStatus() == CandidatePublicationCoordinator.BeanStatus.INVALID
        || switch (status.logicalStatus()) {
          case INVALID, UNRESOLVED, FAILED -> true;
          default -> false;
        };
  }

  public static String validationReason(CandidatePublicationCoordinator.Status status) {
    String reason;
    if (status.beanStatus() == CandidatePublicationCoordinator.BeanStatus.INVALID) {
      reason = "Worldview ingestion blocked by preflight errors";
    } else {
      reason = switch (status.logicalStatus()) {
        case VALID -> "Worldview semantic validation completed";
        case PENDING -> "Whole-worldview semantic validation is in progress";
        case NOT_RUN -> "Waiting for worldview ingestion";
        default -> "Worldview semantic validation " + status.logicalStatus().name().toLowerCase(Locale.ROOT);
      };
    }
    if (!status.diagnostics().isEmpty()) reason += ": " + String.join("; ", status.diagnostics());
    if (status.acceptedGeneration() > 0 && status.logicalStatus() != CandidatePublicationCoordinator.LogicalStatus.VALID)
      reason += "; the prior accepted semantic generation remains available";
    return reason;
  }

  private org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse unavailableValidation(
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest request,
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse response) {
    var status = coordinator.status();
    response.setKnowledgeRevision(status.acceptedGeneration());
    response.setSemanticContentFingerprint(null);
    response.setSemanticSessionId(null);
    response.setStatus(validationBlocked(status)
        ? org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.FAILED
        : org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.UNAVAILABLE);
    response.setReason(status.logicalStatus() == CandidatePublicationCoordinator.LogicalStatus.VALID
        ? "The saved document is not part of the accepted semantic generation; waiting for worldview synchronization"
        : validationReason(status));
    response.addDocumentDiagnostic(request, validationBlocked(status)
        ? org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error
        : org.integratedmodelling.klab.api.services.runtime.Notification.Level.Warning);
    return response;
  }

  public <T> T invoke(String name, Class<?>[] signature, Object[] supplied) {
    try (var lease = lease()) {
      // Retain the generation, but not its monitor, while Runtime materialization calls back.
      var prepared = Arrays.stream(supplied).map(AcceptedSemanticRuntime::prepareScopes).toArray();
      requireOpen();
      if (coordinator.status().acceptedGeneration() != lease.generation())
        throw new IllegalStateException("Accepted semantic generation changed during observer preparation; replan the request");
      var environment = lease.state();
      synchronized (environment) {
        requireOpen();
        if (coordinator.status().acceptedGeneration() != lease.generation())
          throw new IllegalStateException("Accepted semantic generation changed before evaluation; replan the request");
        var actor = environment.actor();
        try (var portable = portableCapabilities(actor, lease.generation())) {
        if (factory.authorityBootstrapEnabled()) actor.enableInteractiveAuthorities();
        var arguments = new Object[supplied.length];
        for (int i = 0; i < arguments.length; i++) arguments[i] = bind(prepared[i], actor);
        var method = ReasonerService.class.getMethod(name, signature);
        var result = method.invoke(actor, arguments);
        requireOpen();
        if (coordinator.status().acceptedGeneration() != lease.generation())
          throw new IllegalStateException("Accepted semantic generation changed during evaluation; replan the request");
        // Only match exposes an output binding map; other maps remain caller-owned inputs.
        for (int i = 0; i < arguments.length; i++)
          if (name.equals("match") && supplied[i] instanceof Map original && arguments[i] instanceof Map mapped
              && !original.equals(mapped)) {
            original.clear(); original.putAll((Map)copy(mapped));
          }
        return (T)copy(result);
        }
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
      var preparedScope = existing == null ? (Scope) prepareScopes(originalScope) : null;
      if (closed) return closedSearch(request);
      if (existing == null && coordinator.status().acceptedGeneration() != operation.generation())
        throw new IllegalStateException("Accepted semantic generation changed during observer preparation; restart the search");
      synchronized (operation.state()) {
        if (closed) return closedSearch(request);
        if (existing == null && coordinator.status().acceptedGeneration() != operation.generation())
          throw new IllegalStateException("Accepted semantic generation changed before search evaluation; restart the search");
        var actor = operation.state().actor();
        try (var portable = portableCapabilities(actor, operation.generation())) {
        if (factory.authorityBootstrapEnabled()) actor.enableInteractiveAuthorities();
        var scoped = existing == null ? scope(preparedScope, actor) : existing.scope();
        var ownedRequest = JacksonConfiguration.newObjectMapper().convertValue(request,
            org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest.class);
        ownedRequest.setSearchId(existing == null ? 0 : existing.actorId());
        var response = (org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse)
            copy(actor.semanticSearch(ownedRequest, scoped));
        if (closed) return closedSearch(request);
        if (existing == null && coordinator.status().acceptedGeneration() != operation.generation())
          throw new IllegalStateException("Accepted semantic generation changed during search evaluation; restart the search");
        if (request.isCancelSearch() || response.getSearchId() == 0) {
          if (existing != null) searches.invalidate(request.getSearchId());
          response.setSearchId(0); return response;
        }
        synchronized (lifecycle) {
          if (closed) return closedSearch(request);
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
      }
    } finally { if (!retained) persistent.close(); }
  }

  /** An admitted search closed while waiting returns its established terminal form, no results. */
  private org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse closedSearch(
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest request) {
    var response = new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse(0, request.getRequestId());
    response.getErrors().add("Semantic runtime is closed");
    return response;
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
            if (method.getName().equals("getObserver") && result instanceof Observation observer) {
              var meaning = actor.resolveConcept(observer.getObservable().getSemantics().getUrn());
              if (meaning == null || !meaning.is(SemanticType.AGENT) || meaning.is(SemanticType.NOTHING))
                throw new IllegalStateException("Selected observer is not an Agent in the leased semantic generation");
            }
            if (result instanceof Scope nested) return nested == original ? proxy : scope(nested, actor);
            if (result instanceof Reasoner) return actor;
            if (result instanceof ResourcesService) return actor.serviceScope().getService(ResourcesService.class);
            return bind(result, actor);
          }
          catch (InvocationTargetException failure) { throw failure.getCause(); }
        });
  }

  /** Request-local observer preparation. No Runtime/graph callback may happen under the semantic monitor. */
  static Object prepareScopes(Object value) {
    if (value instanceof ContextScope context) return ObserverScope.prepare(context);
    if (value instanceof Map<?, ?> map) {
      var prepared = new LinkedHashMap<>(); map.forEach((key, item) -> prepared.put(prepareScopes(key), prepareScopes(item)));
      return prepared;
    }
    if (value instanceof Collection<?> values) {
      Collection<Object> prepared = value instanceof Set ? new LinkedHashSet<>() : new ArrayList<>();
      values.forEach(item -> prepared.add(prepareScopes(item))); return prepared;
    }
    if (value != null && value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
      var prepared = Array.newInstance(value.getClass().getComponentType(), Array.getLength(value));
      for (int i = 0; i < Array.getLength(value); i++) Array.set(prepared, i, prepareScopes(Array.get(value, i)));
      return prepared;
    }
    return value;
  }

  /** Keeps root and request selections distinct, and shares only immutable per-call snapshots. */
  private static final class ObserverScope implements InvocationHandler {
    private final ContextScope original;
    private final Observation observer;
    private final IdentityHashMap<ContextScope, Observation> prepared;

    private ObserverScope(ContextScope original, Observation observer,
        IdentityHashMap<ContextScope, Observation> prepared) {
      this.original = original; this.observer = observer; this.prepared = prepared;
    }
    static ContextScope prepare(ContextScope original) {
      if (Proxy.isProxyClass(original.getClass()) && Proxy.getInvocationHandler(original) instanceof ObserverScope)
        return original;
      var prepared = new IdentityHashMap<ContextScope, Observation>();
      capture(original, prepared);
      return wrap(original, prepared.get(original), prepared);
    }
    private static void capture(ContextScope scope, IdentityHashMap<ContextScope, Observation> prepared) {
      if (scope == null || prepared.containsKey(scope)) return;
      prepared.put(scope, null); // identity recursion guard, including roots that return themselves
      prepared.put(scope, snapshot(scope.getObserver()));
      capture(scope.getRootContextScope(), prepared);
      if (scope.getParentScope() instanceof ContextScope parent) capture(parent, prepared);
    }
    private static Observation snapshot(Observation observer) {
      if (observer != null && (observer.getId() <= 0 || observer.getObservable() == null
          || observer.getObservable().getSemantics() == null
          || !observer.getObservable().getSemantics().is(SemanticType.AGENT)))
        throw new IllegalArgumentException("Semantic evaluation requires a persisted Agent observer");
      return observer == null ? null : (Observation) copy(Observation.forTransport(observer));
    }
    private static ContextScope wrap(ContextScope scope, Observation observer,
        IdentityHashMap<ContextScope, Observation> prepared) {
      var interfaces = new LinkedHashSet<Class<?>>(); interfaces.add(ContextScope.class);
      for (Class<?> type = scope.getClass(); type != null; type = type.getSuperclass())
        for (var implemented : type.getInterfaces())
          if (Scope.class.isAssignableFrom(implemented)) interfaces.add(implemented);
      return (ContextScope) Proxy.newProxyInstance(Scope.class.getClassLoader(),
          interfaces.toArray(Class<?>[]::new), new ObserverScope(scope, observer, prepared));
    }
    @Override public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
      if (method.getName().equals("getObserver")) return snapshot(observer);
      Observation selection = observer;
      if (method.getName().equals("withObserver")) selection = snapshot((Observation) arguments[0]);
      if (method.getName().equals("withResolutionConstraints") && arguments != null && arguments.length == 1
          && arguments[0] instanceof ResolutionConstraint[] constraints) {
        var retained = new ArrayList<ResolutionConstraint>();
        // Match the production scope's named-selection precedence over inherited numeric defaults.
        var namedSelections = Arrays.stream(constraints).filter(Objects::nonNull)
            .filter(constraint -> constraint.getType() == ResolutionConstraint.Type.ObserverUrn && !constraint.empty())
            .flatMap(constraint -> constraint.payload(String.class).stream()).distinct().toList();
        if (namedSelections.size() > 1 || namedSelections.stream().anyMatch(String::isBlank))
          throw new IllegalArgumentException("A submission must select one nonblank observer URN");
        for (var constraint : constraints) {
          if (constraint == null || constraint.empty()) { retained.add(constraint); continue; }
          boolean numeric = constraint.getType() == ResolutionConstraint.Type.Observer;
          boolean named = constraint.getType() == ResolutionConstraint.Type.ObserverUrn;
          if (!numeric && !named) { retained.add(constraint); continue; }
          if (numeric && !namedSelections.isEmpty()) continue;
          boolean matches = observer != null && (numeric
              ? constraint.payload(Long.class).stream().allMatch(id -> id == observer.getId())
              : constraint.payload(String.class).stream().allMatch(urn -> Objects.equals(urn, observer.getUrn())));
          if (!matches) throw new IllegalArgumentException(
              "Select the observer before entering semantic evaluation; a leased scope cannot resolve another observer");
          // An identical selection is already hydrated. Delegating it would perform another KG lookup.
        }
        arguments = new Object[] {retained.toArray(ResolutionConstraint[]::new)};
      }
      final Object result;
      try { result = method.invoke(original, arguments); }
      catch (InvocationTargetException failure) { throw failure.getCause(); }
      if (result instanceof ContextScope nested) {
        if (nested == original && selection == observer) return proxy;
        if (prepared.containsKey(nested)) return wrap(nested, prepared.get(nested), prepared);
        boolean derived = method.getName().startsWith("with") || method.getName().equals("within")
            || method.getName().equals("between");
        if (!derived) throw new IllegalStateException("Context selection was not prepared before semantic evaluation");
        return wrap(nested, selection, prepared);
      }
      return result;
    }
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
    // Observation getters declare Metadata, not Map: retain its contract when binding a proxy.
    if (value instanceof org.integratedmodelling.klab.api.data.Metadata metadata) {
      var result = org.integratedmodelling.klab.api.data.Metadata.create();
      metadata.forEach((key, item) -> result.put(key, bind(item, actor)));
      return result;
    }
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
    factory.invalidateBootstrap();
  }
}
