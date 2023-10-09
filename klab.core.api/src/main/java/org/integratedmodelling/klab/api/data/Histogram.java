package org.integratedmodelling.klab.api.data;

import java.io.Serializable;

/**
 * Histogram object for transport. TODO manage categories, legends, basic statistics etc.
 */
public interface Histogram extends Serializable {
    int[] getBins();

    double[] getBoundaries();

    /**
     * If true, the histogram is empty or not reliable and should not be used. Normally arises from no-data states or
     * abnormal value attribution (e.g. data are changed w/o updating the histogram).
     *
     * @return
     */
    boolean isEmpty();

    double getMin();

    double getMax();
}
