---
phase: 94
plan: 01
slug: team-roles-match-channel-lifecycle
status: shipped
shipped: 2026-05-21
requirement: CHAN-01
---

# Plan 94-01 — CHAN-01 Team Discord role mapping + DiscordRoleCache + admin.css `.discord-actions` cluster

Closed CHAN-01 inline on `gsd/v1.13-discord-integration` in 3 coherent slices per the plan's `<must_haves>`: Flyway V9 schema + `DiscordSnowflake` shared constant + `Team.discordRoleId` (slice 1) → `TeamForm` + `TeamManagementService.save()` signature extension + `DiscordConfigForm.currentMatchCategoryId` (slice 2) → `DiscordRoleCache` clone of `DiscordEmojiCache` + `DiscordConfigController.refreshRolesCache()` wiring + `team-form.html` cache-aware dropdown + `admin.css .discord-actions` responsive cluster (slice 3). The `.discord-actions` cluster doubles as the UAT-03 mobile-overflow closure on `/admin/discord-config` and is reused by Plans 94-02 + 94-03.

No new production dependencies. Single new Flyway migration **V9** bundles both `ALTER TABLE` statements per D-03 (`teams.discord_role_id VARCHAR(32)` NULLABLE + `discord_global_config.current_match_category_id VARCHAR(32) NOT NULL DEFAULT ''`). The shared `DiscordSnowflake.PATTERN` + `MESSAGE` constants replace 5 inline `@Pattern` sites in `DiscordConfigForm` and seed all future Discord-ID validation across 94-02/94-03/95-*.

## Files modified

