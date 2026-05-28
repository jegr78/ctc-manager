# Phase 94: Team Roles + Match Channel Lifecycle - Pattern Map

**Mapped:** 2026-05-21
**Files analyzed:** 51 (15 production + 4 SQL/template config + 32 new/extended templates & tests)
**Analogs found:** 47 / 51 (4 marked "extension-of-existing" — Match-Detail page, Match-Form-Edit, ArchiveCategory record, V9 bundled migration)

## Executive Summary

Phase 94 is overwhelmingly a **clone-and-extend** phase whose closest analogs are the Phase 93 INFRA artifacts shipped just upstream. Concretely:

- **`DiscordRoleCache`** is a verbatim structural twin of `DiscordEmojiCache` (Phase 93 INFRA-01) — same `ConcurrentHashMap<String, CachedEntry<V>>` shape, same `Clock`-injected 60-min TTL, same `refresh(Map)` + `lookup` API. Only the value-type changes (`String` long-form emoji literal → `Role` record).
- **`DiscordChannelService.createMatchChannel`** has NO existing analog in the codebase (first transactional Discord-orchestration service). It is built directly from Phase 93 `DiscordRestClient.createChannel/modifyChannel` + `DiscordApiExceptionMapper` constants + Phase 94 RESEARCH.md § "DiscordChannelService Flow + Audit Architecture" (D-04 contract). The closest internal precedent for the typed-catch + sealed-permit dispatch is `DiscordConfigController.applyErrorFlash` (Phase 93 INFRA-03 — verified at `web/DiscordConfigController.java:136-147`).
- **`DiscordPermissions` constants** is a static-only utility class with no analog (Java records / pure-static utility classes are new for the Discord package). Phase 93 `DiscordTimestamps` provides the package-style precedent (lives at the root `org.ctc.discord` package, NOT under `dto/`).
- **`DiscordCategoryResolver`** is a new regex-based filter/sort service; closest analog for "service that hits a Discord REST endpoint, transforms response in-process, returns DTO list" is `DiscordEmojiCache.refresh` (calls `restClient.fetchGuildEmojis` then bulk-replaces map). Pattern is structurally similar but the resolver does not cache.
- **`MatchForm`** is a verbatim Lombok port of `MatchdayForm` shape (`@Getter @Setter @NoArgsConstructor` + Jakarta validation), with 5 new Discord-scheduling fields and `discordSnowflake.PATTERN` reuse.
- **`MatchController` extensions** mirror `TeamController.edit` (Model injection of dropdown source) + `DriverSheetImportController` (typed-catch sealed `DiscordApiException` flash dispatch) + Phase 93 `DiscordConfigController` (typed-catch + `applyErrorFlash` helper).
- **`match-detail.html`** has the closest precedent in `team-detail.html` (per-entity detail page with toolbar of action buttons + sub-cards). NEW elements: Discord Actions panel (Create-Channel button + Move-to-Archive modal trigger) + archive modal via established `<div class="modal-overlay">` pattern (`season-detail.html:96, 156` — verified zero `<dialog>` precedent).
- **`match-form-edit.html`** clones `match-form.html` (which is the CREATE flow) with the form-action swapped to `/save-edit`, hidden `id` field, and 5 new Discord fields. Per CLAUDE.md § Backward Compatibility the legacy CREATE URL stays unchanged.
- **`V9` + `V10` migrations** follow `V8__discord_global_config.sql` shape verbatim (snake_case columns, H2 + MariaDB compatible, no CHECK, no LONGTEXT, VARCHAR(32) snowflakes, `NOT NULL DEFAULT ''` for non-nullable string fields).
- **All 23 new test classes** follow established Phase 93 patterns: Mockito-only unit tests untagged + Given-When-Then naming; WireMock-backed ITs tagged `@Tag("integration")` with `@RegisterExtension static WireMockExtension wm + @DynamicPropertySource`; Playwright E2E tagged `@Tag("e2e")` extending `PlaywrightConfig` in `org.ctc.e2e.discord` package (verified existing `DiscordConfigPageE2ETest.java` precedent).

The four extension-of-existing items have NO new analog because:
1. **`match-detail.html`** — `team-detail.html` is the role-match anchor, but Phase 94 introduces Discord-specific Actions-panel composition; mapping notes the components that ARE re-used (toolbar, sub-card pattern, badge classes).
2. **`match-form-edit.html`** — derived from `match-form.html` (CREATE flow), but with the edit-mode `id` binding, and additional 5-field card; the planner duplicates rather than parameterizes per [[feedback-grep-all-usages]] discipline (CREATE URL must stay stable).
3. **`ArchiveCategory` record** — new domain DTO (no codebase analog); RESEARCH.md § "Architecture Patterns" specifies the 4-field record shape (id, name, num, currentChannelCount).
4. **V9 bundled migration** — V8 is a single-table migration; V9 bundles TWO ALTER TABLEs (per D-03 — cohesive schema delta). No existing V*+ migration mixes table-scopes; mapping notes the doubled `ALTER TABLE` block follows V8's shape twice over.

## File Classification

### Plan 94-01 — CHAN-01 Team Role Mapping + DiscordRoleCache + UI Polish Base

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/resources/db/migration/V9__add_discord_team_role_and_current_match_category.sql` | migration | DDL ALTER TABLE × 2 | `src/main/resources/db/migration/V8__discord_global_config.sql` | role-match (V8 = single CREATE; V9 = two ALTERs in one file per D-03) |
| `src/main/java/org/ctc/domain/model/Team.java` (MODIFIED, +discordRoleId field) | entity (extension) | n/a (entity field) | `src/main/java/org/ctc/domain/model/Team.java` (existing field shape) | **exact** — adds 7th field to existing Lombok-annotated entity |
| `src/main/java/org/ctc/discord/dto/DiscordSnowflake.java` (NEW shared constant class) | dto (constant utility) | n/a (pure-static) | RESEARCH.md § "Don't Hand-Roll" + CONTEXT D-14 | **no analog** — first shared validation-constant in `org.ctc.discord.dto` |
| `src/main/java/org/ctc/admin/dto/TeamForm.java` (MODIFIED, +discordRoleId field) | dto (extension) | form-bind | `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` (`@Pattern` with `DiscordSnowflake.PATTERN`) | **exact** |
| `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` (MODIFIED, REFACTOR 5 `@Pattern` to constant + add `currentMatchCategoryId` field) | dto (refactor + extension) | form-bind | `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` (self) | **exact** (refactor own file) |
| `src/main/java/org/ctc/domain/service/TeamManagementService.java` (MODIFIED, +discordRoleId parameter on save) | service (signature extension) | CRUD | `src/main/java/org/ctc/domain/service/TeamManagementService.java:221-241` (existing save method) | **exact** — extend positional-arg signature |
| `src/main/java/org/ctc/admin/controller/TeamController.java` (MODIFIED, edit() injects discordRoles, save() threads discordRoleId) | controller (extension) | request-response | `src/main/java/org/ctc/admin/controller/TeamController.java:48-77` (existing edit/save) | **exact** |
| `src/main/java/org/ctc/discord/DiscordRoleCache.java` (NEW) | utility (cache) | cache | `src/main/java/org/ctc/discord/DiscordEmojiCache.java` (full file) | **exact** structural twin |
| `src/main/java/org/ctc/discord/web/DiscordConfigController.java` (MODIFIED, refreshRolesCache() also populates roleCache) | controller (extension) | request-response + cache-write | `src/main/java/org/ctc/discord/web/DiscordConfigController.java:97-114` (existing refreshRolesCache) | **exact** |
| `src/main/resources/templates/admin/team-form.html` (MODIFIED, +discordRoleId field with searchable-dropdown fallback) | template (extension) | view | `src/main/resources/templates/admin/race-form.html:38, 66` (searchable-dropdown pattern) + RESEARCH § "Searchable-Dropdown JS Contract" | **exact** |
| `src/main/resources/templates/admin/discord-config.html` (MODIFIED, +currentMatchCategoryId form-row) | template (extension) | view | `src/main/resources/templates/admin/discord-config.html` (existing 6-field form) | **exact** (add 7th form-row in same shape) |
| `src/main/resources/static/admin/css/admin.css` (MODIFIED, +.discord-actions cluster responsive-wrap) | stylesheet | n/a | existing `.error-badge--*` / `.badge-warning` palette (lines 357–375) | role-match (BEM utility cluster) |

#### Plan 94-01 — Tests

| New Test Class | Type | @Tag | Closest Analog |
|----------------|------|------|----------------|
| `src/test/java/org/ctc/admin/dto/TeamFormSnowflakeValidationTest.java` | unit | untagged | `src/test/java/org/ctc/discord/dto/DiscordConfigFormTest.java` |
| `src/test/java/org/ctc/discord/dto/DiscordSnowflakeTest.java` | unit | untagged | `src/test/java/org/ctc/discord/dto/DiscordConfigFormTest.java` |
| `src/test/java/org/ctc/domain/repository/TeamRepositoryDiscordRoleIdIT.java` | IT | `@Tag("integration")` | `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigRepositoryIT.java` |
| `src/test/java/org/ctc/discord/DiscordRoleCacheTest.java` | unit | untagged | `src/test/java/org/ctc/discord/DiscordEmojiCacheTest.java` (full file — verbatim port) |
| `src/test/java/org/ctc/discord/web/DiscordConfigControllerIT.java` (EXTEND existing) | IT | `@Tag("integration")` | self (extend with refreshRolesCache → roleCache assertion) |
| `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigRepositoryIT.java` (EXTEND existing) | IT | `@Tag("integration")` | self (extend with currentMatchCategoryId assertion) |
| `src/test/java/org/ctc/e2e/discord/TeamFormDiscordRoleDropdownE2ETest.java` | E2E | `@Tag("e2e")` | `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` (full file) |

### Plan 94-02 — CHAN-02 Match-Detail Page + Channel Creation Service + Permission Audit

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/resources/db/migration/V10__add_matches_discord_and_scheduling_fields.sql` | migration | DDL ALTER TABLE (7 columns) | `src/main/resources/db/migration/V8__discord_global_config.sql` | role-match (V8 shape applied to 7 ALTER COLUMN statements; matches table extends pattern) |
| `src/main/java/org/ctc/domain/model/Match.java` (MODIFIED, +7 fields, +@ToString.Exclude on webhookUrl) | entity (extension) | n/a (entity fields) | `src/main/java/org/ctc/domain/model/Match.java` + `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` (`@ToString(exclude=…)` discipline) | **exact** |
| `src/main/java/org/ctc/admin/dto/MatchForm.java` (NEW) | dto | form-bind | `src/main/java/org/ctc/admin/dto/MatchdayForm.java` | **exact** structural template |
| `src/main/java/org/ctc/discord/DiscordPermissions.java` (NEW — package-cohesion: `org.ctc.discord`) | utility (pure-static constants) | n/a | `src/main/java/org/ctc/discord/DiscordTimestamps.java` (package-layout precedent) | role-match (static-only utility) |
| `src/main/java/org/ctc/discord/dto/PermissionOverwrite.java` (NEW record) | dto (record) | request-response | `src/main/java/org/ctc/discord/dto/Channel.java` (record + `@JsonProperty`) | **exact** |
| `src/main/java/org/ctc/discord/dto/Webhook.java` (NEW record) | dto (record) | response | `src/main/java/org/ctc/discord/dto/Channel.java` | **exact** |
| `src/main/java/org/ctc/discord/dto/ChannelCreateRequest.java` (MODIFIED, +permission_overwrites field + `@JsonInclude(NON_NULL)` + convenience constructor) | dto (extension) | request | `src/main/java/org/ctc/discord/dto/ChannelCreateRequest.java` (self) + RESEARCH § "Channel Record Extension Strategy" | **exact** (self-extension) |
| `src/main/java/org/ctc/discord/dto/Channel.java` (MODIFIED, +permission_overwrites optional field + convenience constructor) | dto (extension) | response | `src/main/java/org/ctc/discord/dto/Channel.java` (self) + RESEARCH § "Channel Record Extension Strategy" | **exact** (self-extension) |
| `src/main/java/org/ctc/discord/DiscordRestClient.java` (MODIFIED, +createWebhook +fetchChannel +deleteChannel) | api-client (extension) | request-response | `src/main/java/org/ctc/discord/DiscordRestClient.java:68-91` (createChannel/modifyChannel/listChannels) + RESEARCH § "RestClient DELETE Helper Shape" | **exact** |
| `src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java` (MODIFIED, +AUDIT_FAIL_MESSAGE constant) | exception (extension) | n/a | `src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java` (existing constants) | **exact** |
| `src/main/java/org/ctc/discord/service/DiscordChannelService.java` (NEW) | service (transactional orchestrator) | request-response + DB write | RESEARCH § "DiscordChannelService Flow + Audit Architecture" + `src/main/java/org/ctc/discord/web/DiscordConfigController.java:136-147` (applyErrorFlash pattern) | **no analog** — first Discord-write transactional orchestrator |
| `src/main/java/org/ctc/admin/controller/MatchController.java` (MODIFIED, +5 endpoints) | controller (extension) | request-response + flash | `src/main/java/org/ctc/admin/controller/MatchController.java` (existing 4 endpoints) + `src/main/java/org/ctc/discord/web/DiscordConfigController.java` (typed-catch + applyErrorFlash) | **exact** |
| `src/main/java/org/ctc/domain/service/MatchService.java` (MODIFIED, +`updateDiscordFields(id, MatchForm)` or similar for save-edit) | service (extension) | CRUD | `src/main/java/org/ctc/domain/service/TeamManagementService.java:221-241` (save method shape) | **exact** |
| `src/main/resources/templates/admin/match-detail.html` (NEW) | template | view | `src/main/resources/templates/admin/team-detail.html` (per-entity detail page with toolbar + sub-cards) | role-match (anchor for layout; Discord Actions panel composition is new) |
| `src/main/resources/templates/admin/match-form-edit.html` (NEW) | template | view | `src/main/resources/templates/admin/match-form.html` (CREATE flow shape) | role-match (clone with id hidden + 5 new fields + form-action `/save-edit`) |
| `src/main/resources/templates/admin/matchday-detail.html` (MODIFIED, +"→ Detail" link per match-row) | template (extension) | view | existing match-row structure at `matchday-detail.html:39-70` | **exact** |
| `src/main/resources/static/admin/css/admin.css` (MODIFIED, +.discord-actions--panel rules if Plan 94-01 cluster needs extension) | stylesheet | n/a | existing `.discord-actions` cluster from Plan 94-01 D-06 | self-extension |

