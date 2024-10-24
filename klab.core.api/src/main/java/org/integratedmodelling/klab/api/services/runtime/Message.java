package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kactors.beans.ActionStatistics;
import org.integratedmodelling.klab.api.lang.kactors.beans.TestStatistics;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.impl.MessageImpl;
import org.integratedmodelling.klab.api.services.runtime.impl.ScopeOptions;

import java.io.Serializable;
import java.net.URI;
import java.util.function.Consumer;

/**
 * Messages exchanged between scopes using {@link Channel#send(Object...)}. They will be sent through the
 * messaging queues as configured.
 * <p>
 * Incoming messages will cause the {@link Channel}'s handlers to be called depending on which queue they come
 * from. Only a channel that has been specifically instrumented for messaging will receive messages, and those
 * that were not (such as the {@link org.integratedmodelling.klab.api.scope.ServiceScope}) will send only to
 * themselves.
 * <p>
 * TODO revise and simplify the API with a focus on messaging and DT communication, including federation.
 * TODO categorize the queues through an enum - this could be the pace
 *
 * @author ferdinando.villa
 */
public interface Message extends Serializable {

    public static Message NO_RESPONSE = null;

    /**
     * Different session/digital twin queues that a channel can subscribe to. The client should ask for the
     * desired queues when creating a session or context scope, and verify what it got from the runtime after
     * it responds. The queues will map to AMQP queues with the ID of the scope + the enum value separated by
     * a dot.
     * <p>
     * The queue can be explicitly added to the arguments in {@link Message#create(Channel, Object...)} but
     * each {@link MessageType} brings with it a default queue.
     */
    enum Queue {
        Events,
        Errors,
        Warnings,
        Info,
        Debug,
        Clock,
        Status,
        UI,
        None
    }

//    @Deprecated
//    enum ForwardingPolicy {
//        /**
//         * Message was created locally and will be forwarded to paired scopes
//         */
//        Forward,
//        /**
//         * Message was forwarded from a paired scope and will not be further forwarded. This should be the
//         * default policy for newly created messages.
//         */
//        DoNotForward
//    }

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

        KnowledgeLifecycle,
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

        /**
         * These are skipped from queues and sent directly to the scope's agent.
         */
        ActorCommunication;

        final public MessageType[] messageTypes;

