package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.beans.ActionStatistics;
import org.integratedmodelling.klab.api.lang.kactors.beans.TestStatistics;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.impl.MessageImpl;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * Messages exchanged between the engine and its clients.
 *
 * @author ferdinando.villa
 */
public interface Message extends Serializable {

    public static Message NO_RESPONSE = null;

    enum Repeatability {
        Repeatable, Once
    }

    /**
     * Message class. Ugly type name makes life easier.
     * TODO add enumset of all acceptable messageTypes and validate messages
     *
     * @author ferdinando.villa
     */
    enum MessageClass {

        /**
         * Only for no-op defaults in the message handler annotation
         */
        Void,

        /**
         * Used within a UI for communicating things to react to and between F/B to gather user input.
         */
        UserInterface,

        /**
         * F->B when user selects context
         */
        UserContextChange,

        /**
         * B->F after UserContextChange was received, containing the remaining definition set by the engine
         */
        UserContextDefinition,

        /**
         * Any event referring to a service
         */
        ServiceLifecycle,
        /**
         *
         */
        EngineLifecycle,

        KimLifecycle,

        /**
         *
         */
        ResourceLifecycle,

        /**
         *
         */
        ProjectLifecycle,
        /**
         *
         */
        Authorization,
        /**
         *
         */
        TaskLifecycle,
        /**
         *
         */
        ObservationLifecycle,
        /**
         *
         */
        SessionLifecycle,
        /**
         *
         */
        UnitTests,
        /**
         *
         */
        Notification,
        /**
         * Search-class messages are sent by the front end to initiate or continue incremental knowledge
         * searches.
         */
        Search,
        /**
         * Query messages are sent by the back end upon receiving Search-class messages.
         */
        Query,

        /**
         * Run-class messages start scripts and tests.
         */
        Run,

        /**
         * Messages sent or received by the view actor, called from behaviors.
         */
        ViewActor,

        ActorCommunication
    }

    /**
     * Message type within its class.
     *
     * @author ferdinando.villa
     */
    enum MessageType {


        //        /**
        //         * Only used as a default for the MessageClass annotation.
        //         */
        //        Void,

        //        /*
        //         * For basic engine requests of class EngineLifecycle that don't require a response besides
        //         * collateral effects.
        //         */
        //        ExecuteCommand,
        //
        //        /*
        //         * Console requests: new console, command received, response received
        //         */
        //        ConsoleCreated,
        //        ConsoleClosed,
        //        CommandRequest,
        //        CommandResponse,

        /*
         * Service messages, coming with service capabilities
         */
        ServiceInitializing(String.class),
        ServiceAvailable(KlabService.ServiceCapabilities.class),
        ServiceUnavailable(KlabService.ServiceCapabilities.class),

        /**
         * UI selections
         */
        WorkspaceSelected(String.class),

        /**
         * Sent whenever a file modification (external or through the API) implies a change in a workspace.
         * Accompanied by a ResourceSet that details all the assets affected and their order of loading.
         */
        WorkspaceChanged(ResourceSet.class),

        DocumentSelected(KlabDocument.class),

        //        /*
        //         * UserContextChange-class types.
        //         */
        //        /**
        //         * F->B.
        //         */
        //        RegionOfInterest, FeatureAdded,
        //
        //        /**
        //         * F->B
        //         */
        //        PeriodOfInterest,
        //
        //        /**
        //         * B->F sent whenever a user message affecting the context is processed
        //         */
        //        ScaleDefined,
        //
        //        /**
        //         * F<->B
        //         */
        //        ResetContext,
        //
        //        /**
        //         * F->F (internal message between views)
        //         */
        //        ResetScenarios,
        //
        //        /**
        //         * F->B whenever the user wants to (re)contextualize to either a URN specifying one or
        //         more contexts,
        //         * a query (both may require users to choose one), or a specified observation ID. The
        //         last use case
        //         * can happen with a parent context set, when users want to select a sub-context of the
        //         current, or
        //         * without when the user wants to revert back to the original root context.
        //         * <p>
        //         * Class: ObservationLifecycle
        //         */
        //        Recontextualize,
        //
        //        /*
        //         * Messages with class UserInterface, some local to the UI and not marshalled across
        //         * websockets, others initiated on either side when user input is provided or requested.
        //         */
        //        HistoryChanged, FocusChanged, Notification,
        //
        //        /**
        //         * B->F: notification for projects in user workspace when they are opened.UIs may not be
        //         aware of them
        //         * and want to offer to import them. The backend does not modify or delete projects.
        //         * <p>
        //         * F->B: notification for projects in IDE workspace that are opened and the engine may
        //         not be aware
        //         * of.
        //         */
        //        UserProjectOpened, UserProjectModified, UserProjectDeleted,

