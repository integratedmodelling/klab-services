package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.runtime.scale.CoverageImpl;
import org.integratedmodelling.klab.runtime.scale.ScaleImpl;
import org.junit.jupiter.api.Test;

class DataflowCompilerTest {

  @Test
  void compilesTheRootNodeAndPreservesResolutionMetadata() throws Exception {
    var scope = mock(ContextScope.class);
    when(scope.getId()).thenReturn("context");

    var requested = mock(Observation.class);
    when(requested.getId()).thenReturn(-2L);
    when(requested.getName()).thenReturn("requested");

    var rootObservable = mock(Observable.class);
    when(rootObservable.is(SemanticType.COUNTABLE)).thenReturn(false);
    when(rootObservable.getName()).thenReturn("resolvedRoot");

    var rootScale = new ScaleImpl(Geometry.UNIVERSAL);
    var root = new ObservationImpl();
    root.setId(42L);
    root.setName("root");
    root.setObservable(rootObservable);
    root.setGeometry(rootScale);

    var coverage = new CoverageImpl(rootScale, 0.75);
    var graph = ResolutionGraph.create(scope);
    var targetCoverage = ResolutionGraph.class.getDeclaredField("targetCoverage");
    targetCoverage.setAccessible(true);
    targetCoverage.set(graph, coverage);
    graph.graph().addVertex(root);

    var requirements = new ResourceSet();
    requirements.setWorkspace("test-workspace");
    graph.setDependencies(requirements);

    var dataflow = new DataflowCompiler(requested, graph, scope).compile();

    assertEquals(1, dataflow.getComputation().size());
    assertNotSame(root, dataflow.getComputation().getFirst().getObservation());
    assertFalse(dataflow.getComputation().getFirst().getObservation().getGeometry() instanceof Scale);
    assertEquals(Actuator.Type.REFERENCE, dataflow.getComputation().getFirst().getActuatorType());
    assertFalse(dataflow.getCoverage() instanceof Scale);
    assertFalse(dataflow.getCoverage() instanceof Coverage);
    assertEquals(requirements, dataflow.getRequirements());
    assertEquals(
        0.75,
        ((org.integratedmodelling.common.runtime.DataflowImpl) dataflow).getResolvedCoverage());
  }
}
