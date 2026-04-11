---
phase: 27
slug: restore-matchday-result-pipeline
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-10
---

# Phase 27 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Dev profile only | TestDataService.seed() only runs under @Profile("dev") | Seed data creation (matchdays, races, results, scoring) |
| Fictive data only | All teams, drivers, PSN IDs are fictive | No real user data crosses any boundary |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-27-01 | Tampering | TestDataService.seed() | mitigate | @Profile("dev") on line 47 ensures seed data never runs in production. Verified via grep. | closed |
| T-27-02 | Information Disclosure | Fictive driver PSN IDs | accept | All PSN IDs use {TEAM}_Driver0N pattern, no real user data. Dev-only profile. | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-27-01 | T-27-02 | Fictive PSN IDs ({TEAM}_Driver0N pattern) contain no real user data. Only accessible under dev profile. | Claude (auto) | 2026-04-10 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-10 | 2 | 2 | 0 | gsd-secure-phase |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-10
