package org.integratedmodelling.klab.services.reasoner.internal;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions.ComponentDescriptor;
import org.integratedmodelling.klab.components.AuthorityComponentResolver;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;

/** Single-flight producer of real, independently owned authority state. No deployment writes. */
public final class AuthorityBootstrap {
  static final String CONTRACT = "owned-authority-bootstrap-v1";
  private static final com.fasterxml.jackson.databind.ObjectMapper JSON = JacksonConfiguration.newObjectMapper();
  record Plan(List<ComponentDescriptor> components, ResourceSet resources, String revision) {}
  @FunctionalInterface interface Resolver { Plan resolve(Worldview source, Scope owner); }
  @FunctionalInterface interface Producer {
    CapturedAuthorityCatalog produce(Worldview source, Scope owner, Plan plan,
        CandidatePublicationCoordinator.Cancellation cancellation) throws Exception;
  }
  private record Outcome(long epoch, CapturedAuthorityCatalog catalog, String failure, Plan plan,
                         String providerInput) {}
  private final Resolver resolver;
  private final Producer producer;
  private final String policyRevision;
  String policyRevision() { return policyRevision; }
  private final Map<String, Outcome> outcomes = new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicLong epoch = new java.util.concurrent.atomic.AtomicLong();

  public AuthorityBootstrap(Path root, Map<String, byte[]> core) {
    this(BootstrapSourcePin.fromSystemProperty(),root,core);
  }
  private AuthorityBootstrap(BootstrapSourcePin pin,Path root,Map<String,byte[]> core) {
    this((source,owner)->resolve(source,owner,pin), (source, owner, plan, cancellation) ->
        produce(root, core, source, owner, plan, cancellation), pin==null?null:pin.revision());
  }
  AuthorityBootstrap(Resolver resolver, Producer producer) { this(resolver,producer,null); }
  private AuthorityBootstrap(Resolver resolver, Producer producer,String policyRevision) {
    this.resolver=resolver; this.producer=producer; this.policyRevision=policyRevision;
  }

  /** Metadata-only poll: never download, configure, or run provider code here. */
  String selectionRevision(Worldview source, Scope owner) {
    try { return resolver.resolve(source, owner).revision(); }
    catch (RuntimeException unavailable) {
      return "unavailable:" + unavailable.getClass().getSimpleName();
    }
  }

