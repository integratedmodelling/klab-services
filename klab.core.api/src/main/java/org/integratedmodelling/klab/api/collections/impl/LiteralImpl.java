package org.integratedmodelling.klab.api.collections.impl;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

import java.util.List;
import java.util.Map;

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
        } else if (o instanceof Double d) {
            return Double.isNaN(d) ? ValueType.NODATA : ValueType.DOUBLE;
        } else if (o instanceof Number number) {
            return Double.isNaN(number.doubleValue()) ? ValueType.NODATA : ValueType.NUMBER;
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
        } else if (o instanceof Map<?, ?>) {
            return ValueType.MAP;
        } else if (o instanceof List<?>) {
            return ValueType.LIST;
        } else if (o instanceof Observation) {
            return ValueType.OBSERVATION;
        } else if (o instanceof Quantity) {
            return ValueType.QUANTITY;
        }
        // TODO continue

        System.out.println("PORCO FUNGO NON SO CHE CAZZO È: " + (o == null ? null :
                                                                 o.getClass().getCanonicalName()));

        return o == null ? ValueType.NODATA : ValueType.OBJECT;
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
