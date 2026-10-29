package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;
import org.junit.jupiter.api.Test;

class NativeTestFixturesTest {
  /** Test-only shape of the already public native method; not a production service interface. */
  public interface NativeCatalogAccess {
    Cohort getCohortFor(Semantics semantics, ContextScope scope, boolean addIfMissing);
  }
  @org.junit.jupiter.api.BeforeAll static void initialize() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  static class Fixture {
    final ContextScope context = mock(ContextScope.class);
    final DigitalTwin twin = mock(DigitalTwin.class);
    final RuntimeService runtime = mock(RuntimeService.class, withSettings().extraInterfaces(RuntimeService.Testing.class, NativeCatalogAccess.class));
    final TestCaseBase agent = mock(TestCaseBase.class);
    final TestCaseBase.TestCaseScope owner = new TestCaseBase.TestCaseScope(agent, null, null);
    final Parameters<String> data = Parameters.create();
    Fixture() {
      when(context.getId()).thenReturn("native-fixture");when(context.getDigitalTwin()).thenReturn(twin);
      when(context.getData()).thenReturn(data);when(context.getService(RuntimeService.class)).thenReturn(runtime);
      when(agent.status()).thenReturn("running");owner.registerContext(context);
    }
    void grant() { assertTrue(NativeTestFixtures.grant(owner, context)); }
  }

  @Test void nonTestCallersAndMetadataFlagsCannotGrantOrExecuteFixtures() {
    var invalidContext = new Object();
    assertThrows(IllegalArgumentException.class, () -> CoreActorLibrary.Inspector.fixtures(mock(RuntimeAgent.Scope.class), invalidContext));
    var f = new Fixture();
    f.data.put(NativeTestFixtures.class.getName() + ".permit", Map.of("test", true, "owner", "fake"));
    assertThrows(IllegalArgumentException.class, () -> NativeTestFixtures.empty(mock(Data.Builder.class), Geometry.UNIVERSAL, null, f.context));
    assertThrows(IllegalArgumentException.class, () -> NativeTestFixtures.classify(null, f.context, null));
    assertThrows(IllegalArgumentException.class, () -> NativeTestFixtures.discoverAfter(null, null, null, f.context, null));
    when(f.context.getService(RuntimeService.class)).thenReturn(mock(RuntimeService.class));
    assertThrows(IllegalArgumentException.class, () -> NativeTestFixtures.grant(f.owner, f.context));
  }

  @Test void emptyFixtureOnlyExecutesThroughLiveOwnedPrivatePermitAndDoesNotFabricateGraphEffects() {
    var f = new Fixture();f.grant();
    var builder = mock(Data.Builder.class);
    NativeTestFixtures.empty(builder, Geometry.UNIVERSAL, null, f.context);
    assertEquals(1, NativeTestFixtures.count(f.owner, f.context, "empty"));
    verifyNoInteractions(builder);verify(f.twin, never()).getKnowledgeGraph();
    assertTrue(f.data.values().stream().noneMatch(value -> value instanceof Serializable));
    assertTrue(NativeTestFixtures.clear(f.owner, f.context));
    assertThrows(IllegalArgumentException.class, () -> NativeTestFixtures.empty(builder, Geometry.UNIVERSAL, null, f.context));
  }

  @Test void copiedPermitsCannotCrossTwinIdentityAndExpireWithTheirTestcase() {
    var f = new Fixture();f.grant();
    var id = f.context.getId();
    var other = mock(ContextScope.class);when(other.getId()).thenReturn(id);
    when(other.getDigitalTwin()).thenReturn(mock(DigitalTwin.class));when(other.getData()).thenReturn(f.data);
    when(other.getService(RuntimeService.class)).thenReturn(f.runtime);
    assertFalse(f.owner.ownsContext(other));
    assertThrows(IllegalArgumentException.class, () -> NativeTestFixtures.empty(null, Geometry.UNIVERSAL, null, other));
    f.owner.done();
    assertTrue(f.data.isEmpty());
    assertThrows(IllegalArgumentException.class, () -> NativeTestFixtures.empty(null, Geometry.UNIVERSAL, null, f.context));
    assertThrows(IllegalArgumentException.class, () -> NativeTestFixtures.grant(f.owner, f.context));
  }

