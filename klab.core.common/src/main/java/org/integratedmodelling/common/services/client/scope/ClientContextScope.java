package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.Observer;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionTask;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class ClientContextScope extends ClientSessionScope implements ContextScope {

    private Observer observer;
    private DirectObservation contextObservation;
    private String[] scenarios;
    private String resolutionNamespace;


    public ClientContextScope(ClientUserScope parent, String contextName, RuntimeService runtimeService) {
        super(parent, contextName, runtimeService);
    }

    @Override
    public URL getUrl() {
        return null;
    }

    @Override
    public Observer getObserver() {
        return this.observer;
    }

    @Override
    public DirectObservation getContextObservation() {
        return this.contextObservation;
    }

    @Override
    public ContextScope withObserver(Observer observer) {
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
    public ResolutionTask observe(Observation observation) {

        var runtime = getService(RuntimeService.class);
        if (runtime instanceof RuntimeClient runtimeClient) {
            long id = runtimeClient.graphClient().query(GraphModel.Queries.GraphQL.OBSERVE, Long.class,
                    this, "observation",
                    GraphModel.adapt(observation, this));
            return resolutionWatcher(id); // event watcher using either messaging or queues
        }

        return null; // new ClientResolutionTask(this);
    }

    private ResolutionTask resolutionWatcher(long id) {
        return new ResolutionTask() {

            @Override
            public long getId() {
                return id;
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public Observation get() throws InterruptedException, ExecutionException {
                return null;
            }

            @Override
            public Observation get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return null;
            }
        };
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
    public Collection<Observation> getOutgoingRelationships(DirectObservation observation) {
        return List.of();
    }

    @Override
    public Collection<Observation> getIncomingRelationships(DirectObservation observation) {
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
    public ContextScope withContextualizationData(DirectObservation contextObservation, Scale scale,
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
    public Observer getObserverOf(Observation observation) {
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
}
