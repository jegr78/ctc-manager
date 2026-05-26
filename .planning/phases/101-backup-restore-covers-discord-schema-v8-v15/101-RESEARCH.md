# Phase 101: Backup/Restore covers Discord schema (V8-V15) — Research

**Researched:** 2026-05-26
**Domain:** Backup wire contract extension — Discord schema inclusion
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01 to D-17: All locked — see 101-CONTEXT.md § Implementation Decisions for full text.
  Summary of binding choices:
  - D-08: Package-filter expansion (`startsWith "org.ctc.discord.model"` added to `BackupSchema`)
  - D-09: `SCHEMA_VERSION` 1 → 2
  - D-10: Schema-version gate changes to `!Set.of(1, 2).contains(manifestSchemaVersion)`
  - D-11: v1 imports leave Discord sections empty (no JSON file → empty array → no restorer call)
  - D-12: 4 existing Restorers extend `INSERT_SQL` for all V8-V15 columns
  - D-13: Full first-class test coverage
  - D-14: `BackupSchemaGuardTest` flips count to 26 + version to 2
  - D-15: 13 per-field regression-fence round-trip assertions
  - D-16: `BackupRoundTripIT` byte-equality extends to `DiscordGlobalConfig` + `DiscordPost`
  - D-17: Lenient-v1-acceptance IT with synthetic v1 backup ZIP

### Claude's Discretion
- MixIn shape for `DiscordPost` (FK extraction pattern)
- MixIn shape for `DiscordGlobalConfig` (minimal MixIn)
- `EntityTopoSorter` behaviour verification
- Naming of new restorer classes: `DiscordGlobalConfigRestorer`, `DiscordPostRestorer`
- MixIn package location: `org.ctc.backup.serialization`
- Lenient version-check implementation: `Set.of(1, 2).contains(v)` or named constant
- D-10 V8-default-row planner action (research resolves this — see RQ-1 below)
- Wire-contract documentation in `PROJECT.md`

### Deferred Ideas (OUT OF SCOPE)
- Cross-guild restore tooling / "Reset Discord state on import" toggle
- `@Backupable` marker annotation
- `data_import_audit` inclusion
- Inline v1 → v2 migration code branch
- Encrypted backup ZIP at rest
</user_constraints>

---

## Overview

Phase 101 closes two distinct but related silent-data-loss gaps in the backup wire contract opened
by the v1.13 Discord schema (Flyway V8-V15):

**Gap A — Missing columns in existing Restorers:** The four entity Restorers for `Match`, `Team`,
`Matchday`, and `Season` were last updated before V8-V15 migrations shipped. Their `INSERT_SQL`
lists only the V1 schema columns. Jackson serializes the new Discord/scheduling fields correctly
(Lombok-generated getters are present), but every restore silently inserts NULL for these fields.
This is a confirmed regression bug requiring unconditional fix.

**Gap B — Entire entities excluded from backup scope:** `DiscordGlobalConfig` and `DiscordPost`
live under `org.ctc.discord.model.*`, outside the `org.ctc.domain.model.*` package-filter
applied by `BackupSchema.initializeExportOrder`. The fix is a one-line expansion of the predicate.
Once the filter is widened, the `EntityTopoSorter` picks up both entities, the `BackupExportService`
automatically includes their `data/*.json` entries, and the `BackupImportService.wireRepositoriesByTableName`
and `wireRestorersByTableName` bootstrap validators require corresponding `JpaRepository` beans and
`EntityRestorer` beans to exist — both must be added.

All code changes are mechanical extensions of existing patterns. No new abstractions are introduced.
The planner should structure tasks as a wave of guard-test fixes first (D-14), then implementation
tasks (D-08/D-12/restorers/MixIns), then regression-fence tests (D-15/D-16/D-17), then docs.

**Primary recommendation:** Fix `BackupSchemaGuardTest` first (D-14) so the build fails loudly
after D-08/D-09, then implement the 6-Restorer set (4 extended + 2 new) and 2 MixIns as a single
coherent task, and write the regression-fence tests last.

---

## Technical Approach — Answers to the 13 Research Questions

### RQ-1: V8 default-row resurrection path (D-10 planner action — RESOLVED)

**Answer: The V8 default row IS NOT auto-resurrected by Flyway after a replace-all wipe.
However, no post-wipe seed step in `BackupImportService` is needed because `DiscordGlobalConfigService.getOrInitialize()` provides a live defensive fallback.**

Evidence chain:

1. `V8__discord_global_config.sql` (`src/main/resources/db/migration/V8__discord_global_config.sql:18-21`)
   inserts one default row via `INSERT INTO discord_global_config (...)`. This runs once at
   initial Flyway migration. Flyway records it in `flyway_schema_history`; it will never re-run.

2. A replace-all wipe (`BackupImportService.wipeAllTables`, line 605-611) issues
   `DELETE FROM discord_global_config` via native SQL inside the transaction.
   After the wipe, the table is empty until `restoreAll` inserts backup rows.

3. For a v1 backup (D-11 path): the ZIP has no `data/discord-global-config.json` entry.
   `restoreAll` calls `restoreOneTable`, which calls `zf.getEntry(entryPath)` and gets `null`
   (line 673-679 of `BackupImportService`). Per the code: "Absent data files for an entity are
   not a hard error — an empty array is semantically equivalent. The restorer is simply not
   invoked and the count is 0." The table remains empty after the import.

4. On the next operator action that touches Discord config (e.g., `GET /admin/discord-config`,
   `GET /admin/matches/{id}`), the controller calls `discordGlobalConfigService.getOrInitialize()`
   (`DiscordGlobalConfigService.java:19-26`). This method calls
   `repo.findFirstByOrderByIdAsc()` — returns `null` — then logs
   `"discord_global_config seed row missing — inserting empty defaults"` and saves a fresh
   `DiscordGlobalConfig` entity with empty-string defaults. The app self-heals on first access.

5. No NPE or crash path exists between wipe and the first operator page-load because no
   startup code calls `getOrInitialize()` unconditionally.

**Planner action for D-10:** No explicit post-wipe seed step in `BackupImportService` is required.
The `getOrInitialize()` defensive fallback covers the v1-import empty-table case. The D-17 lenient-v1
IT must assert that after a v1 import: `discordGlobalConfigRepository.count() == 0L` immediately
post-import (the seed row is NOT there yet; it appears only on first access). **OR** the IT
calls `getOrInitialize()` and then asserts the defaults are present — both approaches are valid;
the planner should choose the simpler one (direct repository count check, value == 0).

**Note on D-11 phrasing:** The CONTEXT.md says "DiscordGlobalConfig gets a fresh default row
(per D-10 planner action)" — this is slightly inaccurate. The row arrives on-demand, not
immediately post-import. The IT assertion should test for 0 rows immediately after import.

