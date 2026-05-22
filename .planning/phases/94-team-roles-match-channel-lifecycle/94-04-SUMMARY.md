---
phase: 94
plan: 04
slug: team-roles-match-channel-lifecycle
status: shipped
shipped: 2026-05-22
requirement: CHAN-02-FOLLOWUP
gap_closure: true
gap_origin: UAT-04 attempt 2026-05-22 surfaced production-incompatible permission model
---

# Plan 94-04 — CHAN-04 Bot-Self-Override (UAT-04 gap-closure)

Closed the production-incompatible permission gap surfaced by UAT-04 (2026-05-22) inline on `gsd/v1.13-discord-integration` (Wave 4; gap-closure plan added after Plans 94-01/02/03 shipped, before `/gsd-validate-phase 94`). Plan 94-04 added a 4th permission_overwrite of type=member (bot's own user-ID) to every channel created by `DiscordChannelService.createMatchChannel`, so the bot can audit/archive/delete only its own channels with minimum-permission (MANAGE_CHANNELS + MANAGE_WEBHOOKS + VIEW_CHANNEL server-wide; no Administrator) instead of requiring server-wide ADMINISTRATOR.

UAT-04 origin: the 3-overwrite model from Plan 94-02 only worked when the bot had `ADMINISTRATOR` (which bypasses channel-overrides at the Discord runtime level). Without Administrator, the bot could `POST /guilds/{id}/channels` (server-level `MANAGE_CHANNELS` suffices) but could not `GET /channels/{id}` (audit-fetch), `DELETE /channels/{id}` (cleanup), or `PATCH /channels/{id}` (archive-move) because the `@everyone-DENY` channel-overwrite blocked the bot's own `VIEW_CHANNEL`. Live log evidence: `data/dev/logs/app.log:14025` (orphan channel `1507281506408595456` cleanup-fail) + `:11406-11410` (5× `listChannels` AUTH-fail).

## Files modified

| File | Change |
|------|--------|
| `src/main/java/org/ctc/discord/DiscordPermissions.java` | New `BOT_ALLOW_MASK` constant = `VIEW_CHANNEL \| MANAGE_CHANNELS \| MANAGE_WEBHOOKS \| SEND_MESSAGES \| EMBED_LINKS \| ATTACH_FILES \| READ_MESSAGE_HISTORY`. No MENTION_EVERYONE, no MANAGE_MESSAGES. |
| `src/main/java/org/ctc/discord/DiscordBotIdentityCache.java` | New `@Slf4j @Component @RequiredArgsConstructor` (alphabetical annotation order per CLAUDE.md). `AtomicReference<String> cachedBotUserId`; `getBotUserId()` lazy-fetches via existing `DiscordRestClient.fetchBotUser()` (Phase 93 method); `refresh()` force-re-fetches. Bot user-ID immutable per token — no TTL. |
| `src/main/java/org/ctc/discord/service/DiscordChannelService.java` | Injected `DiscordBotIdentityCache botIdentityCache` via existing `@RequiredArgsConstructor`. `createMatchChannel` now fetches `botUserId = botIdentityCache.getBotUserId()` and adds a 4th overwrite `new PermissionOverwrite(botUserId, OVERWRITE_TYPE_MEMBER, String.valueOf(BOT_ALLOW_MASK), "0")` to the payload. `assertPermissionAudit` signature extended with `String botUserId`; body enforces `overwrites.size() == 4` + the original role-set equality `{homeRoleId, awayRoleId}` + a NEW member-set equality `membersWithView.equals(Set.of(botUserId))`. |
| `src/main/java/org/ctc/discord/web/DiscordConfigController.java` | Injected `DiscordBotIdentityCache botIdentityCache` via existing `@RequiredArgsConstructor`. `refreshRolesCache` now also calls `botIdentityCache.refresh()` after `roleCache.refresh(roles)` so the operator can rotate bot identity via the same existing button (no app restart needed). Existing typed-catch covers both `fetchGuildRoles` AND `fetchBotUser` exceptions through `applyErrorFlash`. |
| `docs/operations/discord-integration.md` | New `## Minimum Bot Permissions` section. Lists the 3 server-wide perms (View Channels + Manage Channels + Manage Webhooks) and explicitly states "No Administrator permission required" + cross-links the rotate-via-Refresh-Server-Roles flow. |
| `.planning/STATE.md` | UAT-04 block updated: pre-cleanup paragraph (orphan channel `1507281506408595456`); Step 4 procedure note (4 permission-overwrites including bot-member); Step 5 audit-fail note (5th overwrite OR missing bot member-overwrite both trigger); new `**Bot permissions**` sub-heading linking to `docs/operations/discord-integration.md`. Heading renamed from "CHAN-01/02/03" to "CHAN-01/02/03/04". |
| `.planning/phases/93-discord-foundation/93-THREAT-MODEL.md` | T-93-03 row Mitigation cell strengthened: closure-strength annotation referencing the member-overwrite (type=1) mechanism; size-4 audit; bot-member-set-equality check. |

## Tests added / extended (5)

| Test class | `@Tag` | Methods | Coverage |
|------------|--------|---------|----------|
| `DiscordBotIdentityCacheTest` | untagged | 3 (NEW, Mockito) | Cold-cache lazy-fetch; cache-hit no-refetch; force-refresh re-fetch + cache update. |
| `DiscordPermissionsTest` | untagged | +1 method (extension) | `BOT_ALLOW_MASK` composition: 7 included bits + 2 deliberately-excluded bits (MENTION_EVERYONE + MANAGE_MESSAGES). |
| `DiscordChannelServiceWireMockIT` | `integration` | 3 happy-path methods updated | `/users/@me` stub added in `@BeforeEach`; fetchChannel response now returns 4 overwrites (4th = type=1, bot-user-ID, BOT_ALLOW_MASK); POST payload assertion verifies 4th overwrite SHAPE via 3 stacked `matchingJsonPath` on `$.permission_overwrites[3].{type,id,allow}` (per plan-checker WARNING B — shape not count). |
| `DiscordChannelServicePermissionAuditFailIT` | `integration` | 3 (2 updated + 1 NEW) | 5-overwrite size-mismatch path; 4-overwrite wrong-role-set path; NEW 4-overwrite-without-bot-member path with noise entry (`allow=0`) so role-set passes and the new member-set check is the actual trip point (per plan-checker WARNING A). |
| `DiscordChannelServiceCleanupFailIT` | `integration` | 1 method updated | Trigger payload extended to 5 overwrites (size mismatch) to exercise the composite audit-fail + DELETE-fail branch under the new size-4 audit. |
| `DiscordConfigControllerTest` | untagged | 2 methods (1 happy-path extended + 1 NEW) | `refreshRolesCache` happy-path asserts `verify(botIdentityCache).refresh()`; NEW test for `botIdentityCache.refresh()` throwing `DiscordAuthException` → flash `errorCategory=auth` through existing `applyErrorFlash`. |
| `DiscordConfigControllerIT` | `integration` | 1 method extended | Added `/users/@me` WireMock stub so the live IT can exercise the full happy-path (was failing because `botIdentityCache.refresh()` returned 404 without the stub). |

Plan 94-02 + 94-03 test classes that mock `DiscordChannelService` or don't exercise `createMatchChannel` (e.g. `MatchControllerCreateChannelErrorCategoryTest`, `MatchEditFormIT`, `MatchDetailControllerE2ETest`, `ArchiveModalE2ETest`) continue to pass unchanged.

## Quality gates (local `./mvnw clean verify -Pe2e` on commit `7011bfd8`)

- BUILD SUCCESS in **8:22 min**.
- JaCoCo line coverage **89.57 %** (8347 covered / 9319 total) — above v1.11 baseline 88.88 %.
- SpotBugs **0 BugInstances**.
- `BackupSchemaGuardTest` green at `EXPORT_ORDER.length == 24` + `SCHEMA_VERSION == 1` — Plan 94-04 added no new entity.
- Flyway V1-V10 unchanged; Plan 94-04 adds NO new migration.
- Branch identity `gsd/v1.13-discord-integration` preserved end-to-end. No subagents, no worktrees ([[feedback-inline-sequential-execution]]).

## Threats addressed / carried forward

| Threat ID | Disposition | Proof |
|-----------|-------------|-------|
| T-93-03 closure-strength | mitigate (strengthened) | The 4th overwrite is type=1 (member), not type=0 (role) — it grants operational perms to the SPECIFIC bot user-ID ONLY. The `@everyone-DENY` remains absolute for all non-bot non-team users. `DiscordChannelServicePermissionAuditFailIT` 3 cases prove the size-4 + role-set + member-set equality checks. |
| T-93-04 carry-forward | mitigate | `getBotUserId()` is called once per `createMatchChannel` AND cache-hits after first invocation. Worst case: 1 extra `/users/@me` GET on app boot per operator session. `DiscordRateLimitInterceptor` (Phase 93) handles bursts. |
| T-94-04-01 (Tampering via crafted 4-overwrite) | mitigate | Audit checks `membersWithView.equals(Set.of(botUserId))` — exactly 1 member overwrite with view, exactly the cached bot-user-ID. A malicious server-admin crafting a 4-overwrite payload with a different bot-user-ID OR a non-bot user-ID OR no member-overwrite fails the set-equality. |
| T-94-04-02 (Spoofing/stale bot-identity-cache) | mitigate | If operator rotates bot token without `refresh()`, cached user-ID is stale → next `createMatchChannel` builds 4th overwrite with OLD bot-user-ID → Discord returns the NEW bot's user-ID in fetchChannel overwrites → audit fails on member-set mismatch → channel deleted via cleanup → operator sees `AUDIT_FAIL_MESSAGE`. Safe failure mode. |
| T-91-02-IL carry-forward | mitigate | `refreshRolesCache` typed-catch routes both `fetchGuildRoles` AND `fetchBotUser` exceptions through `applyErrorFlash` → hardcoded `_MESSAGE` constants only. `DiscordBotIdentityCache.refresh()` does NOT format user-facing messages itself — DEBUG-log only. |

## Decisions made during execution

- **D-Annotation-Order-Alphabetical (CLAUDE.md compliance per plan-checker WARNING C).** `DiscordBotIdentityCache` shipped with `@Slf4j @Component @RequiredArgsConstructor` (alphabetical, `@Slf4j` first) per CLAUDE.md § Lombok Usage, matching the existing `DiscordChannelService.java:30-32` precedent.
- **D-Audit-Payload-Shape-JsonPath-Stacking (per plan-checker WARNING B).** `DiscordChannelServiceWireMockIT` happy-path assertion uses 3 stacked `matchingJsonPath` clauses on `$.permission_overwrites[3].{type,id,allow}` instead of a weaker `length()`-count assertion. Future regressions where the 4th overwrite ships with wrong type or wrong allow-mask would now trip the assertion.
- **D-Noise-Entry-AllowZero (per plan-checker WARNING A).** The new `givenFetchChannelReturnsFourRoleOverwritesNoBotMember_whenAudit_...` test stubs 4 type=0 overwrites with the 4th having `allow=0` + `deny=0` (noise entry). This makes the role-set-equality check PASS (`{100, 200}` matches because the noise entry contributes no VIEW bit) so the audit short-circuits to the NEW member-set-equality check, which then fails on empty `membersWithView != {botUserId}`. Without the `allow=0` noise discipline, the role-set check would have tripped first and the new test would have inadvertently re-covered the existing extra-role case rather than the new member-set check.
- **D-DiscordConfigControllerIT-Stub-Addition (post-verify regression fix).** First full-verify run revealed `DiscordConfigControllerIT.givenCsrfAndConfiguredGuildAndRoleList_whenPostRefreshRolesCache_thenFlashesSuccessAndPopulatesCache` failing with `flash:successMessage was null` because `botIdentityCache.refresh()` invoked `/users/@me` which had no WireMock stub → returned 404 → typed-catch flashed errorCategory=not-found. Fix: add the `/users/@me` happy-path stub alongside the existing `/guilds/{id}/roles` stub. Re-run green.

## Wave-pause artifacts

Screenshots under `.screenshots/94-04/` (gitignored locally):
- `discord-config-refresh-desktop.png` — `/admin/discord-config` Desktop 1280×800 (default state: form fields with "not configured" badges; no visual change vs Plan 94-01 baseline — refresh-roles-cache side-effect is server-side only).
- `discord-config-refresh-mobile.png` — same page Mobile 375×667.

The functional verification of the bot-identity-cache refresh side-effect runs through `DiscordConfigControllerTest` + `DiscordConfigControllerIT` programmatically; the live `Refresh Server Roles` flash (no visual change vs Plan 94-01) is operator-verified during UAT-04 Step 1.

PR #130 body to be appended with Plan 94-04 row (REQ CHAN-02-FOLLOWUP, commit `7011bfd8`, Phase 94 close re-triggered). Awaiting:
1. Operator runs UAT-04 (orphan `1507281506408595456` cleanup → 7-step procedure) against test guild WITHOUT `ADMINISTRATOR` on the bot — all 7 steps green.
2. `/gsd-validate-phase 94` — Nyquist sampling across all 24-25 test classes; flip `nyquist_compliant: true` in 94-VALIDATION.md frontmatter.