  @Test void failingClassifierRequiresAnActualEarlierFamilyBeforeRecordingItsFailure() {
    var f = new Fixture();f.grant();
    var reasoner = mock(Reasoner.class);when(f.context.getService(Reasoner.class)).thenReturn(reasoner);
    var member = mock(Observation.class);var observable = mock(Observable.class);
    when(member.getObservable()).thenReturn(observable);when(f.context.getContextObservation()).thenReturn(member);
    var family = mock(Concept.class);when(family.getUrn()).thenReturn("test:Prior");when(family.is(SemanticType.PREDICATE)).thenReturn(true);
    when(reasoner.resolveConcept("test:Prior")).thenReturn(family);
    var call = mock(ServiceCall.class);when(call.getParameters()).thenReturn(Parameters.create("after", family));
    when(reasoner.directTraits(observable)).thenReturn(List.of());when(reasoner.directRoles(observable)).thenReturn(List.of());
    assertThrows(IllegalStateException.class, () -> NativeTestFixtures.failClassifier(observable, f.context, call));
    assertEquals(0, NativeTestFixtures.count(f.owner, f.context, "prior"));
    var concrete = mock(Concept.class);when(reasoner.is(concrete, family)).thenReturn(true);
    when(reasoner.directTraits(observable)).thenReturn(List.of(concrete));
    var failure = assertThrows(IllegalStateException.class, () -> NativeTestFixtures.failClassifier(observable, f.context, call));
    assertEquals("DELIBERATE_NATIVE_TEST_CLASSIFIER_FAILURE", failure.getMessage());
    assertEquals(1, NativeTestFixtures.count(f.owner, f.context, "prior"));
    assertEquals(1, NativeTestFixtures.count(f.owner, f.context, "classifier"));
    verify(f.twin, never()).getKnowledgeGraph();
  }

  @Test void failingConsumerMustReadTheActualSelectedInputBeforeFailing() {
    var f = new Fixture();f.grant();var selected = mock(Observation.class);
    when(selected.getId()).thenReturn(Observation.QUERY_ID);
    when(f.runtime.getMembers(selected, f.context)).thenReturn(List.of(mock(Observation.class), mock(Observation.class)));
    var failure = assertThrows(IllegalStateException.class, () -> NativeTestFixtures.failConsumer(null, null, Geometry.UNIVERSAL, f.context, selected));
    assertEquals("DELIBERATE_NATIVE_TEST_CONSUMER_FAILURE", failure.getMessage());
    assertEquals(1, NativeTestFixtures.count(f.owner, f.context, "consumer"));
    assertEquals(2, NativeTestFixtures.count(f.owner, f.context, "consumerMembers"));
    verify(f.runtime).getMembers(selected, f.context);verify(f.twin, never()).getKnowledgeGraph();
  }

