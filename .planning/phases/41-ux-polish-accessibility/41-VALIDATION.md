---
phase: 41
slug: ux-polish-accessibility
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-16
---

# Phase 41 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Jsoup (HTML assertion) |
| **Config file** | `pom.xml` (Surefire plugin) |
| **Quick run command** | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds (full suite) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 41-01-01 | 01 | 0 | UX-01 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*skipLink*` | ❌ W0 | ⬜ pending |
| 41-01-02 | 01 | 0 | UX-04 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*winner*` | ❌ W0 | ⬜ pending |
| 41-01-03 | 01 | 0 | UX-06 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*footer*` | ❌ W0 | ⬜ pending |
| 41-01-04 | 01 | 0 | UX-07 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*ariaLabel*` | ❌ W0 | ⬜ pending |
| 41-02-01 | 02 | 1 | UX-04 | — | N/A | unit | `./mvnw test -Dtest=SiteGeneratorServiceTest#*winner*` | ❌ W0 | ⬜ pending |
| 41-02-02 | 02 | 1 | UX-05 | — | N/A | manual | grep `.table-wrap::after` in style.css | ✅ | ⬜ pending |
| 41-02-03 | 02 | 1 | UX-08 | — | N/A | manual | grep `transition` in style.css | ✅ | ⬜ pending |
| 41-02-04 | 02 | 1 | UX-09 | — | N/A | manual | grep `cursor: pointer` in style.css | ✅ | ⬜ pending |
| 41-02-05 | 02 | 1 | QUAL-01 | — | N/A | manual | grep `style=` in driver-profile.html | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `SiteGeneratorServiceTest` — 4 new test methods for UX-01, UX-04, UX-06, UX-07
- [ ] Existing infrastructure covers remaining requirements (CSS-only: UX-05, UX-08, UX-09, QUAL-01)

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Scroll indicator visible on mobile | UX-05 | CSS pseudo-element not testable via Jsoup | Grep `table-wrap::after` in style.css, verify gradient declaration |
| Hover transitions 150-300ms | UX-08 | Timing is CSS-only, not unit testable | Grep `transition` declarations, verify 0.2s (200ms) |
| cursor:pointer on clickables | UX-09 | CSS property not testable via Jsoup | Grep `cursor: pointer` rule in style.css |
| No inline styles in driver-profile | QUAL-01 | Absence check | Grep `style=` in driver-profile.html, expect 0 matches |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
