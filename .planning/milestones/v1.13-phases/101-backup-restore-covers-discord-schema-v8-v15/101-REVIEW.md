---
phase: 101-backup-restore-covers-discord-schema-v8-v15
reviewed: 2026-05-28T00:00:00Z
depth: standard
files_reviewed: 26
files_reviewed_list:
  - src/main/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/DiscordPostRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/MatchRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/MatchdayRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/SeasonRestorer.java
  - src/main/java/org/ctc/backup/restore/entity/TeamRestorer.java
  - src/main/java/org/ctc/backup/schema/BackupSchema.java
  - src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java
  - src/main/java/org/ctc/backup/serialization/DiscordGlobalConfigMixIn.java
  - src/main/java/org/ctc/backup/serialization/DiscordPostMixIn.java
  - src/main/java/org/ctc/backup/service/BackupExportService.java
  - src/main/java/org/ctc/backup/service/BackupImportService.java
  - src/main/java/org/ctc/discord/repository/DiscordGlobalConfigRepository.java
  - src/main/java/org/ctc/discord/repository/DiscordPostRepository.java
  - src/main/resources/templates/admin/backup.html
  - src/test/java/org/ctc/backup/BackupControllerIT.java
  - src/test/java/org/ctc/backup/repository/BackupRepositoryEntityGraphIT.java
  - src/test/java/org/ctc/backup/restore/entity/TeamRestorerTest.java
  - src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java
  - src/test/java/org/ctc/backup/schema/BackupSchemaTopologyIT.java
  - src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java
  - src/test/java/org/ctc/backup/service/BackupDiscordFieldRoundTripIT.java
  - src/test/java/org/ctc/backup/service/BackupExportServiceTest.java
  - src/test/java/org/ctc/backup/service/BackupImportSchemaMismatchIT.java
  - src/test/java/org/ctc/backup/service/BackupLenientV1AcceptanceIT.java
  - src/test/java/org/ctc/backup/service/BackupRoundTripIT.java
  - src/test/java/org/ctc/e2e/BackupImportE2ETest.java
findings:
  critical: 2
  warning: 7
  info: 5
  total: 14
status: issues_found
---

# Phase 101: Code Review Report

**Reviewed:** 2026-05-28
**Depth:** standard
**Files Reviewed:** 27 (26 source + 1 template)
**Status:** issues_found

## Summary

Phase 101 bumps `SCHEMA_VERSION` 1 → 2, wires `DiscordGlobalConfig` + `DiscordPost`
into the backup pipeline, adds two new restorers, augments four existing restorers
with V8-V15 column carriage, and adds the lenient-v1 acceptance gate plus a
13-field round-trip IT.

The structural work is sound — SCHEMA_VERSION guard test pins `2`, topology IT
proves the FK-respecting order, `pinDiscordPostLast` is a clean compensation for
the @Column-UUID FK detection blind spot, and the round-trip IT exercises both
H2 and MariaDB engines including SHA-256 byte-equality on Discord JSON.

Two issues require fixes before this code ships:

1. **Lenient-v1 IT is vacuous** — the synthetic v1 ZIP it builds uses wrong
   table names (`race_scoring`/`match_scoring` vs. the actual `race_scorings`/
   `match_scorings`), so the restore phase silently no-ops on those 2 + the 2
   missing Discord entries, and the "Discord tables stay empty" assertion is
   trivially satisfied by the wipe step rather than by lenient-import semantics.
   This is the test you wrote to prove the lenient gate — and it does not.

2. **`DiscordGlobalConfigRestorer` NPEs on partial/forged backups** — the six
   NOT-NULL-with-default columns are read via `row.get("guildId").asText()`
   (no null-guard). If a corrupted or hand-crafted ZIP omits one of those
   fields, restore NPEs mid-transaction (rollback fires, but the error message
   is `NullPointerException` instead of an actionable manifest violation).
   `Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText()))` has
   the same shape on every restorer.

