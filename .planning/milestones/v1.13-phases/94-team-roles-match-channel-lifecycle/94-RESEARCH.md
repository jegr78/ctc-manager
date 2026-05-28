# Phase 94: Team Roles + Match Channel Lifecycle — Research

**Researched:** 2026-05-21
**Domain:** Discord REST integration (production surface) + Spring Boot 4 admin UI + Flyway V9/V10 + Permission-overwrite audit
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 — Hybrid Match-Detail page** (`/admin/matches/{id}`) + separate `/admin/matches/{id}/edit`; matchday-detail.html keeps a single "→ Detail" link per match-row, no inline-edit. New endpoints on `MatchController`: `GET /{id}`, `GET /{id}/edit`, `POST /save-edit` (or `/save` mode-dispatch — planner discretion), `POST /{id}/create-discord-channel`, `POST /{id}/move-to-archive`. Existing `POST /admin/matches/save` (CREATE flow) stays unchanged.
- **D-02 — Global single-slot** `discord_global_config.current_match_category_id` (VARCHAR(32), NOT NULL DEFAULT ''). Operator sets active category once on `/admin/discord-config`. No per-season / per-phase storage.
- **D-03 — V9 bundles BOTH ALTER TABLEs** in one migration: `V9__add_discord_team_role_and_current_match_category.sql`. Adds `teams.discord_role_id VARCHAR(32)` AND `discord_global_config.current_match_category_id VARCHAR(32) NOT NULL DEFAULT ''`. H2 + MariaDB compatible. No CHECK, no LONGTEXT. V10 stays match-fields-only.
- **D-04 — Audit-fail → Bot DELETE + transactional rollback.** On post-create permission-audit failure: best-effort `restClient.deleteChannel(channelId)`, throw `DiscordAuthException` with hardcoded `AUDIT_FAIL_MESSAGE` constant. On cleanup-DELETE failure: log WARN with orphan channel-ID, append "Cleanup failed: please manually delete channel {id} via Discord." to the user-message. Both Exception paths covered by mapper constants — never `e.getMessage()`. Entire `createMatchChannel` is `@Transactional` so DB write rolls back.
- **D-05 — `DiscordRoleCache`** in `org.ctc.discord` package, structurally identical to `DiscordEmojiCache`: `ConcurrentHashMap<String, CachedEntry<Role>>` keyed by Discord role-ID, 60-min TTL, `Clock` injected. Existing "Refresh Server Roles Cache" button on `/admin/discord-config` (INFRA-03) extended to ALSO call `roleCache.refresh(...)` after `fetchGuildRoles()`. Team-Form renders `.searchable-dropdown` when cache warm; plain-text input + `.badge-warning` when empty. NEVER eager-fetch on Team-Form GET.
- **D-06 — UI-polish CSS fix lands inside Phase 94.** Plan 94-01 ships `admin.css` `.discord-actions` / `.inline-form` / `.btn-group` responsive-wrap rules. Mobile-overflow on `/admin/discord-config` (UAT-03 debt) closes here. Wave-pause after each plan: `playwright-cli` Desktop 1280×800 + Mobile 375×667 screenshots under `.screenshots/94-{plan}/`.
- **D-07 — Three plans, sequential inline** on `gsd/v1.13-discord-integration`. 1:1 mapping CHAN-01 → 94-01, CHAN-02 → 94-02, CHAN-03 → 94-03. No worktrees, no subagents per `[[feedback-inline-sequential-execution]]`.
- **D-08 — Rolling v1.13 milestone PR.** Each plan-ship updates body via `gh pr edit --body-file`. Subject locked for final squash: `feat(v1.13): discord integration & carry-forwards`.
- **D-09 — Standard gates apply.** JaCoCo ≥ 88.88 %, SpotBugs 0, CodeQL exit 0, `EXPORT_ORDER` 24, `SCHEMA_VERSION` 1, Flyway V1-V8 immutable, V9+V10 added by this phase. CI E2E within 17:39 ± 20 %.
- **D-10 — Per-Plan Nyquist VALIDATION.md** ships with each plan; `/gsd-validate-phase 94` runs before next phase.
- **D-11 — `@Tag` convention.** WireMock-ITs → `@Tag("integration")`. Playwright → `@Tag("e2e")` in `org.ctc.e2e.discord`. Unit tests untagged.
- **D-12 — WireMock-IT-only Phase-94 close.** UAT-04 (Live Discord channel-create + permission audit + archive move on operator test guild) deferred to STATE.md `Pending UATs`; operator runs BEFORE Phase 95 plans 95-02/03/04 start. Plan 95-01 (POST-01 entity + skeleton) is unblocked by Phase 94 close alone.
- **D-13 — Production code boundary.** Edits constrained to: `src/main/java/org/ctc/discord/**`, `src/main/java/org/ctc/domain/model/{Team,Match}.java`, `src/main/java/org/ctc/admin/dto/{TeamForm,MatchForm}.java`, `src/main/java/org/ctc/admin/controller/{TeamController,MatchController}.java`, `src/main/java/org/ctc/domain/service/{TeamManagementService,MatchService}.java`, `src/main/resources/db/migration/V9*.sql` + `V10*.sql`, `src/main/resources/templates/admin/{discord-config,team-form,match-detail,match-form-edit,matchday-detail}.html`, `src/main/resources/static/admin/css/admin.css`. No edits to dataimport/backup/sitegen/gt7sync/scoring.
- **D-14 — Snowflake constant extraction** to `org.ctc.discord.dto.DiscordSnowflake` (`PATTERN` + `MESSAGE` + private constructor). `DiscordConfigForm` refactored to reuse (5 `@Pattern` annotations). `TeamForm` adopts. Future `MatchForm` / `SeasonForm` reuse.

### Claude's Discretion

- `MatchForm` package — `org.ctc.admin.dto` (sibling to `MatchdayForm`, `TeamForm`). NO alternative.
- `DiscordPermissions` constants class location — `org.ctc.discord` OR `org.ctc.discord.dto`. Planner picks based on cohesion.
- Exact CSS class names for `.discord-actions` cluster.
- `POST /admin/matches/{id}/save-edit` as NEW route vs. unified `/save` with `id != null` mode-dispatch.
- `Channel` record extension for `permission_overwrites` vs. separate `ChannelWithPermissions` record.
- Archive modal `<dialog>` vs. CSS-overlay div pattern.
- Placeholder `<div class="discord-actions discord-actions--posts">` empty in 94 vs. carrying a `<!-- TODO Phase 95 -->` comment.
- `PermissionOverwrite` `type` field encoding — Phase 94 uses `0` (role); document magic number in `DiscordPermissions` or `PermissionOverwrite`.

### Deferred Ideas (OUT OF SCOPE)

- Per-team-role-color sync from Discord to CTC Manager.
- Match-channel auto-rotation when `current_match_category_id` fills past 50 (operator manually rotates).
- Live-Discord UAT-04 dashboard / one-click reset tool.
- Permission-audit for server-admin role visibility (Discord runtime bypass — documented in T-93-03 forward-ref).
- Match-Detail page graphics preview thumbnails.
- Bulk team-role-assign UI.
- Confirmation modal before Channel DELETE on audit-fail (auto-delete is correct).
- Webhook-URL rotation history.
- Phase 95 POST buttons (POST-01..05), Phase 96 GRAFX-01 + FORUM-01, Phase 95 `discord_post` tracking entity (V11), schedule auto-edit on form-save (POST-05), operator runbook content (DOCS-02).

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **CHAN-01** | `teams.discord_role_id VARCHAR(32)` column (Flyway V9). Team-Form `discordRoleId` field with snowflake validation `^\d{17,20}$`. Plain-text input + live-dropdown of guild-roles when bot reachable (cached 60 min). Operator can clear field to disable channel-creation. Verified by repository IT, validation test, dropdown-on-cache-warm E2E. | Existing `Team` entity + `TeamForm` shape covered. `DiscordRoleCache` mirrors verified `DiscordEmojiCache` template (Phase 93 INFRA-01). `searchable-dropdown.js` contract documented below (§ "Searchable-Dropdown JS Contract"). Snowflake regex pattern verified shared via D-14. |
| **CHAN-02** | (a) V10 ALTER `matches` (7 fields). (b) MatchForm fields (`discordTeaser` max 2000, `streamLink` max 500, `lobbyHost` / `raceDirector` / `streamer` max 100). (c) "Create Discord Channel" button (gated on `discordChannelId==null` AND both team roles set AND `current_match_category_id` set). Channel name `md{N}-{a}-vs-{b}` lowercase dash-separated. Permission-overwrites per design § 4.4. Webhook-create + ID storage. (d) Post-create permission-audit; non-whitelisted role with View → `DiscordAuthException`. Happy-path + audit-mismatch IT. | All Discord REST contracts (POST /guilds/{id}/channels, POST /channels/{id}/webhooks, GET /channels/{id}) verified against `docs.discord.com/developers` (May 2026). Permission bitmasks verified — see § "Discord Permission Bitmask Reference". Composite masks pre-computed below. 9-step service flow + audit/cleanup pattern surfaced in § "DiscordChannelService Flow + Audit Architecture". |
| **CHAN-03** | `DiscordCategoryResolver` regex match `^Match Days Archive (?<year>\d{4})(?: \((?<num>\d+)\))?$` against `restClient.listChannels(guildId)` type=4 results. Modal lists year-matching categories sorted ASC by `num`, displays `{count}/50`. Default-select = highest-num with `<50`. POST patches `parent_id`. `DiscordCategoryFullException` on full / empty → `category-full` badge + runbook link. IT covers regex variants, count, default-selection, patch-call, full path. | `DiscordCategoryFullException` already exists (Phase 93). `ChannelModifyRequest.parentId` already wired. `listChannels` already typed-method. Modal pattern decision documented § "Modal Pattern Decision". Regex compiles in Java — `java.util.regex.Pattern.compile` confirmed feature-complete for the named groups syntax. |

</phase_requirements>

## Summary

Phase 94 is the FIRST Discord-feature surface to call live Discord REST endpoints with business-critical write semantics — channel creation with permission overwrites, webhook creation, post-create permission audit, channel DELETE on audit fail, channel PATCH for archive move. Phase 93 INFRA shipped the platform (DiscordRestClient with sealed exception hierarchy, rate-limit interceptor, emoji cache, exception mapper, V8 admin-config page). The research task here is: surface the exact Discord REST contracts, permission bitmask values, WireMock IT architecture, modal pattern choice, and DTO extension shape so the planner can write three tightly-scoped sequential plans (94-01/02/03) without ambiguity at the wire level.

