package com.basisttha.security.config;

/**
 * Intentionally empty.
 *
 * The duplicate CorsFilter bean that lived here was removed because
 * {@link CustomCorsConfiguration} already provides the single, authoritative
 * CORS policy consumed by Spring Security's filter chain.  Having two CORS
 * configurations active simultaneously can produce conflicting preflight
 * responses and is a common source of hard-to-debug 403 errors.
 */
public class GlobalCorsFilterConfig {
    // No beans — CORS is handled entirely by CustomCorsConfiguration.
}
