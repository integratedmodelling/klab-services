package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ObservationCoverageCompilationTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  private static final Geometry WHOLE = Geometry.create(
      "S2(4,1){proj=EPSG:3857,bbox=[0 400 0 100]}");
  private static final Geometry LEFT = Geometry.create(
      "S2(2,1){proj=EPSG:3857,bbox=[0 200 0 100]}");
  private static final Geometry RIGHT = Geometry.create(
      "S2(2,1){proj=EPSG:3857,bbox=[200 400 0 100]}");

  private List<Actuator> compile(List<Geometry> supports) {
    var scope = mock(ContextScope.class);
    when(scope.getService(Reasoner.class)).thenReturn(mock(Reasoner.class));
    var graph = ResolutionGraph.create(scope);
    var concept = new ConceptImpl(); concept.setUrn("test:Elevation"); concept.setName("Elevation");
    concept.getType().add(SemanticType.QUALITY);
    var observation = new ObservationImpl(); observation.setId(-10); observation.setGeometry(WHOLE);
    observation.setObservable(ObservableImpl.promote(concept, null));
    graph.graph().addVertex(observation);
    var scale = GeometryRepository.INSTANCE.scale(WHOLE);
    for (var support : supports) {
      var strategy = mock(ObservationStrategy.class);
      graph.graph().addVertex(strategy);
      var contribution = support == null ? null : Coverage.create(scale, 0).merge(
          GeometryRepository.INSTANCE.scale(support), org.integratedmodelling.klab.api.lang.LogicalConnector.UNION);
      graph.graph().addEdge(observation, strategy, new ResolutionGraph.ResolutionEdge(contribution, null));
    }
    var compiler = spy(new DataflowCompiler(null, graph, scope));
    // This test isolates transport of already accepted resolution coverage, not model compilation.
    doNothing().when(compiler).compileStrategy(any(), any(), any(), any());
    return compiler.compileObservation(observation, WHOLE, null, null);
  }

  @Test void partialContributionIsCarriedToTheChildObservationAndActuator() {
    var actuators = compile(List.of(LEFT));
    assertEquals(1, actuators.size());
    assertEquals(0.5, assertInstanceOf(org.integratedmodelling.common.runtime.ActuatorImpl.class,
        actuators.getFirst()).getResolvedCoverage(), 1e-8);
    assertEquals(0.5, actuators.getFirst().getObservation().getResolvedCoverage(), 1e-8);
    var transported = org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
        org.integratedmodelling.klab.utilities.Utils.Json.asString(actuators.getFirst()), Actuator.class);
    assertEquals(0.5, transported.getObservation().getResolvedCoverage(), 1e-8);
  }

  @Test void overlappingContributionsAreUnionedRatherThanAdded() {
    for (var actuator : compile(List.of(LEFT, LEFT)))
      assertEquals(0.5, actuator.getObservation().getResolvedCoverage(), 1e-8);
    for (var actuator : compile(List.of(LEFT, RIGHT)))
      assertEquals(1.0, actuator.getObservation().getResolvedCoverage(), 1e-8);
  }

  @Test void geometryWithoutAnAcceptedCoverageContributionRemainsUnknown() {
    var actuator = compile(java.util.Collections.singletonList(null)).getFirst();
    assertEquals(0, actuator.getObservation().getResolvedCoverage());
  }
}
