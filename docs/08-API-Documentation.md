# 08 — API Documentation

**Version:** 2.1 (Module 2 — hardened)  
**Base URL:** `http://localhost:8080`  
**Interactive docs:** `http://localhost:8080/swagger-ui.html`  
**OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

All successful admin responses use the envelope:

```json
{
  "success": true,
  "message": "…",
  "data": { },
  "timestamp": "2026-11-13T10:00:00Z"
}
```

All errors use:

```json
{
  "timestamp": "2026-11-13T10:00:00Z",
  "status": 401,
  "errorType": "AUTHENTICATION_FAILED",
  "message": "Invalid client credentials",
  "path": "/api/v1/auth/token"
}
```

---

## 1. Authentication

Module 2 uses OAuth 2.0 Client Credentials. Obtain `client_id` and
`client_secret` from a platform administrator, exchange them for a JWT, and send
the token as `Authorization: Bearer <token>`.

### 1.1 Obtain a token

`POST /api/v1/auth/token`

**JSON request**

```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "client_credentials",
    "client_id": "cli_...",
    "client_secret": "..."
  }'
```

**Form-encoded request (OAuth standard)**

```bash
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=cli_...&client_secret=..."
```

**200 response**

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

Token responses send `Cache-Control: no-store` and `Pragma: no-cache`
(RFC 6749 §5.1).

**Errors:** 400 malformed request, 401 invalid credentials, 403 inactive partner.

### 1.2 Refresh tokens

`POST /api/v1/auth/refresh`

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "eyJ..."}'
```

Returns the same shape as the token endpoint. Access tokens cannot be used as
refresh tokens.

### 1.3 Verify a token

`GET /api/v1/auth/verify`

```bash
curl http://localhost:8080/api/v1/auth/verify \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "partner_id": "0e6f...",
  "partner_code": "AXIS_BANK",
  "client_id": "cli_...",
  "scopes": ["read"],
  "issued_at": "2026-11-13T09:00:00Z",
  "expires_at": "2026-11-13T10:00:00Z"
}
```

---

## 2. Admin — API Client Management

These endpoints provision partner credentials. In the academic deployment they
are unauthenticated (admin identity is out of Module 2 scope). Protect them
before production.

### 2.1 Create credentials

`POST /api/v1/admin/partners/{partnerId}/api-clients`

```bash
curl -X POST http://localhost:8080/api/v1/admin/partners/{partnerId}/api-clients \
  -H "Content-Type: application/json" \
  -d '{"scopes":["read","write"],"tokenExpirySeconds":3600,"rateLimitPerMinute":100}'
```

**201 response** — `client_secret` is returned **once**:

```json
{
  "success": true,
  "message": "API client created",
  "data": {
    "id": "…",
    "partnerId": "…",
    "clientId": "cli_...",
    "client_secret": "…",
    "scopes": ["read", "write"],
    "tokenExpirySeconds": 3600,
    "rateLimitPerMinute": 100,
    "active": true,
    "createdAt": "2026-11-13T10:00:00Z"
  }
}
```

Defaults: scopes `["read"]`, expiry 3600s, rate limit 100/min.
Allowed scopes: `read`, `write`, `admin`.

### 2.2 List credentials

`GET /api/v1/admin/partners/{partnerId}/api-clients`

Returns metadata only — never the secret or its hash.

### 2.3 Rotate a secret

`PUT /api/v1/admin/partners/{partnerId}/api-clients/{clientId}/rotate`

```json
{
  "success": true,
  "message": "Client secret rotated",
  "data": {
    "clientId": "cli_...",
    "client_secret": "…",
    "lastRotatedAt": "2026-11-13T10:00:00Z",
    "previousSecretValidUntil": "2026-11-14T10:00:00Z"
  }
}
```

Both the previous and new secrets authenticate until
`previousSecretValidUntil`; afterwards only the new secret works.

### 2.4 Activate / deactivate

- `PUT /api/v1/admin/partners/{partnerId}/api-clients/{clientId}/activate`
- `PUT /api/v1/admin/partners/{partnerId}/api-clients/{clientId}/deactivate`

A deactivated client receives 401 on the token endpoint.

---

## 3. Partner API

All partner endpoints require `Authorization: Bearer <access token>`.

### 3.1 Current partner profile

`GET /api/v1/partner/me`

Required scope: `read` (or `admin`).

```bash
curl http://localhost:8080/api/v1/partner/me \
  -H "Authorization: Bearer eyJ..."