---

### RQ-2: EntityTopoSorter cross-package FK handling

**Answer: `DiscordPost` has NO `@ManyToOne` annotations — its 5 FKs are plain `@Column UUID`
fields. The EntityTopoSorter will place `DiscordPost` as a root node (in-degree 0) with no
dependency edges. This means DiscordPost can appear ANYWHERE in the topo order — no ordering
constraint relative to Match/Matchday/Race/Season/Phase is enforced by Kahn's algorithm.**

Evidence from `DiscordPost.java`:
```java
@Column(name = "match_id")
private UUID matchId;

@Column(name = "matchday_id")
private UUID matchdayId;
// ... etc.
```

`EntityTopoSorter.sort()` (`EntityTopoSorter.java:42-66`) walks only `SingularAttribute`s with
`PersistentAttributeType.MANY_TO_ONE` or `PersistentAttributeType.ONE_TO_ONE`. Plain `@Column`
fields are `PersistentAttributeType.BASIC` — they are invisible to the FK walk.

`DiscordGlobalConfig` similarly has no `@ManyToOne` FKs (it extends `BaseEntity` but has no
entity references). Both entities will have in-degree 0 and appear near the front of the topo order.

**Critical consequence:** The DB schema defines `ON DELETE SET NULL` FK constraints in
`V12__discord_post.sql` and `V14__add_discord_post_phase_id.sql`. These are DB-level FKs, not JPA
`@ManyToOne` mappings. During the replace-all wipe (reverse topo order), if `discord_post` is
placed near the FRONT of the export order (early in the reverse = late in the wipe), and the
parent tables (matches, matchdays, etc.) are wiped BEFORE `discord_post`, the `ON DELETE SET NULL`
cascade handles the FK nullification automatically — no crash. But the wipe order still needs
`discord_post` to be deleted BEFORE or WITH its parent tables.

Since `DiscordPost` has in-degree 0, it appears near the START of the topo order (export order =
dependency-first). In reverse order (wipe order), it appears near the END — meaning it gets
deleted AFTER its parent rows. But `ON DELETE SET NULL` ensures that when parent rows are deleted,
the `discord_post.match_id` etc. are set to NULL automatically. By the time the `DELETE FROM
discord_post` runs, the FKs are already NULL. No FK violation.

**Verification:** `EntityTopoSorter.java:55-58` — the FK-walk skips `depClass` when it is not
in `byClass` (the filtered entity set). Since `Match`, `Matchday`, `Race`, `Season`, `SeasonPhase`
ARE in the filtered set (they are under `org.ctc.domain.model.*`), but `DiscordPost.matchId` is a
`UUID @Column`, not a `@ManyToOne Match`, there is no edge recorded. The filter line is:
```java
if (!byClass.containsKey(depClass)) {
    continue;  // FK to non-domain entity — skip
}
```
`depClass` for a `@Column UUID matchId` is `java.util.UUID` — clearly not in `byClass`. Safe.

**DiscordGlobalConfig topo position:** No FKs at all (beyond `BaseEntity.createdAt/updatedAt`
which are `@Basic`). In-degree 0, placed near the front of the export order.

---

### RQ-3: BaseEntity inheritance + @CreatedDate/@LastModifiedDate bypass

**Answer: Both `DiscordGlobalConfig` and `DiscordPost` extend `BaseEntity`.
The `JdbcTemplate.batchUpdate` pattern from Phase 75 carries over unchanged for both.**

From `DiscordGlobalConfig.java:21`:
```java
public class DiscordGlobalConfig extends BaseEntity {
```

From `DiscordPost.java:25`:
```java
public class DiscordPost extends BaseEntity {
```

`BaseEntity` (`BaseEntity.java`) is `@MappedSuperclass @EntityListeners(AuditingEntityListener.class)`
with `@CreatedDate LocalDateTime createdAt` and `@LastModifiedDate LocalDateTime updatedAt`.

Since both Discord entities extend `BaseEntity`, their `created_at`/`updated_at` columns are
managed by `AuditingEntityListener` on JPA-path saves. The Restorer bypass uses `JdbcTemplate.batchUpdate`
which goes directly to JDBC — `AuditingEntityListener` is never invoked. The `INSERT_SQL` in each
new Restorer MUST include `created_at, updated_at` columns explicitly, extracting values from
`row.get("createdAt").asText()` and `row.get("updatedAt").asText()` (same pattern as all 24 existing
restorers, e.g., `MatchRestorer.java:52-53`).

**Note on DB DEFAULT:** V8 defines `created_at TIMESTAMP NOT NULL` with no `DEFAULT CURRENT_TIMESTAMP`
at the column level for rows inserted by the app (the app always supplies the value). If the Restorer's
`INSERT_SQL` omits `created_at`, the DB insert will fail with a NOT NULL constraint violation on
MariaDB. H2 in lenient mode might auto-fill with `NULL` or epoch, masking the bug. The INSERT_SQL
MUST include both audit columns explicitly — same discipline as all existing restorers.

---

### RQ-4: `@JsonIdentityReference(alwaysAsId = true)` MixIn shape for DiscordPost's 5 FKs

**Answer: The 5 FKs in `DiscordPost` are plain `UUID @Column` fields, NOT `@ManyToOne` entity
references. There are NO getter methods like `getMatch()` returning a `Match` object.
No `@JsonIdentityReference` is needed. The MixIn only needs `@JsonIgnoreProperties`.**

`DiscordPost.java` declares:
```java
@Column(name = "match_id")
private UUID matchId;
```

There is no `@ManyToOne Match match` field. Lombok `@Getter` generates `getMatchId()` returning
`UUID`. Jackson serializes this as `"matchId": "xxxxxxxx-xxxx-..."` — a direct UUID string, not a
nested object.

The `DiscordPostRestorer.INSERT_SQL` extraction is therefore:
```java
ps.setObject(idx, nullableUuid(row, "matchId"));
// where nullableUuid parses row.get("matchId").asText() directly
```

No `row.get("match").get("id").asLong()` extraction needed — `matchId` is already the UUID value.

**Contrast with existing MixIns that DO use `@JsonIdentityReference`:** `MatchMixIn` declares
`abstract Matchday getMatchday()` annotated with `@JsonIdentityReference(alwaysAsId = true)` because
`Match.matchday` IS a `@ManyToOne Matchday` field. Jackson would normally serialize the full nested
Matchday object; the MixIn forces it to serialize as the identity value only. `DiscordPost` does not
have entity references, so this pattern is not needed.

**What the DiscordPostMixIn DOES need:**
- `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` — standard for all entities
- Possibly suppress computed or back-reference properties if any exist — none present on `DiscordPost`

