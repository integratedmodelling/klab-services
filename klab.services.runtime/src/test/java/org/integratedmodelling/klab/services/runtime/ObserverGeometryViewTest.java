package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ObserverGeometryViewTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test void projectedShapeIsExportedInLongitudeLatitude() {
    var geometry = Geometry.create(ShapeImpl.create("EPSG:3857 POINT (111319.49079327357 0)").encode());
    var json = Utils.Json.parseObject(RuntimeService.observerGeoJson(geometry), Map.class);
    assertEquals("Point", json.get("type"));
    var coordinates = (List<?>) json.get("coordinates");
    assertEquals(1.0, ((Number) coordinates.get(0)).doubleValue(), 0.00001);
    assertEquals(0.0, ((Number) coordinates.get(1)).doubleValue(), 0.00001);
  }

  @Test void missingSpaceRemainsMissing() {
    assertNull(RuntimeService.observerGeoJson(null));
    assertNull(RuntimeService.observerGeoJson(Geometry.UNIVERSAL));
    assertNull(RuntimeService.observerGeoJson(Geometry.create("T0(1){tstart=10,tend=20,ttype=PHYSICAL}")));
  }
}
