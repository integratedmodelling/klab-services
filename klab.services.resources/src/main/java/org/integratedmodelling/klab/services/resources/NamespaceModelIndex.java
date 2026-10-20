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
  private record Fingerprint(long timestamp, String sourceHash, long knowledgeRevision) {}
  private record Attempt(Fingerprint fingerprint, boolean retryable, long attemptedAt,
      List<Notification> diagnostics) {}
  private final Map<String, Attempt> attempts = new LinkedHashMap<>();
  private final long retryDelay;

  NamespaceModelIndex() { this(Duration.ofSeconds(5).toNanos()); }
  NamespaceModelIndex(long retryDelay) { this.retryDelay = retryDelay; }

  void reconcile(Collection<KimNamespace> namespaces, ModelCatalog catalog, Scope scope,
      long knowledgeRevision, long now) {
    var present = new HashSet<String>();
    for (var namespace : namespaces) {
      String urn = namespace.getUrn(); present.add(urn);
      var fingerprint = new Fingerprint(namespace.getLastUpdateTimestamp(),
          SemanticValidationRequest.sourceHash(namespace.getSourceCode()), knowledgeRevision);
      var previous = attempts.get(urn);
      if (previous != null && previous.fingerprint().equals(fingerprint)
          && (!previous.retryable() || now - previous.attemptedAt() < retryDelay)) continue;
      try {
        ModelValidationGate.require(namespace, scope, knowledgeRevision);
        catalog.replaceNamespace(namespace, scope);
        attempts.put(urn, new Attempt(fingerprint, false, now, List.of()));
      } catch (RuntimeException rejected) {
        // Publication failure is isolated only if withdrawal itself succeeds. A storage failure
        // that cannot hide the previous generation must propagate to the global readiness gate.
        catalog.withholdNamespace(urn, scope);
        boolean retryable = !(rejected instanceof ModelValidationGate.AdmissionFailure failure)
            || failure.retryable();
        List<Notification> diagnostics = rejected instanceof ModelValidationGate.AdmissionFailure failure
            ? failure.diagnostics() : List.of(Notification.error(
                "Model namespace " + urn + " is withheld: " + rejected.getMessage()));
        attempts.put(urn, new Attempt(fingerprint, retryable, now, diagnostics));
      }
    }
    for (String removed : new ArrayList<>(attempts.keySet())) {
      if (!present.contains(removed)) {
        catalog.withholdNamespace(removed, scope);
        attempts.remove(removed);
      }
    }
  }

  Map<String, List<Notification>> diagnostics() {
    var result = new LinkedHashMap<String, List<Notification>>();
    attempts.forEach((urn, attempt) -> {
      if (!attempt.diagnostics().isEmpty()) result.put(urn, attempt.diagnostics());
    });
    return Collections.unmodifiableMap(result);
  }
}