| File | Change |
|------|--------|
| `src/main/resources/db/migration/V9__add_discord_team_role_and_current_match_category.sql` | New Flyway migration: 2 bundled `ALTER TABLE` statements (`teams.discord_role_id VARCHAR(32)` NULLABLE per RESEARCH Pitfall 5; `discord_global_config.current_match_category_id VARCHAR(32) NOT NULL DEFAULT ''` mirroring V8 string-field convention). H2 + MariaDB compatible, no CHECK / LONGTEXT / `@Lob`. |
| `src/main/java/org/ctc/discord/dto/DiscordSnowflake.java` | New `final` class with private ctor: `PATTERN = "^$|^\\d{17,20}$"` + `MESSAGE = "Must be a Discord snowflake (17-20 digits) or empty"`. D-14 decision — shared contract for Discord-ID validation across all forms. |
| `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` | 5 inline `@Pattern(SNOWFLAKE_REGEX)` sites refactored to `@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)`. Local `SNOWFLAKE_REGEX` + `SNOWFLAKE_MESSAGE` constants removed. `WEBHOOK_REGEX` + `WEBHOOK_MESSAGE` retained (URL-specific). New 7th field `currentMatchCategoryId` (D-02) with same shared `@Pattern` + default `= ""`. |
| `src/main/java/org/ctc/domain/model/Team.java` | New nullable `String discordRoleId` field with `@Column(length = 32)` placed after `accentColor`, before relationship fields. No `@ToString.Exclude` (role IDs are non-secret per D-01). |
| `src/main/java/org/ctc/admin/dto/TeamForm.java` | New `discordRoleId` field with `@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)`. |
| `src/main/java/org/ctc/admin/service/TeamManagementService.java` | `save(...)` positional signature extended with `String discordRoleId` (7th arg). New `private static String blankToNull(String)` helper coerces empty `""` to `null` before persistence. `discordRoleId` is NOT propagated to sub-teams per CONTEXT § Domain. |
| `src/main/java/org/ctc/admin/controller/TeamController.java` | New `DiscordRoleCache` dependency. `create`, `edit`, and the `result.hasErrors()` re-render path now inject `discordRoles` model attribute (cache snapshot). `edit` populates `form.discordRoleId` from the team. `save` threads `form.getDiscordRoleId()` to `TeamManagementService.save(...)` as the 7th positional arg. |
| `src/main/java/org/ctc/discord/DiscordRoleCache.java` | New `@Slf4j @Component @RequiredArgsConstructor` class in `org.ctc.discord`: `ConcurrentHashMap<String, CachedEntry<Role>>`, 60-min `TTL = Duration.ofMinutes(60)`, `Clock` injected (mutable-clock pattern). API: `Map<String, Role> snapshot()` returns `Map.copyOf` of valid entries; `Role get(String)` returns `null` if expired/missing; `int refresh(List<Role>)` bulk-replaces store and DEBUG-logs refreshed count. Structural twin of `DiscordEmojiCache`. |
| `src/main/java/org/ctc/discord/web/DiscordConfigController.java` | `refreshRolesCache()` extended: after `fetchGuildRoles()` it now calls `discordRoleCache.refresh(roles)` and reports the cache count in the flash success message (single button-click fills the cache that Team-Form reads per D-05). Typed-catch on `DiscordApiException` → `applyErrorFlash` unchanged. New `DiscordRoleCache` dependency. |
| `src/main/resources/templates/admin/team-form.html` | New Discord Role block between Brand Colors and the actions row. Two mutually-exclusive `<div class="form-group">` blocks: (a) `th:if` cache-warm → `.searchable-dropdown` driven by existing `searchable-dropdown.js` contract (`.dropdown-input` + `<input type="hidden" th:field="*{discordRoleId}">` + `.dropdown-list` with `.dropdown-item` data-id/data-label entries); (b) cache-empty fallback → plain `<input type="text" th:field="*{discordRoleId}" pattern="^\d{17,20}$">` + `.badge-warning` linking to `/admin/discord-config`. Both blocks share `<span th:errors="*{discordRoleId}" class="error-badge error-badge--auth">`. `data-testid` hooks (`discord-role-dropdown`, `discord-role-input`) carry E2E selectors. |
| `src/main/resources/templates/admin/discord-config.html` | New 7th form-row for `currentMatchCategoryId` (label + snowflake-validated input + "not configured" `.badge-warning` when empty + `error-badge`). Mirrors existing `guildId` row shape. |
| `src/main/resources/static/admin/css/admin.css` | New `.discord-actions` BEM cluster: `display: flex; flex-wrap: wrap; gap` defaults + `@media (max-width: 640px)` rule that stacks the button bar into a single column. Reused by Plans 94-02 + 94-03. Closes UAT-03 mobile-overflow debt on `/admin/discord-config`. No inline styles per CLAUDE.md "No Inline Styles on Buttons". |

## Tests added (5 new classes + 3 IT extensions)

| Class | Type | Methods | Coverage |
|-------|------|---------|----------|
| `DiscordSnowflakeTest` | unit | 2 | `PATTERN` accepts 17-20 digits + empty string; rejects shorter/longer/non-numeric |
| `TeamFormSnowflakeValidationTest` | unit | 4 | DTO-level `@Pattern` violations surface via `Validator` (valid snowflake, blank, 16-digit reject, alphanumeric reject) |
| `DiscordRoleCacheTest` | unit | 5 | MutableClock pattern ported from `DiscordEmojiCacheTest`; covers `refresh` count, `snapshot` copy semantics, `get` hit/miss, TTL expiry, `Clock` injection |
| `TeamRepositoryDiscordRoleIdIT` | integration | 3 | 3 round-trips: snowflake persist + null persist + null-update |
| `TeamFormDiscordRoleDropdownE2ETest` | E2E (Playwright) | 3 | Cold-cache plain-text + warning badge + dropdown absent; warm-cache dropdown + 2 items; mobile 375×667 layout sanity. Uses WireMock + seeded `T-DR` test team per [[feedback-test-data-isolation]]. |
| `DiscordConfigControllerIT` (ext) | integration | +1 | Asserts role-cache populated after `refresh-roles-cache` POST (WireMock 2-role stub). Test pollution from leftover `guildId=123456789012345678` mutation closed via `@BeforeEach` + `@AfterEach` reset to empty defaults (separate atomic commit `b87cb14e`). |
| `DiscordGlobalConfigRepositoryIT` (ext) | integration | +1 | `currentMatchCategoryId` round-trip + same reset hygiene as above. |
| `DiscordConfigControllerTest` (ext) | unit | +1 | Constructor signature now takes `DiscordRoleCache` mock; new test stubs `roleCache.refresh(anyList())` → 2 and asserts the flash message includes the cache count. |

