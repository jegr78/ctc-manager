---
phase: 82
slug: backup-cleanup
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-16
---

# Phase 82 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito + Spring Boot Test |
| **Config file** | `pom.xml` (Surefire: lines 184–194; Failsafe: lines 256–278) |
| **Quick run command (unit)** | `./mvnw test -Dtest=<ClassName>` |
| **Quick run command (IT)** | `./mvnw verify -Dit.test=<ClassNameIT> -DfailIfNoTests=false` |
| **Full suite command** | `./mvnw verify -Pe2e` |
| **Estimated runtime (full)** | ~9 minutes (1011 tests + 36 Playwright E2E) |

---

## Sampling Rate

- **After every fix commit (main/java-touching):** Run the targeted `-Dtest=` / `-Dit.test=` per D-26 in CONTEXT.md + `./mvnw spotbugs:check -DskipTests` per D-27 (skip optional for IN-01/IN-02 annotation-only commits).
- **After every test commit:** Run the new test in isolation.
- **After every annotation-only commit (IN-01, IN-02):** `./mvnw test -Dtest='*Restorer*'` (Surefire only, cheap).
- **Before `/gsd:verify-work`:** ONE final `./mvnw verify -Pe2e` — full suite green, JaCoCo ≥ 82 % (target hold 87.80 % ± 0.5 pp), SpotBugs gate green.
- **Max feedback latency:** ~20 seconds for targeted unit tests; ~90 seconds for targeted ITs; ~9 min for final full verify.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 82-01-01 | 01 | 1 | WR-01 | — | `BackupExecutedByResolver.resolve()` returns `"dev"` on `dev|local` profile, callerOverride when non-blank, `auth.getName()` from `SecurityContextHolder`, or `"unknown"` fallback | unit | `./mvnw test -Dtest=BackupExecutedByResolverTest` | ❌ W0 | ⬜ pending |
| 82-02-01 | 02 | 1 | IN-04 | — | `import-backups-dir` resolves to `data/${spring.profiles.active}/import-backups` per profile | integration | `./mvnw verify -Dit.test=BackupImportServiceIT` | ✅ existing (extend) | ⬜ pending |
| 82-03-01 | 03 | 1 | IN-03 | — | Missing ZIP entry escalates from `log.debug` to `log.warn` with corruption-signal message | integration | `./mvnw verify -Dit.test=BackupRestoreZipOpenCountIT` (or sibling IT) | ❌ W0 | ⬜ pending |
| 82-04-01 | 04 | 1 | IN-01 | — | 18 restorers lose no-op `@RequiredArgsConstructor`; existing Spring wiring + restorer ITs still green | integration | `./mvnw verify -Dit.test=BackupImportServiceIT,BackupRoundTripIT` | ✅ existing | ⬜ pending |
| 82-05-01 | 05 | 1 | IN-02 | — | All 24 restorers consistently `@Slf4j @Component [@RequiredArgsConstructor]` annotation order | static (grep) | `git grep -E "^@Component\\b" src/main/java/org/ctc/backup/restore/entity/` returns nothing matching `@Component @Slf4j` | ✅ existing | ⬜ pending |
| 82-06-01 | 06 | 2 | BACK-01 | — | `BackupSchema.SCHEMA_VERSION == 1` AND `getExportOrder().size() == 24` | integration (SpringBootTest slice) | `./mvnw verify -Dit.test=BackupSchemaGuardTest` | ❌ W0 | ⬜ pending |
| 82-07-01 | 07 | 2 | BACK-03 | — | `BackupImportService.restoreAll` opens ZIP exactly once per `execute()` (counter == 1) | integration | `./mvnw verify -Dit.test=BackupRestoreZipOpenCountIT` | ❌ W0 | ⬜ pending |
| 82-08-01 | 08 | 2 | BACK-05 | — | 24-entity row-count parity post-roundtrip on H2 nested class | integration | `./mvnw verify -Dit.test=BackupRoundTripIT` | ✅ existing (extend) | ⬜ pending |
| 82-08-02 | 08 | 2 | BACK-05 | — | 24-entity row-count parity post-roundtrip on MariaDB nested class (`docker.available` gated) | integration | `./mvnw verify -Dit.test=BackupRoundTripIT -Ddocker.available=true` | ✅ existing (extend) | ⬜ pending |
| 82-09-01 | 09 | 3 | BACK-02 | — | `82-BACKLOG-AUDIT.md` lists 7 pre-resolved REVIEW.md IDs with commit SHAs + 5 new commits | manual audit | `cat .planning/phases/82-backup-cleanup/82-BACKLOG-AUDIT.md` + reviewer check | ❌ W0 | ⬜ pending |
| 82-10-01 | 10 | 3 | BACK-04 (gate) | — | `BackupRoundTripIT` + `BackupImportRollbackIT` green on H2 + MariaDB; SpotBugs gate green; JaCoCo ≥ 82 % | full verify | `./mvnw verify -Pe2e` | ✅ existing | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Net-new test files Phase 82 must create (research-confirmed gap list):

