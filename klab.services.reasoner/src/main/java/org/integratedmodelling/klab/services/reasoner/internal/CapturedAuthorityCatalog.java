package org.integratedmodelling.klab.services.reasoner.internal;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.security.*;
import java.time.Clock;
import java.util.*;
import java.util.jar.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.ConfiguredAuthoritySnapshot;
import org.integratedmodelling.klab.api.services.SnapshottingAuthority;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions.ComponentDescriptor;

/** Immutable public-provider catalog. Configured snapshots restore offline for candidates and
 * accepted lookups. Legacy providers replay responses; accepted runtimes may opt into their live
 * operations using owned archives. Capture itself never configures providers. */
public final class CapturedAuthorityCatalog {
  private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  private final Map<String, Provider> providers;
  private final Map<String, byte[]> records, documentation, codelists;
  private final Map<String, byte[]> validatedRequests;
  private final String revision;
  private record FrozenConfiguredSnapshot(ConfiguredAuthoritySnapshot snapshot, String contentSha256) {}
  private final Map<String, FrozenConfiguredSnapshot> configuredSnapshots;
  public record ProviderEvidence(String implementation, String componentSha256, String policySha256, String metadataSha256) {}
  private record Provider(ComponentDescriptor component, String className, String providerUrn, String implementation,
      Authority.CachePolicy policy, Set<String> boundaries, byte[] capabilities, byte[] seeds, byte[] archive, boolean snapshotCapable) {}

  @com.fasterxml.jackson.annotation.JsonIgnoreProperties("updateable")
  private abstract static class ComponentWireMixin {}

  public static CapturedAuthorityCatalog empty() { return new CapturedAuthorityCatalog(Map.of(), Map.of(), Map.of(), Map.of(), Map.of()); }
  private CapturedAuthorityCatalog(Map<String, Provider> providers, Map<String, byte[]> records,
      Map<String, byte[]> documentation, Map<String, byte[]> codelists, Map<String, byte[]> validatedRequests) {
    this(providers, records, documentation, codelists, validatedRequests, Map.of());
  }
  private CapturedAuthorityCatalog(Map<String, Provider> providers, Map<String, byte[]> records,
      Map<String, byte[]> documentation, Map<String, byte[]> codelists, Map<String, byte[]> validatedRequests,
      Map<String, FrozenConfiguredSnapshot> configuredSnapshots) {
    this.configuredSnapshots = Map.copyOf(configuredSnapshots);
    this.providers = Map.copyOf(providers); this.records = freeze(records);
    this.documentation = freeze(documentation); this.codelists = freeze(codelists); this.validatedRequests = freeze(validatedRequests);
    var manifest = new TreeMap<String, String>();
    providers.forEach((urn, p) -> manifest.put("provider:" + urn, p.implementation() + ":" + digest(bytes(p.boundaries().stream().sorted().toList())) + ":" + canonicalMetadataDigest(p.capabilities())
        + ":" + canonicalMetadataDigest(p.seeds()) + ":" + digest(bytes(p.component())) + ":" + digest(bytes(p.policy()))));
    this.records.forEach((k, v) -> manifest.put("response:" + k, digest(v)));
    this.configuredSnapshots.forEach((k, v) -> manifest.put("configured-snapshot:" + k, v.contentSha256()));
    this.documentation.forEach((k, v) -> manifest.put("documentation:" + k, digest(v)));
    this.codelists.forEach((k, v) -> manifest.put("codelist:" + k, digest(v)));
    this.validatedRequests.forEach((k, v) -> manifest.put("validated-request:" + k, digest(v)));
    revision = digest(bytes(manifest));
  }
  private static Map<String, byte[]> freeze(Map<String, byte[]> source) {
    var owned = new LinkedHashMap<String, byte[]>(); source.forEach((k, v) -> owned.put(k, v.clone()));
    return Collections.unmodifiableMap(owned);
  }
  public String revision() { return revision; }
  /** Expired provider responses need a fresh capture before compiling another candidate. */
  public boolean hasExpiredResponses() {
    for (var response : records.values()) {
      try { if (JSON.readTree(response).path("expires").asLong() <= Clock.systemUTC().millis()) return true; }
      catch (IOException malformed) { return true; }
    }
    return false;
  }

