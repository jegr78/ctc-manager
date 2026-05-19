# Phase 89: PERF Instrumentation & Lever 1 (Per-Fork Backup-Staging-Dir) - Context

**Gathered:** 2026-05-19 (revised after Plan 89-01 Attempt 1 flake diagnostic — see `89-FLAKE-DIAGNOSTIC.md`)
**Status:** Re-planning required (existing Plan 89-01/02/03 outdated; D-04 superseded; D-12 strengthened; D-18 and D-19 added)

<domain>
## Phase Boundary

Land Lever-1 of the v1.12 test-wallclock Round-2 forward-path (per-fork `app.backup.staging-dir` that decouples backup ITs from a singleton path race) AND the PERF-02 instrumentation that produces per-context cache-key fingerprints feeding Phase 90's targeted PERF-03 consolidation decision. Two PERF requirements, three sequential plans (refactor / instrumentation / measurement), one wave, inline execution on `gsd/v1.12-driver-import-and-test-perf`.

In scope:
- Replace the singleton `app.backup.staging-dir` (`data/${profile}/backup-staging`) with a per-fork variant resolved at Maven-execution time via Surefire/Failsafe `<systemPropertyVariables>`.
- Make `BackupStagingCleanup` (production `@Component`) operate exclusively on the per-fork path it receives via `@Value("${app.backup.staging-dir}")` — no test-only code branches in production.
- Elevate Failsafe `default-it` `<forkCount>` to 2 (reuseForks=true), keep `e2e-it` at single-fork (Playwright/random-port constraint).
- New `BackupStagingDirPerForkIT` proves per-fork dir contract; new `BackupStagingCleanupRaceIT` proves sweep-isolation under `forkCount=2`.
- 3-seed Failsafe verification (1234/5678/9999) on all `src/test/java/org/ctc/backup/**` ITs under elevated `forkCount=2` — empirical cross-fork-collision proof.
- Extend `ContextLoadCountListener` (init-time count) PLUS add new `ContextCacheKeyFingerprintListener` (`TestExecutionListener#beforeTestClass`) for `MergedContextConfiguration.hashCode()`-keyed fingerprints, both writing into the existing `target/test-perf/context-loads-{PID}.txt` marker (extended format: header line `total <N>` followed by one `<hex-hash>\t<mcc-display>` line per context-init event).
- New executable `scripts/test-perf/aggregate-fingerprints.sh` (shellcheck-clean) + `docs/test-performance.md § PERF-02 Forensics` usage block + Top-5-cluster output example.
- Wave-4 wallclock measurement (3 local runs, idle protocol per Phase-86 D-09) populates `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)` with median + delta vs. 10:24 Phase-86-post-audit baseline + context-load-count delta + JaCoCo ≥ 88.88 %; honest reporting, no hard local reduction gate.
- README.md "Test Performance" section pointer updated to the new Wave-4 figure.

Out of scope (deferred to later phases):
- PERF-03 cluster consolidation onto shared `@ContextConfiguration` (Phase 90 — needs PERF-02 data).
- PERF-04 Testcontainers `withReuse` wiring (Phase 90).
- PERF-05 Test-Module-Split decision (Phase 90).
- PERF-06 authoritative CI 5-run re-harvest of the v1.12 wallclock (Phase 91 — needs all PERF levers merged).
- UX-01 Google-API error UX (Phase 91 stretch).
- Any `application-prod.yml`, `docs/operations/import-runbook.md`, or `CLAUDE.md` Commands-section change (D-14, D-16).

</domain>

<decisions>
## Implementation Decisions

### Plan Structure

