---
phase: 74
plan: "04"
subsystem: backup
tags: [backup, zip-hardening, streaming-json, jackson, path-traversal, bomb-defense]
dependency_graph:
  requires: ["74-02"]
  provides: ["readManifest(Path)", "countDataEntries(Path)", "countUploadFiles(Path)"]
  affects: ["74-05-BackupImportService"]
tech_stack:
  added: []
  patterns:
    - "nonClosingView(InputStream) inner-class guard prevents LimitedInputStream.close() cascade"
    - "AUTO_CLOSE_SOURCE=false on JsonParser mirrors writer-side AUTO_CLOSE_TARGET=false discipline"
    - "stack-local long[] inflatedAcc accumulator updated by LongConsumer onClose callback"
key_files:
  created:
    - src/test/java/org/ctc/backup/service/BackupArchiveServiceReadIT.java
  modified:
    - src/main/java/org/ctc/backup/service/BackupArchiveService.java
decisions:
  - "nonClosingView: wrap ZipInputStream in a no-op-close FilterInputStream before LimitedInputStream to prevent cascade close of the ZipInputStream when a per-entry LimitedInputStream is closed after parsing/draining."
  - "IOException wrapped as BackupArchiveException(MANIFEST_INVALID): reader methods declare only BackupArchiveException so callers (Plan 05) need a single catch branch."
  - "assertEntrySafe called AFTER entry consumed: cheaper arithmetic checks (entry count, total size) run before path-traversal resolution."
metrics:
  duration: "~130 min"
  completed_date: "2026-05-12"
  tasks_completed: 2
  files_changed: 2
---

# Phase 74 Plan 04: BackupArchiveService reader + ZIP hardening integration Summary

Streaming reader extension to `BackupArchiveService`: three public methods (`readManifest`, `countDataEntries`, `countUploadFiles`) that enforce ZIP-Slip and deflate-bomb defenses on every entry via `LimitedInputStream` + `PathTraversalGuard`, plus a 6-test `BackupArchiveServiceReadIT` covering all rejection paths.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add reader methods to BackupArchiveService | 8d0ca97 | `BackupArchiveService.java` (+327 lines) |
| 2 | BackupArchiveServiceReadIT (6 tests) + nonClosingView fix | fe17fb2 | `BackupArchiveServiceReadIT.java` (new), `BackupArchiveService.java` (+fix) |

## What Was Built

### `BackupArchiveService` reader extension (Plan 04 — D-20 single-class invariant)

Three new public methods:

- **`readManifest(Path)`** — Opens a Phase-73 export ZIP, asserts entry 0 is `manifest.json`, deserializes via `backupObjectMapper` (FAIL_ON_UNKNOWN_PROPERTIES=true). Throws `MANIFEST_MISSING` when entry 0 is wrong; `MANIFEST_INVALID` on Jackson failure.
- **`countDataEntries(Path)`** — Walks every `data/<slug>.json` entry via streaming `JsonParser` token-loop (no full-document buffering), returns `LinkedHashMap<tableName, rowCount>`. Table-name slug derived by `replace('-', '_')`.
- **`countUploadFiles(Path)`** — Counts non-directory `uploads/*` entries by draining each through an 8 KB discard buffer (central-directory size is spoofable; actual inflated bytes must be counted).

All three methods share:
- `openHardened(Path)` — opens `ZipInputStream`
- `assertEntrySafe(ZipEntry, Path, int, long)` — entry-count, total-bytes, path-traversal checks
- `nonClosingView(InputStream)` — no-op-close wrapper preventing `LimitedInputStream.close()` from cascading to `ZipInputStream.close()` inside per-entry loops

Phase-73 writer methods (`writeZip`, `writeJson`) are byte-identical — Plan 04 is purely additive.

### `BackupArchiveServiceReadIT` (Failsafe, 6 tests)

