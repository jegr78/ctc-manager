---
phase: 73
slug: backup-export-jackson-mixins-streaming-zip-endpoint
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-11
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
| **Quick run command** | `./mvnw test -Dtest='*Mixin*'` (single-file scope during Wave 1) — or `./mvnw test -Dtest=BackupExportServiceTest` during Wave 2 |
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

> **Note:** Task IDs (`73-NN-NN`) are placeholders. The planner fills the actual `Plan` and `Task ID` columns when it writes `73-NN-PLAN.md`. The rows below enumerate the verification commitments that any task in this phase MUST satisfy collectively.

| # | Plan (TBD) | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | Status |
|---|------------|------|-------------|------------|-----------------|-----------|-------------------|--------|
| V1  | 01 | 1 | EXPORT-03 | — | Each of the 24 entity classes has a paired MixIn that applies `@JsonIdentityInfo` externally; `org.ctc.domain.model` package is byte-identically unchanged | unit | `./mvnw test -Dtest='*MixinTest'` | ⬜ pending |
| V2  | 02 | 1 | EXPORT-03 | — | `BackupSerializationModule` registers exactly 24 mixIn mappings and is wired into `backupObjectMapper` | unit | `./mvnw test -Dtest=BackupSerializationModuleTest` | ⬜ pending |
| V3  | 02 | 1 | EXPORT-04 | — | Each repository fetch method used by the export carries `@EntityGraph(attributePaths={...})` covering every `@ManyToOne` association reachable in the export aggregate | unit | `./mvnw test -Dtest='*RepositoryEntityGraphTest'` | ⬜ pending |
| V4  | 03 | 2 | EXPORT-02, EXPORT-04 | — | `BackupExportService.fetchAllForBackup(...)` runs under `@Transactional(readOnly=true)` and returns the export aggregate without triggering Hibernate session writes | unit + IT | `./mvnw test -Dtest=BackupExportServiceTest` + `./mvnw verify -Dit.test=BackupExportServiceIT` | ⬜ pending |
| V5  | 03 | 2 | EXPORT-02 | — | `BackupArchiveService.writeZip(...)` writes `manifest.json` as ZipEntry #0, then `data/<slug>.json` per EXPORT_ORDER entity, then `uploads/` mirror; ordering is deterministic | IT | `./mvnw verify -Dit.test=BackupArchiveServiceIT` | ⬜ pending |
| V6  | 03 | 2 | EXPORT-04 | — | Full dev-fixture export (Saison 2023 + 2024-3) completes with ZERO `LazyInitializationException` log lines (assertion via log capture / test appender) | IT | `./mvnw verify -Dit.test=BackupExportNoLazyInitIT` | ⬜ pending |
| V7  | 04 | 3 | EXPORT-01, EXPORT-02 | — | `GET /admin/backup` renders `admin/backup.html` (200, sidebar entry "Backup" active); `POST /admin/backup/export` returns `200 application/zip` with `Content-Disposition: attachment; filename=ctc-backup-<ISO-compact>.zip` | IT (MockMvc) | `./mvnw verify -Dit.test=BackupControllerIT` | ⬜ pending |
| V8  | 04 | 3 | EXPORT-06 | T-73-01 | Anonymous `POST /admin/backup/export` against `prod`/`docker` profile config returns 401/403 (Spring Security rejects); request WITHOUT `_csrf` token returns 403 | IT (MockMvc + `@WithAnonymousUser`) | `./mvnw verify -Dit.test=BackupSecurityIT` | ⬜ pending |
| V9  | 04 | 3 | EXPORT-01 | — | Sidebar in `layout.html` shows a new top-level group "Data" with single entry "Backup" linking to `/admin/backup`; active-state class applied when on backup page | IT (Thymeleaf render) | `./mvnw verify -Dit.test=AdminLayoutIT` | ⬜ pending |
| V10 | 04 | 3 | EXPORT-02 | — | Round-trip: re-read manifest.json from the exported ZIP via `backupObjectMapper`, verify schemaVersion + entityCounts match the source DB rowcount per entity | IT | `./mvnw verify -Dit.test=BackupRoundTripIT` | ⬜ pending |
| V11 | 04 | 3 | EXPORT-01, EXPORT-02 | — | Playwright E2E: admin logs in → clicks "Backup" sidebar entry → clicks "Export Backup" → browser receives a download with filename matching `ctc-backup-\d{8}T\d{6}Z\.zip` | E2E | `./mvnw verify -Pe2e -Dit.test=BackupExportE2ETest` | ⬜ pending |
| V12 | 04 | 3 | — | — | JaCoCo line coverage for `org.ctc.backup.**` ≥ 82% after Wave 3; per `pom.xml` global minimum stays satisfied | coverage | `./mvnw verify` then `open target/site/jacoco/index.html` | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] No fresh framework install required — JUnit 5 + Mockito + Spring Boot Test + Playwright are all already on the classpath (verified via `pom.xml` + Phase 72 ITs).
- [ ] Confirm a log-capturing appender helper exists, or add one in Plan 03 to assert `LazyInitializationException` absence in V6.

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| (none) | — | All success criteria are automatable — the E2E download interception (V11) is the only UI-driven path and is automated via Playwright. | — |

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags (Surefire/Failsafe run once and exit — no continuous watchers)
- [ ] Feedback latency < 30s for unit-scoped quick command
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
