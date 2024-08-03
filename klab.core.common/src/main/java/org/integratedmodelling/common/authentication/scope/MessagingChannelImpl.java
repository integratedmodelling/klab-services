package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;

/**
 * Replicates the functionality of the parent
 * {@link org.integratedmodelling.klab.api.services.runtime.Channel} but implements the connection to remote
 * services. If {@link #connect(KlabService)} is called with an actual embedded service as parameter, any
 * listeners will be shared with the service scope; otherwise, the REST API will be used to establish a
 * Websockets connection as long as the service allows it.
 */
public class MessagingChannelImpl extends ChannelImpl implements MessagingChannel  {

    public MessagingChannelImpl(Identity identity) {
        super(identity);
    }

    @Override
    public Message send(Object... message) {
        // TODO dispatch to queues
        return super.send(message);
    }

    //    //    MessageBus messageBus;
//    private WebSocketStompClient stompClient;
//    private StompSession session;
//    private AtomicBoolean paired = new AtomicBoolean(false);
//    private Utils.Http.Client client;
//    private String channel;
//    // This becomes the ID for the paired scopes in the service
//    private String scopeId;
//    private Scope.Type scopeType;
//
//    // default provenance for notifications: set to Forwarded so that client messages are not sent to server
//    private Message.ForwardingPolicy notificationProvenance = Message.ForwardingPolicy.DoNotForward;
//
//    public MessagingChannelImpl(Identity identity, Utils.Http.Client client, Scope.Type scopeType) {
//        super(identity);
//        this.client = client;
//        this.scopeType = scopeType;
//    }
//
//
//    @Override
//    public void info(Object... info) {
//        if (!listeners.isEmpty() || isPaired()) {
//            var notification = Notification.create(info);
//            send(Message.MessageClass.Notification, Message.MessageType.Info, notificationProvenance,
//                    notification);
//        } else {
//            Logging.INSTANCE.info(info);
//        }
//    }
//
//    @Override
//    public void warn(Object... o) {
//        if (!listeners.isEmpty() || isPaired()) {
//            var notification = Notification.create(o);
//            send(Message.MessageClass.Notification, Message.MessageType.Warning, notificationProvenance,
//                    notification);
//        } else {
//            Logging.INSTANCE.warn(o);
//        }
//    }
//
//    @Override
//    public void error(Object... o) {
//        errors.set(true);
//        if (!listeners.isEmpty() || isPaired()) {
//            var notification = Notification.create(o);
//            send(Message.MessageClass.Notification, Message.MessageType.Error, notificationProvenance,
//                    notification);
//        } else {
//            Logging.INSTANCE.error(o);
//        }
//    }
//
//    @Override
//    public void debug(Object... o) {
//        if (!listeners.isEmpty() || isPaired()) {
//            var notification = Notification.create(o);
//            send(Message.MessageClass.Notification, Message.MessageType.Debug, notificationProvenance,
//                    notification);
//        } else {
//            Logging.INSTANCE.debug(o);
//        }
//    }
//
//
//    public boolean isPaired() {
//        return paired.get();
//    }
//
//    // if we got an error during pairing attempts, this is saved for reference
//    private Throwable pairingError = null;
//
//    @Override
//    public boolean connect(KlabService service) {
//
//        // TODO if service is embedded implementation, just install same listeners in service scope
//
//        if (client == null) {
//            return false;
//        }
//
//        this.scopeId = Utils.Names.shortUUID();
//
////        String response = client.get(ServicesAPI.SCOPE.REGISTER, String.class, "scopeType", scopeType,
////                "scopeId", scopeId);
////
////        if (response != null) {
////            String[] split = response.split(",");
////            if (this.stompClient == null) {
////                this.stompClient = getStompClient(split[0], split[1]);
////            }
////
////            if (this.stompClient != null) {
////                info("Client paired to remote " + service.status().getServiceType() + " service " + service.getServiceName());
////                return true;
////            }
////        }
//
//        return false;
//    }
//
//    @Override
//    public boolean disconnect(KlabService service) {
//        if (isPaired() && session.isConnected()) {
//            paired.set(false);
////            var ret = client.get(ServicesAPI.SCOPE.DISPOSE, Boolean.class, "scopeId", scopeId);
////            session.disconnect();
////            return ret != null && ret;
//        }
//        return false;
//    }
//
//    @Override
//    public Message send(Object... message) {
//        var ret = super.send(message);
//        if (session != null && ret.getForwardingPolicy() == Message.ForwardingPolicy.Forward) {
//            session.send(channel, ret);
//        }
//        return ret;
//    }
//
//    @Override
//    public Message post(Consumer<Message> handler, Object... message) {
//        var ret = super.post(handler, message);
//        if (session != null && ret.getForwardingPolicy() == Message.ForwardingPolicy.Forward) {
//            session.send(channel, ret);
//        }
//        return ret;
//    }
//
//    private WebSocketStompClient getStompClient(String url, String channel) {
//
//        this.channel = channel;
//
//        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
//
//            @Override
//            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
//                MessagingChannelImpl.super.info("New session established : " + session.getSessionId());
//                session.subscribe(channel, this);
//                MessagingChannelImpl.super.info("Subscribed to " + channel);
//                session.send(channel, getHandshakingMessage());
//                MessagingChannelImpl.super.info("Message sent to websocket server");
//            }
//
//            @Override
//            public void handleException(StompSession session, StompCommand command, StompHeaders headers,
//                                        byte[] payload, Throwable exception) {
//                MessagingChannelImpl.super.error("Websockets transfer exception", exception);
//            }
//
//            @Override
//            public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
//                return Map.class;
//            }
//
//            @Override
//            public void handleFrame(StompHeaders headers, Object payload) {
//
//                // NAH this will be a Map diocane, must convert and add the forwarding nature
//                Message message = (Message) payload;
//                if (payload == null) {
//                    debug("Received null payload");
//                    return;
//                }
//                info("Received : " + message + " from : " + message.getIdentity());
//                if (message.is(Message.MessageClass.ServiceLifecycle, Message.MessageType.ConnectScope)) {
//                    // TODO handshaking message must be received once, after which we're connected
//                    System.out.println("WE'RE CONNECTED");
//                } else {
//                    // send through listeners, don't bounce back
//                    MessagingChannelImpl.super.send(message);
//                }
//            }
//        };
//
//        /*
//        TODO revise - currently this doesn't work very well, particularly 1) under classloading constraints
//         there are runtime load exceptions, and 2) the server messages don't get here for some reason
//         */
//
//        List<Transport> transports = new ArrayList<>();
//        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
//        transports.add(new RestTemplateXhrTransport());
//        var transport = new SockJsClient(transports);
//
////        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
////        container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
////        container.setDefaultMaxTextMessageBufferSize(1024 * 1024);
////        List<Transport> transports = new ArrayList<>(1);
////        transports.add(new WebSocketTransport(new StandardWebSocketClient(container)));
////        SockJsClient transport = new SockJsClient(transports);
//
//        /*
//         * and three more for the next one
//         */
//        transport.setMessageCodec(new Jackson2SockJsMessageCodec(Utils.Json.newObjectMapper()));
//        stompClient = new WebSocketStompClient(transport);
//        stompClient.setInboundMessageSizeLimit(1024 * 1024);
//        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
//        try {
//            stompClient.connectAsync(url, sessionHandler).whenComplete((session, exception) -> {
//                if (session != null) {
//                    MessagingChannelImpl.this.session = session;
//                    paired.set(true);
//                }
//                if (exception != null) {
//                    super.error(exception);
//                    pairingError = exception;
//                    paired.set(false);
//                }
//            });
//        } catch (Throwable e) {
//            throw new RuntimeException(e);
//        }
//
//
//        return stompClient;
//
//    }
//
//    private Message getHandshakingMessage() {
//        ScopeOptions scopeData = new ScopeOptions();
//
//        return Message.create(scopeId, Message.MessageClass.ServiceLifecycle, Message.MessageType.ConnectScope
//                , scopeData);
//    }
//
//    public Message.ForwardingPolicy getNotificationProvenance() {
//        return notificationProvenance;
//    }
//
//    public void setNotificationProvenance(Message.ForwardingPolicy notificationProvenance) {
//        this.notificationProvenance = notificationProvenance;
//    }

}
