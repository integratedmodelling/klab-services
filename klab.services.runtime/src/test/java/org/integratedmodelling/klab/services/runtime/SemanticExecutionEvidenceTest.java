package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ModelImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

/** Runtime signature accounting/composition against a controlled semantic proof boundary.
 * Real locality extraction and live producer revalidation have separate acceptance tests. */
class SemanticExecutionEvidenceTest {
  @Test void genericConsumedQueryIsFrozenBeforeOwnEffectsAndFailedAttemptCannotCertify() {
    var source=new SemanticExecutionEvidence.Collector();source.term("test:Native");
    var query=new ObservationImpl();query.setId(Observation.QUERY_ID);query.setObservable(observable("test:Native"));
    ((ConceptImpl)query.getObservable().getSemantics()).setCollective(true);query.setChildrenCount(1);
    query.getMetadata().put(SemanticEvidenceBinding.DATA_KEY,Utils.Json.asString(source.finish(scope)));
    var changed=(ObservationImpl)Observation.forTransport(query);changed.setChildrenCount(2);
    var runtime=mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
    var effects=new java.util.concurrent.atomic.AtomicBoolean();
    when(runtime.submit(any(),eq(scope))).thenAnswer(call -> java.util.concurrent.CompletableFuture.completedFuture(
        effects.get()?changed:Observation.forTransport(query)));
    var frozen=new SemanticExecutionEvidence.Collector();frozen.inputs(null,List.of(query),scope);
    effects.set(true);
    var late=new SemanticExecutionEvidence.Collector();late.reference(Observation.forTransport(query),scope);
    assertFalse(late.finish(scope).complete(),"Post-effect replay must not silently bless a changed input view");
    var tx=mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Transaction.class);
    when(scope.getCurrentTransaction()).thenReturn(tx);
    var activity=Activity.of(Activity.Type.INSTANTIATION);
    SemanticExecutionEvidence.recordPrepared(activity,frozen,null,List.of(),List.of(),scope);
    assertTrue(SemanticExecutionEvidence.decode(activity).complete());
    when(tx.isFailed()).thenReturn(true);
    SemanticExecutionEvidence.recordPrepared(activity,frozen,null,List.of(),List.of(),scope);
    assertFalse(SemanticExecutionEvidence.decode(activity).complete(),"Rollback cannot leave complete source evidence");
  }

  @Test void genericInputIdentitySubstitutionOrUnavailableAuditIsRejected() {
    var before=new ObservationImpl();var other=new ObservationImpl();
    assertTrue(CompiledDataflow.sameConsumedInputs(Optional.of(List.of(before)),Optional.of(List.of(before))));
    assertFalse(CompiledDataflow.sameConsumedInputs(Optional.of(List.of(before)),Optional.of(List.of(other))));
    assertFalse(CompiledDataflow.sameConsumedInputs(Optional.of(List.of(before)),Optional.empty()));
  }

  @Test void humanFallbackDoesNotNeedAuthorityFreshnessButSpecificMatchingDoes() {
    when(scope.getId()).thenReturn("test");
    var observer=new ObservationImpl();observer.setId(7);observer.setUrn("observer:7");
    var human=observable("authority:Mutable");human.getSemantics().getType().add(SemanticType.AGENT);
    observer.setObservable(human);when(scope.getObserver()).thenReturn(observer);
    var candidate=observable("test:Native");var requested=observable("test:Native");
    try(var capture=org.integratedmodelling.klab.runtime.ObserverSemanticUse.capture(scope)) {
      assertEquals(1,org.integratedmodelling.klab.runtime.ObserverResolution.preference(candidate,requested,scope));
      var collector=new SemanticExecutionEvidence.Collector();collector.scope(scope);collector.term("test:Native");
      assertTrue(SemanticExecutionEvidence.completeWitness(collector.finish(scope).witness()));
    }
    var agent=observable("test:Agent");agent.getSemantics().getType().add(SemanticType.AGENT);
    candidate.setObserverSemantics(agent.getSemantics());
    when(reasoner.semanticDistance(any(),any(),isNull(),eq(1L))).thenReturn(0);
    try(var capture=org.integratedmodelling.klab.runtime.ObserverSemanticUse.capture(scope)) {
      assertEquals(0,org.integratedmodelling.klab.runtime.ObserverResolution.preference(candidate,requested,scope));
      var collector=new SemanticExecutionEvidence.Collector();collector.scope(scope);
      assertFalse(SemanticExecutionEvidence.completeWitness(collector.finish(scope).witness()));
    }
  }
  @Test void rejectedAuthorityObserverCandidateRemainsDependencyOfFallback() {
    when(scope.getId()).thenReturn("test");
    var observer=new ObservationImpl();observer.setId(7);observer.setUrn("observer:7");
    var human=observable("test:Human");human.getSemantics().getType().add(SemanticType.AGENT);
    observer.setObservable(human);when(scope.getObserver()).thenReturn(observer);
    var candidate=observable("test:Native");var requested=observable("test:Native");
    var agent=observable("authority:Mutable");agent.getSemantics().getType().add(SemanticType.AGENT);
    candidate.setObserverSemantics(agent.getSemantics());
    when(reasoner.semanticDistance(any(),any(),isNull(),eq(1L))).thenReturn(-1);
    try(var capture=org.integratedmodelling.klab.runtime.ObserverSemanticUse.capture(scope)) {
      assertEquals(-1,org.integratedmodelling.klab.runtime.ObserverResolution.preference(candidate,requested,scope));
      candidate.setObserverSemantics(null);
      assertEquals(1,org.integratedmodelling.klab.runtime.ObserverResolution.preference(candidate,requested,scope));
      var collector=new SemanticExecutionEvidence.Collector();collector.scope(scope);
      assertFalse(SemanticExecutionEvidence.completeWitness(collector.finish(scope).witness()));
    }
  }
  static final String RESOURCE = "klab:random:objects:polygons#fraction=1&xdivs=3&ydivs=3";

  SemanticEvidenceBinding parameterEvidence(String function,Object identifiers) {
    var collector=new SemanticExecutionEvidence.Collector();
    collector.parameters(new org.integratedmodelling.common.lang.ServiceCallImpl(function,"urns",identifiers),scope);
    return collector.finish(scope);
  }

  @Test void builtInTypedResourceUrnsMatchStringAccountingAndRetainParameters() {
    var urn=Urn.of(RESOURCE);
    assertEquals(RESOURCE,SemanticExecutionEvidence.resourceIdentifier(urn));
    assertNotEquals(urn.getUrn(),SemanticExecutionEvidence.resourceIdentifier(urn));
    var typed=parameterEvidence("klab.core.urn.resolver",List.of(urn));
    var strings=parameterEvidence("klab.core.urn.resolver",List.of(RESOURCE));
    assertTrue(typed.complete()); assertTrue(SemanticExecutionEvidence.completeWitness(typed.witness()));
    assertEquals(strings,typed);
    assertTrue(parameterEvidence("klab.core.urn.resolver",List.of(urn,RESOURCE)).complete());
  }

  @Test void unsupportedResourceEntriesAndSubclassesRemainUnknown() {
    var subclass=new Urn(){@Override public String toString(){return RESOURCE;}};
    for(var unsupported:List.of(new Object(),subclass,Map.of("urn",RESOURCE),List.of(RESOURCE))) {
      assertNull(SemanticExecutionEvidence.resourceIdentifier(unsupported));
      assertFalse(parameterEvidence("klab.core.urn.resolver",List.of(Urn.of(RESOURCE),unsupported)).complete());
    }
  }

  @Test void typedResourceUrnDoesNotAttestArbitraryPluginArguments() {
    assertFalse(parameterEvidence("opaque.example",List.of(Urn.of(RESOURCE))).complete());
  }

  @Test void typedResourceAccountingDoesNotReplaceActualExecutionFootprintOrAuthorityWitness() {
    var actuator=model("unreviewed","subject:Tree",null,null);
    actuator.getComputation().add(new org.integratedmodelling.common.lang.ServiceCallImpl(
        "klab.core.urn.resolver","urns",List.of(Urn.of(RESOURCE))));
    assertFalse(SemanticExecutionEvidence.prepare(actuator,List.of(),false,scope).finish(scope).complete());
    var affected=new SemanticExecutionEvidence.Collector();
    affected.parameters(new org.integratedmodelling.common.lang.ServiceCallImpl(
        "klab.core.urn.resolver","urns",List.of(Urn.of(RESOURCE))),scope);
    affected.term("authority:Mutable");
    assertFalse(SemanticExecutionEvidence.completeWitness(affected.finish(scope).witness()));
  }
  static final String BASE = "semantic-content-v2:" + "a".repeat(64);
  ServiceContextScope scope;
  Reasoner reasoner;
  Reasoner.Capabilities caps;

  @BeforeAll static void configure() { CollectiveCompletionQueryTest.configure(); }
  @BeforeEach void setup() {
    scope = mock(ServiceContextScope.class); reasoner = mock(Reasoner.class); caps = mock(Reasoner.Capabilities.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.capabilities(scope)).thenReturn(caps);
    when(caps.getServiceId()).thenReturn("reasoner"); when(caps.getKnowledgeRevision()).thenReturn(1L);
    when(caps.getSemanticContentFingerprint()).thenReturn(BASE); when(caps.getSemanticSessionId()).thenReturn("owner-one");
    when(reasoner.semanticDependencies(any(), eq(scope))).thenAnswer(call -> witness(call.getArgument(0)));
  }

  SemanticDependencyWitness witness(SemanticDependencyRequest request) {
    boolean affected = request.obligations().contains("authority:Mutable");
    return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,
        affected ? SemanticDependencyWitness.Status.REVALIDATION_REQUIRED : SemanticDependencyWitness.Status.COMPLETE,
        request.expectedServiceId(), request.expectedKnowledgeRevision(), request.expectedBaseFingerprint(), request.inputHash(),
        affected ? List.of(new SemanticDependencyWitness.AuthorityUse("mutable", SemanticDependencyWitness.Kind.UNVERSIONED, null, 0)) : List.of(), null);
  }

  ObservableImpl observable(String name) {
    var concept = new ConceptImpl(); concept.setUrn(name); concept.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE));
    var result = new ObservableImpl(); result.setSemantics(concept); result.setUrn(name); result.setArtifactType(Artifact.Type.OBJECT);
    result.setDescriptionType(Contextualization.ACKNOWLEDGEMENT); return result;
  }

  ActuatorImpl model(String name, String output, String dependency, List<String> admittedTerms) {
    var model = new ModelImpl(); model.setUrn("fixture." + name); model.setNamespace("fixture");
    model.getObservables().add(observable(output)); if (dependency != null) model.getDependencies().add(observable(dependency));
    var snapshot = ModelValidationGate.snapshot(model);
    var terms = admittedTerms == null ? ModelValidationGate.declaredSemanticObligations(snapshot) : admittedTerms;
    var request = new SemanticDependencyRequest("reasoner", 1, BASE, terms);
    var receipt = new ModelValidationGate.AdmissionReceipt(model.getUrn(), model.getNamespace(), "1", "b".repeat(64),
        snapshot.executableHash(), "reasoner", 1, List.of(), BASE, terms, true, witness(request), "owner-one");
    var observation = new ObservationImpl(); observation.setId(-100); observation.setObservable(model.getObservables().getFirst());
    var actuator = new ActuatorImpl(); actuator.setActuatorType(Actuator.Type.OBSERVE); actuator.setObservation(observation);
    actuator.getData().put(Actuator.PRODUCING_MODELS_KEY, List.of(model.getUrn()));
    actuator.getData().put(ModelValidationGate.PRODUCER_ADMISSIONS, List.of(Utils.Json.asString(receipt)));
    actuator.getData().put(ModelValidationGate.PRODUCER_SNAPSHOTS, List.of(Utils.Json.asString(snapshot)));
    return actuator;
  }

  @Test void differentModelLocalAliasesDoNotChangeSelectionConceptSignature() {
    var requested = new ObservationImpl(); requested.setId(Observation.QUERY_ID);
    var meaning = observable("test:Beech test:Tree"); meaning.setUrn("test:Beech test:Tree named localOne");
    requested.setObservable(meaning);
    var nativeView = new ObservationImpl(); nativeView.setId(Observation.QUERY_ID);
    nativeView.setObservable(observable("each test:Tree"));
    var directive = observable("test:Species of each test:Tree");
    directive.setUrn("any test:Species of each test:Tree named classifierLocal");
    when(reasoner.resolveObservable("any test:Species of each test:Tree")).thenReturn(directive);
    var binding = new CollectiveSelectionBinding(meaning.getUrn(), nativeView.getObservable().getUrn(),
        List.of("test:Beech"), List.of("test:Species"), List.of("any test:Species of each test:Tree"),
        "reasoner", 1, "*", null, null, "scope", null, null, BASE, "owner-one");
    var first = new SemanticExecutionEvidence.Collector(); first.selectionRequest(binding, requested, nativeView, scope);
    var firstProof = first.finish(scope);
    meaning.setUrn("test:Beech test:Tree named differentLocal"); directive.setUrn("another wrapper name");
    var second = new SemanticExecutionEvidence.Collector(); second.selectionRequest(binding, requested, nativeView, scope);
    var secondProof = second.finish(scope);
    assertTrue(firstProof.complete()); assertTrue(secondProof.complete());
    assertEquals(firstProof.obligations(), secondProof.obligations());
    assertEquals(firstProof.witness().inputHash(), secondProof.witness().inputHash());
    assertTrue(firstProof.obligations().stream().noneMatch(term -> term.contains("named") || term.contains("any ")));
  }

  @Test void everyAdmittedModelOutputAndDependencyAndNestedPlanContributes() {
    var root = model("root", "test:Native", "test:HiddenInput", null);
    root.getChildren().add(model("child", "test:Child", "test:ChildInput", null));
    var collector = new SemanticExecutionEvidence.Collector(); collector.plan(root, scope);
    collector.observable(observable("test:ExcludedMember"));
    var proof = collector.finish(scope);
    assertTrue(proof.complete()); assertTrue(SemanticExecutionEvidence.verified(proof, scope));
    assertEquals(Set.of("test:Native", "test:HiddenInput", "test:Child", "test:ChildInput", "test:ExcludedMember"), Set.copyOf(proof.obligations()));
  }

  @Test void subsetThatOmitsAdmittedInputCannotClaimCompleteExecution() {
    var root = model("root", "test:Native", "authority:Mutable", List.of("test:Native"));
    var collector = new SemanticExecutionEvidence.Collector(); collector.plan(root, scope);
    var proof = collector.finish(scope);
    assertFalse(proof.complete()); assertNull(proof.witness());
    verify(reasoner, never()).semanticDependencies(any(), any());
  }

  @Test void runtimeBoundTypedConceptIsAccountedEvenWhenAbsentFromDeclaredSource() {
    var root = model("root", "test:Native", null, null);
    var call = new org.integratedmodelling.common.lang.ServiceCallImpl("opaque.example");
    call.getParameters().put("bound", Map.of("nested", List.of(observable("authority:Mutable").getSemantics())));
    root.getComputation().add(call);
    var collector = new SemanticExecutionEvidence.Collector(); collector.plan(root, scope);
    var proof = collector.finish(scope);
    assertTrue(proof.obligations().contains("authority:Mutable"));
    assertEquals(SemanticDependencyWitness.Status.REVALIDATION_REQUIRED, proof.witness().status());
    assertFalse(SemanticExecutionEvidence.verified(proof, scope));
  }

  @Test void opaqueAndCyclicRuntimeArgumentsCannotEstablishCompleteClosure() {
    for (var argument : List.of(new Object(), new HashMap<String,Object>())) {
      if (argument instanceof Map<?,?>) ((Map<String,Object>)argument).put("cycle", argument);
      var root = model("root", "test:Native", null, null);
      var call = new org.integratedmodelling.common.lang.ServiceCallImpl("opaque.example");
      call.getParameters().put("bound", argument); root.getComputation().add(call);
      var collector = new SemanticExecutionEvidence.Collector(); collector.plan(root, scope);
      var proof = collector.finish(scope);
      assertFalse(proof.complete()); assertNull(proof.witness());
    }
  }

  @Test void auditedFixtureInterpretedStringArgumentsBecomeConceptsWithoutLocalNames() {
    var root = model("root", "test:Native", null, null);
    var call = new org.integratedmodelling.common.lang.ServiceCallImpl("klab.tests.fixtures.discoverafter");
    call.getParameters().put("members", "test:Members named localMembers");
    call.getParameters().put("family", "test:Family named localFamily");
    when(reasoner.resolveObservable("test:Members named localMembers")).thenReturn(observable("test:Members"));
    when(reasoner.resolveObservable("test:Family named localFamily")).thenReturn(observable("test:Family"));
    root.getComputation().add(call);
    var collector = new SemanticExecutionEvidence.Collector(); collector.plan(root, scope);
    var proof = collector.finish(scope);
    assertTrue(proof.obligations().containsAll(List.of("test:Members", "test:Family")));
    assertTrue(proof.obligations().stream().noneMatch(term -> term.contains("named")));
  }

  @Test void missingOriginalModelSignatureIsUnknownEvenWhenRequestHasNoAuthority() {
    var root = model("root", "test:Native", null, null);
    root.getData().remove(ModelValidationGate.PRODUCER_ADMISSIONS);
    var collector = new SemanticExecutionEvidence.Collector(); collector.plan(root, scope);
    assertFalse(collector.finish(scope).complete());
  }

  @Test void sameGlobalBaseAllowsUnaffectedClosureButUnversionedAffectedClosureCannotReplay() {
    var unaffected = new SemanticExecutionEvidence.Collector(); unaffected.term("test:Native");
    assertTrue(SemanticExecutionEvidence.verified(unaffected.finish(scope), scope));
    var affected = new SemanticExecutionEvidence.Collector(); affected.term("authority:Mutable");
    var proof = affected.finish(scope);
    assertTrue(proof.complete(), "Runtime accounting and provider certification are distinct");
    assertEquals(SemanticDependencyWitness.Status.REVALIDATION_REQUIRED, proof.witness().status());
    assertFalse(SemanticExecutionEvidence.verified(proof, scope));
  }

  @Test void arbitraryObservationMetadataIsNotAnOwnedVirtualQueryCapability() {
    var signature = new SemanticExecutionEvidence.Collector(); signature.term("test:Native"); var proof = signature.finish(scope);
    var fake = new ObservationImpl(); fake.setId(Observation.QUERY_ID); fake.setObservable(observable("test:Native"));
    fake.getMetadata().put(SemanticEvidenceBinding.DATA_KEY, Utils.Json.asString(proof));
    var unowned = new SemanticExecutionEvidence.Collector(); unowned.reference(fake, scope);
    assertFalse(unowned.finish(scope).complete());
    SemanticExecutionEvidence.issue(fake, proof, scope);
    var actual = new SemanticExecutionEvidence.Collector(); actual.reference(fake, scope);
    assertTrue(actual.finish(scope).complete());
    var copy = Utils.Json.parseObject(Utils.Json.asString(fake), Observation.class);
    var transported = new SemanticExecutionEvidence.Collector(); transported.reference(copy, scope);
    assertFalse(transported.finish(scope).complete(), "A transported view must obtain a fresh checked query");
  }

  @Test void diagnosticPreservesColdRejectionEvenWhenLaterIndependentRevalidationCompletes() {
    var collector=new SemanticExecutionEvidence.Collector();collector.term("test:Native");var previous=collector.finish(scope);
    var activity=new org.integratedmodelling.klab.api.provenance.impl.ActivityImpl();
    activity.getMetadata().put(RuntimeService.COLLECTIVE_REASONER,"reasoner");
    activity.getMetadata().put(SemanticEvidenceBinding.DATA_KEY,Utils.Json.asString(previous));
    var calls=new java.util.concurrent.atomic.AtomicInteger();
    when(reasoner.semanticDependencies(any(),eq(scope))).thenAnswer(call->{
      SemanticDependencyRequest request=call.getArgument(0);
      return calls.getAndIncrement()==0?new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,
          SemanticDependencyWitness.Status.UNKNOWN,"reasoner",1,BASE,request.inputHash(),List.of(),"Unmaterialized cold expression"):witness(request);
    });
    var cold=new LinkedHashMap<String,Object>();
    assertFalse(SemanticEvidence.matches(activity,"reasoner",1,BASE,"owner-two",false,scope,cold));
    var warm=new LinkedHashMap<String,Object>();
    assertTrue(SemanticEvidence.matches(activity,"reasoner",1,BASE,"owner-two",false,scope,warm));
    var failure=(Map<?,?>)cold.get("verification");
    assertEquals("revalidation",failure.get("gate"));
    assertEquals(SemanticDependencyWitness.Status.UNKNOWN,((SemanticDependencyWitness)failure.get("actual")).status());
    assertEquals("Unmaterialized cold expression",((SemanticDependencyWitness)failure.get("actual")).reason());
    assertEquals(Boolean.FALSE,failure.get("sameProof"));
    assertNotNull(failure.get("before"));assertNotNull(failure.get("after"));
    assertEquals(previous,Utils.Json.parseObject((String)activity.getMetadata().get(SemanticEvidenceBinding.DATA_KEY),SemanticEvidenceBinding.class));
  }
  @Test void identicalContentRestartRecomputesProofWhileChangedContentCannotMatch() {
    var collector = new SemanticExecutionEvidence.Collector(); collector.term("test:Native"); var previous = collector.finish(scope);
    when(caps.getKnowledgeRevision()).thenReturn(9L); when(caps.getSemanticSessionId()).thenReturn("owner-two");
    assertTrue(SemanticExecutionEvidence.verified(previous, scope));
    when(caps.getKnowledgeRevision()).thenReturn(1L);
    when(caps.getSemanticContentFingerprint()).thenReturn("semantic-content-v2:" + "b".repeat(64));
    assertFalse(SemanticExecutionEvidence.verified(previous, scope));
  }

  @Test void transportedNativeInputAcceptsRevalidatedCounterChangeButNotChangedPayloadOrProof() {
    var signature = new SemanticExecutionEvidence.Collector(); signature.term("test:Native");
    var original = new ObservationImpl(); original.setId(Observation.QUERY_ID);
    original.setObservable(observable("test:Native"));
    original.getMetadata().put(SemanticEvidenceBinding.DATA_KEY, Utils.Json.asString(signature.finish(scope)));
    var historicalBytes = original.getMetadata().get(SemanticEvidenceBinding.DATA_KEY);
    when(caps.getKnowledgeRevision()).thenReturn(9L);
    when(caps.getSemanticSessionId()).thenReturn("owner-two");
    var current = new SemanticExecutionEvidence.Collector(); current.term("test:Native");
    var checked = (ObservationImpl) Utils.Json.parseObject(Utils.Json.asString(original), Observation.class);
    checked.getMetadata().put(SemanticEvidenceBinding.DATA_KEY, Utils.Json.asString(current.finish(scope)));
    assertTrue(SemanticExecutionEvidence.sameCheckedPayload(original, checked),
        () -> "Original: " + Utils.Json.asString(original) + "\nChecked: " + Utils.Json.asString(checked));
    var runtime = mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
    when(runtime.submit(original, scope)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(checked));
    var input = new SemanticExecutionEvidence.Collector(); input.reference(original, scope);
    assertTrue(input.finish(scope).complete());
    verify(runtime).submit(original, scope);
    assertEquals(historicalBytes, original.getMetadata().get(SemanticEvidenceBinding.DATA_KEY));
    checked.setChildrenCount(1);
    assertFalse(SemanticExecutionEvidence.sameCheckedPayload(original, checked));
    checked.setChildrenCount(0);
    var changed = new SemanticExecutionEvidence.Collector(); changed.term("test:Different");
    checked.getMetadata().put(SemanticEvidenceBinding.DATA_KEY, Utils.Json.asString(changed.finish(scope)));
    assertFalse(SemanticExecutionEvidence.sameCheckedPayload(original, checked));
    checked.getMetadata().put(SemanticEvidenceBinding.DATA_KEY, Utils.Json.asString(current.finish(scope)));
    ((ConceptImpl)original.getObservable().getSemantics()).setType(new LinkedHashSet<>(
        List.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE)));
    ((ConceptImpl)checked.getObservable().getSemantics()).setType(new LinkedHashSet<>(
        List.of(SemanticType.OBSERVABLE,SemanticType.SUBJECT)));
    assertTrue(SemanticExecutionEvidence.sameCheckedPayload(original,checked),"Concept type is a Set, not an ordered list");
    ((ConceptImpl)checked.getObservable().getSemantics()).setType(Set.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));
    assertFalse(SemanticExecutionEvidence.sameCheckedPayload(original,checked),"Different type contents remain rejected");
  }

  Reasoner.Capabilities changed(String field) {
    var other = mock(Reasoner.Capabilities.class);
    when(other.getServiceId()).thenReturn(field.equals("id") ? "other-reasoner" : "reasoner");
    when(other.getKnowledgeRevision()).thenReturn(field.equals("generation") ? 2L : 1L);
    when(other.getSemanticContentFingerprint()).thenReturn(field.equals("base") ? "semantic-content-v2:" + "b".repeat(64) : BASE);
    when(other.getSemanticSessionId()).thenReturn(field.equals("session") ? "owner-two" : "owner-one");
    return other;
  }

  @Test void sourceVerificationCannotBeRebasedAcrossSwitchBeforeFirstFinishPin() {
    var source = new SemanticExecutionEvidence.Collector(); source.term("test:Native"); var proof = source.finish(scope);
    for (var field : List.of("id", "generation", "base", "session")) {
      clearInvocations(reasoner);
      var other = changed(field);
      when(reasoner.capabilities(scope)).thenReturn(caps, caps, caps, other);
      var composed = new SemanticExecutionEvidence.Collector(); composed.inherit(proof, scope);
      var result = composed.finish(scope);
      assertFalse(result.complete(), field); assertNull(result.witness(), field);
      verify(reasoner, times(1)).semanticDependencies(any(), eq(scope)); // Only verifies A; never mints B.
      when(reasoner.capabilities(scope)).thenReturn(caps);
    }
  }

  @Test void finishSecondCapabilityReadMustMatchAlreadyAdmittedSourceSnapshot() {
    var source = new SemanticExecutionEvidence.Collector(); source.term("test:Native"); var proof = source.finish(scope);
    for (var field : List.of("id", "generation", "base", "session")) {
      clearInvocations(reasoner);
      var other = changed(field);
      when(reasoner.capabilities(scope)).thenReturn(caps, caps, caps, caps, caps, other);
      var composed = new SemanticExecutionEvidence.Collector(); composed.inherit(proof, scope);
      var result = composed.finish(scope);
      assertFalse(result.complete(), field); assertNull(result.witness(), field);
      verify(reasoner, times(1)).semanticDependencies(any(), eq(scope));
      when(reasoner.capabilities(scope)).thenReturn(caps);
    }
  }

  @Test void incarnationChangeDuringProofCannotBeHiddenByRepeatedCounterAndBase() {
    when(reasoner.semanticDependencies(any(), eq(scope))).thenAnswer(call -> {
      when(caps.getSemanticSessionId()).thenReturn("owner-two"); return witness(call.getArgument(0));
    });
    var collector = new SemanticExecutionEvidence.Collector(); collector.term("test:Native");
    assertNull(collector.finish(scope).witness());
  }

  @Test void nativeReplayUsesDeclaredSupportButRejectsAForgedSupportResult() {
    var signature=new SemanticExecutionEvidence.Collector();signature.term("test:Native");
    var original=new ObservationImpl();original.setId(Observation.QUERY_ID);original.setObservable(observable("test:Native"));
    String declared="T1(1){tend=10,tstart=0,ttype=PHYSICAL}";
    original.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create(
        "T1(1){tend=10,tscope=10.0,tstart=0,ttype=PHYSICAL,tunit=MILLISECOND}"));
    original.getMetadata().put(org.integratedmodelling.klab.api.data.Metadata.IM_QUERY_GEOMETRY,declared);
    original.getMetadata().put(SemanticEvidenceBinding.DATA_KEY,Utils.Json.asString(signature.finish(scope)));
    var runtime=mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
    when(runtime.submit(any(),eq(scope))).thenAnswer(call->{
      Observation requested=call.getArgument(0);assertNotSame(original,requested);
      assertEquals(declared,requested.getGeometry().encode());
      return java.util.concurrent.CompletableFuture.completedFuture(Observation.forTransport(original));
    });
    var collector=new SemanticExecutionEvidence.Collector();collector.reference(original,scope);
    assertTrue(collector.finish(scope).complete());
    var forged=(ObservationImpl)Utils.Json.parseObject(Utils.Json.asString(original),Observation.class);
    forged.getMetadata().put(org.integratedmodelling.klab.api.data.Metadata.IM_QUERY_GEOMETRY,
        "T1(1){tend=5,tstart=0,ttype=PHYSICAL}");
    when(runtime.submit(any(),eq(scope))).thenAnswer(call->{
      var checked=(ObservationImpl)Observation.forTransport(forged);
      checked.setGeometry(call.getArgument(0,Observation.class).getGeometry());
      return java.util.concurrent.CompletableFuture.completedFuture(checked);
    });
    var rejected=new SemanticExecutionEvidence.Collector();rejected.reference(forged,scope);
    assertFalse(rejected.finish(scope).complete(),"Declared support cannot authorize a result with different actual geometry");
  }

  @Test void preparedInputContinuationStillRejectsSemanticIdentityDrift() {
    var transaction=mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Transaction.class);
    when(scope.getCurrentTransaction()).thenReturn(transaction);
    for(var field:List.of("unchanged","id","generation","base","session")) {
      when(reasoner.capabilities(scope)).thenReturn(caps);
      var prepared=SemanticExecutionEvidence.prepare(model("input","test:Native",null,null),List.of(),true,scope);
      if(!field.equals("unchanged")) {
        var other=changed(field);when(reasoner.capabilities(scope)).thenReturn(other);
      }
      var activity=org.integratedmodelling.klab.api.provenance.Activity.of(
          org.integratedmodelling.klab.api.provenance.Activity.Type.CLASSIFICATION);
      SemanticExecutionEvidence.recordPrepared(activity,prepared,null,List.of(),List.of(),scope);
      var proof=SemanticExecutionEvidence.decode(activity);
      assertEquals(field.equals("unchanged"),proof.complete(),field);
      if(!field.equals("unchanged"))assertNull(proof.witness(),field);
    }
  }
}
