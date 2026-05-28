---
phase: 101-backup-restore-covers-discord-schema-v8-v15
plan: 02
status: complete
commit: 2fd7acd5
requirements_addressed:
  - D-01
  - D-03
  - D-04
  - D-05
  - D-07
  - D-08
  - D-12
  - D-14 (entity-count half — Plan 01 owned the SCHEMA_VERSION half)
scope_deviation: yes (see "Scope Deviation" section)
---

# Plan 101-02 — Discord MixIns + Restorers + entity-count 24→26

## Outcome

Atomic commit landing Discord entities as first-class backup citizens. The Spring context
boots with `BackupSchema.getExportOrder().size() == 26`, the export round-trip emits two
new ZIP entries (`data/discord-global-config.json` + `data/discord-post.json`), and the
entity-count guard test asserts 26.

## Changes

| File | Status | Purpose |
|------|--------|---------|
| `BackupSchema.java` | modified | Predicate accepts `org.ctc.domain.model.*` AND `org.ctc.discord.model.*` (D-08). |
| `DiscordGlobalConfigMixIn.java` | created | Minimal MixIn — no `@ManyToOne`, no computed getters. |
| `DiscordPostMixIn.java` | created | Minimal MixIn — FKs are `@Column UUID`, no `@JsonIdentityReference`. `webhookToken` preserved (D-05). |
| `BackupSerializationModule.java` | modified | Two new `setMixInAnnotation` registrations; Javadoc count 24 → 26. |
| `DiscordGlobalConfigRestorer.java` | created | 13-column `JdbcTemplate.batchUpdate` (10 data + 1 id + 2 audit). |
| `DiscordPostRestorer.java` | created | 15-column `JdbcTemplate.batchUpdate` (13 data + 1 id + 2 audit). NOT NULL `posted_at` directly bound; 5 nullable UUID FKs use `setNullableUuid` helper. |
| `BackupSchemaGuardTest.java` | modified | Entity-count test renamed `...HasTwentyFourEntities` → `...HasTwentySixEntities` and assertion `.isEqualTo(24)` → `.isEqualTo(26)`. |
| `DiscordGlobalConfigRepository.java` | modified | `findAllForBackup()` added (Plan 73-02 contract). |
| `DiscordPostRepository.java` | modified | `findAllForBackup()` added (Plan 73-02 contract). |
| `BackupExportService.java` | modified | Constructor + `initialize()` map gains the two Discord repositories; stale "24" error message made dynamic. |
| `BackupExportServiceTest.java` | modified | Mock + constructor call extended for the two new repos. |

## Scope Deviation

The original PLAN.md `files_modified` whitelist listed 7 files. The actual atomic commit
touched 11 files. Reason:

PLAN.md `<behavior>` claimed two assumptions that didn't hold against the live codebase:

1. **"`BackupImportService.wireRestorersByTableName()` finds an EntityRestorer bean for every one of the 26 table-names."** — `wireRestorersByTableName` does not exist; the restorer wiring lives inside `BackupImportService.execute()` via component-scan auto-discovery of `EntityRestorer` beans. This part of the assumption survived.
2. **"`BackupImportService.@PostConstruct wireRepositoriesByTableName()` finds a JpaRepository bean for every one of the 26 table-names."** — `wireRepositoriesByTableName` also does not exist. The actual mechanism is `BackupExportService.initialize()`, which manually populates `repositoriesByEntityClass` with constructor-injected repositories. Auto-discovery does NOT happen.

Without extending the scope to `BackupExportService` + the two Discord repositories,
`BackupRoundTripIT` and `BackupArchiveServiceReadIT` failed at runtime with:

```
java.lang.IllegalArgumentException: No repository registered for entity class
  org.ctc.discord.model.DiscordGlobalConfig — must be one of the 24
  BackupSchema.getExportOrder() entities
```

Plan-Quality-Gate symbol-existence audit (`grep -rn "wireRepositoriesByTableName"` on
the live `src/`) would have caught this before execution.

Files added to scope:

- `src/main/java/org/ctc/backup/service/BackupExportService.java`
- `src/main/java/org/ctc/discord/repository/DiscordGlobalConfigRepository.java`
- `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java`
- `src/test/java/org/ctc/backup/service/BackupExportServiceTest.java`

## Verification

- `./mvnw clean verify -Dit.test='BackupSchemaGuardTest,BackupRoundTripIT,BackupImportMariaDbSmokeIT,BackupArchiveServiceReadIT' -DfailIfNoTests=false`:
  - `Tests run: 16, Failures: 0, Errors: 0, Skipped: 2` (skipped = MariaDB-only opt-in).
  - Boot log confirms: `Backup export completed: dataEntries=26, uploadEntries=17` and `Backup data counts read: entries=26, totalRows=3834`.
  - JaCoCo coverage at 75% — expected for a targeted-test run; phase-end `./mvnw clean verify -Pe2e` is the authoritative coverage gate.
- `grep -rn "org.ctc.discord.model" src/main/java/org/ctc/backup/` returns 6 hits (schema predicate, 2 module imports, 2 MixIn imports, 1 export-service import block) — confirms package-filter expansion + module registration + service wiring.
- `grep -rn "implements EntityRestorer" src/main/java/org/ctc/backup/restore/entity/` returns 26 hits (existing 24 + 2 new).
- `wc -l src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java` reports 46 lines (was 42; +2 imports + +2 registrations).

## Scope Fence Preserved

- V8-V15 SQL migrations: untouched (immutable).
- `Match.java`, `Matchday.java`, `Season.java`, `Team.java` entities + their Restorers: untouched (Plan 03 owns V8-V15 column carry-forward).
- `BackupImportService.java` schema-version equality check: untouched (Plan 04 owns lenient `IN (1, 2)`).
- `wipeAllTables`: untouched (V12/V14 `ON DELETE SET NULL` is the wipe-cascade mechanism).
- Existing 24 MixIns + Restorers: append-only (no rewrites).

## Threat-Model Notes

- T-101-01 (webhookToken in ZIP): accepted per D-05 — operator-side filesystem ACL is the mitigation, documented in Plan 06 (`docs/operations/discord-integration.md`).
- T-101-04 (guild_id tampering on import): accepted — V8 `VARCHAR(32)` constrains payload shape; downstream SSRF whitelist (Phase 93 INFRA-02) gates outbound use.
- T-101-02 (cross-guild restore): not mitigated in code per D-07 (operator-trust model).
- T-101-03 (lenient version trust): reachable only via Plan 04; out of scope here.
- T-101-SC (supply-chain): no new external dependencies introduced.

## Next

Plan 101-03 — extend `MatchRestorer`, `TeamRestorer`, `MatchdayRestorer`, `SeasonRestorer`
to carry forward the V8-V15 columns (D-02 silent-NULL fix, D-12 four extension cases).
