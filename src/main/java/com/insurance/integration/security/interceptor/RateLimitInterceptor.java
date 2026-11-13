package com.insurance.integration.security.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.integration.common.response.ApiError;
import com.insurance.integration.security.principal.PartnerPrincipal;
import com.insurance.integration.security.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;

/**
 * Enforces per-partner rate limits on {@code /api/v1/partner/**}.
 *
 * <p>Responds with HTTP 429 and a {@code Retry-After} header when the partner
 * exceeds its configured requests-per-60-seconds limit. Runs after the JWT
 * authentication filter, so the partner principal is always available.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.default-per-minute:100}")
    private int defaultLimit;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof PartnerPrincipal principal)) {
            // The authentication filter runs first and rejects anonymous partner requests.
            // If we get here without a principal, fail closed.
            writeRateLimited(response, request, 1);
            return false;
        }

        int limit = principal.rateLimitPerMinute() > 0
                ? principal.rateLimitPerMinute()
                : defaultLimit;

        RateLimitService.RateLimitResult result =
                rateLimitService.tryAcquire(principal.clientId(), limit);

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, result.remaining())));

        if (!result.allowed()) {
            log.warn("Rate limit exceeded: clientId={}, partnerCode={}, path={}",
                    principal.clientId(), principal.partnerCode(), request.getRequestURI());
            writeRateLimited(response, request, result.retryAfterSeconds());
            return false;
        }

        return true;
    }

    private void writeRateLimited(HttpServletResponse response, HttpServletRequest request,
                                  long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(429)
                .errorType("RATE_LIMIT_EXCEEDED")
                .message("Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.")
                .path(request.getRequestURI())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
