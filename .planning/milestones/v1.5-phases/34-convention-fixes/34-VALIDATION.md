---
phase: 34
slug: convention-fixes
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-14
audited: 2026-04-14
---

# Phase 34 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring MockMvc |
| **Config file** | `pom.xml` (surefire + failsafe) |
| **Quick run command** | `./mvnw test -Dtest=PlayoffControllerTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl .`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 34-01-01 | 01 | 1 | CONV-01 | — | @Valid + BindingResult rejects blank name | integration | `./mvnw test -Dtest=PlayoffControllerTest#givenBlankName_whenSavePlayoff_thenReturnsFormViewWithErrors` | ✅ | ✅ green |
| 34-01-02 | 01 | 1 | CONV-01 | — | @Valid + BindingResult rejects missing seasonId | integration | `./mvnw test -Dtest=PlayoffControllerTest#givenMissingSeasonId_whenSavePlayoff_thenReturnsFormViewWithErrors` | ✅ | ✅ green |
| 34-01-03 | 01 | 1 | CONV-01 | — | Valid form redirects with success | integration | `./mvnw test -Dtest=PlayoffControllerTest#givenValidPlayoffForm_whenSavePlayoff_thenRedirectsAndPersists` | ✅ | ✅ green |
| 34-02-01 | 02 | 1 | CONV-04 | — | Zero inline style= on HTML elements | structural | `grep -n 'style=' race-results.html \| grep -v '<style'` | ✅ | ✅ green |
| 34-02-02 | 02 | 1 | CONV-04 | — | Zero .style. in JavaScript | structural | `grep -n '\.style\.' race-results.html` | ✅ | ✅ green |
| 34-02-03 | 02 | 1 | CONV-04 | — | 9 results- CSS classes in admin.css | structural | `grep -c 'results-' admin.css` | ✅ | ✅ green |
| 34-R-01 | — | — | CONV-02 | — | SeasonTeam/RaceSettings have no @ToString (no lazy load risk) | structural | `grep '@ToString' SeasonTeam.java RaceSettings.java` | ✅ | ✅ green (compliant) |
| 34-R-02 | — | — | CONV-03 | — | All UI text and comments in English | research | Research confirmed D-07 | N/A | ✅ compliant |
| 34-R-03 | — | — | CONV-05 | — | Business rule violations log at warn level | research | Research confirmed D-08 | N/A | ✅ compliant |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

*All phase behaviors have automated or structural verification.*

---

## Validation Audit 2026-04-14

| Metric | Count |
|--------|-------|
| Gaps found | 1 (CONV-01 validation-failure tests) |
| Resolved | 1 (2 tests added by nyquist-auditor) |
| Escalated | 0 |
| Manual-Only | 0 |
| Automated COVERED | 6 |
| Research-Compliant | 3 |

Gap: CONV-01 validation-failure tests (givenBlankName, givenMissingSeasonId) were lost after initial TDD execution. Reconstructed by nyquist-auditor agent. All 19 PlayoffControllerTest tests passing.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-04-14
