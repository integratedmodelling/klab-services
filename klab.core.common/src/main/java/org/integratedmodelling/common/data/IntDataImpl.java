package org.integratedmodelling.common.data;

import java.util.PrimitiveIterator;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.common.data.Instance;

public class IntDataImpl extends BaseDataImpl implements PrimitiveIterator.OfInt {

    public IntDataImpl(Observable observable, Geometry geometry, String name, Instance instance) {
        super(observable, geometry, name, instance);
    }

    @Override
    public int nextInt() {
        return 0;
    }

    @Override
    public boolean hasNext() {
        return false;
    }
}
