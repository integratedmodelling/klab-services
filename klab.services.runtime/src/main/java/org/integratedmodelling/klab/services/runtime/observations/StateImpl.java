package org.integratedmodelling.klab.services.runtime.observations;

import java.util.Iterator;

import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.data.mediation.classification.DataKey;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.State;

public class StateImpl extends ObservationImpl implements State {

    @Override
    public Object get(Locator index) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> T get(Locator index, Class<T> cls) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long set(Locator index, Object value) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long size() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public DataKey getDataKey() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> T aggregate(Locator geometry, Class<? extends T> cls) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ValuePresentation getValuePresentation() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public State as(Type type) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> Iterator<T> iterator(Locator index, Class<? extends T> cls) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public State in(ValueMediator mediator) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public State at(Locator locator) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object aggregate(Locator... locators) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void fill(Object value) {
        // TODO Auto-generated method stub
        
    }

}
