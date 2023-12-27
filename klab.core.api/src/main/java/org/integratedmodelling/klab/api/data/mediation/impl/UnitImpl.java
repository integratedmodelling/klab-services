package org.integratedmodelling.klab.api.data.mediation.impl;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDimension;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDistribution;
import org.integratedmodelling.klab.api.services.UnitService;

import java.util.HashMap;
import java.util.Map;

public class UnitImpl implements Unit {

    private static final long serialVersionUID = -2147231566662236608L;

    String statement;

    // ugly, but the only alternatives are a API-level commitment to services or injection, which
    // causes pain. The idea is that any unit service will call setUnitService() with itself as
    // parameter.
    private static transient UnitService unitService;
    // functional object that contains the actual implementation. can't just use a hash in the
    // service as the same definition may have different contextualization.
    private transient Object functionalUnit;

    private String definition;
    private boolean contextual;
    private boolean unitless;
    private Map<ExtentDimension, ExtentDistribution> aggregatedDimensions = new HashMap<>();

    public UnitImpl() {
    }

    public UnitImpl(String unitStatement) {
        this.definition = unitStatement;
    }

    @Override
    public boolean isCompatible(ValueMediator other) {
        try {
            return unitService.isCompatible(this, (Unit) other);
        } catch (NullPointerException e) {
            throw new KlabInternalErrorException("calling isCompatible() without a unit service");
        }
    }

    @Override
    public Number convert(Number d, ValueMediator scale) {
        try {
            return unitService.convert(d, this, (Unit)scale);
        } catch (NullPointerException e) {
            throw new KlabInternalErrorException("calling convert() without a unit service");
        }
    }

    @Override
    public Number convert(Number value, Locator locator) {
        try {
            return unitService.convert(value, this, locator);
        } catch (NullPointerException e) {
            throw new KlabInternalErrorException("calling convert() without a unit service");
        }
    }

    @Override
    public boolean isContextual() {
        return contextual;
    }

    @Override
    public Unit multiply(Unit unit) {
        try {
            return unitService.multiply(this, unit);
        } catch (NullPointerException e) {
            throw new KlabInternalErrorException("calling convert() without a unit service");
        }
    }

    @Override
    public Unit divide(Unit unit) {
        try {
            return unitService.divide(this, unit);
        } catch (NullPointerException e) {
            throw new KlabInternalErrorException("calling convert() without a unit service");
        }
    }

    @Override
    public Unit scale(double scale) {
        try {
            return unitService.scale(this, scale);
        } catch (NullPointerException e) {
            throw new KlabInternalErrorException("calling convert() without a unit service");
        }
    }

    @Override
    public Map<ExtentDimension, ExtentDistribution> getAggregatedDimensions() {
        return this.aggregatedDimensions;
    }

    @Override
    public Pair<Unit, Unit> splitExtent(ExtentDimension dimension) {
        try {
            return unitService.splitExtent(this, dimension);
        } catch (NullPointerException e) {
            throw new KlabInternalErrorException("calling convert() without a unit service");
        }
    }

    @Override
    public Unit contextualize(Observable observable, Geometry scale) {
        try {
            return unitService.contextualize(this, observable, scale);
        } catch (NullPointerException e) {
            throw new KlabInternalErrorException("calling convert() without a unit service");
        }
    }

    @Override
    public boolean isUnitless() {
        return unitless;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public static void setService(UnitService unitServiceImpl) {
        UnitImpl.unitService = unitServiceImpl;
    }

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public void setContextual(boolean contextual) {
        this.contextual = contextual;
    }

    public void setUnitless(boolean unitless) {
        this.unitless = unitless;
    }

    public void setAggregatedDimensions(Map<ExtentDimension, ExtentDistribution> aggregatedDimensions) {
        this.aggregatedDimensions = aggregatedDimensions;
    }

    public UnitImpl withData(Object data) {
        this.functionalUnit = data;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T data(Class<T> cls) {
        return (T)this.functionalUnit;
    }

}