The rest are stale comments (`24 tables` → should be `26`), pollution markers
(`Phase 73 / Plan 04 / D-15 / WR-02` references throughout the JavaDoc, in
direct violation of the CLAUDE.md "No Comment Pollution" rule applied
identically to JavaDoc and inline comments), and one snake_case→kebab-case
dependency on entity package layout that is undocumented and load-bearing.

## Critical Issues

### CR-01: BackupLenientV1AcceptanceIT — synthetic v1 ZIP uses wrong table names; lenient-gate proof is vacuous

**File:** `src/test/java/org/ctc/backup/service/BackupLenientV1AcceptanceIT.java:52-58`
**Issue:** `V1_TABLES_24` lists `"race_scoring"` and `"match_scoring"` (singular). The
actual JPA `@Table(name=...)` is `race_scorings` and `match_scorings` (plural — see
`RaceScoring.java:13` / `MatchScoring.java:12`). `BackupImportSchemaMismatchIT.snapshotAllCounts`
proves this by using the plural names.

Consequence: in `buildSyntheticZip`, the synthetic ZIP emits entries
`data/race-scoring.json` and `data/match-scoring.json`. At restore time,
`BackupImportService.restoreOneTable` resolves `ref.fileName()` for the
real entities — `data/race-scorings.json` / `data/match-scorings.json` — and
finds neither. Both entries are silently logged as `WARN` and skipped (`return totalRows` = 0).

The test `givenV1ManifestZip_whenExecute_thenDiscordTablesStayEmpty` then
asserts `discordGlobalConfigRepository.count() == 0` and `discordPostRepository.count() == 0`.
Both pass trivially because the WIPE phase already emptied both tables, and the
restore phase reads no Discord entry (correct, since v1 has none). The lenient
gate is not actually exercised — the test would pass identically with a v2
manifest that happens to omit the Discord JSON files.

**Fix:**
```java
private static final List<String> V1_TABLES_24 = List.of(
        "cars", "tracks", "race_scorings", "match_scorings",  // plural
        "drivers", "psn_aliases", "teams", "seasons",
        "season_phases", "season_phase_groups", "phase_teams", "season_teams",
        "season_drivers", "playoffs", "playoff_rounds", "playoff_matchups",
        "playoff_seeds", "matchdays", "matches", "races",
        "race_lineups", "race_results", "race_settings", "race_attachments");
```

Then strengthen the test to seed at least one row into a Discord-touching
table BEFORE the round-trip and assert the lenient path explicitly preserves
the wipe-and-no-restore semantics for v1, e.g. seed one `DiscordGlobalConfig`
row pre-execute, then assert post-execute the row count is 0 (proves the wipe
ran AND the v1 file-missing path did not re-insert).

### CR-02: DiscordGlobalConfigRestorer — unguarded `JsonNode.get(...).asText()` on six NOT-NULL columns NPEs on partial/corrupt backups

**File:** `src/main/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorer.java:48-56`
**Issue:** Lines 48 / 49 / 50 / 51 / 54 / 56 directly chain `row.get("guildId").asText()`,
`row.get("announcementWebhookUrl").asText()`, etc. with NO null-guard:
```java
ps.setString(2, row.get("guildId").asText());
ps.setString(3, row.get("announcementWebhookUrl").asText());
ps.setString(4, row.get("raceResultsForumChannelId").asText());
ps.setString(5, row.get("standingsForumChannelId").asText());
ps.setString(8, row.get("vsEmojiName").asText());
ps.setString(10, row.get("currentMatchCategoryId").asText());
```
If the staged ZIP's `data/discord-global-config.json` omits any of these
fields (corrupt file, hand-crafted attack ZIP, version drift), `row.get(...)`
returns `null` and the next `.asText()` call NPEs mid `batchUpdate`. The
restore rolls back, but:
- The error surfaces as `NullPointerException` not as
  `BackupArchiveException(MANIFEST_INVALID, ...)` — the operator-facing flash
  is wrong.
