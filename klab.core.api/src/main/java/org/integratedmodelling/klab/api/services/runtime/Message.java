package org.integratedmodelling.klab.api.services.runtime;

import java.util.logging.Level;

import org.integratedmodelling.klab.api.services.runtime.impl.MessageImpl;
import org.integratedmodelling.klab.api.utils.Utils;

/**
 * Messages exchanged between the engine and its clients.
 * 
 * @author ferdinando.villa
 *
 */
public interface Message {

    public static Message NO_RESPONSE = null;

    enum Repeatability {
        Repeatable, Once
    }

    /**
     * Message class. Ugly type name makes life easier.
     * 
     * @author ferdinando.villa
     *
     */
    enum MessageClass {

        /**
         * Only for no-op defaults in the message handler annotation
         */
        Void,

        /**
         * Used within a UI for communicating things to react to and between F/B to gather user
         * input.
         */
        UserInterface,

        /**
         * F->B when user selects context
         */
        UserContextChange,

        /**
         * B->F after UserContextChange was received, containing the remaining definition set by the
         * engine
         */
        UserContextDefinition,

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
         * Search-class messages are sent by the front end to initiate or continue incremental
         * knowledge searches.
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
        ViewActor
    }

    /**
     * Message type within its class.
     * 
     * @author ferdinando.villa
     *
     */
    enum Type {

        /**
         * Only used as a default for the MessageClass annotation.
         */
        Void,

        /*
         * For basic engine requests of class EngineLifecycle that don't require a response besides
         * collateral effects.
         */
        ExecuteCommand,

        /*
         * Console requests: new console, command received, response received
         */
        ConsoleCreated, ConsoleClosed, CommandRequest, CommandResponse,

        /*
         * UserContextChange-class types.
         */
        /**
         * F->B.
         */
        RegionOfInterest, FeatureAdded,

        /**
         * F->B
         */
        PeriodOfInterest,

        /**
         * B->F sent whenever a user message affecting the context is processed
         */
        ScaleDefined,

        /**
         * F<->B
         */
        ResetContext,
        
        /**
         * F->F (internal message between views)
         */
        ResetScenarios, 

        /**
         * F->B whenever the user wants to (re)contextualize to either a URN specifying one or more
         * contexts, a query (both may require users to choose one), or a specified observation ID.
         * The last use case can happen with a parent context set, when users want to select a
         * sub-context of the current, or without when the user wants to revert back to the original
         * root context.
         * 
         * Class: ObservationLifecycle
         */
        Recontextualize,

        /*
         * Messages with class UserInterface, some local to the UI and not marshalled across
         * websockets, others initiated on either side when user input is provided or requested.
         */
        HistoryChanged, FocusChanged, Notification,

        /**
         * B->F: notification for projects in user workspace when they are opened.UIs may not be
         * aware of them and want to offer to import them. The backend does not modify or delete
         * projects.
         * <p>
         * F->B: notification for projects in IDE workspace that are opened and the engine may not
         * be aware of.
         */
        UserProjectOpened, UserProjectModified, UserProjectDeleted,

        /**
         * Class UserInterface: User input requests and responses: request is B->F, response is
         * F->B. Use beans {@link UserInputRequest} and {@link UserInputResponse} respectively.
         */
        UserInputRequested, UserInputProvided,

        /**
         * Class UserInterface: B->F when a new documentation item becomes available for display at
         * context level or at the dataflow actuator level. Uses bean {@link RuntimeDocumentation}.
         */
        RuntimeDocumentation, DataflowDocumentation, TicketRequest, TicketResponse, AuthorityDocumentation,

        /**
         * Class UserInterface: request addition of action to either context menu or global menu.
         * Use bean {@link GlobalActionRequest}.
         */
        AddGlobalAction,

        /**
         * Class UserInterface: handling of drop events in UI
         * 
         * {@link #DropInitiated}: F->B communicate content type, name and size (bean
         * {@link DropRequest} {@link #DropPermission}: B->F accept/reject drop (bean
         * {@link DropPermission} {@link #DropData}: F->B execute drop upload and communicate on
         * finish (bean {@link DropData}
         */
        DropInitiated, DropPermission, DropData,

        /**
         * Class UserInterface: request change in setting communicating through bean
         * {@link SettingChangeRequest}. F->B
         */
        ChangeSetting,
        /*
         * B->F, modify fixed explorer view settings
         */
        ViewSetting,