  /** Owned public descriptors for discovery; callers cannot mutate the captured catalog. */
  public List<ComponentDescriptor> components() {
    var mapper = JacksonConfiguration.newObjectMapper().addMixIn(ComponentDescriptor.class, ComponentWireMixin.class);
    var result = new TreeMap<String, ComponentDescriptor>();
    for (var provider : providers.values()) {
      var component = provider.component();
      result.putIfAbsent(component.id(), mapper.convertValue(component, ComponentDescriptor.class));
    }
    return List.copyOf(result.values());
  }

  /** Read a previously captured local Reasoner data directory, with its original public rights. */
  public static CapturedAuthorityCatalog capture(Path data) throws Exception {
    var sourceFile = data.resolve("captured-authority-state/source.json");
    var proofFile = data.resolve("captured-authority-state/proof.json");
    if (!Files.exists(sourceFile) && !Files.exists(proofFile)) return captureRaw(data);
    if (!Files.isRegularFile(sourceFile) || !Files.isRegularFile(proofFile)) throw unresolved("authority capture source/proof is incomplete");
    byte[] source = Files.readAllBytes(sourceFile); var proof = JSON.readTree(Files.readAllBytes(proofFile));
    if (!digest(source).equals(proof.path("inputSha256").asText())) throw unresolved("authority capture source differs from its successful producer input");
    var bindings = JSON.convertValue(proof.get("authorityVersions"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Long>>() {});
    if (bindings == null) throw unresolved("authority capture has no successful configured-binding proof");
    var evidence = JSON.convertValue(proof.get("providerEvidence"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, ProviderEvidence>>() {});
    if (evidence == null) throw unresolved("authority capture has no configured provider provenance");
    var snapshotHashes = proof.has("configuredSnapshots") ? JSON.convertValue(proof.get("configuredSnapshots"),
        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}) : Map.<String,String>of();
    return captureRecorded(data, JacksonConfiguration.newObjectMapper().readValue(source, Worldview.class), bindings, evidence, snapshotHashes);
  }
  private static CapturedAuthorityCatalog captureRaw(Path data) throws Exception {
    var mutableComponents = data.resolve("components");
    var immutableComponents = data.resolve("captured-authority-state/provider-components");
    boolean hasImmutableComponents = Files.isRegularFile(immutableComponents.resolve("catalog.json"));
    var components = hasImmutableComponents ? immutableComponents : mutableComponents;
    var metadata = components.resolve("catalog.json");
    if (!Files.isRegularFile(metadata)) return empty();
    var mapper = JacksonConfiguration.newObjectMapper().addMixIn(ComponentDescriptor.class, ComponentWireMixin.class);
    var descriptors = mapper.readValue(Files.readAllBytes(metadata), ComponentDescriptor[].class);
    if (hasImmutableComponents) verifyRegisteredComponents(mutableComponents, descriptors, mapper);
    var providers = new LinkedHashMap<String, Provider>();
    for (var descriptor : descriptors) {
      if (descriptor.authorities().isEmpty()) continue;
      // The current anonymous fixture only has public components. Never infer private grants.
      var rights = mapper.valueToTree(descriptor.usageRights());
      if (rights == null || !rights.path("public").asBoolean(false))
        throw unresolved("non-public component requires a captured authorized principal: " + descriptor.id());
      Path artifact = components.resolve("plugins").resolve(descriptor.sourceArchive().getName());
      byte[] archive = Files.readAllBytes(artifact);
      if (!HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(archive)).equals(descriptor.fileHash()))
        throw unresolved("installed component content disagrees with its descriptor: " + descriptor.id());
      for (String className : CapturedProviderImplementations.discover(artifact, descriptor)) {
      var loader = new CapturedLoader(archive, className.substring(0, className.lastIndexOf('.') + 1));
      Authority instance = null;
      try {
        var type = loader.loadClass(className);
        instance = (Authority)type.getConstructor().newInstance();
        var annotation = type.getAnnotation(org.integratedmodelling.klab.api.services.reasoner.Authority.class);
        if (annotation == null || !annotation.urn().equals(instance.getUrn())) throw unresolved("provider annotation mismatch");
        boolean snapshotCapable = instance instanceof SnapshottingAuthority;
        if (!snapshotCapable && !CapturedProviderImplementations.legacy(descriptor, className))
          throw unresolved("provider requires a configured snapshot capability: " + descriptor.id());
        var advertised = descriptor.authorities().stream().filter(a -> a.urn().equals(annotation.urn())).findFirst().orElseThrow();
        if (!advertised.version().toString().equals(annotation.version())) throw unresolved("provider version mismatch");
        if (type.getMethod("getCodelists", String.class).getDeclaringClass() != Authority.class
            || type.getMethod("getCodelistDefinitions", String.class).getDeclaringClass() != Authority.class)
          throw unresolved("configuration-dependent provider codelists need an explicit frozen response");
        var capabilities = mapper.valueToTree(instance.getCapabilities());
        ((com.fasterxml.jackson.databind.node.ObjectNode)capabilities).put("subAuthoritiesSearchFilters", instance.getCapabilities().areSubAuthoritiesSearchFilters());
        var record = new Provider(mapper.convertValue(descriptor, ComponentDescriptor.class), className, instance.getUrn(),
            className + ":" + annotation.version() + ":" + digest(archive), instance.getCachePolicy(),
            Set.copyOf(instance.getSemanticBoundaries()), mapper.writeValueAsBytes(capabilities), mapper.writeValueAsBytes(instance.getCodelistDefinitions(null)), archive, snapshotCapable);
        if (providers.put(instance.getUrn(), record) != null) throw unresolved("duplicate provider: " + instance.getUrn());
      } finally {
        try { if (instance != null) closeOwnedProvider(instance); }
        finally { loader.clear(); }
      }
      }
    }
    var records = new LinkedHashMap<String, byte[]>(); var documents = new LinkedHashMap<String, byte[]>();
    var ownedDocuments = CapturedDocumentation.read(data.resolve("captured-authority-state"));
    // An immutable transport must never discover replacement bytes through original URLs.
    // Empty means reject any file documentation URI until its owned manifest is supplied.
    if (ownedDocuments == null && (hasImmutableComponents
        || Files.exists(data.resolve("captured-authority-state/proof.json")))) ownedDocuments = Map.of();
    var cache = data.resolve("authority-cache"); long total = 0;
    if (Files.isDirectory(cache)) try (var paths = Files.walk(cache)) {
      for (var file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
        if (Files.size(file) > 4 * 1024 * 1024) throw unresolved("provider response exceeds capture limit");
        byte[] contents = Files.readAllBytes(file); total += contents.length;
        if (total > 256L * 1024 * 1024) throw unresolved("provider capture exceeds total limit");
        var entry = JSON.readTree(contents);
        if (!entry.has("value") || !entry.path("expires").isIntegralNumber()) throw unresolved("invalid provider cache envelope");
        if (entry.path("expires").asLong() <= Clock.systemUTC().millis()) continue;
        records.put(cache.relativize(file).toString().replace('\\', '/'), contents);
        captureDocuments(entry.get("value"), documents, ownedDocuments);
      }
    }
    var ledgers = new LinkedHashMap<String, byte[]>(); var ledger = data.resolve("authority-codelists");
    if (Files.isDirectory(ledger)) try (var paths = Files.list(ledger)) {
      for (var file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).sorted().toList())
        ledgers.put(file.getFileName().toString(), Files.readAllBytes(file));
    }
    var snapshots = new TreeMap<String, FrozenConfiguredSnapshot>();
    Path snapshotRoot = data.resolve("captured-authority-state/configured-snapshots"); long snapshotBytes = 0;
    if (Files.isDirectory(snapshotRoot)) try (var paths = Files.list(snapshotRoot)) {
      for (var file : paths.filter(Files::isRegularFile).sorted().toList()) {
        if (Files.isSymbolicLink(file) || Files.size(file) > 48L * 1024 * 1024)
          throw unresolved("configured snapshot exceeds file bounds");
        snapshotBytes += Files.size(file);
        if (snapshotBytes > 384L * 1024 * 1024) throw unresolved("configured snapshots exceed total bounds");
        var contents = Files.readAllBytes(file);
        var snapshot = JSON.readValue(contents, ConfiguredAuthoritySnapshot.class);
        if (snapshot == null || !file.getFileName().toString().equals(digest(snapshot.request().name().getBytes(java.nio.charset.StandardCharsets.UTF_8))+".json")
            || snapshots.putIfAbsent(snapshot.request().name(), new FrozenConfiguredSnapshot(snapshot, digest(contents))) != null)
          throw unresolved("invalid configured snapshot identity");
      }
    }
    return new CapturedAuthorityCatalog(providers, records, documents, ledgers, Map.of(), snapshots);
  }
  /** Registration rewrites installation bookkeeping, not the proved configuration contract.
   * Immutable proof metadata remains byte-pinned; current permissions/contributions and actual
   * installed code must still match before that snapshot can serve authority requests. */
  private static void verifyRegisteredComponents(Path currentRoot, ComponentDescriptor[] proved,
      ObjectMapper mapper) throws Exception {
    var file = currentRoot.resolve("catalog.json");
    if (!Files.isRegularFile(file)) throw unresolved("registered component catalog is unavailable");
    var registered = mapper.readValue(Files.readAllBytes(file), ComponentDescriptor[].class);
    for (var expected : proved) {
      if (expected.authorities().isEmpty()) continue;
      var matches = Arrays.stream(registered).filter(c -> c.id().equals(expected.id())).toList();
      if (matches.size() != 1 || !Arrays.equals(componentContract(matches.getFirst(), mapper), componentContract(expected, mapper)))
        throw unresolved("registered provider permissions or configuration contract changed: " + expected.id());
      var actual = matches.getFirst();
      if (actual.sourceArchive() == null || expected.sourceArchive() == null)
        throw unresolved("registered provider has no archive: " + expected.id());
      var installed = currentRoot.resolve("plugins").resolve(actual.sourceArchive().getName());
      var frozen = currentRoot.getParent().resolve("captured-authority-state/provider-components/plugins").resolve(expected.sourceArchive().getName());
      if (!digest(Files.readAllBytes(installed)).equals(digest(Files.readAllBytes(frozen))))
        throw unresolved("registered provider archive differs from immutable successful proof: " + expected.id());
    }
  }
  private static byte[] componentContract(ComponentDescriptor descriptor, ObjectMapper mapper) throws Exception {
    var tree = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.valueToTree(descriptor);
    // These fields identify installation location/update bookkeeping, not provider behavior,
    // version, permissions, advertised authority contracts or contribution definitions.
    tree.remove(List.of("sourceArchive", "timestamp", "latestVersionTimestamp", "updateStatus", "sourceServiceId", "updateable"));
    return bytes(JSON.convertValue(tree, Object.class));
  }
  /** The producer must supply the exact source and bindings successfully configured by its real compiler.
   * Response cache presence alone is insufficient proof for a configuration that fetched no identities. */
  public static CapturedAuthorityCatalog captureRecorded(Path data, Worldview source,
      Map<String, Long> successfullyConfiguredBindings, Map<String, ProviderEvidence> configuredProviderEvidence) throws Exception {
    return captureRecorded(data, source, successfullyConfiguredBindings, configuredProviderEvidence, Map.of());
  }
  public static CapturedAuthorityCatalog captureRecorded(Path data, Worldview source,
      Map<String, Long> successfullyConfiguredBindings, Map<String, ProviderEvidence> configuredProviderEvidence,
      Map<String, String> configuredSnapshotHashes) throws Exception {
    var captured = captureRaw(data); var requests = new LinkedHashMap<String, byte[]>();
    if (configuredSnapshotHashes == null || !configuredSnapshotHashes.keySet().equals(captured.configuredSnapshots.keySet()))
      throw unresolved("configured snapshots differ from successful producer proof");
    for (var entry : captured.configuredSnapshots.entrySet()) {
      if (!entry.getValue().contentSha256().equals(configuredSnapshotHashes.get(entry.getKey())))
        throw unresolved("configured snapshot content differs from successful producer proof");
    }
    if (!source.getAuthorityBindings().stream().map(Worldview.AuthorityBinding::localId).toList()
        .containsAll(captured.configuredSnapshots.keySet())) throw unresolved("unbound configured snapshot");
    var currentEvidence = captured.providerEvidence();
    for (var binding : source.getAuthorityBindings()) {
      if (!successfullyConfiguredBindings.containsKey(binding.localId())
          || successfullyConfiguredBindings.get(binding.localId()) < 0) throw unresolved("configuration has no successful producer proof: " + binding.localId());
      var providerUrn = binding.provider().urn();
      if (configuredProviderEvidence == null || !Objects.equals(currentEvidence.get(providerUrn), configuredProviderEvidence.get(providerUrn))
          || !currentEvidence.containsKey(providerUrn))
        throw unresolved("provider implementation or configuration metadata differs from successful producer proof: " + binding.localId());
      var declaration = source.allConceptStatements().stream().filter(statement ->
          binding.localId().equals(statement.getAuthorityRequired())
          && (statement.getNamespace() + ":" + statement.getUrn()).equals(binding.rootIdentity())).findFirst().orElseThrow();
      var request = new Authority.ConfigurationRequest(source.getUrn(), binding.localId(), binding.rootIdentity(), declaration.getAuthorityParameters());
      var provider = captured.providers.get(providerUrn);
      var frozenSnapshot = captured.configuredSnapshots.get(binding.localId());
      var snapshot = frozenSnapshot == null ? null : frozenSnapshot.snapshot();
      if (provider.snapshotCapable() && (snapshot == null || !snapshot.providerUrn().equals(providerUrn)
          || !snapshot.request().equals(AuthorityBindings.providerRequest(request))))
        throw unresolved("configured snapshot is absent or changed: " + binding.localId());
      if (!provider.snapshotCapable() && snapshot != null) throw unresolved("provider cannot restore configured state");
      String ledgerKey = request.worldview() + "\0" + request.name() + "\0"
          + request.rootIdentity() + "\0" + request.parameters().get("urn");
      byte[] ledger = captured.codelists.get(CachedAuthority.digest(ledgerKey) + ".json");
      long revision = 0;
      if (ledger != null) {
        var state = JSON.readValue(ledger, AuthorityCodelists.State.class);
        if (state == null || state.proposals() == null || state.revision() < 0)
          throw unresolved("invalid captured authority ledger: " + binding.localId());
        revision = state.revision();
      }
      if (revision != successfullyConfiguredBindings.get(binding.localId()))
        throw unresolved("authority ledger differs from successful producer revision: " + binding.localId());
      requests.put(binding.localId(), bytes(request));
    }
    return new CapturedAuthorityCatalog(captured.providers, captured.records, captured.documentation, captured.codelists, requests, captured.configuredSnapshots);
  }
  /** Capture this before the real producer configures providers; retain it with its successful result.
   * Reading current metadata later is not evidence that an earlier producer configured that code. */
  public static Map<String, ProviderEvidence> captureProviderEvidence(Path data) throws Exception {
    return captureRaw(data).providerEvidence();
  }
  private Map<String, ProviderEvidence> providerEvidence() {
    var result = new TreeMap<String, ProviderEvidence>();
    providers.forEach((urn, p) -> result.put(urn, new ProviderEvidence(p.implementation(),
        digest(bytes(p.component())), digest(bytes(p.policy())),
        digest(bytes(List.of(p.boundaries().stream().sorted().toList(), canonicalMetadataDigest(p.capabilities()), canonicalMetadataDigest(p.seeds())))))));
    return Map.copyOf(result);
  }
  private static void captureDocuments(JsonNode value, Map<String, byte[]> result,
      Map<String, byte[]> ownedDocuments) throws Exception {
    if (value.isArray()) { for (var item : value) captureDocuments(item, result, ownedDocuments); return; }
    for (var document : value.path("documentation")) {
      var uri = URI.create(document.asText());
      if ("file".equals(uri.getScheme()) && !result.containsKey(uri.toString())) {
        if (ownedDocuments != null) {
          result.put(uri.toString(), CapturedDocumentation.lookup(ownedDocuments, uri.toString())); continue;
        }
        Path file = Path.of(uri);
        var base = Path.of(System.getProperty("java.io.tmpdir"));
        boolean allowed = false;
        for (var provider : List.of("klab.authority.taxa", "klab.authority.pubchem")) {
          var root = base.resolve(provider).resolve("documentation").toAbsolutePath().normalize();
          if (file.toAbsolutePath().normalize().startsWith(root)
              && (!Files.exists(file) || (Files.exists(root) && file.toRealPath().startsWith(root.toRealPath())))) allowed = true;
        }
        if (!allowed) throw unresolved("provider documentation escapes declared capture roots: " + file);
        if (!Files.exists(file)) { result.put(uri.toString(), new byte[0]); continue; }
        if (!Files.isRegularFile(file) || Files.size(file) > 4 * 1024 * 1024) throw unresolved("captured documentation is missing or too large: " + file);
        result.put(uri.toString(), Files.readAllBytes(file));
      }
    }
  }
  public boolean supports(Worldview.AuthorityBinding binding) {
    var provider = providers.get(binding.provider().urn());
    return provider != null && validatedRequests.containsKey(binding.localId()) && provider.component().id().equals(binding.componentUrn())
        && provider.component().version().equals(binding.componentVersion())
        && provider.component().authorities().stream().anyMatch(a -> a.equals(binding.provider()));
  }
  public void materializeCodelists(Path state) throws IOException {
    if (!codelists.isEmpty()) {
      var root = Files.createDirectories(state.resolve("authority-codelists"));
      for (var entry : codelists.entrySet()) Files.write(root.resolve(entry.getKey()), entry.getValue());
    }
  }
  public Authority provider(Worldview.AuthorityBinding binding, Authority.ConfigurationRequest request, Path assets) {
    return provider(binding, request, assets, () -> false);
  }
  /** Only an accepted runtime may opt into live operations; compilation keeps replay-only providers. */
  public Authority provider(Worldview.AuthorityBinding binding, Authority.ConfigurationRequest request, Path assets,
      java.util.function.BooleanSupplier interactive) {
    if (!supports(binding)) throw unresolved("provider binding is absent or changed: " + binding.localId());
    if (!Arrays.equals(validatedRequests.get(binding.localId()), bytes(request)))
      throw unresolved("authority configuration changed since its validated capture: " + binding.localId());
    var provider = providers.get(binding.provider().urn());
    String fingerprint = digest(bytes(List.of("authority-cache-v2", request.worldview(), request.name(),
        request.rootIdentity(), request.parameters(), binding.provider().urn(), provider.implementation(), provider.policy())));
    String prefix = segment("wv", request.worldview()) + "/" + segment("configuration", request.name()) + "/" + fingerprint + "/";
    return new Snapshot(provider, request, prefix, assets, "", new BridgeState(), interactive);
  }
  public boolean authorized(Worldview.AuthorityBinding binding, org.integratedmodelling.klab.api.scope.Scope scope) {
    return supports(binding) && providers.get(binding.provider().urn()).component().usageRights().checkAuthorization(scope);
  }
  private static final class BridgeState {
    volatile String configured;
    Authority live, implementation;
    String liveId;
    CapturedLoader loader;
  }
  private final class Snapshot implements Authority {
    private final Provider provider; private final ConfigurationRequest original;
    private final String prefix, view; private final Path assets; private final BridgeState bridge;
    private final java.util.function.BooleanSupplier interactive;
    Snapshot(Provider provider, ConfigurationRequest original, String prefix, Path assets, String view, BridgeState bridge,
        java.util.function.BooleanSupplier interactive) {
      this.provider = provider; this.original = original; this.prefix = prefix; this.assets = assets; this.view = view; this.bridge = bridge;
      this.interactive = interactive;
    }
    @Override public String getUrn() { return provider.providerUrn(); }
    @Override public CachePolicy getCachePolicy() { return provider.policy(); }
    @Override public Set<String> getSemanticBoundaries() { return provider.boundaries(); }
    @Override public Map<String, CodelistDefinition> getCodelistDefinitions(String id) {
      check(id);
      try { return JacksonConfiguration.newObjectMapper().readValue(provider.seeds(),
          new com.fasterxml.jackson.core.type.TypeReference<Map<String, CodelistDefinition>>() {}); }
      catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    @Override public Map<String, org.integratedmodelling.klab.api.knowledge.Codelist> getCodelists(String id) {
      var result = new LinkedHashMap<String, org.integratedmodelling.klab.api.knowledge.Codelist>();
      getCodelistDefinitions(id).forEach((name, definition) -> result.put(name, definition.codelist())); return result;
    }
    @Override public Capabilities getCapabilities() {
      try {
        var data = JSON.readTree(provider.capabilities());
        return (Capabilities)java.lang.reflect.Proxy.newProxyInstance(Capabilities.class.getClassLoader(), new Class<?>[]{Capabilities.class}, (proxy, method, arguments) -> {
          String name = method.getName();
          String property = name.startsWith("get") ? Character.toLowerCase(name.charAt(3)) + name.substring(4)
              : name.startsWith("is") ? Character.toLowerCase(name.charAt(2)) + name.substring(3) : "subAuthoritiesSearchFilters";
          return JacksonConfiguration.newObjectMapper().convertValue(data.get(property),
              JacksonConfiguration.newObjectMapper().getTypeFactory().constructType(method.getGenericReturnType()));
        });
      } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    @Override public synchronized String configure(ConfigurationRequest request) {
      if (!AuthorityBindings.providerRequest(original).equals(request)) throw unresolved("provider parameters changed since capture: " + original.name());
      bridge.configured = "snapshot:" + revision + ":" + original.name();
      if (configuredSnapshots.containsKey(original.name())) try { live(); }
      catch (RuntimeException failure) { bridge.configured = null; throw failure; }
      return bridge.configured;
    }
    @Override public synchronized void releaseConfiguration(String id) {
      check(id);
      synchronized (bridge) {
        bridge.configured = null;
        try { if (bridge.live != null) bridge.live.releaseConfiguration(bridge.liveId); }
        finally { closeLive(); }
      }
    }
    private void closeLive() {
      try { if (bridge.implementation != null) closeOwnedProvider(bridge.implementation); }
      finally {
        if (bridge.loader != null) bridge.loader.clear();
        bridge.live = null; bridge.implementation = null; bridge.loader = null; bridge.liveId = null;
      }
    }
    private Authority live() {
      synchronized (bridge) {
        if (bridge.live == null) {
          try {
            // Use owned archive bytes, never a mutable installed plug-in or its original path.
            bridge.loader = new CapturedLoader(provider.archive(), provider.className().substring(0, provider.className().lastIndexOf('.') + 1));
            bridge.implementation = (Authority)bridge.loader.loadClass(provider.className()).getConstructor().newInstance();
            var snapshot = configuredSnapshots.get(original.name());
            if (snapshot != null) {
              if (!(bridge.implementation instanceof SnapshottingAuthority restoring))
                throw unresolved("configured snapshot implementation lost its restore capability");
              bridge.liveId = restoring.restoreConfiguration(AuthorityBindings.providerRequest(original), snapshot.snapshot());
              if (bridge.liveId == null || bridge.liveId.isBlank()) throw unresolved("snapshot restore returned no bridge");
              bridge.live = restoring;
            } else {
              var cached = new CachedAuthority(bridge.implementation, assets.resolve("interactive-authority-cache"),
                  Clock.systemUTC(), provider.implementation());
              bridge.liveId = cached.configure(original);
              bridge.live = cached;
            }
          } catch (Exception failure) {
            closeLive();
            throw new IllegalStateException("Cannot initialize accepted authority provider", failure);
          }
        }
        Authority result = bridge.live;
        if (!view.isEmpty()) for (var segment : view.split("/")) {
          result = result.subAuthority(segment);
          if (result == null) throw new IllegalStateException("Accepted authority view is unavailable");
        }
        return result;
      }
    }
    private void check(String id) { if (bridge.configured == null || !bridge.configured.equals(id)) throw new IllegalStateException("Unknown or released captured authority bridge"); }
    private JsonNode response(Object operation) {
      var contents = records.get(prefix + digest(bytes(operation)) + ".json");
      if (contents == null) throw unresolved("uncaptured provider response for " + original.name() + ": " + operation);
      try {
        var entry = JSON.readTree(contents);
        if (entry.path("expires").asLong() <= Clock.systemUTC().millis()) throw unresolved("captured provider response expired: " + original.name());
        return entry.get("value");
      } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    private Identity identity(JsonNode source) {
      var owned = source.deepCopy();
      var docs = owned.path("documentation");
      if (docs.isObject()) docs.fields().forEachRemaining(entry -> {
        String location = entry.getValue().asText(); var bytes = documentation.get(location);
        if (bytes != null && bytes.length == 0) throw unresolved("captured provider documentation is unavailable: " + location);
        if (bytes != null) try {
          var target = CapturedDocumentation.materialize(assets, location, bytes);
          ((com.fasterxml.jackson.databind.node.ObjectNode)docs).put(entry.getKey(), target.toUri().toString());
        } catch (Exception failure) { throw new IllegalStateException("Cannot materialize owned documentation", failure); }
      });
      try { return JSON.treeToValue(owned, CachedAuthority.StoredIdentity.class); }
      catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    @Override public synchronized Identity resolveIdentity(String id, String code) { return resolveIdentity(id, code, ResolutionMode.CONFIGURED); }
    @Override public synchronized Identity resolveIdentity(String id, String code, ResolutionMode mode) {
      check(id);
      if (configuredSnapshots.containsKey(original.name()) || interactive.getAsBoolean()) return live().resolveIdentity(bridge.liveId, code, mode);
      return identity(response(List.of(mode == ResolutionMode.FULL_HIERARCHY ? "identity-full" : "identity", view, code)));
    }
    @Override public synchronized List<Identity> search(String query, String filter, String id) {
      check(id); var result = new ArrayList<Identity>();
      if (configuredSnapshots.containsKey(original.name()) || interactive.getAsBoolean()) return live().search(query, filter, bridge.liveId);
      response(Arrays.asList("search", view, query, filter)).forEach(item -> result.add(identity(item))); return result;
    }
    @Override public synchronized Identity reconcile(String id, Map<String, String> fields) {
      check(id);
      if (configuredSnapshots.containsKey(original.name()) || interactive.getAsBoolean()) return live().reconcile(bridge.liveId, fields);
      return identity(response(List.of("reconcile", view, fields)));
    }
    @Override public Authority subAuthority(String name) {
      if (provider.boundaries().contains(name)) {
        return new Snapshot(provider, original, prefix, assets, view.isEmpty() ? name : view + "/" + name, bridge, interactive);
      }
      return null;
    }
  }
  private static String suffix(String location) {
    String path = URI.create(location).getPath(); int dot = path.lastIndexOf('.');
    return dot < 0 ? "bin" : path.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
  }
  private static String segment(String prefix, String value) {
    String readable = value.replaceAll("[^A-Za-z0-9_.-]", "_"); if (readable.length() > 48) readable = readable.substring(0, 48);
    return prefix + "_" + readable + "_" + CachedAuthority.digest(value).substring(0, 16);
  }
  private static byte[] bytes(Object value) {
    try { return JSON.writeValueAsBytes(value); } catch (IOException failure) { throw new IllegalArgumentException(failure); }
  }
  /** Provider JSON object-member order can differ between JVMs. Preserve every value and
   * array order, but encode nested object members canonically before provenance comparison. */
  static String canonicalMetadataDigest(byte[] contents) {
    try { return digest(bytes(JSON.readValue(contents, Object.class))); }
    catch (IOException failure) { throw new IllegalArgumentException("Invalid provider metadata", failure); }
  }
  private static String digest(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
  }
  private static UnsupportedOperationException unresolved(String reason) {
    return new UnsupportedOperationException("CANDIDATE_ISOLATION_UNSUPPORTED: " + reason);
  }
  public static void closeOwnedProvider(Authority provider) {
    try {
      try { if (provider instanceof AutoCloseable closeable) closeable.close(); }
      finally { closeOwnedClients(provider, provider.getClass().getClassLoader(), Collections.newSetFromMap(new IdentityHashMap<>())); }
    }
    catch (Exception failure) { throw new IllegalStateException("Owned provider cleanup failed", failure); }
  }
  private static void closeOwnedClients(Object value, ClassLoader owner, Set<Object> seen) throws Exception {
    if (value == null || !seen.add(value)) return;
    if (value instanceof HttpClient client) { client.close(); return; }
    if (value.getClass().getClassLoader() != owner) return;
    for (var field : value.getClass().getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers())) {
      field.setAccessible(true); closeOwnedClients(field.get(value), owner, seen);
    }
  }
  private static final class CapturedLoader extends ClassLoader {
    private final Map<String, byte[]> files = new HashMap<>(); private final String prefix;
    CapturedLoader(byte[] archive, String prefix) throws IOException {
      super(Authority.class.getClassLoader()); this.prefix = prefix;
      try (var jar = new JarInputStream(new ByteArrayInputStream(archive))) {
        JarEntry entry; while ((entry = jar.getNextJarEntry()) != null) if (!entry.isDirectory()) files.put(entry.getName(), jar.readAllBytes());
      }
    }
    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!name.startsWith(prefix)) return super.loadClass(name, resolve);
      synchronized (getClassLoadingLock(name)) {
        var loaded = findLoadedClass(name);
        if (loaded == null) { var contents = files.get(name.replace('.', '/') + ".class"); if (contents == null) throw new ClassNotFoundException(name); loaded = defineClass(name, contents, 0, contents.length); }
        if (resolve) resolveClass(loaded); return loaded;
      }
    }
    @Override public InputStream getResourceAsStream(String name) { var bytes = files.get(name); return bytes == null ? super.getResourceAsStream(name) : new ByteArrayInputStream(bytes); }
    void clear() { files.clear(); }
  }
}
