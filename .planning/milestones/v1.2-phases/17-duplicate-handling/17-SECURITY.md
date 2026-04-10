# SECURITY.md — Phase 17-01: Duplicate Handling (DriverMergeService)

**Generated:** 2026-04-07
**Phase:** 17-duplicate-handling / Plan 01
**ASVS Level:** 1
**Block On:** high
**Auditor:** gsd-secure-phase

---

## Threat Verification

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-17-01 | Tampering | mitigate | CLOSED | `DriverMergeService.java` line 35: `sourceId.equals(targetId)` self-merge guard; lines 40-43: `findById().orElseThrow(EntityNotFoundException)` for both source and target |
| T-17-02 | Information Disclosure | accept | CLOSED | See Accepted Risks below |
| T-17-03 | Denial of Service | accept | CLOSED | See Accepted Risks below |
| T-17-04 | Elevation of Privilege | mitigate | CLOSED | `SecurityConfig.java` line 12: `@Profile({"prod","docker"})`, line 21: `.anyRequest().authenticated()` — all HTTP requests (including future Phase 18 merge endpoint) require authentication; no service in `domain.service` carries its own auth check, consistent with project architecture |

---

## Accepted Risks Log

### T-17-02 — Information Disclosure via audit logs

- **Risk:** `log.info()` calls in `DriverMergeService.merge()` emit PSN-IDs (source/target), season names, and race UUIDs to server-side logs.
- **Rationale:** Logging is scoped to admin-only operations with no PII beyond gaming handles (PSN-IDs). Logs remain server-side. Audit trail is a required operational capability for irreversible merge operations.
- **Residual Risk:** Low. Exposure requires server log access, which implies a higher-privilege compromise.
- **Accepted by:** Architecture (ASVS L1 — no log masking requirement at this level).

### T-17-03 — Denial of Service via large FK entry sets

- **Risk:** A driver with a very large number of `SeasonDriver`, `RaceLineup`, or `RaceResult` entries triggers N+1 duplicate-check queries during merge, potentially causing slow transactions.
- **Rationale:** Driver merge is a rare, manual admin operation. At ASVS L1, no rate limiting or query optimization is required. The application has no SLA for merge throughput.
- **Residual Risk:** Low. Admin-only trigger path; no external-user-facing exposure.
- **Accepted by:** Architecture (ASVS L1 — admin-only rare operation).

---

## Unregistered Flags

**None.** The SUMMARY.md `## Threat Flags` section explicitly states: "No new security-relevant surface introduced." No unregistered flags to log.

---

## Scope

- **Implementation file audited:** `src/main/java/org/ctc/domain/service/DriverMergeService.java`
- **Supporting files checked:** `src/main/java/org/ctc/admin/SecurityConfig.java`
- **Implementation files not modified** (read-only audit).
