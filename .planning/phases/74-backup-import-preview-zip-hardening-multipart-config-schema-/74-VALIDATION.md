---
phase: 74
slug: backup-import-preview-zip-hardening-multipart-config-schema
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-12
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

> Final per-task mapping is populated by the planner. The matrix below lists the **mandatory tests** identified during research; planner may split/merge per plan.

| Test Class | Wave | Requirement | Secure Behavior | Test Type | Automated Command | Status |
|------------|------|-------------|-----------------|-----------|-------------------|--------|
| `BackupImportServiceIT` | 2-3 | IMPORT-01, IMPORT-02 | Happy path: stage real Phase-73 export ZIP, get preview DTO with non-zero counts | IT | `./mvnw -Dit.test=BackupImportServiceIT verify` | ⬜ pending |
| `BackupImportSchemaVersionMismatchIT` | 2-3 | IMPORT-02, SECU-04 | Forged manifest `schema_version=999` → HTTP 400 + Flash + `Repository.count()` snapshot byte-identical before/after | IT | `./mvnw -Dit.test=BackupImportSchemaVersionMismatchIT verify` | ⬜ pending |
| `BackupImportZipSlipIT` | 2-3 | SECU-01, SECU-02 | Fixture ZIP with `../../etc/passwd` → reject + staging file deleted | IT | `./mvnw -Dit.test=BackupImportZipSlipIT verify` | ⬜ pending |
| `BackupImportZipBombIT` | 2-3 | SECU-01 | Inflated-byte counter triggers on entry > 50 MB, total > 500 MB, count > 50 000 | IT | `./mvnw -Dit.test=BackupImportZipBombIT verify` | ⬜ pending |
| `BackupImportMultipartLimitIT` | 2-3 | SECU-03, SECU-04 | 101 MB `MockMultipartFile` → `MaxUploadSizeExceededException` → Flash redirect (D-02#1) | IT | `./mvnw -Dit.test=BackupImportMultipartLimitIT verify` | ⬜ pending |
| `BackupImportConfirmFormValidationIT` | 2-3 | IMPORT-04 | `@NotNull @AssertTrue Boolean acknowledged` rejects missing/false → re-render confirm page with error | IT | `./mvnw -Dit.test=BackupImportConfirmFormValidationIT verify` | ⬜ pending |
| `BackupImportControllerSecurityIT` | 2-3 | SECU-04 | Profile-conditional auth: anonymous/CSRF matrix on prod; anonymous-allowed on dev | IT | `./mvnw -Dit.test=BackupImportControllerSecurityIT verify` | ⬜ pending |
| `BackupStagingCleanupIT` | 1 | D-17 (CONTEXT) | 3 stale `upload-*.zip` fixtures → `ApplicationReadyEvent` → all deleted + 1 info-log | IT | `./mvnw -Dit.test=BackupStagingCleanupIT verify` | ⬜ pending |
| `BackupArchiveServiceReadIT` | 1-2 | IMPORT-01 | `readManifest` / `countDataEntries` (streaming) / `countUploadFiles` round-trip vs Phase 73 export | IT | `./mvnw -Dit.test=BackupArchiveServiceReadIT verify` | ⬜ pending |
| `BackupImportE2ETest` | 3 | IMPORT-01..04 | Playwright full UI: upload Phase-73 export → preview cards → Proceed → confirm checkbox → submit → land on `/admin/backup` with stub Flash (D-02#5) | E2E | `./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest` | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Programmatic malicious fixtures via `ZipOutputStream` in test `@BeforeAll` (D-25 — no committed binaries):
  - Path-traversal: entry name `../../etc/passwd`.
  - ZIP bomb (size lie): `ZipEntry.setSize(Long.MAX_VALUE)`, small payload — forces inflated-byte counter to fire.
  - Total-size overflow: many entries summing > 500 MB.
  - Entry-count overflow: 50 001 trivial entries.
- [ ] Happy-path fixture: invoke Phase 73's `BackupArchiveService.writeZip()` at runtime — produces valid ZIP bytes without committed binary.
- [ ] No new framework installs — JUnit 5, Spring Boot Test, Playwright already on `compile`/`test` classpath.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual card grid responsiveness (24 cards, mobile + desktop) | IMPORT-03 (D-03) | Pixel-level visual review; Playwright covers click-through but not aesthetics | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` → upload a sample backup → `playwright-cli open http://localhost:9090/admin/backup/import-preview` (Desktop + Mobile viewports) per `feedback_playwright_cli.md`. |
| Delta-pill color semantics (red = data loss visible at a glance) | D-03 | Subjective UX evaluation | Same browser walkthrough; assert red pills visible when imported < current. |

---

## Validation Architecture (mapped to ROADMAP success criteria)

Per RESEARCH.md `## Validation Architecture` section. Each ROADMAP success criterion has at least one observable assertion and an owning test class.

| SC# | Success Criterion | Observable Assertion | Owning Test |
|-----|-------------------|----------------------|-------------|
| SC#1 | Admin uploads → preview screen with per-table counts + uploads count + schema-match indicator | UI: `BackupImportE2ETest` asserts grid renders 24 entity cards + schema-match pill. IT: `BackupImportServiceIT` asserts `BackupImportPreview` DTO populated. | `BackupImportE2ETest`, `BackupImportServiceIT` |
| SC#2 | Schema mismatch → HTTP 400 + Flash + DB byte-identically unchanged | IT: `Repository.count()` snapshot for each of 24 entities before/after rejected upload; HTTP 400 status; `errorMessage` flash present. | `BackupImportSchemaVersionMismatchIT` |
| SC#3 | ZIP-Slip / absolute path / >50 MB entry → reject Flash + malicious test fixture committed in source | IT: rejection thrown, `errorMessage` flash present, staging file deleted; fixtures live in `src/test/resources/backup-fixtures/malicious/` generated programmatically (D-25). | `BackupImportZipSlipIT`, `BackupImportZipBombIT` |
| SC#4 | Upload > 100 MB → Flash D-02#1 instead of stack trace | IT: `MockMultipartFile(101 MB)` POST returns redirect with `errorMessage` matching D-02#1; no Tomcat trace in response body. | `BackupImportMultipartLimitIT` |
| SC#5 | Preview state STATELESS — staging UUID re-read at execute, no `@SessionAttributes` | IT: grep `BackupController` for `@SessionAttributes` (must be 0); execute stub re-reads file by UUID from form param. E2E: cookie-jar reset between preview and execute proves no session state carries. | `BackupImportControllerSecurityIT`, `BackupImportE2ETest` |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (malicious fixture generators)
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s for IT, < 180s including E2E
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
