package org.integratedmodelling.klab.api.data.mediation.impl;

import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.Observable;

/**
 * Default class for currency should use the current runtime for all its functions.
 * 
 * @author Ferd
 *
 */
public class CurrencyImpl implements Currency {

    private static final long serialVersionUID = -2758206005902959560L;

    private String definition;
    
    public CurrencyImpl() {}

    public CurrencyImpl(String currencyDefinition) {
        this.definition = currencyDefinition;
    }

    @Override
    public boolean isCompatible(ValueMediator other) {
        return other instanceof CurrencyImpl currency && java.util.Objects.equals(definition, currency.definition);
    }

    @Override
    public Number convert(Number d, ValueMediator scale) {
        if (isCompatible(scale)) return d;
        throw new UnsupportedOperationException("Currency conversion requires a pinned CurrencyService.Rate in the scan request");
    }

    @Override
    public ValueMediator contextualize(Observable observable, Geometry scale) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Number convert(Number value, Locator locator) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isContextual() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isMonetary() {
        return true;
    }

    @Override
    public Unit getUnit() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

}