#### Plan 94-02 — Tests

| New Test Class | Type | @Tag | Closest Analog |
|----------------|------|------|----------------|
| `src/test/java/org/ctc/admin/dto/MatchFormValidationTest.java` | unit | untagged | `src/test/java/org/ctc/discord/dto/DiscordConfigFormTest.java` |
| `src/test/java/org/ctc/domain/repository/MatchRepositoryDiscordFieldsIT.java` | IT | `@Tag("integration")` | `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigRepositoryIT.java` |
| `src/test/java/org/ctc/domain/model/MatchToStringTest.java` | unit | untagged | `src/test/java/org/ctc/discord/model/DiscordGlobalConfigToStringTest.java` (verbatim port) |
| `src/test/java/org/ctc/discord/DiscordPermissionsTest.java` | unit | untagged | RESEARCH § "Permission Bitmask Reference" assertion sketch |
| `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java` | IT (WireMock) | `@Tag("integration")` | `src/test/java/org/ctc/discord/DiscordRestClientIT.java` (WireMock scaffold) |
| `src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java` | IT (WireMock) | `@Tag("integration")` | `src/test/java/org/ctc/discord/DiscordRestClientIT.java` |
| `src/test/java/org/ctc/discord/service/DiscordChannelServiceCleanupFailIT.java` | IT (WireMock) | `@Tag("integration")` | `src/test/java/org/ctc/discord/DiscordRestClientIT.java` |
| `src/test/java/org/ctc/discord/DiscordRestClientIT.java` (EXTEND existing) | IT (WireMock) | `@Tag("integration")` | self (add tests for createWebhook/fetchChannel/deleteChannel) |
| `src/test/java/org/ctc/admin/controller/MatchControllerCreateChannelErrorCategoryTest.java` | unit (Mockito) | untagged | `src/test/java/org/ctc/discord/web/DiscordConfigControllerErrorCategoryTest.java` |
| `src/test/java/org/ctc/discord/service/MatchEditFormIT.java` (or `src/test/java/org/ctc/admin/controller/MatchEditFormIT.java` — planner discretion on package) | IT | `@Tag("integration")` | `src/test/java/org/ctc/discord/web/DiscordConfigControllerIT.java` (MockMvc round-trip) |
| `src/test/java/org/ctc/e2e/discord/MatchDetailControllerE2ETest.java` | E2E | `@Tag("e2e")` | `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` |

### Plan 94-03 — CHAN-03 Archive Modal + Category Resolver

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/discord/dto/ArchiveCategory.java` (NEW record) | dto (record) | view-data | `src/main/java/org/ctc/discord/dto/Channel.java` (record shape) | **no analog** — first domain DTO for archive resolution; record shape is structural twin |
| `src/main/java/org/ctc/discord/service/DiscordCategoryResolver.java` (NEW) | service | request-response | `src/main/java/org/ctc/discord/DiscordRestClient.java:86-91` (listChannels caller) + RESEARCH § "DiscordCategoryResolver" architecture | **no analog** — first regex/filter service in `org.ctc.discord.service` |
| `src/main/java/org/ctc/admin/controller/MatchController.java` (MODIFIED, +POST /{id}/move-to-archive endpoint) | controller (extension) | request-response + flash | `src/main/java/org/ctc/admin/controller/MatchController.java` (self, after 94-02 extension) + `src/main/java/org/ctc/discord/web/DiscordConfigController.java` (typed-catch flash) | **exact** |
| `src/main/resources/templates/admin/match-detail.html` (MODIFIED, +Archive Modal `<div class="modal-overlay">`) | template (extension) | view | `src/main/resources/templates/admin/season-detail.html:96, 156` (modal-overlay pattern — verified TWO usages) | **exact** pattern reuse |
| `src/main/resources/static/admin/css/admin.css` — `.error-badge--category-full` (VERIFY existing; per RESEARCH Open Question 3 it already shipped Phase 93 at admin.css:375) | stylesheet | n/a | existing line in admin.css | n/a — no edit needed |

#### Plan 94-03 — Tests

| New Test Class | Type | @Tag | Closest Analog |
|----------------|------|------|----------------|
| `src/test/java/org/ctc/discord/service/DiscordCategoryResolverTest.java` | unit (Mockito) | untagged | `src/test/java/org/ctc/discord/DiscordEmojiCacheTest.java` (unit-test shape) |
| `src/test/java/org/ctc/discord/service/DiscordCategoryResolverWireMockIT.java` | IT (WireMock) | `@Tag("integration")` | `src/test/java/org/ctc/discord/DiscordRestClientIT.java` |
| `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java` | IT (WireMock) | `@Tag("integration")` | `src/test/java/org/ctc/discord/DiscordRestClientIT.java` |
| `src/test/java/org/ctc/admin/controller/MatchControllerMoveToArchiveErrorCategoryTest.java` | unit (Mockito) | untagged | `src/test/java/org/ctc/discord/web/DiscordConfigControllerErrorCategoryTest.java` |
| `src/test/java/org/ctc/e2e/discord/ArchiveModalE2ETest.java` | E2E | `@Tag("e2e")` | `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` |

---

## Pattern Assignments

### Plan 94-01 — CHAN-01 Team Role Mapping + DiscordRoleCache + UI Polish Base

#### `src/main/resources/db/migration/V9__add_discord_team_role_and_current_match_category.sql` (NEW migration)

**Closest analog:** `src/main/resources/db/migration/V8__discord_global_config.sql` (full file)
**Match quality:** ROLE-MATCH (V8 was CREATE TABLE + seed INSERT; V9 is two ALTER TABLEs per D-03)

**Analog header pattern** (verbatim from V8):

```sql
-- Compatible with H2 2.x and MariaDB 10.7+ — no CHECK constraints, no LONGTEXT
-- (both drift between engines). DiscordConfigForm Jakarta-Validation owns the
-- snowflake/webhook regex contract instead of the DB schema.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").
```

**Adaptation:**

```sql
-- Phase 94 D-03: add Discord team-role mapping AND the operator-managed
-- current-match-category slot. Two ALTER TABLEs bundled in one migration so
-- the schema-delta is cohesive with CHAN-01 + D-02 carrier slot.
-- Compatible with H2 2.x and MariaDB 10.7+ — no CHECK, no LONGTEXT.
-- DO NOT mutate this file after release.

ALTER TABLE teams
    ADD COLUMN discord_role_id VARCHAR(32);

ALTER TABLE discord_global_config
    ADD COLUMN current_match_category_id VARCHAR(32) NOT NULL DEFAULT '';
```

**Notes:**
- `teams.discord_role_id` stays NULLABLE per CONTEXT D-03 + RESEARCH Pitfall 5 (existing rows must not violate NOT NULL constraint).
- `discord_global_config.current_match_category_id NOT NULL DEFAULT ''` — V8 seed row gets this column auto-defaulted to empty string.
- No new indexes (snowflake IDs aren't FKs).

---

#### `src/main/java/org/ctc/domain/model/Team.java` (MODIFIED — add `discordRoleId` field)

**Closest analog:** existing `Team.java` lines 30-39 (`primaryColor`, `secondaryColor`, `accentColor` — un-annotated string fields)
**Match quality:** EXACT

**Existing field shape excerpt** (`Team.java:25-39`):

```java
@NotBlank
@Column(nullable = false)
private String name;

@NotBlank
@Column(nullable = false)
private String shortName;

private String logoUrl;

private String primaryColor;

private String secondaryColor;

private String accentColor;
```

**Adaptation** — add as the 7th un-annotated String field (snowflake is nullable; OPTIONAL):

```java
@Column(length = 32)
private String discordRoleId;
```

**Notes:**
- NO `@ToString.Exclude` (role IDs are non-secret per CONTEXT D-01; Team `@ToString(exclude=…)` already excludes lazy collections, no addition needed).
- NO `@NotBlank` (operator can clear field per REQ CHAN-01 acceptance text).
- Field placement: at the end of the un-annotated-String cluster (after `accentColor`), before the relationship fields (`parentTeam`, `subTeams`).

---

#### `src/main/java/org/ctc/discord/dto/DiscordSnowflake.java` (NEW shared constant class — D-14)

**Closest analog:** none directly; RESEARCH § "Don't Hand-Roll" + CONTEXT D-14 prescribe the file shape.
**Match quality:** NO ANALOG — first shared validation-constant utility in the Discord package.

**Source template** (from CONTEXT D-14 verbatim):

```java
package org.ctc.discord.dto;

public final class DiscordSnowflake {
    public static final String PATTERN = "^$|^\\d{17,20}$";
    public static final String MESSAGE = "Must be a Discord snowflake (17–20 digits) or empty";

    private DiscordSnowflake() {}
}
```

**Notes:**
- `final class` + private constructor → static-only utility.
- Lives in `org.ctc.discord.dto` per D-14 (sibling to other DTOs).
- `DiscordConfigForm` refactored to consume `DiscordSnowflake.PATTERN` + `DiscordSnowflake.MESSAGE` (5 `@Pattern` sites; verify all replaced).
- `TeamForm` (Phase 94 CHAN-01), future `MatchForm` snowflake fields (if needed), future `SeasonForm` (Phase 96) all reuse.

---

#### `src/main/java/org/ctc/admin/dto/TeamForm.java` (MODIFIED — add `discordRoleId` field)

**Closest analog:** `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java:20-21` (`@Pattern`-validated snowflake field shape)
**Match quality:** EXACT

**Analog excerpt** (`DiscordConfigForm.java:20-21`):

```java
@Pattern(regexp = SNOWFLAKE_REGEX, message = SNOWFLAKE_MESSAGE)
private String guildId = "";
```

**Adaptation for `TeamForm` after D-14 constant extraction:**

```java
import org.ctc.discord.dto.DiscordSnowflake;
// ...

@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
private String discordRoleId;
```

**Notes:**
- Field follows existing `TeamForm` shape — Lombok `@Getter @Setter @NoArgsConstructor` (already on the class at `TeamForm.java:9-12`).
- Default value: NO `= ""` initialization (mirrors existing `TeamForm.primaryColor` etc. — null is acceptable; the `@Pattern` regex permits empty string via `^$|...`).
- Position: append after `accentColor` field.

---

#### `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` (MODIFIED — refactor 5 `@Pattern` to constant + add `currentMatchCategoryId`)

**Closest analog:** self (refactor own file)
**Match quality:** EXACT — uses D-14 shared constant on all 5 existing snowflake fields PLUS 6th new field.

**Refactored field excerpt** (replace inline regex with shared constant):

```java
import org.ctc.discord.dto.DiscordSnowflake;
// ...

@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
private String guildId = "";

@Size(max = 500)
@Pattern(regexp = WEBHOOK_REGEX, message = WEBHOOK_MESSAGE)
private String announcementWebhookUrl = "";

@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
private String raceResultsForumChannelId = "";

@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
private String standingsForumChannelId = "";

@NotBlank
@Size(max = 50)
private String vsEmojiName = "CTC";

@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
private String botApplicationId;

// Phase 94 D-02 — new 7th field for active-match-channel category slot
@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
private String currentMatchCategoryId = "";
```

**Notes:**
- Remove the 4 local `private static final String SNOWFLAKE_*` constants on the class (`DiscordConfigForm.java:15-16`); keep `WEBHOOK_REGEX` + `WEBHOOK_MESSAGE` (those are URL-specific, not shared).
- Verify the existing `DiscordConfigFormTest` still green after refactor (the externally visible behavior is unchanged).

---

#### `src/main/java/org/ctc/domain/service/TeamManagementService.java` (MODIFIED — extend `save` signature)

**Closest analog:** `src/main/java/org/ctc/domain/service/TeamManagementService.java:221-241` (existing `save` method)
**Match quality:** EXACT (positional-arg extension)

**Existing save signature** (`TeamManagementService.java:221-241`):

```java
@Transactional
public Team save(UUID id, String name, String shortName,
                 String primaryColor, String secondaryColor, String accentColor) {
    Team team;
    if (id != null) {
        team = findById(id);
        team.setName(name);
        team.setShortName(shortName);
        team.setPrimaryColor(primaryColor);
        team.setSecondaryColor(secondaryColor);
        team.setAccentColor(accentColor);
        team = teamRepository.save(team);
        propagateColorsToSubTeams(team);
    } else {
        team = new Team(name, shortName);
        team.setPrimaryColor(primaryColor);
        team.setSecondaryColor(secondaryColor);
        team.setAccentColor(accentColor);
        team = teamRepository.save(team);
    }
    return team;
}
```

**Adaptation** — add positional `String discordRoleId` parameter at the end:

```java
@Transactional
public Team save(UUID id, String name, String shortName,
                 String primaryColor, String secondaryColor, String accentColor,
                 String discordRoleId) {
    Team team;
    if (id != null) {
        team = findById(id);
        team.setName(name);
        team.setShortName(shortName);
        team.setPrimaryColor(primaryColor);
        team.setSecondaryColor(secondaryColor);
        team.setAccentColor(accentColor);
        team.setDiscordRoleId(blankToNull(discordRoleId));
        team = teamRepository.save(team);
        propagateColorsToSubTeams(team);
    } else {
        team = new Team(name, shortName);
        team.setPrimaryColor(primaryColor);
        team.setSecondaryColor(secondaryColor);
        team.setAccentColor(accentColor);
        team.setDiscordRoleId(blankToNull(discordRoleId));
        team = teamRepository.save(team);
    }
    return team;
}

