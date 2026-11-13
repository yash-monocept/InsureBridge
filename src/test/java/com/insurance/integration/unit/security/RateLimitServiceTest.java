package com.insurance.integration.unit.security;

import com.insurance.integration.security.service.RateLimitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Module 2 — RateLimitService")
class RateLimitServiceTest {

    @Test
    @DisplayName("requests within the limit are allowed and remaining quota decreases")
    void allowsWithinLimit() {
        RateLimitService service = new RateLimitService(60);

        var first = service.tryAcquire("client-a", 2, 1_000L);
        var second = service.tryAcquire("client-a", 2, 2_000L);

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(1);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isZero();
    }

    @Test
    @DisplayName("requests over the limit are rejected with a Retry-After value")
    void rejectsOverLimit() {
        RateLimitService service = new RateLimitService(60);
        service.tryAcquire("client-b", 2, 1_000L);
        service.tryAcquire("client-b", 2, 2_000L);

        var rejected = service.tryAcquire("client-b", 2, 3_000L);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("the sliding window frees capacity after 60 seconds")
    void windowSlides() {
        RateLimitService service = new RateLimitService(60);
        service.tryAcquire("client-c", 2, 1_000L);
        service.tryAcquire("client-c", 2, 2_000L);

        var afterWindow = service.tryAcquire("client-c", 2, 62_000L);

        assertThat(afterWindow.allowed()).isTrue();
        assertThat(afterWindow.remaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("limits are isolated per client")
    void isolatesClients() {
        RateLimitService service = new RateLimitService(60);
        service.tryAcquire("client-d", 1, 1_000L);

        assertThat(service.tryAcquire("client-e", 1, 1_000L).allowed()).isTrue();
        assertThat(service.tryAcquire("client-d", 1, 2_000L).allowed()).isFalse();
    }
}
