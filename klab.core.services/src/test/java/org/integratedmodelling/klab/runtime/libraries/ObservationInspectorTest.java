package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

class ObservationInspectorTest {
  @Test
  @org.junit.jupiter.api.parallel.ResourceLock("submissionDiagnosticsProperty")
  void optionalInvalidViewDiagnosticReachesExistingWarningWithoutChangingVerdict() {
    var property = org.integratedmodelling.common.runtime.SubmissionDiagnostics.PROPERTY;
    var previous = System.getProperty(property);
    try {
      var scope = mock(ContextScope.class);
      when(scope.getId()).thenReturn("test-context");
      var view = Observation.empty(org.integratedmodelling.klab.api.services.runtime.Notification.error(
          "Resolution of private:secret failed: empty dataflow"));
      var warning = org.mockito.ArgumentCaptor.forClass(Object[].class);
      System.setProperty(property, "false");
      assertFalse(ObservationInspector.selectionCheck(scope, view, "test:Region"));
      verify(scope).warn(warning.capture());
      assertEquals("Selection inspector rejected: guard=G01; scope=test-context; viewId=-1", warning.getValue()[0]);
      clearInvocations(scope);
      System.setProperty(property, "true");
      assertFalse(ObservationInspector.selectionCheck(scope, view, "test:Region"));
      verify(scope).warn(warning.capture());
      var text = warning.getValue()[0].toString();
      assertTrue(text.contains("inspector.G01"));
      assertTrue(text.contains("RESOLUTION_EMPTY_DATAFLOW_OR_COMPILE_COMMIT"));
      assertFalse(text.contains("private:secret"));
      verify(scope, never()).getService(Reasoner.class);
      assertEquals(1, view.getNotifications().size());
    } finally {
      if (previous == null) System.clearProperty(property); else System.setProperty(property, previous);
    }
  }

  private static final String CONTENT = "semantic-content-v2:" + "a".repeat(64);
  private static final String OTHER_CONTENT = "semantic-content-v2:" + "b".repeat(64);
  @BeforeAll static void setup() { ServiceConfiguration.injectInstantiators(); }

  private static Geometry geometry(String text) { return Scale.create(ShapeImpl.create(text)); }

  private Fixture nativeFixture() {
    var f = new Fixture();
    when(f.cohort.getObservable()).thenReturn(f.member.getObservable());
    when(f.cohort.getUrn()).thenReturn("cohort:30");when(f.cohort.getGeometry()).thenReturn(Geometry.UNIVERSAL);
    when(f.graph.query(any(KnowledgeGraph.Query.class), eq(Cohort.class), eq(f.scope)))
        .thenReturn(List.of(f.cohort));
    when(f.graph.query(any(KnowledgeGraph.Query.class), eq(Observation.class), eq(f.scope))).thenAnswer(call -> {
      var query = call.getArgument(0, KnowledgeGraphQuery.class);
      if (query.getSource() != null && query.getSource().getId() == f.cohort.getId()) return List.copyOf(f.members);
      if (query.getTarget() != null && query.getTarget().getId() == f.cohort.getId()) return List.of(f.producer);
      return List.copyOf(f.assets.values());
    });
    return f;
  }

  @Test void nativeSnapshotsAcceptCompletedEmptyCohortsWithoutWeakeningMemberSnapshots() {
    var f = nativeFixture();f.members.clear();f.assets.remove(20L);
    var before = ObservationInspector.nativeSnapshot(f.scope);
    assertTrue(before.cohorts().get(30L).members().isEmpty());
    assertEquals(Set.of(10L), before.cohorts().get(30L).contributors());
    assertTrue(ObservationInspector.nativeUnchanged(f.scope, before));
    assertThrows(IllegalStateException.class, f::snapshot);
    var bookkeeping = Activity.of(Activity.Type.SUBMISSION);bookkeeping.setId(90);f.activities.add(bookkeeping);
    assertTrue(ObservationInspector.nativeUnchanged(f.scope, before));
    var work = Activity.of(Activity.Type.CLASSIFICATION);work.setId(91);f.activities.add(work);
    assertFalse(ObservationInspector.nativeUnchanged(f.scope, before));
  }

  @Test void nativeSnapshotsFreezeSemanticsAndDetectLeakedProducersOrMemberChanges() {
    var f = nativeFixture();var before = ObservationInspector.nativeSnapshot(f.scope);
    f.classify();
    assertEquals("test:Region", before.observations().get(20L).semantics());
    assertFalse(ObservationInspector.nativeUnchanged(f.scope, before));
    f = nativeFixture();before = ObservationInspector.nativeSnapshot(f.scope);
    var leaked = f.meaning("each test:Terrestrial test:Region", true, SemanticType.SUBJECT);
    f.observation(21, leaked, Geometry.UNIVERSAL);
    assertFalse(ObservationInspector.nativeUnchanged(f.scope, before));
    var frozen = before;
    assertThrows(UnsupportedOperationException.class, () -> frozen.observations().clear());
  }

  @Test void nativeSnapshotsRejectForeignContextsAndMissingCanonicalMembers() {
    var f = nativeFixture();var before = ObservationInspector.nativeSnapshot(f.scope);
    when(f.scope.getId()).thenReturn("another-context");
    assertThrows(IllegalArgumentException.class, () -> ObservationInspector.nativeUnchanged(f.scope, before));
    var missing = nativeFixture();missing.assets.remove(20L);
    assertThrows(IllegalStateException.class, () -> ObservationInspector.nativeSnapshot(missing.scope));
  }

  class SelectionFixture {
    final Fixture f = nativeFixture();
    final org.integratedmodelling.klab.api.services.RuntimeService runtime = mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    final Reasoner.Capabilities capabilities = mock(Reasoner.Capabilities.class);
    final Concept expected = f.meaning("each test:Terrestrial test:Region", true, SemanticType.SUBJECT);
    final ObservationImpl view = new ObservationImpl();
    final org.integratedmodelling.klab.api.provenance.impl.ActivityImpl completed = Activity.of(Activity.Type.INSTANTIATION);
    List<CollectiveSelectionView.Member> states;
    List<Long> selected = List.of(20L);