- **D-01 (REVISED Plan 89-01 task count): Three plans, sequential inline, one wave. Plan 89-01 now has 5 tasks (was 4).**
  - **Plan 89-01 — PERF-01 (per-fork refactor + 2 assertion ITs + lock-timeout fix + 3-seed verification).** Task 1: pom.xml `<systemPropertyVariables>` per D-03/D-04R/D-04R.2/D-18 (FOUR entries per plugin block, no project-property fallback). Task 2: `BackupStagingDirPerForkIT` per D-12 (Test 2 now non-vacuous). Task 3: `BackupStagingCleanupRaceIT` per D-17. **Task 4 (NEW): `ImportLockedPostRejectorIT` lock-timeout investigation + fix per D-19** — root-cause-first, deadline-bump only with explicit justification. Task 5: 3-seed Failsafe verification on `org.ctc.backup.**` per D-13 + legacy `data/*/backup-staging/` `rm -rf` per D-06. Highest risk → goes first.
  - **Plan 89-02 — PERF-02 (`ContextCacheKeyFingerprintListener` + aggregator script + docs § PERF-02 Forensics).** Depends on Plan-01 only for "clean verify baseline"; independent code surface.
  - **Plan 89-03 — Wave-4 measurement + docs.** 3 local `./mvnw clean verify -Pe2e` runs per Phase-86 D-09 idle protocol; populates `docs/test-performance.md` "Post-Optimization Wallclock (Wave 4)" + "v1.12 Forward Path"-update (Lever 1 = DONE); updates README pointer. Atomic own SUMMARY.md.
  - Reason: ROADMAP literally says "independent parallel-runnable plans" but [[inline-sequential-execution]] + [[wave-pause]] override — sequential inline is the explicit user-locked policy for v1.12. Three plans cleanly separate Refactor / Instrumentation / Measurement; each commit history milestone is self-contained for revert/forensics.

- **D-02: Wave-4 measurement = honest reporting, no hard local reduction gate.** 3 local runs, median + delta vs. 10:24 baseline + context-load-count + JaCoCo ≥ 88.88 % recorded in `docs/test-performance.md § Post-Optimization Wallclock (Wave 4)`. CI-authoritative median deliberately deferred to Phase 91 PERF-06 (D-11 from Phase 86: CI is source of truth). Local run-variance (66s spread in Phase 86 post-audit) makes a hard local gate statistically fragile.

### Fork-Number Injection

- **D-03: Failsafe + Surefire `<systemPropertyVariables>` in pom.xml.** Both Maven plugins set `app.backup.staging-dir=data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}` as a per-fork JVM system property; Spring's standard `SystemEnvironmentPropertySource` (highest precedence among defaults) picks it up. No production application.yml change. `application.yml` stays at `data/${spring.profiles.active:dev}/backup-staging` (D-14).

- **D-04 (SUPERSEDED by D-04R after Attempt 1 flake diagnostic):** ~~Per-fork path schema = `data/${profile}/backup-staging-fork-${surefire.forkNumber}` with project-level fallback `<properties><surefire.forkNumber>0</surefire.forkNumber></properties>`.~~ Empirically falsified: Maven eager-substitutes `${surefire.forkNumber}` from the project-property at POM-load time, defeating Surefire's fork-dispatch substitution. All forks landed in `backup-staging-fork-0`. See `89-FLAKE-DIAGNOSTIC.md` Finding 1.

- **D-04R (REVISED): No project-level `<surefire.forkNumber>` fallback. Path schema stays `data/${profile}/backup-staging-fork-${surefire.forkNumber}`.** pom.xml `<properties>` block does NOT carry a `surefire.forkNumber` entry. Maven sees `${surefire.forkNumber}` as unresolvable and leaves it literal; Surefire/Failsafe substitute at fork-dispatch time. Non-forked invocations (IDE direct-launch without Surefire) consume `application.yml` `app.backup.staging-dir: data/${profile}/backup-staging` directly (no fork suffix) — they bypass `<systemPropertyVariables>` entirely. Single-JVM scenarios don't race; no per-fork isolation needed there.

- **D-04R.2 (NEW companion to D-04R): pom.xml `<systemPropertyVariables>` exposes `surefire.forkNumber` to the JVM as a system property.** Both Surefire and Failsafe `default-it` `<systemPropertyVariables>` carry TWO entries: (1) `<app.backup.staging-dir>data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}</app.backup.staging-dir>` per D-03/D-05, and (2) `<surefire.forkNumber>${surefire.forkNumber}</surefire.forkNumber>` so the JVM sees `surefire.forkNumber` via `System.getProperty(...)`. Reason: Surefire 3.5.5 does NOT automatically inject `surefire.forkNumber` as a JVM system property (only as a placeholder substitution token); the test parity assertion in D-12 needs the JVM-side value. See `89-FLAKE-DIAGNOSTIC.md` Finding 2.

