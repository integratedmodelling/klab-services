package org.integratedmodelling.klab.runtime.language;

import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;

/** Source admission before model publication or executable matching. No distance is computed here. */
public final class ModelValidationGate {
  private ModelValidationGate() {}

  /** Transport/readiness failures are retryable; completed source errors await a changed snapshot. */
  public static final class AdmissionFailure extends KlabValidationException {
    private final org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse response;
    private final boolean invalidReceipt;
    AdmissionFailure(String namespace,
        org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse response,
        boolean invalidReceipt) {
      super("Model source validation failed for " + namespace + (response == null ? ": unavailable"
          : ": " + response.getStatus() + " " + response.getNotifications() + " " + response.getReason()));
      this.response = response;
      this.invalidReceipt = invalidReceipt;
    }
    public boolean retryable() {
      return invalidReceipt || response == null || response.getStatus()
          != org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.COMPLETE
          && response.getStatus()
          != org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.SYNTAX_ERRORS
          || response.valid();
    }
    public java.util.List<org.integratedmodelling.klab.api.services.runtime.Notification> diagnostics() {
      return response == null || response.getNotifications().isEmpty()
          ? java.util.List.of(org.integratedmodelling.klab.api.services.runtime.Notification.error(getMessage()))
          : java.util.List.copyOf(response.getNotifications());
    }
  }

  public static long require(KimNamespace namespace, Scope scope) {
    return require(namespace, scope, -1);
  }

  public static long require(KimNamespace namespace, Scope scope, long expectedRevision) {
    return require(namespace, scope, expectedRevision, false);
  }

  private static long require(KimNamespace namespace, Scope scope, long expectedRevision, boolean executable) {
    return require(namespace,scope,expectedRevision,executable,false);
  }

  private static long require(KimNamespace namespace, Scope scope, long expectedRevision, boolean executable, boolean preparable) {
    if (namespace == null || scope == null) throw new KlabValidationException("Model source validation is unavailable");
    var reasoner = scope.getService(Reasoner.class);
    if (reasoner == null) throw new KlabValidationException("Model source validation requires a reasoner");
    var request = SemanticValidationRequest.of(namespace,
        Long.toString(namespace.getLastUpdateTimestamp()));
    request.setKnowledgeRevision(expectedRevision);
    request.setExecutableModels(executable);
    request.setClassifierPreparableDependencies(preparable);
    var response = reasoner.validateDocument(request, scope);
    boolean receiptMatches = response != null && response.getKnowledgeRevision() >= 0
        && response.matches(request, response.getKnowledgeRevision())
        && (expectedRevision < 0 || response.getKnowledgeRevision() == expectedRevision);
    if (response == null || !response.valid() || !receiptMatches)
      throw new AdmissionFailure(namespace.getUrn(), response, !receiptMatches);
    return response.getKnowledgeRevision();
  }

  public static long require(KimModel model, Scope scope) {
    return require(model, scope, -1);
  }

  private static long require(KimModel model, Scope scope, long expectedRevision) {
    return require(model, scope, expectedRevision, false);
  }

  private static long require(KimModel model, Scope scope, long expectedRevision, boolean executable) {
    return require(model,scope,expectedRevision,executable,false);
  }

  private static long require(KimModel model, Scope scope, long expectedRevision, boolean executable, boolean preparable) {
    var namespace = new KimNamespaceImpl();
    namespace.setUrn(model.getNamespace() == null ? "model.validation" : model.getNamespace());
    namespace.getStatements().add(model);
    return require(namespace, scope, expectedRevision, executable, preparable);
  }

  public static boolean admits(String name, String namespace, ResourcesService resources, Scope scope) {
    return admits(name, namespace, resources, scope, null);
  }

  public static boolean admits(String name, String namespace, ResourcesService resources, Scope scope,
      Observable requested) {
    return validatedRevision(name, namespace, resources, scope, requested) >= 0;
  }

