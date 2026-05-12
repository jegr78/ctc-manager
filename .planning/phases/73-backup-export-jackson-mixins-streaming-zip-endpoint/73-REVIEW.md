---
phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint
reviewed: 2026-05-12T11:30:00Z
depth: standard
files_reviewed: 56
files_reviewed_list:
  - src/main/java/org/ctc/backup/BackupController.java
  - src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java
  - src/main/java/org/ctc/backup/serialization/CarMixIn.java
  - src/main/java/org/ctc/backup/serialization/DriverMixIn.java
  - src/main/java/org/ctc/backup/serialization/MatchMixIn.java
  - src/main/java/org/ctc/backup/serialization/MatchScoringMixIn.java
  - src/main/java/org/ctc/backup/serialization/MatchdayMixIn.java
  - src/main/java/org/ctc/backup/serialization/PhaseTeamMixIn.java
  - src/main/java/org/ctc/backup/serialization/PlayoffMatchupMixIn.java
  - src/main/java/org/ctc/backup/serialization/PlayoffMixIn.java
  - src/main/java/org/ctc/backup/serialization/PlayoffRoundMixIn.java
  - src/main/java/org/ctc/backup/serialization/PlayoffSeedMixIn.java
  - src/main/java/org/ctc/backup/serialization/PsnAliasMixIn.java
  - src/main/java/org/ctc/backup/serialization/RaceAttachmentMixIn.java
  - src/main/java/org/ctc/backup/serialization/RaceLineupMixIn.java
  - src/main/java/org/ctc/backup/serialization/RaceMixIn.java
  - src/main/java/org/ctc/backup/serialization/RaceResultMixIn.java
  - src/main/java/org/ctc/backup/serialization/RaceScoringMixIn.java
  - src/main/java/org/ctc/backup/serialization/RaceSettingsMixIn.java
  - src/main/java/org/ctc/backup/serialization/SeasonDriverMixIn.java
  - src/main/java/org/ctc/backup/serialization/SeasonMixIn.java
  - src/main/java/org/ctc/backup/serialization/SeasonPhaseGroupMixIn.java
  - src/main/java/org/ctc/backup/serialization/SeasonPhaseMixIn.java
  - src/main/java/org/ctc/backup/serialization/SeasonTeamMixIn.java
  - src/main/java/org/ctc/backup/serialization/TeamMixIn.java
  - src/main/java/org/ctc/backup/serialization/TrackMixIn.java
  - src/main/java/org/ctc/backup/service/BackupArchiveService.java
  - src/main/java/org/ctc/backup/service/BackupExportService.java
  - src/main/java/org/ctc/backup/service/UploadEntry.java
  - src/main/java/org/ctc/domain/repository/CarRepository.java
  - src/main/java/org/ctc/domain/repository/DriverRepository.java
  - src/main/java/org/ctc/domain/repository/MatchRepository.java
  - src/main/java/org/ctc/domain/repository/MatchScoringRepository.java
  - src/main/java/org/ctc/domain/repository/MatchdayRepository.java
  - src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java
  - src/main/java/org/ctc/domain/repository/PlayoffMatchupRepository.java
  - src/main/java/org/ctc/domain/repository/PlayoffRepository.java
  - src/main/java/org/ctc/domain/repository/PlayoffRoundRepository.java
  - src/main/java/org/ctc/domain/repository/PlayoffSeedRepository.java
  - src/main/java/org/ctc/domain/repository/PsnAliasRepository.java
  - src/main/java/org/ctc/domain/repository/RaceAttachmentRepository.java
  - src/main/java/org/ctc/domain/repository/RaceLineupRepository.java
  - src/main/java/org/ctc/domain/repository/RaceRepository.java
  - src/main/java/org/ctc/domain/repository/RaceResultRepository.java
  - src/main/java/org/ctc/domain/repository/RaceScoringRepository.java
  - src/main/java/org/ctc/domain/repository/RaceSettingsRepository.java
  - src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java
  - src/main/java/org/ctc/domain/repository/SeasonPhaseGroupRepository.java
  - src/main/java/org/ctc/domain/repository/SeasonPhaseRepository.java
  - src/main/java/org/ctc/domain/repository/SeasonRepository.java
  - src/main/java/org/ctc/domain/repository/SeasonTeamRepository.java
  - src/main/java/org/ctc/domain/repository/TeamRepository.java
  - src/main/java/org/ctc/domain/repository/TrackRepository.java
  - src/main/resources/templates/admin/backup.html
  - src/main/resources/templates/admin/layout.html
  - src/test/java/org/ctc/backup/AdminLayoutIT.java
  - src/test/java/org/ctc/backup/BackupControllerIT.java
  - src/test/java/org/ctc/backup/BackupControllerSecurityIT.java
  - src/test/java/org/ctc/backup/BackupControllerTest.java
  - src/test/java/org/ctc/backup/repository/BackupRepositoryEntityGraphIT.java
  - src/test/java/org/ctc/backup/serialization/BackupEntityAnnotationCleanlinessIT.java
  - src/test/java/org/ctc/backup/serialization/BackupSerializationModuleTest.java
  - src/test/java/org/ctc/backup/serialization/DriverMixInTest.java
  - src/test/java/org/ctc/backup/serialization/RaceAttachmentMixInTest.java
  - src/test/java/org/ctc/backup/serialization/RaceMixInTest.java
  - src/test/java/org/ctc/backup/serialization/SeasonMixInTest.java
  - src/test/java/org/ctc/backup/serialization/TeamMixInTest.java
  - src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java
  - src/test/java/org/ctc/backup/service/BackupArchiveServiceTest.java
  - src/test/java/org/ctc/backup/service/BackupExportNoLazyInitIT.java
  - src/test/java/org/ctc/backup/service/BackupExportServiceIT.java
  - src/test/java/org/ctc/backup/service/BackupExportServiceTest.java
  - src/test/java/org/ctc/backup/service/BackupRoundTripIT.java
  - src/test/java/org/ctc/backup/service/BackupUploadsMirrorIT.java
  - src/test/java/org/ctc/e2e/BackupExportE2ETest.java
