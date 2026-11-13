# Design Document

## Overview

Module 2 implements OAuth 2.0 Client Credentials authentication for partner machine-to-machine API access. The module adds JWT-based authentication filtering, permission-based authorization, rate limiting, and SSRF protection.

Key components:
- **ApiClient entity**: OAuth 2.0 client credentials stored in PostgreSQL
- **JwtService**: Token generation and validation using JWT library
- **AuthenticationFilter**: Spring Security filter that validates JWT on every request
- **RateLimitInterceptor**: Per-partner rate limiting using sliding window
- **SsrfValidator**: URL validation against approved host patterns

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            API Gateway / Spring MVC                         │
│  Request → Filter Chain → Controller → Service → Repository                 │
│                                  ↓                                          │
│                    ┌─────────────────────┐                                  │
│                    │  Authentication     │                                  │
│                    │     Filter          │                                  │
│                    │  (OncePerRequest)   │                                  │
│                    └──────────┬──────────┘                                  │
│                               ↓                                             │
│                    ┌─────────────────────┐                                  │
│                    │   JwtService        │                                  │
│                    │  - Generate Token   │                                  │
│                    │  - Validate Token   │                                  │
│                    └──────────┬──────────┘                                  │
│                               ↓                                             │
│                    ┌─────────────────────┐                                  │
│                    │  Rate Limiting      │                                  │
│                    │  Interceptor        │                                  │
│                    └──────────┬──────────┘                                  │
│                               ↓                                             │
│                    ┌─────────────────────┐                                  │
│                    │   Authorization     │                                  │
│                    │    Service          │                                  │
│                    └──────────┬──────────┘                                  │
│                               ↓                                             │
│                    ┌─────────────────────┐                                  │
│                    │  ApiClientRepo      │                                  │
│                    │    (PostgreSQL)     │                                  │
│                    └─────────────────────┘                                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. ApiClient Entity

**Location**: `src/main/java/com/insurance/integration/security/entity/ApiClient.java`

```java
@Entity
@Table(name = "api_clients")
public class ApiClient extends BaseEntity {
    private UUID partnerId;
    private String clientId;          // Public identifier
    private String clientSecretHash;  // BCrypt hash (never plaintext)
    private String scopes;            // JSON array
    private Integer tokenExpirySeconds;
    private Integer rateLimitPerMinute;
    private Boolean isActive;
    private Instant lastRotatedAt;    // For secret rotation window
}
```

### 2. JwtService Interface

**Location**: `src/main/java/com/insurance/integration/security/service/JwtService.java`

```java
public interface JwtService {
    String generateToken(ApiClient client);
    Claims parseClaims(String token);
    boolean validateToken(String token);
    Instant getExpiration(String token);
}
```

### 3. AuthenticationFilter

**Location**: `src/main/java/com/insurance/integration/security/filter/AuthenticationFilter.java`

```java
public class AuthenticationFilter extends OncePerRequestFilter {
    // Extracts JWT from Authorization header
    // Validates token signature and expiry
    // Populates SecurityContext with partner identity
}
```

### 4. RateLimitInterceptor

**Location**: `src/main/java/com/insurance/integration/security/interceptor/RateLimitInterceptor.java`

```java
@Component
public class RateLimitInterceptor {
    // Checks partner rate limit before request processing
    // Returns HTTP 429 if limit exceeded
    // Implements sliding window counter algorithm
}
```

### 5. SsrfValidator

**Location**: `src/main/java/com/insurance/integration/security/validator/SsrfValidator.java`

```java
@Component
public class SsrfValidator {
    // Validates partner-supplied URLs against approved_source_hosts
    // Supports wildcard patterns
    // Throws SsrfAttemptException if validation fails
}
```

### 6. ClientCredentialsRepository

**Location**: `src/main/java/com/insurance/integration/security/repository/ClientCredentialsRepository.java`

```java
@Repository
public interface ClientCredentialsRepository extends JpaRepository<ApiClient, UUID> {
    Optional<ApiClient> findByClientIdAndIsActiveTrue(String clientId);
    boolean existsByClientIdAndIsActiveTrue(String clientId);
}
```

