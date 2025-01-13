package org.integratedmodelling.common.data;

import java.util.PrimitiveIterator;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.common.data.Instance;

public class LongDataImpl extends BaseDataImpl implements PrimitiveIterator.OfLong {

    public LongDataImpl(Observable observable, Geometry geometry, String name, Instance instance) {
        super(observable, geometry, name, instance);
    }
    @Override
    public boolean hasStates() {
        return true;
    }

    @Override
    public long nextLong() {
        return 0;
    }

    @Override
    public boolean hasNext() {
        return false;
    }
}
