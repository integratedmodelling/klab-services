package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.dynatrace.dynahist.layout.OpenTelemetryExponentialBucketsLayout;
import java.util.Map;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;

class HistogramUtilsTest {

  @Test
  void missingCellsSurviveHistogramTransportIncludingAllNoData() {
    var dynamic = com.dynatrace.dynahist.Histogram.createDynamic(
        OpenTelemetryExponentialBucketsLayout.create(1));
    var allMissing = Utils.Data.adaptHistogram(dynamic, 3);
    assertTrue(allMissing.isEmpty());
    assertEquals(3, allMissing.getMissingCount());
    dynamic.addValue(2);
    var mixed = Utils.Data.adaptHistogram(dynamic, 3);
    assertEquals(2, mixed.getMissingCount());
    var restored = Utils.Data.deserializeHistogramMap(
        Utils.Data.serializeHistogramMap(Map.of(1L, mixed, 2L, allMissing)));
    assertEquals(2, restored.get(1L).getMissingCount());
    assertEquals(3, restored.get(2L).getMissingCount());
    assertEquals(0, Utils.Data.adaptHistogram(null, 3).getMissingCount());
  }

  @Test
  void constantHistogramIsNotReportedAsEmpty() {
    var dynamic =
        com.dynatrace.dynahist.Histogram.createDynamic(
            OpenTelemetryExponentialBucketsLayout.create(1));
    dynamic.addValue(5, 2);

    var snapshot = Utils.Data.adaptHistogram(dynamic);

    assertFalse(snapshot.isEmpty());
    assertEquals(5, snapshot.getMin());
    assertEquals(5, snapshot.getMax());
    assertEquals(2, snapshot.getBins().stream().mapToDouble(b -> b.getCount()).sum());
  }

  @Test
  void nullHistogramProducesARealEmptySnapshot() {
    assertTrue(Utils.Data.adaptHistogram(null).isEmpty());
  }

  @Test
  void dynamicHistogramRoundTripsThroughItsInternalEncoding() {
    var dynamic =
        com.dynatrace.dynahist.Histogram.createDynamic(
            OpenTelemetryExponentialBucketsLayout.create(1));
    dynamic.addValue(-2).addValue(3).addValue(10);

    var restored = Utils.Data.deserializeHistogram(Utils.Data.serializeHistogram(dynamic));

    assertEquals(dynamic.getTotalCount(), restored.getTotalCount());
    assertEquals(dynamic.getMin(), restored.getMin());
    assertEquals(dynamic.getMax(), restored.getMax());
  }

  @Test
  void fixedHistogramMapRoundTripsWithLongTimestampKeys() {
    var dynamic =
        com.dynatrace.dynahist.Histogram.createDynamic(
            OpenTelemetryExponentialBucketsLayout.create(1));
    dynamic.addValue(4);
    var serialized =
        Utils.Data.serializeHistogramMap(Map.of(123456789L, Utils.Data.adaptHistogram(dynamic)));

    var restored = Utils.Data.deserializeHistogramMap(serialized);

    assertEquals(1, restored.size());
    assertFalse(restored.get(123456789L).isEmpty());
    assertEquals(4, restored.get(123456789L).getMin());
  }
}
