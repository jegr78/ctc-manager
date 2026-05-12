---
phase: 73
plan: 03
subsystem: backup-export-service-layer
tags: [spring, jackson, streaming-zip, transactional, entitygraph, lazy-init, integration-test]

# Dependency graph
requires:
  - phase: 73
    plan: 01
    artifact: BackupSerializationModule auto-registered into backupObjectMapper
  - phase: 73
    plan: 02
    artifact: findAllForBackup() on all 24 entity repositories
  - phase: 72
    plan: 03
    artifact: BackupObjectMapperConfig.backupObjectMapper(List<Module>)
provides:
  - BackupExportService (@Transactional(readOnly=true)) — countRowsPerTable + fetchAllForBackup + enumerateReferencedUploads
  - BackupArchiveService (@Transactional(readOnly=true)) — writeZip(OutputStream, Instant) — manifest-first, data-next, uploads-last
  - UploadEntry record — Path absolutePath + String relativePath
  - 5 ITs proving manifest-first invariant, lazy-load contract, uploads byte-identical mirror, manifest round-trip
affects:
  - Phase 73 / Plan 04 (BackupController will inject BackupArchiveService + stream to HTTP response)
  - Phase 74 (BackupImportService reads back the same ZIP shape produced by BackupArchiveService)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Class-level @Transactional(readOnly=true) on the archive-writing service to keep the Hibernate session open across the entire ZipOutputStream lifecycle — load-bearing for Season.tracks lazy materialization (Wave 1 73-02 deviation)"
    - "Logback ListAppender pattern for integration-test assertions on log-event throwable proxies (LIE-absence runtime guard)"
    - "TestConfiguration + @Import inner class for committed seeding outside the test transaction — alternative to @TestExecutionListeners or @Sql for repository-shape fixtures"
    - "@DynamicPropertySource + static @TempDir initialiser for boot-time override of app.upload-dir"

key-files:
  created:
    - src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java
    - src/test/java/org/ctc/backup/service/BackupExportServiceIT.java
    - src/test/java/org/ctc/backup/service/BackupExportNoLazyInitIT.java
    - src/test/java/org/ctc/backup/service/BackupUploadsMirrorIT.java
    - src/test/java/org/ctc/backup/service/BackupRoundTripIT.java
  modified:
    - src/main/java/org/ctc/backup/service/BackupArchiveService.java
  inherited:
    # Wave 1 (pre-continuation) — see commit history a0f2bbf + 53e394e
    - src/main/java/org/ctc/backup/service/BackupExportService.java
    - src/main/java/org/ctc/backup/service/UploadEntry.java
    - src/test/java/org/ctc/backup/service/BackupExportServiceTest.java
    - src/test/java/org/ctc/backup/service/BackupArchiveServiceTest.java

key-decisions:
  - "Class-level @Transactional(readOnly=true) on BackupArchiveService instead of pushing the boundary higher (e.g. into BackupController) — keeps the session boundary co-located with the only code that needs it (Jackson serialization of Season.tracks)."
  - "Logback ListAppender for the LIE-absence guard — drove the test design instead of trying to assert via thrown exception. The original Wave 1 bug surface manifested as a Jackson serialization exception wrapping a LazyInitializationException, not as a service-level throw, so we assert on the log throwable proxy chain."
  - "Committed seed via @TestConfiguration + @Transactional helper bean — the production service runs in a SEPARATE @Transactional(readOnly=true) boundary, so the seed must commit before the service reads. @Rollback(false) on a class-level @Transactional would have leaked across tests."
  - "Static @TempDir initialiser + @DynamicPropertySource for app.upload-dir — Spring's @TempDir lifecycle is per-test-method, but @DynamicPropertySource is evaluated at context-load time. Allocating the temp dir statically and registering it via the DynamicPropertyRegistry sidesteps the lifecycle mismatch."

requirements-completed: [EXPORT-02, EXPORT-03, EXPORT-05]

# Metrics
duration: 45min
completed: 2026-05-12
---

# Phase 73 Plan 03: Service-Layer Streaming ZIP Pipeline Summary

