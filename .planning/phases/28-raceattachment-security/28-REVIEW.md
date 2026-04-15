---
phase: 28-raceattachment-security
reviewed: 2026-04-13T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - src/main/java/org/ctc/domain/service/RaceAttachmentService.java
  - src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java
findings:
  critical: 0
  warning: 3
  info: 1
  total: 4
status: issues_found
---

# Phase 28: Code Review Report

**Reviewed:** 2026-04-13
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

Reviewed `RaceAttachmentService` and its test class. The service contains solid security
foundations: path traversal protection in `downloadAttachment`, URL scheme validation in
`addLink`, and header injection sanitization in the `Content-Disposition` header. The
accompanying test suite covers all critical security paths.

Three warnings were identified: a null-dereference risk from unchecked `getOriginalFilename()`
in `uploadAttachment`, a crash risk from an unconditional substring on the stored URL in
`downloadAttachment`, and a double-extension bug in the generated `Content-Disposition`
filename. One info item relates to a missing test case.

---

## Warnings

### WR-01: Unchecked `null` from `getOriginalFilename()` in `uploadAttachment`

**File:** `src/main/java/org/ctc/domain/service/RaceAttachmentService.java:42-44`

**Issue:** `MultipartFile.getOriginalFilename()` is documented as nullable (returns `null`
when the filename is not known or when the part has no filename). The result is used
directly at line 42 as the `name` argument to `RaceAttachment(...)`, whose JPA column is
`nullable = false`, and returned at line 44. This causes a `NullPointerException` at
runtime when the multipart request omits the filename header (e.g., a programmatic HTTP
client or malformed browser request).

**Fix:**
```java
@Transactional
public String uploadAttachment(UUID raceId, MultipartFile file) {
    var race = raceRepository.findById(raceId).orElseThrow();
    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null || originalFilename.isBlank()) {
        throw new IllegalArgumentException("Filename is required");
    }
    try {
        String url = fileStorageService.store(raceId, file);
        var attachment = new RaceAttachment(race, AttachmentType.FILE, originalFilename, url);
        raceAttachmentRepository.save(attachment);
        return originalFilename;
    } catch (IOException e) {
        log.error("Upload failed for race {}", raceId, e);
        throw new RuntimeException(e.getMessage(), e);
    }
}
```

---

### WR-02: Unconditional `substring` on stored URL crashes on unexpected format

**File:** `src/main/java/org/ctc/domain/service/RaceAttachmentService.java:82`

**Issue:** `downloadAttachment` calls `url.substring("/uploads/".length())` without
first verifying that `url` actually starts with `/uploads/`. If a `FILE`-type attachment
was persisted with a URL that does not start with `/uploads/` (e.g., due to data migration,
a bug in an older code path, or a direct DB write), this produces an incorrect path
silently (if the URL is shorter than 9 chars it throws `StringIndexOutOfBoundsException`).
The path traversal check on line 83 would then fail to protect correctly because
`file` is already computed from a wrong base.

**Fix:**
```java
String url = attachment.getUrl();
if (!url.startsWith("/uploads/")) {
    log.warn("Attachment URL has unexpected format, attachmentId={}", attachmentId);
    return ResponseEntity.badRequest().build();
}
Path uploadDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
Path file = uploadDirPath.resolve(url.substring("/uploads/".length())).normalize();
if (!file.startsWith(uploadDirPath)) {
    log.warn("Path traversal attempt in download, attachmentId={}", attachmentId);
    return ResponseEntity.badRequest().build();
}
```

---

### WR-03: Double file extension in `Content-Disposition` filename

**File:** `src/main/java/org/ctc/domain/service/RaceAttachmentService.java:107`

**Issue:** The `Content-Disposition` header is built as
`safeName + getExtension(file)`. `safeName` originates from `attachment.getName()`, which
`uploadAttachment` sets to `file.getOriginalFilename()` — typically already including the
extension (e.g., `"screenshot.png"`). `getExtension(file)` then appends the physical
extension from the stored filename (e.g., `".png"`), producing
`"screenshot.png.png"`. A browser will honour the double extension literally, leading to
confusing download prompts. The `Content-Disposition` spec expects only a single filename.

**Fix:** Strip the extension from `safeName` before appending `getExtension(file)`, or
rely solely on the name as stored (which already contains the extension):

```java
// Option A: trust the stored name already has the extension — just use safeName directly
.header(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + safeName + "\"")

// Option B: strip trailing extension from safeName, then re-append from physical file
String baseNameSafe = safeName.replaceAll("\\.[a-zA-Z0-9]+$", "");
.header(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + baseNameSafe + getExtension(file) + "\"")
```

Option A is simpler and consistent with how `uploadAttachment` already stores the
original filename (including its extension) as the `name` field.

---

## Info

### IN-01: Test does not cover `null` original filename in `uploadAttachment`

**File:** `src/test/java/org/ctc/domain/service/RaceAttachmentServiceTest.java:127`

**Issue:** The only `uploadAttachment` test stubs `getOriginalFilename()` to return
`"screenshot.png"`. There is no test for the `null`-filename case (WR-01). Once WR-01
is fixed with an explicit guard, a corresponding test should be added to lock in that
behaviour.

**Fix:** Add:
```java
@Test
void givenNullFilename_whenUploadAttachment_thenThrowsIllegalArgument() {
    // given
    var raceId = UUID.randomUUID();
    var file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(null);

    // when / then
    assertThatThrownBy(() -> service.uploadAttachment(raceId, file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Filename");
}
```

---

_Reviewed: 2026-04-13_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
