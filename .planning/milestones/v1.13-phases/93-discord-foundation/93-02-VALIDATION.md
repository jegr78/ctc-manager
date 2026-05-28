---
phase: 93
plan: 02
slug: discord-foundation
status: shipped
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-21
---

# Plan 93-02 — Validation Slice

> Per-plan slice of `93-VALIDATION.md` per CONTEXT D-08.
> Substance: 7 rows 93-02-01..07 covering INFRA-02 threat model + security surfaces.

---

## Sampling Rate

- **Per-task gate (Tasks 1–3, doc/config):** file-existence + grep checks (e.g., `grep -c 'app.discord.bot-token' src/main/resources/application.yml`).
- **Per-task gate (Task 4 RED, Task 5 GREEN):** `./mvnw test -Dtest=DiscordClientHostWhitelistTest` (~10 s, 7 methods).
- **Per-task gate (Task 6 IT):** `./mvnw failsafe:integration-test failsafe:verify -Dit.test=DiscordLogMaskingIT` (~35 s incl. Spring boot).
- **Per-plan full gate (Task 7):** `./mvnw clean verify` (~5–8 min).

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 93-02-01 | 02 | 1 | INFRA-02 | T-93-01..04 | `93-THREAT-MODEL.md` artifact authored with 4 threat rows + 6 mitigation surfaces a–f. T-93-03 explicitly FORWARD-REFERENCE to Phase 94 CHAN-02. | doc grep | `grep -c '^\| T-93-0' .planning/phases/93-discord-foundation/93-THREAT-MODEL.md` returns 4 | ✅ | ✅ green |
| 93-02-02 | 02 | 1 | INFRA-02 | T-93-01 | `application.yml` carries `bot-token: ${DISCORD_BOT_TOKEN:}`, `allowed-hosts: discord.com`, `base-url: https://discord.com/api/v10`, `timezone: Europe/Berlin`. `application-local.yml` documents the env-var requirement. | config grep + smoke | `grep -c 'DISCORD_BOT_TOKEN' src/main/resources/application-local.yml` returns ≥ 1; `./mvnw -q -Dtest=DiscordEmojiCacheTest test` boots Spring without YAML parse errors | ✅ | ✅ green |
| 93-02-03 | 02 | 1 | INFRA-02 | T-93-01, T-93-02 | Both `<encoder>` blocks in `logback-spring.xml` wrap `%m%n%wEx` in `%replace(...){'https?://[^/\s]+/api/webhooks/[^/\s]+/[^/\s]+', 'https://***/api/webhooks/***/***'}`. | XML grep | `grep -c '%replace' src/main/resources/logback-spring.xml` returns 3 (2 live appenders + 1 rationale comment); `grep -c 'api/webhooks' src/main/resources/logback-spring.xml` returns ≥ 5 (incl. comment + pattern references). | ✅ | ✅ green |
| 93-02-04 | 02 | 1 | INFRA-02 | T-93-01, T-93-02 | RED `DiscordClientHostWhitelistTest` references `DiscordHostValidator` (doesn't exist yet) — compilation fails. | RED test | `./mvnw test -Dtest=DiscordClientHostWhitelistTest` fails compile before Task 5 | ✅ | ✅ green |
| 93-02-05 | 02 | 1 | INFRA-02 | T-93-01, T-93-02 | `DiscordHostValidator @Component` extracted; `DiscordConfig.discordBotRestClient` calls `hostValidator.requireAllowed(baseUrl)` before bean construction; `DiscordWebhookClient.execute / executeMultipart / editMessage` each invoke the guard. Case-insensitive host match. Null host throws `"<null>"`. | unit (7 methods) | `./mvnw test -Dtest=DiscordClientHostWhitelistTest` → 7/7 pass; `./mvnw failsafe:integration-test -Dit.test='DiscordRateLimitInterceptorIT,DiscordRestClientIT,DiscordWebhookClientIT,DiscordWebhookClientMultipartIT'` → 27/27 pass (no regression). | ✅ | ✅ green |
| 93-02-06 | 02 | 1 | INFRA-02 | T-93-02 | `DiscordLogMaskingIT` provokes 500-exhausted retry; captured stdout via OutputCaptureExtension MUST contain `***/***` AND MUST NOT contain `secret-token-xyz-12345`. `src/test/resources/logback-test.xml` synced with the production mask so the regression fence is meaningful in test contexts. | integration (1 method) | `./mvnw failsafe:integration-test failsafe:verify -Dit.test=DiscordLogMaskingIT` → 1/1 pass | ✅ | ✅ green |
| 93-02-07 | 02 | 1 | INFRA-02 | all | Full pipeline green: Surefire + Failsafe (incl. new IT) + JaCoCo + SpotBugs. JaCoCo line coverage ≥ 88.88 %; SpotBugs BugInstance count 0. Branch invariant preserved. | full pipeline | `./mvnw clean verify` exits 0; `git branch --show-current` returns `gsd/v1.13-discord-integration` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `.planning/phases/93-discord-foundation/93-THREAT-MODEL.md` (Task 1)
- [x] `src/main/resources/application.yml` + `application-local.yml` updated (Task 2)
- [x] `src/main/resources/logback-spring.xml` masked patterns (Task 3)
- [x] `src/test/java/org/ctc/discord/DiscordClientHostWhitelistTest.java` (Task 4 RED → Task 5 GREEN)
- [x] `src/main/java/org/ctc/discord/DiscordHostValidator.java` + Discord{Config,WebhookClient} wired (Task 5)
- [x] `src/test/java/org/ctc/discord/DiscordLogMaskingIT.java` + `src/test/resources/logback-test.xml` synced (Task 6)
- [x] Task 7 closes with `./mvnw clean verify` GREEN.

---

## Sign-Off

| Field | Value |
|-------|-------|
| Closed by | Plan 93-02 execution (7 tasks, TDD-paired) |
| Closed at | 2026-05-21 |
| Closing commit | TBD (final commit SHA recorded in `93-VALIDATION.md` per CONTEXT D-08) |
| Coverage at close | ≥ 88.88 % line coverage (carries forward from Plan 93-01 89.59 %) |
| SpotBugs at close | 0 BugInstance |
| Threat coverage | T-93-01 (Bot-Token leak — Logback mask + Mapper no-info-leak + env-var-only + SpotBugs gate), T-93-02 (Webhook-URL leak — `%replace` mask + `DiscordLogMaskingIT` regression fence), T-93-04 (Rate-limit burst — Plan 93-01, documented here for completeness). T-93-03 (channel-permission bypass) remains FORWARD-REFERENCE to Phase 94 CHAN-02. |
