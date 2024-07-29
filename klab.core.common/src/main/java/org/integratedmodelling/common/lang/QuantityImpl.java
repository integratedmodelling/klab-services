package org.integratedmodelling.common.lang;

import org.integratedmodelling.klab.api.lang.Quantity;

public class QuantityImpl implements Quantity {

    private static final long serialVersionUID = -4049367875348743501L;

    private String unit;
    private String currency;
    private Number value;

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

    public void setValue(Number value) {
        this.value = value;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

}
