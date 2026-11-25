package org.integratedmodelling.klab.services.reasoner.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;

/** An exclusively owned compiler actor and its private dependencies/assets. */
public final class SemanticEnvironment implements AutoCloseable {
  private final ReasonerService actor;
  private final Path assets;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final SourceValidationProofCache sourceValidationProofs = new SourceValidationProofCache();
  private final HashMap<String, OntologyValidation> ontologyValidations = new HashMap<>();

  private record OntologyValidation(String syntax, long refinement, byte[] response) {}

  /** Seed only from the private candidate's complete document pass, before publication. */
  void retainOntologyValidation(
      KimOntology ontology, DocumentSemanticValidator validator, List<Notification> diagnostics) {
    var result = new SemanticValidationResponse();
    DocumentSemanticValidator.populate(validator, result, diagnostics);
    ontologyValidations.put(
        SemanticEnvironmentFactory.sourceKey(ontology),
        new OntologyValidation(
            ontologySyntax(ontology),
            actor.semanticRefinementGeneration(),
            SourceValidationProofCache.encode(result)));
  }

  SemanticValidationResponse acceptedOntologyValidation(
      SemanticValidationRequest request, long refinement) {
    if (request.getOntology() == null || request.isDiagnosticsOnly()
        || request.isExecutableModels() || request.getExecutionContext() != null
        || request.isClassifierPreparableDependencies()
        || !request.getPreparableMemberQualityDependencies().isEmpty()) return null;
    var saved = ontologyValidations.get(SemanticEnvironmentFactory.sourceKey(request.getOntology()));
    return saved != null && saved.refinement() == refinement
            && saved.syntax().equals(ontologySyntax(request.getOntology()))
        ? SourceValidationProofCache.decode(saved.response())
        : null;
  }

  private static String ontologySyntax(KimOntology ontology) {
    // Compare both beans in their transported representation. Local parser collections such as
    // EnumSets need not serialize in the order used by the captured candidate.
    var syntax = SourceValidationProofCache.documentTree(BeanIngestionPreflight.snapshot(ontology));
    syntax.remove("creationTimestamp");
    // The validator emits its own diagnostics. Request-level syntax errors are gated separately.
    syntax.remove("notifications");
    return SourceValidationProofCache.fingerprint(syntax);
  }

  SourceValidationProofCache sourceValidationProofs() { return sourceValidationProofs; }
  private final long traceGeneration;
  private final String traceKey;
  SemanticEnvironment(ReasonerService actor, Path ownedAssets) {
    this.actor = java.util.Objects.requireNonNull(actor);
    this.assets = ownedAssets.toAbsolutePath().normalize();
    long generation=-1; String key=null;
    if (CandidateTrace.enabled()) try {
      generation=CandidateTrace.job(); key=CandidateTrace.digest(this.assets.getFileName().toString());
    } catch (Throwable ignored) { /* Ownership registration cannot depend on tracing. */ }
    traceGeneration=generation; traceKey=key;
    if (CandidateTrace.enabled()) try {
      CandidateTrace.emit(CandidateTrace.Event.ENV_CREATED,traceGeneration,
          -1,-1,traceKey,null,CandidateTrace.Reason.NONE);
    } catch (Throwable ignored) { /* Diagnostic environment identity is best effort. */ }
  }
  /** Internal routing only: callers must hold their environment lease. */
  public ReasonerService actor() {
    if (closed.get()) throw new IllegalStateException("Semantic environment is closed");
    return actor;
  }
  public String semanticContentFingerprint() { return actor().semanticContentFingerprint(); }
  @Override public void close() throws Exception {
    if (!closed.compareAndSet(false, true)) return;
    sourceValidationProofs.clear();
    ontologyValidations.clear();
    try (var phase = CandidateTrace.phaseAt(CandidateTrace.Phase.PRIVATE_DISPOSE,traceKey,traceGeneration)) {
      Throwable failure=null;
      try { actor.disposeSemanticEnvironment(); }
      catch (Exception | Error error) { failure=error; throw error; }
      finally {
        // This fresh private directory contains only captured bytes written by its factory.
        try { removeAssets(assets,traceGeneration,traceKey); }
        catch (Exception | Error cleanup) {
          if(failure!=null) { if(cleanup!=failure) failure.addSuppressed(cleanup); }
          else throw cleanup;
        }
      }
    }
  }
  static void closeAfterFailure(AutoCloseable owned,Throwable primary) throws Exception {
    try { owned.close(); }
    catch (Exception | Error cleanup) {
      if(primary!=null) { if(cleanup!=primary) primary.addSuppressed(cleanup); }
      else throw cleanup;
    }
  }
  static void removeAssetsAfterFailure(Path assets,Throwable primary) throws Exception {
    try { removeAssets(assets); }
    catch (Exception | Error cleanup) {
      if(primary!=null) { if(cleanup!=primary) primary.addSuppressed(cleanup); }
      else throw cleanup;
    }
  }
  static void removeAssets(Path assets) throws Exception {
    removeAssets(assets,CandidateTrace.enabled() ? CandidateTrace.job() : -1,null);
  }
  private static void removeAssets(Path assets,long generation,String key) throws Exception {
    try (var phase = CandidateTrace.phaseAt(CandidateTrace.Phase.ASSET_REMOVE,key,generation)) {
      if (!Files.exists(assets)) return;
      try (var paths = Files.walk(assets)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
      }
    }
  }
}
