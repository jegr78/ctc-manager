---
phase: 93
slug: discord-foundation
status: shipped
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-21
verified_on: 2026-05-25
# nyquist refreshed by Phase 99 audit-polish — 93-VERIFICATION.md is authoritative
---

# Phase 93 — Validation Strategy

> Per-phase validation contract for feedback sampling during Discord Foundation execution.
> Source: `93-RESEARCH.md` § Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + WireMock 3.x (test-scope) + Playwright (compile-scope, runtime for E2E) |
| **Config file** | `pom.xml` (Surefire / Failsafe / JaCoCo), `src/test/resources/application-test.yml` |
| **Quick run command** | `./mvnw -DfailIfNoTests=false -Dtest='Discord*Test' test` |
| **Plan-wave full suite** | `./mvnw verify` (unit + integration + JaCoCo) |
| **Phase-close full suite** | `./mvnw verify -Pe2e` (incl. Playwright E2E for `DiscordConfigPageE2ETest`) |
| **Estimated runtime** | quick: ~15s; verify: ~12 min; verify -Pe2e: ~18 min |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw -DfailIfNoTests=false -Dtest='<changed-class>*Test' test` plus `./mvnw -Dit.test='<changed-class>*IT' verify -DskipUTs=false` if an IT was touched
- **After every plan wave (plan-close):** Run `./mvnw verify` — JaCoCo coverage MUST stay ≥ 88.88 % per D-07
- **Before `/gsd-verify-work` (phase-close):** `./mvnw clean verify -Pe2e` MUST be fully green, incl. Playwright `DiscordConfigPageE2ETest`
- **Max feedback latency:** ~30 s for quick task verify (single test class); ~12 min for plan-wave full suite

---

## Per-Task Verification Map

> Pre-populated from `93-RESEARCH.md` § Validation Architecture. Task IDs are placeholders — `gsd-planner` will finalize exact `{phase}-{plan}-{task}` identifiers during planning.

### Plan 93-01 — INFRA-01 Discord Clients + Utilities

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 93-01-EX1 | 01 | 1 | INFRA-01 | — | Sealed `DiscordApiException` permits 4 subclasses, mapper returns correct permit per HTTP status | unit | `./mvnw -Dtest='DiscordApiExceptionMapperTest' test` | ❌ W0 | ⬜ pending |
| 93-01-RC1 | 01 | 1 | INFRA-01 | T-93-04 | `DiscordRateLimitInterceptor` sleeps + retries on 429 with `Retry-After`; throws `DiscordTransientException` after 3 exhausted retries | integration | `./mvnw -Dit.test='DiscordRateLimitInterceptorIT' verify` | ❌ W0 | ⬜ pending |
| 93-01-RC2 | 01 | 1 | INFRA-01 | — | `DiscordRestClient.fetchBotUser()` returns typed `BotUser` on 200; routes 401 → `DiscordAuthException`, 404 → `DiscordNotFoundException`, 5xx → `DiscordTransientException` via mapper | integration | `./mvnw -Dit.test='DiscordRestClientIT' verify` | ❌ W0 | ⬜ pending |
| 93-01-WC1 | 01 | 1 | INFRA-01 | — | `DiscordWebhookClient.execute()` POSTs JSON; `executeMultipart()` builds correct `multipart/form-data` with `payload_json` + `files[0..n]` parts | integration | `./mvnw -Dit.test='DiscordWebhookClientIT' verify` | ❌ W0 | ⬜ pending |
| 93-01-EC1 | 01 | 1 | INFRA-01 | — | `DiscordEmojiCache` cold-load returns fallback `:NAME:`; after `refresh()` returns `<:NAME:id>` long-form; 60-min TTL expiry via `Clock.fixed` | unit | `./mvnw -Dtest='DiscordEmojiCacheTest' test` | ❌ W0 | ⬜ pending |
| 93-01-TS1 | 01 | 1 | INFRA-01 | — | `DiscordTimestamps.longDateTime(...)` returns `<t:N:F>` for fixed `Clock` + `ZoneId.of("Europe/Berlin")`; all 6 styles (:F/:f/:D/:d/:t/:R) verified | unit | `./mvnw -Dtest='DiscordTimestampsTest' test` | ❌ W0 | ⬜ pending |
| 93-01-MP1 | 01 | 1 | INFRA-01 | — | WireMock multipart matcher asserts `Content-Type: multipart/form-data; boundary=*` + `payload_json` part + `files[0]` part with PNG magic bytes | integration | `./mvnw -Dit.test='DiscordWebhookClientMultipartIT' verify` | ❌ W0 | ⬜ pending |

### Plan 93-02 — INFRA-02 Threat Model + Security Surfaces

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 93-02-TM1 | 02 | 1 | INFRA-02 | T-93-01..04 | `93-THREAT-MODEL.md` artifact exists with 4 threat rows + 6 mitigation-surface columns | manual | `test -f .planning/phases/93-discord-foundation/93-THREAT-MODEL.md && grep -c 'T-93-0' .planning/phases/93-discord-foundation/93-THREAT-MODEL.md` | ❌ W0 | ⬜ pending |
| 93-02-HW1 | 02 | 1 | INFRA-02 | T-93-01 | `DiscordRestClient` constructor throws `IllegalArgumentException("Discord host blocked: ...")` if base URL host ∉ `app.discord.allowed-hosts` | unit | `./mvnw -Dtest='DiscordClientHostWhitelistTest' test` | ❌ W0 | ⬜ pending |
| 93-02-LM1 | 02 | 1 | INFRA-02 | T-93-01, T-93-02 | Logback `%replace` masks `https?://[^/\s]+/api/webhooks/[^/\s]+/[^/\s]+` → `https://.../api/webhooks/***/***` across all profiles; provoked transient-exception stacktrace contains `***/***` and NOT raw URL/token | integration | `./mvnw -Dit.test='DiscordLogMaskingIT' verify` | ❌ W0 | ⬜ pending |
| 93-02-TE1 | 02 | 1 | INFRA-02 | T-93-01 | `@ToString.Exclude` on `DiscordGlobalConfig.announcementWebhookUrl` field (Phase 93 seed) — `toString()` of entity does NOT contain webhook URL substring | unit | `./mvnw -Dtest='DiscordGlobalConfigToStringTest' test` | ❌ W0 | ⬜ pending |
| 93-02-SB1 | 02 | 1 | INFRA-02 | T-93-01 | SpotBugs `BugInstance` count on `org.ctc.discord.*` package = 0; no new `EI_EXPOSE_REP*` findings introduced | static | `./mvnw spotbugs:check` (bound to `./mvnw verify`) | ✅ (existing gate) | ⬜ pending |
| 93-02-CQ1 | 02 | 1 | INFRA-02 | T-93-01 | CodeQL gate-step exit 0 on PR HEAD SHA; positive-whitelist pattern in `DiscordRestClient` / `DiscordWebhookClient` does NOT trigger new `java/ssrf` HIGH+ findings (or, if it does, 3-layer FP triad applied per Phase 85 D-19) | static | `gh run view --log` after CI run | ✅ (existing gate) | ⬜ pending |

