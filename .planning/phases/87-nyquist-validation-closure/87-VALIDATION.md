---
phase: 87
slug: nyquist-validation-closure
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-18
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

> Filled by the planner — each PLAN.md task maps to one of these rows. Map is finalized when plan-checker passes.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 87-01-XX | 01 (Phase 71 restore + audit + approve) | 1 | VAL-01 (anchor), VAL-02, VAL-03 | — | N/A (doc + retroactive test fill) | Manual + IT (if gap) | `grep '^status: approved' .planning/milestones/v1.10-phases/71-*/71-VALIDATION.md` | ❌ W0 | ⬜ pending |
| 87-02-XX | 02 (Phase 72 restore + audit + approve) | 1 | VAL-01, VAL-03 | — | N/A | Manual + IT (if gap) | `grep '^status: approved' .planning/milestones/v1.10-phases/72-*/72-VALIDATION.md` | ❌ W0 | ⬜ pending |
| 87-03-XX | 03 (Phase 73 restore + audit + approve) | 1 | VAL-01, VAL-03 | — | N/A | Manual + IT (if gap) | `grep '^status: approved' .planning/milestones/v1.10-phases/73-*/73-VALIDATION.md` | ❌ W0 | ⬜ pending |
| 87-04-XX | 04 (Phase 74 restore + audit + approve) | 1 | VAL-01, VAL-03 | T-74-04 (auditor may surface) | If gap: BackupUploadExceptionHandler advice scope behavior | Manual + IT (if gap) | `grep '^status: approved' .planning/milestones/v1.10-phases/74-*/74-VALIDATION.md` | ❌ W0 | ⬜ pending |
| 87-05-XX | 05 (Phase 75 restore + audit + approve) | 1 | VAL-01, VAL-03 | — | If gap: post-commit listener idempotency | Manual + IT (if gap) | `grep '^status: approved' .planning/milestones/v1.10-phases/75-*/75-VALIDATION.md` | ❌ W0 | ⬜ pending |
| 87-06-XX | 06 (Phase 76 restore + audit + approve) | 1 | VAL-01, VAL-03 | T-76-06 SECU-06 | If gap: ImportLockedWriteRejector whitelist-on-equals edge | Manual + IT (high gap likelihood) | `grep '^status: approved' .planning/milestones/v1.10-phases/76-*/76-VALIDATION.md` | ❌ W0 | ⬜ pending |
| 87-07-XX | 07 (Phase 78 restore + audit + approve) | 1 | VAL-02, VAL-03 | — | If gap: Dockerfile pin guard regression | Manual + IT (high gap likelihood) | `grep '^status: approved' .planning/milestones/v1.10-phases/78-*/78-VALIDATION.md` | ❌ W0 | ⬜ pending |
| 87-08-XX | 08 (Phase 79 restore + audit + approve + Phase-87 closer) | 1 | VAL-01, VAL-03, VAL-04 | — | N/A (closer is doc-only: STATE.md row delete + REQUIREMENTS.md flips + v1.10-MILESTONE-AUDIT scoreboard) | Manual (grep + composite) | `grep -c "Nyquist \*-VALIDATION.md" .planning/STATE.md` == 0; `grep -E "compliant_phases: 9" .planning/milestones/v1.10-MILESTONE-AUDIT.md` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity:** Each per-phase plan group lands at minimum 2 commits (restore + approve) and at most 4 (restore + test + fix + approve). Auditor invocation between restore and approve is the implicit gap-discovery step; if it returns `## GAPS FILLED` the test commit is present, otherwise it's skipped. No three consecutive plans rely on manual-only without an automated touch — phase 76, 78, and 71 are predicted to land gap-fill tests per Phase 87 RESEARCH.md gap profile.

---

## Wave 0 Requirements

> Wave 0 = test scaffolding that must exist before the implementation tasks run. For Phase 87, Wave 0 is empty — existing test infrastructure covers everything.