        //        /**
        //         * Class UserInterface: User input requests and responses: request is B->F, response is
        //         F->B. Use
        //         * beans {@link UserInputRequested} and {@link UserInputProvided} respectively.
        //         */
        //        UserInputRequested, UserInputProvided,

        UserAuthorized(UserIdentity.class),
        UserDisconnected(UserIdentity.class),

        //        /**
        //         * Class UserInterface: B->F when a new documentation item becomes available for display
        //         at context
        //         * level or at the dataflow actuator level. Uses bean {@link RuntimeDocumentation}.
        //         */
        //        RuntimeDocumentation, DataflowDocumentation, TicketRequest, TicketResponse,
        //        AuthorityDocumentation,
        //
        //        /**
        //         * Class UserInterface: request addition of action to either context menu or global menu.
        //         Use bean
        //         * {@link GlobalActionRequest}.
        //         */
        //        AddGlobalAction,

        //        /**
        //         * Class UserInterface: handling of drop events in UI
        //         * <p>
        //         * {@link #DropInitiated}: F->B communicate content type, name and size (bean {@link
        //         DropRequest}
        //         * {@link #DropPermission}: B->F accept/reject drop (bean {@link DropPermission} {@link
        //         #DropData}:
        //         * F->B execute drop upload and communicate on finish (bean {@link DropData}
        //         */
        //        DropInitiated, DropPermission, DropData,

        //        /**
        //         * Class UserInterface: request change in setting communicating through bean
        //         * {@link SettingChangeRequest}. F->B
        //         */
        //        ChangeSetting,
        //        /*
        //         * B->F, modify fixed explorer view settings
        //         */
        //        ViewSetting,

        /*
         * F->B: ask engine to modify or delete projects or project assets
//         */
        //        CreateNamespace, CreateScenario, DeleteNamespace, DeleteLocalResource, CreateCodelist,
        //        GetCodelist,
        //        UpdateCodelist, DeleteCodelist, CreateProject, DeleteProject, CreateScript, DeleteScript,
        //        CreateTestCase, DeleteTestCase, CreateBehavior, DeleteBehavior,
        //
        //        /*
        //         * F->B: publish or update a local or public resource
        //         */
        //        PublishLocalResource, UpdatePublicResource,
        //
        //        /**
        //         * B->F: respond to a request to publish a resource (just submit asynchronously).
        //         */
        //        ResourceSubmitted,
        //
        //        /**
        //         * B -> F after a resource operation request, reporting the results
        //         */
        //        ResourceInformation,
        //
        //        /**
        //         * B->F to report the status of a resource as its ResourceReference data plus
        //         online/offline status if
        //         * known, or unknown + the URN if not.
        //         */
        //        ResourceOnline, ResourceOffline, ResourceUnknown,
        //
        //        /**
        //         * F->B: notification when files are explicitly changed, added or deleted; notify
        //         projects to load and
        //         * respond to project lifecycle requests
        //         */
        //        ProjectFileAdded, ProjectFileModified, ProjectFileDeleted, NotifyProjects,
        //        DocumentationModified,

        /**
         * F <-> B: scenario selection from user action (if class == UserInterface) and/or from engine (after
         * selection or from API) with class == SessionLifecycle. In all cases the list of scenarios is
         * assumed to contain and honor all interdependencies and constraints. Scenario selection with no
         * scenarios is a reset.
         */
        ScenariosSelected(String[].class),

        /*
         * --- Notification-class types ---
         */
        Debug(Notification.class),
        Info(Notification.class),
        Warning(Notification.class),
        Error(Notification.class),
        //        EngineEvent, RuntimeEvent,