- **D-05: Both Surefire AND Failsafe `<systemPropertyVariables>` set the same value.** Surefire already runs `forkCount=2`; some surefire-routed tests load a Spring context that resolves `app.backup.staging-dir` (e.g., `BackupImportServiceIT`, `BackupImportConfirmFormValidationIT`). Consistent per-fork isolation across both Maven phases. Single source of truth: a shared `<properties>` definition `<staging.dir.test>data/${spring.profiles.active:dev}/backup-staging-fork-${surefire.forkNumber}</staging.dir.test>` referenced by both plugin configurations OR identical literal in both (planner picks).

- **D-06: Legacy `data/*/backup-staging/` (no fork suffix) is removed via one-shot `rm -rf` documented in Plan-01 SUMMARY.** Path is gitignored; no code change to wipe it. `BackupStagingCleanup` from Plan-01 merge onwards operates exclusively on the per-fork path it receives.

### Cache-Key Fingerprint Surface (PERF-02)

- **D-07: Hybrid surface — keep `ContextLoadCountListener` (ApplicationContextInitializer) for count; add new `ContextCacheKeyFingerprintListener` (TestExecutionListener) for the actual Spring TCF cache-key.** Reason: `ApplicationContextInitializer#initialize(ConfigurableApplicationContext)` does NOT receive `MergedContextConfiguration`; the TCF cache-key lives on the `TestContext` accessible from `TestExecutionListener#beforeTestClass`. Two listeners, two registration paths (`spring.factories` for the initializer stays untouched; the new listener registers via `META-INF/spring.factories` `org.springframework.test.context.TestExecutionListener=…` OR a `@TestExecutionListeners(mergeMode = MERGE_WITH_DEFAULTS)` base class — planner picks the lowest-friction wiring).

- **D-08: Marker-file format = the existing `target/test-perf/context-loads-{PID}.txt`, extended.** Line 1: `total <count>` (backward-compat marker). Subsequent lines: `<hex-hash>\t<mcc-display>` where `<hex-hash>` = `Integer.toHexString(mergedContextConfiguration.hashCode())` (D-10) and `<mcc-display>` = `mergedContextConfiguration.toString()` truncated to ~200 chars. The existing aggregator loop in `docs/test-performance.md` L233-239 (`cat $f` summed) must upgrade to `head -1 $f | awk '{print $2}'` extraction — Plan-02 SUMMARY records the migration.

- **D-09: Aggregator = real shell script at `scripts/test-perf/aggregate-fingerprints.sh` (executable, shellcheck-clean).** `docs/test-performance.md § PERF-02 Forensics` shows usage example + Top-5-by-(occurrence × cluster-size) output sample. Trade-off accepted: two-file surface area beats a brittle inline-only block.

- **D-10: Hash = `Integer.toHexString(mergedContextConfiguration.hashCode())`** (8-char hex). Matches Spring TCF `ContextCache` 1:1 (same bucketing function). Display column = `mergedContextConfiguration.toString()` truncated to 200 chars (file-size containment).

### Failsafe Fork-Count Policy + Assertion ITs

- **D-11: Failsafe `default-it` permanently `<forkCount>2</forkCount><reuseForks>true</reuseForks>` in pom.xml.** Mirrors Surefire's existing `forkCount=2` config; Wallclock-Lever-1 gain realises immediately in CI (and is measurable in the Phase-91 PERF-06 re-harvest). Failsafe `e2e-it` execution stays single-fork (Playwright requires a single Spring context per RANDOM_PORT; pom.xml L450-454 comment from Phase 86).