  static class OrderedFixture extends Fixture {
    final Reasoner reasoner = mock(Reasoner.class);
    final Concept family = mock(Concept.class), concrete = mock(Concept.class), targetConcept = mock(Concept.class);
    final Observable nativeObservable = mock(Observable.class), target = mock(Observable.class), individual = mock(Observable.class), memberMeaning = mock(Observable.class);
    final org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl member = new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
    final org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl completed = new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
    final Geometry geometry = Geometry.create("T0(1){tstart=0,tend=100,ttype=PHYSICAL}");
    final ServiceCall classification = mock(ServiceCall.class), discovery = mock(ServiceCall.class);
    final Data.Builder builder = mock(Data.Builder.class);
    OrderedFixture() {
      when(context.getService(Reasoner.class)).thenReturn(reasoner);
      when(family.getUrn()).thenReturn("test:Family");when(family.is(SemanticType.PREDICATE)).thenReturn(true);
      when(concrete.getUrn()).thenReturn("test:Concrete");when(concrete.is(SemanticType.PREDICATE)).thenReturn(true);
      when(reasoner.resolveConcept("test:Family")).thenReturn(family);when(reasoner.resolveConcept("test:Concrete")).thenReturn(concrete);
      when(reasoner.resolveObservable("test:Region")).thenReturn(nativeObservable);
      when(reasoner.satisfiable(concrete)).thenReturn(true);when(reasoner.lexicalRoot(concrete)).thenReturn(family);
      when(reasoner.is(concrete, family)).thenReturn(true);
      when(nativeObservable.getSemantics()).thenReturn(mock(Concept.class));
      when(nativeObservable.getUrn()).thenReturn("each test:Region");
      var nativeBuilder = mock(Observable.Builder.class);when(nativeObservable.builder(context)).thenReturn(nativeBuilder);
      when(nativeBuilder.collective(true)).thenReturn(nativeBuilder);when(nativeBuilder.buildObservable()).thenReturn(nativeObservable);
      when(target.getSemantics()).thenReturn(targetConcept);when(targetConcept.isCollective()).thenReturn(true);
      when(target.getUrn()).thenReturn("each test:Other");
      var outputBuilder = mock(Observable.Builder.class);when(target.builder(context)).thenReturn(outputBuilder);
      when(outputBuilder.collective(false)).thenReturn(outputBuilder);when(outputBuilder.buildObservable()).thenReturn(individual);
      member.setId(20);member.setUrn("test:member");member.setObservable(memberMeaning);member.setGeometry(geometry);
      completed.setId(Observation.QUERY_ID);completed.setObservable(nativeObservable);completed.setGeometry(geometry);completed.setResolvedCoverage(1);
      when(runtime.submit(any(Observation.class), eq(context))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(completed));
      when(runtime.getMembers(completed, context)).thenReturn(List.of(member));
      when(classification.getParameters()).thenReturn(Parameters.create("result", concrete));
      when(discovery.getParameters()).thenReturn(Parameters.create("members", "test:Region", "family", family));
      when(reasoner.directRoles(memberMeaning)).thenReturn(List.of());when(reasoner.directTraits(memberMeaning)).thenReturn(List.of());
      when(builder.object(anyString(), eq(individual), eq(geometry), any())).thenReturn(mock(Data.Builder.class));
      grant();
    }
    void invoke() {
      when(context.getContextObservation()).thenReturn(member);
      assertSame(concrete, NativeTestFixtures.classify(memberMeaning, context, classification));
      when(context.getContextObservation()).thenReturn(null);
    }
    void stage() { when(reasoner.directTraits(memberMeaning)).thenReturn(List.of(concrete)); }
    void discover() { NativeTestFixtures.discoverAfter(builder, geometry, target, context, discovery); }
  }

  @Test void orderedDiscoveryNeedsActualEarlierInvocationAndStagedResultBeforeNormalObjectEmission() {
    var f = new OrderedFixture();f.stage();
    assertThrows(IllegalStateException.class, f::discover);
    verifyNoInteractions(f.builder);
    f.invoke();when(f.reasoner.directTraits(f.memberMeaning)).thenReturn(List.of());
    assertThrows(IllegalStateException.class, f::discover);
    verifyNoInteractions(f.builder);
    f.stage();f.discover();
    verify(f.runtime, atLeastOnce()).submit(any(Observation.class), eq(f.context));
    verify(f.builder).object(eq("ordered_member"), eq(f.individual), eq(f.geometry), any());
    assertEquals(1, NativeTestFixtures.count(f.owner, f.context, "classification"));
    assertEquals(1, NativeTestFixtures.count(f.owner, f.context, "ordered"));
    assertEquals(1, NativeTestFixtures.count(f.owner, f.context, "discovery"));
    verify(f.twin, never()).getKnowledgeGraph();
  }

  @Test void orderedDiscoveryRejectsForeignMembersIncompleteDiscoveryAndMissingGeometry() {
    var f = new OrderedFixture();f.invoke();f.stage();
    f.member.setUrn("test:foreign");assertThrows(IllegalStateException.class, f::discover);
    f.member.setUrn("test:member");f.completed.setResolvedCoverage(0.5);
    assertThrows(IllegalStateException.class, f::discover);
    assertThrows(IllegalArgumentException.class, () -> NativeTestFixtures.discoverAfter(f.builder, Geometry.UNIVERSAL, f.target, f.context, f.discovery));
    verifyNoInteractions(f.builder);
  }