- The audit row's `error_message` (if there is one) carries no usable hint
  about WHICH field is missing.
- The JdbcTemplate batch may have partially applied — `batchUpdate` semantics
  are tx-dependent; on H2 the batch is atomic, on MariaDB with `rewriteBatchedStatements=true`
  it sends a single multi-row INSERT and the partial-apply concern is moot, but
  the inconsistency is real.

Same pattern recurs in:
- `DiscordGlobalConfigRestorer.java:58-59` — `createdAt` / `updatedAt`
  unguarded `LocalDateTime.parse(row.get(...).asText())`
- `DiscordPostRestorer.java:53-58, 64, 66-67` — `id`, `channelId`, `messageId`,
  `webhookId`, `webhookToken`, `postType`, `postedAt`, `createdAt`, `updatedAt`
- `MatchRestorer.java:61-63` — `bye`, `createdAt`, `updatedAt`
- `MatchdayRestorer.java:52-55` — `label`, `sortIndex`, `createdAt`, `updatedAt`
- `SeasonRestorer.java:66-73` — every "required" field
- `TeamRestorer.java:58-60, 66-67` — `id`, `name`, `shortName`, `createdAt`, `updatedAt`

**Fix:** Add a required-field helper and use it for non-nullable columns:
```java
private static String requireString(JsonNode row, String field) {
    JsonNode n = row.get(field);
    if (n == null || n.isNull()) {
        throw new IllegalArgumentException(
                "Required field missing in backup row: " + field);
    }
    return n.asText();
}

// at top of restore:
ps.setString(2, requireString(row, "guildId"));
ps.setString(3, requireString(row, "announcementWebhookUrl"));
```
Then catch `IllegalArgumentException` in `BackupImportService.restoreOneTable` and
re-throw as `BackupArchiveException(MANIFEST_INVALID, "row %d of %s: %s")`.
At minimum, document the unguarded read as a known limitation in the restorer
JavaDoc and pin an IT that injects a missing field and asserts the diagnostic
message contains the field name.

## Warnings

### WR-01: Stale "24 entities / 24 tables" references across BackupExportService + BackupImportService

**File:** `src/main/java/org/ctc/backup/service/BackupExportService.java:33,221`, `src/main/java/org/ctc/backup/service/BackupImportService.java:77,82,533,543,593,635,652,830,832`
**Issue:** The export order is now 26 entities (24 league + 2 Discord), confirmed
by `BackupSchemaGuardTest.givenBackupSchema_whenInspected_thenExportOrderHasTwentySixEntities`.
But the JavaDoc and inline comments still say "24 tables" / "all 24 tables" /
"24 EntityRestorers" / "BackupSchema.getExportOrder() entities" (24). At least
11 separate occurrences. Future readers will incorrectly assume the iteration
size is 24, which can lead to fence-post bugs in a future refactor.

**Fix:** Replace every literal `24` with `26` in JavaDoc and comments — or
better, refer to `BackupSchema.getExportOrder().size()` as the authoritative
source and drop the magic number entirely:
```java
/**
 * Wipes every table in {@link BackupSchema#getExportOrder()} reverse order.
 */
```

### WR-02: BackupExportService — `lookupRepository` error message also stale at "24 entities"

**File:** `src/main/java/org/ctc/backup/service/BackupExportService.java:309`
**Issue:** `"must be one of the " + repositoriesByEntityClass.size() + " BackupSchema.getExportOrder() entities"` —
the literal substring "BackupSchema.getExportOrder() entities" reads correctly,
but the surrounding JavaDoc at line 213 says "Phase 72 export order is the
contract" (a pollution reference) and line 309 doesn't actually reference 24.
The bug is line 33 JavaDoc, which DOES say "24 repositories" but the
constructor now wires 26. Confirmed: line 33: `(provided by Plan 73-02 on each of the 24 repositories)`.

**Fix:** `(provided on all repositories enumerated by BackupSchema.getExportOrder())`

