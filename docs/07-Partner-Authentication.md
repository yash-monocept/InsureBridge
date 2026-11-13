# 07 — Partner Authentication (Module 2)

**Status:** COMPLETE  
**Completed:** November 13, 2026  
**Specification:** `.kiro/specs/partner-authentication/`

---

## 1. Purpose

Module 2 secures partner machine-to-machine access to the middleware. It adds:

- OAuth 2.0 Client Credentials authentication
- RS256 JWT access and refresh tokens
- JWT validation for all `/api/v1/partner/**` endpoints
- Scope-based authorization (`read`, `write`, `admin`)
- Per-client rate limiting with `Retry-After`
- Client secret rotation with a 24-hour grace window
- Immediate revocation when a client or partner is deactivated
- SSRF protection for partner-supplied document URLs

---

## 2. Architecture

```
Admin Console
  POST /api/v1/admin/partners/{partnerId}/api-clients
        |
        v
  ClientCredentialsService  ---> api_clients (PostgreSQL, BCrypt hashes)
        |
Partner
  POST /api/v1/auth/token (client_credentials)
        |
        v
  AuthenticationService ---> JwtService (RS256) ---> access + refresh token
        |
  GET /api/v1/partner/me   Authorization: Bearer <token>
        |
        v
  JwtAuthenticationFilter -> SecurityContext (PartnerPrincipal + SCOPE_*)
        |
        v
  RateLimitInterceptor -> @PreAuthorize -> PartnerProfileController
```

### Package layout

```
com.insurance.integration.security
├── controller/   AuthController, ApiClientAdminController, PartnerProfileController
├── dto/          TokenRequest/Response, VerifyTokenResponse, credential DTOs
├── entity/       ApiClient
├── filter/       JwtAuthenticationFilter
├── handler/      RestAuthenticationEntryPoint, RestAccessDeniedHandler
├── interceptor/  RateLimitInterceptor
├── jwt/          JwtKeyProvider, JwtService
├── principal/    PartnerPrincipal
├── repository/   ApiClientRepository
├── service/      ClientCredentialsService, AuthenticationService, RateLimitService
├── util/         ScopeUtils
└── validator/    SsrfValidator
```

---

## 3. Database

Migration: `V10__Add_api_client_secret_rotation.sql` — the `api_clients`
table is created in V2; V10 adds the rotation column.

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | Primary key |
| `partner_id` | UUID | FK to `partners.id` |
| `client_id` | VARCHAR(100) | Public identifier, unique |
| `client_secret_hash` | VARCHAR(500) | BCrypt cost 12 |
| `previous_secret_hash` | VARCHAR(500) | Valid only during the 24h grace window |
| `scopes` | TEXT | JSON array, default `["read"]` |
| `token_expiry_seconds` | INTEGER | 60–86400, default 3600 |
| `rate_limit_per_minute` | INTEGER | Default 100 |
| `is_active` | BOOLEAN | Default true |
| `last_rotated_at` | TIMESTAMPTZ | Drives the grace window |
| `created_at`, `updated_at` | TIMESTAMPTZ | Auditing |

Indexes: `client_id`, `partner_id`, `is_active`.

---

## 4. Token Design

### Access token claims

```json
{
  "iss": "insurance-integration-middleware",
  "sub": "AXIS_BANK",
  "partner_id": "0e6f...",
  "partner_code": "AXIS_BANK",
  "client_id": "cli_...",
  "scopes": ["read", "write"],
  "rate_limit": 100,
  "token_type": "access",
  "iat": 1763035200,
  "exp": 1763038800,
  "jti": "..."
}
```

- Algorithm: **RS256** (asymmetric).
- Access token TTL: per client (`token_expiry_seconds`, default 3600).
- Refresh token TTL: `app.security.jwt.refresh-expiry-seconds` (default 604800 = 7 days).
- Refresh tokens carry `token_type: "refresh"` and cannot be used on partner endpoints.

### Key management

`JwtKeyProvider` reads PEM keys from:

| Property | Environment variable | Purpose |
|----------|----------------------|---------|
| `app.security.jwt.private-key` | `JWT_PRIVATE_KEY` | PKCS#8 private key |
| `app.security.jwt.public-key` | `JWT_PUBLIC_KEY` | X.509 public key |
| `app.security.jwt.issuer` | `JWT_ISSUER` | Token issuer |

If no keys are configured, a 2048-bit RSA key pair is generated at startup and a
warning is logged. This is convenient for development but **tokens are invalid
after restart** and no other instance can verify them. Configure persistent keys
for any deployed environment and share the public key with verifiers.

---

## 5. Credential Lifecycle

### Create credentials

`POST /api/v1/admin/partners/{partnerId}/api-clients`

```json
{
  "scopes": ["read", "write"],
  "tokenExpirySeconds": 3600,
  "rateLimitPerMinute": 100
}
```

Response (201) includes `client_secret` **once**. Only the BCrypt hash is stored.
Scope validation accepts only `read`, `write`, `admin`; defaults to `["read"]`.

### Rotate secret

`PUT /api/v1/admin/partners/{partnerId}/api-clients/{clientId}/rotate`

- New secret is returned once.
- The current hash moves to `previous_secret_hash`.
- `last_rotated_at` is set.
- Both secrets authenticate for 24 hours; afterwards only the new secret works.

### Activate / deactivate

- `PUT .../{clientId}/activate`
- `PUT .../{clientId}/deactivate`

Deactivated clients cannot obtain tokens (401). Deactivated partners cannot
obtain tokens (403) and existing partner tokens are rejected on the next request
because the filter re-checks partner status.