- [ ] `src/test/java/org/ctc/backup/audit/BackupExecutedByResolverTest.java` — 4 unit tests covering all 4 resolution branches (WR-01)
- [ ] `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java` — 2 IT tests (SCHEMA_VERSION + EXPORT_ORDER size) — BACK-01
- [ ] `src/test/java/org/ctc/backup/service/BackupRestoreZipOpenCountIT.java` — 2 IT tests: ZIP-open count == 1 (BACK-03) + WARN log on missing entry (IN-03 co-located per research recommendation)
- [ ] Extend existing `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` — add 1 `@Test` to each `@Nested` class (`H2RoundTripTests` + `MariaDbRoundTripTests`) — BACK-05

Net-new docs:

- [ ] `.planning/phases/82-backup-cleanup/82-BACKLOG-AUDIT.md` — covers BACK-02 (maps 7 pre-resolved REVIEW.md IDs to commit SHAs + 5 new commits)

Infrastructure already in place (no Wave 0 needed):

- Maven Surefire + Failsafe + JaCoCo (confirmed by Phase 81 verification — 87.80 % baseline)
- SpotBugs gate (Phase 81 just shipped — config/spotbugs-exclude.xml in place)
- `OutputCaptureExtension` from Spring Boot Test (used by 3 existing backup ITs — research-confirmed convention for IN-03 WARN-log assertion)
- Testcontainers MariaDB pattern (used in `BackupRoundTripIT.MariaDbRoundTripTests` with `docker.available` gate)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| `82-BACKLOG-AUDIT.md` lists the 12 REVIEW.md IDs with correct commit SHAs for the 7 pre-resolved items | BACK-02 | Doc artifact; no automated test can verify that the SHAs map to the correct commit messages | Review the markdown table; spot-check 2–3 SHAs via `git show <sha>` to confirm the subject matches the REVIEW.md ID |
| SpotBugs gate stays green after WR-01 (new `BackupExecutedByResolver` bean) | SC#4 cross-phase | Phase 81 D-08 layer 2 `EI_EXPOSE_REP*` filter is package-level; new bean is in `org.ctc.backup.audit` package — research confirms no new suppression needed, but the planner must re-verify after the first fix commit | `./mvnw spotbugs:check -DskipTests` after `fix(82): WR-01` commit |
| MariaDB nested test inherits `docker.available` gate (does NOT run on every dev verify) | SC#5 cross-phase | Confirms BACK-05's new `@Test` in `MariaDbRoundTripTests` does not regress the dev-verify wall-clock by booting a Testcontainers MariaDB unnecessarily | Run `./mvnw verify` WITHOUT `-Ddocker.available=true` and confirm log shows "skipped due to @EnabledIf*" or equivalent for `MariaDbRoundTripTests` |
| README "## Backup & Restore" section reflects the new profile-isolated `import-backups-dir` path | IN-04 cross-phase | Doc rendering check; the wiki page may also reference the old path | Visually compare the section after the IN-04 commit; confirm wiki page `Backup-and-Restore.md` is updated if it references the recovery path |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies (above table)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (4 new test files + 1 audit doc)
- [ ] No watch-mode flags in any test command
- [ ] Feedback latency < 90s for targeted ITs; < 20s for unit tests
- [ ] `nyquist_compliant: true` set in frontmatter (after planner finalizes task IDs)

**Approval:** pending