  @Test void orderedDiscoveryCannotReportSuccessWhenTheNormalBuilderFails() {
    var f = new OrderedFixture();f.invoke();f.stage();
    when(f.builder.object(anyString(), any(), any(), any())).thenThrow(new IllegalStateException("deliberate builder failure"));
    assertThrows(IllegalStateException.class, f::discover);
    assertEquals(0, NativeTestFixtures.count(f.owner, f.context, "ordered"));
    assertEquals(0, NativeTestFixtures.count(f.owner, f.context, "discovery"));
    assertEquals(1, NativeTestFixtures.count(f.owner, f.context, "classification"));
  }

  static class CatalogFixture extends Fixture {
    final Reasoner reasoner = mock(Reasoner.class);
    final org.integratedmodelling.klab.api.data.KnowledgeGraph graph = mock(org.integratedmodelling.klab.api.data.KnowledgeGraph.class);
    final Map<Long, Cohort> catalogs = new java.util.TreeMap<>();
    final Map<Long, Observation> observations = new java.util.TreeMap<>();
    final org.integratedmodelling.common.knowledge.ConceptImpl nativeConcept = new org.integratedmodelling.common.knowledge.ConceptImpl();
    final NativeCatalogAccess access = (NativeCatalogAccess) runtime;
    CatalogFixture() {
      nativeConcept.setUrn("test:Region");nativeConcept.setNamespace("test");nativeConcept.setName("region");
      nativeConcept.setType(java.util.EnumSet.of(SemanticType.SUBJECT));
      when(context.getService(Reasoner.class)).thenReturn(reasoner);when(twin.getKnowledgeGraph()).thenReturn(graph);
      when(reasoner.resolveConcept("test:Region")).thenReturn(nativeConcept);
      when(reasoner.satisfiable(nativeConcept)).thenReturn(true);when(reasoner.baseSubstantialType(nativeConcept, context)).thenReturn(nativeConcept);
      when(graph.query(any(Class.class), eq(context))).thenAnswer(call ->
          new org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery<>(
              org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery.AssetType.classify(call.getArgument(0, Class.class))));
      when(graph.query(any(org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.class), eq(Cohort.class), eq(context))).thenAnswer(call -> {
        var query = call.getArgument(0, org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery.class);
        return catalogs.values().stream().filter(catalog -> query.getCriteria().stream().allMatch(raw -> {
          var criterion = (org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.Criterion) raw;
          return !criterion.field().equals(org.integratedmodelling.klab.api.digitaltwin.GraphModel.Cohort.OBSERVABLE_FIELD)
              || criterion.argument().equals(catalog.getObservable().getUrn());
        })).toList();
      });
      when(graph.query(any(org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.class), eq(Observation.class), eq(context))).thenAnswer(call -> {
        var query = call.getArgument(0, org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery.class);
        if (query.getSource() != null) return List.of();
        if (query.getTarget() != null) return List.copyOf(observations.values());
        return List.copyOf(observations.values());
      });
      when(graph.query(any(org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.class), eq(org.integratedmodelling.klab.api.provenance.Activity.class), eq(context))).thenReturn(List.of());
      when(graph.getAsset(anyLong(), eq(context), eq(Observation.class))).thenAnswer(call -> observations.get(call.getArgument(0)));
      when(access.getCohortFor(any(Semantics.class), eq(context), eq(true))).thenAnswer(call -> {
        var semantics = call.getArgument(0, Concept.class);
        return catalogs.values().stream().filter(catalog -> catalog.getObservable().getSemantics().equals(semantics)).findFirst()
            .orElseGet(() -> addCatalog(semantics));
      });
      grant();
    }
    Cohort addCatalog(Concept meaning) {
      var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();observable.setSemantics(meaning);observable.setUrn(meaning.getUrn());
      var catalog = mock(Cohort.class);long id = 31L + catalogs.size();
      when(catalog.getId()).thenReturn(id);when(catalog.getUrn()).thenReturn("catalog:" + id);
      when(catalog.getObservable()).thenReturn(observable);when(catalog.getGeometry()).thenReturn(Geometry.UNIVERSAL);
      catalogs.put(id, catalog);return catalog;
    }
    boolean prepare() { return CoreActorLibrary.Inspector.preparecatalog(owner, context, nativeConcept); }
  }