The platform is in good shape: `DiscordRestClient.execute()` helper handles `RestClientResponseException` → mapper, `ResourceAccessException` → unwrap interceptor exception. The pattern requires **a small extension for DELETE** (returns `Void` body, not `Channel`) — documented below. `Channel` record already carries `@JsonIgnoreProperties(ignoreUnknown=true)` so adding an optional `permission_overwrites` field via Jackson on the fetch-back path will not break Phase 93 ITs. `searchable-dropdown.js` is reusable verbatim. The established modal idiom in the codebase is a `<div class="modal-overlay">` with `<div class="modal-body">` and JS `style.display = 'flex'` (used 2× in `season-detail.html`); there is zero `<dialog>` precedent. Stay with the established CSS-overlay-div pattern unless deliberately introducing a new idiom — rationale below.

**Primary recommendation:** Use the established CSS-overlay-div modal pattern for the Archive modal (consistency wins over HTML5-native), extend `ChannelCreateRequest` with `permission_overwrites` via `@JsonInclude(NON_NULL)` (keeps Phase 93 ITs unaffected), extend `Channel` record with an optional `permission_overwrites` list (single record, no parallel `ChannelWithPermissions`), and add the three new `DiscordRestClient` methods (`createWebhook`, `fetchChannel`, `deleteChannel`) through the existing `execute()` helper — DELETE needs a `Void`-returning variant.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Team-role mapping persistence (CHAN-01) | Database / Storage | API / Backend | DB column + JPA field; service stitches via existing TeamManagementService.save signature. |
| Team-role dropdown rendering (CHAN-01) | Frontend Server (SSR) | API / Backend | Controller injects `discordRoles` Model from `DiscordRoleCache.snapshot()`; Thymeleaf renders. No client-side fetch. |
| Searchable-dropdown filter behavior (CHAN-01) | Browser / Client | — | Existing `searchable-dropdown.js` runs entirely in-browser. Zero backend hop. |
| Match-Detail page render (CHAN-02) | Frontend Server (SSR) | API / Backend | New `GET /admin/matches/{id}` → `MatchService` data prep → template; OSIV-friendly. |
| Match-Form persistence (CHAN-02) | API / Backend | Database / Storage | POST `/save-edit` → MatchService updates 5 fields atomically. |
| Discord channel creation (CHAN-02) | API / Backend | External Service | `DiscordChannelService.createMatchChannel` orchestrates DiscordRestClient calls; @Transactional. |
| Permission audit (CHAN-02) | API / Backend | External Service | Read-back via `fetchChannel`, in-process assertion, no client visibility. Failure → DELETE + rollback. |
| Webhook URL storage (CHAN-02) | Database / Storage | — | `@ToString.Exclude` on `Match.discordChannelWebhookUrl` per T-93-02 invariant. |
| Archive modal UI (CHAN-03) | Browser / Client | Frontend Server (SSR) | Modal HTML + categories list rendered server-side; open/close behavior client-side. |
| Category resolution + count (CHAN-03) | API / Backend | External Service | `DiscordCategoryResolver` calls `listChannels` once per modal render; regex + count derivation in-process. |
| Move-to-archive POST (CHAN-03) | API / Backend | External Service | Controller → `DiscordRestClient.modifyChannel(channelId, new ChannelModifyRequest(null, parentId))`. |

## Standard Stack

### Core (already present in v1.13 baseline)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.x | App framework | `[CITED: .planning/codebase/STACK.md]` Existing baseline; no change. |
| Spring `RestClient` | Spring 6.1+ | Discord REST calls | `[VERIFIED: src/main/java/org/ctc/discord/DiscordRestClient.java]` Already configured per `[[feedback-spring-native-preference]]`. Phase 94 extends — does not introduce. |
| Jackson | Spring-Boot-managed | DTO JSON ser/deser | `[VERIFIED: src/main/java/org/ctc/discord/dto/Channel.java]` Used via `@JsonProperty`, `@JsonIgnoreProperties`. Phase 94 adds `@JsonInclude(NON_NULL)` on optional fields. |
| Lombok | project-managed | Boilerplate reduction | `[CITED: CLAUDE.md § Lombok Usage]` Existing convention. |
| Flyway | project-managed | Schema migrations | `[CITED: CLAUDE.md § Constraints]` V1-V8 immutable; V9+V10 added here. |
| WireMock | per `pom.xml` | Discord IT mocks | `[VERIFIED: src/test/java/org/ctc/discord/DiscordRestClientIT.java]` `WireMockExtension` + `WireMockConfiguration.options().dynamicPort()` pattern established. |
| JUnit 5 | per `pom.xml` | Test runner | Untagged for unit, `@Tag("integration")` for ITs, `@Tag("e2e")` for Playwright. |
| Playwright | compile-scope | E2E + UI sweep | `[CITED: CLAUDE.md § Constraints]` Used both for E2E tests AND visual screenshot sweep via `playwright-cli` (D-06). |

### Phase 94 Adds (zero new dependencies — STATE.md baseline preserved)

| Library | Reason | Why Not |
|---------|--------|---------|
| Discord library (JDA, Discord4J, …) | Wraps REST + Gateway | `[CITED: .planning/PROJECT.md § Current Milestone v1.13]` "zero new production dependencies" invariant; outbound-only model means no Gateway WebSocket is needed; Phase 93 self-built RestClient covers all needs. |
| Caffeine / Guava `CacheBuilder` | TTL caching | `[CITED: 93-CONTEXT D-03]` Hand-rolled `ConcurrentHashMap<String, CachedEntry<T>>` already in place; Phase 94 D-05 reuses. |
| Markdown library | Render `discordTeaser` | `[CITED: 94-CONTEXT.md § Specifics]` Discord renders Markdown server-side when the webhook posts; admin templates pass through as raw text. No library needed. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `<div class="modal-overlay">` + JS `style.display='flex'` | HTML5 `<dialog>` + `.showModal()` | `<dialog>` is HTML5-native + accessible OOTB. But codebase has 2× modal-overlay usage in `season-detail.html`, zero `<dialog>`. Consistency wins; modal-overlay is the established pattern. **Recommended: `<div class="modal-overlay">`** to match precedent. |
| Extend existing `Channel` record with optional `permission_overwrites` | New `ChannelWithPermissions` record | Single record avoids type duplication; `@JsonIgnoreProperties(ignoreUnknown=true)` + new field with `@JsonInclude(NON_NULL)` means Phase 93 ITs see no change. **Recommended: extend `Channel`.** |
| Unified `POST /admin/matches/save` with `id != null` mode-dispatch | New `POST /admin/matches/save-edit` route | Existing `save` only handles CREATE flow (no `id` param). Distinct endpoint avoids accidentally breaking CSRF assertions in existing tests. **Recommended: NEW `POST /admin/matches/{id}/save-edit`.** Keeps CLAUDE.md § Backward Compatibility (legacy `/save` URL unchanged). |
| Standalone `DiscordPermissions` class in `org.ctc.discord` | Inner class on `PermissionOverwrite` | Constants are reused across the service AND the IT assertions — standalone class is cleanest. **Recommended: `org.ctc.discord.DiscordPermissions`** as sibling to `DiscordEmojiCache`, `DiscordRoleCache`, `DiscordTimestamps`. |

**Installation:** *None. Zero new dependencies per STATE.md baseline.*

**Version verification:** Not applicable — no new packages.

## Package Legitimacy Audit

> **Required when phase installs external packages.** Phase 94 installs zero new packages — this section is structurally satisfied by the "zero new dependencies" invariant `[CITED: 94-CONTEXT.md § Quality Gates D-09]`. The slopcheck gate would have run as graceful-degradation (no `pip` available on this Mac shell — `pip: command not found`) and tagged all entries `[ASSUMED]`, but the table is empty so nothing to verify.

| Package | Registry | Disposition |
|---------|----------|-------------|
| *(none)* | — | — |

## Architecture Patterns

### System Architecture Diagram

```
                          BROWSER
                             │
                             ▼  HTTP GET /admin/teams/{id}/edit
                  ┌──────────────────────┐
                  │   TeamController     │── reads discordRoles ◀── DiscordRoleCache.snapshot()
                  │   .edit()            │                              │ (60-min TTL)
                  └──────────┬───────────┘                              │
                             │                                          │
                             ▼  team-form.html                          │
                       (cache warm → dropdown; cache empty → plain-text)│
                                                                        │
   Operator clicks "Refresh Server Roles" on /admin/discord-config      │
                             │                                          │
                             ▼  POST /admin/discord-config/refresh-roles-cache
                  ┌──────────────────────┐                              │
                  │ DiscordConfigCtrl    │── fetchGuildRoles ──▶ DiscordRestClient ─▶ Discord REST
                  │ .refreshRolesCache() │                                            (GET /guilds/{id}/roles)
                  └──────────┬───────────┘                              │
                             │                                          │
                             └─── roleCache.refresh(roles) ─────────────┘

  ─────────────────── CHAN-02 channel-creation flow ────────────────────

  POST /admin/matches/{id}/create-discord-channel
                             │
                             ▼
              ┌─────────────────────────────────┐
              │  MatchController.createDiscord  │   typed-catch on DiscordApiException
              │  Channel(id)                    │   → switch(e) → flash errorCategory
              └────────────┬────────────────────┘
                           │
                           ▼  @Transactional
              ┌─────────────────────────────────┐
              │ DiscordChannelService           │
              │ .createMatchChannel(match)      │
              └────┬───────┬───────┬───────┬────┘
                   │       │       │       │
       1. assert  ─┘       │       │       │
       2. compute name     │       │       │
       3. build overwrites │       │       │
                           ▼       │       │
                   POST /guilds/{id}/channels        ─▶ Discord (creates channel)
                           │       │       │
                           ◀─ Channel{id,name,…}     ◀── 201 Created
                           │       │       │
                           ▼       │       │  4. createChannel returns
                   POST /channels/{cid}/webhooks     ─▶ Discord (creates webhook)
                           │       │       │
                           ◀─ Webhook{id,token,url}  ◀── 200 OK
                           │       │       │  5. createWebhook returns
                           ▼       │       │
                   GET /channels/{cid}                ─▶ Discord (fetch back)
                           │       │       │
                           ◀─ Channel + permission_overwrites
                           │       │       │  6. fetchChannel returns
                           ▼       │       │
                  ┌────────────────────────┐
                  │  assertPermissionAudit │
                  │  (count == 3,          │
                  │   roles match home+    │
                  │   away,                │
                  │   no extra View)       │
                  └─┬──────────────┬───────┘
                    │              │
                AUDIT-OK       AUDIT-FAIL
                    │              │
                    │              ▼
                    │      DELETE /channels/{cid}  ─▶ Discord (cleanup)
                    │              │       │
                    │     ┌────────┴───────┴───────┐
                    │  cleanup-OK          cleanup-FAIL
                    │     │                   │
                    │     ▼                   ▼
                    │   throw DiscordAuth   throw DiscordAuth + log WARN
                    │   Exception           + append "manually delete"
                    │   (audit msg)         to user-message
                    │     │                   │
                    │     ▼                   ▼  @Transactional rolls back DB
                    │   (no DB write)        (no DB write)
                    │
                    ▼  8. match.discordChannelId = channel.id
                    │  8. match.discordChannelWebhookUrl = webhook.url
                    │  9. matchRepository.save(match) → COMMIT

  ─────────────────── CHAN-03 archive-move flow ───────────────────────

  Operator opens Match-Detail → server pre-fetches archive categories
                             │
                             ▼  GET /admin/matches/{id}
              ┌─────────────────────────────────┐
              │ MatchController.detail(id)      │── ArchiveCategory[] ◀── DiscordCategoryResolver
              │                                 │                            │
              └────────────┬────────────────────┘                            │
                           ▼                                                 │
                  match-detail.html renders modal hidden + categories radio  │
                                                                             │
              GET /guilds/{id}/channels ◀── listChannels ◀───────────────────┘
                           │                                                 │
                           ◀── List<Channel> (incl. type=4 categories)      │
                                                                             │
              Filter regex + sort by num + count children                    │
                                                                             │
  Operator selects category, clicks Confirm                                  │
                             │                                               │
                             ▼  POST /admin/matches/{id}/move-to-archive     │
              ┌─────────────────────────────────┐                            │
              │ MatchController.moveToArchive   │── modifyChannel ──▶ Discord (PATCH parent_id)
              │                                 │── on CategoryFull →        │
              └────────────┬────────────────────┘   flash "category-full"    │
                           │                        + runbook link           │
                           ▼                                                 │
                  redirect to /admin/matches/{id}
```

