# Phase 101: Backup/Restore covers Discord schema (V8-V15) — Context

**Gathered:** 2026-05-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Close the silent-data-loss gap that the Discord-introduced schema (Flyway
V8-V15) opened in the backup wire contract, and revisit the Phase-72 D-15
package-filter exclusion (`org.ctc.domain.model.*` only) in light of v1.13
promoting Discord to first-class operator state.

**Two distinct surface problems being closed:**

1. **Silent data loss on round-trip for fields on existing entities.** Match /
   Team / Matchday / Season carry V8-V15 columns (`Match.discord_*` +
   scheduling fields, `Team.discord_role_id`, `Matchday.pick_deadline` /
   `scheduled_weekend`, `Season.discord_*_thread_id`). Jackson serialises them
   correctly via Lombok-generated getters (already in the export ZIP today),
   but every `EntityRestorer.INSERT_SQL` lists only V1 columns. Confirmed by
   reading `MatchRestorer.java:32-35` and grepping the 4 affected restorers
   (zero matches for any V8-V15 column name). Result: import silently
   `NULL`s every Discord/scheduling field — operator loses the entire
   per-match Discord state on a restore.

2. **Two entire entities excluded from backup scope.** `DiscordGlobalConfig`
   (V8/V9/V13/V15) and `DiscordPost` (V12/V14) live under
   `org.ctc.discord.model.*` and are filtered out by `BackupSchema`'s
   `org.ctc.domain.model.*` package-filter. Result: operator config
   (guild_id, 3 webhook URLs, 2 forum-channel-IDs, current-match category,
   bot-application-id, vs-emoji-name, matchday-pairings-template) and the
   entire `discord_post` idempotency-tracking table are not in the backup —
   after a restore, every Discord post would have to be manually re-created
   (no `postOrEdit` lookup hits → all `Re-Post` clicks would POST new
   messages instead of PATCHing existing ones, orphaning every prior post in
   Discord).

**In scope (the closure):**

- Extend `BackupSchema` package filter to include `org.ctc.discord.model.*`
  alongside `org.ctc.domain.model.*` — the only mechanism change required
  for entity inclusion (D-08).
- Add MixIns + Restorers for `DiscordGlobalConfig` (single-row entity) and
  `DiscordPost` (FK-rich entity).
- Extend the 4 affected `EntityRestorer` INSERT_SQL statements (Match,
  Team, Matchday, Season) to cover every V8-V15 column.
- Bump `BackupSchema.SCHEMA_VERSION` from 1 to 2 (wire format change,
  Phase 72 invariant compliant).
- Loosen the importer's schema-version equality check to
  `schema_version IN (1, 2)`. v1 imports leave Discord sections empty
  (default-row seed for `DiscordGlobalConfig` from V8 migration,
  `DiscordPost` empty, V8-V15 columns NULL on Match/Team/Matchday/Season).
- Update `BackupSchemaGuardTest` to 26 entities + `SCHEMA_VERSION = 2`.
- Add regression-fence tests per V8-V15 field (round-trip assertion
  per column) so a future restorer rewrite cannot silently drop them
  again.
- Extend `BackupRoundTripIT` byte-equality assertions to
  `DiscordGlobalConfig` + `DiscordPost` on both H2 and MariaDB
  (Testcontainers, opt-in).
- Document single-guild restore semantics in `docs/operations/discord-integration.md`
  (the DOCS-02 runbook produced in Phase 98) — explicit operator warning
  that restore on a non-source guild produces orphan IDs.

**Out of scope (explicit non-goals):**

- Cross-guild migration tooling — preserve-as-is policy is the explicit
  Phase 101 stance (D-05). A v1.14+ feature could add a
  "Reset Discord state on import" toggle, but Phase 101 does not build it.
- Aggressive wipe of Discord fields on import — would defeat the purpose
  of including `DiscordPost` (D-06 rejected option).
- Marker-annotation mechanism (`@Backupable`) — would replace Phase 72
  D-15's structural enforcement; not necessary when extending the
  package filter to one additional package solves the problem.
- Moving `DiscordGlobalConfig` / `DiscordPost` into `org.ctc.domain.model`
  — breaks the package-by-feature convention; rejected.
