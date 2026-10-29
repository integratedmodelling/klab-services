package org.integratedmodelling.klab.services.resources;

import java.util.*;
import java.time.Duration;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;
import org.integratedmodelling.klab.services.resources.persistence.ModelCatalog;

/** Independent model admission snapshots. Authoring ASTs and their parser diagnostics are untouched. */
final class NamespaceModelIndex {
  private record Fingerprint(long timestamp, String sourceHash, long knowledgeRevision, String serviceId, String semanticFingerprint, String semanticSessionId) {}
  private record Attempt(Fingerprint fingerprint, boolean retryable, long attemptedAt,
      List<Notification> diagnostics, String admission) {}
  private final Map<String, Attempt> attempts = new java.util.concurrent.ConcurrentHashMap<>();
  private final long retryDelay;

  NamespaceModelIndex() { this(Duration.ofSeconds(5).toNanos()); }
  NamespaceModelIndex(long retryDelay) { this.retryDelay = retryDelay; }

  synchronized void reconcile(Collection<KimNamespace> namespaces, ModelCatalog catalog, Scope scope,
      long knowledgeRevision, long now) {
    var present = new HashSet<String>();
    for (var namespace : namespaces) {
      String urn = namespace.getUrn(); present.add(urn);
      var currentSemantic = ModelValidationGate.currentSemanticSnapshot(scope);
      var fingerprint = new Fingerprint(namespace.getLastUpdateTimestamp(),
          SemanticValidationRequest.sourceHash(namespace.getSourceCode()), knowledgeRevision,
          currentSemantic==null?null:currentSemantic.serviceId(), currentSemantic==null?null:currentSemantic.semanticFingerprint(), currentSemantic==null?null:currentSemantic.semanticSessionId());
      var previous = attempts.get(urn);
      if (previous != null && previous.fingerprint().equals(fingerprint)
          && (!previous.retryable() || now - previous.attemptedAt() < retryDelay)) continue;
      try (var validation=ModelValidationGate.openPublicationSession(namespace,scope)) {
        var response=ModelValidationGate.sourceAdmission(namespace, scope, knowledgeRevision);
        var semantic=ModelValidationGate.semanticSnapshot(scope);
        var hashes=new LinkedHashMap<String,String>();
        for (var statement:namespace.getStatements()) {
          if (statement instanceof org.integratedmodelling.klab.api.lang.kim.KimModel model) {
            try {
              var executable=org.integratedmodelling.klab.runtime.language.SourceModelMapper.load(model,namespace,scope);
              var hash=ModelValidationGate.publicationHash(executable,response);
              if(hash!=null)hashes.put(model.getUrn(),hash);
            } catch (RuntimeException unavailable) {
              // Unmaterialized/generic models retain ordinary executable admission at resolution.
            }
          }
        }
        var latest=ModelValidationGate.currentSemanticSnapshot(scope);
        var admission=semantic!=null && semantic.revision()==knowledgeRevision && semantic.equals(latest)
            ? org.integratedmodelling.klab.utilities.Utils.Json.asString(new ModelValidationGate.NamespaceAdmission(
                semantic.serviceId(),org.integratedmodelling.klab.utilities.Utils.Json.asString(response),hashes,response.getSemanticContentFingerprint())) : null;
        catalog.replaceNamespace(namespace, scope);
        attempts.put(urn, new Attempt(fingerprint, false, now, List.of(),admission));
      } catch (RuntimeException rejected) {
        // Publication failure is isolated only if withdrawal itself succeeds. A storage failure
        // that cannot hide the previous generation must propagate to the global readiness gate.
        catalog.withholdNamespace(urn, scope);
        boolean retryable = !(rejected instanceof ModelValidationGate.AdmissionFailure failure)
            || failure.retryable();
        List<Notification> diagnostics = rejected instanceof ModelValidationGate.AdmissionFailure failure
            ? failure.diagnostics() : List.of(Notification.error(
                "Model namespace " + urn + " is withheld: " + rejected.getMessage()));
        attempts.put(urn, new Attempt(fingerprint, retryable, now, diagnostics,null));
      }
    }
    for (String removed : new ArrayList<>(attempts.keySet())) {
      if (!present.contains(removed)) {
        catalog.withholdNamespace(removed, scope);
        attempts.remove(removed);
      }
    }
  }

  /** Never attach a publication claim to the workspace AST or accept an authored claim. */
  KimNamespace forTransport(KimNamespace namespace) {
    var copy=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
        org.integratedmodelling.klab.utilities.Utils.Json.asString(namespace),KimNamespace.class);
    copy.getMetadata().remove(org.integratedmodelling.klab.api.services.ResourcesService.NAMESPACE_ADMISSION_METADATA);
    var attempt=attempts.get(namespace.getUrn());
    if (attempt!=null && attempt.admission()!=null && !namespace.isInactive()
        && !org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(namespace.getNotifications())
        && !Boolean.TRUE.equals(namespace.getMetadata().get(
            org.integratedmodelling.klab.api.services.ResourcesService.VALIDATION_DEFERRED_METADATA))
        && attempt.fingerprint().timestamp()==namespace.getLastUpdateTimestamp()
        && Objects.equals(attempt.fingerprint().sourceHash(),SemanticValidationRequest.sourceHash(namespace.getSourceCode())))
      copy.getMetadata().put(org.integratedmodelling.klab.api.services.ResourcesService.NAMESPACE_ADMISSION_METADATA,
          attempt.admission());
    return copy;
  }

  Map<String, List<Notification>> diagnostics() {
    var result = new LinkedHashMap<String, List<Notification>>();
    attempts.forEach((urn, attempt) -> {
      if (!attempt.diagnostics().isEmpty()) result.put(urn, attempt.diagnostics());
    });
    return Collections.unmodifiableMap(result);
  }
}
