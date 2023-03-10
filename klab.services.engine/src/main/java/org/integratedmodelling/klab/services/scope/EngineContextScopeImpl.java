package org.integratedmodelling.klab.services.scope;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.Relationship;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;

public class EngineContextScopeImpl extends EngineSessionScopeImpl implements ContextScope {

    private static final long serialVersionUID = -3241953358893122142L;

    Identity observer;
    DirectObservation context;
    Set<String> scenarios;
    EngineContextScopeImpl parent;
    Geometry geometry;
    String token;
    Map<Observable, Observation> catalog = new HashMap<>();

    EngineContextScopeImpl(EngineSessionScopeImpl parent) {
        super(parent);
        this.observer = parent.getUser();
    }

    private EngineContextScopeImpl(EngineContextScopeImpl parent) {
        super(parent);
        this.parent = parent;
        this.observer = parent.observer;
        this.context = parent.context;
    }

    @Override
    public DirectObservation getContextObservation() {
        return this.context;
    }

    @Override
    public Identity getObserver() {
        return this.observer;
    }

    @Override
    public Geometry getGeometry() {
        return geometry;
    }

    @Override
    public EngineContextScopeImpl withScenarios(String... scenarios) {
        EngineContextScopeImpl ret = new EngineContextScopeImpl(this);
        if (scenarios == null) {
            ret.scenarios = null;
        }
        this.scenarios = new HashSet<>();
        for (String scenario : scenarios) {
            ret.scenarios.add(scenario);
        }
        return ret;
    }

    @Override
    public EngineContextScopeImpl withObserver(Identity observer) {
        EngineContextScopeImpl ret = new EngineContextScopeImpl(this);
        ret.observer = observer;
        return ret;
    }

    @Override
    public EngineContextScopeImpl within(DirectObservation context) {
        EngineContextScopeImpl ret = new EngineContextScopeImpl(this);
        ret.context = context;
        return ret;
    }

    @Override
    public Future<Observation> observe(Object... observables) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Provenance getProvenance() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Report getReport() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Dataflow<?> getDataflow() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DirectObservation getParentOf(Observation observation) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Observation> getChildrenOf(Observation observation) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Relationship> getOutgoingRelationships(DirectObservation observation) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Relationship> getIncomingRelationships(DirectObservation observation) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<Observable, Observation> getCatalog() {
        return catalog;
    }

}