### 7. ClientCredentialsService

**Location**: `src/main/java/com/insurance/integration/security/service/ClientCredentialsService.java`

```java
@Service
@Transactional
public class ClientCredentialsService {
    // Rotates client secret with 24-hour grace period
    // Validates current secret against hash
}
```

## Data Models

### ApiClient (Database Table)

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| partner_id | UUID | FK to partners.id |
| client_id | VARCHAR(100) | Public client identifier |
| client_secret_hash | VARCHAR(500) | BCrypt-hashed secret |
| scopes | TEXT | JSON array of permissions |
| token_expiry_seconds | INTEGER | Token validity (default: 3600) |
| rate_limit_per_minute | INTEGER | Request limit per minute |
| is_active | BOOLEAN | Active status |
| last_rotated_at | TIMESTAMP | Last secret rotation |
| created_at | TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | Last update |

### JWT Token Structure

**Header**:
```json
{
  "alg": "RS256",
  "typ": "JWT"
}
```

**Payload**:
```json
{
  "partner_id": "uuid",
  "partner_code": "AXIS_BANK",
  "client_id": "client-123",
  "scopes": ["read", "write"],
  "exp": timestamp,
  "iat": timestamp,
  "iss": "insurance-middleware"
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Prework Analysis

**Requirement 1.1**: WHEN a partner presents valid client_id and client_secret, THE AuthenticationService SHALL issue a JWT access token

- Thoughts: This tests that valid credentials produce a valid JWT. We can generate random valid client IDs and secrets, authenticate, and verify the token is issued with correct claims.
- Testable: yes - property

**Requirement 1.2**: WHEN the client_secret is invalid or hashed secret does not match, THE AuthenticationService SHALL return HTTP 401 Unauthorized

- Thoughts: We can test that any incorrect secret (wrong password, wrong client_id) results in 401. This is a universal property that should hold for all invalid credentials.
- Testable: yes - property

**Requirement 1.5**: WHEN a token is presented with an expired signature, THE AuthenticationFilter SHALL reject it with HTTP 401 Unauthorized

- Thoughts: We can generate tokens with past expiry timestamps and verify they are rejected. This property should hold for all expired tokens.
- Testable: yes - property

**Requirement 2.1**: WHEN a request is received for any endpoint under /api/v1/partner/**, THE AuthenticationFilter SHALL validate the Authorization header contains a valid JWT

- Thoughts: We can generate random requests to partner endpoints with valid, invalid, and missing tokens and verify the filter behavior.
- Testable: yes - property

**Requirement 5.2**: IF the partner has exceeded their rate_limit_per_minute threshold, THE RateLimitInterceptor SHALL return HTTP 429 Too Many Requests

- Thoughts: We can simulate rate limit exceeded scenarios by sending requests up to and beyond the limit. This property should hold for all partners exceeding their limit.
- Testable: yes - property

**Requirement 6.1**: WHEN a partner provides a pre-signed S3 URL for document processing, THE SsrfValidator SHALL validate the URL against the partner's approved_source_hosts

- Thoughts: We can generate random URLs both within and outside approved patterns and verify validation behavior. This tests SSRF protection universally.
- Testable: yes - property

**Requirement 1.6**: FOR ANY valid token, the token MUST contain partner_id, partner_code, scopes, and expiry timestamp as claims

- Thoughts: This is a structural requirement. We can generate tokens and verify they contain all required claims. This is a property that holds for all valid tokens.
- Testable: yes - property

**Requirement 4.2**: DURING rotation, the OLD client_secret SHALL remain valid for 24 hours to allow graceful migration

- Thoughts: We can test the 24-hour window by setting last_rotated_at and testing both old and new secrets. This is a time-bound property.
- Testable: yes - property

**Requirement 4.3**: AFTER 24 hours from rotation, the OLD client_secret SHALL be permanently invalidated

- Thoughts: We can simulate time after rotation and verify old secrets are rejected. This is a property that holds for all expired rotation windows.
- Testable: yes - property

**Classification Summary**:

| Requirement | Classification | Test Strategy |
|-------------|----------------|---------------|
| 1.1 | PROPERTY | Generate valid credentials → authenticate → verify token issued |
| 1.2 | PROPERTY | Generate invalid credentials → authenticate → verify 401 |
| 1.5 | PROPERTY | Generate expired tokens → validate → verify 401 |
| 2.1 | PROPERTY | Generate requests with various token states → filter → verify behavior |
| 5.2 | PROPERTY | Send requests up to/over limit → verify 429 when exceeded |
| 6.1 | PROPERTY | Generate URLs inside/outside approved patterns → validate → verify behavior |
| 1.6 | PROPERTY | Generate tokens → parse → verify all required claims present |
| 4.2 | PROPERTY | Test rotation window with timestamps → verify old secret works during grace |
| 4.3 | PROPERTY | Test expired rotation window → verify old secret fails |

### Correctness Properties

#### Property 1: Authentication issues valid JWT for valid credentials

*For any* valid ApiClient with active status and matching client_id and client_secret, authentication SHALL produce a JWT token that is valid and contains partner_id, partner_code, scopes, and expiry claims.

**Validates: Requirements 1.1, 1.6**

#### Property 2: Invalid credentials always rejected

*For any* request with invalid client_id or client_secret, the authentication service SHALL return HTTP 401 Unauthorized. This includes cases where the client exists but credentials don't match, the client doesn't exist, or the partner is inactive.

**Validates: Requirement 1.2**

#### Property 3: Expired tokens are always rejected

*For any* JWT token with an expired expiry timestamp, the authentication filter SHALL reject it with HTTP 401 Unauthorized regardless of other token properties.

**Validates: Requirement 1.5**

#### Property 4: Authentication filter validates all partner endpoints

*For any* request to endpoints under `/api/v1/partner/**`, the authentication filter SHALL validate the Authorization header contains a valid, non-expired JWT before allowing the request to proceed to the controller.

**Validates: Requirement 2.1**

#### Property 5: Rate limiting enforces per-partner limits

*For any* partner whose request count exceeds their configured rate_limit_per_minute within a sliding 60-second window, the rate limit interceptor SHALL return HTTP 429 Too Many Requests with Retry-After header.

**Validates: Requirement 5.2**

#### Property 6: SSRF protection validates all URLs

*For any* URL provided by a partner, the SSRF validator SHALL check it against the partner's approved_source_hosts pattern list and reject URLs whose host does not match any pattern with HTTP 400 Bad Request.

**Validates: Requirement 6.1**

#### Property 7: Secret rotation grace period works correctly

*For any* ApiClient that has rotated its secret within the last 24 hours, both the old and new secrets SHALL be accepted for authentication. After 24 hours, only the new secret SHALL be valid.

**Validates: Requirements 4.2, 4.3**

## Error Handling

### Exception Hierarchy

```
RuntimeException
├── PartnerAuthenticationException (401)
├── PartnerNotActiveException (403)
├── SsrfAttemptException (400)
├── RateLimitExceededException (429)
└── PermissionException (403)
```

### Error Response Format

```json
{
  "timestamp": "2024-01-01T00:00:00Z",
  "status": 401,
  "errorType": "AUTHENTICATION_FAILED",
  "message": "Invalid client credentials",
  "path": "/api/v1/auth/token"
}
```

### Exception Handling Strategy

| Exception | HTTP Status | Description |
|-----------|-------------|-------------|
| PartnerAuthenticationException | 401 | Invalid client_id or client_secret |
| PartnerNotActiveException | 403 | Partner account is deactivated |
| SsrfAttemptException | 400 | URL validation failed |
| RateLimitExceededException | 429 | Rate limit exceeded |
| PermissionException | 403 | Insufficient scope for operation |

## Testing Strategy

### Dual Testing Approach

- **Unit tests**: Verify specific examples, edge cases, and error conditions
- **Property tests**: Verify universal properties across all inputs

Property-based tests will use **fast-check** (TypeScript) or **jqwik** (Java) depending on implementation language.

### Property-Based Tests

| Property | Test Description | Library |
|----------|-----------------|---------|
| P1 | Valid credentials produce valid JWT with all required claims | Generate random valid credentials, authenticate, parse token, verify claims |
| P2 | Invalid credentials always return 401 | Generate random invalid credentials (wrong ID, wrong secret, inactive partner), verify 401 |
| P3 | Expired tokens always rejected | Generate tokens with past expiry, validate, verify 401 |
| P4 | Filter rejects requests without valid JWT | Generate requests with missing/invalid/expired tokens, verify 401 |
| P5 | Rate limit exceeded returns 429 | Generate requests up to/over limit, verify 429 with Retry-After |
| P6 | SSRF validation rejects non-approved URLs | Generate URLs inside/outside approved patterns, verify behavior |
| P7 | Rotation grace period accepts both old/new secrets | Set last_rotated_at, test old/new secrets within grace period |

### Unit Tests

- **JwtServiceTest**: Test token generation with various claims, test validation edge cases
- **AuthenticationFilterTest**: Test filter behavior with various Authorization headers
- **RateLimitInterceptorTest**: Test sliding window counter, reset behavior
- **SsrfValidatorTest**: Test pattern matching, wildcard support
- **ClientCredentialsServiceTest**: Test secret rotation, grace period logic

### Integration Tests

- **AuthFlowIntegrationTest**: End-to-end authentication flow
- **RateLimitIntegrationTest**: Real-time rate limiting across requests
- **SsrfValidationIntegrationTest**: URL validation with actual approved hosts

### Test Configuration

- **Property tests**: Minimum 100 iterations per property
- **Tags**: **Feature: partner-authentication, Property {number}: {property_text}**
- **Coverage target**: 90%+ for security modules

## Security Considerations

### Token Storage

- JWT tokens are stateless and self-contained
- No token blacklisting (tokens remain valid until expiry)
- Short token expiry (default 1 hour) limits impact of stolen tokens

### Secret Storage

- Client secrets stored as BCrypt hashes (cost factor 12)
- Never log or expose secrets in responses
- Rotation with 24-hour grace period allows migration

### Rate Limiting

- Per-partner limits configured in database
- Sliding window counter algorithm
- Fail closed (reject if cache unavailable)

### SSRF Protection

- Partner-specific approved host patterns
- Wildcard pattern support (*.s3.amazonaws.com)
- URL validation before any network access

## Deployment Notes

### Database Migration

**File**: `src/main/resources/db/migration/V3__Create_api_clients.sql`

```sql
CREATE TABLE api_clients (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL REFERENCES partners(id),
    client_id VARCHAR(100) NOT NULL UNIQUE,
    client_secret_hash VARCHAR(500) NOT NULL,
    scopes TEXT NOT NULL,
    token_expiry_seconds INTEGER DEFAULT 3600,
    rate_limit_per_minute INTEGER DEFAULT 100,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_rotated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_clients_client_id ON api_clients(client_id);
CREATE INDEX idx_api_clients_partner_id ON api_clients(partner_id);
CREATE INDEX idx_api_clients_is_active ON api_clients(is_active);
```

### Security Configuration Updates

Remove `permitAll` for `/api/v1/partner/**` and `/api/v1/admin/**` from SecurityConfig.java. These endpoints will be secured via JWT filter.

## API Endpoints

### POST /api/v1/auth/token

**Request**:
```json
{
  "client_id": "client-123",
  "client_secret": "secret"
}
```

**Success (200)**:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scopes": ["read", "write"]
}
```

**Errors**:
- 401: Invalid client credentials
- 403: Partner not active

### POST /api/v1/auth/refresh

**Request**:
```json
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Success (200)**:
Same response format as token endpoint.

### GET /api/v1/auth/verify

**Headers**:
```
Authorization: Bearer <token>
```

**Success (200)**:
```json
{
  "partner_id": "uuid",
  "partner_code": "AXIS_BANK",
  "scopes": ["read", "write"],
  "expires_at": "2024-01-01T01:00:00Z"
}
```

**Errors**:
- 401: Invalid or expired token