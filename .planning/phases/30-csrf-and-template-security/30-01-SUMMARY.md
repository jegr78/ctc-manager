---
phase: 30-csrf-and-template-security
plan: "01"
subsystem: security/frontend
tags: [csrf, ajax, thymeleaf, security, javascript]
dependency_graph:
  requires: []
  provides: [csrf-fetch-wrapper, csrf-meta-tags]
  affects: [import.html, template-editors.html, layout.html]
tech_stack:
  added: []
  patterns: [csrf-meta-tag-pattern, csrfFetch-wrapper-pattern]
key_files:
  created: []
  modified:
    - src/main/resources/templates/admin/layout.html
    - src/main/resources/templates/admin/import.html
    - src/main/resources/templates/admin/template-editors.html
decisions:
  - "csrfFetch() wrapper uses Object.assign to merge CSRF header without overwriting Content-Type"
  - "th:if guard prevents meta tags from rendering in dev/local (CSRF disabled)"
  - "GET requests remain as plain fetch() — CSRF protection only needed for state-changing POSTs"
metrics:
  duration: "~8 minutes"
  completed: "2026-04-13"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 3
requirements: [SECU-03]
---

# Phase 30 Plan 01: CSRF Token Transport Layer Summary

CSRF meta tags + csrfFetch() JavaScript wrapper added to admin layout; both AJAX POST call sites updated to use the wrapper, enabling Spring Security CSRF validation in prod/docker without breaking dev/local open profiles.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add CSRF meta tags and csrfFetch() wrapper to layout.html | ba2f8ca | layout.html |
| 2 | Replace fetch() with csrfFetch() in AJAX POST call sites | c7f32c6 | import.html, template-editors.html |

## What Was Built

**layout.html** now includes:
- Conditional `<th:block th:if="${_csrf != null}">` with `<meta name="_csrf">` and `<meta name="_csrf_header">` tags — rendered only in prod/docker where CSRF is enabled by Spring Security
- `csrfFetch(url, options)` inline `<script>` function in `<head>` that reads the meta tags at call-time and merges the `X-CSRF-TOKEN` header via `Object.assign({}, options.headers, ...)` — preserving existing `Content-Type` headers
- Graceful fallback: when meta tags are absent or have empty content (dev/local), the function passes through to plain `fetch(url, options)` unchanged

**import.html** (line 239): `fetch('/admin/matchdays/create-inline', {` → `csrfFetch('/admin/matchdays/create-inline', {`

**template-editors.html** (line 747): `await fetch('/admin/tools/template-editors/...`, {` → `await csrfFetch('/admin/tools/template-editors/...', {`

Both call sites preserve all existing options (method, headers, body) unchanged.

## Verification

```
grep 'csrfFetch' src/main/resources/templates/admin/layout.html  → function definition + meta tags
grep 'csrfFetch' src/main/resources/templates/admin/import.html  → 1 occurrence
grep 'csrfFetch' src/main/resources/templates/admin/template-editors.html → 1 occurrence
./mvnw verify → BUILD SUCCESS
```

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None beyond what was addressed in the threat model. CSRF token transport layer fully implemented for both identified AJAX POST endpoints.

## Self-Check: PASSED

- `src/main/resources/templates/admin/layout.html` — modified, CSRF meta tags and csrfFetch() present
- `src/main/resources/templates/admin/import.html` — modified, csrfFetch() used for POST
- `src/main/resources/templates/admin/template-editors.html` — modified, csrfFetch() used for POST
- Commit ba2f8ca — Task 1 confirmed in git log
- Commit c7f32c6 — Task 2 confirmed in git log
- `./mvnw verify` — BUILD SUCCESS, all tests pass
