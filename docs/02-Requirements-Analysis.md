# 02 — Requirements Analysis

## A. Source Documents

| # | Document | Type | Status |
|---|----------|------|--------|
| 1 | Insurance Policy and Claim Management System.pdf | Official SRS (51 pages) | Fully extracted |
| 2 | image with details about the verbal discussion of this project.jpg | Handwritten architecture notes | Fully read |

---

## B. Handwritten Image — Full Transcription

### Top Section (Page 1 of notebook)

```
IT Background   max
↓
Insurance domain services
Agent / Insurance / Integration
user / Integration

Request →  • Axis Bank
            - Union
            - CB  (Canara Bank / Co-operative Bank — UNCERTAIN)
            - Validation → x-orgnlz  (cross-organisation)
            - syn / Asynch
            - T'pos: fax's  (UNCERTAIN — possibly "Types: factors" or "fax documents")
            - validation
            - Tsted L  (UNCERTAIN — possibly "Tested Logic" or a name)
ul / Axis / clients
ul / Axiom Integrity  (UNCERTAIN — possibly a company name or "URL/Axiom Integrity")
INC
```

### Bottom Section (Page 2 of notebook)

```
Sandy - Doc tfr   (Sandy = a person's name; Doc tfr = Document Transfer)

AWS - (front face)   (AWS API Gateway as the front-facing entry point)
AWS - Bucket / SQS / SNS
SQS - Bucket  (SQS → triggers Lambda → Bucket?)
SNS / Lambda / DFTon  (DFT = Document Forward/Transfer; "on" = on Lambda?)
Aets → API Gateway → Doc /?  (Assets / Agents → API Gateway → Document routing)
Request S3 URL - Doc Bucket → new Bucket
Request S3 URL - Processing  / CDT  (CDT = possibly "Cross-Document Transfer" or "Content Delivery Transfer")
```

### Confidence Assessment

| Item | Confidence | Notes |
|------|-----------|-------|
| Max Life Insurance as insurer | HIGH | "max" clearly written |
| Axis Bank as distribution partner | HIGH | "Axis Bank" clearly written |
| Union Bank as distribution partner | HIGH | "Union" clearly written |
| AWS S3 Buckets | HIGH | "S3 URL", "Doc Bucket", "new Bucket" |
| AWS SQS | HIGH | "SQS" clearly written |
| AWS SNS | HIGH | "SNS" clearly written |
| AWS Lambda | HIGH | "Lambda" clearly written |
| AWS API Gateway | HIGH | "API Gateway" clearly written |
| Document Transfer workflow | HIGH | "Doc tfr", "Request S3 URL", "new Bucket" |
| Synchronous + Asynchronous processing | HIGH | "syn / Asynch" |
| Validation before async | HIGH | "Validation → x-orgnlz" |
| "Sandy" as person/project name | MEDIUM | First name on notes |
| "CB" as third partner bank | UNCERTAIN | Could be Canara Bank or Co-operative Bank |
| "T'pos: fax's" | UNCERTAIN | Could refer to document types including fax |
| "Axiom Integrity" | UNCERTAIN | Could be a company or an integrity check concept |
| "CDT" | UNCERTAIN | Cross-Document Transfer / Content Delivery Transfer |
| "DFTon" | UNCERTAIN | Document Forward Transfer on Lambda |

---

## C. SRS Document — Key Extracted Requirements

### C.1 Project Identity

- **Title:** Insurance Policy and Claim Management System
- **Type:** Backend REST API (Spring Boot)
- **Domain:** Insurance
- **Pages:** 51
- **Difficulty:** Moderate

### C.2 User Roles (SRS)

| Role | Responsibility |
|------|---------------|
| Admin | Products, plans, users, final claim decisions |
| Agent | Policy issuance, claim review, claim recommendations |
| Customer | Profile, policy purchase, payments, claim submission |

### C.3 Entities (SRS)

1. User
2. Customer
3. InsuranceProduct
4. PolicyPlan
5. Policy
6. PremiumPayment
7. Claim
8. ClaimDocument
9. ClaimStatusHistory

### C.4 Modules (SRS)

| Module ID | Name |
|-----------|------|
| MOD-001 | Authentication and Authorization |
| MOD-002 | User Management |
| MOD-003 | Customer Management |
| MOD-004 | Insurance Product |
| MOD-005 | Policy Plan |
| MOD-006 | Policy |
| MOD-007 | Premium Payment |
| MOD-008 | Claim |
| MOD-009 | Claim Status History |
| MOD-010 | Exception Handling |
| MOD-011 | Logging |
| MOD-012 | Swagger Documentation |

### C.5 Out of Scope (SRS — explicitly excluded)

