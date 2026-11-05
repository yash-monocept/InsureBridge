# 03 — Requirements Traceability Matrix

## A. Middleware Platform Requirements

| Req ID | Source | Requirement | Module | Status |
|--------|--------|-------------|--------|--------|
| MID-001 | Prompt Part 2 | Authenticate distribution partners | MOD-2: Partner Auth | PENDING |
| MID-002 | Prompt Part 2 | Check partner permissions | MOD-2: Partner Auth | PENDING |
| MID-003 | Prompt Part 2 | Identify intended insurer | MOD-1: Partner Mgmt | PENDING |
| MID-004 | Prompt Part 2 | Validate incoming request schema | MOD-4: Schema Validation | PENDING |
| MID-005 | Prompt Part 2 | Detect missing/incorrect/conflicting fields | MOD-4: Schema Validation | PENDING |
| MID-006 | Prompt Part 2 | Verify source document S3 references | MOD-6: S3 Source | PENDING |
| MID-007 | Prompt Part 2 | Retrieve documents from partner S3 | MOD-6: S3 Source | PENDING |
| MID-008 | Prompt Part 2 | Perform document validation | MOD-8: Doc Processing | PENDING |
| MID-009 | Prompt Part 2 | Initiate async processing after validation | MOD-7: Async Processing | PENDING |
| MID-010 | Prompt Part 2 | Transfer documents to insurer S3 | MOD-9: Dest S3 | PENDING |
| MID-011 | Prompt Part 2 | Store transfer metadata and status | MOD-10: Metadata | PENDING |
| MID-012 | Prompt Part 2 | Notify relevant systems | MOD-11: Notifications | PENDING |
| MID-013 | Prompt Part 2 | Maintain complete audit history | MOD-12: Audit | PENDING |
| MID-014 | Prompt Part 4 | Return HTTP 202 only after durable persistence | MOD-3: Ingestion | PENDING |
| MID-015 | Prompt Part 4 | Transactional outbox for reliable queue publication | MOD-7: Async | PENDING |
| MID-016 | Prompt Part 5/MOD-0 | Spring Boot application starts | MOD-0: Foundation | PENDING |
| MID-017 | Prompt Part 5/MOD-0 | PostgreSQL connection works | MOD-0: Foundation | PENDING |
| MID-018 | Prompt Part 5/MOD-0 | Flyway migrations run | MOD-0: Foundation | PENDING |
| MID-019 | Prompt Part 5/MOD-0 | Swagger accessible | MOD-0: Foundation | PENDING |
| MID-020 | Prompt Part 5/MOD-0 | Health endpoint works | MOD-0: Foundation | PENDING |

## B. Partner Management Requirements

| Req ID | Source | Requirement | Module | Status |
|--------|--------|-------------|--------|--------|
| PM-001 | Prompt MOD-1 | Partner registration | MOD-1: Partner Mgmt | PENDING |
| PM-002 | Prompt MOD-1 | Partner types (AUTOMOBILE_DEALER, AIRLINE, etc.) | MOD-1: Partner Mgmt | PENDING |
| PM-003 | Prompt MOD-1 | Partner activation/deactivation | MOD-1: Partner Mgmt | PENDING |
| PM-004 | Prompt MOD-1 | Insurer registration | MOD-1: Partner Mgmt | PENDING |
| PM-005 | Prompt MOD-1 | Partner-insurer mapping | MOD-1: Partner Mgmt | PENDING |
| PM-006 | Prompt MOD-1 | Partner-product mapping | MOD-1: Partner Mgmt | PENDING |
| PM-007 | Prompt MOD-1 | Supported document types per partner | MOD-1: Partner Mgmt | PENDING |
| PM-008 | Prompt MOD-1 | Partner administration | MOD-1: Partner Mgmt | PENDING |

## C. Authentication and Security Requirements

| Req ID | Source | Requirement | Module | Status |
|--------|--------|-------------|--------|--------|
| SEC-001 | Prompt MOD-2 | API client registration | MOD-2: Auth | PENDING |
| SEC-002 | Prompt MOD-2 | Secure client secret management | MOD-2: Auth | PENDING |
| SEC-003 | Prompt MOD-2 | OAuth 2.0 Client Credentials | MOD-2: Auth | PENDING |
| SEC-004 | Prompt MOD-2 | Partner-specific permissions | MOD-2: Auth | PENDING |
| SEC-005 | Prompt MOD-2 | API scopes | MOD-2: Auth | PENDING |
| SEC-006 | Prompt MOD-2 | Credential rotation support | MOD-2: Auth | PENDING |
| SEC-007 | Prompt MOD-2 | Rate limiting | MOD-2: Auth | PENDING |
| SEC-008 | Prompt Part 9 | SSRF protection | MOD-6: S3 Source | PENDING |
| SEC-009 | Prompt Part 9 | Approved source host restrictions | MOD-6: S3 Source | PENDING |
| SEC-010 | Prompt Part 9 | Never trust partner-supplied destination bucket | MOD-9: Dest S3 | PENDING |
| SEC-011 | Prompt Part 9 | Webhook signature verification | MOD-11: Notifications | PENDING |
| SEC-012 | Prompt Part 9 | Idempotency for requests | MOD-3: Ingestion | PENDING |

