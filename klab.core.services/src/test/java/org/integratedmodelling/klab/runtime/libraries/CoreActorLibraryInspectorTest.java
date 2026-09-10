package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Modifier;
import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.impl.HistogramImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.Inspector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.jupiter.api.Test;

class CoreActorLibraryInspectorTest {
  @Test
  void allAssetsMustBeSupportedAndNonNull() {
    assertFalse(Inspector.checkViable(null));
    assertFalse(Inspector.checkViable(null, (Object[]) null));
    assertFalse(Inspector.checkViable(null, observation(false), null));
    assertFalse(Inspector.checkViable(null, "not an asset"));
    assertFalse(Inspector.checkViable(null, List.of()));
    assertTrue(Inspector.checkViable(null, List.of(observation(false), observation(true))));
    var cyclic = new ArrayList<>();
    cyclic.add(cyclic);
    assertFalse(Inspector.checkViable(null, cyclic));
  }

  @Test
  void observationDefaultsDistinguishSubstantialsAndDependents() {
    var substantial = observation(false);
    when(substantial.getResolvedCoverage()).thenReturn(0.0);
    assertTrue(Inspector.checkViable(null, substantial));
    assertFalse(Inspector.checkViable(null, substantial, option("resolved", false)));
    var quality = observation(true);
    when(quality.getResolvedCoverage()).thenReturn(0.0);
    assertFalse(Inspector.checkViable(null, quality));
    when(quality.getResolvedCoverage()).thenReturn(0.5);
    assertTrue(Inspector.checkViable(null, quality));
    assertFalse(Inspector.checkViable(null, quality, option("mincoverage", 0.9)));
    when(quality.getResolvedCoverage()).thenReturn(Double.NaN);
    assertFalse(Inspector.resolved(null, quality, null));
    when(substantial.getGeometry()).thenReturn(null);
    assertFalse(Inspector.checkViable(null, substantial));
  }

  @Test
  void noDataPoliciesDistinguishAllMixedCompleteAndUnknown() {
    var quality = observation(true);
    when(quality.getHistograms()).thenReturn(Map.of(1L, histogram(2, 1)));
    assertTrue(Inspector.checkViable(null, quality, option("data", true)));
    assertFalse(Inspector.checkViable(null, quality, option("nodata", true)));
    assertFalse(Inspector.checkViable(null, quality, option("nodata", false)));
    when(quality.getHistograms()).thenReturn(Map.of(1L, histogram(0, 3)));
    assertFalse(Inspector.checkViable(null, quality, option("data", true)));
    assertTrue(Inspector.checkViable(null, quality, option("nodata", true)));
    assertFalse(Inspector.complete(null, quality));
    when(quality.getHistograms()).thenReturn(Map.of(1L, histogram(3, 0)));
    assertTrue(Inspector.complete(null, quality));
    assertFalse(Inspector.noData(null, quality));
    when(quality.getHistograms()).thenReturn(Map.of());
    assertTrue(Inspector.checkViable(null, quality));
    assertFalse(Inspector.hasData(null, quality));
    assertFalse(Inspector.noData(null, quality));
    assertFalse(Inspector.complete(null, quality));
    assertFalse(Inspector.noData(null, Histogram.empty()));
  }

  @Test
  void everyTemporalSliceMustBeCompleteButOnlyOneNeedsData() {
    var quality = observation(true);
    when(quality.getHistograms()).thenReturn(Map.of(1L, histogram(3, 0), 2L, histogram(0, 3)));
    assertTrue(Inspector.hasData(null, quality));
    assertFalse(Inspector.complete(null, quality));
    assertFalse(Inspector.noData(null, quality));
    when(quality.getHistograms()).thenReturn(Map.of(1L, histogram(3, 0), 2L, Histogram.empty()));
    assertFalse(Inspector.complete(null, quality));
  }

