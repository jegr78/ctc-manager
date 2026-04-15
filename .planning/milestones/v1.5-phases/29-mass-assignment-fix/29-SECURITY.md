---
phase: 29-mass-assignment-fix
asvs_level: 1
threats_total: 3
threats_closed: 3
threats_open: 0
---

# Phase 29 Security Audit — Mass Assignment Fix

**Phase:** 29 — mass-assignment-fix
**Audited:** 2026-04-13
**ASVS Level:** 1
**Threats Closed:** 3/3

---

## Threat Verification

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-29-01 | Tampering | mitigate | CLOSED | `MatchdayController.java:99` — `@Valid @ModelAttribute("form") MatchdayForm form`; no `@ModelAttribute Matchday` remains on save(). `MatchdayForm.java` contains exactly 4 fields (id, label, sortIndex, seasonId); JPA-managed fields absent. `matchday-form.html:8` — `th:object="${form}"`. Service call at `MatchdayController.java:110-111` passes individual primitives. |
| T-29-02 | Tampering | accept | CLOSED | Accepted — see Accepted Risks log below. |
| T-29-03 | Information Disclosure | accept | CLOSED | Accepted — see Accepted Risks log below. |

---

## Accepted Risks

### T-29-02 — Tampering: MatchdayForm.id field allows attacker to set arbitrary UUID

**Rationale:** The `id` field in `MatchdayForm` is used for create-vs-update routing in `matchdayService.saveMatchday()`. The service validates that the referenced entity exists before performing an update. If the UUID does not correspond to an existing matchday, the operation fails at the service layer. This pattern is consistent with all 12 other Form DTOs in the codebase. The application is admin-only; in prod the auth profile requires authentication before any controller is reachable. The risk of an authenticated admin submitting a crafted UUID is low and the impact is bounded (no privilege escalation possible — the operation stays within the matchday domain).

**Accepted by:** Phase 29 threat model  
**Condition for re-evaluation:** If the application gains public-facing endpoints, or if unauthenticated access to admin routes becomes possible.

---

### T-29-03 — Information Disclosure: Validation error response leaks season list

**Rationale:** When a form submission fails validation, the controller re-renders the form and re-fetches the season list via `matchdayService.getAllSeasons()`. The season list contains season names and years only — no credentials, PII, or sensitive business data. All users who can reach the matchday form are admins (enforced by auth in prod profile). There is no meaningful information gain for an attacker who already has admin access.

**Accepted by:** Phase 29 threat model  
**Condition for re-evaluation:** If the season model is extended to contain sensitive fields, or if the application gains roles below admin that can access the matchday form.

---

## Unregistered Threat Flags

None. SUMMARY.md `## Threat Flags` section explicitly states no new security surface was introduced.

---

## Audit Notes

- `MatchdayForm.java` includes an additional `@NotNull` constraint on `seasonId` (not present in the PLAN's DTO specification). This tightens validation beyond what was declared and does not weaken any mitigation.
- The old vulnerable pattern `@ModelAttribute Matchday matchday` and `@RequestParam UUID seasonId` on `save()` are both absent from the refactored controller, confirmed by inspection of `MatchdayController.java`.
- Template contains no residual `${matchday.*}` references; all bindings use `${form.*}` or the separate `${season}` display attribute.
- 863 tests pass; JaCoCo coverage threshold (>= 82%) maintained per SUMMARY.md.