### WR-03: Comment pollution — JavaDoc references to Phase / Plan / D-NN / WR-NN / EXPORT-NN / CR-NN / SC# / IMPORT-NN / UI-SPEC throughout main source

**File:** `src/main/java/org/ctc/backup/service/BackupImportService.java` (22 occurrences), `src/main/java/org/ctc/backup/service/BackupExportService.java` (multiple), all 6 restorers (multiple each), `BackupSchema.java`, `DiscordGlobalConfigMixIn.java`, `DiscordPostMixIn.java`
**Issue:** Per CLAUDE.md "No Comment Pollution":
> Hard-banned in source files (Java, SQL migrations, Thymeleaf templates, YAML, tests):
> Phase / Plan / Task / UAT / Wave references (e.g. `Phase 94 V11 UAT-04 follow-up:`,
> `Plan-94-04 fix:`, `// Wave 2 closeout`). They rot — use git history and PR
> descriptions instead.

Examples from `BackupImportService.java`:
- L77: `Phase 74 staging-file contract`
- L86: `(D-11 / D-12)`
- L87: `(Plan 07)`
- L482-484: `WR-04: make the meta-read failure explicit...`
- L546: `CR-01: use unmodifiable LinkedHashMap copies...`
- L567: `WR-08: catch Throwable...`
- L651: `WR-05: open the ZIP exactly once...`
- L687: `CodeQL FP: java/path-injection — ...` (this one is *allowed* by the rule
  — it explains a non-obvious WHY)

The CodeQL FP comment is fine — the rest must go. Same applies to the
restorers (`Plan 73-02`, `Phase 75`, `V8/V9/V13/V15`, `D-06`, `Phase 72`).

**Fix:** Remove all pollution markers. Where a comment captures a genuine
non-obvious invariant (e.g., "Schema-version acceptance set"), keep the
invariant text; drop the cross-references. Tests have 9 occurrences — same
treatment. Note: `// CodeQL FP:` markers are required by the SAST workflow
(CLAUDE.md "CodeQL SAST") and must stay.

### WR-04: BackupRoundTripIT — duplicated helpers (`exportToBytes`, `captureRowCounts`, `hashEntity`, `awaitAuditRow`) across H2 and MariaDB nested classes

**File:** `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java:582-636` and `:882-936`
**Issue:** The two `@Nested` profile classes carry byte-identical copies of
four private helper methods (`exportToBytes`, `captureRowCounts`, `hashEntity`,
`awaitAuditRow`) plus the `SAFE_TABLE_NAME` regex. ~55 lines duplicated. The
explanatory comment "duplicated per @Nested class (each class has its own
ApplicationContext)" is incorrect — each `@Nested` class has its own
`ApplicationContext` but they share method-lookup with the enclosing class.
The helpers could be moved to a static utility in the outer class or to a
small abstract base, eliminating the duplication.

**Fix:** Promote the four helpers to static methods on the outer `BackupRoundTripIT`
class (each `@Nested` class can call them directly). Move `SAFE_TABLE_NAME`
to a single static constant.

### WR-05: BackupLenientV1AcceptanceIT — `snapshotAllCounts` in BackupImportSchemaMismatchIT also missing `discord_global_config` and `discord_post`

**File:** `src/test/java/org/ctc/backup/service/BackupImportSchemaMismatchIT.java:205-232`
**Issue:** `snapshotAllCounts()` covers 24 tables. The new Discord tables are
not snapshotted, so the SC#2 proof "DB state is byte-identical before and
after the rejected stage" no longer covers `discord_global_config` /
`discord_post`. If a future regression caused the schema-mismatch gate to do
a side-effect read on those two tables, this test would still pass.

**Fix:** Inject `DiscordGlobalConfigRepository` and `DiscordPostRepository`
and add `m.put("discord_global_config", discordGlobalConfigRepository.count())`
+ `m.put("discord_post", discordPostRepository.count())` to `snapshotAllCounts`.