### Recommended Project Structure (deltas only — Phase 94)

```
src/main/java/org/ctc/
├── admin/
│   ├── controller/
│   │   ├── MatchController.java        # +5 endpoints
│   │   └── TeamController.java         # +discordRoles model attr
│   └── dto/
│       ├── MatchForm.java              # NEW — 5 Discord fields
│       └── TeamForm.java               # +discordRoleId
├── discord/
│   ├── DiscordPermissions.java         # NEW — bitmask constants
│   ├── DiscordRoleCache.java           # NEW — clone of EmojiCache
│   ├── dto/
│   │   ├── ArchiveCategory.java        # NEW record — id,name,num,count
│   │   ├── Channel.java                # +permission_overwrites optional
│   │   ├── ChannelCreateRequest.java   # +permission_overwrites
│   │   ├── DiscordSnowflake.java       # NEW — PATTERN + MESSAGE
│   │   ├── PermissionOverwrite.java    # NEW record — id,type,allow,deny
│   │   └── Webhook.java                # NEW record — id,token,url,channel_id
│   ├── DiscordRestClient.java          # +createWebhook,fetchChannel,deleteChannel
│   ├── exception/
│   │   └── DiscordApiExceptionMapper.java  # +AUDIT_FAIL_MESSAGE constant
│   └── service/
│       ├── DiscordCategoryResolver.java     # NEW — regex + count + default-selection
│       └── DiscordChannelService.java       # NEW — 9-step transactional flow
└── domain/
    └── model/
        ├── Match.java                  # +7 fields, @ToString.Exclude on webhookUrl
        └── Team.java                   # +discordRoleId
src/main/resources/
├── db/migration/
│   ├── V9__add_discord_team_role_and_current_match_category.sql   # NEW (bundled)
│   └── V10__add_matches_discord_and_scheduling_fields.sql         # NEW
├── static/admin/css/
│   └── admin.css                       # +.discord-actions cluster (D-06)
└── templates/admin/
    ├── discord-config.html             # +currentMatchCategoryId form-row
    ├── match-detail.html               # NEW — full page
    ├── match-form-edit.html            # NEW — edit form
    ├── matchday-detail.html            # +"→ Detail" link per match-row
    └── team-form.html                  # +discordRoleId field
```

### Pattern 1: DiscordChannelService Flow + Audit Architecture

**What:** Single `@Transactional` method `createMatchChannel(Match match)` orchestrates 9 steps. Steps 4–7 catch `DiscordApiException`; audit fail OR any exception between create and DB-save triggers best-effort DELETE + rollback.

**When to use:** CHAN-02 implementation.

**Example (reference shape from D-04 contract):**

```java
// Source: 94-CONTEXT.md § D-04 (locked)
@Transactional
public void createMatchChannel(Match match) throws DiscordApiException {
    // 1. assert preconditions
    if (match.getHomeTeam().getDiscordRoleId() == null
            || match.getAwayTeam().getDiscordRoleId() == null
            || globalConfig.getCurrentMatchCategoryId() == null
            || globalConfig.getCurrentMatchCategoryId().isBlank()) {
        throw new BusinessRuleException("Channel creation requires both team Discord roles and a current match category.");
    }
    // 2. compute channel name (lowercase, dash-separated)
    String name = ("md" + match.getMatchday().getNumber() + "-"
            + match.getHomeTeam().getShortName() + "-vs-"
            + match.getAwayTeam().getShortName()).toLowerCase();
    // 3. build overwrites
    var overwrites = List.of(
        new PermissionOverwrite(globalConfig.getGuildId(), 0,                       // @everyone (role)
            "0", String.valueOf(DiscordPermissions.EVERYONE_DENY_VIEW_MASK)),
        new PermissionOverwrite(match.getHomeTeam().getDiscordRoleId(), 0,
            String.valueOf(DiscordPermissions.TEAM_MEMBER_ALLOW_MASK),
            String.valueOf(DiscordPermissions.TEAM_MEMBER_DENY_MASK)),
        new PermissionOverwrite(match.getAwayTeam().getDiscordRoleId(), 0,
            String.valueOf(DiscordPermissions.TEAM_MEMBER_ALLOW_MASK),
            String.valueOf(DiscordPermissions.TEAM_MEMBER_DENY_MASK)));
    var req = new ChannelCreateRequest(name, 0, globalConfig.getCurrentMatchCategoryId(), overwrites);
    // 4. createChannel
    Channel channel = restClient.createChannel(globalConfig.getGuildId(), req);
    // 5. createWebhook
    Webhook webhook = restClient.createWebhook(channel.id(), "CTC Manager");
    // 6+7. permission audit + cleanup-on-fail
    try {
        assertPermissionAudit(channel.id(),
            match.getHomeTeam().getDiscordRoleId(),
            match.getAwayTeam().getDiscordRoleId());
    } catch (DiscordAuthException auditEx) {
        try {
            restClient.deleteChannel(channel.id());
        } catch (DiscordApiException cleanupEx) {
            log.warn("Audit-fail cleanup DELETE failed for channel {}: {}", channel.id(), cleanupEx.toString());
            throw new DiscordAuthException(
                DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE
                    + " Cleanup failed: please manually delete channel " + channel.id() + " via Discord.",
                auditEx);
        }
        throw auditEx;
    }
    // 8. update entity
    match.setDiscordChannelId(channel.id());
    match.setDiscordChannelWebhookUrl(webhook.url());
    // 9. save → COMMIT
    matchRepository.save(match);
}
```

### Pattern 2: Permission Bitmask Composite

**What:** `DiscordPermissions` exposes 3 composite `long` masks per design spec § 4.4.

**Example:** See § "Discord Permission Bitmask Reference" below for verified bit positions.

### Anti-Patterns to Avoid

- **Echoing `e.getMessage()` in flash attributes.** T-91-02-IL invariant: NEVER echo Discord exception messages to operator (info-leak — token fragments, internal IDs, IP addresses can appear in stacktrace `.getMessage()` chains). Use hardcoded `DiscordApiExceptionMapper.*_MESSAGE` constants only `[CITED: 94-CONTEXT.md § D-04]`.
- **Channel name without pre-normalization.** Discord normalizes server-side (rejects uppercase + spaces) but pre-formatting to `lowercase-dashed` prevents UI flicker when operator compares expected vs. actual channel name `[CITED: 94-CONTEXT.md § Specifics]`.
- **Eager guild-roles fetch on Team-Form GET.** Per D-05: breaks the principle that DB-only admin pages should not require external service availability. Cache-only.
- **Inline edit POST per Discord field.** Per D-01: CSRF surface + Mobile UX suffer; single MatchForm + `/save-edit` endpoint instead.
- **`e.getMessage()` echo on cleanup-fail.** Use the constant + append-on-cleanup-fail pattern (D-04).
- **Hand-rolled HTTP client (`java.net.http.HttpClient`).** Spring `RestClient` is already wired and authenticated `[CITED: feedback-spring-native-preference]`.

## Discord Permission Bitmask Reference

> **HIGH confidence** — verified directly against `docs.discord.com/developers/topics/permissions` `[VERIFIED: docs.discord.com (May 2026)]`.

### Individual Permission Constants

| Permission | Bit | Hex | Decimal | Constant Name in `DiscordPermissions` |
|------------|-----|-----|---------|---------------------------------------|
| CREATE_INSTANT_INVITE | `1 << 0`  | `0x0000000000000001` | 1 | `CREATE_INSTANT_INVITE` |
| MANAGE_CHANNELS       | `1 << 4`  | `0x0000000000000010` | 16 | `MANAGE_CHANNELS` |
| ADD_REACTIONS         | `1 << 6`  | `0x0000000000000040` | 64 | `ADD_REACTIONS` |
| VIEW_CHANNEL          | `1 << 10` | `0x0000000000000400` | 1024 | `VIEW_CHANNEL` |
| SEND_MESSAGES         | `1 << 11` | `0x0000000000000800` | 2048 | `SEND_MESSAGES` |
| MANAGE_MESSAGES       | `1 << 13` | `0x0000000000002000` | 8192 | `MANAGE_MESSAGES` |
| EMBED_LINKS           | `1 << 14` | `0x0000000000004000` | 16384 | `EMBED_LINKS` |
| ATTACH_FILES          | `1 << 15` | `0x0000000000008000` | 32768 | `ATTACH_FILES` |
| READ_MESSAGE_HISTORY  | `1 << 16` | `0x0000000000010000` | 65536 | `READ_MESSAGE_HISTORY` |
| MENTION_EVERYONE      | `1 << 17` | `0x0000000000020000` | 131072 | `MENTION_EVERYONE` |
| USE_EXTERNAL_EMOJIS   | `1 << 18` | `0x0000000000040000` | 262144 | `USE_EXTERNAL_EMOJIS` |
| CONNECT (voice)       | `1 << 20` | `0x0000000000100000` | 1048576 | `CONNECT` |
| SPEAK (voice)         | `1 << 21` | `0x0000000000200000` | 2097152 | `SPEAK` |
| MANAGE_WEBHOOKS       | `1 << 29` | `0x0000000020000000` | 536870912 | `MANAGE_WEBHOOKS` |
| MANAGE_THREADS        | `1 << 34` | `0x0000000400000000` | 17179869184 | `MANAGE_THREADS` |
| USE_EXTERNAL_STICKERS | `1 << 37` | `0x0000002000000000` | 140737488355328 | `USE_EXTERNAL_STICKERS` |

### Composite Masks (per design spec § 4.4 + REQ CHAN-02)

