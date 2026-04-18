---
phase: 39
slug: entity-cross-linking
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-16
---

# Phase 39 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Service → Template | Pre-computed URLs pass from Java service to Thymeleaf template context | String URL paths (no user input) |
| User browser → Static HTML | Users click `<a>` links in static HTML; all URLs are relative paths | Navigation only (no data submission) |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-39-01 | I (Information Disclosure) | Test data | accept | Test data uses unique suffixes, isolated from production data; no PII | closed |
| T-39-02 | S (Spoofing) | Thymeleaf th:href output | mitigate | All URLs pre-computed in Java via `slugify()`; Thymeleaf auto-escapes `th:href` output preventing XSS | closed |
| T-39-03 | T (Tampering) | URL slug injection | mitigate | `slugify()` strips all non-alphanumeric characters and normalizes to lowercase; no special chars survive into href attributes | closed |
| T-39-04 | I (Information Disclosure) | Driver/team enumeration | accept | Static site is public by design; all team/driver data intentionally visible; no PII beyond PSN-IDs (public gaming identifiers) | closed |
| T-39-05 | D (Denial of Service) | N+1 query in generateTeamProfiles | accept | `findByDriverId()` called per driver per team; acceptable for static site generation (runs once, not per-request); bounded by teams x drivers | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-39-01 | T-39-01 | Test data isolation with unique prefixes, no production data exposure | Claude (automated) | 2026-04-16 |
| AR-39-02 | T-39-04 | Public static site — entity enumeration is by design, no PII | Claude (automated) | 2026-04-16 |
| AR-39-03 | T-39-05 | Static generation runs once per publish, not per-request; N+1 bounded | Claude (automated) | 2026-04-16 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-16 | 5 | 5 | 0 | /gsd-secure-phase (orchestrator) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-16
