---
phase: 16
slug: merge-service-core
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-07
---

# Phase 16 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Controller → DriverMergeService | UUID parameters from HTTP request cross into service layer (Phase 18 scope) | Driver UUIDs (non-sensitive) |
| DriverMergeService → Database | FK reassignment writes to multiple tables in single transaction | Driver records, aliases, lineup/result references |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-16-01 | Tampering | DriverMergeService.merge() | mitigate | Self-merge validation (`sourceId.equals(targetId)` → BusinessRuleException), both drivers validated via `orElseThrow(EntityNotFoundException)`, `@Transactional` ensures atomic rollback | closed |
| T-16-02 | Denial of Service | DriverMergeService.merge() | accept | Admin-only operation (Phase 18 controller in secured profile); no rate limiting needed for internal service method | closed |
| T-16-03 | Information Disclosure | Audit log | accept | `log.info` contains driver IDs and PSN-IDs — acceptable for admin audit trail; no PII beyond gamer tags | closed |
| T-16-04 | Elevation of Privilege | DriverMergeService | mitigate | Service has no access control — Phase 18 controller enforces admin-only via Spring Security (prod/docker). Dev/local intentionally no auth per CLAUDE.md | closed |
| T-16-05 | Repudiation | Merge action | mitigate | Structured `log.info` with source/target IDs, PSN-IDs, per-table counts, and timestamp provides audit trail (MERGE-14, D-10) | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-16-01 | T-16-02 | Merge is admin-only, behind Spring Security in prod/docker. No public API surface. | GSD orchestrator | 2026-04-07 |
| AR-16-02 | T-16-03 | Gamer tags (PSN-IDs) are publicly visible on PlayStation Network. No additional PII exposure. | GSD orchestrator | 2026-04-07 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-07 | 5 | 5 | 0 | GSD secure-phase |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-07
