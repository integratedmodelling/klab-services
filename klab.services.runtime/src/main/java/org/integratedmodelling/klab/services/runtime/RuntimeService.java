package org.integratedmodelling.klab.services.runtime;

import com.google.common.collect.ImmutableList;
import org.apache.qpid.server.SystemLauncher;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.DescriptionType;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
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

        /*
         * Components
         */
        Set<String> extensionPackages = new LinkedHashSet<>();
        //        extensionPackages.add("org.integratedmodelling.klab.runtime");
        //        extensionPackages.add("org.integratedmodelling.klab.runtime.temporary");

        if (createMainKnowledgeGraph()) {

            /*
             * Check for updates, load and scan all new plug-ins, returning the main packages to scan
             * FIXME update, put in BaseService
             */
            //        extensionPackages.addAll(Configuration.INSTANCE.updateAndLoadComponents("resolver"));

            // TODO internal libraries
            getComponentRegistry().loadExtensions("org.integratedmodelling.klab.runtime");
            getComponentRegistry().initializeComponents(BaseService.getConfigurationSubdirectory(startupOptions, "components"));

            //            for (String pack : extensionPackages) {
            //                ServiceConfiguration.INSTANCE.scanPackage(pack, Maps.of(
            //                        Library.class,
            //                        ServiceConfiguration.INSTANCE.LIBRARY_LOADER));
            //            }

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
        //        ret.setAvailableMessagingQueues(Utils.URLs.isLocalHost(getUrl()) ?
        //                                        EnumSet.of(Message.Queue.Info, Message.Queue.Errors,
        //                                                Message.Queue.Warnings) :
        //                                        EnumSet.noneOf(Message.Queue.class));
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

    /**
     * Ensure that we have the runtime support for the passed service call. If we need a component to serve
     * it, check that the scope has access to it and load it if necessary as a background process. Return all
     * the relevant notifications which will be passed to clients. If one or more error notifications are
     * return, the service call is invalid and any dataflow it is part of is in error.
     *
     * @param call
     * @param scope
     * @return any notifications. Empty mean "all OK for execution".
     */
    public Collection<Notification> validateServiceCall(ServiceCall call, Scope scope) {
        List<Notification> ret = new ArrayList<>();
        // TODO
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
            serviceContextScope.setDigitalTwin(new DigitalTwinImpl(contextScope, getMainKnowledgeGraph()));

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
    public long submit(Observation observation, ContextScope scope, boolean startResolution) {
        if (scope instanceof ServiceContextScope serviceContextScope) {
            return startResolution
                   ? serviceContextScope.observe(observation).trackingKey()
                   : serviceContextScope.insertIntoKnowledgeGraph(observation);
        }
        return -1L;
    }

    @Override
    public Coverage runDataflow(Dataflow<Observation> dataflow, ContextScope contextScope) {

        
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
            run(rootActuator, contextScope, digitalTwin);
        }

        /*
        intersect coverage from dataflow with contextualization scale
         */

        return null;
    }

    private DigitalTwin getDigitalTwin(ContextScope contextScope) {
        if (contextScope instanceof ServiceContextScope serviceContextScope) {
            return serviceContextScope.getDigitalTwin();
        }
        throw new KlabInternalErrorException("Digital twin is inaccessible because of unexpected scope implementation");
    }

    private void run(Actuator rootActuator, ContextScope scope, DigitalTwin digitalTwin) {

        ExecutionContext executionContext = new ExecutionContext(rootActuator, (ServiceContextScope) scope, digitalTwin);

        /*
        Turn the actuator hierarchy into a flat list in dependency order. Both actuator containment and
        reference count as
        dependencies between observations.
         */
        ExecutionContext currentContext = executionContext;
        for (Actuator actuator : computeActuatorOrder(rootActuator, scope)) {

            currentContext = currentContext.runActuator(actuator);

            if (currentContext.isEmpty()) {
                scope.error(currentContext.statusLine(), currentContext.errorCode(),
                        currentContext.errorContext(), currentContext.statusInfo());
                break;
            }

            /*
            Observation was computed: submit the actuator and its provenance info to the digital twin
             */

        }

    }

    private List<Actuator> computeActuatorOrder(Actuator rootActuator, ContextScope scope) {
        Graph<Actuator, DefaultEdge> dependencyGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        Map<Long, Actuator> cache = new HashMap<>();
        loadGraph(rootActuator, dependencyGraph, cache);
        // keep the actuators that do nothing so we can tag their observation as resolved
        return ImmutableList.copyOf(new TopologicalOrderIterator<>(dependencyGraph));
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
        return List.of();
    }


    // PORTED FROM PREVIOUS

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
    private List<Pair<Actuator, Integer>> sortComputation(Dataflow<Observation> dataflow, ExecutionContext executionContext,
                                                          ContextScope scope) {
        List<Pair<Actuator, Integer>> ret = new ArrayList<>();
        for (Actuator root : dataflow.getComputation()) {
            int executionOrder = 0;
            Map<String, Actuator> branch = new HashMap<>();
            collectActuators(Collections.singletonList(root), dataflow, scope, null, branch);
            var dependencyGraph = createDependencyGraph(branch);
            TopologicalOrderIterator<Actuator, DefaultEdge> order =
                    new TopologicalOrderIterator<>(dependencyGraph);

            // group by dependency w.r.t. the previous group and assign the execution order based on the
            // group index, so that we know what we can execute in parallel
            Set<Actuator> group = new HashSet<>();
            while (order.hasNext()) {
                Actuator next = order.next();
                if (next.getActuatorType() != Actuator.Type.REFERENCE) {
                    // FIXME PASS THE ExecutionContext and find the actuator in there
//                    var data = observationData.get(next.getId());
//                    if (!data.executors.isEmpty()) {
//                        ret.add(Pair.of(next, (executionOrder = checkExecutionOrder(executionOrder, next,
//                                dependencyGraph, group))));
//                    }
                }
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

    private void collectActuators(List<Actuator> actuators, Dataflow<Observation> dataflow, ContextScope scope,
                                  Observation contextObservation, Map<String, Actuator> ret) {
        var context = contextObservation;
        for (Actuator actuator : actuators) {
            if (registerActuator(actuator, dataflow, scope, contextObservation)) {
                /*
                 * TODO compile a list of all services + versions, validate the actuator, create
                 * any needed notifications and a table of translations for local names
                 */
                if (actuator.getObservable().getDescriptionType() == DescriptionType.ACKNOWLEDGEMENT) {
//                    var odata = this.observationData.get(actuator.getId());
//                    context = (DirectObservation) odata.observation;
                }
                //                ret.put(actuator.getId(), actuator);
            }
            collectActuators(actuator.getChildren(), dataflow, scope, context, ret);
        }
    }

    /**
     * Build and return the dependency graph for the passed actuators. Save externally if appropriate -
     * caching does create issues in contextualization and scheduling.
     *
     * @return
     */
    public Graph<Actuator, DefaultEdge> createDependencyGraph(Map<String, Actuator> actuators) {
        Graph<Actuator, DefaultEdge> ret = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (Actuator actuator : actuators.values()) {
            ret.addVertex(actuator);
            for (Actuator child : actuator.getChildren()) {
                var ref = actuators.get(child.getId());
                if (ref != null) {
                    ret.addVertex(ref);
                    ret.addEdge(child, actuator);
                }
            }
        }
        return ret;
    }

    /**
     * Register an actuator and create all support info before execution. Return true if the actuator is new
     * and has computations.
     *
     * @param actuator
     * @param scope
     * @return
     */
    public boolean registerActuator(Actuator actuator, Dataflow<Observation> dataflow, ContextScope scope,
                                    Observation contextObservation) {

        //        var data = observationData.get(actuator.getId());
        //        if (data == null && /* shouldn't happen */ !actuator.isReference()) {
        //            data = new ObservationData();
        //            data.actuator = actuator;
        ////            data.observation = createObservation(actuator, contextObservation, scope);
        //            data.scale = Scale.create(scope.getContextObservation().getGeometry());
        //            data.contextObservation = contextObservation;
        //
        ////            var customScale = dataflow.getResources().get((actuator.getId() + "_dataflow"), Scale.class);
        ////            if (customScale != null) {
        ////                // FIXME why the heck is this an Object and I have to cast?
        ////                data.scale = data.scale.merge((Scale) customScale, LogicalConnector.INTERSECTION);
        ////            }
        //
        //            for (Actuator child : actuator.getChildren()) {
        ////                if (child.isInput() && !child.getName().equals(child.getAlias())) {
        ////                    data.localNames.put(child.getName(), child.getAlias());
        ////                }
        //            }
        //
        //            Executor executor = null;
        //            for (var computation : data.actuator.getComputation()) {
        //                var step = createExecutor(actuator, data.observation, computation, scope, executor);
        //                if (executor != step) {
        //                    data.executors.add(step);
        //                }
        //                executor = step;
        //            }
        //
        //            observationData.put(actuator.getId(), data);
        //
        //            return true;
        //        }

        return false;
    }

}