  public static long validatedRevision(String name, String namespace, ResourcesService resources, Scope scope,
      Observable requested) {
    try {
      var source = source(name, namespace, resources, scope);
      if (source == null) return -1;
      long timestamp = source.getLastUpdateTimestamp();
      String sourceCode = source.getSourceCode();
      long revision = require(source, scope);
      if (requested != null) {
        var model = (KimModel) source.getStatements().stream()
            .filter(statement -> statement instanceof KimModel && name.equals(statement.getUrn()))
            .findFirst().orElseThrow();
        var bound = new KimModelImpl();
        bound.setUrn(model.getUrn());
        bound.setNamespace(model.getNamespace());
        bound.getObservables().addAll(model.getObservables());
        if (bound.getObservables().isEmpty()) return -1;
        if (bindPrimary(model.getObservables().getFirst(), requested, scope)) {
          var primary = detached(resources.declareObservable(requested.getUrn()));
          if (primary == null) return -1;
          if (primary instanceof KimObservableImpl implementation)
            implementation.setFormalName(model.getObservables().getFirst().getFormalName());
          bound.getObservables().set(0, primary);
        }
        bound.getDependencies().addAll(model.getDependencies());
        bound.getContextualization().addAll(model.getContextualization());
        if (!executionBound(bound.getObservables().getFirst(), scope)) return -1;
        long boundRevision = require(bound, scope, revision, true);
        return unchanged(source, timestamp, sourceCode) ? boundRevision : -1;
      }
      return unchanged(source, timestamp, sourceCode) ? revision : -1;
    } catch (RuntimeException unavailable) { return -1; }
  }

  public static void require(String name, String namespace, ResourcesService resources, Scope scope) {
    if (!admits(name, namespace, resources, scope))
      throw new KlabValidationException("Model source is not admitted: " + name);
  }

  private static KimNamespace source(String name, String namespace, ResourcesService resources, Scope scope) {
    if (resources == null || namespace == null) return null;
    var source = resources.retrieve(namespace, KimNamespace.class,
        scope instanceof UserScope user ? user : null);
    return source != null && source.getStatements().stream()
        .anyMatch(statement -> statement instanceof KimModel && name.equals(statement.getUrn()))
        ? source : null;
  }

  /** Named models need their saved source. Only namespace-free ad-hoc models are reconstructed. */
  public static boolean admits(Model model, Scope scope) {
    return admits(model, scope, null);
  }

  public static boolean admits(Model model, Scope scope, Observable requested) {
    return validatedRevision(model, scope, requested) >= 0;
  }

  public static long validatedRevision(Model model, Scope scope, Observable requested) {
    return validatedRevision(model,scope,requested,false);
  }

  /** Conditional resolution admission; every pending classifier must subsequently produce a plan. */
  public static boolean admitsPreparable(Model model, Scope scope, Observable requested) {
    return validatedPreparableRevision(model,scope,requested)>=0;
  }

  public static long validatedPreparableRevision(Model model, Scope scope, Observable requested) {
    return validatedRevision(model,scope,requested,true);
  }

  private static long validatedRevision(Model model, Scope scope, Observable requested, boolean preparable) {
    try {
      var resources = scope.getService(ResourcesService.class);
      long revision = -1;
      KimNamespace saved = null;
      long timestamp = 0;
      String sourceCode = null;
      if (model.getNamespace() != null && !model.getNamespace().isBlank()) {
        saved = source(model.getUrn(), model.getNamespace(), resources, scope);
        if (saved == null) return -1;
        timestamp = saved.getLastUpdateTimestamp(); sourceCode = saved.getSourceCode();
        revision = require(saved, scope);
      }
      if (resources == null) return -1;
      var syntax = new KimModelImpl();
      syntax.setUrn(model.getUrn());
      for (var output : model.getObservables()) {
        var statedName = output.getStatedName();
        if (syntax.getObservables().isEmpty() && requested != null
            && bindPrimary(resources.declareObservable(output.getUrn()), requested, scope)) output = requested;
        var parsed = detached(resources.declareObservable(output.getUrn()));
        if (parsed == null) return -1;
        if (parsed instanceof KimObservableImpl implementation && statedName != null)
          implementation.setFormalName(statedName);
        syntax.getObservables().add(parsed);
      }
      for (var dependency : model.getDependencies()) {
        var parsed = detached(resources.declareObservable(dependency.getUrn()));
        if (parsed == null) return -1;
        if (parsed instanceof KimObservableImpl implementation && dependency.getStatedName() != null)
          implementation.setFormalName(dependency.getStatedName());
        syntax.getDependencies().add(parsed);
      }
      syntax.getContextualization().addAll(model.getComputation());
      if (syntax.getObservables().isEmpty() || !executionBound(syntax.getObservables().getFirst(), scope)) return -1;
      long boundRevision = require(syntax, scope, revision, true, preparable);
      return saved == null || unchanged(saved, timestamp, sourceCode) ? boundRevision : -1;
    } catch (RuntimeException unavailable) { return -1; }
  }