    SelectionFixture() {
      f.classify();
      when(f.reasoner.directTraits(any())).thenAnswer(call -> {
        var value = call.getArgument(0);
        return f.traits.getOrDefault(value instanceof Observable observable ? observable.getSemantics() : value, List.of());
      });
      f.traits.put(expected, List.of(f.category));
      when(f.reasoner.satisfiable(any())).thenReturn(true);
      when(f.reasoner.lexicalRoot(f.category)).thenReturn(f.family);
      when(f.reasoner.baseSubstantialType(any(), eq(f.scope))).thenReturn(f.region);
      when(f.reasoner.resolveObservable("each test:Region")).thenReturn(f.producer.getObservable());
      when(f.reasoner.capabilities(f.scope)).thenReturn(capabilities);
      when(capabilities.getServiceId()).thenReturn("reasoner");when(capabilities.getKnowledgeRevision()).thenReturn(7L);
      when(capabilities.getSemanticContentFingerprint()).thenReturn(CONTENT);
      when(f.reasoner.semanticDependencies(any(),same(f.scope))).thenAnswer(call -> {
        var request=call.getArgument(0,org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest.class);
        return new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness(
            "semantic-dependency-v1",org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.COMPLETE,
            capabilities.getServiceId(),capabilities.getKnowledgeRevision(),capabilities.getSemanticContentFingerprint(),request.inputHash(),List.of(),null);
      });
      when(f.scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
      when(runtime.getMembers(view, f.scope)).thenAnswer(call -> selected.stream().map(f.assets::get).toList());
      when(f.graph.getAsset(30L, f.scope, Cohort.class)).thenReturn(f.cohort);
      when(f.producer.getObservable().getSemantics().singular()).thenReturn(f.region);
      completed.setId(200);completed.setOutcome(Activity.Outcome.SUCCESS);
      completed.getMetadata().put("collectiveReasonerId", "reasoner");
      completed.getMetadata().put("collectiveKnowledgeRevision", 7L);
      completed.getMetadata().put("collectiveSemanticFingerprint", CONTENT);
      completed.getMetadata().put(Metadata.IM_OBSERVER_SELECTION_ID, 0L);
      completed.getMetadata().put(Metadata.IM_EXECUTION_GEOMETRY, Geometry.UNIVERSAL.encode());
      f.activities.add(completed);
      var expectedUrn = expected.getUrn();
      var observable = mock(Observable.class);when(observable.getUrn()).thenReturn(expectedUrn);
      when(observable.getSemantics()).thenReturn(expected);
      var builder = mock(Observable.Builder.class);when(observable.builder(f.scope)).thenReturn(builder);
      when(builder.without(anyCollection())).thenReturn(builder);when(builder.buildObservable()).thenReturn(f.producer.getObservable());
      var individual = mock(Observable.class);when(individual.getSemantics()).thenReturn(f.region);
      var nativeBuilder = mock(Observable.Builder.class);when(f.producer.getObservable().builder(f.scope)).thenReturn(nativeBuilder);
      when(nativeBuilder.collective(false)).thenReturn(nativeBuilder);when(nativeBuilder.buildObservable()).thenReturn(individual);
      when(f.reasoner.is(f.attributed, f.region)).thenReturn(true);
      view.setId(Observation.QUERY_ID);view.setObservable(observable);view.setGeometry(Geometry.UNIVERSAL);
      view.setResolvedCoverage(1);view.setChildrenCount(1);
      states = List.of(new CollectiveSelectionView.Member(20, f.member.getObservable().getUrn(), Geometry.UNIVERSAL.encode()));
      receipt();
    }

    void receipt() {
      var obligations=new java.util.TreeSet<String>(List.of(expected.getUrn(),"each test:Region",f.category.getUrn(),f.family.getUrn(),f.region.getUrn(),f.attributed.getUrn()));
      var request=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest("reasoner",7,CONTENT,List.copyOf(obligations));
      var evidence=new org.integratedmodelling.klab.api.knowledge.SemanticEvidenceBinding(
          "semantic-execution-evidence-v1",true,List.copyOf(obligations),f.reasoner.semanticDependencies(request,f.scope));
      completed.getMetadata().put(org.integratedmodelling.klab.api.knowledge.SemanticEvidenceBinding.DATA_KEY,Utils.Json.asString(evidence));
      var proof = new CollectiveSelectionView(3, expected.getUrn(), "each test:Region", List.of("test:Terrestrial"),
          List.of("test:Environment"), "reasoner", 7, 0, null, Geometry.forTransport(view.getGeometry()).encode(),
          30, List.of(10L), states, selected, f.scope.getId(), null, null, CONTENT, evidence);
      view.getMetadata().put(CollectiveSelectionView.DATA_KEY, Utils.Json.asString(proof));
      view.getMetadata().put(Metadata.IM_QUERY_COHORT_ID, 30L);
      view.getMetadata().put(Metadata.IM_QUERY_SOURCE_IDS, List.of(10L));
    }

    boolean check() { return ObservationInspector.selectionCheck(f.scope, view, expected.getUrn()); }
  }

  @Test void selectionChecksActualNativeStateAndEnumerationRatherThanOnlyTheCount() {
    var f = new SelectionFixture();
    assertTrue(f.check());
    when(f.runtime.getMembers(f.view, f.f.scope)).thenReturn(List.of());
    assertFalse(f.check());
    when(f.runtime.getMembers(f.view, f.f.scope)).thenReturn(List.of(f.f.member, f.f.member));
    assertFalse(f.check());
  }

  @Test void selectionAcceptsProvenEmptyNativeDiscoveryButRejectsMissingCompletion() {
    var f = new SelectionFixture();f.f.members.clear();f.f.assets.remove(20L);
    f.states = List.of();f.selected = List.of();f.view.setChildrenCount(0);f.receipt();
    assertTrue(f.check());
    f.f.activities.remove(f.completed);
    assertFalse(f.check());
  }

  @Test void selectionRejectsMembersOutsidePhysicalSupportEvenIfPortableCardinalityIsNonzero() {
    var f = new SelectionFixture();
    f.view.setGeometry(geometry("EPSG:3857 POLYGON ((0 0,0 10,10 10,10 0,0 0))"));
    f.f.member.setGeometry(geometry("EPSG:3857 POINT (20 20)"));
    f.states = List.of(new CollectiveSelectionView.Member(20, f.f.member.getObservable().getUrn(), f.f.member.getGeometry().encode()));
    f.receipt();
    assertFalse(f.check());
  }

