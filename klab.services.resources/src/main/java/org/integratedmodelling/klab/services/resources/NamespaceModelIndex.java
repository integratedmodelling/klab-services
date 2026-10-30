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
  private record ImportedNamespace(String namespace,List<String> symbols) {}
  private record Configuration(String project,org.integratedmodelling.klab.api.lang.kim.KlabStatement.Scope scope,
      boolean scenario,boolean worldviewBound,String coverage,List<ImportedNamespace> imports,
      List<String> disjoint,Map<String,Integer> criteria) {}
  private record Fingerprint(long timestamp, String sourceHash, long knowledgeRevision, String serviceId, String semanticFingerprint, String semanticSessionId, boolean eligible,Configuration configuration) {}
  private record Attempt(Fingerprint fingerprint, boolean retryable, long attemptedAt,
      List<Notification> diagnostics, String admission) {}
  private final Map<String, Attempt> attempts = new java.util.concurrent.ConcurrentHashMap<>();
  private final long retryDelay;

  NamespaceModelIndex() { this(Duration.ofSeconds(5).toNanos()); }
  NamespaceModelIndex(long retryDelay) { this.retryDelay = retryDelay; }

  private static final class ReconciliationChanged extends IllegalStateException {
    ReconciliationChanged() { super("Source or semantic generation changed during model reconciliation"); }
  }

  private static boolean eligible(KimNamespace namespace) {
    return !namespace.isInactive()
        && !org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(namespace.getNotifications())
        && !Boolean.TRUE.equals(namespace.getMetadata().get(
            org.integratedmodelling.klab.api.services.ResourcesService.VALIDATION_DEFERRED_METADATA));
  }

  private static Fingerprint fingerprint(KimNamespace namespace,ModelValidationGate.SemanticSnapshot semantic) {
    return new Fingerprint(namespace.getLastUpdateTimestamp(),SemanticValidationRequest.sourceHash(namespace.getSourceCode()),
        semantic.revision(),semantic.serviceId(),semantic.semanticFingerprint(),semantic.semanticSessionId(),eligible(namespace),configuration(namespace));
  }

  private static Configuration configuration(KimNamespace namespace) {
    var imports=namespace.getImports().entrySet().stream().map(entry->new ImportedNamespace(entry.getKey(),
        entry.getValue()==null?null:List.copyOf(entry.getValue()))).toList();
    return new Configuration(namespace.getProjectName(),namespace.getScope(),namespace.isScenario(),namespace.isWorldviewBound(),
        namespace.getCoverage()==null?null:org.integratedmodelling.klab.api.geometry.Geometry.forTransport(namespace.getCoverage()).encode(),
        imports,List.copyOf(namespace.getDisjointNamespaces()),Map.copyOf(namespace.getResolutionCriteria()));
  }

  private static void requireEpoch(ModelValidationGate.SemanticSnapshot expected,Scope scope) {
    if(!expected.equals(ModelValidationGate.currentSemanticSnapshot(scope)))throw new ReconciliationChanged();
  }

  synchronized void reconcile(Collection<KimNamespace> namespaces, ModelCatalog catalog, Scope scope,
      long knowledgeRevision, long now) {
    reconcile(()->namespaces,catalog,scope,knowledgeRevision,now);
  }

  synchronized void reconcile(java.util.function.Supplier<? extends Collection<KimNamespace>> namespaceSource,
      ModelCatalog catalog,Scope scope,long knowledgeRevision,long now) {
    var sources=List.copyOf(namespaceSource.get());
    var affected=new HashSet<>(attempts.keySet());sources.forEach(source->affected.add(source.getUrn()));
    try {
      var semantic=ModelValidationGate.currentSemanticSnapshot(scope);
      if(semantic==null || semantic.revision()!=knowledgeRevision)throw new ReconciliationChanged();
      var present=new HashSet<String>();
      var fingerprints=new LinkedHashMap<String,Fingerprint>();
      var staged=new HashMap<>(attempts);
      for(var namespace:sources) {
        String urn=namespace.getUrn();
        if(!present.add(urn))throw new ReconciliationChanged();
        var fingerprint=fingerprint(namespace,semantic);fingerprints.put(urn,fingerprint);
        var previous=attempts.get(urn);
        if(previous!=null && previous.fingerprint().equals(fingerprint)
            && (!previous.retryable() || now-previous.attemptedAt()<retryDelay))continue;
        try(var validation=ModelValidationGate.openPublicationSession(namespace,scope)) {
          if(!fingerprint.eligible())throw new IllegalStateException("Model namespace is inactive, invalid or deferred: "+urn);
          var response=ModelValidationGate.sourceAdmission(namespace,scope,knowledgeRevision);
          var sourceContract=response.isSourceSemanticsComplete()?ModelValidationGate.SOURCE_ADMISSION_CONTRACT:null;
          var hashes=new LinkedHashMap<String,String>();
          for(var statement:namespace.getStatements()) {
            if(statement instanceof org.integratedmodelling.klab.api.lang.kim.KimModel model) {
              try {
                var executable=org.integratedmodelling.klab.runtime.language.SourceModelMapper.load(model,namespace,scope);
                var hash=ModelValidationGate.publicationHash(executable,response);
                if(hash!=null)hashes.put(model.getUrn(),hash);
              }catch(RuntimeException unavailable) {
                // Generic or unmaterialized models require their own executable admission.
              }
            }
          }
          var admitted=ModelValidationGate.semanticSnapshot(scope);
          if(!semantic.equals(admitted) || !fingerprint.equals(fingerprint(namespace,semantic)))throw new ReconciliationChanged();
          requireEpoch(semantic,scope);
          var admission=org.integratedmodelling.klab.utilities.Utils.Json.asString(new ModelValidationGate.NamespaceAdmission(
              semantic.serviceId(),org.integratedmodelling.klab.utilities.Utils.Json.asString(response),hashes,
              response.getSemanticContentFingerprint(),sourceContract));
          catalog.replaceNamespace(namespace,scope);
          staged.put(urn,new Attempt(fingerprint,false,now,List.of(),admission));
        }catch(ReconciliationChanged changed) { throw changed;
        }catch(RuntimeException rejected) {
          catalog.withholdNamespace(urn,scope);
          boolean retryable=!(rejected instanceof ModelValidationGate.AdmissionFailure failure)||failure.retryable();
          List<Notification> diagnostics=rejected instanceof ModelValidationGate.AdmissionFailure failure
              ?failure.diagnostics():List.of(Notification.error("Model namespace "+urn+" is withheld: "+rejected.getMessage()));
          staged.put(urn,new Attempt(fingerprint,retryable,now,diagnostics,null));
        }
      }
      for(String removed:new ArrayList<>(attempts.keySet()))if(!present.contains(removed)) {
        catalog.withholdNamespace(removed,scope);staged.remove(removed);
      }
      // No per-namespace capability traffic on the unchanged path. Concurrent generation/source
      // drift withdraws the staged catalog before this request or another reconciler can use it.
      var latestSources=List.copyOf(namespaceSource.get());
      if(latestSources.size()!=sources.size())throw new ReconciliationChanged();
      for(int i=0;i<sources.size();i++) {
        var source=latestSources.get(i);
        if(source!=sources.get(i) || !Objects.equals(source.getUrn(),sources.get(i).getUrn())
            || !Objects.equals(fingerprints.get(source.getUrn()),fingerprint(source,semantic)))throw new ReconciliationChanged();
      }
      requireEpoch(semantic,scope);
      attempts.clear();attempts.putAll(staged);
    }catch(RuntimeException changed) {
      // A namespace manifest may have been replaced just as the generation changed. Withdrawal
      // failure must propagate: the provider's readiness gate must not continue model queries.
      attempts.clear();
      RuntimeException withdrawalFailure=null;
      for(String urn:affected)try { catalog.withholdNamespace(urn,scope); }
      catch(RuntimeException failure) {
        if(withdrawalFailure==null)withdrawalFailure=failure;else if(failure!=withdrawalFailure)withdrawalFailure.addSuppressed(failure);
      }
      if(withdrawalFailure!=null) { if(withdrawalFailure!=changed)withdrawalFailure.addSuppressed(changed);throw withdrawalFailure; }
      throw changed;
    }
  }
  /** Never attach a publication claim to the workspace AST or accept an authored claim. */
  synchronized KimNamespace forTransport(KimNamespace namespace) {
    var copy=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
        org.integratedmodelling.klab.utilities.Utils.Json.asString(namespace),KimNamespace.class);
    copy.getMetadata().remove(org.integratedmodelling.klab.api.services.ResourcesService.NAMESPACE_ADMISSION_METADATA);
    var attempt=attempts.get(namespace.getUrn());
    if (attempt!=null && attempt.admission()!=null && !namespace.isInactive()
        && !org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(namespace.getNotifications())
        && !Boolean.TRUE.equals(namespace.getMetadata().get(
            org.integratedmodelling.klab.api.services.ResourcesService.VALIDATION_DEFERRED_METADATA))
        && attempt.fingerprint().timestamp()==namespace.getLastUpdateTimestamp()
        && Objects.equals(attempt.fingerprint().configuration(),configuration(namespace))
        && Objects.equals(attempt.fingerprint().sourceHash(),SemanticValidationRequest.sourceHash(namespace.getSourceCode())))
      copy.getMetadata().put(org.integratedmodelling.klab.api.services.ResourcesService.NAMESPACE_ADMISSION_METADATA,
          attempt.admission());
    return copy;
  }

  synchronized Map<String, List<Notification>> diagnostics() {
    var result = new LinkedHashMap<String, List<Notification>>();
    attempts.forEach((urn, attempt) -> {
      if (!attempt.diagnostics().isEmpty()) result.put(urn, attempt.diagnostics());
    });
    return Collections.unmodifiableMap(result);
  }
}
