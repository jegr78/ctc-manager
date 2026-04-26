---
phase: 38
slug: season-content-data-filtering
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-16
---

# Phase 38 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Jsoup (HTML assertion) |
| **Config file** | `pom.xml` (Surefire plugin) |
| **Quick run command** | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~45 seconds (full suite) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest`
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 38-01-01 | 01 | 1 | CONT-01 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*seasonYear*` | ❌ W0 | ⬜ pending |
| 38-01-02 | 01 | 1 | CONT-06 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*testSeason*` | ❌ W0 | ⬜ pending |
| 38-01-03 | 01 | 1 | CONT-07 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*matchMeta*` | ❌ W0 | ⬜ pending |
| 38-01-04 | 01 | 1 | CONT-07 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest#*period*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Rename test season in `SiteGeneratorServiceTest.setUp()` from `"Gen Test ..."` to non-"Test" name (prevents CONT-06 filter from breaking existing tests)
- [ ] Add failing tests for CONT-01 (season year/number in HTML output)
- [ ] Add failing tests for CONT-06 (test season filtered from archive)
- [ ] Add failing tests for CONT-07 (empty match-meta hidden, empty period hidden)

*Existing JUnit 5 + Jsoup infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Season metadata visual appearance | CONT-01 | CSS styling verification | Start dev server, generate site, visually verify `.season-meta` styling |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