### Plan 93-03 — INFRA-03 Admin Config Page + Flyway V8

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 93-03-FW1 | 03 | 1 | INFRA-03 | — | `V8__discord_global_config.sql` applies on H2 + MariaDB; `findFirstByOrderByIdAsc()` returns the seed row with empty-string defaults + `vs_emoji_name='CTC'` | integration | `./mvnw -Dit.test='DiscordGlobalConfigRepositoryIT' verify` | ❌ W0 | ⬜ pending |
| 93-03-CR1 | 03 | 1 | INFRA-03 | T-93-01 | `DiscordGlobalConfigGuardTest` asserts only one row exists post-save (no second INSERT); `BackupSchemaGuardTest` stays at EXPORT_ORDER = 24 (DiscordGlobalConfig excluded by `org.ctc.domain.model.*` filter per Phase 72 D-15) | integration | `./mvnw -Dit.test='DiscordGlobalConfigGuardTest,BackupSchemaGuardTest' verify` | ❌ W0 / ✅ existing | ⬜ pending |
| 93-03-DT1 | 03 | 1 | INFRA-03 | T-91-02-IL (mass-assignment) | `DiscordConfigForm` is a POJO (no entity fields); `DiscordConfigController` binds `@Valid DiscordConfigForm` not `DiscordGlobalConfig`; `@NotBlank` / `@Pattern` snowflake regex `^\d{17,20}$` enforced | unit | `./mvnw -Dtest='DiscordConfigFormTest,DiscordConfigControllerTest' test` | ❌ W0 | ⬜ pending |
| 93-03-CT1 | 03 | 1 | INFRA-03 | — | `GET /admin/discord-config` renders 6 form fields; `POST /admin/discord-config/save` updates the same singleton row; CSRF token required and validated | integration | `./mvnw -Dit.test='DiscordConfigControllerIT' verify` | ❌ W0 | ⬜ pending |
| 93-03-TB1 | 03 | 1 | INFRA-03 | — | 4 test-buttons (Test Connection / Test Announcement-Webhook / Refresh Server-Roles / Refresh Emoji) each invoke the correct RestClient/WebhookClient method and set `successMessage` or `errorMessage` + `errorCategory` flash; D-12 "needs field X" disabled-button tooltips render correctly | integration | `./mvnw -Dit.test='DiscordConfigControllerIT' verify` | ❌ W0 | ⬜ pending |
| 93-03-FE1 | 03 | 1 | INFRA-03 | — | Typed-catch on `DiscordApiException` resolves to BEM `.error-badge--{auth\|transient\|not-found\|category-full}`; new `.error-badge--category-full` (yellow) + `.badge-warning` ("not configured") CSS classes exist and apply | unit | `./mvnw -Dtest='DiscordConfigControllerErrorCategoryTest' test` | ❌ W0 | ⬜ pending |
| 93-03-E2E | 03 | 1 | INFRA-03 | — | Playwright E2E: fill 6 fields, click Save, page-reload shows persisted values; click each test button (against WireMock-backed Discord stub) and assert `.success-badge` / `.error-badge--{category}` render correctly on Desktop + Mobile per [[feedback-playwright-cli]] | e2e | `./mvnw verify -Pe2e -Dit.test='DiscordConfigPageE2ETest'` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

