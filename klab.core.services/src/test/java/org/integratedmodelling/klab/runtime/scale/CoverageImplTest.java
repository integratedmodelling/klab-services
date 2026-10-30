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

  @Test
  void universalCoverageSurvivesCopiesAndIntersection() {
    var universal = (CoverageImpl) Coverage.universal();
    assertEquals(1, new CoverageImpl(universal).getCoverage());
    assertEquals(1, universal.withGain(0).getCoverage());
    assertEquals(1, universal.merge(Coverage.universal(), LogicalConnector.INTERSECTION).getCoverage());
  }

  @Test
  void universalDependencyDoesNotEraseTheRequestedRegion() {
    Scale region = GeometryRepository.INSTANCE.scale(Geometry.create(
        "T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
    var full = Coverage.create(region, 1);
    var universal = Coverage.universal().merge(Coverage.universal(), LogicalConnector.INTERSECTION);
    assertEquals(1, full.merge(universal, LogicalConnector.INTERSECTION).getCoverage());
    assertEquals(0, Coverage.empty().merge(Coverage.universal(), LogicalConnector.INTERSECTION).getCoverage());
  }

  @BeforeAll
  static void configureKlab() {
    ServiceConfiguration.injectInstantiators();
  }

  @Test
  void griddedRegionRetainsFullCoverageThroughDependencyIntersection() {
    Scale region = GeometryRepository.INSTANCE.scale(Geometry.create(
        "T1(1){tstart=1388534400000,tend=1420070400000,ttype=GRID,tunit=YEAR,tscope=1}"
            + "S2(237,258){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((33.796 -7.086&comma;35.946 -7.086&comma;35.946 -9.41&comma;33.796 -9.41&comma;33.796 -7.086))}"));
    Coverage model = Coverage.create(region, 1);
    Coverage dependency = Coverage.create(region, 0).merge(Coverage.create(region, 1), LogicalConnector.UNION);
    assertEquals(1, dependency.getCoverage(), 1e-9);
    assertEquals(1, model.merge(dependency, LogicalConnector.INTERSECTION).getCoverage(), 1e-9);
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
