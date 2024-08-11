package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Report;

import java.net.URL;
import java.util.*;

public abstract class ClientContextScope extends ClientSessionScope implements ContextScope {

    private Observation observer;
    private Observation contextObservation;
    private String[] scenarios;
    private String resolutionNamespace;
    private List<ResolutionConstraint> resolutionConstraints = new ArrayList<>();

    public ClientContextScope(ClientUserScope parent, String contextName, RuntimeService runtimeService) {
        super(parent, contextName, runtimeService);
    }

    @Override
    public URL getUrl() {
        return null;
    }

    @Override
    public Observation getObserver() {
        return this.observer;
    }

    @Override
    public Observation getContextObservation() {
        return this.contextObservation;
    }

    @Override
    public ContextScope withObserver(Observation observer) {
        return this;
    }

    @Override
    public ContextScope withScenarios(String... scenarios) {
        return this;
    }

    @Override
    public ContextScope withResolutionNamespace(String namespace) {
        return this;
    }

    @Override
    public ContextScope within(DirectObservation contextObservation) {
        return null;
    }

    @Override
    public ContextScope withContextualizedPredicate(Concept abstractTrait, Concept concreteTrait) {
        return null;
    }

    @Override
    public ContextScope connect(URL remoteContext) {
        return null;
    }

    @Override
    public Task<Observation, Long> observe(Observation observation) {

        var runtime = getService(RuntimeService.class);
        if (runtime instanceof RuntimeClient runtimeClient) {
            long taskId = runtimeClient.graphClient().query(GraphModel.Queries.GraphQL.OBSERVE, Long.class,
                    this, "observation",
                    GraphModel.adapt(observation, this));
            return newMessageTrackingTask(EnumSet.of(Message.MessageType.ResolutionAborted,
                    Message.MessageType.ResolutionSuccessful), taskId, this::getObservation); // event watcher using either messaging or queues
        }

        return null; // new ClientResolutionTask(this);
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
    public Observation getParentOf(Observation observation) {
        return null;
    }

    @Override
    public Collection<Observation> getChildrenOf(Observation observation) {
        return List.of();
    }

    @Override
    public Collection<Observation> getOutgoingRelationshipsOf(Observation observation) {
        return List.of();
    }

    @Override
    public Collection<Observation> getIncomingRelationshipsOf(Observation observation) {
        return List.of();
    }

    @Override
    public Map<Observable, Observation> getObservations() {
        return Map.of();
    }

    @Override
    public <T extends Observation> T getObservation(String localName, Class<T> cls) {
        return null;
    }

    /**
     * Retrieve the observation with the passed ID straight from the digital twin. This is non-API and is the fastest way.
     *
     * @param id
     * @return
     */
    public Observation getObservation(long id) {
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

//    @Override
//    public DirectObservation getResolutionObservation() {
//        return null;
//    }

    @Override
    public ContextScope withContextualizationData(Observation contextObservation, Scale scale,
                                                  Map<String, String> localNames) {
        return null;
    }

    @Override
    public Map<Concept, Concept> getContextualizedPredicates() {
        return Map.of();
    }

    @Override
    public void close() {

    }

    @Override
    public boolean isConsistent() {
        return false;
    }

    @Override
    public Collection<Observation> getInconsistencies(boolean dependentOnly) {
        return List.of();
    }

    @Override
    public <T extends Observation> Collection<T> getPerspectives(Observable observable) {
        return List.of();
    }

    @Override
    public Observation getObserverOf(Observation observation) {
        return null;
    }

    @Override
    public Collection<Observation> getRootObservations() {
        return List.of();
    }

    @Override
    public ContextScope getRootContextScope() {
        return null;
    }

    @Override
    public ContextScope between(Observation source, Observation target) {
        // TODO
        return null;
    }

    @Override
    public ContextScope withResolutionConstraints(ResolutionConstraint... resolutionConstraints) {
        return null;
    }

    @Override
    public List<ResolutionConstraint> getResolutionConstraints() {
        return this.resolutionConstraints;
    }
}
