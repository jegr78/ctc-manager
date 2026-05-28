---
phase: 101-backup-restore-covers-discord-schema-v8-v15
status: verified
verified_at: 2026-05-26
---

# Phase 101 Verification — Backup/Restore Covers Discord Schema (V8-V15)

## Goal-Backward Analysis

Phase 101's goal: extend the backup wire contract to include Discord operator state
(V8-V15 columns + `discord_global_config` + `discord_post`) so the export/import flow
round-trips Discord configuration alongside league entities, without breaking
compatibility with pre-v1.13 backups.

## Requirements Coverage (17 / 17)

| ID | Requirement | Verified by |
|----|-------------|-------------|
| D-01 | `DiscordGlobalConfig` + `DiscordPost` inclusion | Plan 02 (package-filter expansion + MixIns + Restorers); `BackupSchemaGuardTest` asserts 26 entities |
| D-02 | Silent-NULL fix on V8-V15 columns | Plan 03 (Match/Team/Matchday/Season Restorers extended); `BackupDiscordFieldRoundTripIT` 14 round-trip fence tests |
| D-03 | `DiscordGlobalConfig` single-row handling | Plan 02 (`DiscordGlobalConfigRestorer` treats `rows` generically; empty → 0-row batchUpdate) |
| D-04 | `discord_post` topo-position via `ON DELETE SET NULL` | Plan 02 (V12/V14 wipe-cascade) + Plan 05 hotfix (`pinDiscordPostLast` pinning end of export order for restore-order safety) |
| D-05 | Preserve `webhookToken` as-is | Plan 02 (`DiscordPostMixIn` has no `@JsonIgnore`); `BackupRoundTripIT.givenSeededDiscord_…` SHA-256 byte-equality fences the round-trip |
| D-06 | Single-guild documented | Plan 06 (`docs/operations/discord-integration.md` § 8.1 + recovery flow) |
| D-07 | No cross-guild guard code | No new guards added; deliberately documented as undefined behaviour |
| D-08 | Package-filter expansion mechanism | Plan 02 (`BackupSchema` filter accepts `org.ctc.domain.model.*` AND `org.ctc.discord.model.*`) |
| D-09 | `SCHEMA_VERSION` 1 → 2 | Plan 01 (atomic flip + paired guard test) |
| D-10 | Lenient `IN (1, 2)` | Plan 04 (`SUPPORTED_SCHEMA_VERSIONS` constant + gate-loosening) |
| D-11 | v1 imports leave Discord empty | Plan 04 (`BackupLenientV1AcceptanceIT` test 2 asserts both counts 0); D-11 behavior confirmed |
| D-12 | 4 Restorer extensions | Plan 03 (Match/Team/Matchday/Season) atomic commits |
| D-13 | Full first-class test coverage | Plans 04 + 05 (acceptance IT + 14 per-field fences + byte-equality fences on H2 + MariaDB) |
| D-14 | Guard-test flips (SCHEMA_VERSION + entity-count) | Plan 01 (SCHEMA_VERSION half) + Plan 02 (entity-count half) |
| D-15 | 13 per-field regression-fence | Plan 05 (`BackupDiscordFieldRoundTripIT` 13 positive + 1 negative methods) |
| D-16 | Byte-equality `DiscordGlobalConfig` + `DiscordPost` | Plan 05 (`BackupRoundTripIT.givenSeededDiscord_…` in both H2 + MariaDB nested classes) |
| D-17 | Lenient-v1-acceptance IT | Plan 04 (`BackupLenientV1AcceptanceIT`, 5 IT methods) |

## Build & Test Verification

`./mvnw clean verify -Pe2e` — **BUILD SUCCESS** (08:55 min).

| Gate | Result |
|------|--------|
| Surefire (Unit) | 1249 tests, 0 failures, 0 errors, 3 skipped |
| Failsafe (Integration + E2E + Playwright) | 593 tests, 0 failures, 0 errors, 1 skipped |
| Total | **1842 tests green** |
| JaCoCo line coverage | ≥ 82% threshold met (`All coverage checks have been met`) |
| SpotBugs + find-sec-bugs | `BugInstance size is 0` — no Medium/HIGH findings |
| OpenRewrite, Thymeleaf fragment-call guard, Assumptions-fence guard | green |

## Scope Deviations Recorded

Three scope deviations are documented in the per-plan SUMMARY files (no separate
hotfix plans needed — captured under the phase that discovered them):

1. **Plan 02 — `BackupExportService` wiring + 2 Discord-Repository `findAllForBackup()`.**
   The original plan assumed auto-discovery of repositories; the real wiring is manual
   constructor injection in `BackupExportService.initialize()`. Extended scope by 4 files
   (`BackupExportService.java`, `DiscordGlobalConfigRepository.java`, `DiscordPostRepository.java`,
   `BackupExportServiceTest.java`).

2. **Plan 05 — `pinDiscordPostLast` in `BackupSchema`.** Plan 02 RESEARCH §Pitfall 3
   chose `@Column UUID` for DiscordPost FKs (over `@ManyToOne`), but the JPA-Metamodel
   topo-sorter only considers JPA associations — `discord_post` could be restored BEFORE
   its FK parents. Hotfix lives in `BackupSchema.initializeExportOrder()`; reverts the
   `DiscordPostRestorer` 2-pass attempt to single-pass.

3. **Phase-end collateral — 7 hardcoded `24-entity` / `SCHEMA_VERSION-1` assertions in
   pre-Phase-101 tests.** Plan-Quality-Gate test-impact audit missed:
   `BackupControllerIT`, `BackupArchiveServiceIT`, `BackupSchemaTopologyIT`,
   `BackupRepositoryEntityGraphIT`, `BackupImportSchemaMismatchIT`, `BackupImportE2ETest`,
   `admin/backup.html` template. Plus the `SUPPORTED_SCHEMA_VERSIONS.toString()`
   ordering (Set.of(1, 2) → "[2, 1]" with hash-randomised iteration; switched to
   `LinkedHashSet`). Two `DiscordPostGuardTest` + `DiscordGlobalConfigGuardTest` fences
   that explicitly forbade Phase 101's inclusion were deleted (obsoleted by Phase 101's
   deliberate inclusion; `BackupSchemaGuardTest` carries the equivalent positive
   assertion).

## Verdict

**Phase 101 goal achieved.** Backup/Restore now covers the Discord schema. All 17
requirements have direct or transitive evidence (tests + code references). 6 atomic
plan-SUMMARY commits + multiple per-task atomic commits + 1 collateral-fix commit
land on `gsd/v1.13-discord-integration`. End-of-phase verify is green.
