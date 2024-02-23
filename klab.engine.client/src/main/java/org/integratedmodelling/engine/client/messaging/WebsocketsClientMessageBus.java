package org.integratedmodelling.engine.client.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessageBus;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * The Websockets message bus is used to implement within-firewall scope communication when passing a scope to
 * a service located on the local network.
 */
public class WebsocketsClientMessageBus extends StompSessionHandlerAdapter implements MessageBus {

    private ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private StompSession session;
    private Map<Long, Consumer<Message>> responders = Collections.synchronizedMap(new HashMap<>());
    private Map<String, Set<Scope>> receivers = Collections.synchronizedMap(new HashMap<>());
    private Map<Scope, StompSession.Subscription> subscriptions =
            Collections.synchronizedMap(new HashMap<>());
    //    private Reactor reactor = new Reactor(this);
    private Scope scope;

    public WebsocketsClientMessageBus(String url) {

        JacksonConfiguration.configureObjectMapperForKlabTypes(objectMapper);

        WebSocketClient webSocketClient = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setInboundMessageSizeLimit(1024 * 1024);
        var converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(this.objectMapper);
        stompClient.setMessageConverter(converter);
        try {
            this.session = stompClient.connect(url, this).get();
        } catch (Throwable e) {
            throw new KlabInternalErrorException(e);
        }
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.afterPropertiesSet();
        stompClient.setTaskScheduler(taskScheduler); // for heartbeats
    }


    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                byte[] payload,
                                Throwable exception) {
        throw new RuntimeException("STOMP exception: " + exception.getMessage());
    }

    @Override
    public void handleTransportError(StompSession session, Throwable throwable) {
        if (throwable instanceof ConnectionLostException) {
            // if connection lost, call this
            // error("Connection lost.");
            scope.warn("Connection lost: " + throwable);
        } else {
            // error("Unknown message transport error. Please report the error.");
            scope.warn("Unknown message transport error: " + throwable.getMessage());
        }
        super.handleTransportError(session, throwable);
    }

    /**
     * Override for error handling.
     */
    protected void error(String string) {
        scope.error("ERROR: " + string);
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return Message.class;
    }

    @Override
    public synchronized void handleFrame(StompHeaders headers, Object payload) {
        // won't happen
        scope.warn("stomp message bus: what won't happen happened");
    }

    @Override
    public synchronized void post(Message message) {
        session.send("/klab/message", message);
    }

    @Override
    public synchronized void post(Message message, Consumer<Message> responder) {
        responders.put(message.getId(), responder);
        post(message);
    }

    @Override
    public Collection<Scope> getReceivers(String identity) {
        Set<Scope> ret = receivers.get(identity);
        if (ret == null) {
            return Collections.emptySet();
        }
        return ret;
    }

    @Override
    public void subscribe(Scope identity) {

        Set<Scope> ret = receivers.get(identity);

        if (ret == null) {
            ret = new HashSet<>();
            StompSession.Subscription subscription = session.subscribe("/message/" + identity,
                    new StompFrameHandler() {

                        @Override
                        public synchronized void handleFrame(StompHeaders arg0, Object payload) {

                            try {

                                final Message message = (Message) payload;
                                System.out.println("GOT MESSAGE " + message);

                                //						System.err.println("received payload of type " +
                                //						message
                                //						.getPayloadClass() + ", size="
                                //								+ (payload == null ? 0 : payload.toString()
                                //								.length()
                                //								) + " with mclass = " + message
                                //								.getMessageClass());

                                //                                /*
                                //                                 * No automatic translation at the
                                //                                 receiving end
                                //                                 */
                                //                                if (message.getPayload() instanceof Map
                                //                                && message.getPayloadClass() != null) {
                                //                                    message.setPayload(Utils.Json.convert
                                //                                    (message.getPayload(),
                                //                                            Class.forName(Configuration
                                //                                            .REST_RESOURCES_PACKAGE_ID + "."
                                //                                                    + message
                                //                                                    .getPayloadClass())));
                                //                                }
                                //
                                //                                new Thread() {
                                //
                                //                                    @Override
                                //                                    public void run() {
                                //
                                //                                        if (message.getInResponseTo() !=
                                //                                        null) {
                                //                                            Consumer<Message> responder =
                                //                                                    responders.remove
                                //                                                    (message
                                //                                                    .getInResponseTo());
                                //                                            if (responder != null) {
                                //                                                responder.accept(message);
                                //                                                return;
                                //                                            }
                                //                                        }
                                //
                                //                                        for (Object identity :
                                //                                        getReceivers(message.getIdentity
                                //                                        ())) {
                                //                                            reactor.dispatchMessage
                                //                                            (message, identity);
                                //                                        }
                                //                                    }
                                //                                }.start();
                                //
                            } catch (Throwable e) {
                                error("Internal: websockets communication error: " + e);
                                // throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public Type getPayloadType(StompHeaders arg0) {
                            return Message.class;
                        }
                    });

            subscriptions.put(scope, subscription);
            receivers.put(scope.getId(), ret);
        }
        ret.add(scope);
    }

    @Override
    public synchronized void unsubscribe(Scope identity) {

        if (subscriptions.containsKey(identity)) {
            subscriptions.get(identity).unsubscribe();
            subscriptions.remove(identity);
        }
        receivers.remove(identity);
    }

//    @Override
//    public void unsubscribe(Scope identity, Object receiver) {
//        Set<Object> ret = receivers.get(identity);
//        if (ret != null) {
//            ret.remove(receiver);
//            if (ret.isEmpty()) {
//                unsubscribe(identity);
//            }
//        }
//    }

    public void stop() {
        if (session.isConnected()) {
            for (StompSession.Subscription subscription : subscriptions.values()) {
                subscription.unsubscribe();
            }
        }
        subscriptions.clear();
    }

    @Override
    public Future<Message> ask(Message message) {
        final CompletableFuture<Message> ret = new CompletableFuture<>();
        post(message, (m) -> ret.complete(m));
        return ret;
    }
}
