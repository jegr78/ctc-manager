---
phase: 28-raceattachment-security
fixed_at: 2026-04-13T16:09:30Z
review_path: .planning/phases/28-raceattachment-security/28-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 28: Code Review Fix Report

**Fixed at:** 2026-04-13T16:09:30Z
**Source review:** .planning/phases/28-raceattachment-security/28-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (WR-01, WR-02, WR-03; IN-01 excluded by fix_scope=critical_warning)
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: Unchecked `null` from `getOriginalFilename()` in `uploadAttachment`

**Files modified:** `src/main/java/org/ctc/domain/service/RaceAttachmentService.java`, `src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java`
**Commit:** fb72def
**Applied fix:** Added null/blank guard for `getOriginalFilename()` before the repository call in `uploadAttachment`. The guard is placed before `raceRepository.findById()` so the `IllegalArgumentException` fires immediately without hitting the DB. Added two new tests: `givenNullFilename_whenUploadAttachment_thenThrowsIllegalArgument` and `givenBlankFilename_whenUploadAttachment_thenThrowsIllegalArgument`.

---

### WR-02: Unconditional `substring` on stored URL crashes on unexpected format

**Files modified:** `src/main/java/org/ctc/domain/service/RaceAttachmentService.java`, `src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java`
**Commit:** 9f87b40
**Applied fix:** Added a `!url.startsWith("/uploads/")` guard before the `url.substring(...)` call in `downloadAttachment`. Returns `400 Bad Request` with a warning log when the URL format is unexpected. Added test `givenUnexpectedUrlFormat_whenDownloadAttachment_thenReturnsBadRequest` to lock in this behaviour.

---

### WR-03: Double file extension in `Content-Disposition` filename

**Files modified:** `src/main/java/org/ctc/domain/service/RaceAttachmentService.java`, `src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java`
**Commit:** f18b9da
**Applied fix:** Replaced `safeName + getExtension(file)` with just `safeName` in the `Content-Disposition` header (Option A from the review). The stored `name` field already includes the extension from the original filename. The now-unused `getExtension(Path)` private method was also removed. Added test `givenFilenameWithExtension_whenDownloadAttachment_thenContentDispositionHasNoDoubleExtension` to verify a single extension is produced.

---

_Fixed: 2026-04-13T16:09:30Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
