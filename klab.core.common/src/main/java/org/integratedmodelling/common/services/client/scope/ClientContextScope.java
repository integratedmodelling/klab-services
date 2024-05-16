package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.Relationship;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public abstract class ClientContextScope extends ClientSessionScope implements ContextScope {

    public ClientContextScope(ClientScope parent, String contextId, RuntimeService runtimeService) {
        super(parent, contextId, runtimeService);
    }

    @Override
    public URL getUrl() {
        return null;
    }

    @Override
    public Identity getObserver() {
        return null;
    }

    @Override
    public DirectObservation getContextObservation() {
        return null;
    }

    @Override
    public ContextScope withObserver(Identity observer) {
        return null;
    }

    @Override
    public ContextScope withScenarios(String... scenarios) {
        return null;
    }

    @Override
    public ContextScope withResolutionNamespace(String namespace) {
        return null;
    }

    @Override
    public ContextScope withGeometry(Geometry geometry) {
        return null;
    }

    @Override
    public ContextScope within(DirectObservation contextObservation) {
        return null;
    }

    @Override
    public ContextScope with(Concept abstractTrait, Concept concreteTrait) {
        return null;
    }

    @Override
    public ContextScope connect(URL remoteContext) {
        return null;
    }

    @Override
    public Future<Observation> observe(Object... observables) {
        return null;
    }

    @Override
    public Collection<Observation> affects(Observation observation) {
        return List.of();
    }

    @Override
    public Collection<Observation> affected(Observation observation) {
        return List.of();
    }

    @Override
    public void runTransitions() {

    }

    @Override
    public Provenance getProvenance() {
        return null;
    }

    @Override
    public Report getReport() {
        return null;
    }

    @Override
    public Dataflow<Observation> getDataflow() {
        return null;
    }

    @Override
    public DirectObservation getParentOf(Observation observation) {
        return null;
    }

    @Override
    public Collection<Observation> getChildrenOf(Observation observation) {
        return List.of();
    }

    @Override
    public Collection<Relationship> getOutgoingRelationships(DirectObservation observation) {
        return List.of();
    }

    @Override
    public Collection<Relationship> getIncomingRelationships(DirectObservation observation) {
        return List.of();
    }

    @Override
    public Map<Observable, Observation> getCatalog() {
        return Map.of();
    }

    @Override
    public <T extends Observation> T getObservation(String localName, Class<T> cls) {
        return null;
    }

    @Override
    public String getResolutionNamespace() {
        return "";
    }

    @Override
    public String getResolutionProject() {
        return "";
    }

    @Override
    public Collection<String> getResolutionScenarios() {
        return List.of();
    }

    @Override
    public DirectObservation getResolutionObservation() {
        return null;
    }

    @Override
    public ContextScope withContextualizationData(DirectObservation contextObservation, Scale scale, Map<String, String> localNames) {
        return null;
    }

    @Override
    public void close() throws Exception {

    }
}