- Migration of `data_import_audit` into backup scope — Phase 72 D-15
  excluded it deliberately (operational metadata, not league data); the
  package filter expansion stays narrow to `org.ctc.discord.model.*` only.
- Inline v1 → v2 migration (treating missing entities as empty arrays
  inside a shared parsing path) — operator-friendliness handled by
  the lenient version-check path, not a separate migration code branch.
- `data_import_audit` schema changes for the bumped SCHEMA_VERSION — the
  audit row records `schema_version_used` as a free-form text field,
  no migration needed.

</domain>

<decisions>
## Implementation Decisions

### Scope of inclusion (GA-1)

- **D-01:** All three Discord-related data categories enter the backup
  round-trip: (a) V8-V15 fields on existing entities (Match / Team /
  Matchday / Season), (b) `DiscordGlobalConfig` entity, (c) `DiscordPost`
  entity. Aligns with v1.13 promoting Discord to first-class operator
  state — Phase 72 D-15's exclusion was load-bearing when Discord was
  speculative, no longer.

- **D-02:** Silent-data-loss on category (a) is treated as a regression
  bug introduced by V8-V15 schema landing without restorer updates —
  must be fixed regardless of decisions on (b) / (c). The bug exists
  today; Phase 101 closes it.

- **D-03:** `DiscordGlobalConfig` is a single-row table (V8 seeds one
  row with `INSERT INTO discord_global_config ... VALUES ('', '', '', '',
  'CTC', NULL, ...)`). The Restorer must handle the "wipe + restore"
  cleanly: replace-all import deletes the seed row and writes whatever the
  backup contains (including a potentially empty-string-defaulted row from
  a pristine source system).

- **D-04:** `DiscordPost` carries 5 nullable FKs (match, matchday, race,
  season, phase) — `EntityTopoSorter` must place it AFTER all five.
  `DiscordGlobalConfig` has no FKs to domain.model entities, can go
  early in the topo order.

### Cross-guild restore semantics (GA-2)

- **D-05:** Discord IDs (channel_id, message_id, webhook_id,
  webhook_token, role_id, thread_id, forum-channel-id, guild_id) are
  preserved as-is across the round-trip. No detection logic, no wipe,
  no toggle. Operator owns the guarantee that source-guild = target-guild
  on a restore.

- **D-06:** Cross-guild restore = explicitly undefined behaviour. The
  documented expectation in `docs/operations/discord-integration.md` is
  "single-guild operation". If an operator restores onto a non-source
  guild, every subsequent Discord API call hitting a stored ID will throw
  `DiscordApiException.NotFound` — operator surfaces the issue via the
  existing typed-catch + flash-badge path and manually re-runs the
  `/admin/discord-config` setup + per-team role mapping + per-match
  "Create Discord Channel" flow.

- **D-07:** No code-level guard against cross-guild restore. The
  alternative options (intelligent guild_id-comparison, UI toggle in
  import-preview) were considered and rejected as over-engineering for
  a single-admin, single-league app where multi-guild is explicitly
  DISC-FUTURE-04 / out-of-scope.

### Mechanism + SCHEMA_VERSION bump (GA-3)

- **D-08:** Mechanism is **package-filter expansion** in `BackupSchema`:
  the `@PostConstruct` initializer's filter predicate becomes
  `getPackage().getName().startsWith("org.ctc.domain.model") || getPackage().getName().startsWith("org.ctc.discord.model")`.
  Keeps Phase 72 D-15's structural philosophy (no marker annotation, no
  developer memory, no opt-in list) — just adds one explicit additional
  package. Alternatives rejected: `@Backupable` marker (philosophy
  reversal), moving entities to `org.ctc.domain.model` (breaks
  package-by-feature), explicit class-name whitelist (drift risk).

