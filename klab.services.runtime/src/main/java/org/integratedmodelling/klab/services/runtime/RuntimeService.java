package org.integratedmodelling.klab.services.runtime;

import org.apache.groovy.util.Maps;
import org.apache.qpid.server.SystemLauncher;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.klab.api.data.GraphDatabase;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.kactors.messages.InstrumentContextScope;
import org.integratedmodelling.klab.runtime.kactors.messages.InstrumentSessionScope;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.GraphDatabaseNeo4jEmbedded;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceSessionScope;
import org.integratedmodelling.klab.utilities.Utils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;

public class RuntimeService extends BaseService implements org.integratedmodelling.klab.api.services.RuntimeService, org.integratedmodelling.klab.api.services.RuntimeService.Admin {

    private static final String EMBEDDED_BROKER_CONFIGURATION = "klab-broker-config.json";
    private static final int EMBEDDED_BROKER_PORT = 20937;

    private String hardwareSignature =
            org.integratedmodelling.common.utils.Utils.Strings.hash(Utils.OS.getMACAddress());
    private RuntimeConfiguration configuration;
    private GraphDatabase graphDatabase;
    private SystemLauncher systemLauncher;
    private URI embeddedBrokerURI = null;

    public RuntimeService(ServiceScope scope, ServiceStartupOptions options) {
        super(scope, Type.RUNTIME, options);
        readConfiguration(options);
        initializeMessaging();
    }

    private void initializeMessaging() {


        // CLIENT CODE:
        //        SaslConfig saslConfig = new SaslConfig() {
        //            public SaslMechanism getSaslMechanism(String[] mechanisms) {
        //                return new SaslMechanism() {
        //                    public String getName() {
        //                        return "ANONYMOUS";
        //                    }
        //
        //                    public LongString handleChallenge(LongString challenge, String username,
        //                    String password) {
        //                        return LongStringHelper.asLongString("");
        //                    }
        //                };
        //            }
        //        };
        //        ConnectionFactory factory = new ConnectionFactory();
        //        factory.setHost("localhost");
        //        factory.setPort(20179);
        //        factory.setSaslConfig(saslConfig);
        //
        //        Connection connection = factory.newConnection();
        //        Channel channel = connection.createChannel();

        if (this.configuration.getBrokerURI() == null) {
            // TODO review authentication: should use simple auth with random credentials published in the
            //  context authorization response. This is anonymous, which is only OK for local runtimes.
            //            Should be something like:
            //            "authenticationproviders" : [ {
            //                "id" : "88d0c7eb-4a75-4e5e-85ff-19185e0394d7",
            //                        "name" : "plain",
            //                        "type" : "Plain",
            //                        "secureOnlyMechanisms": "",
            //                        "users" : [ {
            //                            "id" : "4ebb8d66-f8e0-4efb-9bb9-c4578292ab43",
            //                            "name" : "guest",
            //                            "type" : "managed",
            //                            "password" : "guest"
            //                } ]
            //            } ]
            // use a random password regenerated at each boot (or even changed periodically with a message for
            // subscribers)
            Map<String, Object> attributes = new HashMap<>();
            URL initialConfig = this.getClass().getClassLoader().getResource(EMBEDDED_BROKER_CONFIGURATION);
            attributes.put("type", "Memory");
            attributes.put("startupLoggedToSystemOut", true);
            attributes.put("initialConfigurationLocation", initialConfig.toExternalForm());
            try {
                this.systemLauncher = new SystemLauncher();
                if (System.getProperty("QPID_WORK") == null) {
                    // this works; setting qpid.work_dir in the attributes does not.
                    System.setProperty("QPID_WORK", BaseService.getConfigurationSubdirectory(startupOptions
                            , "broker").toString());
                }
                systemLauncher.startup(attributes);
                this.embeddedBrokerURI = new URI("amqp://127.0.0.1:" + EMBEDDED_BROKER_PORT);
                serviceScope().info("Embedded broker available for local connections on " + this.embeddedBrokerURI);
            } catch (Exception e) {
                serviceScope().error("Error initializing embedded broker: " + e.getMessage());
            }
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

    private boolean createGraphDatabase() {
        // TODO choose the DB from configuration - client or embedded server
        var path = BaseService.getConfigurationSubdirectory(startupOptions, "dt").toPath();
        this.graphDatabase = new GraphDatabaseNeo4jEmbedded(path);
        return this.graphDatabase.isOnline();
    }

    public GraphDatabase getGraphDatabase() {
        return this.graphDatabase;
    }

    private void saveConfiguration() {
        File config = BaseService.getFileInConfigurationDirectory(startupOptions, "runtime.yaml");
        org.integratedmodelling.common.utils.Utils.YAML.save(this.configuration, config);
    }

    @Override
    public void initializeService() {

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceInitializing,
                capabilities(serviceScope()).toString());

        /*
         * Components
         */
        Set<String> extensionPackages = new LinkedHashSet<>();
        extensionPackages.add("org.integratedmodelling.klab.runtime");
        extensionPackages.add("org.integratedmodelling.klab.runtime.temporary");

        if (createGraphDatabase()) {

            /*
             * Check for updates, load and scan all new plug-ins, returning the main packages to scan
             * FIXME update, put in BaseService
             */
            //        extensionPackages.addAll(Configuration.INSTANCE.updateAndLoadComponents("resolver"));

            /*
             * Scan all packages registered under the parent package of all k.LAB services. TODO all
             * assets from there should be given default permissions (or those encoded with their
             * annotations) that are exposed to the admin API.
             */
            for (String pack : extensionPackages) {
                ServiceConfiguration.INSTANCE.scanPackage(pack, Maps.of(Library.class,
                        ServiceConfiguration.INSTANCE.LIBRARY_LOADER));
            }

            serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceAvailable
                    , capabilities(serviceScope()));
        } else {

            serviceScope().send(Message.MessageClass.ServiceLifecycle,
                    Message.MessageType.ServiceUnavailable, capabilities(serviceScope()));

        }

    }

