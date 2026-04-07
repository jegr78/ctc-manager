# Security Audit — Phase 18: merge-ui

**Audited:** 2026-04-07
**ASVS Level:** 1
**Plans Covered:** 18-01, 18-02
**Auditor:** gsd-security-auditor

---

## Threat Verification

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-18-01 | Tampering | mitigate | CLOSED | `@Transactional(readOnly = true)` at `DriverMergeService.java:47`; unit test `givenValidPreview_whenPreviewMerge_thenNoMutationsExecuted` at `DriverMergeServiceTest.java:663` verifies `never().save()` and `never().delete()` on all 5 repositories |
| T-18-02 | Information Disclosure | accept | CLOSED | Accepted: preview counts are non-sensitive admin data; `SecurityConfig.java` enforces authentication on all requests under `prod`/`docker` profiles |
| T-18-03 | Spoofing | mitigate | CLOSED | `SecurityConfig.java:12-13` activates on `prod`/`docker` profiles; Spring Security's default CSRF protection is active for all POST endpoints; `httpBasic` auth gate covers `/{id}/merge` |
| T-18-04 | Tampering | mitigate | CLOSED | `@RequestParam UUID targetId` at `DriverController.java:121,132` — Spring's `UUIDEditor` rejects malformed values with 400 before reaching controller body; `DriverMergeService.java:50-51` and `102-103` enforce self-merge guard for both `previewMerge()` and `merge()` |
| T-18-05 | Tampering | mitigate | CLOSED | `DriverMergeService.java:55-58` (previewMerge) and `107-110` (merge) throw `EntityNotFoundException` for missing source/target; `DriverController.java:144` catches `EntityNotFoundException | BusinessRuleException` and redirects to driver list with error flash |
| T-18-06 | Denial of Service | accept | CLOSED | Accepted: admin-only single-user tool; `SecurityConfig.java` restricts access to authenticated users in prod/docker |

---

## Accepted Risks Log

| Threat ID | Category | Rationale |
|-----------|----------|-----------|
| T-18-02 | Information Disclosure | MergePreview exposes only FK reference counts (integers). No PII or sensitive data. Admin-only access enforced by SecurityConfig on prod/docker. Risk accepted for admin tooling at ASVS L1. |
| T-18-06 | Denial of Service | Merge endpoint is admin-only, single-user application. No rate limiting required at ASVS L1 for this threat surface. Risk accepted. |

---

## Unregistered Flags

None — 18-01-SUMMARY.md and 18-02-SUMMARY.md both report no threat flags.

---

## Summary

**Threats Closed:** 6/6
**Open Threats:** 0
**Unregistered Flags:** 0
