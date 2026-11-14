package com.insurance.integration.config;

import com.insurance.integration.security.filter.JwtAuthenticationFilter;
import com.insurance.integration.security.handler.RestAccessDeniedHandler;
import com.insurance.integration.security.handler.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * <p>Module 2 adds stateless JWT authentication for partner APIs:</p>
 * <ul>
 *   <li>{@code /api/v1/auth/**} — public OAuth 2.0 token endpoints.</li>
 *   <li>{@code /api/v1/partner/**} — requires a valid RS256 JWT; the
 *       {@link JwtAuthenticationFilter} populates the SecurityContext and
 *       scope checks are enforced with {@code @PreAuthorize}.</li>
 *   <li>{@code /api/v1/admin/**} — admin console APIs. These remain open in
 *       the academic deployment because platform-admin authentication is out
 *       of Module 2 scope; they must be locked down before production.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            // Same-origin operations console assets
            "/",
            "/index.html",
            "/console.css",
            "/console.js",
            "/favicon.ico",
            // Swagger UI / OpenAPI
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/v3/api-docs",
            // Spring Actuator
            "/actuator/**",
            // OAuth 2.0 token endpoints
            "/api/v1/auth/**",
            // Application health check (public — used by load balancers and API gateways)
            "/api/v1/health",
            // Admin APIs — platform-admin authentication is outside Module 2 scope.
            // TODO: protect with a separate admin identity provider before production.
            "/api/v1/admin/**",
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — REST API uses tokens not cookies
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session — no server-side sessions
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Consistent JSON errors for filter-chain rejections
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                // Public endpoints; partner APIs require JWT; everything else authenticated
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/v1/partner/**").authenticated()
                        .anyRequest().authenticated()
                )

                // JWT filter runs before username/password authentication
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Disable form login and HTTP basic for REST API
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        log.info("Security filter chain configured: JWT authentication active for /api/v1/partner/**");

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