        /*
         * F->B: ask engine to modify or delete projects or project assets
         */
        CreateNamespace, CreateScenario, DeleteNamespace, DeleteLocalResource, CreateCodelist, GetCodelist, UpdateCodelist, DeleteCodelist, CreateProject, DeleteProject, CreateScript, DeleteScript, CreateTestCase, DeleteTestCase, CreateBehavior, DeleteBehavior,

        /*
         * F->B: publish or update a local or public resource
         */
        PublishLocalResource, UpdatePublicResource,

        /**
         * B->F: respond to a request to publish a resource (just submit asynchronously).
         */
        ResourceSubmitted,

        /**
         * B -> F after a resource operation request, reporting the results
         */
        ResourceInformation,

        /**
         * B->F to report the status of a resource as its ResourceReference data plus online/offline
         * status if known, or unknown + the URN if not.
         */
        ResourceOnline, ResourceOffline, ResourceUnknown,

        /**
         * F->B: notification when files are explicitly changed, added or deleted; notify projects
         * to load and respond to project lifecycle requests
         */
        ProjectFileAdded, ProjectFileModified, ProjectFileDeleted, NotifyProjects, DocumentationModified,

        /**
         * F <-> B: scenario selection from user action (if class == UserInterface) and/or from
         * engine (after selection or from API) with class == SessionLifecycle. In all cases the
         * list of scenarios is assumed to contain and honor all interdependencies and constraints.
         * Scenario selection with no scenarios is a reset.
         */
        ScenariosSelected,

        /*
         * --- Notification-class types ---
         */
        Debug, Info, Warning, Error, EngineEvent, RuntimeEvent,

        /*
         * --- KimLifecycle: one-off compile notifications at the namespace or project level
         */
        NamespaceCompilationIssues,

        /*
         * --- Observation lifecycle ---
         */
        /**
         * Request the observation of a URN or logical expression. F->B. If the URN has resulted
         * from a search, send the ID of the search so it can be disposed of.
         */
        RequestObservation,

        /**
         * Authority-related inquiries
         */
        AuthorityQuery, AuthoritySearchResults,

        /**
         * F->B: Start or stop watching an observation, i.e. receive messages about anything that
         * changes related to it. Linked to a {@link WatchRequest} message payload.
         */
        WatchObservation,

        /**
         * A new observation is available. Back->Front.
         */
        NewObservation,

        /**
         * A previously reported observation had its contents modified. Back->Front.
         */
        ModifiedObservation,

        /**
         * F->B: user has selected an action among those supplied by the engine with each
         * observation.
         */
        ExecuteObservationAction,

        /**
         * F->B Authorization class - inquiries about permitted operations and network status
         */
        NetworkStatus,

        /**
         * -- Ticketing system monitoring, send around internally by UserInterface after engine
         * notification
         */
        TicketResolved, TicketStatusChanged, TicketCreated,

        /**
         * --- Task lifecycle --- B -> F
         */
        ScriptStarted, TaskStarted, TaskFinished, TaskAborted, DataflowCompiled, DataflowStateChanged, ProvenanceChanged,

        /**
         * Task lifecycle F -> B
         */
        TaskInterrupted, DataflowNodeDetail, DataflowNodeRating,

        /**
         * Test lifecycle B -> F
         */
        TestRunStarted, TestRunFinished, TestCaseStarted, TestCaseFinished, TestStarted, TestFinished,
        
        /**
         * Scheduler lifecycle F->B
         */
        SchedulingStarted, SchedulingFinished, ScheduleAdvanced, SchedulerReset,

        /*
         * --- Search-class types --- FIXME SemanticSearch is a synonym of SubmitSearch, used in IDE
         * queries to trigger experimental behavior, to be merged with SubmitSearch and removed when
         * done. Same with SemanticMatch vs. MatchAction.
         */
        SemanticSearch, SubmitSearch, MatchAction, SemanticMatch,

        /*
         * --- Query-class types ---
         */
        QueryResult, QueryStatus,

        /*
         * --- EngineLifecycle ---
         */
        EngineUp, EngineDown,

        /*
         * --- Run-class types
         */
        RunScript, RunTest, RunApp, RunUnitTest, DebugScript, DebugTest,

        /*
         * --- ResourceLifecycle-class types, F->B
         */
        ImportResource, DeleteResource, UpdateResource, ValidateResource, PreviewResource, CopyResource, MoveResource, CreateResource, ImportIntoResource, ResourceOperation,

