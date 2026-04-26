---
phase: 37
slug: critical-link-fixes
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-16
---

# Phase 37 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test |
| **Config file** | pom.xml (Surefire + Failsafe) |
| **Quick run command** | `./mvnw test -pl . -Dtest=SiteGeneratorServiceTest` |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60 seconds (quick), ~120 seconds (full) |

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
| 37-01-01 | 01 | 1 | — | — | N/A | setup | `grep -n "uploadDir" SiteGeneratorService.java` | ✅ | ⬜ pending |
| 37-01-02 | 01 | 1 | LINK-01..04 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest` (expect 4 failures) | ❌ W0 | ⬜ pending |
| 37-02-01 | 02 | 2 | LINK-02, LINK-03 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest` | ❌ W0 | ⬜ pending |
| 37-02-02 | 02 | 2 | LINK-01 | — | N/A | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest` | ❌ W0 | ⬜ pending |
| 37-02-03 | 02 | 2 | LINK-04 | T-path-traversal | Logo copy follows path-traversal guard pattern | integration | `./mvnw test -Dtest=SiteGeneratorServiceTest` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/sitegen/SiteGeneratorServiceTest.java` — 4 new test methods:
  - `givenSeason_whenGenerate_thenArchiveContainsCorrectSeasonSlug` — REQ LINK-01
  - `givenActiveSeason_whenGenerate_thenNavDriverRankingLinksToActiveSeason` — REQ LINK-02
  - `givenActiveSeason_whenGenerate_thenRootPagesHaveNoAbsolutePaths` — REQ LINK-03
  - `givenTeamWithLogo_whenGenerate_thenLogoIsCopiedAndLinkedRelatively` — REQ LINK-04
- [ ] LINK-04 test requires a team logo fixture on disk; use `@TempDir` for uploadDir and write a small PNG (or 1-byte file) to simulate

*Existing test infrastructure (JUnit 5, Spring Boot Test, Jsoup, @TempDir) covers all framework requirements.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 60s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
