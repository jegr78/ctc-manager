---
phase: 101-backup-restore-covers-discord-schema-v8-v15
plan: 04
status: complete
commits:
  - 1e51a84e feat(101): accept schema_version IN (1, 2) — lenient v1 import (D-10)
  - 0f2c7b01 test(101): add BackupLenientV1AcceptanceIT for v1 backup compatibility (D-17)
requirements_addressed:
  - D-10
  - D-11
  - D-17
---

# Plan 101-04 — Lenient schema_version IN (1, 2) + acceptance IT

## Outcome

The backup importer now accepts both v1 (pre-v1.13, 24 entities) and v2 (current, 26
entities) backup ZIPs. v3+ and v0 ZIPs are refused with `SCHEMA_MISMATCH`. The new
`BackupLenientV1AcceptanceIT` (5 IT methods) fences the accepted set and proves the
post-import Discord-tables-empty invariant.

## Changes

| File | Purpose |
|------|---------|
| `BackupImportService.java` | Adds `SUPPORTED_SCHEMA_VERSIONS = Set.of(1, 2)` constant; replaces equality check with `Set.contains`; `BackupImportPreview.schemaMatches` set from `SUPPORTED_SCHEMA_VERSIONS.contains(backupVersion)` (gate still throws first if not supported). |
| `BackupLenientV1AcceptanceIT.java` (new) | 5 IT methods — v1 stage acceptance + schemaMatches=true, v1 execute → Discord tables empty, v3 refusal, v0 refusal, v2 sanity. ZIPs built programmatically via `ZipOutputStream`, no binary fixtures. |

## Verification

- `./mvnw clean test-compile` — green.
- `./mvnw verify -Dit.test=BackupLenientV1AcceptanceIT -DfailIfNoTests=false`:
  - `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` (17.33 s).
  - Boot log confirms: `Backup import staged successfully: stagingId=..., schemaVersion=1, entityCount=26, uploadFileCount=0, totalImportedRows=0` — a v1 manifest passes the lenient gate and reaches the 26-entity preview.
  - JaCoCo coverage at 74% — expected for targeted-test runs; phase-end `clean verify -Pe2e` is the authoritative gate.
- `grep -rn "SUPPORTED_SCHEMA_VERSIONS" src/main/java/org/ctc/backup/` returns 1 hit (the new constant in BackupImportService).
- `grep -n "backupVersion != currentVersion" src/main/java/org/ctc/backup/service/BackupImportService.java` returns zero matches (the equality check is gone).

## Threat-Model Notes

- **T-101-03 (lenient version trust):** the manifest is authoritative on the entity-list — `BackupImportService.execute` iterates `BackupSchema.getExportOrder()` and reads ONLY entries listed in `manifest.tableCounts`. A v1 manifest does not list `discord_global_config` / `discord_post`, so even an attacker-injected `data/discord-global-config.json` inside a v1-manifest ZIP is never scanned. Test 2 (`...thenDiscordTablesStayEmpty`) provides the fence: after a v1 execute, both Discord tables are empty.
- D-05 (preserve webhookToken as-is) and D-07 (no cross-guild guard) are out-of-scope for this plan — they only become reachable on v2 imports.

## Scope Fence Preserved

- `wipeAllTables()` untouched — no pre-step UPDATE for `discord_post` (V12/V14 `ON DELETE SET NULL` covers it).
- `restoreAll()` untouched — existing missing-JSON branch handles v1 absence of Discord entries.
- `DiscordGlobalConfigService.getOrInitialize()` untouched — the self-heal contract remains on a separate page-load path, not inside `execute()`.
- `BackupSchema.SCHEMA_VERSION` untouched (Plan 01 already flipped it to 2).
- No SQL migrations changed.
- No checked-in binary backup ZIPs.

## Next

Plan 101-05 — write the D-15 + D-16 regression-fence ITs:
13 per-field round-trip assertions on the new Discord-entity fields +
byte-equality fences on `BackupDiscordFieldRoundTripIT` + extension of
`BackupRoundTripIT` for the new Discord-table row-count parity.
