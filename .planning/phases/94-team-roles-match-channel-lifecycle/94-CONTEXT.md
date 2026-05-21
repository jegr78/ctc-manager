# Phase 94: Team Roles + Match Channel Lifecycle - Context

**Gathered:** 2026-05-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the operator-facing surface for mapping each team to a Discord role
(CHAN-01), creating per-match Discord channels with the full permission-overwrite
model + automatic webhook creation + post-create permission-audit assertion
(CHAN-02), and archiving channels into year-based archive categories that honor
Discord's 50-channels-per-category limit (CHAN-03). Three sequential inline
plans on `gsd/v1.13-discord-integration` per [[feedback-inline-sequential-execution]]
+ [[feedback-wave-pause]], mapping 1:1 to CHAN-01/02/03 (Phase 93 D-05 carry-forward).

Phase 94 builds the FIRST production surface that actually hits the live Discord
REST surface (`POST /guilds/{id}/channels` + `POST /channels/{id}/webhooks` +
`PATCH /channels/{id}`). Phase 93 INFRA-01 already shipped `DiscordRestClient.createChannel`
+ `modifyChannel` + sealed exception hierarchy; Phase 94 wires the business
logic on top (`DiscordChannelService`, `DiscordCategoryResolver`, `DiscordRoleCache`)
plus the V9/V10 schema deltas + Match-Detail page + Team-Form extension.

In scope:

