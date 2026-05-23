---
phase: 96
plan: 96-01
slug: provisional-scores-graphic-multipart-post
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-23
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
| 96-01-01 | `ProvisionalScoresGraphicServiceTest` | unit (Mockito) | untagged | `./mvnw test -Dtest=ProvisionalScoresGraphicServiceTest` | ❌ W0 | ⬜ pending |
| 96-01-02 | (extends 96-01-01 + manual visual review against `.screenshots/96-01/provisional-reference.png`) | manual + unit re-run | untagged | `./mvnw test -Dtest=ProvisionalScoresGraphicServiceTest` | ❌ W0 | ⬜ pending |
| 96-01-03 | `DiscordPostServiceProvisionalScoresIT` | IT (WireMock) | integration | `./mvnw verify -Dit.test=DiscordPostServiceProvisionalScoresIT` | ❌ W0 | ⬜ pending |
| 96-01-03 | `MatchControllerProvisionalPostIT` | IT (MockMvc + WireMock) | integration | `./mvnw verify -Dit.test=MatchControllerProvisionalPostIT` | ❌ W0 | ⬜ pending |
| 96-01-03 | `MatchDetailProvisionalButtonsE2ETest` | E2E (Playwright Desktop + Mobile) | e2e | `./mvnw verify -Pe2e -Dit.test=MatchDetailProvisionalButtonsE2ETest` | ❌ W0 | ⬜ pending |

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

- [ ] `ProvisionalScoresGraphicServiceTest` created in Task 96-01-01
- [ ] `DiscordPostServiceProvisionalScoresIT` created in Task 96-01-03
- [ ] `MatchControllerProvisionalPostIT` created in Task 96-01-03
- [ ] `MatchDetailProvisionalButtonsE2ETest` created in Task 96-01-03
- [ ] Operator drops `.screenshots/96-01/provisional-reference.png` BEFORE Task 96-01-02 begins (pre-flight in PLAN.md)

---

## Manual-Only Verifications

| Behavior | Why Manual | Test Instructions |
|----------|------------|-------------------|
| Provisional PNG visual fidelity vs Google-Sheets reference | Pixel layout requires human eye against `.screenshots/96-01/provisional-reference.png` | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` → `/admin/matches/{id}` → click "Post Provisional Scores" → operator reviews PNG; iterate via `playwright-cli` per [[feedback-graphic-design-iteration]] |

---

## Plan 96-01 Sign-Off

- [ ] All 11 task behaviors verified across the 4 test classes
- [ ] `./mvnw verify -Pe2e -Dit.test=MatchDetailProvisionalButtonsE2ETest` exits 0
- [ ] JaCoCo line coverage ≥ 88.88% maintained
- [ ] BackupSchemaGuardTest stays green
- [ ] D-96-GRX-1c assertion-pin in place (no `?thread_id=` for PROVISIONAL_SCORES)
- [ ] Operator confirms visual fidelity against `.screenshots/96-01/provisional-reference.png`
- [ ] Wave-pause: PR rolling-summary row added for Plan 96-01
- [ ] `nyquist_compliant: true` flipped in frontmatter

**Approval:** pending
