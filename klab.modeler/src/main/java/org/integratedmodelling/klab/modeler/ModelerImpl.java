package org.integratedmodelling.klab.modeler;

import org.integratedmodelling.common.authentication.scope.AbstractReactiveScopeImpl;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.engine.EngineClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.common.view.AbstractUIController;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.data.Repository;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.UI;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.modeler.Modeler;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.modeler.configuration.EngineConfiguration;
import org.integratedmodelling.klab.modeler.model.NavigableKlabStatement;
import org.integratedmodelling.klab.modeler.model.NavigableProject;
import org.integratedmodelling.klab.modeler.panels.controllers.DocumentEditorControllerImpl;
import org.integratedmodelling.klab.modeler.views.controllers.*;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link UIController} specialized to provide and orchestrate the views and panels that compose the
 * k.Modeler application. Uses an {@link EngineClient} which will connect to local services if available. Also
 * handles one or more users and keeps a catalog of sessions and contexts, tagging the "current" one in focus
 * in the UI.
 * <p>
 * Call {@link #boot()} in a separate thread when the view is initialized and let the UI events do the rest.
 */
public class ModelerImpl extends AbstractUIController implements Modeler, PropertyHolder {

    private ContextScope currentContext;
    private SessionScope currentSession;
    EngineConfiguration workbench;
    File workbenchDefinition;
    private Map<String, URL> serviceUrls = new HashMap<>();
    private Geometry focalGeometry = Geometry.EMPTY;

    public ModelerImpl() {
        super();
        // read the workbench config
        this.workbenchDefinition = Configuration.INSTANCE.getFileWithTemplate("modeler/workbench.yaml",
                Utils.YAML.asString(new EngineConfiguration()));
        this.workbench = Utils.YAML.load(workbenchDefinition, EngineConfiguration.class);
    }

    public ModelerImpl(UI ui) {
        super(ui);
        // TODO read the workbench config - NAH this probably pertains to the IDE
    }

    @Override
    public void dispatch(UIReactor sender, UIEvent event, Object... payload) {

        // intercept some messages for bookkeeping
        if (event == UIEvent.ServiceAvailable && payload.length > 0 && payload[0] instanceof KlabService.ServiceCapabilities capabilities) {
            if (capabilities.getUrl() != null) {
                serviceUrls.put(capabilities.getServiceId(), capabilities.getUrl());
            }
            if (capabilities.getBrokerURI() != null && scope() instanceof AbstractReactiveScopeImpl serviceClient) {
                /*
                 * Instrument the service client for messaging. This is pretty involved alas, but the whole
                 * matter isn't exactly trivial.
                 */
                var client = serviceClient.getService(capabilities.getServiceId());
                if (client != null && client.serviceScope() instanceof AbstractServiceDelegatingScope delegatingScope
                        && delegatingScope.getDelegateChannel() instanceof MessagingChannel messagingChannel) {
                    /*
                     * If the scope delegates to a messaging channel, set up messaging and link the
                     * available  service queues to service message dispatchers.
                     */
                    messagingChannel.connectToService(capabilities, (UserIdentity) user().getIdentity(),
                            (message) -> dispatchServerMessage(capabilities, message));
                }
            }
        }

        super.dispatch(sender, event, payload);
    }

    private void dispatchServerMessage(KlabService.ServiceCapabilities capabilities, Message message) {
        // TODO do things
        System.out.println("SERVER MESSAGE FROM " + capabilities.getType() + " " + capabilities.getServiceId() + ": " + message);
    }

    @Override
    public Engine createEngine() {
        // TODO first should locate and set the distribution
        return new EngineClient();
    }

    @Override
    protected void createView() {

        /*
        pre-built view controllers. View implementations will self-register upon creation.
         */
        registerViewController(new ServicesViewControllerImpl(this));
        registerViewController(new DistributionViewImplController(this));
        registerViewController(new ResourcesNavigatorControllerImpl(this));
        registerViewController(new ContextInspectorControllerImpl(this));
        registerViewController(new AuthenticationViewControllerImpl(this));
        registerViewController(new ContextControllerImpl(this));
        registerViewController(new KnowledgeInspectorControllerImpl(this));
        // TODO etc.

        /*
        panel classes
         */
        registerPanelControllerClass(DocumentEditorControllerImpl.class);
    }

    @Override
    public void switchWorkbenchService(UIReactor requestingReactor, KlabService.ServiceCapabilities service) {
        // TODO
        super.switchWorkbenchService(requestingReactor, service);
    }

    @Override
    public void switchWorkbench(UIReactor requestingReactor, NavigableContainer container) {
        if (getUI() != null) {
            // we assume that the workspace is mainly intended to show documents and focus on assets.
            // Switching the focal container changes all that, so we first clean everything.
            getUI().cleanWorkspace();
        }
        super.switchWorkbench(requestingReactor, container);
    }

    @Override
    public void configureWorkbench(UIReactor requestingReactor, NavigableDocument document, boolean shown) {
        // TODO
        super.configureWorkbench(requestingReactor, document, shown);
    }

    @Override
    public void setOption(Option option, Object... payload) {
        // TODO validate option
        // TODO react
    }

    @Override
    public void observe(Object asset, boolean adding) {

        if (currentUser() == null) {
            throw new KlabAuthorizationException("Cannot make observations with an invalid user");
        }

        /**
         * Use cases:
         *
         * Admitted with a current context or focal scale
         *
         *     Concept (from ontology, knowledge explorer/inspector or define)
         *          Promote to observable (if countable becomes collective)
         *     Observable (from define or knowledge inspector)
         *          Observe as expected
         *     Model (from namespace or search)
         *          Observe as expected
         *     Resource from catalog (local or remote)
         *          Observe with non-semantic observable
         *     Observation from define (can be inline, a URN#ID, other)
         *          If adding==true, any existing context is preserved and added to
         *          If adding==false, a new context is created and any previous goes out of focus
         *     Observation from context tree
         *          Just sets the target for the next observations
         *
         *     ALL can be either an object or a URN or DOI from inside or outside
         *
         * Admitted w/o a current context or focal scale
         *
         *      Observation from define (can be inline, a URN#ID, other)
         *
         *      If there is no session, must create a default session & select it
         *      If there is no context, must create a default empty context within the session & select it
         */

        if (currentSession == null) {
            currentSession = createSession("Default session");
        }

        if (currentContext == null && currentSession != null) {
            currentContext = createContext(currentSession, "Default context");
        }

        if (currentContext == null) {
            currentSession.error("cannot make observation");
            return;
        }

        /* TODO handle observer, if not there make it and set it into the context. The logic should be
         *   configurable and specific of the modeler, no defaults should be used in the runtime. */
        List<Object> resolvables = new ArrayList<>();

        /**
         * Assets are observed by URN unless they're models or observation definitions
         */
        if (asset instanceof NavigableAsset navigableAsset) {

            if (navigableAsset instanceof NavigableKlabStatement statement) {
                var delegate = statement.getDelegate();
                if (delegate instanceof KimModel || (delegate instanceof KimSymbolDefinition definition &&
                        "observation".equals(definition.getDefineClass()))) {
                    resolvables.add(delegate);
                } else if (delegate instanceof KimConceptStatement conceptStatement) {
                    resolvables.add(conceptStatement.getNamespace() + ":" + conceptStatement.getUrn());
                } else if (delegate instanceof KimObservable conceptStatement) {
                    resolvables.add(conceptStatement.getUrn());
                }
            }
        } else if (asset instanceof String || asset instanceof Urn) {
            resolvables.add(asset.toString());
        }

        currentContext.observe(DigitalTwin.createObservation(currentContext, resolvables.toArray()));

    }

    private SessionScope createSession(String sessionName) {
        var runtime = engine().serviceScope().getService(RuntimeService.class);
        return currentUser().runSession(sessionName);
    }

    private ContextScope createContext(SessionScope sessionScope, String contextName) {
        var runtime = engine().serviceScope().getService(RuntimeService.class);
        return sessionScope.createContext(contextName);
    }

    @Override
    public void importProject(String workspaceName, String projectUrl, boolean overwriteExisting) {

        var resources = engine().serviceScope().getService(ResourcesService.class);
        if (resources instanceof ResourcesService.Admin admin) {
            Thread.ofVirtual().start(() -> {
                var ret = admin.importProject(workspaceName, projectUrl, overwriteExisting,
                        engine().serviceScope());
                if (ret != null) {
                    handleResultSets(ret);
                }
            });
        } else if (getUI() != null) {
            getUI().alert(Notification.create("Service does not support this operation",
                    Notification.Level.Warning));
        }

    }

    @Override
    public void deleteProject(String projectUrl) {

        if (getUI() != null) {
            if (!getUI().confirm(Notification.create("Confirm unrecoverable deletion of project " + projectUrl + "?"))) {
                return;
            }
        }

        var resources = engine().serviceScope().getService(ResourcesService.class);
        if (resources instanceof ResourcesService.Admin admin) {
            Thread.ofVirtual().start(() -> {
                var ret = admin.deleteProject(projectUrl, scope());
                handleResultSets(ret);
            });
        } else if (getUI() != null) {
            getUI().alert(Notification.create("Service does not support this operation",
                    Notification.Level.Warning));
        }
    }

    @Override
    public void deleteAsset(NavigableAsset asset) {

        if (getUI() != null) {
            if (!getUI().confirm(Notification.create("Confirm unrecoverable deletion of " + asset.getUrn() + "?"))) {
                return;
            }
        }

        var resources = engine().serviceScope().getService(ResourcesService.class);
        if (resources instanceof ResourcesService.Admin admin) {
            Thread.ofVirtual().start(() -> {
                var project = asset.parent(NavigableProject.class);
                var ret = admin.deleteDocument(project.getUrn(), asset.getUrn(), scope());
                handleResultSets(ret);
            });
        } else if (getUI() != null) {
            getUI().alert(Notification.create("Service does not support this operation",
                    Notification.Level.Warning));
        }

    }

    @Override
    public void manageProject(String projectId, Repository.Operation operation, String... arguments) {

        var resources = engine().serviceScope().getService(ResourcesService.class);
        if (resources instanceof ResourcesService.Admin admin) {
            Thread.ofVirtual().start(() -> {
                var ret = ((ResourcesService.Admin) resources).manageRepository(projectId, operation,
                        arguments);
                handleResultSets(ret);
            });
        } else if (getUI() != null) {
            getUI().alert(Notification.create("Service does not support this operation",
                    Notification.Level.Warning));
        }
    }

    private void handleResultSets(List<ResourceSet> ret) {
        if (ret != null && !ret.isEmpty()) {
            for (var change : ret) {
                if (Utils.Notifications.hasErrors(change.getNotifications())) {
                    if (getUI() != null) {
                        getUI().alert(Utils.Notifications.merge(change.getNotifications(),
                                Notification.Level.Error));
                    }
                } else {
                    dispatch(this, UIEvent.WorkspaceModified, change);
                }
            }
        }
    }

    @Override
    public void editProperties(String projectId) {

    }

    @Override
    public void createDocument(String newDocumentUrn, String projectName,
                               ProjectStorage.ResourceType documentType) {
        var resources = engine().serviceScope().getService(ResourcesService.class);
        if (resources instanceof ResourcesService.Admin admin) {
            Thread.ofVirtual().start(() -> {
                var changes = admin.createDocument(projectName, newDocumentUrn, documentType, scope());
                if (changes != null) {
                    for (var change : changes) {
                        dispatch(this, UIEvent.WorkspaceModified, change);
                    }
                }
            });
        } else if (getUI() != null) {
            getUI().alert(Notification.create("Service does not support this operation",
                    Notification.Level.Warning));
        }
    }

    @Override
    public UserScope user() {
        return ((EngineClient) engine()).getUser();
    }

    @Override
    public void setDefaultService(KlabService.ServiceCapabilities service) {
        if (engine() instanceof EngineClient engine) {
            engine.setDefaultService(service);
        } else {
            engine().serviceScope().warn("Modeler: request to set default service wasn't honored " +
                    "because " + "the engine " + "implementation is overridden");
        }
    }

    @Override
    protected Scope scope() {
        return user();
    }

    @Override
    public String configurationPath() {
        return "modeler";
    }

    public UserScope currentUser() {
        return engine() == null || engine().getUsers().isEmpty() ? null : engine().getUsers().getFirst();
    }

    public SessionScope currentSession() {
        // TODO
        return currentSession;
    }

    public ContextScope currentContext() {
        // TODO
        return currentContext;
    }

    public ContextScope context(String context, boolean createIfAbsent) {
        // TODO named context
        return null;
    }

    public UserScope user(String username) {
        // TODO named user
        return null;
    }

    public SessionScope session(String session, boolean createIfAbsent) {
        // TODO named session
        return null;
    }

    @Override
    public URL serviceUrl(String serviceId) {
        return serviceUrls.get(serviceId);
    }

    @Override
    public UIController getController() {
        return this;
    }
}
