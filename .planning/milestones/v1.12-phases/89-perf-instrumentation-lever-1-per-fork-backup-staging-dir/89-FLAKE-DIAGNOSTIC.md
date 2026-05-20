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

---

## Phase 89 — Plan 89-01 Empirical Updates (Attempt 2 / Execute)

**Status:** Three additional load-bearing findings surfaced during Plan 89-01 Attempt 2 execute on 2026-05-19. None require a revert; all were resolved inline. Plan 89-01 commits `75aced21..ffd1b2e8` carry the fixes.

### Finding 5 — same-name `<systemPropertyVariables>` key resolves to empty (D-04R.2 implementation revised)

CONTEXT.md D-04R.2 said: "Both Surefire and Failsafe `default-it` `<systemPropertyVariables>` carry an entry `<surefire.forkNumber>${surefire.forkNumber}</surefire.forkNumber>` so the JVM sees the value via `System.getProperty(...)`." Empirically this does NOT work in Surefire/Failsafe 3.5.5.

- **Evidence:** With `<surefire.forkNumber>${surefire.forkNumber}</surefire.forkNumber>` inside `<systemPropertyVariables>`, the failsafe-reports `<properties>` dump showed `<property name="surefire.forkNumber" value=""/>` — empty string, NOT the fork number. `BackupStagingDirPerForkIT` Test 2 was skipped via `assumeThat` because the property was blank.
- **Mechanism:** Maven's POM-load-time property resolver substitutes `${surefire.forkNumber}` against its own property layers (project / user / outer-JVM system properties) BEFORE Surefire's fork-dispatch substitution runs. The outer Maven JVM has no `surefire.forkNumber` property set, so the value resolves to empty at POM-load. Surefire's late-binding never gets a chance for standalone `${surefire.forkNumber}` values. For LARGER strings like `data/.../backup-staging-fork-${surefire.forkNumber}` the substitution DOES work — the difference appears to be that Maven only pre-resolves placeholder-only values, not embedded placeholders.
- **Fix:** Expose the fork number via `<argLine>` with a distinct key — `<argLine>… -Dtest.fork.number=${surefire.forkNumber}</argLine>`. Surefire substitutes `${surefire.forkNumber}` inside `<argLine>` reliably at fork-dispatch time. Implementation: commit `6ba0f70b`; `BackupStagingDirPerForkIT` Test 2 reads `System.getProperty("test.fork.number")` (not `surefire.forkNumber`).
- **CONTEXT.md status:** D-04R.2 reads "expose `surefire.forkNumber` to the JVM"; the actual implementation uses a distinct key `test.fork.number` via argLine. Treat D-04R.2 as **revised in implementation** — the goal (expose the fork number to the JVM non-vacuously) is unchanged; only the mechanism differs.

## Finding 6 — `app.upload-dir` is a third shared filesystem path

CONTEXT.md D-18 added `app.backup.import-backups-dir` as a second shared path beyond `app.backup.staging-dir`. Empirically there's also a third: `app.upload-dir` (`data/dev/uploads/`).

- **Evidence:** Under Failsafe `default-it forkCount=2 reuseForks=true`, `BackupArchiveServiceReadIT.givenValidZip_whenCountDataEntries_thenReturnsPerTableRowCounts` failed with `java.nio.file.NoSuchFileException: data/dev/uploads/teams/<uuid>/TBR.png`. Two forks each ran `DevDataSeeder.seed() → TestDataService.generateTeamCards() → TeamCardService.generateCard()` writing to the SINGLE shared `data/dev/uploads/teams/` directory; one fork's seed deleted/overwrote the other's PNG files mid-test, leaving `BackupArchiveService.writeZip` to read a missing file.
- **Consumers:** `app.upload-dir` is read by 14+ services (`TeamCardService`, 13 graphic services, `BackupArchiveService`, `WebConfig`, `TeamCardController`, sitegen). All write/read shared filesystem state in test forks.
- **Fix:** Inject `<app.upload-dir>data/${spring.profiles.active:dev}/uploads-fork-${surefire.forkNumber}</app.upload-dir>` in both Surefire and Failsafe `default-it` `<systemPropertyVariables>`. Same per-fork pattern, same D-14 invariant (production `application*.yml` unchanged). Implementation: commit `6061c36d`.
- **CONTEXT.md D-18 status:** D-18 covers TWO shared paths; the actual implementation covers THREE. Treat D-18 as **revised in implementation** with the third entry being `app.upload-dir`.
- **Why the plan missed this:** D-18 was scoped from `AutoBackupBeforeImportPathIT`'s failure under Attempt 1 (FileAlreadyExistsException on `app.backup.import-backups-dir`). The wider grep across `@Value("${app.*-dir}")` consumers was not done in the planning phase. `[[grep-all-usages]]` discipline applies — should grep for ALL `app.*-dir` properties when designing per-fork isolation.

## Finding 7 — Spring Boot parent's anonymous Failsafe execution runs every IT twice (pre-existing bug, not Plan-89-specific)

Before this phase no IT was sensitive to per-execution config differences, so the bug was invisible.

- **Evidence:** `mvn help:effective-pom` reveals two Failsafe executions on `integration-test + verify`:
  1. Anonymous execution inherited from `spring-boot-starter-parent` 4.0.6 (no `<id>`, defaults to `default`), config only `<classesDirectory>`.
  2. The project's `default-it` execution with its per-fork systemPropertyVariables, `forkCount=2`, `<groups>integration</groups>`.

  Maven runs BOTH executions during the `integration-test` phase. Every Failsafe IT runs twice per `mvn verify`. The phantom `failsafe-summary failures=1` (no corresponding `<failure>` in any TEST-XML) that surfaced during Plan-89-01 execute was the inherited execution's fork running `BackupStagingDirPerForkIT` WITHOUT per-fork systemPropertyVariables → IT saw `app.backup.staging-dir = "backup-staging"` (singleton fallback) → Test 1 regex assertion failed.
- **Fix:** `<execution><id>default</id><phase>none</phase></execution>` unbinds the inherited execution from the lifecycle. Only `default-it` runs `integration-test + verify` now (plus `e2e-it` under the `e2e` profile). Implementation: commit `ffd1b2e8`.
- **Bonus side effect:** Halves Failsafe wallclock — every IT was running twice for ages. Plan-89-01 PERF-01 Lever-1 gain compounds with this latent fix.
- **CONTEXT.md status:** No prior decision covers this. Recommend adding a **D-20** to record the unbind as part of the Plan-89-01 forward-path. (NOT a backwards-compat concern: production runtime never invokes Maven's Failsafe lifecycle.)