  @Test void selectionRejectsUnknownFamilyKnowledgeAndContradictoryReceipts() {
    var f = new SelectionFixture();
    f.f.traits.put(f.f.attributed, List.of());
    assertFalse(f.check());
    f = new SelectionFixture();f.selected = List.of();f.view.setChildrenCount(0);f.receipt();
    assertFalse(f.check());
    f = new SelectionFixture();f.states = List.of(f.states.getFirst(), f.states.getFirst());f.receipt();
    assertFalse(f.check());
  }

  @Test void selectionRejectsWrongObserverContentOrQualifiedProducer() {
    var f = new SelectionFixture();
    f.completed.getMetadata().put(Metadata.IM_OBSERVER_SELECTION_ID, 99L);
    assertFalse(f.check());
    f = new SelectionFixture();when(f.capabilities.getSemanticContentFingerprint()).thenReturn(OTHER_CONTENT);
    assertFalse(f.check());
    f = new SelectionFixture();f.f.observation(21, f.expected, Geometry.UNIVERSAL);
    assertFalse(f.check());
  }

  private static CollectiveSelectionView receiptWith(CollectiveSelectionView proof, int version,
      long revision, String fingerprint) {
    return new CollectiveSelectionView(version, proof.requested(), proof.nativeMembers(), proof.predicates(),
        proof.families(), proof.reasonerId(), revision, proof.observerId(), proof.observerUrn(), proof.geometry(),
        proof.cohortId(), proof.sourceIds(), proof.members(), proof.selectedIds(), proof.scopeId(), proof.context(),
        proof.observerClass(), fingerprint, version==3?proof.semanticEvidence():null);
  }

  @Test void selectionContentRemainsValidAcrossDifferentNumericGeneration() {
    var f = new SelectionFixture();
    when(f.capabilities.getKnowledgeRevision()).thenReturn(1L);
    f.completed.getMetadata().put("collectiveKnowledgeRevision", 99L);
    assertTrue(f.check(), "Matching semantic content preserves durable source evidence across restarts");
    f.completed.getMetadata().put("collectiveSemanticFingerprint", OTHER_CONTENT);
    assertFalse(f.check(), "A native completion must independently carry the matching content witness");
  }

  @Test void selectionLegacyAndUncertifiedReceiptsRemainReadableButCannotCertifyCompleteness() {
    var f = new SelectionFixture();
    var encoded = f.view.getMetadata().get(CollectiveSelectionView.DATA_KEY).toString();
    var proof = Utils.Json.parseObject(encoded, CollectiveSelectionView.class);
    for (var version : List.of(1, 2)) {
      var legacy = Utils.Json.asString(receiptWith(proof, version, 7, null));
      f.view.getMetadata().put(CollectiveSelectionView.DATA_KEY, legacy);
      assertNotNull(Utils.Json.parseObject(legacy, CollectiveSelectionView.class));
      assertFalse(f.check());
      assertEquals(legacy, f.view.getMetadata().get(CollectiveSelectionView.DATA_KEY), "Inspection never stamps legacy evidence");
    }
    f.view.getMetadata().put(CollectiveSelectionView.DATA_KEY, encoded);
    when(f.capabilities.getSemanticContentFingerprint()).thenReturn(null);
    assertFalse(f.check(), "An unbounded mutable provider does not certify durable content");
  }

  @Test void receiptRejectsMalformedOrUnknownFingerprintVersions() {
    var f = new SelectionFixture();
    var proof = Utils.Json.parseObject(f.view.getMetadata().get(CollectiveSelectionView.DATA_KEY).toString(), CollectiveSelectionView.class);
    for (var malformed : List.of("semantic-content-v2:abc", "semantic-content-v3:" + "a".repeat(64),
        "semantic-content-v2:" + "A".repeat(64)))
      assertThrows(IllegalArgumentException.class, () -> receiptWith(proof, 2, 7, malformed));
    assertThrows(IllegalArgumentException.class, () -> receiptWith(proof, 4, 7, CONTENT));
    assertThrows(IllegalArgumentException.class, () -> receiptWith(proof, 1, 7, CONTENT));
  }

  @Test void selectionRejectsMembersOutsideTheRetainedNativeSemanticDomain() {
    var f = new SelectionFixture();when(f.f.reasoner.is(f.f.attributed, f.f.region)).thenReturn(false);
    assertFalse(f.check());
  }

  @Test void selectionRejectsWrongExpectedSemanticsInvalidCountAndNonfiniteCoverage() {
    var f = new SelectionFixture();
    assertFalse(ObservationInspector.selectionCheck(f.f.scope, f.view, "test:Region"));
    f.view.setChildrenCount(0);assertFalse(f.check());
    f.view.setChildrenCount(1);f.view.setResolvedCoverage(Double.NaN);assertFalse(f.check());
    f.view.setResolvedCoverage(Double.POSITIVE_INFINITY);assertFalse(f.check());
    f = new SelectionFixture();when(f.f.scope.getId()).thenReturn("foreign-context");assertFalse(f.check());
    f = new SelectionFixture();when(f.f.scope.getContextObservation()).thenReturn(f.f.member);assertFalse(f.check());
  }

  @Test void capturedSelectionInputsUseCanonicalConsumerMetadataAndActualEnumeration() {
    var f = new SelectionFixture();
    var consumer = f.f.observation(40, f.f.region, Geometry.UNIVERSAL);
    consumer.getMetadata().put(CollectiveSelectionView.INPUTS_KEY,
        Utils.Json.asString(Map.of("selected", f.view.getMetadata().get(CollectiveSelectionView.DATA_KEY))));
    var expectedUrn = f.expected.getUrn();var observable = f.view.getObservable();
    when(f.f.reasoner.resolveObservable(expectedUrn)).thenReturn(observable);
    when(f.runtime.getMembers(any(Observation.class), eq(f.f.scope))).thenReturn(List.of(f.f.member));
    var input = ObservationInspector.selectionInput(f.f.scope, consumer, "selected");
    assertEquals(Observation.QUERY_ID, input.getId());assertEquals(1, input.getChildrenCount());
    assertTrue(ObservationInspector.selectionCheck(f.f.scope, input, expectedUrn));
    assertThrows(IllegalArgumentException.class, () -> ObservationInspector.selectionInput(f.f.scope, consumer, "other_model_alias"));
    when(f.runtime.getMembers(any(Observation.class), eq(f.f.scope))).thenReturn(List.of(f.f.member, f.f.member));
    assertThrows(IllegalStateException.class, () -> ObservationInspector.selectionInput(f.f.scope, consumer, "selected"));
  }

