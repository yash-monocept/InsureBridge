# Module Architecture & Implementation Details

**Document Version:** 2.0  
**Date:** November 13, 2026  
**Status:** COMPLETED MODULES 0, 1 & 2

---

## TABLE OF CONTENTS

1. [Module 0 — Foundation Layer](#module-0--foundation-layer)
2. [Module 1 — Partner Management](#module-1--partner-management)
3. [Module 2 — Partner Authentication & API Security](#module-2--partner-authentication--api-security)
4. [Completed Modules Summary](#completed-modules-summary)

---

## MODULE 0 — FOUNDATION LAYER

### Purpose

Establish the core Spring Boot application infrastructure, database schema, exception handling, API response structures, logging, and foundational tests.

### Components Implemented

#### 1. **Spring Boot Application Setup**

**File:** `src/main/java/com/insurance/integration/IntegrationMiddlewareApplication.java`

- Main Spring Boot application class
- Java 21 with Spring Boot 3.3.5
- Default package: `com.insurance.integration`
- Tomcat embedded server on port 8080

**Configuration:**
- `application.yml` — Main configuration
- `application-local.yml` — Local development profile
- `application-test.yml` (profiles/test) — Test profile with Flyway clean enabled

#### 2. **Spring Configuration Classes**

**JpaConfig**
- Enables Spring Data JPA repositories
- Enables JPA auditing for timestamp auto-population
- Configures Hibernate dialect for PostgreSQL

**SecurityConfig**
- Spring Security configuration foundation
- HTTP session management
- CORS configuration placeholder for future Module 2

**SwaggerConfig**
- OpenAPI 3.0 documentation
- Swagger UI at `/swagger-ui.html`
- Auto-scan of `@RestController` classes
- API version 1.0

**AwsConfig**
- AWS SDK v2 client factory methods
- Region and endpoint override support
- LocalStack support for local testing

#### 3. **Database & ORM**

**Flyway Migrations:**

| Migration | Purpose |
|-----------|---------|
| V1 | Create `partners`, `insurers`, `partner_types`, `insurer_types` tables |
| V2 | Create `partner_insurer_mappings` table |
| V3 | Create schema engine tables (`product_schemas`, `schema_versions`, `field_rules`, `document_rules`) |
| V4 | Create ingestion tables (`ingestion_requests`, `ingestion_documents`) |
| V5 | Create transfer tables (`transfer_jobs`, `document_validations`, `destination_documents`) |
| V6 | Create notifications table (`webhook_events`) |
| V7 | Create audit and outbox tables (`audit_events`, `outbox_events`) |
| V8 | Seed 3 test insurers (Max Life, Care Health, Axis Max Life) |
| V9 | Seed product schemas (Motor, Travel, Health, Loan) |

**JPA Entities:**

- `BaseEntity` — Abstract superclass with UUID PK, createdAt, updatedAt timestamps
- `Partner` — Distribution partner (bank, airline, etc.)
- `Insurer` — Insurance provider
- `PartnerInsurerMapping` — Authorization link between Partner and Insurer

**Key Database Features:**
- UUID primary keys (application-generated)
- Unique constraints on codes (partnerCode, insurerCode)
- Foreign key relationships with CASCADE delete (where appropriate)
- Indexes on frequently queried columns
- Timestamp auditing via Hibernate `@CreationTimestamp`, `@UpdateTimestamp`

#### 4. **Exception Handling**

**GlobalExceptionHandler** (`common/exception/`)

Implements centralized exception handling for:

| Exception | HTTP Status | Scenario |
|-----------|-------------|----------|
| `DuplicateResourceException` | 409 Conflict | Partner/Insurer code already exists |
| `ResourceNotFoundException` | 404 Not Found | Partner/Insurer/Mapping not found |
| `ValidationException` | 400 Bad Request | Invalid input data |
| `PartnerAuthenticationException` | 401 Unauthorized | Invalid API credentials (Module 2) |
| `PartnerNotActiveException` | 403 Forbidden | Deactivated partner attempting request |
| `SsrfAttemptException` | 400 Bad Request | SSRF attack detected |
| `DocumentRetrievalException` | 500 Internal Server Error | S3 retrieval failed |

**Response Format:**
- All errors wrapped in `ApiResponse<Void>` with `success=false`
- `ApiError` contains: code, message, details, timestamp
- Stack traces NEVER exposed to client
- Request ID and Partner ID logged via MDC (Mapped Diagnostic Context)

#### 5. **API Response Structure**

**ApiResponse<T>** — Standard response envelope

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... },
  "timestamp": "2026-11-12T11:46:15Z"
}
```

**PagedResponse<T>** — For paginated endpoints

```json
{
  "success": true,
  "data": [ ... ],
  "pagination": {
    "page": 0,
    "pageSize": 10,
    "totalElements": 42,
    "totalPages": 5,
    "sortBy": "createdAt",
    "sortDir": "DESC"
  }
}
```

#### 6. **Common Enums**

| Enum | Values | Usage |
|------|--------|-------|
| `PartnerType` | BANK, AIRLINE, RAILWAY, TRAVEL_PLATFORM, ECOMMERCE, OTHER | Partner classification |
| `InsurerType` | LIFE, HEALTH, GENERAL, MOTOR | Insurance provider type |
| `ProductType` | MOTOR_INSURANCE, TRAVEL_INSURANCE, HEALTH_INSURANCE, LOAN_INSURANCE | Insurance product |
| `IngestionStatus` | RECEIVED, VALIDATING, VALIDATED, QUEUED, PROCESSING, COMPLETED, FAILED | Request processing state |
| `DocumentStatus` | PENDING, VALID, REJECTED, TRANSFERRED, ARCHIVED | Document state |
| `NotificationEventType` | INGESTION_RECEIVED, VALIDATION_FAILED, VALIDATION_PASSED, TRANSFER_COMPLETED, TRANSFER_FAILED | Events |
| `AuditAction` | CREATE, UPDATE, ACTIVATE, DEACTIVATE, DELETE, RETRIEVE | Actions logged |

#### 7. **Logging Foundation**

**Configuration:**
- SLF4J with Logback
- Log levels: INFO (default), DEBUG (com.insurance.integration), WARN (Spring Security)
- MDC context: `requestId`, `partnerId` for traceability
- Pattern includes timestamp, thread, log level, request ID, partner ID, logger, message

**Usage:**
```java
@Slf4j
public class PartnerService {
    public PartnerResponse registerPartner(CreatePartnerRequest request, String createdBy) {
        log.info("Partner registered: code={}, type={}, createdBy={}", 
                 request.getPartnerCode(), request.getPartnerType(), createdBy);
    }
}
```

#### 8. **Unit Tests**

**Test Classes:**
- `PartnerServiceTest` — 10 tests
- `InsurerServiceTest` — 7 tests
- `PartnerMappingServiceTest` — 4 tests

**Framework:**
- JUnit 5
- Mockito for dependency mocking
- AssertJ for fluent assertions

**Coverage:**
- Happy path scenarios
- Duplicate resource detection
- Not-found scenarios
- Update/activate/deactivate flows
- Response DTO validation

**Total: 21 tests, 100% passing**

---

## MODULE 1 — PARTNER MANAGEMENT

### Purpose

Implement REST APIs for administrators to register partners, insurers, and configure their relationships. This is the administrative control plane for the entire middleware.

### Components Implemented

#### 1. **REST Controllers**

**PartnerController** (`partner/controller/PartnerController.java`)

| Endpoint | Method | Purpose | Status |
|----------|--------|---------|--------|
| `/api/v1/admin/partners` | POST | Register new partner | ✅ |
| `/api/v1/admin/partners/{id}` | PUT | Update partner details | ✅ |
| `/api/v1/admin/partners/{id}/activate` | PUT | Activate deactivated partner | ✅ |
| `/api/v1/admin/partners/{id}/deactivate` | PUT | Deactivate active partner | ✅ |
| `/api/v1/admin/partners/{id}` | GET | Get partner by ID | ✅ |
| `/api/v1/admin/partners/code/{code}` | GET | Get partner by code | ✅ |
| `/api/v1/admin/partners` | GET | List partners (paginated) | ✅ |
| `/api/v1/admin/partners/{id}/mappings` | POST | Map partner to insurer | ✅ |
| `/api/v1/admin/partners/{id}/mappings` | GET | List partner's mappings | ✅ |
| `/api/v1/admin/partners/mappings/{mappingId}` | DELETE | Deactivate mapping | ✅ |

**InsurerController** (`insurer/controller/InsurerController.java`)

| Endpoint | Method | Purpose | Status |
|----------|--------|---------|--------|
| `/api/v1/admin/insurers` | POST | Register new insurer | ✅ |
| `/api/v1/admin/insurers/{id}` | GET | Get insurer by ID | ✅ |
| `/api/v1/admin/insurers/code/{code}` | GET | Get insurer by code | ✅ |
| `/api/v1/admin/insurers` | GET | List insurers (paginated) | ✅ |
| `/api/v1/admin/insurers/{id}/activate` | PUT | Activate insurer | ✅ |
| `/api/v1/admin/insurers/{id}/deactivate` | PUT | Deactivate insurer | ✅ |

#### 2. **Request/Response DTOs**

**Partner DTOs:**

- `CreatePartnerRequest` — Fields: partnerCode, partnerName, partnerType, contactEmail, maxDocumentSizeMb
- `UpdatePartnerRequest` — Partial update of partner details
- `PartnerResponse` — Returned by all partner endpoints (excludes sensitive fields)

**Insurer DTOs:**

- `CreateInsurerRequest` — Fields: insurerCode, insurerName, insurerType, contactEmail, destinationBucketName, destinationBucketRegion, destinationIamRoleArn, destinationPathPrefix, webhookUrl (optional)
- `InsurerResponse` — Public response (excludes IAM ARN, webhook secrets)

**Mapping DTO:**

- `CreateMappingRequest` — Fields: insurerCode, permittedProductTypes (JSON string)
- `MappingResponse` — Mapping status and configuration

#### 3. **Request Validation**

All DTOs use Jakarta Bean Validation:

```java
@NotBlank(message = "Partner code is required")
@Pattern(regexp = "^[A-Z0-9_]+$", message = "Code must be uppercase alphanumeric")
private String partnerCode;

@Email(message = "Invalid email format")
private String contactEmail;

@Min(value = 1)
@Max(value = 500)
private Integer maxDocumentSizeMb;
```

**Validation Error Response:**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "details": [
      {
        "field": "partnerCode",
        "message": "Partner code is required"
      }
    ]
  }
}
```

#### 4. **Service Layer**

**PartnerService** (`partner/service/PartnerService.java`)

Methods:
- `registerPartner(CreatePartnerRequest, createdBy)` → PartnerResponse
- `updatePartner(partnerId, UpdatePartnerRequest)` → PartnerResponse
- `activatePartner(partnerId)` → PartnerResponse
- `deactivatePartner(partnerId)` → PartnerResponse
- `getPartnerById(partnerId)` → PartnerResponse
- `getPartnerByCode(partnerCode)` → PartnerResponse
- `listPartners(active, type, search, pageable)` → Page<PartnerResponse>

**Business Logic:**
- Validates unique partner codes
- Enforces partner activation/deactivation state transitions
- Supports partial updates
- Transactional boundaries on state changes

**InsurerService** (`insurer/service/InsurerService.java`)

Methods:
- `registerInsurer(CreateInsurerRequest)` → InsurerResponse
- `activateInsurer(insurerId)` → InsurerResponse
- `deactivateInsurer(insurerId)` → InsurerResponse
- `getInsurerById(insurerId)` → InsurerResponse
- `getInsurerByCode(insurerCode)` → InsurerResponse
- `listInsurers(active, type, pageable)` → Page<InsurerResponse>

**Business Logic:**
- Validates unique insurer codes
- Stores IAM ARN and S3 config server-side only (never returned to client)
- Supports pagination and filtering

**PartnerMappingService** (`partner/service/PartnerMappingService.java`)

Methods:
- `createMapping(partnerId, CreateMappingRequest, createdBy)` → MappingResponse
- `getMappingsForPartner(partnerId)` → List<MappingResponse>
- `deactivateMapping(mappingId)` → MappingResponse

**Business Logic:**
- Validates partner and insurer exist and are active
- Prevents duplicate mappings
- Deactivation preserves historical records

#### 5. **Data Access Layer**

**Repositories** (Spring Data JPA):

```java
public interface PartnerRepository extends JpaRepository<Partner, UUID> {
    Optional<Partner> findByPartnerCode(String code);
    boolean existsByPartnerCode(String code);
    Page<Partner> findAll(Specification<Partner> spec, Pageable pageable);
}

public interface InsurerRepository extends JpaRepository<Insurer, UUID> {
    Optional<Insurer> findByInsurerCode(String code);
    boolean existsByInsurerCode(String code);
    Page<Insurer> findAll(Specification<Insurer> spec, Pageable pageable);
}

public interface PartnerInsurerMappingRepository extends JpaRepository<PartnerInsurerMapping, UUID> {
    List<PartnerInsurerMapping> findActiveByPartnerId(UUID partnerId);
    boolean existsByPartnerIdAndInsurerId(UUID partnerId, UUID insurerId);
}
```

**Query Examples:**
- Find by code (unique index) — O(1) lookup
- Find by active status — Filtered query with index
- Paginated listing — Efficient offset-based pagination

#### 6. **Integration Tests**

**PartnerControllerIT** — 14 test cases

| Test | Coverage |
|------|----------|
| `registerPartner_success` | Happy path, 201 response |
| `registerPartner_duplicateCode_conflict` | 409 for duplicate code |
| `registerPartner_missingFields_badRequest` | 400 for invalid request |
| `registerPartner_invalidEmail_badRequest` | Email validation |
| `listPartners_success` | Pagination works |
| `listPartners_filterByActive` | Active filter |
| `listPartners_maxPageSize` | Max page size enforcement (100) |
| `getPartner_success` | Retrieve by ID |
| `getPartner_notFound` | 404 for non-existent |
| `activatePartner_success` | State transition |
| `deactivatePartner_success` | State transition |
| `createMapping_success` | Mapping creation |
| `createMapping_invalidInsurer_notFound` | 404 for bad insurer |
| `getMappings_success` | List mappings |

**InsurerControllerIT** — 15 test cases

| Test | Coverage |
|------|----------|
| `registerInsurer_success` | Happy path, 201 response |
| `registerInsurer_duplicateCode_conflict` | 409 for duplicate |
| `registerInsurer_missingFields_badRequest` | Validation |
| `registerInsurer_invalidEmail_badRequest` | Email validation |
| `registerInsurer_invalidIamArn_badRequest` | IAM ARN format validation |
| `listInsurers_success` | Pagination |
| `listInsurers_filterByActive` | Active filter |
| `listInsurers_filterByType` | Type filter |
| `listInsurers_maxPageSize` | Max page size |
| `getInsurer_success` | Retrieve by ID |
| `getInsurer_notFound` | 404 |
| `getInsurerByCode_success` | Code lookup |
| `activateInsurer_success` | State transition |
| `deactivateInsurer_success` | State transition |
| `getInsurer_sensitiveFieldsNotExposed` | Security: No IAM ARN in response |

**Note:** Integration tests require Spring Boot test context and are designed to run with `@SpringBootTest` + `@AutoConfigureMockMvc`.

### Test Execution

**Unit Tests (no Docker required):**
```bash
mvn test -Dtest="!ApplicationContextTest,!*ControllerIT"
```

**All Tests (includes Docker-based tests):**
```bash
mvn test
```

**Result (full suite, November 16): 129 tests, 0 failures, 7 skipped — 122 executed**

The 7 skips are `ApplicationContextTest` Testcontainers cases that require
Docker. Module-scoped counts in the completed-module summaries below describe the
suite at each module's delivery; the M2 additions are listed in its Test Coverage
table.

---

## COMPLETED MODULES SUMMARY

### MODULE 0 ✅ COMPLETE

**Status:** All acceptance criteria met

**Deliverables:**
- Spring Boot 3.3.5 application
- PostgreSQL schema (9 Flyway migrations)
- Exception handling
- API response structures
- Logging infrastructure
- context tests (Testcontainers cases skip without Docker) + H2 profile tests
- Executable JAR (76.5 MB)
- Complete documentation

**Key Metrics:**
- LOC (Lines of Code): ~2,500
- Test Coverage: context tests (Testcontainers cases skip without Docker)
- Database Tables: 15
- Compilation: Success
- Build Artifacts: integration-middleware-1.0.0-SNAPSHOT.jar

### MODULE 1 ✅ COMPLETE

**Status:** All acceptance criteria met

**Deliverables:**
- 2 REST controllers (Partner, Insurer)
- 10 endpoints for partner management
- 6 endpoints for insurer management
- 3 endpoints for partner-insurer mappings
- Request/response DTOs with validation
- Service layer (3 services)
- Repository layer (3 repositories)
- 29 integration tests
- Swagger/OpenAPI documentation
- Complete controller-layer documentation

**Key Metrics:**
- REST Endpoints: 16 business endpoints (17 with health)
- Service Methods: 22 public methods
- DTOs: 8 classes
- Integration Tests: 29 test cases
- Validation Rules: 15+ constraints

### COMBINED MODULE 0 + 1 STATUS

| Metric | Value |
|--------|-------|
| Total Test Cases | 50 (21 unit + 29 integration) |
| Test Pass Rate | 100% |
| REST Endpoints | 16 business (17 with health) |
| Database Tables | 15 |
| Flyway Migrations | 9 |
| Service Methods | 22 |
| LOC (Approx) | 5,000+ |
| Build Status | SUCCESS |

---

## MODULE 2 — PARTNER AUTHENTICATION & API SECURITY

**Status:** COMPLETE (November 13, 2026; verified and hardened November 16, 2026)

### Purpose

Authenticate external distribution partners using OAuth 2.0 Client Credentials,
issue and validate RS256 JWTs, enforce scoped permissions, rate-limit partner
traffic, support secret rotation, and block SSRF attempts in partner-supplied
document URLs.

Detailed design and operations: [`docs/07-Partner-Authentication.md`](07-Partner-Authentication.md).

### Components Implemented

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `ApiClient` | `security/entity` | OAuth 2.0 credential record; BCrypt secret hashes only |
| `ApiClientRepository` | `security/repository` | Client lookup, active filtering, per-partner listing |
| `ClientCredentialsService` | `security/service` | Credential creation, authentication, rotation, activation |
| `JwtService` | `security/jwt` | RS256 access/refresh token generation and parsing |
| `JwtKeyProvider` | `security/jwt` | Loads configured RSA keys; ephemeral dev key pair otherwise |
| `JwtAuthenticationFilter` | `security/filter` | Validates Bearer tokens for `/api/v1/partner/**`, populates SecurityContext |
| `RateLimitService` | `security/service` | In-memory sliding-window counters per client |
| `RateLimitInterceptor` | `security/interceptor` | Returns 429 + `Retry-After` when a partner exceeds its limit |
| `SsrfValidator` | `security/validator` | Approved-host and private-address validation for document URLs |
| `AuthController` | `security/controller` | `POST /auth/token` (JSON + form), `/auth/refresh`, `/auth/verify` |
| `ApiClientAdminController` | `security/controller` | Admin credential lifecycle under `/admin/partners/{id}/api-clients` |
| `PartnerProfileController` | `security/controller` | `GET /partner/me`, protected by JWT + `SCOPE_read` |
| `V10__Add_api_client_secret_rotation.sql` | `db/migration` | Adds `previous_secret_hash` to the `api_clients` table created in V2 |

### Authentication Flow

```
Admin: POST /api/v1/admin/partners                         -> partnerId
Admin: POST /api/v1/admin/partners/{partnerId}/api-clients -> clientId + clientSecret (once)
Partner: POST /api/v1/auth/token (client_credentials)      -> access_token + refresh_token
Partner: GET  /api/v1/partner/me  Authorization: Bearer …  -> 200 profile
```

1. `ClientCredentialsService.authenticate` looks up the active client and
   compares the presented secret against the BCrypt hash.
2. A deactivated client returns 401; a deactivated partner returns 403.
3. `JwtService` issues an RS256 access token (default 3600s, per-client override)
   and a refresh token (default 7 days).
4. On partner requests, `JwtAuthenticationFilter` verifies signature, issuer and
   expiry, rejects non-access tokens, re-checks partner status, and maps scopes
   to `SCOPE_*` authorities.
5. `@PreAuthorize` enforces the required scope; `RateLimitInterceptor` enforces
   the per-client quota before the controller runs.

### Security Properties

- Client secrets: 256-bit random, URL-safe, BCrypt cost 12, never logged or
  returned after creation/rotation.
- Rotation: previous secret is accepted for 24 hours, then permanently invalid.
- Immediate revocation: deactivating a partner blocks already-issued tokens
  (status is re-checked per request).
- SSRF: `http`/`https` only, no userinfo, no non-standard ports, private/
  loopback/link-local/metadata hosts blocked, host must match
  `approved_source_hosts` (wildcards match one DNS label).
- Rate limiting: sliding 60-second window per client id, fail-closed when no
  principal is present, `X-RateLimit-*` headers on allowed requests.
- Standards compliance: token responses are non-cacheable (RFC 6749 §5.1) and
  partner 401/403 responses carry RFC 6750 `WWW-Authenticate` challenges.

### Endpoints Added (Module 2)

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/api/v1/auth/token` | Public | Client Credentials (JSON or form-encoded) |
| POST | `/api/v1/auth/refresh` | Public (refresh token) | New access/refresh pair |
| GET | `/api/v1/auth/verify` | Bearer | Token introspection |
| GET | `/api/v1/partner/me` | Bearer + `read` | Authenticated partner profile |
| POST | `/api/v1/admin/partners/{partnerId}/api-clients` | Admin (open in academic build) | Create credentials |
| GET | `/api/v1/admin/partners/{partnerId}/api-clients` | Admin (open in academic build) | List credential metadata |
| PUT | `/api/v1/admin/partners/{partnerId}/api-clients/{clientId}/rotate` | Admin (open in academic build) | Rotate secret |
| PUT | `/api/v1/admin/partners/{partnerId}/api-clients/{clientId}/activate` | Admin (open in academic build) | Activate client |
| PUT | `/api/v1/admin/partners/{partnerId}/api-clients/{clientId}/deactivate` | Admin (open in academic build) | Deactivate client |

### Test Coverage

| Suite | Tests | Result |
|-------|-------|--------|
| `JwtServiceTest` | 6 | PASS |
| `ClientCredentialsServiceTest` | 9 | PASS |
| `RateLimitServiceTest` | 4 | PASS |
| `SsrfValidatorTest` | 7 | PASS |
| `SecurityPropertyTest` (100 randomized inputs per property) | 6 | PASS |
| `PartnerAuthenticationIT` | 27 | PASS |
| Postman collection | 33 requests / 37 assertions | PASS |

---

## NEXT MODULES

### MODULE 3 - REQUEST INGESTION
- Partner-facing REST API
- Insurance request submission
- Request persistence
- Idempotency handling

### MODULE 4 - Schema & Tag Validation
- Configurable validation engine
- Product-specific schemas
- Tag/field validation rules
- Validation result reporting

---

*End of Module Architecture Document*
