package org.integratedmodelling.klab.runtime.scale.space;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.junit.jupiter.api.Test;

class ShapeImplRobustOverlayTest {

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
