package org.integratedmodelling.klab.api.view.modeler.visualization;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;

class ColorRampTest {
  @Test void continuousRampsInterpolateAndClamp() {
    var ramp = ramp("colors", List.of("#000", "#fff"));
    assertEquals(0xff808080, ramp.argb(5, 0, 10));
    assertEquals(0xff000000, ramp.argb(-5, 0, 10));
    assertEquals(0xffffffff, ramp.argb(15, 0, 10));
    assertEquals(0xff808080, ramp.argb(7, 7, 7));
  }

  @Test void centerAnchorsAsymmetricLimitsAndAutomaticSymmetricRange() {
    var ramp = ramp("colors", List.of("red", "white", "green"), "center", 0, "min", -2, "max", 8);
    assertEquals(0xffffffff, ramp.argb(0, -100, 100));
    assertEquals(0xffff8080, ramp.argb(-1, -100, 100));
    assertEquals(0xff80c080, ramp.argb(4, -100, 100));
    var automatic = ramp("palette", "red-white-green", "center", 0);
    assertEquals(0xffff8080, automatic.argb(-4, -2, 8));
    assertEquals(0xffffffff, automatic.argb(0, 0, 0));
  }

  @Test void absoluteStopsAreIndependentOfObservedRange() {
    var ramp = ramp("stops", Map.of(-2, "blue", 0, "white", 8, "red"));
    assertEquals(0xff8080ff, ramp.argb(-1, 100, 200));
    assertEquals(0xffff8080, ramp.argb(4, 100, 200));
  }

  @Test void conceptsAndNumericValuesUseExactCategories() {
    var concept = new KimConceptImpl();
    concept.setUrn("land:Forest");
    var ramp = ramp("values", List.of(List.of("land:Forest", List.of(34, 139, 34)), List.of(1, "#00f")),
        "unknown", "gray");
    assertEquals(0xff228b22, ramp.argb(concept, 0, 0));
    assertEquals(0xff0000ff, ramp.argb(1L, 0, 0));
    assertEquals(0xff808080, ramp.argb("land:Unknown", 0, 0));
    assertEquals(0, ramp.argb(null, 0, 0));
  }

  @Test void publishedPalettesHaveTheirOriginalEndpointsAndCanBeReversed() {
    assertEquals(0xff440154, ramp("palette", "viridis").argb(0, 0, 1));
    assertEquals(0xfffde725, ramp("palette", "viridis").argb(1, 0, 1));
    assertEquals(0xfffde725, ramp("palette", "viridis", "reverse", true).argb(0, 0, 1));
    for (var palette : ColorRamp.paletteNames()) {
      assertNotEquals(0, ramp("palette", palette).argb(0.5, 0, 1));
    }
  }

  @Test void cssAndRgbAlphaHaveExplicitChannelConventions() {
    assertEquals(0x80112233, ColorRamp.color("#11223380"));
    assertEquals(0x88112233, ColorRamp.color("#1238"));
    assertEquals(0x80112233, ColorRamp.color("rgba(17,34,51,0.5)"));
    assertEquals(0x80112233, ColorRamp.color(List.of(17, 34, 51, 128)));
    assertEquals(0xff112233, ColorRamp.color("rgb(17,34,51)"));
    assertThrows(IllegalArgumentException.class, () -> ColorRamp.color(List.of(256, 0, 0)));
  }

  @Test void invalidSpecificationsFailInsteadOfSilentlySelectingAnotherPalette() {
    assertThrows(IllegalArgumentException.class, () -> ramp("palette", "unknown"));
    assertThrows(IllegalArgumentException.class, () -> ramp("colors", List.of("red")));
    assertThrows(IllegalArgumentException.class, () -> ramp("center", 0, "min", 1, "max", 2));
    assertThrows(IllegalArgumentException.class, () -> ramp("min", 0));
    assertThrows(IllegalArgumentException.class, () -> ramp("palette", "gray", "stops", Map.of(0, "red")));
    assertThrows(IllegalArgumentException.class, () -> ramp("reverse", "true"));
    assertThrows(IllegalArgumentException.class, () -> ramp("values", List.of(List.of(1, "red"), List.of(1L, "blue"))));
  }

  @Test void effectiveObservationAnnotationAndNoDataAreRespected() {
    var observation = new ObservationImpl();
    observation.mergeAnnotations(List.of(Annotation.of("colormap", "palette", "viridis")), 0);
    observation.mergeAnnotations(List.of(Annotation.of("colormap", "colors", List.of("black", "white"), "nodata", "#f008")), 2);
    var ramp = ColorRamp.fromObservation(observation);
    assertEquals(0xff808080, ramp.argb(5, 0, 10));
    assertEquals(0x88ff0000, ramp.argb(Double.NaN, 0, 10));
    assertEquals(0x88ff0000, ramp.argb(Double.POSITIVE_INFINITY, 0, 10));
  }

  @Test void terrainAnchorsSeaLevelBetweenBathymetryAndTopography() {
    var terrain = ramp("palette", "terrain", "center", 0, "min", -8000, "max", 4000);
    assertEquals(0xff081d58, terrain.argb(-8000, -1, 1));
    assertEquals(0xffe8e6b5, terrain.argb(0, -1, 1));
    assertEquals(0xff78a75a, terrain.argb(1000, -1, 1));
    assertEquals(0xfff5f5f5, terrain.argb(4000, -1, 1));
    var automatic = ramp("palette", "terrain", "center", 0);
    assertEquals(0xffe8e6b5, automatic.argb(0, -8000, 4000));
    assertEquals(0xffe8e6b5, automatic.argb(0, 0, 0));
  }

  private ColorRamp ramp(Object... parameters) {
    return ColorRamp.fromAnnotation(Annotation.of("colormap", parameters));
  }
}