**What the DiscordGlobalConfigMixIn needs:**
- `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` — minimal
- No FK suppression needed

**Implication for `DiscordGlobalConfigRestorer`:** The `id` field is `Long` (BIGINT AUTO_INCREMENT,
`GenerationType.IDENTITY`). The INSERT_SQL must use `ps.setLong(1, row.get("id").asLong())` not
`UUID.fromString(...)`. The exact pattern to use: `ps.setLong(...)`.

**Implication for `DiscordPostRestorer`:** Similarly `id` is `Long`. The UUID FKs are raw UUID
strings on the JSON — extract as `UUID.fromString(row.get("matchId").asText())` with null-safety.

---

### RQ-5: `BackupSerializationModule` registration mechanism

**Answer: `setMixInAnnotation(EntityClass.class, MixInClass.class)` is called once per entity
in the `BackupSerializationModule` constructor. New entities require exactly one line each.**

From `BackupSerializationModule.java:15-41`:
```java
public BackupSerializationModule() {
    super("BackupSerializationModule");
    setMixInAnnotation(Car.class, CarMixIn.class);
    // ... 23 more lines, one per entity ...
}
```

`setMixInAnnotation` is a method on `SimpleModule` (superclass). The call is at the module level,
not the ObjectMapper level.

**Two lines to add (in the constructor body, after the existing 24 registrations):**
```java
setMixInAnnotation(DiscordGlobalConfig.class, DiscordGlobalConfigMixIn.class);
setMixInAnnotation(DiscordPost.class, DiscordPostMixIn.class);
```

The import for `org.ctc.discord.model.*` will be needed. The existing `import org.ctc.domain.model.*`
wildcard does not cover `org.ctc.discord.model.*`.

**`BackupObjectMapperConfig.java:64`** collects all Spring `Module` beans via
`backupObjectMapper(List<Module> backupMixInModules)`. `BackupSerializationModule` is `@Component`,
so it is auto-discovered. No change to `BackupObjectMapperConfig` is needed.

---

### RQ-6: DiscordPostType enum serialization

**Answer: `@Enumerated(EnumType.STRING)` is present on `DiscordPost.postType`. The column in
V12 is `post_type VARCHAR(32)`. Jackson serializes it as a plain String by default.
The Restorer extracts via `.asText()`.**

From `DiscordPost.java:43-45`:
```java
@Enumerated(EnumType.STRING)
@Column(name = "post_type", length = 32, nullable = false)
private DiscordPostType postType;
```

From `V12__discord_post.sql:5`:
```sql
post_type VARCHAR(32) NOT NULL,
```

The default Jackson `ObjectMapper` serializes enums as their `name()` string (e.g., `"TEAM_CARDS"`).
The `backupObjectMapper` has no special enum configuration that would change this. The Restorer
extracts `row.get("postType").asText()` and inserts `ps.setString(idx, ...)`.

`DiscordPostType` has 13 values: `TEAM_CARDS`, `SETTINGS`, `LINEUPS`, `SCHEDULE`,
`PROVISIONAL_SCORES`, `MATCH_RESULTS`, `RACE_RESULTS`, `MATCHDAY_PAIRINGS`, `MATCHDAY_SCHEDULE`,
`MATCH_PREVIEW`, `MATCHDAY_OVERVIEW`, `POWER_RANKINGS`, `STANDINGS`.

---

### RQ-7: Cross-entity wipe ordering

**Answer: The wipe uses `backupSchema.getExportOrder().reversed()` automatically. When `DiscordPost`
and `DiscordGlobalConfig` are added to the export order, the reversed order places them where the
topo-sort computed them. No separate explicit deletion order list exists. The V12/V14 `ON DELETE SET NULL`
constraints on `discord_post` handle FK nullification automatically — no pre-step UPDATE is needed.**

`BackupImportService.wipeAllTables()` at line 605:
```java
List<EntityRef> wipeOrder = backupSchema.getExportOrder().reversed();
for (EntityRef ref : wipeOrder) {
    entityManager.createNativeQuery("DELETE FROM " + table).executeUpdate();
}
```

The only manual pre-step NULLs are for 3 identified self-FKs (line 601-603). The DB-level
`ON DELETE SET NULL` for `discord_post.match_id`, `matchday_id`, `race_id`, `season_id`, `phase_id`
means that when their parent tables are deleted (earlier in the reverse-topo order), the FK columns
in `discord_post` are automatically NULLed by the DB. By the time `DELETE FROM discord_post` runs,
there are no remaining FK violations.

**`discord_global_config` wipe position:** No FK dependencies on other entities. It can be deleted
at any position in the reverse-topo order without constraint.

**Planner note:** No changes to `wipeAllTables` are needed — the existing logic handles 26 entities
the same way it handles 24.

---

### RQ-8: `BackupSchemaGuardTest` assertion shape

**Answer: Both assertions use exact integer equality (`isEqualTo`). Both must be flipped in Phase 101.**

From `BackupSchemaGuardTest.java`:

Line 31-35 (SCHEMA_VERSION assertion):
```java
assertThat(BackupSchema.SCHEMA_VERSION)
    .as("BackupSchema.SCHEMA_VERSION changed from 1 — ...")
    .isEqualTo(1);
```

Line 38-43 (EXPORT_ORDER.size() assertion):
```java
assertThat(backupSchema.getExportOrder().size())
    .as("BackupSchema.EXPORT_ORDER size changed from 24 — ...")
    .isEqualTo(24);
```

Both are `isEqualTo(n)` — exact integer, not `>= n`. They will fail immediately after the D-08/D-09
code changes land, which is intentional (the guard test is a regression fence).

**Phase 101 flips:** `isEqualTo(1)` → `isEqualTo(2)` and `isEqualTo(24)` → `isEqualTo(26)`.
The `.as(...)` message strings should also be updated to reference the new baseline values.

**Other assertions in `BackupSchemaGuardTest`:** Only these two tests exist. No other wire-format
invariants are asserted in this test class. The entity-list completeness assertion
(tableCounts keyset == export-order tablenames) lives in `BackupRoundTripIT`.

**Tag:** `@Tag("integration")` — routed to Failsafe, not Surefire. The test class note says
"Despite the `*Test` filename suffix, this class is `@Tag("integration")`."

---

### RQ-9: V1 synthetic backup ZIP fixture path

**Answer: No existing `src/test/resources/backup-fixtures/` directory exists. The pattern used
by all existing backup ITs is to generate fixtures programmatically — NOT to commit binary ZIP files.
The D-17 v1 fixture SHOULD be generated programmatically in the test, not committed as a binary.**

`BackupArchiveServiceReadIT.java:48-49`:
> "All malicious fixtures are generated programmatically (D-25). No binary blobs committed
> under `src/test/resources/backup-fixtures/malicious/`."

