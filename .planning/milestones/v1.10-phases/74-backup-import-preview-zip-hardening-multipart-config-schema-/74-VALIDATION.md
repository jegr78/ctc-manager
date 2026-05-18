---
phase: 74
slug: backup-import-preview-zip-hardening-multipart-config-schema
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-12
approved_on: 2026-05-18
audit_method: retroactive
---

# Phase 74 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Playwright (E2E) |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo) |
| **Quick run command** | `./mvnw -pl . -q -Dtest='BackupImport*Test' test` |
| **Full suite command** | `./mvnw verify` (Unit + IT + JaCoCo); E2E via `./mvnw verify -Pe2e` |
| **Estimated runtime** | ~90 seconds (`verify`), ~3 minutes (`verify -Pe2e`) |

---

## Sampling Rate

- **After every task commit:** Run targeted `./mvnw -Dtest='<NewClass>' test` or `-Dit.test='<NewIT>' failsafe:integration-test`.
- **After every plan wave:** Run `./mvnw verify` (Unit + IT + JaCoCo coverage check ≥ 82 %).
- **Before `/gsd-verify-work`:** `./mvnw verify -Pe2e` must be green (includes Playwright `BackupImportE2ETest`).
- **Max feedback latency:** ~60 seconds for unit, ~90 seconds for IT, ~3 minutes including E2E.

---

## Per-Task Verification Map

> **Note:** Original draft rows mapped to placeholder test class names (e.g. `BackupImportSchemaVersionMismatchIT`). The rows below have been retroactively reconciled with the real test classes that landed on disk during Plans 01..10 (in-flight class renames preserved). Real file paths under `src/test/java/` shown in the "Test File" column. A new structural gap-fill IT (`BackupUploadExceptionHandlerScopeIT`) was added in Phase 87 / Plan 87-04 to close the SECU-04 advice-scope invariant.

| # | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Test File | Status |
|---|------|------|-------------|------------|-----------------|-----------|-----------|--------|
| V1  | 01-04 | 2-3 | IMPORT-01, IMPORT-02 | — | Happy path: stage real Phase-73 export ZIP, get preview DTO with non-zero counts; `BackupArchiveService` reads ZIP manifest + per-entity JSON streams (single archive open per restore) | IT | `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java` | ✅ green |
| V2  | 02 | 1-2 | IMPORT-01 | — | `readManifest` / `countDataEntries` (streaming) / `countUploadFiles` round-trip vs Phase 73 export; per-entity JSON stream open exactly once | IT | `src/test/java/org/ctc/backup/service/BackupArchiveServiceReadIT.java`, `BackupArchiveServiceIT.java` | ✅ green |
| V3  | 03 | 1 | D-17 (CONTEXT) | — | 3 stale `upload-*.zip` fixtures → `ApplicationReadyEvent` → all deleted + 1 info-log; staging directory empty after startup | IT | `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java` | ✅ green |
| V4  | 04 | 2-3 | IMPORT-02, SECU-04 | T-74-SCHEMA | Forged manifest `schema_version=999` → HTTP 400 + Flash `errorMessage`; `Repository.count()` snapshot byte-identical before/after rejected upload (DB unchanged on mismatch) | IT | `src/test/java/org/ctc/backup/service/BackupImportSchemaMismatchIT.java` (renamed from draft `BackupImportSchemaVersionMismatchIT`) | ✅ green |
| V5  | 05 | 2-3 | IMPORT-03 | — | `BackupImportService.preview()` produces no-write per-table wipe + restore preview from manifest; preview DTO populated; entity row counts rendered | IT + unit | `src/test/java/org/ctc/backup/dto/BackupImportPreviewTest.java`, `src/test/java/org/ctc/backup/dto/EntityRowCountTest.java`, `src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java` | ✅ green |
| V6  | 06 | 2-3 | IMPORT-04 | — | `BackupImportConfirmForm` Pflicht-Checkbox `@NotNull @AssertTrue Boolean acknowledged` rejects missing/false → re-render confirm page with binding error | IT + unit | `src/test/java/org/ctc/backup/BackupImportConfirmFormValidationIT.java`, `src/test/java/org/ctc/backup/dto/BackupImportConfirmFormValidationTest.java` | ✅ green |
| V7  | 07 | 2-3 | SECU-01 | T-74-SCHEMA, T-74-SCOPE | Fixture ZIP with `../../etc/passwd` → reject + staging file deleted; `startsWith(uploadDir.toRealPath())` containment guard; absolute-path entries rejected | IT + unit | `src/test/java/org/ctc/backup/service/BackupImportZipSlipIT.java`, `src/test/java/org/ctc/backup/security/PathTraversalGuardTest.java` | ✅ green |
| V8  | 08 | 2-3 | SECU-02 | — | Inflated-byte counter fires on entry > 50 MB, total > 500 MB, count > 50 000; per-entry/total/count caps enforced via `LimitedInputStream` | IT + unit | `src/test/java/org/ctc/backup/service/BackupImportZipBombIT.java`, `src/test/java/org/ctc/backup/service/BackupImportLimitsTest.java`, `src/test/java/org/ctc/backup/io/LimitedInputStreamTest.java` | ✅ green |
| V9  | 09 | 2-3 | SECU-03, SECU-04 | T-74-SECU04 | 101 MB `MockMultipartFile` / real Tomcat upload → `MaxUploadSizeExceededException` → Flash redirect with locked D-02#1 string; no stack-trace leak in body; small upload NOT intercepted | IT | `src/test/java/org/ctc/backup/exception/BackupImportMultipartLimitIT.java`, `src/test/java/org/ctc/backup/exception/BackupArchiveExceptionTest.java` | ✅ green |
| V10 | 09 | 2-3 | SECU-04 | T-74-SECU04 | Structural invariant: `BackupUploadExceptionHandler` is a dedicated `@ControllerAdvice` with `@Order(HIGHEST_PRECEDENCE)`, `handleMaxUploadSizeExceeded` returns `String` (redirect); `GlobalExceptionHandler` does NOT register `MaxUploadSizeExceededException` (mixed-return-type guard) | IT (reflective) | `src/test/java/org/ctc/backup/exception/BackupUploadExceptionHandlerScopeIT.java` *(NEW — Phase 87 / Plan 87-04 gap-fill)* | ✅ green |
| V11 | 10 | 2-3 | SECU-04 | T-74-SCOPE | Profile-conditional auth: anonymous/CSRF matrix on prod; anonymous-allowed on dev; preview state STATELESS — staging UUID re-read at execute, no `@SessionAttributes` | IT | `src/test/java/org/ctc/backup/BackupImportControllerSecurityIT.java` | ✅ green |
| V12 | 10 | 3 | IMPORT-01..04 | — | Playwright full UI: upload Phase-73 export → preview cards → Proceed → confirm checkbox → submit → land on `/admin/backup` with stub Flash (D-02#5) | E2E | `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Programmatic malicious fixtures via `ZipOutputStream` in test `@BeforeAll` (D-25 — no committed binaries):
  - Path-traversal: entry name `../../etc/passwd`.
  - ZIP bomb (size lie): `ZipEntry.setSize(Long.MAX_VALUE)`, small payload — forces inflated-byte counter to fire.
  - Total-size overflow: many entries summing > 500 MB.
  - Entry-count overflow: 50 001 trivial entries.
  *(satisfied retroactively — `BackupImportZipSlipIT` and `BackupImportZipBombIT` both generate fixtures via `ZipOutputStream` in setup; no binary fixtures committed)*
- [x] Happy-path fixture: invoke Phase 73's `BackupArchiveService.writeZip()` at runtime — produces valid ZIP bytes without committed binary. *(satisfied retroactively — `BackupImportServiceIT` and `BackupArchiveServiceReadIT` reuse the Phase-73 writer to produce fresh ZIPs per test)*
- [x] No new framework installs — JUnit 5, Spring Boot Test, Playwright already on `compile`/`test` classpath. *(satisfied retroactively — no `pom.xml` test-scope additions for Phase 74)*

*All Wave 0 dependencies satisfied retroactively — every referenced test file exists on disk as of Phase 87 / Plan 87-04.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions | Status |
|----------|-------------|------------|-------------------|--------|
| Visual card grid responsiveness (24 cards, mobile + desktop) | IMPORT-03 (D-03) | Pixel-level visual review; Playwright covers click-through but not aesthetics | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` → upload a sample backup → `playwright-cli open http://localhost:9090/admin/backup/import-preview` (Desktop + Mobile viewports) per `feedback_playwright_cli.md`. | manual (v1.10 close) |
| Delta-pill color semantics (red = data loss visible at a glance) | D-03 | Subjective UX evaluation | Same browser walkthrough; assert red pills visible when imported < current. | manual (v1.10 close) |

---

## Validation Architecture (mapped to ROADMAP success criteria)

Per RESEARCH.md `## Validation Architecture` section. Each ROADMAP success criterion has at least one observable assertion and an owning test class.

| SC# | Success Criterion | Observable Assertion | Owning Test |
|-----|-------------------|----------------------|-------------|
| SC#1 | Admin uploads → preview screen with per-table counts + uploads count + schema-match indicator | UI: `BackupImportE2ETest` asserts grid renders 24 entity cards + schema-match pill. IT: `BackupImportServiceIT` asserts `BackupImportPreview` DTO populated. | `BackupImportE2ETest`, `BackupImportServiceIT` |
| SC#2 | Schema mismatch → HTTP 400 + Flash + DB byte-identically unchanged | IT: `Repository.count()` snapshot for each of 24 entities before/after rejected upload; HTTP 400 status; `errorMessage` flash present. | `BackupImportSchemaMismatchIT` |
| SC#3 | ZIP-Slip / absolute path / >50 MB entry → reject Flash + malicious test fixture committed in source | IT: rejection thrown, `errorMessage` flash present, staging file deleted; fixtures live in `src/test/resources/backup-fixtures/malicious/` generated programmatically (D-25). | `BackupImportZipSlipIT`, `BackupImportZipBombIT` |
| SC#4 | Upload > 100 MB → Flash D-02#1 instead of stack trace | IT: `MockMultipartFile(101 MB)` POST returns redirect with `errorMessage` matching D-02#1; no Tomcat trace in response body; advice-scope locked to dedicated `BackupUploadExceptionHandler`. | `BackupImportMultipartLimitIT`, `BackupUploadExceptionHandlerScopeIT` |
| SC#5 | Preview state STATELESS — staging UUID re-read at execute, no `@SessionAttributes` | IT: grep `BackupController` for `@SessionAttributes` (must be 0); execute stub re-reads file by UUID from form param. E2E: cookie-jar reset between preview and execute proves no session state carries. | `BackupImportControllerSecurityIT`, `BackupImportE2ETest` |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or post-hoc evidence *(Wave 0 satisfied retroactively — all referenced test files now exist on disk)*
- [x] Sampling continuity: no 3 consecutive tasks without automated verify *(verified post-execution — V1..V12 each have a real test file)*
- [x] Wave 0 covers all MISSING references (malicious fixture generators) *(retroactively — `BackupImportZipSlipIT` / `BackupImportZipBombIT` generate fixtures via `ZipOutputStream`; no binary fixtures committed)*
- [x] No watch-mode flags (Surefire/Failsafe run once and exit — no continuous watchers)
- [x] Feedback latency < 90s for IT, < 180s including E2E *(targeted Failsafe run for the new gap-fill IT: 28.91 s wallclock — within budget)*
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-18 — retroactive audit via Phase 87 / Plan 87-04

---

## Validation Audit 2026-05-18

| Metric | Count |
|--------|-------|
| Gaps found | 1 |
| Resolved | 1 |
| Escalated | 0 |
| Impl bugs fixed | 0 |
| Impl bugs deferred | 0 |

**Audit method:** retroactive (audit_method: retroactive) — all 8 Phase-74 requirements (IMPORT-01..04, SECU-01..04) were audited against the real test surface on disk via filename inventory, `@Tag` verification, and targeted Surefire/Failsafe runs. 11 of 12 Per-Task Verification Map rows mapped 1:1 to existing test classes; 1 row (V10, SECU-04 structural invariant) had no on-disk test and was closed by a new `BackupUploadExceptionHandlerScopeIT` in commit `fb68a87e`.

**Per-requirement coverage matrix:**

| REQ-ID | Test Files | Verdict |
|--------|------------|---------|
| IMPORT-01 (BackupArchiveService streaming reads, single archive open) | `BackupImportServiceIT`, `BackupArchiveServiceReadIT`, `BackupArchiveServiceIT`, `BackupStagingCleanupIT` | COVERED |
| IMPORT-02 (schema-version mismatch → HTTP 400 + Flash, DB unchanged) | `BackupImportSchemaMismatchIT` | COVERED |
| IMPORT-03 (no-write preview with per-table wipe-and-restore counts) | `BackupImportPreviewTest`, `EntityRowCountTest`, `BackupImportConfirmFormValidationIT` | COVERED |
| IMPORT-04 (Pflicht-Checkbox `@AssertTrue acknowledged`) | `BackupImportConfirmFormValidationTest`, `BackupImportConfirmFormValidationIT` | COVERED |
| SECU-01 (ZIP-Slip defense + `startsWith(uploadDir.toRealPath())`) | `BackupImportZipSlipIT`, `PathTraversalGuardTest` | COVERED |
| SECU-02 (ZipBomb caps 50 MB / 500 MB / 50 000 entries + `LimitedInputStream`) | `BackupImportZipBombIT`, `BackupImportLimitsTest`, `LimitedInputStreamTest` | COVERED |
| SECU-03 (Multipart 100 MB Spring+Tomcat, `MaxUploadSizeExceededException` handling) | `BackupImportMultipartLimitIT`, `BackupArchiveExceptionTest` | COVERED |
| SECU-04 (dedicated `@ControllerAdvice` Flash-only mapping, NOT `GlobalExceptionHandler`) | `BackupImportControllerSecurityIT` (behaviour), `BackupImportMultipartLimitIT` (runtime), **`BackupUploadExceptionHandlerScopeIT` (structural — NEW)** | COVERED (after gap-fill) |

**Gap-fill detail (V10 — SECU-04 structural invariant):**

The original Per-Task Map had behaviour-only coverage of SECU-04 (`BackupImportMultipartLimitIT` exercises the real 100 MB Tomcat limit; `BackupImportControllerSecurityIT` exercises the CSRF/auth matrix). What was missing: a structural assertion that the `MaxUploadSizeExceededException` handler lives in `BackupUploadExceptionHandler` and NOT in `GlobalExceptionHandler` — the Phase-74 D-02 rationale being that mixing `String "redirect:..."` and `ModelAndView` return types in one advice produces Spring binding ambiguity.

`BackupUploadExceptionHandlerScopeIT` closes this with 3 reflective assertions:

1. `BackupUploadExceptionHandler` is annotated `@ControllerAdvice` and registered as a Spring bean.
2. `handleMaxUploadSizeExceeded` declares `@ExceptionHandler(MaxUploadSizeExceededException.class)` and returns `String`.
3. `GlobalExceptionHandler` does **not** register `MaxUploadSizeExceededException` via any `@ExceptionHandler` method.

A future PR that silently merges the two advices would flip assertion #3 and fail this test — exactly the regression Phase 74 D-02 set out to prevent.

**Targeted Phase 74 test runs (Plan 87-04, 2026-05-18 09:32–09:34 local):**
- Failsafe gap-fill IT: `BackupUploadExceptionHandlerScopeIT` — Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 28.91 s → **BUILD SUCCESS** (commit `fb68a87e`).
- Pre-existing Phase 74 ITs were not re-executed in this plan — they were last seen green in CI run `26008754136` (same baseline cited in Plan 87-03 audit). The new gap-fill IT was the only new behaviour added.

**CI evidence:** Run-id `26008754136` (workflow_dispatch on `gsd/v1.11-tooling-and-cleanup` @ `b7f20b53`, conclusion: success, 2026-05-18T01:30:27Z, e2e step wallclock: 23:00) — full pre-Plan-87-04 backup-import suite green. Post-Plan-87-04 wallclock impact from the single new gap-fill IT: ~29 s context start-up (single new `@SpringBootTest`), comfortably within the 69 s remaining headroom toward the 5 % regression threshold (per 87-RESEARCH.md §Wallclock Baseline).

**Tag compliance:** All Phase 74 ITs (including `BackupUploadExceptionHandlerScopeIT`) carry `@Tag("integration")`; `BackupImportE2ETest` carries `@Tag("e2e")`. Unit tests (`*Test.java` for `BackupImportPreviewTest`, `EntityRowCountTest`, `BackupImportConfirmFormValidationTest`, `BackupImportLimitsTest`, `LimitedInputStreamTest`, `PathTraversalGuardTest`, `BackupArchiveExceptionTest`, `BackupArchiveServiceTest`) are untagged per `.planning/codebase/TESTING.md` §Test Categorization (Surefire-routed). Conforms to CLAUDE.md "Tag Tests by Category".

**Originating v1.10 verification:** `74-VERIFICATION.md status: passed` (2026-05-13) — Phase 74 already had a post-hoc verification gate at v1.10 close.

---

*Phase 74 retroactive Nyquist audit complete. 11 of 12 Per-Task Verification Map rows already had on-disk tests; 1 SECU-04 structural-invariant gap closed inside Plan 87-04 (`BackupUploadExceptionHandlerScopeIT`, 3 tests, green). No impl bugs surfaced.*
