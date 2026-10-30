package org.integratedmodelling.klab.services.reasoner;

import org.integratedmodelling.common.diagnostics.DocumentValidationTrace;
import org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime;

import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;

import org.integratedmodelling.klab.api.exceptions.KlabValidationException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.IntelligentMap;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.services.client.ServiceClientCatalog;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.ApplicableConcept;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils.CamelCase;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.indexing.Indexer;
import org.integratedmodelling.klab.indexing.SemanticSearchSession;
import org.integratedmodelling.klab.runtime.language.KimObservableVisitor;
import org.integratedmodelling.klab.runtime.language.SemanticDocumentation;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.configuration.ReasonerConfiguration;
import org.integratedmodelling.klab.services.configuration.ReasonerConfiguration.ProjectConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;
import org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace;
import org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.integratedmodelling.klab.services.reasoner.internal.IngestionNotifications;
import org.integratedmodelling.klab.services.reasoner.internal.BeanIngestionPreflight;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.integratedmodelling.klab.services.reasoner.owl.Ontology;
import org.integratedmodelling.klab.services.reasoner.owl.OwlDocumentation;
import org.integratedmodelling.klab.services.reasoner.owl.Vocabulary;
import org.integratedmodelling.klab.utilities.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReasonerService extends BaseService implements Reasoner, Reasoner.Admin {

  @Override
  public <T> T info(
      String urn, KlabAsset.KnowledgeClass objectClass, Class<T> infoClass, UserScope scope) {
    if (candidateRuntime != null && !semanticOnly && infoClass == String.class
        && (objectClass == KlabAsset.KnowledgeClass.CONCEPT || objectClass == KlabAsset.KnowledgeClass.OBSERVABLE))
      return candidateRuntime.invoke("info", new Class<?>[]{String.class, KlabAsset.KnowledgeClass.class, Class.class, UserScope.class},
          new Object[]{urn, objectClass, infoClass, scope});
    if (infoClass == String.class
        && (objectClass == KlabAsset.KnowledgeClass.CONCEPT
            || objectClass == KlabAsset.KnowledgeClass.OBSERVABLE)) {
      Semantics asset =
          objectClass == KlabAsset.KnowledgeClass.OBSERVABLE
              ? resolveObservable(urn)
              : resolveConcept(urn);
      if (asset == null) return null;
      var markdown =
          "# Semantic documentation\n\n"
              + SemanticDocumentation.code(urn)
              + "\n\n"
              + owl.coreAliasDocumentation().describe(asset.asConcept())
              + SemanticDocumentation.describe("Resolved bean and metadata", asset)
              + SemanticInfoDocumentation.clauses(this, asset)
              + OwlDocumentation.describe(owl, asset.asConcept());
      return infoClass.cast(markdown);
    }
    return super.info(urn, objectClass, infoClass, scope);
  }

  /**
   * Flag for {@link #compatible(Semantics, Semantics, int)}.
   *
   * <p>If passed to {@link #compatible(Semantics, Semantics, int)}, different realms will not
   * determine incompatibility.
   */
  public static final int ACCEPT_REALM_DIFFERENCES = 0x01;

  /**
   * Flag for {@link #compatible(Semantics, Semantics, int)}.
   *
   * <p>If passed to {@link #compatible(Semantics, Semantics, int)}, only types that have the exact
   * same core type will be accepted.
   */
  public static final int REQUIRE_SAME_CORE_TYPE = 0x02;

  /**
   * Flag for {@link #compatible(Semantics, Semantics, int)}.
   *
   * <p>If passed to {@link #compatible(Semantics, Semantics, int)}, types with roles that are more
   * general of the roles in the first concept will be accepted.
   */
  public static final int USE_ROLE_PARENT_CLOSURE = 0x04;

  /**
   * Flag for {@link #compatible(Semantics, Semantics, int)}.
   *
   * <p>If passed to {@link #compatible(Semantics, Semantics, int)}, types with traits that are more
   * general of the traits in the first concept will be accepted.
   */
  public static final int USE_TRAIT_PARENT_CLOSURE = 0x08;

  private final AtomicBoolean consistent = new AtomicBoolean(true);
  private final AtomicLong knowledgeRevision = new AtomicLong();
  private final Map<String, String> loadedOntologySources = new HashMap<>();
  private final Map<String, Concept> declarationDomains = new HashMap<>();
  private final Map<String, List<Notification>> loadedOntologyDiagnostics = new HashMap<>();
  // Changed only under the existing mutation monitor. A generation mismatch hides an old
  // publication during the interval between status settlement and publishing discovery data.
  private volatile long capabilitiesGeneration;
  private volatile org.integratedmodelling.klab.services.reasoner.internal.SemanticCapabilitiesSnapshot
      publishedSemanticCapabilities =
          org.integratedmodelling.klab.services.reasoner.internal.SemanticCapabilitiesSnapshot.initial();

  private record SubsumptionKey(long revision, Concept concept, Concept other) {}

  private ReasonerConfiguration configuration = new ReasonerConfiguration();
  private final Map<String, String> coreConceptPeers = new HashMap<>();
  private final Map<Concept, Emergence> emergent = new HashMap<>();
  private final IntelligentMap<Set<Emergence>> emergence;
  private ObservationReasoner observationReasoner;
  private volatile Worldview worldview;
  private volatile org.integratedmodelling.klab.services.reasoner.internal.AuthoredVocabulary authoredVocabulary;
  private final org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings
      authorityBindings;

  @Override
  public synchronized org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistResponse authorityCodelists(
      org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistRequest request, Scope scope) {
      if (candidateRuntime != null && !semanticOnly) {
          if (request.operation() != org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistRequest.Operation.LIST)
            throw new UnsupportedOperationException("Authority ledger changes require authoritative persistence and candidate dependency publication");
          return candidateRuntime.invoke("authorityCodelists", new Class<?>[] { org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistRequest.class, Scope.class }, new Object[] { request, scope });
      }
      authorizedAuthority(request.authority(), scope);
    var operation = request.operation();
    if (operation == org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistRequest.Operation.REVIEW
        || operation == org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistRequest.Operation.DELETE
        || operation == org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistRequest.Operation.CREATE
        || operation == org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistRequest.Operation.UPDATE) {
      if (!(scope instanceof org.integratedmodelling.klab.services.scopes.ServiceUserScope user)
          || !user.getRoles().contains(org.integratedmodelling.klab.services.application.security.Role.ROLE_ADMINISTRATOR))
        throw new SecurityException("Codelist review requires an administrator");
    }
    var result = authorityBindings.codelists(request.authority()).execute(request, scope.getIdentity().getId());
    if (operation != org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistRequest.Operation.LIST) {
      semanticContentFingerprint = null;
      concepts.invalidateAll(); observables.invalidateAll(); conceptSyntax.clear();
    }
    return result;
  }

  @Override
  public synchronized String configureAuthority(Authority.ConfigurationRequest request, Scope scope) {
    if (candidateRuntime != null && !semanticOnly)
      throw new UnsupportedOperationException("Authority configuration requires a captured, independently owned candidate provider");
    semanticContentFingerprint = null;
    var result = configureAuthorityBinding(request, scope);
    if (semanticValidationState.ready()) publishSemanticCapabilities();
    return result;
  }

  @Override
  public synchronized Map<String, java.net.URL> getAuthorityDocumentation(
      String authority, String identity, Scope scope) {
      if (candidateRuntime != null && !semanticOnly)
          return candidateRuntime.invoke("getAuthorityDocumentation", new Class<?>[] { String.class, String.class, Scope.class }, new Object[] { authority, identity, scope });
      if (scope == null) throw new KlabValidationException("Authority documentation requires a scope");
    return authorityBindings.documentation(authority, identity);
  }

  private Worldview.AuthorityBinding authorizedAuthority(String name, Scope scope) {
    if (scope == null) throw new SecurityException("Authority operations require an authorized scope");
    var definition = worldview == null ? null : worldview.getAuthorityBindings().stream()
        .filter(binding -> binding.localId().equals(name)).findFirst().orElse(null);
    if (definition == null) throw new java.util.NoSuchElementException("Authority is not configured");
    if (semanticOnly && !authorityBootstrapActor) {
      if (!capturedAuthorities.authorized(definition, scope)) throw new SecurityException("Authority component access is not captured");
      return definition;
    }
    var component = getComponentRegistry().getComponents(scope).stream()
        .filter(candidate -> candidate.id().equals(definition.componentUrn())
            && candidate.version().equals(definition.componentVersion())).findFirst().orElse(null);
    if (component == null) throw new java.util.NoSuchElementException("Authority component is unavailable");
    if (component.usageRights() != null && !component.usageRights().checkAuthorization(scope))
      throw new SecurityException("Authority component access denied");
    return definition;
  }

  @Override
  public synchronized org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse searchAuthority(
      org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchRequest request, Scope scope) {
      if (candidateRuntime != null && !semanticOnly)
          return candidateRuntime.invoke("searchAuthority", new Class<?>[] { org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchRequest.class, Scope.class }, new Object[] { request, scope });
      try {
      var definition = authorizedAuthority(request.authority(), scope);
      return authorityBindings.search(request);
    } catch (java.util.NoSuchElementException e) {
      return org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.failure(
          org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.Status.UNAVAILABLE,
          "Authority is not configured");
    }
  }

  private synchronized SemanticSearchSession.AuthoritySelection resolveAuthoritySelection(
      String name, String code, Scope scope) {
    authorizedAuthority(name, scope);
    var binding = authorityBindings.get(name);
    if (binding == null) throw new KlabValidationException("Authority is not configured");
    final Authority.Identity identity;
    try { identity = binding.provider().resolveIdentity(binding.id(), code); }
    catch (RuntimeException e) { throw new KlabValidationException("Authority identity lookup failed"); }
    if (identity == null || !code.equals(identity.getId()) || (identity.getNotifications() != null
        && identity.getNotifications().stream().anyMatch(n -> n.getLevel() == Notification.Level.Error)))
      throw new KlabValidationException("Authority did not resolve the selected canonical identity");
    String token = org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityIdentitySyntax.encode(name, code);
    var concept = owl.getConcept(token);
    if (concept == null || !concept.is(SemanticType.IDENTITY) || concept.is(SemanticType.NOTHING))
      throw new KlabValidationException("Authority identity is unavailable");
    return new SemanticSearchSession.AuthoritySelection(concept, token,
        authorityBindings.codelists(name).aliases(code));
  }

  private static String requestRoot(KimConceptStatement statement) {
    return statement.getNamespace() + ":" + statement.getUrn();
  }

  private String configureAuthorityBinding(Authority.ConfigurationRequest request, Scope scope) {
    request = org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings
        .forWorldview(request, worldview);
    var root = owl.getConcept(request.rootIdentity());
    if (root == null || !root.is(SemanticType.IDENTITY) || root.is(SemanticType.NOTHING))
      throw new KlabValidationException("Authority bridge root must be a loaded identity concept");
    String urn = (String) request.parameters().get("urn");
    String localName = request.name();
    var selected = worldview.getAuthorityBindings().stream()
        .filter(binding -> binding.localId().equals(localName)).findFirst().orElse(null);
    if (selected == null || !selected.rootIdentity().equals(request.rootIdentity())
        || !org.integratedmodelling.klab.api.data.Version.splitVersion(urn).getFirst()
            .equals(selected.provider().urn()))
      throw new KlabValidationException("Authority is not a validated worldview binding");
    var declared = worldview.allConceptStatements().stream()
        .filter(statement -> localName.equals(statement.getAuthorityRequired())
            && requestRoot(statement).equals(selected.rootIdentity())).findFirst().orElseThrow();
    if (!Objects.equals(declared.getAuthorityParameters().get("codelists"), request.parameters().get("codelists")))
      throw new KlabValidationException("Codelists must match the worldview configuration");
    if (!Objects.equals(declared.getAuthorityParameters().get("semanticBoundaries"), request.parameters().get("semanticBoundaries")))
      throw new KlabValidationException("Semantic boundaries must match the worldview configuration");
    var provider = authorityBootstrapActor ? getComponentRegistry().getAuthority(selected,scope)
        : semanticOnly ? capturedAuthorities.provider(selected, request, semanticAssets, () -> interactiveAuthoritiesEnabled && semanticProofRestoration.interactiveAllowed())
        : Utils.Resources.resolveAuthority(selected, scope, this, worldview);
    if (provider == null)
      throw new KlabValidationException("Authority provider is unavailable: " + urn);
    if (request.parameters().get("codelists") instanceof Map<?, ?> names) {
      for (var namespace : names.values())
        if (worldview.getOntologies().stream().anyMatch(o -> o.getUrn().equals(namespace)))
          throw new KlabValidationException("Codelist namespace collides with an ontology: " + namespace);
    }
    try {
      var configured = authorityBindings.configure(request, provider);
      // Binding a nested identity selects a lexical family even though ordinary nested
      // declarations do not own a restricting property. Prepare its normal identity port
      // during configuration, never as a side effect of lexicalRoot/read-only lookup.
      var rootOntology = owl.getOntology(root.getNamespace());
      createProperties(root, rootOntology);
      rootOntology.define();
      return configured;
    } catch (RuntimeException failure) {
      if (authorityBootstrapActor) org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics.report(
          org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics.Phase.AUTHORITY_CONFIGURATION,
          selected.componentUrn()+"@"+selected.componentVersion(),
          org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics.Reason.EXCEPTION,failure);
      throw failure;
    }
  }
  private org.integratedmodelling.klab.services.reasoner.internal.CapturedAuthorityCatalog capturedAuthorities =
      org.integratedmodelling.klab.services.reasoner.internal.CapturedAuthorityCatalog.empty();

  public List<org.integratedmodelling.klab.api.services.runtime.extension.Extensions.ComponentDescriptor> capturedAuthorityComponents() {
    return capturedAuthorities.components();
  }
  private java.nio.file.Path semanticAssets;
  private volatile boolean interactiveAuthoritiesEnabled;
  private final org.integratedmodelling.klab.services.reasoner.internal.SemanticProofRestoration semanticProofRestoration =
      new org.integratedmodelling.klab.services.reasoner.internal.SemanticProofRestoration();

  /** Called under an accepted environment lease, never during candidate compilation. */
  public void enableInteractiveAuthorities() {
    if (!semanticOnly || authorityBootstrapActor) throw new IllegalStateException("Not an accepted semantic actor");
    interactiveAuthoritiesEnabled = true;
  }
  private volatile boolean knowledgeReady;
  private final org.integratedmodelling.klab.services.reasoner.internal.SemanticValidationState
      semanticValidationState = new org.integratedmodelling.klab.services.reasoner.internal.SemanticValidationState();
  private org.integratedmodelling.klab.services.reasoner.internal.AuthorityIdentityResolver authorityIdentityResolver;

  @Override public synchronized Concept resolveAuthorityHierarchy(String authority, String code, Scope scope) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("resolveAuthorityHierarchy", new Class<?>[] { String.class, String.class, Scope.class }, new Object[] { authority, code, scope });
  authorizedAuthority(authority, scope);
    org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityIdentitySyntax.encode(authority, code);
    return authorityIdentityResolver.resolve(authority, code, true);
  }
  /** Internal matching hook; public semanticDistance routes retain contextual and head checks. */
  public Authority.SemanticDistance authoritySemanticDistance(Concept target, Concept candidate) {
    return authorityIdentityResolver == null ? Authority.SemanticDistance.unsupported()
        : authorityIdentityResolver.semanticDistance(target, candidate);
  }

  public boolean hasConfiguredAuthorities() { return !authorityBindings.snapshot().isEmpty(); }

  private volatile List<Notification> worldviewLoadDiagnostics = List.of();
  private SyntacticMatcher syntacticMatcher;
  private SemanticMatcher semanticMatcher;
  private List<Notification> advisories = new ArrayList<>();

  /** Caches for concepts and observables. */
  private final Cache<String, Concept> concepts =
      Caffeine.newBuilder().maximumSize(5_000).recordStats().build();

  private final Cache<String, Observable> observables =
      Caffeine.newBuilder().maximumSize(5_000).recordStats().build();

  private final org.integratedmodelling.klab.services.reasoner.internal.ConceptSyntaxCache conceptSyntax =
      new org.integratedmodelling.klab.services.reasoner.internal.ConceptSyntaxCache();

  public org.integratedmodelling.klab.services.reasoner.internal.ConceptSyntaxCache conceptSyntaxCache() {
    if (candidateRuntime != null && !semanticOnly) throw new IllegalStateException("Compiler state requires a private semantic actor");
    return conceptSyntax;
  }

  private final Cache<SubsumptionKey, Boolean> subsumption =
      Caffeine.newBuilder().maximumSize(20_000).recordStats().build();

  Indexer indexer;

  /**
   * Cache for ongoing requests expires in 10 minutes. CHECK this may be less and become
   * configurable.
   */
  private record OwnedSemanticSession(Object owner, SemanticSearchSession session) {}
  private Cache<Integer, OwnedSemanticSession> semanticExpressions =
      Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofMinutes(10)).build();

  private final OWL owl;
  private final boolean semanticOnly;
  private volatile String semanticContentFingerprint;
  /** Factory seal of the accepted input; editor/private mutations must never stamp receipts. */
  public void sealSemanticContent(String fingerprint) {
    if (!semanticOnly) throw new IllegalStateException("Only private captured actors have content identity");
    semanticContentFingerprint = fingerprint;
  }
  public String semanticContentFingerprint() { return semanticContentFingerprint; }
  public Object semanticCodelists() { return authorityBindings.semanticCodelists(); }

  private final String semanticSessionId=java.util.UUID.randomUUID().toString();
  private record PortableCapabilityIdentity(ReasonerService actor, String serviceId, long generation,String sessionId,
      PortableCapabilityIdentity previous) {}
  private static final ThreadLocal<PortableCapabilityIdentity> portableCapabilityIdentity = new ThreadLocal<>();

  /** Internal accepted-boundary scope; private mutation revisions continue to own semantic caches. */
  public interface PortableCapabilityLease extends AutoCloseable { @Override void close(); }

  public PortableCapabilityLease portableCapabilities(String serviceId, long generation) {
    return portableCapabilities(serviceId,generation,semanticSessionId);
  }
  public PortableCapabilityLease portableCapabilities(String serviceId,long generation,String sessionId) {
    if(sessionId==null||sessionId.isBlank())throw new IllegalArgumentException("Semantic owner session required");
    if (!semanticOnly || serviceId == null || serviceId.isBlank() || generation <= 0)
      throw new IllegalArgumentException("Portable capabilities require a private actor and an accepted deployment generation");
    var previous = portableCapabilityIdentity.get();
    var identity = new PortableCapabilityIdentity(this, serviceId, generation,sessionId, previous);
    portableCapabilityIdentity.set(identity);
    return new PortableCapabilityLease() {
      private boolean closed;
      @Override public void close() {
        if (closed) return;
        if (portableCapabilityIdentity.get() != identity)
          throw new IllegalStateException("Portable capability scopes must close in lexical order");
        closed = true;
        if (previous == null) portableCapabilityIdentity.remove();
        else portableCapabilityIdentity.set(previous);
      }
    };
  }

  private PortableCapabilityIdentity portableCapabilities() {
    for (var current = portableCapabilityIdentity.get(); current != null; current = current.previous())
      if (current.actor() == this) return current;
    return null;
  }
  private boolean authorityBootstrapActor;
  private List<Worldview.AuthorityBinding> bootstrapDisposalBindings=List.of();
  public void bootstrapDisposalBindings(Worldview source) {
    if (!authorityBootstrapActor) throw new IllegalStateException("Not a bootstrap actor");
    bootstrapDisposalBindings=List.copyOf(source.getAuthorityBindings());
  }
  private volatile org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime candidateRuntime;

  public org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime candidateRuntime() { return candidateRuntime; }
  public synchronized void enableCandidatePublication(
      org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory factory) {
    if (candidateRuntime != null) throw new IllegalStateException("Candidate publication is already configured");
    candidateRuntime = new org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime(factory, serviceId());
  }
  public org.integratedmodelling.klab.services.reasoner.internal.SemanticCapabilitiesSnapshot semanticCapabilitiesSnapshot() {
    return publishedSemanticCapabilities;
  }
  private final String hardwareSignature = Utils.Names.getHardwareId();
  static Pattern internalConceptPattern = Pattern.compile("[A-Z]+_[0-9]+");

  public boolean derived(Semantics c) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("derived", new Class<?>[] { Semantics.class }, new Object[] { c });
  return internalConceptPattern.matcher(c.getName()).matches();
  }

  public OWL owl() {
    if (candidateRuntime != null && !semanticOnly) throw new IllegalStateException("OWL access requires an accepted semantic lease");
    return owl;
  }

  public boolean isSemanticExecution() { return semanticOnly; }

  public long knowledgeRevision() {
    if (candidateRuntime != null && !semanticOnly) return candidateRuntime.status().acceptedGeneration();
    return knowledgeRevision.get();
  }

  private void rememberOntologySource(KimOntology ontology) {
    var hash = SemanticValidationRequest.sourceHash(ontology.getSourceCode());
    if (hash != null) loadedOntologySources.put(ontology.getUrn(), hash);
  }

  @Override
  public org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness semanticDependencies(
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest request, Scope scope) {
    if(request==null) throw new IllegalArgumentException("Semantic dependency request required");
    if(candidateRuntime!=null && !semanticOnly) {
      try { return candidateRuntime.invoke("semanticDependencies",new Class<?>[]{
          org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest.class,Scope.class},
          new Object[]{request,scope}); }
      catch(IllegalStateException unavailable) {
        return org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.unknown(request,
            "Accepted semantic closure changed or is unavailable");
      }
    }
    var capability=capabilities(scope);
    var id=capability.getServiceId(); var generation=capability.getKnowledgeRevision();
    var base=capability.getSemanticContentFingerprint();
    var status=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.COMPLETE;
    String reason=null;
    var uses=new ArrayList<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.AuthorityUse>();
    if(!org.integratedmodelling.klab.api.knowledge.SemanticContentFingerprint.scoped(base)
        || request.expectedServiceId()==null || request.expectedKnowledgeRevision()<0
        || !org.integratedmodelling.klab.api.knowledge.SemanticContentFingerprint.scoped(request.expectedBaseFingerprint())) {
      status=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.UNKNOWN;
      reason="Scoped accepted recipe and expected semantic identity are required";
    } else if(!Objects.equals(id,request.expectedServiceId()) || generation!=request.expectedKnowledgeRevision()
        || !Objects.equals(base,request.expectedBaseFingerprint())) {
      status=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.STALE;
      reason="Semantic dependency request targets a different accepted generation or recipe";
    } else {
      // Derived expression registry entries are process-local. Recompile canonical meanings
      // under this accepted lease before proving closure; do not fetch unseen mutable answers.
      var restorationFailure=semanticProofRestoration.restore(request.obligations(),this::resolveConcept);
      if(restorationFailure!=null) {
        status=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.UNKNOWN;
        reason=restorationFailure;
      } else {
      var closure=new org.integratedmodelling.klab.services.reasoner.owl.AuthorityDependencySupport(owl)
          .closure(request.obligations(),authorityBindings.snapshot(),base);
      if(!closure.known()) {
        status=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.UNKNOWN;
        reason=closure.reason();
      } else {
        for(var authority:closure.authorities()) {
          var binding=authorizedAuthority(authority,scope);
          var proof=capturedAuthorities.dependencyProof(binding); uses.add(proof);
          if(proof.kind()==org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Kind.UNVERSIONED) {
            status=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.REVALIDATION_REQUIRED;
            reason="Affected mutable authority requires independently captured current semantic revalidation; known identity cache is not refresh evidence";
          }
        }
      }
    }
    }
    return new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness(
        org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.VERSION,status,id,generation,
        base,request.inputHash(),uses,reason);
  }

  @Override
  public SemanticValidationResponse validateDocument(
      SemanticValidationRequest request, Scope scope) {
    var response = SemanticValidationResponse.forRequest(request);
    if (DocumentValidationTrace.enabled())
      DocumentValidationTrace.snapshot(
          DocumentValidationTrace.Event.REASONER_RECEIVED,
          request.document(),
          request.getDocumentVersion(),
          request.getKnowledgeRevision(),
          -1,
          DocumentValidationTrace.Decision.OBSERVE);
    try {
      return validateDocumentPreflight(request, scope, response);
    } catch (org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException e) {
      response.setStatus(SemanticValidationResponse.Status.UNAVAILABLE);
      response.setReason("Semantic validation requires an available Resources service: " + e.getMessage());
      response.addDocumentDiagnostic(request, Notification.Level.Warning);
    } catch (RuntimeException e) {
      response.setStatus(SemanticValidationResponse.Status.FAILED);
      response.setReason("Semantic validation could not complete: " + e.getMessage());
      response.addDocumentDiagnostic(request, Notification.Level.Error);
    } finally {
      response.deduplicateNotifications();
      if (DocumentValidationTrace.enabled())
        DocumentValidationTrace.response(
            DocumentValidationTrace.Event.REASONER_RETURN,
            request,
            response,
            -1,
            DocumentValidationTrace.Decision.OBSERVE);
    }
    return response;
  }

  /** Source diagnostics and availability checks must not wait for worldview ingestion. */
  private SemanticValidationResponse validateDocumentPreflight(
      SemanticValidationRequest request, Scope scope, SemanticValidationResponse response) {
    response.setKnowledgeRevision(knowledgeRevision());
    var document = request.document();
    // Project type is authoritative Resources metadata. Reject unsupported .kwv documents before
    // attempting a worldview repair: an unchanged adopted worldview cannot ever admit them.
    if (document instanceof KimOntology ontology && scope instanceof UserScope userScope
        && document.getProjectName() != null && !document.getProjectName().isBlank()) {
      var resources = scope.getService(ResourcesService.class);
      var project = resources == null ? null : resources.retrieve(document.getProjectName(),
          org.integratedmodelling.klab.api.knowledge.organization.Project.class, userScope);
      if (project != null && Objects.equals(project.getUrn(), document.getProjectName())
          && project.getManifest() != null
          && !org.integratedmodelling.klab.api.services.reasoner.objects.OntologyProjectPolicy
              .definesWorldview(project.getManifest())) {
        response.setStatus(SemanticValidationResponse.Status.COMPLETE);
        response.setReason("Ontology validation is blocked by the document's project type");
        response.getNotifications().addAll(document.getNotifications());
        response.getNotifications().add(
            org.integratedmodelling.klab.api.services.reasoner.objects.OntologyProjectPolicy.diagnostic(ontology));
        return response;
      }
    }
    if (Utils.Notifications.hasErrors(document.getNotifications())) {
      response.getNotifications().addAll(document.getNotifications());
      response.deduplicateNotifications();
      response.setStatus(SemanticValidationResponse.Status.SYNTAX_ERRORS);
      response.setReason("Semantic validation waits for parsing errors to be corrected");
      return response;
    }
    if (document instanceof KimOntology ontology
        && org.integratedmodelling.klab.services.reasoner.owl.WorldviewDeclarationSupport.hasDeferredValues(ontology)) {
      response.setStatus(SemanticValidationResponse.Status.UNAVAILABLE);
      response.setReason("DESCRIPTION_VALUE_UNRESOLVED: describes-as source is retained for authoring; "
          + "typed characterization validity and value matching cannot yet be certified");
      response.addDocumentDiagnostic(request, Notification.Level.Warning);
      return response;
    }
    // An editor snapshot may predate Resources validation of the saved file. Surface those
    // diagnostics even before OWL startup, but only when they refer to exactly this source.
    if (document instanceof KimOntology
        && response.getSourceHash() != null
        && scope instanceof UserScope userScope) {
      try {
        var resources = scope.getService(ResourcesService.class);
        if (resources != null) {
          var saved = resources.retrieve(document.getUrn(), KimOntology.class, userScope);
          if (saved != null
              && Objects.equals(
                  response.getSourceHash(),
                  SemanticValidationRequest.sourceHash(saved.getSourceCode()))
              && Utils.Notifications.hasErrors(saved.getNotifications())) {
            response.getNotifications().addAll(saved.getNotifications());
            response.deduplicateNotifications();
            response.setStatus(SemanticValidationResponse.Status.COMPLETE);
            response.setReason("The saved ontology contains validation errors");
            return response;
          }
        }
      } catch (RuntimeException e) {
        response.setReason("Cannot retrieve saved ontology diagnostics: " + e.getMessage());
        response.addDocumentDiagnostic(request, Notification.Level.Warning);
        return response;
      }
    }
    if (candidateRuntime != null && !semanticOnly) {
      if (document instanceof KimOntology && scope instanceof UserScope userScope
          && response.getSourceHash() != null
          && !response.getSourceHash().equals(candidateRuntime.submittedSources().get(
              org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory.sourceKey(document)))) {
        var resources = userScope.getService(ResourcesService.class);
        var saved = resources.retrieve(document.getUrn(), KimOntology.class, userScope);
        if (saved != null && Objects.equals(response.getSourceHash(), SemanticValidationRequest.sourceHash(saved.getSourceCode()))
            && worldview != null) {
          var complete = resources.retrieve(worldview.getUrn(), Worldview.class, userScope);
          if (complete != null) {
            if (org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.enabled()) org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.emit(
                org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Event.SAVED_SOURCE_REPAIR,
                candidateRuntime.status().inputGeneration(), -1, -1, response.getSourceHash(), null,
                org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Reason.USER);
            var admission = loadKnowledge(complete, scope);
            if (Utils.Resources.hasErrors(admission)) {
              response.getNotifications().addAll(admission.getNotifications());
              response.setStatus(SemanticValidationResponse.Status.UNAVAILABLE);
              response.setReason("Saved source could not be admitted to the service worldview");
              response.addDocumentDiagnostic(request, Notification.Level.Warning);
              return response;
            }
          }
        }
      }
      return candidateRuntime.validate(request, response,scope);
    }
    if ((semanticValidationState != null && semanticValidationState.loading())
        || !knowledgeReady
        || owl() == null
        || !owl().isInitialized()
        || scope == null
        || scope.getService(ResourcesService.class) == null) {
      response.setReason(
          "Semantic validation requires loaded knowledge and an available Resources service");
      return response;
    }
    return validateReadyDocument(request, scope, response);
  }

  /** Keep OWL reads and synchronization serialized with knowledge loading. */
  private synchronized SemanticValidationResponse validateReadyDocument(
      SemanticValidationRequest request, Scope scope, SemanticValidationResponse response) {
    var document = request.document();
    // A reload may have begun between preflight and acquiring the ingestion monitor.
    if (!knowledgeReady) {
      response.setReason("Semantic validation requires loaded knowledge");
      return response;
    }
    if (request.getKnowledgeRevision() >= 0
        && request.getKnowledgeRevision() != knowledgeRevision()) {
      response.setStatus(SemanticValidationResponse.Status.STALE_KNOWLEDGE);
      response.setReason(
          "The knowledge revision changed; retry validation against the current revision");
      return response;
    }
    try {
      if (document instanceof KimOntology ontology
          && (response.getSourceHash() == null
              || !Objects.equals(
                  response.getSourceHash(), loadedOntologySources.get(document.getUrn())))) {
        if (!(scope instanceof UserScope userScope)
            || response.getSourceHash() == null
            || !synchronizeValidationOntology(ontology, userScope)) {
          response.setReason(
              "Cannot validate this ontology until its saved source and dependencies are available from Resources");
          return response;
        }
        response.setKnowledgeRevision(knowledgeRevision());
      }
      if (document instanceof KimOntology) {
        // Resources diagnostics already belong to the document in the editor. Replaying its
        // warnings here makes one occurrence look like a second semantic diagnostic. Only cached
        // errors participate in the semantic-validation barrier.
        response
            .getNotifications()
            .addAll(
                loadedOntologyDiagnostics.getOrDefault(document.getUrn(), List.of()).stream()
                    .filter(
                        notification ->
                            notification.getLevel().severity
                                >= Notification.Level.Error.severity)
                    .toList());
        if (Utils.Notifications.hasErrors(response.getNotifications())) {
          response.setStatus(SemanticValidationResponse.Status.COMPLETE);
          return response;
        }
      }
      // A failed in-place update is not an accepted readable snapshot. Ontologies above may
      // synchronize a corrected saved source; unrelated namespaces must not certify partial OWL.
      if (semanticValidationState != null && !semanticValidationState.ready()) {
        response.setReason("Semantic validation requires a completed knowledge update");
        return response;
      }
      var validator=new org.integratedmodelling.klab.services.reasoner.internal.DocumentSemanticValidator(
          this,request.isExecutableModels(),request.isClassifierPreparableDependencies(), request.getExecutionContext(),request.getPreparableMemberQualityDependencies());
      response.getNotifications().addAll(validator.validate(document));
      response.getPendingClassifierDependencies().addAll(validator.getPendingClassifierDependencies());
      response.getPendingMemberQualityDependencies().addAll(validator.getPendingMemberQualityDependencies());
      response.getConditionalModelContexts().addAll(validator.getConditionalModelContexts());
      response.getPendingModelContexts().addAll(validator.getPendingModelContexts());
      response.setSemanticObligations(validator.getSemanticObligations());
      response.setSemanticObligationsComplete(validator.isSemanticObligationsComplete());
      response.setSourceSemanticsComplete(validator.isSourceSemanticsComplete());
      var capabilities=capabilities(scope);
      var dependencyRequest=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest(
          capabilities.getServiceId(),capabilities.getKnowledgeRevision(),capabilities.getSemanticContentFingerprint(),
          validator.getSemanticObligations());
      response.setSemanticContentFingerprint(capabilities.getSemanticContentFingerprint());
      response.setSemanticSessionId(capabilities.getSemanticSessionId());
      try {response.setSemanticWitness(validator.isSemanticObligationsComplete()?semanticDependencies(dependencyRequest,scope)
          :org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.unknown(dependencyRequest,
              "Source contains unaccounted or invalid semantic expressions"));}
      catch(SecurityException denied) {response.setSemanticWitness(
          org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.unknown(dependencyRequest,
              "Authority dependency access is not authorized for this validation scope"));}
      response.setStatus(SemanticValidationResponse.Status.COMPLETE);
    } catch (RuntimeException e) {
      response.setStatus(SemanticValidationResponse.Status.FAILED);
      response.setReason("Semantic validation could not complete: " + e.getMessage());
      response.addDocumentDiagnostic(request, Notification.Level.Error);
    } finally {
      response.setKnowledgeRevision(knowledgeRevision());
      response.deduplicateNotifications();
    }
    return response;
  }

  /** Synchronize only authoritative saved sources, never an ontology supplied in the request. */
  private boolean synchronizeValidationOntology(KimOntology requested, UserScope scope) {
    var resources = scope.getService(ResourcesService.class);
    var saved = resources.retrieve(requested.getUrn(), KimOntology.class, scope);
    var hash = SemanticValidationRequest.sourceHash(requested.getSourceCode());
    if (saved == null
        || !Objects.equals(hash, SemanticValidationRequest.sourceHash(saved.getSourceCode()))
        || Utils.Notifications.hasErrors(saved.getNotifications())) return false;
    var ordered = new LinkedHashMap<String, KimOntology>();
    if (!collectValidationOntologies(saved, resources, scope, ordered, new HashSet<>()))
      return false;
    var changed = new ResourceSet();
    var serviceId = resources.capabilities(scope).getServiceId();
    for (var source : ordered.values()) {
      var sourceHash = SemanticValidationRequest.sourceHash(source.getSourceCode());
      if (!sourceHash.equals(loadedOntologySources.get(source.getUrn()))) {
        changed
            .getOntologies()
            .add(
                new ResourceSet.Resource(
                    serviceId,
                    source.getUrn(),
                    source.getProjectName(),
                    source.getVersion(),
                    KlabAsset.KnowledgeClass.ONTOLOGY,
                    source.getLastUpdateTimestamp(),
                    false));
      }
    }
    if (!changed.getOntologies().isEmpty()) {
      updateKnowledge(changed, scope);
      if (Utils.Notifications.hasErrors(changed.getNotifications()))
        throw new IllegalStateException(changed.getNotifications().getFirst().getMessage());
    }
    return hash.equals(loadedOntologySources.get(requested.getUrn()));
  }

  private boolean collectValidationOntologies(
      KimOntology ontology,
      ResourcesService resources,
      UserScope scope,
      Map<String, KimOntology> ordered,
      Set<String> visiting) {
    if (ordered.containsKey(ontology.getUrn())) return true;
    if (!visiting.add(ontology.getUrn())
        || ontology.getSourceCode() == null
        || (!Utils.URLs.isLocalHost(getUrl())
            && Utils.Notifications.hasErrors(ontology.getNotifications()))) return false;
    for (var dependency : ontology.getImportedOntologies()) {
      var imported = resources.retrieve(dependency, KimOntology.class, scope);
      if (imported == null
          || !collectValidationOntologies(imported, resources, scope, ordered, visiting))
        return false;
    }
    visiting.remove(ontology.getUrn());
    ordered.put(ontology.getUrn(), ontology);
    return true;
  }

  /** Derived closure changes invalidate queries without changing the accepted knowledge revision. */
  public void derivedRefinementsChanged() {
    if (semanticExpressions != null) semanticExpressions.invalidateAll();
    if (concepts != null) concepts.invalidateAll();
    if (observables != null) observables.invalidateAll();
    if (subsumption != null) subsumption.invalidateAll();
    if (semanticMatcher != null) semanticMatcher.resetCaches();
    if (syntacticMatcher != null) syntacticMatcher.resetCaches();
  }

  private void invalidateSemanticCaches() {
    try { if (owl != null) owl.monotoneRefinements().reset(true); }
    finally {
      knowledgeRevision.incrementAndGet();derivedRefinementsChanged();conceptSyntax.clear();
    }
  }

  private void authorityKnowledgeChanged() {
    boolean retracted=false;
    try {
      if (owl != null) owl.monotoneRefinements().reset(false);
      retracted=true;
    } finally {
      knowledgeRevision.incrementAndGet();derivedRefinementsChanged();conceptSyntax.clear();
      if(retracted) semanticValidationState.enriched(knowledgeRevision());
      else {
        knowledgeReady=false;
        semanticValidationState.settle(knowledgeRevision(),false);
      }
    }
  }

  private Concept nothingConcept(String urn) {
    if (this.owl != null) {
      return this.owl.nothing(urn);
    }
    var ret = new ConceptImpl();
    ret.setNonSemanticId(ConceptImpl.NOTHING_ID);
    ret.setUrn("owl:Nothing");
    ret.setNamespace("owl");
    ret.getType().add(SemanticType.NOTHING);
    return ret;
  }

  /**
   * An emergence is the appearance of an observation triggered by another, under the assumptions
   * stated in the worldview. It applies to processes and relationships and its emergent observable
   * can be a configuration, subject or process.
   *
   * @author Ferd
   */
  public class Emergence {

    public Set<Concept> triggerObservables = new LinkedHashSet<>();
    public Concept emergentObservable;
    public String namespaceId;

    public Set<Observation> matches(Concept relationship, ContextScope scope) {

      for (Concept trigger : triggerObservables) {
        Set<Observation> ret = new HashSet<>();
        checkScope(trigger, makeObservationCatalog(scope), relationship, ret);
        if (!ret.isEmpty()) {
          return ret;
        }
      }

      return Collections.emptySet();
    }

    private Map<Observable, Observation> makeObservationCatalog(ContextScope scope) {
      Map<Observable, Observation> ret = new HashMap<>();
      //      for (var observation : scope.query(Observation.class)) {
      //        ret.put(observation.getObservable(), observation);
      //      }
      return ret;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + getEnclosingInstance().hashCode();
      result = prime * result + Objects.hash(emergentObservable, namespaceId, triggerObservables);
      return result;
    }

    private Object getEnclosingInstance() {
      return ReasonerService.this;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      Emergence other = (Emergence) obj;
      if (!getEnclosingInstance().equals(other.getEnclosingInstance())) return false;
      return Objects.equals(emergentObservable, other.emergentObservable)
          && Objects.equals(namespaceId, other.namespaceId)
          && Objects.equals(triggerObservables, other.triggerObservables);
    }

    /*
     * current observable must be one of the triggers, any others need to be in
     * scope
     */
    private void checkScope(
        Concept trigger,
        Map<Observable, Observation> map,
        Concept relationship,
        Set<Observation> obs) {
      if (trigger.is(SemanticType.UNION)) {
        for (Concept trig : operands(trigger)) {
          checkScope(trig, map, relationship, obs);
        }
      } else if (trigger.is(SemanticType.INTERSECTION)) {
        for (Concept trig : operands(trigger)) {
          Set<Observation> oobs = new HashSet<>();
          checkScope(trig, map, relationship, oobs);
          if (oobs.isEmpty()) {
            obs = oobs;
          }
        }
      } else {
        Observation a = map.get(trigger);
        if (a != null) {
          obs.add(a);
        }
      }
    }
  }

  @Autowired
  public ReasonerService(ServiceScope scope, ServiceStartupOptions options) {
    super(scope, Type.REASONER, options);
    this.semanticOnly = false;
    this.authorityBindings = new org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings(
        BaseService.getConfigurationDirectory(options).toPath().resolve("authority-cache"));
    this.owl = new OWL(scope);
    initializeAuthorityIdentityResolver();
    this.indexer = new Indexer(scope);
    this.emergence = new IntelligentMap<>(scope);
    readConfiguration(options);
    setComponentRegistry();
    getComponentRegistry()
        .initializeComponents(getConfigurationSubdirectory(options, "components"));
    ServiceConfiguration.INSTANCE.setMainService(this);
  }

  void initializeAuthorityIdentityResolver() {
    this.authorityIdentityResolver = new org.integratedmodelling.klab.services.reasoner.internal.AuthorityIdentityResolver(
        this.owl, authorityBindings, this::authorityKnowledgeChanged);
    this.owl.setAuthorityResolver(authorityIdentityResolver::resolve);
  }

  /** Private compiler actor: owns semantic state, never a deployment or registered main service. */
  public ReasonerService(
      org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentScope scope,
      java.nio.file.Path privateKnowledge, java.nio.file.Path privateState) {
    this(scope, privateKnowledge, privateState,
        org.integratedmodelling.klab.services.reasoner.internal.CapturedAuthorityCatalog.empty());
  }
  public ReasonerService(
      org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentScope scope,
      java.nio.file.Path privateKnowledge, java.nio.file.Path privateState,
      org.integratedmodelling.klab.services.reasoner.internal.CapturedAuthorityCatalog authorities) {
    this(scope, privateKnowledge, privateState, authorities, false);
  }
  public ReasonerService(
      org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentScope scope,
      java.nio.file.Path privateKnowledge, java.nio.file.Path privateState,
      org.integratedmodelling.klab.services.reasoner.internal.CapturedAuthorityCatalog authorities,
      boolean authorityBootstrapActor) {
    super(scope, Type.REASONER,
        new org.integratedmodelling.klab.services.reasoner.internal.SemanticExecutionSettings(),
        semanticDisplayUrl());
    this.semanticOnly = true;
    this.authorityBootstrapActor = authorityBootstrapActor;
    this.capturedAuthorities = Objects.requireNonNull(authorities); this.semanticAssets = privateState;
    OWL ownedOwl = null;
    try {
    this.authorityBindings = new org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings(
        privateState.resolve("authority-cache"));
    this.owl = ownedOwl = new OWL(scope, privateKnowledge.toFile());
    this.authorityIdentityResolver = new org.integratedmodelling.klab.services.reasoner.internal.AuthorityIdentityResolver(
        this.owl, authorityBindings, this::invalidateSemanticCaches);
    this.owl.setAuthorityResolver((authority, code) -> {
      // OWL consults this callback for ordinary namespaces too. Their absence is local,
      // while an authority lookup must never fall back to a deployment provider.
      if (!Character.isUpperCase(authority.charAt(0)) && authorityBindings.find(authority) == null
          && authorityBindings.alias(authority, code) == null) return null;
      if (authorityBindings.find(authority) == null && authorityBindings.alias(authority, code) == null)
        throw new UnsupportedOperationException("CANDIDATE_ISOLATION_UNSUPPORTED: authority binding is not captured: " + authority);
      return authorityIdentityResolver.resolve(authority, code);
    });
    this.indexer = new Indexer(scope);
    this.emergence = new IntelligentMap<>(scope);
    this.configuration.setServiceId(UUID.randomUUID().toString());
      if (authorityBootstrapActor) {
        startupOptions.setUpdateComponents(false); startupOptions.setAutoUpdateComponents(false);
        try { setOwnedBootstrapRegistry(privateState.resolve("components")); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Cannot create owned provider registry", failure); }
      }
      scope.bind(this);
      // This overload owns its matcher/strategies without loading deployment extensions.
      this.observationReasoner = new ObservationReasoner((Reasoner)this, this::declareObservable);
      this.syntacticMatcher = new SyntacticMatcher(this);
      this.semanticMatcher = new SemanticMatcher(this);
    } catch (RuntimeException | Error failure) {
      try { if (indexer != null) indexer.ensureClosed(); }
      catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
      try { if (ownedOwl != null) ownedOwl.reset(); }
      catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
      finally { scope.close(); closeSemanticExecutor(); }
      throw failure;
    }
  }

  private static java.net.URL semanticDisplayUrl() {
    try { return java.net.URI.create("http://127.0.0.1/candidate").toURL(); }
    catch (java.net.MalformedURLException e) { throw new IllegalStateException(e); }
  }

  public Map<String, Long> successfulAuthorityConfigurations() { return authorityBindings.codelistRevisions(); }
  public Map<String, org.integratedmodelling.klab.api.services.ConfiguredAuthoritySnapshot> configuredAuthoritySnapshots() { return authorityBindings.configuredSnapshots(); }

  public void disposeSemanticEnvironment() {
    if (!semanticOnly) throw new IllegalStateException("Not a private semantic environment");
    try {
      if (authorityBootstrapActor && getComponentRegistry() != null) {
        try {
          for (var binding : bootstrapDisposalBindings) {
            var provider = getComponentRegistry().getAuthority(binding, serviceScope());
            if (provider != null) org.integratedmodelling.klab.services.reasoner.internal.CapturedAuthorityCatalog.closeOwnedProvider(provider);
          }
        } finally { getComponentRegistry().closeOwnedBootstrap(); }
      }
      authorityBindings.clear();
    }
    finally {
    try { owl.reset(); }
    finally {
      try { indexer.ensureClosed(); }
      finally { serviceScope().close(); closeSemanticExecutor(); }
    }
    }
  }

  private void readConfiguration(ServiceStartupOptions options) {
    File config = BaseService.getFileInConfigurationDirectory(options, "reasoner.yaml");
    if (config.exists() && config.length() > 0 && !options.isClean()) {
      try {
        this.configuration =
            org.integratedmodelling.common.utils.Utils.YAML.load(
                config, ReasonerConfiguration.class);
      } catch (Exception e) {
        Logging.INSTANCE.warn("Configuration file is being reset after corruption was detected");
        Utils.Files.deleteQuietly(config);
        this.configuration = new ReasonerConfiguration();
        this.configuration.setServiceId(UUID.randomUUID().toString());
        Utils.YAML.save(this.configuration, config);
      }
    } else {
      // make an empty config
      this.configuration = new ReasonerConfiguration();
      this.configuration.setServiceId(UUID.randomUUID().toString());
      saveConfiguration();
    }
    // for the local client to know when the service is off
    super.setRuntimeLockfile(this.configuration.getServiceId());
  }

  @Override
  public boolean initializeService() {

    if (semanticOnly) return true;

    Logging.INSTANCE.setSystemIdentifier("Reasoner service: ");
    for (ProjectConfiguration authority : configuration.getAuthorities()) {
      loadAuthority(authority);
    }

    this.observationReasoner = new ObservationReasoner(this);
    this.syntacticMatcher = new SyntacticMatcher(this);
    this.semanticMatcher = new SemanticMatcher(this);

    return true;
  }

  @Override
  public void runAdditionalTimedTasks() {
    try {
      checkWorldview();
      if (candidateRuntime != null && !semanticOnly) {
        var status = candidateRuntime.status();
        switch (status.logicalStatus()) {
          case FAILED, UNAVAILABLE, INVALID, UNRESOLVED, NOT_RUN -> {
            if (status.diagnostics().isEmpty()) break;
            var diagnostic = "Worldview semantic validation " + status.logicalStatus() + ": "
                + String.join("; ", status.diagnostics());
            if (!diagnostic.equals(lastCandidateDiagnostic)) {
              lastCandidateDiagnostic = diagnostic;
              Logging.INSTANCE.warn(diagnostic);
            }
          }
          case VALID -> lastCandidateDiagnostic = null;
          default -> { }
        }
      }
    } catch (Throwable t) {
      Logging.INSTANCE.error(t);
    }
  }

  private String lastCandidateDiagnostic;

  /**
   * TODO this must become more dynamic and integrate existing worldview contributors. Priority must
   * be given to local sourcing (already built into ResourceSet but local may come in later).
   *
   * @return
   */
  boolean checkWorldview() {

    boolean candidatePublication = candidateRuntime != null && !semanticOnly;
    if (knowledgeReady && !candidatePublication) {
      return true;
    }

    var ret = candidatePublication && candidateRuntime.status().acceptedGeneration() > 0;
    if (candidatePublication) setOperational(ret);
    // Prefer local authoring sources, while allowing certificate-advertised remote sources
    // when no local worldview is available. Never replace an unchanged local selection remotely.
    var sources = serviceScope().getServices(ResourcesService.class).stream()
        .sorted(Comparator.comparing(resources -> !Utils.URLs.isLocalHost(resources.getUrl())))
        .toList();
    for (var resources : sources) {

      if (resources.status().isAvailable()
          && resources.capabilities(serviceScope()).isWorldviewProvider()) {

        // FIXME switch to using the full service list and use the updated worldview retrieval
        // logic.
        // FIXME set up a worldview update check at a configurable interval in timedTasks

        // The service scheduler retries every five seconds. Sleeping/retrying here blocks its
        // status and initialization work without making an unavailable worldview arrive sooner.
        // Discovery is public during startup, before a user scope exists.
        long discoveryStarted = System.nanoTime();
        Worldview discovered = resources.list(Worldview.class, null).stream().findFirst().orElse(null);
        if (discovered == null) continue;
        if (candidateRuntime != null && !semanticOnly
            && !candidateRuntime.shouldRetryPreflight(discovered, serviceScope())) return ret;
        serviceScope().info("Worldview discovered from " + resources.getUrl() + " in "
            + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - discoveryStarted) + " ms");
        if (org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.enabled()) org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.emit(
            org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Event.DISCOVERY,
            candidateRuntime == null ? -1 : candidateRuntime.status().inputGeneration(), -1, -1,
            org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.enabled()
                ? org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.digest(resources.serviceId()) : null,
            null, org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Reason.SERVICE);
        var notifications = loadKnowledge(discovered, serviceScope());
        if (candidatePublication) {
          // A submitted candidate compiles asynchronously. Keep this source selected while
          // pending, and poll it again for source/provider changes on the next scheduler tick.
          ret = candidateRuntime.status().acceptedGeneration() > 0;
          setOperational(ret);
          break;
        }
        if (knowledgeReady
            && (Utils.URLs.isLocalHost(getUrl()) || !Utils.Resources.hasErrors(notifications))) {
          setOperational(true);
          ret = true;
          serviceScope()
              .info(
                  Utils.Resources.hasErrors(notifications)
                      ? "Worldview loaded for local editing; validation errors remain"
                      : "Worldview loaded into local reasoner");

          // TODO if there were previous logical notifications they should be deleted now

          /*
          We stop at the first worldview that loads. All available worldiews should be
          synchronized and mirrored automatically, and two services with different worldviews
          accessible to the same reasoner is a configuration abomination that should never happen.
           */
          break;
        }
      }
    }
    return ret;
  }

  @Override
  public boolean operationalizeService() {
    if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.status().acceptedGeneration() > 0;
    return knowledgeReady;
  }

  @SuppressWarnings("unchecked")
  private void loadAuthority(ProjectConfiguration authority) {
    if (authority.getUrl().startsWith("classpath:")) {
      try {
        Logging.INSTANCE.info(
            "loading authority " + authority.getProject() + " from local " + "classpath");
        Class<? extends Authority> cls =
            (Class<? extends Authority>)
                Class.forName(authority.getUrl().substring(("classpath" + ":").length()));
        ServiceConfiguration.INSTANCE.registerAuthority(cls.getDeclaredConstructor().newInstance());
        Logging.INSTANCE.info(
            "Authority "
                + authority.getProject()
                + " ready for "
                + (authority.isServe() ? "global" : "local")
                + " use");
      } catch (Exception e) {
        Logging.INSTANCE.error(e);
      }
    }
  }

  private void saveConfiguration() {
    File config = BaseService.getFileInConfigurationDirectory(startupOptions, "reasoner.yaml");
    org.integratedmodelling.common.utils.Utils.YAML.save(this.configuration, config);
  }

  @Override
  public Concept defineConcept(KimConceptStatement statement, Scope scope) {
    if (candidateRuntime != null && !semanticOnly)
      throw new IllegalStateException("Source declarations must enter through whole-worldview bean preflight and candidate publication");
    return build(
        statement,
        this.owl.requireOntology(statement.getNamespace(), OWL.DEFAULT_ONTOLOGY_PREFIX),
        null,
        scope);
  }

  @Override
  public ServiceStatus status() {
    var ret = super.status();
    if (candidateRuntime != null && !semanticOnly) {
      var status = candidateRuntime.status();
      ret.getMetadata().put(org.integratedmodelling.klab.services.reasoner.internal.SemanticValidationState.METADATA_KEY,
          "accepted:" + status.acceptedGeneration() + (status.acceptedGeneration() > 0 ? ":ready" : ":unavailable"));
      ret.getMetadata().put("semantic.candidate", status);
      ((org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl) ret)
          .setOperational(status.acceptedGeneration() > 0);
      ret.getMetadata().put("semantic.validation.reason", AcceptedSemanticRuntime.validationReason(status));
      ret.getMetadata().put("semantic.validation.blocked", AcceptedSemanticRuntime.validationBlocked(status));
      status.diagnostics().forEach(message -> ret.getAdvisories().add(Notification.warning(message)));
      return ret;
    }
    ret.getMetadata().put(
        org.integratedmodelling.klab.services.reasoner.internal.SemanticValidationState.METADATA_KEY,
        semanticValidationState.signal());
    ret.getMetadata().put("authority.codelist.revisions", authorityBindings.codelistRevisions());
    ret.getAdvisories().addAll(this.advisories);
    ret.getAdvisories().addAll(this.worldviewLoadDiagnostics);
    if (!this.consistent.get()) {
      ret.getAdvisories().add(Notification.error("Reasoner knowledge base is inconsistent"));
    }
    return ret;
  }

  @Override
  public Concept resolveConcept(String definition) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("resolveConcept", new Class<?>[] { String.class }, new Object[] { definition });
  Objects.requireNonNull(definition, "definition");
    var result = concepts.get(definition, this::resolveConceptInternal);
    if (result != null && result.is(SemanticType.NOTHING)) concepts.invalidate(definition);
    return result;
  }
  /** The alternative HTTP builder path must parse under the same accepted lease as its build. */
  public Concept resolveConceptWithBuilder(String definition, Scope scope) {
    if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("resolveConceptWithBuilder", new Class<?>[]{String.class, Scope.class}, new Object[]{definition, scope});
    var syntax = (semanticOnly ? serviceScope() : scope).getService(ResourcesService.class).declareConcept(definition);
    return syntax == null ? resolveConcept(definition)
        : org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder.create(
            syntax, this, semanticOnly ? serviceScope() : scope).buildConcept();
  }

  @Override
  public Observable resolveObservable(String definition) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("resolveObservable", new Class<?>[] { String.class }, new Object[] { definition });
  Objects.requireNonNull(definition, "definition");
    var result = observables.get(definition, this::resolveObservableInternal);
    if (result != null && result.is(SemanticType.NOTHING)) observables.invalidate(definition);
    return result;
  }

  public Concept resolveConceptInternal(String definition) {
    if (candidateRuntime != null && !semanticOnly) return candidateRuntime.invoke("resolveConceptInternal", new Class<?>[]{String.class}, new Object[]{definition});
    Concept ret = null;
    if (Urn.isAtomicConcept(definition)) {
      ret = owl.getConcept(definition);
    } else {
      // FIXME this should use all services and resolve in user scope
      KimConcept parsed =
          serviceScope().getService(ResourcesService.class).declareConcept(definition);
      if (parsed != null) {
        ret = declareConcept(parsed);
      }
    }
    return ret == null ? owl.nothing(definition)
        : Urn.isAtomicConcept(definition) ? identityRequirements().project((ConceptImpl) ret) : ret;
  }

  /** Requirements change expression eligibility; canonical declarations retain explicit flags. */
  public ConceptImpl projectExpressionStatus(ConceptImpl concept, KimConcept syntax) {
    return identityRequirements().project(concept, syntax);
  }

  private org.integratedmodelling.klab.services.reasoner.owl.IdentityRequirementSupport identityRequirements() {
    return new org.integratedmodelling.klab.services.reasoner.owl.IdentityRequirementSupport(owl, this::identityEligible);
  }

  /** Retain existing captured bridge restrictions without triggering provider lookup. */
  public boolean identityEligible(Concept root, Concept identity) {
    var hierarchy = new org.integratedmodelling.klab.services.reasoner.owl.IdentityRequirementSupport(owl);
    if (worldview != null) for (var declared : worldview.getAuthorityBindings()) {
      var bridgeRoot = owl.getConcept(declared.rootIdentity());
      if (bridgeRoot != null && hierarchy.proves(root, bridgeRoot)
          && !authorityIdentityResolver.acceptedFor(identity, authorityBindings.get(declared.localId())))
        return false;
    }
    return !identity.is(SemanticType.AUTHORITY_IDENTITY)
        || authorityBindings.snapshot().stream()
            .anyMatch(binding -> authorityIdentityResolver.acceptedFor(identity, binding));
  }

  public Observable resolveObservableInternal(String definition) {
    if (candidateRuntime != null && !semanticOnly) return candidateRuntime.invoke("resolveObservableInternal", new Class<?>[]{String.class}, new Object[]{definition});
    Observable ret = null;
    if(definition.contains(" observed as ")) {
      var restored=org.integratedmodelling.klab.services.reasoner.owl.ObserverOntologySupport.restore(
          definition,owl.getOntologies(true).stream().map(Ontology::getOWLOntology).toList(),
          this::resolveObservableInternal,this::resolveConcept);
      if(restored!=null)return restored;
    }
    // FIXME the service should be passed
    KimObservable parsed =
        serviceScope().getService(ResourcesService.class).declareObservable(definition);
    if (parsed != null) {
      ret = declareObservable(parsed);
    }
    return ret == null ? Observable.nothing("owl:Nothing") : ret;
  }

  @Override
  public Collection<Concept> operands(Semantics target) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("operands", new Class<?>[] { Semantics.class }, new Object[] { target });
  List<Concept> ret = new ArrayList<>();
    if (target.is(SemanticType.UNION) || target.is(SemanticType.INTERSECTION)) {
      ret.addAll(this.owl.getOperands(target.asConcept()));
    } else {
      ret.add(target.asConcept());
    }

    return projectHierarchy(ret);
  }

  @Override
  public Collection<Concept> children(Semantics target) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("children", new Class<?>[] { Semantics.class }, new Object[] { target });
  return projectHierarchy(this.owl.getChildren(target.asConcept()));
  }

  public Map<Concept, Collection<Observation>> emergentResolvables(
      Observation trigger, ContextScope scope) {
    if (candidateRuntime != null && !semanticOnly) return candidateRuntime.invoke("emergentResolvables", new Class<?>[]{Observation.class, ContextScope.class}, new Object[]{trigger, scope});

    Map<Concept, Collection<Observation>> ret = new HashMap<>();
    Collection<Emergence> emergents = this.emergence.get(trigger.getObservable().getSemantics());

    // if (!(scope instanceof IRuntimeScope) || ((IRuntimeScope)
    // scope).getActuator() == null) {
    // return Collections.emptyMap();
    // }
    //
    // Mode mode = ((IRuntimeScope) scope).getActuator().getMode();
    //
    // /*
    // * Skip a search in the map if we can't trigger anything.
    // */
    // if (!trigger.getObservable().is(Type.QUALITY)
    // && !(trigger.getObservable().is(Type.RELATIONSHIP) && mode ==
    // Mode.INSTANTIATION)) {
    // return Collections.emptyMap();
    // }
    //
    // Map<IConcept, Collection<IObservation>> ret = new HashMap<>();
    // Collection<Emergence> emergents =
    // this.emergence.get(trigger.getObservable().getType());
    //
    // if (emergents != null) {
    //
    // for (Emergence emergent : emergents) {
    //
    // Collection<IObservation> match =
    // emergent.matches(trigger.getObservable().getType(),
    // (IRuntimeScope) scope);
    //
    // /*
    // * if process or configuration, update and skip if the scope already contains
    // * the emergent observation
    // */
    // if (emergent.emergentObservable.is(Type.PROCESS)
    // || emergent.emergentObservable.is(Type.CONFIGURATION)) {
    // if (((IRuntimeScope) scope).getCatalog()
    // .get(new ObservedConcept(emergent.emergentObservable)) != null) {
    // /*
    // * TODO update with the new observation(s)! API to be defined
    // */
    // if (((IDirectObservation) trigger).getOriginatingPattern() != null) {
    // ((IDirectObservation) trigger).getOriginatingPattern().update(trigger);
    // return ret;
    // }
    // }
    // }
    //
    // ret.put(emergent.emergentObservable, match);
    // }
    // }
    return ret;
  }

  @Override
  public Collection<Concept> parents(Semantics target) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("parents", new Class<?>[] { Semantics.class }, new Object[] { target });
  if (authorityIdentityResolver != null) authorityIdentityResolver.ensureHierarchy(target.asConcept());
    return projectHierarchy(this.owl.getParents(target.asConcept()));
  }

  @Override
  public Collection<Concept> allChildren(Semantics target) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("allChildren", new Class<?>[] { Semantics.class }, new Object[] { target });
  Set<Concept> ret = collectChildren(target, new HashSet<>());
    ret.remove(target.asConcept());
    return ret;
  }

  private Set<Concept> collectChildren(Semantics target, Set<Concept> hashSet) {

    for (Concept c : children(target)) {
      if (hashSet.add(c)) {
        collectChildren(c, hashSet);
      }
    }
    return hashSet;
  }

  @Override
  public Collection<Concept> allParents(Semantics target) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("allParents", new Class<?>[] { Semantics.class }, new Object[] { target });
  return allParentsInternal(target, new HashSet<Concept>());
  }

  private Collection<Concept> allParentsInternal(Semantics target, Set<Concept> seen) {

    Set<Concept> concepts = new HashSet<>();

    if (seen.contains(target.asConcept())) {
      return concepts;
    }

    seen.add(target.asConcept());

    for (Concept c : parents(target)) {
      concepts.add(c);
      concepts.addAll(allParentsInternal(c, seen));
    }

    return concepts;
  }

  @Override
  public Collection<Concept> closure(Semantics target) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("closure", new Class<?>[] { Semantics.class }, new Object[] { target });
  return projectHierarchy(this.owl.getSemanticClosure(target.asConcept()));
  }

  @Override
  public Collection<Concept> authoredClosure(Semantics target) {
    if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("authoredClosure", new Class<?>[] { Semantics.class }, new Object[] { target });
    var source = worldview;
    long revision = knowledgeRevision();
    var vocabulary = authoredVocabulary;
    if (vocabulary == null || !vocabulary.belongsTo(source, revision)) {
      vocabulary = org.integratedmodelling.klab.services.reasoner.internal.AuthoredVocabulary.capture(
          source, revision, owl::getConcept);
      authoredVocabulary = vocabulary;
    }
    var membership = vocabulary;
    return projectHierarchy(owl.getSemanticClosure(target.asConcept()).stream()
        .filter(membership::contains)
        .sorted(java.util.Comparator.comparing(Concept::getUrn)).toList());
  }

  private Collection<Concept> projectHierarchy(Collection<Concept> concepts) {
    return concepts.stream().map(this::effectiveConcept).toList();
  }

  /** Public semantic traversals expose effective eligibility, not raw declaration flags. */
  private Concept effectiveConcept(Concept concept) {
    if (concept == null || !(concept.is(SemanticType.OBSERVABLE)
        || concept.is(SemanticType.PREDICATE))) return concept;
    if (!Urn.isAtomicConcept(concept.getUrn())) {
      // Preserve the separately supplied identity witnesses in qualified expressions;
      // the OWL hasIdentity union alone cannot reconstruct their authored scope.
      try {
        var syntax = serviceScope().getService(ResourcesService.class).declareConcept(concept.getUrn());
        if (syntax != null) return projectExpressionStatus((ConceptImpl) concept, syntax);
      } catch (RuntimeException unavailable) {
        // Without captured syntax, use conservative evidence from the semantic graph.
      }
      if (concept.is(SemanticType.PREDICATE)) {
        // Raw derived OWL classes do not retain authored head abstractness. Never
        // advertise an unprojectable predicate as an admissible concrete result.
        var unavailable = new ConceptImpl((ConceptImpl) concept);
        unavailable.setAbstract(true);
        return unavailable;
      }
    }
    return identityRequirements().project((ConceptImpl) concept);
  }

  @Override
  public Concept baseSubstantialType(Semantics concept, Scope scope) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("baseSubstantialType", new Class<?>[] { Semantics.class, Scope.class }, new Object[] { concept, scope });
  Concept original = concept == null ? null : concept.asConcept();
    if (original == null) {
      return nothingConcept("null substantial concept");
    }

    if (!SemanticType.isEnumerableSubstantial(original.getType())) {
      return nothingConcept(original.getUrn());
    }

    try {
      var builder =
          SemanticsBuilder.create(original, this, scope == null ? serviceScope() : scope)
              .without(SemanticRole.TRAIT)
              .without(SemanticRole.ROLE)
              .without(SemanticRole.modifiers());
      // Identity predicates do not partition cohorts. An authored substantial subclass does;
      // native discovery clauses and execution eligibility remain separate from this category.
      Concept ret = builder.buildConcept();
      return ensureCountableSubstantial(
          ret == null || ret.is(SemanticType.NOTHING) ? original : ret);
    } catch (RuntimeException t) {
      Logging.INSTANCE.warn(
          "Could not establish base substantial type for " + original.getUrn() + ": " + t);
      return ensureCountableSubstantial(original);
    }
  }

  private Concept ensureCountableSubstantial(Concept concept) {
    if (concept != null
        && SemanticType.isEnumerableSubstantial(concept.getType())
        && !concept.is(SemanticType.COUNTABLE)) {
      Logging.INSTANCE.warn(
          "Restoring missing COUNTABLE semantic type on substantial {}", concept.getUrn());
      concept.getType().add(SemanticType.COUNTABLE);
    }
    return concept;
  }

  @Override
  public boolean resolves(Semantics toResolve, Semantics other, Semantics context) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("resolves", new Class<?>[] { Semantics.class, Semantics.class, Semantics.class }, new Object[] { toResolve, other, context });

    /*
    TODO if these are observables, the observer also must be considered and matched by semantic distance.

    TODO the observable now just carries an Observer identity but NOT a contract about the type of
     observer it
    accepts. This is necessary for models to be able to declare their observers before they are actually
     observed.

          In each observable, the actual Observer (if semantic) takes over the contract when matching. This,
          with the
          fact that the observer may be a mere Identity, makes for a pretty complicated comparison.

    The observer instance is an Identity - if that's also a DirectObservation use semantics, otherwise
    match with
    equals().

     An incoming without observer will match one with, but not the other way around unless the observers
      are
     compatible.
     */

    return semanticDistance(other, toResolve, context) >= 0;
  }

  /*
   * TODO this is used in resolution and can be work, so it should cache
   *
   * FIXME concrete observables must remain the same; inherent and clauses should be generalized to all parents
   * FIXME value operators should be all added back as they are
   *
   * @param semantics
   * @return
   */
  @Override
  public Collection<Concept> resolving(Semantics semantics) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("resolving", new Class<?>[] { Semantics.class }, new Object[] { semantics });

    var concept = semantics.asConcept();
    Set<Concept> ret = new LinkedHashSet<>();

    ret.add(concept);

    var base =
        SemanticsBuilder.create(concept.asConcept(), this, serviceScope())
            .without(SemanticRole.INHERENT)
            .without(SemanticRole.modifiers())
            .buildConcept();

    var inherent = directInherent(semantics);
    if (base.is(SemanticType.PREDICATE)) {
      var heads = new LinkedHashSet<Concept>();
      heads.add(base);
      heads.addAll(allParents(base));
      var bearers = new LinkedHashSet<Concept>();
      if (inherent != null) {
        bearers.add(inherent.singular());
        bearers.addAll(allParents(inherent.singular()));
      }
      for (var head : heads) {
        if (!head.is(SemanticType.PREDICATE) || CoreOntology.isCore(head)) continue;
        if (inherent == null) ret.add(head);
        else
          for (var bearer : bearers) {
            // Worldview aliases such as imod:Quality resolve to foundational observables
            // (odo:Quality). Keep these bearers so generic predicate models remain discoverable,
            // but omit structural OWL ancestors that have no observable semantics.
            if (bearer.is(SemanticType.NOTHING)
                || CoreOntology.isCore(bearer) && !bearer.is(SemanticType.OBSERVABLE)) continue;
            try {
              var candidate =
                  SemanticsBuilder.create(head, this, serviceScope())
                      .of(inherent.isCollective() ? bearer.collective() : bearer)
                      .buildConcept();
              if (candidate != null && !candidate.is(SemanticType.NOTHING)) ret.add(candidate);
            } catch (KlabValidationException incompatibleGeneralization) {
              // A valid predicate application need not remain valid for every ancestor of
              // its bearer (e.g. an applies-to constraint). Reject only that candidate.
            }
          }
      }
      return ret;
    }

    if (inherent != null) {
      ret.addAll(
          allParents(inherent.singular()).stream()
              .map(
                  ctx ->
                      SemanticsBuilder.create(base, this, serviceScope())
                          .of(inherent.isCollective() ? ctx.collective() : ctx)
                          .buildConcept())
              .toList());
    }

    // TODO do the same with the clauses
    for (var role : SemanticClause.values()) {}

    return ret;
  }

  @Override
  public int semanticDistance(Semantics target, Semantics other) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("semanticDistance", new Class<?>[] { Semantics.class, Semantics.class }, new Object[] { target, other });
    if (owl != null) owl.requireAvailableClosure();
  return semanticDistance(target, other, null);
  }

  @Override
  public int semanticDistance(Semantics target, Semantics other, Semantics context) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("semanticDistance", new Class<?>[] { Semantics.class, Semantics.class, Semantics.class }, new Object[] { target, other, context });
    if (owl != null) owl.requireAvailableClosure();
  if (!eligibleDistanceRequest(other)) return -10000;
    int perspectiveDistance=observerDistance(target,other);
    if(perspectiveDistance<0)return perspectiveDistance;
  int distance = semanticMatcher.semanticDistance(target, other, context);
    if (distance < 0) {
      return distance;
    }
    int observableDistance = observableDistance(target, other);
    return observableDistance < 0 ? observableDistance : distance + perspectiveDistance + observableDistance;
  }

  /** Matching must use the same accepted generation that validated model dependencies. */
  @Override
  public int semanticDistance(Semantics target, Semantics other, Semantics context,
      long expectedKnowledgeRevision) {
    if (expectedKnowledgeRevision < 0) return -10000;
    if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.semanticDistanceAtRevision(target, other, context, expectedKnowledgeRevision);
    if (owl != null) owl.requireAvailableClosure();
    synchronized (this) {
      if (!knowledgeReady || expectedKnowledgeRevision != knowledgeRevision()) return -10000;
      return semanticDistance(target, other, context);
    }
  }

  /** Observer hierarchy comparisons obey the same validation receipt as model matching. */
  public int assertedDistanceAtRevision(Concept target, Concept requested, long expectedKnowledgeRevision) {
    if (expectedKnowledgeRevision < 0) return -10000;
    if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.assertedDistanceAtRevision(target, requested, expectedKnowledgeRevision);
    if (owl != null) owl.requireAvailableClosure();
    synchronized (this) {
      return knowledgeReady && expectedKnowledgeRevision == knowledgeRevision()
          ? assertedDistance(target, requested) : -10000;
    }
  }

  /** Category comparisons are structural; concrete observation requests need completed eligibility. */
  private boolean eligibleDistanceRequest(Semantics request) {
    if (request == null) return false;
    try {
      // Recheck canonical concept status; full Observable wrappers additionally retain
      // observer clauses and unresolved generic components. Never trust caller type flags.
      var canonical = resolveConcept(request.asConcept().getUrn());
      if (canonical == null || canonical.is(SemanticType.NOTHING) || !satisfiable(canonical)) return false;
      if (canonical.isGeneric() || canonical.is(SemanticType.ANY)
          || canonical.is(SemanticType.ALL) || canonical.is(SemanticType.NONE)) return false;
      if (canonical.is(SemanticType.PREDICATE)) return true;
      if (!SemanticType.isDependent(canonical.getType())
          && !SemanticType.isEnumerableSubstantial(canonical.getType())) return true;
      return !canonical.isAbstract() && !canonical.isGeneric()
          && (!(request instanceof Observable observable) || observable.getGenericComponents().isEmpty());
    } catch (RuntimeException unavailable) { return false; }
  }

  private int observerDistance(Semantics candidate,Semantics requested) {
    Concept statedCandidate=candidate instanceof Observable o?o.getObserverSemantics():null;
    Concept statedQuery=requested instanceof Observable o?o.getObserverSemantics():null;
    Concept candidateObserver=canonicalObserver(statedCandidate),queryObserver=canonicalObserver(statedQuery);
    if(statedCandidate!=null&&candidateObserver==null || statedQuery!=null&&queryObserver==null)return -50;
    if(statedQuery==null)return 0; // Context-selected perspective is a ranking preference elsewhere.
    if(candidateObserver==null)return -50; // Explicit query cannot fall back to absolute semantics.
    return semanticMatcher.observerClauseDistance(candidateObserver,queryObserver);
  }
  private Concept canonicalObserver(Concept stated) {
    if(stated==null)return null;
    var canonical=resolveConcept(stated.getUrn());
    return canonical!=null&&canonical.is(SemanticType.AGENT)&&!canonical.is(SemanticType.NOTHING)?canonical:null;
  }

  private int observableDistance(Semantics target, Semantics other) {
    if (!(target instanceof Observable targetObservable)
        || !(other instanceof Observable otherObservable)) {
      return 0;
    }
    int distance = 0;
    if (targetObservable.getContextualization() != null) {
      if (otherObservable.getContextualization() == null
          || !otherObservable.is(targetObservable.getContextualization())) {
        return -50;
      }
    }

    var targetMediator = targetObservable.mediator();
    var otherMediator = otherObservable.mediator();
    // An omitted request mediator places no restriction on a model's native value scale.
    // In particular, recursive quality requests may omit units supplied by their models.
    if (targetMediator != null && otherMediator != null) {
      if (!targetMediator.isCompatible(otherMediator)) {
        return -50;
      }
      if (!targetMediator.equals(otherMediator)) {
        distance++;
      }
    } else if (targetMediator == null && otherMediator != null) {
      distance++;
    }
    return distance;
  }

  @Override
  public Concept coreObservable(Semantics first) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("coreObservable", new Class<?>[] { Semantics.class }, new Object[] { first });
  Concept ret = first.asConcept();
    Set<String> visited = new HashSet<>();
    while (ret != null && visited.add(ret.getUrn())) {
      String next = ret.getMetadata().get(NS.CORE_OBSERVABLE_PROPERTY, String.class);
      if (next == null) {
        return effectiveConcept(ret);
      }
      ret = resolveConcept(next);
    }
    return ret == null ? nothingConcept(first.getUrn()) : effectiveConcept(ret);
  }

  @Override
  public Pair<Concept, List<SemanticType>> splitOperators(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("splitOperators", new Class<?>[] { Semantics.class }, new Object[] { concept });

    Concept cret = concept.asConcept();
    List<SemanticType> types = new ArrayList<>();
    Set<SemanticType> type = Sets.intersection(cret.getType(), SemanticType.OPERATOR_TYPES);

    while (type.size() > 0) {
      types.add(type.iterator().next());
      Concept ccret = describedType(cret);
      if (ccret == null) {
        break;
      } else {
        cret = ccret;
      }
      type = Sets.intersection(cret.getType(), SemanticType.OPERATOR_TYPES);
    }

    return Pair.of(cret, types);
  }

  @Override
  public Concept describedType(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("describedType", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Collection<Concept> traits(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("traits", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Set<Concept> ret = new HashSet<>();
    ret.addAll(
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY)));
    ret.addAll(
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY)));
    ret.addAll(
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY)));
    return ret;
  }

  @Override
  public int assertedDistance(Semantics from, Semantics to) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("assertedDistance", new Class<?>[] { Semantics.class, Semantics.class }, new Object[] { from, to });
    if (owl != null) owl.requireAvailableClosure();
  return semanticMatcher.assertedDistance(from, to);
  }

  @Override
  public boolean hasTrait(Semantics concept, Concept trait) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("hasTrait", new Class<?>[] { Semantics.class, Concept.class }, new Object[] { concept, trait });
  for (Concept c : traits(concept)) {
      if (is(c, trait)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Collection<Concept> roles(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("roles", new Class<?>[] { Semantics.class }, new Object[] { concept });
  return this.owl.getRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_ROLE_PROPERTY));
  }

  @Override
  public boolean hasRole(Semantics concept, Concept role) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("hasRole", new Class<?>[] { Semantics.class, Concept.class }, new Object[] { concept, role });
  for (Concept c : roles(concept)) {
      if (is(c, role)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Concept directInherent(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directInherent", new Class<?>[] { Semantics.class }, new Object[] { concept });
  var explicit = explicitInherent(concept);
    if (explicit != null) return explicit;
    Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_INHERENT_TO_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept inherent(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("inherent", new Class<?>[] { Semantics.class }, new Object[] { concept });
  var explicit = explicitInherent(concept);
    if (explicit != null) return explicit;
    Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_INHERENT_TO_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  /** OWL restrictions retain the inherent class but cannot encode its k.LAB collective flag. */
  private Concept explicitInherent(Semantics semantics) {
    // On a configuration, the lexical 'of' operand is an emergence ingredient.
    if (semantics.is(SemanticType.CONFIGURATION)) return null;
    var definition = semantics.asConcept().getUrn();
    if (Urn.isAtomicConcept(definition)) return null;
    var syntax = serviceScope().getService(ResourcesService.class).declareConcept(definition);
    if (syntax == null || syntax.getInherent() == null) return null;
    return resolveConcept(syntax.getInherent().getUrn());
  }

  @Override
  public Concept directGoal(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directGoal", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_PURPOSE_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept goal(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("goal", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_PURPOSE_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept directCooccurrent(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directCooccurrent", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.OCCURS_DURING_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept directCausant(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directCausant", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_CAUSANT_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept directCaused(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directCaused", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_CAUSED_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept directAdjacent(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directAdjacent", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_ADJACENT_TO_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept directCompresent(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directCompresent", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_COMPRESENT_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept directRelativeTo(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directRelativeTo", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_COMPARED_TO_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept cooccurrent(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("cooccurrent", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.OCCURS_DURING_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept causant(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("causant", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_CAUSANT_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept caused(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("caused", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_CAUSED_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept adjacent(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("adjacent", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_ADJACENT_TO_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept compresent(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("compresent", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_COMPRESENT_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public Concept relativeTo(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("relativeTo", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_COMPARED_TO_PROPERTY));
    return cls.isEmpty() ? null : effectiveConcept(cls.iterator().next());
  }

  @Override
  public String displayLabel(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("displayLabel", new Class<?>[] { Semantics.class }, new Object[] { concept });
  String ret = displayName(concept);
    if (!ret.contains(" ")) {
      ret = StringUtils.capitalize(CamelCase.toLowerCase(ret, ' '));
    }
    return ret;
  }

  @Override
  public String displayName(Semantics semantics) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("displayName", new Class<?>[] { Semantics.class }, new Object[] { semantics });
  return semantics instanceof Observable
        ? observableDisplayName((Observable) semantics)
        : conceptDisplayName(semantics.asConcept());
  }

  private String conceptDisplayName(Concept t) {

    String ret = t.getMetadata().get(NS.DISPLAY_LABEL_PROPERTY, String.class);

    if (ret == null) {
      ret = t.getMetadata().get(Metadata.DC_LABEL, String.class);
    }
    if (ret == null) {
      ret = t.getName();
    }
    if (ret.startsWith("i")) {
      ret = ret.substring(1);
    }
    return ret;
  }

  private String observableDisplayName(Observable o) {

    StringBuilder ret = new StringBuilder(conceptDisplayName(o.asConcept()));

    //    for (Pair<ValueOperator, Object> operator : o.getValueOperators()) {
    //
    //      ret.append(StringUtils.capitalize(operator.getFirst().declaration.replace(' ', '_')));
    //
    //      if (operator.getSecond() instanceof KimConcept concept) {
    //        ret.append(conceptDisplayName(declareConcept(concept)));
    //      } else if (operator.getSecond() instanceof KimObservable observable) {
    //        ret.append(observableDisplayName(declareObservable(observable)));
    //      } else {
    //        ret.append("_").append(operator.getSecond().toString().replace(' ', '_'));
    //      }
    //    }
    return ret.toString();
  }

  @Override
  public String style(Concept concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("style", new Class<?>[] { Concept.class }, new Object[] { concept });
  throw new KlabUnimplementedException("Reasoner concept styling is not supported");
  }

  @Override
  public Capabilities capabilities(Scope scope) {

    var ret = new ReasonerCapabilitiesImpl();
    long revision = knowledgeRevision();
    long generation = capabilitiesGeneration;
    String validation = candidateRuntime != null && !semanticOnly ? null : semanticValidationState.signal();
    var acceptedMetadata = candidateRuntime != null && !semanticOnly ? candidateRuntime.metadata() : null;
    var published = acceptedMetadata == null ? publishedSemanticCapabilities : acceptedMetadata.publication();
    if (acceptedMetadata != null) revision = acceptedMetadata.generation();

    ret.setPermissions(permissions(scope));

    ret.setWorldviewId(published.worldviewId());
    ret.setWorldviewUrn(published.worldviewUrn());
    ret.setServiceName(serviceName);
    ret.setType(Type.REASONER);
    ret.setUrl(getUrl());
    ret.setServerId(hardwareSignature == null ? null : ("REASONER_" + hardwareSignature));
    var portable = portableCapabilities();
    ret.setServiceId(portable == null ? configuration.getServiceId() : portable.serviceId());
    ret.getExportSchemata().putAll(ResourceTransport.INSTANCE.getExportSchemata());
    ret.getImportSchemata().putAll(ResourceTransport.INSTANCE.getImportSchemata());
    if (getComponentRegistry() != null) ret.getComponents().addAll(getComponentRegistry().getInstalledComponents(scope));
    if (acceptedMetadata != null) {
      var owned = acceptedMetadata.components();
      ret.getComponents().removeIf(component -> owned.stream().anyMatch(c -> c.id().equals(component.id())));
      ret.getComponents().addAll(owned);
    }
    // A request may straddle a complete mutation. Never label a newer publication with an
    // older revision, or certify metadata captured while readiness/generation changed.
    boolean readable = acceptedMetadata != null ? acceptedMetadata.generation() > 0
        : published.generation() == generation
          && generation == capabilitiesGeneration
          && validation.equals(semanticValidationState.signal())
          && validation.endsWith(":ready")
          && revision == knowledgeRevision();
    if (readable) ret.setAuthorityBindings(published.authorityBindings().stream()
        .map(binding -> new Worldview.AuthorityBinding(binding.localId(), binding.rootIdentity(),
            binding.sourceOntology(), binding.sourceOffset(), binding.sourceLength(), binding.sourceHash(),
            binding.provider(), binding.componentUrn(), binding.componentVersion(), getUrl())).toList());
    ret.setConsistent(readable && published.consistent());
    ret.setKnowledgeRevision(portable == null ? revision : portable.generation());
    ret.setSemanticSessionId(acceptedMetadata!=null?candidateRuntime.semanticSessionId()
        :portable==null?semanticSessionId:portable.sessionId());
    if (readable) ret.setWorldviewCommitment(published.commitment());
    if (readable) ret.setSemanticContentFingerprint(acceptedMetadata == null
        ? semanticContentFingerprint : acceptedMetadata.semanticContentFingerprint());
    return ret;
  }

  /** Capture under the existing ingestion/update monitor; discovery never acquires that monitor. */
  private void publishSemanticCapabilities() {
    var loaded = worldview;
    boolean usable = loaded != null && knowledgeReady && consistent.get();
    var bindings = usable ? loaded.getAuthorityBindings().stream()
        .filter(binding -> authorityBindings.get(binding.localId()) != null
            && binding.sourceHash() != null
            && binding.sourceHash().equals(loadedOntologySources.get(binding.sourceOntology())))
        .map(binding -> new Worldview.AuthorityBinding(binding.localId(), binding.rootIdentity(),
            binding.sourceOntology(), binding.sourceOffset(), binding.sourceLength(), binding.sourceHash(),
            binding.provider(), binding.componentUrn(), binding.componentVersion(), getUrl())).toList()
        : List.<Worldview.AuthorityBinding>of();
    var id = loaded == null ? null : loaded.getWorldviewId();
    var commitment = usable && id != null && !id.isBlank() && !loadedOntologySources.isEmpty()
        ? new WorldviewCommitment(1, id, loadedOntologySources) : null;
    publishedSemanticCapabilities =
        org.integratedmodelling.klab.services.reasoner.internal.SemanticCapabilitiesSnapshot.capture(
            capabilitiesGeneration, id, loaded == null ? null : loaded.getUrn(), usable, bindings, commitment);
  }

  @Override
  public String serviceId() {
    return configuration.getServiceId();
  }

  @Override
  public Collection<Concept> identities(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("identities", new Class<?>[] { Semantics.class }, new Object[] { concept });
  return this.owl.getRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY));
  }

  @Override
  public Collection<Concept> attributes(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("attributes", new Class<?>[] { Semantics.class }, new Object[] { concept });
  return this.owl.getRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY));
  }

  @Override
  public Collection<Concept> realms(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("realms", new Class<?>[] { Semantics.class }, new Object[] { concept });
  return this.owl.getRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY));
  }

  @Override
  public Concept lexicalRoot(Semantics trait) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("lexicalRoot", new Class<?>[] { Semantics.class }, new Object[] { trait });
  return lexicalRoot(trait, new HashSet<>());
  }

  private Concept lexicalRoot(Semantics trait, Set<Concept> visited) {
    if (trait == null || trait.is(SemanticType.NOTHING) || !visited.add(trait.asConcept()))
      return null;
    // Authority families are explicitly selected by this generation's worldview binding.
    // Neither provider ancestry nor mapped semantic boundaries choose a lexical category.
    if (authorityIdentityResolver != null) {
      Concept anchor = authorityIdentityResolver.lexicalRoot(trait.asConcept());
      if (anchor != null) return anchor;
    }
    if (CoreOntology.isCore(trait.asConcept())) return trait.asConcept();

    // A transported provider concept cannot supply its own lexical boundary or erase its
    // authority flag. Only this generation's loaded class and configured bridge can do so.
    var owningOntology = owl().getOntology(trait.asConcept().getNamespace());
    var loaded = owningOntology == null ? null : owningOntology.getConcept(trait.asConcept().getName());
    if (owningOntology != null && owningOntology.isInternal()
        && (trait.is(SemanticType.AUTHORITY_IDENTITY)
            || loaded != null && loaded.is(SemanticType.AUTHORITY_IDENTITY))) return null;
    if (loaded != null && loaded.is(SemanticType.AUTHORITY_IDENTITY)) {
      if (!loaded.getUrn().equals(trait.asConcept().singular().getUrn())) return null;
      trait = loaded;
    }

    String original = trait.getMetadata().get(NS.ORIGINAL_TRAIT, String.class);
    if (original != null && !original.equals(trait.getUrn()))
      return lexicalRoot(owl().getConcept(original), visited);

    if (trait.getMetadata().get(NS.BASE_DECLARATION) != null) return trait.asConcept();
    if (trait.is(SemanticType.AUTHORITY_IDENTITY) && owningOntology == null) return null;
    // Lexical categories are supplied by the worldview boundary. Composition must not turn
    // this category check into an external ancestry request.
    for (Concept parent : owl().getParents(trait.asConcept())) {
      Concept root = lexicalRoot(parent, visited);
      if (root != null) return root;
    }
    return null;
  }

  @Override
  public boolean hasDirectTrait(Semantics type, Concept trait) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("hasDirectTrait", new Class<?>[] { Semantics.class, Concept.class }, new Object[] { type, trait });

    for (Concept c : directTraits(type)) {
      if (is(trait, c)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean hasDirectRole(Semantics type, Concept trait) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("hasDirectRole", new Class<?>[] { Semantics.class, Concept.class }, new Object[] { type, trait });
  for (Concept c : directRoles(type)) {
      if (is(trait, c)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Collection<Concept> directTraits(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directTraits", new Class<?>[] { Semantics.class }, new Object[] { concept });
  Set<Concept> ret = new HashSet<>();
    ret.addAll(
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY)));
    ret.addAll(
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY)));
    ret.addAll(
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY)));
    return ret;
  }

  @Override
  public Collection<Concept> directAttributes(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directAttributes", new Class<?>[] { Semantics.class }, new Object[] { concept });
  return this.owl.getDirectRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY));
  }

  @Override
  public Collection<Concept> directIdentities(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directIdentities", new Class<?>[] { Semantics.class }, new Object[] { concept });
  return this.owl.getDirectRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY));
  }

  @Override
  public Collection<Concept> directRealms(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directRealms", new Class<?>[] { Semantics.class }, new Object[] { concept });
  return this.owl.getDirectRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY));
  }

  @Override
  public Concept negated(Concept concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("negated", new Class<?>[] { Concept.class }, new Object[] { concept });
  return this.owl.makeNegation(concept.asConcept());
  }

  @Override
  public SemanticType observableType(Semantics observable, boolean acceptTraits) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("observableType", new Class<?>[] { Semantics.class, boolean.class }, new Object[] { observable, acceptTraits });
  if (observable instanceof Observable
        && ((Observable) observable).getArtifactType().equals(Artifact.Type.VOID)) {
      return SemanticType.NOTHING;
    }
    Set<SemanticType> type = EnumSet.copyOf(observable.asConcept().getType());
    type.retainAll(SemanticType.BASE_MODELABLE_TYPES);
    if (type.size() != 1) {
      throw new IllegalArgumentException(
          "trying to extract the observable type from non-observable " + observable);
    }
    return type.iterator().next();
  }

  @Override
  public Concept relationshipSource(Semantics relationship) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("relationshipSource", new Class<?>[] { Semantics.class }, new Object[] { relationship });
  Collection<Concept> ret = relationshipSources(relationship);
    return ret.isEmpty() ? null : ret.iterator().next();
  }

  @Override
  public Collection<Concept> relationshipSources(Semantics relationship) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("relationshipSources", new Class<?>[] { Semantics.class }, new Object[] { relationship });
  return projectHierarchy(org.integratedmodelling.common.utils.Utils.Collections.join(
        this.owl.getDirectRestrictedClasses(
            relationship.asConcept(), this.owl.getProperty(NS.IMPLIES_SOURCE_PROPERTY)),
        this.owl.getRestrictedClasses(
            relationship.asConcept(), this.owl.getProperty(NS.IMPLIES_SOURCE_PROPERTY))));
  }

  @Override
  public Concept relationshipTarget(Semantics relationship) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("relationshipTarget", new Class<?>[] { Semantics.class }, new Object[] { relationship });
  Collection<Concept> ret = relationshipTargets(relationship);
    return ret.isEmpty() ? null : ret.iterator().next();
  }

  @Override
  public Collection<Concept> relationshipTargets(Semantics relationship) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("relationshipTargets", new Class<?>[] { Semantics.class }, new Object[] { relationship });
  return projectHierarchy(org.integratedmodelling.common.utils.Utils.Collections.join(
        this.owl.getDirectRestrictedClasses(
            relationship.asConcept(), this.owl.getProperty(NS.IMPLIES_DESTINATION_PROPERTY)),
        this.owl.getRestrictedClasses(
            relationship.asConcept(), this.owl.getProperty(NS.IMPLIES_DESTINATION_PROPERTY))));
  }

  @Override
  public boolean satisfiable(Semantics ret) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("satisfiable", new Class<?>[] { Semantics.class }, new Object[] { ret });
  return this.owl.isSatisfiable(ret);
  }

  @Override
  public Collection<Concept> applicableObservables(Concept main) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("applicableObservables", new Class<?>[] { Concept.class }, new Object[] { main });
  return projectHierarchy(this.owl.getRestrictedClasses(main, this.owl.getProperty(NS.APPLIES_TO_PROPERTY)));
  }

  @Override
  public Collection<Concept> directRoles(Semantics concept) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("directRoles", new Class<?>[] { Semantics.class }, new Object[] { concept });
  return this.owl.getDirectRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_ROLE_PROPERTY));
  }

  private static final CandidatePublicationCoordinator.Cancellation NO_COMPILATION_CANCELLATION = () -> false;
  private boolean initialPrivateCompilationStarted;

  @Override
  public synchronized ResourceSet loadKnowledge(Worldview worldview, Scope scope) {
    if (semanticOnly) initialPrivateCompilationStarted = true;
    return loadKnowledgeEntry(worldview, scope, NO_COMPILATION_CANCELLATION);
  }

  /** Internal one-shot entry. Its generation predicate must never be retained by an accepted actor. */
  public synchronized ResourceSet compilePrivateKnowledge(Worldview worldview,
      org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentScope scope,
      CandidatePublicationCoordinator.Cancellation cancellation) {
    if (!semanticOnly || knowledgeReady || initialPrivateCompilationStarted || scope != serviceScope())
      throw new IllegalStateException("Compilation cancellation requires a fresh private actor and its owned scope");
    Objects.requireNonNull(cancellation);
    initialPrivateCompilationStarted = true;
    return loadKnowledgeEntry(worldview, scope, cancellation);
  }

  private static void compilationCheckpoint(CandidatePublicationCoordinator.Cancellation cancellation, CandidateTrace.Phase phase) {
    CandidateTrace.checkpoint(cancellation, phase);
  }

  private ResourceSet loadKnowledgeEntry(Worldview worldview, Scope scope, CandidatePublicationCoordinator.Cancellation cancellation) {
    if (org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.enabled()) org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.emit(
        org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Event.LOAD_REQUEST,
        candidateRuntime == null ? -1 : candidateRuntime.status().inputGeneration(), -1, -1,
        null, null, scope instanceof UserScope
            ? org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Reason.USER
            : org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Reason.SERVICE);
    if (candidateRuntime != null && !semanticOnly && scope instanceof UserScope) {
      // User retrieval/validation stays authorized by the request. The one global runtime
      // must use the same independently discovered sources and owner as startup polling.
      // Reject a private, stale or unavailable request without invalidating global state.
      try {
        if (worldview == null) return ResourceSet.empty(Notification.error("Requested worldview is unavailable"));
        var errors = BeanIngestionPreflight.errors(worldview);
        if (!errors.isEmpty()) {
          var rejected = ResourceSet.empty();
          rejected.getNotifications().addAll(errors);
          return rejected;
        }
        worldview = org.integratedmodelling.klab.services.reasoner.internal.ServiceWorldviewAdmission.resolve(
            worldview, serviceScope());
        scope = serviceScope();
      } catch (RuntimeException failure) {
        return ResourceSet.empty(Notification.error("Service worldview admission unavailable: " + failure.getMessage()));
      }
    }
    org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime.PreflightAttempt attempt = null;
    if (candidateRuntime != null && !semanticOnly && worldview != null && !worldview.getOntologies().isEmpty()) {
      try { attempt = candidateRuntime.beginPreflight(worldview, scope); }
      catch (RuntimeException failure) {
        candidateRuntime.invalidate(org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Reason.PREFLIGHT_EXCEPTION);
        return ResourceSet.empty(Notification.error("Authoritative bean preflight unavailable"));
      }
      if (!attempt.retry()) return ResourceSet.empty();
      worldview = attempt.snapshot();
    } else if (candidateRuntime != null && !semanticOnly) {
      candidateRuntime.invalidate(org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Reason.EMPTY_INPUT);
    }
    List<Notification> notifications = new ArrayList<>();
    // Ingestion also runs before user scopes exist. Collect on the supplied channel and always
    // detach the listener, so startup errors survive in the ontology validation diagnostics.
    try (var collector = new IngestionNotifications(scope, notifications)) {
      return loadKnowledge(worldview, scope, notifications, attempt, cancellation);
    } finally {
      if (candidateRuntime == null || semanticOnly) {
      if (semanticValidationState.loading())
        semanticValidationState.settle(knowledgeRevision(), false);
      publishSemanticCapabilities();
      }
    }
  }

  private ResourceSet loadKnowledge(Worldview worldview, Scope scope, List<Notification> ret,
      org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime.PreflightAttempt attempt,
      CandidatePublicationCoordinator.Cancellation cancellation) {
    compilationCheckpoint(cancellation,CandidateTrace.Phase.INPUT_PREFLIGHT);
    try (var phase = CandidateTrace.phase(CandidateTrace.Phase.INPUT_PREFLIGHT,null)) {
      // Freeze and validate authoritative semantic beans before initialization or any OWL write.
      // Aggregate isEmpty flags may be stale; current structured diagnostics own this gate.
      if (worldview == null || worldview.getOntologies().isEmpty())
        return ResourceSet.empty(Notification.error("Authoritative worldview is unavailable or contains no ontologies"));
      try {
        worldview = BeanIngestionPreflight.snapshot(worldview);
        var beanErrors = BeanIngestionPreflight.errors(worldview);
        if (!beanErrors.isEmpty()) {
          if (candidateRuntime != null && !semanticOnly
              && !candidateRuntime.rejectPreflight(attempt, beanErrors, scope))
            return ResourceSet.empty(Notification.info("Stale bean preflight discarded"));
          worldviewLoadDiagnostics = List.copyOf(beanErrors);
          var rejected = ResourceSet.empty();
          rejected.getNotifications().addAll(beanErrors);
          return rejected;
        }
      } catch (RuntimeException e) {
        return ResourceSet.empty(Notification.error("Authoritative bean preflight unavailable: " + e.getMessage()));
      }
      compilationCheckpoint(cancellation,CandidateTrace.Phase.INPUT_PREFLIGHT);
      if (!org.integratedmodelling.klab.services.reasoner.internal.WorldviewLoadingSupport.loadable(worldview, false)) {
        List<Notification> failures = List.of(Notification.error("Authoritative worldview has no valid root ontology"));
        if (candidateRuntime != null && !semanticOnly
            && !candidateRuntime.rejectPreflight(attempt, failures, scope))
          return ResourceSet.empty(Notification.info("Stale bean preflight discarded"));
        worldviewLoadDiagnostics = failures;
        return ResourceSet.empty(failures.getFirst());
      }
    }
    if (candidateRuntime != null && !semanticOnly) {
      if (!candidateRuntime.submit(worldview, attempt, scope))
        return ResourceSet.empty(Notification.info("Stale worldview submission discarded"));
      this.worldview = BeanIngestionPreflight.snapshot(worldview);
      var status = candidateRuntime.status();
      setOperational(status.acceptedGeneration() > 0);
      return ResourceSet.empty(Notification.info(AcceptedSemanticRuntime.validationReason(status)));
    }
    // this remains the only service whose initialization depends on others, so we set it up
    // manually when the specific conditions for initialization are met
    if (observationReasoner == null) {
      if (!initializeService()) {
        Logging.INSTANCE.error(
            "failed to initialize the observation reasoner and related services");
        return ResourceSet.empty(Notification.error("Failed to initialize reasoner"));
      }
      setInitialized(true);
    }

    boolean localAuthoring = Utils.URLs.isLocalHost(getUrl());
    var sourceDiagnostics =
        org.integratedmodelling.klab.services.reasoner.internal.WorldviewLoadingSupport.diagnostics(
            worldview);
    worldviewLoadDiagnostics = List.of();
    ret.addAll(sourceDiagnostics);

    semanticValidationState.begin(knowledgeRevision());
    capabilitiesGeneration++;
    knowledgeReady = false;
    invalidateSemanticCaches();
    this.worldview = worldview;
    this.observationReasoner = new ObservationReasoner(this);

    long ingestionStarted = System.nanoTime();
    compilationCheckpoint(cancellation,CandidateTrace.Phase.OWL_INITIALIZE);
    try (var phase = CandidateTrace.phase(CandidateTrace.Phase.OWL_INITIALIZE,null)) {
      this.owl.initialize(worldview.getOntologies().getFirst());
      serviceScope().info("Worldview OWL initialization completed in "
          + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ingestionStarted) + " ms");
    }
    compilationCheckpoint(cancellation,CandidateTrace.Phase.OWL_INITIALIZE);
    authorityBindings.clear();
    loadedOntologySources.clear();
    declarationDomains.clear();
    loadedOntologyDiagnostics.clear();
    compileStagedOntologies(worldview.getOntologies(),scope,cancellation);
    long flushStarted = System.nanoTime();
    compilationCheckpoint(cancellation,CandidateTrace.Phase.LOAD_FLUSH);
    try (var phase = CandidateTrace.phase(CandidateTrace.Phase.LOAD_FLUSH,null)) {
      this.owl.flushReasoner();
      serviceScope().info("Worldview OWL flush completed in "
          + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - flushStarted) + " ms");
    }
    compilationCheckpoint(cancellation,CandidateTrace.Phase.LOAD_FLUSH);
    for (var ontology : worldview.getOntologies()) {
      loadedOntologyDiagnostics.put(
          ontology.getUrn(),
          ret.stream()
              .filter(
                  n ->
                      n.getLexicalContext() != null
                          && ontology.getUrn().equals(n.getLexicalContext().getDocumentUrn()))
              .toList());
    }
    long strategiesStarted = System.nanoTime();
    compilationCheckpoint(cancellation,CandidateTrace.Phase.STRATEGIES);
    try (var phase = CandidateTrace.phase(CandidateTrace.Phase.STRATEGIES,null)) {
      for (var strategyDocument : worldview.getObservationStrategies()) {
        for (var strategy : strategyDocument.getStatements()) {
          observationReasoner.registerStrategy(strategy);
        }
      }
      observationReasoner.initializeStrategies();
      serviceScope().info("Worldview strategy initialization completed in "
          + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - strategiesStarted) + " ms");
    }
    compilationCheckpoint(cancellation,CandidateTrace.Phase.STRATEGIES);

    // assess consistent status and if consistent, set operational
    long consistencyStarted = System.nanoTime();
    boolean logicallyConsistent;
    compilationCheckpoint(cancellation,CandidateTrace.Phase.LOAD_CONSISTENCY);
    try (var phase = CandidateTrace.phase(CandidateTrace.Phase.LOAD_CONSISTENCY,null)) {
      this.owl.monotoneRefinements().refresh(this);
      logicallyConsistent = !this.owl.isOn() || this.owl.isConsistent();
    }
    compilationCheckpoint(cancellation,CandidateTrace.Phase.LOAD_CONSISTENCY);
    serviceScope().info("Worldview consistency check completed in "
        + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - consistencyStarted) + " ms");
    this.consistent.set(!Utils.Notifications.hasErrors(ret) && logicallyConsistent);
    if (!logicallyConsistent) {
      ret.add(Notification.error(Utils.Notifications.hasErrors(ret)
          ? "Rejected worldview produced inconsistent OWL"
          : "VALIDATION_TRANSLATION_BUG: accepted worldview produced inconsistent OWL"));
    }
    this.advisories.addAll(ret);

    knowledgeReady = true;
    semanticValidationState.settle(knowledgeRevision(), logicallyConsistent);
    setOperational(localAuthoring || this.consistent.get());

    return Utils.Resources.createFromLexicalNotifications(ret);
  }

  @Override
  public synchronized ResourceSet updateKnowledge(ResourceSet changes, UserScope scope) {
    var ownResources = scope.getService(ResourcesService.class);
    Map<URL, ResourcesService> services = new HashMap<>();

    var affectedKnowledge = new HashSet<String>();
    changes.getOntologies().forEach(r -> affectedKnowledge.add("ontology:" + r.getResourceUrn()));
    changes.getObservationStrategies().forEach(r -> affectedKnowledge.add("strategy:" + r.getResourceUrn()));
    var ontologies = new LinkedHashMap<ResourceSet.Resource, PreparedOntology>();
    var strategies = new LinkedHashMap<ResourceSet.Resource, PreparedStrategy>();
    var sourceWorldviews = new ArrayList<Worldview>();
    var documents = new ArrayList<KlabDocument<?>>();
    try {
      // Fetch the whole batch before releasing even one old ontology. Only these owned copies
      // are validated and compiled; a later Resources edit cannot replace the validated beans.
      for (var resource : changes.getOntologies()) {
        var resources = resourcesForChange(resource, changes, ownResources, services, scope);
        var notifications = new ArrayList<Notification>();
        var parsingScope = getScopeManager().collectMessagePayload(scope, Notification.class, notifications);
        var retrieved = resources.retrieve(resource.getResourceUrn(), KimOntology.class, parsingScope);
        if (retrieved == null) throw new KlabValidationException("Authoritative ontology unavailable: " + resource.getResourceUrn());
        var ontology = BeanIngestionPreflight.snapshot(retrieved);
        notifications.addAll(ontology.getNotifications());
        // The worldview may report aggregate errors from unrelated or superseded sources.
        // Resolve from its structured beans; do not attribute its channel messages to this bean.
        var authoritySnapshot = resources.retrieve(worldview.getUrn(), Worldview.class, scope);
        if (authoritySnapshot == null) throw new KlabValidationException("Cannot synchronize worldview authority definitions");
        authoritySnapshot = BeanIngestionPreflight.snapshot(authoritySnapshot);
        sourceWorldviews.add(authoritySnapshot);
        documents.add(ontology);
        ontologies.put(resource, new PreparedOntology(ontology, authoritySnapshot, parsingScope, notifications));
      }
      for (var resource : changes.getObservationStrategies()) {
        var resources = resourcesForChange(resource, changes, ownResources, services, scope);
        var notifications = new ArrayList<Notification>();
        var parsingScope = getScopeManager().collectMessagePayload(scope, Notification.class, notifications);
        var retrieved = resources.retrieve(resource.getResourceUrn(), KimObservationStrategyDocument.class, parsingScope);
        if (retrieved == null) throw new KlabValidationException("Authoritative strategy unavailable: " + resource.getResourceUrn());
        var strategy = BeanIngestionPreflight.snapshot(retrieved);
        notifications.addAll(strategy.getNotifications());
        var sourceSnapshot = resources.retrieve(worldview.getUrn(), Worldview.class, scope);
        if (sourceSnapshot == null) throw new KlabValidationException("Authoritative strategy dependencies unavailable");
        sourceWorldviews.add(BeanIngestionPreflight.snapshot(sourceSnapshot));
        documents.add(strategy);
        strategies.put(resource, new PreparedStrategy(strategy, notifications));
      }
      var errors = new LinkedHashSet<>(BeanIngestionPreflight.errors(documents, sourceWorldviews));
      ontologies.values().forEach(p -> errors.addAll(BeanIngestionPreflight.errors(p.notifications())));
      strategies.values().forEach(p -> errors.addAll(BeanIngestionPreflight.errors(p.notifications())));
      if (!errors.isEmpty()) return rejectBeanUpdate(changes, affectedKnowledge, List.copyOf(errors));
    } catch (Exception e) {
      return rejectBeanUpdate(changes, affectedKnowledge,
          List.of(Notification.error("Authoritative bean preflight unavailable: " + e.getMessage())));
    }

    if (candidateRuntime != null && !semanticOnly) {
      if (sourceWorldviews.isEmpty() && worldview == null)
        return rejectBeanUpdate(changes, affectedKnowledge, List.of(Notification.error("Authoritative worldview snapshot is unavailable")));
      var complete = BeanIngestionPreflight.snapshot(sourceWorldviews.isEmpty() ? worldview : sourceWorldviews.getFirst());
      if (!(complete instanceof org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl mutable))
        return rejectBeanUpdate(changes, affectedKnowledge, List.of(Notification.error("Authoritative worldview snapshot cannot be prepared")));
      var changing = new HashSet<String>();
      documents.forEach(document -> changing.add(org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory.sourceKey(document)));
      var manifest = org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory.sources(complete);
      for (var sourceSnapshot : sourceWorldviews) {
        var sourceManifest = org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory.sources(sourceSnapshot);
        if (!Objects.equals(complete.getWorldviewId(), sourceSnapshot.getWorldviewId())
            || !manifest.keySet().equals(sourceManifest.keySet())
            || manifest.entrySet().stream().anyMatch(entry -> !changing.contains(entry.getKey())
                && !Objects.equals(entry.getValue(), sourceManifest.get(entry.getKey()))))
          return rejectBeanUpdate(changes, affectedKnowledge,
              List.of(Notification.error("Authoritative dependency snapshot changed during bean prefetch; retry the complete batch")));
      }
      var preparedBindings = new ArrayList<>(mutable.getAuthorityBindings());
      for (var prepared : ontologies.values()) {
        org.integratedmodelling.klab.services.reasoner.internal.WorldviewLoadingSupport.replaceOntology(mutable, prepared.ontology());
        preparedBindings.removeIf(binding -> binding.sourceOntology().equals(prepared.ontology().getUrn()));
        preparedBindings.addAll(prepared.worldview().getAuthorityBindings().stream()
            .filter(binding -> binding.sourceOntology().equals(prepared.ontology().getUrn())).toList());
      }
      mutable.setAuthorityBindings(preparedBindings);
      var completeStrategies = new ArrayList<>(mutable.getObservationStrategies());
      for (var prepared : strategies.values()) {
        int position = -1;
        for (int i = 0; i < completeStrategies.size(); i++) {
          if (completeStrategies.get(i).getUrn().equals(prepared.document().getUrn())) {
            position = i;
            break;
          }
        }
        if (position < 0) return rejectBeanUpdate(changes, affectedKnowledge,
            List.of(Notification.error("Authoritative strategy dependency order is unavailable; retry the complete batch")));
        completeStrategies.set(position, prepared.document());
      }
      mutable.setObservationStrategies(completeStrategies);
      var errors = BeanIngestionPreflight.errors(complete);
      if (!errors.isEmpty()) return rejectBeanUpdate(changes, affectedKnowledge, errors);
      // Use the same fingerprint/preflight gate as startup polling. Duplicate change events
      // must not cancel a pending candidate, reconfigure providers, or notify editors again.
      if (org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.enabled()) org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.emit(
          org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Event.UPDATE_BATCH,
          candidateRuntime.status().inputGeneration(), -1, -1, null, null,
          org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Reason.USER);
      changes.getNotifications().addAll(loadKnowledge(complete, scope).getNotifications());
      return changes;
    }

    serviceScope().setMaintenanceMode(true);

    semanticValidationState.beginUpdate(knowledgeRevision(), affectedKnowledge);
    capabilitiesGeneration++;
    invalidateSemanticCaches();

    boolean inconsistent = false;

    try {
      /*
      release all ontologies first. This should not be necessary but it prevents a NPE in case there are
      forward references - which the syntax should flag as errors, but doesn't at the moment.
       */
      for (var resource : changes.getOntologies()) {
        for (var binding : authorityBindings.snapshot()) {
          if (binding.request().rootIdentity().startsWith(resource.getResourceUrn() + ":")) {
            var authorityOntology = owl.getOntology(
                org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings.ontologyId(binding));
            if (authorityOntology != null) owl.releaseOntology(authorityOntology);
          }
        }
        authorityBindings.releaseNamespace(resource.getResourceUrn());
        loadedOntologySources.remove(resource.getResourceUrn());
        declarationDomains.remove(resource.getResourceUrn());
        loadedOntologyDiagnostics.remove(resource.getResourceUrn());
        var ontology = this.owl.getOntology(resource.getResourceUrn());
        if (ontology != null) {
          this.owl.releaseOntology(ontology);
        }
      }

      var stagedScopes = new LinkedHashMap<KimOntology,Scope>();
      for (var resource : changes.getOntologies()) {
        var prepared = ontologies.get(resource);
        var notifications = prepared.notifications();
        var parsingScope = prepared.scope();
        var ontology = prepared.ontology();
        var authoritySnapshot = prepared.worldview();
        if (worldview instanceof org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl mutable) {
          var reconciled = new ArrayList<>(mutable.getAuthorityBindings());
          reconciled.removeIf(binding -> binding.sourceOntology().equals(ontology.getUrn()));
          reconciled.addAll(authoritySnapshot.getAuthorityBindings().stream()
              .filter(binding -> binding.sourceOntology().equals(ontology.getUrn())).toList());
          mutable.setAuthorityBindings(reconciled);
          org.integratedmodelling.klab.services.reasoner.internal.WorldviewLoadingSupport
              .replaceOntology(mutable, ontology);
        }
        stagedScopes.put(ontology,parsingScope);
        this.advisories.removeAll(
            loadedOntologyDiagnostics.getOrDefault(ontology.getUrn(), List.of()));
        loadedOntologyDiagnostics.put(ontology.getUrn(), List.copyOf(notifications));
        this.advisories.addAll(notifications);
        resource.getNotifications().addAll(notifications);

        if (Utils.Notifications.hasErrors(notifications)) {
          inconsistent = true;
        }
      }

      compileStagedOntologies(stagedScopes);

      for (var resource : changes.getObservationStrategies()) {
        var prepared = strategies.get(resource);
        var notifications = prepared.notifications();
        var observationStrategyDocument = prepared.document();

        observationReasoner.releaseNamespace(observationStrategyDocument.getUrn());
        for (var strategy : observationStrategyDocument.getStatements()) {
          observationReasoner.registerStrategy(strategy);
        }
        observationReasoner.initializeStrategies();

        resource.getNotifications().addAll(notifications);
      }
      this.owl.flushReasoner();
      if (this.owl.isOn() && !this.owl.isConsistent()) {
        inconsistent = true;
        this.advisories.add(
            Notification.error("VALIDATION_TRANSLATION_BUG: worldview validation or translation allowed inconsistent OWL"));
      }
    } catch (Throwable t) {
      inconsistent = true;
      changes
          .getNotifications()
          .add(Notification.error("Knowledge synchronization failed: " + t.getMessage()));
      for (var resource : changes.getOntologies()) {
        loadedOntologySources.remove(resource.getResourceUrn());
        declarationDomains.remove(resource.getResourceUrn());
      }
      Logging.INSTANCE.error("failed to update knowledge", t);
      this.advisories.add(Notification.error(t));
    } finally {
      serviceScope().setMaintenanceMode(false);
      semanticValidationState.settleUpdate(
          knowledgeRevision(), affectedKnowledge, !inconsistent && knowledgeReady);
      this.consistent.set(
          semanticValidationState.ready()
              && loadedOntologyDiagnostics.values().stream()
                  .noneMatch(Utils.Notifications::hasErrors));
      publishSemanticCapabilities();
    }

    return changes;
  }

  private record PreparedOntology(KimOntology ontology, Worldview worldview, Scope scope,
      List<Notification> notifications) {}
  private record PreparedStrategy(KimObservationStrategyDocument document,
      List<Notification> notifications) {}

  private ResourcesService resourcesForChange(ResourceSet.Resource resource, ResourceSet changes,
      ResourcesService ownResources, Map<URL, ResourcesService> services, UserScope scope) {
    if (ownResources.capabilities(scope).getServiceId().equals(resource.getServiceId())) return ownResources;
    var url = changes.getServices().get(resource.getServiceId());
    if (url == null) throw new KlabValidationException("Authoritative Resources service unavailable: " + resource.getServiceId());
    return services.computeIfAbsent(url, target -> ServiceClientCatalog.INSTANCE.getService(
        target, settingsForSlaveServices, scope, ResourcesService.class));
  }

  private ResourceSet rejectBeanUpdate(ResourceSet changes, Set<String> affected,
      List<Notification> errors) {
    changes.getNotifications().addAll(errors);
    if (candidateRuntime != null && !semanticOnly) {
      // A failed user batch may be private or stale. Keep its diagnostics on the request;
      // only independently discovered service beans may invalidate/reject global state.
      try {
        var discovered = org.integratedmodelling.klab.services.reasoner.internal.ServiceWorldviewAdmission.discover(serviceScope());
        if (candidateRuntime.shouldRetryPreflight(discovered, serviceScope())) {
          changes.getNotifications().addAll(loadKnowledge(discovered, serviceScope()).getNotifications());
        }
      } catch (RuntimeException unavailable) {
        changes.getNotifications().add(Notification.warning(
            "Service worldview refresh unavailable after rejected request: " + unavailable.getMessage()));
      }
      return changes;
    }
    semanticValidationState.beginUpdate(knowledgeRevision(), affected);
    semanticValidationState.settleUpdate(knowledgeRevision(), affected, false);
    // Loaded OWL, source fingerprints, declarations and authority bindings remain untouched.
    // Failure describes the saved source update, not a newly established logical contradiction.
    publishSemanticCapabilities();
    return changes;
  }

  /** Retain the originating Resources service on declared concepts for later namespace-bound retrieval. */
  private void tagConceptService(KimConceptStatement statement, String serviceId) {
    if (serviceId == null) return;
    var concept = owl.getConcept(statement.getNamespace() + ":" + statement.getUrn());
    if (concept instanceof ConceptImpl implementation) implementation.setServiceId(serviceId);
    for (var child : statement.getChildren()) tagConceptService(child, serviceId);
  }

  @Override
  public boolean is(Semantics concept, Semantics other) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("is", new Class<?>[] { Semantics.class, Semantics.class }, new Object[] { concept, other });

    if (owl != null) owl.requireAvailableClosure();
    Objects.requireNonNull(concept, "concept");
    Objects.requireNonNull(other, "other");

    if (concept == other || concept.equals(other)) {
      return true;
    }

    if (concept.asConcept().isCollective() != other.asConcept().isCollective()) return false;
    // Admitted authority terms use the provider's directional contract, including UNKNOWN.
    // Exported OWL parents never override a provider decision or supply a missing mapping.
    var delegated = authorityIdentityResolver == null ? Authority.Subsumption.UNSUPPORTED
        : authorityIdentityResolver.subsumption(other.asConcept(), concept.asConcept());
    if (delegated != Authority.Subsumption.UNSUPPORTED)
      return delegated == Authority.Subsumption.SUBSUMES;
    if (authorityIdentityResolver != null && other.asConcept().is(SemanticType.AUTHORITY_IDENTITY))
      authorityIdentityResolver.ensureHierarchy(concept.asConcept());
    var key = new SubsumptionKey(knowledgeRevision(), concept.asConcept(), other.asConcept());
    return subsumption.get(key, ignored -> computeSubsumption(concept, other));
  }

  private boolean computeSubsumption(Semantics concept, Semantics other) {

    if (concept.asConcept().isCollective() != other.asConcept().isCollective()) {
      return false;
    }

    // non-semantic concepts can only be the same thing.
    if (concept instanceof ConceptImpl concept1 && concept1.getNonSemanticId() < 0) {
      return other instanceof ConceptImpl concept2 && concept2.getUrn().equals(concept1.getUrn());
    }

    /*
     * first use "isn't" based on the enum types to quickly cut out those that don't
     * match. Also works with concepts in different ontologies that have the same
     * definition.
     */
    if (inWorldview(concept, other)) {
      var fundamentalType = SemanticType.fundamentalType(other.asConcept().getType());
      if (fundamentalType != null
          && !Sets.intersection(concept.asConcept().getType(), other.asConcept().getType())
              .contains(fundamentalType)) {
        return false;
      }
    }

    /*
     * Speed up checking for logical expressions without forcing the reasoner to
     * compute complex logics.
     */
    Boolean logicalResult = logicalSubsumption(concept, other, this::operands, this::is);
    if (logicalResult != null) {
      return logicalResult;
    }

    if (this.owl.isOn()) {
      return this.owl.is(concept.asConcept(), other.asConcept());
    }
    Collection<Concept> collection = allParents(concept);
    return collection.contains(other.asConcept());
  }

  static Boolean logicalSubsumption(
      Semantics concept,
      Semantics other,
      Function<Semantics, Collection<Concept>> operandProvider,
      BiPredicate<Semantics, Semantics> subsumption) {
    if (concept.is(SemanticType.UNION)) {
      for (Concept operand : operandProvider.apply(concept)) {
        if (!subsumption.test(operand, other)) {
          return false;
        }
      }
      return true;
    }
    if (concept.is(SemanticType.INTERSECTION)) {
      for (Concept operand : operandProvider.apply(concept)) {
        if (subsumption.test(operand, other)) {
          return true;
        }
      }
    }
    return null;
  }

  @Override
  public Semantics domain(Semantics conceptImpl) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("domain", new Class<?>[] { Semantics.class }, new Object[] { conceptImpl });
  if (conceptImpl == null) {
      return null;
    }
    var definingDomain = org.integratedmodelling.klab.services.reasoner.owl.WorldviewDomainSupport
        .domain(owl, conceptImpl.asConcept());
    if (definingDomain != null) return definingDomain;
    ArrayDeque<Concept> queue = new ArrayDeque<>();
    Set<Concept> visited = new HashSet<>();
    queue.add(conceptImpl.asConcept());
    while (!queue.isEmpty()) {
      Concept current = queue.removeFirst();
      if (!visited.add(current)) {
        continue;
      }
      if (current.is(SemanticType.DOMAIN)) {
        return current;
      }
      // Domain is a worldview category, available through the configured boundary.
      queue.addAll(owl.getParents(current));
    }
    return null;
  }

  private void requireInitializedKnowledge() {
    owl.requireAvailableClosure();
    if (!owl.isInitialized())
      throw new org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException(
          "Reasoner knowledge is unavailable: correct worldview errors and wait for initialization");
  }

  public Concept declareConcept(KimConcept conceptDeclaration) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("declareConcept", new Class<?>[] { KimConcept.class }, new Object[] { conceptDeclaration });
  requireInitializedKnowledge();
    // Syntax substitution invalidates cached definitions. Normalize a detached tree before
    // canonical lookup; never finalize the caller's frozen preparation payload in place.
    if (conceptDeclaration.getUrn() == null && conceptDeclaration instanceof KimConceptImpl syntax)
      conceptDeclaration = syntax.mapSyntax(node -> node.finalizeDefinition());
    return declare(
        conceptDeclaration,
        this.owl.requireOntology(conceptDeclaration.getNamespace()),
        serviceScope());
  }

  public Observable declareObservable(KimObservable observableDeclaration) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("declareObservable", new Class<?>[] { KimObservable.class }, new Object[] { observableDeclaration });
  requireInitializedKnowledge();
    if (observableDeclaration.getSemantics() != null
        && observableDeclaration.getSemantics().getUrn() == null
        && observableDeclaration instanceof KimObservableImpl syntax)
      observableDeclaration = new KimObservableImpl(syntax, (path, node) -> node.finalizeDefinition());
    return declare(
        observableDeclaration,
        this.owl.requireOntology(observableDeclaration.getSemantics().getNamespace()),
        serviceScope());
  }

  public Observable declareObservable(
      KimObservable observableDeclaration, Map<String, Object> patternVariables) {
      if (candidateRuntime != null && !semanticOnly)
          return candidateRuntime.invoke("declareObservable", new Class<?>[] { KimObservable.class, Map.class }, new Object[] { observableDeclaration, patternVariables });

    if (observableDeclaration.getPattern() == null) {
      return declareObservable(observableDeclaration);
    }
    String urn = observableDeclaration.getPattern();
    for (var key : observableDeclaration.getPatternVariables()) {
      var value = patternVariables.get(key);
      if (value == null) {
        return null;
      }
      String valueCode =
          switch (value) {
            case KimConcept kimConcept -> /*"(" + */ kimConcept.getUrn() /* + ")"*/;
            case KimObservable kimConcept -> /*"(" + */ kimConcept.getUrn() /*+ ")"*/;
            case Concept kimConcept -> /*"(" + */ kimConcept.getUrn() /* + ")"*/;
            case Observable kimConcept -> /*"(" + */ kimConcept.getUrn() /* + ")"*/;
            case String string -> "\"" + Utils.Escape.forDoubleQuotedString(string, false) + "\"";
            default -> value.toString();
          };
      urn = urn.replace("$:" + key, valueCode);
    }
    return resolveObservable(urn);
  }

  //  @Override
  public Concept declareConcept(
      KimConcept observableDeclaration, Map<String, Object> patternVariables) {
      if (candidateRuntime != null && !semanticOnly)
          return candidateRuntime.invoke("declareConcept", new Class<?>[] { KimConcept.class, Map.class }, new Object[] { observableDeclaration, patternVariables });

    if (!observableDeclaration.isPattern()) {
      return declareConcept(observableDeclaration);
    }
    String urn = observableDeclaration.getUrn();
    for (var key : observableDeclaration.getPatternVariables()) {
      var value = patternVariables.get(key);
      if (value == null) {
        return null;
      }
      String valueCode =
          switch (value) {
            case KimConcept kimConcept -> "(" + kimConcept.getUrn() + ")";
            case KimObservable kimConcept -> "(" + kimConcept.getUrn() + ")";
            case Concept kimConcept -> "(" + kimConcept.getUrn() + ")";
            case Observable kimConcept -> "(" + kimConcept.getUrn() + ")";
            case String string -> "\"" + Utils.Escape.forDoubleQuotedString(string, false) + "\"";
            default -> value.toString();
          };
      urn = urn.replace("$:" + key, valueCode);
    }
    return resolveConcept(urn);
  }

  @Override
  public boolean compatible(Semantics o1, Semantics o2) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("compatible", new Class<?>[] { Semantics.class, Semantics.class }, new Object[] { o1, o2 });
    if (owl != null) owl.requireAvailableClosure();
  return compatible(o1, o2, 0);
  }

  // @Override
  public boolean compatible(Semantics o1, Semantics o2, int flags) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("compatible", new Class<?>[] { Semantics.class, Semantics.class, int.class }, new Object[] { o1, o2, flags });
    if (owl != null) owl.requireAvailableClosure();

    if (observerDistance(o1,o2)<0) return false;
    if (o1 == o2 || o1.equals(o2)) {
      return true;
    }

    boolean mustBeSameCoreType = (flags & REQUIRE_SAME_CORE_TYPE) != 0;
    boolean useRoleParentClosure = (flags & USE_ROLE_PARENT_CLOSURE) != 0;
    // boolean acceptRealmDifferences = (flags & ACCEPT_REALM_DIFFERENCES) != 0;

    // TODO unsupported
    boolean useTraitParentClosure = (flags & USE_TRAIT_PARENT_CLOSURE) != 0;

    /**
     * The check of fundamental types is only performed when both concepts are inside the worldview.
     */
    if (inWorldview(o1, o2)) {
      if ((!o1.is(SemanticType.OBSERVABLE) || !o2.is(SemanticType.OBSERVABLE))
          && !(o1.is(SemanticType.CONFIGURATION) && o2.is(SemanticType.CONFIGURATION))) {
        return false;
      }
    }

    /**
     * first compatibility check is a simple subsumption if o1 is abstract, or a full core
     * observability check if not.
     */
    if (o2.isAbstract()) {

      if (is(o2, o1)) {
        return false;
      }

    } else {

      Concept core1 = coreObservable(o1);
      Concept core2 = coreObservable(o2);

      if (core1 == null
          || core2 == null
          || !(mustBeSameCoreType ? core1.equals(core2) : is(core1, core2))) {
        return false;
      }
    }

    Concept ic1 = inherent(o1);
    Concept ic2 = inherent(o2);

    // same with inherency
    if (ic1 == null && ic2 != null) {
      return false;
    }
    if (ic1 != null && ic2 != null) {
      if (!compatible(ic1, ic2)) {
        return false;
      }
    }

    for (Concept t : traits(o2)) {
      boolean ok = hasTrait(o1, t);
      if (!ok && useTraitParentClosure) {
        ok = hasDirectTrait(o1, t);
      }
      if (!ok) {
        return false;
      }
    }

    for (Concept t : roles(o2)) {
      boolean ok = hasRole(o1, t);
      if (!ok && useRoleParentClosure) {
        ok = hasParentRole(o1, t);
      }
      if (!ok) {
        return false;
      }
    }

    return true;
  }

  /**
   * True if the concept comes from a loaded worldview. The alternative is that it comes from a core
   * imported ontology, and possibly (in the future) from a conceptual extent ontology.
   *
   * @param semantics
   * @return
   */
  private boolean inWorldview(Semantics... semantics) {
    for (Object o : semantics) {
      if (switch (o) {
        case ConceptImpl concept -> concept.getType().isEmpty();
        case KimConceptImpl concept -> concept.getType().isEmpty();
        case ObservableImpl observable -> observable.getSemantics().getType().isEmpty();
        case KimObservableImpl observable -> observable.getSemantics().getType().isEmpty();
        default -> false;
      }) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean hasParentRole(Semantics o1, Concept t) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("hasParentRole", new Class<?>[] { Semantics.class, Concept.class }, new Object[] { o1, t });
  for (Concept role : roles(o1)) {
      if (is(t, role)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean contextuallyCompatible(Semantics focus, Semantics context1, Semantics context2) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("contextuallyCompatible", new Class<?>[] { Semantics.class, Semantics.class, Semantics.class }, new Object[] { focus, context1, context2 });
  boolean ret = compatible(context1, context2, 0);
    if (!ret && occurrent(context1)) {
      ret = affectedBy(focus, context1);
      Concept itsContext = inherent(context1);
      if (!ret) {
        if (itsContext != null) {
          ret = compatible(itsContext, context2);
        }
      }
    }
    return ret;
  }

  @Override
  public boolean occurrent(Semantics context1) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("occurrent", new Class<?>[] { Semantics.class }, new Object[] { context1 });
  return context1 != null
        && (context1.is(SemanticType.PROCESS) || context1.is(SemanticType.EVENT));
  }

  @Override
  public Collection<Concept> affectedOrCreated(Semantics semantics) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("affectedOrCreated", new Class<?>[] { Semantics.class }, new Object[] { semantics });
  Set<Concept> ret = new HashSet<>(affected(semantics));
    ret.addAll(created(semantics));
    return ret;
  }

  @Override
  public Collection<org.integratedmodelling.klab.api.knowledge.SemanticInfluence> influences(
      Semantics semantics) {
      if (candidateRuntime != null && !semanticOnly)
          return candidateRuntime.invoke("influences", new Class<?>[] { Semantics.class }, new Object[] { semantics });
      var result = new java.util.LinkedHashSet<org.integratedmodelling.klab.api.knowledge.SemanticInfluence>();
    var query = semantics.asConcept();
    boolean occurrence = query.is(SemanticType.PROCESS) || query.is(SemanticType.EVENT);
    // Query both ends of descriptive restrictions. Keep the declaration direction in the result.
    var sources = occurrence ? List.of(query) : owl.listConcepts(false);
    for (var source : sources) {
      for (var kind : org.integratedmodelling.klab.api.knowledge.SemanticInfluence.Kind.values()) {
        if (kind.descriptive() == occurrence) continue;
        if (kind.descriptive() && !(source.is(SemanticType.QUALITY) || source.is(SemanticType.PREDICATE))) continue;
        var property = owl.getProperty(kind.property());
        if (property == null) continue;
        for (var target : owl.getRestrictedClasses(source, property)) {
          if (owl.getOntology(target.getNamespace()).isInternal()) continue;
          if (!occurrence && !source.equals(query) && !target.equals(query)
              && !is(query, target)) continue;
          result.add(new org.integratedmodelling.klab.api.knowledge.SemanticInfluence(
              effectiveConcept(source), effectiveConcept(target), kind, owl.restrictionProvenance(source, property, target)));
        }
      }
    }
    return result.stream().sorted(java.util.Comparator.comparing(
        (org.integratedmodelling.klab.api.knowledge.SemanticInfluence i) -> i.source().getUrn())
        .thenComparing(i -> i.kind().name()).thenComparing(i -> i.target().getUrn())).toList();
  }
  @Override
  public Collection<Concept> affected(Semantics semantics) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("affected", new Class<?>[] { Semantics.class }, new Object[] { semantics });
  Set<Concept> ret = new HashSet<>();
    if (!semantics.is(SemanticType.PROCESS) && !semantics.is(SemanticType.EVENT)) return ret;
    for (Concept c :
        this.owl.getRestrictedClasses(
            semantics.asConcept(), this.owl.getProperty(NS.AFFECTS_PROPERTY))) {
      if (!this.owl.getOntology(c.getNamespace()).isInternal()) {
        ret.add(c);
      }
    }
    return projectHierarchy(ret);
  }

  @Override
  public Collection<Concept> created(Semantics semantics) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("created", new Class<?>[] { Semantics.class }, new Object[] { semantics });
  Set<Concept> ret = new HashSet<>();
    for (Concept c :
        this.owl.getRestrictedClasses(
            semantics.asConcept(), this.owl.getProperty(NS.CREATES_PROPERTY))) {
      if (!this.owl.getOntology(c.getNamespace()).isInternal()) {
        ret.add(c);
      }
    }
    return projectHierarchy(ret);
  }

  @Override
  public boolean match(Semantics candidate, Semantics pattern) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("match", new Class<?>[] { Semantics.class, Semantics.class }, new Object[] { candidate, pattern });
    if (owl != null) owl.requireAvailableClosure();
  return observerDistance(candidate,pattern)>=0 && syntacticMatcher.match(candidate, pattern);
  }

  @Override
  public boolean match(Semantics candidate, Semantics pattern, Map<Concept, Concept> matches) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("match", new Class<?>[] { Semantics.class, Semantics.class, Map.class }, new Object[] { candidate, pattern, matches });
    if (owl != null) owl.requireAvailableClosure();
  if (matches == null) {
      throw new KlabIllegalArgumentException("The generic match result map cannot be null");
    }
    if (match(candidate, pattern) && !pattern.isAbstract()) {
      return true;
    }
    throw new KlabUnimplementedException(
        "Generic semantic matching with captured substitutions is not implemented");
  }

  @Override
  public <T extends Semantics> T concretize(T pattern, Map<Concept, Concept> concreteConcepts) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("concretize", new Class<?>[] { Semantics.class, Map.class }, new Object[] { pattern, concreteConcepts });
  Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(concreteConcepts, "concreteConcepts");
    String declaration = pattern.getUrn();
    for (var replacement : concreteConcepts.entrySet()) {
      declaration =
          declaration.replace(replacement.getKey().getUrn(), replacement.getValue().getUrn());
    }
    Semantics ret =
        pattern instanceof Observable
            ? resolveObservable(declaration)
            : resolveConcept(declaration);
    @SuppressWarnings("unchecked")
    T typed = (T) ret;
    return typed;
  }

  @Override
  public <T extends Semantics> T concretize(T pattern, List<Concept> concreteConcepts) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("concretize", new Class<?>[] { Semantics.class, List.class }, new Object[] { pattern, concreteConcepts });
  throw new KlabUnimplementedException(
        "Inference-based generic concretization is not implemented");
  }

  @Override
  public boolean affectedBy(Semantics affected, Semantics affecting) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("affectedBy", new Class<?>[] { Semantics.class, Semantics.class }, new Object[] { affected, affecting });
  for (Concept c : affected(affecting)) {
      if (is(affected, c)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean createdBy(Semantics affected, Semantics affecting) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("createdBy", new Class<?>[] { Semantics.class, Semantics.class }, new Object[] { affected, affecting });
  for (Concept c : created(affecting)) {
      if (is(affected, c)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Concept baseObservable(Semantics c) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("baseObservable", new Class<?>[] { Semantics.class }, new Object[] { c });

    if (c instanceof Concept concept) {
      return effectiveConcept(concept);
    }

    Collection<Concept> traits = directTraits(c);
    Collection<Concept> roles = directRoles(c);
    if (traits.isEmpty() && roles.isEmpty() && derived(c)) {
      return effectiveConcept(c.asConcept());
    }

    return baseObservable(parent(c));
  }

  @Override
  public Concept parent(Semantics c) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("parent", new Class<?>[] { Semantics.class }, new Object[] { c });
  Collection<Concept> parents = this.owl.getParents(c.asConcept());
    return parents.isEmpty() ? null : effectiveConcept(parents.iterator().next());
  }

  @Override
  public Concept compose(Collection<Concept> concepts, LogicalConnector connector) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("compose", new Class<?>[] { Collection.class, LogicalConnector.class }, new Object[] { concepts, connector });

    if (connector == LogicalConnector.EXCLUSION || connector == LogicalConnector.DISJOINT_UNION) {
      throw new KlabIllegalArgumentException(
          "Reasoner::compose: connector " + connector + " not " + "supported");
    }
    if (concepts.size() == 1) {
      return concepts.iterator().next();
    }
    if (concepts.size() > 1) {
      return connector == LogicalConnector.UNION
          ? this.owl.getUnion(
              concepts,
              this.owl.getOntology(concepts.iterator().next().getNamespace()),
              concepts.iterator().next().getType())
          : this.owl.getIntersection(
              concepts,
              this.owl.getOntology(concepts.iterator().next().getNamespace()),
              concepts.iterator().next().getType());
    }
    return owl.getNothing();
  }

  @Override
  public Concept rawObservable(Semantics observable) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("rawObservable", new Class<?>[] { Semantics.class }, new Object[] { observable });
  String def = observable.getMetadata().get(NS.CORE_OBSERVABLE_PROPERTY, String.class);
    Concept ret = observable.asConcept();
    if (def != null) {
      ret = resolveConcept(def);
    }
    return effectiveConcept(ret);
  }

  /*
   * --- non-API
   */

  /*
   * Record correspondence of core concept peers to worldview concepts. Called by
   * KimValidator for later use at namespace construction.
   */
  public void setWorldviewPeer(String coreConcept, String worldviewConcept) {
    if (candidateRuntime != null && !semanticOnly) throw new IllegalStateException("Compiler state requires a private semantic actor");
    coreConceptPeers.put(worldviewConcept, coreConcept);
  }

  public Concept build(
      KimConceptStatement concept,
      Ontology ontology,
      KimConceptStatement kimObject,
      Scope monitor) {
    if (candidateRuntime != null && !semanticOnly) throw new IllegalStateException("Compiler state requires a private semantic actor");

    try {

      var authorityErrors =
          org.integratedmodelling.klab.runtime.language.KimWorldviewValidator.authorityErrors(concept);
      if (!authorityErrors.isEmpty())
        throw new KlabValidationException(String.join("; ", authorityErrors));

      if (concept.isAlias() || concept.getUpperConceptDefined() != null) {
        installAlias(concept, ontology, monitor);
        return null;
      }

      Concept ret = buildInternal(concept, ontology, kimObject, monitor);

      if (ret != null) {

        Concept upperConceptDefined = null;
        if (concept.getDeclaredParent() == null) {
          Concept parent = null;
          if (concept.getUpperConceptDefined() != null) {
            upperConceptDefined = parent = this.owl.getConcept(concept.getUpperConceptDefined());
            if (parent == null) {
              monitor.error(
                  "Core concept " + concept.getUpperConceptDefined() + " is " + "unknown", concept);
            }
          } else {
            parent = this.owl.getCoreOntology().getCoreType(concept.getType());
            if (coreConceptPeers.containsKey(ret.toString())) {
              // ensure that any non-trivial core inheritance is dealt with
              // appropriately
              parent = this.owl.getCoreOntology().alignCoreInheritance(ret);
            }
          }

          if (parent != null) {
            ontology.add(
                Axiom.SubClass(parent.getNamespace() + ":" + parent.getName(), ret.getName()));
          }
        }

        createProperties(ret, ontology);
        ontology.define();

        if (coreConceptPeers.containsKey(ret.toString()) && upperConceptDefined != null
        /* && "true".equals(upperConceptDefined.getMetadata().get(NS.IS_CORE_KIM_TYPE,
        "false")*/ ) {
          // TODO revise - use core ontology statements only
          this.owl.getCoreOntology().setAsCoreType(ret);
        }
      }

      if (!stagingDeclarationStructure && ret != null && concept.getAuthorityRequired() != null) {
        configureAuthorityBinding(new Authority.ConfigurationRequest(
            worldview.getUrn(), concept.getAuthorityRequired(), ret.getUrn(),
            concept.getAuthorityParameters()), monitor);
      }
      return ret;

    } catch (Throwable e) {
      monitor.error(e, concept);
    }
    return null;
  }

  private void installAlias(KimConceptStatement concept, Ontology ontology, Scope monitor) {
    /*
     * can only have 'is' or 'equals' X; for core concepts 'is' means 'equals', and we use the
     * statement to establish the semantic type.
     */
    Concept parent = null;
    if (concept.getUpperConceptDefined() != null) {
      parent = this.owl.getConcept(concept.getUpperConceptDefined());
      if (parent == null) {
        monitor.error("Core concept " + concept.getUpperConceptDefined() + " is unknown", concept);
      } else {
        if(parent.is(SemanticType.RELATIONSHIP)) {
          var inherited=org.integratedmodelling.klab.api.knowledge.RelationshipAxes.of(parent.getType());
          var own=org.integratedmodelling.klab.runtime.language.RelationshipDeclarationSupport.authored(concept);
          inherited.merge(own); // Opposite choices fail before canonical type mutation.
          if(own.nature()!=org.integratedmodelling.klab.api.knowledge.RelationshipAxes.Nature.UNSPECIFIED && own.nature()!=inherited.nature()
              || own.orientation()!=org.integratedmodelling.klab.api.knowledge.RelationshipAxes.Orientation.UNSPECIFIED && own.orientation()!=inherited.orientation())
            throw new KlabValidationException("RELATIONSHIP_ALIAS_AXIS_CONFLICT: core alias cannot specialize the canonical target");
        }
        var canonicalTypes=org.integratedmodelling.klab.runtime.language.CoreAliasTypeSupport.types(parent.getUrn(),parent.getType());
        var declarationTypes=org.integratedmodelling.klab.runtime.language.CoreAliasTypeSupport.types(parent.getUrn(),concept.getType());
        parent.getType().clear();parent.getType().addAll(canonicalTypes);parent.getType().addAll(declarationTypes);
      }
    } else if (concept.getDeclaredParent() != null) {
      parent = declareConcept(concept.getDeclaredParent());
    }

    if (parent != null) {
      if(parent.is(SemanticType.RELATIONSHIP) && concept.getUpperConceptDefined()==null)
        org.integratedmodelling.klab.runtime.language.RelationshipDeclarationSupport.resolve(concept,
            org.integratedmodelling.klab.api.knowledge.RelationshipAxes.of(parent.getType()),false);
      var annotated = new ConceptImpl((ConceptImpl) parent);
      annotated.setAnnotations(
          AnnotationCollector.merge(parent.getAnnotations(), concept.getAnnotations()));
      ontology.addDelegateConcept(concept.getUrn(), ontology.getName(), annotated);
      if (concept.getUpperConceptDefined() != null
          || CoreOntology.CORE_ONTOLOGY_NAME.equals(parent.getNamespace())) {
        var source = worldview == null ? null : worldview.getOntologies().stream()
            .filter(o -> o.getUrn().equals(ontology.getName())).findFirst().orElse(null);
        owl.coreAliasDocumentation().replace(ontology, parent, annotated, concept, source,
            worldview == null ? concept.getProjectName() : worldview.getWorldviewId());
      }
    }
  }

  private boolean stagingDeclarationStructure;
  private Map<String,Long> declarationStagingMetrics = Map.of();
  public Map<String,Long> declarationStagingMetrics() { return declarationStagingMetrics; }

  private void compileStagedOntologies(List<KimOntology> ontologies, Scope scope) {
    var scopes = new LinkedHashMap<KimOntology,Scope>();
    ontologies.forEach(ontology -> scopes.put(ontology,scope));
    compileStagedOntologies(scopes);
  }
  private void compileStagedOntologies(List<KimOntology> ontologies, Scope scope,
      CandidatePublicationCoordinator.Cancellation cancellation) {
    var scopes = new LinkedHashMap<KimOntology,Scope>();
    ontologies.forEach(ontology -> scopes.put(ontology,scope));
    compileStagedOntologies(scopes,cancellation);
  }
  private void compileStagedOntologies(Map<KimOntology,Scope> scopes) {
    compileStagedOntologies(scopes,NO_COMPILATION_CANCELLATION);
  }

  /** Only private compilation reaches this shared full/incremental staging primitive. */
  private void compileStagedOntologies(Map<KimOntology,Scope> scopes, CandidatePublicationCoordinator.Cancellation cancellation) {
    org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry registry;
    compilationCheckpoint(cancellation,CandidateTrace.Phase.DECLARATION_REGISTRY);
    try (var phase = CandidateTrace.phase(CandidateTrace.Phase.DECLARATION_REGISTRY,null)) {
      registry = new org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry(scopes.keySet());
      if (!registry.errors().isEmpty()) throw new KlabValidationException(registry.errors().toString());
    }
    compilationCheckpoint(cancellation,CandidateTrace.Phase.DECLARATION_REGISTRY);
    var metricsBeforeStaging=owl.compilationMetrics();
    try (var deferred = owl.stageDeclarations()) {
      // Typed names are actual source declarations, private to this candidate actor.
      compilationCheckpoint(cancellation,CandidateTrace.Phase.NAMED_DECLARATIONS);
      try (var phase = CandidateTrace.phase(CandidateTrace.Phase.NAMED_DECLARATIONS,null)) {
        for (var entry : registry.ordered()) {
          compilationCheckpoint(cancellation,CandidateTrace.Phase.NAMED_DECLARATIONS);
          var statement=entry.statement(); var ontology=owl.requireOntology(entry.ontology().getUrn());
          if (statement.getUpperConceptDefined()!=null) {
            installAlias(statement,ontology,scopes.get(entry.ontology()));
          } else if (!statement.isAlias()) {
            ontology.add(Axiom.ClassAssertion(statement.getUrn(),new HashSet<>(statement.getType())));
            ontology.define();
          }
        }
      }
      compilationCheckpoint(cancellation,CandidateTrace.Phase.NAMED_DECLARATIONS);
      // Authority roots must exist before parent expressions can resolve authority identities.
      compilationCheckpoint(cancellation,CandidateTrace.Phase.AUTHORITY_CONFIGURATION);
      try (var phase = CandidateTrace.phase(CandidateTrace.Phase.AUTHORITY_CONFIGURATION,null)) {
        for (var entry : registry.ordered()) if (entry.statement().getAuthorityRequired()!=null) {
          compilationCheckpoint(cancellation,CandidateTrace.Phase.AUTHORITY_CONFIGURATION);
          var statement=entry.statement();var owner=owl.getConcept(entry.urn());
          configureAuthorityBinding(new Authority.ConfigurationRequest(worldview.getUrn(),
              statement.getAuthorityRequired(),owner.getUrn(),statement.getAuthorityParameters()),scopes.get(entry.ontology()));
        }
      }
      compilationCheckpoint(cancellation,CandidateTrace.Phase.AUTHORITY_CONFIGURATION);
      stagingDeclarationStructure=true;
      try {
        compilationCheckpoint(cancellation,CandidateTrace.Phase.DECLARATION_STRUCTURE);
        try (var phase = CandidateTrace.phase(CandidateTrace.Phase.DECLARATION_STRUCTURE,null)) {
          for (var entry : registry.ordered()) if (entry.statement().getUpperConceptDefined()==null) {
          compilationCheckpoint(cancellation,CandidateTrace.Phase.DECLARATION_STRUCTURE);
            var ontology=owl.getOntology(entry.ontology().getUrn());var scope=scopes.get(entry.ontology());
            if (entry.statement().isAlias()) installAlias(entry.statement(),ontology,scope);
            else if (entry.enclosing()==null) build(entry.statement(),ontology,null,scope);
            else buildInternal(entry.statement(),ontology,entry.enclosing(),scope);
          }
        }
        compilationCheckpoint(cancellation,CandidateTrace.Phase.DECLARATION_STRUCTURE);
      } finally { stagingDeclarationStructure=false; }
      // ABOUT in a preamble needs the complete named domain hierarchy, not just
      // typed signatures. Its endpoint and grounding contracts remain unchanged.
      compilationCheckpoint(cancellation,CandidateTrace.Phase.DOMAIN_BINDING);
      try (var phase = CandidateTrace.phase(CandidateTrace.Phase.DOMAIN_BINDING,null)) {
        for (var entry : scopes.entrySet()) {
          compilationCheckpoint(cancellation,CandidateTrace.Phase.DOMAIN_BINDING);
          var source = entry.getKey();
          declarationDomains.put(source.getUrn(),
              org.integratedmodelling.klab.services.reasoner.owl.WorldviewDomainSupport.validate(source,this::declareConcept));
        }
        for (var entry : scopes.entrySet()) {
          compilationCheckpoint(cancellation,CandidateTrace.Phase.DOMAIN_BINDING);
          var source = entry.getKey();
          org.integratedmodelling.klab.services.reasoner.owl.WorldviewDomainSupport.bind(
              owl,source,declarationDomains.get(source.getUrn()));
        }
      }
      compilationCheckpoint(cancellation,CandidateTrace.Phase.DOMAIN_BINDING);
      var clauses=new ArrayList<org.integratedmodelling.klab.services.reasoner.owl.WorldviewDescriptionStaging.Entry>();
      compilationCheckpoint(cancellation,CandidateTrace.Phase.AUTHORED_CLAUSES);
      try (var phase = CandidateTrace.phase(CandidateTrace.Phase.AUTHORED_CLAUSES,null)) {
        for (var entry : registry.ordered()) if (!entry.statement().isAlias()
            && entry.statement().getUpperConceptDefined()==null) {
          compilationCheckpoint(cancellation,CandidateTrace.Phase.AUTHORED_CLAUSES);
          var ontology=owl.getOntology(entry.ontology().getUrn());var owner=owl.getConcept(entry.urn());
          var scope=scopes.get(entry.ontology());
          java.util.function.Function<KimConcept,Concept> resolve=operand->declare(operand,ontology,scope);
          try {
            org.integratedmodelling.klab.services.reasoner.owl.WorldviewDeclarationSupport.compileAuthored(
                owl,ontology,owner,entry.statement(),resolve);
            clauses.add(new org.integratedmodelling.klab.services.reasoner.owl.WorldviewDescriptionStaging.Entry(
                ontology,entry.ontology(),owner,entry.statement(),resolve,scope));
            if (!entry.statement().getEmergenceTriggers().isEmpty())
              registerEmergent(owner,entry.statement().getEmergenceTriggers().stream().map(resolve).toList());
            if (org.integratedmodelling.klab.services.reasoner.owl.WorldviewDeclarationSupport.hasDeferredValue(entry.statement()))
              scope.warn("DESCRIPTION_VALUE_UNRESOLVED: typed value semantics remain unresolved",entry.statement());
          } catch (RuntimeException error) { scope.error(error,entry.statement()); }
        }
      }
      compilationCheckpoint(cancellation,CandidateTrace.Phase.AUTHORED_CLAUSES);
      compilationCheckpoint(cancellation,CandidateTrace.Phase.DESCRIPTION_RECONCILE);
      try (var phase = CandidateTrace.phase(CandidateTrace.Phase.DESCRIPTION_RECONCILE,null)) {
        declarationStagingMetrics=org.integratedmodelling.klab.services.reasoner.owl.WorldviewDescriptionStaging.reconcile(owl,clauses,
            () -> compilationCheckpoint(cancellation,CandidateTrace.Phase.DESCRIPTION_RECONCILE));
      }
      compilationCheckpoint(cancellation,CandidateTrace.Phase.DESCRIPTION_RECONCILE);
      compilationCheckpoint(cancellation,CandidateTrace.Phase.ONTOLOGY_REGISTER);
      try (var phase = CandidateTrace.phase(CandidateTrace.Phase.ONTOLOGY_REGISTER,null)) {
        for (var entry : scopes.entrySet()) {
          compilationCheckpoint(cancellation,CandidateTrace.Phase.ONTOLOGY_REGISTER);
          var source = entry.getKey(); var scope = entry.getValue();
          try { org.integratedmodelling.klab.services.reasoner.owl.DomainInterpretationSupport.requireEffectiveGrounding(
              owl,declarationDomains.get(source.getUrn())); }
          catch (RuntimeException error) {scope.error(error,source);}
          for(var statement:source.getStatements()) {
            compilationCheckpoint(cancellation,CandidateTrace.Phase.ONTOLOGY_REGISTER);
            tagConceptService(statement,source.getServiceId());
          }
          owl.registerWithReasoner(source);rememberOntologySource(source);
        }
      }
      compilationCheckpoint(cancellation,CandidateTrace.Phase.ONTOLOGY_REGISTER);
      var measured=new LinkedHashMap<String,Long>(declarationStagingMetrics);
      var metricsAfterStaging=owl.compilationMetrics();
      for(var name:List.of("reasonerCreations","actualFlushes","satisfiabilityQueries","entailmentQueries"))
        measured.put("staged_"+name,metricsAfterStaging.get(name)-metricsBeforeStaging.get(name));
      declarationStagingMetrics=Map.copyOf(measured);
    } catch (RuntimeException error) { throw error; }
    catch (Exception error) { throw new KlabValidationException(error); }
    // Compilation may resolve names before description-derived bearer closure is
    // complete. Do not publish those provisional eligibility/subsumption views.
    invalidateSemanticCaches();
  }

  private Concept buildInternal(
      final KimConceptStatement concept,
      Ontology ontology,
      KimConceptStatement kimObject,
      final Scope monitor) {

    if (concept.isAlias() || concept.getUpperConceptDefined() != null) {
      installAlias(concept, ontology, monitor);
      return null; // An alias is not a child class and must not receive a subclass axiom.
    }

    Concept main = null;
    String mainId = concept.getUrn();
    Concept resolvedParent = null;
    var declarationType = new java.util.HashSet<>(concept.getType());
    if (concept.isGenericQuality() && concept.getDeclaredParent() != null) {
      resolvedParent = declare(concept.getDeclaredParent(), ontology, monitor);
      if (resolvedParent == null || resolvedParent.is(SemanticType.NOTHING)) {
        monitor.error("Unresolved parent expression", concept.getDeclaredParent());
        return null;
      }
      var flags = java.util.EnumSet.of(SemanticType.ABSTRACT, SemanticType.SEALED,
          SemanticType.SUBJECTIVE, SemanticType.DENIABLE);
      declarationType.retainAll(flags);
      var inherited = new java.util.HashSet<>(resolvedParent.getType());
      inherited.removeAll(flags);
      declarationType.addAll(inherited);
      // Staged signatures may still carry a legacy coarse kind. This declaration's resolved
      // parent determines its final kind; merging would retain competing QUANTITY/RATIO flags.
      var predeclared=ontology.getConcept(mainId);
      if(predeclared!=null) {
        predeclared.getType().clear();predeclared.getType().addAll(declarationType);
      }
    }

    if(declarationType.contains(SemanticType.RELATIONSHIP)) {
      var parents=org.integratedmodelling.klab.runtime.language.RelationshipDeclarationSupport.inherited(
          concept.getDeclaredParent(),x->{var c=declare(x,ontology,monitor);return c==null?Set.of():c.getType();});
      var axisAncestry=new ArrayList<Concept>();
      if(kimObject!=null) {
        var enclosing=ontology.getConcept(kimObject.getUrn());
        if(enclosing!=null) {
          parents=parents.merge(org.integratedmodelling.klab.api.knowledge.RelationshipAxes.of(enclosing.getType()));
          axisAncestry.add(enclosing);
        }
      }
      var axes=org.integratedmodelling.klab.runtime.language.RelationshipDeclarationSupport.resolve(concept,parents,true);
      if(concept.getDeclaredParent()!=null) {
        var parentConcept=declare(concept.getDeclaredParent(),ontology,monitor);
        if(parentConcept!=null)axisAncestry.add(parentConcept);
      }
      if(!owl.permitsRelationshipAxes(axisAncestry,axes))
        throw new IllegalArgumentException("RELATIONSHIP_AXIS_CONFLICT: no shared compatible inherited boolean branch");
      axes.apply(declarationType);
      var predeclared=ontology.getConcept(mainId);
      if(predeclared!=null)axes.apply(predeclared.getType());
    }

    ontology.add(
        Axiom.ClassAssertion(
            mainId,
            declarationType.stream()
                .map((c) -> SemanticType.valueOf(c.name()))
                .collect(Collectors.toSet())));

    // set the k.IM definition
    ontology.add(
        Axiom.AnnotationAssertion(
            mainId, NS.CONCEPT_DEFINITION_PROPERTY, ontology.getName() + ":" + concept.getUrn()));

    // and the reference name
    ontology.add(
        Axiom.AnnotationAssertion(
            mainId,
            NS.REFERENCE_NAME_PROPERTY,
            OWL.getCleanFullId(ontology.getName(), concept.getUrn())));

    if (declarationType.contains(SemanticType.NOTHING)) {
      monitor.error("Declaration is inconsistent or uses unknown concepts", concept);
      return null;
    }

    if (concept.getDocstring() != null) {
      ontology.add(
          Axiom.AnnotationAssertion(mainId, Vocabulary.RDFS_COMMENT, concept.getDocstring()));
    }

    if (kimObject == null) {
      ontology.add(Axiom.AnnotationAssertion(mainId, NS.BASE_DECLARATION, "true"));
    }

    /*
     * basic attributes subjective deniable internal uni/bidirectional
     * (relationship)
     */
    if (concept.isAbstract() || concept.getNamespace().equals(CoreOntology.CORE_ONTOLOGY_NAME)) {
      ontology.add(Axiom.AnnotationAssertion(mainId, CoreOntology.NS.IS_ABSTRACT, "true"));
    }

    if (concept.isSubjective()) {
      ontology.add(Axiom.AnnotationAssertion(mainId, CoreOntology.NS.IS_SUBJECTIVE, "true"));
    }
    if (kimObject != null)
      ontology.add(Axiom.SubClass(kimObject.getNamespace() + ":" + kimObject.getUrn(), mainId));
    ontology.define();
    main = ontology.getConcept(mainId);
    main.getMetadata().putAll(concept.getMetadata());
    org.integratedmodelling.klab.services.reasoner.owl.RelationshipOntologySupport.compile(owl,ontology,main,concept);
    ((ConceptImpl) main).setAnnotations(AnnotationCollector.merge(concept.getAnnotations()));

    org.integratedmodelling.klab.services.reasoner.owl.WorldviewDeclarationSupport.compileDeniability(
        owl, ontology, main, concept);

    indexer.index(concept);

    if (concept.getDeclaredParent() != null) {

      //            List<Concept> concepts = new ArrayList<>();
      //            for (KimConcept pdecl : parent.getConcepts()) {
      Concept declared = resolvedParent == null
          ? declare(concept.getDeclaredParent(), ontology, monitor) : resolvedParent;
      if (declared == null || declared.is(SemanticType.NOTHING)) {
        monitor.error(
            "parent declaration "
                + concept.getDeclaredParent().getUrn()
                + " does not identify known concepts",
            concept.getDeclaredParent());
        return null;
      } else {
        ontology.add(Axiom.SubClass(declared.getNamespace() + ":" + declared.getName(), mainId));
        ((ConceptImpl) main)
            .setAnnotations(
                AnnotationCollector.merge(declared.getAnnotations(), concept.getAnnotations()));
      }
      //                concepts.add(declared);
      //            }
      //
      //            if (concepts.size() == 1) {
      //
      //            }
      /* else {
          Concept expr = null;
          switch (parent.getConnector()) {
              case INTERSECTION:
                  expr = this.owl.getIntersection(concepts, ontology, concepts.get(0).getType());
                  break;
              case UNION:
                  expr = this.owl.getUnion(concepts, ontology, concepts.get(0).getType());
                  break;
              case FOLLOWS:
                  expr = this.owl.getConsequentialityEvent(concepts, ontology);
                  break;
              default:
                  // won't happen
                  break;
          }
          if (concept.isAlias()) {
              ontology.addDelegateConcept(mainId, ontology.getName(), expr);
          } else {
              ontology.add(Axiom.SubClass(expr.getNamespace() + ":" + expr.getName(), mainId));
          }
      }*/
      ontology.define();
    }

    for (KimConcept inherited : concept.getTraitsInherited()) {
      Concept trait = declare(inherited, ontology, monitor);
      if (trait == null || trait.is(SemanticType.NOTHING)) {
        monitor.error(
            "inherited " + inherited.getName() + " does not identify known concepts", inherited);
        // return null;
      } else {
        this.owl.addTrait(main, trait, ontology);
      }
    }

    org.integratedmodelling.klab.services.reasoner.owl.WorldviewDomainSupport.bind(
        owl, ontology.getName(), concept, declarationDomains.get(ontology.getName()));
    if (!stagingDeclarationStructure)
      org.integratedmodelling.klab.services.reasoner.owl.WorldviewDeclarationSupport.compile(
          owl, ontology, main, concept, operand -> declare(operand, ontology, monitor));
    if (!stagingDeclarationStructure && org.integratedmodelling.klab.services.reasoner.owl.WorldviewDeclarationSupport.hasDeferredValue(concept))
      monitor.warn("DESCRIPTION_VALUE_UNRESOLVED: describes-as source is retained, but typed "
          + "value/unit/category/modality validation and enforcement are not yet implemented", concept);

    if (!stagingDeclarationStructure) for (var child : concept.getChildren()) {
      try {
        // KimConceptStatement chobj = kimObject == null ? null : new
        // KimConceptStatement((IKimConceptStatement) child);
        Concept childConcept =
            buildInternal((KimConceptStatement) child, ontology, concept, /*
                         * monitor instanceof ErrorNotifyingMonitor ? ((ErrorNotifyingMonitor)
                         * monitor).contextualize(child) :
                         */ monitor);
        if (childConcept != null) {
          ontology.add(Axiom.SubClass(mainId, childConcept.getName()));
          ontology.define();
        }
        // kimObject.getChildren().add(chobj);
      } catch (Throwable e) {
        monitor.error(e, child);
      }
    }

    if (concept.isChildrenDisjoint() && concept.getChildren().size() > 1) {
      ontology.define(
          List.of(
              Axiom.DisjointClasses(
                  concept.getChildren().stream()
                      .map(child -> concept.getNamespace() + ":" + child.getUrn())
                      .toArray(String[]::new))));
    }

    if (!stagingDeclarationStructure && !concept.getEmergenceTriggers().isEmpty()) {
      List<Concept> triggers = new ArrayList<>();
      for (KimConcept trigger : concept.getEmergenceTriggers()) {
        triggers.add(declare(trigger, ontology, monitor));
      }
      registerEmergent(main, triggers);
    }

    // if (kimObject != null) {
    // kimObject.set(main);
    // }

    return main;
  }

  /**
   * Arrange a set of concepts into the collection of the most specific members of each concept
   * hierarchy therein.
   *
   * <p>TODO/FIXME not exposed, as I'm not sure this one is useful or intuitive enough.
   *
   * @param cc
   * @return least general
   */
  public Collection<Concept> leastGeneral(Collection<Concept> cc) {
    if (candidateRuntime != null && !semanticOnly) return candidateRuntime.invoke("leastGeneral", new Class<?>[]{Collection.class}, new Object[]{cc});

    Set<Concept> ret = new HashSet<>();
    for (Concept c : cc) {
      List<Concept> ccs = new ArrayList<>(ret);
      boolean set = false;
      for (Concept kn : ccs) {
        if (is(c, kn)) {
          ret.remove(kn);
          ret.add(c);
          set = true;
        } else if (is(kn, c)) {
          set = true;
        }
      }
      if (!set) {
        ret.add(c);
      }
    }
    return projectHierarchy(ret);
  }

  /**
   * Return the most specific ancestor that the concepts in the passed collection have in common, or
   * null if none.
   *
   * @param cc
   * @return
   */
  @Override
  public Concept leastGeneralCommon(Collection<Concept> cc) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("leastGeneralCommon", new Class<?>[] { Collection.class }, new Object[] { cc });

    Concept ret = null;
    Iterator<Concept> ii = cc.iterator();

    if (ii.hasNext()) {

      ret = ii.next();

      if (ret != null)
        while (ii.hasNext()) {
          ret = this.owl.getLeastGeneralCommonConcept(ret, ii.next());
          if (ret == null) break;
        }
    }

    return effectiveConcept(ret);
  }

  /*
   * Register the triggers and each triggering concept in the emergence map.
   */
  public boolean registerEmergent(Concept configuration, Collection<Concept> triggers) {
    if (candidateRuntime != null && !semanticOnly) return candidateRuntime.invoke("registerEmergent", new Class<?>[]{Concept.class, Collection.class}, new Object[]{configuration, triggers});

    if (!configuration.isAbstract()) {

      if (this.emergent.containsKey(configuration)) {
        return true;
      }
      Emergence descriptor = new Emergence();
      descriptor.emergentObservable = configuration;
      descriptor.triggerObservables.addAll(triggers);
      descriptor.namespaceId = configuration.getNamespace();
      this.emergent.put(configuration, descriptor);

      for (Concept trigger : triggers) {
        for (Concept tr : this.owl.flattenOperands(trigger)) {
          Set<Emergence> es = emergence.get(tr);
          if (es == null) {
            es = new HashSet<>();
            emergence.put(tr, es);
          }
          es.add(descriptor);
        }
      }

      return true;
    }

    return false;
  }

  private void createProperties(Concept ret, Ontology ns) {

    String pName = null;
    String pProp = null;
    if (ret.is(SemanticType.ATTRIBUTE)) {
      // hasX
      pName = "has" + ret.getName();
      pProp = NS.HAS_ATTRIBUTE_PROPERTY;
    } else if (ret.is(SemanticType.REALM)) {
      // inX
      pName = "in" + ret.getName();
      pProp = NS.HAS_REALM_PROPERTY;
    } else if (ret.is(SemanticType.IDENTITY)) {
      // isX
      pName = "is" + ret.getName();
      pProp = NS.HAS_IDENTITY_PROPERTY;
    }
    if (pName != null) {
      ns.add(Axiom.ObjectPropertyAssertion(pName));
      ns.add(Axiom.ObjectPropertyRange(pName, ret.getName()));
      ns.add(Axiom.SubObjectProperty(pProp, pName));
      ns.add(
          Axiom.AnnotationAssertion(
              ret.getName(), NS.TRAIT_RESTRICTING_PROPERTY, ns.getName() + ":" + pName));
    }
  }

  private Concept declare(KimConcept concept, Ontology ontology, Scope monitor) {
    var resolved = declareInternal(concept, ontology, monitor);
    if (resolved == null) return null;
    var result = new ConceptImpl((ConceptImpl) resolved);
    result.setAnnotations(AnnotationCollector.collect(concept, owl::getConcept));
    return result;
  }

  private Concept declareInternal(KimConcept concept, Ontology ontology, Scope monitor) {
    var resolved = compileInternal(concept, ontology, monitor);
    return resolved == null
        ? null
        : projectExpressionStatus((ConceptImpl) resolved, concept);
  }

  private Concept compileInternal(KimConcept concept, Ontology ontology, Scope monitor) {

    Concept main = null;

    var existing = owl.getConcept(concept.getUrn());
    if (existing != null) {
      owl.monotoneRefinements().register(existing, concept, this);
      return ((ConceptImpl) existing).withSelectors(concept.getType());
    }

    if (concept.getObservable() != null) {
      main = declareInternal(concept.getObservable(), ontology, monitor);
    } else if (concept.getName() != null) {
      main = this.owl.getConcept(concept.getName());
    }

    if (main == null) {
      return null;
    }

    var builder = SemanticsBuilder.create(main, this, monitor);

    // TODO annotations

    if (concept.getSemanticModifier() != null) {
      Concept other = null;
      if (concept.getComparisonConcept() != null) {
        other = declareInternal(concept.getComparisonConcept(), ontology, monitor);
      }
      builder.as(
          concept.getSemanticModifier(), other == null ? (Concept[]) null : new Concept[] {other});
    }
    // Unary operators wrap the full operand, including its own collectivity. This flag belongs
    // to the outer expression and must not erase the operand before it is wrapped.
    builder.collective(concept.isCollective());

    //        if (concept.getDistributedInherent() != null) {
    //            builder.withDistributedInherency(true);
    //        }

    /*
     * transformations first
     */

    if (concept.getInherent() != null) {
      Concept c = declareInternal(concept.getInherent(), ontology, monitor);
      if (c != null) {
        builder.of(c);
      }
    }
    //        if (concept.getContext() != null) {
    //            Concept c = declareInternal(concept.getContext(), ontology, monitor);
    //            if (c != null) {
    //                if (SemanticRole.CONTEXT.equals(concept.getDistributedInherent())) {
    //                    builder.of(c);
    //                } else {
    //                    builder.within(c);
    //                }
    //            }
    //        }
    if (concept.getCompresent() != null) {
      Concept c = declareInternal(concept.getCompresent(), ontology, monitor);
      if (c != null) {
        builder.with(c);
      }
    }
    if (concept.getCausant() != null) {
      Concept c = declareInternal(concept.getCausant(), ontology, monitor);
      if (c != null) {
        builder.withCausant(c);
      }
    }
    if (concept.getCaused() != null) {
      Concept c = declareInternal(concept.getCaused(), ontology, monitor);
      if (c != null) {
        builder.withCaused(c);
      }
    }
    if (concept.getGoal() != null) {
      Concept c = declareInternal(concept.getGoal(), ontology, monitor);
      if (c != null) {
        //                if (SemanticRole.GOAL.equals(concept.getDistributedInherent())) {
        //                    builder.of(c);
        //                } else {
        builder.withGoal(c);
        //                }
      }
    }
    if (concept.getCooccurrent() != null) {
      Concept c = declareInternal(concept.getCooccurrent(), ontology, monitor);
      if (c != null) {
        builder.withCooccurrent(c);
      }
    }
    if (concept.getAdjacent() != null) {
      Concept c = declareInternal(concept.getAdjacent(), ontology, monitor);
      if (c != null) {
        builder.withAdjacent(c);
      }
    }
    if (concept.getRelationshipSource() != null || concept.getRelationshipTarget() != null) {
      if (concept.getRelationshipSource() == null || concept.getRelationshipTarget() == null)
        throw new KlabValidationException("linking requires both source and target");
      Concept source = declareInternal(concept.getRelationshipSource(), ontology, monitor);
      Concept target = declareInternal(concept.getRelationshipTarget(), ontology, monitor);
      if (source == null
          || target == null
          || source.is(SemanticType.NOTHING)
          || target.is(SemanticType.NOTHING))
        throw new KlabValidationException("Unresolved linking endpoint");
      builder.linking(source, target);
    }

    for (KimConcept c : concept.getTraits()) {
      Concept trait = declareInternal(c, ontology, monitor);
      if (trait != null) {
        builder.withTrait(trait);
      }
    }

    for (KimConcept c : concept.getRoles()) {
      Concept role = declareInternal(c, ontology, monitor);
      if (role != null) {
        builder.withRole(role);
      }
    }

    Concept ret = null;
    try {

      ret = builder.buildConcept();

      /*
       * handle unions and intersections
       */
      if (!concept.getOperands().isEmpty()) {
        List<Concept> concepts = new ArrayList<>();
        concepts.add(ret);
        for (KimConcept op : concept.getOperands()) {
          concepts.add(declareInternal(op, ontology, monitor));
        }
        ret =
            concept.is(SemanticType.INTERSECTION)
                ? this.owl.getIntersection(
                    concepts, ontology, concept.getOperands().get(0).getType())
                : this.owl.getUnion(concepts, ontology, concept.getOperands().getFirst().getType());

        if (concept.getAbout().isEmpty())
          this.owl.setConceptUrn((ConceptImpl) ret, concept.getUrn());
        ret.getType()
            .add(
                concept.is(SemanticType.INTERSECTION)
                    ? SemanticType.INTERSECTION
                    : SemanticType.UNION);
      }

      // Subject matter restricts the complete domain expression, including a union.
      if (!concept.getAbout().isEmpty()) {
        ret = org.integratedmodelling.klab.services.reasoner.owl.DomainInterpretationSupport.compose(
            owl, ret, concept, target -> declareInternal(target, ontology, monitor));
      }

      //      // set the k.IM definition in the concept.This must only happen if the
      //      // concept wasn't there - within build() and repeat if mods are made
      //      if (builder.axiomsAdded()) {
      //
      //        this.owl
      //            .getOntology(ret.getNamespace())
      //            .define(
      //                Collections.singletonList(
      //                    Axiom.AnnotationAssertion(
      //                        ret.getName(), NS.CONCEPT_DEFINITION_PROPERTY, concept.getUrn())));
      //
      //        // consistency check
      //        if (!satisfiable(ret)) {
      //          ret.getType().add(SemanticType.NOTHING);
      //          monitor.error(
      //              "the definition of this concept has logical errors and " + "is inconsistent",
      //              concept);
      //        }
      //
      //        /** Now that the URN is set, put away the description */
      //        registerConcept(ret);
      //      }

    } catch (org.integratedmodelling.klab.api.exceptions.KlabValidationException e) {
      throw e;
    } catch (RuntimeException e) {
      // The caller binds the diagnostic to this source occurrence. Do not publish it twice.
      var failure = new org.integratedmodelling.klab.api.exceptions.KlabValidationException(
          Notification.conciseMessage(e));
      failure.initCause(e);
      throw failure;
    }

    if (concept.isNegated()) {
      ret = negated(ret);
    }

    return ret == null ? null : ((ConceptImpl) ret).withSelectors(concept.getType());
  }

  public Observable declare(KimObservable concept, Ontology declarationOntology, Scope monitor) {
    if (candidateRuntime != null && !semanticOnly) throw new IllegalStateException("Compiler state requires a private semantic actor");

    if (concept.getNonSemanticType() != null) {
      Concept nsmain =
          this.owl.getNonsemanticPeer(concept.getModelReference(), concept.getNonSemanticType());
      ObservableImpl observable = ObservableImpl.promote(nsmain, serviceScope());
      //			observable.setModelReference(concept.getModelReference());
      observable.setName(concept.getFormalName());
      observable.setStatedName(concept.getFormalName());
      observable.setReferenceName(concept.getFormalName());
      observable.setArtifactType(Artifact.Type.forSemantics(nsmain.getType()));
      observable.setAnnotations(AnnotationCollector.merge(concept.getAnnotations()));
      if(concept.getObserverSemantics()!=null) {
        var observer=declare(concept.getObserverSemantics(),declarationOntology,monitor);
        if(observer==null||!observer.is(SemanticType.AGENT))throw new KlabValidationException("OBSERVER_AGENT_REQUIRED: observed as requires a resolved Agent class");
        observable.setObserverSemantics(observer);
        observable.setUrn(observable.getUrn()+" observed as "+observer.getUrn());
        observable.setReferenceName(observable.getReferenceName()+"_observed_as_"+observer.getReferenceName());
      }
      return observable;
    }

    Concept main = declare(concept.getSemantics(), declarationOntology, monitor);
    if (main == null) {
      return null;
    }

    Concept observable = main;

    Observable.Builder builder = SemanticsBuilder.create(observable, this, monitor);

    if(concept.getObserverSemantics()!=null) {
      var observer=declare(concept.getObserverSemantics(),declarationOntology,monitor);
      if(observer==null||!observer.is(SemanticType.AGENT))
        throw new KlabValidationException("OBSERVER_AGENT_REQUIRED: observed as requires a resolved Agent class");
      builder.withObserverSemantics(observer);
    }

    // ret.setUrl(concept.getURI());
    // builder.withUrl(concept.getURI());

    boolean unitsSet = false;

    if (concept.getUnit() != null) {
      unitsSet = true;
      builder = builder.withUnit(concept.getUnit());
    }

    if (concept.getCurrency() != null) {
      unitsSet = true;
      builder = builder.withCurrency(concept.getCurrency());
    }

    ((SemanticsBuilder) builder).withInlineValue(concept.getValue());

    if (concept.getDefaultValue() != null) {
      Object value = concept.getDefaultValue();
      if (value instanceof KimConcept) {
        value = declareConcept((KimConcept) value);
      }
      builder = builder.withDefaultValue(value);
    }

    for (var exc : concept.getResolutionExceptions()) {
      builder = builder.withResolutionException(exc);
    }

    if (concept.getRange() != null) {
      builder = builder.withRange(concept.getRange());
    }

    builder =
        builder
            .optional(concept.isOptional())
            //            .generic(concept.isGeneric()) /* .global(concept
            //        .isGlobal()) */
            .named(concept.getFormalName());

    // TODO gather generic concepts and abstract ones
    //        if (concept.isExclusive()) {
    //            builder = builder.withResolution(Observable.Resolution.Only);
    //        } else if (concept.isGlobal()) {
    //            builder = builder.withResolution(Observable.Resolution.All);
    //        } else if (concept.isGeneric()) {
    //            builder = builder.withResolution(Observable.Resolution.Any);
    //        }

    for (var operator : concept.getValueOperators()) {
      builder = builder.withValueOperator(operator.getFirst(), operator.getSecond());
    }

    for (var annotation : AnnotationCollector.merge(concept.getAnnotations())) {
      builder = builder.withAnnotation(new AnnotationImpl(annotation));
    }

    // CHECK: fluidUnits = needsUnits() && !unitsSet;

    var result = (ObservableImpl) builder.buildObservable();
    var semantics = ((ConceptImpl) result.getSemantics()).withObservableStatus(concept, owl::getConcept);
    semantics.setAnnotations(main.getAnnotations());
    result.setSemantics(semantics);
    return result;
  }


  //  public void registerConcept(Concept thing) {
  //    this.concepts.put(thing.getUrn(), thing);
  //  }

  @Override
  public Collection<Concept> rolesFor(Concept observable, Concept context) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("rolesFor", new Class<?>[] { Concept.class, Concept.class }, new Object[] { observable, context });
  throw new KlabUnimplementedException("Context-implied role computation is not implemented");
  }

  @Override
  public Concept impliedRole(Concept baseRole, Concept contextObservable) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("impliedRole", new Class<?>[] { Concept.class, Concept.class }, new Object[] { baseRole, contextObservable });
  throw new KlabUnimplementedException("Context-implied role computation is not implemented");
  }

  @Override
  public Collection<Concept> impliedRoles(Concept role, boolean includeRelationshipEndpoints) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("impliedRoles", new Class<?>[] { Concept.class, boolean.class }, new Object[] { role, includeRelationshipEndpoints });
  throw new KlabUnimplementedException("Implied role closure is not implemented");
  }

  /**
   * Allocate a semantic search only for ID zero. Unknown or expired IDs never silently restart an
   * expression; the client must explicitly initialize again.
   *
   * @param request
   */
  @Override
  public SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {
    return semanticSearch(request, serviceScope());
  }

  @Override
  public SemanticSearchResponse semanticSearch(SemanticSearchRequest request, Scope scope) {
    if (candidateRuntime != null && !semanticOnly) return candidateRuntime.search(request, scope);
    if (scope == null) throw new SecurityException("Semantic search requires an authorized scope");
    Object owner = scope instanceof UserScope user ? user.getUser().getUsername() : scope;

    long started = System.currentTimeMillis();
    int id = request.getSearchId();
    SemanticSearchResponse response;
    var owned = id == 0 ? null : semanticExpressions.getIfPresent(id);
    if (owned != null && !java.util.Objects.equals(owner, owned.owner()))
      throw new SecurityException("Semantic search belongs to another user");
    if (request.isCancelSearch()) {
      semanticExpressions.invalidate(id);
      return new SemanticSearchResponse(0, request.getRequestId());
    }
    var session = owned == null ? null : owned.session();
    if (id != 0 && session == null) {
      response = new SemanticSearchResponse(0, request.getRequestId());
      response.getErrors().add("This search has expired. Start a new expression.");
    } else {
      if (session == null) {
        if (request.getSearchMode() != null
            && request.getSearchMode() != SemanticSearchRequest.Mode.TOKEN) {
          response = new SemanticSearchResponse(0, request.getRequestId());
          response.getErrors().add("Initialize a search before editing an expression.");
          return response;
        }
        session =
            new SemanticSearchSession(
                this,
                indexer::query,
                request,
                new org.integratedmodelling.klab.services.reasoner.owl.OWLSemanticClauseSupport(
                    owl), (authority, code) -> resolveAuthoritySelection(authority, code, scope));
        do {
          id = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        } while (semanticExpressions.asMap().putIfAbsent(id, new OwnedSemanticSession(owner, session)) != null);
      }
      response = session.handle(request, id);
    }
    response.setElapsedTimeMs(System.currentTimeMillis() - started);
    return response;
  }

  @Override
  public synchronized boolean shutdown() {
    if (org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.enabled()) org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.emit(
        org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Event.INVALIDATED,
        candidateRuntime == null ? -1 : candidateRuntime.status().inputGeneration(), -1, -1,
        null, null, org.integratedmodelling.klab.services.reasoner.internal.CandidateTrace.Reason.SHUTDOWN);
    if (candidateRuntime != null && !semanticOnly) candidateRuntime.close();

    //    serviceScope()
    //        .send(
    //            Message.MessageClass.ServiceLifecycle,
    //            Message.MessageType.ServiceUnavailable,
    //            capabilities(serviceScope()));
    semanticValidationState.settle(knowledgeRevision(), false);
    capabilitiesGeneration++;
    invalidateSemanticCaches();
    knowledgeReady = false;
    authorityBindings.clear();
    owl.reset();
    declarationDomains.clear();
    return super.shutdown();
  }

  @Override
  protected org.integratedmodelling.klab.services.configuration.ServiceConfiguration
      getServiceConfiguration() {
    return this.configuration;
  }

  @Override
  public List<ObservationStrategy> computeObservationStrategies(
      Observation observation, ContextScope scope) {
      if (candidateRuntime != null && !semanticOnly)
          return candidateRuntime.invoke("computeObservationStrategies", new Class<?>[] { Observation.class, ContextScope.class }, new Object[] { observation, scope });
      var context = scope.getContextObservation();
      if (!org.integratedmodelling.klab.services.reasoner.owl.ContextualRequestAdmission.admits(
          owl(), observation.getObservable().getSemantics(),
          context == null ? null : context.getObservable().getSemantics())) {
        scope.warn("CONTEXTUAL_REQUEST_INCOMPATIBLE: no native bearer or authored containment proof for "
            + observation.getObservable().getUrn());
        return List.of();
      }
      return observationReasoner.computeMatchingStrategies(observation, scope, true);
  }

  @Override
  public IdentificationStrategy computeIdentificationStrategies(
      Observable observable, ContextScope scope) {
      if (candidateRuntime != null && !semanticOnly)
          return candidateRuntime.invoke("computeIdentificationStrategies", new Class<?>[] { Observable.class, ContextScope.class }, new Object[] { observable, scope });
      return observationReasoner.computeIdentificationStrategy(observable, scope);
  }

  public Collection<Concept> collectComponents(Concept concept, Collection<SemanticType> types) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("collectComponents", new Class<?>[] { Concept.class, Collection.class }, new Object[] { concept, types });
  Set<Concept> ret = new HashSet<>();
    KimConcept peer =
        serviceScope().getService(ResourcesService.class).declareConcept(concept.getUrn());
    var visitor = new KimObservableVisitor();
    visitor.visit(peer);
    var requestedTypes = org.integratedmodelling.common.utils.Utils.Collections.asSet(types);
    for (var component : visitor.getConcepts()) {
      if (component.getName() == null
          || Sets.intersection(component.getType(), requestedTypes).size() != types.size()) {
        continue;
      }
      var resolved = resolveConcept(component.getName());
      if (resolved != null) ret.add(resolved);
    }
    return ret;
  }

  public Concept replaceComponent(Concept original, Map<Concept, Concept> replacements) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("replaceComponent", new Class<?>[] { Concept.class, Map.class }, new Object[] { original, replacements });

    /*
     * TODO this is the original lexical replacement, which is risky and incomplete.
     * This should use a specialized visitor to rebuild the concept piecewise from a
     * modified KimConcept.
     */

    if (replacements.isEmpty()) {
      return original;
    }

    String declaration = original.getUrn();
    for (Concept key : replacements.keySet()) {
      String rep = replacements.get(key).toString();
      if (rep.contains(" ")) {
        rep = "(" + rep + ")";
      }
      declaration = declaration.replace(key.getUrn(), rep);
    }

    return declareConcept(
        serviceScope().getService(ResourcesService.class).declareConcept(declaration));
  }

  @Override
  public Concept buildConcept(ObservableBuildStrategy builder, Scope scope) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("buildConcept", new Class<?>[] { ObservableBuildStrategy.class, Scope.class }, new Object[] { builder, scope });
  Observable.Builder ret = createSemanticBuilder(builder, scope);
    ret = defineBuilder(builder, ret);
    return ret.buildConcept();
  }

  @Override
  public Observable buildObservable(ObservableBuildStrategy builder, Scope scope) {
  if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("buildObservable", new Class<?>[] { ObservableBuildStrategy.class, Scope.class }, new Object[] { builder, scope });
  Observable.Builder ret = createSemanticBuilder(builder, scope);
    ret = defineBuilder(builder, ret);
    return ret.buildObservable();
  }

  private Observable.Builder createSemanticBuilder(ObservableBuildStrategy builder, Scope scope) {
    if (builder.getBaseObservable() != null)
      return SemanticsBuilder.create(builder.getBaseObservable(), this, scope);
    if (builder.getBaseConcept() != null)
      return SemanticsBuilder.create(builder.getBaseConcept(), this, scope);
    throw new IllegalArgumentException("Observable builder requires a base concept or observable");
  }

  private Observable.Builder defineBuilder(
      ObservableBuildStrategy builder, Observable.Builder ret) {
    for (ObservableBuildStrategy.Operation op : builder.getOperations()) {
      switch (op.getType()) {
        case OF -> {
          ret = ret.of(op.getConcepts().get(0));
        }
        case WITH -> {
          ret = ret.with(op.getConcepts().get(0));
        }
        case GOAL -> {
          ret = ret.withGoal(op.getConcepts().get(0));
        }
        case FROM -> {
          ret = ret.withCausant(op.getConcepts().get(0));
        }
        case TO -> {
          ret = ret.withCaused(op.getConcepts().get(0));
        }
        case WITH_ROLE -> {
          ret = ret.withRole(op.getConcepts().get(0));
        }
        case AS -> {
          ret = ret.as(op.getOperator(), op.getConcepts().toArray(new Concept[0]));
        }
        case WITH_TRAITS -> {
          ret = ret.withTrait(op.getConcepts().toArray(new Concept[0]));
        }
        case WITHOUT -> {
          ret = ret.without(op.getConcepts().toArray(new Concept[0]));
        }
        case WITHOUT_ANY_TYPES -> {
          ret = ret.withoutAny(op.getTypes().toArray(new SemanticType[0]));
        }
        case WITHOUT_ANY_CONCEPTS -> {
          ret = ret.withoutAny(op.getConcepts().toArray(new Concept[0]));
        }
        case ADJACENT -> {
          ret = ret.withAdjacent(op.getConcepts().get(0));
        }
        case COOCCURRENT -> {
          ret = ret.withCooccurrent(op.getConcepts().get(0));
        }
        case WITH_UNIT -> {
          ret = ret.withUnit(op.getUnit());
        }
        case WITH_CURRENCY -> {
          ret = ret.withCurrency(op.getCurrency());
        }
        case WITH_RANGE -> {
          ret = ret.withRange(op.getRange());
        }
        case WITH_VALUE_OPERATOR -> {
          ret =
              ret.withValueOperator(
                  op.getValueOperation().getFirst(), op.getValueOperation().getSecond());
        }
        case LINKING -> {
          ret = ret.linking(op.getConcepts().get(0), op.getConcepts().get(1));
        }
        case NAMED -> {
          ret = ret.named((String) op.getPod());
        }
        case WITHOUT_VALUE_OPERATORS -> {
          ret = ret.withoutValueOperators();
        }
        case AS_OPTIONAL -> {
          ret = ret.optional((Boolean) op.getPod());
        }
        case WITHOUT_ROLES -> {
          ret = ret.without(op.getRoles().toArray(new SemanticRole[0]));
        }
        case WITH_TEMPORAL_INHERENT -> {
          ret = ret.withTemporalInherent(op.getConcepts().get(0));
        }
        case WITH_OBSERVER_SEMANTICS -> {
          ret = ret.withObserverSemantics(op.getConcepts().get(0));
        }
        case COLLECTIVE -> {
          ret = ret.collective((Boolean) op.getPod());
        }
        case WITH_DEFAULT_VALUE -> {
          ret = ret.withDefaultValue(op.getPod());
        }
        case WITH_RESOLUTION_EXCEPTION -> {
          ret = ret.withResolutionException(op.getResolutionException());
        }
        case WITH_ANNOTATION -> {
          for (Annotation annotation : op.getAnnotations()) {
            ret = ret.withAnnotation(annotation);
          }
        }
        default ->
            throw new KlabUnimplementedException(
                "ReasonerService::defineBuilder: unhandled " + "operation " + op.getType());
      }
    }
    return ret;
  }

  @Override
  public boolean exportNamespace(String namespace, File directory) {
    if (candidateRuntime != null && !semanticOnly)
      return candidateRuntime.invoke("exportNamespace", new Class<?>[]{String.class, File.class}, new Object[]{namespace, directory});
    return this.owl.exportOntology(namespace, directory);
  }

  @Override
  public <T extends Serializable> T retrieveAsset(
      String urn, Scheduler.Event locator, Class<T> assetClass, Scope scope) {
    throw new KlabUnimplementedException(
        "Reasoner assets cannot be retrieved through this service");
  }
}
