package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.impl.LiteralImpl;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.impl.kim.KimLiteralImpl;

public interface KimLiteral extends KimAsset {

    ValueType getValueType();

    <T> T get(Class<? extends T> valueClass);

    /**
     * Classifies and returns a literal for the passed object.
     *
     * @param o
     * @return
     */
    static KimLiteral of(Object o) {
        if (o instanceof KimLiteral literal) {
            return literal;
        }
        KimLiteralImpl ret = new KimLiteralImpl();
        ret.setValue(o);
        ret.setValueType(LiteralImpl.classifyLiteral(o));
        return ret;
    }
}
