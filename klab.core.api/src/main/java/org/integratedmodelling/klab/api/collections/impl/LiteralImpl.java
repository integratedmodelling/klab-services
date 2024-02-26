package org.integratedmodelling.klab.api.collections.impl;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

public class LiteralImpl implements Literal {

    private static final long serialVersionUID = -4099902319334878080L;

    private ValueType valueType;
    private Object value;

    /**
     * Classifies and returns a literal for the passed object.
     * 
     * @param o
     * @return
     */
    public static LiteralImpl of(Object o) {
        LiteralImpl ret = new LiteralImpl();
        ret.value = o;
        ret.valueType = classifyLiteral(o);
        return ret;
    }

    public static ValueType classifyLiteral(Object o) {
        if (o instanceof Integer) {
            return ValueType.INTEGER;
        } else if (o instanceof Double) {
            return ValueType.DOUBLE;
        } else if (o instanceof Number) {
            return ValueType.NUMBER;
        } else if (o instanceof String) {
            return ValueType.STRING;
        } else if (o instanceof Boolean) {
            return ValueType.BOOLEAN;
        } else if (o instanceof NumericRangeImpl) {
            return ValueType.RANGE;
        } else if (o instanceof KimObservable) {
            return ValueType.OBSERVABLE;
        } else if (o instanceof KimConcept) {
            return ValueType.CONCEPT;
        } // TODO continue
        return null;
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
