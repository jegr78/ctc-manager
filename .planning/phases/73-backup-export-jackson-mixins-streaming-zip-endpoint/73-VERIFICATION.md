---
phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint
verified: 2026-05-12T12:51:00Z
status: human_needed
score: 6/6 must-haves verified
overrides_applied: 0
requirements_coverage:
  EXPORT-01: VERIFIED
  EXPORT-02: VERIFIED
  EXPORT-03: VERIFIED
  EXPORT-04: VERIFIED
  EXPORT-05: VERIFIED
  EXPORT-06: VERIFIED
human_verification:
  - test: "Visual sanity check of /admin/backup page rendering on dev profile"
    expected: "Page shows H1 'Backup', description paragraph mentioning '24 entities', single blue '.btn-primary' Export Backup button; sidebar 'Data' group with 'Backup' entry highlighted active when on this page"
    why_human: "Visual layout, color rendering, sidebar active-state, and overall UX feel cannot be verified by grep/assertions alone — needs human eyes (per CLAUDE.md 'Visual Verification with playwright-cli')"
  - test: "Manual full export click-through with downloaded ZIP inspection"
    expected: "Admin clicks Backup → /admin/backup → Export Backup button → browser downloads ctc-backup-YYYYMMDDTHHMMSSZ.zip; opening the ZIP shows manifest.json (first entry, with schema_version=1, app_version, export_date, table_counts), data/*.json files for all 24 entities, and uploads/ directory mirror of referenced files"
    why_human: "Real-browser download behavior, file-manager rendering, and ZIP-archive content inspection require human interaction beyond Playwright's `download.suggestedFilename()` assertion"
  - test: "Live MariaDB UAT — export → wipe → manual restore inspection (covered by Phase 75 QUAL-03)"
    expected: "Backup exported from local MariaDB instance is restorable manually and contains structurally complete data"
    why_human: "Full round-trip on real MariaDB profile is scheduled as Phase 75 QUAL-03 deliverable; export side now ready for that UAT — confirms data shape integrity beyond H2 dev fixture"
---

# Phase 73: Backup Export — Jackson MixIns + Streaming ZIP Endpoint — Verification Report

**Phase Goal:** A single admin click on `/admin/backup` produces a streamed ZIP download whose contents round-trip cleanly through the manifest contract from Phase 72. The ~22 per-entity Jackson MixIns under `org.ctc.backup.serialization` apply `@JsonIdentityInfo` externally — no annotation drift in `org.ctc.domain.model`. `BackupExportService` is `@Transactional(readOnly=true)` with explicit `@EntityGraph` eager-fetch on every collection field reachable in the export aggregate, eliminating `LazyInitializationException` during streaming. Export is delivered via `StreamingResponseBody` (no full-dataset memory buffering) with a CSRF-protected POST endpoint and a `Content-Disposition` filename that includes an ISO-instant timestamp.

