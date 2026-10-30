package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UniversalModelCoverageTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test void universalGeneratorResolvesACollectiveAndItsRegionDependency() {
    var root = ResolutionGraph.create(mock(ContextScope.class));
    var scale = GeometryRepository.INSTANCE.scale(Geometry.create(
        "T1(1){tstart=1388534400000,tend=1420070400000,ttype=GRID,tunit=YEAR,tscope=1}"
            + "S2(237,258){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((33.796 -7.086&comma;35.946 -7.086&comma;35.946 -9.41&comma;33.796 -9.41&comma;33.796 -7.086))}"));
    var region = root.createChild(model(), scale);
    var collective = region.createChild(mock(ObservationStrategy.class), Scale.create(Geometry.UNIVERSAL));
    var generator = collective.createChild(model(), Scale.create(Geometry.UNIVERSAL));
    assertTrue(generator.getCoverage().isComplete(), "An unrestricted generator covers its universal request");
    assertTrue(collective.checkCoverage(generator).isComplete());
    collective.merge(generator);
    assertTrue(region.checkCoverage(collective).isComplete(), "Universal dependency must preserve region coverage");
    region.merge(collective);
    assertTrue(region.getCoverage().isComplete());
    assertEquals(2, region.graph().edgeSet().size());
  }

  @Test void universalModelDoesNotMakeAnEmptyDependencyRelevant() {
    var model = ResolutionGraph.create(mock(ContextScope.class))
        .createChild(model(), Scale.create(Geometry.UNIVERSAL));
    assertFalse(model.checkCoverage(ResolutionGraph.empty()).isRelevant());
  }

  private static Model model() {
    var model = mock(Model.class);
    when(model.getCoverage()).thenReturn(Coverage.universal());
    return model;
  }
}
