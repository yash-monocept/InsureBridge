package com.insurance.integration.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.integration.common.response.ApiError;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.repository.PartnerRepository;
import com.insurance.integration.security.jwt.JwtService;
import com.insurance.integration.security.principal.PartnerPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Validates JWT bearer tokens for all {@code /api/v1/partner/**} endpoints.
 *
 * <p>On success the Spring Security context is populated with a
 * {@link PartnerPrincipal} and {@code SCOPE_*} authorities so that
 * {@code @PreAuthorize} can enforce scopes. On failure a structured
 * {@link ApiError} is written directly (401 for missing/invalid/expired tokens,
 * 403 for inactive partners) — controllers are never invoked.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PARTNER_PATH_PREFIX = "/api/v1/partner/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final PartnerRepository partnerRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PARTNER_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            log.warn("Authentication required for {} — missing bearer token", request.getRequestURI());
            writeError(response, request, 401, "AUTHENTICATION_REQUIRED",
                    "Authorization header with a Bearer token is required.");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeError(response, request, 401, "AUTHENTICATION_REQUIRED",
                    "Authorization header with a Bearer token is required.");
            return;
        }

        Claims claims;
        try {
            claims = jwtService.parseClaims(token);
        } catch (ExpiredJwtException e) {
            writeError(response, request, 401, "TOKEN_EXPIRED", "The access token has expired.");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            writeError(response, request, 401, "INVALID_TOKEN", "The access token is invalid.");
            return;
        }

        if (!JwtService.TOKEN_TYPE_ACCESS.equals(claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class))) {
            writeError(response, request, 401, "INVALID_TOKEN", "The access token is invalid.");
            return;
        }

        UUID partnerId;
        try {
            partnerId = UUID.fromString(claims.get(JwtService.CLAIM_PARTNER_ID, String.class));
        } catch (Exception e) {
            writeError(response, request, 401, "INVALID_TOKEN", "The access token is invalid.");
            return;
        }

        // Re-check partner status so deactivation takes effect immediately,
        // without waiting for outstanding access tokens to expire.
        Partner partner = partnerRepository.findById(partnerId).orElse(null);
        if (partner == null || !partner.isActive()) {
            log.warn("SECURITY: request from inactive or unknown partner: id={}", partnerId);
            writeError(response, request, 403, "PARTNER_NOT_ACTIVE",
                    "Partner is not active: " + claims.get(JwtService.CLAIM_PARTNER_CODE, String.class));
            return;
        }

        @SuppressWarnings("unchecked")
        List<String> scopes = claims.get(JwtService.CLAIM_SCOPES, List.class);
        if (scopes == null) {
            scopes = List.of();
        }
        Integer rateLimit = claims.get(JwtService.CLAIM_RATE_LIMIT, Integer.class);

        PartnerPrincipal principal = new PartnerPrincipal(
                partnerId,
                claims.get(JwtService.CLAIM_PARTNER_CODE, String.class),
                claims.get(JwtService.CLAIM_CLIENT_ID, String.class),
                scopes,
                rateLimit == null ? 0 : rateLimit,
                claims.getExpiration().toInstant());

        var authorities = scopes.stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();

        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Authenticated partner request: partnerCode={}, clientId={}, scopes={}",
                principal.partnerCode(), principal.clientId(), scopes);

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request,
                            int status, String errorType, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(status)
                .errorType(errorType)
                .message(message)
                .path(request.getRequestURI())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
