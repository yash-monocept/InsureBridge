# Requirements Document

## Introduction

Module 2 implements secure partner authentication and API security for the Insurance Integration Middleware. This module adds OAuth 2.0 Client Credentials flow for machine-to-machine authentication, JWT token management, API request filtering, rate limiting, and security enhancements including SSRF protection and approved source host validation.

This module builds on Module 1 (Partner Management) by adding authentication and authorization controls for partner API clients registered in the system.

## Glossary

- **Partner**: An external distribution partner (bank, car dealer, airline, etc.) that integrates with the platform via API
- **ApiClient**: OAuth 2.0 client credentials record in the `api_clients` table representing a partner's authentication identity
- **Client ID**: Public identifier for an ApiClient (not secret)
- **Client Secret**: Secret credential for an ApiClient (must be protected, stored as BCrypt hash)
- **JWT**: JSON Web Token - stateless authentication token containing partner identity and permissions
- **OAuth 2.0 Client Credentials**: Standard OAuth 2.0 flow where clients exchange credentials for access tokens
- **Scope**: Permission granted to an ApiClient (e.g., "read", "write", "admin")
- **Rate Limit**: Maximum number of API requests allowed per minute for a partner
- **SSRF**: Server-Side Request Forgery - attack where attacker tricks server into making unintended HTTP requests
- **Approved Host**: Pre-approved S3 bucket host pattern allowed for document URL validation

## Requirements

### Requirement 1: OAuth 2.0 Client Credentials Authentication

**User Story:** As a partner, I want to authenticate with the platform using OAuth 2.0 Client Credentials, so that I can securely obtain tokens for API access without exposing my credentials on each request.

#### Acceptance Criteria

1. WHEN a partner presents valid client_id and client_secret, THE AuthenticationService SHALL issue a JWT access token
2. WHEN the client_secret is invalid or hashed secret does not match, THE AuthenticationService SHALL return HTTP 401 Unauthorized
3. WHEN the associated partner is inactive, THE AuthenticationService SHALL return HTTP 403 Forbidden
4. THE TokenService SHALL generate JWT tokens with expiry configured in `token_expiry_seconds` field (default 3600 seconds)
5. WHEN a token is presented with an expired signature, THE AuthenticationFilter SHALL reject it with HTTP 401 Unauthorized
6. FOR ANY valid token, the token MUST contain partner_id, partner_code, scopes, and expiry timestamp as claims

### Requirement 2: API Authentication Filter

**User Story:** As a developer, I want all partner API endpoints to require valid JWT authentication, so that unauthenticated requests are rejected before reaching business logic.

#### Acceptance Criteria

1. WHEN a request is received for any endpoint under `/api/v1/partner/**`, THE AuthenticationFilter SHALL validate the Authorization header contains a valid JWT
2. WHEN a request lacks a valid JWT token, THE AuthenticationFilter SHALL return HTTP 401 Unauthorized
3. WHEN a request includes an expired or malformed JWT, THE AuthenticationFilter SHALL reject it with HTTP 401 Unauthorized
4. WHEN a request includes a valid JWT, THE SecurityContext SHALL be populated with partner identity and scopes
5. THE AuthenticationFilter SHALL be configured as a Spring Security filter that executes before controller mapping
6. FOR ANY request that passes the filter, the filter SHALL extract partner_id and partner_code from the JWT and make them available in the request context

### Requirement 3: Permission-Based Authorization

**User Story:** As a partner, I want to have scoped permissions on my API credentials, so that my access is limited to only the operations I need.

#### Acceptance Criteria

1. WHEN a request includes a JWT, THE AuthorizationService SHALL check if the requested operation is permitted by the token's scopes
2. IF the requested operation requires a scope not present in the token, THE AuthorizationService SHALL return HTTP 403 Forbidden
3. FOR ANY endpoint requiring specific scopes, THE SecurityConfig SHALL enforce scope-based authorization using `@PreAuthorize` annotations
4. WHEN a partner attempts to access an endpoint beyond their permissions, THE system SHALL return a descriptive error indicating insufficient permissions
5. THE default scope for new ApiClients SHALL be ["read"] unless explicitly configured otherwise

### Requirement 4: Client Secret Rotation

**User Story:** As a partner administrator, I want to rotate my client_secret periodically, so that I can maintain security best practices without requiring platform re-registration.

#### Acceptance Criteria