    @Override
    public boolean shutdown() {

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceUnavailable,
                capabilities(serviceScope()));
        if (systemLauncher != null) {
            systemLauncher.shutdown();
        }
        if (graphDatabase != null) {
            graphDatabase.shutdown();
        }
        return super.shutdown();
    }

    @Override
    public Capabilities capabilities(Scope scope) {

        /*
        TODO if scope is admin, add descriptors for all the DTs and their data
         */
        return new RuntimeCapabilitiesImpl() {

            @Override
            public Type getType() {
                return Type.RUNTIME;
            }

            @Override
            public String getLocalName() {
                return localName;
            }

            @Override
            public String getServiceName() {
                return "Runtime";
            }

            @Override
            public String getServiceId() {
                return serviceId();
            }

            @Override
            public String getServerId() {
                return hardwareSignature == null ? null : ("REASONER_" + hardwareSignature);
            }

            @Override
            public URI getBrokerURI() {
                return (scope != null && scope.getIdentity().isAuthenticated()) ?
                       (configuration.getBrokerURI() != null ? configuration.getBrokerURI() :
                        embeddedBrokerURI) : null;
            }
        };
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
    public boolean releaseScope(Scope scope) {
        //
        //        /**
        //         * TODO fix based on the type of scope. Each should release every resource held below the
        //          scope.
        //         */
        //        var dt = this.digitalTwins.remove(scope.getIdentity().getId());
        //        if (dt != null) {
        //            try {
        //                dt.close();
        //            } catch (IOException e) {
        //                throw new KlabInternalErrorException(e);
        //            }
        //        }
        //        return dt != null;
        return true;
    }

    @Override
    public String registerSession(SessionScope sessionScope) {
        if (sessionScope instanceof ServiceSessionScope serviceSessionScope) {
            serviceSessionScope.setId(Utils.Names.shortUUID());
            getScopeManager().registerScope(serviceSessionScope, capabilities(sessionScope).getBrokerURI());
            serviceSessionScope.getAgent().tell(new InstrumentSessionScope(capabilities(sessionScope).getBrokerURI()));
            return serviceSessionScope.getId();
        }
        throw new KlabIllegalArgumentException("unexpected scope class");
    }

    @Override
    public String registerContext(ContextScope contextScope) {

        if (contextScope instanceof ServiceContextScope serviceContextScope) {

            serviceContextScope.setId(serviceContextScope.getParentScope().getId() + "." + Utils.Names.shortUUID());
            getScopeManager().registerScope(serviceContextScope, capabilities(contextScope).getBrokerURI());

            /*
            create the digital twin and send it to the scope's actor where it will be managed. The runtime
            in the scope is guaranteed to exist and be this
             */
            var digitalTwin = new DigitalTwinImpl(contextScope, getGraphDatabase());

            /*
            TODO instrument the actor for messaging if the request declares the ability to connect
             */
            serviceContextScope.getAgent().tell(new InstrumentContextScope(digitalTwin,
                    capabilities(contextScope).getBrokerURI()));


            return serviceContextScope.getId();

        }
        throw new KlabIllegalArgumentException("unexpected scope class");
    }

    @Override
    public long observe(ContextScope scope, Object... resolvables) {
        // TODO this is the actual shit. Talk to the actor in the scope, sending the proper message.
        return Observation.UNASSIGNED_ID;
    }

}
