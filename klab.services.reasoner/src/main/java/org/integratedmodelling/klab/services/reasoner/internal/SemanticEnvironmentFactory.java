package org.integratedmodelling.klab.services.reasoner.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;

/** Runs the production compiler in private semantic state, without normal service construction. */
public final class SemanticEnvironmentFactory
    implements CandidatePublicationCoordinator.Builder<Worldview, SemanticEnvironment> {
  public static final String CONFIGURATION_REVISION = "semantic-staged-declarations-v10-identity-closure-core-alignment-generic-quality-canonical-aliases-configured-authority-snapshots-temporal-derivatives-nested-rates-affine-scales-canonical-rate-identity-verified-aliases-mixed-operators";
  private final Path assetRoot;
  private final Map<String, byte[]> core;
  private final String dependencyRevision;
  private final Map<String, java.util.Set<String>> coreIdentities;
  private final Map<String, String> documentLocators;
  private final CapturedAuthorityCatalog authorities;
  private final Map<String,String> expectedDocumentHashes;
  private volatile AuthorityBootstrap bootstrap;
  private record BootstrapInput(org.integratedmodelling.klab.api.scope.Scope owner, String key) {}
  private final Map<String,BootstrapInput> bootstrapInputs = new java.util.concurrent.ConcurrentHashMap<>();
  public synchronized void enableAuthorityBootstrap() {
    if (bootstrap != null) throw new IllegalStateException("Bootstrap is already enabled");
    bootstrap = new AuthorityBootstrap(assetRoot.resolve("authority-bootstrap"), core);
  }
  public boolean authorityBootstrapEnabled() { return bootstrap != null; }

  public Worldview refreshAuthoritySelection(Worldview source, org.integratedmodelling.klab.api.scope.Scope owner) {
    if (bootstrap == null || source.getAuthorityBindings().isEmpty()) return source;
    var snapshot = BeanIngestionPreflight.snapshot(source);
    snapshot.getMetadata().put(org.integratedmodelling.klab.components.AuthorityComponentResolver.SELECTION_METADATA,
        bootstrap.selectionRevision(snapshot, owner));
    return snapshot;
  }
  boolean publicationCurrent(CandidatePublicationCoordinator.Input<Worldview> input) {
    if (bootstrap==null || input.payload().getAuthorityBindings().isEmpty()) return true;
    var context=bootstrapInputs.get(input.dependencyRevision());
    return context!=null && context.key().equals(AuthorityBootstrap.key(input.payload(),context.owner()));
  }
  void invalidateBootstrap() { if (bootstrap != null) bootstrap.invalidate(); bootstrapInputs.clear(); }
  public CandidatePublicationCoordinator.Input<Worldview> capture(Worldview source, org.integratedmodelling.klab.api.scope.Scope owner) {
    var input = capture(source);
    if (bootstrap == null || source.getAuthorityBindings().isEmpty()) return input;
    String key = AuthorityBootstrap.key(input.payload(), owner);
    String revision = dependencyRevision() + ":" + key;
    while (bootstrapInputs.size() >= 64 && !bootstrapInputs.containsKey(revision))
      bootstrapInputs.remove(bootstrapInputs.keySet().iterator().next());
    bootstrapInputs.put(revision, new BootstrapInput(owner, key));
    return new CandidatePublicationCoordinator.Input<>(input.sourceHashes(), input.sourceOrder(), revision,
        input.configurationRevision(), input.payload(), input.beanStatus(), input.diagnostics());
  }
  private final Map<String,java.util.Set<String>> coreClassNames;

  public SemanticEnvironmentFactory(Path privateAssetRoot, Map<String, byte[]> capturedCore) throws Exception {
    this(privateAssetRoot, capturedCore, Map.of());
  }
  public SemanticEnvironmentFactory(Path privateAssetRoot, Map<String, byte[]> capturedCore,
      Map<String, String> capturedDocumentLocators) throws Exception {
    this(privateAssetRoot, capturedCore, capturedDocumentLocators, Map.of());
  }
  public SemanticEnvironmentFactory(Path privateAssetRoot, Map<String, byte[]> capturedCore,
      Map<String, String> capturedDocumentLocators, Map<String, String> expectedDocumentHashes) throws Exception {
    this(privateAssetRoot, capturedCore, capturedDocumentLocators, expectedDocumentHashes, CapturedAuthorityCatalog.empty());
  }
  public SemanticEnvironmentFactory(Path privateAssetRoot, Map<String, byte[]> capturedCore,
      Map<String, String> capturedDocumentLocators, Map<String, String> expectedDocumentHashes,
      CapturedAuthorityCatalog authorities) throws Exception {
    this.authorities = Objects.requireNonNull(authorities);
    this.expectedDocumentHashes = Map.copyOf(expectedDocumentHashes);
    assetRoot = privateAssetRoot.toAbsolutePath().normalize();
    var owned = new LinkedHashMap<String, byte[]>();
    var digest = MessageDigest.getInstance("SHA-256");
    var identities = new LinkedHashMap<String, java.util.Set<String>>();
    var identityOwners = new java.util.HashMap<String, String>();
    var contentHashes = new java.util.HashMap<String, String>();
    var aliasOwners = new java.util.HashMap<String, String>();
    for (var name : capturedCore.keySet().stream().sorted().toList()) {
      if (!name.endsWith(".owl") || Path.of(name).isAbsolute()
          || !assetRoot.resolve(name).normalize().startsWith(assetRoot)
          || !CoreOntologyCatalog.isVisible(Path.of(name)))
        throw new IllegalArgumentException("Invalid private core catalog entry " + name);
      byte[] bytes = capturedCore.get(name).clone(); owned.put(name, bytes);
      var alias = CoreOntologyCatalog.alias(Path.of(name));
      var previousAlias = aliasOwners.putIfAbsent(alias, name);
      if (previousAlias != null) throw new IllegalArgumentException(
          "Ambiguous captured core alias " + alias + " in " + previousAlias + " and " + name);
      contentHashes.put(alias, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
      identities.put(alias, ontologyIdentities(bytes));
      for (var identity : identities.get(alias)) {
        var previous = identityOwners.putIfAbsent(identity, name);
        if (previous != null) throw new IllegalArgumentException(
            "Ambiguous captured ontology identity " + identity + " in " + previous + " and " + name);
      }
      digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      digest.update((byte)0); digest.update(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    if (owned.isEmpty()) throw new IllegalArgumentException("Candidate requires a captured core catalog");
    core = java.util.Collections.unmodifiableMap(owned);
    var classes = new LinkedHashMap<String,java.util.Set<String>>();
    for (var entry:owned.entrySet()) {
      var alias = CoreOntologyCatalog.alias(Path.of(entry.getKey()));
      classes.put(alias,ontologyClassNames(entry.getValue()));
    }
    coreClassNames=Map.copyOf(classes);
    coreIdentities = Map.copyOf(identities);
    documentLocators = Map.copyOf(capturedDocumentLocators);
    for (var alias : documentLocators.keySet().stream().sorted().toList()) {
      if (!coreIdentities.containsKey(alias)) throw new IllegalArgumentException("Locator has no captured core alias: " + alias);
      var locator = documentLocators.get(alias);
      var expectedHash = expectedDocumentHashes.get(alias);
      if (expectedHash != null && !expectedHash.equals(contentHashes.get(alias)))
        throw new IllegalArgumentException("Captured document does not match its pinned source hash: " + locator);
      var release = java.util.regex.Pattern.compile("/releases/([^/]+)/[^/]+\\.owl$").matcher(locator);
      // Historical release directory names and ontology version IRIs can differ. An explicit
      // pinned document hash authorizes those exact bytes without rewriting either identity.
      if (expectedHash == null && release.find()
          && coreIdentities.get(alias).stream().noneMatch(iri -> iri.endsWith("/" + release.group(1))))
        throw new IllegalArgumentException("Captured document version does not match its numbered release locator: " + locator);
      digest.update(alias.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      digest.update(locator.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    digest.update(org.integratedmodelling.klab.services.reasoner.OperandGuards.CONTRACT_REVISION.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    digest.update(authorities.revision().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    dependencyRevision = HexFormat.of().formatHex(digest.digest());
  }
  public String dependencyRevision() {
    return bootstrap!=null && bootstrap.policyRevision()!=null?dependencyRevision+":"+bootstrap.policyRevision():dependencyRevision;
  }
  /** Captures bundled structural core plus local extensions; installed core cannot override the distribution. */
  public static SemanticEnvironmentFactory fromLocalCatalog(Path privateAssets, Path catalog) throws Exception {
    return fromLocalCatalog(privateAssets, catalog, catalog.toAbsolutePath().getParent());
  }
  public static SemanticEnvironmentFactory fromLocalCatalog(Path privateAssets, Path catalog, Path authorityData) throws Exception {
    var files = new LinkedHashMap<String, byte[]>();
    if (Files.isDirectory(catalog)) {
      try (var entries = Files.walk(catalog)) {
        for (var file : entries.filter(Files::isRegularFile)
            .filter(p -> CoreOntologyCatalog.isVisible(catalog.relativize(p)))
            .filter(p -> !CoreOntologyCatalog.isBundledDocument(catalog.relativize(p)))
            .filter(p -> p.getFileName().toString().endsWith(".owl")).sorted().toList())
          files.put(catalog.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
      }
    }
    var locators = new java.util.Properties();
    var manifest = catalog.resolve("source-catalog.properties");
    if (Files.isRegularFile(manifest)) try (var input = Files.newInputStream(manifest)) { locators.load(input); }
    var bundled = CoreOntologyCatalog.bundledCore();
    files.putAll(bundled);
    // These entries describe installed bytes, not the authoritative bundled document. Do not
    // certify installed provenance as the origin of the bundled structural documents.
    for (var document : bundled.keySet()) {
      var alias = CoreOntologyCatalog.alias(Path.of(document));
      locators.remove(alias);
      locators.remove(alias + ".sha256");
    }
    var names = new LinkedHashMap<String, String>();
    var hashes = new LinkedHashMap<String, String>();
    locators.stringPropertyNames().stream().sorted().forEach(name -> {
      if (name.endsWith(".sha256")) hashes.put(name.substring(0, name.length() - 7), locators.getProperty(name));
      else names.put(name, locators.getProperty(name));
    });
    return new SemanticEnvironmentFactory(privateAssets, files, names, hashes,
        CapturedAuthorityCatalog.capture(authorityData));
  }
  private static java.util.Set<String> ontologyIdentities(byte[] bytes) throws Exception {
    var parser = javax.xml.parsers.DocumentBuilderFactory.newInstance();
    parser.setNamespaceAware(true);
    parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    parser.setFeature("http://xml.org/sax/features/external-general-entities", false);
    parser.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    parser.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
    parser.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    var document = parser.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
    var nodes = document.getElementsByTagNameNS("http://www.w3.org/2002/07/owl#", "Ontology");
    var identities = new java.util.HashSet<String>();
    for (int i = 0; i < nodes.getLength(); i++) {
      var ontology = (org.w3c.dom.Element)nodes.item(i);
      identities.add(ontology.getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "about"));
      var versions = ontology.getElementsByTagNameNS("http://www.w3.org/2002/07/owl#", "versionIRI");
      for (int j = 0; j < versions.getLength(); j++) identities.add(((org.w3c.dom.Element)versions.item(j))
          .getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource"));
    }
    identities.remove("");
    if (identities.isEmpty()) throw new IllegalArgumentException("Captured RDF/XML core has no ontology identity");
    return java.util.Set.copyOf(identities);
  }
  private static java.util.Set<String> ontologyClassNames(byte[] bytes) throws Exception {
    var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
    factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");
    factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
    var document=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(bytes));
    var nodes=document.getElementsByTagNameNS("http://www.w3.org/2002/07/owl#","Class");
    var result=new java.util.HashSet<String>();
    for(int i=0;i<nodes.getLength();i++) {
      var iri=((org.w3c.dom.Element)nodes.item(i)).getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#","about");
      int split=iri.lastIndexOf('#');if(split<0)split=iri.lastIndexOf('/');
      if(split>=0)result.add(iri.substring(split+1));
    }
    return java.util.Set.copyOf(result);
  }
  private List<Notification> coreAliasErrors(Worldview worldview) {
    var errors=new ArrayList<Notification>();
    var registry=new org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry(worldview.getOntologies());
    for(var declaration:registry.declarations()) {
      var target=declaration.statement().getUpperConceptDefined();if(target==null)continue;
      var parts=target.split(":",2);
      if(parts.length!=2||!coreClassNames.getOrDefault(parts[0],java.util.Set.of()).contains(parts[1]))
        errors.add(Notification.error("CORE_ALIAS_TARGET_UNAVAILABLE: "+target,
            Notification.LexicalContext.of(declaration.statement(),declaration.ontology())));
    }
    return errors;
  }

  /** Freeze at submission time; the worker never compiles a caller's mutable source trees. */
  public CandidatePublicationCoordinator.Input<Worldview> capture(Worldview worldview) {
    var owned = BeanIngestionPreflight.snapshot(worldview);
    var errors = new ArrayList<>(BeanIngestionPreflight.errors(owned));
    errors.addAll(coreAliasErrors(owned));
    return new CandidatePublicationCoordinator.Input<>(sources(owned), sourceOrder(owned),
        dependencyRevision, CONFIGURATION_REVISION, owned,
        errors.isEmpty() ? CandidatePublicationCoordinator.BeanStatus.VALID
            : CandidatePublicationCoordinator.BeanStatus.INVALID,
        errors.stream().map(Notification::getMessage).toList());
  }
  public static List<String> sourceOrder(Worldview worldview) {
    var result = new ArrayList<String>();
    worldview.getOntologies().forEach(d -> result.add(sourceKey(d)));
    worldview.getObservationStrategies().forEach(d -> result.add(sourceKey(d)));
    return List.copyOf(result);
  }
  public static String sourceKey(KlabDocument<?> document) {
    return org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.classify(document.getClass()).name()
        + ":" + document.getUrn();
  }
  public static Map<String, String> sources(Worldview worldview) {
    var result = new LinkedHashMap<String, String>();
    var documents = new ArrayList<KlabDocument<?>>();
    documents.addAll(worldview.getOntologies()); documents.addAll(worldview.getObservationStrategies());
    for (var document : documents) {
      var hash = SemanticValidationRequest.sourceHash(document.getSourceCode());
      if (hash == null || result.put(sourceKey(document), hash) != null)
        throw new IllegalArgumentException("Candidate source identity is missing or duplicated");
    }
    return Map.copyOf(result);
  }
  @Override public CandidatePublicationCoordinator.Result<SemanticEnvironment> build(
      CandidatePublicationCoordinator.Input<Worldview> input,
      CandidatePublicationCoordinator.Cancellation cancellation) throws Exception {
    var context = bootstrapInputs.get(input.dependencyRevision());
    if (context == null) return buildCaptured(input, cancellation);
    var owned = BeanIngestionPreflight.snapshot(input.payload());
    if (!input.sourceHashes().equals(sources(owned)) || !input.sourceOrder().equals(sourceOrder(owned))
        || input.beanStatus() != CandidatePublicationCoordinator.BeanStatus.VALID
        || !CONFIGURATION_REVISION.equals(input.configurationRevision())
        || !input.dependencyRevision().equals(dependencyRevision() + ":" + context.key())
        || !BeanIngestionPreflight.errors(owned).isEmpty() || !coreAliasErrors(owned).isEmpty())
      throw new IllegalArgumentException("Bootstrap input does not match frozen validated manifest");
    var catalog = bootstrap.prepare(owned, context.owner(), context.key(), cancellation);
    var isolated = new SemanticEnvironmentFactory(assetRoot, core, documentLocators, expectedDocumentHashes, catalog);
    var result = isolated.buildCaptured(isolated.capture(owned), cancellation);
    try {
      cancellation.checkpoint();
      // Re-resolve exact metadata after compilation too; no publication of stale rights/artifacts.
      bootstrap.prepare(owned, context.owner(), context.key(), cancellation);
      return result;
    } catch (Throwable stale) {
      if (result.state() != null) result.state().close();
      throw stale;
    }
  }
  private CandidatePublicationCoordinator.Result<SemanticEnvironment> buildCaptured(
      CandidatePublicationCoordinator.Input<Worldview> input,
      CandidatePublicationCoordinator.Cancellation cancellation) throws Exception {
    cancellation.checkpoint();
    if (input.beanStatus() != CandidatePublicationCoordinator.BeanStatus.VALID)
      throw new IllegalArgumentException("Private compiler requires passed bean preflight");
    var snapshot = BeanIngestionPreflight.snapshot(input.payload());
    if (!input.sourceHashes().equals(sources(snapshot))
        || !input.sourceOrder().equals(sourceOrder(snapshot))
        || !CONFIGURATION_REVISION.equals(input.configurationRevision())
        || !input.dependencyRevision().equals(dependencyRevision))
      throw new IllegalArgumentException("Candidate does not match its captured source/dependency/configuration manifest");
    var beanErrors = new ArrayList<>(BeanIngestionPreflight.errors(snapshot));
    beanErrors.addAll(coreAliasErrors(snapshot));
    if (!beanErrors.isEmpty())
      return new CandidatePublicationCoordinator.Result<>(null,
          CandidatePublicationCoordinator.LogicalStatus.NOT_RUN, true,
          beanErrors.stream().map(Notification::getMessage).toList());
    var unsupported = new ArrayList<String>();
    for (var ontology : snapshot.getOntologies()) {
      for (var imported : ontology.getOwlImports()) {
        var captured = coreIdentities.get(imported.getFirst());
        var odoRelease = "odo".equals(imported.getFirst())
            ? CoreOntologyCatalog.odoReleaseIdentity(imported.getSecond()) : null;
        if (captured == null || (!captured.contains(imported.getSecond())
            && !(odoRelease != null && captured.contains(odoRelease))
            && !Objects.equals(documentLocators.get(imported.getFirst()), imported.getSecond()))) unsupported.add(
            "CANDIDATE_ISOLATION_UNSUPPORTED: uncaptured OWL import " + imported);
      }
      if (!ontology.getVocabularyImports().isEmpty()) unsupported.add(
          "CANDIDATE_ISOLATION_UNSUPPORTED: vocabulary provider for " + ontology.getUrn());
      var visitor = new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor(
          new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor.LenientValidator(), null);
      visitor.visit(ontology);
      if (visitor.getStatements().stream().anyMatch(s ->
          s instanceof org.integratedmodelling.klab.api.lang.kim.KimConceptStatement declaration
              && declaration.getAuthorityRequired() != null
              && snapshot.getAuthorityBindings().stream().noneMatch(binding ->
                  binding.localId().equals(declaration.getAuthorityRequired()) && authorities.supports(binding)))) unsupported.add(
          "CANDIDATE_ISOLATION_UNSUPPORTED: authority provider for " + ontology.getUrn());
    }
    for (var strategies : snapshot.getObservationStrategies()) {
      var visitor = new org.integratedmodelling.klab.runtime.language.KimObservationStrategyDocumentVisitor(
          new org.integratedmodelling.klab.runtime.language.KimObservationStrategyDocumentVisitor.LenientValidator() {
            @Override public List<Notification> validatePlanNode(
                org.integratedmodelling.klab.api.lang.kim.KimObservationPlan.Node node,
                org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Context context) {
              if ((node instanceof org.integratedmodelling.klab.api.lang.kim.KimObservationPlan.StrategyCall call
                  && !org.integratedmodelling.klab.services.reasoner.ObservationReasoner.SUPPORTED_FUNCTIONS.contains(call.getFunction()))
                  || (node instanceof org.integratedmodelling.klab.api.lang.kim.KimObservationPlan.ExternalMatcher matcher
                      && matcher.getCall() == null))
                return List.of(Notification.warning(
                    "CANDIDATE_ISOLATION_UNSUPPORTED: strategy function is not implemented by the captured runtime: "
                        + (node instanceof org.integratedmodelling.klab.api.lang.kim.KimObservationPlan.StrategyCall call
                            ? call.getFunction() : "missing external matcher call")));
              return List.of();
            }
          }, null);
      visitor.visit(strategies);
      unsupported.addAll(visitor.getNotifications().stream().map(Notification::getMessage).toList());
    }
    for (var binding : snapshot.getAuthorityBindings()) if (!authorities.supports(binding)) unsupported.add(
        "CANDIDATE_ISOLATION_UNSUPPORTED: authority binding requires a matching owned provider snapshot: " + binding.localId());
    if (!unsupported.isEmpty()) return new CandidatePublicationCoordinator.Result<>(null,
        CandidatePublicationCoordinator.LogicalStatus.UNRESOLVED, true, unsupported);
    Files.createDirectories(assetRoot);
    Path assets = Files.createTempDirectory(assetRoot, "semantic-");
    var knowledge = Files.createDirectories(assets.resolve("knowledge"));
    SemanticEnvironment environment = null;
    boolean transferred = false;
    try {
      for (var entry : core.entrySet()) {
        var file = knowledge.resolve(entry.getKey()).normalize();
        if (!file.startsWith(knowledge)) throw new IllegalArgumentException("Core catalog escaped private assets");
        Files.createDirectories(file.getParent()); Files.write(file, entry.getValue());
      }
      cancellation.checkpoint();
      var scope = new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(snapshot).service());
      authorities.materializeCodelists(assets);
      var actor = new ReasonerService(scope, knowledge, assets, authorities);
      environment = new SemanticEnvironment(actor, assets);
      var loaded = actor.loadKnowledge(snapshot, scope);
      cancellation.checkpoint();
      var diagnostics = new ArrayList<Notification>(scope.getNotifications());
      diagnostics.addAll(loaded.getNotifications());
      if (scope.hasErrors() && !Utils.Notifications.hasErrors(diagnostics))
        diagnostics.add(Notification.error("Candidate compilation emitted errors without source-bound transport"));
      if (!Utils.Notifications.hasErrors(diagnostics)) {
        for (var ontology : snapshot.getOntologies()) {
          cancellation.checkpoint();
          diagnostics.addAll(new DocumentSemanticValidator(actor).validate(ontology));
        }
        for (var strategies : snapshot.getObservationStrategies()) {
          cancellation.checkpoint();
          diagnostics.addAll(new DocumentSemanticValidator(actor).validate(strategies));
        }
      }
      // Validators can materialize captured expressions. Include those final axioms in the check.
      if (actor.owl().hasReasoner()) {
        actor.owl().flushReasoner();
        if (!actor.owl().isConsistent()) diagnostics.add(Notification.error(
            "Candidate OWL is inconsistent after semantic expression validation"));
        else {
          // Expressions may add axioms after a source declaration was checked. Classify the
          // final candidate once: global consistency alone does not reject impossible classes.
          actor.owl().getUnsatisfiableClasses().entities().filter(c -> !c.isOWLNothing())
              .sorted(java.util.Comparator.comparing(c -> c.getIRI().toString()))
              .forEach(c -> diagnostics.add(Notification.error(
                  "Candidate contains unsatisfiable named class: " + c.getIRI())));
        }
      }
      var messages = diagnostics.stream().map(Notification::getMessage).toList();
      var status = messages.stream().anyMatch(m -> m.contains("CANDIDATE_ISOLATION_UNSUPPORTED"))
          ? CandidatePublicationCoordinator.LogicalStatus.UNRESOLVED
          : Utils.Notifications.hasErrors(diagnostics)
              ? CandidatePublicationCoordinator.LogicalStatus.INVALID
              : CandidatePublicationCoordinator.LogicalStatus.VALID;
      if (!actor.owl().hasReasoner()) status = CandidatePublicationCoordinator.LogicalStatus.UNRESOLVED;
      if (snapshot.getOntologies().stream()
          .anyMatch(org.integratedmodelling.klab.services.reasoner.owl.WorldviewDeclarationSupport::hasDeferredValues))
        status = CandidatePublicationCoordinator.LogicalStatus.UNRESOLVED;
      var result = new CandidatePublicationCoordinator.Result<>(environment, status, true, messages);
      transferred = true;
      environment = null; // Coordinator now owns disposal of returned state.
      return result;
    } finally {
      if (!transferred) {
        if (environment != null) environment.close();
        else SemanticEnvironment.removeAssets(assets);
      }
    }
  }
}