  private static boolean bindPrimary(KimObservable primary, Observable requested, Scope scope) {
    if (primary == null || requested == null) return false;
    var reasoner = scope.getService(Reasoner.class);
    var original = reasoner.resolveObservable(primary.getUrn());
    if (original == null) return false;
    var head = reasoner.coreObservable(original);
    var requestedHead = reasoner.coreObservable(requested);
    if (head == null || requestedHead == null || !head.getUrn().equals(requestedHead.getUrn())) return false;
    boolean predicate = original.is(SemanticType.PREDICATE);
    if (!(unboundSyntax(primary) || original.isAbstract() && !predicate || predicate)) return false;
    var bearer = reasoner.inherent(original);
    var requestedBearer = reasoner.inherent(requested);
    return bearer == null || requestedBearer != null && reasoner.is(requestedBearer, bearer);
  }

  private static boolean executionBound(KimObservable primary, Scope scope) {
    if (unboundSyntax(primary)) return false;
    if (primary.getSemantics() == null) return true; // Nonsemantic learners.
    var resolved = scope.getService(Reasoner.class).resolveObservable(primary.getUrn());
    return resolved != null && (!resolved.isAbstract() || resolved.is(SemanticType.PREDICATE))
        && !resolved.isGeneric() && resolved.getGenericComponents().isEmpty()
        && !primary.isGeneric() && !primary.getSemantics().isPattern();
  }

  private static boolean unboundSyntax(KimObservable observable) {
    return observable.isGeneric() || observable.getPattern() != null
        || unboundConcept(observable.getSemantics(), java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
  }

  /** Selectors may occur on a nested head, trait or bearer, not the aggregate expression type. */
  public static boolean hasUnboundSyntax(KimObservable observable) {
    return unboundSyntax(observable);
  }

  private static boolean unboundConcept(KimConcept concept, java.util.Set<KimConcept> visited) {
    if (concept == null || !visited.add(concept)) return false;
    if (concept.isPattern() || concept.is(SemanticType.ANY)
        || concept.is(SemanticType.ALL) || concept.is(SemanticType.NONE)) return true;
    if (unboundConcept(concept.getObservable(), visited)
        || unboundConcept(concept.getComparisonConcept(), visited)) return true;
    for (var trait : concept.getTraits()) if (unboundConcept(trait, visited)) return true;
    for (var role : concept.getRoles()) if (unboundConcept(role, visited)) return true;
    for (var operand : concept.getOperands()) if (unboundConcept(operand, visited)) return true;
    for (var target : concept.getAbout()) if (unboundConcept(target, visited)) return true;
    for (var modifier : concept.getModifiers())
      if (unboundConcept(modifier.getSecond(), visited)) return true;
    return false;
  }

  private static KimObservable detached(KimObservable observable) {
    return observable instanceof KimObservableImpl implementation
        ? new KimObservableImpl(implementation) : observable;
  }

  private static boolean unchanged(KimNamespace namespace, long timestamp, String sourceCode) {
    return timestamp == namespace.getLastUpdateTimestamp()
        && java.util.Objects.equals(sourceCode, namespace.getSourceCode());
  }
}
