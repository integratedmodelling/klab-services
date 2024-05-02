package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.collections.impl.LiteralImpl;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimLiteral;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

public class KimLiteralImpl extends KimAssetImpl implements KimLiteral {

    private ValueType valueType;
    private Object value;

    /**
     * Classifies and returns a literal for the passed object.
     *
     * @param o
     * @return
     */
    public static KimLiteralImpl of(Object o) {
        KimLiteralImpl ret = new KimLiteralImpl();
        ret.value = o;
        ret.valueType = classifyLiteral(o);
        return ret;
    }

    public static ValueType classifyLiteral(Object o) {
        return LiteralImpl.classifyLiteral(o);
    }

    @Override
    public ValueType getValueType() {
        return valueType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<? extends T> valueClass) {
        return (T) value;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setValueType(ValueType valueType) {
        this.valueType = valueType;
    }

}