        //        /*
        //         * --- KimLifecycle: one-off compile notifications at the namespace or project level
        //         */
        //        NamespaceCompilationIssues,
        //
        //        /*
        //         * --- Observation lifecycle ---
        //         */
        //        /**
        //         * Request the observation of a URN or logical expression. F->B. If the URN has resulted
        //         from a
        //         * search, send the ID of the search so it can be disposed of.
        //         */
        //        RequestObservation,
        //
        //        /**
        //         * Authority-related inquiries
        //         */
        //        AuthorityQuery, AuthoritySearchResults,
        //
        //        /**
        //         * F->B: Start or stop watching an observation, i.e. receive messages about anything that
        //         changes
        //         * related to it. Linked to a {@link WatchRequest} message payload.
        ////         */
        //        WatchObservation,
        //
        //        /**
        //         * A new observation is available. Back->Front.
        //         */
        //        NewObservation,
        //
        //        /**
        //         * A previously reported observation had its contents modified. Back->Front.
        //         */
        //        ModifiedObservation,
        //
        //        /**
        //         * F->B: user has selected an action among those supplied by the engine with each
        //         observation.
        //         */
        //        ExecuteObservationAction,
        //
        //        /**
        //         * F->B Authorization class - inquiries about permitted operations and network status
        //         */
        //        NetworkStatus,
        //
        //        /**
        //         * -- Ticketing system monitoring, send around internally by UserInterface after engine
        //         notification
        //         */
        //        TicketResolved, TicketStatusChanged, TicketCreated,
        //
        //        /**
        //         * --- Task lifecycle --- B -> F
        //         */
        //        ScriptStarted, TaskStarted, TaskFinished, TaskAborted, DataflowCompiled,
        //        DataflowStateChanged,
        //        ProvenanceChanged,
        //
        //        /**
        //         * Task lifecycle F -> B
        //         */
        //        TaskInterrupted, DataflowNodeDetail, DataflowNodeRating,
        //
        //        /**
        //         * Test lifecycle B -> F
        //         */
        //        TestRunStarted,
        //        TestRunFinished,
        TestCaseStarted(TestStatistics.class),
        TestCaseFinished(TestStatistics.class),
        TestStarted(ActionStatistics.class),
        TestFinished(ActionStatistics.class),
        //
        //        /**
        //         * Scheduler lifecycle F->B
        //         */
        //        SchedulingStarted, SchedulingFinished, ScheduleAdvanced, SchedulerReset,
        //
        //        /*
        //         * --- Search-class types --- FIXME SemanticSearch is a synonym of SubmitSearch, used in IDE
        //         * queries to trigger experimental behavior, to be merged with SubmitSearch and removed when
        //         * done. Same with SemanticMatch vs. MatchAction.
        //         */
        //        SemanticSearch, SubmitSearch, MatchAction, SemanticMatch,
        //
        //        /*
        //         * --- Query-class types ---
        //         */
        //        QueryResult, QueryStatus,
        //
        //        /*
        //         * --- EngineLifecycle ---
        //         */
        //        EngineStarting, EngineUp, EngineDown,
        //
        //        /*
        //         * --- Run-class types
        //         */
        //        RunScript, RunTest, RunApp, RunUnitTest, DebugScript, DebugTest,
        //
        //        /*
        //         * --- ResourceLifecycle-class types, F->B
        //         */
        //        ImportResource, DeleteResource, UpdateResource, ValidateResource, PreviewResource,
        //        CopyResource,
        //        MoveResource, CreateResource, ImportIntoResource, ResourceOperation,
        //
        //        /*
        //         * --- ResourceLifecycle-class types, B->F
        //         */
        //        ResourceImported, ResourceDeleted, ResourceUpdated, ResourceValidated, ResourceCreated,
        //        CodelistCreated, CodelistUpdated, CodelistDeleted,
        //
        /*
         * --- View actor messages
         */
        CreateViewComponent,
        SetupInterface,
        CreateWindow,
        CreateModalWindow,
        //
        //        /*
        //         * --- Sent F->B when a view action interacts with a component and B->F to send a response
        //         * to an explicit method call on a widget.
        //         */
        //        ViewAction,
        //
        //        /*
        //         * Sent B->F when a new view has been generated in a context
        //         */
        //        ViewAvailable,
        //
        //        /*
        //         * Sent B->F when one or more documentation views have incorporated a new element
        //         */
        //        DocumentationChanged, AgentResponse
        ;

        // TODO add this to the message type so that we can validate the message payload against it. Use
        //  Void.class
        // as the default
        Class<?> payloadClass;

