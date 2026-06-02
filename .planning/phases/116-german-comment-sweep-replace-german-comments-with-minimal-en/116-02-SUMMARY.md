---
phase: 116-german-comment-sweep
plan: 02
type: execute
status: complete
requirements: [CLEAN-03]
---

# Plan 116-02 Summary — Test-source German comment sweep (CLEAN-03)

## Outcome

Replaced every German comment in the test sources with concise English, including "Saison" → "Season" in comment prose to match the actual fixture name `createSeason("Season 2023", ...)` (`TestDataService.java:178`). Comment prose only — no test-data name, string literal, `createSeason(...)` argument, assertion value, or `{@code}`/`{@link}` identifier changed.

## Final target list (Task-1 re-scan, authoritative)

**E2E tests (Task 2):**
- `AdminWorkflowE2ETest.java` — `:177` (`hat Fahrer ... und`), `:191` (`Fahrer im DOM vorhanden ...`), **plus drift** `:199` (`hat Sub-Teams`) and `:218` (`hat Lineups`) not in the CONTEXT floor → all four translated to English. Identifiers (`T-ALF`, `Test-Season 2026/2025`) preserved.
- `GroupsSeasonE2ETest.java` — `:27` (`GROUPS-Saison` → `GROUPS-season`), `:30-32` German workflow sentence → English, `:41` German mandate quote `"UI-Klick-Eintragung für Race-Results"` → `"enter race results via UI clicks"`. Existing requirement/decision codes (QUAL-02, ROADMAP-SC3, D-15) left as-is (English already; not the German job).
- `LegacyMigratedSeasonE2ETest.java` — `:56`, `:98` (`Saison-Detail` → `Season detail`), **plus drift** `:143` (`The Saison toolbar ...` → `The season toolbar ...`).

**Backup tests (Task 3) — "Saison" → "Season" in prose:**
- `BackupImportRollbackIT.java` — `:85,86,88` (Javadoc) + `:142,144,207` (inline).
- `BackupExportServiceIT.java:24`, `BackupImportExecuteIT.java:47`, `RaceResultRestorerTest.java:22` (`Saison-2023` → `Season-2023`), `FailAtTableInjector.java:23,73`.
- `BackupImportMariaDbSmokeIT.java:54,160,162` prose → `Season 2023`; the `{@code seedSaison2023()}` / `testDataService.seedSaison2023()` **code identifier** (`:54`, `:159`) left intact (references a non-existent method by name — identifier, not prose).

## Inventory drift found (beyond CONTEXT floor)

- `AdminWorkflowE2ETest.java:199` and `:218` — two additional German `//` comments not listed in the inventory (file already in files_modified, so in-scope).
- `LegacyMigratedSeasonE2ETest.java:143` — third `Saison` comment confirmed (flagged in plan).

## Out of scope (verified untouched)

- `PlayoffRestorerTest.java:59,99` — `"Saison 2023 Playoffs"` string-literal pair (JSON input + assertion). NOT in diff — confirmed.
- Track-name/UI string literals with umlauts (`Nürburgring`, `Über-Liga`, `Straße`, `Ümlauts`) in DiscordChannelServiceNamingTest, LogSanitizerTest, TemplatePreviewServiceTest, BackupExportNoLazyInitIT, TrackRestorerTest — string literals, not comments. Untouched.

## Verification (this plan)

- `Saison` in the 9 target files: only the 2 `seedSaison2023()` identifier references remain (correct per plan).
- Umlaut scan over the 9 files: **0** in comments.
- Test diff is comment-lines-only (`//`, `/* */`, Javadoc `*`) — no assertion, locator, `createSeason` arg, or string literal changed.
- PlayoffRestorerTest NOT in diff.
- Per locked verify-cadence, NO build run in this plan — deferred to 116-03.

## Files changed (9 test files)

AdminWorkflowE2ETest, GroupsSeasonE2ETest, LegacyMigratedSeasonE2ETest, BackupImportRollbackIT, BackupExportServiceIT, BackupImportExecuteIT, BackupImportMariaDbSmokeIT, FailAtTableInjector, RaceResultRestorerTest.
