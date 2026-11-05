# 04 — System Architecture

## A. Architecture Overview

The Insurance Integration Middleware is a **modular monolithic Spring Boot application** that acts as a
controlled integration channel between external distribution partners and insurance providers.

It is NOT an insurance system itself. It is a document routing and integration platform.

---

## B. High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                           EXTERNAL DISTRIBUTION PARTNERS                            │
│  (Car Dealerships, Axis Bank, Union Bank, Airlines, Railways, Travel Platforms)     │
└─────────────────────────────────┬───────────────────────────────────────────────────┘
                                   │  HTTPS + OAuth 2.0 Client Credentials
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              AWS API GATEWAY                                        │
│                     (Rate limiting, TLS termination, routing)                       │
└─────────────────────────────────┬───────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                       SPRING BOOT INTEGRATION API                                   │
│                                                                                     │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────────────┐  │
│  │ Partner Auth     │  │ Partner Registry │  │ Request Ingestion Controller      │  │
│  │ (OAuth/JWT)      │  │ & Permissions    │  │ POST /api/v1/partner/insurance/   │  │
│  └──────────────────┘  └──────────────────┘  │        submit                    │  │
│                                               └──────────────────────────────────┘  │
│                                                              │                       │
│  ┌───────────────────────────────────────────────────────────▼───────────────────┐  │
│  │                        SYNCHRONOUS VALIDATION PIPELINE                        │  │
│  │                                                                               │  │
│  │  [1] Schema & Tag Validation  →  [2] Business Validation  →                  │  │
│  │  [3] Source Document Reference Check  →  [4] Persist IngestionRequest        │  │
│  │  [5] Publish to SQS (Transactional Outbox)  →  Return HTTP 202               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────┬───────────────────────────────────────────────────┘
                                   │
                ┌──────────────────▼──────────────────────┐
                │         AMAZON SQS (Document Transfer    │
                │         Queue + Dead Letter Queue)       │
                └──────────────────┬──────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                    AWS LAMBDA / BACKGROUND WORKER                                   │
│                    (Asynchronous Document Processing)                               │
│                                                                                     │
│  [1] Retrieve job from SQS message                                                  │
│  [2] Fetch documents from PARTNER S3 (via pre-signed URL or IAM role)              │
│  [3] Validate documents (PDF/PNG/JPEG signature, checksum, MIME)                   │
│  [4] Upload validated documents to INSURER S3                                      │
│  [5] Verify post-upload checksum                                                   │
│  [6] Update TransferJob status in PostgreSQL                                       │
│  [7] Publish event to SNS / trigger webhook                                        │
└──────────────────────┬───────────────────────────────────────┬──────────────────────┘
                       │                                       │
          ┌────────────▼──────────────┐          ┌────────────▼──────────────┐
          │  PARTNER S3 BUCKET        │          │  INSURER S3 BUCKET        │
          │  (Source documents)       │          │  (Destination documents)  │
          │  (Partner's AWS account)  │          │  (Insurer's AWS account)  │
          └───────────────────────────┘          └───────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                        AMAZON SNS / WEBHOOK NOTIFICATIONS                           │
│                     (Partner systems + Insurer systems notified)                    │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                         POSTGRESQL DATABASE                                         │
│         (All metadata, status, audit, partner config, schemas)                      │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## C. Component Architecture

### C.1 Spring Boot Application Packages

```
com.insurance.integration
│
├── config/              # Spring, Security, AWS, Swagger configuration
├── security/            # JWT, OAuth2, auth filters, partner auth
├── partner/             # Partner and insurer management
│   ├── entity/
│   ├── repository/
│   ├── service/
│   ├── controller/
│   └── dto/
├── insurer/             # Insurer registry and configuration
├── api/                 # Common API layer utilities
├── ingestion/           # Request ingestion and lifecycle
│   ├── entity/
│   ├── repository/
│   ├── service/
│   ├── controller/
│   └── dto/
├── schema/              # Schema and tag validation engine
│   ├── entity/
│   ├── repository/
│   ├── service/
│   └── dto/
├── validation/          # Business validation and discrepancy detection
│   ├── service/
│   └── dto/
├── document/            # Document validation (file format, checksum)
│   ├── service/
│   └── model/
├── storage/             # AWS S3 integration (source + destination)
│   ├── source/
│   └── destination/
├── transfer/            # Document transfer job management
│   ├── entity/
│   ├── repository/
│   └── service/
├── notification/        # SNS and webhook notification
│   ├── entity/
│   ├── repository/
│   ├── service/
│   └── dto/
├── audit/               # Audit logging
│   ├── entity/
│   ├── repository/
│   └── service/
└── common/
    ├── exception/       # Global exception handling
    ├── response/        # Standard API response structure
    ├── logging/         # Structured logging utilities
    ├── enums/           # All enumerations
    └── utility/         # Shared utilities
```