- **D-12 (CLARIFIED — Test 2 now actually fires after D-04R.2):** Per-Fork-Dir Assertion IT shape = single-fork self-assertion + 3-seed suite-run as empirical cross-fork-collision proof.
  - New `BackupStagingDirPerForkIT` (`src/test/java/org/ctc/backup/service/`, `@SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS) @Tag("integration")`).
  - `@Value("${app.backup.staging-dir}") Path stagingDir;`
  - Test 1 — `stagingDir.getFileName().toString()` matches regex `backup-staging-fork-\d+`.
  - Test 2 — `System.getProperty("surefire.forkNumber")` parity check. Per D-04R.2, the property IS exposed to the JVM; Test 2 fires non-vacuously under Failsafe forks. Conditional skip remains for IDE-direct invocations where `surefire.forkNumber` is null. Attempt 1 had Test 2 silently passing because the property wasn't exposed (Finding 2 in `89-FLAKE-DIAGNOSTIC.md`) — D-04R.2 resolves that.
  - Empirical cross-fork-collision proof: D-13's 3-seed suite-run produces zero flakes/failures = no race.

- **D-13: 3-seed verification scope = all `src/test/java/org/ctc/backup/**` ITs.** Broader than the 7 ROADMAP-SC#2-named ITs. Command: `./mvnw verify -Dit.test='org.ctc.backup.**' -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}` (one run per seed; 3 runs total). Plan-01 SUMMARY lists every covered IT class (BackupRoundTripIT, BackupImportMariaDbSmokeIT, BackupImportRollbackIT, BackupRestoreZipOpenCountIT, ImportConcurrentLockIT, ImportLockBannerAdviceIT, ImportLockedPostRejectorIT, BackupImportServiceIT, BackupImportConfirmFormValidationIT, BackupImportSchemaMismatchIT, BackupUploadsMirrorIT, BackupExportServiceIT, BackupSchemaTopologyIT, BackupSchemaExclusionIT, BackupControllerSecurityIT, BackupImportControllerSecurityIT, BackupControllerIT, BackupObjectMapperConfigIT, BackupEntityAnnotationCleanlinessIT, BackupRepositoryEntityGraphIT, AutoBackupBeforeImportPathIT, AutoBackupCatchOrderIT, AutoBackupBeforeImportFailureIT, AdminLayoutIT, plus the two new per-fork ITs).

### Production Behavior

- **D-14: `application.yml` is NOT modified.** Value stays `data/${spring.profiles.active:dev}/backup-staging`. Production deployments resolve to the current path — zero breaking change, zero env-var-required-or-prod-breaks risk. The per-fork override applies ONLY when Surefire/Failsafe push the `app.backup.staging-dir` system property, which never happens in production runtime. `application-prod.yml` likewise unchanged.

### Quality Gates

- **D-15: Standard gates apply, no tightening, no loosening.** JaCoCo line coverage ≥ 88.88 % (Phase-86 baseline; v1.11 baseline per STATE.md "Baselines to Preserve"). SpotBugs `BugInstance` = 0 (blocking). CodeQL gate-step exit 0 on PR HEAD SHA. New `System.getProperty("surefire.forkNumber")` reads in the assertion IT may surface SpotBugs `DM_DEFAULT_ENCODING` or property-injection flags — if so, suppress via targeted `@SuppressFBWarnings({"CODE"}, justification="…")` per CLAUDE.md SAST pattern, NOT via blanket suppression file entries.

### Documentation

- **D-16: Three doc surfaces updated, two left alone.**
  - **Updated (Plan-02 + Plan-03):** `docs/test-performance.md` — new `§ PERF-02 Forensics` (aggregator usage + Top-5 output sample); new `§ Post-Optimization Wallclock (Wave 4)` (3 local runs + median + delta + context-counts); existing `§ v1.12 Forward Path` updated to mark Lever 1 as DONE with the Wave-4 reference and to mention PERF-02 data feeding PERF-03.
  - **Updated (Plan-03):** `README.md` — Test-Performance section pointer to the new Wave-4 figure.
  - **NOT touched:** `CLAUDE.md` Commands section (3-seed run pattern follows existing `-Dit.test` discipline already documented; per [[test-call-optimization]]); `docs/operations/import-runbook.md` (operator-facing, production behavior unchanged per D-14).

### Cleanup Race Verification

