package org.integratedmodelling.klab.runtime.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension.Type;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CoverageImplTest {

  @BeforeAll
  static void configureKlab() {
    ServiceConfiguration.injectInstantiators();
  }

  @Test
  void mergeAcceptsRawGeometryAndComputesCoverage() {
    Scale original =
        GeometryRepository.INSTANCE.scale(
            Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
    Coverage coverage = CoverageImpl.empty(original);
    Geometry incoming = Geometry.create("T0(1){tend=5,tstart=0,ttype=PHYSICAL}");

    Coverage merged = coverage.merge(incoming, LogicalConnector.UNION);

    assertEquals(0.5, merged.getCoverage(), 1e-9);
    assertEquals(0.5, merged.getCoverage(Type.TIME), 1e-9);
    assertEquals(0.5, merged.getGain(), 1e-9);
  }

  @Test
  void emptyCoverageReportsZeroDimensionCoverage() {
    Scale original =
        GeometryRepository.INSTANCE.scale(
            Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
    Coverage coverage = CoverageImpl.empty(original);

    assertEquals(0.0, coverage.getCoverage(Type.TIME), 1e-9);
  }

  @Test
  void disjointUnionDoesNotIncreaseCoverage() {
    Scale original =
        GeometryRepository.INSTANCE.scale(
            Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
    Coverage coverage = CoverageImpl.empty(original);
    Geometry incoming = Geometry.create("T0(1){tend=30,tstart=20,ttype=PHYSICAL}");

    Coverage merged = coverage.merge(incoming, LogicalConnector.UNION);

    assertEquals(0.0, merged.getCoverage(), 1e-9);
    assertEquals(0.0, merged.getGain(), 1e-9);
  }
}