Listing of `src/test/resources/` confirms no `backup-fixtures` directory exists.

**Recommended approach for D-17:** The lenient-v1-acceptance IT builds the v1 ZIP in-memory
using `ZipOutputStream` + `BackupObjectMapper` (the same approach as `BackupArchiveServiceReadIT`),
setting `schema_version: 1` in the manifest JSON and including 24 `data/*.json` entries for the
existing entities (empty arrays or minimal fixture rows). No checked-in ZIP file is needed.

The test should use `BackupSchema.getExportOrder()` to derive the 24 entry names, then filter out
`discord-global-config.json` and `discord-post.json` to simulate a v1 backup structure. Alternatively,
export a current backup and modify the manifest — but the programmatic approach is cleaner and
avoids binary file drift.

---

### RQ-10: MariaDB Testcontainer opt-in mechanism

**Answer: `@EnabledIfSystemProperty(named = "docker.available", matches = "true")`.
The annotation is at the `@Nested` class level (not method level). Set `-Ddocker.available=true`
to activate. CI does NOT run this by default in the standard `verify` step.**

From `BackupRoundTripIT.java:513-514`:
```java
@EnabledIfSystemProperty(named = "docker.available", matches = "true",
    disabledReason = "Set -Ddocker.available=true (with Docker daemon) to run...")
```

From `BackupImportMariaDbSmokeIT.java:89-91`:
```java
@EnabledIfSystemProperty(named = "docker.available", matches = "true",
    disabledReason = "Set -Ddocker.available=true (with a running Docker daemon)...")
```

Both use `named = "docker.available"` (not `docker_available` or `testcontainers.enabled`).

For Phase 101's D-16 extension of `BackupRoundTripIT`, the new Discord assertions go inside the
existing `MariaDbRoundTripTests` nested class — inheriting the `@EnabledIfSystemProperty` without
needing to re-declare it.

**CI behaviour:** The standard `./mvnw clean verify -Pe2e` CI run does NOT pass `-Ddocker.available=true`,
so the MariaDB tests are skipped. Local developers run with `-Ddocker.available=true`. The new
Discord byte-equality assertions follow this same opt-in model.

---

### RQ-11: PROJECT.md "Backup Wire Contract (v1.10)" paragraph 4 exact wording

**Answer: Full text of paragraph 4 (lines 304-305 of PROJECT.md):**

> **4. The 24-entity scope (D-03 corrected post-research: 24 operative entities including `PlayoffRound`).**
> The runtime topo-sort returns 24 `EntityRef` instances (CONTEXT.md originally said 23; `PlayoffRound` was missed in D-03 and reconciled in RESEARCH §OQ-1). The 24 are: `Car`, `Track`, `Season`, `SeasonPhase`, `SeasonPhaseGroup`, `Team`, `PhaseTeam`, `SeasonTeam`, `Driver`, `SeasonDriver`, `PsnAlias`, `RaceScoring`, `MatchScoring`, `RaceSettings`, `Matchday`, `Match`, `Race`, `RaceLineup`, `RaceResult`, `RaceAttachment`, `Playoff`, `PlayoffRound`, `PlayoffMatchup`, `PlayoffSeed`. `Car` and `Track` ARE included (D-01: round-trip from environment A to B must not require gt7sync on B first). `FeatureSettings` is DROPPED (D-02: class does not exist in `src/main/java/org/ctc/domain/model/`; if ever introduced, it bumps `SCHEMA_VERSION` 1 to 2). Any new entity under `org.ctc.domain.model` is automatically picked up by the next boot's topo-sort — no marker, no opt-in.

**The CONTEXT.md template for the rewrite** adds a sub-paragraph:
> "v1.13 Phase 101 raised the scope to 26 entities by adding `DiscordGlobalConfig` and `DiscordPost`
> via package-filter expansion to `org.ctc.discord.model.*`. `BackupSchema.SCHEMA_VERSION` is now `2`;
> the importer accepts `schema_version IN (1, 2)` (lenient v1 acceptance for pre-v1.13 operator
> backups, which restore with empty Discord sections)."

The planner should also update the opening paragraph of `BackupSchema.java`'s Javadoc (line 17)
which currently says "all `org.ctc.domain.model.*` entities" — should read "all `org.ctc.domain.model.*`
and `org.ctc.discord.model.*` entities".

---

### RQ-12: `AuditingEntityListener` interaction during restore

**Answer: `JdbcTemplate.batchUpdate` completely bypasses `AuditingEntityListener`. The DB DEFAULT
`CURRENT_TIMESTAMP` on V8/V12 `created_at`/`updated_at` columns would NOT preserve backup values.
The INSERT_SQL MUST explicitly include both audit columns to preserve the exported values.**

The bypass works because `JdbcTemplate.batchUpdate` executes raw SQL through JDBC — no JPA
`EntityManager.persist/merge`, no Hibernate lifecycle, no `AuditingEntityListener`. The Spring
Data `@CreatedDate`/`@LastModifiedDate` annotations only fire when saving through the JPA layer.

`V8__discord_global_config.sql` defines: `created_at TIMESTAMP NOT NULL` (no DEFAULT clause).
`V12__discord_post.sql` defines: `created_at TIMESTAMP NOT NULL` (no DEFAULT clause).

The `INSERT INTO discord_global_config (...) VALUES (...)` statement in V8's seed row uses
`CURRENT_TIMESTAMP` as a literal in the INSERT — but the DDL column definition has no DEFAULT.
This means if the restorer's `INSERT_SQL` omits `created_at`, MariaDB will fail with a NOT NULL
constraint violation. H2 may behave differently. **The INSERT_SQL must list `created_at` and
`updated_at` explicitly.**

**Pattern for `DiscordGlobalConfigRestorer`:**
```java
ps.setTimestamp(idx, Timestamp.valueOf(LocalDateTime.parse(row.get("createdAt").asText())));
```

**Pattern for `DiscordPostRestorer`:** Same. `DiscordPost` also has `posted_at` and
`attachments_replaced_at` columns beyond the standard `BaseEntity` audit columns — both must be
in the INSERT_SQL.

---

### RQ-13: Test categorization tags for new ITs

**Answer: New `*IT.java` files for Phase 101 must use `@Tag("integration")`.
The existing backup ITs use `@Tag("integration")` exclusively (no E2E Playwright involved).**

Confirmed from source:
- `BackupSchemaGuardTest.java:19`: `@Tag("integration")`
- `BackupImportMariaDbSmokeIT.java:92`: `@Tag("integration")`
- `BackupRoundTripIT.java:93`: `@Tag("integration")`

