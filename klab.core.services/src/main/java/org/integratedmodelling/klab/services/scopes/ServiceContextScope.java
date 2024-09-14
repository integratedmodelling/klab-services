package org.integratedmodelling.klab.services.scopes;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Report;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * The service-side {@link ContextScope}. Does most of the heavy lifting in the runtime service through the
 * services chosen by the session scope. Uses agents as needed. Relies on external instrumentation after
 * creation.
 * <p>
 * Maintained by the {@link ScopeManager}
 */
public class ServiceContextScope extends ServiceSessionScope implements ContextScope {

    private Observation observer;
    private Observation contextObservation;
    private Set<String> resolutionScenarios = new LinkedHashSet<>();
    private Scale geometry = Scale.empty();
    @Deprecated
    private String resolutionNamespace;
    @Deprecated
    private String resolutionProject;
    @Deprecated
    private Map<Observable, Observation> catalog;
    @Deprecated
    private Map<String, Observable> namedCatalog = new HashMap<>();
    @Deprecated
    private Map<Concept, Concept> contextualizedPredicates = new HashMap<>();
    private URL url;
    private DigitalTwin digitalTwin;
    // FIXME there's also parentScope (generic) and I'm not sure these should be duplicated
    protected ServiceContextScope parent;
    protected List<ResolutionConstraint> resolutionConstraints = new ArrayList<>();