### WR-06: DiscordGlobalConfigMixIn / DiscordPostMixIn — `@JsonIdentityInfo` adds an `@id` reference field but contradicts the stated "no id" intent

**File:** `src/main/java/org/ctc/backup/serialization/DiscordGlobalConfigMixIn.java:15`, `src/main/java/org/ctc/backup/serialization/DiscordPostMixIn.java:21`
**Issue:** Both MixIns declare:
```java
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
```
This causes Jackson to add an `@id` reference column on first emission and
emit `@ref` on subsequent emissions when the same object appears twice in the
graph. For these two singleton-ish entities this never triggers in practice,
but:
- It does NOT suppress the `id` field — `id` is emitted in JSON, and both
  restorers explicitly read it (`row.get("id").asLong()` at
  `DiscordGlobalConfigRestorer:47` and `DiscordPostRestorer:53`).
- The phase-101 design instruction stated MixIns "must NOT include id
  (re-assigned on restore)". The implementation does the opposite — it
  preserves the source ZIP's `id` verbatim, which is the correct choice for
  entity-graph integrity but contradicts the design intent.

This may cause AUTO_INCREMENT sequence-bump issues on MariaDB after a restore
(the next inserted row may collide with a stale sequence value). H2 handles
this gracefully; MariaDB needs `ALTER TABLE ... AUTO_INCREMENT = MAX(id)+1`
after restore. There is no evidence of such logic in `BackupImportService`.

**Fix:** Either:
(a) Embrace the current behavior — preserve `id` — and document the
sequence-bump risk explicitly in `DiscordPostRestorer` / `DiscordGlobalConfigRestorer`
JavaDoc, plus add a post-restore `ALTER TABLE discord_post AUTO_INCREMENT =
(SELECT COALESCE(MAX(id), 0) + 1 FROM discord_post)` for MariaDB. H2 path is
no-op.
(b) Add a real ID-suppression MixIn (`@JsonIgnoreProperties("id")` on the MixIn
plus per-row `id` generation via DB AUTO_INCREMENT) and stop reading
`row.get("id").asLong()` in the restorer.

The current code does neither; pick a stance and document it.

### WR-07: BackupSchema — `pinDiscordPostLast` is hardcoded; does not extend to future @Column-UUID FK entities

**File:** `src/main/java/org/ctc/backup/schema/BackupSchema.java:69-83`
**Issue:** `pinDiscordPostLast` filters by the literal string `"discord_post"`
and moves that single entity to the tail. The comment correctly explains the
problem (5 `@Column UUID` FKs invisible to the topo sorter), but the solution
is bespoke — it does not extend to:
- A future second entity that adopts the same `@Column UUID` FK pattern.
- A rename of the `discord_post` table (silent breakage — pin no-ops without
  warning).

The existing `BackupSchemaTopologyIT.givenSpringContext_whenGetExportOrder_thenManyToOneDependenciesPrecedeOwners`
checks `@ManyToOne` / `@OneToOne` edges, but does NOT cover `@Column UUID` FK
edges. So a future addition of another such entity will pass the topology IT
yet break restore at runtime.

**Fix:** Either:
(a) Add a marker annotation (`@BackupRestoreOrder(Last.class)`) on entities
that need tail-pinning; `pinDiscordPostLast` becomes `pinLastByAnnotation`.
(b) Pin by entity-class identity (`entityClass().equals(DiscordPost.class)`)
so a table rename does not silently break the pin.
(c) Document in `BackupSchemaTopologyIT` that `@Column UUID` FK edges are NOT
checked, and add a guard test that scans for `@Column UUID` fields whose
target type is a known entity class and asserts they appear after the
referenced entity.

## Info

### IN-01: BackupSchema — package-name filter uses `startsWith` which is slightly broader than necessary

**File:** `src/main/java/org/ctc/backup/schema/BackupSchema.java:44-47`
**Issue:**
```java
return pkg.startsWith("org.ctc.domain.model")
    || pkg.startsWith("org.ctc.discord.model");
```
`startsWith` is broader than `equals`. If a future entity lands in
`org.ctc.domain.model.subpackage.Foo`, it would be auto-picked up by the
export. That may be intentional (recursive scoping) or accidental. Document
the intent.

