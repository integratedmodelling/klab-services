package org.integratedmodelling.klab.services.reasoner.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;

/** Runs the production compiler in private semantic state, without normal service construction. */
public final class SemanticEnvironmentFactory
    implements CandidatePublicationCoordinator.Builder<Worldview, SemanticEnvironment> {
  private final Path assetRoot;
  private final Map<String, byte[]> core;
  private final String dependencyRevision;

  public SemanticEnvironmentFactory(Path privateAssetRoot, Map<String, byte[]> capturedCore) throws Exception {
    assetRoot = privateAssetRoot.toAbsolutePath().normalize();
    var owned = new LinkedHashMap<String, byte[]>();
    var digest = MessageDigest.getInstance("SHA-256");
    for (var name : capturedCore.keySet().stream().sorted().toList()) {
      if (!name.endsWith(".owl") || Path.of(name).isAbsolute()
          || !assetRoot.resolve(name).normalize().startsWith(assetRoot))
        throw new IllegalArgumentException("Invalid private core catalog entry " + name);
      byte[] bytes = capturedCore.get(name).clone(); owned.put(name, bytes);
      digest.update(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      digest.update((byte)0); digest.update(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    if (owned.isEmpty()) throw new IllegalArgumentException("Candidate requires a captured core catalog");
    core = java.util.Collections.unmodifiableMap(owned);
    dependencyRevision = HexFormat.of().formatHex(digest.digest());
  }
  public String dependencyRevision() { return dependencyRevision; }
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
    cancellation.checkpoint();
    if (input.beanStatus() != CandidatePublicationCoordinator.BeanStatus.VALID)
      throw new IllegalArgumentException("Private compiler requires passed bean preflight");
    if (!input.sourceHashes().equals(sources(input.payload()))
        || !input.dependencyRevision().equals(dependencyRevision))
      throw new IllegalArgumentException("Candidate does not match its captured source/dependency manifest");
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
      var scope = new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(input.payload()).service());
      var actor = new ReasonerService(scope, knowledge, assets);
      environment = new SemanticEnvironment(actor, assets);
      var loaded = actor.loadKnowledge(input.payload(), scope);
      cancellation.checkpoint();
      var diagnostics = new ArrayList<Notification>(scope.getNotifications());
      diagnostics.addAll(loaded.getNotifications());
      if (scope.hasErrors() && !Utils.Notifications.hasErrors(diagnostics))
        diagnostics.add(Notification.error("Candidate compilation emitted errors without source-bound transport"));
      if (!Utils.Notifications.hasErrors(diagnostics)) {
        for (var ontology : input.payload().getOntologies()) {
          cancellation.checkpoint();
          diagnostics.addAll(new DocumentSemanticValidator(actor).validate(ontology));
        }
      }
      var messages = diagnostics.stream().map(Notification::getMessage).toList();
      var status = messages.stream().anyMatch(m -> m.contains("CANDIDATE_ISOLATION_UNSUPPORTED"))
          ? CandidatePublicationCoordinator.LogicalStatus.UNRESOLVED
          : Utils.Notifications.hasErrors(diagnostics)
              ? CandidatePublicationCoordinator.LogicalStatus.INVALID
              : CandidatePublicationCoordinator.LogicalStatus.VALID;
      if (!actor.owl().hasReasoner()) status = CandidatePublicationCoordinator.LogicalStatus.UNRESOLVED;
      if (input.payload().getOntologies().stream()
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
