package org.integratedmodelling.klab.runtime.scale.space;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.junit.jupiter.api.Test;

class ShapeImplRobustOverlayTest {
  @org.junit.jupiter.api.Test
  void intersectionsPreservePointAndLineSupport() {
    var region = ShapeImpl.create("EPSG:4326 POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))");
    for (var wkt : java.util.List.of("POINT (1 1)", "LINESTRING (0.5 0.5, 1.5 1.5)")) {
      var member = ShapeImpl.create("EPSG:4326 " + wkt);
      for (var intersection : java.util.List.of(member.intersection(region), region.intersection(member))) {
        org.junit.jupiter.api.Assertions.assertFalse(intersection.isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(member.getGeometryType(), intersection.getGeometryType());
      }
      var remote = ShapeImpl.create("EPSG:4326 POLYGON ((3 3, 4 3, 4 4, 3 4, 3 3))");
      org.junit.jupiter.api.Assertions.assertTrue(member.intersection(remote).isEmpty());
    }
  }

  private static final String BOWTIE =
      "EPSG:4326 POLYGON ((0 0, 2 2, 0 2, 2 0, 0 0))";

  private static final String CLIP =
      "EPSG:4326 POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))";

  @Test
  void emptyOverlaysRetainProjectedCoordinates() {
    var left = ShapeImpl.create("EPSG:3857 POLYGON ((0 0, 100 0, 100 100, 0 100, 0 0))");
    var right = ShapeImpl.create("EPSG:3857 POLYGON ((300 300, 400 300, 400 400, 300 400, 300 300))");
    for (var result : java.util.List.of(left.intersection(right), left.difference(left))) {
      org.junit.jupiter.api.Assertions.assertEquals(left.getProjection(), result.getProjection());
      assertTrue(((ShapeImpl) result).getJTSGeometry().isEmpty());
      assertNotNull(assertDoesNotThrow(() -> result.encode()));
    }
  }

  @Test
  void fixInvalidRepairsSelfCrossingPolygon() {
    ShapeImpl invalid = ShapeImpl.create(BOWTIE);

    Shape fixed = invalid.fixInvalid();

    assertNotNull(fixed);
    assertTrue(((ShapeImpl) fixed).getJTSGeometry().isValid());
  }

  @Test
  void overlayOperationsRepairInvalidInputs() {
    ShapeImpl invalid = ShapeImpl.create(BOWTIE);
    ShapeImpl clip = ShapeImpl.create(CLIP);

    Shape intersection = assertDoesNotThrow(() -> invalid.intersection(clip));
    Shape union = assertDoesNotThrow(() -> invalid.union(clip));
    Shape difference = assertDoesNotThrow(() -> clip.difference(invalid));

    assertTrue(((ShapeImpl) intersection).getJTSGeometry().isValid());
    assertTrue(((ShapeImpl) union).getJTSGeometry().isValid());
    assertTrue(((ShapeImpl) difference).getJTSGeometry().isValid());
  }
}
