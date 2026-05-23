---
phase: 96
plan: 96-01
slug: provisional-scores-graphic-multipart-post
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-23
completed: 2026-05-23
---

# Plan 96-01 — Validation Strategy (GRAFX-01)

> Per-plan validation contract specializing `96-VALIDATION.md` for Plan 96-01.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + WireMock + Playwright |
| **Quick run command** | `./mvnw test -Dtest=ProvisionalScoresGraphicServiceTest` |
| **Wave run command** | `./mvnw verify -Dit.test='DiscordPostServiceProvisionalScoresIT,MatchControllerProvisionalPostIT'` |
| **Plan-close command** | `./mvnw verify -Pe2e -Dit.test=MatchDetailProvisionalButtonsE2ETest` |
| **Estimated runtime** | Quick < 30 s · Wave < 16 m · Plan-close < 18 m |

---

## Sampling Rate

- **After every task commit:** Run the Mockito-only unit test for the touched file (feedback < 30 s).
- **After Task 96-01-03 (final task):** Run `./mvnw verify` (Surefire + Failsafe + JaCoCo, no Playwright) for full IT coverage (< 16 m).
- **Before plan close (wave-pause):** Run `./mvnw verify -Pe2e -Dit.test=MatchDetailProvisionalButtonsE2ETest` for Playwright Desktop + Mobile sweep (< 18 m).
- **Max feedback latency:** 30 s for task-local quick run; 16 m for wave-level verify.

---

## Per-Task Verification Map

| Task | Test Class | Test Type | Tag | Automated Command | File Exists | Status |
|------|------------|-----------|-----|-------------------|-------------|--------|
| 96-01-01 | `ProvisionalScoresGraphicServiceTest` | unit (Mockito) | untagged | `./mvnw test -Dtest=ProvisionalScoresGraphicServiceTest` | ✅ | ✅ 7/7 green |
| 96-01-02 | (extends 96-01-01 + operator visual review at wave-pause) | manual + unit re-run | untagged | `./mvnw test -Dtest=ProvisionalScoresGraphicServiceTest` | ✅ | ✅ 7/7 green (manual visual review pending operator wave-pause) |
| 96-01-03 | `DiscordPostServiceProvisionalScoresIT` | IT (WireMock) | integration | `./mvnw verify -Dit.test=DiscordPostServiceProvisionalScoresIT` | ✅ | ✅ 8/8 green |
| 96-01-03 | `MatchControllerProvisionalPostIT` | IT (MockMvc + WireMock) | integration | `./mvnw verify -Dit.test=MatchControllerProvisionalPostIT` | ✅ | ✅ 2/2 green |
| 96-01-03 | `MatchDetailProvisionalButtonsE2ETest` | E2E (Playwright Desktop + Mobile) | e2e | `./mvnw verify -Pe2e -Dit.test=MatchDetailProvisionalButtonsE2ETest` | ✅ | ✅ 4/4 green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Validation Dimensions (Nyquist)

| Dimension | Coverage Method | Tests / Artifact |
|-----------|-----------------|------------------|
| **Code — service logic** | JaCoCo line + branch coverage ≥ 88.88% on `ProvisionalScoresGraphicService` | `ProvisionalScoresGraphicServiceTest` (5 behaviors: empty-results guard, null-team guards, byte[] non-empty, context vars set, DiscordPostType enum constant) |
| **Code — orchestration** | WireMock-stubbed multipart Discord POST/PATCH | `DiscordPostServiceProvisionalScoresIT` (5 behaviors: filter-by-race-results, N-attachment bundle, PATCH re-edit, no-data BusinessRuleException, NO `?thread_id=` URL param per D-96-GRX-1c) |
| **Code — controller** | MockMvc + WireMock + flash assertion | `MatchControllerProvisionalPostIT` (happy + pre-flight branches + flash text exact-match) |
| **Visual — Provisional PNG layout** | Iterative `playwright-cli` loop against `.screenshots/96-01/provisional-reference.png` per [[feedback-graphic-design-iteration]] + [[feedback-graphic-pixel-positioning]] | Manual operator review at task-96-01-02 close; visual-regression pixel-hash test is DEFERRED to Phase 98 per D-96-01 |
| **Mobile-viewport** | Playwright Mobile sweep at 375 px on Match-Detail | `MatchDetailProvisionalButtonsE2ETest` Mobile variant |
| **Backup wire-contract** | No new entity in Plan 96-01 — `BackupSchemaGuardTest` stays green (EXPORT_ORDER=25, SCHEMA_VERSION=2) | `BackupSchemaGuardTest` (no change required) |
| **Static analysis — SpotBugs** | `./mvnw verify` enforces 0 Medium+HIGH findings; new `ProvisionalScoresGraphicService` reuses `@SuppressFBWarnings` pattern from `MatchResultsGraphicService` | gate-step on `verify` |
| **Static analysis — CodeQL** | PR-gate workflow at `.github/workflows/codeql.yml`; no new SSRF suppressions expected | `gh run watch` after PR push |
| **D-96-GRX-1c regression-fence** | WireMock assertion: NO `thread_id=` URL param in any PROVISIONAL_SCORES request | `DiscordPostServiceProvisionalScoresIT.noThreadIdEverAppended` (single explicit assertion) |

---

## Wave 0 Requirements (Plan 96-01)

- [x] `ProvisionalScoresGraphicServiceTest` created in Task 96-01-01 (7 tests)
- [x] `DiscordPostServiceProvisionalScoresIT` created in Task 96-01-03 (8 tests)
- [x] `MatchControllerProvisionalPostIT` created in Task 96-01-03 (2 tests)
- [x] `MatchDetailProvisionalButtonsE2ETest` created in Task 96-01-03 (4 tests)
- [x] Operator wave-pause review of `.screenshots/96-01/` PNGs is the documented next step — reference PNG was not dropped pre-execute, so Task 96-01-02 produced the baseline layout per the `<pre_flight>` contract.

---

## Manual-Only Verifications

| Behavior | Why Manual | Test Instructions |
|----------|------------|-------------------|
| Provisional PNG visual fidelity vs Google-Sheets reference | Pixel layout requires human eye against `.screenshots/96-01/provisional-reference.png` | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` → `/admin/matches/{id}` → click "Post Provisional Scores" → operator reviews PNG; iterate via `playwright-cli` per [[feedback-graphic-design-iteration]] |

---

## Plan 96-01 Sign-Off

- [x] All 21 task behaviors verified across the 4 test classes (7 + 8 + 2 + 4 = 21 individual test methods; all green)
- [x] `./mvnw clean verify -Pe2e` exits 0 — 1580 + 391 + 78 = 2049 tests green
- [x] JaCoCo line coverage gate met (`All coverage checks have been met`)
- [x] BackupSchemaGuardTest stays green (no schema entities added in Plan 96-01)
- [x] D-96-GRX-1c assertion-pin in place — `DiscordPostServiceProvisionalScoresIT.noThreadIdEverAppended`
- [ ] Operator confirms visual fidelity against `.screenshots/96-01/provisional-reference.png` (pending wave-pause review)
- [ ] Wave-pause: PR rolling-summary row added for Plan 96-01
- [x] `nyquist_compliant: true` flipped in frontmatter

**Approval:** automated gates green; awaiting operator wave-pause visual review of provisional PNG layout before Plan 96-02 starts.
