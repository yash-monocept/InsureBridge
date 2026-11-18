# 08 — Frontend Architecture (Updated for Modules 3 & 4)

**Status:** COMPLETE (redesigned)

## Stack

- Vanilla JavaScript (ES modules, IIFE), plain CSS (CSS custom properties), HTML5
- Served from `src/main/resources/static/` (index.html, console.js, console.css)
- No build step, no framework, no new UI library — reuses the existing stack

## Architecture

Single-page application with a fixed sidebar (navigation), topbar (health, theme toggle), and main content area showing one `.page` section at a time. Page switching is client-side via `setPage(name)` which toggles the `.active` class.

### Page-to-backend mapping

| Page | Backend endpoints |
|------|-------------------|
| Overview | `GET /api/v1/health`, `/api/v1/admin/partners`, `/api/v1/admin/insurers` |
| Partners | `GET/POST/PUT /api/v1/admin/partners`, mappings |
| Insurers | `GET/POST /api/v1/admin/insurers` |
| API clients | `GET/POST/PUT /api/v1/admin/partners/{id}/api-clients` |
| Authentication | `POST /api/v1/auth/token`, `GET /api/v1/auth/verify`, `GET /api/v1/partner/me` |
| **Insurance submissions** | `GET/POST /api/v1/partner/insurance/submissions`, `GET .../{id}`, `GET .../{id}/validation` |
| **Schema management** | `GET/POST/PUT /api/v1/admin/schemas`, rules endpoints |

## Request helper

`request(path, options)` wrapper around `fetch`:
- Sets `Content-Type: application/json` for JSON bodies
- Attaches `Authorization: Bearer <token>` when `options.auth` and a token exist
- Parses JSON response; throws on non-ok with `status` and `body`
- Used by all page loaders

## UI state

`state` object holds pagination (partnerPage, insurerPage, submissionPage, schemaPage), access/refresh tokens, and the currently open submission/schema IDs.

## New pages (Modules 3 & 4)

### Insurance Submissions (`page-submissions`)
- Table of submissions: external reference, partner, insurer, product, received, status (pill), errors, docs, actions
- Toolbar: search, status filter, product filter, insurer filter (populated from `/api/v1/admin/insurers`), refresh
- Pagination footer with record count
- Click "Details" → navigates to submission-detail page

### Submission Details (`page-submission-detail`)
- Sections: overview, partner, insurer, customer, application data (JSON), document metadata (table, URLs excluded), validation result (summary + errors table)
- "Back to submissions" button
- Loading panel shown while fetching

### Schema Management (`page-schemas`)
- Table of schemas: name, product type, version, scope, status (pill), actions
- Toolbar: search, product filter, active filter, refresh
- Pagination
- Click "Details" → shows schema-detail panel (field rules table, document rules table)
- "Create schema" form at bottom → POST to `/api/v1/admin/schemas`

## States handled

- **Loading:** skeleton/spinner text while fetching
- **Empty:** "No submissions match these filters." / "Select a schema to view field rules."
- **Error:** toast notification + inline table error row
- **Success:** toast confirmation after create

## Partner isolation

Partner-facing submission endpoints return only records for the authenticated partner (JWT `PartnerPrincipal.partnerId`). The UI never requests another partner's data.

## Files

- `src/main/resources/static/index.html` — markup (existing pages preserved + new submissions/schemas/detail sections)
- `src/main/resources/static/console.js` — application logic (existing + `loadSubmissions`, `loadSubmissionDetail`, `loadSchemas`, `loadSchemaDetail`, `createSchema`, event wiring)
- `src/main/resources/static/console.css` — styles (existing + new pill colours, field grids, sub-sections)
