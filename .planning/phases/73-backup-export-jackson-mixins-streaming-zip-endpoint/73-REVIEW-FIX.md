---
phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint
fixed_at: 2026-05-12T11:50:00Z
review_path: .planning/phases/73-backup-export-jackson-mixins-streaming-zip-endpoint/73-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase 73: Code Review Fix Report

**Fixed at:** 2026-05-12T11:50:00Z
**Source review:** `.planning/phases/73-backup-export-jackson-mixins-streaming-zip-endpoint/73-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (1 Critical + 6 Warnings)
- Fixed: 7
- Skipped: 0
- Info findings (5): out of scope (fix_scope=critical_warning)

## Fixed Issues

### CR-01: enumerateReferencedUploads() lacks upload-root scope check

**Files modified:**
- `src/main/java/org/ctc/backup/service/BackupExportService.java`
- `src/test/java/org/ctc/backup/service/BackupExportServiceTest.java`

**Commit:** `6ae746d`

**Applied fix:** Added defense-in-depth `absolute.startsWith(uploadRoot)`
check in `enumerateReferencedUploads()` BEFORE the `Files.exists()` probe.
Path-traversal references like `/uploads/../../../../etc/passwd` are now
rejected at the enumerator boundary with a WARN log, preventing
filesystem-state disclosure outside `uploadRoot` via timing side channels.
Added `givenPathTraversalLogoUrl_whenEnumerateReferencedUploads_thenRejectedAtEnumeratorBoundary`
test that asserts an empty result for a malicious `Team.logoUrl`.
Verified: 7/7 `BackupExportServiceTest` cases green.

---

### WR-01: BackupArchiveService.writeJson() leaves JsonGenerator un-closed

**Files modified:**
- `src/main/java/org/ctc/backup/service/BackupArchiveService.java`

**Commit:** `b47ae80`

**Applied fix:** Wrapped `backupObjectMapper.writeValue(generator, value)`
in `try`/`finally` and replaced the bare `generator.flush()` with
`generator.close()` in the finally branch. With `AUTO_CLOSE_TARGET=false`,
`close()` flushes and releases the generator's internal state without
touching the underlying `ZipOutputStream` — exactly what streaming requires
— and prevents Jackson buffer references from lingering until GC.
Updated the trailing comment to reflect the corrected rationale.
Verified: 4/4 `BackupArchiveServiceTest` cases green.

---

### WR-02: BackupController catches IOException only

**Files modified:**
- `src/main/java/org/ctc/backup/BackupController.java`

**Commit:** `8dc5836`

**Applied fix:** Added a `catch (RuntimeException e)` defense-in-depth
branch to the `StreamingResponseBody` lambda that logs with the filename
context (`log.error("Backup export failure mid-stream (filename={})", filename, e)`)
and rethrows. Non-IO exceptions raised mid-stream
(`LazyInitializationException`, Jackson serialization errors,
`NoSuchElementException`, etc.) now surface a clear controller-level log
entry before Spring's async dispatch unwinds the stack.
Verified: 3/3 `BackupControllerTest` cases green.

---

### WR-03: BackupArchiveService.writeZip() Javadoc contradicts ZipOutputStream close semantics

**Files modified:**
- `src/main/java/org/ctc/backup/service/BackupArchiveService.java`

**Commit:** `4ac12ec`

**Applied fix:** Rewrote the writeZip Javadoc to match the JDK
`ZipOutputStream.close()` contract: closing the ZIP writes the central
directory AND closes the delegate stream. Documented that in production
this is desired behaviour because Spring's `StreamingResponseBody` closes
the servlet response output stream only after the controller returns.
Verified: 4/4 `BackupArchiveServiceTest` cases still green (docs-only).

---

### WR-04: BackupUploadsMirrorIT has dead `Paths.get(".")` call

**Files modified:**
- `src/test/java/org/ctc/backup/service/BackupUploadsMirrorIT.java`

**Commit:** `a2cdc75`

**Applied fix:** Removed the dead `Paths.get(".");` call inside
`readUploadEntries()` and the now-unnecessary `import java.nio.file.Paths;`
import. No functional change.
Verified: 2/2 `BackupUploadsMirrorIT` cases green.

---

### WR-05: BackupExportService loses ReflectiveOperationException entity-class context

**Files modified:**
- `src/main/java/org/ctc/backup/service/BackupExportService.java`

**Commit:** `badcc83`

**Applied fix:** Wrapped the unwrapped `RuntimeException` cause in an
`IllegalStateException` carrying the
`"Reflective invocation of findAllForBackup() on <EntityClass> failed"`
context on BOTH branches of the ReflectiveOperationException catch.
The original `RuntimeException` remains as the cause so the full stack
trace is preserved.
Verified: 7/7 `BackupExportServiceTest` cases green.

---

### WR-06: BackupControllerSecurityIT missing anonymous+no-CSRF assertion

**Files modified:**
- `src/test/java/org/ctc/backup/BackupControllerSecurityIT.java`

**Commit:** `3c18839`

**Applied fix:** Added `givenAnonymousNoCsrf_whenPostExport_thenForbidden`
test in `ProdProfileSecurityTest` that asserts an anonymous POST without
CSRF returns 403 — locking the layered defence (CsrfFilter rejects with
403 BEFORE AuthorizationFilter runs).
Verified: 6/6 `BackupControllerSecurityIT` cases green (4 prod + 2 dev).

## Skipped Issues

None — all 7 in-scope findings were fixed successfully.

## Out-of-Scope Info Findings

The following 5 Info findings from REVIEW.md are NOT fixed in this
iteration (fix_scope=critical_warning):

- IN-01: Cache `Method` reference for `findAllForBackup()` at `@PostConstruct`
- IN-02: Cross-link `SeasonRepository.findAllForBackup()` rationale from `BackupArchiveService` Javadoc
- IN-03: Add exact `isoSafeFilename(Instant)` pin test
- IN-04: Use derived query for `seedSeasonWithTracks()` cleanup
- IN-05: Add CSRF auto-injection comment to `backup.html`

---

_Fixed: 2026-05-12T11:50:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
