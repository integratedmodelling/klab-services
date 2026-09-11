package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.ScaleImpl;
import org.junit.jupiter.api.Test;

class ResolutionFlowChartAdapterTest {
  @Test void operationNodesAndCyclesRemainPortableTopology() throws Exception {
    var observable = mock(Observable.class);
    when(observable.getUrn()).thenReturn("test:Environment of each test:Region");
    when(observable.getContextualization()).thenReturn(org.integratedmodelling.klab.api.knowledge.Contextualization.CLASSIFICATION);
    when(observable.isOptional()).thenReturn(true);
    var context = new ObservationImpl(); context.setId(10);
    var target = new OperationTarget(observable, context, observable);
    var model = mock(org.integratedmodelling.klab.api.knowledge.Model.class);
    when(model.getUrn()).thenReturn("test:classifier");
    var graph = ResolutionGraph.create(mock(ContextScope.class));
    graph.graph().addVertex(target); graph.graph().addVertex(model);
    graph.graph().addEdge(target, model); graph.graph().addEdge(model, target);
    var chart = new ResolutionFlowChartAdapter().adapt(graph);
    chart.validate();
    var node = chart.getRoot().getChildren().stream().filter(n -> "operation".equals(n.getMetadata().get("kind"))).findFirst().orElseThrow();
    assertEquals("CLASSIFICATION", node.getMetadata().get("contextualization"));
    assertEquals(true, node.getMetadata().get("optionalModelDependency"));
    assertEquals(10L, node.getMetadata().get("contextObservationId"));
    var json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(chart);
    assertFalse(json.contains("OperationTarget")); assertFalse(json.contains("Mockito"));
    assertEquals(2, chart.getRoot().getEdges().size());
  }
  @Test void preservesSharedReferencesParallelBindingsAndDetachesFromSource() {
    ServiceConfiguration.injectInstantiators();
    var scale = new ScaleImpl(Geometry.UNIVERSAL);
    var observable = mock(Observable.class); when(observable.getUrn()).thenReturn("test:Thing");
    var requested = new ObservationImpl(); requested.setId(-2); requested.setObservable(observable); requested.setGeometry(scale);
    var existing = new ObservationImpl(); existing.setId(42); existing.setObservable(observable); existing.setGeometry(scale);
    var graph = ResolutionGraph.create(mock(ContextScope.class)).createChild(requested, scale);
    graph.addReference(existing, Coverage.create(scale, 1));
    graph.addReference(existing, Coverage.create(scale, 1));
    int i = 0; for (var edge : graph.graph().edgeSet()) edge.localName = "port" + i++;
    var chart = new ResolutionFlowChartAdapter().adapt(graph);
    chart.validate(); assertEquals(2, chart.getRoot().getChildren().size());
    assertEquals(2, chart.getRoot().getEdges().size());
    for (var edge : chart.getRoot().getEdges()) {
      assertEquals(42L, edge.getMetadata().get("referenceObservationId"));
      assertNotNull(edge.getMetadata().get("binding"));
      assertEquals(1.0, edge.getMetadata().get("coverage"));
    }
    graph.graph().removeVertex(observable);
    chart.validate(); assertEquals(2, chart.getRoot().getEdges().size());
  }
}
