package com.basisttha.security.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Single, authoritative CORS configuration for the application.
 * Used by {@link SecurityConfig} via {@code .cors(c -> c.configurationSource(...))}.
 *
 * <p>The allowed origin is read from the {@code app.cors.allowed-origins} property
 * (set via env var {@code APP_CORS_ALLOWED_ORIGINS}) so the same binary works
 * in local dev, staging, and production without code changes.
 */
@Component
public class CustomCorsConfiguration implements CorsConfigurationSource {

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Requested-With", "Accept"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }
}
