package org.integratedmodelling.klab.runtime.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void spatialEncodingAddsBoundingBoxAndCanonicalizesShape() {
    Geometry clockwise =
        Geometry.create(
            "s2(1,1){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 1&comma;1 1&comma;1 0&comma;0 0))}");
    Geometry counterClockwise =
        Geometry.create(
            "s2(1,1){shape=EPSG:4326 POLYGON ((0 0&comma;1 0&comma;1 1&comma;0 1&comma;0 0)),proj=EPSG:4326}");

    Geometry canonicalClockwise = GeometryRepository.INSTANCE.sanitize(clockwise);
    Geometry canonicalCounterClockwise = GeometryRepository.INSTANCE.sanitize(counterClockwise);

    assertTrue(canonicalClockwise.encode().contains("bbox=[0.0 1.0 0.0 1.0]"));
    assertEquals(canonicalClockwise.encode(), canonicalCounterClockwise.encode());
    assertEquals(canonicalClockwise.key(), canonicalCounterClockwise.key());
  }

  @Test
  void persistedGeometryDefinitionLoadsWithoutRecursingIntoItsOwnCacheEntry() {
    var encoded = "T0(1){tend=987654321,tstart=123456789,ttype=PHYSICAL}";

    Geometry geometry = GeometryRepository.INSTANCE.get(encoded, Geometry.class);
    Scale scale = GeometryRepository.INSTANCE.get(encoded, Scale.class);

    assertNotNull(geometry);
    assertNotNull(scale);
    assertEquals(geometry.key(), scale.as(Geometry.class).key());
    assertEquals(
        geometry.key(), GeometryRepository.INSTANCE.get(encoded, Geometry.class).key());
    assertEquals(scale.encode(), GeometryRepository.INSTANCE.get(encoded, Scale.class).encode());
  }
}
