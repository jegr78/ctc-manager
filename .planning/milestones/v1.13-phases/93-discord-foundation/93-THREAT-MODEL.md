# Phase 93 Threat Model

**Authored:** 2026-05-21
**Status:** Active
**Scope:** v1.13 Discord Integration baseline (Phases 93–98)

Single source of truth for Discord-integration threats. Future phases (94 CHAN-02
permission audit, 96 GRAFX-01, 98 DOCS-02 Troubleshooting) reference this file
rather than re-litigating threats in their own PLAN.md. Mirror of
`docs/security/sast-acceptance.md` Update-on-Triage discipline.

---

## Threat Register

| Threat | Likelihood | Impact | Mitigation | Verification |
|--------|------------|--------|------------|--------------|
| T-93-01: Bot-Token leak via stacktrace/logs | Medium | High | (a) `DISCORD_BOT_TOKEN` env-var only — never `application.yml` literal. (b) Logback `%replace` masking of webhook URLs in both console + file appenders. (c) `@ToString.Exclude` discipline on any future entity field carrying secrets (Plan 93-03 applies to `DiscordGlobalConfig.announcementWebhookUrl`). (d) SpotBugs BugInstance count = 0 gate on `org.ctc.discord.*`. (e) `DiscordApiExceptionMapper` uses 4 hardcoded user-visible message constants — never echoes `e.getMessage()` (T-91-02-IL invariant extended). | `DiscordLogMaskingIT` (Plan 93-02) + `DiscordGlobalConfigToStringTest` (Plan 93-03) + SpotBugs gate on `org.ctc.discord.*`. |
| T-93-02: Webhook-URL leak via logs | Medium | High | Logback `%replace` masking of `https?://[^/\s]+/api/webhooks/[^/\s]+/[^/\s]+` (broadened anchor catches WireMock loopback URLs per RESEARCH.md Q6) replacing with `https://***/api/webhooks/***/***`. Anchor at `/api/webhooks/` path segment keeps false-positive risk low (no other system writes that path). | `DiscordLogMaskingIT` provokes a 500-exhausted transient-exception path via WireMock and asserts the captured log output contains `***/***` AND does NOT contain the secret token fragment. |
| T-93-03: Channel-permission bypass via wrong role-mapping | Low (Phase 94 scope) | High | **CLOSED — Phase 94 Plan 94-02 shipped + strengthened by Plan 94-04.** `DiscordChannelService.createMatchChannel` performs the post-create permission audit: `fetchChannel` returns the 4 overwrites and set-equality of role-IDs-with-VIEW_CHANNEL is checked against `Set.of(homeRoleId, awayRoleId)` AND set-equality of member-IDs-with-VIEW_CHANNEL against `Set.of(botUserId)`; mismatch → cleanup `deleteChannel` + `DiscordAuthException(AUDIT_FAIL_MESSAGE)`; cleanup-fail composes a manual-cleanup user-message (no `e.getMessage()` echo per T-91-02-IL). Closure-strength (Plan 94-04): bot-self-override is a *member* overwrite (type=1) with the bot's own user-ID — it grants ALL operational perms to the bot ONLY, not to arbitrary users. The everyone-DENY remains absolute for all non-team non-bot users. | `DiscordChannelServicePermissionAuditFailIT` (extra-role + wrong-role-set + missing-bot-member paths) + `DiscordChannelServiceCleanupFailIT` (audit-fail + DELETE-fail composite, WARN log captured) — shipped with Plans 94-02 + 94-04. |
| T-93-04: Rate-limit burst → Discord-bot-ban | Medium | Medium | `DiscordRateLimitInterceptor` per-bucket token-bucket + max 3 retries on 429 with `Retry-After` sleep + jitter + exponential 5xx backoff (200/1000/5000 ms default). Exhaustion → `DiscordTransientException` routed through the sealed-permit hierarchy to caller's typed-catch. | `DiscordRateLimitInterceptorIT` (Plan 93-01, 6 methods) exercises 429-retry, 429-exhaustion, 5xx-retry, 5xx-exhaustion, bucket-update, and no-retry-on-401 paths against WireMock. |

---

