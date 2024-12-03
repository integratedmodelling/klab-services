package org.integratedmodelling.common.services.client.scope;

import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Report;

import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public abstract class ClientContextScope extends ClientSessionScope implements ContextScope {

    private Observation observer;
    private Observation contextObservation;
    private Map<ResolutionConstraint.Type, ResolutionConstraint> resolutionConstraints =
            new LinkedHashMap<>();

    /**
     * The default client scope has the user as the embedded agent.
     *
     * @param parent
     * @param contextName
     * @param runtimeService
     */
    public ClientContextScope(ClientUserScope parent, String contextName, RuntimeService runtimeService) {
        super(parent, contextName, runtimeService);
        resolutionConstraints.put(ResolutionConstraint.Type.Provenance,
                ResolutionConstraint.of(ResolutionConstraint.Type.Provenance,
                        Agent.create(parent.getUser().getUsername())));
    }

    private ClientContextScope(ClientContextScope parent) {
        super(parent, parent.name, parent.runtimeService);
        // this will have been reset by super to the user's id
        setId(parent.getId());
        resolutionConstraints.putAll(parent.resolutionConstraints);
        observer = parent.observer;
        contextObservation = parent.contextObservation;
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
        var ret = childContext(this);
        ret.observer = observer;
        return ret;
    }

    protected ClientContextScope childContext(final ClientContextScope parent) {
        var ret = new ClientContextScope(parent) {

            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
                return parent.getService(serviceClass);
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                return parent.getServices(serviceClass);
            }
        };
        ret.copyMessagingSetup(parent);
        return ret;
    }

    @Override
    public ContextScope within(Observation contextObservation) {
        var ret = childContext(this);
        ret.contextObservation = contextObservation;
        return ret;
    }

    @Override
    public ContextScope connect(URL remoteContext) {
        return null;
    }

    @Override
    public Future<Observation> observe(Observation observation) {

        var runtime = getService(RuntimeService.class);
        long taskId = runtime.submit(observation, this);

        if (taskId != Observation.UNASSIGNED_ID) {
            if (observation instanceof ObservationImpl observation1) {
                observation1.setId(taskId);
                observation1.setUrn(this.getId() + "." + taskId);
            }
            return runtime.resolve(taskId, this);
        }

        // failure: this just returns the unresolved observation
        info("Submission of " + observation + " failed");

        return ConcurrentUtils.constantFuture(observation);

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
    public <T extends RuntimeAsset> List<T> query(Class<T> resultClass, Object... queryData) {
        return getService(RuntimeService.class).retrieveAssets(this, resultClass, queryData);
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

    @Override
    public void close() {
        var runtime = getService(RuntimeService.class);
        if (runtime != null) {
            runtime.releaseContext(this);
        } else {
            throw new KlabInternalErrorException("Context scope: no runtime service available");
        }
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
        ClientContextScope ret = childContext(this);

        if (resolutionConstraints == null) {
            ret.resolutionConstraints.clear();
        } else {
            for (var constraint : resolutionConstraints) {
                if (constraint == null || constraint.empty()) {
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
        return (T) constraint.payload(defaultValue.getClass()).getFirst();
    }

    @Override
    public <T> T getConstraint(ResolutionConstraint.Type type, Class<T> resultClass) {
        var constraint = resolutionConstraints.get(type);
        if (constraint == null || constraint.size() == 0) {
            return null;
        }
        return (T) constraint.payload(resultClass).getFirst();
    }

    @Override
    public <T> List<T> getConstraints(ResolutionConstraint.Type type, Class<T> resultClass) {
        var constraint = resolutionConstraints.get(type);
        if (constraint == null || constraint.size() == 0) {
            return List.of();
        }
        return constraint.payload(resultClass);
    }

    @Override
    public Storage getStorage(Observation observation) {
        return null;
    }

}
