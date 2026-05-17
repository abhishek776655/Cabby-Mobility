package com.mobility.realtime.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${realtime.websocket.endpoint:/ws}")
    private String endpoint;

    @Value("${realtime.websocket.application-prefix:/app}")
    private String applicationPrefix;

    @Value("${realtime.websocket.topic-prefix:/topic}")
    private String topicPrefix;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(topicPrefix);
        registry.setApplicationDestinationPrefixes(applicationPrefix);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns("*");
    }
}