- **D-17: New `BackupStagingCleanupRaceIT` proves sweep-isolation under `forkCount=2`.** `src/test/java/org/ctc/backup/service/`, `@SpringBootTest @ActiveProfiles("dev") @Tag("integration")`. `@BeforeAll` (or `@BeforeEach`) writes test files into: (a) the test's own per-fork dir; (b) a sibling dummy fork dir like `…-fork-99` that no real fork ever uses. After `BackupStagingCleanup` runs (either via injected bean + manual invocation OR by reading already-completed sweep state), assertion: own-fork files removed (those matching `upload-*.zip` / `upload-*.zip.meta`), sibling-fork files completely untouched. Cleanup target dir (`-fork-99`) is removed in `@AfterAll`.

### Scope Extension — Second Shared Filesystem Path

- **D-18 (NEW after Attempt 1 flake diagnostic): `app.backup.import-backups-dir` ALSO requires per-fork isolation via the same Surefire/Failsafe `<systemPropertyVariables>` mechanism.** `AutoBackupBeforeImportPathIT` failed under `forkCount=2` with `FileAlreadyExistsException: data/dev/import-backups/<ts>/auto-backup-before-import.zip` because two forks materialised the same auto-backup path. Mirror the D-03/D-05 pattern: both Surefire and Failsafe `default-it` `<systemPropertyVariables>` carry a SECOND entry `<app.backup.import-backups-dir>data/${spring.profiles.active:dev}/import-backups-fork-${surefire.forkNumber}</app.backup.import-backups-dir>`. Production `application.yml` `app.backup.import-backups-dir: data/${profile}/import-backups` stays UNCHANGED (D-14 invariant — production data layout unaffected). Plan 89-01 Task 1 grows to inject FOUR `<systemPropertyVariables>` total per plugin (2 plugins × 2 properties incl. `surefire.forkNumber` from D-04R.2 = 4 entries each). See `89-FLAKE-DIAGNOSTIC.md` Finding 3.

### Lock-Timeout Investigation

- **D-19 (NEW after Attempt 1 flake diagnostic): `ImportLockedPostRejectorIT` lock-acquisition timeout investigation as Plan 89-01 Task 5.** Under `reuseForks=true + forkCount=2`, `ImportLockedPostRejectorIT.givenLockHeld_whenGetAdminSeasons_thenPassesThrough` failed at line 214 with `[thread A must acquire the lock within 10 s]` (actual wallclock 12.10 s). Plan 89-01 must add a dedicated Task 5 that: (1) reads `ImportLock` acquisition path + the test's lock-handoff threading model, (2) checks whether `ImportLock` is `static`-shared across test classes in the same JVM (latent state-leak under `reuseForks=true`), (3) chooses ONE of: (a) fix the latent isolation bug with a targeted source patch, (b) tighten test-isolation via `@AfterEach` lock-release, OR (c) bump the deadline to 20 s with an explanatory comment + follow-up issue if a-or-b can't be done within Plan 89-01's scope. Per `[[no-flaky-dismissal]]`: silent deadline-bump without root-cause analysis is forbidden. See `89-FLAKE-DIAGNOSTIC.md` Finding 4.

### Claude's Discretion

