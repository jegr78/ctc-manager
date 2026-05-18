---
phase: "74"
plan: "07"
subsystem: backup
tags: [startup-sweep, event-listener, staging-cleanup, integration-test]
dependency_graph:
  requires: ["74-01"]
  provides: ["BackupStagingCleanup startup sweep (D-17)"]
  affects: []
tech_stack:
  added: []
  patterns: ["@EventListener(ApplicationReadyEvent.class)", "OutputCaptureExtension", "@DynamicPropertySource + @TempDir"]
key_files:
  created:
    - src/main/java/org/ctc/backup/service/BackupStagingCleanup.java
    - src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java
  modified: []
decisions:
  - "Explicit constructor used for @Value Path injection instead of Lombok @RequiredArgsConstructor (Lombok does not lift @Value onto generated constructor parameter)"
  - "OutputCaptureExtension chosen over Logback ListAppender — zero plumbing, string match aligns with D-17 contract"
  - "Per-test Files.createDirectories(tempStagingDir) to guard against test-order sensitivity with static @TempDir"
metrics:
  duration: "~20 minutes"
  completed: "2026-05-12"
  tasks_completed: 2
  files_created: 2
---

# Phase 74 Plan 07: BackupStagingCleanup — Startup Sweep Listener

**One-liner:** `@Component` startup sweep via `ApplicationReadyEvent` that deletes orphaned `upload-*.zip` files from the staging dir on every JVM boot (D-17 safety net).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 74-07-01 | Create `BackupStagingCleanup` `@Component` | a315310 | `BackupStagingCleanup.java` (new) |
| 74-07-02 | Add `BackupStagingCleanupIT` integration test | 218276c | `BackupStagingCleanup.java` (fix), `BackupStagingCleanupIT.java` (new) |

## Decisions Made

1. **Explicit constructor for `@Value` injection:** Lombok `@RequiredArgsConstructor` does not transfer `@Value` annotations onto generated constructor parameters — Spring Boot then attempts type-based autowiring of `java.nio.file.Path`, which fails with `NoSuchBeanDefinitionException`. Fixed by writing an explicit package-private constructor with `@Value` on the parameter, consistent with the project's `BackupArchiveService` pattern for similar injection cases.

2. **`OutputCaptureExtension` for log assertions:** First use of this Spring Boot Test extension in the CTC codebase. Chosen over `ListAppender<ILoggingEvent>` because D-17 pins the contract to the exact formatted string `Cleared {N} stale staging files` — a string match is semantically correct and requires zero Logback plumbing.

3. **`Files.createDirectories` guard in each writing test:** `@TempDir static` is shared across all test methods. Test 3 (`givenStagingDirDoesNotExist`) calls `Files.deleteIfExists(tempStagingDir)` to simulate a missing staging dir. Without defensive `createDirectories` in Tests 1, 2, and 4, those tests fail with `NoSuchFileException` if they run after Test 3. Each test that writes files now calls `Files.createDirectories(tempStagingDir)` first.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed `@Value` constructor injection incompatible with Lombok `@RequiredArgsConstructor`**
- **Found during:** Task 74-07-02 (IT execution)
- **Issue:** `@Component` context load failed: `No qualifying bean of type 'java.nio.file.Path' available`. Spring attempted type-based autowiring for the `Path stagingDir` field because Lombok's generated constructor did not carry the `@Value` annotation.
- **Fix:** Replaced `@RequiredArgsConstructor` + field-level `@Value` with an explicit package-private constructor `BackupStagingCleanup(@Value("${app.backup.staging-dir}") Path stagingDir)`.
- **Files modified:** `src/main/java/org/ctc/backup/service/BackupStagingCleanup.java`
- **Commit:** 218276c (combined with IT task)

**2. [Rule 1 - Bug] Added `Files.createDirectories` guard for test-order safety**
- **Found during:** Task 74-07-02 (IT execution — Test 1 failed with `NoSuchFileException` when run after Test 3)
- **Issue:** `@TempDir static` is shared; Test 3 deletes the directory. Tests 1 and 2 did not recreate it before writing fixture files.
- **Fix:** Added `Files.createDirectories(tempStagingDir)` at the start of Tests 1 and 2.
- **Files modified:** `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java`
- **Commit:** 218276c

## Test Results

```
./mvnw failsafe:integration-test failsafe:verify -Dit.test=BackupStagingCleanupIT
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

Four D-17 scenarios verified:
- `givenThreeStaleStagingFiles_whenApplicationReady_thenAllDeletedAndCountLogged` — PASS
- `givenEmptyStagingDir_whenApplicationReady_thenLogsZeroCleared` — PASS
- `givenStagingDirDoesNotExist_whenApplicationReady_thenNoCleanupLogEmitted` — PASS
- `givenUnrelatedFileAndOneStaleStagingFile_whenApplicationReady_thenOnlyStagingFileDeleted` — PASS

## Known Stubs

None.

## Threat Flags

None — this component has no network endpoints, no auth paths, and no schema changes. It performs local filesystem cleanup within the configured staging directory only.

## Self-Check: PASSED
