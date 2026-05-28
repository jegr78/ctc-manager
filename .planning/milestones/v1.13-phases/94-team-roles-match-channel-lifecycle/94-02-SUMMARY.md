---
phase: 94
plan: 02
slug: team-roles-match-channel-lifecycle
status: shipped
shipped: 2026-05-22
requirement: CHAN-02
---

# Plan 94-02 — CHAN-02 Match-detail page + Discord channel creation + permission audit (closes T-93-03)

Closed CHAN-02 inline on `gsd/v1.13-discord-integration` (Wave 2; depends on Plan 94-01 V9 + `Team.discordRoleId` + `current_match_category_id` + shared `DiscordSnowflake`). Delivered the operator surface that turns a match into a private Discord text channel: Flyway **V10** adds 7 nullable columns to `matches`, a new `DiscordChannelService` orchestrates a 9-step transactional flow (precondition → 3 PermissionOverwrites → `createChannel` → `createWebhook` → post-create permission audit via set-equality on role-IDs-with-VIEW_CHANNEL → cleanup `deleteChannel` on audit-fail → composed user-message on cleanup-fail + WARN log), and `MatchController` ships 4 new endpoints + a 5-field operator-editable form. Closes **T-93-03** (channel-permission bypass via wrong role-mapping) — the Phase 93 threat-model row Verification column now points to the two shipped ITs.

No new production dependencies. The new `DiscordRestClient` methods (`createWebhook`, `fetchChannel`, `deleteChannel`) reuse the existing `execute(...)` interceptor chain, so SSRF positive-whitelist + logback `%replace` mask + sealed-exception mapper from Phase 93 INFRA-02 all apply unchanged.

## Files modified

