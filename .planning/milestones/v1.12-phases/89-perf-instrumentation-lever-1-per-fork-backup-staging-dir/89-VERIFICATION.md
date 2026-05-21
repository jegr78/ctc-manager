---
phase: 89
verified_on: 2026-05-21
status: passed
verifier: gsd-verifier (retroactive — v1.12 carry-forward Phase 92 DOCS-01)
score: 5/5 success-criteria + 8/8 dimensions
overrides_applied: 0
audit_method: retroactive
---

# Phase 89 — PERF Instrumentation & Lever 1 (Per-Fork Backup-Staging-Dir) — Verification Report

**Phase Goal (from `.planning/milestones/v1.12-ROADMAP.md` § Phase 89):**
Land the largest single-delta PERF lever (per-fork `app.backup.staging-dir` enabling
Failsafe `forkCount>1C` on backup ITs) and the instrumentation that drives PERF-03's
targeted consolidation. Per CONTEXT.md D-01 (sequential inline override): three plans,
three waves, inline on `gsd/v1.12-driver-import-and-test-perf`.

**Verified:** 2026-05-21 (retroactive — substance derived from `89-VALIDATION.md` +
`89-{01,02,03}-SUMMARY.md`; no new validation work per Phase 92 CONTEXT D-01).
**Status:** passed
**Method:** retroactive goal-backward — SC-1..SC-5 each cross-referenced against the
existing `89-VALIDATION.md` Per-Task Verification Map (rows 89-01-* through 89-03-*) and
the per-plan SUMMARY.md shipped-evidence sections.
**Re-verification:** Initial retroactive verification — no prior VERIFICATION.md present.

---

## Goal Achievement — Success Criteria

| # | Success Criterion (from ROADMAP.md) | Status | Evidence |
|---|-------------------------------------|--------|----------|
| SC-1 | `app.backup.staging-dir` resolves to a per-fork path keyed on `${surefire.forkNumber}` (or equivalent); a dedicated IT asserts no two concurrent forks ever observe the same staging dir. | VERIFIED | Plan 89-01 ship: `BackupStagingDirPerForkIT.java` + `BackupStagingCleanupRaceIT.java` created; per-fork resolution via `${surefire.forkNumber}` placeholder on `app.backup.staging-dir` + `app.backup.import-backups-dir` + `app.upload-dir`. Cross-reference: `89-01-SUMMARY.md` § Self-Check + `89-VALIDATION.md` rows 89-01-01..04. |
| SC-2 | `BackupStagingCleanup` startup listener respects the per-fork path; existing backup IT suite (7 ITs) passes at elevated Failsafe `forkCount` without flakes (3-seed verification 1234/5678/9999). | VERIFIED | Plan 89-01 enabled Failsafe `default-it` `forkCount=2`; inherited Spring-Boot Failsafe execution unbound. 3-seed verification (1234/5678/9999) green on all 7 backup ITs. `ImportLockedPostRejectorIT` deadline bumped to accommodate elevated fork concurrency. Cross-reference: `89-01-SUMMARY.md` § Self-Check + 89-FLAKE-DIAGNOSTIC.md replan trail (Task 4 D-19 lock-timeout investigation). |
| SC-3 | `ContextLoadCountListener` dumps per-context cache-key hashes (`MergedContextConfiguration.hashCode()` or equivalent) into existing `target/test-perf/context-loads-{PID}.txt` markers alongside the load count. | VERIFIED | Plan 89-02 ship: `ContextCacheKeyFingerprintListener` (`TestExecutionListener`) + marker-file format migration; PID-keyed output extended with cache-key hash column. Cross-reference: `89-02-SUMMARY.md` § Self-Check + `89-VALIDATION.md` rows 89-02-01..02. |
| SC-4 | A bash aggregator sample script is added to `docs/test-performance.md § PERF-02 Forensics`, grouping loads by hash and listing top-5 fragmentation clusters. | VERIFIED | `scripts/test-perf/aggregate-fingerprints.sh` + `docs/test-performance.md § PERF-02 Forensics` section populated with top-5 cluster table. Cross-reference: `89-02-SUMMARY.md` § Deliverables. |
| SC-5 | After Phase-89 measurement (3 local runs, idle protocol), `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` is populated with delta vs Phase-86 baseline (median 10:24 local / 23:00 CI); JaCoCo coverage stays ≥ 88.88 %. | VERIFIED | Plan 89-03 ship: 3 idle-protocol runs landed median **09:19** wallclock (−10.4 % vs Phase-86 10:24 local). `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` populated; § v1.12 Forward Path Lever-1 marked DONE; `README.md § Test Performance` pointer added. JaCoCo line coverage measured ≥ 88.88 % at the Phase-89 close gate. Cross-reference: `89-03-SUMMARY.md` § Self-Check. |

**Score:** 5/5 Success Criteria verified.

---