        /*
         * --- ResourceLifecycle-class types, B->F
         */
        ResourceImported, ResourceDeleted, ResourceUpdated, ResourceValidated, ResourceCreated, CodelistCreated, CodelistUpdated, CodelistDeleted,

        /*
         * --- View actor messages
         */
        CreateViewComponent, SetupInterface, CreateWindow, CreateModalWindow,

        /*
         * --- Sent F->B when a view action interacts with a component and B->F to send a response
         * to an explicit method call on a widget.
         */
        ViewAction,

        /*
         * Sent B->F when a new view has been generated in a context
         */
        ViewAvailable,

        /*
         * Sent B->F when one or more documentation views have incorporated a new element
         */
        DocumentationChanged

    }

    Repeatability getRepeatability();

    /**
     * Unique ID for each message.
     * 
     * @return
     */
    long getId();

    /**
     * The message exposes the identity that created it through a token, which may or may not be
     * parseable at the receiving end but will be consistently linked to the message type. For
     * example, task messages will have the identity of the task that generated them so they can be
     * correctly distributed among tasks.
     * 
     * @return the sender's identity. Never null.
     */
    String getIdentity();

    /**
     * 
     * @return the message class
     */
    MessageClass getMessageClass();

    /**
     * 
     * @return the message type
     */
    Type getType();

    /**
     * Timestamp (milliseconds since epoch) of message at sender side. Meant to enforce
     * sequentiality rather than reliable date/time attribution.
     * 
     * @return
     */
    long getTimestamp();

    /**
     * Get the payload of the message, whatever it is.
     * 
     * @return the payload or null.
     */
    Object getPayload();

    /**
     * Get the payload of the message, ensuring it is of type T.
     * 
     * @param cls
     * @return the payload
     */
    <T> T getPayload(Class<? extends T> cls);
    
    /**
     * Build a message by arranging all the arguments appropriately. Only one
     * payload object can be passed.
     * 
     * @param identity
     * @param o
     * @return a new message
     * @throws IllegalArgumentException if there are not enough arguments or more
     *                                  than one payload was passed
     */
    public static MessageImpl create(String identity, Object... o) {
        MessageImpl ret = new MessageImpl();
        ret.setIdentity(identity);
        Notification.Type notype = null;
        for (Object ob : o) {
            if (ob instanceof Type) {
                ret.setType((Type) ob);
            } else if (ob instanceof MessageClass) {
                ret.setMessageClass((MessageClass) ob);
            } else if (ob instanceof Notification.Type) {
                notype = (Notification.Type) ob;
            } else if (ob instanceof Repeatability) {
                ret.setRepeatability((Repeatability)ob);
            }  else if (ob instanceof Notification) {
               notype = ((Notification)ob).getType();
               ret.setPayload(((Notification)ob).getMessage());
            } else if (ob != null) {
                if (ret.getPayload() == null) {
                    ret.setPayload(ob);
                    ret.setPayloadClass(Utils.Paths.getLast(ob.getClass().getCanonicalName(), '.'));
                } else {
                    throw new IllegalArgumentException("payload already set: too many arguments");
                }
            }
        }

        // defaults so that we can just post a string
        if (ret.getMessageClass() == null) {
            ret.setMessageClass(MessageClass.Notification);
            if (ret.getType() == null) {
                ret.setType(Type.Info);
            }
        }
        ret.setNotificationType(notype);

        return ret;
    }

    /**
     * Build a message from a standard {@link INotification} and an identity.
     * 
     * @param notification
     * @param identity
     * @return a new message
     */
    public static MessageImpl create(Notification notification, String identity) {

        MessageImpl ret = new MessageImpl();
        ret.setIdentity(identity);
        ret.setMessageClass(MessageClass.Notification);
        ret.setPayload(notification.getMessage());
        ret.setPayloadClass("String");

        if (notification.getLevel().equals(Level.FINE.getName())) {
            ret.setType(Type.Debug);
        } else if (notification.getLevel().equals(Level.INFO.getName())) {
            ret.setType(Type.Info);
        } else if (notification.getLevel().equals(Level.WARNING.getName())) {
            ret.setType(Type.Warning);
        } else if (notification.getLevel().equals(Level.SEVERE.getName())) {
            ret.setType(Type.Error);
        }

        return ret;
    }

}