---

## D. Data Flow — End to End

### D.1 Synchronous Path (HTTP Request)

```
Partner → POST /api/v1/partner/insurance/submit
        → AuthFilter validates OAuth token
        → PartnerService verifies partner active + has permission
        → SchemaValidationService validates fields and document rules
        → BusinessValidationService runs cross-field checks
        → IngestionService persists IngestionRequest (status=VALIDATED)
        → OutboxService publishes message to SQS within same transaction
        → Return HTTP 202 Accepted with ingestionRequestId
```

### D.2 Asynchronous Path (Lambda/Worker)

```
SQS message received by Lambda
→ TransferJobService fetches job details from DB
→ S3SourceService retrieves document from partner bucket
→ DocumentValidationService validates file
→ S3DestinationService uploads to insurer bucket
→ TransferJobService updates status to TRANSFERRED
→ NotificationService publishes SNS event / triggers webhook
→ Status: COMPLETED
```

### D.3 Failure Handling

```
Any step fails → Update status to appropriate failure code
             → Increment retry count
             → If retry < maxRetries → re-queue
             → If retry >= maxRetries → move to DEAD_LETTER queue
             → Notify operations team via SNS
```

---

## E. Authentication Architecture

### E.1 Partner Authentication

```
OAuth 2.0 Client Credentials Flow:

Partner sends: POST /auth/token
  client_id=<partnerId>
  client_secret=<secret>
  grant_type=client_credentials
  scope=document:submit

Platform validates:
  → Client exists
  → Secret matches (bcrypt)
  → Partner is active
  → Scope is permitted

Platform returns:
  → access_token (JWT)
  → token_type: Bearer
  → expires_in: 3600
  → scope
```

### E.2 Admin Authentication

```
Admin login: POST /api/v1/admin/auth/login
  → Email + password
  → Returns JWT token
  → Admin can manage partners, insurers, schemas
```

### E.3 API Security Layers

1. AWS API Gateway — TLS, rate limiting, IP filtering
2. Spring Security — Token validation, role enforcement
3. Partner isolation — Each request scoped to authenticated partner
4. Field-level access control — Partners cannot see other partners' data

---

## F. AWS Multi-Account Architecture

```
┌────────────────────────────────────────────────────────────────┐
│  MIDDLEWARE AWS ACCOUNT                                         │
│                                                                 │
│  ┌──────────────────┐    ┌──────────────────┐                  │
│  │  API Gateway     │    │  SQS + DLQ       │                  │
│  └──────────────────┘    └──────────────────┘                  │
│  ┌──────────────────┐    ┌──────────────────┐                  │
│  │  Lambda Worker   │    │  SNS Topics      │                  │
│  └──────────────────┘    └──────────────────┘                  │
│  ┌──────────────────┐    ┌──────────────────┐                  │
│  │  RDS PostgreSQL  │    │  Secrets Manager │                  │
│  └──────────────────┘    └──────────────────┘                  │
│  ┌──────────────────┐    ┌──────────────────┐                  │
│  │  CloudWatch Logs │    │  IAM Roles       │                  │
│  └──────────────────┘    └──────────────────┘                  │
└────────────────────────────────────────────────────────────────┘
         ↑                                    ↓
  Cross-account access                 Cross-account access
  via S3 bucket policy                 via IAM role assumption
         │                                    │
┌────────┴──────────────┐    ┌───────────────┴────────────────┐
│  PARTNER AWS ACCOUNT  │    │  INSURER AWS ACCOUNT            │
│  (e.g. Axis Bank)     │    │  (e.g. Max Life Insurance)      │
│                       │    │                                  │
│  S3 Source Bucket     │    │  S3 Destination Bucket          │
│  (read access granted │    │  (write access granted to       │
│   to middleware role) │    │   middleware role)               │
└───────────────────────┘    └──────────────────────────────────┘
```

