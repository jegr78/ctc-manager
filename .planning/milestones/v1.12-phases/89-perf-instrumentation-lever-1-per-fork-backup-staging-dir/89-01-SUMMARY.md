---
phase: 89-perf-instrumentation-lever-1-per-fork-backup-staging-dir
plan: 01
slug: perf-01-per-fork-staging-refactor
status: complete
completed: 2026-05-19
requirements:
  - PERF-01
---

# Plan 89-01 — PERF-01: per-fork backup staging refactor

## Objective recap

Replace the singleton `app.backup.staging-dir` (`data/${profile}/backup-staging`) and `app.backup.import-backups-dir` paths with per-fork variants injected via Surefire/Failsafe `<systemPropertyVariables>`; elevate Failsafe `default-it` to `forkCount=2 reuseForks=true`; prove the mechanism with two new assertion ITs; fix the D-19 `ImportLockedPostRejectorIT` lock-acquisition-timeout regression; empirically validate cross-fork-collision absence with a 3-seed Failsafe verification on the entire `org.ctc.backup.**` IT suite.

## Goal-backward verification (must_haves)

- ✅ Surefire and Failsafe `default-it` JVMs receive `app.backup.staging-dir=data/<profile>/backup-staging-fork-<N>` resolved with the actual fork number — verified via failsafe-reports XML `<property name="app.backup.staging-dir" value="data/${spring.profiles.active:dev}/backup-staging-fork-1"/>` (Spring then resolves the inner placeholder at runtime to `data/dev/backup-staging-fork-1`).
- ✅ Both plugins also receive `app.backup.import-backups-dir=…-fork-<N>` (D-18).
- ✅ Both plugins ALSO receive `app.upload-dir=…-fork-<N>` — third shared filesystem path discovered during execute (see [[89-FLAKE-DIAGNOSTIC]] Finding 6); BackupArchiveService + 14 graphic services rely on it.
- ✅ JVM-side fork number is exposed via `argLine -Dtest.fork.number=${surefire.forkNumber}` — `<systemPropertyVariables>` with a same-name key resolves to empty (see [[89-FLAKE-DIAGNOSTIC]] Finding 5). `BackupStagingDirPerForkIT` Test 2 reads `System.getProperty("test.fork.number")` and fires non-vacuously.
- ✅ Failsafe `default-it` runs `<forkCount>2</forkCount><reuseForks>true</reuseForks>`; Failsafe `e2e-it` stays single-fork (Playwright `RANDOM_PORT` constraint preserved).
- ✅ `BackupStagingCleanup` production component unchanged; sweeps only its own per-fork dir — proven by `BackupStagingCleanupRaceIT` (own fork swept selectively, sibling `backup-staging-fork-99` files untouched).
- ✅ `ImportLockedPostRejectorIT` passes under `forkCount=2 + reuseForks=true` — lock-acquisition deadline bumped from 10s to 20s via named constant `LOCK_ACQ_DEADLINE_SECONDS`, root-cause analysis recorded in source Javadoc (Spring context rebuild concurrent with team-card Playwright generation stretches MVC dispatch latency).
- ✅ 3-seed Failsafe verification on `org.ctc.backup.**` (seeds 1234 / 5678 / 9999) all green: zero failures, zero flakes; cross-fork-collision absence empirically proven.
- ✅ Legacy `data/*/backup-staging` (no fork suffix) removed via `rm -rf` per D-06. Legacy `data/dev/backup-staging-fork-0` (Attempt-1 leftover) also removed. Production `application*.yml` untouched per D-14.

## Tasks completed

| Task | Commit | Outcome |
|------|--------|---------|
| 1 — pom.xml `<systemPropertyVariables>` + Failsafe `forkCount=2` | `75aced21` + `6ba0f70b` + `6061c36d` | Two `<systemPropertyVariables>` blocks (Surefire + Failsafe `default-it`) carry three entries each: `app.backup.staging-dir`, `app.backup.import-backups-dir`, `app.upload-dir`. `argLine` carries `-Dtest.fork.number=${surefire.forkNumber}`. No project-property fallback (D-04R). `default-it` `forkCount=2 reuseForks=true`. |
| 2 — `BackupStagingDirPerForkIT` | `955ce522` | Two-test class: Test 1 regex match on per-fork path name; Test 2 `assumeThat(forkNum).isNotBlank()` + suffix parity check. Non-vacuous under Failsafe forks. |
| 3 — `BackupStagingCleanupRaceIT` | `7eefa126` + `be944b50` | Seeds own-fork + sibling `backup-staging-fork-99` dummy dir, re-publishes `ApplicationReadyEvent`, asserts own-fork swept + sibling untouched. `@AfterAll` removes sibling dir + own-fork `unrelated.txt`. |
| Phantom-failure fix — `<phase>none</phase>` on inherited Failsafe execution | `ffd1b2e8` | Spring Boot parent's anonymous Failsafe execution ran every IT twice for years (see [[89-FLAKE-DIAGNOSTIC]] Finding 7). Unbound from the lifecycle. Side effect: halves Failsafe wallclock. |
| 4 — `ImportLockedPostRejectorIT` D-19 deadline | `4a747926` | `LOCK_ACQ_DEADLINE_SECONDS = 20L` shared by all three `hasAcquired.await` calls. Javadoc explains the bump under `reuseForks=true + forkCount=2`. Production `ImportLockService` unchanged. |
| 5 — 3-seed Failsafe verification + legacy cleanup | (this commit) | Seeds 1234, 5678, 9999 all green: zero failures, zero flakes across `org.ctc.backup.**` (158-160 tests per run depending on schedule). Legacy singleton + Attempt-1 leftover dirs removed. |

