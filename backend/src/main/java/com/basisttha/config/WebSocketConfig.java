package com.basisttha.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Comma-separated list of allowed origins for WebSocket connections.
     * Set via the ALLOWED_ORIGINS environment variable (or application.yml).
     * Defaults to localhost:5173 for local development.
     */
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker for the /docs prefix.
        // In a multi-node deployment this would be replaced with a full
        // STOMP broker relay (e.g. RabbitMQ) so broadcasts reach all nodes.
        config.enableSimpleBroker("/docs");
        config.setApplicationDestinationPrefixes("/docs");
        config.setPreservePublishOrder(true);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint("/docs/ws")
                // Never use "*" in production — it disables the same-origin check
                // and allows any website to open a WebSocket to this server using
                // a victim user's credentials (CSRF-over-WebSocket).
                .setAllowedOrigins(allowedOrigins.split(","));
                // SockJS fallback is intentionally omitted.  The frontend connects
                // via raw WebSocket (@stomp/stompjs brokerURL), which avoids the
                // need for the sockjs-client npm package and keeps the connection
                // path simple.  Add .withSockJS() here and sockjs-client in the
                // frontend if corporate proxy support is required.
    }
}
