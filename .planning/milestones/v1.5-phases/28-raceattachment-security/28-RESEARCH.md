# Phase 28: RaceAttachment Security - Research

**Researched:** 2026-04-13
**Domain:** Java file download security (path traversal, null content type, header injection)
**Confidence:** HIGH

## Summary

Phase 28 addresses three concrete bugs in the `downloadAttachment` method of `RaceAttachmentService`. The method was written without a path-within-upload-dir guard, uses `Files.probeContentType()` whose return value may be `null` on some OS/JVM combinations (causing an NPE when passed to `MediaType.parseMediaType()`), and constructs the `Content-Disposition` filename directly from user-controlled attachment name without sanitizing newlines, semicolons, or quotes.

All three fixes are pure Java NIO / Spring standard-library patterns — no new dependencies are needed. The patterns already exist in `FileStorageService.validatePathWithinUploadDir()`, `FileStorageService.sanitize()`, and similar defensive code elsewhere in the codebase. The plan is to mirror those patterns in `RaceAttachmentService.downloadAttachment()`.

The existing test class (`RaceAttachmentServiceTest`) covers 90.2% of `RaceAttachmentService` lines (37/41). Four lines are in the untested `downloadAttachment` path. New unit tests must cover all three security scenarios, plus the existing gap on the file-not-found path.

**Primary recommendation:** Patch `downloadAttachment()` to (1) resolve and boundary-check the path, (2) fall back to `application/octet-stream` when `probeContentType` returns null, and (3) strip `\r`, `\n`, `"`, and `;` from the filename before building the header. Add unit tests for all three cases.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SECU-02 | File download validates resolved path stays within upload directory (path traversal defense) | `FileStorageService.validatePathWithinUploadDir()` is the established pattern; mirror it in `downloadAttachment()` using `Path.startsWith(uploadDirPath)` |
| SECU-05 | Content-Disposition header uses sanitized filename to prevent header injection | Strip `\r \n ; "` with `String.replaceAll` before embedding filename; established precedent in `FileStorageService.sanitize()` |
| DATA-02 | File download handles null content type from probeContentType gracefully | `Files.probeContentType()` is documented to return null; use `Objects.requireNonNullElse()` or a ternary; no NPE path can reach `MediaType.parseMediaType()` |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Test Coverage:** Minimum 82% line coverage must be maintained. (Current overall: 87.4%)
- **TDD:** Write tests first (Red -> Green -> Refactor).
- **Test Naming:** BDD `givenContext_whenAction_thenResult()` pattern.
- **No new dependencies:** Stack is fixed; use Java NIO and Spring Core only.
- **Controllers stay thin:** `downloadAttachment` logic lives in the service, not the controller. The controller already delegates correctly.
- **No Flyway changes:** This phase is pure Java; no schema changes.
- **Feature sequence:** Unit Tests -> Implementation -> Integration Tests -> E2E Tests.

## Standard Stack

### Core (no additions needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `java.nio.file.Files` | Java 25 stdlib | `probeContentType()`, `exists()`, path ops | Already in use in `RaceAttachmentService` |
| `java.nio.file.Path` | Java 25 stdlib | Path normalization and boundary check | Already in `FileStorageService` |
| `org.springframework.core.io.FileSystemResource` | Spring Boot 4.x | Streaming file bodies | Already in use |
| `org.springframework.http.MediaType` | Spring Boot 4.x | `parseMediaType()` | Already in use |
| JUnit 5 + Mockito + AssertJ | Spring Boot 4.x | Testing | Project standard |

**Installation:** No new packages.

## Architecture Patterns

### The Bug Surface in `downloadAttachment()`

The current implementation (lines 73-97 of `RaceAttachmentService.java`):

```java
// BUG 1 (SECU-02): path check is missing
Path file = Paths.get(uploadDir).toAbsolutePath().normalize()
        .resolve(url.substring("/uploads/".length()));
// No `file.startsWith(uploadDirPath)` guard here

// BUG 2 (DATA-02): probeContentType can return null
String contentType = "application/octet-stream";
try { contentType = Files.probeContentType(file); }  // null on Linux for unknown types
catch (IOException e) { ... }
// null is now assigned to contentType

return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))  // NPE if contentType == null
        ...
        // BUG 3 (SECU-05): attachment.getName() is unescaped user input
        .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + attachment.getName() + getExtension(file) + "\"")
```

### Pattern 1: Path Traversal Guard (SECU-02)

**What:** After resolving the path, verify it still starts with the upload directory root.
**When to use:** Any time a URL/filename from the database is used to construct a filesystem path.

