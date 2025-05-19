package org.integratedmodelling.klab.api.data;

import java.io.Serializable;
import java.util.List;

/**
 * Histogram object for transport. TODO manage categories, legends, basic statistics etc.
 */
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
     * If true, the histogram is empty or not reliable and should not be used. Normally arises from no-data states or
     * abnormal value attribution (e.g. data are changed w/o updating the histogram).
     *
     * @return
     */
    boolean isEmpty();

    double getMin();

    double getMax();

    double getMissingCount();

    List<Bin> getBins();
}
