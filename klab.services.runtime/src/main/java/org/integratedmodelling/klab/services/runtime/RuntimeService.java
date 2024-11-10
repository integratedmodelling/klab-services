package org.integratedmodelling.klab.services.runtime;

import com.google.common.collect.ImmutableList;
import org.apache.qpid.server.SystemLauncher;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;
import org.integratedmodelling.klab.api.view.UI;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4JEmbedded;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.messaging.EmbeddedBroker;
import org.integratedmodelling.klab.utilities.Utils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class RuntimeService extends BaseService implements org.integratedmodelling.klab.api.services.RuntimeService, org.integratedmodelling.klab.api.services.RuntimeService.Admin {

    private String hardwareSignature =
            org.integratedmodelling.common.utils.Utils.Strings.hash(Utils.OS.getMACAddress());
    private RuntimeConfiguration configuration;
    private KnowledgeGraph knowledgeGraph;
    private SystemLauncher systemLauncher;

    public RuntimeService(AbstractServiceDelegatingScope scope, ServiceStartupOptions options) {
        super(scope, Type.RUNTIME, options);
        ServiceConfiguration.INSTANCE.setMainService(this);
        readConfiguration(options);
        initializeMessaging();
    }

    private void initializeMessaging() {
        if (this.configuration.getBrokerURI() == null) {
            this.embeddedBroker = new EmbeddedBroker();
        }
    }

    private void readConfiguration(ServiceStartupOptions options) {
        File config = BaseService.getFileInConfigurationDirectory(options, "runtime.yaml");
        if (config.exists() && config.length() > 0 && !options.isClean()) {
            this.configuration = Utils.YAML.load(config, RuntimeConfiguration.class);
        } else {
            // make an empty config
            this.configuration = new RuntimeConfiguration();
            this.configuration.setServiceId(UUID.randomUUID().toString());
            saveConfiguration();
        }
    }

    private boolean createMainKnowledgeGraph() {
        // TODO choose the DB from configuration - client or embedded server
        var path = BaseService.getConfigurationSubdirectory(startupOptions, "dt").toPath();
        this.knowledgeGraph = new KnowledgeGraphNeo4JEmbedded(path);
        return this.knowledgeGraph.isOnline();
    }

    public KnowledgeGraph getMainKnowledgeGraph() {
        return this.knowledgeGraph;
    }

    private void saveConfiguration() {
        File config = BaseService.getFileInConfigurationDirectory(startupOptions, "runtime.yaml");
        org.integratedmodelling.common.utils.Utils.YAML.save(this.configuration, config);
    }

    @Override
    public void initializeService() {

        Logging.INSTANCE.setSystemIdentifier("Runtime service: ");

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceInitializing,
                capabilities(serviceScope()).toString());

        if (createMainKnowledgeGraph()) {

            // TODO internal libraries
            getComponentRegistry().loadExtensions("org.integratedmodelling.klab.runtime");
            getComponentRegistry().initializeComponents(BaseService.getConfigurationSubdirectory(startupOptions, "components"));
            serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceAvailable
                    , capabilities(serviceScope()));
        } else {

            serviceScope().send(Message.MessageClass.ServiceLifecycle,
                    Message.MessageType.ServiceUnavailable, capabilities(serviceScope()));

        }

    }

    @Override
    public boolean operationalizeService() {
        // nothing to do here
        return true;
    }

    @Override
    public boolean shutdown() {

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceUnavailable,
                capabilities(serviceScope()));
        if (systemLauncher != null) {
            systemLauncher.shutdown();
        }
        if (knowledgeGraph != null) {
            knowledgeGraph.shutdown();
        }
        return super.shutdown();
    }

    @Override
    public Capabilities capabilities(Scope scope) {

        var ret = new RuntimeCapabilitiesImpl();
        ret.setLocalName(localName);
        ret.setType(Type.RUNTIME);
        ret.setUrl(getUrl());
        ret.setServerId(hardwareSignature == null ? null : ("RUNTIME_" + hardwareSignature));
        ret.setServiceId(configuration.getServiceId());
        ret.setServiceName("Runtime");
        ret.setBrokerURI(embeddedBroker != null ? embeddedBroker.getURI() : configuration.getBrokerURI());
        ret.setBrokerURI(embeddedBroker != null ? embeddedBroker.getURI() : configuration.getBrokerURI());

        return ret;
    }

    public String serviceId() {
        return configuration.getServiceId();
    }

    @Override
    public Map<String, String> getExceptionTestcases(Scope scope, boolean deleteExisting) {
        Map<String, String> ret = new HashMap<>();
        return ret;
    }

    @Override
    public String registerSession(SessionScope sessionScope) {
        if (sessionScope instanceof ServiceSessionScope serviceSessionScope) {

            serviceSessionScope.setId(Utils.Names.shortUUID());
            getScopeManager().registerScope(serviceSessionScope, capabilities(sessionScope).getBrokerURI());

            if (serviceSessionScope.getServices(RuntimeService.class).isEmpty()) {
                // add self as the runtime service, which is needed by the slave scopes
                serviceSessionScope.getServices(RuntimeService.class).add(this);
            }

            // all other services need to know the session we created
            var fail = new AtomicBoolean(false);
            for (var serviceClass : List.of(Resolver.class, Reasoner.class, ResourcesService.class)) {
                try {
                    Thread.ofVirtual().start(() -> {
                        for (var service : serviceSessionScope.getServices(serviceClass)) {
                            // if things are OK, the service repeats the ID back
                            if (!serviceSessionScope.getId().equals(
                                    service.registerSession(serviceSessionScope))) {
                                fail.set(true);
                            }
                        }
                    }).join();
                } catch (InterruptedException e) {
                    fail.set(true);
                }
            }

            if (fail.get()) {
                serviceSessionScope.send(Notification.error(
                        "Error registering session with other services:" +
                                " session is inoperative",
                        UI.Interactivity.DISPLAY));
                serviceSessionScope.setOperative(false);
            }

            return serviceSessionScope.getId();
        }
        throw new KlabIllegalArgumentException("unexpected scope class");
    }

    @Override
    public String registerContext(ContextScope contextScope) {

        if (contextScope instanceof ServiceContextScope serviceContextScope) {

            serviceContextScope.setId(
                    serviceContextScope.getParentScope().getId() + "." + Utils.Names.shortUUID());
            getScopeManager().registerScope(serviceContextScope, capabilities(contextScope).getBrokerURI());
            serviceContextScope.setDigitalTwin(new DigitalTwinImpl(this, contextScope,
                    getMainKnowledgeGraph()));

            if (serviceContextScope.getServices(RuntimeService.class).isEmpty()) {
                // add self as the runtime service, which is needed by the slave scopes
                serviceContextScope.getServices(RuntimeService.class).add(this);
            }

            // all other services need to know the context we created. TODO we may also need to
            // register with the stats services and maybe any independent authorities
            var fail = new AtomicBoolean(false);
            for (var serviceClass : List.of(Resolver.class, Reasoner.class, ResourcesService.class)) {
                try {
                    Thread.ofVirtual().start(() -> {
                        for (var service : serviceContextScope.getServices(serviceClass)) {
                            // if things are OK, the service repeats the ID back
                            if (!serviceContextScope.getId().equals(
                                    service.registerContext(serviceContextScope))) {
                                fail.set(true);
                            }
                        }
                    }).join();
                } catch (InterruptedException e) {
                    fail.set(true);
                }
            }

            if (fail.get()) {
                serviceContextScope.send(Notification.error(
                        "Error registering context with other services:" +
                                " context is inoperative",
                        UI.Interactivity.DISPLAY));
                serviceContextScope.setOperative(false);
            }

            return serviceContextScope.getId();

        }
        throw new KlabIllegalArgumentException("unexpected scope class");
    }

    @Override
    public long submit(Observation observation, ContextScope scope) {
        if (scope instanceof ServiceContextScope serviceContextScope) {
            return serviceContextScope.insertIntoKnowledgeGraph(observation);
        }
        return Observation.UNASSIGNED_ID;
    }

    @Override
    public Future<Observation> resolve(long id, ContextScope scope) {

        if (scope instanceof ServiceContextScope serviceContextScope) {

            var resolver = serviceContextScope.getService(Resolver.class);
            var observation = serviceContextScope.getObservation(id);
            var digitalTwin = getDigitalTwin(scope);
            var activity = digitalTwin.knowledgeGraph().activity(digitalTwin.knowledgeGraph().klab(), scope,
                    observation, Activity.Type.RESOLUTION, null);

            final var ret = new CompletableFuture<Observation>();

            Thread.ofVirtual().start(() -> {
                try {
                    var result = observation;
                    scope.send(Message.MessageClass.ObservationLifecycle,
                            Message.MessageType.ResolutionStarted, result);
                    var dataflow = resolver.resolve(observation, scope);
                    if (dataflow != null) {
                        if (!dataflow.isEmpty()) {
                            result = runDataflow(dataflow, scope);
                            ret.complete(result);
                        }
                        activity.success(scope, result, dataflow,
                                "Resolution of observation _" + observation.getUrn() + "_ of **" + observation.getObservable().getUrn() + "**");
                    } else {
                        activity.fail(scope, observation);
                    }
                    scope.send(Message.MessageClass.ObservationLifecycle,
                            Message.MessageType.ResolutionSuccessful, result);
                } catch (Throwable t) {
                    ret.completeExceptionally(t);
                    activity.fail(scope, observation, t);
                    scope.send(Message.MessageClass.ObservationLifecycle,
                            Message.MessageType.ResolutionAborted, observation);
                }
            });

            return ret;
        }

        throw new KlabInternalErrorException("Digital twin is inaccessible because of unexpected scope " +
                "implementation");
    }

    @Override
    public Observation runDataflow(Dataflow<Observation> dataflow, ContextScope contextScope) {

        var digitalTwin = getDigitalTwin(contextScope);

        /*
        Load or confirm availability of all needed resources and create any non-existing observations
         */


        /*
        find contextualization scale and hook point into the DT from the scope
         */

        /**
         * Run each actuator set in order
         */
        for (var rootActuator : dataflow.getComputation()) {
            ExecutionSequence executionSequence = ExecutionSequence.compile(sortComputation(rootActuator,
                            dataflow,
                            contextScope), (dataflow instanceof DataflowImpl dataflow1 ?
                                            dataflow1.getResolvedCoverage() : 1.0),
                    (ServiceContextScope) contextScope, digitalTwin, getComponentRegistry());
            if (!executionSequence.isEmpty()) {
                if (!executionSequence.run()) {
                    return Observation.empty();
                }
            }
        }

        /*
        intersect coverage from dataflow with contextualization scale
         */

        if (dataflow instanceof DataflowImpl df && dataflow.getTarget() instanceof ObservationImpl obs) {
            obs.setResolved(true);
            obs.setResolvedCoverage(df.getResolvedCoverage());
        }

        return dataflow.getTarget();
    }

    private DigitalTwin getDigitalTwin(ContextScope contextScope) {
        if (contextScope instanceof ServiceContextScope serviceContextScope) {
            return serviceContextScope.getDigitalTwin();
        }
        throw new KlabInternalErrorException("Digital twin is inaccessible because of unexpected scope " +
                "implementation");
    }

    private Graph<Actuator, DefaultEdge> computeActuatorOrder(Actuator rootActuator, ContextScope scope) {
        Graph<Actuator, DefaultEdge> dependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        Map<Long, Actuator> cache = new HashMap<>();
        loadGraph(rootActuator, dependencyGraph, cache);
        // keep the actuators that do nothing so we can tag their observation as resolved
        return dependencyGraph;
    }

    private void loadGraph(Actuator rootActuator, Graph<Actuator, DefaultEdge> dependencyGraph, Map<Long,
            Actuator> cache) {
        cache.put(rootActuator.getId(), rootActuator);
        dependencyGraph.addVertex(rootActuator);
        for (Actuator child : rootActuator.getChildren()) {
            if (child.getActuatorType() == Actuator.Type.REFERENCE) {
                dependencyGraph.addEdge(cache.get(child.getId()), rootActuator);
            } else {
                loadGraph(child, dependencyGraph, cache);
                dependencyGraph.addEdge(child, rootActuator);
            }
        }
    }

    @Override
    public <T extends RuntimeAsset> List<T> retrieveAssets(ContextScope contextScope, Class<T> assetClass,
                                                           Object... queryParameters) {
        return knowledgeGraph.get(contextScope, assetClass, queryParameters);
    }

    @Override
    public ResourceSet resolveContextualizables(List<Contextualizable> contextualizables,
                                                ContextScope scope) {

        ResourceSet ret = new ResourceSet();
        // TODO FIXME USE ALL SERVICES
        var resourcesService = scope.getService(ResourcesService.class);
        /**
         * These are the contextualizables that need resolution at the runtime side, the others come with
         * their definition and are directly inserted in the dataflow
         */
        for (var contextualizable : contextualizables) {
            if (contextualizable.getServiceCall() != null) {
                var resolution =
                        resourcesService.resolveServiceCall(contextualizable.getServiceCall().getUrn(),
                                contextualizable.getServiceCall().getRequiredVersion(),
                                scope);
                if (resolution.isEmpty()) {
                    return resolution;
                }
                /*
                we load directly and report errors if not
                 */
                if (getComponentRegistry().loadComponents(resolution, scope)) {
                    ret = Utils.Resources.merge(ret, resolution);
                } else {
                    return Utils.Resources.createEmpty(Notification.error("Runtime: errors ingesting " +
                            "resolved component for service " + contextualizable.getServiceCall().getUrn()));
                }
            } else if (contextualizable.getResourceUrn() != null) {
                // TODO ensure resource is accessible
                //                var resolution = resourcesService.resolveRe(contextualizable
                //                .getServiceCall().getUrn(), scope);
                //                if (resolution.isEmpty()) {
                //                    return resolution;
                //                }
            }
        }

        return ret;
    }

    @Override
    public List<SessionInfo> getSessionInfo(Scope scope) {
        return knowledgeGraph.getSessionInfo(scope);
    }

    /**
     * Establish the order of execution and the possible parallelism. Each root actuator should be sorted by
     * dependency and appended in order to the result list along with its order of execution. Successive roots
     * can refer to the previous roots but they must be executed sequentially.
     * <p>
     * The DigitalTwin is asked to register the actuator in the scope and prepare the environment and state
     * for its execution, including defining its contextualization scale in context.
     *
     * @param dataflow
     * @return
     */
    private List<Pair<Actuator, Integer>> sortComputation(Actuator rootActuator,
                                                          Dataflow<Observation> dataflow,
                                                          ContextScope scope) {
        List<Pair<Actuator, Integer>> ret = new ArrayList<>();
        int executionOrder = 0;
        Map<Long, Actuator> branch = new HashMap<>();
        Set<Actuator> group = new HashSet<>();
        var dependencyGraph = computeActuatorOrder(rootActuator, scope);
        for (var nextActuator : ImmutableList.copyOf(new TopologicalOrderIterator<>(dependencyGraph))) {
            if (nextActuator.getActuatorType() != Actuator.Type.REFERENCE) {
                ret.add(Pair.of(nextActuator, (executionOrder = checkExecutionOrder
                        (executionOrder, nextActuator,
                                dependencyGraph, group))));
            }
        }
        return ret;
    }

    /**
     * If the actuator depends on any in the currentGroup, empty the group and increment the order; otherwise,
     * add it to the group and return the same order.
     *
     * @param executionOrder
     * @param current
     * @param dependencyGraph
     * @param currentGroup
     * @return
     */
    private int checkExecutionOrder(int executionOrder, Actuator current,
                                    Graph<Actuator, DefaultEdge> dependencyGraph,
                                    Set<Actuator> currentGroup) {
        boolean dependency = false;
        for (Actuator previous : currentGroup) {
            for (var edge : dependencyGraph.incomingEdgesOf(current)) {
                if (currentGroup.contains(dependencyGraph.getEdgeSource(edge))) {
                    dependency = true;
                    break;
                }
            }
        }

        if (dependency) {
            currentGroup.clear();
            return executionOrder + 1;
        }

        currentGroup.add(current);

        return executionOrder;
    }

}