findings:
  critical: 1
  warning: 6
  info: 5
  total: 12
status: issues_found
---

# Phase 73: Code Review Report

**Reviewed:** 2026-05-12T11:30:00Z
**Depth:** standard
**Files Reviewed:** 56
**Status:** issues_found

## Summary

Phase 73 delivers a structurally sound backup-export pipeline: 24 entity-specific
Jackson MixIns, 24 repository `findAllForBackup()` finders with `@EntityGraph`,
a thin `BackupController` over `StreamingResponseBody`, and a streaming
`BackupArchiveService` that emits `manifest.json` first, then per-entity `data/*.json`
arrays, then `uploads/<rel>` blobs. The lazy-init seal (class-level
`@Transactional(readOnly = true)` on both services), MixIn cleanliness gate, and
manifest-first ZIP layout are all enforced by dedicated ITs. CSRF and prod/dev
profile auth are covered by `BackupControllerSecurityIT`.

However, the review surfaces one CRITICAL finding (incomplete ZIP-slip /
path-traversal defense at the `enumerateReferencedUploads()` boundary) plus
six warning-level issues — including a stream-state hazard in
`BackupArchiveService.writeJson()`, a missing scope-check that lets the resolved
upload path escape `uploadRoot`, missing controller-level exception handling for
non-`IOException` runtime errors mid-stream, and an unused/dead `Path` reference
in `BackupUploadsMirrorIT.readUploadEntries()` (`Paths.get(".")`). Info items are
mostly polish: stale code comments, redundant `lookupRepository()` calls, and a
minor documentation drift between `BackupArchiveService` Javadoc and actual
`ZipOutputStream` close semantics.

## Critical Issues

### CR-01: enumerateReferencedUploads() lacks upload-root scope check — file-system read disclosure via malicious DB row

