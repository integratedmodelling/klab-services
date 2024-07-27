package org.integratedmodelling.common.lang.kim;

import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.kim.KimQuantity;

import java.io.Serial;

public class KimQuantityImpl extends KimAssetImpl implements KimQuantity {

    @Serial
    private static final long serialVersionUID = -8532532479815240609L;

    private Number value;
    private String unit;
    private String currency;

    @Override
    public Number getValue() {
        return value;
    }

    @Override
    public String getUnit() {
        return this.unit;
    }

    @Override
    public String getCurrency() {
        return this.currency;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setValue(Number value) {
        this.value = value;
    }

    //    @Override
//    public ValueType getValueType() {
//        // TODO classify which number
//        return ValueType.DOUBLE;
//    }
//
//    @Override
//    public <T> T getUnparsedValue(Class<? extends T> valueClass) {
//        return Number.class.isAssignableFrom(valueClass) ? (T)getValue() : null;
//    }
}
