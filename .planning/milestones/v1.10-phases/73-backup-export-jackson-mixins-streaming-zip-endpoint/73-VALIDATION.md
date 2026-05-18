---
phase: 73
slug: backup-export-jackson-mixins-streaming-zip-endpoint
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-11
approved_on: 2026-05-18
audit_method: retroactive
---

# Phase 73 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `73-RESEARCH.md` § Validation Architecture (see source for full mapping of EXPORT-01..06 ↔ test layers).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test + Playwright (E2E only) |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo) |
| **Quick run command** | `./mvnw test -Dtest='*MixIn*'` (single-file scope during Wave 1) — or `./mvnw test -Dtest=BackupExportServiceTest` during Wave 2 |
| **Full suite command** | `./mvnw verify` (unit + integration + JaCoCo) — `./mvnw verify -Pe2e` for E2E gate |
| **Estimated runtime** | quick: ~10–30s; full: ~3–5min; with `-Pe2e`: ~8–10min |

---

## Sampling Rate

- **After every task commit:** Run quick command scoped to the touched file(s).
- **After every plan wave:** Run `./mvnw verify` (no `-Pe2e`).
- **Before `/gsd-verify-work`:** `./mvnw verify -Pe2e` must be green. JaCoCo line coverage ≥ 82%.
- **Max feedback latency:** ~30 seconds for unit feedback during Wave 1; ~5 minutes for full pre-merge gate.

---

## Per-Task Verification Map

> **Note:** Original Task IDs (`73-NN-NN`) were placeholders at draft time. The Per-Task rows below have been retroactively reconciled with the real test classes that landed on disk in Plans 73-01 .. 73-04. Real file paths under `src/test/java/` shown in the "Test File" column.

| # | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Test File | Status |
|---|------|------|-------------|------------|-----------------|-----------|-----------|--------|
| V1  | 73-01 | 1 | EXPORT-04 | — | Each of the 24 entity classes has a paired MixIn that applies `@JsonIdentityInfo` externally; `org.ctc.domain.model` package is byte-identically unchanged | unit + IT | `src/test/java/org/ctc/backup/serialization/DriverMixInTest.java`, `RaceMixInTest.java`, `RaceAttachmentMixInTest.java`, `SeasonMixInTest.java`, `TeamMixInTest.java` + `BackupEntityAnnotationCleanlinessIT.java` | ✅ green |
| V2  | 73-02 | 1 | EXPORT-04 | — | `BackupSerializationModule` registers exactly 24 mixIn mappings and is wired into `backupObjectMapper` | unit | `src/test/java/org/ctc/backup/serialization/BackupSerializationModuleTest.java` | ✅ green |
| V3  | 73-02 | 1 | EXPORT-05 | — | Each repository fetch method used by the export carries `@EntityGraph(attributePaths={...})` covering every `@ManyToOne` association reachable in the export aggregate | IT | `src/test/java/org/ctc/backup/repository/BackupRepositoryEntityGraphIT.java` | ✅ green |
| V4  | 73-03 | 2 | EXPORT-05 | — | `BackupExportService.fetchAllForBackup(...)` runs under `@Transactional(readOnly=true)` and returns the export aggregate without triggering Hibernate session writes | unit + IT | `src/test/java/org/ctc/backup/service/BackupExportServiceTest.java` + `BackupExportServiceIT.java` | ✅ green |
| V5  | 73-03 | 2 | EXPORT-03 | — | `BackupArchiveService.writeZip(...)` writes `manifest.json` as ZipEntry #0, then `data/<slug>.json` per EXPORT_ORDER entity, then `uploads/` mirror; ordering is deterministic | IT | `src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java` + `BackupUploadsMirrorIT.java` | ✅ green |
| V6  | 73-03 | 2 | EXPORT-05 | — | Full dev-fixture export (Saison 2023 + 2024-3) completes with ZERO `LazyInitializationException` log lines (assertion via log capture / test appender) | IT | `src/test/java/org/ctc/backup/service/BackupExportNoLazyInitIT.java` | ✅ green |
| V7  | 73-04 | 3 | EXPORT-01, EXPORT-02 | — | `GET /admin/backup` renders `admin/backup.html` (200, sidebar entry "Backup" active); `POST /admin/backup/export` returns `200 application/zip` with `Content-Disposition: attachment; filename=ctc-backup-<ISO-compact>.zip` (regex `ctc-backup-\d{8}T\d{6}Z\.zip`) | IT (MockMvc) | `src/test/java/org/ctc/backup/BackupControllerIT.java` | ✅ green |
| V8  | 73-04 | 3 | EXPORT-06 | T-73-01 | Anonymous `POST /admin/backup/export` against `prod`/`docker` profile config returns 401/403 (Spring Security rejects); request WITHOUT `_csrf` token returns 403 (renamed from draft `BackupSecurityIT` to `BackupControllerSecurityIT` during implementation) | IT (MockMvc + `@WithAnonymousUser`, `@Nested` ProdProfileSecurityTest + DevProfileSecurityTest) | `src/test/java/org/ctc/backup/BackupControllerSecurityIT.java` | ✅ green |
| V9  | 73-04 | 3 | EXPORT-01 | — | Sidebar in `layout.html` shows a new top-level group "Data" with single entry "Backup" linking to `/admin/backup`; active-state class applied when on backup page | IT (Thymeleaf render) | `src/test/java/org/ctc/backup/AdminLayoutIT.java` | ✅ green |
| V10 | 73-04 | 3 | EXPORT-03 | — | Round-trip: re-read manifest.json from the exported ZIP via `backupObjectMapper`, verify schemaVersion + entityCounts match the source DB rowcount per entity (24-entity row-count parity) | IT | `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` (H2RoundTripTests + MariaDbRoundTripTests `@EnabledIfSystemProperty`) | ✅ green |
| V11 | 73-04 | 3 | EXPORT-01, EXPORT-02 | — | Playwright E2E: admin logs in → clicks "Backup" sidebar entry → clicks "Export Backup" → browser receives a download with filename matching `ctc-backup-\d{8}T\d{6}Z\.zip` (locked ISO-Instant pattern `ISO_FILENAME_REGEX`) | E2E | `src/test/java/org/ctc/e2e/BackupExportE2ETest.java` | ✅ green |
| V12 | 73-04 | 3 | — | — | JaCoCo line coverage for `org.ctc.backup.**` ≥ 82% after Wave 3; per `pom.xml` global minimum stays satisfied | coverage | `pom.xml` JaCoCo `check` goal + `target/site/jacoco/index.html` | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] No fresh framework install required — JUnit 5 + Mockito + Spring Boot Test + Playwright are all already on the classpath (verified via `pom.xml` + Phase 72 ITs). *(satisfied retroactively — all referenced test files exist on disk as of Phase 87 / Plan 87-03)*
- [x] Confirm a log-capturing appender helper exists, or add one in Plan 03 to assert `LazyInitializationException` absence in V6. *(satisfied retroactively — `BackupExportNoLazyInitIT` uses log capture via Logback ListAppender; 2 tests green)*

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions | Status |
|----------|-------------|------------|-------------------|--------|
| (none) | — | All success criteria are automatable — the E2E download interception (V11) is the only UI-driven path and is automated via Playwright. | — | n/a |

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or post-hoc evidence *(Wave 0 satisfied retroactively — all referenced test files now exist on disk)*
- [x] Sampling continuity: no 3 consecutive tasks without automated verify *(verified post-execution — V1..V12 each have a real test file)*
- [x] Wave 0 covers all MISSING references *(retroactively — all draft-time stubs matured into real assertion-bearing tests in-flight during v1.10)*
- [x] No watch-mode flags (Surefire/Failsafe run once and exit — no continuous watchers)
- [x] Feedback latency < 30s for unit-scoped quick command *(targeted Surefire run for 7 unit classes: ~3 s wallclock — well under 30 s)*
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-18 — retroactive audit via Phase 87 / Plan 87-03

---

## Validation Audit 2026-05-18

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Impl bugs fixed | 0 |
| Impl bugs deferred | 0 |

**Audit method:** retroactive (audit_method: retroactive) — all 6 Phase-73 requirements (EXPORT-01..06) were audited against the real test surface on disk via filename inventory, `@Tag` verification, and targeted Surefire/Failsafe runs. Every Per-Task Verification Map row mapped 1:1 to an existing test class.