private static String blankToNull(String value) {
    return (value == null || value.isBlank()) ? null : value;
}
```

**Notes:**
- `blankToNull` helper coerces empty Form-input `""` to NULL in DB (per CONTEXT D-03 — `teams.discord_role_id` is nullable).
- DO NOT propagate `discordRoleId` to sub-teams (per CONTEXT D-13 + RaceLineup-source-of-truth memory: role mapping is per-team, not inherited).
- Call-site (TeamController.save) must thread `form.getDiscordRoleId()` through.

---

#### `src/main/java/org/ctc/admin/controller/TeamController.java` (MODIFIED — inject discordRoles + thread discordRoleId)

**Closest analog:** `src/main/java/org/ctc/admin/controller/TeamController.java:48-77` (existing edit/save)
**Match quality:** EXACT

**Existing edit() shape** (`TeamController.java:48-61`):

```java
@GetMapping("/{id}/edit")
public String edit(@PathVariable UUID id, Model model) {
    var team = teamManagementService.findById(id);
    var form = new TeamForm();
    form.setId(team.getId());
    form.setName(team.getName());
    form.setShortName(team.getShortName());
    form.setPrimaryColor(team.getPrimaryColor());
    form.setSecondaryColor(team.getSecondaryColor());
    form.setAccentColor(team.getAccentColor());
    model.addAttribute("teamForm", form);
    model.addAttribute("team", team);
    return "admin/team-form";
}
```

**Adaptation** — populate form's `discordRoleId` + inject `discordRoles` Model attr from `roleCache.snapshot()`:

```java
@GetMapping("/{id}/edit")
public String edit(@PathVariable UUID id, Model model) {
    var team = teamManagementService.findById(id);
    var form = new TeamForm();
    form.setId(team.getId());
    form.setName(team.getName());
    form.setShortName(team.getShortName());
    form.setPrimaryColor(team.getPrimaryColor());
    form.setSecondaryColor(team.getSecondaryColor());
    form.setAccentColor(team.getAccentColor());
    form.setDiscordRoleId(team.getDiscordRoleId());
    model.addAttribute("teamForm", form);
    model.addAttribute("team", team);
    model.addAttribute("discordRoles", discordRoleCache.snapshot());  // D-05
    return "admin/team-form";
}
```

**Existing save() shape** (`TeamController.java:63-77`) — modify to thread `discordRoleId`:

```java
@PostMapping("/save")
public String save(@Valid @ModelAttribute("teamForm") TeamForm form, BindingResult result,
                   RedirectAttributes redirectAttributes) {
    if (result.hasErrors()) {
        return "admin/team-form";
    }
    try {
        teamManagementService.save(form.getId(), form.getName(), form.getShortName(),
                form.getPrimaryColor(), form.getSecondaryColor(), form.getAccentColor(),
                form.getDiscordRoleId());  // new positional arg
        redirectAttributes.addFlashAttribute("successMessage", "Team saved: " + form.getName());
    } catch (BusinessRuleException e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    }
    return "redirect:/admin/teams";
}
```

**Notes:**
- New dependency: `private final DiscordRoleCache discordRoleCache;` (Lombok `@RequiredArgsConstructor` auto-wires).
- The `BindingResult.hasErrors()` path must ALSO inject `discordRoles` Model attr so the form re-renders with the dropdown (else NPE). Wrap into a private helper if planner prefers.
- ALSO update `create()` (GET `/new`) — if a `discordRoles` attribute is referenced in `team-form.html`, every path that returns the template must inject it; or guard the template with `th:if="${discordRoles != null}"`.

---

#### `src/main/java/org/ctc/discord/DiscordRoleCache.java` (NEW)

**Closest analog:** `src/main/java/org/ctc/discord/DiscordEmojiCache.java` (full file)
**Match quality:** EXACT — verbatim structural twin

**Full analog file** (`DiscordEmojiCache.java:1-41`):

```java
package org.ctc.discord;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.util.CachedEntry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordEmojiCache {

    private static final Duration TTL = Duration.ofMinutes(60);

    private final Map<String, CachedEntry<String>> store = new ConcurrentHashMap<>();
    private final Clock clock;

    public String emojiFor(String shortName) {
        CachedEntry<String> entry = store.get(shortName);
        if (entry != null && entry.isValid(clock)) {
            return entry.value();
        }
        return ":" + shortName + ":";
    }

    public int refresh(Map<String, String> shortNameToTag) {
        Map<String, CachedEntry<String>> next = new HashMap<>(shortNameToTag.size());
        for (Map.Entry<String, String> e : shortNameToTag.entrySet()) {
            next.put(e.getKey(), new CachedEntry<>(e.getValue(), clock.instant().plus(TTL)));
        }
        store.clear();
        store.putAll(next);
        log.debug("Discord emoji cache refreshed with {} entries", next.size());
        return next.size();
    }
}
```

**Adaptation for `DiscordRoleCache`:**

```java
package org.ctc.discord;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.dto.Role;
import org.ctc.discord.util.CachedEntry;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordRoleCache {

    private static final Duration TTL = Duration.ofMinutes(60);

    private final Map<String, CachedEntry<Role>> store = new ConcurrentHashMap<>();
    private final Clock clock;

    /** Returns immutable snapshot keyed by Discord role-ID for Thymeleaf rendering. */
    public Map<String, Role> snapshot() {
        Map<String, Role> out = new HashMap<>(store.size());
        for (Map.Entry<String, CachedEntry<Role>> e : store.entrySet()) {
            if (e.getValue().isValid(clock)) {
                out.put(e.getKey(), e.getValue().value());
            }
        }
        return Map.copyOf(out);
    }

    public Role get(String roleId) {
        CachedEntry<Role> entry = store.get(roleId);
        return (entry != null && entry.isValid(clock)) ? entry.value() : null;
    }

    public int refresh(java.util.List<Role> roles) {
        Map<String, CachedEntry<Role>> next = new HashMap<>(roles.size());
        for (Role r : roles) {
            next.put(r.id(), new CachedEntry<>(r, clock.instant().plus(TTL)));
        }
        store.clear();
        store.putAll(next);
        log.debug("Discord role cache refreshed with {} entries", next.size());
        return next.size();
    }
}
```

**Notes:**
- Same Lombok annotations (`@Slf4j @Component @RequiredArgsConstructor` — alphabetical per CLAUDE.md).
- Same TTL constant (60 minutes).
- API surface: `snapshot()` (Map for Thymeleaf), `get(roleId)`, `refresh(List<Role>)`. Phase 93 D-05 prescribes these three methods exactly.
- Key type changes from `shortName String` (emoji case) → `roleId String` (Discord snowflake).
- Value type changes from `String` (long-form emoji literal) → `Role` (record from `org.ctc.discord.dto.Role`).
- `snapshot()` returns immutable `Map.copyOf` for template safety.

---

#### `src/main/java/org/ctc/discord/web/DiscordConfigController.java` (MODIFIED — refreshRolesCache also populates roleCache)

**Closest analog:** self at lines 97-114 (existing `refreshRolesCache` method)
**Match quality:** EXACT

**Existing shape** (`DiscordConfigController.java:97-114`):

```java
@PostMapping("/refresh-roles-cache")
public String refreshRolesCache(RedirectAttributes redirectAttributes) {
    DiscordGlobalConfig current = configService.getOrInitialize();
    String guildId = current.getGuildId();
    if (guildId == null || guildId.isBlank()) {
        redirectAttributes.addFlashAttribute("errorMessage", "Guild ID is not configured.");
        redirectAttributes.addFlashAttribute("errorCategory", "not-found");
        return REDIRECT;
    }
    try {
        int count = discordRestClient.fetchGuildRoles(guildId).size();
        redirectAttributes.addFlashAttribute(
                "successMessage", "Server roles refreshed (" + count + " entries).");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Refresh Server Roles");
    }
    return REDIRECT;
}
```

**Adaptation** — keep the fetch + ALSO populate `discordRoleCache.refresh(roles)`:

```java
@PostMapping("/refresh-roles-cache")
public String refreshRolesCache(RedirectAttributes redirectAttributes) {
    DiscordGlobalConfig current = configService.getOrInitialize();
    String guildId = current.getGuildId();
    if (guildId == null || guildId.isBlank()) {
        redirectAttributes.addFlashAttribute("errorMessage", "Guild ID is not configured.");
        redirectAttributes.addFlashAttribute("errorCategory", "not-found");
        return REDIRECT;
    }
    try {
        var roles = discordRestClient.fetchGuildRoles(guildId);  // returns List<Role>
        int count = discordRoleCache.refresh(roles);  // D-05 — single button-click fills cache
        redirectAttributes.addFlashAttribute(
                "successMessage", "Server roles refreshed (" + count + " entries).");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Refresh Server Roles");
    }
    return REDIRECT;
}
```

**Notes:**
- Add new dependency: `private final DiscordRoleCache discordRoleCache;` (auto-wired by `@RequiredArgsConstructor`).
- Existing IT (`DiscordConfigControllerIT`) needs ONE new assertion: after POST → `discordRoleCache.snapshot()` is non-empty (or mock the bean and `verify(roleCache).refresh(any())`).

---

#### `src/main/resources/templates/admin/team-form.html` (MODIFIED — add `discordRoleId` field)

**Closest analog:** `src/main/resources/templates/admin/race-form.html:38, 66` (searchable-dropdown usage) + RESEARCH § "Searchable-Dropdown JS Contract"
**Match quality:** EXACT pattern reuse

**Cache-warm template excerpt** (from RESEARCH § Searchable-Dropdown JS Contract):

```html
<div class="form-group" th:if="${discordRoles != null && !discordRoles.isEmpty()}">
    <label for="discordRoleIdDropdown">Discord Role</label>
    <div class="searchable-dropdown" id="discordRoleIdDropdown">
        <input type="text" class="dropdown-input" placeholder="Type to search Discord roles…" />
        <input type="hidden" th:field="*{discordRoleId}" />
        <div class="dropdown-list">
            <div th:each="role : ${discordRoles.values()}"
                 class="dropdown-item"
                 th:attr="data-id=${role.id()},data-label=${role.name()}"
                 th:text="${role.name()}"></div>
        </div>
    </div>
    <span th:errors="*{discordRoleId}" class="error-badge error-badge--auth"></span>
</div>
<div class="form-group" th:if="${discordRoles == null || discordRoles.isEmpty()}">
    <label for="discordRoleIdInput">Discord Role ID</label>
    <input type="text" id="discordRoleIdInput" th:field="*{discordRoleId}"
           placeholder="Snowflake (17–20 digits)" pattern="^\d{17,20}$" />
    <span class="badge-warning">
        Refresh "Server Roles Cache" on
        <a th:href="@{/admin/discord-config}">/admin/discord-config</a>
        to enable dropdown picker. Plain-text entry works as fallback.
    </span>
    <span th:errors="*{discordRoleId}" class="error-badge error-badge--auth"></span>
</div>
```

**Notes:**
- Inserted INSIDE the existing `<form th:action="@{/admin/teams/save}" th:object="${teamForm}" ...>` block (after the Brand Colors section, before `<div class="actions mt-md">`).
- `discordRoles` Model attribute is `Map<String, Role>` per controller injection — Thymeleaf iterates `.values()`.
- `searchable-dropdown.js` is already loaded globally via `admin/layout.html:102` — no extra `<script>` needed.
- `data-testid="discord-role-dropdown"` / `data-testid="discord-role-input"` recommended for the E2E test selectors.
- `pattern="^\d{17,20}$"` on the plain-text fallback is HTML5 client-side; the server-side `@Pattern(DiscordSnowflake.PATTERN)` is authoritative.

---

#### `src/main/resources/templates/admin/discord-config.html` (MODIFIED — add `currentMatchCategoryId` form-row)

**Closest analog:** self (existing 6-field form layout)
**Match quality:** EXACT (copy any existing snowflake field-row shape)

**Adaptation** — add as 7th form-row, snowflake-validated. Mirror the existing `guildId` field-row shape (label + input + error badge + `not configured` badge per D-12). Plus a `<span class="badge-warning">` "not configured" indicator when value is empty.

---

#### `src/main/resources/static/admin/css/admin.css` (MODIFIED — `.discord-actions` responsive-wrap cluster)

**Closest analog:** existing `.error-badge--*` palette at lines 357-375
**Match quality:** role-match (BEM utility cluster — new responsive-wrap rules)

**Adaptation** — add a `.discord-actions` cluster (per D-06):

```css
/* Phase 94 D-06 — Discord-actions responsive layout (fixes UAT-03 mobile overflow on /admin/discord-config). */
.discord-actions {
    display: flex;
    flex-wrap: wrap;
    gap: var(--gap-sm);
    align-items: center;
}