- **D-09:** `BackupSchema.SCHEMA_VERSION` bumps from `1` to `2`. The
  wire format strictly changes — manifest goes from 24 entries to 26
  entries, ZIP gains `data/discord-global-config.json` +
  `data/discord-post.json`. Compliant with Phase 72 invariant ("every
  wire-incompatible schema change bumps the integer by 1").

- **D-10:** Importer accepts **schema_version IN (1, 2)** —
  Phase-74's strict-equality check is loosened to a 2-value whitelist.
  Implementation: replace `manifestSchemaVersion != BackupSchema.SCHEMA_VERSION`
  refusal with `!Set.of(1, 2).contains(manifestSchemaVersion)`. Operator
  can still restore pre-v1.13 v1 backups; Discord sections will be empty
  after restore (default-seeded `DiscordGlobalConfig` row from V8
  migration survives the wipe-restore-with-no-data because the V8
  default-row is RE-INSERTED by Flyway on the next boot... actually wait,
  the V8 migration only runs once on initial schema setup. On import
  replace-all, the row is wiped and not restored. **Planner action:**
  verify the V8 default-row resurrection path or add an explicit
  post-wipe seed step in `BackupImportService` for v1 imports.

- **D-11:** SCHEMA_VERSION 2 imports populate Discord state from the
  backup. SCHEMA_VERSION 1 imports skip Discord-section restore
  entirely (no JSON file → empty array → no restorer call) — V8-V15
  columns on Match/Team/Matchday/Season land NULL, `DiscordPost`
  table stays empty, `DiscordGlobalConfig` gets a fresh default row
  (per D-10 planner action).

- **D-12:** The 4 affected `EntityRestorer.INSERT_SQL` must be extended
  to cover all V8-V15 columns on their respective tables. Mechanical
  per-table edit; no abstraction extraction — Phase 75's per-entity
  Restorer pattern is the canonical shape.

### Test coverage shape (GA-4)

- **D-13:** Test coverage is **full first-class** — Pflicht + per-field
  regression-fence + wide byte-equality on H2 AND MariaDB-Testcontainer.

- **D-14:** Pflicht-Updates (`BackupSchemaGuardTest` adjustments):
  `EXPORT_ORDER.size()` assertion flips from 24 to 26;
  `SCHEMA_VERSION` assertion flips from 1 to 2. Both updates are
  load-bearing — without them `./mvnw clean verify` fails on the guard
  test before any Phase-101 code change can be exercised.

- **D-15:** Per-field regression-fence — one round-trip assertion per
  V8-V15 column on existing entities. Covers: `Match.discord_channel_id`,
  `discord_channel_webhook_url`, `discord_teaser`, `stream_link`,
  `lobby_host`, `race_director`, `streamer`, `discord_channel_archived_at`
  (8 fields), `Team.discord_role_id` (1 field), `Matchday.pick_deadline`,
  `scheduled_weekend` (2 fields), `Season.discord_race_results_thread_id`,
  `discord_standings_thread_id` (2 fields) — 13 fields total. Each
  asserts that a seeded non-null value survives export → import → re-fetch.
  Prevents the silent-NULL regression from being silently re-introduced
  by a future restorer rewrite.

- **D-16:** Wide byte-equality on `DiscordGlobalConfig` + `DiscordPost`
  rows — extends `BackupRoundTripIT`'s existing pattern (currently
  asserts on Race / SeasonDriver / Team) to the 2 new entities. SHA-256
  on the post-restore row JSON-serialized form vs. pre-export form, on
  both H2 (default Surefire) and MariaDB-Testcontainer
  (`@EnabledIfSystemProperty(docker.available=true)`, opt-in like
  existing Backup ITs).

- **D-17:** Lenient-v1-acceptance test — one Failsafe IT that builds a
  synthetic v1 backup ZIP (manifest.json `schema_version=1`, no
  Discord sections), imports it, asserts (a) import succeeds (no
  `SchemaVersionRefusalException`), (b) `DiscordGlobalConfig` table has
  the default seed row, (c) `DiscordPost` table is empty, (d) Match /
  Team / Matchday / Season V8-V15 fields are NULL on every row.

### Claude's Discretion

- **MixIn shape for `DiscordPost`**: 5 FK references should follow the
  existing `@JsonIdentityReference(alwaysAsId = true)` pattern from
  `MatchMixIn`. Planner's call on whether to add `@JsonIgnoreProperties`
  exclusions for `hibernateLazyInitializer` (probably yes, by
  convention).
- **MixIn shape for `DiscordGlobalConfig`**: simple single-row entity
  with no FKs; minimal MixIn needed (probably just
  `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})`).
- **EntityTopoSorter behaviour verification**: planner should write a
  RESEARCH-phase grep / read to confirm that the Kahn sort handles
  cross-package FKs (e.g., `DiscordPost.match` → `Match`) correctly
  when both ends are in the filtered entity set. The current sort
  walks `@ManyToOne` / `@OneToOne` owning-side attributes — should
  work, but verify.
- **Naming of new restorer classes**: follow the existing
  `{Entity}Restorer` convention (`DiscordGlobalConfigRestorer`,
  `DiscordPostRestorer`) and place them in
  `src/main/java/org/ctc/backup/restore/entity/`.
- **MixIn package location**: existing MixIns live in
  `src/main/java/org/ctc/backup/serialization/`. Discord MixIns follow.
- **Lenient version-check implementation**: either `Set.of(1, 2).contains(v)`
  or a `SUPPORTED_SCHEMA_VERSIONS` constant. Planner's call.
- **D-10 V8-default-row planner action**: if the V8 seed row is wiped
  by the replace-all and not auto-resurrected by Flyway, the planner
  must add an explicit post-wipe seed step in `BackupImportService` for
  v1 imports (and v2 imports where the backup `DiscordGlobalConfig`
  array is somehow empty). RESEARCH-phase verification required.
- **Wire-Contract documentation in PROJECT.md** "Backup Wire Contract
  (v1.10)" section: planner updates the "24-entity scope" text to
  "26-entity scope" + adds a "v1.13 Phase 101 update" sub-paragraph
  documenting the package-filter expansion + the SCHEMA_VERSION 2 bump
  + the lenient v1 acceptance — same Update-on-Triage discipline as
  the existing wire-contract paragraphs.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 72 baseline (the decision being revisited)
- `.planning/phases/72-backup-wire-contract-schema-manifest-objectmapper-audit-log-scope/72-CONTEXT.md` — D-15 package-filter exclusion (`org.ctc.domain.model.*` only) is the load-bearing decision Phase 101 amends. Read this BEFORE editing `BackupSchema.initializeExportOrder`.
- `.planning/PROJECT.md` § "Backup Wire Contract (v1.10)" — the wire-format invariants (SCHEMA_VERSION = monotonic int, ZIP layout per-entity manifest-first, EXPORT_ORDER topo-sorted, 24-entity scope, ObjectMapper isolation). Phase 101 updates the "24-entity" + "SCHEMA_VERSION = 1" lines and adds a sub-paragraph on the package-filter expansion + lenient v1 acceptance.

### Phase 75 restorer pattern (the code surface to extend)
- `.planning/phases/75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat/` — Phase-75 introduced the per-entity `EntityRestorer` pattern with explicit `INSERT_SQL` column lists (bypasses `AuditingEntityListener`). Phase 101 extends 4 existing Restorers + adds 2 new ones.
- `src/main/java/org/ctc/backup/restore/EntityRestorer.java` — Restorer SPI (the `restore(List<JsonNode>, JdbcTemplate)` method signature).
- `src/main/java/org/ctc/backup/restore/entity/MatchRestorer.java` — example of an existing Restorer; lines 32-35 (`INSERT_SQL`) is the surface that misses V10/V11 columns.

### Phase 82 BACK-01 guard test (must be updated)
- `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java` — asserts `SCHEMA_VERSION = 1` (line 34) + `EXPORT_ORDER.size() = 24` (line 43). Both flips in Phase 101.

### Affected migrations (already shipped, do NOT modify)
- `src/main/resources/db/migration/V8__discord_global_config.sql` — `discord_global_config` table + seed row.
- `src/main/resources/db/migration/V9__add_discord_team_role_and_current_match_category.sql` — `teams.discord_role_id` + `discord_global_config.current_match_category_id`.
- `src/main/resources/db/migration/V10__add_matches_discord_and_scheduling_fields.sql` — 7 columns on `matches`.
- `src/main/resources/db/migration/V11__add_matches_discord_channel_archived_at.sql` — 1 column on `matches`.
- `src/main/resources/db/migration/V12__discord_post.sql` — `discord_post` table + 5 FK indexes.
- `src/main/resources/db/migration/V13__add_seasons_discord_threads_and_forum_webhooks.sql` — 2 columns on `seasons` + 2 columns on `discord_global_config`.
- `src/main/resources/db/migration/V14__add_discord_post_phase_id.sql` — `discord_post.phase_id` FK.
- `src/main/resources/db/migration/V15__add_matchday_pairings_fields.sql` — 2 columns on `matchdays` + 1 column on `discord_global_config`.

### Existing code (the implementation surface)
- `src/main/java/org/ctc/backup/schema/BackupSchema.java:39-49` — `@PostConstruct initializeExportOrder()` is the package-filter site (D-08 edits line 42).
- `src/main/java/org/ctc/backup/service/BackupImportService.java` — schema-version refusal site (D-10 edits the equality check).
- `src/main/java/org/ctc/backup/service/BackupExportService.java` — `@Transactional(readOnly=true)` streaming exporter; reads `BackupSchema.getExportOrder()` — needs no changes if the topo-sort handles new entities correctly.
- `src/main/java/org/ctc/backup/serialization/` — 24 existing MixIns + `BackupSerializationModule.java`; new MixIns join this directory; `BackupSerializationModule` may need a registration update.
- `src/main/java/org/ctc/backup/restore/entity/MatchRestorer.java`, `TeamRestorer.java`, `MatchdayRestorer.java`, `SeasonRestorer.java` — 4 Restorers whose `INSERT_SQL` must be extended.

### Discord domain model (the new entities to include)
- `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` — single-row entity, 12+ columns after V8/V9/V13/V15.
- `src/main/java/org/ctc/discord/model/DiscordPost.java` — FK-rich entity with `(match, matchday, race, season, phase)` references.
- `src/main/java/org/ctc/discord/model/DiscordPostType.java` — enum; serialised as string by Jackson default.
- `src/main/java/org/ctc/discord/repository/DiscordGlobalConfigRepository.java`, `DiscordPostRepository.java` — repository surface, planner may consult for read-only assertions in tests.

### Affected domain entities (existing, must NOT be re-shaped)
- `src/main/java/org/ctc/domain/model/Match.java:51-73` — Discord/scheduling fields (V10/V11).
- `src/main/java/org/ctc/domain/model/Team.java:42` — `discord_role_id` (V9).
- `src/main/java/org/ctc/domain/model/Matchday.java:41-45` — `pick_deadline` / `scheduled_weekend` (V15).
- `src/main/java/org/ctc/domain/model/Season.java:39-43` — `discord_*_thread_id` (V13).

### Test infrastructure to extend
- `src/test/java/org/ctc/backup/service/BackupRoundTripIT.java` — row-count parity (24 → 26) + byte-equality (extend Race/SeasonDriver/Team coverage to DiscordGlobalConfig + DiscordPost).
- `src/test/java/org/ctc/backup/service/BackupImportMariaDbSmokeIT.java` — Testcontainers round-trip; planner may seed Discord data into the fixture.
- `src/test/java/org/ctc/backup/service/BackupRestoreZipOpenCountIT.java` — single-pass ZIP read fence; unaffected unless reading the new entity files needs special handling.

### Project-level invariants (must read before planning)
- `CLAUDE.md` — "Do Not Modify Flyway Migrations" (V8-V15 are immutable; restorer-side INSERT_SQL changes are the lever), "Build & Test Discipline" (`./mvnw clean verify -Pe2e` end-of-phase gate), "Subagent Rules" (inline sequential on `gsd/v1.13-discord-integration` milestone branch, no worktrees, no parallel waves — planner explicitly NOT allowed to spawn parallel-wave subagents even if the plan has wave-2/wave-3 sequencing), "WireMock is not Real-API Coverage" (Backup ITs must hit a real H2 / MariaDB engine; no DataSource mocks), "Architectural Principles" → "Grep All Usages Before Refactor" (every restorer modified must be grepped for callsites; every MixIn must be checked for serialization-module registration).
- `.planning/STATE.md` "Baselines to Preserve" — `BackupSchema.SCHEMA_VERSION: 1` + `EXPORT_ORDER size: 24` are explicitly flagged with "revisited in Phase 101". Phase 101 updates this baseline.
- `.planning/ROADMAP.md` Phase 101 entry — Goal: TBD at roadmap time; Phase 101 CONTEXT formalises the goal as the 2-problem closure above.

### Discord platform constraints
- Discord developer docs (no local copy): the `webhook_token` (64-128 char hex) is a PII-equivalent secret AND is now part of the backup file payload. Operator must protect backup ZIPs accordingly. Planner documents this in DOCS-02 runbook (the Phase 98 doc, updated in-place).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`EntityTopoSorter` (Kahn's algorithm)** — Phase 72 D-Decision; runtime-generated topological sort. Walks `EntityType.getDeclaredSingularAttributes()` for `@ManyToOne` / `@OneToOne`. `DiscordPost.match` / `matchday` / `race` / `season` / `phase` are all `@ManyToOne` to entities already in the filtered set → planner expects clean integration. Self-FKs (Team.parentTeam analog) are absent on Discord entities.

- **Phase 75 `EntityRestorer` SPI pattern** — `tableName()` + `restore(List<JsonNode>, JdbcTemplate)`. Each restorer owns its `INSERT_SQL`. Adding two new restorers + extending four existing ones follows the existing shape exactly.

- **Phase 73 `BackupSerializationModule`** — `@Component` Jackson module that registers all MixIns. New MixIns get one line each added here.

- **`BackupObjectMapperConfig`** — `@Qualifier("backupObjectMapper")` strict-mode mapper (Phase 72 D-11 amended per RESEARCH §Pitfall P-2). The new MixIns flow through this mapper automatically once registered.

- **`BackupRoundTripIT` H2 + MariaDB Testcontainers pattern** — Phase 77 + Phase 82 BACK-03/04 established this. SHA-256 byte-equality on row JSON-serialized form. Adding 2 new entity assertions follows the existing shape.

- **`BackupImportMariaDbSmokeIT.@withReuse(true)`** — Phase 90 PERF-04 made the Testcontainer opt-in reusable via `~/.testcontainers.properties`. New Discord seed data piggybacks on this fixture.

### Established Patterns

- **Package-filter as structural enforcement (Phase 72 D-15)** — additive expansion (`startsWith A OR startsWith B`) preserves the philosophy. No marker annotation, no opt-in list.

- **Schema version bump pattern (Phase 72 D-Decision)** — monotonic int, bump on every wire-incompatible change. Phase 101 = 1 → 2 (first bump since v1.10).

- **`@Transactional` boundary on import** — Phase 75 single-transaction wipe-and-restore. Adding 2 new entity restorers respects the same transaction; planner must include them in the FK-reverse delete ordering AND the FK-respecting insert ordering.

- **Lombok `@Getter` on entities** — Discord entity getters drive default Jackson serialization. No code changes on Discord entities; only the MixIns customize the JSON shape.

- **CSRF protection on import endpoint** — existing `POST /admin/backup/import` is CSRF-protected. Schema-version refusal change is purely server-side; no UI change.

- **`AuditingEntityListener` bypass** — `JdbcTemplate.batchUpdate` writes `created_at` / `updated_at` from the JSON, not from `@CreatedDate` / `@LastModifiedDate`. Both DiscordGlobalConfig + DiscordPost extend `BaseEntity` (verify via planner-grep) and need the same bypass.

### Integration Points

- **`BackupSchemaGuardTest`** — flips its two assertions (count + version). Without this update, every subsequent change fails the build before reaching the new code.

- **`BackupSerializationModule`** — registers new MixIns (1-line additions).

- **`BackupImportService` schema-version check** — single-line change (equality → `Set.contains`).

- **`PROJECT.md` "Backup Wire Contract (v1.10)" section** — wire-contract paragraph documentation (Update-on-Triage discipline per the existing Phase 72 D-Decisions table convention).

- **`docs/operations/discord-integration.md`** — DOCS-02 runbook from Phase 98; planner adds a "Backup & Restore" section explicitly calling out single-guild semantics + the webhook_token secrecy implication.

- **Backup file format** — wire-format change (manifest entries 24 → 26, two new `data/*.json` files). Operators with v1 backups still benefit from the lenient version check (D-10).

</code_context>

<specifics>
## Specific Ideas

- **Concrete worked example for the planner** — restore `BackupRoundTripIT`'s
  existing Saison-2023 fixture, plus seed 1 `DiscordGlobalConfig` row with
  non-default values (e.g., `guild_id='1234567890123456789'`,
  `vs_emoji_name='CTC'`, populated webhooks) + 3 `DiscordPost` rows
  spanning post types (TEAM_CARDS, SCHEDULE, MATCHDAY_PAIRINGS) + populate
  `Match.discord_channel_id` / `Match.discord_channel_webhook_url` on at
  least one match + `Team.discord_role_id` on at least one team +
  `Matchday.pick_deadline` + `scheduled_weekend` on at least one matchday
  + `Season.discord_race_results_thread_id` + `discord_standings_thread_id`
  on the season. Run export → wipe → import → assert byte-equality on every
  seeded value.

- **One per-field regression-fence test method per V8-V15 column** — 13 total
  (per D-15). Each method seeds a single non-null value, round-trips, asserts
  the value survives. Mechanical and easy to write; pays back enormously the
  first time someone refactors a Restorer and forgets one column.

- **v1-backup synthetic fixture** — `src/test/resources/backup-fixtures/v1-pre-discord.zip`
  with manifest.json `{"schema_version": 1, "exported_at": "...", "entities": [...24 entries...]}`
  and 24 `data/*.json` files (can be a stripped-down minimal Saison-2020 fixture).
  Planner produces this once, checks it in. Drives the D-17 lenient-acceptance test.

- **Wire-Contract paragraph update template (PROJECT.md)** — paragraph 4
  ("The 24-entity scope") gets a sub-paragraph: "v1.13 Phase 101 raised the
  scope to 26 entities by adding `DiscordGlobalConfig` and `DiscordPost`
  via package-filter expansion to `org.ctc.discord.model.*`.
  `BackupSchema.SCHEMA_VERSION` is now `2`; the importer accepts
  `schema_version IN (1, 2)` (lenient v1 acceptance for pre-v1.13
  operator backups, which restore with empty Discord sections)."

- **DOCS-02 update** — add a short § to
  `docs/operations/discord-integration.md` titled "Backup & Restore
  semantics", documenting (a) single-guild operation, (b) webhook_token
  secrecy implication for backup ZIPs (operator-action: encrypt at rest
  or restrict access), (c) v1-backup compatibility note.

</specifics>

<deferred>
## Deferred Ideas

### v1.14+ backlog

- **Cross-guild restore tooling** — admin-UI toggle on import-preview
  "Reset Discord state on import" (default false). Wipes V8-V15 columns
  on Match/Team/Matchday/Season + clears `DiscordPost` + resets
  `DiscordGlobalConfig` to default. Skipped by D-07 (preserve-as-is
  policy); revisit if a v1.14+ deployment model moves to multi-guild.

- **`@Backupable` marker annotation** — explicit opt-in instead of
  package-filter inclusion. Rejected by D-08 (D-15 structural philosophy
  preserved). If future entities need finer-grained control (e.g.,
  some `org.ctc.discord.model.*` entity should NOT round-trip), this is
  the entry point.

- **`data_import_audit` inclusion** — Phase 72 D-15 explicitly excluded
  it as operational metadata. Phase 101 keeps that exclusion; the
  package-filter expansion is narrow to `org.ctc.discord.model.*` only.
  If a future operator requirement surfaces ("we want to see import
  history after a restore"), this is the entry point.

- **Inline v1 → v2 migration code branch** — instead of D-11's "v1
  imports leave Discord empty", the importer could synthesise empty
  Discord-section JSON for v1 backups. Rejected for Phase 101 as
  unnecessary code complexity for marginal benefit (operator gets the
  same end state either way).

- **Encrypted backup ZIP at rest** — webhook_token / role_id /
  channel_id are PII-equivalent secrets after Phase 101. Operator-side
  mitigation today is filesystem-level access control. A future phase
  could add app-level ZIP encryption with operator-supplied passphrase
  on `/admin/backup/export`.

### Out-of-scope for Phase 101 (covered by other phases / not project scope)

- **V8-V15 migration rewrites** — CLAUDE.md "Do Not Modify Flyway
  Migrations" forbids editing the V8-V15 files. Restorer INSERT_SQL is
  the lever.

- **`org.ctc.backup.audit.DataImportAudit` schema** — Phase 72 D-15
  permanent exclusion stands. Phase 101 does not change it.

- **Multi-guild support** — DISC-FUTURE-04, out-of-scope for v1.13 and
  v1.14 unless deployment model changes.

</deferred>

---

*Phase: 101-backup-restore-covers-discord-schema-v8-v15*
*Context gathered: 2026-05-26*