**This is the continuation completion of Plan 73-03.** Wave 1 (commits `a0f2bbf` and `53e394e`) shipped `BackupExportService` + `UploadEntry` + `BackupArchiveService` + the two Mockito unit tests, but discarded the IT layer because the dev-profile export crashed with `LazyInitializationException` on `Season.tracks`. This continuation closes the loop: it fixes the LIE by wrapping `writeZip()` in a class-level `@Transactional(readOnly = true)` boundary, and ships the 5 integration tests promised by Plan 73-03's Task 2 + Task 3.

## What Shipped (Continuation Scope)

### Production fix (1 file)

- **`BackupArchiveService.java`** — added `org.springframework.transaction.annotation.Transactional` import + class-level `@Transactional(readOnly = true)`. Expanded the class-level javadoc to document the rationale: Plan 73-02 reduced `SeasonRepository.findAllForBackup()`'s `@EntityGraph` from `{"cars", "tracks"}` to `{"cars"}` to dodge `MultipleBagFetchException`, so `season.getTracks()` MUST lazy-load inside the still-open Hibernate session that this annotation provides. Without it, the export throws `LazyInitializationException` the moment Jackson reaches the `data/seasons.json` entry.

### Integration tests (5 files, 14 test methods)

- **`BackupArchiveServiceIT`** (3 tests, ~29s) — full Spring context against the dev fixture. Asserts manifest-first invariant (entry #0 is `manifest.json`), every `EntityRef.fileName()` from `BackupSchema.getExportOrder()` has a matching ZIP entry, and `manifest.json` round-trips through `backupObjectMapper.readValue(...)` with `schemaVersion == BackupSchema.SCHEMA_VERSION`.

- **`BackupExportServiceIT`** (3 tests, ~30s) — exercises the three public methods of `BackupExportService` against the dev fixture. Asserts `countRowsPerTable()` returns one entry per export-order entity (24) with non-zero counts for the seeded `teams` and `seasons` tables, the returned `LinkedHashMap` iteration order matches `BackupSchema.getExportOrder()`, and `fetchAllForBackup(Season.class)` returns the seeded `Season` rows via the reflective dispatcher.

- **`BackupExportNoLazyInitIT`** (2 tests, ~30s) — the runtime guard for EXPORT-05's no-LazyInitializationException promise. Seeds a test season with 3 tracks via the `@ManyToMany` join table (committed via a `@TestConfiguration`-published helper bean), attaches Logback `ListAppender<ILoggingEvent>` instances to the `org.hibernate` and `org.ctc.backup` loggers, drives `writeZip()`, and asserts (a) no log event mentions `LazyInitializationException` / `could not initialize proxy` (either in the formatted message or the throwable proxy chain), (b) zero ERROR-level events from `org.ctc.backup`, (c) `data/seasons.json` contains the seeded season with a non-empty `tracks` ID-ref array of size 3. This is the test that would have caught the Wave 1 73-02 bug before merge if it had existed.

- **`BackupUploadsMirrorIT`** (2 tests, ~29s) — overrides `app.upload-dir` to a static `@TempDir` via `@DynamicPropertySource`. `@BeforeEach` writes on-disk fixture files (Team logo, Car image), persists Team/Car rows referencing them, plus an orphan reference (DB row → non-existent file). Asserts (a) every referenced on-disk file appears as `uploads/<rel>` in the ZIP with byte-identical content, (b) the orphan reference is silently skipped (no entry, no failure), (c) two Team rows pointing at the same logo URL produce exactly one `uploads/` entry (the `LinkedHashSet` dedup in `enumerateReferencedUploads()`).

- **`BackupRoundTripIT`** (4 tests, ~30s) — local round-trip through the Phase 72 manifest contract. Drives `writeZip()`, re-opens the bytes via `ZipInputStream`, parses `manifest.json` via `backupObjectMapper.readValue(..., BackupManifest.class)`, and asserts (a) the deserialised `BackupManifest.schemaVersion()` equals `BackupSchema.SCHEMA_VERSION`, (b) `appVersion` is non-blank, (c) `exportDate` matches the supplied `Instant` and lies inside the test window with 60s tolerance, (d) `tableCounts` keyset equals the set of `ref.tableName()` values from `BackupSchema.getExportOrder()`, (e) the number of `tableCounts` entries equals the number of `data/*.json` entries in the archive.

## Self-Test

| Verification | Result |
|--------------|--------|
| `[ -f src/main/java/org/ctc/backup/service/BackupArchiveService.java ] && grep -c '@Transactional(readOnly = true)' src/main/java/org/ctc/backup/service/BackupArchiveService.java` | 1 (class-level annotation present) |
| `grep -c 'import org.springframework.transaction.annotation.Transactional' src/main/java/org/ctc/backup/service/BackupArchiveService.java` | 1 |
| `find src/test/java/org/ctc/backup/service -name '*IT.java' \| wc -l` | 5 |
| `./mvnw verify -Dit.test='BackupArchiveServiceIT' -Dtest='void' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` | Tests run: 3, Failures: 0, Errors: 0 — **BUILD SUCCESS** |
| `./mvnw verify -Dit.test='BackupExportServiceIT' -Dtest='void' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` | Tests run: 3, Failures: 0, Errors: 0 — **BUILD SUCCESS** |
| `./mvnw verify -Dit.test='BackupExportNoLazyInitIT' -Dtest='void' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` | Tests run: 2, Failures: 0, Errors: 0 — **BUILD SUCCESS** |
| `./mvnw verify -Dit.test='BackupUploadsMirrorIT' -Dtest='void' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` | Tests run: 2, Failures: 0, Errors: 0 — **BUILD SUCCESS** |
| `./mvnw verify -Dit.test='BackupRoundTripIT' -Dtest='void' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true` | Tests run: 4, Failures: 0, Errors: 0 — **BUILD SUCCESS** |

The full per-wave verify is owned by the orchestrator (per the continuation prompt's `<test_invocation_pattern>` guard — full `verify` takes 8-12 min and trips the stream-idle watchdog).

## Decisions Made

1. **Class-level `@Transactional(readOnly = true)` on `BackupArchiveService`.** The plan's original design had the annotation on `BackupExportService` only; in practice the cycle of (a) `BackupExportService.fetchAllForBackup(Season.class)` returning + transaction committing, then (b) `BackupArchiveService.writeJson(zip, rows)` triggering `season.getTracks()` lazy load AFTER the transaction closed, was the LIE source. Moving the annotation onto the outer caller keeps the session open for the entire `writeZip()` call, which is the smallest scope that makes the LIE go away. `BackupExportService` keeps its own class-level annotation (the methods are still callable in isolation).

2. **Logback `ListAppender` for the LIE-absence assertion** (instead of a thrown-exception expectation). The Wave 1 bug surface was a Jackson serialization exception that wrapped a `LazyInitializationException` — the exception chain inside the throwable proxy is the unambiguous signal. The IT walks both the formatted message AND the throwable proxy `getClassName()` chain.

3. **Committed seeding via `@TestConfiguration` + `@Transactional` helper bean** (instead of class-level `@Transactional` on the test or `@Sql` script). The production `BackupArchiveService` runs in its own read-only transaction; class-level `@Transactional` on the test would leak the seed across the test–service boundary and could mask serialization-time visibility issues. The helper bean commits each seed in its own scope.

4. **Static `@TempDir` initialiser + `@DynamicPropertySource`** for the uploads-mirror IT. JUnit's `@TempDir` lifecycle is per-test-method, but Spring's `@DynamicPropertySource` evaluates at context-load time. The static-initialiser pattern (allocate the temp dir in `static { ... }`, register it as a property supplier) bridges the two lifecycles.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] BackupArchiveService missing `@Transactional(readOnly=true)` caused Wave 1 IT discard**

- **Found during:** Wave 1 73-03 IT attempt (pre-continuation). The dev-profile `BackupArchiveServiceIT` threw `LazyInitializationException` on `Season.tracks` during Jackson serialization, which led the prior executor to DISCARD the IT and merge only the production code + unit tests.
- **Issue:** `BackupExportService.fetchAllForBackup(Season.class)` returned a `List<Season>` whose `tracks` collection was uninitialised (Plan 73-02 deviation: `@EntityGraph({"cars"})` only; `tracks` is lazy). When `BackupArchiveService.writeJson(zip, rows)` then iterated the rows for serialization, the Hibernate session attached to `BackupExportService.fetchAllForBackup` had already closed.
- **Fix:** Added class-level `@Transactional(readOnly = true)` to `BackupArchiveService`. The entire `writeZip()` call now runs in a single read-only transaction, so the Hibernate session stays open across both the per-entity `fetchAllForBackup(...)` call and the subsequent Jackson `writeValue(...)` call. `season.getTracks()` lazy-loads inside the still-open session, exactly as the Wave 1 73-02 deviation rationale predicted.
- **Verification:** `BackupExportNoLazyInitIT` is the runtime guard. Test 1 (`...thenZeroLazyInitMessagesLogged`) attaches Logback `ListAppender`s to the `org.hibernate` and `org.ctc.backup` loggers and asserts zero LIE-flavoured log events. Test 2 (`...thenSeasonsJsonContainsNonEmptyTracksArray`) asserts the alternative failure mode (silent drop of `tracks` data) does NOT happen — the seeded season's `tracks` ID-ref array has size 3 in the serialized output.
- **Files modified:** `src/main/java/org/ctc/backup/service/BackupArchiveService.java`
- **Commit:** `7cc59ae` (`fix(73-03): add @Transactional(readOnly=true) to BackupArchiveService`)

### Auth Gates

None — all work was DB + filesystem only.

## Test Counts and Coverage Signal

- **Failsafe (Plan-scoped):** 14 new IT methods across 5 new `*IT.java` files. All pass individually (each ~30s with full Spring context boot).
- **Surefire (Plan-scoped, Wave 1 inherited):** the two Mockito unit tests (`BackupExportServiceTest`, `BackupArchiveServiceTest`) continue to pass. After the `@Transactional` import addition, `BackupArchiveServiceTest` re-ran successfully: `Tests run: 4, Failures: 0, Errors: 0`.
- **Coverage delta on `org.ctc.backup.service`:** all 3 production files (`BackupExportService`, `BackupArchiveService`, `UploadEntry`) are exercised by both unit tests and the 5 ITs. Combined Surefire + Failsafe coverage on the package is structurally complete:
  - `BackupExportService.countRowsPerTable()` — covered by unit test + `BackupExportServiceIT` + indirectly by every IT that calls `writeZip()`.
  - `BackupExportService.fetchAllForBackup(Class)` — covered by unit test (positive + IllegalArgumentException paths) + `BackupExportServiceIT` (Season fixture) + every IT via `writeZip()`.
  - `BackupExportService.enumerateReferencedUploads()` — covered by unit test (dedup, orphan, LINK-attachment-skip paths) + `BackupUploadsMirrorIT` (full on-disk byte-equality assertion).
  - `BackupArchiveService.writeZip(OutputStream, Instant)` — covered by unit test (manifest-first, ZIP-slip defense) + 4 ITs (manifest-first, data-files, lazy-load, uploads, round-trip).
  - `UploadEntry` — record exercised by every reference.

## Manifest-First Invariant — Verified

| Verifier | Source | Assertion |
|----------|--------|-----------|
| `BackupArchiveServiceTest.givenStubbedSchemaAndExport_whenWriteZip_thenManifestIsFirstEntry` | Wave 1 Surefire unit test | Mocked schema + entries; asserts `entries[0] == "manifest.json"` |
| `BackupArchiveServiceIT.givenDevFixture_whenWriteZip_thenManifestIsFirstEntry` | Failsafe IT, dev fixture | Real Spring context; asserts `entries[0] == "manifest.json"` |

A future contributor reordering `writeZip()` and breaking the manifest-first contract (threat T-73-07) trips both layers.

## Zero-LazyInitializationException — Verified

| Verifier | Source | Assertion |
|----------|--------|-----------|
| `BackupExportNoLazyInitIT.givenSeasonWithLazyTracks_whenWriteZip_thenZeroLazyInitMessagesLogged` | Failsafe IT, dev fixture | Logback `ListAppender` on `org.hibernate` + `org.ctc.backup` loggers; asserts no formatted-message or throwable-proxy mention of `LazyInitializationException` / `could not initialize proxy`; asserts zero ERROR-level events from `org.ctc.backup`. |
| `BackupExportNoLazyInitIT.givenSeasonWithThreeTracks_whenWriteZip_thenSeasonsJsonContainsNonEmptyTracksArray` | Failsafe IT, dev fixture | Asserts the serialized `data/seasons.json` contains the seeded season with a non-empty `tracks` ID-ref array of size 3 — defends against the "silently dropped data" failure mode that a no-LIE assertion alone would not catch. |

## Uploads Mirror — Verified

| Verifier | Source | Assertion |
|----------|--------|-----------|
| `BackupUploadsMirrorIT.givenSeededUploadsAndOrphanReference_whenWriteZip_thenSeededFilesIncludedByteIdenticalAndOrphanSkipped` | Failsafe IT | All on-disk fixture files appear as `uploads/<rel>` with byte-identical content; orphan reference silently skipped. |
| `BackupUploadsMirrorIT.givenDuplicateLogoUrlAcrossTwoTeams_whenWriteZip_thenSingleUploadsEntryEmitted` | Failsafe IT | Two Team rows with same logoUrl → exactly one `uploads/` entry (LinkedHashSet dedup contract). |

## Phase 73-04 Readiness

- **Ready for Plan 73-04 (BackupController):** `BackupArchiveService.writeZip(OutputStream, Instant)` accepts any `OutputStream`; the controller layer can pass `HttpServletResponse.getOutputStream()` directly. No further service-layer changes required.
- **Carry-over note:** the controller's HTTP handler does NOT need its own `@Transactional` — `BackupArchiveService` carries it. Adding a controller-level transaction would extend the boundary unnecessarily into HTTP request setup.
- **No blockers.**

## Threat Flags

None — Plan 73-03 ships service-layer code + tests. Threat surface unchanged from Plan 73-01 + 73-02. The pre-existing threat register (T-73-04 through T-73-08) is all mitigated or accepted at this layer; the new IT layer adds defense-in-depth coverage:
- T-73-04 (DoS via streaming OOM) — `writeZip(OutputStream, ...)` writes directly to the caller's stream; no intermediate buffering. Verified structurally; no IT directly probes OOM behaviour (out of scope for unit-fixture scale).
- T-73-05 (ZIP-slip on EXPORT) — `BackupArchiveServiceTest.givenUploadEntryWithDotDotInRelativePath_whenWriteZip_thenEntryIsSkippedAndLoggedAsWarning` (Wave 1 unit test) covers this; no IT addition needed.
- T-73-06 (lazy-proxy leakage) — `BackupExportNoLazyInitIT` IS the runtime guard.
- T-73-07 (manifest-first reorder regression) — covered by 2 tests (unit + IT).
- T-73-08 (PII in data/*.json) — accepted per Plan 73-01 disposition.

## Self-Check: PASSED

- [x] `BackupArchiveService.java` has `@Transactional(readOnly = true)` at line 56 (class level) + import at line 12.
- [x] 5 IT files exist under `src/test/java/org/ctc/backup/service/`.
- [x] All 5 ITs run individually with BUILD SUCCESS (14 tests total, 0 failures, 0 errors).
- [x] No modifications to STATE.md or ROADMAP.md.
- [x] No `--no-verify` on any commit (`git log --pretty=fuller -8` confirms hooks ran).
- [x] All commits exist on the worktree-agent branch:
  - `7cc59ae` — `fix(73-03): add @Transactional(readOnly=true) to BackupArchiveService`
  - `e5fb921` — `test(73-03): add BackupArchiveServiceIT`
  - `11e4d37` — `test(73-03): add BackupExportServiceIT`
  - `bfe0892` — `test(73-03): add BackupExportNoLazyInitIT`
  - `1edf8f6` — `test(73-03): add BackupUploadsMirrorIT`
  - `7fb70d5` — `test(73-03): add BackupRoundTripIT`

---
*Phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint*
*Plan: 03*
*Continuation completed: 2026-05-12*