.discord-actions .inline-form,
.discord-actions form {
    margin: 0;
}

.discord-actions .btn {
    min-height: 32px;
}

@media (max-width: 640px) {
    .discord-actions {
        flex-direction: column;
        align-items: stretch;
    }
    .discord-actions .btn {
        width: 100%;
    }
}
```

**Notes:**
- Re-used by `team-form.html` `discordRoleId` field's button row (if any) AND Phase 94-02 `match-detail.html` Discord Actions panel.
- Hover/wrap behavior must keep buttons inside the parent card on mobile 375×667 (D-06 visual sweep gates this on `/admin/discord-config` UAT-03 re-screenshot).

---

#### Plan 94-01 Tests — `DiscordRoleCacheTest` (NEW, untagged unit)

**Closest analog:** `src/test/java/org/ctc/discord/DiscordEmojiCacheTest.java` (full file — verbatim port)
**Match quality:** EXACT structural twin

**Full analog template** (DiscordEmojiCacheTest.java — adapt naming + value-type):

- Constants: `private static final Instant T0 = Instant.parse("2026-05-21T10:00:00Z");`
- `MutableClock` inner class — port verbatim (Phase 93 DiscordEmojiCacheTest:93-118).
- Tests:
  - `givenRefreshedCache_whenSnapshot_thenContainsRoleEntries()` — refresh with `List.of(new Role("12345", "Admin", 3), …)`; snapshot returns Map.
  - `givenCacheMiss_whenGet_thenReturnsNull()` — fresh cache, lookup unknown ID → null.
  - `givenClockAdvanced61Min_whenSnapshot_thenEmpty()` — advance past TTL.
  - `givenRefresh_whenCalled_thenReturnsEntryCountAndReplaces()` — second refresh wipes prior entries.
  - `givenClockAtTtlBoundary_whenSnapshot_thenTreatsAsExpired()` — `clock.set(T0.plusSeconds(60L * 60L))` (boundary == expired).

**Notes:** untagged (Mockito-only unit per project convention — D-11). BDD method names per CLAUDE.md.

---

#### Plan 94-01 Tests — `TeamFormSnowflakeValidationTest` (NEW, untagged unit)

**Closest analog:** `src/test/java/org/ctc/discord/dto/DiscordConfigFormTest.java` (full file — adapt for TeamForm)
**Match quality:** EXACT pattern

**Test scenarios:**
- `givenEmptyDiscordRoleId_whenValidate_thenNoViolation()` — empty string accepted per `^$|^\\d{17,20}$`.
- `givenValidSnowflake_whenValidate_thenNoViolation()` — `"12345678901234567"` (17 digits).
- `givenInvalidDiscordRoleId_whenValidate_thenPatternViolation()` — `"abc"` produces violation.
- `givenName_shortName_required_whenValidate_thenViolation()` — covers the existing `@NotBlank` invariants.

**Notes:** Uses `Validation.buildDefaultValidatorFactory().getValidator()` shape — verbatim from `DiscordConfigFormTest:19-23`.

---

#### Plan 94-01 Tests — `TeamRepositoryDiscordRoleIdIT` (NEW, `@Tag("integration")`)

**Closest analog:** `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigRepositoryIT.java`
**Match quality:** ROLE-MATCH (entity-level round-trip)

**Test scenarios:**
- `givenTeamWithDiscordRoleId_whenSaveAndReload_thenSnowflakePersists()` — round-trip via `TeamRepository.save` + `findById`.
- `givenTeamWithNullDiscordRoleId_whenSave_thenColumnNullable()` — V9's nullable column invariant.
- `givenTeamSavedWithRoleId_whenClearRoleId_thenColumnUpdatesToNull()` — D-13 "operator can clear" path.

**Annotations:** `@SpringBootTest @ActiveProfiles("dev") @Tag("integration") @Transactional` (copy from `DiscordGlobalConfigRepositoryIT:13-17`).

---

#### Plan 94-01 Tests — `TeamFormDiscordRoleDropdownE2ETest` (NEW, `@Tag("e2e")` in `org.ctc.e2e.discord`)

**Closest analog:** `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` (full file)
**Match quality:** EXACT — extends `PlaywrightConfig`, same `@Tag("e2e")`, same WireMock `@RegisterExtension`

**Pattern excerpt** (from DiscordConfigPageE2ETest.java:25-60):

```java
@Tag("e2e")
class TeamFormDiscordRoleDropdownE2ETest extends PlaywrightConfig {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
        registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
        registry.add("app.discord.bot-token", () -> "e2e-bot-token");
        registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
        registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
        registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
    }

    @BeforeEach void setUp() { setupPage(); wm.resetAll(); }
    @AfterEach void tearDown() { teardownPage(); }
}
```

**Test scenarios:**
- `givenColdCache_whenLoadTeamEditPage_thenRendersPlainTextWithBadgeWarning()` — direct visit, asserts `.badge-warning` visible + `<input type="text" id="discordRoleIdInput">` visible.
- `givenWarmCache_whenLoadTeamEditPage_thenRendersSearchableDropdown()` — first POST `/admin/discord-config/refresh-roles-cache` (WireMock returns 2 roles), then visit team-edit, assert `.searchable-dropdown` visible.
- `givenMobileViewport_whenLoadTeamEditPage_thenLayoutIsMobileCorrect()` — `375×667` viewport sweep per D-06.

**Mobile sweep:** Use the existing `BrowserContext mobileContext = browser.newContext(new Browser.NewContextOptions().setViewportSize(new ViewportSize(375, 667)))` pattern from `DiscordConfigPageE2ETest.java:73-77`.

---

### Plan 94-02 — CHAN-02 Match-Detail Page + Channel Creation Service + Permission Audit

#### `src/main/resources/db/migration/V10__add_matches_discord_and_scheduling_fields.sql` (NEW)

**Closest analog:** `src/main/resources/db/migration/V8__discord_global_config.sql` (header + shape)
**Match quality:** ROLE-MATCH (V8 = CREATE; V10 = 7 × ALTER ADD COLUMN against existing `matches` table)

**Adaptation:**

```sql
-- Phase 94 V10 D-13: extend matches table with Discord channel-handle + 5
-- scheduling/team-facing fields. All columns nullable (operator can leave
-- empty until Phase 95 schedule-edit flow fills them).
-- Compatible with H2 2.x and MariaDB 10.7+ — no CHECK, no LONGTEXT.
-- DO NOT mutate this file after release.

ALTER TABLE matches
    ADD COLUMN discord_channel_id VARCHAR(32);

ALTER TABLE matches
    ADD COLUMN discord_channel_webhook_url VARCHAR(500);

ALTER TABLE matches
    ADD COLUMN discord_teaser VARCHAR(2000);

ALTER TABLE matches
    ADD COLUMN stream_link VARCHAR(500);

ALTER TABLE matches
    ADD COLUMN lobby_host VARCHAR(100);

ALTER TABLE matches
    ADD COLUMN race_director VARCHAR(100);

ALTER TABLE matches
    ADD COLUMN streamer VARCHAR(100);
```

**Notes:**
- All 7 columns nullable (existing matches must not violate constraints).
- No FK indexes — snowflake IDs aren't FKs.
- `discord_channel_webhook_url` length 500 to fit `https://discord.com/api/webhooks/{17-20 digits}/{long token}` payload.

---

#### `src/main/java/org/ctc/domain/model/Match.java` (MODIFIED — +7 fields, `@ToString.Exclude` on webhookUrl)

**Closest analog:** existing `Match.java:13-55` + `DiscordGlobalConfig` `@ToString(exclude = {"announcementWebhookUrl"})` precedent
**Match quality:** EXACT

**Existing `@ToString` shape** (`Match.java:18`):

```java
@ToString(exclude = {"matchday", "homeTeam", "awayTeam", "races"})
```

**Adaptation** — extend the exclude list:

```java
@ToString(exclude = {"matchday", "homeTeam", "awayTeam", "races", "discordChannelWebhookUrl"})
```

**Add 7 new fields** (after the existing `races` collection):

```java
@Column(length = 32)
private String discordChannelId;

@Column(name = "discord_channel_webhook_url", length = 500)
private String discordChannelWebhookUrl;

@Column(length = 2000)
private String discordTeaser;

@Column(length = 500)
private String streamLink;

@Column(length = 100)
private String lobbyHost;

@Column(length = 100)
private String raceDirector;

@Column(length = 100)
private String streamer;
```

**Notes:**
- `@Column(name = "discord_channel_webhook_url")` matches V10 column name; the others auto-translate via Hibernate naming strategy (camelCase → snake_case).
- All nullable (no `@NotNull`); operator path fills them via Phase 94-02 Match-Form-Edit OR Phase 95 schedule-auto-edit.
- No `@Lob` on `discordTeaser` (2000 chars fits VARCHAR(2000) — keeps engine-portability).

---

#### `src/main/java/org/ctc/admin/dto/MatchForm.java` (NEW)

**Closest analog:** `src/main/java/org/ctc/admin/dto/MatchdayForm.java` (full file — verbatim Lombok shape)
**Match quality:** EXACT structural template

**Analog file** (MatchdayForm.java:1-24):

```java
package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MatchdayForm {

    private UUID id;

    @NotBlank
    private String label;

    private int sortIndex;

    @NotNull
    private UUID seasonId;
}
```

**Adaptation for `MatchForm`** (5 Discord-scheduling fields per CONTEXT D-01 + REQ CHAN-02):

```java
package org.ctc.admin.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MatchForm {

    private UUID id;

    @Size(max = 2000)
    private String discordTeaser;

    @Size(max = 500)
    private String streamLink;

    @Size(max = 100)
    private String lobbyHost;

    @Size(max = 100)
    private String raceDirector;

    @Size(max = 100)
    private String streamer;
}
```

**Notes:**
- NO `@Pattern` on `streamLink` — accepts URL, `<#channelId>`, or blank (passed raw to Discord per CONTEXT § Specifics 4).
- NO `discordChannelId` / `discordChannelWebhookUrl` Form fields — those are service-populated, NOT operator-bound (CHAN-02 button action writes them; the Form-Edit only handles operator-facing fields).
- `id` field stays — round-trip pattern: GET edit injects `id`, POST save-edit reads `id` to dispatch UPDATE.

---

#### `src/main/java/org/ctc/discord/DiscordPermissions.java` (NEW — package-level constants)