        private MessageType() {
            this(Void.class);
        }

        private MessageType(Class<?> payloadClass) {
            this.payloadClass = payloadClass;
        }

    }

    Repeatability getRepeatability();

    /**
     * Unique ID for each message.
     *
     * @return
     */
    long getId();

    /**
     * Return this or a new message with the response ID set to that of the passed message, so that the call
     * chain can be reconstructed across network boundaries. This is used to enable the
     * {@link Channel#post(Consumer, Object...)} call.
     *
     * @param message
     * @return
     */
    Message respondingTo(Message message);

    /**
     * The message exposes the identity that created it through a token, which may or may not be parseable at
     * the receiving end but will be consistently linked to the message type. For example, task messages will
     * have the identity of the task that generated them so they can be correctly distributed among tasks.
     *
     * @return the sender's identity. Never null.
     */
    String getIdentity();

    /**
     * @return the message class
     */
    MessageClass getMessageClass();

    /**
     * @return the message type
     */
    MessageType getMessageType();

    /**
     * Timestamp (milliseconds since epoch) of message at sender side. Meant to enforce sequentiality rather
     * than reliable date/time attribution.
     *
     * @return
     */
    long getTimestamp();

    /**
     * Get the payload of the message, ensuring it is of type T.
     *
     * @param cls
     * @return the payload
     */
    <T> T getPayload(Class<? extends T> cls);

    public static Message create(Channel scope, Object... o) {
        return create(scope.getIdentity().getId(), o);
    }

    /**
     * Build a message by arranging all the arguments appropriately. Only one payload object can be passed.
     * <p>
     * TODO if we are passing a Message and it's not the only parameter, we should add its ID as the one
     *  we're responding to. If it's the only parameter, copy the message.
     * TODO validate the message type according to the class and the object payload according to the types
     *
     * @param identity
     * @param o
     * @return a new message
     * @throws IllegalArgumentException if there are not enough arguments or more than one payload was passed
     */
    public static Message create(String identity, Object... o) {

        if (o == null) {
            return null;
        }

        if (o.length == 1 && o[0] instanceof Message message) {
            return message;
        }

        MessageImpl ret = new MessageImpl();
        ret.setIdentity(identity);
        Notification.Type notype = null;
        for (Object ob : o) {
            if (ob instanceof MessageType) {
                ret.setMessageType((MessageType) ob);
            } else if (ob instanceof MessageClass) {
                ret.setMessageClass((MessageClass) ob);
            } else if (ob instanceof Notification.Type) {
                notype = (Notification.Type) ob;
            } else if (ob instanceof Repeatability) {
                ret.setRepeatability((Repeatability) ob);
            } else if (ob instanceof Notification) {
                notype = ((Notification) ob).getType();
                ret.setPayload(ob);
            } else if (ob != null) {
                if (ret.getPayload() == null) {
                    ret.setPayload(ob);
                    ret.setPayloadClass("Unknown");
                } else {
                    throw new IllegalArgumentException("payload already set: too many arguments");
                }
            }
        }

        // defaults so that we can just post a string
        if (ret.getMessageClass() == null) {
            ret.setMessageClass(MessageClass.Notification);
            if (ret.getMessageType() == null) {
                ret.setMessageType(MessageType.Info);
            }
        }
        ret.setNotificationType(notype);

        return ret;
    }

    /**
     * Build a message from a standard {@link Notification} and an identity.
     *
     * @param notification
     * @param identity
     * @return a new message
     */
    public static MessageImpl create(Notification notification, String identity) {

        MessageImpl ret = new MessageImpl();
        ret.setIdentity(identity);
        ret.setMessageClass(MessageClass.Notification);
        ret.setPayload(notification);
        ret.setPayloadClass("String");

        if (notification.getLevel().equals(Notification.Level.Debug)) {
            ret.setMessageType(MessageType.Debug);
        } else if (notification.getLevel().equals(Notification.Level.Info)) {
            ret.setMessageType(MessageType.Info);
        } else if (notification.getLevel().equals(Notification.Level.Warning)) {
            ret.setMessageType(MessageType.Warning);
        } else if (notification.getLevel().equals(Notification.Level.Error)) {
            ret.setMessageType(MessageType.Error);
        }

        return ret;
    }

}