        private MessageClass(MessageType... messageTypes) {
            this.messageTypes = messageTypes == null ? new MessageType[]{} : messageTypes;
        }

    }

    /**
     * Message type within its class.
     *
     * @author ferdinando.villa
     */
    enum MessageType {
        /*
         * Service messages, coming with service capabilities
         */
        ServiceInitializing(Queue.Events, KlabService.ServiceCapabilities.class),
//        ReasoningAvailable(Queue.Events, Reasoner.Capabilities.class),
        ServiceAvailable(Queue.Events, KlabService.ServiceCapabilities.class),
        ServiceUnavailable(Queue.Events, KlabService.ServiceCapabilities.class),
        ServiceStatus(Queue.Events, KlabService.ServiceStatus.class),
        ConnectScope(Queue.Events, ScopeOptions.class),

        /**
         * UI selections
         */
        WorkspaceSelected(Queue.UI, String.class),

        /**
         * Sent whenever a file modification (external or through the API) implies a change in a workspace.
         * Accompanied by a ResourceSet that details all the assets affected and their order of loading.
         */
        WorkspaceChanged(Queue.UI, ResourceSet.class),

        DocumentSelected(Queue.UI, KlabDocument.class),
        UserAuthorized(Queue.Events, UserIdentity.class),
        UserDisconnected(Queue.UI, UserIdentity.class),
        /**
         * F <-> B: scenario selection from user action (if class == UserInterface) and/or from engine (after
         * selection or from API) with class == SessionLifecycle. In all cases the list of scenarios is
         * assumed to contain and honor all interdependencies and constraints. Scenario selection with no
         * scenarios is a reset.
         */
        ScenariosSelected(Queue.Events, String[].class),

        /*
         * --- Notification-class types ---
         */
        Debug(Queue.Debug, Notification.class),
        Info(Queue.Info, Notification.class),
        Warning(Queue.Warnings, Notification.class),
        Error(Queue.Errors, Notification.class),

        /*
         * --- reasoning-related messages
         */
        LogicalValidation(Queue.Events,ResourceSet.class),

        /**
         * Runtime event messages
         */
        TestCaseStarted(Queue.Events, TestStatistics.class),
        TestCaseFinished(Queue.Events, TestStatistics.class),
        TestStarted(Queue.Events, ActionStatistics.class),
        TestFinished(Queue.Events, ActionStatistics.class),
        RunApplication,
        RunBehavior,
        CreateContext,
        CreateSession,
        Fire,

        /**
         * Resolver event messages
         */
        ResolutionSuccessful(Queue.Events, Long.class),
        ResolutionAborted(Queue.Events, Long.class),


        /**
         * Engine status has changed
         */
        EngineStatusChanged(Queue.Events, Engine.Status.class),

        /*
         * --- View actor messages
         */
        CreateViewComponent,
        SetupInterface,
        CreateWindow,
        CreateModalWindow,
        /**
         * Engine lifecycle, should only be client-wide
         */
        UsingDistribution(Queue.UI, Distribution.class);

        public final Class<?> payloadClass;
        public final Queue queue;

        private MessageType() {
            this.payloadClass = Void.class;
            this.queue = Queue.None;
        }

        private MessageType(Queue queue, Class<?> payloadClass) {
            this.queue = queue;
            this.payloadClass = payloadClass;
        }

    }

//    ForwardingPolicy getForwardingPolicy();

    /**
     * Unique ID for each message.
     *
     * @return
     */
    long getId();

    /**
     * Return this or a new message with the response ID set to that of the passed message, so that the call
     * chain can be reconstructed across network boundaries. At the moment unused (post() in channels has been
     * removed, may come back) because there are better ways to exchange messages and they shouldn't be
     * instructions.
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

    default boolean is(MessageClass messageClass) {
        return this.getMessageClass() == messageClass;
    }

    default boolean is(MessageType messageClass) {
        return this.getMessageType() == messageClass;
    }

    default boolean is(MessageClass messageClass, MessageType messageType) {
        return this.getMessageClass() == messageClass && getMessageType() == messageType;
    }

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

        boolean queueOverridden = false;
        MessageImpl ret = new MessageImpl();
        ret.setIdentity(identity);
//        Notification.Type notype = null;
        for (Object ob : o) {
            if (ob instanceof MessageType) {
                ret.setMessageType((MessageType) ob);
            } else if (ob instanceof MessageClass) {
                ret.setMessageClass((MessageClass) ob);
            } /*else if (ob instanceof Notification.Type) {
                notype = (Notification.Type) ob;
            } */else if (ob instanceof Queue q) {
                queueOverridden = true;
                ret.setQueue(q);
            } /*else if (ob instanceof ForwardingPolicy) {
                ret.setForwardingPolicy((ForwardingPolicy) ob);
            } */else if (ob instanceof Notification notification) {
                ret.setMessageClass(MessageClass.Notification);
                ret.setMessageType(switch (notification.getLevel()) {
                    case Debug -> MessageType.Debug;
                    case Info -> MessageType.Info;
                    case Warning -> MessageType.Warning;
                    case Error, SystemError -> MessageType.Error;
                });
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

        if (ret.getMessageType() != null && !queueOverridden) {
            ret.setQueue(ret.getMessageType().queue);
        }

        return ret;
    }

    /**
     * Return the message queue this was intended for or came from. If the message is part of no queue, this
     * returns {@link Queue#None}.
     *
     * @return
     */
    Queue getQueue();

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

        if (ret.getMessageType() != null) {
            ret.setQueue(ret.getMessageType().queue);
        }

        return ret;
    }

}