**Closest analog:** `src/main/java/org/ctc/discord/DiscordTimestamps.java` (package-layout precedent — pure-utility class at `org.ctc.discord` root, sibling to caches)
**Match quality:** ROLE-MATCH (static-only utility)
**Package decision (CONTEXT D-13 + Claude's Discretion):** `org.ctc.discord` (cohesive with `DiscordRoleCache`, `DiscordEmojiCache` — sibling of bean-style utilities; NOT `dto/` since this is logic-constants, not wire types).

**Full source** (verbatim from RESEARCH § Permission Bitmask Reference):

```java
package org.ctc.discord;

public final class DiscordPermissions {

    public static final long CREATE_INSTANT_INVITE = 1L << 0;
    public static final long MANAGE_CHANNELS       = 1L << 4;
    public static final long ADD_REACTIONS         = 1L << 6;
    public static final long VIEW_CHANNEL          = 1L << 10;
    public static final long SEND_MESSAGES         = 1L << 11;
    public static final long MANAGE_MESSAGES       = 1L << 13;
    public static final long EMBED_LINKS           = 1L << 14;
    public static final long ATTACH_FILES          = 1L << 15;
    public static final long READ_MESSAGE_HISTORY  = 1L << 16;
    public static final long MENTION_EVERYONE      = 1L << 17;
    public static final long USE_EXTERNAL_EMOJIS   = 1L << 18;
    public static final long CONNECT               = 1L << 20;
    public static final long SPEAK                 = 1L << 21;
    public static final long MANAGE_WEBHOOKS       = 1L << 29;
    public static final long MANAGE_THREADS        = 1L << 34;
    public static final long USE_EXTERNAL_STICKERS = 1L << 37;

    public static final long EVERYONE_DENY_VIEW_MASK = VIEW_CHANNEL;
    public static final long TEAM_MEMBER_ALLOW_MASK =
        VIEW_CHANNEL | SEND_MESSAGES | ADD_REACTIONS | ATTACH_FILES
        | EMBED_LINKS | READ_MESSAGE_HISTORY | USE_EXTERNAL_EMOJIS | USE_EXTERNAL_STICKERS;
    public static final long TEAM_MEMBER_DENY_MASK =
        CONNECT | SPEAK | MANAGE_CHANNELS | MANAGE_MESSAGES | MANAGE_THREADS
        | MANAGE_WEBHOOKS | CREATE_INSTANT_INVITE | MENTION_EVERYONE;

    public static final int OVERWRITE_TYPE_ROLE = 0;
    public static final int OVERWRITE_TYPE_MEMBER = 1;

    private DiscordPermissions() {}
}
```

**Notes:**
- Composites are OR-expressions of individual bits — NEVER hardcode decimal literals (RESEARCH A1/A2 risk mitigation).
- `OVERWRITE_TYPE_ROLE = 0` constant disambiguates the magic number used in PermissionOverwrite `type` field.
- Pure-static; no Lombok, no `@Component`.

---

#### `src/main/java/org/ctc/discord/dto/PermissionOverwrite.java` (NEW record)

**Closest analog:** `src/main/java/org/ctc/discord/dto/Channel.java` (record + Jackson annotations)
**Match quality:** EXACT

**Analog excerpt** (`Channel.java:1-12`):

```java
package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Channel(
        String id,
        String name,
        int type,
        @JsonProperty("parent_id") String parentId) {
}
```

**Adaptation for `PermissionOverwrite`** (per RESEARCH § Permission Bitmask "allow/deny serialized as strings"):

```java
package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PermissionOverwrite(
        String id,
        int type,
        String allow,
        String deny) {
}
```

**Notes:**
- `allow` + `deny` are `String` (NOT `long`) — Discord docs explicitly require string-encoded bitmasks (RESEARCH Pitfall 2: numeric > 53 bits unsafe for JS-bridged clients).
- Call-site uses `String.valueOf(DiscordPermissions.TEAM_MEMBER_ALLOW_MASK)`.

---

#### `src/main/java/org/ctc/discord/dto/Webhook.java` (NEW record)

**Closest analog:** `src/main/java/org/ctc/discord/dto/Channel.java`
**Match quality:** EXACT

**Adaptation** (per RESEARCH § "POST /channels/{id}/webhooks"):

```java
package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Webhook(
        String id,
        String token,
        String url,
        @JsonProperty("channel_id") String channelId) {
}
```

**Notes:**
- `url` field is Discord-provided (we don't reconstruct).
- The `token` field is a SECRET — flows directly into `match.discordChannelWebhookUrl` (which is `@ToString.Exclude`-protected).

---

#### `src/main/java/org/ctc/discord/dto/ChannelCreateRequest.java` (MODIFIED — extend with `permission_overwrites`)

**Closest analog:** self (existing 3-arg record)
**Match quality:** EXACT (self-extension)

**Existing file:**

```java
package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChannelCreateRequest(
        String name,
        int type,
        @JsonProperty("parent_id") String parentId) {
}
```

**Adaptation** (per RESEARCH § "Channel Record Extension Strategy" + Pitfall 1):

```java
package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelCreateRequest(
        String name,
        int type,
        @JsonProperty("parent_id") String parentId,
        @JsonProperty("permission_overwrites") List<PermissionOverwrite> permissionOverwrites) {

    public ChannelCreateRequest(String name, int type, String parentId) {
        this(name, type, parentId, null);
    }
}
```

**Notes:**
- `@JsonInclude(NON_NULL)` is MANDATORY (Pitfall 1) — otherwise existing Phase 93 IT `givenCreateChannel` sends `"permission_overwrites": null` which Discord 400-rejects.
- Convenience constructor preserves Phase 93 IT callers using the 3-arg form.

---

#### `src/main/java/org/ctc/discord/dto/Channel.java` (MODIFIED — extend with optional `permission_overwrites`)

**Closest analog:** self
**Match quality:** EXACT (self-extension)

**Adaptation** (per RESEARCH § "Channel Record Extension Strategy"):

```java
package org.ctc.discord.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Channel(
        String id,
        String name,
        int type,
        @JsonProperty("parent_id") String parentId,
        @JsonProperty("permission_overwrites") List<PermissionOverwrite> permissionOverwrites) {

    public Channel(String id, String name, int type, String parentId) {
        this(id, name, type, parentId, null);
    }
}
```

**Notes:**
- Convenience constructor preserves `DiscordRestClientIT.givenCreateChannel` 4-arg invocations.
- Deserialization handles missing `permission_overwrites` by defaulting to `null` (record-component contract).

---

#### `src/main/java/org/ctc/discord/DiscordRestClient.java` (MODIFIED — add `createWebhook` + `fetchChannel` + `deleteChannel`)

**Closest analog:** self at lines 68-91 (existing `createChannel`, `modifyChannel`, `listChannels`)
**Match quality:** EXACT (typed-method extension pattern)

**Existing pattern excerpt** (`DiscordRestClient.java:68-75`):

```java
public Channel createChannel(String guildId, ChannelCreateRequest request) throws DiscordApiException {
    return execute(() -> bot.post()
            .uri("/guilds/{guildId}/channels", guildId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(Channel.class));
}
```

**Adaptation** (per RESEARCH § "RestClient DELETE Helper Shape"):

```java
public Webhook createWebhook(String channelId, String name) throws DiscordApiException {
    return execute(() -> bot.post()
            .uri("/channels/{channelId}/webhooks", channelId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new WebhookCreateRequest(name))
            .retrieve()
            .body(Webhook.class));
}

public Channel fetchChannel(String channelId) throws DiscordApiException {
    return execute(() -> bot.get()
            .uri("/channels/{channelId}", channelId)
            .retrieve()
            .body(Channel.class));
}

public void deleteChannel(String channelId) throws DiscordApiException {
    execute(() -> {
        bot.delete()
            .uri("/channels/{channelId}", channelId)
            .retrieve()
            .toBodilessEntity();
        return null;
    });
}

private record WebhookCreateRequest(String name) {}
```

**Notes:**
- `toBodilessEntity()` consumes the 200-OK-with-body response Discord returns on DELETE (per RESEARCH § DELETE contract).
- `return null` in the DELETE lambda is required because `execute(...)` is `<T>`-parameterized.
- `WebhookCreateRequest` private record stays co-located (mirrors the existing `Emoji` / `ThreadList` private records at `DiscordRestClient.java:145-149`).
- 404 on `deleteChannel` is treated as success-equivalent — RESEARCH § DELETE Edge Cases notes this; planner-discretion to wrap a `try/catch DiscordNotFoundException` in `DiscordChannelService` for the cleanup path.

---

#### `src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java` (MODIFIED — add `AUDIT_FAIL_MESSAGE`)

**Closest analog:** self (existing constants block)
**Match quality:** EXACT (extension)

**Existing pattern** — verify against the file; add constant:

```java
public static final String AUDIT_FAIL_MESSAGE =
        "Channel permission audit failed - an unexpected role had View permission. "
        + "Channel was deleted; verify Discord server-role setup and retry.";
```

**Notes:**
- Hardcoded constant (T-91-02-IL — never echo `e.getMessage()` per CONTEXT D-04).
- `DiscordChannelService` and `MatchControllerCreateChannelErrorCategoryTest` reference this constant by name.
- The cleanup-fail append pattern is `AUDIT_FAIL_MESSAGE + " Cleanup failed: please manually delete channel " + channelId + " via Discord."` (constructed in service; not a separate constant).

---

#### `src/main/java/org/ctc/discord/service/DiscordChannelService.java` (NEW — 9-step transactional orchestrator)

**Closest analog:** NO existing service in `org.ctc.discord.service.*` calls `restClient.createChannel` + `createWebhook` + `fetchChannel` + `deleteChannel`. Closest reference patterns:
- `src/main/java/org/ctc/discord/web/DiscordConfigController.java:136-147` (typed-catch + applyErrorFlash style — adapted for service-layer throw)
- RESEARCH § "DiscordChannelService Flow + Audit Architecture" (D-04 contract — provides the 9-step body)

**Match quality:** NO ANALOG — first transactional Discord-write orchestrator. Use RESEARCH sketch as substitute template.

**Source template** (verbatim from RESEARCH § Pattern 1):

```java
package org.ctc.discord.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.DiscordPermissions;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.Channel;
import org.ctc.discord.dto.ChannelCreateRequest;
import org.ctc.discord.dto.PermissionOverwrite;
import org.ctc.discord.dto.Webhook;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordChannelService {

    private final DiscordRestClient restClient;
    private final DiscordGlobalConfigService configService;
    private final MatchRepository matchRepository;

    @Transactional
    public void createMatchChannel(Match match) throws DiscordApiException {
        // 1. assert preconditions
        DiscordGlobalConfig cfg = configService.getOrInitialize();
        if (match.getHomeTeam() == null || match.getAwayTeam() == null
                || match.getHomeTeam().getDiscordRoleId() == null
                || match.getAwayTeam().getDiscordRoleId() == null
                || cfg.getCurrentMatchCategoryId() == null
                || cfg.getCurrentMatchCategoryId().isBlank()) {
            throw new BusinessRuleException(
                    "Channel creation requires both team Discord roles and a current match category.");
        }

        // 2. compute channel name
        String name = ("md" + match.getMatchday().getNumber() + "-"
                + match.getHomeTeam().getShortName() + "-vs-"
                + match.getAwayTeam().getShortName()).toLowerCase();

        // 3. build overwrites — @everyone deny VIEW; both teams allow/deny masks
        List<PermissionOverwrite> overwrites = List.of(
            new PermissionOverwrite(cfg.getGuildId(),
                DiscordPermissions.OVERWRITE_TYPE_ROLE,
                "0",
                String.valueOf(DiscordPermissions.EVERYONE_DENY_VIEW_MASK)),
            new PermissionOverwrite(match.getHomeTeam().getDiscordRoleId(),
                DiscordPermissions.OVERWRITE_TYPE_ROLE,
                String.valueOf(DiscordPermissions.TEAM_MEMBER_ALLOW_MASK),
                String.valueOf(DiscordPermissions.TEAM_MEMBER_DENY_MASK)),
            new PermissionOverwrite(match.getAwayTeam().getDiscordRoleId(),
                DiscordPermissions.OVERWRITE_TYPE_ROLE,
                String.valueOf(DiscordPermissions.TEAM_MEMBER_ALLOW_MASK),
                String.valueOf(DiscordPermissions.TEAM_MEMBER_DENY_MASK)));

        ChannelCreateRequest req = new ChannelCreateRequest(name, 0,
                cfg.getCurrentMatchCategoryId(), overwrites);

        // 4. createChannel
        Channel channel = restClient.createChannel(cfg.getGuildId(), req);

        // 5. createWebhook
        Webhook webhook = restClient.createWebhook(channel.id(), "CTC Manager");

        // 6+7. audit + cleanup
        try {
            assertPermissionAudit(channel.id(),
                    match.getHomeTeam().getDiscordRoleId(),
                    match.getAwayTeam().getDiscordRoleId());
        } catch (DiscordAuthException auditEx) {
            try {
                restClient.deleteChannel(channel.id());
            } catch (DiscordApiException cleanupEx) {
                log.warn("Audit-fail cleanup DELETE failed for channel {}: {}",
                        channel.id(), cleanupEx.toString());
                throw new DiscordAuthException(
                        DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE
                                + " Cleanup failed: please manually delete channel "
                                + channel.id() + " via Discord.",
                        auditEx);
            }
            throw auditEx;
        }

        // 8. update entity, 9. save → COMMIT
        match.setDiscordChannelId(channel.id());
        match.setDiscordChannelWebhookUrl(webhook.url());
        matchRepository.save(match);
        log.info("Discord channel created for match {} → {} (channelId={})",
                match.getId(), channel.name(), channel.id());
    }

    private void assertPermissionAudit(String channelId, String homeRoleId, String awayRoleId)
            throws DiscordApiException {
        Channel back = restClient.fetchChannel(channelId);
        List<PermissionOverwrite> overwrites = back.permissionOverwrites();
        if (overwrites == null || overwrites.size() != 3) {
            throw new DiscordAuthException(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE, null);
        }
        java.util.Set<String> rolesWithView = overwrites.stream()
                .filter(o -> o.type() == DiscordPermissions.OVERWRITE_TYPE_ROLE)
                .filter(o -> (Long.parseLong(o.allow()) & DiscordPermissions.VIEW_CHANNEL) != 0L)
                .map(PermissionOverwrite::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!rolesWithView.equals(java.util.Set.of(homeRoleId, awayRoleId))) {
            throw new DiscordAuthException(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE, null);
        }
    }
}
```

**Notes:**
- `@Transactional` is critical — DB write at step 9 rolls back if any of steps 4–8 throw.
- Audit uses **set equality of role-IDs-with-View** (RESEARCH Pitfall 4 mitigation — robust against extra inherited Discord overwrites).
- Cleanup-fail composes the user-message inline (no separate constant per RESEARCH).
- Log at INFO on success with `channel.name()` (Discord-server-returned name, not the pre-formatted name — RESEARCH § Channel Name Normalization rationale).

---

#### `src/main/java/org/ctc/admin/controller/MatchController.java` (MODIFIED — +5 endpoints)

**Closest analog (5 endpoints):**
- GET `/{id}` (detail) — `src/main/java/org/ctc/admin/controller/TeamController.java:31-40` (detail-page shape)
- GET `/{id}/edit` — `src/main/java/org/ctc/admin/controller/TeamController.java:48-61`
- POST `/{id}/save-edit` — `src/main/java/org/ctc/admin/controller/TeamController.java:63-77` (POST save with `@Valid` + `BindingResult`)
- POST `/{id}/create-discord-channel` — `src/main/java/org/ctc/discord/web/DiscordConfigController.java:69-77` (typed-catch + applyErrorFlash)
- POST `/{id}/move-to-archive` — same as above (Plan 94-03)

**Match quality:** EXACT (CRUD + typed-catch composition)

**Adaptation** — extend `MatchController` with detail/edit/create-channel endpoints (move-to-archive lands in 94-03):

```java
@GetMapping("/{id}")
public String detail(@PathVariable UUID id, Model model) {
    var data = matchService.getDetailData(id);
    model.addAttribute("match", data.match());
    model.addAttribute("archiveCategories", data.archiveCategories());  // populated in 94-03
    model.addAttribute("pageTitle",
            "Match: " + data.match().getHomeTeam().getShortName() + " vs "
                    + (data.match().getAwayTeam() != null ? data.match().getAwayTeam().getShortName() : "Bye"));
    return "admin/match-detail";
}

@GetMapping("/{id}/edit")
public String edit(@PathVariable UUID id, Model model) {
    var match = matchService.findById(id);
    var form = new MatchForm();
    form.setId(match.getId());
    form.setDiscordTeaser(match.getDiscordTeaser());
    form.setStreamLink(match.getStreamLink());
    form.setLobbyHost(match.getLobbyHost());
    form.setRaceDirector(match.getRaceDirector());
    form.setStreamer(match.getStreamer());
    model.addAttribute("matchForm", form);
    model.addAttribute("match", match);
    return "admin/match-form-edit";
}

@PostMapping("/{id}/save-edit")
public String saveEdit(@PathVariable UUID id,
                       @Valid @ModelAttribute("matchForm") MatchForm form, BindingResult result,
                       RedirectAttributes redirectAttributes) {
    if (result.hasErrors()) {
        return "admin/match-form-edit";
    }
    matchService.updateDiscordFields(id, form);
    redirectAttributes.addFlashAttribute("successMessage", "Match details updated.");
    return "redirect:/admin/matches/" + id;
}

@PostMapping("/{id}/create-discord-channel")
public String createDiscordChannel(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
        var match = matchService.findById(id);
        discordChannelService.createMatchChannel(match);
        redirectAttributes.addFlashAttribute("successMessage", "Discord channel created.");
    } catch (BusinessRuleException e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        redirectAttributes.addFlashAttribute("errorCategory", "not-found");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Create Discord Channel");
    }
    return "redirect:/admin/matches/" + id;
}

// applyErrorFlash helper — copied verbatim from DiscordConfigController:136-147
private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e, String action) {
    String message = switch (e.category()) {
        case TRANSIENT -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
        case AUTH -> DiscordApiExceptionMapper.AUTH_MESSAGE;
        case NOT_FOUND -> DiscordApiExceptionMapper.NOT_FOUND_MESSAGE;
        case CATEGORY_FULL -> DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE;
    };
    String category = e.category().name().toLowerCase().replace('_', '-');
    log.warn("{} failed: category={}", action, category);
    ra.addFlashAttribute("errorMessage", message);
    ra.addFlashAttribute("errorCategory", category);
}
```

**Notes:**
- 5 new dependencies via Lombok `@RequiredArgsConstructor`: `MatchService` (already there), `DiscordChannelService`, plus 94-03 adds `DiscordCategoryResolver`.
- `applyErrorFlash` is copy-paste from `DiscordConfigController:136-147`. Planner may refactor into a shared `DiscordErrorFlashSupport` utility (planner-discretion) if it accumulates 3+ usages — Phase 95+ POST endpoints will multiply this.
- `BusinessRuleException` catch comes BEFORE `DiscordApiException` (sealed hierarchy ordering — same as `DriverSheetImportController` permits ordering).

---

#### `src/main/java/org/ctc/domain/service/MatchService.java` (MODIFIED — `findById`, `getDetailData`, `updateDiscordFields`)

**Closest analog:** existing `MatchService` methods (`getCreateFormData`, `createMatch`, `addLeg`, `deleteMatch`, `getMatchdayId`) — extension pattern visible at `MatchController.java`
**Match quality:** EXACT (CRUD extension)

**New method signatures:**

```java
public Match findById(UUID id) { /* repository.findById + EntityNotFoundException */ }
public MatchDetailData getDetailData(UUID id) { /* prepare data record incl. archiveCategories (94-03 lookup) */ }
@Transactional public void updateDiscordFields(UUID id, MatchForm form) {
    Match match = findById(id);
    match.setDiscordTeaser(form.getDiscordTeaser());
    match.setStreamLink(form.getStreamLink());
    match.setLobbyHost(form.getLobbyHost());
    match.setRaceDirector(form.getRaceDirector());
    match.setStreamer(form.getStreamer());
    matchRepository.save(match);
}
```

**Notes:**
- `MatchDetailData` is a `record` co-located with the service (sibling to existing `Gt7SyncPreview` style records). Carries `match`, `archiveCategories` (List<ArchiveCategory>), and any view-prep helpers.
- 94-03 wires `archiveCategories` via `DiscordCategoryResolver.resolveArchiveCategoriesFor(LocalDate.now().getYear())` (planner picks year-source).

---

#### `src/main/resources/templates/admin/match-detail.html` (NEW)

**Closest analog:** `src/main/resources/templates/admin/team-detail.html` (per-entity detail page with toolbar + sub-cards)
**Match quality:** ROLE-MATCH (Discord Actions panel composition is new)

**Analog excerpt** (team-detail.html opening):

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout(${pageTitle}, ~{::section})}">
<body>
<section>
    <div class="toolbar">
        <div>
            <a th:href="@{/admin/teams}" class="back-link">&larr; Back to Teams</a>
            <h1 th:text="${team.name}"></h1>
        </div>
        <div class="actions">
            <a th:href="@{/admin/teams/{id}/edit(id=${team.id})}" class="btn btn-primary">Edit</a>
            <form th:action="@{/admin/teams/{id}/delete(id=${team.id})}" method="post"
                  onsubmit="return confirm('Really delete this team?')">
                <button type="submit" class="btn btn-danger">Delete</button>
            </form>
        </div>
    </div>
    <div class="card">...</div>
</section>
</body>
</html>
```

**Adaptation outline** (full template to be assembled per planner):

```html
<section>
    <div class="toolbar">
        <a th:href="@{/admin/matchdays/{id}(id=${match.matchday.id})}" class="back-link">
            &larr; Back to [[${match.matchday.label}]]
        </a>
        <h1>
            [[${match.homeTeam.shortName}]] vs
            <span th:if="${match.awayTeam != null}">[[${match.awayTeam.shortName}]]</span>
            <span th:if="${match.awayTeam == null}" class="badge badge-warning">Bye</span>
        </h1>
        <div class="actions">
            <a th:href="@{/admin/matches/{id}/edit(id=${match.id})}" class="btn btn-primary">Edit</a>
        </div>
    </div>

    <!-- Discord Actions panel (CHAN-02 + CHAN-03 + Phase 95 placeholders) -->
    <div class="card">
        <h2>Discord Actions</h2>
        <div class="discord-actions">
            <!-- Create Channel button (gated on 3 conditions; D-01) -->
            <form th:if="${match.discordChannelId == null}"
                  th:action="@{/admin/matches/{id}/create-discord-channel(id=${match.id})}"
                  method="post" class="inline-form">
                <button type="submit" class="btn btn-primary"
                        th:disabled="${match.homeTeam.discordRoleId == null || match.awayTeam == null || match.awayTeam.discordRoleId == null}"
                        data-testid="create-discord-channel">Create Discord Channel</button>
            </form>
            <!-- Channel link + Move-to-Archive (when channel exists) -->
            <div th:if="${match.discordChannelId != null}" class="discord-actions">
                <span class="badge badge-active">Channel: [[${match.discordChannelId}]]</span>
                <button type="button" class="btn btn-secondary"
                        onclick="document.getElementById('archiveModal').style.display='flex'"
                        data-testid="open-archive-modal">Move to Archive</button>
            </div>
        </div>
        <!-- Phase 95 placeholder -->
        <div class="discord-actions discord-actions--posts">
            <!-- TODO Phase 95: POST buttons land here (POST-01 .. POST-05) -->
        </div>
    </div>

    <!-- Schedule summary -->
    <div class="card">
        <h2>Schedule</h2>
        <div class="detail-fields">
            <span class="label">Stream Link</span><span th:text="${match.streamLink ?: '-'}"></span>
            <span class="label">Lobby Host</span><span th:text="${match.lobbyHost ?: '-'}"></span>
            <span class="label">Race Director</span><span th:text="${match.raceDirector ?: '-'}"></span>
            <span class="label">Streamer</span><span th:text="${match.streamer ?: '-'}"></span>
            <span class="label">Teaser</span>
            <pre class="markdown-source" th:text="${match.discordTeaser ?: '-'}"></pre>
        </div>
    </div>

    <!-- Races section — reuse existing match-row shape from matchday-detail.html -->
    <div class="card">
        <h2>Races</h2>
        <ul>
            <li th:each="race : ${match.races}">
                <a th:href="@{/admin/races/{id}(id=${race.id})}" th:text="${race.dateTime}"></a>
            </li>
        </ul>
    </div>

    <!-- Archive Modal (CHAN-03) — see Plan 94-03 section -->
    <!-- ... -->
</section>
```

**Notes:**
- Toolbar pattern from `team-detail.html` (back-link + h1 + actions).
- Sub-cards for Discord Actions / Schedule / Races match the existing `team-detail.html` `<div class="card">` composition.
- Teaser rendered as `<pre>` (NOT escaped HTML — Discord renders Markdown server-side; admin shows raw source per CONTEXT § Specifics 6).
- `data-testid="..."` selectors enable Playwright E2E queries (`MatchDetailControllerE2ETest`).

---

#### `src/main/resources/templates/admin/match-form-edit.html` (NEW)

**Closest analog:** `src/main/resources/templates/admin/match-form.html` (CREATE flow)
**Match quality:** ROLE-MATCH (clone with `id` hidden + 5 fields + `/save-edit` action)

**Adaptation** — clone shape, swap form-action to `/save-edit`, bind to `matchForm` DTO with `@Valid`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Edit Match', ~{::section})}">
<body>
<section>
    <a th:href="@{/admin/matches/{id}(id=${match.id})}" class="back-link">
        &larr; Back to Match Detail
    </a>
    <h1>Edit Match</h1>

    <form th:action="@{/admin/matches/{id}/save-edit(id=${match.id})}" th:object="${matchForm}" method="post">
        <input type="hidden" th:field="*{id}">

        <div class="card">
            <h2>Discord &amp; Schedule</h2>
            <div class="form-group">
                <label for="discordTeaser">Discord Teaser (Markdown — server-rendered)</label>
                <textarea id="discordTeaser" th:field="*{discordTeaser}" rows="6" maxlength="2000"></textarea>
                <span th:errors="*{discordTeaser}" class="error-badge error-badge--auth"></span>
            </div>
            <div class="form-row">
                <div class="form-group">
                    <label for="streamLink">Stream Link</label>
                    <input type="text" id="streamLink" th:field="*{streamLink}" maxlength="500">
                </div>
                <div class="form-group">
                    <label for="lobbyHost">Lobby Host</label>
                    <input type="text" id="lobbyHost" th:field="*{lobbyHost}" maxlength="100">
                </div>
            </div>
            <div class="form-row">
                <div class="form-group">
                    <label for="raceDirector">Race Director</label>
                    <input type="text" id="raceDirector" th:field="*{raceDirector}" maxlength="100">
                </div>
                <div class="form-group">
                    <label for="streamer">Streamer</label>
                    <input type="text" id="streamer" th:field="*{streamer}" maxlength="100">
                </div>
            </div>
            <div class="actions mt-md">
                <button type="submit" class="btn btn-primary">Save</button>
                <a th:href="@{/admin/matches/{id}(id=${match.id})}" class="btn btn-secondary">Cancel</a>
            </div>
        </div>
    </form>
</section>
</body>
</html>
```

**Notes:**
- Form-action `/admin/matches/{id}/save-edit` — distinct from legacy `/admin/matches/save` (CREATE per CLAUDE.md § Backward Compatibility).
- Bind to `matchForm` (`@ModelAttribute` named `matchForm` matches the controller GET injection).
- 5 fields exactly per CHAN-02 acceptance text; max-lengths align with `MatchForm` `@Size` annotations.

---

#### `src/main/resources/templates/admin/matchday-detail.html` (MODIFIED — add "→ Detail" link per match-row)

**Closest analog:** self at lines 39-70 (existing match-row composition)
**Match quality:** EXACT

**Adaptation** — within `<div th:each="match : ${matchday.matches}" class="match-row">` block, append a "→ Detail" link to the existing Edit/Delete button group:

```html
<a th:href="@{/admin/matches/{id}(id=${match.id})}" class="btn btn-xs btn-secondary">→ Detail</a>
```

**Notes:**
- DO NOT add inline Discord-field form inputs on matchday-detail.html (CONTEXT D-01 rejected Option B).
- Position the link next to existing Edit/Delete buttons; CSS `.btn-xs` keeps the row compact.

---

#### Plan 94-02 Tests — Mockito unit tests

`MatchFormValidationTest` — copy `DiscordConfigFormTest` shape; assert `@Size` violations on each 5 fields beyond their max length; assert blank-input is accepted.

`MatchToStringTest` — verbatim port of `DiscordGlobalConfigToStringTest`:

```java
@Test
void givenWebhookUrlPopulated_whenToString_thenDoesNotEchoTokenFragment() {
    Match match = new Match();
    match.setDiscordChannelWebhookUrl("https://discord.com/api/webhooks/100/secret-token-xyz");
    String rendered = match.toString();
    assertThat(rendered).doesNotContain("secret-token-xyz");
}
```

`DiscordPermissionsTest` — assert composite mask correctness:

```java
@Test
void teamMemberAllowMask_isCorrectComposite() {
    assertThat(DiscordPermissions.TEAM_MEMBER_ALLOW_MASK)
        .isEqualTo(1024L + 2048L + 64L + 32768L + 16384L + 65536L + 262144L + 140737488355328L);
}
```

`MatchControllerCreateChannelErrorCategoryTest` — copy `DiscordConfigControllerErrorCategoryTest` parameterized shape (TRANSIENT/AUTH/NOT_FOUND/CATEGORY_FULL → lowercase-hyphen BEM suffix).

#### Plan 94-02 Tests — WireMock-IT pattern (3 classes)

**Closest analog:** `src/test/java/org/ctc/discord/DiscordRestClientIT.java` (WireMock scaffold + `@DynamicPropertySource` + `wm.resetAll()`).

**Scaffold template** (verbatim from `DiscordRestClientIT.java:36-61`):

```java
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordChannelServiceWireMockIT {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
        registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
        registry.add("app.discord.bot-token", () -> "test-bot-token");
        registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
        registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
        registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
    }

    @Autowired private DiscordChannelService service;
    @Autowired private MatchRepository matchRepository;
    @Autowired private DiscordGlobalConfigRepository configRepo;

    @BeforeEach void resetWireMock() { wm.resetAll(); /* + seed test config + test match */ }
}
```

**Stub-shape examples** (from RESEARCH § Code Examples + § Discord REST API Contracts):

- Happy-path createChannel:
  ```java
  wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/g1/channels"))
          .willReturn(okJson("{\"id\":\"c1\",\"name\":\"md1-clr-vs-tnt\",\"type\":0,\"parent_id\":\"cat1\"}")));
  ```
- createWebhook:
  ```java
  wm.stubFor(post(urlPathEqualTo("/api/v10/channels/c1/webhooks"))
          .willReturn(okJson("{\"id\":\"w1\",\"token\":\"tok-abc\","
                + "\"url\":\"https://discord.com/api/webhooks/w1/tok-abc\","
                + "\"channel_id\":\"c1\"}")));
  ```
- fetchChannel (audit-pass):
  ```java
  wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c1"))
          .willReturn(okJson("""
              {
                "id":"c1","name":"md1-clr-vs-tnt","type":0,"parent_id":"cat1",
                "permission_overwrites":[
                  {"id":"g1","type":0,"allow":"0","deny":"1024"},
                  {"id":"home-role","type":0,"allow":"140737488735808","deny":"17719906033"},
                  {"id":"away-role","type":0,"allow":"140737488735808","deny":"17719906033"}
                ]
              }""")));
  ```

**Test class responsibilities** (per Validation Architecture table):
- **`DiscordChannelServiceWireMockIT`** — happy-path (9-step flow); asserts `match.discordChannelId` + `match.discordChannelWebhookUrl` populated; `wm.verify()` all 3 outbound calls fired.
- **`DiscordChannelServicePermissionAuditFailIT`** — fetchChannel stub returns 4 overwrites (third unauthorized role); expects DELETE called + `DiscordAuthException` thrown + DB unchanged (match.discordChannelId still null).
- **`DiscordChannelServiceCleanupFailIT`** — audit fails AND DELETE stub returns 500; expects `DiscordAuthException` with "Cleanup failed: please manually delete channel c1 via Discord." appended; WARN log assertion via `OutputCaptureExtension` (mirrors Phase 93 `DiscordLogMaskingIT`).

#### Plan 94-02 Tests — `MatchEditFormIT` (round-trip)

**Closest analog:** `src/test/java/org/ctc/discord/web/DiscordConfigControllerIT.java` (MockMvc-based round-trip)
**Match quality:** EXACT

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class MatchEditFormIT {

    @Autowired private MockMvc mockMvc;

    @Test
    void givenValidForm_whenPostSaveEdit_thenFlashSuccessAndFieldsPersisted() throws Exception {
        // POST .../save-edit with 5 fields → expect redirect to /admin/matches/{id}
        //   + flash successMessage + repository round-trip
    }
}
```

#### Plan 94-02 Tests — `MatchDetailControllerE2ETest` (Playwright)

**Closest analog:** `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java`
**Match quality:** EXACT (extends `PlaywrightConfig`, `@Tag("e2e")`, `org.ctc.e2e.discord` package)

**Test scenarios:**
- `givenMatchWithoutDiscord_whenLoadDetail_thenCreateChannelButtonVisible()` — but disabled when team roles missing.
- `givenMatchWithDiscord_whenLoadDetail_thenChannelLinkVisible()` — channel-link badge + Move-to-Archive button shown.
- Mobile sweep (375×667) per D-06.

---

### Plan 94-03 — CHAN-03 Archive Modal + Category Resolver

#### `src/main/java/org/ctc/discord/dto/ArchiveCategory.java` (NEW record)

**Closest analog:** `src/main/java/org/ctc/discord/dto/Channel.java` (record shape)
**Match quality:** ROLE-MATCH (record + Jackson contract)

**Source:**

```java
package org.ctc.discord.dto;

public record ArchiveCategory(String id, String name, int num, int currentChannelCount) {
}
```

**Notes:**
- Not a Jackson-bound DTO (no Discord wire shape — purely internal). No `@JsonIgnoreProperties` needed.
- `num` = the optional `(N)` suffix from regex `Match Days Archive {year} (N)` (default 1 if absent).
- `currentChannelCount` derived from `restClient.listChannels(guildId)` filtered by this category's `id` as `parent_id`.

---

#### `src/main/java/org/ctc/discord/service/DiscordCategoryResolver.java` (NEW)

**Closest analog:** `src/main/java/org/ctc/discord/DiscordRestClient.java:86-91` (listChannels call-site) + RESEARCH § "Architecture Patterns" CHAN-03 flow
**Match quality:** NO ANALOG — first regex/filter service; substitute with RESEARCH sketch

**Source template** (per CONTEXT + RESEARCH):

```java
package org.ctc.discord.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.ArchiveCategory;
import org.ctc.discord.dto.Channel;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordCategoryResolver {

    private static final Pattern ARCHIVE_NAME =
            Pattern.compile("^Match Days Archive (?<year>\\d{4})(?: \\((?<num>\\d+)\\))?$");
    private static final int CATEGORY_TYPE = 4;
    private static final int CATEGORY_LIMIT = 50;

    private final DiscordRestClient restClient;
    private final DiscordGlobalConfigService configService;

    public List<ArchiveCategory> resolveArchiveCategoriesFor(int year) throws DiscordApiException {
        String guildId = configService.getOrInitialize().getGuildId();
        List<Channel> all = restClient.listChannels(guildId);
        return all.stream()
                .filter(c -> c.type() == CATEGORY_TYPE)
                .map(c -> matchYear(c, year, all))
                .flatMap(Optional::stream)
                .sorted(java.util.Comparator.comparingInt(ArchiveCategory::num))
                .toList();
    }

    public Optional<ArchiveCategory> defaultSelection(List<ArchiveCategory> categories) {
        return categories.stream()
                .filter(c -> c.currentChannelCount() < CATEGORY_LIMIT)
                .max(java.util.Comparator.comparingInt(ArchiveCategory::num));
    }

    private Optional<ArchiveCategory> matchYear(Channel cat, int year, List<Channel> all) {
        Matcher m = ARCHIVE_NAME.matcher(cat.name());
        if (!m.matches()) return Optional.empty();
        if (Integer.parseInt(m.group("year")) != year) return Optional.empty();
        int num = m.group("num") == null ? 1 : Integer.parseInt(m.group("num"));
        int count = (int) all.stream().filter(c -> cat.id().equals(c.parentId())).count();
        return Optional.of(new ArchiveCategory(cat.id(), cat.name(), num, count));
    }
}
```

**Notes:**
- Compiled `Pattern` is a `static final` field (no per-call recompilation).
- `CATEGORY_TYPE = 4` documents the Discord magic number (channel-type for category).
- `defaultSelection` returns `Optional.empty()` if all categories full → controller maps to `DiscordCategoryFullException`.
- No caching per CONTEXT D-02 + RESEARCH Open Question 2 (modal renders need live counts).

---

#### `src/main/java/org/ctc/admin/controller/MatchController.java` (MODIFIED — +POST /{id}/move-to-archive)

**Closest analog:** Plan 94-02 `createDiscordChannel` endpoint (same typed-catch + applyErrorFlash shape)
**Match quality:** EXACT

**Adaptation:**

```java
@PostMapping("/{id}/move-to-archive")
public String moveToArchive(@PathVariable UUID id,
                            @RequestParam String categoryId,
                            RedirectAttributes redirectAttributes) {
    try {
        var match = matchService.findById(id);
        if (match.getDiscordChannelId() == null) {
            throw new BusinessRuleException("Match has no Discord channel to archive.");
        }
        if (categoryId == null || categoryId.isBlank()) {
            throw new DiscordCategoryFullException(
                    DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE, null);
        }
        discordRestClient.modifyChannel(match.getDiscordChannelId(),
                new ChannelModifyRequest(null, categoryId));
        redirectAttributes.addFlashAttribute("successMessage", "Channel moved to archive.");
    } catch (BusinessRuleException e) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        redirectAttributes.addFlashAttribute("errorCategory", "not-found");
    } catch (DiscordApiException e) {
        applyErrorFlash(redirectAttributes, e, "Move to Archive");
    }
    return "redirect:/admin/matches/" + id;
}
```

**Notes:**
- Empty categoryId → maps to CategoryFull (same operator semantic: "no valid target category").
- `discordRestClient` dependency already injected (Plan 94-02); 94-03 adds no new fields to the controller.
- `applyErrorFlash` covers CATEGORY_FULL → `.error-badge--category-full` BEM CSS (already shipped Phase 93 per RESEARCH Open Question 3).

---

#### `src/main/resources/templates/admin/match-detail.html` (MODIFIED — add Archive Modal `<div class="modal-overlay">`)

**Closest analog:** `src/main/resources/templates/admin/season-detail.html:96, 156` (modal-overlay pattern — VERIFIED two existing usages)
**Match quality:** EXACT pattern reuse

**Analog excerpt** (`season-detail.html:96-110` pattern + RESEARCH § Modal Pattern Decision):

```html
<div id="seasonTeamModal" class="modal-overlay">
    <div class="modal-body modal-body--md">
        <h3 class="modal-title">Add Team to Season</h3>
        <form th:action="@{...}" method="post">
            <!-- ... radio buttons ... -->
            <div class="actions mt-md">
                <button type="submit" class="btn btn-primary">Confirm</button>
                <button type="button" class="btn btn-secondary"
                        onclick="document.getElementById('seasonTeamModal').style.display='none'">Cancel</button>
            </div>
        </form>
    </div>