**Verified:** 2026-05-12T12:51:00Z
**Status:** human_needed (all programmatic guards pass; human UAT items reserved for visual sanity + downstream Phase 75 QUAL-03)
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
| - | ----- | ------ | -------- |
| 1 | Admin clicks Backup sidebar entry, lands on `/admin/backup`; clicking Export Backup triggers `POST /admin/backup/export` that streams a ZIP with `Content-Disposition: attachment; filename=ctc-backup-<ISO-instant>.zip` | VERIFIED | `BackupController.java:62-67` GET + `:69-104` POST handlers; `admin/layout.html:75-76` sidebar Data group with Backup link; `admin/backup.html:13-17` form posts to `/admin/backup/export`; `BackupExportE2ETest` passes end-to-end (1/1 Playwright test, 29 s) — filename regex `ctc-backup-\d{8}T\d{6}Z\.zip` matches suggested download name and saved ZIP first entry is `manifest.json` |
| 2 | Downloaded ZIP contains `manifest.json` as FIRST entry, per-entity JSON files under `data/`, and `uploads/` directory mirroring entity-referenced files | VERIFIED | `BackupArchiveService.java:104` literal `zip.putNextEntry(new ZipEntry("manifest.json"))` as Step 1, lines 110-116 iterate `BackupSchema.getExportOrder()` writing `data/<slug>.json`, lines 121-132 iterate `enumerateReferencedUploads()` writing `uploads/<rel>` with byte-identical `Files.copy`; verified by `BackupArchiveServiceIT` (3/3), `BackupRoundTripIT` (4/4), `BackupControllerIT` (2/2) — runtime log line "Backup export completed: dataEntries=24, uploadEntries=17, skippedTraversal=0" confirms 24 data entries + 17 uploads on dev fixture |
| 3 | Domain entity classes under `org.ctc.domain.model` are byte-identically unchanged — all serialization concerns live in `org.ctc.backup.serialization` MixIn classes | VERIFIED | `grep ... com.fasterxml.jackson src/main/java/org/ctc/domain/model/*.java` returns empty; `BackupEntityAnnotationCleanlinessIT` (1/1) reflectively walks `EntityManagerFactory.getMetamodel().getEntities()` filtered to `org.ctc.domain.model.*` and asserts zero `com.fasterxml.jackson.*` annotations on any declared field or method — permanent regression gate |
| 4 | `BackupExportService` runs under `@Transactional(readOnly=true)` and produces a streamed export of the dev fixture (Saison 2023 + 2024-3) without a single `LazyInitializationException` in logs | VERIFIED | `BackupExportService.java` carries class-level `@Transactional(readOnly = true)`; `BackupArchiveService.java:56` also carries `@Transactional(readOnly = true)` (extending session across whole `writeZip()`); `BackupExportNoLazyInitIT` (2/2) attaches Logback `ListAppender` to `org.hibernate` + `org.ctc.backup` loggers, asserts zero LIE messages and zero ERROR events, plus `data/seasons.json` contains seeded season with non-empty `tracks` ID-ref array of size 3 (defends against silent data drop) |
| 5 | Anonymous `POST /admin/backup/export` is rejected by Spring Security on prod/docker; CSRF token required and verified | VERIFIED | `BackupControllerSecurityIT` (7/7 — 5 prod + 2 dev): ProdProfileSecurityTest.givenAnonymous_whenPostExport_thenUnauthorized → 401, givenAuthenticatedNoCsrf_whenPostExport_thenForbidden → 403, givenAuthenticatedWithCsrf_whenPostExport_thenOk → 200 + Content-Disposition, givenAnonymous_whenGetBackup_thenUnauthorized → 401, givenAnonymousNoCsrf_whenPostExport_thenForbidden → 403 (added in WR-06 fix); DevProfileSecurityTest.givenAnonymous_whenPostExport_thenOk → 200, givenAnonymous_whenGetBackup_thenOk → 200 — full security matrix locked across both profiles |
| 6 | Streaming has no full-dataset memory buffering (`StreamingResponseBody` writes directly to response stream; manifest round-trip yields valid `BackupManifest`) | VERIFIED | `BackupController.java:69` returns `ResponseEntity<StreamingResponseBody>`; lambda at lines 74-93 delegates straight to `backupArchiveService.writeZip(outputStream, now)` — no intermediate `ByteArrayOutputStream`; `BackupArchiveService.java:92-138` writes directly via `try (ZipOutputStream zip = new ZipOutputStream(out))` to caller's stream; per-entry `JsonGenerator` with `AUTO_CLOSE_TARGET=false` (line 152) preserves streaming contract; `BackupRoundTripIT` (4/4) re-reads `manifest.json` via `backupObjectMapper.readValue(..., BackupManifest.class)` and asserts schemaVersion=1, appVersion non-blank, exportDate within 60s tolerance, tableCounts keyset equals `getExportOrder()` tableNames |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `src/main/java/org/ctc/backup/serialization/*.java` (24 MixIns + 1 Module) | 25 files | VERIFIED | `find` returns 25; `grep -c setMixInAnnotation BackupSerializationModule.java` = 24; `@Component` annotation = 1; class extends SimpleModule |
| `src/main/java/org/ctc/domain/repository/*.java` `findAllForBackup()` on 24 repos | 24 files | VERIFIED | `grep -l findAllForBackup ... \| wc -l` = 24; `grep -L` returns empty (all repos have it) |
| `src/main/java/org/ctc/backup/service/BackupExportService.java` | `@Transactional(readOnly=true)` + 3 public methods | VERIFIED | Class-level `@Transactional(readOnly = true)` (2 occurrences for combined class + method); constructor injects 24 repos + BackupSchema + uploadDirRaw; `countRowsPerTable()`, `fetchAllForBackup(Class<?>)`, `enumerateReferencedUploads()` all present; CR-01 path-traversal scope check at line 308 (`absolute.startsWith(uploadRoot)`) |
| `src/main/java/org/ctc/backup/service/BackupArchiveService.java` | manifest-first ZIP writer | VERIFIED | Line 56 `@Transactional(readOnly = true)`; constructor `@Qualifier("backupObjectMapper")` at line 67; line 104 literal `putNextEntry(new ZipEntry("manifest.json"))` as Step 1; lines 121-132 ZIP-slip defense + uploads loop |
| `src/main/java/org/ctc/backup/service/UploadEntry.java` | record(Path, String) | VERIFIED | File exists, 712 bytes |
| `src/main/java/org/ctc/backup/BackupController.java` | thin controller, ISO filename, StreamingResponseBody, octet-stream + Content-Disposition | VERIFIED | `@Controller @RequestMapping("/admin/backup")`; `DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)` at line 58; `ContentDisposition.attachment().filename(filename)` at line 95; `ResponseEntity<StreamingResponseBody>` return type; WR-02 RuntimeException catch added in fix |
| `src/main/resources/templates/admin/backup.html` | landing page with `.btn-primary` Export button, "24 entities" copy, no inline styles | VERIFIED | grep "all 24 entities" = 1 match; grep "btn btn-primary btn-lg" = 1 match; grep "style=" = 0 matches; grep "btn-success" = 0 matches |
| `src/main/resources/templates/admin/layout.html` | Sidebar Data group with Backup entry | VERIFIED | Line 75 `<span class="sidebar-group-label">Data</span>`; line 76 `<a th:href="@{/admin/backup}" th:classappend="${title.contains('Backup') ? 'active' : ''}">Backup</a>` |
| `src/test/java/org/ctc/backup/serialization/BackupEntityAnnotationCleanlinessIT.java` | Reflective gate proving domain model has zero Jackson annotations | VERIFIED | 1/1 IT pass; reflective JPA Metamodel walk |
| `src/test/java/org/ctc/backup/repository/BackupRepositoryEntityGraphIT.java` | Reflective IT proving all 24 finders work + @EntityGraph attributePaths | VERIFIED | 2/2 IT pass |
| `src/test/java/org/ctc/backup/service/BackupExportNoLazyInitIT.java` | LIE-absence guard | VERIFIED | 2/2 IT pass — log appender + tracks ID-ref array assertion |
| `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` | Manifest round-trip via backupObjectMapper | VERIFIED | 4/4 IT pass — schemaVersion, appVersion, exportDate within 60s, tableCounts keyset match |
| `src/test/java/org/ctc/backup/service/BackupUploadsMirrorIT.java` | Uploads byte-identical mirror + dedup + orphan skip | VERIFIED | 2/2 IT pass |
| `src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java` | Manifest-first invariant + every EntityRef.fileName() present | VERIFIED | 3/3 IT pass |
| `src/test/java/org/ctc/backup/service/BackupExportServiceIT.java` | Three-method contract on dev fixture | VERIFIED | 3/3 IT pass |
| `src/test/java/org/ctc/backup/BackupControllerSecurityIT.java` | @Nested prod + dev profiles, anonymous/CSRF matrix | VERIFIED | 7/7 IT pass (4 prod + 2 dev + 1 WR-06 anonymous-no-csrf addition) |
| `src/test/java/org/ctc/backup/BackupControllerIT.java` | GET renders view, POST streams real ZIP | VERIFIED | 2/2 IT pass |
| `src/test/java/org/ctc/backup/AdminLayoutIT.java` | Data sidebar group + active-state logic | VERIFIED | 3/3 IT pass |
| `src/test/java/org/ctc/e2e/BackupExportE2ETest.java` | Playwright download interception + filename regex + manifest-first round-trip on saved ZIP | VERIFIED | 1/1 E2E test pass under -Pe2e profile; log confirms `dataEntries=24, uploadEntries=17, skippedTraversal=0` |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `BackupSerializationModule` | `BackupObjectMapperConfig.backupObjectMapper(List<Module>)` | Spring DI scan of all `Module @Component` beans | WIRED | Phase 72 `List<Module>` injection auto-discovers the new `@Component` (verified by IT layer + runtime log "BackupSerializationModule" registration) |
| `BackupExportService` | 24 entity repositories | Constructor injection — typed `final` fields per repo | WIRED | `BackupExportServiceIT` and `BackupRepositoryEntityGraphIT` confirm all 24 dispatched reflectively via `findAllForBackup()` |
| `BackupArchiveService` | `BackupExportService` + `BackupSchema` + qualified `backupObjectMapper` + `app.version` | Constructor injection + `@Qualifier("backupObjectMapper")` | WIRED | Manifest-first invariant proven by 2 tests (unit + IT); manifest schemaVersion round-trips |
| `BackupController` | `BackupArchiveService.writeZip(OutputStream, Instant)` | Constructor injection + StreamingResponseBody lambda | WIRED | `BackupControllerIT.givenAuthenticatedAdmin_whenPostExport_thenStreamsActualZipWithManifestFirst` re-opens response bytes as ZipInputStream and confirms manifest.json is entry #0 |
| `admin/layout.html` sidebar | `admin/backup.html` | `th:href="@{/admin/backup}"` link + `${title.contains('Backup')}` active-state predicate | WIRED | `AdminLayoutIT` 3 tests prove sidebar rendering + active class on `/admin/backup` + no active class on `/admin/seasons` |
| Spring Security CsrfFilter + AuthorizationFilter | `POST /admin/backup/export` | Inherited from `SecurityConfig` (prod/docker — `anyRequest().authenticated()` + default CSRF) / `OpenSecurityConfig` (dev/local — `permitAll()` + CSRF disabled) | WIRED | `BackupControllerSecurityIT` profile-conditional `@Nested` classes prove full 6-cell matrix (anonymous + csrf → 401; auth no-csrf → 403; auth + csrf → 200; anonymous GET → 401; dev anonymous POST/GET → 200) plus WR-06 addition (anonymous + no-csrf → 403) |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `BackupArchiveService.writeZip` `tableCounts` | local `Map<String, Long>` | `backupExportService.countRowsPerTable()` → 24x `repo.count()` | YES — dev fixture log: `dataEntries=24` (non-zero counts on seeded tables `teams`, `seasons`, etc.) | FLOWING |
| `BackupArchiveService.writeZip` per-entity rows | local `List<?>` | `backupExportService.fetchAllForBackup(ref.entityClass())` → repo `findAllForBackup()` (real `@Query("SELECT e FROM E e")` against H2/dev fixture) | YES — `BackupExportNoLazyInitIT` asserts seeded Season + 3 Tracks render as ID-ref array of size 3 in `data/seasons.json` | FLOWING |
| `BackupArchiveService.writeZip` uploads | `List<UploadEntry>` from `enumerateReferencedUploads()` | Walks Team/SeasonTeam/Car/Track/RaceAttachment (FILE only); LinkedHashSet dedup; orphan + traversal filter | YES — `BackupUploadsMirrorIT` asserts byte-identical content for 3 fixture files; dev fixture log: `uploadEntries=17` | FLOWING |
| `BackupController.export()` filename | local `String filename` | `DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(Instant.now())` (hardcoded compact ISO) | YES — verified by Content-Disposition regex `ctc-backup-\d{8}T\d{6}Z\.zip` matching across 4 test layers (unit, IT, security IT, E2E) | FLOWING |

