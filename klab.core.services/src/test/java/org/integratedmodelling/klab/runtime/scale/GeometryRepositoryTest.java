package org.integratedmodelling.klab.runtime.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GeometryRepositoryTest {

  @BeforeAll
  static void configureKlab() {
    ServiceConfiguration.injectInstantiators();
  }

  @Test
  void outerUnionReturnsCachedScale() {
    Geometry first = Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}");
    Geometry second = Geometry.create("T0(1){tend=20,tstart=5,ttype=PHYSICAL}");

    Scale union = GeometryRepository.INSTANCE.outerUnion(first, second);

    assertNotNull(union);
    assertSame(union, union.as(Scale.class));
    assertEquals(0L, union.getTime().getStart().getMilliseconds());
    assertEquals(20L, union.getTime().getEnd().getMilliseconds());
    assertSame(union, GeometryRepository.INSTANCE.outerUnion(first, second));
  }

  @Test
  void timeMergeDoesNotMutateCachedScale() {
    Geometry first = Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}");
    Geometry second = Geometry.create("T0(1){tend=20,tstart=5,ttype=PHYSICAL}");
    Scale cachedFirst = GeometryRepository.INSTANCE.scale(first);

    Scale intersection =
        GeometryRepository.INSTANCE.getMerged(
            first, second, LogicalConnector.INTERSECTION, Scale.class);

    assertNotNull(intersection);
    assertEquals(5L, intersection.getTime().getStart().getMilliseconds());
    assertEquals(10L, intersection.getTime().getEnd().getMilliseconds());
    assertEquals(0L, cachedFirst.getTime().getStart().getMilliseconds());
    assertEquals(10L, cachedFirst.getTime().getEnd().getMilliseconds());
  }
}