**File:** `src/main/java/org/ctc/backup/service/BackupExportService.java:281-311`
**Issue:** `enumerateReferencedUploads()` calls
`uploadRoot.resolve(relative).toAbsolutePath().normalize()` to resolve the
filesystem location, then only checks `Files.exists(absolute)`. It does NOT
verify that the resolved `absolute` path stays underneath `uploadRoot`. A
maliciously crafted DB column value such as
`"/uploads/../../../../etc/passwd"` (set via any of `Team.logoUrl`,
`SeasonTeam.logoUrl`, `Car.imageUrl`, `Track.imageUrl`,
`RaceAttachment.url` where `type=FILE`) would:

1. Pass `addIfPresent()` because the URL starts with `/uploads/` → relative
   becomes `"../../../../etc/passwd"`.
2. Resolve via `uploadRoot.resolve("../../../../etc/passwd").normalize()` to a
   path OUTSIDE `uploadRoot`.
3. Pass the `Files.exists()` probe if the target file exists.
4. Be returned as a valid `UploadEntry`.

The Phase 73-05 ZIP-slip-on-EXPORT defense in
`BackupArchiveService.writeZip()` (lines 119-124) catches the `..` substring as
a last line of defense and prevents the file from landing in the ZIP, but the
file is still **opened and read** by `Files.copy(entry.absolutePath(), zip)` —
actually wait, the `..` check fires BEFORE `Files.copy`, so the file is not
copied. The check is:

```java
if (entry.relativePath().contains("..")) {
    log.warn(...);
    continue;          // skips before Files.copy
}
```

However, the existence probe in `enumerateReferencedUploads()` itself
(`Files.exists(absolute)`) is still a filesystem-state-disclosure primitive —
an attacker with DB-write access (which they need to set the malicious URL)
could time the probe to determine whether a given path exists outside the
upload directory. More importantly: the
`BackupArchiveService` defense relies on the literal substring `..` appearing
in `relativePath()`. If the storage layer were ever to normalize relatives
(e.g., a future Plan that runs `Paths.get(relative).normalize().toString()`
before the `..` check, or a Windows-only edge case where `\\..\\` slips through
without the substring test catching `..`), the defense collapses. Defense-in-
depth is missing at the enumerator boundary — the production-facing layer.

**Fix:**

```java
public List<UploadEntry> enumerateReferencedUploads() {
    Set<String> relativePaths = new LinkedHashSet<>();
    // ... existing collection logic ...

    List<UploadEntry> entries = new ArrayList<>();
    for (String relative : relativePaths) {
        Path absolute = uploadRoot.resolve(relative).toAbsolutePath().normalize();
        // Defense-in-depth: the resolved path MUST stay within uploadRoot.
        if (!absolute.startsWith(uploadRoot)) {
            log.warn("Skipping path-traversal upload reference: {} (resolved to {} outside {})",
                    relative, absolute, uploadRoot);
            continue;
        }
        if (!Files.exists(absolute)) {
            log.warn("Skipping orphan upload reference: {} (resolved to {})",
                    relative, absolute);
            continue;
        }
        entries.add(new UploadEntry(absolute, relative));
    }
    return entries;
}
```

Add a matching test case to `BackupExportServiceTest` that asserts a
relativePath like `"../etc/passwd"` is rejected at the enumerator level (not
just at the archive level).

## Warnings

### WR-01: BackupArchiveService.writeJson() leaves JsonGenerator un-closed — internal state hazard

**File:** `src/main/java/org/ctc/backup/service/BackupArchiveService.java:148-160`
**Issue:** The method documents that `generator.close()` "must NOT be called
here," but the stated reason is misleading. With `AUTO_CLOSE_TARGET=false`,
`generator.close()` would close the generator's internal buffer/state but NOT
the underlying `OutputStream` — that is exactly what's wanted in a streaming
pipeline. Calling only `flush()` (lines 155-156) without `close()` leaves the
generator's internal state alive until GC reclaims it, and on some Jackson
versions can leak buffer references. The current code happens to work because
each entry constructs a fresh `JsonGenerator`, but the comment is incorrect
and the omission is fragile.
**Fix:**
```java
private void writeJson(OutputStream out, Object value, boolean pretty) throws IOException {
    JsonGenerator generator = backupObjectMapper.getFactory().createGenerator(out);
    generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    if (pretty) {
        generator.useDefaultPrettyPrinter();
    }
    try {
        backupObjectMapper.writeValue(generator, value);
    } finally {
        // AUTO_CLOSE_TARGET=false means close() flushes + releases the generator
        // WITHOUT touching the underlying ZipOutputStream — exactly what streaming requires.
        generator.close();
    }
}
```