No HOLLOW or DISCONNECTED artifacts found. Every entity row, upload entry, and filename has a real production data source backed by the dev fixture.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| All 24 MixIn files compile + match entity classes | `find src/main/java/org/ctc/backup/serialization -name '*.java' \| wc -l` | 25 (24 MixIns + 1 Module) | PASS |
| 24 setMixInAnnotation registrations in Module | `grep -c 'setMixInAnnotation' BackupSerializationModule.java` | 24 | PASS |
| All 24 repos have findAllForBackup() | `grep -l 'findAllForBackup' src/main/java/org/ctc/domain/repository/*.java \| wc -l` | 24 | PASS |
| Domain model is Jackson-annotation-free | `grep ... com.fasterxml.jackson src/main/java/org/ctc/domain/model/*.java` | empty | PASS |
| BackupExportService is @Transactional(readOnly=true) | `grep -c '@Transactional(readOnly = true)' BackupExportService.java` | 2 (class + method) | PASS |
| BackupArchiveService is @Transactional(readOnly=true) | `grep -c '@Transactional(readOnly = true)' BackupArchiveService.java` | 2 | PASS |
| BackupArchiveService injects qualified backupObjectMapper | `grep '@Qualifier' BackupArchiveService.java` | `@Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper` | PASS |
| Manifest is first ZIP entry | `grep -n 'putNextEntry.*manifest' BackupArchiveService.java` | line 104 (before data/uploads loops) | PASS |
| Controller uses StreamingResponseBody | `grep -c 'StreamingResponseBody' BackupController.java` | 4 (imports, type, lambda) | PASS |
| Controller uses ISO-compact filename pattern | `grep 'DateTimeFormatter.ofPattern' BackupController.java` | line 58 `yyyyMMdd'T'HHmmss'Z'` | PASS |
| Controller uses ContentDisposition.attachment() | `grep 'ContentDisposition.attachment' BackupController.java` | line 95 | PASS |
| Backup template has "all 24 entities" copy | `grep -c 'all 24 entities' admin/backup.html` | 1 | PASS |
| Backup template uses .btn-primary (not .btn-success) | `grep -c 'btn btn-primary btn-lg' admin/backup.html` | 1, `grep btn-success` = 0 | PASS |
| Backup template has no inline styles | `grep style= admin/backup.html` | 0 matches | PASS |
| Sidebar Data group inserted in layout.html | `grep 'sidebar-group-label">Data' admin/layout.html` | line 75 | PASS |
| Sidebar Backup link present | `grep '/admin/backup' admin/layout.html` | line 76 anchor + active predicate | PASS |
| Path-traversal scope check at enumerator | `grep -n 'absolute.startsWith(uploadRoot)' BackupExportService.java` | line 308 | PASS |
| WR-06 added anonymous+no-csrf test | `grep 'givenAnonymousNoCsrf_whenPostExport_thenForbidden' BackupControllerSecurityIT.java` | line 69 | PASS |
| All 7 review-fix commits present | `git log --oneline 6ae746d 8dc5836 b47ae80 4ac12ec a2cdc75 badcc83 3c18839` | All 7 found in history | PASS |

