package org.integratedmodelling.klab.services.scopes;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.Observer;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionTask;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Report;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * The service-side {@link ContextScope}. Does most of the heavy lifting in the runtime service through the
 * services chosen by the session scope. Uses agents as needed..
 * <p>
 * Maintained by the {@link ScopeManager}
 */
public class ServiceContextScope extends ServiceSessionScope implements ContextScope {

    private Observer observer;
    private DirectObservation contextObservation;
    private Set<String> resolutionScenarios = new LinkedHashSet<>();
    private Scale geometry = Scale.empty();
    private String resolutionNamespace;
    private String resolutionProject;
    private Map<Observable, Observation> catalog;
    private Map<String, Observable> namedCatalog = new HashMap<>();
    private Map<Concept, Concept> contextualizedPredicates = new HashMap<>();
    private URL url;

    protected ServiceContextScope parent;
    private Dataflow<Observation> dataflow = Dataflow.empty(Observation.class);

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
    public Observer getObserver() {
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

    @Override
    public Observer getObserverOf(Observation observation) {
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
    public ServiceContextScope withObserver(Observer observer) {
        ServiceContextScope ret = new ServiceContextScope(this);
        ret.observer = observer;
        ret.catalog = new HashMap<>(this.catalog);
        return ret;
    }

    @Override
    public ResolutionTask observe(Object... resolvables) {

        Observation observation = null; // TODO have the DT create the prototype unresolved obs

        //
        //        if (resolvables != null) {
        //            for (Object o : resolvables) {
        //                if (o instanceof Observable obs) {
        //                    message.setObservable(obs);
        //                } else if (o instanceof Geometry geom) {
        //                    if (message.getGeometry() == null) {
        //                        message.setGeometry(geom);
        //                    } else {
        //                        message.setObserverGeometry(geom);
        //                    }
        //                } else if (o instanceof String string) {
        //                    if (message.getName() == null) {
        //                        message.setName(string);
        //                    } else {
        //                        message.setDefaultValue(Utils.Data.asPOD(string));
        //                    }
        //                } else if (o instanceof Urn urn) {
        //                    message.setResourceUrn(urn);
        //                } else if (o instanceof KimModel model) {
        //                    message.setModelUrn(model.getUrn());
        //                }
        //            }
        //        }

        var taskId = ask(Long.class, Message.MessageType.ResolveObservation, observation);

        // TODO return a completable future that watches the response using the existing channels. Even
        //  when the messaging is duplex, ask one first time in case the response was already sent or missed.
        return responseFuture(observation);
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
        return this.dataflow;
    }

    @Override
    public ContextScope getRootContextScope() {
        return null;
    }

    @Override
    public DirectObservation getParentOf(Observation observation) {
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
    public Collection<Observation> getOutgoingRelationships(DirectObservation observation) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Observation> getIncomingRelationships(DirectObservation observation) {
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

    @Override
    public DirectObservation getResolutionObservation() {
        return contextObservation;
    }

    @Override
    public ContextScope withContextualizationData(DirectObservation contextObservation, Scale scale,
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
    public DirectObservation getContextObservation() {
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
    public ContextScope withResolutionNamespace(String namespace) {
        ServiceContextScope ret = new ServiceContextScope(this);
        ret.resolutionNamespace = namespace;
        return ret;

    }

    @Override
    public void close() {

        getService(RuntimeService.class).releaseScope(this);

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