- [x] **None** — Existing JUnit 5 + Surefire `forkCount=2C` + Failsafe `forkCount=1C` + `@Tag` routing from Phase 79 covers all gap-fill needs.
- [x] **No new framework install** — `pom.xml` already has JUnit 5 / Mockito / Spring Boot Test / Playwright / JaCoCo.
- [x] **No new IT base class** — auditor reuses `@SpringBootTest @ActiveProfiles("dev") @Transactional @Tag("integration")` shells already in `org.ctc.backup.*` test packages.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 8 v1.10 VALIDATION.md files reach `status: approved` + `nyquist_compliant: true` | VAL-01, VAL-02 | Frontmatter assertions = file-existence + grep; not a runtime test | `for n in 71 72 73 74 75 76 78 79; do grep -E '^status: approved' .planning/milestones/v1.10-phases/${n}-*/*-VALIDATION.md; done \| wc -l` → returns `8` |
| Gap-coverage tests committed atomically per phase | VAL-03 | Git log inspection, not a runtime test | `git log --oneline gsd/v1.11-tooling-and-cleanup -- src/test/java/ \| grep -E "test\(87-[0-9]{2}\):" \| wc -l` → returns ≥ 1 (lower bound; actual depends on gap fill) |
| `/gsd:validate-phase` ran for each of the 8 phases | VAL-03 | Workflow side effect, evidenced by commits + per-phase audit notes in VALIDATION.md | Per-phase VALIDATION.md "## Validation Audit YYYY-MM-DD" section with CI run-id citation present in all 8 files |
| STATE.md Deferred Items has no Nyquist row at v1.11 close | VAL-04 | File grep, not unit test | `grep -c "Nyquist \*-VALIDATION.md" .planning/STATE.md` → returns `0` |
| `.planning/milestones/v1.10-MILESTONE-AUDIT.md` Nyquist scoreboard updated | VAL-01..VAL-04 anchor | YAML diff; not a runtime test | `grep -E "compliant_phases: 9" .planning/milestones/v1.10-MILESTONE-AUDIT.md` returns 1 line; `partial_phases: 0` and `missing_phases: 0` likewise present |
| Phase-86 wallclock baseline preserved (D-06 5 % guard) | (D-06 internal) | Manual CI `workflow_dispatch` + log parse; cannot be a JUnit assertion | Trigger CI run via `workflow_dispatch` on the milestone branch, capture `Total time:` from the run log, verify ≤ 24:09 (Phase-86 23:00 × 1.05). If exceeded → run tidy-up cycle per CONTEXT D-06 |
| JaCoCo line coverage ≥ 82 % (CLAUDE.md hard gate) | — | pom.xml gate runs inside `./mvnw verify` | `./mvnw verify -Pe2e` BUILD SUCCESS; `target/site/jacoco/jacoco.csv` line coverage ≥ 82 % |

---

## Validation Sign-Off

- [ ] All 8 v1.10 VALIDATION.md files contain `status: approved` + `nyquist_compliant: true` in frontmatter
- [ ] Each per-phase plan landed atomically (restore + optional test + optional fix + approve commits inside one logical group)
- [ ] Sampling continuity: no 3 consecutive plans without an automated touch (mitigated by predicted gap fills in 71 / 76 / 78)
- [ ] STATE.md "Deferred Items" no longer contains a Nyquist row (VAL-04)
- [ ] REQUIREMENTS.md VAL-01..VAL-04 flipped from `[ ]` to `[x]`
- [ ] `.planning/milestones/v1.10-MILESTONE-AUDIT.md` Nyquist scoreboard updated to `compliant_phases: 9 / partial_phases: 0 / missing_phases: 0 / overall: compliant`
- [ ] No watch-mode flags
- [ ] Feedback latency < 30 s (quick) / < 9 min (full)
- [ ] CI wallclock ≤ 24:09 on the milestone branch (Phase-86 23:00 × 1.05 D-06 guard)
- [ ] JaCoCo line coverage ≥ 82 % held after all gap-fill tests committed
- [ ] `nyquist_compliant: true` set in this VALIDATION.md frontmatter when planner finalizes the per-task map

**Approval:** pending