| File | Change |
|------|--------|
| `src/main/resources/db/migration/V10__add_matches_discord_and_scheduling_fields.sql` | New Flyway migration: 7 nullable `ALTER TABLE matches ADD COLUMN` statements (`discord_channel_id VARCHAR(32)`, `discord_channel_webhook_url VARCHAR(500)`, `discord_teaser VARCHAR(2000)`, `stream_link VARCHAR(500)`, `lobby_host VARCHAR(100)`, `race_director VARCHAR(100)`, `streamer VARCHAR(100)`). H2 + MariaDB compatible, no CHECK / LONGTEXT / `@Lob`. Header comment block matches V8/V9 convention. |
| `src/main/java/org/ctc/domain/model/Match.java` | `@ToString(exclude = {...})` extended with `discordChannelWebhookUrl` (carries forward T-93-02 webhook-secret invariant). 7 new fields appended after `races` with matching `@Column(length=...)` annotations. |
| `src/main/java/org/ctc/admin/dto/MatchForm.java` | New `@Getter @Setter @NoArgsConstructor` DTO. 6 fields: `id` + 5 `@Size`-bounded strings (`discordTeaser` 2000, `streamLink` 500, `lobbyHost`/`raceDirector`/`streamer` 100). No `@Pattern` (URLs, `<#channelId>`, and blank all valid). |
| `src/main/java/org/ctc/discord/DiscordPermissions.java` | New pure-static utility (sibling of `DiscordRoleCache` / `DiscordTimestamps`). 16 individual bit constants + 3 composite masks (`EVERYONE_DENY_VIEW`, `TEAM_MEMBER_ALLOW`, `TEAM_MEMBER_DENY`) + 2 overwrite-type ints. Private ctor, no Lombok. |
| `src/main/java/org/ctc/discord/dto/PermissionOverwrite.java` | New `record (String id, int type, String allow, String deny)` with `@JsonIgnoreProperties(ignoreUnknown = true)`. `allow`/`deny` are `String` per Discord docs (call-site uses `String.valueOf(MASK)`). |
| `src/main/java/org/ctc/discord/dto/Webhook.java` | New `record (id, token, url, @JsonProperty("channel_id") channelId)` with ignore-unknown. |
| `src/main/java/org/ctc/discord/dto/ChannelCreateRequest.java` | Extended to 4-component with class-level `@JsonInclude(NON_NULL)` (RESEARCH Pitfall 1 — prevents `"permission_overwrites": null` on the wire). 3-arg convenience ctor preserves Phase 93 callers. |
| `src/main/java/org/ctc/discord/dto/Channel.java` | Extended to 5-component symmetrically with 4-arg convenience ctor. |
| `src/main/java/org/ctc/discord/DiscordRestClient.java` | 3 new typed methods (`createWebhook` POST → `Webhook`, `fetchChannel` GET → `Channel`, `deleteChannel` DELETE with `toBodilessEntity()`) via existing `execute(...)` helper. Co-located private `WebhookCreateRequest` record. |
| `src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java` | New `AUDIT_FAIL_MESSAGE` constant. No `e.getMessage()` echo (T-91-02-IL invariant). |
| `src/main/java/org/ctc/discord/service/DiscordChannelService.java` | New `@Slf4j @Service @RequiredArgsConstructor` with single public `@Transactional createMatchChannel(Match)` implementing the locked 9-step flow per CONTEXT D-04. Private `assertPermissionAudit(...)` uses set-equality on role-IDs-with-VIEW_CHANNEL bit (RESEARCH § Permission Audit Semantics + Pitfall 4). |
| `src/main/java/org/ctc/discord/dto/ArchiveCategory.java` | New stub record `(id, name, num, currentChannelCount)` — Plan 94-03 enriches the consumer (record shape itself is final). |
| `src/main/java/org/ctc/domain/service/MatchService.java` | 3 new methods (`findById`, `getDetailData`, `updateDiscordFields`) + co-located public `MatchDetailData` record. `archiveCategories` + `defaultSelectionId` are stubs that Plan 94-03 populates. |
| `src/main/java/org/ctc/admin/controller/MatchController.java` | 4 new endpoints: `GET /{id}` (detail), `GET /{id}/edit`, `POST /{id}/save-edit` (`@Valid` + `BindingResult` re-renders on errors), `POST /{id}/create-discord-channel` (typed-catch: `BusinessRuleException` → `not-found` flash; `DiscordApiException` → `applyErrorFlash`). New `DiscordChannelService` dependency. |
| `src/main/resources/templates/admin/match-detail.html` | New template — toolbar (back-link + h1 + Edit), Discord Actions card (Create-Channel form gated on team-roles + currentMatchCategoryId, channel-id `.badge-active` + Move-to-Archive button when channel exists), Schedule card (5 fields + teaser as raw `<pre class="markdown-source">` per CONTEXT § Specifics 6), Races list, archive-modal outer container (Plan 94-03 fills the body). Every interactive element has a stable `data-testid`. |
| `src/main/resources/templates/admin/match-form-edit.html` | New template — form-action `/save-edit`, `th:object="${matchForm}"`, hidden id, 5 form-groups, per-field `.error-badge--auth`, Save + Cancel actions. |
| `src/main/resources/templates/admin/matchday-detail.html` | Minimal extension: `→ Detail` link added inside each match-row badge cluster (`.ml-md`). No other modifications. |
| `.planning/phases/93-discord-foundation/93-THREAT-MODEL.md` | T-93-03 row's Verification column updated from forward-reference to concrete IT references (`DiscordChannelServicePermissionAuditFailIT` + `DiscordChannelServiceCleanupFailIT`). |

## Tests added (10)

| Test class | `@Tag` | Methods | Coverage |
|------------|--------|---------|----------|
| `MatchRepositoryDiscordFieldsIT` | `integration` | 3 | Round-trip with 7 fields populated, null defaults, clear-to-null. Proves V10 + Match entity persist correctly on H2. |
| `MatchFormValidationTest` | untagged | 6 | Blank form passes; each of 5 max-length boundaries fires exactly one violation on the right path. |
| `MatchToStringTest` | untagged | 1 | `Match.toString()` does NOT echo the webhook secret fragment (T-93-02 invariant). |
| `DiscordPermissionsTest` | untagged | 4 | `EVERYONE_DENY_VIEW_MASK == VIEW_CHANNEL`, `TEAM_MEMBER_ALLOW_MASK` composite, `TEAM_MEMBER_DENY_MASK` composite, `OVERWRITE_TYPE_ROLE == 0`. |
| `DiscordChannelServiceWireMockIT` | `integration` | 3 | Happy-path (3 outbound calls, DB write), missing-role precondition (no outbound), webhook-fail mid-flow (DB unchanged). |
| `DiscordChannelServicePermissionAuditFailIT` | `integration` | 2 | Extra-role overwrite + missing-role overwrite — both trigger cleanup DELETE, throw `DiscordAuthException(AUDIT_FAIL_MESSAGE)`, DB unchanged. |
| `DiscordChannelServiceCleanupFailIT` | `integration` | 1 | Audit fails AND DELETE returns 500 — composed user-message contains "Cleanup failed: please manually delete channel c1 via Discord.", WARN log captured, webhook secret never echoed. |
| `MatchControllerCreateChannelErrorCategoryTest` | untagged | 4 (param) | TRANSIENT/AUTH/NOT_FOUND/CATEGORY_FULL → kebab-case BEM suffix. |
| `MatchEditFormIT` | `integration` | 3 | Valid form round-trip (success flash + DB persist), oversized teaser re-renders form with error badge, blank submit clears every field. |
| `MatchDetailControllerE2ETest` | `e2e` | 4 | Detail render with team-roles missing → button disabled; both roles + category → button enabled; existing channelId → badge + Move-to-Archive visible; mobile 375×667 → no horizontal overflow. |