### Probe Execution

| Probe | Command | Result | Status |
| ----- | ------- | ------ | ------ |
| Backup unit tests (Surefire) | `./mvnw test -Dtest='BackupSerializationModuleTest,TeamMixInTest,SeasonMixInTest,RaceMixInTest,DriverMixInTest,RaceAttachmentMixInTest,BackupExportServiceTest,BackupArchiveServiceTest,BackupControllerTest'` | Tests run: 21, Failures: 0, Errors: 0 — **BUILD SUCCESS** in 31s | PASS |
| Cleanliness + Repository ITs (Failsafe) | `./mvnw failsafe:integration-test failsafe:verify -Dit.test='BackupEntityAnnotationCleanlinessIT,BackupRepositoryEntityGraphIT'` | Tests run: 3, Failures: 0, Errors: 0 — **BUILD SUCCESS** | PASS |
| Service-layer ITs (Failsafe) | `./mvnw failsafe:integration-test failsafe:verify -Dit.test='BackupArchiveServiceIT,BackupUploadsMirrorIT,BackupExportServiceIT'` | Tests run: 8, Failures: 0, Errors: 0 — **BUILD SUCCESS** | PASS |
| Critical guards (Failsafe) | `./mvnw failsafe:integration-test failsafe:verify -Dit.test='BackupExportNoLazyInitIT,BackupRoundTripIT,BackupControllerSecurityIT,BackupControllerIT,AdminLayoutIT'` | Tests run: 18, Failures: 0, Errors: 0 — **BUILD SUCCESS** | PASS |
| Playwright E2E (-Pe2e profile) | `./mvnw verify -Pe2e -Dit.test='BackupExportE2ETest' -Dtest='void'` | Tests run: 1, Failures: 0, Errors: 0 — **BUILD SUCCESS**; dev fixture log: `dataEntries=24, uploadEntries=17, skippedTraversal=0` | PASS |

