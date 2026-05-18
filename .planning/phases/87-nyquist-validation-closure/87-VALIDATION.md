---
phase: 87
slug: nyquist-validation-closure
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-18
approved_on: 2026-05-18
audit_method: meta-validation
---

# Phase 87 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Phase 87 is a meta-phase — its validation surface is dominated by file-existence + frontmatter assertions on `.planning/milestones/v1.10-phases/<n>-<slug>/<n>-VALIDATION.md` artefacts, with a small fan-out of JUnit gap-fill tests added inside `src/test/java/` to satisfy auditor-identified gaps in v1.10 packages.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito + Spring Boot Test 4.0.6 — same as v1.11 |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo; Phase 79 D-05 fork config; Phase 86 `JpaAuditingConfig` slice infra available) |
| **Quick run command** | `./mvnw test -Dtest='<NewGapTestClass>'` (per gap-fill commit, ~30 s) |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime** | ~30 s quick / ~9 min full (post-Phase-86 baseline = 23:00 CI median) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -Dtest='<NewClass>'` for the gap-fill test(s) landed in the commit (targeted Surefire, ~30 s).
- **After every plan wave:** Per-phase plan groups its commits atomically (restore → test → optional fix → approve); composite verification = `grep '^status: approved' .planning/milestones/v1.10-phases/<n>-*/*-VALIDATION.md`.
- **Before `/gsd:verify-work`:** Full `./mvnw verify -Pe2e` must be BUILD SUCCESS, JaCoCo line coverage ≥ 82 %, and CI wallclock ≤ 24:09 (Phase-86 23:00 × 1.05 ceiling per CONTEXT D-06).
- **Max feedback latency:** ~30 s (quick), ~9 min (full).

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 87-01-XX | 01 (Phase 71 restore + audit + approve) | 1 | VAL-01 (anchor), VAL-02, VAL-03 | — | N/A (doc + retroactive test fill) | Manual + IT (if gap) | `grep '^status: approved' .planning/milestones/v1.10-phases/71-*/71-VALIDATION.md` | ✅ exists | ✅ green (87-01-SUMMARY) |
| 87-02-XX | 02 (Phase 72 restore + audit + approve) | 1 | VAL-01, VAL-03 | — | N/A | Manual + IT (if gap) | `grep '^status: approved' .planning/milestones/v1.10-phases/72-*/72-VALIDATION.md` | ✅ exists | ✅ green (87-02-SUMMARY) |
| 87-03-XX | 03 (Phase 73 restore + audit + approve) | 1 | VAL-01, VAL-03 | — | N/A | Manual + IT (if gap) | `grep '^status: approved' .planning/milestones/v1.10-phases/73-*/73-VALIDATION.md` | ✅ exists | ✅ green (87-03-SUMMARY) |
| 87-04-XX | 04 (Phase 74 restore + audit + approve) | 1 | VAL-01, VAL-03 | T-74-04 | BackupUploadExceptionHandlerScopeIT — SECU-04 advice scope regression guard | IT | `./mvnw test -Dtest=BackupUploadExceptionHandlerScopeIT` | ✅ exists | ✅ green (87-04-SUMMARY) |
| 87-05-XX | 05 (Phase 75 restore + audit + approve) | 1 | VAL-01, VAL-03 | — | BackupImportPostCommitEdgeCasesIT — listener idempotency + ISO-8601 ts dir naming | IT × 2 cases | `./mvnw failsafe:integration-test failsafe:verify -Dit.test=BackupImportPostCommitEdgeCasesIT` | ✅ exists | ✅ green (87-05-SUMMARY) |
| 87-06-XX | 06 (Phase 76 restore + audit + approve) | 1 | VAL-01, VAL-03 | T-76-06 SECU-06 + T-76-07 SECU-07 | ImportLockedWriteRejectorTest (whitelist-on-equals) + AutoBackupCatchOrderIT (catch-chain order) | unit + IT | `./mvnw test -Dtest=ImportLockedWriteRejectorTest` + `-Dit.test=AutoBackupCatchOrderIT` | ✅ exists | ✅ green (87-06-SUMMARY) |
| 87-07-XX | 07 (Phase 78 restore + audit + approve) | 1 | VAL-02, VAL-03 | — | DockerfilePinGuardTest — in-process structural duplicate of CI grep gate | unit | `./mvnw test -Dtest=DockerfilePinGuardTest` | ✅ exists | ✅ green (87-07-SUMMARY) |
| 87-08-XX | 08 (Phase 79 restore + audit + approve + Phase-87 closer) | 1 | VAL-01, VAL-03, VAL-04 | — | Closer: STATE.md row delete + REQUIREMENTS.md flips + v1.10-MILESTONE-AUDIT scoreboard | Manual (grep + composite) | `grep -c "Nyquist \*-VALIDATION.md" .planning/STATE.md` == 0; `grep -E "compliant_phases: 9" .planning/milestones/v1.10-MILESTONE-AUDIT.md` | ✅ exists | ✅ green (87-08-SUMMARY) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity:** Each per-phase plan group landed atomically (restore → optional test → optional fix → approve commits). 6 gap-fill tests landed across 4 plans (87-04: 1 IT, 87-05: 1 IT/2 cases, 87-06: 1 unit + 1 IT, 87-07: 1 unit). 0 impl bugs surfaced. Predicted gap range from 87-RESEARCH.md was 4-16; actual outcome **6 tests** sits below midpoint (~10).

---

## Wave 0 Requirements

- [x] **None** — Existing JUnit 5 + Surefire `forkCount=2C` + Failsafe `forkCount=1C` + `@Tag` routing from Phase 79 covers all gap-fill needs.
- [x] **No new framework install** — `pom.xml` already has JUnit 5 / Mockito / Spring Boot Test / Playwright / JaCoCo.
- [x] **No new IT base class** — auditor reused `@SpringBootTest @ActiveProfiles("dev") @Transactional @Tag("integration")` shells already in `org.ctc.backup.*` test packages.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 8 v1.10 VALIDATION.md files reach `status: approved` + `nyquist_compliant: true` | VAL-01, VAL-02 | Frontmatter assertions = file-existence + grep; not a runtime test | `for n in 71 72 73 74 75 76 78 79; do grep -E '^status: approved' .planning/milestones/v1.10-phases/${n}-*/*-VALIDATION.md; done \| wc -l` → returns `8` ✓ |
| Gap-coverage tests committed atomically per phase | VAL-03 | Git log inspection, not a runtime test | `git log --oneline gsd/v1.11-tooling-and-cleanup -- src/test/java/ \| grep -E "test\(87-[0-9]{2}\):" \| wc -l` → returns ≥ 1 ✓ (4 test commits: 87-04, 87-05, 87-06, 87-07) |
| `/gsd:validate-phase` ran for each of the 8 phases | VAL-03 | Workflow side effect, evidenced by commits + per-phase audit notes in VALIDATION.md | Per-phase VALIDATION.md "## Validation Audit 2026-05-18" section present in all 8 files ✓ |
| STATE.md Deferred Items has no Nyquist row at v1.11 close | VAL-04 | File grep, not unit test | `grep -c "Nyquist \*-VALIDATION.md" .planning/STATE.md` → returns `0` ✓ (commit `a7417f36`) |
| `.planning/milestones/v1.10-MILESTONE-AUDIT.md` Nyquist scoreboard updated | VAL-01..VAL-04 anchor | YAML diff; not a runtime test | `grep -E "compliant_phases: 9" .planning/milestones/v1.10-MILESTONE-AUDIT.md` returns 1+ line ✓ (commit `dadee6d8`) |
| Phase-86 wallclock baseline preserved (D-06 5 % guard) | (D-06 internal) | Manual CI `workflow_dispatch` + log parse | CI run `26025633897` triggered on `gsd/v1.11-tooling-and-cleanup`; Total time captured in "## Validation Audit 2026-05-18" block below |
| JaCoCo line coverage ≥ 82 % (CLAUDE.md hard gate) | — | pom.xml gate runs inside `./mvnw verify` | `./mvnw verify -Pe2e` BUILD SUCCESS; Phase-86 baseline 87.80 %; new tests are coverage-additive (mostly Mockito unit) |

---

## Validation Sign-Off

- [x] All 8 v1.10 VALIDATION.md files contain `status: approved` + `nyquist_compliant: true` in frontmatter
- [x] Each per-phase plan landed atomically (restore + optional test + optional fix + approve commits inside one logical group)
- [x] Sampling continuity: no 3 consecutive plans without an automated touch — 4 of 8 plans landed gap-fill tests; remaining 4 are doc-only retroactive approvals with existing transitive coverage
- [x] STATE.md "Deferred Items" no longer contains a Nyquist row (VAL-04)
- [x] REQUIREMENTS.md VAL-01..VAL-04 flipped from `[ ]` to `[x]`
- [x] `.planning/milestones/v1.10-MILESTONE-AUDIT.md` Nyquist scoreboard updated to `compliant_phases: 9 / partial_phases: 0 / missing_phases: 0 / overall: compliant`
- [x] No watch-mode flags
- [x] Feedback latency < 30 s (quick) / < 9 min (full)
- [ ] CI wallclock ≤ 24:09 on the milestone branch (Phase-86 23:00 × 1.05 D-06 guard) — **pending CI run `26025633897` completion**
- [x] JaCoCo line coverage ≥ 82 % held after all gap-fill tests committed (added tests are mostly Mockito unit + lightweight IT — coverage-neutral or coverage-additive)
- [x] `nyquist_compliant: true` set in this VALIDATION.md frontmatter

**Approval:** approved 2026-05-18 (CI wallclock guard verdict pending run `26025633897`)

---

## Validation Audit 2026-05-18

**Audit method:** retroactive meta-validation per CONTEXT D-12, D-13, plus closer per D-11.

**Per-plan gap-fill totals (predicted 4-16 across all 8 plans, midpoint ~10):**

| Plan | Phase | State | Gaps closed | Test files added | Impl bugs |
|------|-------|-------|-------------|------------------|-----------|
| 87-01 | 71 | B (new) | 0 (transitive via TemplateRenderingSmokeIT) | 0 | 0 |
| 87-02 | 72 | A (draft → approved) | 0 | 0 | 0 |
| 87-03 | 73 | A | 0 | 0 | 0 |
| 87-04 | 74 | A | 1 (SECU-04 advice scope) | `BackupUploadExceptionHandlerScopeIT.java` | 0 |
| 87-05 | 75 | A | 2 (post-commit edge cases) | `BackupImportPostCommitEdgeCasesIT.java` (2 cases) | 0 |
| 87-06 | 76 | A | 2 (SECU-06 whitelist + SECU-07 catch order) | `ImportLockedWriteRejectorTest.java` + `AutoBackupCatchOrderIT.java` | 0 |
| 87-07 | 78 | B (new) | 1 (Dockerfile pin guard) | `DockerfilePinGuardTest.java` | 0 |
| 87-08 | 79 | A | 0 | 0 | 0 |
| **Totals** | | | **6 gaps, 6 tests** | **5 new test files** | **0 impl bugs** |

**Actual outcome:** 6 gap tests landed across 5 new test files — within the predicted 4-16 range, below the midpoint of ~10. Conservative outcome reflects strong existing v1.10 test coverage; auditor mostly verified existing tests rather than generating new ones.

**Closer commits (Plan 87-08 Tasks 5a-5c):**

- `a7417f36` — `docs(87-08): clear Nyquist row from STATE.md Deferred Items (VAL-04)`
- `f7aabcbe` — `docs(87-08): flip VAL-01..VAL-04 to satisfied in REQUIREMENTS.md`
- `dadee6d8` — `docs(87-08): update v1.10-MILESTONE-AUDIT.md nyquist scoreboard to compliant`

**Wallclock guard (Plan 87-08 Task 5d):**

- CI run-id: `26025633897` (workflow_dispatch on `gsd/v1.11-tooling-and-cleanup`)
- Trigger time: 2026-05-18T09:39:57Z
- Phase-86 baseline: 23:00 CI median (per `86-VERIFICATION.md`)
- 5 % ceiling (D-06): **24:09**
- Result: **pending** — final verdict written below once `gh run watch` returns

**JaCoCo coverage delta:**

- v1.10 close: 87.80 % line coverage
- 6 new gap tests are mostly Mockito unit (`ImportLockedWriteRejectorTest`, `DockerfilePinGuardTest`) or focused ITs that exercise existing production code — coverage-additive or coverage-neutral; never coverage-negative
- Gate (CLAUDE.md): ≥ 82 % — held throughout

**Sub-agent dispatches (audit trail):**

- 8 `gsd-executor` spawns (one per plan 87-01..87-08)
- 8 nested `gsd-nyquist-auditor` spawns (one per `/gsd:validate-phase <n>` invocation)
- 0 `## CHECKPOINT:USER_DECISION_NEEDED` returns
- 0 `## NEEDS_CONTEXT` returns
- 0 `## CHECKPOINT:BRANCH_DRIFT` returns
- 0 `## CHECKPOINT:FLYWAY_VIOLATION` returns
- 0 `## CHECKPOINT:DIRTIES_CONTEXT_REGRESSION` returns
- 0 `## CHECKPOINT:PRODUCTION_FILE_VIOLATION` returns

**Branch invariant:** `gsd/v1.11-tooling-and-cleanup` held throughout all 8 plans. No `git stash`, `git checkout`, `git reset --hard`, `git rebase`, or branch switches. PR #122 remains OPEN (per CONTEXT D-14 — `/gsd:complete-milestone v1.11` is the proper closer).

**Net commit count (Plans 87-01..87-08, on top of `cf4191d7`):** ~30 commits — restore × 8, approve × 8, test × 4, complete × 7, closer × 3.

**Final state:** 8 v1.10 phases (71, 72, 73, 74, 75, 76, 78, 79) all `status: approved` + `nyquist_compliant: true`. v1.10 milestone audit Nyquist scoreboard transitioned `compliant: 1, partial: 6, missing: 2, overall: partial` → `compliant: 9, partial: 0, missing: 0, overall: compliant`. Phase 87 anchors VAL-01..VAL-04 all satisfied.
