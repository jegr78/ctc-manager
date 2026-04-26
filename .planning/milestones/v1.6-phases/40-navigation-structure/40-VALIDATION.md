---
phase: 40
slug: navigation-structure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-16
---

# Phase 40 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test |
| **Config file** | `pom.xml` (Surefire + Failsafe plugins) |
| **Quick run command** | `./mvnw test -Dtest=SiteGeneratorServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest=SiteGeneratorServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 40-01-01 | 01 | 1 | CONT-05 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenStandingsHasSubnav` | ❌ W0 | ⬜ pending |
| 40-01-02 | 01 | 1 | CONT-05 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenCreatesMatchdayIndexPage` | ❌ W0 | ⬜ pending |
| 40-01-03 | 01 | 1 | CONT-05 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenSubnavMatchdaysLinkCorrect` | ❌ W0 | ⬜ pending |
| 40-02-01 | 02 | 1 | UX-02 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenStandingsPage_whenGenerate_thenStandingsNavItemActive` | ❌ W0 | ⬜ pending |
| 40-03-01 | 03 | 1 | UX-03 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenStandingsHasBreadcrumb` | ❌ W0 | ⬜ pending |
| 40-03-02 | 03 | 1 | UX-03 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenSeason_whenGenerate_thenBreadcrumbCurrentNotLink` | ❌ W0 | ⬜ pending |
| 40-03-03 | 03 | 1 | UX-03 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#givenArchivePage_whenGenerate_thenNoBreadcrumb` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `SiteGeneratorServiceTest.java` — 7 new test stubs for CONT-05, UX-02, UX-03
- [ ] No new test files needed — all tests go into existing test class

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Subnav pill styling matches top-nav design | UX-02 | Visual design verification | Generate site, open standings.html in browser, verify subnav pills match top-nav proportions |
| Mobile responsive subnav behavior | UX-02 | Responsive layout check | Open in mobile viewport (375px width), verify subnav wraps or scrolls correctly |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
