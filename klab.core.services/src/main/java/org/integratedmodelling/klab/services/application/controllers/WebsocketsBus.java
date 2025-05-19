package org.integratedmodelling.klab.services.application.controllers;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import java.lang.reflect.Type;
import java.util.Collections;

/*
 * Q: Explain the following Java code:
 *
 * @Override
 * public void configureMessageBroker(MessageBrokerRegistry config) {
 *   config.setApplicationDestinationPrefixes("/app");
 *   config.enableSimpleBroker("/topic", "/queue");
 * }
 *
 * A: This code configures a WebSocket message broker in a Spring application:
 *
 * 1. Application Destination Prefixes:
 *    config.setApplicationDestinationPrefixes("/app")
 *    - This line sets `/app` as the prefix for messages that are bound for @MessageMapping methods in @Controller classes
 *    - When clients send messages to destinations starting with `/app`, they will be routed to these message-handling methods
 *
 * 2. Simple Broker Enable:
 *    config.enableSimpleBroker("/topic", "/queue")
 *    - This enables a simple memory-based message broker to carry messages back to the clients
 *    - `/topic` is typically used for publish-subscribe (one-to-many) messaging
 *    - `/queue` is typically used for point-to-point (one-to-one) messaging
 *
 * Q: Can I use this same class as a singleton to support more than one queue for different clients?
 *
 * A: Yes, you can use this class as a singleton to support multiple queues for different clients.
 * The @Configuration annotation already makes this class a Spring singleton bean, and the broker
 * configuration supports multiple queues by default.
 *
 * Here's how you can work with multiple queues:
 * 1. The message broker configuration already supports multiple queues under the `/queue` prefix
 * 2. Different clients can subscribe to different queue names, for example:
 *    - `/queue/client1`
 *    - `/queue/client2`
 *    - `/queue/notifications-user123`
 *
 * Example usage:
 * StompClient client1 = new StompClient("ws://localhost:8080/comms/application", "/queue/client1");
 * StompClient client2 = new StompClient("ws://localhost:8080/comms/application", "/queue/client2");
 *
 * client1.connect();
 * client2.connect();
 *
 * Keep in mind:
 * - Each queue name should be unique for its purpose
 * - You can have as many queues as needed, they're created dynamically
 * - Messages sent to one queue won't leak to other queues
 * - The broker maintains separate message streams for each queue
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebsocketsBus implements WebSocketMessageBrokerConfigurer {

    public static class StompClient {
        private final String serverUrl;
        private final String queueName;
        private WebSocketStompClient stompClient;
        private StompSession stompSession;

        public StompClient(String serverUrl, String queueName) {
            this.serverUrl = serverUrl;
            this.queueName = queueName;
            this.stompClient =
                    new WebSocketStompClient(
                            new SockJsClient(
                                    Collections.singletonList(
                                            new WebSocketTransport(new StandardWebSocketClient()))));
        }

        public void connect() {
            try {
                stompSession =
                        stompClient
                                .connectAsync(
                                        serverUrl,
                                        new StompSessionHandlerAdapter() {
                                            @Override
                                            public void afterConnected(
                                                    StompSession session, StompHeaders connectedHeaders) {
                                                session.subscribe(
                                                        queueName,
                                                        new StompFrameHandler() {
                                                            @Override
                                                            public Type getPayloadType(StompHeaders headers) {
                                                                return String.class;
                                                            }

                                                            @Override
                                                            public void handleFrame(StompHeaders headers, Object payload) {
                                                                System.out.println("Received message: " + payload);
                                                            }
                                                        });
                                            }
                                        })
                                .get();
            } catch (Exception e) {
                throw new RuntimeException("Could not connect to WebSocket server", e);
            }
        }

        public void disconnect() {
            if (stompSession != null && stompSession.isConnected()) {
                stompSession.disconnect();
            }
            if (stompClient != null) {
                stompClient.stop();
            }
        }
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/comms/application").setAllowedOrigins("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.setApplicationDestinationPrefixes("/app");
        config.enableSimpleBroker("/topic", "/queue");
    }

    public void start() {
        // Server startup logic
    }

    public void shutdown() {
        // Server shutdown logic
    }
}