New Phase 101 test classes: `BackupDiscordFieldRoundTripIT` (D-15 regression fence, 13 per-field
tests), extension of `BackupRoundTripIT` (D-16 byte-equality), and `BackupLenientV1AcceptanceIT`
(D-17) — all use `@Tag("integration")`.

---

## Code Surface Map

Every file the planner will modify or create, with line-level edit sites.

### Files to MODIFY

| File | Line(s) | Change |
|------|---------|--------|
| `src/main/java/org/ctc/backup/schema/BackupSchema.java` | 32 | `SCHEMA_VERSION = 1` → `SCHEMA_VERSION = 2` |
| `src/main/java/org/ctc/backup/schema/BackupSchema.java` | 42 | Extend `startsWith` predicate to include `"org.ctc.discord.model"` |
| `src/main/java/org/ctc/backup/schema/BackupSchema.java` | 17, 20 | Update Javadoc to mention `org.ctc.discord.model.*` in the filter |
| `src/main/java/org/ctc/backup/service/BackupImportService.java` | 831 | Replace `backupVersion != currentVersion` equality check with `!Set.of(1, 2).contains(backupVersion)` |
| `src/main/java/org/ctc/backup/service/BackupImportService.java` | 69-89 (class Javadoc) | Update "24 tables" mentions to "26 tables" |
| `src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java` | after line 40 | Add 2 `setMixInAnnotation` calls for Discord entities + import |
| `src/main/java/org/ctc/backup/restore/entity/MatchRestorer.java` | 32-35 (INSERT_SQL) | Add 8 V10/V11 columns |
| `src/main/java/org/ctc/backup/restore/entity/MatchRestorer.java` | 43-53 (restore body) | Add 8 setter calls |
| `src/main/java/org/ctc/backup/restore/entity/TeamRestorer.java` | 37-40 (INSERT_SQL_PASS1) | Add 1 V9 column (`discord_role_id`) |
| `src/main/java/org/ctc/backup/restore/entity/TeamRestorer.java` | 52-64 (restore pass1 body) | Add 1 setter call |
| `src/main/java/org/ctc/backup/restore/entity/MatchdayRestorer.java` | 32-35 (INSERT_SQL) | Add 2 V15 columns (`pick_deadline`, `scheduled_weekend`) |
| `src/main/java/org/ctc/backup/restore/entity/MatchdayRestorer.java` | 43-51 (restore body) | Add 2 setter calls |
| `src/main/java/org/ctc/backup/restore/entity/SeasonRestorer.java` | 49-52 (INSERT_SQL) | Add 2 V13 columns (`discord_race_results_thread_id`, `discord_standings_thread_id`) |
| `src/main/java/org/ctc/backup/restore/entity/SeasonRestorer.java` | 61-70 (restore body) | Add 2 setter calls |
| `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java` | 33 | `isEqualTo(1)` → `isEqualTo(2)` + update `.as(...)` message |
| `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java` | 43 | `isEqualTo(24)` → `isEqualTo(26)` + update `.as(...)` message |
| `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` | H2RoundTripTests + MariaDbRoundTripTests inner classes | Add `DiscordGlobalConfig`/`DiscordPost` byte-equality assertions |
| `.planning/PROJECT.md` | 304-305 | Update paragraph 4 + add v1.13 Phase 101 sub-paragraph |
| `docs/operations/discord-integration.md` | end of file | Add "Backup & Restore semantics" section |

### Files to CREATE

| File | Purpose |
|------|---------|
| `src/main/java/org/ctc/backup/serialization/DiscordGlobalConfigMixIn.java` | Jackson MixIn for `DiscordGlobalConfig` |
| `src/main/java/org/ctc/backup/serialization/DiscordPostMixIn.java` | Jackson MixIn for `DiscordPost` |
| `src/main/java/org/ctc/backup/restore/entity/DiscordGlobalConfigRestorer.java` | EntityRestorer for `discord_global_config` |
| `src/main/java/org/ctc/backup/restore/entity/DiscordPostRestorer.java` | EntityRestorer for `discord_post` |
| `src/test/java/org/ctc/backup/service/BackupDiscordFieldRoundTripIT.java` | D-15: 13 per-field regression-fence tests |
| `src/test/java/org/ctc/backup/service/BackupLenientV1AcceptanceIT.java` | D-17: lenient-v1-acceptance IT |

---

## Pitfalls

### Pitfall 1: `@PostConstruct` Bootstrap Validation — Entity Count Must Match Restorer Count

**What goes wrong:** `BackupImportService.wireRestorersByTableName()` (line 249) throws
`IllegalStateException` at startup if `restorerByTableName.size() != backupSchema.getExportOrder().size()`.
After the D-08 package-filter expansion, `getExportOrder()` returns 26 entities. If the two new
Restorer `@Component` beans are not present, the service fails to start.

**Why it happens:** The `@PostConstruct` validator is a deliberate fail-fast guard introduced in
Phase 75 to prevent silent NOP restores.

**How to avoid:** Create `DiscordGlobalConfigRestorer` and `DiscordPostRestorer` as `@Component`
beans BEFORE (or in the same task as) the D-08 filter expansion. Task ordering in the plan MUST
sequence: restorers-created → filter-expanded → guard-test-updated → app-starts.

**Similarly:** `wireRepositoriesByTableName()` (line 208) requires a `JpaRepository` for each entity
in the export order. `DiscordGlobalConfigRepository` and `DiscordPostRepository` already exist
(`DiscordGlobalConfigRepository.java`, `DiscordPostRepository.java`) — they extend `JpaRepository`
and are `@Component`-discovered. No new repository creation needed; the validator passes automatically.

---

### Pitfall 2: D-10 V8 Default-Row False Assumption

**What goes wrong:** The CONTEXT.md D-10 wording says "Flyway re-inserts the V8 default row" as
one possibility. This is FALSE. Flyway never re-runs a migration.

**How to avoid:** The planner MUST NOT add a post-wipe seed step in `BackupImportService`. The
`getOrInitialize()` defensive fallback in `DiscordGlobalConfigService` provides the correct on-demand
behavior. The D-17 IT assertion should verify `count() == 0` immediately post-import (not `count() == 1`).

---

### Pitfall 3: DiscordPost FK Shape Misconception

**What goes wrong:** A planner might see "5 FK references to Match/Matchday/Race/Season/Phase" and
assume `@JsonIdentityReference(alwaysAsId = true)` is needed (as in `MatchMixIn` for `Matchday`).
This would produce a MixIn that suppresses fields that don't exist as JPA associations.

**Why it happens:** The FK columns are `@Column UUID` fields, not `@ManyToOne` entity fields.
Jackson serializes `UUID matchId` as `"matchId": "uuid-string"` directly — no nested object,
no identity reference needed.

