package com.insurance.integration.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter keyed by client id.
 *
 * <p>Each key keeps the timestamps of accepted requests within the current
 * window. A request is rejected when the number of timestamps inside the
 * window has reached the partner's configured limit. This is intentionally
 * local to the instance; a multi-instance deployment should back it with
 * Redis (the interface is deliberately small to make that swap easy).</p>
 */
@Service
public class RateLimitService {

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final long windowMillis;

    public RateLimitService(
            @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.windowMillis = windowSeconds * 1000L;
    }

    public RateLimitResult tryAcquire(String key, int limit) {
        return tryAcquire(key, limit, System.currentTimeMillis());
    }

    /**
     * Testable variant with an explicit clock.
     */
    public RateLimitResult tryAcquire(String key, int limit, long nowMillis) {
        if (limit <= 0) {
            return new RateLimitResult(true, Integer.MAX_VALUE, 0);
        }

        Deque<Long> timestamps = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            long cutoff = nowMillis - windowMillis;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= limit) {
                long oldest = timestamps.peekFirst();
                long retryAfterMillis = Math.max(0, oldest + windowMillis - nowMillis);
                long retryAfterSeconds = Math.max(1, (retryAfterMillis + 999) / 1000);
                return new RateLimitResult(false, 0, retryAfterSeconds);
            }

            timestamps.addLast(nowMillis);
            return new RateLimitResult(true, limit - timestamps.size(), 0);
        }
    }

    /**
     * Clears a key. Used by tests and when a client is deactivated.
     */
    public void reset(String key) {
        windows.remove(key);
    }

    /**
     * @param allowed           whether the request may proceed
     * @param remaining         remaining requests in the window after this call
     * @param retryAfterSeconds seconds until the next request is permitted (when rejected)
     */
    public record RateLimitResult(boolean allowed, int remaining, long retryAfterSeconds) {
    }
}