```java
// Source: FileStorageService.validatePathWithinUploadDir() — same project
Path uploadDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
Path file = uploadDirPath.resolve(url.substring("/uploads/".length())).normalize();
if (!file.startsWith(uploadDirPath)) {
    log.warn("Path traversal attempt in download, attachmentId={}", attachmentId);
    return ResponseEntity.badRequest().build();
}
```

[VERIFIED: project codebase — `FileStorageService` lines 153-158]

### Pattern 2: Null-Safe Content Type (DATA-02)

**What:** `Files.probeContentType()` returns `null` when the OS/JVM cannot determine the MIME type (common on Linux for non-standard extensions). Fall back to `application/octet-stream`.

```java
// Source: Java 25 stdlib javadoc — probeContentType returns null if not determinable
String probed = null;
try { probed = Files.probeContentType(file); } catch (IOException e) { log.debug("..."); }
String contentType = (probed != null) ? probed : "application/octet-stream";
```

[VERIFIED: Java stdlib — `Files.probeContentType` Javadoc states it "may return null"]

### Pattern 3: Header Injection Sanitization (SECU-05)

**What:** `Content-Disposition: attachment; filename="..."` is split by newlines and semicolons by HTTP parsers. A filename containing `\n`, `\r`, or `"` can break the header structure or inject additional headers. Strip these characters before embedding.

```java
// Characters that break Content-Disposition header parsing:
// \n \r  — can split the header and inject arbitrary headers
// "      — terminates the quoted filename parameter
// ;      — terminates the filename parameter (unquoted contexts)
String safeName = attachment.getName()
        .replaceAll("[\\r\\n\";]", "_");
```

[ASSUMED: OWASP HTTP Response Splitting guidance — the specific character set `\r\n";` is standard mitigation]

The safeName is then used in the header:

```java
.header(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"" + safeName + getExtension(file) + "\"")
```

### Anti-Patterns to Avoid

- **Do not throw an exception for the path traversal case.** Return `ResponseEntity.badRequest()` (consistent with the LINK-type guard already in the method). Throwing would route through `GlobalExceptionHandler` and render an error page — appropriate for a 500, not a 400.
- **Do not use `String.format` or `MessageFormat` for the header.** Plain concatenation is what the existing code uses; change only what needs changing.
- **Do not refactor `downloadAttachment` to call `FileStorageService.validatePathWithinUploadDir`.** That method throws `IllegalArgumentException`; the download method should return a `ResponseEntity`, not throw. Inline the check.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Path boundary check | Custom string manipulation | `Path.startsWith(normalizedUploadDir)` | Handles OS separators, symlinks, `.` segments |
| Null content type | Default string in try-catch | `(probed != null) ? probed : "application/octet-stream"` | Already idiomatic; simpler |
| Header character stripping | Complex encoder | `replaceAll("[\\r\\n\";]", "_")` | Sufficient — no encoding needed, just stripping |

**Key insight:** All three fixes are 1-3 line changes to `downloadAttachment()`. The heavyweight solutions (e.g., Apache Tika for MIME detection, RFC 5987 encoded filenames) are disproportionate for an admin-only internal application.

## Common Pitfalls

### Pitfall 1: `probeContentType` Returning `null` on Linux
**What goes wrong:** `Files.probeContentType(path)` returns `null` for many file types on Linux (no installed MIME database entry). `MediaType.parseMediaType(null)` throws `IllegalArgumentException`.
**Why it happens:** The current code assigns the null return directly to `contentType` without null check.
**How to avoid:** Ternary guard: `probed != null ? probed : "application/octet-stream"`.
**Warning signs:** Works fine in macOS dev, fails in Docker/Linux CI.

### Pitfall 2: Path Traversal via Stored URL
**What goes wrong:** An attachment URL stored in the DB like `/uploads/../../etc/passwd` resolves outside the upload directory.
**Why it happens:** `uploadDir.resolve(...)` with `..` components can escape the root. `.normalize()` alone resolves the segments but does not enforce boundaries — the boundary check (`startsWith`) is the guard.
**How to avoid:** After `normalize()`, check `file.startsWith(uploadDirPath)` before `Files.exists()`.
**Warning signs:** Any upload URL containing `..` segments.