## Observable Truths (must-haves)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `BackupStagingDirPerForkIT.java` exists and asserts per-fork dir uniqueness | VERIFIED | File present at `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java`; introduced Plan 89-01. |
| 2 | `BackupStagingCleanupRaceIT.java` exists and verifies cleanup race-safety | VERIFIED | File present in same package; introduced Plan 89-01. |
| 3 | Failsafe `default-it` `forkCount=2` configured in pom.xml | VERIFIED | `pom.xml` Failsafe configuration includes `forkCount=2` per Plan 89-01 patch; inherited Spring-Boot Failsafe execution unbound. |
| 4 | `ContextCacheKeyFingerprintListener` registered via test-scope `spring.factories` | VERIFIED | Listener class introduced Plan 89-02; registered as `TestExecutionListener` (test-scope only — not in production JAR per the same convention as the Phase 86 `ContextLoadCountListener`). |
| 5 | `scripts/test-perf/aggregate-fingerprints.sh` exists and groups loads by hash | VERIFIED | Script introduced Plan 89-02; documented in `docs/test-performance.md § PERF-02 Forensics`. |
| 6 | `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` populated with measured delta | VERIFIED | Section added Plan 89-03; documents local median 09:19 (−10.4 % vs Phase-86 baseline). |
| 7 | `89-VALIDATION.md` carries `nyquist_compliant: true` + `status: complete` | VERIFIED | Phase 91 retroactive sweep table confirms (per `91-VALIDATION.md`): `\| 89 \| status: complete, nyquist_compliant: true \| ✅`. |
| 8 | Branch invariant — all Phase 89 commits on `gsd/v1.12-driver-import-and-test-perf` | VERIFIED | `git log --oneline --grep="89-" master..gsd/v1.13-discord-integration` plus the v1.12 milestone archive structurally locate every Phase-89 commit on the v1.12 branch (now squash-merged into master). |

**Score:** 8/8 truths verified.

---

## Per-Dimension Verdict Table

| # | Dimension | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Goal achievement (SC-1..SC-5) | PASS | All 5 SCs concretely satisfied — see Success Criteria table |
| 2 | Requirements coverage (PERF-01, PERF-02) | PASS | Both PERF items will flip `[ ]` → `[x]` in `v1.12-REQUIREMENTS.md` via Plan 92-04 Task 2 (BOOK-01) on this audit's ship |
| 3 | CONTEXT decision compliance (D-01, D-04R, D-18, D-19) | PASS | Key decisions verified — sequential-inline D-01, FLAKE-DIAGNOSTIC D-04R fallback removal, D-18 import-backups-dir entry tracked, D-19 lock-timeout Task 4 investigation completed |
| 4 | `docs/test-performance.md` deliverable completeness | PASS | New § PERF-02 Forensics + § Post-Optimization Wallclock (Wave 4) sections populated; aggregator script linked |
| 5 | Wave-1 / Wave-2 / Wave-3 sequential structure honored | PASS | 3 plans executed in waves with `[[wave-pause]]` between each per CONTEXT D-01 + `[[inline-sequential-execution]]` |
| 6 | Branch invariant — no Flyway, prod code touched only for application*.yml | PASS | Phase 89 production-touch limited to `application*.yml` per-fork variable expansion; no Flyway migrations; `BackupStagingCleanup` listener respects the per-fork placeholder |
| 7 | Coverage gate maintained (≥82% JaCoCo line) | PASS | JaCoCo coverage ≥ 88.88 % at Phase-89 close (cross-reference: STATE.md baseline) |
| 8 | Wallclock delta materially captured (not silent skip) | PASS | Plan 89-03 records 09:19 median (−10.4 % vs 10:24 baseline) — quantified, not narrative |

**Score:** 8/8 dimensions PASS.

---

## CONTEXT.md Decision Compliance (selected key decisions)

| Decision | Compliance | Evidence |
|----------|------------|----------|
| D-01 sequential-inline override of ROADMAP "parallel-runnable" wording | PASS | All 3 plans executed inline with `[[wave-pause]]` per `89-{01,02,03}-SUMMARY.md` |
| D-04R fallback removed post-FLAKE-DIAGNOSTIC | PASS | `89-FLAKE-DIAGNOSTIC.md` documents the diagnosis; revision 2 of `89-VALIDATION.md` (approved 2026-05-19) reflects D-04R fallback removal |
| D-18 `app.backup.import-backups-dir` entry tracked alongside primary staging-dir | PASS | Plan 89-01 patch covers both directories with the same `${surefire.forkNumber}` placeholder |
| D-19 lock-timeout investigation (Task 4) | PASS | `ImportLockedPostRejectorIT` deadline bump documented in `89-01-SUMMARY.md` § Task 4 + 89-FLAKE-DIAGNOSTIC.md root-cause section |

---

## Verification Outcome

Phase 89 passes all 5 Success Criteria and all 8 Nyquist dimensions. PERF-01 + PERF-02
deliverables shipped per the Plan 89-01/02/03 progression. The 09:19 local wallclock
median demonstrates a quantified −10.4 % improvement vs the Phase-86 baseline. No
overrides required. Substance in this report is derived from the existing
`89-VALIDATION.md` + per-plan SUMMARY.md files (file-shape compliance per Phase 92
CONTEXT D-01 — no new validation work).

---

_Verified: 2026-05-21_
_Verifier: gsd-verifier (retroactive — v1.12 carry-forward Phase 92 DOCS-01)_
