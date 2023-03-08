package org.integratedmodelling.klab.api.collections;

import java.io.Serializable;

import org.integratedmodelling.klab.api.collections.impl.LiteralImpl;
import org.integratedmodelling.klab.api.data.ValueType;

/**
 * A serializable container for a literal, either a POD or a k.IM/k.Actors literal such as an
 * observable, table, list or parameter map.
 * <p>
 * Serializes and deserializes correctly using the specially instrumented k.LAB Jackson object
 * mapper in the klab.services.core package.
 * 
 * @author Ferd
 *
 */
public interface Literal extends Serializable {

    ValueType getValueType();

    <T> T get(Class<? extends T> valueClass);
    
    /**
     * Classifies and returns a literal for the passed object.
     * 
     * @param o
     * @return
     */
    public static LiteralImpl of(Object o) {
        LiteralImpl ret = new LiteralImpl();
        ret.setValue(o);
        ret.setValueType(LiteralImpl.classifyLiteral(o));
        return ret;
    }
}
