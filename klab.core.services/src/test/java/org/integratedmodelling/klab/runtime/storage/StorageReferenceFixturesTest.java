package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Properties;
import org.integratedmodelling.klab.api.data.Data;
import org.junit.jupiter.api.Test;

/** Analytic oracles for later stages; does not claim that spatial/keyed mediation is implemented. */
class StorageReferenceFixturesTest {
  private Properties fixture() throws Exception {
    var result = new Properties();
    try (var input = getClass().getResourceAsStream("/storage/stage0/reference.properties")) {
      assertNotNull(input);
      result.load(input);
    }
    return result;
  }

  private double[] values(Properties p, String key) {
    return Arrays.stream(p.getProperty(key).split(",")).mapToDouble(Double::parseDouble).toArray();
  }

  @Test
  void numberedRasterCrossesUnevenShardBoundariesInBothRequestedTraversals() throws Exception {
    var p = fixture();
    var raster = values(p, "raster.values");
    var ends = values(p, "raster.shard.ends");
    for (var curve : new Data.FillCurve[] {Data.FillCurve.D2_YX, Data.FillCurve.D2_XInvY}) {
      var expected = values(p, curve == Data.FillCurve.D2_YX ? "raster.yx" : "raster.xInvY");
      var touched = new boolean[ends.length];
      for (int step = 0; step < raster.length; step++) {
        int offset = (int) curve.offset(step, new long[] {5, 3});
        int shard = 0;
        while (offset >= ends[shard]) shard++;
        touched[shard] = true;
        assertEquals(expected[step], raster[offset]);
      }
      for (boolean used : touched) assertTrue(used);
    }
  }

  @Test
  void eventGridHasExplicitPartialOverlapAndNearestElevationOracle() throws Exception {
    var p = fixture();
    var expected = values(p, "overlap.nearest");
    int index = 0;
    for (double x : values(p, "overlap.target.x")) {
      for (double y : values(p, "overlap.target.y")) {
        double value = x >= 3 ? Double.NaN : 100 + 10 * (Math.floor(x) + 0.5) + 2 * y;
        assertEquals(expected[index++], value);
      }
    }
  }

  @Test
  void contextualReferenceValuesDistinguishCellsAndCalendarIntervals() throws Exception {
    var p = fixture();
    var depth = values(p, "precip.depth");
    var area = values(p, "precip.area");
    var volume = values(p, "precip.volume");
    for (int i = 0; i < depth.length; i++) assertEquals(volume[i], depth[i] * 0.001 * area[i]);
    var latitude = values(p, "geographic.latitude");
    var ratio = values(p, "geographic.relative.area");
    double equatorialBand = Math.sin(Math.toRadians(0.5)) - Math.sin(Math.toRadians(-0.5));
    for (int i = 0; i < latitude.length; i++) {
      double band = Math.sin(Math.toRadians(latitude[i] + 0.5)) - Math.sin(Math.toRadians(latitude[i] - 0.5));
      assertEquals(ratio[i], band / equatorialBand, 1e-12);
    }
    var starts = p.getProperty("calendar.start").split(",");
    var ends = p.getProperty("calendar.end").split(",");
    var days = values(p, "calendar.days");
    for (int i = 0; i < starts.length; i++)
      assertEquals(days[i], ChronoUnit.DAYS.between(LocalDate.parse(starts[i]), LocalDate.parse(ends[i])));
  }

  @Test
  void syntheticCodesHaveStableMeaningsAndAnExplicitMissingCode() throws Exception {
    var p = fixture();
    assertEquals("test:storage-worldview@1", p.getProperty("key.worldview"));
    String[] expected = {"test:Forest", "test:Grassland", "test:Forest", null};
    var codes = p.getProperty("key.codes").split(",");
    for (int i = 0; i < codes.length; i++) {
      String semantic = codes[i].equals(p.getProperty("key.missing")) ? null : p.getProperty("key." + codes[i]);
      assertEquals(expected[i], semantic);
    }
    assertNull(p.getProperty("key.99"), "Unknown must not be confused with a real category");
  }
}