## D. Validation Requirements (Middleware)

| Req ID | Source | Requirement | Module | Status |
|--------|--------|-------------|--------|--------|
| VAL-001 | Prompt MOD-4 | Mandatory field validation | MOD-4: Schema | PENDING |
| VAL-002 | Prompt MOD-4 | Data type validation | MOD-4: Schema | PENDING |
| VAL-003 | Prompt MOD-4 | Format validation | MOD-4: Schema | PENDING |
| VAL-004 | Prompt MOD-4 | Enumerated value validation | MOD-4: Schema | PENDING |
| VAL-005 | Prompt MOD-4 | Partner-specific schemas | MOD-4: Schema | PENDING |
| VAL-006 | Prompt MOD-4 | Insurer-specific schemas | MOD-4: Schema | PENDING |
| VAL-007 | Prompt MOD-4 | Product-specific schemas | MOD-4: Schema | PENDING |
| VAL-008 | Prompt MOD-4 | Schema versioning | MOD-4: Schema | PENDING |
| VAL-009 | Prompt MOD-5 | Cross-field business validation | MOD-5: Business Valid. | PENDING |
| VAL-010 | Prompt MOD-5 | Discrepancy detection and reporting | MOD-5: Business Valid. | PENDING |
| VAL-011 | Prompt MOD-5 | Blocking vs non-blocking validation | MOD-5: Business Valid. | PENDING |

## E. AWS Integration Requirements

| Req ID | Source | Requirement | Module | Status |
|--------|--------|-------------|--------|--------|
| AWS-001 | Prompt MOD-6 | Pre-signed S3 URL handling | MOD-6: S3 Source | PENDING |
| AWS-002 | Prompt MOD-6 | URL expiry handling | MOD-6: S3 Source | PENDING |
| AWS-003 | Prompt MOD-6 | MIME type and file signature checks | MOD-6/8 | PENDING |
| AWS-004 | Prompt MOD-7 | Amazon SQS integration | MOD-7: Async | PENDING |
| AWS-005 | Prompt MOD-7 | AWS Lambda worker | MOD-7: Async | PENDING |
| AWS-006 | Prompt MOD-7 | Dead-letter queues | MOD-7: Async | PENDING |
| AWS-007 | Prompt MOD-9 | Insurer-specific S3 configuration | MOD-9: Dest S3 | PENDING |
| AWS-008 | Prompt MOD-9 | IAM role-based cross-account access | MOD-9: Dest S3 | PENDING |
| AWS-009 | Prompt MOD-9 | Post-upload checksum verification | MOD-9: Dest S3 | PENDING |
| AWS-010 | Prompt MOD-11 | SNS integration | MOD-11: Notifications | PENDING |
| AWS-011 | Handwriting | API Gateway as front face | MOD-0: Foundation | PENDING |

## F. SRS Original Requirements Traceability

### F.1 SRS Authentication & Authorization

| SRS Req ID | Requirement | Middleware Equivalent | Status | Notes |
|-----------|-------------|----------------------|--------|-------|
| FR-AUTH-001 | Customer public registration | N/A in middleware | DEFERRED | Belongs to insurer admin system |
| FR-AUTH-002 | No public admin/agent registration | Platform admin managed internally | DEFERRED | |
| FR-AUTH-003 | Active users can login | Partner auth active check | ADAPTED | Partner equivalent |
| FR-AUTH-004 | JWT token generation | OAuth token / JWT for admin | ADAPTED | |
| FR-AUTH-005 | JWT validation for protected APIs | Token validation filter | IN SCOPE | |
| FR-AUTH-006 | Reject invalid/expired tokens | Auth filter | IN SCOPE | |
| FR-AUTH-007 | Role-based access | Partner permissions | ADAPTED | |
| FR-AUTH-008 | Inactive users blocked | Inactive partner blocked | ADAPTED | |
| FR-AUTH-009 | Secure password storage | Hashed client secrets | IN SCOPE | |

### F.2 SRS User Management

| SRS Req ID | Requirement | Status | Notes |
|-----------|-------------|--------|-------|
| FR-USER-001 | Admin view all users | DEFERRED | Insurer admin system |
| FR-USER-002 | Admin activate users | DEFERRED | |
| FR-USER-003 | Admin deactivate users | DEFERRED | |
| FR-USER-004 | Admin create agent accounts | DEFERRED | |
| FR-USER-005 | Prevent duplicate email | IN SCOPE (admin users) | Platform admin |
| FR-USER-006 | Created/updated dates | IN SCOPE | All entities |
| FR-USER-007 | Paginated user listing | DEFERRED | |