**Fix:** Either narrow to `pkg.equals("org.ctc.domain.model")` to lock the
flat-package convention, or add a JavaDoc note "recursive — sub-packages are
included intentionally".

### IN-02: TeamRestorer.parentTeam — Pass 2 batchUpdate could fire even when withParent is empty if a future refactor removes the guard

**File:** `src/main/java/org/ctc/backup/restore/entity/TeamRestorer.java:75-81`
**Issue:** The `if (!withParent.isEmpty())` guard is correct. The
`TeamRestorerTest.givenAllTeamsAreRootLevel_whenRestoreCalled_thenOnlyPass1Executes`
test pins it. No bug today — just note that the guard is load-bearing and the
`@Component` is non-`@Primary` (the test pins this too). Keep both invariants.

**Fix:** None — just acknowledged as load-bearing.

### IN-03: BackupExportService — `lookupRepository` throws `IllegalArgumentException` for unknown class but the JavaDoc says `IllegalStateException`

**File:** `src/main/java/org/ctc/backup/service/BackupExportService.java:200-205, 304-313`
**Issue:** The `fetchAllForBackup` JavaDoc at line 200 says "Throws
`IllegalArgumentException` if no repository is registered" — correct, that
matches `lookupRepository:306-309`. But the test
`BackupExportServiceTest.givenUnknownEntityClass_whenFetchAllForBackup_thenThrowsIllegalArgumentException`
pins the contract. OK.

The `NoSuchMethodException` at line 210 wraps to `IllegalStateException`,
"Plan 73-02 contract violation" — pollution marker, see WR-03.

**Fix:** Remove the "Plan 73-02 contract violation" phrase from the exception
message — that prose leaks planning vocabulary into the production binary.
Use "Repository contract violation: findAllForBackup() must be declared on
every JpaRepository registered with BackupSchema."

### IN-04: BackupLenientV1AcceptanceIT — `givenV2ManifestZipBuiltLikeV1_whenStage_thenSchemaMatchesIsTrue` does not exercise the v2 import body

**File:** `src/test/java/org/ctc/backup/service/BackupLenientV1AcceptanceIT.java:156-165`
**Issue:** The "v2 like v1" sanity test only calls `stage()`, not `execute()`.
It proves the gate accepts v2 but does not prove the v2 import body works
when the v2 manifest omits Discord JSON entries — exactly the future
forward-compat scenario the lenient gate exists for. The test name implies a
behavioral guarantee that is not exercised.

**Fix:** Add `backupImportService.execute(preview.stagingId())` and assert
the Discord tables are empty after, matching the v1 test shape (but with the
table-name fix from CR-01).

### IN-05: BackupExportService — file-system traversal log message uses formatted `relative` value (potential log injection)

**File:** `src/main/java/org/ctc/backup/service/BackupExportService.java:266-275`
**Issue:**
```java
log.warn("Skipping path-traversal upload reference: {} (resolved to {} outside {})",
        relative, absolute, uploadRoot);
```
`relative` is derived from a DB-stored URL string. A malicious or accidental
DB row containing `\r\n[INFO ] ...` could inject CRLF into the log file —
the same CRLF-sanitization pattern already applied in commit `be5d9285`
(`fix(security): sanitize CRLF in MatchdayService.savePairings log`).

Lower-impact than the matchday log (which is reachable via admin form input),
but the upload-dir cleanup paths in `Team.logoUrl` etc. ARE admin-editable
via the team-edit form. Should be sanitized for symmetry with the recent
matchday hotfix.

**Fix:** Use a `sanitizeForLog(String)` helper applied to `relative` and
`absolute.toString()` before passing to `log.warn`. Add to a shared utility
class so the matchday + backup paths share one implementation.

---

_Reviewed: 2026-05-28_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