  @Test void numericCellChecksRejectMissingNonfiniteAndInvalidTolerance() {
    assertTrue(ObservationInspector.numericCell("12.0", 12, 0));
    assertTrue(ObservationInspector.numericCell("12.00001", 12, 0.0001));
    assertFalse(ObservationInspector.numericCell("13", 12, 0));
    for (var missing : Arrays.asList(null, "", "NaN", "Infinity", "-Infinity", "missing"))
      assertFalse(ObservationInspector.numericCell(missing, 12, 0));
    for (double tolerance : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
      assertThrows(IllegalArgumentException.class, () -> ObservationInspector.numericCell("12", 12, tolerance));
    assertThrows(IllegalArgumentException.class, () -> ObservationInspector.numericCell("12", Double.NaN, 0));
  }

  private SelectionFixture replayFixture() {
    var f = new SelectionFixture();
    var concept = new org.integratedmodelling.common.knowledge.ConceptImpl();
    concept.setUrn(f.expected.getUrn());concept.setName("Region");
    concept.setType(EnumSet.of(SemanticType.SUBJECT));concept.setCollective(true);
    var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
    observable.setSemantics(concept);observable.setUrn(concept.getUrn());f.view.setObservable(observable);
    when(f.runtime.submit(any(Observation.class), eq(f.f.scope))).thenAnswer(call ->
        java.util.concurrent.CompletableFuture.completedFuture(call.getArgument(0, Observation.class)));
    when(f.runtime.getMembers(any(Observation.class), eq(f.f.scope))).thenAnswer(call -> f.selected.stream().map(f.f.assets::get).toList());
    return f;
  }

  @Test void selectionReplayUsesActualObservationJsonAndNormalQueryWithoutNativeWork() {
    var f = replayFixture();
    assertTrue(ObservationInspector.selectionReplay(f.f.scope, f.view));
    var request = org.mockito.ArgumentCaptor.forClass(Observation.class);
    verify(f.runtime).submit(request.capture(), eq(f.f.scope));
    assertNotSame(f.view, request.getValue());
    assertEquals(Observation.QUERY_ID, request.getValue().getId());
    assertEquals(f.view.getObservable().getSemantics(), request.getValue().getObservable().getSemantics());
    assertEquals(f.view.getMetadata().get(CollectiveSelectionView.DATA_KEY), request.getValue().getMetadata().get(CollectiveSelectionView.DATA_KEY));
    assertTrue(ObservationInspector.selectionReplay(f.f.scope, f.view));
  }

  @Test void selectionReplayAcceptsIdenticalContentRestartAndHistoricalNumericAudit() {
    var f = replayFixture();
    when(f.capabilities.getKnowledgeRevision()).thenReturn(1L);
    when(f.runtime.submit(any(Observation.class), eq(f.f.scope))).thenAnswer(call -> {
      var result = call.getArgument(0, Observation.class);
      var encoded = result.getMetadata().get(CollectiveSelectionView.DATA_KEY).toString();
      var proof = Utils.Json.parseObject(encoded, CollectiveSelectionView.class);
      result.getMetadata().put(CollectiveSelectionView.DATA_KEY, Utils.Json.asString(receiptWith(proof, 3, 1, CONTENT)));
      return java.util.concurrent.CompletableFuture.completedFuture(result);
    });
    var before = f.view.getMetadata().get(CollectiveSelectionView.DATA_KEY);
    assertTrue(ObservationInspector.selectionReplay(f.f.scope, f.view));
    assertEquals(before, f.view.getMetadata().get(CollectiveSelectionView.DATA_KEY), "Original persisted audit bytes stay unchanged");
  }

  @Test void selectionReplayRejectsLegacyBeforeQueryWithoutChangingItsObservation() {
    var f = replayFixture();
    var proof = Utils.Json.parseObject(f.view.getMetadata().get(CollectiveSelectionView.DATA_KEY).toString(), CollectiveSelectionView.class);
    var legacy = Utils.Json.asString(receiptWith(proof, 1, 7, null));
    f.view.getMetadata().put(CollectiveSelectionView.DATA_KEY, legacy);
    assertFalse(ObservationInspector.selectionReplay(f.f.scope, f.view));
    verify(f.runtime, never()).submit(any(), any());
    assertEquals(legacy, f.view.getMetadata().get(CollectiveSelectionView.DATA_KEY));
    assertTrue(f.f.assets.containsKey(20L), "Rejecting the receipt leaves its native observation intact");
  }

  @Test void selectionReplayRejectsStaleScopeObserverAndChangedContentBeforeQuery() {
    var f = replayFixture();when(f.f.scope.getId()).thenReturn("wrong-context");
    assertFalse(ObservationInspector.selectionReplay(f.f.scope, f.view));verify(f.runtime, never()).submit(any(), any());
    f = replayFixture();when(f.capabilities.getSemanticContentFingerprint()).thenReturn(OTHER_CONTENT);
    assertFalse(ObservationInspector.selectionReplay(f.f.scope, f.view));verify(f.runtime, never()).submit(any(), any());
    f = replayFixture();when(f.f.scope.getObserver()).thenReturn(f.f.member);
    assertFalse(ObservationInspector.selectionReplay(f.f.scope, f.view));verify(f.runtime, never()).submit(any(), any());
  }

  @Test void selectionReplayRejectsQueryFailureDuplicateMembersAndNativeMutation() {
    var f = replayFixture();
    when(f.runtime.submit(any(), eq(f.f.scope))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
    assertFalse(ObservationInspector.selectionReplay(f.f.scope, f.view));
    f = replayFixture();when(f.runtime.getMembers(any(), eq(f.f.scope))).thenReturn(List.of(f.f.member, f.f.member));
    assertFalse(ObservationInspector.selectionReplay(f.f.scope, f.view));
    f = replayFixture();var changed = f;
    when(f.runtime.submit(any(), eq(f.f.scope))).thenAnswer(call -> {
      var work = Activity.of(Activity.Type.INSTANTIATION);work.setId(999);changed.f.activities.add(work);
      return java.util.concurrent.CompletableFuture.completedFuture(call.getArgument(0, Observation.class));
    });
    assertFalse(ObservationInspector.selectionReplay(f.f.scope, f.view));
  }

  class Fixture {
    final ContextScope scope = mock(ContextScope.class);
    final DigitalTwin twin = mock(DigitalTwin.class);
    final KnowledgeGraph graph = mock(KnowledgeGraph.class);
    final Reasoner reasoner = mock(Reasoner.class);
    final Cohort cohort = mock(Cohort.class);
    final Map<String, Concept> concepts = new HashMap<>();
    final Map<Concept, List<Concept>> traits = new HashMap<>();
    final Map<Long, Observation> assets = new HashMap<>();
    final List<Observation> members = new ArrayList<>();
    final List<Activity> activities = new ArrayList<>();
    final Map<Long, List<KnowledgeGraph.Link>> incoming = new HashMap<>(), outgoing = new HashMap<>();
    final Concept region = meaning("test:Region", false, SemanticType.SUBJECT);
    final Concept family = meaning("test:Environment", false, SemanticType.REALM, SemanticType.PREDICATE, SemanticType.ABSTRACT);
    final Concept category = meaning("test:Terrestrial", false, SemanticType.REALM, SemanticType.PREDICATE);
    final Concept attributed = meaning("test:Terrestrial test:Region", false, SemanticType.SUBJECT);
    ObservationImpl producer, member;

    Fixture() {
      when(scope.getId()).thenReturn("context"); when(scope.getDigitalTwin()).thenReturn(twin);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner); when(twin.getKnowledgeGraph()).thenReturn(graph);
      when(cohort.getId()).thenReturn(30L);
      when(reasoner.resolveConcept(anyString())).thenAnswer(call -> concepts.get(call.getArgument(0)));
      when(reasoner.directTraits(any())).thenAnswer(call -> traits.getOrDefault(call.getArgument(0), List.of()));
      when(reasoner.directRoles(any())).thenReturn(List.of());
      when(reasoner.is(category, family)).thenReturn(true);
      traits.put(attributed, List.of(category));
      var collective = meaning("each test:Region", true, SemanticType.SUBJECT);
      when(collective.singular()).thenReturn(region);
      producer = observation(10, collective, Geometry.UNIVERSAL);
      member = observation(20, region, Geometry.UNIVERSAL); members.add(member);
      when(graph.query(any(Class.class), eq(scope))).thenAnswer(call ->
          new KnowledgeGraphQuery<>(KnowledgeGraphQuery.AssetType.classify(call.getArgument(0, Class.class))));
      when(graph.query(any(KnowledgeGraph.Query.class), eq(Cohort.class), eq(scope))).thenAnswer(call -> {
        var query = call.getArgument(0, KnowledgeGraphQuery.class);
        return query.getSource() != null && query.getSource().getId() == producer.getId()
            && query.getRelationship() == GraphModel.Relationship.CONTRIBUTED_TO ? List.of(cohort) : List.of();
      });
      when(graph.query(any(KnowledgeGraph.Query.class), eq(Observation.class), eq(scope))).thenAnswer(call -> {
        var query = call.getArgument(0, KnowledgeGraphQuery.class);
        return query.getSource() != null && query.getSource().getId() == cohort.getId()
            && query.getRelationship() == GraphModel.Relationship.HAS_MEMBER ? List.copyOf(members) : List.of();
      });
      when(graph.query(any(KnowledgeGraph.Query.class), eq(Activity.class), eq(scope)))
          .thenAnswer(call -> List.copyOf(activities));
      when(graph.getAsset(anyLong(), eq(scope), eq(Observation.class))).thenAnswer(call -> assets.get(call.getArgument(0)));
      when(graph.getLinks(any(), any(), eq(scope), any(GraphModel.Relationship[].class))).thenAnswer(call -> {
        var asset = call.getArgument(0, RuntimeAsset.class);
        return (call.getArgument(1) == GraphModel.Relationship.Direction.INCOMING ? incoming : outgoing)
            .getOrDefault(asset.getId(), List.of());
      });
    }

    Concept meaning(String urn, boolean collective, SemanticType... types) {
      var concept = mock(Concept.class); concepts.put(urn, concept);
      var set = EnumSet.noneOf(SemanticType.class); set.addAll(List.of(types));
      when(concept.getUrn()).thenReturn(urn); when(concept.getType()).thenReturn(set);
      when(concept.isCollective()).thenReturn(collective); when(concept.singular()).thenReturn(concept);
      when(concept.is(any(SemanticType.class))).thenAnswer(call -> set.contains(call.getArgument(0)));
      return concept;
    }

    ObservationImpl observation(long id, Concept semantics, Geometry geometry) {
      var urn = semantics.getUrn();
      var observable = mock(Observable.class); when(observable.getSemantics()).thenReturn(semantics);
      when(observable.getUrn()).thenReturn(urn);
      var result = new ObservationImpl(); result.setId(id); result.setUrn("instance:" + id);
      result.setObservable(observable); result.setGeometry(geometry); assets.put(id, result); return result;
    }

    Activity audit(long id, String input, String origin) {
      var activity = Activity.of(Activity.Type.CLASSIFICATION); activity.setId(id); activity.setOutcome(Activity.Outcome.SUCCESS);
      activity.getMetadata().put(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS, Map.of("context", "context", "memberResults", List.of(
          Map.of("memberIdAtInvocation", 20, "inputObservable", input, "requestedPredicate", "test:Environment",
              "concretePredicates", List.of("test:Terrestrial"), "origin", origin))));
      activities.add(activity); return activity;
    }

    KnowledgeGraph.Link edge(RuntimeAsset source, RuntimeAsset target, GraphModel.Relationship relationship, Map<String, Object> properties) {
      var edge = mock(KnowledgeGraph.Link.class); when(edge.source()).thenReturn(source); when(edge.target()).thenReturn(target);
      when(edge.type()).thenReturn(relationship); when(edge.properties()).thenReturn(Parameters.create(properties)); return edge;
    }

    void classify() {
      var urn = attributed.getUrn();
      var after = mock(Observable.class); when(after.getSemantics()).thenReturn(attributed); when(after.getUrn()).thenReturn(urn);
      member.setObservable(after);
      var activity = audit(100, "test:Region", "CLASSIFIER");
      incoming.put(20L, List.of(edge(activity, member, GraphModel.Relationship.CLASSIFIED,
          Map.of("before", "test:Region", "after", "test:Terrestrial test:Region",
              "abstractPredicate", "test:Environment", "predicate", "test:Terrestrial"))));
    }

    ObservationInspector.MemberSnapshot snapshot() { return ObservationInspector.snapshot(scope, producer); }
  }

  @Test void classificationPreservesImmutableBeforeSnapshotAndRequiresTypedMutationEvidence() {
    var f = new Fixture(); var before = f.snapshot(); f.classify();
    assertEquals("test:Region", before.members().get(20L).semantics());
    assertTrue(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
    f.incoming.clear();
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
  }

  private void concreteAudit(Fixture f) {
    when(f.reasoner.lexicalRoot(f.category)).thenReturn(f.family);
    f.activities.getFirst().getMetadata().put(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS,
        Map.of("context", "context", "memberResults", List.of(Map.of(
            "memberIdAtInvocation", 20L, "inputObservable", "test:Region",
            "requestedPredicate", "test:Terrestrial", "concretePredicates", List.of("test:Terrestrial"),
            "origin", "CLASSIFIER"))));
    f.incoming.put(20L, List.of(f.edge(f.activities.getFirst(), f.member, GraphModel.Relationship.CLASSIFIED,
        Map.of("before", "test:Region", "after", "test:Terrestrial test:Region",
            "abstractPredicate", "test:Terrestrial", "predicate", "test:Terrestrial"))));
  }

  @Test void concreteClassificationChecksRequestedPredicateSeparatelyFromFamily() {
    var f = new Fixture(); var before = f.snapshot(); f.classify(); concreteAudit(f);
    assertTrue(ObservationInspector.classificationCheckFor(f.scope, before, "test:Terrestrial", "test:Environment"));
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"),
        "Legacy family-request audit checking must remain exact");
  }

  @Test void concreteClassificationRejectsWrongRequestedSiblingAndWrongFamily() {
    var f = new Fixture(); var before = f.snapshot(); f.classify(); concreteAudit(f);
    var sibling = f.meaning("test:Aquatic", false, SemanticType.REALM, SemanticType.PREDICATE);
    when(f.reasoner.lexicalRoot(sibling)).thenReturn(f.family);
    assertFalse(ObservationInspector.classificationCheckFor(f.scope, before, "test:Aquatic", "test:Environment"));
    when(f.reasoner.lexicalRoot(f.category)).thenReturn(f.category);
    assertThrows(IllegalArgumentException.class, () -> ObservationInspector.classificationCheckFor(
        f.scope, before, "test:Terrestrial", "test:Environment"));
  }

  @Test void concreteClassificationStillRequiresOriginalIdsAndCommittedAttribution() {
    var f = new Fixture(); var before = f.snapshot(); f.classify(); concreteAudit(f);
    f.members.set(0, f.observation(21, f.attributed, Geometry.UNIVERSAL));
    assertFalse(ObservationInspector.classificationCheckFor(f.scope, before, "test:Terrestrial", "test:Environment"));
    f.members.set(0, f.member); f.activities.clear();
    assertFalse(ObservationInspector.classificationCheckFor(f.scope, before, "test:Terrestrial", "test:Environment"));
  }

  @Test void concreteClassificationRejectsFamilyOnlyEdgeAndWrongSiblingResult() {
    var f = new Fixture(); var before = f.snapshot(); f.classify(); concreteAudit(f);
    f.incoming.put(20L, List.of(f.edge(f.activities.getFirst(), f.member, GraphModel.Relationship.CLASSIFIED,
        Map.of("before", "test:Region", "after", "test:Terrestrial test:Region",
            "abstractPredicate", "test:Environment", "predicate", "test:Terrestrial"))));
    assertFalse(ObservationInspector.classificationCheckFor(f.scope, before, "test:Terrestrial", "test:Environment"));

    var sibling = f.meaning("test:Aquatic", false, SemanticType.REALM, SemanticType.PREDICATE);
    var wrongMeaning = f.meaning("test:Aquatic test:Region", false, SemanticType.SUBJECT);
    when(f.reasoner.is(sibling, f.family)).thenReturn(true);
    f.traits.put(wrongMeaning, List.of(sibling));
    var wrong = mock(Observable.class);when(wrong.getSemantics()).thenReturn(wrongMeaning);when(wrong.getUrn()).thenReturn("test:Aquatic test:Region");
    f.member.setObservable(wrong);
    f.activities.getFirst().getMetadata().put(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS,
        Map.of("context", "context", "memberResults", List.of(Map.of(
            "memberIdAtInvocation", 20L, "inputObservable", "test:Region", "requestedPredicate", "test:Terrestrial",
            "concretePredicates", List.of("test:Aquatic"), "origin", "CLASSIFIER"))));
    f.incoming.put(20L, List.of(f.edge(f.activities.getFirst(), f.member, GraphModel.Relationship.CLASSIFIED,
        Map.of("before", "test:Region", "after", "test:Aquatic test:Region",
            "abstractPredicate", "test:Terrestrial", "predicate", "test:Aquatic"))));
    assertFalse(ObservationInspector.classificationCheckFor(f.scope, before, "test:Terrestrial", "test:Environment"),
        "A matching request audit cannot certify a newly produced sibling value");
  }

  @Test void classificationRejectsDuplicateAuditResultsAndEdgesForAnUnauditedValue() {
    var f = new Fixture(); var before = f.snapshot(); f.classify();
    f.activities.getFirst().getMetadata().put(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS,
        Map.of("context", "context", "memberResults", List.of(Map.of(
            "memberIdAtInvocation", 20L, "inputObservable", "test:Region", "requestedPredicate", "test:Environment",
            "concretePredicates", List.of("test:Terrestrial", "test:Terrestrial"), "origin", "CLASSIFIER"))));
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));

