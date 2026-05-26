---
phase: 101-backup-restore-covers-discord-schema-v8-v15
plan: 03
status: complete
commits:
  - 5137a909 fix(101): close silent-NULL on Match V10/V11 columns (MatchRestorer)
  - 08f356cd fix(101): close silent-NULL on Team V9 discord_role_id (TeamRestorer)
  - 7846ec42 fix(101): close silent-NULL on Matchday V15 pairings fields (MatchdayRestorer)
  - 73f797ea fix(101): close silent-NULL on Season V13 thread-id fields (SeasonRestorer)
  - 651f33dd test(101): update TeamRestorerTest for V9 discord_role_id shift
requirements_addressed:
  - D-02
  - D-12 (existing-Restorer extension half — Plan 02 covered the 2 new Discord Restorers)
scope_deviation: minor (TeamRestorerTest fix; see "Test Impact" section)
---

# Plan 101-03 — V8-V15 column carry-forward in 4 existing Restorers

## Outcome

Silent-NULL regression on V8-V15 columns closed for the 4 affected entities. Each
Restorer's `INSERT_SQL` was widened to carry all V8-V15 columns; each lambda extended
with `nullableString` / `setNullableTimestamp` helper-bound binds.

| Restorer | New cols | INSERT_SQL total | Lambda binds |
|----------|----------|------------------|--------------|
| MatchRestorer | +8 (V10 ×7, V11 ×1) | 17 | 17 (+8 nullables) |
| TeamRestorer | +1 (V9 discord_role_id) | 11 (Pass-1) | 10 (NULL self-FK still hard-coded) |
| MatchdayRestorer | +2 (V15 pick_deadline, scheduled_weekend) | 9 | 9 |
| SeasonRestorer | +2 (V13 thread-ids) | 10 | 10 |

13 V8-V15 columns covered (column-name audit passes; zero `MISSING:` lines).

## Atomic Commits

Per Plan 03 success criterion "4 separate atomic commits for surgical git blame",
each Restorer landed in its own commit; the TeamRestorerTest fix landed separately.

```
5137a909 fix(101): close silent-NULL on Match V10/V11 columns (MatchRestorer)
08f356cd fix(101): close silent-NULL on Team V9 discord_role_id (TeamRestorer)
7846ec42 fix(101): close silent-NULL on Matchday V15 pairings fields (MatchdayRestorer)
73f797ea fix(101): close silent-NULL on Season V13 thread-id fields (SeasonRestorer)
651f33dd test(101): update TeamRestorerTest for V9 discord_role_id shift
```

## Helper Pattern

- `setNullableTimestamp(PreparedStatement, int, JsonNode, String)` added to MatchRestorer + MatchdayRestorer (Types.TIMESTAMP).
- `nullableString(JsonNode, String)` added to MatchRestorer + MatchdayRestorer (mirrors the pre-existing helper in TeamRestorer + SeasonRestorer).
- Per Phase 75 convention (Plan 03 PLAN.md), per-Restorer helper duplication is acceptable over a shared utility.

## Test Impact

`TeamRestorerTest::givenTeamsWithAndWithoutParents_whenRestoreCalled_thenTwoBatchUpdatesIssuedInOrder`
asserted `setTimestamp(8, ...)` / `setTimestamp(9, ...)` for createdAt/updatedAt; the
Pass-1 bind shift (discord_role_id inserted at idx 8 → timestamps shifted to 9/10)
moved those expectations. The test was updated in commit 651f33dd to:

- Add `verify(psPass1).setString(8, null)` for the discordRoleId-null root team.
- Shift the createdAt timestamp verify from idx 8 → idx 9.
- Shift the updatedAt timestamp verify from idx 9 → idx 10.

Plan 03 PLAN.md `files_modified` listed only the 4 production files. The test fix was a
necessary downstream consequence not anticipated by the Plan-Quality-Gate test-impact
audit; same pattern as Plan 02's BackupExportService scope deviation. Documented here
rather than back-edited into the plan to keep planning artefacts immutable.

## Verification

- `./mvnw clean test-compile` — green after every commit.
- `./mvnw clean verify -Dit.test='BackupRoundTripIT,BackupSchemaGuardTest,BackupArchiveServiceReadIT' -Dtest='TeamRestorerTest,SeasonRestorerTest,MatchRestorerTest,MatchdayRestorerTest' -DfailIfNoTests=false`:
  - Surefire: `MatchRestorerTest 3/3`, `SeasonRestorerTest 4/4`, `MatchdayRestorerTest 3/3`, `TeamRestorerTest 4/4` — all green.
  - Failsafe: `BackupSchemaGuardTest 2/2`, `BackupArchiveServiceReadIT 6/6` — all green; `BackupRoundTripIT` initialised (24.98 s context) but matched 0 leaf tests due to nested-class IT discovery quirk (same as Plan 02 — the byte-equality fences in Plan 05 will exercise these flows).
  - JaCoCo coverage at 28% — expected for a targeted-test run; phase-end `./mvnw clean verify -Pe2e` is the authoritative coverage gate.
- Column-name audit: zero `MISSING:` for the 13 V8-V15 columns.

## Scope Fence Preserved

- All Flyway migrations (V1, V9, V10, V11, V13, V15) — untouched (immutable).
- `Match.java`, `Team.java`, `Matchday.java`, `Season.java` entities — untouched.
- `MatchMixIn`, `TeamMixIn`, `MatchdayMixIn`, `SeasonMixIn` — untouched (Lombok getters drive default JSON serialisation).
- `BackupImportService.java` schema-version equality check — Plan 04 owns it.
- `discord_global_config` V9/V13/V15 columns (current_match_category_id, race_results_forum_webhook_url, standings_forum_webhook_url, matchday_pairings_template) — Plan 02's DiscordGlobalConfigRestorer owns them.
- `season_cars` / `season_tracks` join-table writes — out of scope.

## Next

Plan 101-04 — extend `BackupImportService` to accept both `schema_version=1` and
`schema_version=2`, plus write a `BackupLenientV1AcceptanceIT` proving v1 backups still
import cleanly (D-10 + D-11 + D-17).