IT extension: `DiscordRestClientIT` (+4 WireMock cases for `createWebhook`, `fetchChannel`, `deleteChannel` happy + 5xx).

## Quality gates (local `./mvnw clean verify -Pe2e` on commit `07287282`)

- BUILD SUCCESS in **9:45 min**.
- JaCoCo line coverage **89.34 %** (8269 covered / 9256 total) — above v1.11 baseline 88.88 %.
- SpotBugs **0 BugInstances**.
- `BackupSchemaGuardTest` green at `EXPORT_ORDER.length == 24` + `SCHEMA_VERSION == 1`.
- Flyway V1-V9 unchanged; V10 sole new migration; applied cleanly on H2 (via Spring-context boot in `MatchRepositoryDiscordFieldsIT`).
- Branch identity `gsd/v1.13-discord-integration` preserved end-to-end. No subagents, no worktrees ([[feedback-inline-sequential-execution]]).

## Threats closed / carried forward

| Threat ID | Disposition | Proof |
|-----------|-------------|-------|
| **T-93-03** | **CLOSED** (Phase 93 threat-model row updated) | `DiscordChannelServicePermissionAuditFailIT` (2 paths) + `DiscordChannelServiceCleanupFailIT` (composite path with WARN log assertion). |
| T-93-02 carry-forward | mitigate | `MatchToStringTest` proves `discordChannelWebhookUrl` is excluded from the Lombok `@ToString` output. |
| T-91-02-IL carry-forward | mitigate | `MatchControllerCreateChannelErrorCategoryTest` + `applyErrorFlash` helper — only hardcoded `_MESSAGE` constants reach the UI. |
| T-94-02-01 (Tampering on `discordTeaser`) | mitigate | `MatchFormValidationTest` enforces `@Size(max = 2000)`. |

## Decisions made during execution

- **D-Matchday-Sort-Index-Plus-One (Claude's discretion).** Plan referred to `match.matchday.number` in the channel-name formula; the entity has `sortIndex` (0-indexed) instead. Used `sortIndex + 1` to produce the 1-indexed channel name "md1-home-vs-away" matching the plan's example. Single-line construction in `DiscordChannelService.channelName(...)`.
- **D-Test-Class-Transactional (Claude's discretion).** All 3 channel-service ITs use `@Transactional` on the test class — the service joins the test transaction so happy-path DB writes are visible via `findById`, and audit-fail "no DB commit" claims are proven structurally (no `setDiscordChannelId(...)` call ever runs). Avoids brittle commit-then-cleanup test plumbing.
- **D-Race-Track-Ternary-In-SpEL (operator-feedback fix).** Initial `match-detail.html` Race-list expression placed the `race.track != null ? race.track.name : '—'` ternary outside Thymeleaf's `${...}` SpEL context, so the literal text rendered. Fixed to `${race.track?.name ?: '—'}` in a follow-up commit (`55a127ac`).

## Wave-pause artifacts

Screenshots under `.screenshots/94-02/` (gitignored locally):
- `matchday-detail-with-link-desktop.png` — `→ Detail` link visible in each match row.
- `match-detail-desktop.png` — toolbar + Discord Actions card (disabled button when team-roles missing) + Schedule + Races list.
- `match-detail-mobile.png` — 375×667 viewport, `.discord-actions` wraps to single column, `document.body.scrollWidth == clientWidth`.
- `match-form-edit-desktop.png` — 5-field form layout.
- `match-form-edit-mobile.png` — 375×667 viewport, form stays inside viewport.

PR #130 body appended with Plan 94-02 row (REQ CHAN-02, commit SHA `07287282`, T-93-03 closure note). Operator approval required before Plan 94-03 (CHAN-03 archive flow).
