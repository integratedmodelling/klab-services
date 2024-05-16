package org.integratedmodelling.common.lang;

import org.integratedmodelling.klab.api.collections.impl.LiteralImpl;
import org.integratedmodelling.klab.api.lang.Quantity;

public class QuantityImpl extends LiteralImpl implements Quantity {

    private static final long serialVersionUID = -4049367875348743501L;

    private String unit;
    private String currency;

    @Override
    public Number getValue() {
        return (Number)super.getValue();
    }

    @Override
    public String getUnit() {
        return this.unit;
    }

    @Override
    public String getCurrency() {
        return this.currency;
    }

    public void setValue(Number value) {
        super.setValue(value);
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

}
