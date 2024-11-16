package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.knowledge.KnowledgeRepository;
import org.integratedmodelling.common.knowledge.ModelImpl;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ResolverCapabilitiesImpl;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.services.scopes.messaging.EmbeddedBroker;
import org.integratedmodelling.klab.utilities.Utils;

import java.io.File;
import java.util.*;

public class ResolverService extends BaseService implements Resolver {

    private static final String RESOLUTION_GRAPH_KEY = "__RESOLUTION_GRAPH__";
    /**
     * FIXME this should be modifiable at the scope level
     */
    private static double MINIMUM_WORTHWHILE_CONTRIBUTION = 0.15;

    /*
     * The knowledge repository. Models and instances are built and kept in the resolver upon input
     * from the resource services. For now we keep everything in memory.
     *
     * FIXME the URNs should include the version number after @ to ensure version matching instead
     * of only storing the latest version
     *
     * Version of the latest loaded object is kept for everything, including namespaces
     */
    //    Map<String, Version> urnToVersion = Collections.synchronizedMap(new HashMap<>());
    //    Map<String, Model> models = Collections.synchronizedMap(new HashMap<>());
    //    Map<String, Observation> instances = Collections.synchronizedMap(new HashMap<>());
    //    Parameters<String> defines = Parameters.createSynchronized();
    private String hardwareSignature = Utils.Names.getHardwareId();
    private ResolverConfiguration configuration;

    // OBVIOUSLY temporary - when all done, merge its methods with this and remove the porker and the old
    // dirt.
    private ResolutionCompiler resolutionCompiler = new ResolutionCompiler();

    public ResolverService(AbstractServiceDelegatingScope scope, ServiceStartupOptions options) {
        super(scope, Type.RESOLVER, options);
        //        setProvideScopesAutomatically(true);
        ServiceConfiguration.INSTANCE.setMainService(this);
        readConfiguration(options);
        KnowledgeRepository.INSTANCE.setProcessor(KlabAsset.KnowledgeClass.NAMESPACE, (ns) -> {
            return loadNamespace((KimNamespace) ns, scope);
        });
    }

    private void readConfiguration(ServiceStartupOptions options) {
        File config = BaseService.getFileInConfigurationDirectory(options, "resolver.yaml");
        if (config.exists() && config.length() > 0 && !options.isClean()) {
            this.configuration = Utils.YAML.load(config, ResolverConfiguration.class);
        } else {
            // make an empty config
            this.configuration = new ResolverConfiguration();
            this.configuration.setServiceId(UUID.randomUUID().toString());
            // TODO anything else we need
            saveConfiguration();
        }
    }

    private void saveConfiguration() {
        File config = BaseService.getFileInConfigurationDirectory(startupOptions, "resolver.yaml");
        org.integratedmodelling.common.utils.Utils.YAML.save(this.configuration, config);
    }

    @Override
    public boolean shutdown() {

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceUnavailable,
                capabilities(serviceScope()));

