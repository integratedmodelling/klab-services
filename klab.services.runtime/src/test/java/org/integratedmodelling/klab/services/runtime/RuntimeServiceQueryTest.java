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
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
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
  void detachedQueriesAreLimitedToEnumerableSubstantials() {
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