  @Test
  void scalarChecksRequireNoStorageAndPreserveZeroAndFalse() {
    var scalar = observation(true);
    when(scalar.getGeometry().isScalar()).thenReturn(true);
    when(scalar.getValue()).thenReturn(0.0);
    assertTrue(Inspector.complete(null, scalar));
    assertTrue(Inspector.inRange(null, scalar, 0, 0));
    when(scalar.getValue()).thenReturn(false);
    assertTrue(Inspector.hasData(null, scalar));
    when(scalar.getValue()).thenReturn(Double.NaN);
    assertTrue(Inspector.noData(null, scalar));
    assertFalse(Inspector.hasData(null, scalar));
    when(scalar.getValue()).thenReturn(Double.POSITIVE_INFINITY);
    assertTrue(Inspector.hasData(null, scalar));
    assertFalse(Inspector.inRange(null, scalar, 0, 1));
  }

  @Test
  void metadataDistinguishesAbsentKeysFromExplicitNull() {
    var observation = observation(false);
    var metadata = Metadata.create();
    metadata.put("explicitNull", null);
    metadata.put("name", "region");
    when(observation.getMetadata()).thenReturn(metadata);
    assertTrue(Inspector.metadata(null, observation, "explicitNull", null));
    assertFalse(Inspector.metadata(null, observation, "absent", null));
    assertTrue(Inspector.metadata(null, observation, "name", "region"));
  }

  @Test
  void explicitStorageLookupPreservesBackendFailure() {
    var context = mock(ContextScope.class);
    var twin = mock(DigitalTwin.class);
    var manager = mock(StorageManager.class);
    var storage = mock(Storage.class);
    var observation = observation(true);
    when(context.getDigitalTwin()).thenReturn(twin);
    when(twin.getStorageManager()).thenReturn(manager);
    when(manager.getStorage(observation)).thenReturn(storage);
    assertSame(storage, Inspector.storage(null, new CoreActorLibrary.Context(context), observation));
    var failure = new IllegalStateException("Storage unavailable");
    when(manager.getStorage(observation)).thenThrow(failure);
    assertSame(failure, assertThrows(IllegalStateException.class,
        () -> Inspector.storage(null, context, observation)));
  }

  @Test
  void numericRangesAreInclusiveAndMissingDataIsSeparate() {
    var histogram = histogram(2, 1);
    assertTrue(Inspector.inRange(null, histogram, 2, 4));
    assertFalse(Inspector.inRange(null, histogram, 2, 3));
    assertFalse(Inspector.inRange(null, Histogram.empty(), 2, 4));
    assertThrows(IllegalArgumentException.class, () -> Inspector.inRange(null, histogram, 4, 2));
  }

  @Test
  void optionsAreValidatedBeforeAnyAssetChecks() {
    assertThrows(IllegalArgumentException.class,
        () -> Inspector.problems(null, observation(true), option("nodtaa", true)));
    assertThrows(IllegalArgumentException.class,
        () -> Inspector.problems(null, observation(true), option("nodata", "true")));
    assertThrows(IllegalArgumentException.class,
        () -> Inspector.problems(null, observation(true), option("mincoverage", 2)));
  }

  @Test
  void activitiesAndDataflowsFailOnErrorsAndCycles() {
    var activity = mock(Activity.class);
    assertFalse(Inspector.checkViable(null, activity));
    when(activity.getOutcome()).thenReturn(Activity.Outcome.SUCCESS);
    assertTrue(Inspector.checkViable(null, activity));
    var actuator = mock(Actuator.class);
    when(actuator.getActuatorType()).thenReturn(Actuator.Type.REFERENCE);
    var targetObservation = observation(false);
    when(actuator.getObservation()).thenReturn(targetObservation);
    var flow = mock(Dataflow.class);
    when(flow.getComputation()).thenReturn(List.of(actuator));
    assertTrue(Inspector.checkViable(null, flow));
    when(flow.getNotifications()).thenReturn(List.of(Notification.error("failed")));
    assertFalse(Inspector.checkViable(null, flow));
    when(actuator.getChildren()).thenReturn(List.of(actuator));
    assertFalse(Inspector.checkViable(null, actuator));
  }