**How to avoid:** Read `DiscordPost.java` carefully. The MixIn needs only `@JsonIgnoreProperties`.
The Restorer extracts `row.get("matchId").asText()` and parses it as UUID.

---

### Pitfall 4: `DiscordGlobalConfig` ID is Long, Not UUID

**What goes wrong:** All 24 existing entities use `UUID` primary keys with `GenerationType.UUID`.
`DiscordGlobalConfig` uses `Long id` with `GenerationType.IDENTITY` (BIGINT AUTO_INCREMENT in V8).
`DiscordPost` also uses `Long id`. A restorer that calls `UUID.fromString(row.get("id").asText())`
will throw `IllegalArgumentException`.

**How to avoid:** `DiscordGlobalConfigRestorer.INSERT_SQL` and `DiscordPostRestorer.INSERT_SQL`
must use `ps.setLong(1, row.get("id").asLong())` for the id column.

**Note:** This also means the `@JsonIdentityInfo` generator in the MixIn (if used at all) should
use `ObjectIdGenerators.PropertyGenerator` with property `"id"` — Jackson will serialize it as a
long integer. Deserialization at restore time: `row.get("id").asLong()`.

---

### Pitfall 5: `DiscordPost.postedAt` and `attachmentsReplacedAt` — Non-BaseEntity Audit Columns

**What goes wrong:** `DiscordPost` has two additional timestamp columns beyond `BaseEntity.createdAt`
and `BaseEntity.updatedAt`:
- `posted_at TIMESTAMP NOT NULL` (V12, maps to `LocalDateTime postedAt`)
- `attachments_replaced_at TIMESTAMP NULL` (V12, maps to `LocalDateTime attachmentsReplacedAt`)

A restorer that only covers the BaseEntity audit columns will leave `posted_at` as NULL on restore,
violating the `NOT NULL` constraint and failing the INSERT.

**How to avoid:** `DiscordPostRestorer.INSERT_SQL` must include ALL columns:
`id`, `channel_id`, `message_id`, `webhook_id`, `webhook_token`, `post_type`,
`match_id`, `matchday_id`, `race_id`, `season_id`, `phase_id`,
`posted_at`, `attachments_replaced_at`, `created_at`, `updated_at`.
That is 15 parameters total.

---

### Pitfall 6: `BackupSchemaGuardTest` Must Be the First Task

**What goes wrong:** If D-08 (package-filter expansion) and D-09 (SCHEMA_VERSION bump) land
BEFORE the guard test is updated, `./mvnw clean verify` will fail with two guard-test assertion
errors. Every subsequent task then runs against a failing build, making it hard to tell what's
actually broken.

**How to avoid:** The plan's first task (or wave) must update `BackupSchemaGuardTest` to
`isEqualTo(2)` and `isEqualTo(26)`. All other implementation tasks depend on this task completing
successfully.

---

### Pitfall 7: `BackupObjectMapperConfig` `FAIL_ON_UNKNOWN_PROPERTIES = true`

**What goes wrong:** The `backupObjectMapper` is configured with
`mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)` (line 66 of
`BackupObjectMapperConfig`). If `DiscordPost` has any property that appears in the JSON but is not
mapped through the entity class + MixIn, the restore will throw a `JsonMappingException`.

**Specifically for `DiscordPost`:** Lombok `@Getter` generates getters for all fields including
`id`, `channelId`, `messageId`, `webhookId`, `webhookToken`, `postType`, `matchId`, `matchdayId`,
`raceId`, `seasonId`, `phaseId`, `postedAt`, `attachmentsReplacedAt`, plus `createdAt`/`updatedAt`
from `BaseEntity`. The export JSON will include all of these. The restorer reads them individually
from `JsonNode` — no full deserialization into the entity class, so `FAIL_ON_UNKNOWN_PROPERTIES`
is not triggered at restore time (the raw `JsonNode` approach doesn't go through `ObjectMapper.readValue`
into the entity type).

However, the export side DOES serialize through the mapper. Any property that should be excluded
(e.g., computed properties, lazy proxies) must be in `@JsonIgnoreProperties` on the MixIn.
Check for any Lombok-generated convenience methods on `DiscordPost` that produce JSON keys —
none are visible in the current class, but re-verify after reading the final class.

---

### Pitfall 8: Self-FK pre-step NULLs in `wipeAllTables` — `discord_post` NOT needed

**What goes wrong:** A planner might add a pre-step `UPDATE discord_post SET match_id = NULL, ...`
to the `wipeAllTables` method before the DELETE loop, mimicking the 3 existing self-FK pre-steps.

**Why this is wrong:** The 3 existing pre-steps handle SELF-FKs (table A references table A). The
`discord_post` FKs reference OTHER tables (matches, matchdays, etc.). These are handled by the
`ON DELETE SET NULL` DB-level cascade — no pre-step needed.

**How to avoid:** Do NOT add any UPDATE pre-steps to `wipeAllTables` for `discord_post`. The
`ON DELETE SET NULL` constraint in V12/V14 handles FK cleanup automatically.

---

## Validation Architecture (Nyquist Dimension 8)

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers |
| Config | `src/test/resources/logback-test.xml`, Surefire/Failsafe routing by `@Tag` |
| Quick run command | `./mvnw verify -Dit.test=BackupSchemaGuardTest` |
| Full suite command | `./mvnw clean verify -Pe2e` |

### Phase Requirements → Test Map