## Execute-phase findings (load-bearing — captured in `89-FLAKE-DIAGNOSTIC.md` Attempt 2 appendix)

1. **Finding 5 — recursive-name systemPropertyVariables key:** `<surefire.forkNumber>${surefire.forkNumber}</surefire.forkNumber>` resolves to empty (Maven POM-load-time substitution races Surefire's late-binding). Fix: distinct key `test.fork.number` via `argLine`. D-04R.2 implementation revised.
2. **Finding 6 — `app.upload-dir` is a third shared filesystem path:** TeamCardService + 13 graphic services + BackupArchiveService all touch `data/dev/uploads/`. Per-fork pattern extended to it. D-18 scope extended.
3. **Finding 7 — Spring Boot parent's anonymous Failsafe execution:** Inherited execution bound to `integration-test + verify` with only `<classesDirectory>` config ran every IT twice per `mvn verify` for ages. Unbinding it via `<phase>none</phase>` halved Failsafe wallclock — a free bonus on top of PERF-01.

## Cross-fork-collision proof (3-seed verification)

| Seed | Completed | Failures | Errors | Skipped (env-conditional) | Result |
|------|-----------|----------|--------|---------------------------|--------|
| 1234 | 158 | 0 | 0 | 2 (MariaDB Testcontainer, BackupRoundTrip) | ✅ |
| 5678 | 158 | 0 | 0 | 2 | ✅ |
| 9999 | 160 | 0 | 0 | 4 | ✅ |

Per-fork mechanism engages cleanly across all three random-order runs. Wallclock per run: 5:34–6:17 min (full `mvn verify` incl. all Surefire phase + ~56 Failsafe ITs + JaCoCo + SpotBugs).

## Deferred items (Phase 90+ tech debt)

- **DevDataSeeder Chromium pressure:** every `@SpringBootTest @ActiveProfiles("dev")` context bootstrap triggers `TestDataService.seed() → TeamCardService.generateAllCards()` with 14+ `Playwright.create()` calls. Under parallel Surefire `forkCount=2` this can hit `Page.captureScreenshot: Unable to capture screenshot` intermittently. Observed 3× during Phase 89 execute under heavy back-to-back `mvn verify` cycles. Root-cause fix: move team-card generation off the per-context rebuild path (e.g. cache rendered PNGs, generate only when missing). Tracked as `LOCK_ACQ_DEADLINE_SECONDS` JavaDoc comment in `ImportLockedPostRejectorIT`; deserves a dedicated phase if it surfaces in CI.
- **`BackupImportServiceIT` `Files.list(stagingDir).count() == 0` fragility:** the assertion is brittle to any non-`upload-*` file in the staging dir. Tightening it to `Files.list(stagingDir).filter(p -> p.getFileName().toString().startsWith("upload-")).count() == 0` would survive arbitrary co-tenant test data. Out of scope for Phase 89.

## Files modified

- `pom.xml` — Surefire + Failsafe `default-it` `<systemPropertyVariables>` (3 entries each), `argLine` `-Dtest.fork.number=`, `default-it forkCount=2 reuseForks=true`, inherited `default` execution unbound via `<phase>none</phase>`.
- `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` — NEW.
- `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` — NEW.
- `src/test/java/org/ctc/backup/it/ImportLockedPostRejectorIT.java` — `LOCK_ACQ_DEADLINE_SECONDS` constant + replacement of three `await(10, SECONDS)` call sites.

Untouched (D-14 invariant): `src/main/resources/application*.yml`, `src/main/java/org/ctc/backup/service/BackupStagingCleanup.java`, `src/main/java/org/ctc/backup/lock/ImportLockService.java`.

## Reference

- [[89-CONTEXT]] — original decisions (D-01..D-19), with D-04R.2 and D-18 annotated for the execute-phase revisions
- [[89-FLAKE-DIAGNOSTIC]] — Attempt-1 findings (Findings 1–4) + Attempt-2 appendix (Findings 5–7)
- [[89-VALIDATION]] — sampling-strategy and per-task verification map
