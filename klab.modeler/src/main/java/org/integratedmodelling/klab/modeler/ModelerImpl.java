package org.integratedmodelling.klab.modeler;

import org.integratedmodelling.common.services.client.engine.EngineClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.common.view.AbstractUIController;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.PropertyHolder;
import org.integratedmodelling.klab.api.data.Repository;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.UI;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.modeler.Modeler;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.modeler.configuration.EngineConfiguration;
import org.integratedmodelling.klab.modeler.model.NavigableProject;
import org.integratedmodelling.klab.modeler.panels.controllers.DocumentEditorControllerImpl;
import org.integratedmodelling.klab.modeler.views.controllers.*;

import java.io.File;
import java.util.List;

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
    public void observe(KlabAsset asset, boolean adding) {

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
         *      Observation from context tree
         *          Just sets the target for the next observations
         *
         * Admitted w/o a current context or focal scale
         *
         *      Observation from define (can be inline, a URN#ID, other)
         *
         *      If there is no session, must create a default session & select it
         *      If there is no context, must create a default empty context within the session & select it
         */


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
            if (!getUI().confirm(Notification.create("Confirm unrecoverable deletion of project " + projectUrl +
                    "?"))) {
                return;
            }
        }

        var resources = engine().serviceScope().getService(ResourcesService.class);
        if (resources instanceof ResourcesService.Admin admin) {
            Thread.ofVirtual().start(() -> {
                var ret = admin.deleteProject(projectUrl, scope().getIdentity().getId());
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
            if (!getUI().confirm(Notification.create("Confirm unrecoverable deletion of " + asset.getUrn() +
                    "?"))) {
                return;
            }
        }

        var resources = engine().serviceScope().getService(ResourcesService.class);
        if (resources instanceof ResourcesService.Admin admin) {
            Thread.ofVirtual().start(() -> {
                var project = asset.parent(NavigableProject.class);
                var ret = admin.deleteDocument(project.getUrn(), asset.getUrn(),
                        scope().getIdentity().getId());
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
                var changes = admin.createDocument(projectName, newDocumentUrn, documentType,
                        scope().getIdentity().getId());
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
                    "because " +
                    "the engine " +
                    "implementation is overridden");
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

    public ContextScope context(String context) {
        // TODO named context
        return null;
    }

    public UserScope user(String username) {
        // TODO named user
        return null;
    }

    public SessionScope session(String session) {
        // TODO named session
        return null;
    }

    @Override
    public UIController getController() {
        return this;
    }
}