| Req | Behavior | Test Type | Automated Command | File Exists? |
|-----|----------|-----------|-------------------|-------------|
| D-14 | `SCHEMA_VERSION == 2` | integration | `./mvnw verify -Dit.test=BackupSchemaGuardTest` | ✅ (modify) |
| D-14 | `EXPORT_ORDER.size() == 26` | integration | `./mvnw verify -Dit.test=BackupSchemaGuardTest` | ✅ (modify) |
| D-15a | `Match.discordChannelId` survives round-trip | integration | `./mvnw verify -Dit.test=BackupDiscordFieldRoundTripIT` | ❌ Wave 0 |
| D-15b | `Match.discordChannelWebhookUrl` survives | integration | (same) | ❌ Wave 0 |
| D-15c | `Match.discordTeaser` survives | integration | (same) | ❌ Wave 0 |
| D-15d | `Match.streamLink` survives | integration | (same) | ❌ Wave 0 |
| D-15e | `Match.lobbyHost` survives | integration | (same) | ❌ Wave 0 |
| D-15f | `Match.raceDirector` survives | integration | (same) | ❌ Wave 0 |
| D-15g | `Match.streamer` survives | integration | (same) | ❌ Wave 0 |
| D-15h | `Match.discordChannelArchivedAt` survives | integration | (same) | ❌ Wave 0 |
| D-15i | `Team.discordRoleId` survives | integration | (same) | ❌ Wave 0 |
| D-15j | `Matchday.pickDeadline` survives | integration | (same) | ❌ Wave 0 |
| D-15k | `Matchday.scheduledWeekend` survives | integration | (same) | ❌ Wave 0 |
| D-15l | `Season.discordRaceResultsThreadId` survives | integration | (same) | ❌ Wave 0 |
| D-15m | `Season.discordStandingsThreadId` survives | integration | (same) | ❌ Wave 0 |
| D-16 | `DiscordGlobalConfig` byte-equality on H2 | integration | `./mvnw verify -Dit.test=BackupRoundTripIT` | ✅ (extend) |
| D-16 | `DiscordPost` byte-equality on H2 | integration | `./mvnw verify -Dit.test=BackupRoundTripIT` | ✅ (extend) |
| D-16 | `DiscordGlobalConfig` byte-equality on MariaDB | integration | `./mvnw verify -Dit.test=BackupRoundTripIT -Ddocker.available=true` | ✅ (extend) |
| D-16 | `DiscordPost` byte-equality on MariaDB | integration | (same) | ✅ (extend) |
| D-17 | v1 ZIP import succeeds (no `SCHEMA_MISMATCH` exception) | integration | `./mvnw verify -Dit.test=BackupLenientV1AcceptanceIT` | ❌ Wave 0 |
| D-17 | v1 import: `DiscordGlobalConfig` count == 0 post-import | integration | (same) | ❌ Wave 0 |
| D-17 | v1 import: `DiscordPost` count == 0 post-import | integration | (same) | ❌ Wave 0 |
| D-17 | v1 import: V8-V15 columns NULL on Match/Team/Matchday/Season | integration | (same) | ❌ Wave 0 |
| D-08 | Package filter includes `org.ctc.discord.model.*` | integration | `./mvnw verify -Dit.test=BackupSchemaGuardTest` | ✅ (via count=26) |
| D-10 | v1 backup accepted (no version refusal) | integration | `./mvnw verify -Dit.test=BackupLenientV1AcceptanceIT` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw verify -Dit.test=BackupSchemaGuardTest` (< 30s)
- **Per wave merge:** `./mvnw clean verify` (full IT suite, no E2E)
- **Phase gate:** `./mvnw clean verify -Pe2e` before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/backup/service/BackupDiscordFieldRoundTripIT.java` — covers D-15 (13 per-field regression-fence assertions)
- [ ] `src/test/java/org/ctc/backup/service/BackupLenientV1AcceptanceIT.java` — covers D-17 (lenient-v1-acceptance)

Note: `BackupSchemaGuardTest` and `BackupRoundTripIT` exist and are modified (not created).

---

## Open Questions for Planner

### OQ-1 — `DiscordGlobalConfig` MixIn: suppress computed properties? (SEVERITY: LOW)

`DiscordGlobalConfig` has no computed properties visible in the class body. However, `BaseEntity`'s
`createdAt`/`updatedAt` are in the JSON via Lombok `@Getter`. No `@JsonIgnoreProperties` values
beyond `"hibernateLazyInitializer"` and `"handler"` appear necessary. Planner should grep for
any `@Transient` or computed fields added after the RESEARCH phase. Current assessment: minimal MixIn.

### OQ-2 — `DiscordPost` `webhookToken` in backup: SecretFilter needed? (SEVERITY: MEDIUM)

The `webhook_token` is a 128-char secret. The CONTEXT.md acknowledges this as PII-equivalent (D-05,
canonical_refs § Discord platform constraints). The current decision is to include it in the backup
verbatim (preserve-as-is per D-05). No `@JsonIgnore` is added.

The planner should verify that no existing `BackupObjectMapper` serializer filter strips sensitive
fields. Current analysis: the mapper has no such filter. The `DiscordPost.java:24`
`@ToString(exclude = {"webhookToken"})` only affects `toString()`, not Jackson serialization.
Result: `webhookToken` WILL appear in the backup ZIP — operator must protect the ZIP as noted in
the DOCS-02 runbook update.

If the planner chooses to add a `@JsonIgnore` on `webhookToken` in the MixIn, the restore path
must be adjusted (the Restorer would need to handle a missing `webhookToken` field in old backups).
This would be a different tradeoff from D-05. The CONTEXT.md locks D-05 (preserve as-is), so no
`@JsonIgnore` on `webhookToken`. The DOCS-02 update is the correct mitigation.

### OQ-3 — `BackupRoundTripIT` seeding: does `TestDataService.seed()` produce `DiscordPost` rows? (SEVERITY: MEDIUM)

`TestDataService.java` currently seeds `discordRoleId` on two teams and `discordTeaser` on a match
(lines 1065, 1069, 1096) but does NOT create any `DiscordPost` or `DiscordGlobalConfig` rows with
non-default values (confirmed by grep — no `DiscordPost` or `DiscordGlobalConfig` save calls).

The D-16 byte-equality test for `DiscordPost` will find zero rows if it relies on `TestDataService.seed()`
alone. The planner must add Discord fixture seeding to the `@BeforeEach` of the D-16 test class,
or extend `TestDataService` with a `seedDiscordFixture()` method. Per CLAUDE.md "Isolate Test Data
Completely": use test-prefixed IDs.

For `DiscordGlobalConfig`, one default row exists post-Flyway-V8 (Flyway seeds it). The byte-equality
test can use the existing default row as the "before" state — but for a meaningful test, the row
should have non-empty values (non-empty guildId, webhookUrl, etc.). The planner should seed a
`DiscordGlobalConfig` with test values in `@BeforeEach`.

### OQ-4 — `BackupImportMariaDbSmokeIT` entity count assertion (SEVERITY: LOW)

`BackupImportMariaDbSmokeIT.java:168`: `expectedEntityCount = backupSchema.getExportOrder().size()`
(dynamic, not hardcoded). After Phase 101, this becomes 26. The test already uses the dynamic
value — no change needed to this file. But the planner should verify the assertion at line 180:
```java
assertThat(preExportCounts).hasSize(expectedEntityCount);
```
This passes for 26 after Phase 101. Low-risk, but worth noting.

### OQ-5 — `BackupImportService` Javadoc string "24" (SEVERITY: LOW)

Multiple places in `BackupImportService.java` mention "24 tables" or "24 EntityRestorers" in
comments (lines 76-77, 56). These should be updated to "26" in Phase 101. The planner should
include a documentation-only task or inline with the schema-version task.

---

## Architecture Diagram

