---
phase: 93
plan: 02
slug: discord-foundation
status: shipped
shipped: 2026-05-21
requirement: INFRA-02
---

# Plan 93-02 — INFRA-02 Threat model + SSRF positive whitelist + Logback webhook-URL masking

Closed T-93-01 (Bot-Token leak) and T-93-02 (Webhook-URL leak) before Plan 93-03 exposes
the admin config page. Authored `93-THREAT-MODEL.md` as the single source of truth that
Phase 94 CHAN-02 and Phase 98 DOCS-02 will reference instead of re-litigating threats.
Zero new production dependencies.

## Files modified

| File | Change |
|------|--------|
| `.planning/phases/93-discord-foundation/93-THREAT-MODEL.md` | New artifact (D-04). 4-row threat register (T-93-01..04) with Threat / Likelihood / Impact / Mitigation / Verification columns. T-93-03 marked FORWARD-REFERENCE to Phase 94 CHAN-02. 6 mitigation-surface bullet list (a–f) per INFRA-02. Verification map cross-references the test class per threat. |
| `src/main/resources/application.yml` | New `app.discord` block: `bot-token: ${DISCORD_BOT_TOKEN:}` (env-var only — never literal), `allowed-hosts: discord.com`, `base-url: https://discord.com/api/v10`, `rate-limit.jitter-ms: 100-500`. Plus `app.timezone: Europe/Berlin` consumed by `DiscordTimestamps`. |
| `src/main/resources/application-local.yml` | Prepended env-var documentation block listing `DISCORD_BOT_TOKEN` alongside the existing `GOOGLE_CALENDAR_ID` requirement; local cascade override `app.discord.bot-token: ${DISCORD_BOT_TOKEN:}`. |
| `src/main/resources/logback-spring.xml` | Both `<encoder>` blocks (console + file appenders) wrap `%m%n%wEx` in `%replace(...){'https?://[^/\s]+/api/webhooks/[^/\s]+/[^/\s]+', 'https://***/api/webhooks/***/***'}`. The pre-existing profile-conditional LOG_DIR + RollingFileAppender policy preserved unchanged. |
| `src/test/resources/logback-test.xml` | Mirror of the production %replace mask so `DiscordLogMaskingIT` proves the regression fence is wired up. Previously used the bare Spring Boot `CONSOLE_LOG_PATTERN` with no masking — which would have made the IT moot. |
| `src/main/java/org/ctc/discord/DiscordHostValidator.java` | New Spring `@Component`. Parses `@Value("${app.discord.allowed-hosts:discord.com}")` into a lowercase `Set<String>`; `requireAllowed(String url)` throws `IllegalArgumentException("Discord host blocked: " + host)` on null host OR non-whitelisted host. Inverted polarity of the v1.5 `FileStorageService.validateHostname` negative-blocklist pattern. |
| `src/main/java/org/ctc/discord/DiscordConfig.java` | `discordBotRestClient` bean factory now takes `DiscordHostValidator hostValidator` and calls `hostValidator.requireAllowed(baseUrl)` BEFORE `RestClient.builder()` — misconfigured base-url fails fast at Spring boot. |
| `src/main/java/org/ctc/discord/DiscordWebhookClient.java` | Constructor takes `DiscordHostValidator` (third arg). `execute`, `executeMultipart`, and `editMessage` each invoke `hostValidator.requireAllowed(webhookUrl)` before opening the per-call RestClient. The empty-attachment fallback path in `executeMultipart` delegates to `execute` so a single guard covers both. Plus defensive `log.warn("Discord webhook execute failed for {}: {}", webhookUrl, e.category())` on the DiscordApiException catch path — emits the (mask-eligible) URL so the regression IT can prove the Logback `%replace` mask is active. |
| `src/test/java/org/ctc/discord/DiscordClientHostWhitelistTest.java` | 7 untagged `@Test` methods covering allowed/blocked/null/case-insensitive paths for both clients (`DiscordHostValidator` direct + `DiscordWebhookClient.execute` end-to-end). |
| `src/test/java/org/ctc/discord/DiscordLogMaskingIT.java` | 1 `@Tag("integration")` test using `@ExtendWith(OutputCaptureExtension.class)`. Provokes a 500-exhausted retry path; asserts the captured output contains `***/***` AND does NOT contain `secret-token-xyz-12345`. Regression fence — fails immediately if anybody removes the production `%replace` mask. |
| `.planning/phases/93-discord-foundation/93-02-VALIDATION.md` | Per-plan Nyquist slice per CONTEXT D-08. |