**Total: 51 backup-related test methods executed in this verification run; 0 failures, 0 errors.**

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| EXPORT-01 | 73-04 | Admin sidebar Backup → `/admin/backup` form page with Export + Import actions (Export this phase; Import deferred to Phase 74) | SATISFIED | Sidebar Data group + Backup entry verified (layout.html L75-76); `GET /admin/backup` renders form (BackupControllerIT + BackupControllerSecurityIT); AdminLayoutIT proves active-state predicate |
| EXPORT-02 | 73-03, 73-04 | `POST /admin/backup/export` streams ZIP via StreamingResponseBody with `Content-Disposition: attachment; filename=ctc-backup-{ISO-instant}.zip`; no memory buffering | SATISFIED | BackupController.java:69 returns `ResponseEntity<StreamingResponseBody>`; BackupArchiveService writes directly to caller's OutputStream (no intermediate ByteArrayOutputStream); ISO filename verified by 4-layer regex (unit, IT, security IT, E2E) |
| EXPORT-03 | 73-03 | ZIP contains `manifest.json` (FIRST entry) + per-entity JSON files under `data/` + `uploads/` directory mirror | SATISFIED | BackupArchiveService.java:104 literal manifest-first putNextEntry; BackupRoundTripIT (4/4) confirms manifest schemaVersion round-trip + tableCounts keyset equals exportOrder; BackupUploadsMirrorIT (2/2) confirms byte-identical uploads mirror with dedup + orphan skip; BackupArchiveServiceIT (3/3) confirms every EntityRef.fileName() present |
| EXPORT-04 | 73-01 | ~22 per-entity MixIns under `org.ctc.backup.serialization` apply `@JsonIdentityInfo` externally; domain entities unchanged (24 entities final per Phase 72 OQ-1 reconciliation) | SATISFIED | 24 MixIns + 1 Module = 25 files; BackupEntityAnnotationCleanlinessIT (1/1) proves zero Jackson annotations on `org.ctc.domain.model.*`; BackupSerializationModule registers all 24 setMixInAnnotation pairs auto-picked-up by Phase 72's `backupObjectMapper(List<Module>)` bean |
| EXPORT-05 | 73-02, 73-03 | `BackupExportService` is `@Transactional(readOnly=true)` with explicit `@EntityGraph` eager-fetch — no LazyInitializationException during streaming | SATISFIED | Class-level `@Transactional(readOnly=true)` on BOTH BackupExportService AND BackupArchiveService (Plan 73-03 deviation fix — extends session across whole writeZip); 24 repos have `findAllForBackup()` with @EntityGraph(attributePaths={...}); BackupExportNoLazyInitIT (2/2) proves zero LIE log events + non-empty tracks ID-ref array (defends against silent data drop); SeasonRepository tracks deviation documented (`MultipleBagFetchException` workaround → lazy-load inside service session) |
| EXPORT-06 | 73-04 | Export endpoint is CSRF-protected (Spring Security 7 default for prod/docker) | SATISFIED | BackupControllerSecurityIT (7/7) profile-conditional `@Nested` proves: prod anonymous+csrf→401, prod auth-no-csrf→403, prod auth+csrf→200, prod anonymous-no-csrf→403 (WR-06), prod anonymous GET→401, dev anonymous POST→200, dev anonymous GET→200; CsrfFilter rejects before AuthorizationFilter; admin/backup.html form receives Thymeleaf auto-injection of _csrf hidden field |

