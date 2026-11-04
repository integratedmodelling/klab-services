package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.impl.HistogramImpl;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;

class HistogramReadViewTransportTest {
 static HistogramImpl histogram() {
  var histogram=new HistogramImpl();histogram.setEmpty(false);histogram.setMin(2);histogram.setMax(8);histogram.setMissingCount(3);
  var bin=new HistogramImpl.BinImpl();bin.setMin(2);bin.setMax(8);bin.setMean(5);bin.setCount(4);bin.setSum(20);bin.setSumSquared(116);bin.setCategory("category");bin.setWeight(0.75);bin.setMissingCount(2);
  histogram.getBins().add(bin);return histogram;
 }
 static Observation readView(Observation source)throws Exception {
  var type=Arrays.stream(DigitalTwinImpl.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("ObservationReadView")).findFirst().orElseThrow();
  var method=type.getDeclaredMethod("of",Observation.class);method.setAccessible(true);return (Observation)method.invoke(null,source);
 }
 static void equalHistogram(Histogram expected,Histogram actual) {
  assertEquals(expected.isEmpty(),actual.isEmpty());assertEquals(expected.getMin(),actual.getMin());assertEquals(expected.getMax(),actual.getMax());assertEquals(expected.getMissingCount(),actual.getMissingCount());
  assertEquals(expected.getBins().size(),actual.getBins().size());
  for(int n=0;n<expected.getBins().size();n++) {
   var a=expected.getBins().get(n);var b=actual.getBins().get(n);
   assertEquals(a.getMean(),b.getMean());assertEquals(a.getMin(),b.getMin());assertEquals(a.getMax(),b.getMax());assertEquals(a.getCount(),b.getCount());assertEquals(a.getSum(),b.getSum());assertEquals(a.getSumSquared(),b.getSumSquared());assertEquals(a.getCategory(),b.getCategory());assertEquals(a.getWeight(),b.getWeight());assertEquals(a.getMissingCount(),b.getMissingCount());
  }
 }
 @Test void nativeReadProjectionUsesExplicitSnapshotsWithoutWeakeningLocalViewGuard()throws Exception {
  var original=histogram();var source=new ObservationImpl();source.setId(42);source.setHistograms(Map.of(0L,original));
  var view=readView(source);assertInstanceOf(LocalView.class,view.getHistograms().get(0L));
  assertThrows(IllegalArgumentException.class,()->Utils.Json.asString(view.getHistograms()));
  var persisted=Utils.Data.deserializeHistogramMap(Utils.Data.serializeHistogramMap(view.getHistograms()));
  var transported=Observation.forTransport(view);assertDoesNotThrow(()->Utils.Json.asString(transported));
  equalHistogram(original,persisted.get(0L));equalHistogram(original,transported.getHistograms().get(0L));
  assertFalse(transported.getHistograms().get(0L) instanceof LocalView);
  original.setMissingCount(99);((HistogramImpl.BinImpl)original.getBins().getFirst()).setCount(100);
  assertEquals(3,persisted.get(0L).getMissingCount());assertEquals(4,transported.getHistograms().get(0L).getBins().getFirst().getCount());
 }
 @Test void directSnapshotCopiesBinsAndRetainsEmptyMissingState() {
  var source=histogram();var copy=Histogram.forTransport(source);equalHistogram(source,copy);
  assertNotSame(source.getBins().getFirst(),copy.getBins().getFirst());source.getBins().clear();assertEquals(1,copy.getBins().size());
  source.setEmpty(true);source.setMissingCount(7);var empty=Histogram.forTransport(source);assertTrue(empty.isEmpty());assertEquals(7,empty.getMissingCount());assertNull(Histogram.forTransport(null));
 }
}