## Mitigation Surfaces (per INFRA-02)

The 6 surfaces a–f the INFRA-02 requirement enumerates, mapped to concrete locations:

- (a) **`DISCORD_BOT_TOKEN` env-var only — never YAML literal.**
  - Location: `application.yml` → `app.discord.bot-token: ${DISCORD_BOT_TOKEN:}`; `application-local.yml` documents the env-var requirement (mirror of `GOOGLE_CALENDAR_ID` pattern).
  - Owner: Plan 93-02 Task 2.

- (b) **`app.discord.allowed-hosts=discord.com` SSRF positive whitelist.**
  - Location: `DiscordConfig.discordBotRestClient(...)` validates `URI.create(baseUrl).getHost()` against the allowed-hosts set; `DiscordWebhookClient.forWebhookUrl(...)` applies the same guard. Case-insensitive comparison. Throws `IllegalArgumentException("Discord host blocked: " + host)` on mismatch. Positive whitelist (only `discord.com`), inverted polarity of the v1.5 `FileStorageService.validateHostname` negative-blocklist pattern.
  - Owner: Plan 93-02 Task 5.

- (c) **Logback `%replace` mask for webhook URL pattern.**
  - Location: `logback-spring.xml` — both `<encoder>` blocks (console + file appenders). Pattern `https?://[^/\s]+/api/webhooks/[^/\s]+/[^/\s]+` → `https://***/api/webhooks/***/***`.
  - Owner: Plan 93-02 Task 3.

- (d) **`@ToString.Exclude` discipline on secret-carrying entity fields.**
  - Location: `DiscordGlobalConfig.announcementWebhookUrl` (Plan 93-03 entity). Future entity fields holding bot tokens, webhook secrets, or PSN credentials inherit this convention.
  - Owner: Plan 93-03 (entity).

- (e) **CSRF active on all `POST /admin/discord/**` endpoints.**
  - Location: Inherited from Phase 30 `SecurityConfig` / `OpenSecurityConfig` — no `SecurityConfig` changes needed. Phase 93 only adds endpoints; the existing CSRF filter chain covers them automatically.
  - Owner: Plan 93-03 (controller) inherits.

- (f) **`DiscordConfigForm` DTO mass-assignment defense.**
  - Location: `DiscordConfigController` binds `@ModelAttribute DiscordConfigForm` (Form DTO, not the JPA entity) per CLAUDE.md § Controller patterns; `@Valid` + `BindingResult` validation; entity is updated field-by-field in the service layer.
  - Owner: Plan 93-03 (form + controller).

---

## Cross-Phase Forward References

- **Threat T-93-03 → Phase 94 CHAN-02:** `DiscordChannelService.createMatchChannel` post-create permission audit. After channel-create, fetch the channel and assert only the 2 whitelisted team-roles have View permission; any other role with View → throw `DiscordAuthException`. Phase 94 CONTEXT.md must cite this file as the source of T-93-03 acceptance.
- **Phase 98 DOCS-02:** `docs/operations/discord-integration.md` Troubleshooting section pulls from the Verification column of the threat register above.

---

## Verification Map

| Test class | Threat(s) verified | Plan |
|------------|-------------------|------|
| `DiscordLogMaskingIT` | T-93-01, T-93-02 | 93-02 |
| `DiscordGlobalConfigToStringTest` | T-93-01 (entity ToString discipline) | 93-03 |
| `DiscordClientHostWhitelistTest` | T-93-01, T-93-02 (SSRF positive whitelist) | 93-02 |
| `DiscordRateLimitInterceptorIT` | T-93-04 | 93-01 (shipped) |
| `DiscordApiExceptionMapperTest` | T-93-01 (no `e.getMessage()` echo) | 93-01 (shipped) |
| SpotBugs `BugInstance` count = 0 on `org.ctc.discord.*` | T-93-01 (accidental getter exposure) | gate (every commit) |
| CodeQL gate-step exit 0 (HIGH+ findings block on PR) | All — overall SSRF / log-injection / hardcoded-credential coverage | gate (every PR) |
| Phase 94 CHAN-02 channel-permission-audit IT | T-93-03 | 94 (forward) |
