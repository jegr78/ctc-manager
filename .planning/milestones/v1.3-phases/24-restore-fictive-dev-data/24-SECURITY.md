---
phase: 24
slug: restore-fictive-dev-data
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-10
---

# Phase 24 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Dev profile only | Seed data only runs in dev/test profiles, never production | Fictive team/driver names, no real PII |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-24-01 | I (Information Disclosure) | TestDataService seed data | accept | Fictive data by design — no real PII. Dev profile only (`@Profile("dev")`), not deployed to production. | closed |
| T-24-02 | T (Tampering) | TeamCardService Playwright execution | accept | TeamCardService already exists and is tested. Wrapped in try/catch — failure is non-fatal (log.warn). | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-24-01 | T-24-01 | Fictive data only, no real PII exposed. `@Profile("dev")` prevents prod instantiation. | Claude (orchestrator) | 2026-04-10 |
| AR-24-02 | T-24-02 | Existing service with established error handling. Non-fatal on failure. | Claude (orchestrator) | 2026-04-10 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-10 | 2 | 2 | 0 | Claude (orchestrator) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-10
