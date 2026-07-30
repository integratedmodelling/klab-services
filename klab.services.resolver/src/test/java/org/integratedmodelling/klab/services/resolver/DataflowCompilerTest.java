package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
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

    var rootScale = mock(Scale.class);
    var root = mock(Observation.class);
    when(root.getId()).thenReturn(42L);
    when(root.getName()).thenReturn("root");
    when(root.getObservable()).thenReturn(rootObservable);
    when(root.getGeometry()).thenReturn(rootScale);

    var coverage = mock(Coverage.class);
    when(coverage.getCoverage()).thenReturn(0.75);
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
    assertSame(root, dataflow.getComputation().getFirst().getObservation());
    assertEquals(Actuator.Type.REFERENCE, dataflow.getComputation().getFirst().getActuatorType());
    assertSame(coverage, dataflow.getCoverage());
    assertSame(requirements, dataflow.getRequirements());
    assertEquals(
        0.75,
        ((org.integratedmodelling.common.runtime.DataflowImpl) dataflow).getResolvedCoverage());
  }
}
