package org.integratedmodelling.common.lang;

import org.integratedmodelling.klab.api.lang.Quantity;

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

        if (specification == null) return null;
        var match=java.util.regex.Pattern.compile("^([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:[eE][+-]?\\d+)?)[.\\s]+(.+)$")
            .matcher(specification.trim());
        if (!match.matches()) return null;
        var ret=new QuantityImpl();
        ret.setValue(Double.parseDouble(match.group(1)));
        String unit=match.group(2).trim();
        if (unit.contains("@")) ret.setCurrency(unit); else ret.setUnit(unit);
        return ret;
    }
}