- Exact wording of `docs/test-performance.md § PERF-02 Forensics` prose; exact Top-5-cluster output format (table vs. plain list — planner picks the shape that reads well alongside the existing § Context Load Counts section).
- Whether the shared per-fork-path string in pom.xml lives in `<properties>` (named `staging.dir.test` or similar) or is literally repeated in Surefire + Failsafe configurations — planner picks based on Maven-property-substitution ordering quirks.
- Whether `ContextCacheKeyFingerprintListener` registers via `META-INF/spring.factories` or via a tiny test-only `@TestExecutionListeners(mergeMode = MERGE_WITH_DEFAULTS)` base class — planner picks the lowest-friction wiring that doesn't require touching individual IT classes.
- `MergedContextConfiguration.toString()` truncation length (D-08 says "~200 chars") — planner picks the cutoff that keeps the marker file under ~10KB per fork while preserving cluster identifiability.
- `BackupStagingCleanupRaceIT` (D-17) sibling-fork-dir name (`-fork-99` is a placeholder; planner picks any value guaranteed not to clash with realistic `${surefire.forkNumber}` values 0-32).
- Filename of the new fingerprint listener: `ContextCacheKeyFingerprintListener` (default suggestion) vs. extending `ContextLoadCountListener`'s family naming (e.g., `ContextCacheKeyDumpListener`) — planner picks.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- **`89-FLAKE-DIAGNOSTIC.md` (MANDATORY for re-plan):** load-bearing empirical input — RESEARCH RQ-1 falsified, Surefire 3.5.5 doesn't expose `surefire.forkNumber` as JVM property, `app.backup.import-backups-dir` is a second shared path, `ImportLockedPostRejectorIT` lock-timeout under elevated load. Re-planning MUST read this file first.
- `.planning/ROADMAP.md` — Phase 89 section (Goal, Depends-on, Requirements, Success Criteria 1-5); Phase 90 (downstream dependency — PERF-03 reads PERF-02 fingerprint data); Phase 91 (PERF-06 re-harvests CI median after this phase ships)
- `.planning/REQUIREMENTS.md` lines 21-22 — full PERF-01 and PERF-02 text
- `.planning/PROJECT.md` — v1.12 milestone scope; "Phase 86 D-14 / D-15 / D-16 forward-path"
- `.planning/STATE.md` — current position (Phase 88 complete, Phase 89 next); "Baselines to Preserve" (JaCoCo 88.88 %, CI E2E median 23:00, SpotBugs 0, CodeQL exit 0)

### Phase 86 Forward-Path Anchor