> Wave 0 = first commit of each plan creates the failing test stubs that subsequent tasks make green. Pattern carries forward from Phase 91 D-08 (per-plan Nyquist VALIDATION.md, [[feedback-test-call-optimization]]).

### Plan 93-01 (INFRA-01) Wave 0

- [ ] `src/test/java/org/ctc/discord/exception/DiscordApiExceptionMapperTest.java` — stubs for 4 sealed permits + mapper dispatch
- [ ] `src/test/java/org/ctc/discord/DiscordRestClientIT.java` — WireMockExtension setup, `@DynamicPropertySource` Discord-base-URL override, stub for `fetchBotUser()` happy + 401 + 404 paths
- [ ] `src/test/java/org/ctc/discord/DiscordWebhookClientIT.java` — WireMock stub for `execute()` happy + 429-retry + multipart skeleton
- [ ] `src/test/java/org/ctc/discord/DiscordRateLimitInterceptorIT.java` — WireMock 429-then-200 fixture + Awaitility wallclock-tolerance assertion
- [ ] `src/test/java/org/ctc/discord/DiscordEmojiCacheTest.java` — `Clock.fixed(...)` + TTL boundary stubs
- [ ] `src/test/java/org/ctc/discord/DiscordTimestampsTest.java` — `Clock.fixed(...)` + `ZoneId.of("Europe/Berlin")` + 6 style assertions
- [ ] `src/test/java/org/ctc/discord/DiscordWebhookClientMultipartIT.java` — WireMock multipart matcher stub