**REQ CHAN-02 acceptance text** (verbatim): *"allow View+Send+React+Attach+Embed+History+ExternalEmojis+ExternalStickers; deny Voice+ManageChannels+ManageMessages+ManageThreads+ManageWebhooks+CreateInvite+MentionEveryone"*

`TEAM_MEMBER_ALLOW_MASK` = OR-of: VIEW_CHANNEL | SEND_MESSAGES | ADD_REACTIONS | ATTACH_FILES | EMBED_LINKS | READ_MESSAGE_HISTORY | USE_EXTERNAL_EMOJIS | USE_EXTERNAL_STICKERS

| Component | Bit Value |
|-----------|-----------|
| VIEW_CHANNEL          | 1024 |
| SEND_MESSAGES         | 2048 |
| ADD_REACTIONS         | 64 |
| ATTACH_FILES          | 32768 |
| EMBED_LINKS           | 16384 |
| READ_MESSAGE_HISTORY  | 65536 |
| USE_EXTERNAL_EMOJIS   | 262144 |
| USE_EXTERNAL_STICKERS | 140737488355328 |
| **OR sum (decimal)**  | **140737488735808** |
| **OR sum (hex)**      | **`0x0000002000005EC0`** `[ASSUMED: arithmetic — recompute in source code as the OR-expression; never hardcode the composite]` |

`TEAM_MEMBER_DENY_MASK` = OR-of: CONNECT | SPEAK | MANAGE_CHANNELS | MANAGE_MESSAGES | MANAGE_THREADS | MANAGE_WEBHOOKS | CREATE_INSTANT_INVITE | MENTION_EVERYONE

| Component | Bit Value |
|-----------|-----------|
| CONNECT               | 1048576 |
| SPEAK                 | 2097152 |
| MANAGE_CHANNELS       | 16 |
| MANAGE_MESSAGES       | 8192 |
| MANAGE_THREADS        | 17179869184 |
| MANAGE_WEBHOOKS       | 536870912 |
| CREATE_INSTANT_INVITE | 1 |
| MENTION_EVERYONE      | 131072 |
| **OR sum (decimal)**  | **17719906033** |
| **OR sum (hex)**      | **`0x0000000420139011`** `[ASSUMED: arithmetic — recompute as OR-expression in source]` |

`EVERYONE_DENY_VIEW_MASK` = VIEW_CHANNEL = `1024L` (single bit; trivial).

**Source code recommendation:** Define the components as `static final long` constants and compute composites as OR-expressions at the class level so the bit math is auditable in code:

```java
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

### Permission Overwrite `type` Field Encoding

`[VERIFIED: docs.discord.com/developers/resources/channel — Permission Overwrite Object]` — `type` is `integer`; `0 = role`, `1 = member`. Phase 94 emits only `0` (role-based overwrites). Document via the `OVERWRITE_TYPE_ROLE = 0` constant.

`[VERIFIED: docs.discord.com/developers/resources/channel — Permission Overwrite Object]` — `allow` and `deny` are serialized as **strings** (because the values can exceed JavaScript's 53-bit safe integer range). Send as `String.valueOf(bitmask)` in JSON.

## Discord REST API Contracts

> All wire shapes `[VERIFIED: docs.discord.com (May 2026)]`. Codebase already pins `/api/v10` base via `app.discord.base-url` `[VERIFIED: src/main/java/org/ctc/discord/DiscordRestClient.java]`.

### POST `/guilds/{guild.id}/channels`

**Request body (Phase 94 fields only — record `ChannelCreateRequest`):**

```json
{
  "name": "md1-clr-vs-tnt",
  "type": 0,
  "parent_id": "1234567890123456789",
  "permission_overwrites": [
    {"id": "guildId",       "type": 0, "allow": "0",                  "deny": "1024"},
    {"id": "homeTeamRoleId","type": 0, "allow": "140737488735808",    "deny": "17719906033"},
    {"id": "awayTeamRoleId","type": 0, "allow": "140737488735808",    "deny": "17719906033"}
  ]
}
```

**Success response:** `201 Created` (per Discord convention; existing IT uses `okJson` = 200, but contractually 201 is what Discord returns — WireMock stubs MAY use either since RestClient's `.retrieve().body(Channel.class)` does not gate on exact status code, only on 2xx-success vs. mapper-routed 4xx/5xx). Body is a full Channel object.

**Edge cases & rate-limit headers:**
- `X-RateLimit-Bucket`, `X-RateLimit-Remaining`, `X-RateLimit-Reset-After` — already consumed by `DiscordRateLimitInterceptor` (Phase 93).
- `429 Too Many Requests` with `Retry-After` — already routed by interceptor + retry-3× pattern.
- `403` (missing MANAGE_CHANNELS bot permission) → `DiscordAuthException` via mapper.
- `404` (guild not found) → `DiscordNotFoundException`.
- `400` with `code: 30013` (max channels per category) → `DiscordCategoryFullException` `[VERIFIED: src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java]`.

### POST `/channels/{channel.id}/webhooks`

**Request body:**

```json
{"name": "CTC Manager"}
```

`[VERIFIED: docs.discord.com — Webhook Resource]` — `name` is required (1–80 chars). `avatar` is optional. The bot's authenticating Bot-Token must have MANAGE_WEBHOOKS in the target channel.

**Success response:** `200 OK` per Discord webhook docs (also seen as 201 in some channel-create flows; safer to stub 200 in WireMock per Phase 93 IT convention). Body includes:

```json
{
  "id":              "987654321",
  "token":           "AbCdEf-secret-token-fragment",
  "url":             "https://discord.com/api/webhooks/987654321/AbCdEf-secret-token-fragment",
  "channel_id":      "111222333",
  "name":            "CTC Manager",
  "application_id":  "bot-app-snowflake",
  "type":            1
}
```

**`Webhook` DTO recommendation:**

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record Webhook(String id, String token, String url,
                      @JsonProperty("channel_id") String channelId) {
}
```

URL field is provided by Discord (no need to reconstruct), but if `url` is null in some edge cases, fall back to `"https://discord.com/api/webhooks/" + id + "/" + token`.

### GET `/channels/{channel.id}`

**Success response:** `200 OK`. Body is a full Channel object with `permission_overwrites` populated as an array:

```json
{
  "id": "1112223334445556",
  "name": "md1-clr-vs-tnt",
  "type": 0,
  "guild_id": "...",
  "parent_id": "...",
  "permission_overwrites": [
    {"id": "guildId",      "type": 0, "allow": "0",               "deny": "1024"},
    {"id": "homeRoleId",   "type": 0, "allow": "140737488735808", "deny": "17719906033"},
    {"id": "awayRoleId",   "type": 0, "allow": "140737488735808", "deny": "17719906033"}
  ]
}
```

### PATCH `/channels/{channel.id}`

**Request body (Phase 94 archive-move case):**

```json
{"parent_id": "1234567890"}
```

**Accepted fields:** `name`, `position`, `topic`, `permission_overwrites`, `parent_id`, … (Phase 94 uses only `parent_id`). `[VERIFIED: docs.discord.com/developers/resources/channel — Modify Channel]`.

**Success response:** `200 OK`, body is updated Channel.

**400 Bad Request with `code: 30013`** → `DiscordCategoryFullException` (already routed by mapper). This is the first production path that fires this exception type.

### DELETE `/channels/{channel.id}`

`[VERIFIED: docs.discord.com — Delete/Close Channel]` — Response is `200 OK` with the deleted channel object in the body. **NOT 204 No Content** as the existing CONTEXT.md `<canonical_refs>` line suggested — verified against current docs May 2026. Either way, the response body is **ignored** by Phase 94 (best-effort cleanup); the existing `DiscordRestClient.execute()` helper handles both shapes if we wire a `Void`-returning variant (see § "RestClient DELETE Helper Shape").