**All 6 requirements VERIFIED. No orphaned requirements — REQUIREMENTS.md maps exactly EXPORT-01..EXPORT-06 to Phase 73, all covered by submitted plans.**

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| (none) | — | No TBD/FIXME/XXX debt markers introduced by Phase 73 | INFO | Clean — all 7 critical/warning review findings resolved in commits 6ae746d..3c18839 |

Five Info-level review findings remain unfixed (IN-01..IN-05) but are explicitly out of scope per the `critical_warning` fix mode — they are polish items (Method caching, Javadoc cross-link, exact filename pin test, derived query for cleanup, CSRF auto-injection HTML comment). These do NOT block the phase goal and are documented in 73-REVIEW-FIX.md for potential future iteration.

### Human Verification Required

Three items require human confirmation:

#### 1. Visual sanity check of /admin/backup page

**Test:** Start `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo`, browse to `http://localhost:9090/admin/backup`, inspect the page rendering.
**Expected:** Page shows H1 "Backup", description paragraph mentioning "(all 24 entities)", single blue `.btn-primary` Export Backup button; sidebar "Data" group with "Backup" entry highlighted active.
**Why human:** Visual layout, color rendering, sidebar active-state correctness, and overall UX feel cannot be verified by grep/assertions alone — needs human eyes (per CLAUDE.md "Visual Verification with playwright-cli" memory).