### F.3 SRS Customer Management

| SRS Req ID | Requirement | Status | Notes |
|-----------|-------------|--------|-------|
| FR-CUS-001 | Customer creates own profile | DEFERRED | Downstream insurer system |
| FR-CUS-002 | Customer updates own profile | DEFERRED | |
| FR-CUS-003 | Customer views own profile | DEFERRED | |
| FR-CUS-004 | Admin/agent view customers | DEFERRED | |
| FR-CUS-005 | Customer isolation | IN SCOPE (partner isolation) | Middleware enforces partner isolation |
| FR-CUS-006 | Paginated customer listing | DEFERRED | |

### F.4 SRS Policy & Claims (Abbreviated)

| SRS Module | Status | Notes |
|-----------|--------|-------|
| Insurance Product Mgmt (MOD-004) | DEFERRED | Insurer domain |
| Policy Plan Mgmt (MOD-005) | DEFERRED | Insurer domain |
| Policy Mgmt (MOD-006) | DEFERRED | Insurer domain |
| Premium Payment (MOD-007) | DEFERRED | Insurer domain |
| Claim Mgmt (MOD-008) | DEFERRED | Insurer domain |
| Claim Status History (MOD-009) | DEFERRED | Insurer domain |

**DEFERRED** means these requirements belong to a downstream insurance management system that the middleware delivers documents **to**, not functionality built inside the middleware itself.

---

## G. Processing Lifecycle Requirements

| Req ID | Status | Description |
|--------|--------|-------------|
| LC-001 | PENDING | RECEIVED status |
| LC-002 | PENDING | AUTHENTICATION_FAILED status |
| LC-003 | PENDING | VALIDATING status |
| LC-004 | PENDING | VALIDATION_FAILED status |
| LC-005 | PENDING | FETCHING_SOURCE status |
| LC-006 | PENDING | SOURCE_RETRIEVED status |
| LC-007 | PENDING | DOCUMENT_VALIDATION status |
| LC-008 | PENDING | DOCUMENT_REJECTED status |
| LC-009 | PENDING | VALIDATED status |
| LC-010 | PENDING | QUEUED status |
| LC-011 | PENDING | PROCESSING status |
| LC-012 | PENDING | TRANSFER_FAILED status |
| LC-013 | PENDING | TRANSFERRED status |
| LC-014 | PENDING | NOTIFICATION_PENDING status |
| LC-015 | PENDING | NOTIFICATION_FAILED status |
| LC-016 | PENDING | COMPLETED status |
| LC-017 | PENDING | DEAD_LETTER status |
| LC-018 | PENDING | State transition enforcement |

---

## H. Final Acceptance Criteria Traceability

| AC ID | Criterion | Module | Status |
|-------|-----------|--------|--------|
| AC-001 | Application starts | MOD-0 | PENDING |
| AC-002 | PostgreSQL connection works | MOD-0 | PENDING |
| AC-003 | Migrations work | MOD-0 | PENDING |
| AC-004 | Administrator can register partners | MOD-1 | PENDING |
| AC-005 | Partners can be authenticated | MOD-2 | PENDING |
| AC-006 | Partner permissions work | MOD-2 | PENDING |
| AC-007 | Partners can submit requests | MOD-3 | PENDING |
| AC-008 | Schema is validated | MOD-4 | PENDING |
| AC-009 | Missing/invalid tags identified | MOD-4 | PENDING |
| AC-010 | Data discrepancies detected | MOD-5 | PENDING |
| AC-011 | Invalid requests rejected | MOD-4/5 | PENDING |
| AC-012 | Valid requests persisted | MOD-3 | PENDING |
| AC-013 | Source S3 documents retrieved | MOD-6 | PENDING |
| AC-014 | Document integrity verified | MOD-8 | PENDING |
| AC-015 | Valid documents queued async | MOD-7 | PENDING |
| AC-016 | Background processing works | MOD-7 | PENDING |
| AC-017 | Documents reach insurer S3 | MOD-9 | PENDING |
| AC-018 | Metadata recorded | MOD-10 | PENDING |
| AC-019 | Transfer status queryable | MOD-10 | PENDING |
| AC-020 | Failed jobs retried safely | MOD-7 | PENDING |
| AC-021 | Notifications work | MOD-11 | PENDING |
| AC-022 | Audit history maintained | MOD-12 | PENDING |
| AC-023 | Swagger available | MOD-13 | PENDING |
| AC-024 | Postman tests provided | MOD-13 | PENDING |
| AC-025 | Unit/integration tests executed | All | PENDING |
| AC-026 | Security controls tested | MOD-2 | PENDING |
| AC-027 | Documentation complete | All | PENDING |
| AC-028 | Setup/deployment instructions work | MOD-0 | PENDING |
| AC-029 | Final report reflects actual implementation | MOD-13 | PENDING |
| AC-030 | Known limitations disclosed | All | PENDING |
