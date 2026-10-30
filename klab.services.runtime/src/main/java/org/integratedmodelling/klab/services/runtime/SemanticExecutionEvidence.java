package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticEvidenceBinding;
import org.integratedmodelling.klab.api.knowledge.SemanticContentFingerprint;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/** Complete Runtime accounting precedes Reasoner locality analysis. A request signature alone
 * cannot certify the model, native producer or dynamic classification execution. */
final class SemanticExecutionEvidence {
  static final String DATA_KEY = SemanticEvidenceBinding.DATA_KEY;
  static final String DIAGNOSTIC_KEY = "diagnostic.semanticExecution";
  private static final ThreadLocal<Map<String,Object>> LAST_COLLECTOR = new ThreadLocal<>();
  private static final ThreadLocal<Map<String,Object>> LAST_VERIFICATION = new ThreadLocal<>();

  static Map<String,Object> diagnostic(Object... pairs) {
    var result = new LinkedHashMap<String,Object>();
    for (int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]);
    return result;
  }

  /** Temporary diagnostic data only; never consulted to admit an observation or proof. */
  static String failureDiagnostic(SemanticEvidenceBinding evidence, Observation nativeView,
      Collection<Observation> members, ServiceContextScope scope) {
    var collector = LAST_COLLECTOR.get();
    var activities = new ArrayList<Object>();
    var seen = Collections.newSetFromMap(new IdentityHashMap<Activity,Boolean>());
    var inputs = new ArrayList<Observation>(); inputs.add(nativeView); inputs.addAll(members);
    var missingSources = new ArrayList<Long>();
    for(var id:nativeView.getMetadata().getList(org.integratedmodelling.klab.api.data.Metadata.IM_QUERY_SOURCE_IDS,Long.class)) {
      var source=scope.getObservation(id);
      if(source==null)missingSources.add(id);else inputs.add(source);
    }
    for (var input : inputs) {
      var sources = new ArrayList<>(sourceActivities(input,scope));
      sources.addAll(attributionActivities(input,scope));
      for (var source : sources) if (seen.add(source)) activities.add(diagnostic(
          "id",source.getId(),"type",source.getType(),"outcome",source.getOutcome(),
          "evidence",decode(source),"diagnostic",source.getMetadata().get(DIAGNOSTIC_KEY),
          "footprints",source.getMetadata().get(SemanticExecutionFootprints.DIAGNOSTIC_KEY)));
    }
    return Utils.Json.asString(diagnostic("version",1,"timestampMillis",System.currentTimeMillis(),"scope",scope.getId(),
        "context",scope.getContextObservation()==null?null:scope.getContextObservation().getId(),
        "observer",scope.getObserver()==null?null:scope.getObserver().getId(),
        "evidenceComplete",evidence.complete(),"witnessComplete",completeWitness(evidence.witness()),
        "evidence",evidence,"collector",collector,"nativeEvidence",decode(nativeView.getMetadata().get(DATA_KEY)),
        "missingSources",missingSources,"activities",activities));
  }
  private SemanticExecutionEvidence() {}

  /** Match the core URN resolver's string conversion, retaining resource parameters. */
  static String resourceIdentifier(Object value) {
    if (value instanceof String text) return text;
    return value != null && value.getClass() == org.integratedmodelling.klab.api.knowledge.Urn.class
        ? value.toString() : null;
  }

  private static final java.lang.ref.ReferenceQueue<Observation> ISSUED_QUEUE = new java.lang.ref.ReferenceQueue<>();
  private record Issued(SemanticEvidenceBinding evidence, String payload, String scope) {}
  private static final Map<IdentityReference, Issued> ISSUED = new HashMap<>();
  private static final class IdentityReference extends java.lang.ref.WeakReference<Observation> {
    private final int hash;
    IdentityReference(Observation observation, java.lang.ref.ReferenceQueue<Observation> queue) {
      super(observation, queue); hash = System.identityHashCode(observation);
    }
    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
      return this == other || other instanceof IdentityReference that && get() != null && get() == that.get();
    }
  }
  private static String payload(Observation observation) {
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    return canonicalPayload(mapper.valueToTree(Observation.forTransport(observation))).toString();
  }
  private static com.fasterxml.jackson.databind.JsonNode canonicalPayload(com.fasterxml.jackson.databind.JsonNode value) {
    if(value.isObject()) {
      var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
      var names=new TreeSet<String>();value.fieldNames().forEachRemaining(names::add);
      for(var name:names) {
        var field=value.get(name);
        // Concept.type is a Set; its JSON array order can change through transport. Preserve
        // every value and multiplicity, and all ordering of genuinely ordered payload arrays.
        if(name.equals("type") && field.isArray() && value.path("@CLASS").asText().equals(
            "org.integratedmodelling.common.knowledge.ConceptImpl")) {
          var types=new ArrayList<com.fasterxml.jackson.databind.JsonNode>();field.forEach(types::add);
          types.sort(Comparator.comparing(com.fasterxml.jackson.databind.JsonNode::toString));
          var ordered=result.putArray(name);types.forEach(ordered::add);
        } else result.set(name,canonicalPayload(field));
      }
      return result;
    }
    if(value.isArray()) {
      var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
      value.forEach(item->result.add(canonicalPayload(item)));return result;
    }
    return value;
  }
  private static String scopePayload(ServiceContextScope scope) {
    var observer = scope.getObserver(); var focus = scope.getContextObservation();
    if (scope.getCurrentTransaction() != null) {
      if (observer != null) observer = scope.getCurrentTransaction().readObservation(observer);
      if (focus != null) focus = scope.getCurrentTransaction().readObservation(focus);
    }
    return Utils.Json.asString(Arrays.asList(scope.getId(), observer == null ? null : payload(observer),
        focus == null ? null : payload(focus)));
  }
  static void issue(Observation view, SemanticEvidenceBinding evidence, ServiceContextScope scope) {
    var snapshot = new Issued(evidence, payload(view), scopePayload(scope));
    synchronized (ISSUED) {
      java.lang.ref.Reference<? extends Observation> dead;
      while ((dead = ISSUED_QUEUE.poll()) != null) ISSUED.remove(dead);
      ISSUED.put(new IdentityReference(view, ISSUED_QUEUE), snapshot);
    }
  }
  private static SemanticEvidenceBinding issued(Observation view, ServiceContextScope scope) {
    final Issued snapshot;
    synchronized (ISSUED) { snapshot = ISSUED.get(new IdentityReference(view, null)); }
    return snapshot != null && Objects.equals(snapshot.payload(), payload(view))
        && Objects.equals(snapshot.scope(), scopePayload(scope)) ? snapshot.evidence() : null;
  }
  static boolean sameCheckedPayload(Observation original, Observation checked) {
    var normalized = (org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl) Observation.forTransport(checked);
    // Virtual query transport identities are local bookkeeping. All observable/production,
    // geometry, counts, source/cohort metadata and user-visible values remain compared exactly.
    normalized.setTransientId(original.getTransientId());
    var oldReceipt = original.getMetadata().get(org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView.DATA_KEY);
    if (oldReceipt != null) {
      var actualReceipt = checked.getMetadata().get(org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView.DATA_KEY);
      if (!(oldReceipt instanceof String before) || !(actualReceipt instanceof String after)
          || !CollectiveSelectionExecution.durablyEquivalent(Utils.Json.parseObject(before,
              org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView.class),
              Utils.Json.parseObject(after, org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView.class))) return false;
      // Preserve historical audit counters/bytes only after explicit durable equivalence above.
      normalized.getMetadata().put(org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView.DATA_KEY, oldReceipt);
    }
    var oldEvidence = original.getMetadata().get(DATA_KEY);
    var freshEvidence = checked.getMetadata().get(DATA_KEY);
    if (oldEvidence != null || freshEvidence != null) {
      var before = decode(oldEvidence); var after = decode(freshEvidence);
      if (before == null || after == null || !before.complete() || !after.complete()
          || !Objects.equals(before.contractVersion(), after.contractVersion())
          || !Objects.equals(before.obligations(), after.obligations())
          || !sameProof(before.witness(), after.witness())) return false;
      // The ordinary query already revalidated the actual source/member/observer state.
      // Only proof-equivalent historical audit counters may differ after an unchanged restart.
      normalized.getMetadata().put(DATA_KEY, oldEvidence);
    }
    return Objects.equals(payload(original), payload(normalized));
  }

  static final class Collector {
    private final TreeSet<String> terms = new TreeSet<>();
    private final Set<Actuator> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean complete = true;
    private final List<Object> diagnostics = new ArrayList<>();
    void trace(String gate,Object value) { diagnostics.add(diagnostic("gate",gate,"value",value)); }
    private Active admittedActive;

    private void pin(ServiceContextScope scope) {
      var reasoner = scope.getService(Reasoner.class);
      var capabilities = reasoner == null ? null : reasoner.capabilities(scope);
      if (!(capabilities instanceof Reasoner.Capabilities semantic)) { unknown(); return; }
      var current = Active.capture(semantic);
      if (admittedActive == null) admittedActive = current;
      else if (!admittedActive.equals(current)) unknown();
    }
    void expression(String expression, ServiceContextScope scope) {
      pin(scope);
      var reasoner = scope.getService(Reasoner.class);
      try {
        var resolved = reasoner == null || expression == null ? null : reasoner.resolveObservable(expression);
        if (resolved == null) unknown(); else observable(resolved);
      } catch (RuntimeException unavailable) { unknown(); }
    }

    void parameters(org.integratedmodelling.klab.api.lang.ServiceCall call, ServiceContextScope scope) {
      var active = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
      if(call==null || call.getParameters()==null){unknown();return;}
      for (var entry : call.getParameters().entrySet()) {
        var key = entry.getKey(); var value = entry.getValue();
        trace("parameter",diagnostic("call",call.getUrn(),"key",key,
            "valueType",value==null?null:value.getClass().getName()));
        if ("urns".equals(key)
            && org.integratedmodelling.klab.api.services.RuntimeService.CoreFunctor.classify(call)
                == org.integratedmodelling.klab.api.services.RuntimeService.CoreFunctor.URN_RESOLVER) {
          if (value instanceof Collection<?> identifiers) {
            if (identifiers.size() > 4096) { unknown(); continue; }
            for (var identifier : identifiers) if (resourceIdentifier(identifier) == null) unknown();
          } else if (resourceIdentifier(value) == null) unknown();
          // Identifiers are data for this exact core call. The actual adapter must separately
          // pass its finite execution-footprint contract; this does not attest arbitrary URNs.
          continue;
        }
        if (org.integratedmodelling.klab.api.services.RuntimeService.CoreFunctor.classify(call)
            == org.integratedmodelling.klab.api.services.RuntimeService.CoreFunctor.EXPRESSION_RESOLVER
            && "expression".equals(key)) continue;
        // Expression text is not executed from this mutable parameter map. The actual private
        // compiled scalar engine/processed-step proof is mandatory at the execution boundary.
        boolean interpreted = switch (call.getUrn()) {
          case "klab.tests.fixtures.classify" -> "result".equals(key);
          case "klab.tests.fixtures.failclassifier" -> "after".equals(key);
          case "klab.tests.fixtures.discoverafter" -> "members".equals(key) || "family".equals(key);
          default -> false;
        };
        if (interpreted && value instanceof String expression) expression(expression, scope);
        else value(value, scope, active, 0, Set.of("_targetId", "target", "urns").contains(key));
      }
    }
    private void value(Object value, ServiceContextScope scope, Set<Object> active, int depth, boolean dataString) {
      if (value == null) return;
      if (depth > 64) { unknown(); return; }
      var type = value.getClass();
      if (Set.of(Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class,
          java.math.BigDecimal.class, java.math.BigInteger.class, Boolean.class, Character.class).contains(type)) return;
      if (value instanceof String) { if (!dataString) unknown(); return; }
      if (type == org.integratedmodelling.common.knowledge.ObservableImpl.class) { observable((Observable) value); return; }
      if (type == org.integratedmodelling.common.knowledge.ConceptImpl.class) {
        term(((org.integratedmodelling.klab.api.knowledge.Concept) value).getUrn()); return;
      }
      if (type == org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl.class) {
        var observable = (org.integratedmodelling.klab.api.lang.kim.KimObservable) value;
        if (observable.getSemantics() == null) unknown(); else term(observable.getSemantics().getUrn());
        if (observable.getObserverSemantics() != null) term(observable.getObserverSemantics().getUrn());
        return;
      }
      if (type == org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl.class) {
        term(((org.integratedmodelling.klab.api.lang.kim.KimConcept) value).getUrn()); return;
      }
      if (value instanceof Observation observation) { observation(observation); return; }
      if (value instanceof Map<?,?> map) {
        if (!active.add(value) || map.size() > 4096) { unknown(); return; }
        try { for (var entry : map.entrySet()) {
          if (!(entry.getKey() instanceof String)) unknown();
          value(entry.getValue(), scope, active, depth + 1, dataString);
        } } finally { active.remove(value); }
        return;
      }
      if (value instanceof Collection<?> collection) {
        if (!active.add(value) || collection.size() > 4096) { unknown(); return; }
        try { for (var item : collection) value(item, scope, active, depth + 1, dataString); }
        finally { active.remove(value); }
        return;
      }
      // Interpretable strings, expressions, callbacks and opaque adapter parameters are UNKNOWN
      // unless a finite actual-implementation contract accounts for them explicitly.
      unknown();
    }

    void unknown() {
      complete = false;
      trace("UNKNOWN",StackWalker.getInstance().walk(frames -> frames.skip(1).limit(5)
          .map(frame -> frame.getClassName()+"."+frame.getMethodName()+":"+frame.getLineNumber()).toList()));
    }
    void term(String expression) { if (expression == null || expression.isBlank()) unknown(); else terms.add(expression); }
    void observable(Observable observable) {
      if (observable == null || observable.getSemantics() == null) { unknown(); return; }
      term(observable.getSemantics().getUrn());
      if (observable.getObserverSemantics() != null) term(observable.getObserverSemantics().getUrn());
    }
    void observation(Observation observation) {
      if (observation == null || observation.isEmpty()) { unknown(); return; }
      var current = observation;
      observable(current.getObservable());
      if (current.getValue() instanceof org.integratedmodelling.klab.api.knowledge.Semantics semantic)
        term(semantic.asConcept().getUrn());
    }
    void scope(ServiceContextScope scope) {
      pin(scope);
      observerInputs(scope);
      var context=scope.getContextObservation();
      if (context != null) {
        observation(context); // Keep the durable/admitted input in the dependency witness.
        if (scope.getCurrentTransaction() instanceof DigitalTwinImpl.TransactionImpl nativeAttempt)
          // A prerequisite can attribute this focus before a selection is published. Its
          // compound projected meaning is a semantic input too, not just its components.
          observation(nativeAttempt.readObservation(context));
      }
    }
    private void observerInputs(ServiceContextScope scope) {
      if(scope.getObserver()==null)return;
      var use=org.integratedmodelling.klab.runtime.ObserverSemanticUse.requirements(scope);
      if(!use.tracked())observation(scope.getObserver()); // legacy/uninstrumented is not independence
      else if(!use.valid())unknown();
      else use.terms().forEach(this::term);
    }
    void inherit(SemanticEvidenceBinding proof, ServiceContextScope scope) {
      pin(scope);
      boolean accepted = verified(proof, scope);
      trace("inherit",diagnostic("accepted",accepted,"proof",proof,"verification",LAST_VERIFICATION.get()));
      pin(scope);
      if (!accepted) { unknown(); return; }
      terms.addAll(proof.obligations());
    }
    void plan(Actuator actuator, ServiceContextScope scope) {
      pin(scope);
      if (actuator == null) { unknown(); return; }
      if (!visited.add(actuator)) return;
      if(admittedActive!=null && (actuator.getData().containsKey(org.integratedmodelling.klab.runtime.ObserverSemanticUse.KEY)
          || actuator.getData().get(Actuator.PRODUCING_MODELS_KEY) instanceof List<?> producers && !producers.isEmpty()))
        org.integratedmodelling.klab.runtime.ObserverSemanticUse.merge(
          actuator.getData().get(org.integratedmodelling.klab.runtime.ObserverSemanticUse.KEY),scope,
          new org.integratedmodelling.klab.runtime.ObserverSemanticUse.Owner(admittedActive.serviceId(),
              admittedActive.revision(),admittedActive.base(),admittedActive.session()));
      if (actuator.getObservation() != null) {
        observation(actuator.getObservation());
        if (actuator.getActuatorType() == Actuator.Type.REFERENCE)
          reference(actuator.getObservation(), scope);
      }
      if (actuator.getOperationObservable() != null) observable(actuator.getOperationObservable());
      if (actuator.getModelDependency() != null) observable(actuator.getModelDependency());
      for (var binding : actuator.getTargetBindings()) if (binding.getTarget() != null) observation(binding.getTarget());
      if (actuator.getContextBinding() != null) {
        var binding = actuator.getContextBinding();
        var context = scope.getObservation(binding.contextId());
        if (context == null || context.getId() != binding.contextId()) unknown();
        else observation(scope.getCurrentTransaction() == null ? context : scope.getCurrentTransaction().readObservation(context));
        expression(binding.childObservable(), scope);
      }
      var models = actuator.getData().get(Actuator.PRODUCING_MODELS_KEY);
      var receipts = actuator.getData().get(ModelValidationGate.PRODUCER_ADMISSIONS);
      var snapshots = actuator.getData().get(ModelValidationGate.PRODUCER_SNAPSHOTS);
      trace("actuator",diagnostic("type",actuator.getActuatorType(),"models",models,
          "admissions",receipts,"snapshotCount",snapshots instanceof List<?> list?list.size():null,
          "checkOnly",actuator.getData().get(Actuator.CLASSIFICATION_CHECK_ONLY)));
      if (models instanceof List<?> selected && !selected.isEmpty()) {
        if (!(receipts instanceof List<?> admissions) || !(snapshots instanceof List<?> frozen)) unknown();
        else for (var model : selected) {
          ModelValidationGate.AdmissionReceipt admitted = null;
          ModelValidationGate.ProducerSnapshot content = null;
          for (var entry : admissions) {
            if (!(entry instanceof String encoded)) { unknown(); continue; }
            var candidate = Utils.Json.parseObject(encoded, ModelValidationGate.AdmissionReceipt.class);
            if (Objects.equals(model, candidate.modelUrn())) {
              if (admitted != null) unknown();
              admitted = candidate;
            }
          }
          for (var entry : frozen) {
            if (!(entry instanceof String encoded)) { unknown(); continue; }
            var candidate = Utils.Json.parseObject(encoded, ModelValidationGate.ProducerSnapshot.class);
            if (Objects.equals(model, candidate.modelUrn())) {
              if (content != null) unknown();
              content = candidate;
            }
          }
          trace("modelAdmissionComparison",diagnostic("model",model,"admitted",admitted,
              "snapshotPresent",content!=null,"snapshotExecutableHash",content==null?null:content.executableHash(),
              "snapshotNamespace",content==null?null:content.namespace(),"active",admittedActive,
              "declaredObligations",content==null?null:ModelValidationGate.declaredSemanticObligations(content)));
          if (admitted == null || content == null || !admitted.sourceBacked()
              || !Objects.equals(admitted.executableHash(), content.executableHash())
              || !Objects.equals(admitted.namespace(), content.namespace())
              || admitted.sourceHash() == null || admitted.sourceHash().isBlank()
              || admittedActive == null || !Objects.equals(admitted.reasonerId(), admittedActive.serviceId())
              || admitted.knowledgeRevision() != admittedActive.revision()
              || !Objects.equals(admitted.semanticFingerprint(), admittedActive.base())
              || !Objects.equals(admitted.semanticSessionId(), admittedActive.session())
              || !admitted.semanticObligationsComplete()
              || !admitted.semanticObligations().containsAll(ModelValidationGate.declaredSemanticObligations(content))) unknown();
          else terms.addAll(admitted.semanticObligations());
        }
      } else if (actuator.getActuatorType() != Actuator.Type.REFERENCE
          && !Boolean.TRUE.equals(actuator.getData().get(Actuator.CLASSIFICATION_CHECK_ONLY))) {
        // Even empty producing models need their admitted signature. A blank list is not proof.
        unknown();
      }
      for (var call : actuator.getComputation()) parameters(call, scope);
      for (var child : actuator.getChildren()) plan(child, scope);
      var selected = actuator.getCollectiveSelectionDependency();
      if (selected != null) for (var root : selected.getComputation()) plan(root, scope);
      for (var dependency : actuator.getClassifierDependencies()) {
        if (dependency.source() == null || dependency.source().getSemantics() == null) unknown();
        else term(dependency.source().getSemantics().getUrn());
        if (dependency.contextObservable() != null) expression(dependency.contextObservable(), scope);
        for (var occurrence : dependency.occurrences()) plan(occurrence.classifier(), scope);
      }
      for (var input : actuator.getMemberQualityInputs().values()) {
        expression(input.sourceObservable(), scope);
        expression(input.request().members(), scope); expression(input.request().quality(), scope);
        expression(input.request().container().contextObservable(), scope);
      }
    }
    void selectionRequest(org.integratedmodelling.klab.api.knowledge.CollectiveSelectionBinding binding,
        Observation requested, Observation nativeView, ServiceContextScope scope) {
      pin(scope);
      observation(requested); observation(nativeView);
      binding.predicates().forEach(this::term); binding.families().forEach(this::term);
      for (var directive : binding.directives()) expression(directive, scope);
    }

    void reference(Observation observation, ServiceContextScope scope) {
      if (observation.getId() == Observation.QUERY_ID) {
        var owned = issued(observation, scope);
        if (owned == null) {
          var runtime = scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
          if (runtime == null) { unknown(); return; }
          // Transport is not an execution capability. Re-enter the ordinary query, which checks
          // actual native/update provenance and every view/member/support/observer guard.
          Observation query=observation;
          var declaredSupport=observation.getMetadata().get(org.integratedmodelling.klab.api.data.Metadata.IM_QUERY_GEOMETRY);
          if(declaredSupport instanceof String encoded) {
            // The native view's promoted Time extent adds derived fields. Re-submit the
            // original declared query support; the complete checked payload below must still
            // equal the supplied view, so forged support cannot grant a different result.
            var detached=(org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl)
                Observation.forTransport(observation);
            detached.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create(encoded));
            query=detached;
          }
          var checked = runtime.submit(query, scope).join();
          if (checked == null || checked.isEmpty() || !sameCheckedPayload(observation, checked)) { unknown(); return; }
          var view = checked.getMetadata().get(org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView.DATA_KEY);
          if (view instanceof String text) owned = Utils.Json.parseObject(text,
              org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView.class).semanticEvidence();
          else owned = decode(checked.getMetadata().get(DATA_KEY));
          if (!verified(owned, scope)) { unknown(); return; }
          issue(observation, owned, scope);
        }
        inherit(owned, scope);
        return;
      }
      boolean found = false;
      for (var activity : sourceActivities(observation, scope)) {
        var proof = decode(activity);
        if (activity.getOutcome() == Activity.Outcome.SUCCESS && verified(proof, scope)) {
          terms.addAll(proof.obligations()); found = true;
        }
      }
      if (!found) unknown();
    }

    /** Account consumed query views before this contextualizer changes their native domain. */
    void inputs(Observation target, Collection<Observation> inputs, ServiceContextScope scope) {
      for (var input : inputs) {
        if (input == null || target != null && (input == target || target.getId() != Observation.QUERY_ID
            && target.getId() != Observation.UNASSIGNED_ID && input.getId() == target.getId())) continue;
        observation(input); reference(input, scope);
      }
    }

    void activityChildren(Activity activity, ServiceContextScope scope) {
      var transaction = scope.getCurrentTransaction();
      if (transaction == null) { unknown(); return; }
      var seen = Collections.newSetFromMap(new IdentityHashMap<Activity, Boolean>());
      var queue = new ArrayDeque<Activity>(); queue.add(activity);
      while (!queue.isEmpty()) {
        var next = queue.removeFirst();
        if (!seen.add(next)) continue;
        for (var link : transaction.outgoing(next)) if (link.type() == GraphModel.Relationship.TRIGGERED
            && link.target() instanceof Activity child) {
          if (child.getType().isContextualization()) {
            if (child.getOutcome() != Activity.Outcome.SUCCESS) unknown();
            else inherit(decode(child), scope);
          }
          queue.add(child);
        }
      }
    }

    SemanticEvidenceBinding finish(ServiceContextScope scope) {
      pin(scope);
      observerInputs(scope);
      var reasoner = scope.getService(Reasoner.class);
      var caps = reasoner == null ? null : reasoner.capabilities(scope);
      SemanticDependencyWitness witness = null;
      trace("finishCapabilities",diagnostic("available",caps instanceof Reasoner.Capabilities,
          "active",caps instanceof Reasoner.Capabilities current?Active.capture(current):null,
          "admittedActive",admittedActive,"alreadyComplete",complete));
      if (complete && caps instanceof Reasoner.Capabilities semantic
          && SemanticContentFingerprint.scoped(semantic.getSemanticContentFingerprint())) {
        var request = new SemanticDependencyRequest(semantic.getServiceId(),
            semantic.getKnowledgeRevision(), semantic.getSemanticContentFingerprint(), List.copyOf(terms));
        var before = Active.capture(semantic);
        if (!before.equals(admittedActive)) unknown();
        if (complete) {
          witness = reasoner.semanticDependencies(request, scope);
          trace("reasonerAnswer",diagnostic("request",request,"witness",witness,"activeBefore",before));
          var after = reasoner.capabilities(scope);
          if (!(after instanceof Reasoner.Capabilities current) || !admittedActive.equals(Active.capture(current))
              || !before.equals(Active.capture(current)) || !answers(request, witness)) {
            unknown(); witness = null;
          }
        }
      }
      var result = new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION, complete, List.copyOf(terms), witness);
      LAST_COLLECTOR.set(diagnostic("complete",complete,"admittedActive",admittedActive,
          "events",List.copyOf(diagnostics),"evidence",result));
      return result;
    }
  }

  /** Called after real contextualization/result publication, before its child commit, outside
   * the root graph monitor. No semantic RPC is deferred into a beforeCommit callback. */
  static void record(Activity activity, Actuator plan, Observation target,
      Collection<Observation> outcomes, Collection<Observation> actualInputs, Collection<String> additionalTerms,
      boolean boundedExecution, ServiceContextScope scope) {
    recordPrepared(activity,prepare(plan,additionalTerms,boundedExecution,scope),target,outcomes,actualInputs,scope);
  }

  /** Capture consumed input views while their pre-effect scope/read views still apply. */
  static Collector prepare(Actuator plan, Collection<String> additionalTerms,
      boolean boundedExecution, ServiceContextScope scope) {
    var collector = new Collector();
    collector.trace("executionFootprintBounded",boundedExecution);
    if (!boundedExecution) collector.unknown();
    additionalTerms.forEach(collector::term);
    collector.scope(scope); collector.plan(plan, scope);
    return collector;
  }

  /** Private in-operation continuation, never a caller-supplied or persisted proof. */
  static void recordPrepared(Activity activity, Collector collector, Observation target,
      Collection<Observation> outcomes, Collection<Observation> actualInputs, ServiceContextScope scope) {
    if(scope.isInterrupted() || scope.getCurrentTransaction()!=null && scope.getCurrentTransaction().isFailed())
      collector.unknown();
    if (target != null) collector.observation(scope.getCurrentTransaction() == null ? target
        : scope.getCurrentTransaction().readObservation(target));
    for (var outcome : outcomes) collector.observation(scope.getCurrentTransaction() == null ? outcome
        : scope.getCurrentTransaction().readObservation(outcome));
    collector.inputs(target,actualInputs,scope);
    collector.activityChildren(activity, scope);
    activity.getMetadata().put(DATA_KEY, Utils.Json.asString(collector.finish(scope)));
    activity.getMetadata().put(DIAGNOSTIC_KEY,Utils.Json.asString(LAST_COLLECTOR.get()));
    if (scope.getCurrentTransaction() != null) scope.getCurrentTransaction().update(activity);
  }

  static SemanticEvidenceBinding selection(org.integratedmodelling.klab.api.services.runtime.Dataflow plan,
      org.integratedmodelling.klab.api.knowledge.CollectiveSelectionBinding binding,
      Observation nativeView, Collection<Observation> members, Observation previous,
      ServiceContextScope scope) {
    var collector = new Collector(); collector.scope(scope);
    collector.selectionRequest(binding, previous, nativeView, scope);
    collector.inherit(decode(nativeView.getMetadata().get(DATA_KEY)), scope);
    if (plan != null) for (var root : plan.getComputation()) collector.plan(root, scope);
    if (previous != null && previous.getMetadata().get(
        org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView.DATA_KEY) instanceof String encoded) {
      var prior = Utils.Json.parseObject(encoded, org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView.class);
      collector.inherit(prior.semanticEvidence(), scope);
    }
    for (var member : members) {
      collector.observation(member);
      // Classification facts are not reclassified to a desired answer. Their actual source
      // activities still contribute the classifier/model/input semantic obligations on replay;
      // caller-supplied view metadata cannot narrow those durable dependencies.
      for (var activity : attributionActivities(member, scope)) {
        if (activity.getOutcome() != Activity.Outcome.SUCCESS) collector.unknown();
        else collector.inherit(decode(activity), scope);
      }
    }
    return collector.finish(scope);
  }

  private static List<Activity> attributionActivities(Observation member, ServiceContextScope scope) {
    var result = new ArrayList<Activity>();
    for (var effect : List.of(GraphModel.Relationship.CLASSIFIED, GraphModel.Relationship.CHARACTERIZED)) {
      for (var transaction = scope.getCurrentTransaction(); transaction != null; transaction = transaction.getParent())
        for (var link : transaction.incoming(member))
          if (link.type() == effect && link.source() instanceof Activity activity
              && transaction instanceof DigitalTwinImpl.TransactionImpl nativeAttempt
              && nativeAttempt.ownsExecutionActivity(activity)) result.add(activity);
      if (member.getId() > 0) result.addAll(scope.getDigitalTwin().getKnowledgeGraph()
          .query(Activity.class, scope).target(member).along(effect).run(scope).stream()
          .filter(activity -> activity.getId() > 0).toList());
    }
    return result;
  }

  static List<Activity> sourceActivities(Observation observation, ServiceContextScope scope) {
    var result = new ArrayList<Activity>();
    if (observation.getObservable() == null) return result;
    final GraphModel.Relationship effect;
    try { effect = GraphModel.Relationship.forContextualization(observation.getObservable().getContextualization()); }
    catch (IllegalArgumentException unsupported) { return result; }
    for (var transaction = scope.getCurrentTransaction(); transaction != null; transaction = transaction.getParent())
      for (var link : transaction.incoming(observation))
        if (link.type() == effect && link.source() instanceof Activity activity
            && transaction instanceof DigitalTwinImpl.TransactionImpl nativeAttempt
            && nativeAttempt.ownsExecutionActivity(activity)) result.add(activity);
    if (observation.getId() > 0) result.addAll(scope.getDigitalTwin().getKnowledgeGraph()
        .query(Activity.class, scope).target(observation).along(effect).run(scope).stream()
        .filter(activity -> activity.getId() > 0).toList());
    return result;
  }

  static SemanticEvidenceBinding decode(Object encoded) {
    if (!(encoded instanceof String text)) return null;
    try { return Utils.Json.parseObject(text, SemanticEvidenceBinding.class); }
    catch (RuntimeException malformed) { return null; }
  }

  static SemanticEvidenceBinding decode(Activity activity) {
    return decode(activity.getMetadata().get(DATA_KEY));
  }

  static Map<String,Object> verificationDiagnostic() { return LAST_VERIFICATION.get(); }

  static boolean verified(SemanticEvidenceBinding proof, ServiceContextScope scope) {
    LAST_VERIFICATION.set(diagnostic("gate","proof-present-complete-witness","proof",proof));
    if (proof == null || !proof.complete() || proof.witness() == null) return false;
    var reasoner = scope.getService(Reasoner.class);
    var caps = reasoner == null ? null : reasoner.capabilities(scope);
    LAST_VERIFICATION.set(diagnostic("gate","capabilities","available",caps instanceof Reasoner.Capabilities));
    if (!(caps instanceof Reasoner.Capabilities semantic)) return false;
    var request = new SemanticDependencyRequest(semantic.getServiceId(), semantic.getKnowledgeRevision(),
        semantic.getSemanticContentFingerprint(), proof.obligations());
    LAST_VERIFICATION.set(diagnostic("gate","inputHash","expected",request.inputHash(),"actual",proof.witness().inputHash()));
    if (!Objects.equals(request.inputHash(), proof.witness().inputHash())) return false;
    var before = Active.capture(semantic);
    var actual = reasoner.semanticDependencies(request, scope);
    var after = reasoner.capabilities(scope);
    LAST_VERIFICATION.set(diagnostic("gate","revalidation","request",request,"before",before,
        "after",after instanceof Reasoner.Capabilities current?Active.capture(current):null,
        "actual",actual,"previous",proof.witness(),"answers",answers(request,actual),
        "sameProof",sameProof(proof.witness(),actual)));
    return after instanceof Reasoner.Capabilities current && before.equals(Active.capture(current))
        && answers(request, actual) && sameProof(proof.witness(), actual);
  }

  private record Active(String serviceId, long revision, String base, String session) {
    static Active capture(Reasoner.Capabilities caps) {
      return new Active(caps.getServiceId(), caps.getKnowledgeRevision(), caps.getSemanticContentFingerprint(), caps.getSemanticSessionId());
    }
  }
  private static boolean answers(SemanticDependencyRequest request, SemanticDependencyWitness answer) {
    return answer != null && SemanticDependencyWitness.VERSION.equals(answer.contractVersion())
        && Objects.equals(request.expectedServiceId(), answer.serviceId())
        && request.expectedKnowledgeRevision() == answer.knowledgeRevision()
        && Objects.equals(request.expectedBaseFingerprint(), answer.baseFingerprint())
        && Objects.equals(request.inputHash(), answer.inputHash());
  }

  static boolean completeWitness(SemanticDependencyWitness witness) {
    return witness != null && witness.completeAt(System.currentTimeMillis());
  }

  static boolean sameProof(SemanticDependencyWitness previous, SemanticDependencyWitness actual) {
    return SemanticDependencyWitness.revalidates(previous, actual);
  }
}