</div>
```

**Adaptation for Archive Modal** in `match-detail.html`:

```html
<div id="archiveModal" class="modal-overlay">
    <div class="modal-body modal-body--md">
        <h3 class="modal-title">Move Channel to Archive</h3>
        <form th:action="@{/admin/matches/{id}/move-to-archive(id=${match.id})}" method="post">
            <div th:if="${archiveCategories.isEmpty()}" class="alert alert-warning">
                All archive categories are full — see
                <a th:href="@{/operations/discord-integration#creating-archive-category}">runbook</a>.
            </div>
            <div th:each="cat,iter : ${archiveCategories}" class="form-check">
                <input type="radio" th:id="'cat-' + ${iter.index}" name="categoryId"
                       th:value="${cat.id}"
                       th:checked="${defaultSelectionId != null && defaultSelectionId.equals(cat.id)}">
                <label th:for="'cat-' + ${iter.index}"
                       th:text="${cat.name} + ' — ' + ${cat.currentChannelCount} + '/50'"></label>
            </div>
            <div class="actions mt-md">
                <button type="submit" class="btn btn-primary"
                        th:disabled="${archiveCategories.isEmpty()}"
                        data-testid="archive-confirm">Confirm</button>
                <button type="button" class="btn btn-secondary"
                        onclick="document.getElementById('archiveModal').style.display='none'">Cancel</button>
            </div>
        </form>
    </div>