---

## G. Processing Status State Machine

```
                    ┌──────────┐
                    │ RECEIVED │
                    └────┬─────┘
                         │
             ┌───────────▼──────────┐
             │    AUTHENTICATION    │
             │      FAILED          │ ← terminal failure
             └──────────────────────┘
                         │ (auth OK)
                    ┌────▼─────┐
                    │VALIDATING│
                    └────┬─────┘
              ┌──────────┴──────────┐
              │                     │
     ┌────────▼──────┐   ┌──────────▼────────┐
     │VALIDATION_    │   │  FETCHING_SOURCE   │
     │FAILED (term.) │   └──────────┬─────────┘
     └───────────────┘              │
                         ┌──────────▼─────────┐
                         │  SOURCE_RETRIEVED  │
                         └──────────┬─────────┘
                                    │
                         ┌──────────▼─────────┐
                         │ DOCUMENT_VALIDATION│
                         └──────────┬─────────┘
                    ┌───────────────┴───────────┐
                    │                           │
          ┌─────────▼────────┐       ┌──────────▼──────┐
          │ DOCUMENT_REJECTED│       │    VALIDATED    │
          │    (terminal)    │       └──────────┬──────┘
          └──────────────────┘                  │
                                     ┌──────────▼──────┐
                                     │     QUEUED      │
                                     └──────────┬──────┘
                                                │
                                     ┌──────────▼──────┐
                                     │   PROCESSING   │
                                     └──────────┬──────┘
                              ┌─────────────────┴─────────────┐
                              │                               │
                   ┌──────────▼──────┐             ┌──────────▼──────┐
                   │ TRANSFER_FAILED │             │  TRANSFERRED   │
                   │(retry / DLQ)    │             └──────────┬──────┘
                   └─────────────────┘                        │
                              ┌───────────────────────────────┘
                              │
                   ┌──────────▼──────────┐
                   │ NOTIFICATION_PENDING │
                   └──────────┬──────────┘
                   ┌──────────┴──────────┐
                   │                     │
        ┌──────────▼──────┐   ┌──────────▼──────┐
        │NOTIFICATION_    │   │   COMPLETED     │
        │FAILED (retry)   │   └─────────────────┘
        └─────────────────┘

        ┌───────────────────┐
        │   DEAD_LETTER     │ ← after maxRetries exhausted
        └───────────────────┘
```

---

## H. Technology Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Architecture style | Modular monolith | Required; avoids microservice complexity |
| Background worker | AWS Lambda | Required; separately deployable |
| Message queue | Amazon SQS | Required; reliable async delivery |
| Reliable publishing | Transactional outbox | Prevents lost jobs on app crash |
| Partner auth | OAuth 2.0 Client Credentials + JWT | Machine-to-machine standard |
| Admin auth | JWT (email/password) | Standard REST API auth |
| Database | PostgreSQL + Flyway | Required; UUID PKs for integration records |
| Document storage | AWS S3 (never in DB) | Security and scalability |
| Notifications | Amazon SNS + webhooks | Required |
| Local dev | LocalStack (S3, SQS, SNS) | No AWS credentials needed locally |
| Documentation | OpenAPI 3 + Swagger UI | Required |
| Java version | Java 21 | Required (LTS) |
| Spring Boot | 3.3.x | Latest stable for Java 21 |

---

## I. Module Dependency Diagram

```
MOD-0 (Foundation)
    └──► MOD-1 (Partner Management)
              └──► MOD-2 (Partner Auth & Security)
                        └──► MOD-3 (Request Ingestion)
                                  └──► MOD-4 (Schema Validation)
                                            └──► MOD-5 (Business Validation)
                                                      └──► MOD-6 (Source S3)
                                                                └──► MOD-7 (Async/SQS)
                                                                          └──► MOD-8 (Doc Processing)
                                                                                    └──► MOD-9 (Dest S3)
                                                                                              └──► MOD-10 (Metadata)
                                                                                                        └──► MOD-11 (Notifications)
                                                                                                                  └──► MOD-12 (Audit)
                                                                                                                            └──► MOD-13 (Swagger/Docs)
```

Each module depends on all modules above it but is independently testable with mocks.
