package org.integratedmodelling.klab.api.modeler;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anything that reacts to UI events implements this interface. The enums and the inherent constraints
 * validate and establish the communication within the modeler. All this is meant to build the "controller"
 * part of the modeler as much as possible within the core k.LAB implementation.
 * <p>
 * Interfaces derived from this define the wiring of events and actions by means of methods annotated with
 * the
 *
 * @{@link org.integratedmodelling.klab.api.modeler.annotations.UIEventHandler} and
 * @{@link org.integratedmodelling.klab.api.modeler.annotations.UIActionHandler} annotations. The modeler
 * collects them and automatically wires events from the engine to UI actions that are dispatched to all views
 * and panels registered with them.
 */
public interface UIReactor {

    /**
     * We categorize the different UI views and panels so that we can build a wiring pattern of events through
     * annotations on the correspondent interfaces.
     */
    enum Type {
        CLIConsole,
        ContextView,
        DebugView,
        DocumentationEditor,
        DocumentEditor,
        EngineView,
        KnowledgeNavigator,
        ResourceNavigator,
        ProjectPropertyEditor,
        ResourceEditor,
        LocalizationEditor,
    }

    /**
     * Defines the direction in which a UI event is sent. Each event contains the possible direction of
     * messaging for validation. Inactive events cannot be sent.
     */
    enum EventDirection {
        ViewToView,
        ViewToEngine,
        EngineToView,
        Bidirectional,
        Inactive
    }

    /**
     * The UI only reacts to these events, which contain their direction and the allowed payload class(es) for
     * validation. If the payload classes are multiple, the payload in the corresponding message/method must
     * be an array of objects of those classes, in order.
     * <p>
     * Some of these events go from the UI to the engine, others the other way around, some even both ways.
     * This should be indicated somehow, e.g. with a first parameter enum.
     */
    enum UIEvent {

        EngineStarting(EventDirection.EngineToView, UserIdentity.class),
        /**
         * From this point on, the UI should be synced to the services in the scope and made its functions
         * available based on the current service capabilities.
         */
        EngineAvailable(EventDirection.EngineToView, UserScope.class),
        EngineUnavailable(EventDirection.EngineToView, UserIdentity.class),
        /**
         * Sent only for the DEFAULT services in case they are local to the modeler's instance.
         */
        ServiceStarting(EventDirection.EngineToView, KlabService.ServiceCapabilities.class),
        /**
         * Sent only for the DEFAULT services in case they are local to the modeler's instance or for other
         * services in case they come online after going offline. Besides a default/local service, the
         * services listed in the UI should be those listed in the user scope sent with EngineAvailable.
         */
        ServiceAvailable(EventDirection.EngineToView, KlabService.ServiceCapabilities.class),
        /**
         * Sent for any of the services in the user scope when they go offline.
         */
        ServiceUnavailable(EventDirection.EngineToView, KlabService.ServiceCapabilities.class),

        /**
         * Sent from the UI to the engine to define which service to use as the default for the class, and to
         * the UI to the UI to tune the views on the new service in focus.
         */
        ServiceSelected(EventDirection.Bidirectional, KlabService.class),

        /**
         * Document add requested contains type of document, URN and a map of parameters
         */
        DocumentAddRequest(EventDirection.ViewToEngine, KlabAsset.KnowledgeClass.class, String.class,
                Map.class),

        DocumentDeleteRequest(EventDirection.ViewToEngine, KlabDocument.class),

        DocumentSelected(EventDirection.ViewToEngine, KlabDocument.class),

        DocumentPositionChanged(EventDirection.ViewToEngine, KlabDocument.class, Integer.class),

        /**
         * First parameter is the Project (or null for "current service", the second the URN, the rest is the
         * initialization payload
         */
        ResourceAddRequest(EventDirection.ViewToEngine, String.class, String.class,
                Map.class),

        /**
         * Show a document after selection. The second parameter is true if the document should be read only,
         * otherwise an editor will be used.
         */
        DocumentShowRequest(EventDirection.ViewToView, NavigableDocument.class, Boolean.class),
        DocumentHideRequest(EventDirection.ViewToView, NavigableDocument.class),
        AllDocumentHideRequest(EventDirection.ViewToView),

        ResourceDeleteRequest(EventDirection.ViewToEngine, String.class),

        ResourceSelected(EventDirection.ViewToEngine, KlabDocument.class),

        ResourceChanged(EventDirection.ViewToEngine, Resource.class),

        WorkspaceChanged(EventDirection.EngineToView, ResourceSet.class),

        ViewShown(EventDirection.ViewToView, Type.class),

        ViewHidden(EventDirection.ViewToView, Type.class),

        /**
         * Only used as a default in empty
         * {@link org.integratedmodelling.klab.api.modeler.annotations.UIEventHandler} annotations
         */
        AnyEvent(EventDirection.Inactive);

        List<Class<?>> payloadClasses = new ArrayList<>();
        EventDirection direction;

        UIEvent(EventDirection direction, Class<?>... payloadClass) {
            this.direction = direction;
            if (payloadClass != null) {
                for (var cls : payloadClass) {
                    this.payloadClasses.add(cls);
                }
            }
        }
    }

    Modeler getModeler();

    /**
     * React to the passed UI event. The payload class is validated to be of the expected type before this is
     * called (an array if multiple). This is a catch-all method that is called only if there are no
     * specifically annotated methods in the reactor.
     *
     * @param event
     * @param payload
     */
    void onEvent(UIEvent event, Object payload);
}