  static String key(Worldview source, Scope owner) {
    try {
      var value=Arrays.asList(CONTRACT, new TreeMap<>(SemanticEnvironmentFactory.sources(source)),
          SemanticEnvironmentFactory.sourceOrder(source), source.getAuthorityBindings(),
          source.getMetadata().get(AuthorityComponentResolver.REVISION_METADATA),
          source.getMetadata().get(AuthorityComponentResolver.SELECTION_METADATA),
          AuthorityComponentResolver.ownerRevision(owner));
      return digest(JSON.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(value));
    } catch (Exception failure) { throw new IllegalArgumentException("Cannot fingerprint authority bootstrap input"); }
  }
  /** Provider configuration and identity lookups do not depend on unrelated worldview source text.
   * Keep the full source key above for candidate cancellation and failed-attempt retry semantics. */
  static String providerInput(Worldview source, Scope owner) {
    var declarations = source.allConceptStatements().stream()
        .filter(s -> s.getAuthorityRequired() != null)
        .map(s -> new org.integratedmodelling.klab.api.services.Authority.ConfigurationRequest(
            source.getUrn(), s.getAuthorityRequired(), s.getNamespace() + ":" + s.getUrn(), s.getAuthorityParameters()))
        .sorted(Comparator.comparing(org.integratedmodelling.klab.api.services.Authority.ConfigurationRequest::name)).toList();
    var bindings = source.getAuthorityBindings().stream().sorted(Comparator.comparing(Worldview.AuthorityBinding::localId))
        .map(b -> List.of(b.localId(), b.rootIdentity(), b.provider(), b.componentUrn(), b.componentVersion())).toList();
    var namespaces = new HashSet<String>();
    source.getAuthorityBindings().forEach(b -> namespaces.add(b.localId()));
    for (var declaration : declarations) {
      if (declaration.parameters().get("codelists") instanceof Map<?, ?> lists)
        lists.values().stream().filter(String.class::isInstance).map(String.class::cast).forEach(namespaces::add);
    }
    var references = new TreeSet<String>();
    java.util.function.Consumer<org.integratedmodelling.klab.runtime.language.KimObservableVisitor> collect = visitor -> {
      for (var concept : visitor.getConcepts())
        if (concept.getAuthority() != null)
          references.add(org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityIdentitySyntax
              .encode(concept.getAuthority(), concept.getAuthorityTerm()));
      for (var reference : visitor.getReferences()) {
        String urn = reference.urn();
        if (urn != null && namespaces.stream().anyMatch(n -> urn.startsWith(n + ":") || urn.startsWith(n + ".")))
          references.add(urn);
      }
    };
    for (var ontology : source.getOntologies()) {
      var visitor = new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor(
          new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor.LenientValidator(), null);
      visitor.visit(ontology); collect.accept(visitor);
    }
    for (var document : source.getObservationStrategies()) {
      var visitor = new org.integratedmodelling.klab.runtime.language.KimObservationStrategyDocumentVisitor(
          new org.integratedmodelling.klab.runtime.language.KimObservationStrategyDocumentVisitor.LenientValidator(), null);
      visitor.visit(document); collect.accept(visitor);
    }
    try {
      return digest(JSON.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsBytes(List.of(source.getUrn(), declarations, bindings, references, AuthorityComponentResolver.ownerRevision(owner))));
    } catch (Exception failure) { throw new IllegalArgumentException("Cannot fingerprint authority provider input"); }
  }
  /** Failed input is remembered too. Only changed input or explicit invalidation retries side effects. */
  synchronized CapturedAuthorityCatalog prepare(Worldview source, Scope owner, String expectedKey,
      CandidatePublicationCoordinator.Cancellation cancellation) throws Exception {
    cancellation.checkpoint();
    long startedEpoch=epoch.get();
    if (!expectedKey.equals(key(source,owner))) throw new IllegalStateException("Authority bootstrap owner/input changed");
    var previous=outcomes.get(expectedKey);
    if (previous!=null && previous.epoch()!=startedEpoch) {
      outcomes.remove(expectedKey,previous); previous=null;
    }
    if (previous != null) {
      requireEpoch(startedEpoch);
      if (previous.failure()!=null) throw new IllegalStateException(previous.failure());
      verify(source,owner,previous.plan(),expectedKey); requireEpoch(startedEpoch); return previous.catalog();
    }
    try {
      var plan=resolver.resolve(source,owner); cancellation.checkpoint();
      var selectedRevision = source.getMetadata().get(AuthorityComponentResolver.SELECTION_METADATA);
      if (selectedRevision != null && !selectedRevision.equals(plan.revision()))
        throw new IllegalStateException("Authority selection changed after preflight");
      var providerInput = providerInput(source, owner);
      for (var outcome : outcomes.values()) {
        if (outcome.epoch() == startedEpoch && outcome.failure() == null
            && providerInput.equals(outcome.providerInput()) && plan.revision().equals(outcome.plan().revision())
            && !outcome.catalog().hasExpiredResponses()) {
          verify(source, owner, plan, expectedKey); cancellation.checkpoint();
          remember(expectedKey, new Outcome(startedEpoch, outcome.catalog(), null, plan, providerInput));
          return outcome.catalog();
        }
      }
      var catalog=producer.produce(source,owner,plan,cancellation); cancellation.checkpoint();
      if (catalog==null || source.getAuthorityBindings().stream().anyMatch(b->!catalog.supports(b)))
        throw new IllegalStateException("Producer supplied no matching configured proof");
      verify(source,owner,plan,expectedKey); cancellation.checkpoint();
      remember(expectedKey,new Outcome(startedEpoch,catalog,null,plan,providerInput)); return catalog;
    } catch (java.util.concurrent.CancellationException cancelled) { throw cancelled; }
    catch (Exception failure) {
      // Provider exceptions can contain private parameters, URLs, or headers.
      String message="AUTHORITY_BOOTSTRAP_FAILED: "+failure.getClass().getSimpleName();
      remember(expectedKey,new Outcome(startedEpoch,null,message,null,null));
      throw new IllegalStateException(message);
    }
  }
  void verify(Worldview source, Scope owner, Plan plan, String expectedKey) {
    if (!expectedKey.equals(key(source,owner)) || !plan.revision().equals(resolver.resolve(source,owner).revision()))
      throw new IllegalStateException("Authority bootstrap owner/provider metadata changed");
  }
  private void requireEpoch(long expected) {
    if(epoch.get()!=expected) throw new java.util.concurrent.CancellationException("Bootstrap invalidated");
  }
  private void remember(String key, Outcome outcome) {
    requireEpoch(outcome.epoch());
    outcomes.put(key,outcome);
    // Invalidation never waits for this producer or the map operation. The epoch tag is
    // authoritative even if invalidation cleared the map before this old put completed.
    if(epoch.get()!=outcome.epoch()) {
      outcomes.remove(key,outcome);
      throw new java.util.concurrent.CancellationException("Bootstrap invalidated");
    }
    var entries=outcomes.keySet().iterator();
    while(outcomes.size()>4 && entries.hasNext()) outcomes.remove(entries.next());
  }
  // Called under the runtime lifecycle lock: never wait on provider network/configuration.
  void invalidate() { epoch.incrementAndGet(); outcomes.clear(); }