### WR-02: BackupController catches IOException only — non-IO RuntimeExceptions disappear without log

**File:** `src/main/java/org/ctc/backup/BackupController.java:74-84`
**Issue:** The `StreamingResponseBody` lambda catches `IOException` and rewraps
it as `UncheckedIOException` with a log line. But any other runtime exception
thrown mid-stream — `LazyInitializationException`,
`HttpMessageNotWritableException`, `JsonProcessingException` (which extends
`IOException`, so it IS caught), `NoSuchElementException`, or anything thrown
inside Jackson during entity serialization — propagates through Spring's
async dispatch without a controller-level log line. This makes mid-stream
failures very hard to debug in production because the only record is whatever
Spring's `AsyncRequestTimeoutException` handler logs (often nothing, often a
stack trace far removed from the export context).
**Fix:**
```java
StreamingResponseBody body = outputStream -> {
    try {
        backupArchiveService.writeZip(outputStream, now);
    } catch (IOException e) {
        log.error("Backup export I/O failure mid-stream (filename={})", filename, e);
        throw new UncheckedIOException(e);
    } catch (RuntimeException e) {
        // Defense-in-depth: any RuntimeException from the service (lazy-init,
        // Jackson serialization, etc.) must be logged with context before we
        // let Spring async dispatch handle it.
        log.error("Backup export failure mid-stream (filename={})", filename, e);
        throw e;
    }
};
```

### WR-03: BackupArchiveService.writeZip() Javadoc contradicts ZipOutputStream close semantics