- **CHAN-01 (Plan 94-01)** — Team Role Mapping + DiscordRoleCache:
  - Flyway `V9__add_discord_team_role_and_current_match_category.sql` BUNDLES
    BOTH ALTER TABLEs in one migration (per D-03 below):
    `ALTER TABLE teams ADD COLUMN discord_role_id VARCHAR(32)` +
    `ALTER TABLE discord_global_config ADD COLUMN current_match_category_id
    VARCHAR(32)`. H2 + MariaDB compatible (no CHECK, no LONGTEXT — matches
    V8 conventions). Snowflake validation lives in the Form-DTO layer
    (Phase 93 D-02 precedent), not in the DB.
  - `Team` entity gets `private String discordRoleId;` field. No
    `@ToString.Exclude` needed (role IDs are NOT secrets — they're public
    Discord identifiers visible in any client's Developer-Mode-context-menu).
  - `TeamForm` gets `@Pattern(SNOWFLAKE_REGEX)` field for `discordRoleId`
    (empty string allowed per D-Spec-15 / REQ CHAN-01 "operator can clear
    the field to disable Discord-channel-creation"). Snowflake regex is
    extracted to a shared constant `DiscordSnowflake.PATTERN` +
    `DiscordSnowflake.MESSAGE` in `org.ctc.discord.dto` (since
    `DiscordConfigForm`, `TeamForm`, MatchForm/SeasonForm Phase 95-96 all
    need it — Phase 93 D-02 form-multiplication inflection point handled
    upfront so the planner picks the shared constant in 94-01).
  - `TeamManagementService.save()` signature extended with the new
    `discordRoleId` parameter (positional arg — current style).
  - New `DiscordRoleCache` class in `org.ctc.discord` package (D-05).
    Structurally identical to `DiscordEmojiCache`: `ConcurrentHashMap<String,
    CachedEntry<Role>>` keyed by Discord role-ID, 60-min TTL, `Clock`
    injected, `refresh(Map<String, Role>)` API. The EXISTING "Refresh Server
    Roles Cache" button on `/admin/discord-config` (Phase 93 INFRA-03) is
    extended to ALSO call `roleCache.refresh(...)` after `fetchGuildRoles()`.
  - `DiscordConfigController.refreshRolesCache()` updated accordingly.
  - Team-Form template (`templates/admin/team-form.html`) renders the
    `discordRoleId` field with `.searchable-dropdown` shape when the cache
    is warm (sourced from `roleCache.snapshot()` injected via Model attribute);
    when the cache is empty, renders only the plain-text input + a
    `.badge-warning` hint linking to `/admin/discord-config` "Refresh
    Server Roles Cache" button. NEVER tries an inline eager-fetch on the
    edit GET (per D-05).
  - Tests: `TeamFormSnowflakeValidationTest` (rejects non-snowflake,
    accepts empty), `TeamRepositoryDiscordRoleIdIT` (round-trip persistence),
    `DiscordRoleCacheTest` (clock-injected TTL behavior — mirrors
    `DiscordEmojiCacheTest`), `TeamFormDiscordRoleDropdownE2ETest`
    (Playwright: cache-warm → dropdown shown; cache-empty → plain-text +
    warning badge visible) per [[feedback-playwright-cli]] + Mobile-sweep
    (D-06).

- **CHAN-02 (Plan 94-02)** — Match-Detail Page + Channel Creation Service +
  Permission-Audit:
  - Flyway `V10__add_matches_discord_and_scheduling_fields.sql`:
    `ALTER TABLE matches ADD COLUMN discord_channel_id VARCHAR(32);
    discord_channel_webhook_url VARCHAR(500); discord_teaser VARCHAR(2000);
    stream_link VARCHAR(500); lobby_host VARCHAR(100); race_director
    VARCHAR(100); streamer VARCHAR(100);`. No FK indexes (snowflake IDs
    aren't FKs).
  - `Match` entity adds 7 fields. `discordChannelWebhookUrl` carries
    `@ToString.Exclude` (T-93-02 webhook-secret invariant, per
    `93-THREAT-MODEL.md`).
  - New `MatchForm` DTO in `org.ctc.admin.dto` (mirrors `MatchdayForm`
    Lombok shape per CLAUDE.md § Controller patterns). Fields: `id` (UUID),
    `discordTeaser` (`@Size(max=2000)`, Markdown allowed — NOT HTML-escaped
    at Form level; render-time the template uses Thymeleaf `th:utext`
    discipline IF Markdown is rendered, OR raw-text-passthrough IF Discord
    handles the Markdown render server-side — per design spec § 4.5 Discord
    handles Markdown so `discordTeaser` is passed as raw text to the
    webhook, never rendered in admin templates), `streamLink` (`@Size(max=500)`,
    accepts `<#channelId>` / URL / blank — no strict regex; passed as raw
    text to Discord), `lobbyHost` / `raceDirector` / `streamer`
    (`@Size(max=100)` each, plain-text).
  - **Hybrid Match-Detail page architecture (per D-01):**
    - NEW `GET /admin/matches/{id}` route on `MatchController` → renders
      new template `templates/admin/match-detail.html`. Read-only view
      with: match-header (team-shortNames + score + bye badge), Discord
      Actions panel (Create Discord Channel button visible when
      `discordChannelId == null && both teams have discordRoleId &&
      discord_global_config.current_match_category_id != ''`; once channel
      exists: render channel-link + Move-to-Archive button), Schedule
      summary table (5 Discord fields if set), Races section (link to
      each race-detail; reuses existing match-row inner layout).
    - NEW `GET /admin/matches/{id}/edit` → renders new template
      `templates/admin/match-form-edit.html` (separate from existing
      `match-form.html` which is the CREATE flow). Form binds to MatchForm
      DTO with `@Valid` + `BindingResult`; on save, redirects to
      `/admin/matches/{id}` (detail).
    - NEW `POST /admin/matches/save-edit` (or extend existing `/save` with
      mode-dispatch via `id != null` check — planner-discretion). Distinct
      from `POST /admin/matches/save` (which CREATES a new match — keeps
      legacy URL stable per CLAUDE.md § Backward Compatibility).
    - `matchday-detail.html` modified: each `.match-row` gets a small
      "→ Detail" link next to the existing Edit/Delete buttons. NO inline
      form fields for Discord on matchday-detail.html. Existing match
      rendering (score, bye, leg-container, race-links) stays unchanged.
  - **New `DiscordChannelService` in `org.ctc.discord.service`:**
    - `createMatchChannel(Match match)` (transactional method):
      1. Asserts `match.homeTeam.discordRoleId != null` + `match.awayTeam.discordRoleId != null`
         + `globalConfig.currentMatchCategoryId != ''` (else `BusinessRuleException`).
      2. Computes channel-name: `("md" + match.matchday.number + "-" +
         homeShort + "-vs-" + awayShort).toLowerCase()`. (Discord enforces
         lowercase + dash-separated server-side, but we pre-format to
         match.)
      3. Builds `ChannelCreateRequest` with `permission_overwrites` array
         (3 entries: @everyone deny VIEW_CHANNEL, teamA-role
         TEAM_MEMBER_ALLOW_MASK + TEAM_MEMBER_DENY_MASK, teamB-role same
         masks). Requires `ChannelCreateRequest` extension (new field
         `List<PermissionOverwrite> permissionOverwrites` + Discord
         JSON wire-name `permission_overwrites` via `@JsonProperty`) +
         new `PermissionOverwrite` record + new `DiscordPermissions`
         constants class (`TEAM_MEMBER_ALLOW_MASK`, `TEAM_MEMBER_DENY_MASK`,
         `EVERYONE_DENY_VIEW_MASK` as `static final long` per design
         spec § 4.4).
      4. `restClient.createChannel(guildId, request)` → `Channel` response
         with snowflake ID + Discord-server-normalized name.
      5. `restClient.createWebhook(channelId, "CTC Manager")` →
         `Webhook` response with ID + token (full webhook-URL constructed
         as `https://discord.com/api/webhooks/{id}/{token}`). REQUIRES
         new `DiscordRestClient.createWebhook(channelId, name)` typed
         method + `Webhook` DTO (records `id`, `token`, `url`).
      6. **Post-create permission-audit (D-04):**
         `restClient.fetchChannel(channelId)` → re-reads the just-created
         channel's `permission_overwrites`. Asserts: exactly 2 overwrites
         with type=role + allow-mask contains VIEW_CHANNEL + role-IDs
         match the 2 team-discord-role-IDs (@everyone deny is the third
         expected overwrite — passes audit). Any other role with
         allow=VIEW_CHANNEL → audit FAIL.
      7. **Audit-fail handling (D-04 hard rule):** On audit-fail OR on
         any exception between step 4 and step 8, call
         `restClient.deleteChannel(channelId)` (best-effort cleanup) and
         throw `DiscordAuthException` with hardcoded user-message constant
         "Channel-permission audit failed - role {X} had unexpected View
         permission. Channel was deleted; verify Discord server-role
         setup and retry." DB transaction rolls back (steps 8-9 never
         executed). If the cleanup DELETE itself fails: log the cleanup
         exception at WARN with the orphan channel-ID + append
         "Cleanup failed: please manually delete channel {id} via Discord."
         to the user-message via `redirectAttributes.errorMessage`. Both
         the audit-fail Exception AND the cleanup-fail Exception are
         covered by `DiscordApiExceptionMapper` constants (NOT
         `e.getMessage()` echo — T-91-02-IL invariant).
      8. Set `match.discordChannelId = channel.id`,
         `match.discordChannelWebhookUrl = webhook.url`.
      9. `matchRepository.save(match)` → COMMIT.
    - Wrap entire method in `@Transactional` so DB rollback on
      DiscordApiException is guaranteed.
  - `MatchController` extended with:
    - `GET /{id}` (detail render)
    - `GET /{id}/edit` (edit form)
    - `POST /{id}/save-edit` (or unified `/save` — planner-discretion)
    - `POST /{id}/create-discord-channel` (typed-catch sealed
      `DiscordApiException` → Phase-91 D-06 `errorCategory` flash pattern
      + `category-full` badge variant when applicable — though
      CHAN-02 itself never throws CategoryFull, only CHAN-03 does)
  - Tests: `MatchDetailControllerE2ETest` (Playwright: detail-page renders,
    Create-Channel button visibility-gating works), `MatchEditFormIT`
    (round-trip save of 5 Discord fields), `DiscordChannelServiceWireMockIT`
    (happy-path: createChannel returns 201 → fetchChannel returns
    permission-overwrites matching → DB writes), `DiscordChannelServicePermissionAuditFailIT`
    (WireMock: extra third role with View → expects DELETE call + thrown
    DiscordAuthException + DB unchanged), `DiscordChannelServiceCleanupFailIT`
    (WireMock: audit fails AND cleanup DELETE returns 500 → both exceptions
    surfaced in user-message). All ITs tagged `@Tag("integration")` per
    CLAUDE.md.

- **CHAN-03 (Plan 94-03)** — Archive Modal + Category Resolver:
  - New `DiscordCategoryResolver` service in `org.ctc.discord.service`:
    - `resolveArchiveCategoriesFor(int year)`: calls
      `restClient.listChannels(guildId)` → filters by `type == 4`
      (Discord category) → matches name against compiled regex
      `^Match Days Archive (?<year>\d{4})(?: \((?<num>\d+)\))?$` →
      filters by year-group-match equals param → sorts by num-group
      ascending (no-num = 1). Returns `List<ArchiveCategory>` record
      DTO with `id`, `name`, `num`, `currentChannelCount` (count of
      channels with this category's `id` as `parent_id`).
    - `defaultSelection(List<ArchiveCategory>)`: returns highest-num
      with `currentChannelCount < 50`, or `Optional.empty()` if all
      full / list is empty.
  - `MatchController.POST /{id}/move-to-archive` endpoint:
    - Accepts `@RequestParam String categoryId` from modal-form.
    - Calls `restClient.modifyChannel(channelId, new
      ChannelModifyRequest(null, null, categoryId))` (sets `parent_id`
      to the chosen category).
    - On `DiscordCategoryFullException`: handled by typed-catch + flash
      `errorCategory=category-full` with hardcoded user-message
      "All archive categories are full (50 channels each). Create a new
      `Match Days Archive {year} ({n+1})` category in Discord and retry."
      + badge variant `.error-badge--category-full` (already exists from
      Phase 91 D-07 + Phase 93 D-10 plan).
    - On empty-list case (no `Match Days Archive {year}` category exists
      at all): SAME `DiscordCategoryFullException` flow — semantic
      equivalence: operator-action required either way.
  - Modal UI on Match-Detail page (`match-detail.html`):
    - "Move to Archive" button (visible when `discordChannelId != null`).
    - On-click opens `<dialog>` modal with `<form>` POSTing to
      `/admin/matches/{id}/move-to-archive`.
    - Modal body: pre-fetched (via Controller call in `GET /{id}`) list
      of `ArchiveCategory` records rendered as radio-buttons:
      `Match Days Archive {year}{" (" + num + ")" if num > 1} —
      {currentChannelCount}/50`. Default-selected = highest-num with
      `<50`, OR if all full: no radio pre-selected + warning banner
      "All categories full — see operator runbook section 'Creating a
      new archive category'" (link to `docs/operations/discord-integration.md`
      placeholder — actual runbook content lands in Phase 98 DOCS-02).
    - Confirm-button submits the form; Cancel-button closes the modal.
    - NO `confirm()` JS prompt (the modal IS the confirmation surface —
      user must explicitly pick a category and click Confirm).
  - **`ChannelModifyRequest` extension:** existing DTO has `name` + `parentId`
    (per spec); planner verifies the JSON field-name maps to
    `parent_id` via `@JsonProperty` (already present per Phase 93 wiring).
  - Tests: `DiscordCategoryResolverWireMockIT` (regex matches multiple
    year-suffix variants, year-mismatch filtering, sort-by-num-ascending,
    default-selection: highest-num-with-room / all-full → empty,
    channel-count derivation), `ArchiveModalE2ETest` (Playwright: modal
    opens, lists categories with counts, default-radio pre-selected,
    confirm POST hits move-to-archive endpoint, full-category state
    renders banner). Mobile-sweep per D-06.

- **D-06 UI-Polish fold-in across all 3 plans:**
  - Each CHAN-Plan's new template (team-form extension, match-detail,
    match-form-edit, archive modal) is responsive-by-default: use
    `flex-wrap`, `gap` utilities, mobile-first widths instead of
    inline-styles (per CLAUDE.md § "No Inline Styles on Buttons").
  - Plan 94-01 ALSO ships a CSS fix for the UAT-03 mobile-overflow on
    `/admin/discord-config` (.inline-form / .btn-group responsive wrap
    behaviour added to `admin.css` `.discord-actions` cluster; benefits
    Phase 93's discord-config page AND the new team-form / match-detail
    pages). Wave-pause after each plan includes Mobile-screenshot sweep
    via `playwright-cli` (Desktop 1280×800 + Mobile 375×667) per
    [[feedback-playwright-cli]].

Out of scope (deferred / not Phase-94 scope):

- **All Phase-95 POST-* buttons (Team Cards / Settings / Lineups /
  Schedule / Match Results) on Match-Detail page** — Phase 94 builds the
  Match-Detail page surface + Discord Actions panel skeleton; the 5 POST
  buttons land in Phase 95 plans 95-02/03/04 alongside the
  `discord_post` tracking entity (Flyway V11). Phase 94's Match-Detail
  template scaffolds a placeholder `<div class="discord-actions
  discord-actions--posts">` section that Phase 95 fills.
- **`ProvisionalScoresGraphicService` + "Post Provisional Scores" button
  on Match-Detail** — Phase 96 GRAFX-01 + plan 96-03.
- **Season-Detail "Discord Integration" section** — Phase 96 FORUM-01.
- **Schedule auto-edit on form-save when stream-link / teaser changes**
  — Phase 95 POST-05 (the persistence path in Phase 94 only writes the
  DB fields; the "trigger Discord PATCH on field change" wiring is
  Phase 95).
- **`discord_post` tracking entity + `DiscordPostService.postOrEdit`
  pattern** — Phase 95 POST-01 (Flyway V11).
- **Live Discord UAT-04 (real channel-create + permission-audit + archive
  move against operator's test guild)** — runs as **UAT-04** under
  STATE.md `Pending UATs`, operator-action BEFORE Phase 95 plans 95-02/03/04
  start (analog UAT-03 → CHAN-02 gating from Phase 93 D-01). WireMock
  ITs cover all CHAN-01..03 happy + failure paths inside Phase 94 close;
  the live-Discord cycle (real bot creates real channel with real
  permission-overwrites, audit either passes or fails meaningfully)
  is the first time those code-paths hit production-grade Discord
  behaviour.
- **Operator runbook `docs/operations/discord-integration.md` extensions
  for CHAN-01/02/03 sections** — Phase 98 DOCS-02; Phase 94 plans only
  forward-reference the runbook from error-messages (e.g., the
  `category-full` flash message links to the placeholder section anchor;
  Phase 98 fills the content).
- **Match-channel-create "current-category" auto-rotation** (e.g., when
  `Match Days {year}` fills past 50, auto-create `Match Days {year}
  (2)`) — operator-manual scope; the typed `DiscordCategoryFullException`
  fires only on `move-to-archive`. The current-category for new channels
  is a single operator-set slot per D-02.
- **Per-team-role-color sync from Discord to CTC Manager** —
  out-of-scope (cosmetic, low value; Discord role colors are decorative
  in Discord chat, not in CTC admin or static site).

</domain>

<decisions>
## Implementation Decisions

### Match-Detail Page Architecture

- **D-01: Hybrid Match-Detail page (`/admin/matches/{id}`) + separate
  Edit-Page (`/admin/matches/{id}/edit`); matchday-detail.html stays
  minimal.** New routes on `MatchController`:
  - `GET /admin/matches/{id}` — read-only Match-Detail page renders header,
    Discord Actions panel (Create-Channel button + Move-to-Archive
    button + Phase-95/96 button placeholders), Schedule summary, Races
    sub-list.
  - `GET /admin/matches/{id}/edit` — Match-Form-Edit page with `MatchForm`
    DTO binding 5 new Discord fields (`discordTeaser`, `streamLink`,
    `lobbyHost`, `raceDirector`, `streamer`).
  - `POST /admin/matches/save-edit` (or unified `/save` with
    `id != null` mode-dispatch — planner-discretion) → redirects to
    Detail.
  - `POST /admin/matches/{id}/create-discord-channel` — Phase 94-02
    CHAN-02 action.
  - `POST /admin/matches/{id}/move-to-archive` — Phase 94-03 CHAN-03
    action.
  - `matchday-detail.html` is only modified to add a "→ Detail" link
    next to existing Edit/Delete buttons per match-row. NO inline-edit
    form fields for the 5 Discord-fields on matchday-detail.html.
  - Existing `POST /admin/matches/save` (CREATE flow) stays unchanged
    per CLAUDE.md § Backward Compatibility.
  Rationale: Phase 95-96 add 5-7 more buttons + "Discord Integration"
  panel sub-sections per match. Inline-on-matchday-detail.html (Option B)
  bloats that template heavily for 5+ matches/MD; inline-edit POST-per-field
  (Option B) multiplies CSRF surface + Mobile UX suffers. A single
  Match-Detail page concentrates the Discord surface, scales cleanly
  through Phases 95-96, and follows the existing `team-detail.html` /
  `matchday-detail.html` precedent (per-entity detail page is the
  established pattern in this admin app). REJECTED: Option B (inline-on-matchday)
  for bloat + mobile-overflow risk; Option C (edit-page-only with buttons
  inline) for the same Phase-95 button-scaling problem as Option B.

### Active Match-Channel Category (parent_id source)

- **D-02: Global single-slot `discord_global_config.current_match_category_id`
  (VARCHAR(32)) on the V8 table extended via V9.** Operator sets the
  active category once on `/admin/discord-config` (the field already
  shipped surface gets one more input row + the snowflake-validation
  pattern). All new match-channels created via CHAN-02 use this slot
  as `parent_id`. When the operator rotates seasons or fills a category,
  they update the single slot once. KEINE per-season / per-phase
  storage (Options B/C rejected as YAGNI for the single-active-league
  operator-model — multi-guild support is DISC-FUTURE-04). DiscordConfigForm
  gains a 7th field `currentMatchCategoryId` (snowflake-validated, empty
  string allowed). discord-config.html template gains one form-row in
  the existing 6-field layout.

### Migration V9 Bundling

- **D-03: V9 bundles BOTH ALTER TABLEs in one migration file:**
  `V9__add_discord_team_role_and_current_match_category.sql` contains:
  1. `ALTER TABLE teams ADD COLUMN discord_role_id VARCHAR(32);`
  2. `ALTER TABLE discord_global_config ADD COLUMN
     current_match_category_id VARCHAR(32) NOT NULL DEFAULT '';`
  H2 + MariaDB compatible. NO CHECK constraints (D-93-02 cross-engine
  drift discipline). NO default seed for `teams.discord_role_id`
  (existing teams stay NULL until operator fills the Team-Form). The
  bundled-migration approach beats two separate `V9` + `V9.5` files
  (Flyway convention is sequential integers; one migration for both
  CHAN-01 + the D-02 category-slot keeps the schema delta cohesive).
  V10 stays match-fields-only (`V10__add_matches_discord_and_scheduling_fields.sql`).
  V11 = Phase 95 `discord_post`, V12 = Phase 96 `seasons.discord_*_thread_id`
  as already planned.

### Permission-Audit Fail Behavior

- **D-04: On post-create permission-audit failure, Bot DELETEs the
  just-created channel AND the surrounding `@Transactional` rolls back
  the DB write.** Pattern in `DiscordChannelService.createMatchChannel(Match)`:
  ```java
  Channel channel = restClient.createChannel(guildId, req);
  Webhook webhook = restClient.createWebhook(channel.id(), "CTC Manager");
  try {
      assertPermissionAudit(channel.id(), match.homeTeam.discordRoleId,
                           match.awayTeam.discordRoleId);
  } catch (DiscordAuthException auditEx) {
      try {
          restClient.deleteChannel(channel.id());
      } catch (DiscordApiException cleanupEx) {
          log.warn("Audit-fail cleanup DELETE failed for channel {}: {}",
                   channel.id(), cleanupEx.toString());
          throw new DiscordAuthException(
              DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE
              + " Cleanup failed: please manually delete channel "
              + channel.id() + " via Discord.", auditEx);
      }
      throw auditEx;
  }
  match.setDiscordChannelId(channel.id());
  match.setDiscordChannelWebhookUrl(webhook.url());
  matchRepository.save(match);
  ```
  - `DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE` constant added: "Channel
    permission audit failed - an unexpected role had View permission.
    Channel was deleted; verify Discord server-role setup and retry."
    (No `e.getMessage()` echo — T-91-02-IL invariant.)
  - "Audit passes" definition: exactly 3 permission-overwrites on the
    fetched channel — `@everyone` (deny VIEW_CHANNEL), `teamA.discordRoleId`
    (allow VIEW_CHANNEL via TEAM_MEMBER_ALLOW_MASK), `teamB.discordRoleId`
    (same). ANY additional role with `(allow & VIEW_CHANNEL) != 0` →
    fail. Server-admin roles inherently bypass channel-permissions at
    the Discord runtime level (Manage_Channels permission grants
    visibility regardless of overwrites) — this is NOT detectable via
    `permission_overwrites` audit and is OUT-OF-SCOPE (documented as a
    forward-reference in `93-THREAT-MODEL.md` T-93-03 mitigation column
    so Phase 98 DOCS-02 runbook can flag it operationally).
  - `DiscordRestClient` needs new typed methods: `createWebhook(String
    channelId, String name)` → `Webhook` DTO, `fetchChannel(String
    channelId)` → `Channel` (with permission-overwrites populated;
    extend existing `Channel` record), `deleteChannel(String channelId)`
    → void. Wire-up via existing `execute()` exception-mapper helper.
  - REJECTED: Option B (leave channel + lauten error) — operator-cleanup-
    forgetting risk + zombie-channels eat category slots + weaker for
    T-93-03 (opposing team sees channel until manual cleanup); Option C
    (auto-revoke roles + warn) — masks operator-setup errors instead of
    surfacing them, Discord-permission-overwrite-count limits make full
    revocation impossible at scale (admin roles cannot be revoked via
    channel-overwrites alone).

### Live-Role Dropdown UX + Role Cache Shape

- **D-05: New `DiscordRoleCache` class in `org.ctc.discord`, structurally
  identical to `DiscordEmojiCache` (D-93-03 pattern). Team-Form renders
  cache-only dropdown with plain-text fallback.**
  - Class shape: `ConcurrentHashMap<String, CachedEntry<Role>>` keyed
    by Discord role-ID (snowflake string), value = `Role` record from
    existing `org.ctc.discord.dto.Role`. TTL = `Duration.ofMinutes(60)`.
    `Clock` injected via existing Spring `@Bean Clock systemClock()`
    in `DiscordConfig`. APIs: `Map<String, Role> snapshot()` (returns
    all currently-cached entries as immutable map), `int refresh(List<Role>
    roles)` (bulk-replace + log count), `Role get(String roleId)`
    (lookup-or-null).
  - Existing `DiscordConfigController.refreshRolesCache()` method
    (Phase 93 INFRA-03) is extended: after
    `restClient.fetchGuildRoles(guildId)` returns the list, also call
    `roleCache.refresh(roles)`. Single button-click fills both the
    Discord-side cache AND now the role-cache for Team-Form to read.
  - TeamController.edit() injects `Model.addAttribute("discordRoles",
    roleCache.snapshot())`. The team-form.html template reads this map:
    - If non-empty → renders `.searchable-dropdown` with options
      `<option value="{id}">{name}</option>` for each role, hidden input
      bound to `teamForm.discordRoleId` (existing `searchable-dropdown.js`
      JS pattern). Pre-selects the existing `discordRoleId` if set.
    - If empty → renders plain `<input type="text" pattern="^\d{17,20}$">`
      bound to `teamForm.discordRoleId` + a `.badge-warning` adjacent
      span: "Refresh Server Roles Cache on
      `<a href='/admin/discord-config'>/admin/discord-config</a>` to
      enable dropdown picker. Plain-text entry works as fallback."
  - Operator-workflow assertion: post-UAT-03, operator already knows to
    use the existing "Refresh Server Roles Cache" button — same
    operator-discipline pattern. No new UX-affordance burden.
  - REJECTED: Option B (eager-fetch on Team-Form GET) — Team-Form edit
    suddenly depends on Discord-API-reachability for every page-load,
    breaks the existing principle that DB-only admin pages should not
    require external service availability; Option C (AJAX-on-focus) —
    extra CSRF endpoint + JS race-condition surface + visible delay on
    first focus.

### UI-Polish Fold-In Strategy

- **D-06: UI-polish CSS fix lands inside Phase 94 plans, NOT deferred to
  Phase 98.** Plan 94-01 ships the `admin.css` `.discord-actions` /
  `.inline-form` / `.btn-group` responsive-wrap rule (closes the UAT-03
  mobile-overflow on `/admin/discord-config`) as part of the team-form.html
  + admin.css delta. Plans 94-02 + 94-03 then use the same CSS cluster
  for the Match-Detail page + Archive modal, ensuring all new Discord
  pages are mobile-correct on first ship. Wave-pause after each plan
  ([[feedback-wave-pause]]) MUST include a `playwright-cli` Mobile
  (375×667) + Desktop (1280×800) screenshot-sweep for every new/touched
  Discord-page; screenshots land under `.screenshots/94-{plan}/`
  ([[feedback_screenshots_folder]]). Rationale: a single CSS pass
  benefits both Phase 93's existing page AND Phase 94's new pages
  simultaneously; deferring to Phase 98 means operator UAT-04 runs on
  mobile-broken pages and triggers another deferred-debt cycle. REJECTED:
  Option B (defer to Phase 98) — exactly the deferred-debt pattern we
  just closed for UAT-03 itself; Option C (separate Plan 94-04
  UI-only) — breaks the "3 plans 1:1 CHAN-01/02/03" symmetry from Phase
  93 D-05 + design spec § 5 plan-count.

### Plan Decomposition & Sequencing (carried forward from Phase 93 D-05)

- **D-07: Three plans, sequential inline on `gsd/v1.13-discord-integration`.**
  Mirrors Phase 92 D-05 + Phase 93 D-05 + Design Spec § 5. Order:
  - **Plan 94-01 — CHAN-01 Team Role Mapping + DiscordRoleCache + UI
    Polish Base.** Flyway V9 (bundled ALTER TABLEs per D-03), `Team`
    entity field + `TeamForm` extension with shared `DiscordSnowflake.PATTERN`
    constant, `TeamManagementService.save()` signature extension,
    `DiscordRoleCache` class + DiscordConfig bean wiring,
    `DiscordConfigController.refreshRolesCache()` extension to populate
    the role-cache, `discord-config.html` template extension for
    `currentMatchCategoryId` field, `team-form.html` template extension
    for `discordRoleId` field with searchable-dropdown / plain-text
    fallback, `admin.css` `.discord-actions` responsive-wrap CSS fix
    (D-06 fold-in for the UAT-03 mobile overflow), tests
    (TeamFormSnowflakeValidationTest, TeamRepositoryDiscordRoleIdIT,
    DiscordRoleCacheTest, TeamFormDiscordRoleDropdownE2ETest with
    Desktop + Mobile sweep).
  - **Plan 94-02 — CHAN-02 Match-Detail Page + Channel-Creation Service
    + Permission Audit.** Flyway V10 (matches.discord_* + scheduling
    fields), `Match` entity 7-field extension with
    `@ToString.Exclude` on webhookUrl, `MatchForm` DTO,
    `MatchController` GET-detail + GET-edit + POST-save-edit + POST-create-discord-channel
    endpoints, new templates `templates/admin/match-detail.html` +
    `templates/admin/match-form-edit.html`, `matchday-detail.html`
    "→ Detail" link addition, `DiscordChannelService.createMatchChannel`
    with D-04 audit + cleanup pattern, `DiscordRestClient` typed-method
    extensions (`createWebhook`, `fetchChannel`, `deleteChannel`),
    `ChannelCreateRequest` extension with `permission_overwrites` array,
    new `PermissionOverwrite` record + `DiscordPermissions` constants
    class + `Webhook` DTO, tests (MatchDetailControllerE2ETest,
    MatchEditFormIT, DiscordChannelServiceWireMockIT happy-path,
    DiscordChannelServicePermissionAuditFailIT, DiscordChannelServiceCleanupFailIT).
  - **Plan 94-03 — CHAN-03 Archive Modal + Category Resolver.** New
    `DiscordCategoryResolver` service + `ArchiveCategory` record DTO,
    `MatchController.POST /{id}/move-to-archive` endpoint with typed
    `DiscordCategoryFullException` catch + `category-full` flash badge,
    `match-detail.html` Archive-modal `<dialog>` element + form,
    `admin.css` `.error-badge--category-full` variant (if not already
    landed by Phase 93 — verify),
    `DiscordRestClient.modifyChannel(parentId)` flow exercised via
    `ChannelModifyRequest`, tests (DiscordCategoryResolverWireMockIT,
    ArchiveModalE2ETest with Desktop + Mobile sweep).
  No worktrees, no subagents per [[feedback-inline-sequential-execution]].
  Wave-pause + Mobile-sweep after each plan per [[feedback-wave-pause]]
  + [[feedback-playwright-cli]] (D-06).

### PR Mechanics (carried forward from Phase 92 D-06 / Phase 93 D-06)

- **D-08: Rolling v1.13 milestone PR — each Phase-94 plan-ship updates
  body via `gh pr edit --body-file`.** Phase 92 Plan 92-01 opened the
  Draft PR; Phases 93 + 94 plans only update it. Each plan-ship appends
  a new entry to the rolling per-plan summary table (Plan # / REQ-ID /
  status / commit SHA / CI run URL). Subject locked for the eventual
  squash: `feat(v1.13): discord integration & carry-forwards`
  ([[feedback-squash-merge-message]]). PR stays Draft until end of Phase
  98.

### Quality Gates (carried forward from Phase 92 D-07 / Phase 93 D-07)

- **D-09: Standard gates apply, no tightening, no loosening.**
  - JaCoCo line coverage ≥ 88.88 % at end of Phase 94. Phase 94 adds
    ~30-50 tests (Form-validation, Repository IT, WireMock-ITs for 3
    paths × 2-3 variants, 3 Playwright E2E + Mobile sweep). Coverage
    MUST hold above the Phase 93 baseline.
  - SpotBugs `BugInstance` count = 0 (blocking, verify-bound check).
    `org.ctc.discord.service.*` package added without surfacing new
    `EI_EXPOSE_REP*` findings; `@ToString.Exclude` on
    `Match.discordChannelWebhookUrl` avoids the same trap as Phase 93
    `DiscordGlobalConfig.announcementWebhookUrl`. New `DiscordPermissions`
    constants class is pure-static — no Lombok / no exposure findings.
  - CodeQL gate-step exit 0 on PR HEAD SHA. No new SSRF-suppression
    needed (DiscordRestClient already constructor-guards via
    `DiscordHostValidator`). Three-layer FP invariant (Phase 85 D-19)
    applies if `DiscordChannelService.createMatchChannel` triggers a
    new SSRF finding (planner-discretion to add the codeql-config.yml
    + source-marker + sast-acceptance.md triad if needed).
  - `EXPORT_ORDER` = 24 entities; `BackupSchema.SCHEMA_VERSION` = 1.
    Phase 94 adds NO new entities in `org.ctc.domain.model.*` (Team +
    Match field extensions only — both ALREADY in `org.ctc.domain.model.*`
    and tracked). `BackupSchemaGuardTest` stays green at 24. The new
    `discord_role_id` / `current_match_category_id` / 7
    match.discord_*+scheduling columns ARE included in backup wire
    contract automatically (they're columns on existing in-scope
    entities). NO `SCHEMA_VERSION` bump because backup schema-version
    tracks the entity-set + Jackson-serialization shape, not
    individual-column count (per Phase 72 D-15 design — verify with
    planner whether Lombok-getter exposure of the 7 new `Match` fields
    needs MixIn extension OR if the existing MixIn covers them
    structurally; same question for the 2 new `Team` / `DiscordGlobalConfig`
    columns).
  - Flyway V1-V8 immutable; Phase 94 adds V9 + V10.
  - `./mvnw verify -Pe2e` finishes within v1.12 CI E2E 17:39 ± 20 %
    tolerance. Phase 94 adds ~5-10 WireMock-backed ITs + 3 Playwright
    E2E + Mobile-sweep. Expected impact: < 90 s.

### Test Discipline (carried forward from Phase 93 D-08 / D-09)

- **D-10: Per-Plan Nyquist VALIDATION.md.** Plans 94-01..94-03 each ship
  with a VALIDATION.md. Phase 94 self-validates via `/gsd-validate-phase 94`
  before `/gsd-execute-phase 95` starts.

- **D-11: Tag every new test class per CLAUDE.md `@Tag` convention.**
  - WireMock-backed ITs (`DiscordChannelService*IT`, `DiscordCategoryResolver*IT`,
    `TeamRepositoryDiscordRoleIdIT`, `MatchEditFormIT`) → `@Tag("integration")`.
  - Mockito-only unit tests (`TeamFormSnowflakeValidationTest`,
    `DiscordRoleCacheTest`, `DiscordPermissionsTest`,
    `MatchFormValidationTest`) → untagged (project convention).
  - Playwright E2E (`TeamFormDiscordRoleDropdownE2ETest`,
    `MatchDetailControllerE2ETest`, `ArchiveModalE2ETest`) →
    `@Tag("e2e")`, package `org.ctc.e2e.discord` per
    `.planning/codebase/TESTING.md` § Test Categorization.

### Live-UAT Strategy (analog to Phase 93 D-01)

- **D-12: WireMock-IT-only Phase-94 close; UAT-04 deferred to STATE.md
  `Pending UATs` (operator action before Phase 95 plans 95-02/03/04).**
  Phase 94 closes when `./mvnw verify -Pe2e` is green and all WireMock
  ITs cover CHAN-01..03 happy + failure paths. The live-Discord UAT
  (UAT-04) executes against the operator's test guild:
  1. Refresh-Server-Roles-Cache populates the role-cache → Team-Form
     dropdown renders with real role names.
  2. Edit a test team, pick a role, save → DB persists snowflake.
  3. Create a test match between two role-bound teams → Create Discord
     Channel button visible.
  4. Click Create → live channel + webhook created with the 14
     permission-overwrites (3 entries × allow/deny masks).
  5. Audit-pass case: green flash badge + channel link works.
  6. Audit-fail case: operator temporarily grants a third role View
     permission on the test guild → re-trigger → expects red flash badge
     "Channel permission audit failed" + verify channel was deleted.
  7. Move-to-Archive case: operator sets `current_match_category_id` to
     a near-full category (e.g., 49/50), creates a channel (fills
     50/50), then for a second test creates a new channel with the
     same category → expects `category-full` flash badge after attempting
     archive-move.
  Operator runs UAT-04 BEFORE Phase 95 plans 95-02/03/04 start (the
  POST buttons require an existing channel + webhook; UAT-04 produces
  exactly that). Phase 95 plan 95-01 (POST-01 = `discord_post` entity
  + service skeleton) does NOT require UAT-04 — it's pure DB +
  WireMock-IT — so plan 95-01 can start immediately after Phase 94
  closes; UAT-04 is the hard precondition for 95-02 onwards.

### Production Behavior Boundary

- **D-13: Production code touched in Phase 94 stays within these paths:**
  `src/main/java/org/ctc/discord/**` (new role-cache, channel-service,
  category-resolver, dto extensions),
  `src/main/java/org/ctc/domain/model/{Team,Match}.java` (field
  extensions only — no behavior changes),
  `src/main/java/org/ctc/admin/dto/{TeamForm,MatchForm}.java`,
  `src/main/java/org/ctc/admin/controller/{TeamController,MatchController}.java`,
  `src/main/java/org/ctc/domain/service/{TeamManagementService,MatchService}.java`
  (method-signature extensions), `src/main/resources/db/migration/V9*.sql`
  + `V10*.sql`, `src/main/resources/templates/admin/{discord-config,
  team-form,match-detail,match-form-edit,matchday-detail}.html`,
  `src/main/resources/static/admin/css/admin.css`. No edits to
  `org.ctc.dataimport.*`, `org.ctc.backup.*`, `org.ctc.sitegen.*`,
  `org.ctc.gt7sync.*`, `org.ctc.scoring.*` (Phase-94 is Discord-feature-scope,
  not cross-cutting). Each plan SUMMARY asserts `src/` clean outside
  the explicitly listed paths.

### Snowflake Constant Extraction

- **D-14: Extract snowflake validation regex to shared constant in
  `org.ctc.discord.dto`** (Plan 94-01):
  ```java
  public final class DiscordSnowflake {
      public static final String PATTERN = "^$|^\\d{17,20}$";
      public static final String MESSAGE = "Must be a Discord snowflake (17–20 digits) or empty";
      private DiscordSnowflake() {}
  }
  ```
  `DiscordConfigForm` (Phase 93) gets refactored to reuse the constant
  (5 `@Pattern` annotations replaced); `TeamForm` (Phase 94 CHAN-01),
  `MatchForm` (Phase 94 CHAN-02 if needed for snowflake fields — but
  `match.discordChannelId` is internal/auto-populated not Form-bound),
  Future `SeasonForm` (Phase 96 FORUM-01 `discord*ThreadId` fields)
  all reuse. Avoids regex-drift across Forms. Same shape as
  `org.ctc.discord.dto.DiscordConfigForm` package-local constants
  (Phase 93 D-93-02) but now promoted to shared class.

### Claude's Discretion

- **`MatchForm` location**: planner-discretion `org.ctc.admin.dto`
  (sibling to `MatchdayForm`, `TeamForm`) per CLAUDE.md § Naming
  Patterns. NO alternative considered.
- **`DiscordPermissions` constants location**: `org.ctc.discord` (sibling
  to `DiscordRoleCache`, `DiscordEmojiCache`) OR `org.ctc.discord.dto`
  (sibling to `ChannelCreateRequest`, `PermissionOverwrite`) — planner
  picks based on cohesion with whichever class touches it most.
- **Exact CSS class names for `.discord-actions` cluster** — planner
  verifies against existing `admin.css` palette; likely cohesive with
  the Phase 93 `.discord-actions` cluster already used on
  `/admin/discord-config`.
- **Whether `POST /admin/matches/{id}/save-edit` is a NEW route or
  unified into existing `POST /admin/matches/save` with `id != null`
  mode-dispatch** — planner picks based on CLAUDE.md § Backward
  Compatibility + existing controller convention.
- **`Channel` record field for `permission_overwrites` population on
  the fetch-back** — planner verifies whether the existing
  `org.ctc.discord.dto.Channel` record's `@JsonIgnoreProperties(ignoreUnknown
  = true)` shape needs extension to surface the array, OR whether a new
  `ChannelWithPermissions` record is cleaner.
- **Archive modal: `<dialog>` element vs. CSS overlay div** —
  `<dialog>` is HTML5-native + accessible OOTB; CSS overlay is
  established admin pattern (if any). Planner picks based on
  `admin/layout.html` precedent.
- **Whether the placeholder `<div class="discord-actions
  discord-actions--posts">` section in `match-detail.html` is empty
  in Phase 94 or has a "TODO Phase 95" comment** — planner picks; both
  acceptable.
- **Permission-overwrite `type` field encoding (Discord API: 0 = role,
  1 = member)** — planner uses `0` for role-based overwrites (the only
  type Phase 94 emits); document the magic number with a constant in
  `DiscordPermissions` or `PermissionOverwrite`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- `.planning/ROADMAP.md` § "Phase 94: Team Roles + Match Channel Lifecycle"
  — goal, Depends-on (Phase 93), Requirements (CHAN-01, CHAN-02, CHAN-03)
- `.planning/REQUIREMENTS.md` § "Discord Channel Lifecycle (CHAN, Phase
  94)" — CHAN-01/02/03 full requirement text + acceptance criteria