  static Plan resolve(Worldview source, Scope owner) { return resolve(source,owner,null); }
  static Plan resolve(Worldview source, Scope owner,BootstrapSourcePin pin) {
    var identity = owner instanceof org.integratedmodelling.klab.api.scope.UserScope user ? user.getUser()
        : owner != null && owner.getIdentity() instanceof org.integratedmodelling.klab.api.identities.UserIdentity user ? user : null;
    if (!AuthorityComponentResolver.publicServiceScope(owner)
        && (identity==null || identity.isAnonymous() || identity.getUsername()==null || identity.getId()==null))
      throw new SecurityException("Authority bootstrap requires an authenticated user or service scope");
    var selectionScope=pin==null?owner:pin.scope(owner);
    var components=new ArrayList<ComponentDescriptor>(); var resources=new ResourceSet(); var revisions=new TreeMap<String,String>();
    if(pin!=null)revisions.put("explicit.approval",pin.revision());
    for (var binding:source.getAuthorityBindings()) {
      if(pin!=null)pin.requireBinding(binding);
      var resolution=AuthorityComponentResolver.resolve(binding.provider().urn()+"@"+binding.provider().version(),selectionScope);
      var selected=resolution.components().stream().filter(c->c.id().equals(binding.componentUrn())
          && c.version().equals(binding.componentVersion()) && c.authorities().contains(binding.provider())).toList();
      if (selected.size()!=1) throw new IllegalStateException("Exact authorized provider metadata unavailable");
      var component=selected.getFirst();
      // Current immutable catalog explicitly rejects non-public capture; do not invent a principal grant.
      if (component.usageRights()==null || !component.usageRights().isPublic()
          || (identity != null && !component.usageRights().checkAuthorization(identity.getUsername(),identity.getGroups()==null?List.of():identity.getGroups())))
        throw new SecurityException("Unsupported or denied capture rights");
      if (component.fileHash()==null || !component.fileHash().matches("[0-9a-fA-F]{32}"))
        throw new IllegalStateException("Provider has no verifiable artifact pin");
      var selectedResources=resolution.resources().getResults().stream().filter(r->
          r.getResourceUrn().equals(component.id()+"@"+component.version()) && component.version().equals(r.getResourceVersion())).toList();
      if (selectedResources.size()!=1) throw new IllegalStateException("Provider has no unique authorized component source");
      var resource=selectedResources.getFirst();
      if(pin!=null)pin.pinResource(component,resource,resolution.resources());
      resource.getMetadata().put("authority.bootstrap.expected.md5",component.fileHash());
      resources.getResults().add(resource);resources.getServices().putAll(resolution.resources().getServices());
      components.add(component);revisions.put(binding.localId(),resolution.revision());
    }
    resources.setEmpty(resources.getResults().isEmpty());
    return new Plan(List.copyOf(components),resources,digest(JSON.valueToTree(revisions).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  private static CapturedAuthorityCatalog produce(Path root, Map<String,byte[]> core, Worldview source,
      Scope owner, Plan plan, CandidatePublicationCoordinator.Cancellation cancellation) throws Exception {
    Files.createDirectories(root);var job=Files.createTempDirectory(root,"capture-");
    var knowledge=Files.createDirectories(job.resolve("knowledge"));var state=Files.createDirectories(job.resolve("data/services/reasoner"));
    ReasonerService actor=null;
    try {
      for(var entry:core.entrySet()) {
        var target=knowledge.resolve(entry.getKey()).normalize();if(!target.startsWith(knowledge))throw new IllegalArgumentException("Core escapes staging");
        Files.createDirectories(target.getParent());Files.write(target,entry.getValue());
      }
      cancellation.checkpoint();var scope=new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(source).service(),owner);
      actor=new ReasonerService(scope,knowledge,state,CapturedAuthorityCatalog.empty(),true);
      actor.bootstrapDisposalBindings(source);
      // Preserve UserScope HTTP authorization at the export boundary; the private compiler
      // scope carries source/identity only and must never stand in for the requesting owner.
      if (!actor.getComponentRegistry().loadComponents(plan.resources(),owner)) throw new IllegalStateException("Authorized component load failed");
      for(var expected:plan.components()) {
        var installed=actor.getComponentRegistry().getComponent(expected.id(),expected.version());
        if(installed==null || !expected.version().equals(installed.version()) || !expected.fileHash().equals(installed.fileHash())
            || !expected.authorities().equals(installed.authorities())
            || !JSON.valueToTree(expected.usageRights()).equals(JSON.valueToTree(installed.usageRights())))
          throw new IllegalStateException("Installed provider differs from selected content or permissions");
      }
      cancellation.checkpoint();var evidence=CapturedAuthorityCatalog.captureProviderEvidence(state);
      var loaded=actor.loadKnowledge(source,scope);cancellation.checkpoint();
      var versions=actor.successfulAuthorityConfigurations();
      if(scope.hasErrors() || org.integratedmodelling.klab.api.utils.Utils.Notifications.hasErrors(loaded.getNotifications())
          || source.getAuthorityBindings().stream().anyMatch(b->!versions.containsKey(b.localId())))
        throw new IllegalStateException("Real provider configuration did not finish successfully");
      if(!evidence.equals(CapturedAuthorityCatalog.captureProviderEvidence(state))) throw new IllegalStateException("Configured provider provenance changed");
      var capture=Files.createDirectories(state.resolve("captured-authority-state"));
      copy(state.resolve("components"),capture.resolve("provider-components"));
      var documents=new TreeMap<String,Path>();
      if(Files.isDirectory(state.resolve("authority-cache")))try(var files=Files.walk(state.resolve("authority-cache"))) {
        for(var file:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".json")).toList())
          documents(JSON.readTree(Files.readAllBytes(file)),documents);
      }
      var ownedDocuments=new ArrayList<Map<String,Object>>();var temp=Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
      for(var entry:documents.entrySet()) {
        var original=entry.getValue().toRealPath();
        if(!original.startsWith(temp) || !Files.isRegularFile(original) || Files.size(original)>4*1024*1024)
          throw new IllegalStateException("Provider documentation escapes owned supported capture contract");
        var bytes=Files.readAllBytes(original);var hash=digest(bytes);var relative=Path.of("provider-documentation",original.getFileName().toString());
        Files.createDirectories(capture.resolve(relative).getParent());Files.write(capture.resolve(relative),bytes);
        ownedDocuments.add(Map.of("originalUri",entry.getKey(),"bundlePath",relative.toString().replace('\\','/'),"sha256",hash,"bytes",bytes.length,"missingAtCapture",false));
      }
      Files.write(capture.resolve("documentation-materialization.json"),JSON.writeValueAsBytes(ownedDocuments));
      var snapshotHashes = new TreeMap<String, String>();
      for (var entry : actor.configuredAuthoritySnapshots().entrySet()) {
        var bytes = JSON.writeValueAsBytes(entry.getValue());
        var folder = Files.createDirectories(capture.resolve("configured-snapshots"));
        Files.write(folder.resolve(digest(entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8))+".json"), bytes);
        snapshotHashes.put(entry.getKey(), digest(bytes));
      }
      var sourceBytes=JSON.writeValueAsBytes(source);Files.write(capture.resolve("source.json"),sourceBytes);
      var proof=Map.of("inputSha256",digest(sourceBytes),"authorityVersions",versions,"providerEvidence",evidence,
          "configuredSnapshots",snapshotHashes);
      Files.write(capture.resolve("proof.json"),JSON.writeValueAsBytes(proof));
      var result=CapturedAuthorityCatalog.capture(state);cancellation.checkpoint();return result;
    } finally {
      try { if(actor!=null) actor.disposeSemanticEnvironment(); }
      finally { SemanticEnvironment.removeAssets(job); }
    }
  }
  private static void copy(Path from,Path to)throws Exception {
    try(var files=Files.walk(from)){for(var p:files.toList()) {
      if(Files.isSymbolicLink(p))throw new IllegalStateException("Capture contains symbolic link");
      var target=to.resolve(from.relativize(p));if(Files.isDirectory(p))Files.createDirectories(target);else Files.copy(p,target);
    }}
  }
  private static void documents(com.fasterxml.jackson.databind.JsonNode tree,Map<String,Path> found) {
    if(tree.isObject()) {
      var docs=tree.path("documentation");if(docs.isObject())docs.elements().forEachRemaining(value->{
        var uri=java.net.URI.create(value.asText());if("file".equals(uri.getScheme()))found.put(uri.toString(),Path.of(uri));
      });
    }
    if(tree.isContainerNode())tree.elements().forEachRemaining(value->documents(value,found));
  }
  private static String digest(byte[] bytes) {
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    catch(Exception impossible){throw new IllegalStateException(impossible);}
  }
}