- `docs/test-performance.md` (entire file) — Phase-86 baseline (09:45 local) + post-audit (10:24 local) + CI median (23:00) + § v1.12 Forward Path (Lever 1 description anchors PERF-01)
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-CONTEXT.md` — D-09 (3-run idle local-measurement protocol; carries to Wave-4), D-10/D-11 (CI median methodology; deferred to Phase 91), D-12 (`ContextLoadCountListener` design that PERF-02 extends), D-14 (per-fork backup-staging-dir identified as Top-1 v1.12 lever), D-15 (realistic-optimistic expectation framing)
- `.planning/milestones/v1.11-phases/86-test-wallclock-reduction/86-SUMMARY.md` — Plan-02 cache-key-fragmentation lesson (per-class `@DynamicPropertySource` split shared cache key into 7 distinct keys) → PERF-03 must use PERF-02 data to avoid re-introducing the pattern

### Code Surface (PERF-01 touchpoints)

- `src/main/resources/application.yml` lines 4-5 (`app.backup.staging-dir`) — UNCHANGED per D-14; production-default anchor
- `src/main/resources/application-{dev,local,docker,prod}.yml` — verify none override `app.backup.staging-dir`; if they do, the per-fork override semantics need re-evaluation
- `src/main/java/org/ctc/backup/service/BackupStagingCleanup.java` — `@Value("${app.backup.staging-dir}")` binding; `@EventListener(ApplicationReadyEvent.class) sweepStagingDir()`; no per-fork logic needed since the resolved path IS already per-fork after D-03
- `pom.xml` lines 268-280 — Surefire plugin config (forkCount=2, reuseForks=true, argLine); lines 282-310 — Failsafe `default-it` execution (no forkCount today → set to 2 per D-11); lines 436-465 — Failsafe `e2e-it` (stays single-fork per D-11)
- `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java`, `BackupImportConfirmFormValidationIT.java`, `BackupImportServiceIT.java`, and all other `src/test/java/org/ctc/backup/**` ITs that `@Value("${app.backup.staging-dir}")` — must keep working unmodified (no per-fork awareness leaks into test code)

### Code Surface (PERF-02 touchpoints)

- `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` — current count-only listener; stays as-is (D-07 hybrid surface)
- `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` — keep green after PERF-02 lands; may need extension for new marker-file format
- `src/test/resources/META-INF/spring.factories` — current `ApplicationContextInitializer` registration; possibly extended with `TestExecutionListener` registration line per D-07

### Testing Conventions (must respect)

- `.planning/codebase/TESTING.md` § "Test Categorization (`@Tag`)" — new ITs are `@Tag("integration")`
- `.planning/codebase/TESTING.md` § "Test Invocation Discipline" — single final `./mvnw verify -Pe2e` per phase; targeted `-Dtest=` / `-Dit.test=` between waves
- `CLAUDE.md` "Tag Tests by Category" + "Subagent Rules" + "Clean Build/Test Only" + "Test-Aufrufe optimieren"

### Static Analysis & Gates

- `CLAUDE.md` § "Static Analysis (SpotBugs + find-sec-bugs)" — `config/spotbugs-exclude.xml` rationale-comment discipline; targeted `@SuppressFBWarnings` only
- `CLAUDE.md` § "CodeQL SAST" — `.github/codeql/codeql-config.yml` query-filters + `docs/security/sast-acceptance.md` table-row discipline
- `.github/workflows/codeql.yml` — PR gate-step

### Deliverable Files (created/modified by this phase)

- `docs/test-performance.md` — new sections § PERF-02 Forensics, § Post-Optimization Wallclock (Wave 4); existing § v1.12 Forward Path edited (Lever 1 DONE)
- `scripts/test-perf/aggregate-fingerprints.sh` — NEW, executable (chmod +x), shellcheck-clean
- `README.md` — Test-Performance section pointer update
- `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` — NEW
- `src/test/java/org/ctc/backup/service/BackupStagingDirPerForkIT.java` — NEW
- `src/test/java/org/ctc/backup/service/BackupStagingCleanupRaceIT.java` — NEW
- `pom.xml` — Surefire + Failsafe `default-it` `<systemPropertyVariables>` carrying TWO entries each (`app.backup.staging-dir`, `app.backup.import-backups-dir`) per D-04R/D-18 + a third entry exposing `surefire.forkNumber` to the JVM per D-04R.2. Failsafe `default-it` `<forkCount>2</forkCount><reuseForks>true</reuseForks>` per D-11. NO project-level `<surefire.forkNumber>` fallback (D-04 superseded).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`ContextLoadCountListener` infrastructure** (`src/test/java/org/ctc/testsupport/`, registered via `src/test/resources/META-INF/spring.factories`) — PID-keyed shutdown-hook writing to `target/test-perf/context-loads-{PID}.txt` is already PERF-02's foundation. PERF-02 keeps it count-only and parallels it with the new fingerprint listener writing into the same marker file (D-07, D-08).
- **`@Value("${app.backup.staging-dir}")` injection pattern** — used identically by `BackupStagingCleanup` (production), `BackupImportServiceIT`, `BackupImportConfirmFormValidationIT`, `BackupImportSchemaMismatchIT`, `BackupUploadsMirrorIT`, `BackupImportZipSlipIT`, `ImportLockedPostRejectorIT`. After D-03/D-05 the resolved value is per-fork during Maven test phases — no source-side change required for any of these consumers.
- **3-seed Failsafe-verification pattern** — established in Phase 86 (`-Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed={1234,5678,9999}`). Direct copy-forward.
- **Phase-86 D-09 idle-protocol** — `time ./mvnw clean verify -Pe2e --no-transfer-progress -Dspring.profiles.active=dev` × 3, logs in `.test-perf-logs/` (kept outside `target/` since `mvn clean` wipes it), context-load tallies copied per run. Direct copy-forward for Plan 89-03.
- **Marker-file aggregator loop** (currently in `docs/test-performance.md` L233-239) — base for the extended-format aggregator; the new shell script encapsulates and extends it.

### Established Patterns

- **`@Tag("integration") @SpringBootTest @ActiveProfiles("dev") @TestInstance(PER_CLASS)`** — canonical IT shape; both new ITs (`BackupStagingDirPerForkIT`, `BackupStagingCleanupRaceIT`) follow it.
- **`META-INF/spring.factories` for Spring bootstrap listeners** — current `ApplicationContextInitializer` registration. The new `TestExecutionListener` can register via the same file (`org.springframework.test.context.TestExecutionListener=…`) without touching individual test classes.
- **Surefire/Failsafe `argLine` JEP-498 workaround** (pom.xml lines 270, 285) — preserved as-is; new `<systemPropertyVariables>` lives alongside it.
- **`@SuppressFBWarnings({"CODE"}, justification="…")`** targeted SpotBugs suppression — applied case-by-case if the new System.getProperty reads or marker-file writes trip find-sec-bugs.

### Integration Points

- **Spring `SystemEnvironmentPropertySource` precedence** — pom.xml `<systemPropertyVariables>` becomes a JVM system property inside each forked JVM; Spring picks it up automatically above `application*.yml` defaults via the standard `Environment` property-source order. No `@PropertySource` or custom resolver required.
- **`MergedContextConfiguration` access from `TestExecutionListener`** — `TestContext#getApplicationContext()` returns the live context, but `MergedContextConfiguration` itself is accessible via `((DefaultTestContext) testContext).getMergedContextConfiguration()` (cast-dependent, but stable across Spring TCF generations). Alternative: read via reflection or expose via a `@TestPropertySource`-style helper. Planner picks the lowest-friction approach.
- **`BackupStagingCleanup` event timing** — fires on `ApplicationReadyEvent`, which is per-context. Under `forkCount=2 + reuseForks=true` with N IT classes per fork, the listener fires N times per fork on its OWN per-fork directory — proven race-free by D-17's dedicated IT.

</code_context>

<specifics>
## Specific Ideas

- **Plan-01 ordering rationale (D-01 first):** PERF-01 is the highest-risk lever because elevating Failsafe `forkCount` exposes test-isolation latent bugs that Phase 86 deferred. If `forkCount=2` causes flakes in non-backup ITs (e.g., shared filesystem state, port collisions, singleton-bean races), Plan-01 surfaces them and Plan-02/03 work on stabilised infrastructure.
- **Honest Wave-4 reporting (D-02):** the 10:24 Phase-86-post-audit local median is the baseline against which Plan 89-03 reports the delta — NOT the 09:45 pre-audit baseline (which would inflate apparent improvement). The 23:00 CI baseline is the eventual PERF-06 reference, not Plan 89-03's job.
- **Aggregator output format anchor (D-09):** "Top-5 by occurrence × cluster size" — `occurrence` = how many context-init events share the same hash; `cluster size` = how many distinct test classes share that hash. Product weights both "many tests rebuild the same context" (high occurrence, low cluster) AND "many tests fragment into distinct contexts" (low occurrence, high cluster).

</specifics>

<deferred>
## Deferred Ideas

- **Testcontainers `withReuse(true)` wiring** — Phase 86 D-15 Lever 3; deferred to Phase 90 PERF-04. Pre-emptive (no MariaDB IT yet to exercise).
- **Shared `@ContextConfiguration` consolidation (PERF-03 cluster lever)** — explicit Phase-90 scope; needs PERF-02 fingerprint data to identify the consolidation target. Acknowledged in `domain.Out of scope`.
- **CI-authoritative 5-run wallclock re-harvest** — Phase 91 PERF-06; D-02 explicitly defers CI numbers to that phase. Plan 89-03 stays local-only.
- **Aggressive `<forkCount>1C</forkCount>` (1 fork per CPU core)** — considered in Area 4 discussion, rejected for Phase 89 because of test-isolation risk + CI memory pressure. May surface as a Phase-90 follow-up if PERF-03 cluster consolidation makes the contexts cheaper per-fork.
- **`CLAUDE.md` Commands section: 3-seed verification command shortcut** — considered in Area 7; deferred since the existing `-Dit.test=…` discipline is already documented and per [[test-call-optimization]] adding more standard commands creates noise rather than clarity.
- **JaCoCo gate increase from 88.88 % → 89.0 %** — considered in Area 6; rejected because PERF phases don't add production-code coverage, so a strict-increase gate would block on a wrong metric.
- **Backup-staging Legacy-dir startup sweeper bean** — considered in Area 2 (Fork-Injection); rejected as YAGNI (gitignored dir, one-shot rm is enough per D-06). May reappear in a future cleanup phase if dev-machine clutter becomes a recurring complaint.

</deferred>

---

*Phase: 89-PERF Instrumentation & Lever 1 (Per-Fork Backup-Staging-Dir)*
*Context gathered: 2026-05-19*