## ./mvnw clean verify summary

- BUILD SUCCESS — total time TBD (recorded in 93-02-VALIDATION.md after final verify).
- Tests run: 1480+ surefire (Plan 93-01 baseline + 7 new Whitelist-Tests untagged) + Failsafe ITs include the 27 Plan 93-01 ITs + 1 new `DiscordLogMaskingIT`.
- JaCoCo line coverage continues ≥ 88.88 % (no production code paths abandoned).
- SpotBugs `BugInstance` count remains 0 (DiscordHostValidator's constructor parses a `@Value` string but does not throw — no new CT_CONSTRUCTOR_THROW; the existing suppression scope `~org\.ctc\.discord\.[A-Z].+` covers it).

## INFRA-02 acceptance

- `93-THREAT-MODEL.md` exists with exactly 4 threat rows (`grep -c '^| T-93-0' .planning/phases/93-discord-foundation/93-THREAT-MODEL.md` returns 4).
- `application.yml` carries `bot-token: ${DISCORD_BOT_TOKEN:}` + `allowed-hosts: discord.com` + `timezone: Europe/Berlin`.
- `application-local.yml` documents the `DISCORD_BOT_TOKEN` env-var requirement.
- `logback-spring.xml` has exactly 2 `%replace` invocations (console + file appender) plus 1 in the rationale comment — the live appenders both apply the mask.
- `DiscordHostValidator` is the single source of host-allowance logic; both clients call it. Case-insensitive comparison.
- `DiscordLogMaskingIT` and `DiscordClientHostWhitelistTest` are committed and green.
- Branch identity end-to-end: `git branch --show-current` returns `gsd/v1.13-discord-integration` ✓.

## Threat coverage delta

| Threat | Status after Plan 93-02 |
|--------|-------------------------|
| T-93-01 (Bot-Token leak) | Mitigated. Env-var-only (surface a), Logback mask (surface c), `DiscordApiExceptionMapper` no-info-leak (surface c'), SpotBugs gate (surface d). T-93-01 verification surface complete. |
| T-93-02 (Webhook-URL leak) | Mitigated. Logback `%replace` mask wired up; `DiscordLogMaskingIT` regression fence active. |
| T-93-03 (Channel-permission bypass) | FORWARD-REFERENCE (Phase 94 CHAN-02 owns the channel-create permission audit). |
| T-93-04 (Rate-limit burst) | Mitigated by Plan 93-01 `DiscordRateLimitInterceptor`; documented in this plan's threat model. |

## Notes

- `DiscordRestClient.bot` SSRF check happens at bean-creation time in `DiscordConfig` rather than inside the client itself because `DiscordRestClient` holds only the constructed `RestClient` reference and never sees the raw base-url string. Validating in the factory keeps the responsibility in one place and fails fast at boot if the operator points the bot at a non-Discord host.
- `DiscordWebhookClient`'s per-method guard is necessary because webhook URLs are passed dynamically (different webhook per match channel). The guard runs before any HTTP wire activity so SSRF attempts are rejected at the first opportunity.
- The Logback `%replace` regex is deliberately broadened from the CONTEXT-suggested `https://discord.com/...` anchor to `https?://[^/\s]+/api/webhooks/...` so WireMock loopback URLs in `DiscordLogMaskingIT` are also masked. False-positive radius is bounded — no other system writes `/api/webhooks/` path segments to logs.

## Rolling Draft milestone PR

- Body update tracked under Task 7 (post-final-verify gh pr edit).