| ID | Excluded Feature |
|----|----------------|
| OOS-001 | Real payment gateway |
| OOS-002 | Real KYC verification |
| OOS-003 | Email/SMS notification |
| OOS-004 | Actual cloud document storage |
| OOS-005 | AI fraud detection |
| OOS-006 | Frontend application |
| OOS-007 | Microservices architecture |
| OOS-008 | Docker/Kubernetes deployment |
| OOS-009 | Real insurance underwriting engine |
| OOS-010 | Policy renewal automation |
| OOS-011 | Refund processing |
| OOS-012 | Claim settlement bank transfer |

### C.6 Business Rules Summary

- Policy starts as PENDING_PAYMENT → becomes ACTIVE after required premium is paid
- Claims can only be raised on ACTIVE policies
- Claim lifecycle: Submitted → Under Review → Recommended for Approval/Rejection → Approved/Rejected
- Only Admin makes final claim decision
- Agents can only recommend
- Products/plans deactivated, not deleted
- Pagination mandatory for all list APIs (default 10, max 100)

---

## D. Middleware Architecture Requirements (from Prompt)

### D.1 Distribution Partners (External)

- Automobile manufacturers
- Car dealerships
- Railway ticket-booking platforms
- Airline ticket-booking platforms
- Banks (Axis Bank, Union Bank confirmed from handwriting)
- Financial institutions
- Travel booking platforms
- E-commerce companies

### D.2 Insurance Providers

- Max Life Insurance (confirmed from handwriting)
- Axis Max Life
- Care Health Insurance
- Other insurance providers

### D.3 Core Processing Flow

1. Partner authenticates via API
2. Platform validates partner credentials and permissions
3. Partner submits insurance request with customer data + document S3 references
4. Platform validates schema and business rules
5. Platform retrieves documents from partner's S3
6. Platform validates documents
7. Platform queues request via SQS
8. Lambda worker processes: retrieves source documents, validates, transfers to insurer S3
9. Platform updates metadata and status
10. Platform sends notifications via SNS/webhook

### D.4 Key Architectural Distinctions

- Platform is an **integration intermediary**, NOT the insurer
- Platform does NOT do: underwriting, premium calculation, policy approval, claim settlement
- Cross-account AWS document transfer is the core technical differentiator
- Synchronous path: authentication + validation → return 202 Accepted
- Asynchronous path: SQS → Lambda → S3 transfer → SNS notification

---

## E. Conflict and Gap Analysis

### E.1 Conflicts Between SRS and Middleware Architecture

| # | Conflict | Resolution |
|---|---------|-----------|
| C-001 | SRS explicitly excludes "actual cloud document storage" (OOS-004) | The middleware IS the cloud document transfer system — these are two different projects. The SRS describes an insurance admin system; the middleware is a separate integration platform. Treat as separate scopes. |
| C-002 | SRS excludes microservices; prompt says modular monolith | No conflict — modular monolith is consistent with both. Lambda is a separate worker, not a microservice. |
| C-003 | SRS uses JWT for customer login; middleware uses OAuth 2.0 Client Credentials for partner auth | Different authentication mechanisms for different actors — no conflict. Partners use machine-to-machine OAuth; end users use JWT. |
| C-004 | SRS has Admin/Agent/Customer roles; middleware has Partner/Insurer/Admin roles | Different role systems for different subsystems. Both must coexist in the traceability matrix. |

### E.2 Relationship Between SRS and Middleware

**Decision Required from Student/Evaluator:**

The SRS describes an insurance policy administration system (customers, policies, claims).
The middleware describes an integration platform (partners, document transfer, insurers).

**Proposed Resolution (Option A — Recommended):**
- Build the **middleware platform** as the primary deliverable.
- Treat the SRS insurance entities (Policy, Claim, Customer, etc.) as belonging to a **downstream insurer system** (external) that our middleware delivers documents to.
- Document the SRS as a separate module that can be built as a Phase 2 addition or represents the "insurer backend."
- This avoids duplication and matches the verbal description.

**This decision is flagged for explicit approval before SRS modules are implemented.**

### E.3 Gaps / Uncertain Requirements

| # | Gap | Status |
|---|-----|--------|
| G-001 | "CB" partner in handwriting — Canara Bank or Co-operative Bank? | Treated as placeholder "CB_BANK" |
| G-002 | "Axiom Integrity" — product name or company? | Treated as unknown; not implemented |
| G-003 | "CDT" abbreviation in handwriting | Treated as Cross-Document Transfer |
| G-004 | "DFTon" in handwriting | Treated as Document Forward Transfer on Lambda |
| G-005 | Whether SRS admin/agent/customer system should be built | Flagged — awaiting decision |
| G-006 | Pre-signed URL expiry time limits | Default 15 minutes; configurable |
| G-007 | Maximum document file size | Default 50MB; configurable |
| G-008 | Number of supported concurrent partners at launch | No limit specified; architecture supports N partners |
| G-009 | KMS encryption requirement for S3 | Assume SSE-S3 minimum; KMS configurable |
| G-010 | Multi-part upload threshold | Default 100MB threshold |
