# Insurance Integration Middleware and Cross-Account AWS Document Transfer Platform

## Project Summary

This platform is an **integration intermediary** between external distribution partners (car dealerships,
banks, airlines, etc.) and insurance providers (Max Life, Care Health, etc.).

It receives insurance application requests from partners, validates them, retrieves supporting documents
from partner S3 storage, and securely transfers them to the correct insurer's S3 storage — asynchronously
and with full auditability.

> **This system is NOT an insurance company.** It does not underwrite, price, approve, or settle insurance.
> It routes, validates, and transfers documents between parties.

---

## Reference Documents

| Document | Purpose |
|----------|---------|
| `Insurance Policy and Claim Management System.pdf` | Official SRS (51 pages) |
| `image with details about the verbal discussion of this project.jpg` | Handwritten architecture notes |

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.3.x, Maven |
| Database | PostgreSQL 15, Flyway, JPA/Hibernate |
| Security | OAuth 2.0 Client Credentials, JWT, Spring Security |
| AWS | S3, SQS, Lambda, SNS, API Gateway, IAM |
| AWS SDK | AWS SDK for Java v2 |
| Documentation | OpenAPI 3, Swagger UI |
| Testing | JUnit 5, Mockito, Spring Boot Test, Testcontainers |
| Local Dev | LocalStack, Docker |

---

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 15+ (only if using PostgreSQL instead of the default H2 profile)
- Docker (optional; for LocalStack)

### Local Setup

```bash
# Run with the default in-memory H2 database (data is lost on restart)
mvn spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui.html`
Health check: `http://localhost:8080/api/v1/health`
Operations console: `http://localhost:8080/`

The default `h2` profile creates the schema with Hibernate and keeps all data in
memory for local API testing; everything is cleared when the app stops. Use the
PostgreSQL profile (Flyway migrations V1–V10) for persistent data.

The operations console covers the implemented partner, insurer, partner-insurer
mapping, API-client, and partner-authentication endpoints. The default H2 database
is in-memory, so locally created data and credentials are cleared when the app stops.
API-client secrets are displayed only at creation or rotation and are kept in the
browser page's memory only.

### Authenticate as a partner (Module 2)

```bash
# 1. Register a partner (admin API)
curl -X POST http://localhost:8080/api/v1/admin/partners \
  -H "Content-Type: application/json" \
  -d '{"partnerCode":"DEMO_BANK","partnerName":"Demo Bank","partnerType":"BANK","contactEmail":"demo@bank.example"}'

# 2. Issue OAuth 2.0 credentials for that partner (id from step 1)
curl -X POST http://localhost:8080/api/v1/admin/partners/{partnerId}/api-clients \
  -H "Content-Type: application/json" \
  -d '{"scopes":["read","write"]}'

# 3. Exchange the credentials for a JWT
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"grant_type":"client_credentials","client_id":"cli_...","client_secret":"..."}'

# 4. Call a partner endpoint with the token
curl http://localhost:8080/api/v1/partner/me \
  -H "Authorization: Bearer <access_token>"
```

### Authentication endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/auth/token` | Exchange client credentials for access and refresh tokens (JSON or form-encoded) |
| POST | `/api/v1/auth/refresh` | Exchange a refresh token for a new token pair |
| GET | `/api/v1/auth/verify` | Inspect a valid access token (`Authorization: Bearer <access_token>`) |
| GET | `/api/v1/partner/me` | Test protected partner access; requires a JWT with `read` or `admin` scope |

First register a partner, then create an API client using
`POST /api/v1/admin/partners/{partnerId}/api-clients`. The admin endpoints are
intentionally unauthenticated in this academic build; protect them before any
public or production deployment.

`client_secret` is returned only when credentials are created or rotated. See
[`docs/07-Partner-Authentication.md`](docs/07-Partner-Authentication.md) and
[`docs/08-API-Documentation.md`](docs/08-API-Documentation.md).

### Postman

Import the Module 2 collection and environment from `postman/`, then run:

```bash
npx postman-cli collection run \
  postman/Module-2-Partner-Authentication.postman_collection.json \
  -e postman/Insurance-Integration-Local.postman_environment.json
```

The collection has 33 requests covering credential provisioning, token
issuance/refresh/verification, JWT enforcement, scopes, rate limiting, secret
rotation and revocation. It also asserts the RFC 6750 `WWW-Authenticate`
challenges and non-cacheable token responses.

### Tests

```bash
mvn -B test
```

Current suite: **129 tests, 0 failures** (122 executed against H2; 7 PostgreSQL
Testcontainers tests skip when Docker is unavailable). Module 2 includes
example-based unit tests, integration tests and a randomized property suite
(`SecurityPropertyTest`, 100 inputs per property) for token integrity, SSRF
host validation and rate limiting.

---

## Project Structure

```
src/main/java/com/insurance/integration/
├── config/          # Spring, Security, AWS, Swagger config
├── security/        # OAuth, JWT, filters
├── partner/         # Partner and insurer management
├── ingestion/       # Request ingestion
├── schema/          # Schema and tag validation engine
├── validation/      # Business validation
├── document/        # Document file validation
├── storage/         # AWS S3 source and destination
├── transfer/        # Transfer job management
├── notification/    # SNS and webhooks
├── audit/           # Audit logging
└── common/          # Exceptions, responses, enums, utils

docs/                # All documentation (21 documents)
infrastructure/      # AWS, Docker, LocalStack configs
postman/             # Postman collection
scripts/             # Utility scripts
```

---

## Documentation Index

| # | Document |
|---|---------|
| 01 | Project Overview |
| 02 | Requirements Analysis |
| 03 | Requirements Traceability Matrix |
| 04 | System Architecture |
| 05 | Module Architecture |
| 06 | Database Design |
| 07 | Partner Authentication (Module 2) |
| 08 | API Documentation |
| 09 | Partner Integration Guide |
| 10 | AWS Architecture |
| 11 | S3 Document Transfer Workflow |
| 12 | Validation Engine |
| 13 | Asynchronous Processing |
| 14 | Security Architecture |
| 15 | Error Handling |
| 16 | Testing Strategy |
| 17 | Test Execution Report |
| 18 | Deployment Guide |
| 19 | Environment Configuration |
| 20 | Troubleshooting |
| 21 | Final Project Report |

---

## Key Partner Integration Flow

```
Partner → POST /api/v1/partner/insurance/submit
        ← 202 Accepted + ingestionRequestId

Partner → GET /api/v1/partner/status/{ingestionRequestId}
        ← Current processing status

System asynchronously:
  1. Fetches documents from partner S3
  2. Validates documents
  3. Uploads to insurer S3
  4. Notifies partner via webhook/SNS
```

---

## Module Progress

See [PROJECT_PROGRESS.md](PROJECT_PROGRESS.md) for current status.

---

## Submission Deadline

**November 13–18, 2026**

---

*Built for academic submission. This is a backend engineering project demonstrating
integration middleware architecture, AWS multi-account document transfer, and enterprise-grade
validation pipelines.*