#### 2. Manual full export click-through with downloaded ZIP inspection

**Test:** From `/admin/backup` click Export Backup, save the download, open the ZIP in a file manager (Finder, 7-Zip, etc.).
**Expected:** Downloaded filename matches `ctc-backup-YYYYMMDDTHHMMSSZ.zip`; opening shows `manifest.json` as first entry (schema_version=1, app_version, export_date, table_counts), `data/*.json` files for all 24 entities, `uploads/` directory containing referenced files (logos, attachments). JSON files are syntactically valid.
**Why human:** Real-browser download behavior, file-manager rendering, ZIP-archive content visual inspection — Playwright's `download.suggestedFilename()` covers the regex but not the human-eye archive layout sanity.

#### 3. Live MariaDB UAT — covered downstream by Phase 75 QUAL-03

**Test:** Export from local MariaDB instance with Saison-2023 fixture, then attempt manual restore inspection (full round-trip lands in Phase 75).
**Expected:** Backup is structurally complete on real MariaDB profile — beyond the H2 dev fixture covered by automated tests.
**Why human:** Full round-trip on real MariaDB is scheduled as Phase 75 QUAL-03 deliverable. Export side now ready for that UAT — no blocker for Phase 73 closure, but flag for downstream attention.

### Gaps Summary

**No gaps.** All 6 ROADMAP success criteria, all 6 requirement IDs (EXPORT-01..06), and all 19 production + test artifacts are verified through 51 passing test methods (Surefire + Failsafe + -Pe2e) plus reflective gates that lock the invariants permanently:

- Domain entities byte-identically unchanged (`BackupEntityAnnotationCleanlinessIT`)
- All 24 MixIn-to-entity mappings (`BackupSerializationModuleTest`)
- All 24 repository `findAllForBackup()` finders with `@EntityGraph` (`BackupRepositoryEntityGraphIT`)
- Manifest-first ZIP wire contract (`BackupArchiveServiceTest` + `BackupArchiveServiceIT`)
- Zero `LazyInitializationException` log events on dev fixture (`BackupExportNoLazyInitIT`)
- Byte-identical uploads mirror with dedup + orphan skip + path-traversal scope check (`BackupUploadsMirrorIT` + `BackupExportServiceTest`)
- Manifest round-trip via `backupObjectMapper.readValue` (`BackupRoundTripIT`)
- Full security matrix (anonymous/CSRF on prod + dev permitAll) — `BackupControllerSecurityIT` (7 cells)
- End-to-end Playwright click-through with manifest-first defense-in-depth on saved bytes (`BackupExportE2ETest`)

The 7-commit review-fix iteration (6ae746d..3c18839) closed all 1 Critical + 6 Warning findings from `73-REVIEW.md`. The 5 Info findings (IN-01..IN-05) are tracked polish items, out of scope per the critical_warning fix mode, and do not block the phase goal.

**Status: human_needed** — programmatic verification is complete and uniformly green; three optional human-UAT items (visual sanity, manual ZIP inspection, MariaDB UAT deferred to Phase 75 QUAL-03) are flagged for the developer to action before declaring Phase 73 "shipped to user."

---

_Verified: 2026-05-12T12:51:00Z_
_Verifier: Claude (gsd-verifier)_