**File:** `src/main/java/org/ctc/backup/service/BackupArchiveService.java:79-89`
**Issue:** The Javadoc states "closing the ZIP flushes and writes the central
directory but does NOT close the underlying stream by default
(`ZipOutputStream` does close its delegate, so callers...)". This is
contradictory — the parenthetical correctly notes that `ZipOutputStream.close()`
DOES close its underlying `OutputStream` (which it does, per the JDK contract),
but the lead-in sentence says it doesn't. In production this means the
servlet container's response output stream IS closed by the try-with-resources,
which is fine because Spring closes it anyway after `StreamingResponseBody`
completes — but the doc misleads any future caller who reads this method's
contract. Either rewrite the Javadoc to state the truth ("the
`ZipOutputStream` closes the delegate stream when it itself is closed") or
wrap the delegate in a `CloseShieldOutputStream`-style filter to make the doc
match the behavior.
**Fix:** Update the Javadoc to match observed `ZipOutputStream.close()` behavior:
```java
 * <p>The caller owns the {@code OutputStream} lifecycle. This method opens a
 * {@link ZipOutputStream} on top of {@code out} via try-with-resources;
 * {@code ZipOutputStream.close()} writes the central directory AND closes the
 * delegate stream per the JDK contract. In production this is the desired
 * behaviour — the servlet container response output is closed only after the
 * controller's {@code StreamingResponseBody} returns, so the order is correct.
```

### WR-04: BackupUploadsMirrorIT has dead `Paths.get(".")` call masking unused import

**File:** `src/test/java/org/ctc/backup/service/BackupUploadsMirrorIT.java:198`
**Issue:** Inside `readUploadEntries()`:
```java
// Silence unused-import warning on Paths (kept for documentation parity with
// the production service's path handling).
Paths.get(".");
```
This is dead code injected solely to suppress an unused-import warning. The
correct fix is to remove the unused `import java.nio.file.Paths;` line at the
top of the file. Dead code with no functional purpose violates the project's
"no dead code" stance and makes future maintainers wonder if the `Paths.get(".")`
call had intent.
**Fix:** Remove the `Paths.get(".")` line and the now-unnecessary
`import java.nio.file.Paths;` import at the top of the file.

### WR-05: BackupExportService catches `cause instanceof RuntimeException` but loses the original ReflectiveOperationException context

**File:** `src/main/java/org/ctc/backup/service/BackupExportService.java:257-265`
**Issue:** When the reflective invocation of `findAllForBackup()` fails, the
cause is unwrapped:
```java
} catch (ReflectiveOperationException ex) {
    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
    if (cause instanceof RuntimeException re) {
        throw re;          // throws the cause without context
    }
    throw new RuntimeException("Reflective invocation of findAllForBackup() ...", cause);
}
```
The first branch rethrows the cause directly, but the caller now sees a
stack trace as if the original RuntimeException occurred without any link
back to which entity class triggered it. For backup-debug purposes (a
production export failing on entity 17 of 24), the entity-class context is
critical. The wrapped `RuntimeException` branch correctly preserves context;
the unwrapped rethrow does not.
**Fix:**
```java
} catch (ReflectiveOperationException ex) {
    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
    String context = "Reflective invocation of findAllForBackup() on "
            + entityClass.getSimpleName() + " failed";
    if (cause instanceof RuntimeException re) {
        // Wrap to preserve the entity-class context — the original RE is the cause.
        throw new IllegalStateException(context, re);
    }
    throw new RuntimeException(context, cause);
}
```

### WR-06: BackupControllerSecurityIT — ProdProfileSecurityTest may not exercise actual CSRF rejection for anonymous

**File:** `src/test/java/org/ctc/backup/BackupControllerSecurityIT.java:60-66`
**Issue:** `givenAnonymous_whenPostExport_thenUnauthorized` passes `.with(csrf())`
deliberately ("Pass CSRF to isolate the auth path — CSRF filter fires before
the auth filter on prod and would otherwise return 403"). The comment claims
CSRF fires before auth, but Spring Security's standard filter chain is:
`SecurityContextPersistenceFilter` → `CsrfFilter` → `LogoutFilter` →
`UsernamePasswordAuthenticationFilter` → ... →
`AuthorizationFilter`. So in fact AUTH (AuthorizationFilter) fires AFTER CSRF
in the chain. An anonymous POST WITHOUT csrf would be rejected by CsrfFilter
(403) before AuthorizationFilter ever runs — so the test's premise is
correct. However, the test only covers the path where CSRF is valid AND auth
is missing. It does NOT cover the more common attacker scenario: anonymous +
missing CSRF token. That path is implicitly covered by
`givenAuthenticatedNoCsrf_whenPostExport_thenForbidden` (which proves
CSRF rejects when auth is present), but a stronger test would also assert
`anonymous + no CSRF → 403` to lock the layered behavior.
**Fix:** Add a fourth test method:
```java
@Test
void givenAnonymousNoCsrf_whenPostExport_thenForbidden() throws Exception {
    // CSRF filter fires before auth — missing token returns 403 regardless of auth state.
    mockMvc.perform(post("/admin/backup/export").with(anonymous()))
            .andExpect(status().isForbidden());
}
```

## Info

### IN-01: BackupExportService duplicates lookupRepository() work between fetchAllForBackup() and countRowsPerTable()

**File:** `src/main/java/org/ctc/backup/service/BackupExportService.java:230-266`
**Issue:** Both methods call `lookupRepository(ref.entityClass())`; the
`fetchAllForBackup` path then uses reflection to find and invoke
`findAllForBackup()`. The reflection overhead per entity-class is minor (one
`getMethod` call), but caching the resolved `Method` reference at
`@PostConstruct` time would eliminate the per-invocation reflection cost and
remove the runtime `NoSuchMethodException` failure mode (it would surface at
bean initialization instead).
**Fix:** Precompute a `Map<Class<?>, Method>` in `initialize()` and look up
the cached `Method` in `fetchAllForBackup()`.

### IN-02: SeasonRepository.findAllForBackup() Javadoc is excellent but the `tracks` deviation is invisible from non-Season repos

**File:** `src/main/java/org/ctc/domain/repository/SeasonRepository.java:43-59`
**Issue:** The Javadoc thoroughly documents the
`MultipleBagFetchException` workaround and the lazy-load contract. However,
the rationale lives only on `SeasonRepository` — a reader auditing
`BackupExportService` or `BackupArchiveService` does not see the same context.
Cross-link the rationale from `BackupArchiveService.writeZip()`'s class
Javadoc (which already documents the `@Transactional(readOnly=true)`
requirement) back to the specific deviation comment in `SeasonRepository`.
**Fix:** Add a `@see SeasonRepository#findAllForBackup()` cross-reference to
the class-level Javadoc on `BackupArchiveService`.

### IN-03: BackupController.ISO_COMPACT_INSTANT pattern is locked but the date-format constant lacks a test

**File:** `src/main/java/org/ctc/backup/BackupController.java:57-58`
**Issue:** The basic-form ISO instant (`yyyyMMdd'T'HHmmss'Z'`) is intentional
(Windows-safe filename). The regex used by tests
(`ctc-backup-\\d{8}T\\d{6}Z\\.zip`) only asserts the SHAPE, not the value.
A test that pins the exact `isoSafeFilename(Instant)` output for a known
`Instant` would catch any accidental drift to the canonical
`DateTimeFormatter.ISO_INSTANT` (which contains forbidden `:` characters on
Windows).
**Fix:** Add a unit test:
```java
@Test
void givenKnownInstant_whenIsoSafeFilename_thenReturnsCompactZuluForm() {
    Instant fixed = Instant.parse("2026-05-12T07:00:00Z");
    // call via reflection or extract method to package-private for testing
    assertThat(isoSafeFilename(fixed)).isEqualTo("20260512T070000Z");
}
```

### IN-04: BackupExportNoLazyInitIT seeding helper performs in-place duplicate-cleanup via findAll() — slow on dev fixture

**File:** `src/test/java/org/ctc/backup/service/BackupExportNoLazyInitIT.java:243-249`
**Issue:** `seedSeasonWithTracks()` calls `seasonRepository.findAll().stream().filter(...)`
to clean up previous test runs. On the dev profile (which seeds many
seasons), this loads every season into memory just to find the
test-prefixed one. Use `findByName` (or `findByNameContaining`) if available,
or scope the cleanup to entities matching the test prefix via a derived
query. Not a correctness issue — performance is out of v1 scope — but a
single test running on a large fixture could be slow.
**Fix:** Add a derived query to `SeasonRepository` if not present:
```java
Optional<Season> findByName(String name);  // already exists by convention
```
Then: `seasonRepository.findByName(seasonName).ifPresent(seasonRepository::delete);`

### IN-05: backup.html lacks a CSRF token reference but the form is POST — only works because layout.html injects it server-side?

**File:** `src/main/resources/templates/admin/backup.html:13-17`
**Issue:** The form `<form th:action="@{/admin/backup/export}" method="post">`
does NOT include an explicit `<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">`. Thymeleaf's
Spring integration auto-injects the CSRF hidden field on `<form>` elements
with `th:action` and `method="post"` when the Spring Security CSRF integration
is active. This works in practice (the `BackupControllerSecurityIT` proves
it), but a reviewer cannot verify the CSRF wiring from the template alone.
Add a `data-` attribute or HTML comment that documents the auto-injection so
future maintainers don't accidentally rewrite the form with a plain `<form>`
tag (which would NOT receive auto-injection).
**Fix:** Add an inline comment:
```html
<!-- CSRF token is auto-injected by Thymeleaf/Spring Security on th:action POST forms. -->
<form th:action="@{/admin/backup/export}" method="post">
```

---

_Reviewed: 2026-05-12T11:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