    // This uses the SAME catalog, which should only be redefined when changing context or perspective
    private ServiceContextScope(ServiceContextScope parent) {
        super(parent);
        this.parent = parent;
        this.observer = parent.observer;
        this.contextObservation = parent.contextObservation;
        this.catalog = parent.catalog;
        this.namedCatalog.putAll(parent.namedCatalog);
        this.contextualizedPredicates.putAll(parent.contextualizedPredicates);
        this.resolutionScenarios.addAll(parent.resolutionScenarios);
        this.resolutionNamespace = parent.resolutionNamespace;
        this.digitalTwin = parent.digitalTwin;
        this.resolutionConstraints.addAll(parent.resolutionConstraints);
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
        this.observer = null; // NAAH parent.getUser();
        this.data = Parameters.create();
        this.data.putAll(parent.data);
        this.catalog = new HashMap<>();
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
     * fastest way.
     *
     * @param id
     * @return
     */
    private Observation getObservation(long id) {
        return null;
    }

    @Override
    public Observation getObserverOf(Observation observation) {
        return null;
    }

    @Override
    public Collection<Observation> getRootObservations() {
        return List.of();
    }

    //    @Override
    //    public Scale getScale() {
    //        return geometry;
    //    }

    @Override
    public ServiceContextScope withScenarios(String... scenarios) {
        ServiceContextScope ret = new ServiceContextScope(this);
        if (scenarios == null) {
            ret.resolutionScenarios = null;
        }
        this.resolutionScenarios = new HashSet<>();
        for (String scenario : scenarios) {
            ret.resolutionScenarios.add(scenario);
        }
        return ret;
    }

    @Override
    public ServiceContextScope withObserver(Observation observer) {
        ServiceContextScope ret = new ServiceContextScope(this);
        ret.observer = observer;
        ret.catalog = new HashMap<>(this.catalog);
        return ret;
    }

    @Override
    public Task<Observation, Long> observe(Observation observation) {

        if (!isOperative()) {
            return null;
        }

        long id = submitObservation(observation);

        // create task before resolution starts so we guarantee a response
        var ret = newMessageTrackingTask(EnumSet.of(Message.MessageType.ResolutionAborted,
                Message.MessageType.ResolutionSuccessful), id, this::getObservation);

        final var runtime = getService(RuntimeService.class);
        final var resolver = getService(Resolver.class);

        // start virtual resolution thread. This should be everything we need.
        Thread.ofVirtual().start(() -> {
            try {
                var dataflow = resolver.resolve(observation, this);
                if (dataflow != null && !dataflow.isEmpty()) {
                    var provenance = runtime.runDataflow(dataflow, this);
                    System.out.println("RESOLVED CRAPPETTONE " + id);
                    digitalTwin.finalizeObservation(observation, dataflow, provenance);
                }
                send(Message.MessageClass.ObservationLifecycle, Message.MessageType.ResolutionSuccessful, id);
            } catch (Throwable t) {
                System.out.println("RESOLVING INKULÉ " + id);
                send(Message.MessageClass.ObservationLifecycle, Message.MessageType.ResolutionAborted, id);
            }
        });
        return ret;

    }

    /*
    MODIFIES the observation if it's an ObservationImpl, otherwise throws an exception.
     */
    private long submitObservation(Observation observation) {
        // TODO FIXME - create all the structure and metadata from the current context, parents and all
        // should we have the same for relationships? A context 'between" x and y where a relationship
        // can be observed? (wouldn't address collective relationships)

        return digitalTwin.submit(observation, this.contextObservation,
                DigitalTwin.Relationship.Parent,
                null);
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
    public Dataflow<Observation> getDataflow() {
        return null;
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
    public Map<Concept, Concept> getContextualizedPredicates() {
        return contextualizedPredicates;
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
    public Map<Observable, Observation> getObservations() {
        return catalog;
    }

    @Override
    public String getResolutionNamespace() {
        return resolutionNamespace;
    }

    public String getResolutionProject() {
        return resolutionProject;
    }

    public void setResolutionProject(String resolutionProject) {
        this.resolutionProject = resolutionProject;
    }

    @Override
    public Set<String> getResolutionScenarios() {
        return resolutionScenarios;
    }

    //    @Override
    //    public Observation getResolutionObservation() {
    //        return contextObservation;
    //    }

    @Override
    public ContextScope withContextualizationData(Observation contextObservation, Scale scale,
                                                  Map<String, String> localNames) {
        if (scale == null && localNames.isEmpty()) {
            return this;
        }
        ServiceContextScope ret = new ServiceContextScope(this);
        ret.contextObservation = contextObservation;
        if (scale != null) {
            ret.geometry = scale;
        }
        if (!localNames.isEmpty()) {
            this.namedCatalog = Utils.Maps.translateKeys(namedCatalog, localNames);
        }
        return ret;
    }

    @Override
    public <T extends Observation> T getObservation(String localName, Class<T> cls) {
        // TODO Auto-generated method stub
        return null;
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
    public ContextScope within(DirectObservation contextObservation) {
        ServiceContextScope ret = new ServiceContextScope(this);
        ret.contextObservation = contextObservation;
        ret.catalog = new HashMap<>(this.catalog);
        return ret;
    }

    @Override
    public ContextScope withContextualizedPredicate(Concept abstractTrait, Concept concreteTrait) {
        ServiceContextScope ret = new ServiceContextScope(this);
        ret.contextualizedPredicates.put(abstractTrait, concreteTrait);
        return ret;
    }

    @Override
    public ContextScope between(Observation source, Observation target) {
        return null;
    }

    @Override
    public ContextScope withResolutionNamespace(String namespace) {
        ServiceContextScope ret = new ServiceContextScope(this);
        ret.resolutionNamespace = namespace;
        return ret;
    }

    @Override
    public ContextScope withResolutionConstraints(ResolutionConstraint... resolutionConstraints) {
        ServiceContextScope ret = new ServiceContextScope(this);
        if (resolutionConstraints == null) {
            ret.resolutionConstraints.clear();
        } else {
            ret.resolutionConstraints.addAll(Arrays.stream(resolutionConstraints).toList());
        }
        return ret;
    }

    @Override
    public List<ResolutionConstraint> getResolutionConstraints() {
        return this.resolutionConstraints;
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

        digitalTwin.dispose();

        // Call close() on all closeables in our dataset, including AutoCloseable if any.
        for (String key : getData().keySet()) {
            Object object = getData().get(key);
            if (object instanceof Closeable closeable) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
