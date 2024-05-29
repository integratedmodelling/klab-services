package org.integratedmodelling.klab.services.application.security;

import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Usage: from JS:
 *
 * <pre>
 * var socket = new SockJS('/modeler/message');
 * stompClient = Stomp.over(socket);
 * stompClient.connect({}, function(frame) {
 * stompClient.subscribe('/message/' + sessionId, function(command){
 * var cmd = JSON.parse(command.body);
 * // process(cmd);
 * });
 * });
 *
 * Sending:
 *
 * stompClient.send("/klab/message", {}, JSON.stringify({ 'name': name}
 *
 * </pre>
 *
 * @author ferdinando.villa
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebsocketsConfiguration implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private ObjectMapper mapper;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ServicesAPI.MESSAGE).setAllowedOriginPatterns("*").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefix for messages from SERVER to CLIENT
        registry.enableSimpleBroker(ServicesAPI.MESSAGE);
        // Prefix for messages from CLIENT to SERVER, sent to /klab/message
        registry.setApplicationDestinationPrefixes("/klab");
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(15 * 1000).setMessageSizeLimit(1024 * 1024)
                    .setSendBufferSizeLimit(1024 * 1024);
    }

    @Bean
    public ServletServerContainerFactoryBean createServletServerContainerFactoryBean() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(32768);
        container.setMaxBinaryMessageBufferSize(32768);
        return container;
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(4).maxPoolSize(10);
    }


}