</div>
```

**Notes:**
- `<div class="modal-overlay">` + `<div class="modal-body modal-body--md">` — VERIFIED CSS already shipped (Phase 93 stylesheet).
- Modal opens via the trigger button in the Discord Actions panel (94-02 template) — `onclick="document.getElementById('archiveModal').style.display='flex'"`.
- `defaultSelectionId` Model attribute populated by `MatchController.detail()` via `categoryResolver.defaultSelection(archiveCategories).map(ArchiveCategory::id).orElse(null)`.
- Disable Confirm button + show banner when zero categories — unified zero-state UX per CONTEXT § Specifics 3.

---

#### Plan 94-03 Tests — `DiscordCategoryResolverTest` (Mockito unit)

**Closest analog:** `src/test/java/org/ctc/discord/DiscordEmojiCacheTest.java` (unit-test shape)
**Match quality:** ROLE-MATCH (Mockito + pure-fn assertions)

**Test scenarios:**
- `givenCategoryNameWithoutSuffix_whenMatched_thenNumDefaultsToOne()` — regex variant.
- `givenCategoryNameWithSuffix_whenMatched_thenNumExtracted()` — `(2)`, `(3)`, etc.
- `givenYearMismatch_whenFiltered_thenExcluded()`.
- `givenMixedNums_whenSorted_thenAscending()`.
- `givenAllCategoriesFull_whenDefaultSelection_thenEmpty()`.
- `givenSomeWithRoom_whenDefaultSelection_thenHighestNumWithRoom()`.

#### Plan 94-03 Tests — WireMock-IT (2 classes)

**Closest analog:** Plan 94-02 WireMock scaffold (verbatim).

**`DiscordCategoryResolverWireMockIT`** stubs `GET /guilds/{id}/channels` returning a mix of channels (some type=4 categories with matching/non-matching regex, some type=0 channels with parent_id set). Asserts regex variants, sort order, year-filtering, channel-count derivation.

**`DiscordChannelArchiveServiceWireMockIT`** stubs `PATCH /channels/{id}` with `{parent_id: "..."}`. Happy-path → 200 OK. CategoryFull-path → 400 with `code: 30013` → mapper routes to `DiscordCategoryFullException`. Verify outbound JSON via `wm.verify()` + `equalToJson`.

#### Plan 94-03 Tests — `MatchControllerMoveToArchiveErrorCategoryTest` (Mockito unit)

Verbatim port of `DiscordConfigControllerErrorCategoryTest` shape:

```java
@ParameterizedTest
@CsvSource({"TRANSIENT,transient", "AUTH,auth", "NOT_FOUND,not-found", "CATEGORY_FULL,category-full"})
void givenCategoryEnum_whenLowercaseAndHyphenated_thenMatchesBemClassSuffix(
        Category category, String expected) {
    assertThat(category.name().toLowerCase().replace('_', '-')).isEqualTo(expected);
}
```

#### Plan 94-03 Tests — `ArchiveModalE2ETest` (Playwright)

**Closest analog:** `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java`
**Match quality:** EXACT

**Test scenarios:**
- `givenChannelExists_whenOpenArchiveModal_thenCategoriesAndCountsVisible()` — WireMock stubs listChannels, Playwright clicks "Move to Archive", asserts radio buttons rendered.
- `givenAllCategoriesFull_whenOpenModal_thenWarningBannerAndDisabledConfirm()` — zero-state UX.
- `givenSelectAndConfirm_whenPost_thenHitsMoveToArchiveEndpoint()` — `wm.verify(patchRequestedFor(...))` round-trip.
- Mobile sweep (375×667) — modal-open state per D-06.

---

## Shared Patterns

### Pattern S-01: Sealed Exception Hierarchy + Typed-Catch + applyErrorFlash (Phase 91 D-06/D-07 → Phase 93 INFRA → Phase 94 carry-forward)

**Source:** `src/main/java/org/ctc/discord/web/DiscordConfigController.java:136-147` (verbatim Phase 93 INFRA-03)
**Apply to:** `MatchController.createDiscordChannel` + `MatchController.moveToArchive` (Plan 94-02 + 94-03)

**Excerpt** (DiscordConfigController.java:136-147):

```java
private void applyErrorFlash(RedirectAttributes ra, DiscordApiException e, String action) {
    String message = switch (e.category()) {
        case TRANSIENT -> DiscordApiExceptionMapper.TRANSIENT_MESSAGE;
        case AUTH -> DiscordApiExceptionMapper.AUTH_MESSAGE;
        case NOT_FOUND -> DiscordApiExceptionMapper.NOT_FOUND_MESSAGE;
        case CATEGORY_FULL -> DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE;
    };
    String category = e.category().name().toLowerCase().replace('_', '-');
    log.warn("{} failed: category={}", action, category);
    ra.addFlashAttribute("errorMessage", message);
    ra.addFlashAttribute("errorCategory", category);
}
```

**No-info-leak invariant (T-91-02-IL):** controllers MUST NEVER echo `e.getMessage()` outward. Only the `DiscordApiExceptionMapper.*_MESSAGE` constants reach the UI. Phase 94 adds `AUDIT_FAIL_MESSAGE` to this set.

---

### Pattern S-02: Hand-Rolled `ConcurrentHashMap<String, CachedEntry<V>>` + `Clock` (Phase 93 D-03 → Phase 94 D-05)

**Source:** `src/main/java/org/ctc/discord/DiscordEmojiCache.java` (full file) + `src/main/java/org/ctc/discord/util/CachedEntry.java` (existing record)
**Apply to:** `DiscordRoleCache` (Plan 94-01)

The cache pattern shipped Phase 93 INFRA-01 is now used by TWO classes (emoji + role); confirms the pattern as a repeatable template for any future Discord-side cache (e.g., `DiscordChannelListCache` if performance demands in Phase 95+).

---

### Pattern S-03: `@ToString.Exclude` for Webhook Secrets (T-93-02 invariant)

**Source:** `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` `@ToString(exclude = {"announcementWebhookUrl"})`
**Apply to:** `src/main/java/org/ctc/domain/model/Match.java` — extend existing `@ToString(exclude = {...})` to include `discordChannelWebhookUrl`.

**Excerpt** (Match.java:18 before/after):

```java
// Before
@ToString(exclude = {"matchday", "homeTeam", "awayTeam", "races"})

