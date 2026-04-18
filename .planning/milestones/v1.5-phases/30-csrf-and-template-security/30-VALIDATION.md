---
phase: 30
slug: csrf-and-template-security
status: validated
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-13
audited: 2026-04-14
---

# Phase 30 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring MockMvc + Spring Security Test |
| **Config file** | pom.xml (Surefire/Failsafe configuration) |
| **Quick run command** | `./mvnw test -pl . -Dtest=TemplateEditorControllerTest,TemplatePreviewServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=TemplateEditorControllerTest,TemplatePreviewServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 30-01-01 | 01 | 1 | SECU-03 | T-30-01 | csrfFetch() adds CSRF header when meta tags present | JS manual verification | N/A — JS function; covered by integration smoke | N/A | ✅ manual-only |
| 30-01-02 | 01 | 1 | SECU-03 | T-30-01 | csrfFetch() skips CSRF header when meta tags absent/empty | JS manual verification | N/A | N/A | ✅ manual-only |
| 30-01-03 | 01 | 1 | SECU-04 | T-30-02 | save() rejects malicious template with flash error | Integration | `./mvnw test -Dtest=TemplateEditorControllerTest#givenMaliciousTemplate_whenSave_thenRedirectsWithError` | ✅ | ✅ green |
| 30-01-04 | 01 | 1 | SECU-04 | — | save() accepts safe template (unchanged happy path) | Integration | `./mvnw test -Dtest=TemplateEditorControllerTest#givenTemplateContent_whenSaveTeamCardTemplate_thenRedirectsWithSuccess` | ✅ | ✅ green |
| 30-01-05 | 01 | 1 | SECU-04 | T-30-03 | translateY() in CSS does NOT trigger security rejection | Unit | `./mvnw test -Dtest=TemplatePreviewServiceTest$TemplateSecurity#givenCssTransformFunction_whenValidate_thenAcceptsTemplate` | ✅ | ✅ green |
| 30-01-06 | 01 | 1 | SECU-04 | T-30-03 | T( inside ${...} still triggers rejection | Unit | `./mvnw test -Dtest=TemplatePreviewServiceTest$TemplateSecurity#givenSpringElTypeAccess_whenValidate_thenRejectsTemplate` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `TemplateEditorControllerTest` — `givenMaliciousTemplate_whenSave_thenRedirectsWithError` (added in Plan 02, commit 7dbe82a)
- [x] `TemplatePreviewServiceTest` — `givenCssTransformFunction_whenValidate_thenAcceptsTemplate` (added in Plan 02, commit 7dbe82a)

*All Wave 0 tests implemented and passing.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| csrfFetch() adds CSRF header on AJAX POST | SECU-03 | JS function; no Java-level test possible for header injection logic | Start dev server with prod profile, open Network tab, submit AJAX POST, verify X-CSRF-TOKEN header present |
| csrfFetch() graceful fallback in dev/local | SECU-03 | Thymeleaf renders meta tags conditionally per profile | Start dev server with dev profile, verify meta tags absent, verify AJAX POST succeeds without CSRF header |

---

## Validation Audit 2026-04-14

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Manual-Only | 2 (JS-only behaviors) |
| Automated COVERED | 4 |

All Wave 0 tests were implemented during phase execution (Plan 02). No new gaps detected.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** validated 2026-04-14