### Pitfall 3: Test Mocking `Files.probeContentType`
**What goes wrong:** `Files.probeContentType()` is a static method — cannot be mocked with Mockito unless using `MockedStatic`.
**Why it happens:** The download method touches the filesystem. In tests, the file won't exist.
**How to avoid:** Unit tests for the security paths (path traversal, null content type, header injection) should use `ReflectionTestUtils` to set `uploadDir` to a temp directory and use `@TempDir` + real file creation, OR restructure the test to call the method on a non-existent file path and verify the `notFound()` response, OR use `MockedStatic<Files>` to control `probeContentType`. Since the NPE reproduces with a real null return, `MockedStatic` is the cleanest approach for the DATA-02 test.
**Warning signs:** Test passes locally (macOS probes successfully) but CI (Linux) hits NPE.

### Pitfall 4: Coverage Drop
**What goes wrong:** Adding new code paths without tests drops coverage.
**Why it happens:** The `downloadAttachment` method currently has 4 uncovered lines. Adding path traversal, null content type, and sanitization logic increases the method body — all new branches must be tested.
**How to avoid:** Write tests first (TDD). Run `./mvnw verify` after adding tests to confirm coverage stays above 82%. Current overall: 87.4%.

## Code Examples

### Full Fixed `downloadAttachment` Method

```java
// Source: synthesized from FileStorageService patterns (same codebase)
public ResponseEntity<Resource> downloadAttachment(UUID attachmentId) {
    var attachment = raceAttachmentRepository.findById(attachmentId).orElseThrow();
    if (attachment.getType() != AttachmentType.FILE) {
        return ResponseEntity.badRequest().build();
    }

    // SECU-02: resolve path and enforce upload-dir boundary
    String url = attachment.getUrl();
    Path uploadDirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
    Path file = uploadDirPath.resolve(url.substring("/uploads/".length())).normalize();
    if (!file.startsWith(uploadDirPath)) {
        log.warn("Path traversal attempt in download, attachmentId={}", attachmentId);
        return ResponseEntity.badRequest().build();
    }

    if (!Files.exists(file)) {
        return ResponseEntity.notFound().build();
    }

    // DATA-02: null-safe content type
    String probed = null;
    try { probed = Files.probeContentType(file); } catch (IOException e) {
        log.debug("Could not probe content type for {}", file, e);
    }
    String contentType = (probed != null) ? probed : "application/octet-stream";

    // SECU-05: sanitize filename to prevent header injection
    String safeName = attachment.getName().replaceAll("[\\r\\n\";]", "_");

    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + safeName + getExtension(file) + "\"")
            .body(new FileSystemResource(file));
}
```

### Test Cases to Add

```java
// Source: existing RaceAttachmentServiceTest patterns (same project)

@Test
void givenPathTraversalUrl_whenDownloadAttachment_thenReturnsBadRequest() {
    // given
    var attachmentId = UUID.randomUUID();
    var race = new Race();
    race.setId(UUID.randomUUID());
    // URL crafted to escape uploadDir via "../.."
    var attachment = new RaceAttachment(race, AttachmentType.FILE, "evil", "/uploads/../../etc/passwd");
    attachment.setId(attachmentId);
    when(raceAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

    // when
    var response = service.downloadAttachment(attachmentId);

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
}

@Test
void givenNullProbeContentType_whenDownloadAttachment_thenUsesOctetStream(@TempDir Path tempDir) throws Exception {
    // given — real file so exists() passes; mock probeContentType via MockedStatic
    ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
    Path racesDir = tempDir.resolve("races").resolve(UUID.randomUUID().toString());
    Files.createDirectories(racesDir);
    Path testFile = racesDir.resolve("file.bin");
    Files.writeString(testFile, "content");

    var attachmentId = UUID.randomUUID();
    var race = new Race();
    race.setId(UUID.randomUUID());
    String urlPath = "/uploads/races/" + racesDir.getFileName() + "/file.bin";
    // ... set up attachment with url matching tempDir
    // ... mock raceAttachmentRepository

    try (var mockedFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
        mockedFiles.when(() -> Files.probeContentType(any())).thenReturn(null);
        // when
        var response = service.downloadAttachment(attachmentId);
        // then
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }
}

@Test
void givenFilenameWithInjectionChars_whenDownloadAttachment_thenHeaderIsSanitized(@TempDir Path tempDir) throws Exception {
    // given — filename with newline and quote
    // "evil\nX-Injected: header\""
    // ... set up attachment with that name, real file in tempDir
    // when
    var response = service.downloadAttachment(attachmentId);
    // then
    String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
    assertThat(disposition).doesNotContain("\n").doesNotContain("\r").doesNotContain("\"evil");
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + AssertJ |
| Config file | `pom.xml` (Surefire plugin) |
| Quick run command | `./mvnw test -pl . -Dtest=RaceAttachmentServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SECU-02 | Path traversal URL returns 400 | unit | `./mvnw test -Dtest=RaceAttachmentServiceTest#givenPathTraversalUrl_*` | Wave 0 |
| DATA-02 | Null probeContentType falls back to octet-stream | unit | `./mvnw test -Dtest=RaceAttachmentServiceTest#givenNullProbeContentType_*` | Wave 0 |
| SECU-05 | Injected chars stripped from Content-Disposition | unit | `./mvnw test -Dtest=RaceAttachmentServiceTest#givenFilenameWithInjectionChars_*` | Wave 0 |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=RaceAttachmentServiceTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `RaceAttachmentServiceTest` needs three new test methods (listed above) — covers SECU-02, DATA-02, SECU-05
- [ ] `MockedStatic<Files>` usage — Mockito already on classpath, no new dependency needed

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | n/a (admin-only internal app) |
| V3 Session Management | no | n/a |
| V4 Access Control | no | n/a |
| V5 Input Validation | yes | Sanitize filename, reject out-of-bounds paths |
| V6 Cryptography | no | n/a |
| V12 File and Resources | yes | Path traversal defense, safe MIME detection |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Path traversal (`../..` in URL) | Elevation of Privilege | `Path.startsWith(uploadDirPath)` after normalize |
| HTTP response splitting (newline in header) | Tampering | Strip `\r\n` from filename before embedding in header |
| Header parameter injection (`"` or `;` in filename) | Tampering | Strip `";` from filename |
| NPE as availability attack (null MIME type) | Denial of Service | Null-safe fallback to `application/octet-stream` |