```
EXPORT FLOW (unchanged — BackupExportService reads getExportOrder())
  BackupSchema.initializeExportOrder()
    ├── filter: startsWith("org.ctc.domain.model")  [24 entities — current]
    └── filter: OR startsWith("org.ctc.discord.model")  [+2 entities — Phase 101]
          → EntityTopoSorter.sort() → 26 EntityRef entries
          → BackupExportService.writeZip() → ZIP with 26 data/*.json entries

IMPORT FLOW (version-check updated, 2 new Restorers, 4 extended Restorers)
  BackupImportService.buildPreview()
    └── schema_version check: !Set.of(1, 2).contains(v)  [Phase 101 loosening]

  BackupImportService.execute()
    ├── wipeAllTables(): DELETE FROM 26 tables in reverse topo order
    │     ON DELETE SET NULL handles discord_post FK nullification automatically
    └── restoreAll(): iterate 26 EntityRestorers
          ├── MatchRestorer (extended: +8 V10/V11 columns)
          ├── TeamRestorer (extended: +1 V9 column)
          ├── MatchdayRestorer (extended: +2 V15 columns)
          ├── SeasonRestorer (extended: +2 V13 columns)
          ├── DiscordGlobalConfigRestorer (NEW: Long id, 10 data columns + 2 audit)
          └── DiscordPostRestorer (NEW: Long id, 13 data columns + 2 audit)

V1 IMPORT PATH (schema_version == 1):
  restoreOneTable() → zf.getEntry("data/discord-global-config.json") → null
    → log WARN, return 0 (no crash, no restorer invocation)
  DiscordGlobalConfig table: empty post-import
  First operator page-load → DiscordGlobalConfigService.getOrInitialize() → INSERT fresh defaults
```

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `DiscordGlobalConfigService.getOrInitialize()` is called before any Discord feature page renders, providing the v1-import defensive fallback without an explicit post-wipe seed step | RQ-1 | If a startup bean calls `findFirstByOrderByIdAsc()` without null-check, app crashes after v1 import |
| A2 | No other computed properties exist on `DiscordPost` or `DiscordGlobalConfig` beyond what is visible in the current class files | RQ-4 | MixIn would need additional `@JsonIgnoreProperties` entries to prevent `FAIL_ON_UNKNOWN_PROPERTIES` exceptions |
| A3 | `DiscordPostRepository` and `DiscordGlobalConfigRepository` are already discovered as `JpaRepository` beans, satisfying the `wireRepositoriesByTableName` startup validator | Pitfall 1 | If the repositories are excluded from Spring's component scan in some profile, the app will fail to start |

**If this table is wrong:** The planner must verify A1 by grepping all callers of
`DiscordGlobalConfigRepository` before adding any post-wipe seed logic.

---

## Sources

### Primary (HIGH confidence)
- Direct source code reads — all claims are code-citation verified
  - `BackupSchema.java` (filter predicate, SCHEMA_VERSION constant)
  - `BackupImportService.java` (schema-version gate, wipeAllTables, wireRestorersByTableName)
  - `EntityTopoSorter.java` (FK-walk logic, MANY_TO_ONE filter)
  - `DiscordGlobalConfig.java`, `DiscordPost.java` (field types, BaseEntity inheritance, id type)
  - `DiscordGlobalConfigService.java` (getOrInitialize defensive fallback)
  - `BackupSchemaGuardTest.java` (exact assertion forms)
  - `BackupRoundTripIT.java` (MariaDB opt-in annotation, byte-equality pattern)
  - `BackupSerializationModule.java` (registration mechanism)
  - All V8-V15 migration files (column lists, FK constraints, NOT NULL constraints)
  - `MatchRestorer.java`, `TeamRestorer.java`, `MatchdayRestorer.java`, `SeasonRestorer.java`

### Secondary (MEDIUM confidence)
- Project planning documents: `101-CONTEXT.md`, `STATE.md`, `PROJECT.md` — cross-referenced with code

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all existing patterns read from source
- Architecture: HIGH — code-reading confirms all assertions
- Pitfalls: HIGH — derived from exact code behavior, not heuristics
- Open questions: MEDIUM — require planner/executor to verify specific edge cases

**Research date:** 2026-05-26
**Valid until:** 2026-07-26 (stable codebase; V8-V15 migrations are immutable)

---

## RESEARCH COMPLETE

**Phase:** 101 — Backup/Restore covers Discord schema (V8-V15)
**Confidence:** HIGH

### Key Findings

1. **V8 default-row resurrection is NOT needed as an explicit `BackupImportService` step.** `DiscordGlobalConfigService.getOrInitialize()` provides a live self-healing fallback; the D-17 IT assertion must check for 0 rows post-v1-import, not 1.

2. **`DiscordPost` FKs are plain `@Column UUID` fields, NOT `@ManyToOne` JPA associations.** The `EntityTopoSorter` places `DiscordPost` with in-degree 0 (no topo ordering constraint relative to its parent tables). The MixIn needs only `@JsonIgnoreProperties`, not `@JsonIdentityReference`. The Restorer extracts raw UUID strings with `row.get("matchId").asText()`.

3. **Both Discord entities use `Long id` (BIGINT AUTO_INCREMENT), not UUID.** All new Restorers must use `ps.setLong(1, row.get("id").asLong())`, not `UUID.fromString(...)`.

4. **`BackupSchemaGuardTest` uses exact `isEqualTo(n)` — not `>= n`.** Both assertions fail immediately after D-08/D-09 land. The guard test update must be the first task in the plan.

5. **`DiscordPost` has 15 INSERT_SQL parameters** — beyond the 2 BaseEntity audit columns, it has `posted_at NOT NULL` and `attachments_replaced_at NULL` which must be in the INSERT to avoid NOT NULL constraint violations.

6. **No existing test fixture directory for backup ZIPs.** The D-17 synthetic v1 ZIP must be built programmatically in the test (project convention: generate, don't commit binary fixtures).

### File Created
`.planning/phases/101-backup-restore-covers-discord-schema-v8-v15/101-RESEARCH.md`

### Confidence Assessment
| Area | Level | Reason |
|------|-------|--------|
| Standard Stack | HIGH | All patterns verified by direct source reading |
| Architecture | HIGH | EntityTopoSorter logic confirmed by code; DiscordPost FK shape confirmed |
| Pitfalls | HIGH | Derived from exact code behavior (PostConstruct validators, Long vs UUID ids, NOT NULL columns) |
| Test Infrastructure | HIGH | Guard test exact assertion forms read from source; MariaDB opt-in mechanism confirmed |

### Open Questions
- OQ-2 (MEDIUM): Whether any `@JsonIgnore` should be added to `webhookToken` — CONTEXT D-05 locks preserve-as-is, so no action; DOCS-02 update is the mitigation.
- OQ-3 (MEDIUM): `TestDataService.seed()` does not produce `DiscordPost` rows — planner must add Discord fixture seeding to `@BeforeEach` in D-16 test class.

### Ready for Planning
Research complete. Planner can now create PLAN.md for Phase 101.
