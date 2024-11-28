package org.integratedmodelling.klab.services.scopes;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Report;
import org.integratedmodelling.klab.services.base.BaseService;
import org.ojalgo.concurrent.Parallelism;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * The service-side {@link ContextScope}. Does most of the heavy lifting in the runtime service through the
 * services chosen by the session scope. Uses agents as needed. Relies on external instrumentation after
 * creation.
 * <p>
 * Maintained by the {@link ScopeManager}
 */
public class ServiceContextScope extends ServiceSessionScope implements ContextScope {

    // TODO make this configurable
    private static long MAX_CACHED_OBSERVATIONS = 100;
    private static long MAX_CACHED_GEOMETRIES = 20;

    private Observation observer;
    private Observation contextObservation;
    private URL url;
    private DigitalTwin digitalTwin;
    // FIXME there's also parentScope (generic) and I'm not sure these should be duplicated
    protected ServiceContextScope parent;
    protected Map<ResolutionConstraint.Type, ResolutionConstraint> resolutionConstraints =
            new LinkedHashMap<>();

    LoadingCache<Long, Observation> observationCache;

    // This uses the SAME catalog, which should only be redefined when changing context or perspective
    private ServiceContextScope(ServiceContextScope parent) {
        super(parent);
        this.parent = parent;
        this.observer = parent.observer;
        this.contextObservation = parent.contextObservation;
        this.digitalTwin = parent.digitalTwin;
        this.observationCache = parent.observationCache;
        this.resolutionConstraints.putAll(parent.resolutionConstraints);
    }

    @Override
    ServiceContextScope copy() {
        return new ServiceContextScope(this);
    }

    // next 3 are overridden with the same code as the parent because they need to use the local maps, not the
    // parent's

    @Override
    public <T extends KlabService> T getService(Class<T> serviceClass) {
        return (T) defaultServiceMap.get(KlabService.Type.classify(serviceClass));
    }

    @Override
    public <T extends KlabService> T getService(String serviceId, Class<T> serviceClass) {
        for (var service : getServices(serviceClass)) {
            if (serviceId.equals(service.serviceId())) {
                return service;
            }
        }
        throw new KlabResourceAccessException("cannot find service with ID=" + serviceId + " in the scope");
    }

    @Override
    public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
        return new org.integratedmodelling.klab.api.utils.Utils.Casts<KlabService, T>().cast((Collection<KlabService>) serviceMap.get(KlabService.Type.classify(serviceClass)));
    }

    ServiceContextScope(ServiceSessionScope parent) {
        super(parent);
        this.observer = null;
        this.data = Parameters.create();
        this.data.putAll(parent.data);
        // TODO do the same for scales, from the geometries in the knowledge graph
        this.observationCache =
                CacheBuilder.newBuilder().maximumSize(MAX_CACHED_OBSERVATIONS).build(new CacheLoader<Long,
                        Observation>() {
                    @Override
                    public Observation load(Long key) throws Exception {
                        return digitalTwin.knowledgeGraph().get(key, Observation.class);
                    }
                });
        /*
         * TODO choose the services if this context or user requires specific ones
         */
    }


    @Override
    public Observation getObserver() {
        return this.observer;
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

    /**
     * Retrieve the observation with the passed ID straight from the digital twin. This is non-API and is the
     * fastest way. The knowledge graph should in turn cache scales, so that no geometries are created
     * unnecessarily.
     * <p>
     * TODO check if the caching logic should be entirely within the knowledge graph (probably).
     *
     * @param id
     * @return
     */
    public Observation getObservation(long id) {
        try {
            return observationCache.get(id);
        } catch (ExecutionException e) {
            throw new KlabInternalErrorException(e);
        }
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
    public ServiceContextScope withObserver(Observation observer) {
        ServiceContextScope ret = new ServiceContextScope(this);
        ret.observer = observer;
        return ret;
    }


    @Override
    public Future<Observation> observe(Observation observation) {

        if (!isOperative()) {
            return null;
        }

        var runtime = getService(RuntimeService.class);
        long taskId = runtime.submit(observation, this);
        return runtime.resolve(taskId, this);
    }

    private void finalizeObservation(Observation observation, Dataflow<Observation> dataflow,
                                     Provenance provenance) {
        // TODO do stuff in the knowledge graph
    }

    @Override
    public Provenance getProvenance() {
        return digitalTwin.getProvenanceGraph(this);
    }

    @Override
    public Report getReport() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Dataflow<Observation> getDataflow() {
        return digitalTwin.getDataflowGraph(this);
    }

    @Override
    public ContextScope getRootContextScope() {
        return null;
    }

    @Override
    public Observation getParentOf(Observation observation) {
        return null;
    }

    @Override
    public Collection<Observation> getChildrenOf(Observation observation) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Observation> getOutgoingRelationshipsOf(Observation observation) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Observation> getIncomingRelationshipsOf(Observation observation) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T extends RuntimeAsset> List<T> query(Class<T> resultClass, Object... queryData) {
        return getService(RuntimeService.class).retrieveAssets(this, resultClass, queryData);
    }

    @Override
    public void runTransitions() {
        // TODO Auto-generated method stub

    }

    @Override
    public Collection<Observation> affects(Observation observation) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Observation> affected(Observation observation) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public ContextScope connect(URL remoteContext) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Observation getContextObservation() {
        return this.contextObservation;
    }

    @Override
    public ContextScope within(Observation contextObservation) {
        ServiceContextScope ret = new ServiceContextScope(this);
        ret.contextObservation = contextObservation;
        //        ret.catalog = new HashMap<>(this.catalog);
        return ret;
    }

    @Override
    public ContextScope between(Observation source, Observation target) {
        return null;
    }

    @Override
    public ServiceContextScope withResolutionConstraints(ResolutionConstraint... resolutionConstraints) {
        ServiceContextScope ret = new ServiceContextScope(this);
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
    public boolean initializeAgents(String scopeId) {
        // setting the ID here is dirty as technically this is still being set and will be set again later,
        // but
        // no big deal for now. Alternative is a complicated restructuring of messages to take multiple
        // payloads.
        setId(scopeId);
        setStatus(Status.WAITING);
        KActorsBehavior.Ref contextAgent = parentScope.ask(KActorsBehavior.Ref.class,
                Message.MessageClass.ActorCommunication, Message.MessageType.CreateContext, this);
        if (contextAgent != null && !contextAgent.isEmpty()) {
            setStatus(Status.STARTED);
            setAgent(contextAgent);
            return true;
        }
        setStatus(Status.ABORTED);
        return false;
    }

    public DigitalTwin getDigitalTwin() {
        return digitalTwin;
    }

    public void setDigitalTwin(DigitalTwin digitalTwin) {
        this.digitalTwin = digitalTwin;
    }

    @Override
    public void close() {

        // TODO when we're not in a runtime, we should not touch the digital twin (which is null) and
        //  we MUST call closeContext on all the other services we have paired with

        digitalTwin.dispose();

        // Call close() on all closeables in our dataset, including AutoCloseable if any.
        for (String key : getData().keySet()) {
            Object object = getData().get(key);
            if (object instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        closeMessaging();

        var runtime = getService(RuntimeService.class);
        if (runtime instanceof BaseService baseService) {
            baseService.getScopeManager().releaseScope(this.getId());
        }
    }

    public Parallelism getParallelism() {
        // TODO
        return Parallelism.CORES;
    }
}
