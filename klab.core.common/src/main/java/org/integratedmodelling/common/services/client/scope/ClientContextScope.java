package org.integratedmodelling.common.services.client.scope;

import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.KlabService;
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
    private Map<ResolutionConstraint.Type, ResolutionConstraint> resolutionConstraints =
            new LinkedHashMap<>();

    public ClientContextScope(ClientUserScope parent, String contextName, RuntimeService runtimeService) {
        super(parent, contextName, runtimeService);
    }

    private ClientContextScope(ClientContextScope parent) {
        super(parent, parent.name, parent.runtimeService);
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

    //    @Override
    //    public ContextScope withScenarios(String... scenarios) {
    //        return this;
    //    }
    //
    //    @Override
    //    public ContextScope withResolutionNamespace(String namespace) {
    //        return this;
    //    }

    @Override
    public ContextScope within(DirectObservation contextObservation) {
        return null;
    }

    //    @Override
    //    public ContextScope withContextualizedPredicate(Concept abstractTrait, Concept concreteTrait) {
    //        return null;
    //    }

    @Override
    public ContextScope connect(URL remoteContext) {
        return null;
    }

    @Override
    public Task<Observation, Long> observe(Observation observation) {

        var runtime = getService(RuntimeService.class);
        if (runtime instanceof RuntimeClient runtimeClient) {
            long taskId =
                    runtimeClient.graphClient().query(GraphModel.Queries.GraphQL.OBSERVE.queryPattern(),
                            GraphModel.Queries.GraphQL.OBSERVE.resultTarget(), Long.class,
                            this, "observation",
                            GraphModel.adapt(observation, this));
            return newMessageTrackingTask(EnumSet.of(Message.MessageType.ResolutionAborted,
                    Message.MessageType.ResolutionSuccessful), taskId, this::getObservation); // event
            // watcher using either messaging or queues
        }

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
     * Retrieve the observation with the passed ID straight from the digital twin. This is non-API and is the
     * fastest way.
     *
     * @param id
     * @return
     */
    public Observation getObservation(long id) {
        return null;
    }

    //    @Override
    //    public String getResolutionNamespace() {
    //        return "";
    //    }
    //
    //    @Override
    //    public String getResolutionProject() {
    //        return "";
    //    }
    //
    //    @Override
    //    public Collection<String> getResolutionScenarios() {
    //        return List.of();
    //    }

    //    @Override
    //    public DirectObservation getResolutionObservation() {
    //        return null;
    //    }

    //    @Override
    //    public ContextScope withContextualizationData(Observation contextObservation, Scale scale,
    //                                                  Map<String, String> localNames) {
    //        return null;
    //    }
    //
    //    @Override
    //    public Map<Concept, Concept> getContextualizedPredicates() {
    //        return Map.of();
    //    }

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

        final var thisScope = this;

        ClientContextScope ret = new ClientContextScope(this) {
            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
                return thisScope.getService(serviceClass);
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                return thisScope.getServices(serviceClass);
            }
        };

        if (resolutionConstraints == null) {
            ret.resolutionConstraints.clear();
        } else {
            for (var constraint : resolutionConstraints) {
                if (constraint == null || constraint.isEmpty()) {
                    continue;
                }
                if (constraint.getType().incremental && ret.resolutionConstraints.containsKey(constraint.getType())) {
                    ret.resolutionConstraints.put(constraint.getType(),
                            ret.resolutionConstraints.get(constraint.getType()).merge(constraint));
                } else {
                    ret.resolutionConstraints.put(constraint.getType(), constraint);
                }
            }
        }

        return ret;
    }

    @Override
    public List<ResolutionConstraint> getResolutionConstraints() {
        return Utils.Collections.promoteToList(this.resolutionConstraints.values());
    }

    @Override
    public <T> T getConstraint(ResolutionConstraint.Type type, T defaultValue) {
        var constraint = resolutionConstraints.get(type);
        if (constraint == null || constraint.size() == 0) {
            return defaultValue;
        }
        return (T) constraint.get(defaultValue.getClass()).getFirst();
    }

    @Override
    public <T> T getConstraint(ResolutionConstraint.Type type, Class<T> resultClass) {
        var constraint = resolutionConstraints.get(type);
        if (constraint == null || constraint.size() == 0) {
            return null;
        }
        return (T) constraint.get(resultClass).getFirst();
    }

    @Override
    public <T> List<T> getConstraints(ResolutionConstraint.Type type, Class<T> resultClass) {
        var constraint = resolutionConstraints.get(type);
        if (constraint == null || constraint.size() == 0) {
            return List.of();
        }
        return constraint.get(resultClass);
    }
}