        // TODO Auto-generated method stub
        return super.shutdown();
    }

    @Override
    public Capabilities capabilities(Scope scope) {

        var ret = new ResolverCapabilitiesImpl();
        ret.setLocalName(localName);
        ret.setType(Type.RESOLVER);
        ret.setUrl(getUrl());
        ret.setServerId(hardwareSignature == null ? null : ("RESOLVER_" + hardwareSignature));
        ret.setServiceId(configuration.getServiceId());
        ret.setServiceName("Resolver");
        ret.setBrokerURI((embeddedBroker != null && embeddedBroker.isOnline()) ? embeddedBroker.getURI() :
                         configuration.getBrokerURI());
        ret.setAvailableMessagingQueues(Utils.URLs.isLocalHost(getUrl()) ?
                                        EnumSet.of(Message.Queue.Info, Message.Queue.Errors,
                                                Message.Queue.Warnings) :
                                        EnumSet.noneOf(Message.Queue.class));
        return ret;
    }

    @Override
    public Dataflow<Observation> resolve(Observation observation, ContextScope contextScope) {
        var ret = resolutionCompiler.resolve(observation, contextScope);
        if (!ret.isEmpty()) {
            return new DataflowCompiler(observation, ret, contextScope).compile();
        }
        return Dataflow.empty(Observation.class);
    }

    @Override
    public String serviceId() {
        return configuration.getServiceId();
    }

    private List<Knowledge> loadNamespace(KimNamespace namespace, Scope scope) {

        List<Knowledge> ret = new ArrayList<>();
        for (KlabStatement statement : namespace.getStatements()) {
            if (statement instanceof KimModel) {
                ret.add(loadModel((KimModel) statement, scope));
            } // TODO the rest (?) - also needs a symbol table etc
        }
        return ret;
    }

    private Model loadModel(KimModel statement, Scope scope) {

        var reasoner = scope.getService(Reasoner.class);

        ModelImpl model = new ModelImpl();
        model.getAnnotations().addAll(statement.getAnnotations()); // FIXME process annotations
        for (KimObservable observable : statement.getObservables()) {
            model.getObservables().add(reasoner.declareObservable(observable));
        }
        for (KimObservable observable : statement.getDependencies()) {
            model.getDependencies().add(reasoner.declareObservable(observable));
        }

        // TODO learners, geometry covered etc.
        model.setUrn(statement.getUrn());
        model.setMetadata(
                statement.getMetadata()); // FIXME add processed metadata with the existing symbol table
        model.setNamespace(statement.getNamespace());
        model.setProjectName(statement.getProjectName());

        // TODO any literal value must be added first

        for (var resourceUrn : statement.getResourceUrns()) {
            // FIXME this should be one multi-resource contextualizable
            model.getComputation().add(new ContextualizableImpl(resourceUrn));
        }
        model.getComputation().addAll(statement.getContextualization());

        // FIXME use coverage from NS or model if any
        model.setCoverage(Coverage.universal());

        return model;
    }

    @Override
    public boolean scopesAreReactive() {
        return false;
    }

    @Override
    public void initializeService() {

        Logging.INSTANCE.setSystemIdentifier("Resolver service: ");

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceInitializing,
                capabilities(serviceScope()).toString());

        /*
         * Components
         */
        Set<String> extensionPackages = new LinkedHashSet<>();
        extensionPackages.add("org.integratedmodelling.klab.runtime");
        /*
         * Check for updates, load and scan all new plug-ins, returning the main packages to scan
         */
        // FIXME update paths and simplify, put in BaseService
        //        extensionPackages.addAll(Configuration.INSTANCE.updateAndLoadComponents("resolver"));

        /*
         * Scan all packages registered under the parent package of all k.LAB services. TODO all
         * assets from there should be given default permissions (or those encoded with their
         * annotations) that are exposed to the admin API.
         */
        getComponentRegistry().loadExtensions(extensionPackages.toArray(new String[]{}));

        /**
         * Setup an embedded broker, possibly to be shared with other services, if we're local and there
         * is no configured broker.
         */
        if (Utils.URLs.isLocalHost(this.getUrl()) && this.configuration.getBrokerURI() == null) {
            this.embeddedBroker = new EmbeddedBroker();
        }

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceAvailable,
                capabilities(serviceScope()));

    }

    @Override
    public boolean operationalizeService() {
        return true;
    }

    @Override
    public String encodeDataflow(Dataflow<Observation> dataflow) {

        StringBuilder kdl = new StringBuilder(1024);

        Map<String, String> resources = new HashMap<>();
        for (Actuator actuator : dataflow.getComputation()) {
            kdl.append("\n");
            kdl.append(encodeActuator(actuator, 0, resources));
        }

        StringBuilder ret = new StringBuilder(2048);
        ret.append(encodePreamble(dataflow));
        ret.append("\n");
        var res = encodeResources(dataflow, resources);
        if (!res.isEmpty()) {
            ret.append(res);
            ret.append("\n");
        }
        ret.append(kdl);

        // if (offset == 0 && parentActuator == null) {
        // ret += "@klab " + Version.CURRENT + "\n";
        // ret += "@author 'k.LAB resolver " + creationTime + "'" + "\n";
        // // TODO should encode coverage after the resolver.
        // // if (coverage != null && coverage.getExtentCount() > 0) {
        // // List<IServiceCall> scaleSpecs = ((Scale) coverage).getKimSpecification();
        // // if (!scaleSpecs.isEmpty()) {
        // // ret += "@coverage load_me_from_some_sidecar_file()";
        // // ret += "\n";
        // // }
        // // }
        // ret += "\n";
        // }
        //
        // Pair<IActuator, List<IActuator>> structure = getResolutionStructure();
        //
        // if (structure == null) {
        // for (IActuator actuator : actuators) {
        // ret += ((Actuator) actuator).encode(offset, null) + "\n";
        // }
        // return ret;
        // }
        //
        // return ret + ((Actuator) structure.getFirst()).encode(0,
        // structure.getSecond().isEmpty() ? (List<IActuator>) null : structure.getSecond());
        return ret.toString();
    }

    private StringBuffer encodeResources(Dataflow<Observation> dataflow, Map<String, String> resources) {
        StringBuffer ret = new StringBuffer(1024);
        // TODO
        return ret;
    }

    private StringBuffer encodePreamble(Dataflow<Observation> dataflow) {
        StringBuffer ret = new StringBuffer(1024);
        ret.append("@klab " + Version.CURRENT + "\n");
        ret.append("@author 'k.LAB resolver " + TimeInstant.create().toRFC3339String() + "'" + "\n");
        return ret;
    }

    private StringBuffer encodeActuator(Actuator actuator, int offset, Map<String, String> resources) {
        String ofs = org.integratedmodelling.common.utils.Utils.Strings.spaces(offset);
        StringBuffer ret = new StringBuffer(1024);

        ret.append(ofs + actuator.getObservable().getDescriptionType().getKdlType() + " "
                + actuator.getObservable().getReferenceName() + " (\n");

        for (Actuator child : actuator.getChildren()) {
            ret.append(encodeActuator(child, offset + 2, resources));
        }

        boolean done = false;
        for (ServiceCall contextualizable : actuator.getComputation()) {
            if (!done) {
                ret.append(ofs + ofs + "compute\n");
            }
            ret.append(encodeServiceCall(contextualizable, offset + 6, resources) + "\n");
            done = true;
        }

        /*
         * ? coverage
         */

        //        ret.append(ofs + ")" + (actuator.getAlias() == null ? "" : (" named " + actuator.getAlias
        //        ()))
        //                + (actuator.getObservable().getObserver() == null
        //                ? ""
        //                : (" as " + actuator.getObservable().getObserver().getName()))
        //                + "\n");

        return ret;
    }

    private String encodeServiceCall(ServiceCall contextualizable, int offset,
                                     Map<String, String> resources) {
        // TODO extract resource parameters and substitute with variables
        return org.integratedmodelling.common.utils.Utils.Strings.spaces(offset) + contextualizable.encode(
                Language.KDL);
    }

    /**
     * Replicate a remote scope in the scope manager. This should be called by the runtime service after
     * creating it so if the scope has no ID we issue an error, as we do not create independent scopes.
     *
     * @param sessionScope a client scope that should record the ID for future communication. If the ID is
     *                     null, the call has failed.
     * @return
     */
    @Override
    public String registerSession(SessionScope sessionScope) {

        if (sessionScope instanceof ServiceSessionScope serviceSessionScope) {

            if (sessionScope.getId() == null) {
                throw new KlabIllegalArgumentException("resolver: session scope has no ID, cannot register " +
                        "a scope autonomously");
            }

            getScopeManager().registerScope(serviceSessionScope, capabilities(sessionScope).getBrokerURI());
            return serviceSessionScope.getId();
        }

        throw new KlabIllegalArgumentException("unexpected scope class");
    }

    /**
     * Replicate a remote scope in the scope manager. This should be called by the runtime service after
     * creating it so if the scope has no ID we issue an error, as we do not create independent scopes.
     *
     * @param contextScope a client scope that should record the ID for future communication. If the ID is
     *                     null, the call has failed.
     * @return
     */
    @Override
    public String registerContext(ContextScope contextScope) {

        contextScope.getData().put(RESOLUTION_GRAPH_KEY, ResolutionGraph.create(contextScope));

        if (contextScope instanceof ServiceContextScope serviceContextScope) {

            if (contextScope.getId() == null) {
                throw new KlabIllegalArgumentException("resolver: context scope has no ID, cannot register " +
                        "a scope autonomously");
            }

            getScopeManager().registerScope(serviceContextScope, capabilities(contextScope).getBrokerURI());
            return serviceContextScope.getId();
        }

        throw new KlabIllegalArgumentException("unexpected scope class");

    }

    public static ResolutionGraph getResolutionGraph(ContextScope scope) {
        return scope.getData().get(RESOLUTION_GRAPH_KEY, ResolutionGraph.class);
    }

}