## Environment Availability

Step 2.6: SKIPPED (no external dependencies — pure Java code change and unit tests only)

## Open Questions

1. **`MockedStatic<Files>` for DATA-02 test complexity**
   - What we know: The null-return path of `probeContentType` cannot be reliably triggered in a unit test without `MockedStatic`, or by using a real temp file on a platform that returns null (Linux with unknown extension).
   - What's unclear: Whether `MockedStatic` with `CALLS_REAL_METHODS` for `Files.exists()` and other static methods on the same class creates interference.
   - Recommendation: Use `@TempDir` with a real file and `MockedStatic<Files>` scoped to `probeContentType` only. Alternative: test the null-safe logic by extracting it into a package-private helper method that can be called directly without filesystem interaction.

2. **Semicolon in filename vs. RFC 6266**
   - What we know: RFC 6266 defines `Content-Disposition` and allows `filename*=UTF-8''...` encoding for non-ASCII names. The current code uses the simpler `filename="..."` form.
   - What's unclear: Whether the planner should upgrade to `filename*` encoding or just strip problematic characters.
   - Recommendation: Strip only — the attachment filenames are admin-controlled uploads (GT7 screenshots, PDFs). RFC 6266 encoded filenames are out of scope per REQUIREMENTS.md (no breaking changes, minimal footprint).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Stripping `\r\n";` is sufficient mitigation for Content-Disposition header injection | Security Domain / Pattern 3 | If `;` unescaped causes issues in some parser, filename parameter leaks to next param. Low risk for admin-internal app. |

## Sources

### Primary (HIGH confidence)
- Project codebase — `FileStorageService.java` lines 153-158: `validatePathWithinUploadDir` pattern [VERIFIED: codebase grep]
- Project codebase — `RaceAttachmentService.java` lines 73-97: exact bug surface [VERIFIED: file read]
- Project codebase — `RaceAttachmentServiceTest.java`: current 6 tests, 4 uncovered lines in `downloadAttachment` [VERIFIED: jacoco.csv + file read]
- Java 25 stdlib Javadoc — `Files.probeContentType` returns null if content type cannot be determined [ASSUMED: well-known stdlib behavior]

### Secondary (MEDIUM confidence)
- JaCoCo CSV report — overall 87.4% line coverage, RaceAttachmentService 90.2% [VERIFIED: target/site/jacoco/jacoco.csv]

### Tertiary (LOW confidence)
- OWASP HTTP Response Splitting — character set `\r\n";` for Content-Disposition sanitization [ASSUMED: standard OWASP guidance]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new libraries; all patterns from same codebase
- Architecture: HIGH — exact bug lines identified, exact fix patterns from FileStorageService
- Pitfalls: HIGH — confirmed by reading actual code and coverage data; MockedStatic complexity is known

**Research date:** 2026-04-13
**Valid until:** 2026-05-13 (stable domain, no external dependencies)