## Quality gates (local `./mvnw clean verify -Pe2e` on commit `5706689e`)

- BUILD SUCCESS in **10:35 min**.
- JaCoCo line coverage **89.56 % (8162/9113)** — +0.68 pp above v1.11 baseline 88.88 %.
- SpotBugs **0 BugInstances** (`No errors/warnings found`).
- `BackupSchemaGuardTest` green at `EXPORT_ORDER.length == 24` + `SCHEMA_VERSION == 1` (no new domain entities — `DiscordRoleCache` is a `@Component`, not an `@Entity`).
- Flyway V1-V8 unchanged; V9 sole new migration.
- Branch identity `gsd/v1.13-discord-integration` preserved end-to-end across all 6 plan commits.

## Threats closed / carried forward

- **T-94-01-01** (Tampering `TeamForm.discordRoleId`) — closed by `TeamFormSnowflakeValidationTest` (`@Pattern` regex contract).
- **T-94-01-02** (Tampering `TeamController.save()`) — closed by Mass-assignment-safe DTO binding inherited from Phase 29; `discordRoleId` is the only new field, threaded through positional service arg.
- **T-94-01-03** (Information Disclosure refreshRolesCache flash) — closed by existing `applyErrorFlash` with hardcoded mapper constants (T-91-02-IL invariant carried forward, no `e.getMessage()` echo).
- **T-94-01-04** (Spoofing `DiscordRoleCache.refresh()`) — accepted: role list sourced server-side from authenticated Bot-Token call, no external attacker path.
- **T-93-02 carry-forward** (webhook URL log leak) — no new webhook fields in this plan; Phase 93 logback `%replace` mask remains active.

## Decisions made during execution

- **Slice ordering** (V9 → DiscordSnowflake → Cache) chosen over single-shot mega-commit so each slice is independently verifiable + JaCoCo measurements remain actionable per slice.
- **Test pollution fix as atomic test-only commit `b87cb14e`** rather than bundled with `5706689e` — preserves single-purpose commit grain even when the surface that revealed the bug is the same UI commit.
- **`.discord-actions` cluster scope** widened from "/admin/discord-config only" (CONTEXT-stated) to "any Discord action bar" so Plans 94-02 + 94-03 inherit the responsive-wrap behavior without duplicating CSS. Validated retroactively in Plan 94-02 `MatchDetailControllerE2ETest` viewport-overflow assertion.

## Wave-pause artifacts

- Desktop 1280×800 + Mobile 375×667 screenshots saved under `.screenshots/94-01/` (gitignored locally). Mobile screenshot pins the `.discord-actions` column-stacking media query.
- Mobile-card-overflow on Team-Form gradient cards tracked separately as Phase-98 in-milestone commitment (commit `605b61e6` — `docs(v1.13)`). Out of scope for 94-01 per CONTEXT § Domain.

## Refs

- Decisions D-01, D-02, D-03, D-05, D-06, D-07, D-08, D-09, D-14
- Plan tasks 1-5 (RED stubs → V9 + entity → DTO + service → cache + wiring → UI surfaces)
- Closes UAT-03 mobile-overflow debt on `/admin/discord-config` (Phase 93 follow-up)
- Threat model T-94-01-01..04 (Plan 94-01 STRIDE register)
