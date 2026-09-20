package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RuntimeServiceQueryTest {

  @BeforeAll
  static void configureKlab() {
    ServiceConfiguration.injectInstantiators();
  }

  @Test
  void disjointSpatialSupportCanBeCachedAndEncoded() {
    var left = ShapeImpl.create("EPSG:4326 POLYGON ((0 0, 1 0, 1 1, 0 1, 0 0))");
    var right = ShapeImpl.create("EPSG:4326 POLYGON ((3 3, 4 3, 4 4, 3 4, 3 3))");
    var leftGeometry = Geometry.create(left.encode());
    var rightGeometry = Geometry.create(right.encode());
    var intersection = RuntimeService.intersection(leftGeometry, rightGeometry);
    org.junit.jupiter.api.Assertions.assertNotNull(intersection.encode());
    assertEquals(0.0, RuntimeService.coverage(leftGeometry, intersection));
    org.junit.jupiter.api.Assertions.assertFalse(RuntimeService.hasMemberSupport(intersection));
    org.junit.jupiter.api.Assertions.assertTrue(RuntimeService.hasMemberSupport(
        RuntimeService.intersection(leftGeometry, leftGeometry)));
    assertEquals(intersection.encode(), RuntimeService.intersection(leftGeometry, rightGeometry).encode());
  }

  @Test
  void rootSubstantialResetsFocusButNestedMemberAndQualityKeepIt() {
    var scope = org.mockito.Mockito.mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);
    var reset = org.mockito.Mockito.mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);
    var observable = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
    var semantics = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    var observation = new ObservationImpl();
    observation.setObservable(observable);
    org.mockito.Mockito.when(observable.getSemantics()).thenReturn(semantics);
    org.mockito.Mockito.when(semantics.getType()).thenReturn(java.util.EnumSet.of(SemanticType.SUBJECT));
    org.mockito.Mockito.when(scope.getContextObservation()).thenReturn(new ObservationImpl());
    org.mockito.Mockito.when(scope.within(null)).thenReturn(reset);
    org.junit.jupiter.api.Assertions.assertSame(reset, RuntimeService.submissionScope(observation, scope));
    org.mockito.Mockito.when(scope.getActivity()).thenReturn(org.mockito.Mockito.mock(org.integratedmodelling.klab.api.provenance.Activity.class));
    org.junit.jupiter.api.Assertions.assertSame(scope, RuntimeService.submissionScope(observation, scope));
    org.mockito.Mockito.when(scope.getActivity()).thenReturn(null);
    org.mockito.Mockito.when(semantics.getType()).thenReturn(java.util.EnumSet.of(SemanticType.QUALITY));
    org.junit.jupiter.api.Assertions.assertSame(scope, RuntimeService.submissionScope(observation, scope));
  }

  @Test
  void exactCoveragePreservesContributionsBelowResolverThreshold() {
    Geometry requested = Geometry.create("T0(1){tend=1000,tstart=0,ttype=PHYSICAL}");
    Geometry source = Geometry.create("T0(1){tend=5,tstart=0,ttype=PHYSICAL}");
    Geometry actual = RuntimeService.intersection(requested, source);

    assertEquals(0.005, RuntimeService.coverage(requested, actual), 1.0e-12);
  }

  @Test
  void partialQueryResultIsDetachedAndKeepsQueryId() {
    Geometry requested = Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}");
    Geometry actual = Geometry.create("T0(1){tend=5,tstart=0,ttype=PHYSICAL}");
    var source = new ObservationImpl();
    source.setId(42);
    source.setGeometry(requested);
    source.getMetadata().put("source", true);

    var result = RuntimeService.queryResult(source, actual, requested, 0.5);

    assertEquals(Observation.QUERY_ID, result.getId());
    assertEquals(42, source.getId());
    assertNotEquals(source.getGeometry().encode(), result.getGeometry().encode());
    assertEquals(true, result.getMetadata().get("source"));
    assertEquals(0.5, result.getMetadata().get(Metadata.IM_QUERY_COVERAGE, Double.class));
  }

  @Test
  void qualityQueriesKeepIndependentSourceAndRequestedContracts() {
    var source = new ObservationImpl(); source.setId(42); source.setGeometry(Geometry.create("1"));
    var cd = new ObservationImpl.ContextualizationDataImpl();
    cd.setNativeShardingStrategy(org.integratedmodelling.klab.api.data.Data.ShardingStrategy.trivial(org.integratedmodelling.klab.api.data.Storage.Type.LONG));
    source.setContextualizationData(cd);
    var query = new ObservationImpl();query.setId(0);
    var first = RuntimeService.qualityQueryResult(source,query,source.getGeometry());
    var second = RuntimeService.qualityQueryResult(source,query,source.getGeometry());
    first.getContextualizationData().getNativeShardingStrategy().setSuggestedSplits(7);
    assertEquals(1,source.getContextualizationData().getNativeShardingStrategy().getSuggestedSplits());
    assertEquals(1,second.getContextualizationData().getNativeShardingStrategy().getSuggestedSplits());
    assertEquals(List.of(42L),first.getMetadata().get(Metadata.IM_QUERY_SOURCE_IDS));
    assertEquals(0,first.getId());assertEquals(42,source.getId());
  }

  @Test
  void contributorGeometryIsAUnionRatherThanAConvexHull() {
    Geometry left =
        Geometry.create(
            "S2(1,1){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 1&comma;1 1&comma;1 0&comma;0 0))}");
    Geometry right =
        Geometry.create(
            "S2(1,1){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((2 0&comma;2 1&comma;3 1&comma;3 0&comma;2 0))}");

    Geometry union = RuntimeService.union(left, right);
    var shape = (ShapeImpl) GeometryRepository.INSTANCE.scale(union).getSpace().getGeometricShape();

    assertEquals(2, shape.getJTSGeometry().getNumGeometries());
  }

  @Test
  void collectiveQueryClassificationExcludesQualities() {
    assertFalse(SemanticType.isEnumerableSubstantial(EnumSet.of(SemanticType.QUALITY)));
    assertTrue(SemanticType.isEnumerableSubstantial(EnumSet.of(SemanticType.SUBJECT)));
    assertTrue(SemanticType.isEnumerableSubstantial(EnumSet.of(SemanticType.AGENT)));
    assertTrue(SemanticType.isEnumerableSubstantial(EnumSet.of(SemanticType.EVENT)));
    assertTrue(SemanticType.isEnumerableSubstantial(EnumSet.of(SemanticType.RELATIONSHIP)));
  }

  @Test
  void pendingCohortMemberIsFoundByIdentificationStrategy() {
    var cohort = new CohortImpl();
    var existing = new ObservationImpl();
    existing.setId(-2);
    existing.setUrn("test:object");
    var submitted = new ObservationImpl();
    submitted.setUrn("test:object");
    var unrelated = new ObservationImpl();
    unrelated.setUrn("test:object");

    var links =
        List.of(
            new LinkImpl(cohort, unrelated, GraphModel.Relationship.HAS_CHILD),
            new LinkImpl(cohort, existing, GraphModel.Relationship.HAS_MEMBER));

    assertEquals(
        existing,
        RuntimeService.findIdenticalMember(
            submitted,
            links.stream().map(l -> (KnowledgeGraph.Link) l).collect(Collectors.toList()),
            (first, second) -> first.getUrn().compareTo(second.getUrn())));
  }

  @Test
  void cohortEligibilityRemainsDefensiveWhenATypeSetLosesItsCountableBit() {
    var subject = new ConceptImpl();
    subject.setType(EnumSet.of(SemanticType.SUBJECT));

    var relationship = new ConceptImpl();
    relationship.setType(EnumSet.of(SemanticType.RELATIONSHIP));

    assertTrue(RuntimeService.requiresCohort(subject));
    assertTrue(RuntimeService.requiresCohort(relationship));
  }

  @Test
  void newCohortIsStoredAndLinkedToTheContextInOneKnowledgeGraphTransaction() {
    class CapturingTransaction implements KnowledgeGraph.Transaction {
      private RuntimeAsset stored;
      private RuntimeAsset source;
      private RuntimeAsset destination;
      private GraphModel.Relationship relationship;

      @Override
      public void store(RuntimeAsset asset, Object... additionalProperties) {
        stored = asset;
      }

      @Override
      public void update(RuntimeAsset asset, Object... properties) {}

      @Override
      public void link(
          RuntimeAsset source,
          RuntimeAsset destination,
          GraphModel.Relationship relationship,
          Object... additionalProperties) {
        this.source = source;
        this.destination = destination;
        this.relationship = relationship;
      }

      @Override
      public void fail(Exception e) {}

      @Override
      public void close() {}
    }

    var subject = new ConceptImpl();
    subject.setUrn("earth:Region");
    subject.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE));
    var transaction = new CapturingTransaction();

    var cohort = RuntimeService.storeNewCohort(subject, transaction);

    assertEquals(cohort, transaction.stored);
    assertEquals(RuntimeAsset.CONTEXT_ASSET, transaction.source);
    assertEquals(cohort, transaction.destination);
    assertEquals(GraphModel.Relationship.HAS_CHILD, transaction.relationship);
    assertEquals("earth:Region", cohort.getObservable().getUrn());
    assertEquals(0, cohort.getChildrenCount());
  }

  @Test
  void persistedCohortMemberMatchesItsLogicalNamespaceAndNameUrn() {
    var cohort = new CohortImpl();
    var existing = new ObservationImpl();
    existing.setId(42);
    existing.setUrn("context-1:individuals:test.tanzania:ruaha");
    var submitted = new ObservationImpl();
    submitted.setUrn("test.tanzania:ruaha");

    var links =
        List.<KnowledgeGraph.Link>of(
            new LinkImpl(cohort, existing, GraphModel.Relationship.HAS_MEMBER));

    assertEquals("test.tanzania:ruaha", ObservationImpl.logicalUrn(existing.getUrn()));
    assertEquals(
        "context-1:individuals:test.tanzania:ruaha",
        ObservationImpl.catalogUrn("context-1", existing.getUrn()));
    assertEquals(
        existing,
        RuntimeService.findIdenticalMember(
            submitted, links, RuntimeService::compareDefaultIdentity));
  }

  @Test
  void concurrentSubmissionsWithTheSameIdentityShareOneOperation() throws Exception {
    var inFlight = new ConcurrentHashMap<String, CompletableFuture<Observation>>();
    var operationResult = new CompletableFuture<Observation>();
    var starts = new AtomicInteger();
    var barrier = new CyclicBarrier(2);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first =
          executor.submit(
              () -> {
                barrier.await();
                return RuntimeService.coalesce(
                    inFlight,
                    "context|cohort|identity",
                    () -> {
                      starts.incrementAndGet();
                      return operationResult;
                    });
              });
      var second =
          executor.submit(
              () -> {
                barrier.await();
                return RuntimeService.coalesce(
                    inFlight,
                    "context|cohort|identity",
                    () -> {
                      starts.incrementAndGet();
                      return operationResult;
                    });
              });

      var firstFuture = first.get();
      var secondFuture = second.get();
      assertEquals(1, starts.get());
      assertFalse(firstFuture == secondFuture);
      firstFuture.cancel(false);

      var observation = new ObservationImpl();
      operationResult.complete(observation);
      assertTrue(firstFuture.isCancelled());
      assertEquals(observation, secondFuture.join());
      assertTrue(inFlight.isEmpty());
    } finally {
      executor.shutdownNow();
    }
  }
}