### Plan 93-02 (INFRA-02) Wave 0

- [ ] `.planning/phases/93-discord-foundation/93-THREAT-MODEL.md` — 4 threat rows (T-93-01..04) + 6 mitigation-surface columns (a token env-var, b SSRF whitelist, c log-pattern mask, d `@ToString.Exclude`, e CSRF, f DTO mass-assignment)
- [ ] `src/test/java/org/ctc/discord/DiscordClientHostWhitelistTest.java` — constructor rejection assertions for non-`discord.com` hosts (positive whitelist)
- [ ] `src/test/java/org/ctc/discord/DiscordLogMaskingIT.java` — `OutputCaptureExtension` + WireMock provoke + assertion captured output contains `***/***` not raw URL
- [ ] `src/test/java/org/ctc/discord/model/DiscordGlobalConfigToStringTest.java` — `toString()` excludes webhook URL field (Phase 93 seed, full coverage in Phase 94 when more fields added)

### Plan 93-03 (INFRA-03) Wave 0

- [ ] `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigRepositoryIT.java` — `@DataJpaTest` + seed-row assertion + save-updates-same-row assertion
- [ ] `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigGuardTest.java` — single-row invariant after multiple saves
- [ ] `src/test/java/org/ctc/discord/dto/DiscordConfigFormTest.java` — `@Valid` + `BindingResult` + snowflake regex
- [ ] `src/test/java/org/ctc/discord/web/DiscordConfigControllerTest.java` — Mockito-only flash-attribute happy paths
- [ ] `src/test/java/org/ctc/discord/web/DiscordConfigControllerIT.java` — `@SpringBootTest` + `MockMvc` + CSRF + 4 test-button POST endpoints
- [ ] `src/test/java/org/ctc/discord/web/DiscordConfigControllerErrorCategoryTest.java` — typed-catch BEM resolution
- [ ] `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` — Playwright + `@Tag("e2e")` + WireMock outbound stub

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live Discord Test Connection + Announcement-Webhook + Refresh Server-Roles + Refresh Emoji Cache against operator's real Discord test-server (yields real `<:NAME:id>` entries; real bot avatar visible in announcement channel) | INFRA-01 acceptance (UAT-03) | Requires real Discord credentials (Bot Token, Guild ID, real Webhook URL) and a test-server only the operator owns — cannot ship in CI. Deferred to STATE.md `Pending UATs` per D-01; operator runs BEFORE Phase 94 CHAN-02 starts. | 1. Operator sets `DISCORD_BOT_TOKEN` env-var on local with real token. 2. Operator fills `/admin/discord-config` with real guild_id + webhook_url + vs_emoji_name='CTC'. 3. Click each of the 4 test buttons; observe success badges + real Discord server (bot user visible, webhook fires test message, role/emoji counts reflect actual server state). 4. Operator confirms in `STATE.md § Pending UATs` UAT-03 with date + result before any Phase 94 work that hits live Discord. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies (UAT-03 explicitly deferred per D-01)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify (every task above maps to a `mvn` / `gh` / `test -f` command)
- [ ] Wave 0 covers all MISSING references (each plan's first commit installs the failing-test stubs listed above)
- [ ] No watch-mode flags (`./mvnw` is run-once per CTC convention)
- [ ] Feedback latency < 30s for quick-task verify, < 12 min for plan-wave full verify
- [ ] `nyquist_compliant: true` set in frontmatter once gsd-planner finalizes task IDs and gsd-validate-phase confirms coverage

**Approval:** pending — gsd-planner will finalize task IDs in step 8; gsd-validate-phase will set `nyquist_compliant: true` after PLAN.md tasks are mapped row-by-row.
