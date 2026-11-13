package com.insurance.integration.security.entity;

import com.insurance.integration.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * OAuth 2.0 client credentials for a partner's machine-to-machine API access.
 *
 * <p>The {@code clientSecretHash} column stores a BCrypt hash (cost factor 12).
 * Plaintext secrets are never persisted and are returned by the API only once,
 * at creation/rotation time.</p>
 *
 * <p>{@code previousSecretHash} + {@code lastRotatedAt} implement the 24-hour
 * rotation grace window: both the old and the new secret authenticate while the
 * window is open, and only the new secret afterwards.</p>
 */
@Entity
@Table(name = "api_clients")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiClient extends BaseEntity {

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    /** Public identifier — safe to expose in responses and logs. */
    @Column(name = "client_id", nullable = false, unique = true, length = 100)
    private String clientId;

    /** BCrypt hash of the current client secret. Never expose. */
    @Column(name = "client_secret_hash", nullable = false, length = 500)
    private String clientSecretHash;

    /** BCrypt hash of the previous secret, valid only during the grace window. */
    @Column(name = "previous_secret_hash", length = 500)
    private String previousSecretHash;

    /** JSON array of granted scopes, e.g. {@code ["read","write"]}. */
    @Column(name = "scopes", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String scopes = "[\"read\"]";

    @Column(name = "token_expiry_seconds", nullable = false)
    @Builder.Default
    private int tokenExpirySeconds = 3600;

    @Column(name = "rate_limit_per_minute", nullable = false)
    @Builder.Default
    private int rateLimitPerMinute = 100;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** When the current secret was issued; null until the first rotation. */
    @Column(name = "last_rotated_at")
    private Instant lastRotatedAt;
}
