# Postman — Module 2 Partner Authentication

This collection exercises the complete Module 2 authentication surface against a
running instance of the middleware:

| Folder | What it proves |
|--------|----------------|
| 1. Smoke | Health endpoint is public |
| 2. Credential Provisioning | Admin can register a partner and issue OAuth 2.0 credentials; secrets are never exposed by list APIs |
| 3. OAuth 2.0 Token Flow | JSON and form-encoded token requests, token introspection, refresh, non-cacheable token responses, and access/refresh token type checks |
| 4. JWT Enforcement | Valid token accepted; missing/malformed/wrong-secret paths rejected with RFC 6750 `WWW-Authenticate` challenges; refresh tokens cannot be used as access tokens |
| 5. Scope Enforcement | A `write`-only token cannot call a `read` endpoint (403 + `insufficient_scope` challenge) |
| 6. Rate Limiting | Per-client limit returns 429 with `Retry-After` |
| 7. Secret Rotation | New secret works, old secret works during the 24-hour grace window |
| 8. Revocation | Deactivated client -> 401 until reactivated; deactivated partner -> 403 for both new and already-issued tokens until reactivated |

The collection currently contains **33 requests / 37 assertions**; each folder
asserts status codes, response fields and the security headers relevant to that
flow.

## Run locally

1. Start the application (default `h2` profile):

   ```bash
   mvn spring-boot:run
   ```

   or:

   ```bash
   java -jar target/integration-middleware-1.0.0-SNAPSHOT.jar
   ```

2. Run the collection with the Postman CLI:

   ```bash
   npx postman-cli collection run \
     postman/Module-2-Partner-Authentication.postman_collection.json \
     -e postman/Insurance-Integration-Local.postman_environment.json
   ```

   If the API is on a different host/port, override the environment value:

   ```bash
   npx postman-cli collection run \
     postman/Module-2-Partner-Authentication.postman_collection.json \
     -e postman/Insurance-Integration-Local.postman_environment.json \
     --env-var baseUrl=http://localhost:9090
   ```

## What the collection does to data

The collection creates a uniquely named partner (`POSTMAN_BANK_<timestamp>`),
three API clients (read+write, write-only, rate-limited), and rotates one secret.
It deactivates and reactivates the write-only client and partner to exercise
both revocation and restoration. It also checks that a partner's already-issued
access token is blocked while the partner is inactive and works again after
reactivation. The collection leaves the write-only client and partner active.
Against the default in-memory H2 database all data disappears when the
application stops.

## Notes

- Admin endpoints (`/api/v1/admin/**`) are intentionally unauthenticated in the
  academic deployment. Only partner endpoints (`/api/v1/partner/**`) require a
  JWT in Module 2.
- `client_secret` is returned only when a client is created or rotated. The
  collection stores it in Postman collection variables for the duration of the
  run; it is never written to disk by the application.