```

```json
{
  "success": true,
  "message": "Partner profile retrieved",
  "data": {
    "partnerId": "…",
    "partnerCode": "AXIS_BANK",
    "partnerName": "Axis Bank",
    "partnerType": "BANK",
    "contactEmail": "integrations@axisbank.example",
    "active": true,
    "clientId": "cli_...",
    "scopes": ["read", "write"],
    "tokenExpiresAt": "2026-11-13T11:00:00Z"
  }
}
```

### 3.2 Authentication errors

| Status | `errorType` | `WWW-Authenticate` | Cause |
|--------|-------------|--------------------|-------|
| 401 | `AUTHENTICATION_REQUIRED` | `Bearer realm="partner-api"` | Missing Bearer token |
| 401 | `INVALID_TOKEN` | `Bearer … error="invalid_token"` | Malformed, wrong signature, or refresh token used |
| 401 | `TOKEN_EXPIRED` | `Bearer … error="invalid_token"` | Expired access token |
| 403 | `PARTNER_NOT_ACTIVE` | — | Partner deactivated |
| 403 | `FORBIDDEN` | `Bearer … error="insufficient_scope"` | Missing required scope |
| 429 | `RATE_LIMIT_EXCEEDED` | — | Per-client rate limit exceeded |

Rate-limited responses include `Retry-After`. Allowed partner responses include
`X-RateLimit-Limit` and `X-RateLimit-Remaining`.

---

## 4. Partner Management (Module 1)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/admin/partners` | Register partner |
| PUT | `/api/v1/admin/partners/{id}` | Update partner |
| PUT | `/api/v1/admin/partners/{id}/activate` | Activate partner |
| PUT | `/api/v1/admin/partners/{id}/deactivate` | Deactivate partner |
| GET | `/api/v1/admin/partners/{id}` | Get by ID |
| GET | `/api/v1/admin/partners/code/{code}` | Get by code |
| GET | `/api/v1/admin/partners` | Paginated list |
| POST | `/api/v1/admin/partners/{id}/mappings` | Map partner to insurer |
| GET | `/api/v1/admin/partners/{id}/mappings` | List mappings |
| DELETE | `/api/v1/admin/partners/mappings/{mappingId}` | Deactivate mapping |
| POST | `/api/v1/admin/insurers` | Register insurer |
| GET | `/api/v1/admin/insurers/{id}` | Get insurer |
| GET | `/api/v1/admin/insurers/code/{code}` | Get insurer by code |
| GET | `/api/v1/admin/insurers` | Paginated list |
| PUT | `/api/v1/admin/insurers/{id}/activate` | Activate insurer |
| PUT | `/api/v1/admin/insurers/{id}/deactivate` | Deactivate insurer |

Paginated responses use `records`, `currentPage`, `pageSize`, `totalRecords`,
`totalPages`, `lastPage`, `sortField`, `sortDirection`.

---

## 5. Health

`GET /api/v1/health` — public liveness probe.

`GET /actuator/health` — actuator health (public path in this build).

---

## 6. Postman

Import:

- `postman/Module-2-Partner-Authentication.postman_collection.json`
- `postman/Insurance-Integration-Local.postman_environment.json`

Run:

```bash
npx postman-cli collection run \
  postman/Module-2-Partner-Authentication.postman_collection.json \
  -e postman/Insurance-Integration-Local.postman_environment.json
```

The collection provisions a unique partner and clients, then verifies token
issuance, JWT enforcement, scopes, rotation, rate limiting and revocation.
It contains 33 requests and also asserts RFC 6750 challenge headers and
non-cacheable token responses.

---

## 7. Versioning and Compatibility

- Partner-facing APIs are versioned under `/api/v1`.
- Access tokens are bearer tokens; treat them as opaque in client code.
- Tokens are stateless; clients should refresh proactively before `expires_in`
  elapses and retry once on a 401 caused by expiry.
- Error envelopes are stable and machine-readable via `errorType`.
