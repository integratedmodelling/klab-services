package org.integratedmodelling.klab.services.application.security;

import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.handler.invocation.HandlerMethodReturnValueHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Component;
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

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ServicesAPI.MESSAGE).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(ServicesAPI.MESSAGE);
        registry.setApplicationDestinationPrefixes("/klab");
    }

    //
    //    @Autowired
    //    private ObjectMapper mapper;
    //
    public WebsocketsConfiguration() {
        //        objectMapper =
        //                new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        //                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION).disable(DeserializationFeature
        //                .FAIL_ON_UNKNOWN_PROPERTIES);
        //        JacksonConfiguration.configureObjectMapperForKlabTypes(objectMapper);
    }
    //
    //    @Override
    //    public void registerStompEndpoints(StompEndpointRegistry registry) {
    //        //    	Message.setPayloadMapTranslator((map, cls) -> {
    //        //    		if (org.integratedmodelling.klab.api.configuration.Configuration
    //        //    		.REST_RESOURCES_PACKAGE_ID.equals(cls.getPackage().getName())) {
    //        //    			return Utils.Json.convertMap(map, cls);
    //        //    		}
    //        //    		return map;
    //        //    	});
    //        registry.addEndpoint(ServicesAPI.MESSAGE).setAllowedOriginPatterns("*").withSockJS();
    //    }
    //
    //    @Override
    //    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
    //        registration.setSendTimeLimit(15 * 1000).setMessageSizeLimit(1024 * 1024)
    //        .setSendBufferSizeLimit(1024 * 1024);
    //    }
    //
    ////    @Bean
    ////    public ObjectMapper objectMapper() {
    ////        ObjectMapper objectMapper =
    ////                new ObjectMapper()
    ////                        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    ////                        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    ////                        .enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
    ////        // objectMapper.getSerializerProvider().setNullKeySerializer(new Jsr310NullKeySerializer());
    ////        JacksonConfiguration.configureObjectMapperForKlabTypes(objectMapper);
    ////        return objectMapper;
    ////    }
    //
    //    @Bean
    //    public ServletServerContainerFactoryBean createServletServerContainerFactoryBean() {
    //        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
    //        container.setMaxTextMessageBufferSize(32768);
    //        container.setMaxBinaryMessageBufferSize(32768);
    //        return container;
    //    }
    //
    //
    //    @Override
    //    public void configureClientOutboundChannel(ChannelRegistration registration) {
    //        registration.taskExecutor().corePoolSize(4).maxPoolSize(10);
    //    }
    //
    //    @Override
    //    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
    //        // TODO: ??
    //    }
    //
    //    @Override
    //    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> returnValueHandlers) {
    //        // TODO: ??
    //    }
    //
    //    @Override
    //    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
    //        // Workaround for issue 2445:
    //        // https://github.com/spring-projects/spring-boot/issues/2445
    //        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
    //        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
    //        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
    //        converter.setObjectMapper(mapper);
    //        converter.setContentTypeResolver(resolver);
    //        messageConverters.add(converter);
    //        return false;
    //    }
    //
    //    @Override
    //    public void configureMessageBroker(MessageBrokerRegistry configurer) {
    //        // Prefix for messages FROM server TO client
    //        configurer.enableSimpleBroker(ServicesAPI.MESSAGE);
    //        // Prefix for messages FROM client TO server, sent to /klab/message: :
    //        configurer.setApplicationDestinationPrefixes("/klab");
    //    }

}
