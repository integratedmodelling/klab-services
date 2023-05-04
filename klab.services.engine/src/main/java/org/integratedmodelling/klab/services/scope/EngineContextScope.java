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

public class EngineContextScope extends EngineSessionScope implements ContextScope {

    private static final long serialVersionUID = -3241953358893122142L;

    Identity observer;
    DirectObservation context;
    Set<String> scenarios;
    EngineContextScope parent;
    Geometry geometry;
    String token;
    Map<Observable, Observation> catalog = new HashMap<>();

    EngineContextScope(EngineSessionScope parent) {
        super(parent);
        this.observer = parent.getUser();
        /*
         * TODO choose the services if this context or user requires specific ones
         */
    }

    private EngineContextScope(EngineContextScope parent) {
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
    public EngineContextScope withScenarios(String... scenarios) {
        EngineContextScope ret = new EngineContextScope(this);
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
    public EngineContextScope withObserver(Identity observer) {
        EngineContextScope ret = new EngineContextScope(this);
        ret.observer = observer;
        return ret;
    }

    @Override
    public EngineContextScope within(DirectObservation context) {
        EngineContextScope ret = new EngineContextScope(this);
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
