---
phase: 26
slug: restore-fictive-team-logos
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-10
---

# Phase 26 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Dev profile only | Logo files only loaded when `@Profile("dev")` is active | Static PNG resources, no user input |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-26-01 | T (Tampering) | demo/team-logos/*.png | accept | Dev-only profile resources, not served in production. No user-uploaded content. | closed |
| T-26-02 | I (Information Disclosure) | logo files | accept | Fictive team logos contain no sensitive data. Only used in dev/demo profile. | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-26-01 | T-26-01 | Fictive PNG logos are dev-only resources under @Profile("dev"). Not served in production. No user-uploaded content path. | Claude (auto) | 2026-04-10 |
| AR-26-02 | T-26-02 | Logo files contain only fictive team imagery, no sensitive data. Only accessible in dev/demo profile. | Claude (auto) | 2026-04-10 |

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