| Test | Scenario | Reason asserted |
|------|----------|-----------------|
| `givenPhase73Export_whenReadManifest_thenSchemaVersionEqualsOne` | Round-trip via `writeZip` | `schemaVersion == 1` |
| `givenManifestNotFirst_whenReadManifest_thenThrowsManifestMissing` | `data/foo.json` is entry 0 | `MANIFEST_MISSING` |
| `givenManifestMalformedJson_whenReadManifest_thenThrowsManifestInvalid` | `manifest.json` = `{` | `MANIFEST_INVALID` |
| `givenValidZip_whenCountDataEntries_thenReturnsPerTableRowCounts` | Phase-73 export, 24 tables | 24 keys, all >= 0, >= 1 non-zero |
| `givenZipSlipEntry_whenCountDataEntries_thenThrowsPathTraversal` | `../../etc/passwd` entry | `PATH_TRAVERSAL` |
| `givenEntryWithInflatedSizeExceedingLimit_whenCountDataEntries_thenThrowsEntryTooLarge` | `data/big.json` inflates to > 60 MB | `ENTRY_TOO_LARGE` |

All fixtures generated programmatically (D-25) — no binary blobs committed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed ZipInputStream close-cascade in countDataEntries / countUploadFiles**
- **Found during:** Task 2 (first test run: `givenValidZip_whenCountDataEntries_thenReturnsPerTableRowCounts` threw `IOException: Stream closed`)
- **Issue:** `LimitedInputStream.close()` → `FilterInputStream.close()` → `ZipInputStream.close()`. After closing the first entry's `LimitedInputStream`, the `ZipInputStream` was closed, making the next `getNextEntry()` throw `Stream closed`.
- **Fix:** Added `nonClosingView(InputStream)` private static helper that returns a `FilterInputStream` whose `close()` is a no-op. `LimitedInputStream` is constructed over `nonClosingView(zis)` instead of `zis` directly. The `LongConsumer onClose` still fires correctly (it fires when `LimitedInputStream.close()` is called, which now only releases the `LimitedInputStream`'s internal state — the `ZipInputStream` lifecycle remains managed by the outer `try-with-resources`).
- **Files modified:** `BackupArchiveService.java`
- **Commit:** fe17fb2

## Verification Results

All plan success criteria met:

```
grep ... | wc -l results:
  3 public reader methods present
  6 writer-method references present (not removed)
  0 sibling BackupArchiveReadService class
  1 @Qualifier("backupObjectMapper") constructor annotation
  0 stale Reason names (ZIP_SLIP / SCHEMA_VERSION_MISMATCH / MANIFEST_PARSE_FAILED)
  0 this::trackInflatedBytes references
  0 class ByteCountingInputStream
```

`./mvnw verify` → **BUILD SUCCESS** (161 IT tests, 0 failures, 1 skipped pre-existing)

`BackupArchiveServiceReadIT`: **6/6** green
`BackupArchiveServiceIT` (Phase 73): **3/3** still green

## Known Stubs

None — all three reader methods are fully implemented and wired.

## Threat Flags

None — Plan 04 adds no new network endpoints, auth paths, or schema changes. The reader is a pure in-memory parse pass over a caller-supplied Path; the ZIP is never extracted to disk.

## Self-Check: PASSED

- `src/main/java/org/ctc/backup/service/BackupArchiveService.java` — exists, contains `readManifest`, `countDataEntries`, `countUploadFiles`
- `src/test/java/org/ctc/backup/service/BackupArchiveServiceReadIT.java` — exists, 6 test methods
- Commit `8d0ca97` — verified in git log
- Commit `fe17fb2` — verified in git log
- Phase-73 writer methods byte-identical: `git diff 5c2c462..HEAD -- BackupArchiveService.java | grep '^-' | grep -v '^---'` → empty
- No `MANIFEST_PARSE_FAILED`, `ZIP_SLIP`, `SCHEMA_VERSION_MISMATCH` anywhere in src/