  @Test void catalogSetupInvokesOnlyExactPublicNativeMethodAndVerifiesFreshEmptyGraphState() {
    var f = new CatalogFixture();assertTrue(f.prepare());
    verify(f.access).getCohortFor(eq(f.nativeConcept), eq(f.context), eq(true));
    assertEquals(1, f.catalogs.size());
    var first = ObservationInspector.nativeSnapshot(f.context);
    assertTrue(first.observations().isEmpty());assertTrue(first.contextualizations().isEmpty());
    assertTrue(first.cohorts().values().stream().allMatch(c -> c.members().isEmpty() && c.contributors().isEmpty()));
    assertTrue(f.prepare());assertEquals(first, ObservationInspector.nativeSnapshot(f.context));
    assertEquals(0, NativeTestFixtures.count(f.owner, f.context, "classification"));
    assertEquals(0, NativeTestFixtures.count(f.owner, f.context, "discovery"));
    assertEquals(0, NativeTestFixtures.count(f.owner, f.context, "empty"));
  }

  @Test void catalogSetupRejectsTransactionForeignOwnerRevocationAndQualifiedMeaning() {
    var f = new CatalogFixture();when(f.context.getCurrentTransaction()).thenReturn(mock(DigitalTwin.Transaction.class));
    assertThrows(IllegalStateException.class, f::prepare);verifyNoInteractions(f.access);
    when(f.context.getCurrentTransaction()).thenReturn(null);
    var otherAgent = mock(TestCaseBase.class);when(otherAgent.status()).thenReturn("running");
    var otherOwner = new TestCaseBase.TestCaseScope(otherAgent, null, null);otherOwner.registerContext(f.context);
    assertThrows(IllegalArgumentException.class, () -> CoreActorLibrary.Inspector.preparecatalog(otherOwner, f.context, f.nativeConcept));
    f.nativeConcept.setUrn("test:Qualified test:Region");
    assertThrows(IllegalArgumentException.class, f::prepare);
    f.nativeConcept.setUrn("test:Region");NativeTestFixtures.clear(f.owner, f.context);
    assertThrows(IllegalArgumentException.class, f::prepare);
    assertTrue(f.catalogs.isEmpty());
  }

  @Test void catalogSetupRejectsPopulatedCatalogsAndUnpersistedReturnBeans() {
    var f = new CatalogFixture();f.addCatalog(f.nativeConcept);
    var producer = new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
    var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();observable.setSemantics(f.nativeConcept);observable.setUrn(f.nativeConcept.getUrn());
    producer.setId(10);producer.setUrn("producer:10");producer.setObservable(observable);producer.setGeometry(Geometry.UNIVERSAL);
    f.observations.put(10L, producer);
    assertThrows(IllegalStateException.class, f::prepare);
    verify(f.access, never()).getCohortFor(any(), any(), anyBoolean());
    var missing = new CatalogFixture();var bean = missing.addCatalog(missing.nativeConcept);missing.catalogs.clear();
    when(missing.access.getCohortFor(any(), any(), anyBoolean())).thenReturn(bean);
    assertThrows(IllegalStateException.class, missing::prepare);
    assertTrue(missing.catalogs.isEmpty());
  }

  @Test void catalogSetupLeaseBlocksRevocationUntilSetupFinishes() throws Exception {
    var f = new CatalogFixture();
    var entered = new java.util.concurrent.CountDownLatch(1);var release = new java.util.concurrent.CountDownLatch(1);
    when(f.access.getCohortFor(any(), any(), anyBoolean())).thenAnswer(call -> {
      entered.countDown();
      if (!release.await(3, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Setup lease timed out");
      return f.addCatalog(f.nativeConcept);
    });
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var setup = java.util.concurrent.CompletableFuture.supplyAsync(f::prepare, pool);
      assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS));
      var revocation = java.util.concurrent.CompletableFuture.supplyAsync(() -> NativeTestFixtures.clear(f.owner, f.context), pool);
      try { assertThrows(java.util.concurrent.TimeoutException.class, () -> revocation.get(100, java.util.concurrent.TimeUnit.MILLISECONDS)); }
      finally { release.countDown(); }
      assertTrue(setup.get(3, java.util.concurrent.TimeUnit.SECONDS));
      assertTrue(revocation.get(3, java.util.concurrent.TimeUnit.SECONDS));
      assertThrows(IllegalArgumentException.class, f::prepare);
    } finally { release.countDown(); }
  }
}
