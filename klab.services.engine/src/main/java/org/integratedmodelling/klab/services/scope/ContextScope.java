package org.integratedmodelling.klab.services.scope;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.Relationship;
import org.integratedmodelling.klab.api.knowledge.observation.scope.KContextScope;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;

public class ContextScope extends SessionScope implements KContextScope {

    private static final long serialVersionUID = -3241953358893122142L;

    Identity observer;
    DirectObservation context;
    Set<String> scenarios;
    ContextScope parent;
    Geometry geometry;
    String token;
    Map<Observable, Observation> catalog = new HashMap<>();

    ContextScope(SessionScope parent) {
        super(parent);
        this.observer = parent.getUser();
    }

    private ContextScope(ContextScope parent) {
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
    public ContextScope withScenarios(String... scenarios) {
        ContextScope ret = new ContextScope(this);
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
    public ContextScope withObserver(Identity observer) {
        ContextScope ret = new ContextScope(this);
        ret.observer = observer;
        return ret;
    }

    @Override
    public ContextScope within(DirectObservation context) {
        ContextScope ret = new ContextScope(this);
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
