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
- PostgreSQL 15+
- Docker (for LocalStack)

### Local Setup

```bash
# 1. Copy environment file
cp .env.example .env
# Edit .env with your local PostgreSQL credentials

# 2. Start LocalStack (for S3, SQS, SNS)
docker-compose -f infrastructure/docker/docker-compose.yml up -d

# 3. Build and run
mvn clean install
mvn spring-boot:run

# 4. Access Swagger
open http://localhost:8080/swagger-ui.html

# 5. Health check
curl http://localhost:8080/actuator/health
```

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
| 07 | ER Diagram |
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