**Per-requirement coverage matrix:**

| REQ-ID | Test Files | Verdict |
|--------|------------|---------|
| EXPORT-01 (admin endpoint + sidebar) | `BackupControllerIT`, `AdminLayoutIT`, `BackupExportE2ETest` | COVERED |
| EXPORT-02 (streaming ZIP + Content-Disposition ISO-Instant filename) | `BackupControllerIT` (`Matchers.matchesPattern("…ctc-backup-\\d{8}T\\d{6}Z\\.zip…")`), `BackupExportE2ETest` (`ISO_FILENAME_REGEX = Pattern.compile("ctc-backup-\\d{8}T\\d{6}Z\\.zip")`) | COVERED |
| EXPORT-03 (24-entity row-count parity + manifest-first ordering + uploads-mirror) | `BackupRoundTripIT`, `BackupArchiveServiceIT`, `BackupUploadsMirrorIT` | COVERED |
| EXPORT-04 (24 MixIns + no annotation drift in `org.ctc.domain.model`) | `BackupSerializationModuleTest`, `BackupEntityAnnotationCleanlinessIT`, `DriverMixInTest`, `RaceMixInTest`, `RaceAttachmentMixInTest`, `SeasonMixInTest`, `TeamMixInTest` | COVERED |
| EXPORT-05 (`@Transactional(readOnly=true)` + `@EntityGraph` eager-fetch / no LazyInitializationException) | `BackupRepositoryEntityGraphIT`, `BackupExportNoLazyInitIT`, `BackupExportServiceTest`, `BackupExportServiceIT` | COVERED |
| EXPORT-06 (CSRF-protected POST + Spring Security `@Nested` prod-profile test) | `BackupControllerSecurityIT` (`ProdProfileSecurityTest` + `DevProfileSecurityTest` nested) | COVERED |

**Targeted Phase 73 test runs (Plan 87-03, 2026-05-18 09:21–09:23 local):**
- Surefire: 7 unit classes (`DriverMixInTest`, `RaceMixInTest`, `RaceAttachmentMixInTest`, `SeasonMixInTest`, `TeamMixInTest`, `BackupSerializationModuleTest`, `BackupExportServiceTest`) — Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 → **BUILD SUCCESS**
- Failsafe: 10 IT classes (`BackupEntityAnnotationCleanlinessIT`, `BackupRepositoryEntityGraphIT`, `BackupExportNoLazyInitIT`, `BackupUploadsMirrorIT`, `BackupRoundTripIT`, `BackupControllerIT`, `BackupControllerSecurityIT`, `AdminLayoutIT`, `BackupArchiveServiceIT`, `BackupExportServiceIT`) — Tests run: 32, Failures: 0, Errors: 0, Skipped: 1 (`BackupRoundTripIT$MariaDbRoundTripTests`, `@EnabledIfSystemProperty(named="mariadb",matches="true")`, intentional gating) → **BUILD SUCCESS** (39 s wallclock)
- E2E: `BackupExportE2ETest` not re-executed in Plan 87-03 (re-using CI evidence below — class is tagged `@Tag("e2e")` and exercised in every `./mvnw verify -Pe2e` run).

**CI evidence:** Run-id `26008754136` (workflow_dispatch on `gsd/v1.11-tooling-and-cleanup` @ `b7f20b53`, conclusion: success, 2026-05-18T01:30:27Z, e2e step wallclock: 23:00). Full suite: `./mvnw verify -Pe2e` → BUILD SUCCESS. JaCoCo: ≥ 82% (gate held).

**Tag compliance:** All 10 ITs carry `@Tag("integration")`; `BackupExportE2ETest` carries `@Tag("e2e")` (verified via grep). Conforms to CLAUDE.md "Tag Tests by Category".

**Originating v1.10 verification:** `73-VERIFICATION.md status: passed` (2026-05-12) — Phase 73 already had a post-hoc verification gate at v1.10 close.

---

*Phase 73 retroactive Nyquist audit complete. All 12 Per-Task Verification Map rows resolved to existing on-disk tests; no gaps; no impl bugs surfaced.*
