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
    
    public CurrencyImpl(String currencyDefinition) {
        this.definition = currencyDefinition;
    }

    @Override
    public boolean isCompatible(ValueMediator other) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Number convert(Number d, ValueMediator scale) {
        // TODO Auto-generated method stub
        return null;
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
        // TODO Auto-generated method stub
        return false;
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
