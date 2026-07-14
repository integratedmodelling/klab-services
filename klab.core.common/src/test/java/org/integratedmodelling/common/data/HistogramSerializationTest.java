package org.integratedmodelling.common.data;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.impl.HistogramImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;

class HistogramSerializationTest {

  @Test
  void histogramBinsRoundTripAsConcreteSerializableBins() {
    var bin = new HistogramImpl.BinImpl();
    bin.setMin(1);
    bin.setMax(2);
    bin.setCount(3);
    var histogram = new HistogramImpl();
    histogram.setEmpty(false);
    histogram.setMin(1);
    histogram.setMax(2);
    histogram.setBins(List.of(bin));

    var restored =
        Utils.Json.parseObject(Utils.Json.asString(histogram), HistogramImpl.class);

    assertFalse(restored.isEmpty());
    assertEquals(1, restored.getBins().size());
    assertInstanceOf(HistogramImpl.BinImpl.class, restored.getBins().getFirst());
    assertEquals(3, restored.getBins().getFirst().getCount());
  }

  @Test
  void observationCommunicatesHistogramsByTemporalSlice() {
    var histogram = new HistogramImpl();
    histogram.setEmpty(false);
    histogram.setMin(10);
    histogram.setMax(10);
    var observation = new ObservationImpl();
    observation.setHistograms(Map.of(1000L, histogram));

    var restored =
        Utils.Json.parseObject(Utils.Json.asString(observation), ObservationImpl.class);

    assertEquals(1, restored.getHistograms().size());
    assertInstanceOf(HistogramImpl.class, restored.getHistograms().get(1000L));
    assertEquals(10, restored.getHistograms().get(1000L).getMin());
  }
}