  @Test
  void graphMembershipUsesNamedUrnsAndNeverTransientIds() {
    var ctx = mock(ContextScope.class);
    var twin = mock(DigitalTwin.class);
    var graph = mock(KnowledgeGraph.class);
    when(ctx.getDigitalTwin()).thenReturn(twin);
    when(twin.getKnowledgeGraph()).thenReturn(graph);
    var contextActor = new CoreActorLibrary.Context(ctx);
    when(graph.isOnline()).thenReturn(true);
    assertTrue(Inspector.checkViable(null, contextActor));
    var committed = observation(false);
    when(graph.getAsset("named:region", ctx, RuntimeAsset.class)).thenReturn(committed);
    assertTrue(Inspector.contains(null, contextActor, "named:region"));
    assertFalse(Inspector.contains(null, contextActor, -1L));
    verify(graph, never()).getAsset(-1L, ctx, RuntimeAsset.class);
    when(graph.isOnline()).thenReturn(false);
    assertFalse(Inspector.checkViable(null, contextActor));
  }

  @Test
  void linksCompareTransientIdentitiesBeforeCommit() {
    var ctx = mock(ContextScope.class);
    var twin = mock(DigitalTwin.class);
    var graph = mock(KnowledgeGraph.class);
    when(ctx.getDigitalTwin()).thenReturn(twin);
    when(twin.getKnowledgeGraph()).thenReturn(graph);
    var source = observation(false);
    var target = observation(false);
    var other = observation(false);
    when(target.getId()).thenReturn(-1L);
    when(other.getId()).thenReturn(-1L);
    when(target.getTransientId()).thenReturn(42L);
    when(other.getTransientId()).thenReturn(43L);
    var link = mock(KnowledgeGraph.Link.class);
    when(link.target()).thenReturn(other);
    when(link.type()).thenReturn(GraphModel.Relationship.HAS_CHILD);
    when(graph.getLinks(source, GraphModel.Relationship.Direction.OUTGOING, ctx,
        GraphModel.Relationship.HAS_CHILD)).thenReturn(List.of(link));
    assertFalse(Inspector.linked(null, ctx, source, target, GraphModel.Relationship.HAS_CHILD));
    when(other.getTransientId()).thenReturn(42L);
    assertTrue(Inspector.linked(null, ctx, source, target, GraphModel.Relationship.HAS_CHILD));
  }

  @Test
  void graphStructureChecksDoNotConfuseAnEmptyDagWithAViableGraph() {
    var graph = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);
    assertTrue(Inspector.acyclic(null, graph));
    assertFalse(Inspector.checkViable(null, graph));
    graph.addVertex("a"); graph.addVertex("b"); graph.addEdge("a", "b");
    assertEquals(2, Inspector.vertices(null, graph));
    assertEquals(1, Inspector.edges(null, graph));
    assertTrue(Inspector.acyclic(null, graph));
    graph.addEdge("b", "a");
    assertFalse(Inspector.acyclic(null, graph));
  }

  @Test
  void allPublicVerbsHaveUniqueNamesAndArgumentDescriptors() {
    var names = new HashSet<String>();
    for (var method : Inspector.class.getDeclaredMethods()) {
      if (!Modifier.isPublic(method.getModifiers())) continue;
      var verb = method.getAnnotation(Verb.class);
      assertNotNull(verb, method.getName());
      assertTrue(names.add(verb.name()));
      assertEquals(Verb.Type.FUNCTION, verb.executionType());
      for (int i = 1; i < method.getParameterCount(); i++)
        assertNotNull(method.getParameters()[i].getAnnotation(Verb.Argument.class));
    }
  }

  private static Observation observation(boolean quality) {
    var observation = mock(Observation.class);
    var observable = mock(Observable.class);
    var geometry = mock(Geometry.class);
    when(geometry.size()).thenReturn(3L);
    when(observation.getGeometry()).thenReturn(geometry);
    when(observation.getObservable()).thenReturn(observable);
    when(observable.is(SemanticType.QUALITY)).thenReturn(quality);
    when(observation.getResolvedCoverage()).thenReturn(1.0);
    return observation;
  }

  private static Metadata option(String key, Object value) {
    var metadata = Metadata.create(); metadata.put(key, value); return metadata;
  }

  private static Histogram histogram(double valid, double missing) {
    var ret = new HistogramImpl();
    ret.setEmpty(valid == 0); ret.setMissingCount(missing); ret.setMin(2); ret.setMax(4);
    if (valid > 0) {
      var bin = new HistogramImpl.BinImpl(); bin.setCount(valid); ret.getBins().add(bin);
    }
    return ret;
  }
}
