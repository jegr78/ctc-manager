# Phase 89 — Plan 89-01 Flake Diagnostic (Attempt 1)

**Status:** REVERTED — re-plan required.
**Run:** 2026-05-19, Seed 1234 Failsafe verification on `org.ctc.backup.**` with `forkCount=2, reuseForks=true`.
**Outcome:** 3 regressions surfaced. Working tree reset to pre-89-01 state.

## Empirical Findings (load-bearing for re-plan)

### Finding 1 — RESEARCH RQ-1 is FALSIFIED

The plan assumed Surefire/Failsafe substitute `${surefire.forkNumber}` at fork-dispatch time even when the project defines `<surefire.forkNumber>0</surefire.forkNumber>` as a fallback. Empirically this is wrong under Surefire 3.5.5:

- **Evidence:** Every backup IT's `target/failsafe-reports/TEST-*.xml` `<properties>` section showed `<property name="app.backup.staging-dir" value="data/${spring.profiles.active:dev}/backup-staging-fork-0"/>` — Maven eager-substituted `${surefire.forkNumber}` from the project-property block at POM-load time, before Surefire's fork-time substitution could run.
- **Consequence:** ALL forks shared `data/dev/backup-staging-fork-0/` — the per-fork mechanism never engaged. `BackupRoundTripIT` Run 2 failed: `NoSuchFileException: .../backup-staging-fork-0/upload-…zip` (file written by one fork's Spring context, deleted by sibling fork's cleanup or simply not present in second-run state).

### Finding 2 — Surefire 3.5.5 does NOT inject `surefire.forkNumber` as JVM system property

- **Evidence:** `target/failsafe-reports/TEST-org.ctc.backup.service.BackupStagingDirPerForkIT.xml` `<properties>` dump contains `surefire.real.class.path` but NO `surefire.forkNumber`.
- **Consequence:** `BackupStagingDirPerForkIT` Test 2 (`endsWith("-" + System.getProperty("surefire.forkNumber"))`) was vacuously passing — `forkNum == null` triggered the conditional-skip branch. The IT did NOT actually prove per-fork parity; it only proved the regex shape (which is `\d+` and matches `0`).

### Finding 3 — `app.backup.import-backups-dir` is a SECOND shared filesystem path NOT covered by PERF-01

- **Evidence:** `AutoBackupBeforeImportPathIT` failed with `FileAlreadyExistsException: /Users/jegr/Documents/github/ctc-manager/data/dev/import-backups/2026-05-19T15-06-25Z/auto-backup-before-import.zip`. `application.yml` line 6: `app.backup.import-backups-dir: data/${spring.profiles.active:dev}/import-backups` — singleton across forks.
- **Consequence:** Elevating Failsafe `forkCount=2` requires per-fork isolation for `app.backup.import-backups-dir` in addition to `app.backup.staging-dir`. Plan 89-01 scope was incomplete.

### Finding 4 — `ImportLockedPostRejectorIT` lock-acquisition timeout under `reuseForks=true`

- **Evidence:** `ImportLockedPostRejectorIT.givenLockHeld_whenGetAdminSeasons_thenPassesThrough` failed at line 214: `[thread A must acquire the lock within 10 s] Expecting value to be true but was false`. Test wallclock 12.10 s — exceeds the 10 s assertion timeout.
- **Root cause hypothesis (not verified):** intra-JVM lock contention amplified by `reuseForks=true` + CPU saturation from 2 parallel Failsafe forks. Could be a latent test-isolation bug where a prior test class in the same JVM left `ImportLock` in a non-default state, OR simply a 10 s deadline too tight for the current load profile.
- **Action for re-plan:** read `ImportLockedPostRejectorIT.java:214` and the test's lock-acquisition path; consider whether the 10 s deadline is robust under `reuseForks=true`+`forkCount=2`, and whether `ImportLock` is `static`-shared across test classes in the same JVM.

## Re-Plan Inputs

Re-plan MUST account for:

1. **Drop the project-property `<surefire.forkNumber>0</surefire.forkNumber>` fallback.** It defeats Surefire's late substitution. For non-forked invocations (IDE direct-Surefire-skip), application.yml's `data/${spring.profiles.active:dev}/backup-staging` (no fork suffix) is consumed directly — that path is fine, the per-fork mechanism is a TEST-FORK concern only.
2. **Strengthen `BackupStagingDirPerForkIT` Test 2.** Since Surefire 3.5.5 doesn't expose `surefire.forkNumber` as a JVM system property, the parity assertion must use a different signal — e.g. assert the suffix is in `{1, 2}` (the expected fork numbers for `forkCount=2`), OR drop Test 2 entirely and rely on Test 1's regex.
3. **Extend scope to `app.backup.import-backups-dir`.** Mirror the `<systemPropertyVariables>` injection for both Surefire and Failsafe `default-it`. Either re-affirm D-14 (no application.yml change) and inject TWO per-fork properties, OR relax D-14 to allow application.yml to define both as `data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber:0}` style (cleaner — single source of truth — but breaks D-14 lock).
4. **Investigate `ImportLockedPostRejectorIT` under `reuseForks=true`+`forkCount=2`.** Either bump the timeout OR fix the latent isolation bug if one exists.

## Reverted Artifacts

- `pom.xml` — fully reverted via `git checkout`.
- `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` — deleted.
- `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` — deleted.
- Legacy `data/dev/backup-staging/`, `data/dev,demo/backup-staging/` — deleted (gitignored, will not regenerate without per-fork mechanism actively writing there; harmless).

## Next Step

Invoke `/gsd-discuss-phase 89` or `/gsd-plan-phase 89` to consume this diagnostic and replan Plan 89-01 with corrected RQ-1 + extended scope.