**Edge cases:**
- 403 (missing MANAGE_CHANNELS) → `DiscordAuthException` via mapper — this is the "cleanup-fail" branch in D-04.
- 404 (channel already deleted) → `DiscordNotFoundException` — also routes to "cleanup-fail" but note: this is effectively a "no-op success" for our use case. **Recommendation:** treat 404 as success-equivalent for the audit-cleanup path (channel is gone; that's what we wanted). Document this in the IT.

## Channel Record Extension Strategy

> Resolves CONTEXT.md `Claude's Discretion` item 5.

**Current shape** (`[VERIFIED: src/main/java/org/ctc/discord/dto/Channel.java]`):

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record Channel(String id, String name, int type,
                      @JsonProperty("parent_id") String parentId) {}
```

**Recommended extension:**

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record Channel(String id, String name, int type,
                      @JsonProperty("parent_id") String parentId,
                      @JsonProperty("permission_overwrites") List<PermissionOverwrite> permissionOverwrites) {

    // Convenience for fetchChannel paths that don't populate overwrites
    public Channel(String id, String name, int type, String parentId) {
        this(id, name, type, parentId, null);
    }
}
```

**Why:**
- `@JsonIgnoreProperties(ignoreUnknown=true)` already in place — adding a field is forward-compatible.
- Phase 93 IT (`DiscordRestClientIT.givenCreateChannel`) constructs `new Channel("c1","matchday-1",0,"cat1")` — convenience-constructor keeps it green without source edits.
- A separate `ChannelWithPermissions` record duplicates 4 fields; not worth the cohesion cost.
- Jackson deserialization handles a missing `permission_overwrites` JSON field by defaulting to `null` per record-component contract.
- `@JsonInclude(Include.NON_NULL)` at the record level lets the **outgoing** `ChannelCreateRequest`-side omission stay clean.

**`ChannelCreateRequest` extension:**

```java
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

This keeps `DiscordRestClientIT.givenCreateChannel` (3-arg constructor) green.

## Searchable-Dropdown JS Contract

> Resolves CHAN-01 dropdown integration question.

`[VERIFIED: src/main/resources/static/admin/js/searchable-dropdown.js]` — Each `.searchable-dropdown` container must contain:

| Selector | Role |
|----------|------|
| `.dropdown-input` | Visible text input the operator types into. Filters list on each `input` event. |
| `input[type="hidden"]` | Bound to form field (`*{discordRoleId}`). Set on `.dropdown-item.click`. |
| `.dropdown-list` | The list container (hidden by default; shown on `.dropdown-input.focus`). |
| `.dropdown-item` | Each item; carries `data-id="<roleId>"` and `data-label="<roleName>"`. Item click sets hidden input + clears the list. |

**Pre-select behavior:** If `hidden.value` matches a `.dropdown-item[data-id=...]`, the visible input is populated with that item's `data-label`. So Phase 94 just needs to render `*{discordRoleId}` as the hidden field value; preselection is automatic.

**Cache-warm template (recommended Thymeleaf shape):**

```html
<div class="searchable-dropdown" th:if="${!discordRoles.isEmpty()}">
    <input type="text" class="dropdown-input" placeholder="Type to search Discord roles…" />
    <input type="hidden" th:field="*{discordRoleId}" />
    <div class="dropdown-list">
        <div th:each="role : ${discordRoles.values()}"
             class="dropdown-item"
             th:attr="data-id=${role.id()},data-label=${role.name()}"
             th:text="${role.name()}"></div>
    </div>
    <span th:errors="*{discordRoleId}" class="error-badge error-badge--auth"></span>
</div>
<div th:if="${discordRoles.isEmpty()}" class="form-group">
    <input type="text" th:field="*{discordRoleId}"
           placeholder="Discord role snowflake (17–20 digits)"
           pattern="^\d{17,20}$" />
    <span class="badge-warning">
        Refresh "Server Roles Cache" on
        <a th:href="@{/admin/discord-config}">/admin/discord-config</a>
        to enable dropdown picker.
    </span>
    <span th:errors="*{discordRoleId}" class="error-badge error-badge--auth"></span>
</div>
```

**Important:** The `discordRoles` model attribute MUST be a `Map<String, Role>` (from `DiscordRoleCache.snapshot()`), not a `List<Role>` — Thymeleaf iterates over `.values()`. Alternative: change the controller to pass `discordRoles.values()` directly as a `Collection<Role>`. Either works.

## Modal Pattern Decision

> Resolves CONTEXT.md `Claude's Discretion` item 6.

`[VERIFIED: src/main/resources/templates/admin/season-detail.html lines 96, 156]` — the established admin-modal pattern is a CSS overlay div with JS toggle:

```html
<div id="archiveModal" class="modal-overlay">
    <div class="modal-body modal-body--md">
        <h3 class="modal-title">Move Channel to Archive</h3>
        <form th:action="@{/admin/matches/{id}/move-to-archive(id=${match.id})}" method="post">
            <!-- ... radio buttons ... -->
            <div class="actions mt-md">
                <button type="submit" class="btn btn-primary">Confirm</button>
                <button type="button" class="btn btn-secondary"
                        onclick="document.getElementById('archiveModal').style.display='none'">Cancel</button>
            </div>
        </form>
    </div>
</div>
```

**Opening:** `document.getElementById('archiveModal').style.display = 'flex'` (inline `onclick` on the trigger button is the existing pattern in `season-detail.html`).

**Why not `<dialog>`:**
- Zero `<dialog>` usage in the codebase today; introducing it adds a new idiom to maintain.
- `.modal-overlay` + `.modal-body` CSS already shipped (`admin.css` lines 1104–1129).
- `<dialog>` has accessibility wins but the team has an established pattern; consistency wins for this phase.

**Defer:** A future polish phase MAY convert all admin modals to `<dialog>` for accessibility — DISC-FUTURE-style backlog candidate, not Phase-94 scope.

## RestClient DELETE Helper Shape

> Resolves CONTEXT.md `<code_context>` question on the existing `execute()` helper.

`[VERIFIED: src/main/java/org/ctc/discord/DiscordRestClient.java lines 118–139]` — existing `execute()` returns `T` and is parameterized over `RestCall<T>` (a `Supplier`-like functional interface). It does NOT have a `Void` overload today.

**Phase 94 must add `deleteChannel(channelId)`** — minimal extension:

```java
// Add to DiscordRestClient

public void deleteChannel(String channelId) throws DiscordApiException {
    execute(() -> {
        bot.delete()
            .uri("/channels/{channelId}", channelId)
            .retrieve()
            .toBodilessEntity();  // discards 200-OK body
        return null;
    });
}

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

private record WebhookCreateRequest(String name) {}
```

**Why `toBodilessEntity()`:** Discord returns the deleted channel object in DELETE responses (200 OK with body). We don't need it. `toBodilessEntity()` consumes the response stream without deserialization and works regardless of whether Discord returns 200 + body or 204 No Content.

**Why the cast `return null` in the `execute(...)` lambda:** Because `execute()` returns `T`. The DELETE caller ignores the return. Alternative: add an `executeVoid(...)` helper if we want stricter typing — planner discretion, but the current shape is two lines of glue.

## Channel Name Normalization

> Resolves CONTEXT.md `<specifics>` item 1.

**Discord's server-side behavior** `[CITED: discord/discord-api-docs Discussion #5338]`: Discord lowercases letters in channel names with locale-aware case folding and removes/replaces characters it considers invalid. The exact normalization regex is **not officially documented** — confirmed by community discussion on the official Discord API discussions repo.

**Confirmed safe character set for `md{N}-{a}-vs-{b}` pattern:**
- `a-z` lowercase letters
- `0-9` digits
- `-` hyphen as separator

This subset is universally accepted server-side.

**Recommendation for Phase 94:**

1. Pre-format the name in `DiscordChannelService` as `.toLowerCase(Locale.ROOT)`.
2. Replace any non-ASCII letters / spaces / underscores in team-shortNames with `-` defensively (some teams may have non-ASCII shortNames — `team.shortName` is unconstrained in the schema).
3. Store the Discord-server-returned `channel.name()` in DB, NOT the pre-formatted name, since Discord MAY normalize further (e.g., collapsing repeated dashes). This guarantees DB `discordChannelId` lookup matches what Discord actually has.
4. The Phase-93 `DiscordRestClientIT.givenCreateChannel` already expects `"matchday-1"` as the server-returned name — pattern is consistent.

**Defensive normalization helper:**

```java
private static String normalizeChannelName(String raw) {
    return raw.toLowerCase(Locale.ROOT)
              .replaceAll("[^a-z0-9-]", "-")
              .replaceAll("-+", "-")
              .replaceAll("^-|-$", "");
}
```

`[ASSUMED]` — this exact helper is recommended but Discord-side normalization is undocumented, so the post-create assertion that `channel.name()` equals the pre-formatted name might fail for exotic shortNames. **Mitigation:** the IT for `DiscordChannelServiceWireMockIT` should NOT assert exact name equality between request and response; it should assert the channel-name we *send* and trust Discord's normalization for the *response*.

## Permission Audit Semantics

> Resolves CONTEXT.md `<specifics>` item 2.

**Audit-pass definition:** The `permission_overwrites` array on `GET /channels/{id}` response contains exactly 3 entries:

| Entry | `id` | `type` | `allow` check | `deny` check |
|-------|------|--------|---------------|--------------|
| 1 | `globalConfig.guildId` (Discord uses guild-ID as the @everyone role-ID) | `0` (role) | `(allow & VIEW_CHANNEL) == 0` | `(deny & VIEW_CHANNEL) != 0` |
| 2 | `match.homeTeam.discordRoleId` | `0` | `(allow & VIEW_CHANNEL) != 0` | (don't assert exact mask; just allow-view-bit set) |
| 3 | `match.awayTeam.discordRoleId` | `0` | `(allow & VIEW_CHANNEL) != 0` | (same) |

**Audit-fail conditions** (any one triggers `DiscordAuthException`):
- Total overwrite count ≠ 3.
- Any overwrite with `(allow & VIEW_CHANNEL) != 0` whose `id` is NOT one of `{homeTeam.discordRoleId, awayTeam.discordRoleId}`.
- The two team-role overwrites are not both present.
- The @everyone overwrite is missing or has VIEW_CHANNEL in `allow`.

**Audit-pass code shape (algorithmic):**

```java
private void assertPermissionAudit(String channelId, String homeRoleId, String awayRoleId)
        throws DiscordApiException {
    Channel back = restClient.fetchChannel(channelId);
    List<PermissionOverwrite> overwrites = back.permissionOverwrites();
    if (overwrites == null || overwrites.size() != 3) {
        throw new DiscordAuthException(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE, null);
    }
    Set<String> rolesWithView = overwrites.stream()
        .filter(o -> o.type() == DiscordPermissions.OVERWRITE_TYPE_ROLE)
        .filter(o -> (Long.parseLong(o.allow()) & DiscordPermissions.VIEW_CHANNEL) != 0L)
        .map(PermissionOverwrite::id)
        .collect(Collectors.toSet());
    if (!rolesWithView.equals(Set.of(homeRoleId, awayRoleId))) {
        throw new DiscordAuthException(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE, null);
    }
}
```

**`AUDIT_FAIL_MESSAGE` constant** (added to `DiscordApiExceptionMapper`): `"Channel permission audit failed - an unexpected role had View permission. Channel was deleted; verify Discord server-role setup and retry."` (per D-04 verbatim).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP client to Discord | New `java.net.http.HttpClient` | Extend existing `DiscordRestClient` | Phase 93 INFRA-01 already wired auth, base URL, allowed-hosts validation, rate-limit interceptor, sealed exception mapper. `[[feedback-spring-native-preference]]`. |
| 60-min TTL role cache | Caffeine / Guava `CacheBuilder` | Hand-rolled `ConcurrentHashMap<String, CachedEntry<Role>>` clone of `DiscordEmojiCache` | Per 93-CONTEXT D-03 — "zero new prod dependencies" + clock-injectable testability. |
| Snowflake regex per Form | Per-Form `@Pattern(regexp="...")` | `DiscordSnowflake.PATTERN` shared constant in `org.ctc.discord.dto` | D-14 locked. `DiscordConfigForm` refactors 5 sites in this phase. |
| Modal markup | New HTML5 `<dialog>` element | `<div class="modal-overlay">` with `style.display='flex'` | Existing pattern in `season-detail.html`. CSS already shipped. |
| Markdown rendering for `discordTeaser` | Marked.js / commonmark-java | Pass-through raw text | Discord renders Markdown server-side; admin UI just shows the raw text in a `<textarea>` for editing, `<pre>` for display. |
| Permission bitmask math at call sites | Hardcoded numeric literals | `DiscordPermissions.TEAM_MEMBER_ALLOW_MASK` constants | Auditable in code, single source of truth, easy to test in `DiscordPermissionsTest`. |
| Modal channel-picker JS | Custom JS toggle / show/hide | Existing CSS-overlay pattern + inline `onclick="...style.display='flex'"` | 2 existing usages in `season-detail.html` — copy verbatim. |

**Key insight:** Every Phase-94 "new thing" has a structurally identical Phase-93 precedent within the same `org.ctc.discord.*` package or a `Phase-91 dataimport.exception` analog. The planner's job is to **clone-and-rename**, not to invent.

## Runtime State Inventory

> Phase 94 is **not** a rename/refactor phase, but it does add stored data with Discord-side semantics that survive code redeploy. The following inventory documents the runtime state Phase 94 introduces.

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| **Stored data (DB)** | `teams.discord_role_id` (V9) ; `discord_global_config.current_match_category_id` (V9) ; 7 new `matches.discord_*` + scheduling columns (V10) | Schema additions only; no existing data needs migration (new columns nullable / DEFAULT '' per D-03). |
| **Live service config (Discord-side)** | Created channels persist in Discord guild — orphan-channel risk if audit fails and cleanup-DELETE also fails. | D-04 handles via append-to-user-message + WARN log. Operator must manually delete orphaned channels (runbook DOCS-02 Phase 98). |
| **OS-registered state** | None — phase has no OS hooks. | None. |
| **Secrets / env vars** | `DISCORD_BOT_TOKEN` env var (already Phase 93). Webhook URLs stored in DB are secrets (`@ToString.Exclude` on `Match.discordChannelWebhookUrl`). | Webhook-URL handling already covered by Phase 93 T-93-02 mitigations (logback mask). Phase 94 extends `@ToString.Exclude` discipline to the new field. |
| **Build artifacts / installed packages** | None — pure source + schema changes. | None. |

## Common Pitfalls

### Pitfall 1: Forgetting `@JsonInclude(NON_NULL)` on `ChannelCreateRequest`
**What goes wrong:** Phase 93 IT callers that construct `new ChannelCreateRequest("name", 0, "cat1")` produce JSON with `"permission_overwrites": null`, which some Jackson configurations may serialize. Discord 400-rejects unexpected `null` values in `permission_overwrites`.
**Why it happens:** Records always serialize all components by default.
**How to avoid:** Add `@JsonInclude(JsonInclude.Include.NON_NULL)` at the record level (or globally configure Jackson) so null `permissionOverwrites` is omitted.
**Warning signs:** Phase 93 `DiscordRestClientIT.givenCreateChannel` starts failing with 400 from WireMock after the record extension.

### Pitfall 2: Treating `allow`/`deny` as JSON numbers instead of strings
**What goes wrong:** Sending `"allow": 140737488735808` (numeric literal) in `permission_overwrites` works for the lower-bit masks but Discord docs explicitly say strings, and JavaScript-bridged clients lose precision above 53 bits (USE_EXTERNAL_STICKERS is `1 << 37`, well into unsafe territory).
**Why it happens:** Records typing fields as `long` and letting Jackson serialize as numbers.
**How to avoid:** Declare `PermissionOverwrite.allow` and `.deny` as `String`; serialize with `String.valueOf(mask)` at the call site.
**Warning signs:** Discord 400 with cryptic message; or USE_EXTERNAL_STICKERS bit silently dropped on round-trip.

### Pitfall 3: `@everyone` overwrite uses `guild.id` as its `id`
**What goes wrong:** Builders try to send `"id": "everyone"` or `"id": null` for the @everyone overwrite; Discord 400-rejects.
**Why it happens:** Common misunderstanding — Discord models `@everyone` as a role whose ID equals the guild ID.
**How to avoid:** Use `globalConfig.guildId` as the overwrite ID for the @everyone-deny-view entry. Document this in the `DiscordChannelService` source with a comment.
**Warning signs:** WireMock IT happy-path test passes (because we stub the response), but live UAT-04 fails with 400.

### Pitfall 4: Permission audit reads back a different overwrite count than expected
**What goes wrong:** Discord server may add an extra inherited overwrite from the category, or the bot's own role may be auto-added with permissions.
**Why it happens:** Discord behavior is documented but operators sometimes have category-level overwrites that propagate.
**How to avoid:** Audit on **set equality of role-IDs-with-View** rather than total-count equality. The set check (homeRoleId + awayRoleId == roles-with-view) is more robust. Document the precise audit-pass definition in the code (see § "Permission Audit Semantics" above).
**Warning signs:** False audit-fails on live UAT-04 — channel actually has correct permissions but extra inherited overwrites cause count != 3.

### Pitfall 5: V9 column added with NOT NULL but no DEFAULT on existing table
**What goes wrong:** `ALTER TABLE teams ADD COLUMN discord_role_id VARCHAR(32) NOT NULL` fails for existing rows.
**Why it happens:** Existing teams have no Discord role; adding NOT NULL with no default is an immediate constraint violation on any pre-existing row.
**How to avoid:** Per D-03, leave `teams.discord_role_id` **nullable** (no NOT NULL, no DEFAULT). `discord_global_config.current_match_category_id` is `NOT NULL DEFAULT ''` because the row was inserted with empty-string defaults in V8 — the V9 ALTER simply adds another VARCHAR-DEFAULT-empty-string column to a single seed row.
**Warning signs:** Flyway migration fails on `./mvnw verify` at H2/MariaDB startup.

### Pitfall 6: Discord channel name normalization differs between pre-format and server response
**What goes wrong:** Operator's expected channel name (e.g., `md1-CLR-vs-TNT`) doesn't match the Discord-server-returned name (`md1-clr-vs-tnt`), leading to visual flicker or test flakiness.
**Why it happens:** Discord normalizes lowercase + dash-collapse server-side.
**How to avoid:** Pre-format at the service layer. Store **Discord's returned name** in DB (not the pre-formatted name).
**Warning signs:** Live UAT-04: channel link shows different name than DB-stored name.

### Pitfall 7: SpotBugs `EI_EXPOSE_REP` on the new `Match` fields
**What goes wrong:** Adding `String discordChannelWebhookUrl` to Match without `@ToString.Exclude` — Lombok's generated `toString()` exposes the webhook secret in logs.
**Why it happens:** Default Lombok behavior includes all fields.
**How to avoid:** `@ToString.Exclude` on `discordChannelWebhookUrl` per T-93-02 forward-reference (verified Phase 93 threat model).
**Warning signs:** `DiscordLogMaskingIT` regression or SpotBugs gate failure.

### Pitfall 8: `permission_overwrites` JSON field uses snake_case, Java record uses camelCase
**What goes wrong:** Without `@JsonProperty("permission_overwrites")`, Jackson maps the Java field `permissionOverwrites` to JSON key `permissionOverwrites`, which Discord doesn't recognize → 400.
**Why it happens:** Default Jackson field-mapping.
**How to avoid:** Always annotate `@JsonProperty("permission_overwrites")` on the field in both `Channel` and `ChannelCreateRequest`.
**Warning signs:** 400 from Discord with "unknown property" error.

## Code Examples

### Verified Phase-93 IT pattern that Phase 94 ITs follow

```java
// Source: src/test/java/org/ctc/discord/DiscordRestClientIT.java [VERIFIED]
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

    @BeforeEach
    void resetWireMock() { wm.resetAll(); }

    // ... @Test methods follow Given-When-Then naming per CLAUDE.md
}
```

### Verified WireMock stub shape for create-channel happy-path

```java
// Source: src/test/java/org/ctc/discord/DiscordRestClientIT.java line 91-100 [VERIFIED]
wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/g1/channels"))
        .willReturn(okJson("{\"id\":\"c1\",\"name\":\"matchday-1\",\"type\":0,\"parent_id\":\"cat1\"}")));
```

For Phase 94 audit ITs, the stub for `GET /channels/{id}` returns the same JSON shape with `permission_overwrites` populated:

```java
wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c1"))
        .willReturn(okJson("""
            {
              "id": "c1",
              "name": "md1-clr-vs-tnt",
              "type": 0,
              "parent_id": "cat1",
              "permission_overwrites": [
                {"id":"g1","type":0,"allow":"0","deny":"1024"},
                {"id":"home-role","type":0,"allow":"140737488735808","deny":"17719906033"},
                {"id":"away-role","type":0,"allow":"140737488735808","deny":"17719906033"}
              ]
            }""")));
```

### Verified existing controller error-flash pattern

```java
// Source: src/main/java/org/ctc/discord/web/DiscordConfigController.java line 136-147 [VERIFIED]
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

Phase 94 `MatchController` reuses this pattern verbatim — copy into controller (or refactor to a shared helper if planner prefers; planner discretion).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hardcoded user-message via `e.getMessage()` | Hardcoded constant per Category + flash `errorCategory` | Phase 91 D-06/D-07 | Closes T-91-02-IL info-leak invariant; carries to Phase 94. |
| Inline-edit POSTs on matchday-detail | Match-Detail page per-entity | Phase 94 D-01 (now) | Concentrates Discord surface; matches existing detail-page convention. |
| Hand-rolled cache only for emoji | Hand-rolled cache pattern shared as `CachedEntry<T>` | Phase 93 D-03 | Reuse pattern for `DiscordRoleCache` (Phase 94) and any future cache. |

**Deprecated/outdated:**
- *None applicable to Phase 94.*

## Project Constraints (from CLAUDE.md)

- **Test Coverage ≥ 82%** (currently 88.88%) — Phase 94 must hold ≥ 88.88% (D-09).
- **Flyway V1+ immutable** — only V9 + V10 added in this phase. No edits to V1-V8.
- **No Inline Styles on Buttons** — Phase 94 admin.css adds `.discord-actions` cluster (D-06). All Discord-page buttons use CSS classes only.
- **DTOs instead of Entities in Controllers** — `MatchForm` DTO, `TeamForm` extended; never `@ModelAttribute Match`.
- **Keep Thymeleaf Templates Lean** — modal categories pre-resolved in `DiscordCategoryResolver`; templates only render.
- **Tag Tests by Category (`@Tag`)** — WireMock-IT → `@Tag("integration")`, Playwright → `@Tag("e2e")`, unit → untagged (D-11).
- **Backward Compatibility** — existing `POST /admin/matches/save` (CREATE flow) URL unchanged. New `/save-edit` is a sibling route.
- **OSIV active** — match-detail.html can access `match.matchday.season.year` directly without `@EntityGraph`.
- **SpotBugs `BugInstance` = 0** (D-09) — `@ToString.Exclude` on `Match.discordChannelWebhookUrl` is mandatory.
- **CodeQL gate exit 0** — no new SSRF surfaces (RestClient already constructor-guards via `DiscordHostValidator`).
- **English UI strings only** `[CITED: CLAUDE.md § Language]`.
- **TDD discipline** — write tests first per phase plan.
- **Conventional Commits** — `feat`, `fix`, `docs`, etc.
- **Subagent rules** — Phase 94 is inline-sequential, no subagents per D-07 + `[[feedback-inline-sequential-execution]]`.
- **Clean Maven build is the source of truth** `[CITED: CLAUDE.md Memory: feedback_clean_maven_build_authority]` — never trust IDE caches; run `./mvnw clean test-compile` before assuming compile errors.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `TEAM_MEMBER_ALLOW_MASK` decimal sum 140,737,488,735,808 is correct | Permission Bitmask Reference | Mitigated by recommending OR-expression in source (auditable in code). Risk: wire-format bytewise correct only if individual bits are correct. |
| A2 | `TEAM_MEMBER_DENY_MASK` decimal sum 17,719,906,033 is correct | Permission Bitmask Reference | Same mitigation. Re-compute via the OR-expression in `DiscordPermissions`; never copy the decimal literal. |
| A3 | DELETE `/channels/{id}` 200-OK-with-body (NOT 204) | DELETE contract | Mitigated by `toBodilessEntity()` consumer — works for both 200+body and 204. |
| A4 | Channel name pre-normalization helper (lowercase + dash-collapse) is correct | Channel Name Normalization | Discord-side normalization is undocumented; live UAT-04 may surface exotic team-shortName edge cases. **Mitigation:** IT should not assert request-name == response-name; store the response-name in DB. |
| A5 | Discord webhook POST returns 200 (not 201) | POST /channels/{id}/webhooks | Mitigation: WireMock stubs in Phase 93 use `okJson` (200); RestClient `.retrieve().body(...)` does not gate on exact 2xx code. |
| A6 | `@everyone` overwrite id == `guildId` | Permission Audit | Documented in Discord docs implicitly via "the @everyone role has the same id as the guild it belongs to". Verified through long-standing community pattern — high confidence. |
| A7 | Discord normalizes channel name to `[a-z0-9-]` only | Channel Name Normalization | Verified via discord/discord-api-docs Discussion #5338 (community) — not formal docs. Defensive helper recommended. |

**Risk summary:** All assumed items have explicit mitigations. The bitmask sums (A1, A2) are pure arithmetic — verify by JUnit assertion in `DiscordPermissionsTest`:

```java
@Test
void teamMemberAllowMask_isCorrectComposite() {
    assertThat(DiscordPermissions.TEAM_MEMBER_ALLOW_MASK)
        .isEqualTo(1024L + 2048L + 64L + 32768L + 16384L + 65536L + 262144L + 140737488355328L);
}
```

## Open Questions

1. **Does the existing `DiscordRoleCache` integration with `DiscordConfigController.refreshRolesCache()` cause a unit-test regression?**
   - What we know: Phase 93 INFRA-03 wired `refreshRolesCache()` to call `restClient.fetchGuildRoles(guildId)`. Phase 94 D-05 says "extend to ALSO call `roleCache.refresh(...)`".
   - What's unclear: The existing `DiscordConfigControllerIT` may not have a `DiscordRoleCache` bean wired; adding the dependency may need Spring context refactor.
   - Recommendation: Planner adds `DiscordRoleCache` as a `@Component` so Spring autowiring picks it up; the existing IT will need Spring context to include the new bean. No code-level breakage expected.

2. **Should the `DiscordCategoryResolver` cache the `listChannels` response?**
   - What we know: `listChannels` is one Discord call returning all guild channels (50–200 entries). Modal renders are infrequent.
   - What's unclear: Whether the planner wants caching (lower Discord traffic) or live-read (counts always current).
   - Recommendation: NO cache in Phase 94 — counts must be live so the operator sees actual category-full state. CONTEXT.md `<code_context>` line 1013 confirms this design choice.

3. **Where exactly does the `.error-badge--category-full` CSS class get added?**
   - What we know: Phase 93 D-10 plan specified adding it; CONTEXT.md `<code_context>` line 965 says "planner verifies whether the CSS class actually shipped in `admin.css` or needs to be added in Plan 94-01".
   - What's unclear: I verified via grep — `admin.css` line 375 contains `.error-badge--category-full { background: #3b2a0a; color: #ffcc80; border: 1px solid #e69138; }`. **It already shipped in Phase 93.** No work needed in Phase 94.
   - Recommendation: Plan 94-03 just uses the existing class. No CSS addition needed.

4. **Mobile-overflow on Match-Detail page — what's the screenshot target?**
   - What we know: D-06 says screenshots under `.screenshots/94-{plan}/`.
   - What's unclear: Whether the screenshot covers the modal-open state.
   - Recommendation: Plan 94-03's Mobile sweep includes BOTH the match-detail-page-without-modal AND match-detail-page-with-archive-modal-open states.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Eclipse Temurin) | All compilation | ✓ | per CLAUDE.md | — |
| Maven via `./mvnw` | Build / test | ✓ | per CLAUDE.md | — |
| H2 (in-memory) | dev profile | ✓ | per STACK.md | — |
| MariaDB | local/docker/prod profiles | ✓ (operator-provisioned) | per STACK.md | H2 for dev |
| Playwright Chromium | E2E + visual sweep | ✓ (installed via `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`) | per CLAUDE.md | — |
| WireMock | ITs | ✓ (transitive dep) | `[VERIFIED: src/test/java/org/ctc/discord/DiscordRestClientIT.java]` | — |
| Discord REST API v10 | Live UAT-04 (post-phase) | ✓ (public service) | v10 | WireMock IT for Phase 94 close (D-12) |
| Operator's Discord test guild | UAT-04 | ✓ (operator-provisioned per UAT-03 setup) | — | Phase 94 closes on WireMock-only |
| `slopcheck` | Package legitimacy gate | ✗ (no `pip` on this Mac) | — | N/A — Phase 94 installs zero new packages |

**Missing dependencies with no fallback:** None — phase has zero new external dependencies.
**Missing dependencies with fallback:** `slopcheck` (no packages to verify so the gate is structurally satisfied).

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Mockito + WireMock + Playwright |
| Config file | `pom.xml` (Surefire/Failsafe routed by `@Tag` per CLAUDE.md) |
| Quick run command | `./mvnw -Dtest=<ClassName> test` (per-class unit) ; `./mvnw -Dit.test=<ClassName>IT verify -DfailIfNoTests=false` (per-class IT) |
| Full suite command | `./mvnw verify -Pe2e` (incl. Playwright) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Test Class | @Tag | Plan | Wave 0 Status |
|--------|----------|-----------|------------|------|------|---------------|
| **CHAN-01** | Snowflake regex on `TeamForm.discordRoleId` rejects non-snowflake, accepts empty + 17–20-digit | Unit | `TeamFormSnowflakeValidationTest` | untagged | 94-01 | New |
| **CHAN-01** | Shared `DiscordSnowflake.PATTERN` constant matches across DiscordConfigForm + TeamForm | Unit | `DiscordSnowflakeTest` | untagged | 94-01 | New |
| **CHAN-01** | `teams.discord_role_id` round-trip persistence (H2 + MariaDB) | IT | `TeamRepositoryDiscordRoleIdIT` | `@Tag("integration")` | 94-01 | New |
| **CHAN-01** | `DiscordRoleCache` 60-min TTL + clock-injected expiry | Unit | `DiscordRoleCacheTest` | untagged | 94-01 | New |
| **CHAN-01** | `DiscordRoleCache.refresh()` bulk-replaces atomically | Unit | `DiscordRoleCacheTest` (same class) | untagged | 94-01 | New |
| **CHAN-01** | `DiscordConfigController.refreshRolesCache()` ALSO populates `roleCache` | IT | `DiscordConfigControllerIT` (extend existing) | `@Tag("integration")` | 94-01 | Extension of existing |
| **CHAN-01** | Team-Form renders dropdown when cache warm, plain-text + warning when empty | Playwright E2E | `TeamFormDiscordRoleDropdownE2ETest` | `@Tag("e2e")` | 94-01 | New, package `org.ctc.e2e.discord` |
| **CHAN-01** | `discord_global_config.current_match_category_id` round-trip + Form rendering | IT | `DiscordGlobalConfigRepositoryIT` (extend) | `@Tag("integration")` | 94-01 | Extension of existing |
| **CHAN-02** | V10 Flyway migration applies on H2 + MariaDB | IT (implicit via Spring context boot) | Existing migration smoke | `@Tag("integration")` | 94-02 | Existing |
| **CHAN-02** | `Match` entity round-trips 7 new fields | IT | `MatchRepositoryDiscordFieldsIT` | `@Tag("integration")` | 94-02 | New |
| **CHAN-02** | `MatchForm` validation: discordTeaser max 2000, streamLink max 500, host fields max 100 | Unit | `MatchFormValidationTest` | untagged | 94-02 | New |
| **CHAN-02** | Match-Detail page `GET /admin/matches/{id}` renders header + Discord Actions + Schedule + Races | Playwright E2E | `MatchDetailControllerE2ETest` | `@Tag("e2e")` | 94-02 | New |
| **CHAN-02** | `POST /save-edit` round-trips 5 fields | IT | `MatchEditFormIT` | `@Tag("integration")` | 94-02 | New |
| **CHAN-02** | "Create Discord Channel" button visibility gating (3 conditions) | Playwright E2E | `MatchDetailControllerE2ETest` (same class) | `@Tag("e2e")` | 94-02 | New |
| **CHAN-02** | `DiscordChannelService.createMatchChannel` happy-path (9-step flow, all 4 RestClient calls, DB write) | IT WireMock | `DiscordChannelServiceWireMockIT` | `@Tag("integration")` | 94-02 | New |
| **CHAN-02** | `DiscordChannelService` audit-fail → DELETE + DB rollback + `DiscordAuthException` | IT WireMock | `DiscordChannelServicePermissionAuditFailIT` | `@Tag("integration")` | 94-02 | New |
| **CHAN-02** | `DiscordChannelService` cleanup-DELETE-fails → both exceptions surfaced + log WARN | IT WireMock | `DiscordChannelServiceCleanupFailIT` | `@Tag("integration")` | 94-02 | New |
| **CHAN-02** | `DiscordPermissions` composite mask correctness | Unit | `DiscordPermissionsTest` | untagged | 94-02 | New |
| **CHAN-02** | `DiscordRestClient.createWebhook` typed method | IT WireMock | `DiscordRestClientIT` (extend existing) | `@Tag("integration")` | 94-02 | Extension |
| **CHAN-02** | `DiscordRestClient.fetchChannel` typed method (returns Channel with permission_overwrites) | IT WireMock | `DiscordRestClientIT` (extend) | `@Tag("integration")` | 94-02 | Extension |
| **CHAN-02** | `DiscordRestClient.deleteChannel` typed method (DELETE + bodiless) | IT WireMock | `DiscordRestClientIT` (extend) | `@Tag("integration")` | 94-02 | Extension |
| **CHAN-02** | `Match.discordChannelWebhookUrl` `@ToString.Exclude` invariant | Unit | `MatchToStringTest` | untagged | 94-02 | New (mirror of `DiscordGlobalConfigToStringTest`) |
| **CHAN-02** | Controller typed-catch → flash badge for AUTH / TRANSIENT / NOT_FOUND categories | Unit (Mockito) | `MatchControllerCreateChannelErrorCategoryTest` | untagged | 94-02 | New |
| **CHAN-03** | `DiscordCategoryResolver` regex matches `Match Days Archive {year}` + `({n})` variants | Unit | `DiscordCategoryResolverTest` | untagged | 94-03 | New |
| **CHAN-03** | `resolveArchiveCategoriesFor(year)` filters by year, sorts by num ASC | IT WireMock | `DiscordCategoryResolverWireMockIT` | `@Tag("integration")` | 94-03 | New |
| **CHAN-03** | `defaultSelection` returns highest-num with `<50`, empty if all-full | Unit | `DiscordCategoryResolverTest` (same) | untagged | 94-03 | New |
| **CHAN-03** | Channel-count derivation from `parent_id` match | IT WireMock | `DiscordCategoryResolverWireMockIT` (same) | `@Tag("integration")` | 94-03 | New |
| **CHAN-03** | `POST /move-to-archive` happy-path PATCH | IT WireMock | `DiscordChannelArchiveServiceWireMockIT` | `@Tag("integration")` | 94-03 | New |
| **CHAN-03** | `DiscordCategoryFullException` → `category-full` flash badge + runbook link | Unit (Mockito) | `MatchControllerMoveToArchiveErrorCategoryTest` | untagged | 94-03 | New |
| **CHAN-03** | Archive modal renders categories with counts, default-radio pre-selected | Playwright E2E | `ArchiveModalE2ETest` | `@Tag("e2e")` | 94-03 | New |
| **CHAN-03** | All-full state renders warning banner + no radio pre-selected | Playwright E2E | `ArchiveModalE2ETest` (same) | `@Tag("e2e")` | 94-03 | New |
| **CHAN-03** | Mobile sweep (375×667) of match-detail.html + archive modal | Visual sweep | `playwright-cli` screenshots → `.screenshots/94-03/` | — | 94-03 | Manual sweep per D-06 |

### Sampling Rate
- **Per task commit:** `./mvnw -Dtest=<NewClass> test` for unit, `./mvnw -Dit.test=<NewClassIT> verify -DfailIfNoTests=false` for IT (per `[[feedback-test-call-optimization]]`).
- **Per wave merge:** `./mvnw clean test-compile` + `./mvnw verify` for the plan's test set; full `-Pe2e` only at plan-close per `[[feedback-clean-build-only]]`.
- **Phase gate:** `./mvnw verify -Pe2e` green + Desktop + Mobile screenshot sweep complete + JaCoCo ≥ 88.88% before `/gsd-verify-work`.

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/admin/dto/TeamFormSnowflakeValidationTest.java` — covers CHAN-01 (snowflake regex)
- [ ] `src/test/java/org/ctc/discord/dto/DiscordSnowflakeTest.java` — covers shared constant correctness
- [ ] `src/test/java/org/ctc/domain/repository/TeamRepositoryDiscordRoleIdIT.java` — covers CHAN-01 (round-trip)
- [ ] `src/test/java/org/ctc/discord/DiscordRoleCacheTest.java` — covers CHAN-01 (TTL + clock + refresh)
- [ ] `src/test/java/org/ctc/e2e/discord/TeamFormDiscordRoleDropdownE2ETest.java` — covers CHAN-01 (UI)
- [ ] `src/test/java/org/ctc/admin/dto/MatchFormValidationTest.java` — covers CHAN-02 (Form validation)
- [ ] `src/test/java/org/ctc/domain/repository/MatchRepositoryDiscordFieldsIT.java` — covers CHAN-02 (round-trip)
- [ ] `src/test/java/org/ctc/domain/model/MatchToStringTest.java` — covers CHAN-02 (T-93-02 webhook-secret invariant)
- [ ] `src/test/java/org/ctc/discord/DiscordPermissionsTest.java` — covers CHAN-02 (bitmask composite)
- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java` — covers CHAN-02 (happy-path 9 steps)
- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java` — covers CHAN-02 (audit-fail + cleanup-OK)
- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelServiceCleanupFailIT.java` — covers CHAN-02 (audit-fail + cleanup-FAIL)
- [ ] `src/test/java/org/ctc/admin/controller/MatchControllerCreateChannelErrorCategoryTest.java` — covers CHAN-02 (controller typed-catch)
- [ ] `src/test/java/org/ctc/e2e/discord/MatchDetailControllerE2ETest.java` — covers CHAN-02 (UI + visibility gating)
- [ ] `src/test/java/org/ctc/discord/service/IT/MatchEditFormIT.java` — covers CHAN-02 (5-field round-trip)
- [ ] `src/test/java/org/ctc/discord/service/DiscordCategoryResolverTest.java` — covers CHAN-03 (regex + sort + default)
- [ ] `src/test/java/org/ctc/discord/service/DiscordCategoryResolverWireMockIT.java` — covers CHAN-03 (live listChannels + count)
- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java` — covers CHAN-03 (PATCH parent_id)
- [ ] `src/test/java/org/ctc/admin/controller/MatchControllerMoveToArchiveErrorCategoryTest.java` — covers CHAN-03 (category-full flash)
- [ ] `src/test/java/org/ctc/e2e/discord/ArchiveModalE2ETest.java` — covers CHAN-03 (modal UI)

No new test framework configuration needed — Surefire/Failsafe routing by `@Tag` already in place.

## Security Domain

> Required when `security_enforcement` is enabled (absent = enabled).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V1 Architecture | yes | T-93-03 forward-reference closes here — permission-overwrite audit before commit. |
| V2 Authentication | partial | Bot-Token env-var (Phase 93 INFRA-02 a); Phase 94 no new auth surface. |
| V3 Session Management | yes | CSRF active on all `POST /admin/matches/**` endpoints (inherited from Phase 30). |
| V4 Access Control | yes | Discord channel-level access controlled via overwrites; bot's MANAGE_CHANNELS permission required (operator-side). |
| V5 Input Validation | yes | `DiscordSnowflake.PATTERN` on TeamForm.discordRoleId; `@Size` on MatchForm fields; Jakarta-Validation. |
| V6 Cryptography | yes | Webhook URLs treated as secrets; `@ToString.Exclude` on `Match.discordChannelWebhookUrl`; logback mask (Phase 93 c). |
| V7 Error Handling | yes | Sealed `DiscordApiException` + 4 permits + hardcoded user-messages — no `e.getMessage()` echo (T-91-02-IL invariant). |
| V9 Communication | yes | All outbound calls hit `discord.com` only (Phase 93 INFRA-02 b SSRF positive whitelist). |
| V10 Malicious Code | partial | No new dependencies (zero-package phase). |

### Known Threat Patterns for Spring Boot + Discord REST stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Webhook-URL leak in `toString` | Information Disclosure | `@ToString.Exclude` on `Match.discordChannelWebhookUrl` (T-93-02 forward) |
| Channel-permission bypass (wrong role-mapping) | Information Disclosure / Spoofing | Post-create permission audit + DELETE-on-fail (T-93-03 closes in this phase) |
| Mass-assignment via POST | Tampering | `MatchForm` DTO (not Match entity) bound via `@ModelAttribute`; `@Valid` + BindingResult |
| CSRF on POST endpoints | Tampering | Inherited from Phase 30 CSRF chain — no new SecurityConfig changes |
| Token-fragment echo in flash error | Information Disclosure | Hardcoded user-message constants in mapper — never `e.getMessage()` |
| Audit-fail leaves orphan channel | Confused Deputy | Best-effort DELETE in cleanup branch; if cleanup itself fails, surface to operator in flash message (D-04) |
| Permission overwrite type confusion (role vs member) | Tampering | Phase 94 only sends `type=0` (role); `DiscordPermissions.OVERWRITE_TYPE_ROLE` constant for clarity |
| Rate-limit burst → Discord-bot-ban | DoS (self-inflicted) | `DiscordRateLimitInterceptor` per-bucket token-bucket (Phase 93 INFRA-01); T-93-04 mitigation continues |
| Excessive Discord 429 retries on cleanup DELETE | Cascading failure | Cleanup-DELETE catches `DiscordApiException` and appends to user-message; does not block the original audit-fail exception (D-04) |

## Sources

### Primary (HIGH confidence — VERIFIED)

- `docs.discord.com/developers/topics/permissions` — Discord permission bitmask reference (May 2026 fetch).
- `docs.discord.com/developers/resources/channel` — Channel object shape, PermissionOverwrite shape, DELETE/PATCH contracts (May 2026 fetch).
- `docs.discord.com/developers/resources/webhook` — POST `/channels/{id}/webhooks` contract (May 2026 fetch).
- `src/main/java/org/ctc/discord/DiscordRestClient.java` — existing `execute()` helper + typed methods.
- `src/main/java/org/ctc/discord/DiscordEmojiCache.java` — exact template for `DiscordRoleCache`.
- `src/main/java/org/ctc/discord/dto/ChannelCreateRequest.java` — current 3-arg shape to extend.
- `src/main/java/org/ctc/discord/dto/Channel.java` — current 4-field record to extend.
- `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` — current snowflake regex + Jakarta validation pattern.
- `src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java` — hardcoded user-message constants.
- `src/main/java/org/ctc/discord/web/DiscordConfigController.java` — typed-catch + applyErrorFlash pattern.
- `src/main/resources/db/migration/V8__discord_global_config.sql` — V9/V10 migration shape template.
- `src/main/resources/templates/admin/discord-config.html` — form shape + badge-warning + buttons.
- `src/main/resources/templates/admin/team-form.html` — current TeamForm template to extend.
- `src/main/resources/templates/admin/matchday-detail.html` — existing match-row inline layout (Phase 94 adds "→ Detail" link).
- `src/main/resources/templates/admin/season-detail.html` lines 96–229 — existing modal-overlay pattern (used 2×).
- `src/main/resources/static/admin/js/searchable-dropdown.js` — reusable JS contract.
- `src/main/resources/static/admin/css/admin.css` lines 357–375, 792–840, 1104–1129 — badge-warning, error-badge variants (incl. `--category-full` already shipped Phase 93), searchable-dropdown, modal-overlay/body.
- `src/test/java/org/ctc/discord/DiscordRestClientIT.java` — WireMock IT pattern reference.
- `.planning/phases/93-discord-foundation/93-CONTEXT.md` — Phase 93 hand-off (D-93-01..D-93-12).
- `.planning/phases/93-discord-foundation/93-THREAT-MODEL.md` — T-93-01..T-93-04, T-93-03 forward-ref closes in CHAN-02.
- `.planning/phases/94-team-roles-match-channel-lifecycle/94-CONTEXT.md` — locked D-01..D-14 + canonical refs.

### Secondary (MEDIUM confidence)

- `github.com/discord/discord-api-docs/discussions/5338` — Channel name normalization community discussion (Discord-side normalization is undocumented in formal API docs).

### Tertiary (LOW confidence)

- Decimal sums in composite bitmasks (TEAM_MEMBER_ALLOW_MASK = 140,737,488,735,808 and TEAM_MEMBER_DENY_MASK = 17,719,906,033) — arithmetic computed from verified individual bits. **Mitigation:** recommend OR-expression in source code, never decimal literal, plus a JUnit `assertEquals` in `DiscordPermissionsTest`.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every library/version is already in the codebase, verified by source-file inspection.
- Architecture: HIGH — established patterns from Phase 93 are explicitly cloned; modal pattern verified via grep of `season-detail.html`.
- Permission bitmasks: HIGH for individual bits (verified against docs.discord.com), LOW for composite decimal sums (recommended OR-expression mitigates).
- Discord REST contracts: HIGH — verified directly against `docs.discord.com` May 2026.
- Pitfalls: HIGH — derived from explicit T-93-01..04 mitigations, Phase 91 D-06/D-07 patterns, CLAUDE.md anti-patterns, and Discord docs edge cases.

**Research date:** 2026-05-21
**Valid until:** 2026-06-20 (30-day window for stable infra; Discord API v10 stable; codebase Phase 93 baseline immutable for v1.13)

## RESEARCH COMPLETE
