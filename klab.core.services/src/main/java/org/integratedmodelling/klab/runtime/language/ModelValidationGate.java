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

  public static final String PRODUCER_ADMISSIONS = "im:model-admissions";

  /** Immutable proof of the exact source and executable candidate admitted before matching. */
  public record AdmissionReceipt(String modelUrn, String namespace, String sourceVersion,
      String sourceHash, String executableHash, String reasonerId, long knowledgeRevision,
      java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency> pendingMemberQualities)
      implements java.io.Serializable {
    public AdmissionReceipt { pendingMemberQualities=pendingMemberQualities==null?java.util.List.of():java.util.List.copyOf(pendingMemberQualities); }
    public AdmissionReceipt(String modelUrn,String namespace,String sourceVersion,String sourceHash,
        String executableHash,String reasonerId,long knowledgeRevision) {
      this(modelUrn,namespace,sourceVersion,sourceHash,executableHash,reasonerId,knowledgeRevision,java.util.List.of());
    }
    public boolean sourceBacked() { return namespace != null && !namespace.isBlank(); }
  }

  /** Source-only preparation witness. This is deliberately not an execution admission. */
  public static final class PreparationReceipt {
    private final AdmissionReceipt snapshot;
    private PreparationReceipt(AdmissionReceipt snapshot) { this.snapshot = snapshot; }
    public String modelUrn() { return snapshot.modelUrn(); }
    public String namespace() { return snapshot.namespace(); }
    public String sourceHash() { return snapshot.sourceHash(); }
    public String sourceVersion() { return snapshot.sourceVersion(); }
    public String executableHash() { return snapshot.executableHash(); }
    public String reasonerId() { return snapshot.reasonerId(); }
    public long knowledgeRevision() { return snapshot.knowledgeRevision(); }
  }

  /** Pure source/native proof before controlled contextualizer prerequisite preparation. */
  public static PreparationReceipt validatedSourcePreparation(Model model, Scope scope, Observable requested) {
    if (model == null || model.getNamespace() == null || model.getNamespace().isBlank()) return null;
    var receipt = validatedAdmission(model, scope, requested, true, false);
    return receipt != null && matches(receipt, model, scope) ? new PreparationReceipt(receipt) : null;
  }

  public static boolean matches(PreparationReceipt receipt, Model model, Scope scope) {
    return receipt != null && matches(receipt.snapshot, model, scope);
  }

  /** Reject source, executable, semantic-generation or pending-proof drift during preparation. */
  public static boolean sameSnapshot(PreparationReceipt preparation, AdmissionReceipt executable) {
    return preparation != null && executable != null && preparation.snapshot.equals(executable);
  }

  private static com.fasterxml.jackson.databind.JsonNode canonical(com.fasterxml.jackson.databind.JsonNode value) {
    if (value.isObject()) {
      var result = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
      var names = new java.util.TreeSet<String>();value.fieldNames().forEachRemaining(names::add);
      for (var name : names) result.set(name, canonical(value.get(name)));
      return result;
    }
    if (value.isArray()) {
      var result = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
      value.forEach(item -> result.add(canonical(item)));return result;
    }
    return value;
  }

  private static String digest(Object value) {
    try {
      var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
      return SemanticValidationRequest.sourceHash(canonical(mapper.readTree(
          org.integratedmodelling.klab.utilities.Utils.Json.asString(value))).toString());
    } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot fingerprint admission source",failure); }
  }

  private static Object observableContent(Observable value) {
    var data=new java.util.LinkedHashMap<String,Object>();
    data.put("urn",value.getUrn());data.put("semantics",value.asConcept()==null?null:value.asConcept().getUrn());
    data.put("observer",value.getObserverSemantics()==null?null:value.getObserverSemantics().getUrn());
    // Portable mediator beans include definition/context/distribution, never Object.toString identity.
    data.put("contextualization",value.getContextualization());data.put("unit",value.getUnit());
    data.put("currency",value.getCurrency());data.put("range",value.getRange());
    data.put("statedName",value.getStatedName());data.put("optional",value.isOptional());
    data.put("generic",value.isGeneric());data.put("abstract",value.isAbstract());
    data.put("directives",value.getResolutionDirectives()==null?null:value.getResolutionDirectives().stream().map(Enum::name).sorted().toList());
    data.put("annotations",value.getAnnotations());data.put("value",value.getValue());data.put("defaultValue",value.getDefaultValue());
    return data;
  }

  /** Stable executable projection: meaning, ports, geometry, computation and authored annotations.
   * It deliberately excludes service routing, logs, caches and prioritizer diagnostic metadata. */
  public static String executableHash(Model model) {
    var data=new java.util.LinkedHashMap<String,Object>();
    data.put("urn",model.getUrn());data.put("namespace",model.getNamespace());data.put("project",model.getProjectName());
    data.put("type",model.getType());data.put("contextualization",model.getDescriptionType());
    data.put("observables",model.getObservables().stream().map(ModelValidationGate::observableContent).toList());
    data.put("dependencies",model.getDependencies().stream().map(ModelValidationGate::observableContent).toList());
    data.put("computation",model.getComputation());data.put("annotations",model.getAnnotations());
    data.put("coverage",model.getCoverage()==null?null:org.integratedmodelling.klab.api.geometry.Geometry.forTransport(model.getCoverage()).encode());
    return digest(data);
  }

  /** Check unchanged saved source and executable content without performing a second validation. */
  public static boolean matches(AdmissionReceipt receipt, Model model, Scope scope) {
    try {
      if (receipt==null || model==null || !java.util.Objects.equals(receipt.modelUrn(),model.getUrn())
          || !java.util.Objects.equals(receipt.namespace(),model.getNamespace())
          || !java.util.Objects.equals(receipt.executableHash(),executableHash(model))) return false;
      var reasoner=scope.getService(Reasoner.class);var cap=reasoner==null?null:reasoner.capabilities(scope);
      if (!(cap instanceof Reasoner.Capabilities semantic) || receipt.knowledgeRevision()<0
          || !java.util.Objects.equals(receipt.reasonerId(),semantic.getServiceId())
          || receipt.reasonerId()==null || receipt.knowledgeRevision()!=semantic.getKnowledgeRevision()) return false;
      if (!receipt.sourceBacked()) return receipt.sourceHash()==null && receipt.sourceVersion()==null;
      var saved=source(model.getUrn(),model.getNamespace(),scope.getService(ResourcesService.class),scope);
      return saved!=null && java.util.Objects.equals(receipt.sourceVersion(),Long.toString(saved.getLastUpdateTimestamp()))
          && java.util.Objects.equals(receipt.sourceHash(),digest(saved));
    } catch (RuntimeException unavailable) { return false; }
  }

  /** The returned receipt belongs to this one validation, never to a later capabilities sample. */
  public static AdmissionReceipt validatedPreparableAdmission(Model model, Scope scope, Observable requested) {
    var result=validatedAdmission(model,scope,requested,true);
    return result!=null && matches(result,model,scope) ? result : null;
  }


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
    return requireReceipt(namespace,scope,expectedRevision,executable,preparable,java.util.List.of()).getKnowledgeRevision();
  }

  private static org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse requireReceipt(
      KimNamespace namespace,Scope scope,long expectedRevision,boolean executable,boolean preparable,
      java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency> memberDependencies) {
    if (namespace == null || scope == null) throw new KlabValidationException("Model source validation is unavailable");
    var reasoner = scope.getService(Reasoner.class);
    if (reasoner == null) throw new KlabValidationException("Model source validation requires a reasoner");
    var request = SemanticValidationRequest.of(namespace,
        Long.toString(namespace.getLastUpdateTimestamp()));
    request.setKnowledgeRevision(expectedRevision);
    request.setExecutableModels(executable);
    if (executable) request.setExecutionContext(executionContext(scope));
    request.setClassifierPreparableDependencies(preparable);
    request.setPreparableMemberQualityDependencies(preparable?memberDependencies:java.util.List.of());
    var response = reasoner.validateDocument(request, scope);
    boolean receiptMatches = response != null && response.getKnowledgeRevision() >= 0
        && response.matches(request, response.getKnowledgeRevision())
        && (!executable || java.util.Objects.equals(request.getExecutionContext(), executionContext(scope))
            && response.getPendingModelContexts().isEmpty() && response.getConditionalModelContexts().isEmpty()
            && (preparable ? memberDependencies.containsAll(response.getPendingMemberQualityDependencies())
                : response.getPendingMemberQualityDependencies().isEmpty()))
        && (expectedRevision < 0 || response.getKnowledgeRevision() == expectedRevision);
    if (response == null || !response.valid() || !receiptMatches)
      throw new AdmissionFailure(namespace.getUrn(), response, !receiptMatches);
    return response;
  }

  private static String executionContext(Scope scope) {
    if (scope instanceof org.integratedmodelling.klab.api.scope.ContextScope contextual
        && contextual.getContextObservation() != null
        && contextual.getContextObservation().getObservable() != null
        && contextual.getContextObservation().getObservable().getSemantics() != null)
      return contextual.getContextObservation().getObservable().getSemantics().getUrn();
    return null;
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
    return validatedCatalogRevision(name, namespace, resources, scope, requested, true);
  }

  /** Catalog source/preparable proof only: no installed inspection, ranking or execution grant. */
  public static long validatedPreparableCatalogRevision(String name, String namespace,
      ResourcesService resources, Scope scope, Observable requested) {
    return validatedCatalogRevision(name, namespace, resources, scope, requested, false);
  }

  private static long validatedCatalogRevision(String name, String namespace, ResourcesService resources,
      Scope scope, Observable requested, boolean inspectInstalled) {
    try {
      var source = source(name, namespace, resources, scope);
      if (source == null || !inspectInstalled && (source.getSourceCode() == null || source.getSourceCode().isBlank())) return -1;
      long timestamp = source.getLastUpdateTimestamp();
      String sourceCode = source.getSourceCode();
      var sourceReceipt = requireReceipt(source,scope,-1,false,false,java.util.List.of());
      long revision = sourceReceipt.getKnowledgeRevision();
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
        var memberDependencies=sourceReceipt.getPendingMemberQualityDependencies().stream()
            .filter(p->name.equals(p.modelUrn())).toList();
        if(!memberSourceConsumersProved(model,scope,memberDependencies,inspectInstalled))return -1;
        var boundNamespace=new KimNamespaceImpl();boundNamespace.setUrn(bound.getNamespace());boundNamespace.getStatements().add(bound);
        long boundRevision = requireReceipt(boundNamespace,scope,revision,true,!memberDependencies.isEmpty(),memberDependencies).getKnowledgeRevision();
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
    var receipt=validatedAdmission(model,scope,requested,preparable);
    return receipt==null ? -1 : receipt.knowledgeRevision();
  }

  /** Return only the atomic leaf of an explicitly authored `of`, allowing transparent parser wrappers. */
  public static KimConcept explicitAtomicInherent(KimObservable source) {
    var node=source==null || source.getSemantics()==null?null:source.getSemantics().getInherent();
    var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<KimConcept,Boolean>());
    while(node!=null && seen.add(node)) {
      if(node.isCollective() || node.isNegated() || node.isPattern() || node.getSemanticModifier()!=null
          || !node.getOperands().isEmpty() || !node.getTraits().isEmpty() || !node.getRoles().isEmpty()
          || !node.getModifiers().isEmpty() || !node.getAbout().isEmpty() || !node.getValueOperators().isEmpty()
          || node.getComparisonConcept()!=null || node.getParent()!=null
          || node.getExpressionType()!=null && node.getExpressionType()!=KimConcept.Expression.SINGLETON)return null;
      if(node.getName()!=null)return node.getObservable()==null?node:null;
      node=node.getObservable();
    }
    return null;
  }
  private static boolean requiredMemberPortsCovered(java.util.List<org.integratedmodelling.klab.api.lang.Contextualizable> computation,
      java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency> pending,
      org.integratedmodelling.klab.api.services.RuntimeService runtime,Scope lexical) {
    var required=new java.util.HashSet<String>();var proved=new java.util.HashSet<String>();
    for(var obligation:pending)if(obligation.port()==null || obligation.port().isBlank() || !proved.add(obligation.port()))return false;
    for(var code:computation)if(code.getServiceCall()!=null) {
      var inspection=runtime.inspectMemberQualityInputs(code.getServiceCall(),lexical);
      if(inspection==null || !inspection.available() || inspection.ports()==null)return false;
      for(String port:inspection.ports())if(port==null || port.isBlank())return false;
      required.addAll(inspection.ports());
    }
    return required.equals(proved);
  }
  private static Scope memberInspectionScope(Scope scope,String namespace,String project) {
    if(scope instanceof org.integratedmodelling.klab.api.scope.ContextScope context)
      return context.withResolutionConstraints(
          org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.of(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.ResolutionNamespace,namespace),
          org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.of(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.ResolutionProject,project));
    return scope;
  }
  /** Catalog preflight proves the whole installed input shape; actual Model admission remains mandatory. */
  private static boolean memberSourceConsumersProved(KimModel model,Scope scope,
      java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency> pending,
      boolean inspectInstalled) {
    if(model.getContextualization().stream().noneMatch(c->c.getServiceCall()!=null))return pending.isEmpty();
    if (inspectInstalled) {
      var runtime=scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
      var lexical=memberInspectionScope(scope,model.getNamespace(),model.getProjectName());
      if(runtime==null || lexical==null || !requiredMemberPortsCovered(model.getContextualization(),pending,runtime,lexical))return false;
    }
    if(pending.isEmpty())return true;
    if(model.getContextualization().size()!=1 || model.getContextualization().getFirst().getServiceCall()==null)return false;
    for(var obligation:pending) {
      if(obligation.dependencyIndex()<0 || obligation.dependencyIndex()>=model.getDependencies().size())return false;
      if(model.getDependencies().stream().filter(o->java.util.Objects.equals(obligation.port(),o.getFormalName())).count()!=1)return false;
      var occurrence=model.getDependencies().get(obligation.dependencyIndex());
      if(explicitAtomicInherent(occurrence)==null
          || !java.util.Objects.equals(obligation.modelUrn(),model.getUrn())
          || !java.util.Objects.equals(obligation.sourceObservable(),occurrence.getUrn())
          || !java.util.Objects.equals(obligation.port(),occurrence.getFormalName()))return false;
    }
    return true;
  }
  private static boolean memberConsumersProved(Model model,Scope scope,
      java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency> pending,
      boolean inspectInstalled) {
    if(model.getComputation().stream().noneMatch(c->c.getServiceCall()!=null))return pending.isEmpty();
    if (inspectInstalled) {
      var runtime=scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
      var lexical=memberInspectionScope(scope,model.getNamespace(),model.getProjectName());
      if(runtime==null || lexical==null || !requiredMemberPortsCovered(model.getComputation(),pending,runtime,lexical))return false;
    }
    if(pending.isEmpty())return true;
    if(model.getComputation().size()!=1 || model.getComputation().getFirst().getServiceCall()==null)return false;
    for(var obligation:pending) {
      if(obligation.dependencyIndex()<0 || obligation.dependencyIndex()>=model.getDependencies().size())return false;
      if(model.getDependencies().stream().filter(o->java.util.Objects.equals(obligation.port(),o.getStatedName())).count()!=1)return false;
      var dependency=model.getDependencies().get(obligation.dependencyIndex());
      if(!java.util.Objects.equals(obligation.port(),dependency.getStatedName())
          || !java.util.Objects.equals(obligation.quality(),dependency.getUrn()))return false;
    }
    return true;
  }
  private static AdmissionReceipt validatedAdmission(Model model, Scope scope, Observable requested, boolean preparable) {
    return validatedAdmission(model, scope, requested, preparable, true);
  }

  private static AdmissionReceipt validatedAdmission(Model model, Scope scope, Observable requested,
      boolean preparable, boolean inspectInstalled) {
    try {
      var frozenHash=executableHash(model);
      var reasoner=scope.getService(Reasoner.class);
      var initial=reasoner==null?null:reasoner.capabilities(scope);
      String reasonerId=initial instanceof Reasoner.Capabilities semantic?semantic.getServiceId():null;
      long expected=initial instanceof Reasoner.Capabilities semantic?semantic.getKnowledgeRevision():-1;
      var resources = scope.getService(ResourcesService.class);
      long revision = -1;
      KimNamespace saved = null;
      long timestamp = 0;
      String sourceCode = null;
      String namespaceHash = null;
      java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency> pendingMembers=java.util.List.of();
      if (model.getNamespace() != null && !model.getNamespace().isBlank()) {
        saved = source(model.getUrn(), model.getNamespace(), resources, scope);
        if (saved == null || saved.getSourceCode()==null || saved.getSourceCode().isBlank()) return null;
        timestamp = saved.getLastUpdateTimestamp(); sourceCode = saved.getSourceCode();
        namespaceHash=digest(saved);
        var sourceReceipt=requireReceipt(saved,scope,expected,false,false,java.util.List.of());
        revision=sourceReceipt.getKnowledgeRevision();
        pendingMembers=sourceReceipt.getPendingMemberQualityDependencies().stream().filter(p->model.getUrn().equals(p.modelUrn())).toList();
        if(!preparable && !pendingMembers.isEmpty())return null;
      }
      if (resources == null || !memberConsumersProved(model,scope,pendingMembers,inspectInstalled)) return null;
      var syntax = new KimModelImpl();
      syntax.setUrn(model.getUrn());
      for (var output : model.getObservables()) {
        var statedName = output.getStatedName();
        if (syntax.getObservables().isEmpty() && requested != null
            && bindPrimary(resources.declareObservable(output.getUrn()), requested, scope)) output = requested;
        var parsed = detached(resources.declareObservable(output.getUrn()));
        if (parsed == null) return null;
        if (parsed instanceof KimObservableImpl implementation && statedName != null)
          implementation.setFormalName(statedName);
        syntax.getObservables().add(parsed);
      }
      for (var dependency : model.getDependencies()) {
        var parsed = detached(resources.declareObservable(dependency.getUrn()));
        if (parsed == null) return null;
        if (parsed instanceof KimObservableImpl implementation && dependency.getStatedName() != null)
          implementation.setFormalName(dependency.getStatedName());
        syntax.getDependencies().add(parsed);
      }
      syntax.getContextualization().addAll(model.getComputation());
      if (syntax.getObservables().isEmpty() || !executionBound(syntax.getObservables().getFirst(), scope)) return null;
      var executableNamespace=new KimNamespaceImpl();executableNamespace.setUrn(model.getNamespace()==null?"model.validation":model.getNamespace());
      executableNamespace.getStatements().add(syntax);
      var executableReceipt=requireReceipt(executableNamespace,scope,revision,true,preparable,pendingMembers);
      long boundRevision=executableReceipt.getKnowledgeRevision();
      if (!java.util.Objects.equals(frozenHash,executableHash(model))
          || saved!=null && (!unchanged(saved,timestamp,sourceCode) || !java.util.Objects.equals(namespaceHash,digest(saved)))) return null;
      var latest=reasoner==null?null:reasoner.capabilities(scope);
      if (initial instanceof Reasoner.Capabilities && (!(latest instanceof Reasoner.Capabilities semantic)
          || !java.util.Objects.equals(reasonerId,semantic.getServiceId()) || expected!=semantic.getKnowledgeRevision()
          || boundRevision!=expected)) return null;
      return new AdmissionReceipt(model.getUrn(),model.getNamespace(),saved==null?null:Long.toString(timestamp),
          namespaceHash,frozenHash,reasonerId,boundRevision,pendingMembers);
    } catch (RuntimeException unavailable) { return null; }
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
