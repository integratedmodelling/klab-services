package org.integratedmodelling.common.lang;

import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.Serial;

public class QuantityImpl implements Quantity {

    @Serial
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

    public String toString() {
        return value + (unit == null ? "" : ("." + unit)) + (currency == null ? "" : ("." + currency));
    }

    public static Quantity parse(String specification) {

        QuantityImpl ret = null;
        int dot = specification.indexOf('.');
        if (dot < 0) {
            dot = specification.indexOf(' ');
        }

        if (dot > 0) {
            String number = specification.substring(0,dot);
            String unit = specification.substring(dot +1);
            ret = new QuantityImpl();
            ret.setValue(Utils.Data.parseAsType(number, Double.class));
            if (unit.contains("@")) {
                ret.setCurrency(unit);
            } else {
                ret.setUnit(unit);
            }
        }

        return ret;
    }
}