- `.planning/milestones/v1.13-ROADMAP.md` § "Phase 94" — Success Criteria
  + Phase Dependency Graph (depends on Phase 93 INFRA; Phase 95
  POST-01 forward-chain depends on this phase's V10 + Match-Detail page)
- `.planning/PROJECT.md` § "Current Milestone: v1.13" — "zero new
  production dependencies", "outbound-only", "button-triggered" invariants
- `.planning/STATE.md` § "Active Milestone — v1.13" + § "Baselines to
  Preserve" (JaCoCo ≥ 88.88 %, CI E2E 17:39 ± 20 %, SpotBugs 0, CodeQL
  exit 0, EXPORT_ORDER 24, SCHEMA_VERSION 1, Flyway V1-V8 immutable,
  V9 + V10 land here) + § "Pending UATs" (UAT-03 ✅ Resolved; UAT-04
  to be added during Phase 94 close per D-12) + § "Deferred Items"
  (UI debt mobile-overflow rolled into D-06)
- `docs/superpowers/specs/2026-05-20-discord-integration-design.md` —
  THE authoritative design document. § 3.2 Package Layout
  (`org.ctc.discord.*`), § 3.3 Data Model (V9 `teams.discord_role_id`,
  V10 `matches.discord_*`), § 3.4 Security (T-93-03 channel-permission
  bypass mitigation is THIS phase's CHAN-02 audit), § 3.5 Error
  Handling (sealed exception pattern with `category-full` permit used
  in CHAN-03), § 4.2 Extended Pages (Team-Form, Match-Form, Match-Detail,
  Matchday-Detail), § 4.3 Button Matrix (Match-Detail buttons + Modal
  for archive), § 4.4 Channel Permission-Overwrite Model (3 entries:
  @everyone deny + 2 team-role allow/deny masks — VERBATIM Phase 94
  CHAN-02 implementation contract), § 5 Phase Breakdown (Phase 94 = 3
  plans), § 6 Risks (channel-permission bypass + rate-limit-burst), § 9
  Resolved Brainstorming Decisions (D-Spec-6 team-role-mapping, D-Spec-12
  archive-regex, D-Spec-16 channel-permissions = explicit allow + deny
  bitmasks)

### Phase 93 Hand-off (PRIMARY INPUT — INFRA foundation)

- `.planning/phases/93-discord-foundation/93-CONTEXT.md` — **D-93-01
  (UAT-03 deferred + run before Phase 94 CHAN-02; UAT-03 PASSED
  2026-05-21)**, **D-93-02 (single-row `findFirstByOrderByIdAsc()`
  pattern for DiscordGlobalConfig + V8 seed-row + DiscordConfigForm
  snowflake regex — Phase 94 D-02 extends with new field)**, **D-93-03
  (hand-rolled `CachedEntry<T>` + Clock pattern — Phase 94 D-05 reuses
  for DiscordRoleCache)**, **D-93-04 (`93-THREAT-MODEL.md` — Phase 94
  CHAN-02 closes the T-93-03 mitigation; T-93-01/02/04 carry forward
  unchanged)**, **D-93-05 (sequential inline on
  `gsd/v1.13-discord-integration`, no worktrees, no subagents)**,
  **D-93-06 (rolling milestone PR)**, **D-93-07 (standard quality gates)**,
  **D-93-08 (per-plan VALIDATION.md)**, **D-93-09 (`@Tag` convention)**,
  **D-93-11 (production code boundary discipline)**.
- `.planning/phases/93-discord-foundation/93-THREAT-MODEL.md` — Phase
  94 CHAN-02 implements the T-93-03 mitigation (post-create
  permission-audit assertion); the threat-model row's "Verification"
  column gets a forward-reference to `DiscordChannelServicePermissionAuditFailIT`.
- `src/main/java/org/ctc/discord/DiscordRestClient.java` — existing
  `createChannel`, `modifyChannel`, `listChannels`, `fetchGuildRoles`,
  `fetchGuildEmojis` methods. Phase 94 ADDS: `createWebhook(channelId,
  name)`, `fetchChannel(channelId)`, `deleteChannel(channelId)`.
- `src/main/java/org/ctc/discord/dto/ChannelCreateRequest.java` — extend
  with `List<PermissionOverwrite> permissionOverwrites` field +
  `@JsonProperty("permission_overwrites")`.
- `src/main/java/org/ctc/discord/dto/Channel.java` — verify whether
  `permission_overwrites` array surfaces correctly OR if a separate
  `ChannelWithPermissions` record is needed for the audit-fetch path.
- `src/main/java/org/ctc/discord/DiscordEmojiCache.java` — EXACT
  structural template for `DiscordRoleCache` (D-05). Copy the file
  shape, swap names, swap value-type from `String` to `Role`.
- `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` — Phase 94
  D-02 adds 7th field `currentMatchCategoryId`. Plan 94-01 refactors
  the 5 hardcoded `@Pattern` annotations to use new
  `DiscordSnowflake.PATTERN` constant (D-14).
- `src/main/java/org/ctc/discord/web/DiscordConfigController.java` —
  existing `refreshRolesCache()` method (Phase 93 INFRA-03); Phase 94
  CHAN-01 extends to ALSO call `roleCache.refresh(...)` after
  `fetchGuildRoles()`.
- `src/main/resources/templates/admin/discord-config.html` — Phase 94
  D-02 adds one form-row for `currentMatchCategoryId`.
- `src/main/resources/db/migration/V8__discord_global_config.sql` —
  reference for V9 ALTER TABLE shape (H2 + MariaDB compatible,
  VARCHAR(32) for snowflakes, NOT NULL DEFAULT '' for non-nullable
  string fields).

### Phase 91 Hand-off (PRIMARY INPUT — typed-catch + flash pattern)

- `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-CONTEXT.md`
  — **D-06 (sealed `GoogleApiException` + 4 permits + Mapper —
  Phase 94 reuses the structural pattern via DiscordApiException already
  in place)**, **D-07 (`errorMessage` + `errorCategory` flash-attribute
  with `.error-badge--{category}` BEM CSS — Phase 94 reuses for
  `category-full` variant in CHAN-03)**.
- `src/main/java/org/ctc/admin/controller/DriverSheetImportController.java`
  — exact typed-catch + flash-badge controller pattern Phase 94
  MatchController.create-discord-channel + move-to-archive endpoints
  mirror.

### v1.5/v1.6 Admin UI Anchors

- `src/main/java/org/ctc/admin/controller/TeamController.java` — Phase
  94 extends `edit()` to inject `discordRoles` Model attribute;
  `save()` to thread `discordRoleId` through to
  `TeamManagementService.save()`.
- `src/main/java/org/ctc/admin/dto/TeamForm.java` — Phase 94 D-14 adds
  `@Pattern(DiscordSnowflake.PATTERN)` field `discordRoleId`.
- `src/main/java/org/ctc/admin/dto/MatchdayForm.java` — canonical
  shape for the new `MatchForm` DTO.
- `src/main/resources/templates/admin/team-form.html` — Phase 94 adds
  `discordRoleId` field with searchable-dropdown / plain-text fallback.
- `src/main/resources/templates/admin/matchday-detail.html` — Phase 94
  adds "→ Detail" link per match-row (NO inline-edit).
- `src/main/resources/static/admin/js/searchable-dropdown.js` —
  canonical JS pattern Phase 94 reuses for `discordRoleId` dropdown.
- `src/main/resources/static/admin/css/admin.css` — Phase 94 adds
  `.discord-actions` cluster + responsive-wrap rules (D-06).
- `src/main/resources/templates/admin/discord-config.html` — Phase 94
  D-02 adds form-row.

### Flyway / Migration Conventions

- `src/main/resources/db/migration/V8__discord_global_config.sql` —
  Phase 94 V9 mirrors the H2 + MariaDB compatible shape (no CHECK, no
  LONGTEXT, VARCHAR(32) snowflakes, NOT NULL DEFAULT '' string fields).
- `src/main/resources/db/migration/V7__data_import_audit.sql` — H2 +
  MariaDB compatible migration shape reference.
- `.planning/codebase/STACK.md` — Flyway version + DB engine
  compatibility constraints.

### Codebase Foundation Docs

- `.planning/codebase/ARCHITECTURE.md` — 3-tier layered architecture
  Phase 94 conforms to (Controller → Service → Repository); OSIV
  active.
- `.planning/codebase/CONVENTIONS.md` — naming patterns Phase 94
  follows.
- `.planning/codebase/STACK.md` — Spring Boot 4 + Spring 6.1+ RestClient
  availability.
- `.planning/codebase/TESTING.md` § "Test Categorization (`@Tag`)" —
  `@Tag("integration")` for `*IT.java`, `@Tag("e2e")` for
  `org.ctc.e2e.*`, untagged for unit tests.
- `.planning/codebase/INTEGRATIONS.md` — outbound integration surface
  (Phase 94 ADDS Discord channel-create + webhook-create + permission-audit
  + channel-modify + category-list operations to the surface).

### Conventions & Memory Anchors

- `CLAUDE.md` § "Test Naming (Given-When-Then)" + § "Tag Tests by
  Category" — every new test class follows BDD pattern + correct tag.
- `CLAUDE.md` § "Architectural Principles" — "DTOs instead of Entities
  in Controllers" (MatchForm DTO), "Keep Thymeleaf Templates Lean"
  (match-detail.html prepares data in service, template renders only),
  "No Inline Styles on Buttons" (D-06 CSS-class discipline), "Do Not
  Modify Flyway Migrations" (V1-V8 immutable).
- `CLAUDE.md` § "Static Analysis (SpotBugs + find-sec-bugs)" — Phase
  94 keeps `BugInstance` count = 0.
- `CLAUDE.md` § "CodeQL SAST (Code Scanning)" — gate-step exit 0 on
  PR HEAD SHA.
- `CLAUDE.md` § "Subagent Rules" — execution per
  [[feedback-inline-sequential-execution]] (no subagents during Phase
  94 execution).
- [[feedback-spring-native-preference]] — Spring `RestClient` (already
  in place); Phase 94 new methods (`createWebhook`, `fetchChannel`,
  `deleteChannel`) all extend the existing RestClient bean.
- [[feedback-inline-sequential-execution]] — D-07 sequential execution
  on `gsd/v1.13-discord-integration` is binding.
- [[feedback-wave-pause]] — pause for user feedback after each plan
  ship + screenshot-sweep per D-06 before starting the next plan.
- [[feedback-pr-description-update]] — D-08 rolling milestone PR body
  update pattern is binding.
- [[feedback-squash-merge-message]] — D-08 final PR subject
  `feat(v1.13): discord integration & carry-forwards` is binding for
  the MINOR bump.
- [[feedback-no-flaky-dismissal]] — Phase 94 tests that pass locally
  but fail in CI are regressions, never deferred.
- [[feedback-clean-build-only]] — Phase 94 verifies via
  `./mvnw clean test-compile` + `./mvnw verify -Pe2e`; no skip flags.
- [[feedback-clean-maven-build-authority]] — if compile errors appear
  in VS Code/Eclipse JDT (likely with new sealed-permit additions or
  signature changes on TeamManagementService.save), run
  `./mvnw clean test-compile` FIRST.
- [[feedback-playwright-cli]] — D-06 Desktop + Mobile sweep after every
  plan ship.
- [[feedback-screenshots-folder]] — D-06 screenshots land under
  `.screenshots/94-{plan}/`.
- [[feedback-test-call-optimization]] — gezielte `-Dtest=` for new
  Phase-94 ITs during dev; ONE final `./mvnw verify -Pe2e` per plan
  close.

### Discord External API (informational, no offline doc)

- Discord REST API v10 (`https://discord.com/developers/docs/resources/channel`
  + `https://discord.com/developers/docs/resources/guild#create-guild-channel`
  + `https://discord.com/developers/docs/resources/webhook#create-webhook`
  + `https://discord.com/developers/docs/topics/permissions`) — Phase
  94 WireMock fixtures encode the request/response shapes. Specifically:
  - `POST /guilds/{id}/channels` body `permission_overwrites: [...]`
    with `{id, type, allow, deny}` shape (type 0 = role, 1 = member).
  - `POST /channels/{id}/webhooks` body `{name: "..."}` returns
    `{id, token, url, channel_id}`.
  - `GET /channels/{id}` response includes `permission_overwrites`
    array (for audit-fetch).
  - `PATCH /channels/{id}` body `{parent_id: "..."}` for move-to-archive.
  - `DELETE /channels/{id}` returns 204 No Content for audit-fail cleanup.
- Discord permission bitmask reference: `VIEW_CHANNEL = 1 << 10`,
  `SEND_MESSAGES = 1 << 11`, `MANAGE_CHANNELS = 1 << 4`,
  `MANAGE_WEBHOOKS = 1 << 29`, etc. — `DiscordPermissions` constants
  class encodes these as `static final long` per design spec § 4.4
  (planner verifies bit-positions against Discord docs at code time
  to avoid wire-incompatibility).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`org.ctc.discord.DiscordEmojiCache` (Phase 93 INFRA-01)** — EXACT
  structural template for `DiscordRoleCache` (D-05). 42 lines:
  `ConcurrentHashMap<String, CachedEntry<T>>` + Clock + TTL constant +
  `refresh()` + `lookup()`. Copy the file, swap value-type from
  `String` to `Role`, swap log message. `DiscordEmojiCacheTest`
  similarly is the template for `DiscordRoleCacheTest`.
- **`org.ctc.discord.dto.DiscordConfigForm`** — Lombok shape +
  Jakarta-Validation pattern Phase 94 `TeamForm` extension + new
  `MatchForm` mirror. Phase 94 also extracts the snowflake regex to a
  shared `DiscordSnowflake.PATTERN` constant (D-14).
- **`org.ctc.discord.DiscordRestClient`** — Phase 94 EXTENDS with 3
  new typed methods (`createWebhook`, `fetchChannel`, `deleteChannel`)
  following the existing `execute(() -> ...)` helper-call pattern
  (lines 118-126).
- **`org.ctc.discord.dto.ChannelCreateRequest`** — Phase 94 EXTENDS
  with `List<PermissionOverwrite> permissionOverwrites` field +
  `@JsonProperty("permission_overwrites")`. Keep backward-compat:
  existing Phase 93 IT callers that don't set the field send it as
  `null` → Jackson omits in JSON if `@JsonInclude(NON_NULL)` is set
  (planner verifies the record's serialization shape).
- **`org.ctc.discord.dto.ChannelModifyRequest`** — already has `parentId`
  field; Phase 94 CHAN-03 just exercises it via the new
  `MatchController.POST /{id}/move-to-archive` endpoint.
- **`org.ctc.discord.exception.DiscordCategoryFullException`** — sealed
  permit shipped Phase 93; Phase 94 CHAN-03 is the FIRST production
  thrower (`DiscordCategoryResolver` raises it when `defaultSelection`
  returns empty OR when all categories' `currentChannelCount >= 50`).
  `DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE` constant pulled in.
- **`org.ctc.admin.dto.TeamForm`** — 5-field Lombok DTO; Phase 94
  CHAN-01 adds 6th field `discordRoleId` (snowflake-pattern).
- **`org.ctc.domain.service.TeamManagementService.save(id, name,
  shortName, primaryColor, secondaryColor, accentColor)`** —
  Phase 94 extends positional-arg signature with `discordRoleId`
  parameter. NO new method; reuse existing flow.
- **`org.ctc.admin.controller.TeamController`** — Phase 94 modifies:
  `edit()` to inject `discordRoles` Model attribute;
  `save()` to thread `form.getDiscordRoleId()` through.
- **`org.ctc.admin.controller.MatchController`** — minimal today (4
  endpoints). Phase 94 ADDS: `GET /{id}` (detail), `GET /{id}/edit`,
  `POST /save-edit` (or unified /save), `POST /{id}/create-discord-channel`,
  `POST /{id}/move-to-archive`.
- **`searchable-dropdown.js`** + `.searchable-dropdown` CSS — reused
  verbatim for `discordRoleId` dropdown in `team-form.html`.
- **`admin/layout.html` nav structure** — Discord Config link already
  exists under "Tools" group. Phase 94 does NOT add new nav entries
  (Team / Match pages are already linked). Match-Detail is reachable
  via Matchday-Detail "→ Detail" link only — not directly from sidebar
  (matches the existing entity-detail-page convention).
- **`.error-badge--{auth|transient|not-found}` BEM palette (Phase 91
  D-07 + Phase 93)** — Phase 94 CHAN-03 uses `.error-badge--category-full`
  variant (already specified in Phase 93 D-10; planner verifies
  whether the CSS class actually shipped in `admin.css` or needs to be
  added in Plan 94-01 alongside the `.discord-actions` cluster).

### Established Patterns

- **Sealed exception hierarchy + Mapper + hardcoded user-message
  constants (Phase 91 D-06 / Phase 93 INFRA-01)** — Phase 94
  `DiscordChannelService` raises `DiscordAuthException` (audit-fail)
  + `DiscordCategoryFullException` (archive-modal full); controllers
  typed-catch with `switch(e)` pattern + flash `errorCategory`. NEVER
  `e.getMessage()` echoed (T-91-02-IL invariant).
- **`@Tag("integration")` for WireMock-backed ITs** per
  `.planning/codebase/TESTING.md` § Test Categorization.
- **OSIV-active rendering** — `match-detail.html` can access
  lazy-loaded `match.matchday.season.year` directly per OSIV invariant
  (no `@EntityGraph` needed for this read-path; controller fetches
  match by ID, OSIV keeps session open for template render).
- **Flash-attribute success/error pattern** — Phase 94 controllers use
  `redirectAttributes.addFlashAttribute("successMessage", ...)` /
  `("errorMessage", ...)` + `("errorCategory", ...)` per Phase 91
  carry-forward.
- **Form-DTO @Valid + BindingResult** — `MatchForm` follows
  `MatchdayForm` shape; Controller `POST /save-edit` does
  `@Valid @ModelAttribute("matchForm") MatchForm form, BindingResult
  result` per CLAUDE.md § Controller patterns.
- **Spring-native preference** — `RestClient.create(...).get().uri(...)
  .header(...).retrieve().toEntity(...)` over `java.net.http.HttpClient`
  builder ([[feedback-spring-native-preference]]).
- **Hand-rolled cache pattern (Phase 93 D-03)** — Phase 94 D-05
  `DiscordRoleCache` is the SECOND user of the `CachedEntry<T>` record
  (after `DiscordEmojiCache`). Confirms the pattern; if a 3rd cache
  emerges in Phase 95+ (e.g., `DiscordChannelListCache` for archive
  modal performance), same pattern reuses.
- **No-Inline-Styles / responsive-wrap on `.discord-actions` cluster
  (Phase 94 D-06)** — admin.css gains responsive-flex rules; templates
  use `class="..."` exclusively.

### Integration Points

- **`org.ctc.discord.service.DiscordChannelService` (new) ←
  `DiscordRestClient.createChannel` + `createWebhook` + `fetchChannel`
  + `deleteChannel`** — orchestrates the 9-step transactional
  match-channel-create flow with D-04 audit + cleanup.
- **`org.ctc.discord.service.DiscordCategoryResolver` (new) ←
  `DiscordRestClient.listChannels(guildId)`** — fetches the live
  channel-list once per modal-render, filters + sorts in-process.
  Performance: Discord's `listChannels` is ONE call returning all
  guild channels (typically 50-200 entries); Phase 94 does not cache
  the result (modal renders are infrequent + count must be live).
- **`org.ctc.discord.DiscordRoleCache` (new) ← Spring `@Bean Clock
  systemClock()` (existing in `DiscordConfig`)** — single-bean,
  thread-safe via `ConcurrentHashMap`.
- **`org.ctc.admin.controller.MatchController` ← `MatchService` +
  `DiscordChannelService` + `DiscordCategoryResolver`** — controller
  delegates per CLAUDE.md § Keep Controllers Thin.
- **Flyway V9 + V10 ← schema register** — applies on H2 + MariaDB on
  startup. `BackupSchemaGuardTest` MUST stay at 24 entities (no NEW
  entities added; field-extensions on Team + Match don't change the
  topo-sort result).
- **`DiscordConfigController.refreshRolesCache()` ← also calls
  `roleCache.refresh(roles)` after `restClient.fetchGuildRoles(guildId)`**
  — single button-click populates both Discord-side caching AND the
  new `DiscordRoleCache` for Team-Form to read.
- **`@ToString.Exclude` on `Match.discordChannelWebhookUrl`** — T-93-02
  webhook-secret invariant from Phase 93 `93-THREAT-MODEL.md` applied
  to the new field.

</code_context>

<specifics>
## Specific Ideas

- **Channel-name normalization**: Service pre-formats to lowercase +
  dash-separated; Discord normalizes server-side anyway (rejects
  uppercase + spaces), but pre-formatting avoids visual flicker when
  the operator sees the channel name in Discord vs what they expected.
- **Permission-audit "exactly 3 overwrites" semantics**: 1 for
  `@everyone` (deny VIEW_CHANNEL) + 2 for team-roles. Any additional
  entry → audit fail. The audit MUST also verify the 3 ARE the right
  3 (role IDs match `match.homeTeam.discordRoleId` + `awayTeam.discordRoleId`),
  not just count.
- **Archive modal "all full" vs "none exist"**: both states render the
  same `category-full` flash + runbook link on the POST → unified
  failure mode. The pre-modal GET render shows "All categories full —
  see operator runbook section 'Creating a new archive category'"
  banner with NO radio buttons pre-selected (zero-state UX).
- **`current_match_category_id` empty vs unset**: V9 DEFAULT '' (per
  V8 pattern); UI treats `''` as "not configured" + disables
  Create-Channel button + shows "not configured" badge (mirrors
  Phase 93 D-12 bootstrap UX exactly).
- **5 Match-Form fields persistence vs Schedule-auto-edit**: Phase 94
  ONLY persists the 5 fields to DB. The auto-Discord-PATCH on
  stream-link / teaser / host changes (POST-05) is Phase 95 — the
  service-layer event/hook to trigger it lives in Phase 95.
- **Match-Form `discordTeaser` Markdown handling**: stored as raw text
  in DB; the admin Edit-form shows it in a `<textarea>`; the
  admin Match-Detail view renders it as plain `<pre>` or
  `<div class="markdown-source">` (NOT rendered as HTML — Discord
  renders Markdown server-side when the webhook posts it, so the admin
  app is just a passthrough). NO `Marked.js` or similar dependency.

</specifics>

<deferred>
## Deferred Ideas

- **Per-team-role-color sync** — Discord role colors are decorative
  (role color shows in chat for that user); not valuable in CTC admin
  or static site. OUT OF SCOPE for v1.13.
- **Match-channel auto-rotation when current-category fills** — operator
  manually rotates `discord_global_config.current_match_category_id`
  when their active category approaches 50/50. Auto-rotation would
  require detection (per-create count check) + auto-create of next
  category + Discord-side category-create permission — adds complexity
  for an operator-rare event. Tracked as `DISC-FUTURE-06` candidate
  for v1.14+ if matchday cadence reveals operator burden.
- **Live-Discord UAT-04 dashboard / one-click reset** — UAT-04 requires
  the operator to manually create test-data + test-roles + test-categories
  + then clean up after the smoke. A "reset Discord test guild to clean
  state" admin tool would simplify, but is out-of-scope (operator runs
  UAT-04 infrequently; manual cleanup is acceptable).
- **Permission-audit for server-admin role visibility** — Discord
  server-admin roles bypass channel-overwrites at the Discord runtime
  level; no `permission_overwrites` audit can detect this. T-93-03
  forward-reference in `93-THREAT-MODEL.md` documents the gap; Phase
  98 DOCS-02 runbook flags it operationally ("ensure no
  non-staff-only role has the Administrator permission server-wide").
- **Match-Detail page graphics preview thumbnails** — could render
  thumbnail-sized previews of Team Cards / Settings / Lineups graphics
  on the Match-Detail page next to the Post buttons (Phase 95). Adds
  UX value but increases page-render cost (graphics-service calls on
  every page-load). Defer to v1.14+ polish if operator feedback requests.
- **Bulk team-role-assign UI** — operator could pick `discordRoleId`
  for all teams in one screen instead of per-team-edit. Useful at
  season-start (10-20 teams) but operator is the league-runner who
  knows the league well; per-team-edit is the established pattern.
  Defer unless operator complains.
- **Confirmation modal before Channel DELETE on audit-fail** — currently
  D-04 deletes immediately on audit-fail. Could ask operator
  "Channel-permission audit failed — delete just-created channel?"
  via JS confirm. Rejected: the audit-fail IS the security signal;
  the channel MUST be deleted for T-93-03 mitigation. Operator
  confirmation introduces a race where a malicious user has window-of-time
  to see the channel. Auto-delete is correct.
- **Webhook-URL rotation on audit-fail recreate** — when operator
  retries after fixing server-role-setup, the new channel gets a new
  webhook URL. Phase 94 stores it on the same `match.discordChannelWebhookUrl`
  field (no rotation history). Webhook-URL history is YAGNI for v1.13.

</deferred>

---

*Phase: 94-team-roles-match-channel-lifecycle*
*Context gathered: 2026-05-21*