    var sibling = f.meaning("test:Aquatic", false, SemanticType.REALM, SemanticType.PREDICATE);
    when(f.reasoner.is(sibling, f.family)).thenReturn(true);
    var both = f.meaning("test:Terrestrial test:Aquatic test:Region", false, SemanticType.SUBJECT);
    f.traits.put(both, List.of(f.category, sibling));
    var afterUrn = both.getUrn();
    var after = mock(Observable.class);when(after.getSemantics()).thenReturn(both);when(after.getUrn()).thenReturn(afterUrn);
    f.member.setObservable(after);
    f.activities.getFirst().getMetadata().put(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS,
        Map.of("context", "context", "memberResults", List.of(Map.of(
            "memberIdAtInvocation", 20L, "inputObservable", "test:Region", "requestedPredicate", "test:Environment",
            "concretePredicates", List.of("test:Terrestrial"), "origin", "CLASSIFIER"))));
    f.incoming.put(20L, List.of(f.edge(f.activities.getFirst(), f.member, GraphModel.Relationship.CLASSIFIED,
        Map.of("before", "test:Region", "after", both.getUrn(), "abstractPredicate", "test:Environment", "predicate", "test:Aquatic"))));
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"),
        "A family-compatible edge cannot certify a result omitted from that member's audit row");
  }

  @Test void missingAuditOrWrongOriginCannotPassClassification() {
    var f = new Fixture(); var before = f.snapshot(); f.classify(); f.activities.clear();
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
    f.audit(101, "test:Region", "EXISTING");
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
  }

  @Test void fractionalAuditMemberIdAndDifferentActivityAttributionCannotPass() {
    var f = new Fixture(); var before = f.snapshot(); f.classify();
    var original = f.activities.removeFirst();
    var other = f.audit(101, "test:Region", "CLASSIFIER");
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"),
        "A valid edge from a different activity must not certify this audit");
    f.activities.clear(); f.activities.add(original);
    original.getMetadata().put(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS, Map.of("context", "context", "memberResults", List.of(
        Map.of("memberIdAtInvocation", 20.5, "inputObservable", "test:Region", "requestedPredicate", "test:Environment",
            "concretePredicates", List.of("test:Terrestrial"), "origin", "CLASSIFIER"))));
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
  }

  @Test void replacementIdentityOrChangedGeometryCannotMasqueradeAsClassification() {
    var f = new Fixture(); var before = f.snapshot(); f.classify();
    f.members.set(0, f.observation(21, f.attributed, Geometry.UNIVERSAL));
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
    f.members.set(0, f.member); f.member.setGeometry(geometry("EPSG:4326 POINT(0 0)"));
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
  }

  @Test void noopNeedsFreshSuccessfulExistingAuditAndUnchangedState() {
    var f = new Fixture(); f.classify(); var before = f.snapshot();
    assertFalse(ObservationInspector.classificationNoop(f.scope, before, "test:Environment"));
    f.audit(101, "test:Terrestrial test:Region", "EXISTING");
    assertTrue(ObservationInspector.classificationNoop(f.scope, before, "test:Environment"));
    f.audit(102, "test:Terrestrial test:Region", "EXISTING");
    assertFalse(ObservationInspector.classificationNoop(f.scope, before, "test:Environment"));
  }

  @Test void concreteNoopSeparatesRequestedPredicateFromItsExistingFamilyValue() {
    var f = new Fixture(); f.classify(); var before = f.snapshot();
    var requested = f.meaning("test:Aquatic", false, SemanticType.REALM, SemanticType.PREDICATE);
    when(f.reasoner.lexicalRoot(requested)).thenReturn(f.family);
    var audit = f.audit(101, "test:Terrestrial test:Region", "EXISTING");
    audit.getMetadata().put(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS, Map.of("context", "context", "memberResults", List.of(
        Map.of("memberIdAtInvocation", 20, "inputObservable", "test:Terrestrial test:Region", "requestedPredicate", "test:Aquatic",
            "concretePredicates", List.of("test:Terrestrial"), "origin", "EXISTING"))));
    assertTrue(ObservationInspector.classificationNoopFor(f.scope, before, "test:Aquatic", "test:Environment"));
    assertFalse(ObservationInspector.classificationNoop(f.scope, before, "test:Environment"));
    when(f.reasoner.lexicalRoot(requested)).thenReturn(f.category);
    assertThrows(IllegalArgumentException.class, () -> ObservationInspector.classificationNoopFor(
        f.scope, before, "test:Aquatic", "test:Environment"));
  }

  @Test void previouslyClassifiedFixtureCannotProveMutationAndWrongContextCannotReuseSnapshot() {
    var f = new Fixture(); f.classify(); var before = f.snapshot(); f.audit(101, "test:Terrestrial test:Region", "EXISTING");
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
    when(f.scope.getId()).thenReturn("another-context");
    assertThrows(IllegalArgumentException.class, () -> ObservationInspector.classificationNoop(f.scope, before, "test:Environment"));
  }

  @Test void emptyOrDuplicateMembersCannotProduceSnapshot() {
    var f = new Fixture(); f.members.clear();
    assertThrows(IllegalStateException.class, f::snapshot);
    f.members.add(f.member); f.members.add(f.member);
    assertThrows(IllegalStateException.class, f::snapshot);
  }

  private Fixture relationships() {
    var f = new Fixture();
    var relation = f.meaning("test:Link", false, SemanticType.RELATIONSHIP, SemanticType.FUNCTIONAL, SemanticType.UNIDIRECTIONAL);
    var collective = f.meaning("each test:Link", true, SemanticType.RELATIONSHIP, SemanticType.FUNCTIONAL, SemanticType.UNIDIRECTIONAL);
    when(collective.singular()).thenReturn(relation);
    var junction = f.meaning("test:Junction", false, SemanticType.SUBJECT);
    when(f.reasoner.relationshipSource(relation)).thenReturn(junction); when(f.reasoner.relationshipTarget(relation)).thenReturn(junction);
    f.producer = f.observation(10, collective, Geometry.UNIVERSAL);
    f.member = f.observation(20, relation, geometry("EPSG:4326 LINESTRING(0 0, 1 1)"));
    f.members.clear(); f.members.add(f.member);
    var left = f.observation(501, junction, geometry("EPSG:4326 POINT(0 0)"));
    var right = f.observation(502, junction, geometry("EPSG:4326 POINT(1 1)"));
    f.outgoing.put(20L, List.of(f.edge(f.member, left, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, Map.of()),
        f.edge(f.member, right, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET, Map.of())));
    return f;
  }

  @Test void directedRelationshipRequiresCommittedOrderedEndpointsWithConnectingGeometry() {
    var f = relationships();
    assertTrue(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.member.setGeometry(geometry("EPSG:4326 LINESTRING(0 0, 2 0)"));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.member.setGeometry(geometry("EPSG:4326 LINESTRING(0 0, 1 1)"));
    ((ObservationImpl)f.assets.get(501L)).setGeometry(geometry("EPSG:4326 LINESTRING(0 0, 0.2 0.2)"));
    assertThrows(UnsupportedOperationException.class,
        () -> ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
  }

  @Test void polygonsInteriorEndpointsAndExtraVerticesAreNotPointConnections() {
    for (String shape : List.of("POLYGON((-1 -1, 2 -1, 2 2, -1 2, -1 -1))",
        "LINESTRING(-1 -1, 2 2)", "LINESTRING(0 0, 0.5 0.5, 1 1)")) {
      var f = relationships(); f.member.setGeometry(geometry("EPSG:4326 " + shape));
      assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"), shape);
    }
  }

  @Test void canonicalWkbMayReverseSupportWithoutChangingDirectedParticipantRoles() {
    var f = relationships();
    // The actual shared shape encoder canonicalizes coordinate order. Reproduce a directed
    // high-coordinate source -> low-coordinate target before and after transport/persistence.
    var raw = ShapeImpl.create("EPSG:4326 LINESTRING(1 1, 0 0)");
    var decoded = ShapeImpl.create(raw.asWKB());
    var before = (org.locationtech.jts.geom.LineString) raw.getJTSGeometry();
    var after = (org.locationtech.jts.geom.LineString) decoded.getJTSGeometry();
    assertTrue(before.getCoordinateN(0).equals2D(after.getCoordinateN(1)));
    assertTrue(before.getCoordinateN(1).equals2D(after.getCoordinateN(0)));
    f.outgoing.put(20L, List.of(f.edge(f.member, f.assets.get(502L), GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, Map.of()),
        f.edge(f.member, f.assets.get(501L), GraphModel.Relationship.HAS_RELATIONSHIP_TARGET, Map.of())));
    f.member.setGeometry(Scale.create(raw));
    assertTrue(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.member.setGeometry(Scale.create(decoded));
    assertTrue(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
  }

  @Test void directedEndpointSemanticRolesRemainStrictWhenSupportHasNoOrientation() {
    var f = relationships();
    var other = f.meaning("test:OtherEndpoint", false, SemanticType.SUBJECT);
    var relation = f.member.getObservable().getSemantics();
    when(f.reasoner.relationshipTarget(relation)).thenReturn(other);
    var target = f.observation(502, other, geometry("EPSG:4326 POINT(1 1)"));
    f.outgoing.put(20L, List.of(f.edge(f.member, f.assets.get(501L), GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, Map.of()),
        f.edge(f.member, target, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET, Map.of())));
    assertTrue(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.outgoing.put(20L, List.of(f.edge(f.member, target, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, Map.of()),
        f.edge(f.member, f.assets.get(501L), GraphModel.Relationship.HAS_RELATIONSHIP_TARGET, Map.of())));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
  }

  @Test void heterogeneousBondsMatchEndpointMeaningsWithoutIdOrdering() {
    var f = relationships();
    var bond = f.meaning("test:Bond", false, SemanticType.RELATIONSHIP, SemanticType.STRUCTURAL, SemanticType.BIDIRECTIONAL);
    var collective = f.meaning("each test:Bond", true, SemanticType.RELATIONSHIP, SemanticType.STRUCTURAL, SemanticType.BIDIRECTIONAL);
    when(collective.singular()).thenReturn(bond);
    var other = f.meaning("test:OtherEndpoint", false, SemanticType.SUBJECT);
    var junction = f.concepts.get("test:Junction");
    when(f.reasoner.relationshipSource(bond)).thenReturn(other); when(f.reasoner.relationshipTarget(bond)).thenReturn(junction);
    f.producer = f.observation(10, collective, Geometry.UNIVERSAL);
    f.member = f.observation(20, bond, geometry("EPSG:4326 LINESTRING(0 0, 1 1)"));
    f.members.clear(); f.members.add(f.member);
    var source = f.observation(502, other, geometry("EPSG:4326 POINT(1 1)"));
    var target = f.observation(501, junction, geometry("EPSG:4326 POINT(0 0)"));
    f.outgoing.put(20L, List.of(f.edge(f.member, target, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT, Map.of()),
        f.edge(f.member, source, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT, Map.of())));
    assertTrue(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Bond"));
  }

  @Test void relationshipSelfLoopsMissingParticipantsAndWrongEndpointSemanticsAreRejected() {
    var f = relationships(); var source = f.assets.get(501L);
    f.outgoing.put(20L, List.of(f.edge(f.member, source, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, Map.of()),
        f.edge(f.member, source, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET, Map.of())));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.outgoing.clear(); assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f = relationships(); f.assets.remove(502L);
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
  }

  @Test void duplicateOrderedPairsAndEmptyRelationshipResultsCannotPass() {
    var f = relationships(); var duplicate = f.observation(21, f.member.getObservable().getSemantics(), f.member.getGeometry());
    f.members.add(duplicate); f.outgoing.put(21L, f.outgoing.get(20L));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.members.clear(); assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
  }

  @Test void staleParticipantReferencesCannotHideCurrentCommittedMeaningOrGeometry() {
    var f = relationships();
    f.observation(501, f.region, geometry("EPSG:4326 POINT(0 0)"));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"),
        "A stale edge meaning must not override the current committed endpoint");
    f = relationships();
    var junction = f.assets.get(501L).getObservable().getSemantics();
    f.observation(501, junction, geometry("EPSG:4326 POINT(0 1)"));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"),
        "A stale edge geometry must not override the current committed endpoint");
  }
}