1. WHEN a new client_secret is provided for rotation, THE ClientCredentialsService SHALL hash it with BCrypt and update the stored hash
2. DURING rotation, the OLD client_secret SHALL remain valid for 24 hours to allow graceful migration
3. AFTER 24 hours from rotation, the OLD client_secret SHALL be permanently invalidated
4. WHEN a secret is rotated, THE system SHALL record the last_rotated_at timestamp in the api_clients table
5. FOR ANY authentication attempt, THE system SHALL validate the presented secret against the CURRENT hashed secret

### Requirement 5: Rate Limiting Per Partner

**User Story:** As a platform operator, I want to enforce rate limits on API usage per partner, so that no single partner can overwhelm the system.

#### Acceptance Criteria

1. WHEN a request is received, THE RateLimitInterceptor SHALL check the partner's rate limit configuration
2. IF the partner has exceeded their rate_limit_per_minute threshold, THE RateLimitInterceptor SHALL return HTTP 429 Too Many Requests
3. WHEN rate limiting is enforced, THE response SHALL include Retry-After header indicating when the partner can retry
4. THE rate limit counter SHALL reset every 60 seconds
5. FOR ANY request that passes rate limiting, THE interceptor SHALL decrement the remaining quota in the current window

### Requirement 6: SSRF Protection and Approved Host Validation

**User Story:** As a security officer, I want to validate partner-supplied document URLs to prevent SSRF attacks, so that the system cannot be tricked into accessing internal or malicious resources.

#### Acceptance Criteria

1. WHEN a partner provides a pre-signed S3 URL for document processing, THE SsrfValidator SHALL validate the URL against the partner's approved_source_hosts
2. IF the URL host does not match any pattern in the approved_source_hosts list, THE SsrfValidator SHALL return HTTP 400 Bad Request
3. WHEN URL validation fails, THE error message SHALL NOT reveal the actual URL or infrastructure details
4. THE approved_source_hosts field SHALL support wildcard patterns (e.g., `*.s3.amazonaws.com`)
5. FOR ANY document URL received from a partner, THE validation SHALL occur before any network request is made

### Requirement 7: Database Persistence

**User Story:** As a system, I want to persist ApiClient credentials in PostgreSQL, so that authentication data survives application restarts and is available for multi-instance deployment.

#### Acceptance Criteria

1. WHEN an ApiClient is created, THE system SHALL persist it to the `api_clients` table in PostgreSQL
2. WHEN authentication occurs, THE ClientCredentialsRepository SHALL query the database using the provided client_id
3. THE api_clients table SHALL store client_secret as a BCrypt hash (never in plaintext)
4. FOR ANY query, THE system SHALL include only active clients (is_active = true)
5. THE table SHALL have indexes on client_id and partner_id for fast lookups

### Requirement 8: Security Enhancements and Error Handling

**User Story:** As a security engineer, I want comprehensive security exceptions and logging, so that authentication failures are properly tracked and differentiated.

#### Acceptance Criteria

1. THE PartnerAuthenticationException SHALL be thrown for invalid credentials with HTTP 401 status
2. THE PartnerNotActiveException SHALL be thrown for inactive partners with HTTP 403 status
3. THE SsrfAttemptException SHALL be thrown for SSRF validation failures with HTTP 400 status
4. WHEN an authentication failure occurs, THE system SHALL log partner_id or client_id for debugging (never secrets)
5. FOR ANY exception in the authentication flow, THE GlobalExceptionHandler SHALL return a standardized error response

## Non-Functional Requirements

### NFR 1: Performance

- JWT token validation SHALL complete within 50ms for 95th percentile
- Rate limiting check SHALL complete within 10ms for 95th percentile
- Database queries for ApiClient lookup SHALL complete within 20ms for 95th percentile

### NFR 2: Security

- Client secrets SHALL be stored as BCrypt hashes with cost factor 12
- JWT tokens SHALL use RS256 algorithm (asymmetric signing)
- All authentication-related logs SHALL be sent to security information system
- Token secrets SHALL never appear in logs, error messages, or responses

### NFR 3: Availability

- Authentication service SHALL maintain 99.9% uptime during business hours
- Token validation SHALL continue to function even if external services are unavailable
- Rate limiting SHALL fail closed (reject requests) if Redis or cache service is unavailable

### NFR 4: Maintainability

- Authentication filters SHALL be modular and testable in isolation
- Configuration SHALL be externalized to application.yml
- All security-related code SHALL be unit testable with mockable dependencies

## Out of Scope

- User interface for managing ApiClients (handled in admin portal, Module 1)
- Multi-factor authentication for partners (not applicable for machine-to-machine)
- Social login or consumer authentication
- Single Sign-On (SSO) with external identity providers
- Password reset flows for human users