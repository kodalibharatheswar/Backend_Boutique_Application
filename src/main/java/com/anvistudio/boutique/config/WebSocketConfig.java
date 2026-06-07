package com.anvistudio.boutique.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Use /topic for broadcasting messages (e.g., /topic/live.chat,
        // /topic/live.viewers)
        config.enableSimpleBroker("/topic");
        // Use /app for messages bound for methods annotated with @MessageMapping (e.g.,
        // /app/chat.send)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint that clients will connect to
        registry.addEndpoint("/api/ws-live")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
