package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.data.impl.HistogramImpl;

import java.io.Serializable;
import java.util.List;

/** Histogram object for transport. TODO manage categories, legends, basic statistics etc. */
public interface Histogram extends Serializable {

  interface Bin {
    double getMean();

    double getMin();

    double getMax();

    double getCount();

    double getSum();

    double getSumSquared();

    String getCategory();

    double getWeight();

    double getMissingCount();
  }

  /**
   * If true, the histogram is empty or not reliable and should not be used. Normally arises from
   * no-data states or abnormal value attribution (e.g. data are changed w/o updating the
   * histogram).
   *
   * @return true if the histogram is empty or not reliable, false otherwise
   */
  boolean isEmpty();

  double getMin();

  double getMax();

  double getMissingCount();

  List<Bin> getBins();

  /** Detach an already stable histogram view for transport or persistence. All public values,
   * including bin statistics and missing counts, are copied; no native view is unwrapped. */
  static Histogram forTransport(Histogram source) {
    if (source == null) return null;
    var result = new HistogramImpl();
    result.setEmpty(source.isEmpty());
    result.setMin(source.getMin());
    result.setMax(source.getMax());
    result.setMissingCount(source.getMissingCount());
    for (var bin : source.getBins()) {
      var copy = new HistogramImpl.BinImpl();
      copy.setMean(bin.getMean());
      copy.setMin(bin.getMin());
      copy.setMax(bin.getMax());
      copy.setCount(bin.getCount());
      copy.setSum(bin.getSum());
      copy.setSumSquared(bin.getSumSquared());
      copy.setCategory(bin.getCategory());
      copy.setWeight(bin.getWeight());
      copy.setMissingCount(bin.getMissingCount());
      result.getBins().add(copy);
    }
    return result;
  }

  static Histogram empty() {
    return new HistogramImpl();
  }
}
