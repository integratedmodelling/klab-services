package org.integratedmodelling.klab.runtime.scale.space;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.concurrent.*;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.WKBReader;

class ShapeEncodingTest {
  @Test void parallelPointAndPolygonEncodingsRemainIndependent() throws Exception {
    var shapes = java.util.List.of(
        ShapeImpl.create("EPSG:4326 POINT (34.62 -8.316)"),
        ShapeImpl.create("EPSG:4326 POLYGON ((33 -7, 35 -7, 35 -9, 33 -9, 33 -7))"));
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(8)) {
      var jobs = new ArrayList<Future<?>>();
      for (int worker = 0; worker < 8; worker++) {
        final var shape = shapes.get(worker % shapes.size());
        jobs.add(workers.submit(() -> {
          start.await();
          for (int repeat = 0; repeat < 500; repeat++) {
            var reader = new WKBReader();
            assertTrue(shape.getJTSGeometry().norm().equalsExact(reader.read(WKBReader.hexToBytes(shape.getWKB()))));
            assertTrue(shape.getJTSGeometry().norm().equalsExact(ShapeImpl.create(shape.asWKB()).getJTSGeometry()));
            var raw = ShapeImpl.wkbEncoder.encode(shape.getJTSGeometry());
            assertTrue(shape.getJTSGeometry().equalsExact(reader.read(WKBReader.hexToBytes(raw))));
          }
          return null;
        }));
      }
      start.countDown();
      for (var job : jobs) job.get(30, TimeUnit.SECONDS);
    }
  }

  @Test void corruptWkbFailsWithItsOriginalCause() {
    var failure = assertThrows(KlabValidationException.class, () -> ShapeImpl.create(
        "0000000000010000000140414F2D404884E940414F2D40488463C020A1828569042EC020A1828569042E"));
    assertTrue(failure.getMessage().contains("WKB"));
    assertNotNull(failure.getCause());
  }
}