// After (Phase 94)
@ToString(exclude = {"matchday", "homeTeam", "awayTeam", "races", "discordChannelWebhookUrl"})
```

**Verification:** `MatchToStringTest` (Plan 94-02) — verbatim port of `DiscordGlobalConfigToStringTest`.

---

### Pattern S-04: Flash-Attribute Renderer with BEM Error-Badge (Phase 91 D-07 → Phase 93 → Phase 94)

**Source:** `src/main/resources/templates/admin/layout.html:85-92` (layout-level flash renderer — already in production)
**Apply to:** All Phase 94 controllers' redirect responses; `match-detail.html` and `team-form.html` inherit automatically via `th:replace="~{admin/layout :: layout(...)}"`.

**Excerpt:**

```html
<div th:if="${successMessage}" class="alert alert-success" th:text="${successMessage}"></div>
<div th:if="${errorMessage}" class="alert alert-error">
    <span th:if="${errorCategory}"
          class="error-badge"
          th:classappend="|error-badge--${#strings.toLowerCase(errorCategory)}|"
          th:text="${errorCategory}"></span>
    <span th:text="${errorMessage}"></span>
</div>
```

**Phase 94 contract:** controllers flash `errorCategory ∈ {"transient", "auth", "not-found", "category-full"}` (lowercase + hyphen). `.error-badge--category-full` already shipped Phase 93 admin.css:375 (RESEARCH Open Question 3 — VERIFIED).

---

### Pattern S-05: WireMock IT Scaffold (Phase 93 INFRA-01 → Phase 94 ITs)

**Source:** `src/test/java/org/ctc/discord/DiscordRestClientIT.java:36-61`
**Apply to:** ALL Phase 94 `*WireMockIT.java` test classes (5 new + 1 extension):
- `DiscordChannelServiceWireMockIT`
- `DiscordChannelServicePermissionAuditFailIT`
- `DiscordChannelServiceCleanupFailIT`
- `DiscordCategoryResolverWireMockIT`
- `DiscordChannelArchiveServiceWireMockIT`
- `DiscordRestClientIT` (EXTEND for new typed methods)

**Excerpt (full scaffold):**

```java
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class XxxWireMockIT {
    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
        registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
        registry.add("app.discord.bot-token", () -> "test-bot-token");
        registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
        registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
        registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
    }

    @BeforeEach void resetWireMock() { wm.resetAll(); }
}
```

---

### Pattern S-06: Playwright E2E Scaffold extending `PlaywrightConfig` (Phase 93 → Phase 94)

**Source:** `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java:25-60`
**Apply to:** `TeamFormDiscordRoleDropdownE2ETest` (Plan 94-01), `MatchDetailControllerE2ETest` (Plan 94-02), `ArchiveModalE2ETest` (Plan 94-03)

**Mobile-sweep contract** (per D-06):
- Desktop: 1280×800 (default `PlaywrightConfig` viewport).
- Mobile: 375×667 via `new BrowserContext.NewContextOptions().setViewportSize(new ViewportSize(375, 667))`.
- Screenshots land under `.screenshots/94-{plan-number}/` per [[feedback-screenshots-folder]].

---

### Pattern S-07: BDD Given-When-Then test naming (CLAUDE.md § Test Naming)

**Source:** CLAUDE.md § "Test Naming (Given-When-Then)" + `.planning/codebase/TESTING.md` § Test Categorization
**Apply to:** All 23 new Phase 94 test methods

Pattern enforces:
- Method name: `givenContext_whenAction_thenExpectedResult()` (or `whenAction_thenResult()` if no preconditions).
- Body: `// given` / `// when` / `// then` comments — visible in existing Phase 93 test files (`DiscordEmojiCacheTest`, `DiscordConfigFormTest`, `DiscordGlobalConfigRepositoryIT`).
- Exception assertions: `// when / then` combined for `assertThatThrownBy`.

---

### Pattern S-08: `@Tag` Discipline for Test Categorization (CLAUDE.md + TESTING.md)

| Test Class Suffix | Location | `@Tag` Value | Surefire/Failsafe |
|-------------------|----------|--------------|--------------------|
| `*IT.java` (Spring-context) | `src/test/java/org/ctc/{discord,domain,admin}/...` | `@Tag("integration")` | Failsafe |
| `*E2ETest.java` (Playwright) | `src/test/java/org/ctc/e2e/discord/...` | `@Tag("e2e")` | Failsafe `-Pe2e` |
| `*Test.java` (Mockito-only unit) | anywhere | untagged (project convention) | Surefire |

Verified Phase 93 references:
- `@Tag("integration")` at `DiscordRestClientIT.java:38`, `DiscordGlobalConfigRepositoryIT.java:15`, `DiscordConfigControllerIT.java`
- `@Tag("e2e")` at `DiscordConfigPageE2ETest.java:25`
- Untagged at `DiscordEmojiCacheTest.java`, `DiscordConfigFormTest.java`

---

## No Analog Found

Files for which no close codebase analog exists; planner consumes RESEARCH.md sketches as substitute template:

| New File | Role | Why No Analog | Substitute Reference |
|----------|------|---------------|----------------------|
| `src/main/java/org/ctc/discord/service/DiscordChannelService.java` | service (transactional orchestrator) | First Discord-write service that chains createChannel + createWebhook + fetchChannel + deleteChannel + DB write under one `@Transactional`. | RESEARCH.md § "DiscordChannelService Flow + Audit Architecture" (D-04 contract, 9 steps) + `DiscordConfigController.applyErrorFlash` (typed-catch reference at controller layer; adapt to service-layer throw) |
| `src/main/java/org/ctc/discord/service/DiscordCategoryResolver.java` | service (regex-based filter/sort) | First regex/in-process filter service in `org.ctc.discord.service`. Phase 93 DiscordEmojiCache calls `restClient.fetchGuildEmojis` then transforms; structurally similar but Phase 94 resolver does NOT cache. | RESEARCH.md § "DiscordCategoryResolver" architecture + § "Architecture Patterns" CHAN-03 flow |
| `src/main/java/org/ctc/discord/dto/ArchiveCategory.java` | dto (record) | First non-Discord-wire DTO (purely internal view-data); record-shape twins Channel structurally. | RESEARCH.md § "Architecture Patterns" Recommended Project Structure |
| `src/main/java/org/ctc/discord/dto/DiscordSnowflake.java` | utility (static constants) | First shared validation-constant in Discord package; D-14 explicitly creates the precedent. | CONTEXT D-14 verbatim source |
| `src/main/java/org/ctc/discord/DiscordPermissions.java` | utility (static constants) | First pure-static bitmask-constants class in Discord package. | RESEARCH.md § "Permission Bitmask Reference" verbatim source |
| `src/main/resources/templates/admin/match-detail.html` | template | First per-entity Discord-orchestration detail page; `team-detail.html` provides toolbar/sub-card layout but the Discord Actions panel composition is new. | RESEARCH.md § "Modal Pattern Decision" + Phase 94 CONTEXT § D-01 page-architecture |

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/discord/**` — full Phase 93 INFRA package (cache, exception, mapper, controller, service, dto, model, repository, util)
- `src/main/java/org/ctc/admin/dto/` — Form DTO template (MatchdayForm, TeamForm)
- `src/main/java/org/ctc/admin/controller/` — MatchController, TeamController (existing endpoints)
- `src/main/java/org/ctc/domain/model/` — Team, Match (entity extension pattern + `@ToString(exclude=)`)
- `src/main/java/org/ctc/domain/service/` — TeamManagementService (signature extension), MatchService (extension)
- `src/main/resources/db/migration/` — V8 shape reference for V9/V10
- `src/main/resources/templates/admin/` — team-form, team-detail, match-form, matchday-detail, season-detail (modal pattern), discord-config
- `src/main/resources/static/admin/css/admin.css` — BEM badge palette, modal-overlay, searchable-dropdown
- `src/main/resources/static/admin/js/searchable-dropdown.js` — JS contract for cache-warm dropdown
- `src/test/java/org/ctc/discord/**` — Phase 93 test scaffolds (WireMock-IT, MockMvc-IT, Mockito-unit, ToString-test)
- `src/test/java/org/ctc/e2e/discord/` — Playwright E2E scaffold (DiscordConfigPageE2ETest)

**Negative results (grep searches returning ZERO matches — drive "no analog" classifications):**
- `@Transactional` + `restClient.*` chain in `org.ctc.discord.service.*` (no precedent for Discord-write orchestrator)
- `java.util.regex.Pattern.compile` in `org.ctc.discord.**` (Resolver is first regex-pattern service in this package)
- `<dialog>` element in `src/main/resources/templates/admin/` (modal-overlay div is established; <dialog> has zero usage)
- `findFirstByOrderBy` outside the Phase 93 `DiscordGlobalConfigRepository` (singleton pattern is new but stays in Phase 93 scope)

**Files scanned:** ~40 Java sources (incl. all Phase 93 INFRA files), ~6 SQL migrations, ~15 templates, ~10 test classes.

**Pattern extraction date:** 2026-05-21

---

## PATTERN MAPPING COMPLETE
