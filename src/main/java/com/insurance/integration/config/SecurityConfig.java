package com.insurance.integration.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration.
 *
 * Module 0 — foundation configuration.
 * Full JWT filter and partner authentication will be added in Module 2.
 * This configuration permits public access to Swagger, actuator, and the auth endpoint.
 * All partner API endpoints will be secured in Module 2.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            // Swagger UI
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/v3/api-docs",
            // Spring Actuator
            "/actuator/**",
            // Auth endpoint
            "/api/v1/auth/**",
            // Application health check (public — used by load balancers and API gateways)
            "/api/v1/health",
            // Admin and partner APIs — temporarily open until Module 2 JWT filter is added.
            // TODO Module 2: Remove these and enforce JWT for all /api/** endpoints.
            "/api/v1/admin/**",
            "/api/v1/partner/**",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — REST API uses tokens not cookies
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session — no server-side sessions
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Permit public endpoints; secure everything else
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )

                // Disable form login and HTTP basic for REST API
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        log.debug("Security filter chain configured (Module 0 foundation — JWT filter added in Module 2)");

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
