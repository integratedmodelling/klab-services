package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.lang.kim.KimQuantity;

import java.io.Serial;

public class KimQuantityImpl /*extends KimStatementImpl*/ implements KimQuantity {

    @Serial
    private static final long serialVersionUID = -8532532479815240609L;

    private Number value;
    private String unit;
    private String currency;

    @Override
    public Number getValue() {
        return this.value;
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