---

## 6. Authentication and Authorization

### Token endpoint

`POST /api/v1/auth/token`

JSON or `application/x-www-form-urlencoded`:

```json
{
  "grant_type": "client_credentials",
  "client_id": "cli_...",
  "client_secret": "..."
}
```

Response:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJSUzI1NiIs...",
  "refresh_expires_in": 604800,
  "scopes": ["read", "write"]
}
```

Errors:

| Condition | Status | `errorType` |
|-----------|--------|-------------|
| Unknown client / wrong secret / inactive client | 401 | `AUTHENTICATION_FAILED` |
| Inactive partner | 403 | `PARTNER_NOT_ACTIVE` |
| Unsupported `grant_type` | 401 | `AUTHENTICATION_FAILED` |

### Refresh and verify

- `POST /api/v1/auth/refresh` with `{"refresh_token": "..."}` returns a new pair.
- `GET /api/v1/auth/verify` with `Authorization: Bearer <access token>` returns
  `partner_id`, `partner_code`, `client_id`, `scopes`, `issued_at`, `expires_at`.

### Partner request filter

`JwtAuthenticationFilter` applies to `/api/v1/partner/**`:

| Situation | Status | `errorType` |
|-----------|--------|-------------|
| Missing Authorization header | 401 | `AUTHENTICATION_REQUIRED` |
| Malformed / wrong signature / refresh token used | 401 | `INVALID_TOKEN` |
| Expired token | 401 | `TOKEN_EXPIRED` |
| Partner missing or deactivated | 403 | `PARTNER_NOT_ACTIVE` |

On success it sets `PartnerPrincipal` and authorities `SCOPE_read`,
`SCOPE_write`, `SCOPE_admin`.

### Scope enforcement

Example:

```java
@PreAuthorize("hasAuthority('SCOPE_read') or hasAuthority('SCOPE_admin')")
@GetMapping("/api/v1/partner/me")
```

A token without the required scope receives 403 with `errorType: FORBIDDEN`.

---

## 7. Rate Limiting

- `RateLimitInterceptor` applies to `/api/v1/partner/**`.
- Algorithm: in-memory sliding window per `client_id`.
- Window: `app.rate-limit.window-seconds` (default 60).
- Limit: client `rate_limit_per_minute`, falling back to
  `app.rate-limit.default-per-minute`.
- Rejection: 429, `Retry-After`, `errorType: RATE_LIMIT_EXCEEDED`.
- Allowed responses include `X-RateLimit-Limit` and `X-RateLimit-Remaining`.

The in-memory store is per instance. A multi-instance deployment must replace
`RateLimitService` with a Redis-backed implementation to enforce a global limit.
This is called out as a known limitation.

---

## 8. SSRF Protection

`SsrfValidator.validate(url, partner.approvedSourceHosts)`:

1. URL must parse and use `http` or `https`.
2. URL must not contain userinfo (`user:pass@`).
3. Port must be absent, 80, or 443.
4. Host must not be loopback, private, link-local, `.local`, `.internal`, or a
   cloud metadata endpoint (`169.254.169.254`, `metadata.google.internal`).
5. Host must match an approved pattern. `*` matches exactly one DNS label, so
   `*.s3.amazonaws.com` matches `bucket.s3.amazonaws.com` but not
   `s3.amazonaws.com` or `a.b.s3.amazonaws.com`.

Failure raises `SsrfAttemptException` (HTTP 400, `errorType: INVALID_URL`).
Error messages never include the supplied URL or host.

The validator is wired and unit-tested now; Module 6 will call it before any
document fetch.

---

## 9. Configuration

```yaml
app:
  security:
    jwt:
      issuer: insurance-integration-middleware
      private-key: ${JWT_PRIVATE_KEY:}
      public-key: ${JWT_PUBLIC_KEY:}
      refresh-expiry-seconds: 604800
    jwt-expiry-seconds: 3600
  rate-limit:
    default-per-minute: 100
    window-seconds: 60
```

---

## 10. Testing Evidence

| Layer | Suite | Result |
|-------|-------|--------|
| Unit | `JwtServiceTest` — claims, expiry, signature, issuer | 6/6 PASS |
| Unit | `ClientCredentialsServiceTest` — hashing, auth, rotation grace | 9/9 PASS |
| Unit | `RateLimitServiceTest` — sliding window, isolation | 4/4 PASS |
| Unit | `SsrfValidatorTest` — wildcard, internal hosts, URL shapes | 7/7 PASS |
| Integration | `PartnerAuthenticationIT` — full HTTP flow | 21/21 PASS |
| Collection | Postman Module 2 collection | 26 requests / 30 assertions PASS |

Full suite: **101 tests, 0 failures, 0 errors, 3 skipped** (the three
Testcontainers tests skip automatically when Docker is unavailable).

---

## 11. Known Limitations

1. **Admin API authentication** — `/api/v1/admin/**` remains unauthenticated in
   the academic deployment because platform-admin identity is outside Module 2.
   It must be protected before production.
2. **Rate limiting is per instance** — replace with Redis for horizontal scaling.
3. **No token revocation list** — access tokens remain valid until expiry, but
   partner deactivation is checked per request. Per-client deactivation blocks
   new tokens; outstanding tokens from a deactivated client remain valid until
   expiry unless token introspection is added to the filter.
4. **Ephemeral JWT keys by default** — configure RSA keys for persistent,
   multi-instance deployments.
5. **Secret rotation grace is time-based** — a scheduled cleanup of
   `previous_secret_hash` is not required because validation checks
   `last_rotated_at`, but old hashes remain in the row until the next rotation.
