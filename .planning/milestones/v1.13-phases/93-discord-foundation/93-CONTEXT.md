# Phase 93: Discord Foundation - Context

**Gathered:** 2026-05-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Deliver the Spring `RestClient` Bot client + Webhook client + sealed
`DiscordApiException` hierarchy + rate-limit interceptor + emoji cache +
`/admin/discord-config` admin page + Flyway V8 (`discord_global_config`) so
Phases 94-98 have a stable, security-hardened, WireMock-testable platform
to build business logic on. Three sequential inline plans on
`gsd/v1.13-discord-integration` per [[feedback-inline-sequential-execution]]
+ [[feedback-wave-pause]], mapping 1:1 to INFRA-01/02/03.

Phase 93 is INFRA-only — no Discord business logic (channels, posts, threads,
team-role-mapping all belong to Phases 94-97). The success signal is "Phase
94 can wire its first channel-create button against the clients without
introducing new infrastructure".

In scope:

- **INFRA-01 (Plan 93-01)** — `org.ctc.discord.*` package skeleton:
  - `DiscordRestClient` (Spring `RestClient` with `Authorization: Bot
    ${app.discord.bot-token}` interceptor, `/api/v10` base URI). Typed
    methods: `fetchBotUser()` (for Test-Connection), `fetchGuildRoles()`,
    `fetchGuildEmojis()`, `createChannel(...)`, `modifyChannel(...)`,
    `listChannels(...)`, `listActiveThreads(...)`, `listArchivedThreads(...)`,
    `createThread(...)`. Each call routes exceptions through
    `DiscordApiExceptionMapper.from(...)` analog to Phase 91
    `GoogleApiExceptionMapper`.
  - `DiscordWebhookClient` (Spring `RestClient` to webhook URL): `execute()`
    + `executeMultipart()` (via `MultipartBodyBuilder`) + `editMessage()`
    (Webhook-PATCH).
  - `DiscordRateLimitInterceptor` (`ClientHttpRequestInterceptor`):
    per-bucket token-bucket via hand-rolled
    `ConcurrentHashMap<String, BucketState>` keyed by `X-RateLimit-Bucket`;
    on 429: max 3 retries with `Retry-After` sleep + jitter; on 5xx:
    exponential 200ms → 1s → 5s. Throws `DiscordTransientException` after
    exhaustion (D-03).
  - Sealed `DiscordApiException` hierarchy (analog Phase 91 `GoogleApiException`
    in `org.ctc.dataimport.exception`): 4 permits `DiscordTransientException`
    (5xx, 429-exhausted, network-IO), `DiscordAuthException` (401/403 token),
    `DiscordNotFoundException` (404 channel/role/webhook), `DiscordCategoryFullException`
    (Discord 50-channel-per-category limit). `Category` enum
    `TRANSIENT/AUTH/NOT_FOUND/CATEGORY_FULL`. `DiscordApiExceptionMapper`
    static helper with hardcoded user-message constants.
  - `DiscordTimestamps` utility: `longDateTime(LocalDateTime, ZoneId)` →
    `<t:N:F>`, `relative(...)` → `<t:N:R>`, plus `:f/:D/:d/:t` styles. Reads
    `app.timezone` (default `Europe/Berlin`).
  - `DiscordEmojiCache`: hand-rolled
    `ConcurrentHashMap<String, CachedEntry<String>>` with `Instant`-based
    60-min TTL (D-03). `emojiFor(shortName)` returns `<:NAME:id>` long-form
    or fallback `:NAME:` literal. `refresh()` rebuilds via
    `DiscordRestClient.fetchGuildEmojis()`. `Clock` injectable for tests.

- **INFRA-02 (Plan 93-02)** — Security/Threat surfaces:
  - Eigenständiges `93-THREAT-MODEL.md` artifact in this phase dir (D-04)
    with T-93-01..04 tabular threat list (Threat / Likelihood / Impact /
    Mitigation / Verification) + 6 Mitigation-Surfaces from REQ INFRA-02.
  - `application-local.yml` documents `DISCORD_BOT_TOKEN` env-var pattern
    (analog `GOOGLE_CALENDAR_ID`); `application.yml` adds
    `app.discord.bot-token: ${DISCORD_BOT_TOKEN:}` +
    `app.discord.allowed-hosts: discord.com` + `app.timezone: Europe/Berlin`
    + `logging.pattern` mask for webhook-URL regex
    `https://discord.com/api/webhooks/[^/\s]+/[^/\s]+`.
  - SSRF-whitelist enforcement: `DiscordRestClient` + `DiscordWebhookClient`
    constructor asserts every outbound URL host is in the `allowed-hosts`
    list. Throws `IllegalArgumentException("Discord host blocked: " + host)`
    on mismatch. Analog to v1.5 `FileStorageService.validateHostname`
    pattern but POSITIVE whitelist (only `discord.com`), not blocklist.
  - `@ToString.Exclude` discipline: any future entity field holding
    webhook-secret or token MUST carry `@ToString.Exclude`. Phase 93
    seed: `DiscordGlobalConfig.announcementWebhookUrl` carries the
    annotation. SpotBugs scan on `org.ctc.discord.*` package must report
    zero findings.
  - Log-snapshot test: `DiscordLogMaskingTest` asserts the webhook-URL
    regex never appears unmasked in any log line emitted by
    `DiscordWebhookClient.execute()` even on transient-exception paths
    (deliberately fail a WireMock call to provoke a stacktrace).
  - CSRF: every `POST /admin/discord/**` endpoint inherits the existing
    CSRF chain from Phase 30 `SecurityConfig`/`OpenSecurityConfig` —
    Phase 93 only adds the endpoints; no SecurityConfig changes needed.
  - DTO mass-assignment defense: `DiscordConfigForm` POJO (analog
    `MatchdayForm`) replaces direct entity-binding on `POST
    /admin/discord-config/save`. `@Valid` + `BindingResult` per
    CLAUDE.md § Controller patterns.

- **INFRA-03 (Plan 93-03)** — Admin config page:
  - Flyway `V8__discord_global_config.sql` (H2 + MariaDB compatible) per
    Design Spec § 3.3 schema PLUS an inline seed row `INSERT INTO
    discord_global_config (guild_id, announcement_webhook_url,
    race_results_forum_channel_id, standings_forum_channel_id,
    vs_emoji_name, created_at, updated_at) VALUES ('', '', '', '', 'CTC',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)` (D-02). The empty-string seed
    row makes Service-singleton lookup `findFirstByOrderByIdAsc()`
    deterministically return ONE row; UI renders "not configured" badges
    until operator fills the form.
  - `DiscordGlobalConfig` JPA entity (Lombok `@Getter @Setter
    @NoArgsConstructor`, extends `BaseEntity`); `DiscordGlobalConfigRepository`
    (Spring Data) exposes `findFirstByOrderByIdAsc()`;
    `DiscordGlobalConfigService.getOrInitialize()` (idempotent loader).
  - `DiscordConfigController` + `DiscordConfigForm` (DTO mass-assignment
    defense) at route `GET/POST /admin/discord-config` (CSRF active).
  - Thymeleaf template `admin/discord-config.html` with 6 form fields
    (guild-ID, bot-token-status indicator derived from
    `DiscordRestClient.fetchBotUser()` cached result,
    announcement-webhook-URL, race-results-forum-channel-ID,
    standings-forum-channel-ID, vs-emoji-name) and 4 test/refresh buttons
    (Test Connection / Test Announcement-Webhook / Refresh Server-Roles
    Cache / Refresh Emoji Cache). All buttons trigger CSRF-protected
    POST endpoints that delegate to the corresponding RestClient method
    and set `successMessage` or `errorMessage` + `errorCategory` flash
    attributes via the typed-catch pattern (Phase 91 D-06/D-07 carry-forward).
  - Navigation: admin-nav entry under "Integrations" group (or analog to
    Google-Integration link if exists; planner-discretion).

Out of scope (deferred / not Phase-93 scope):

- **Live-Discord operator UAT in Phase 93 close (D-01).** Phase 93 closes
  on WireMock-IT coverage alone; UAT-03 (Live Discord Test Connection
  + Announcement-Webhook against operator's test-server) is deferred
  to `STATE.md § Pending UATs` and operator runs it before Phase 94
  CHAN-02 (real channel-create) starts.
- **`teams.discord_role_id`, `matches.discord_*`, `seasons.discord_*_thread_id`
  schema columns** — Flyway V9 (Phase 94), V10 (Phase 94), V12 (Phase 96)
  scope. Phase 93 only V8.
- **`discord_post` tracking table + `DiscordPostService.postOrEdit`
  pattern** — Flyway V11 (Phase 95) scope.
- **Channel-permission-overwrite model + permission-audit assertion**
  (T-93-03 mitigation site lives in Phase 94 `DiscordChannelService`,
  not Phase 93). Phase 93 documents the threat in `93-THREAT-MODEL.md`
  but the mitigation code lands in CHAN-02.
- **`ProvisionalScoresGraphicService`** — Phase 96 GRAFX-01.
- **`MatchForm`/`TeamForm`/`SeasonForm` extensions for Discord fields** —
  Phase 94/96.
- **Operator runbook `docs/operations/discord-integration.md`** — Phase
  98 DOCS-02; Phase 93 may add a TODO marker note but no content.
- **Static-site notification mirror / public-site Discord embed** —
  out-of-milestone per REQUIREMENTS.md § Out of Scope.

</domain>

<decisions>
## Implementation Decisions

### Live-UAT Strategy (Phase-Close vs Operator-Action)

- **D-01: WireMock-IT-only Phase-93 close; UAT-03 deferred to STATE.md
  `Pending UATs` (operator action before Phase 94 CHAN-02).** Phase 93
  closes when `./mvnw verify` is green and every WireMock-backed IT
  covers the 4 sealed-exception permits + rate-limit-retry + multipart-upload
  + emoji-cache 60-min refresh paths. The Live-Discord UAT against a
  test-server (Test Connection + Test Announcement-Webhook + Refresh
  Server-Roles Cache + Refresh Emoji Cache producing real entries) is
  deferred as **UAT-03** under STATE.md `Pending UATs`, analog
  UAT-02-Pattern (legacy-season smoke from v1.11 QUAL-05) and the
  v1.12 UX-01 visual-UAT carry-forward. The operator runs UAT-03
  BEFORE starting Phase 94 CHAN-02 (real `createChannel` against the
  test-server) so any Bot-Token/OAuth-Scope/Guild-ID setup gap surfaces
  pre-CHAN-02. Phase 94 success criteria reference UAT-03 as a
  hard-precondition. Rationale: Phase 94's Plan 94-01 (Team-Role-Mapping
  CHAN-01) is DB-only and unblocks immediately; only CHAN-02 actually
  hits Discord, so the live-UAT gating cost is paid exactly once at the
  right moment, not twice. REJECTED: Live-UAT as Phase-93 close-gate
  (delays Phase 94 unnecessarily, conflicts with [[feedback-wave-pause]]
  cadence — Phase 93 is INFRA-only and should ship as soon as WireMock
  ITs are green); both Live-UAT + post-deploy UAT-03 with prod-creds
  (doubles operator effort with no additional safety gain — Phase 98
  E2E already covers the prod-creds path via the runbook walkthrough).

### `DiscordGlobalConfig` Single-Row Enforcement

- **D-02: Service-side singleton via `findFirstByOrderByIdAsc()` +
  Flyway V8 seed-row with empty-string defaults.** V8 schema (Design
  Spec § 3.3) stays as-is (no UNIQUE / CHECK constraints) PLUS an inline
  `INSERT` that seeds one row with empty-strings (`guild_id=''`,
  `announcement_webhook_url=''`, `race_results_forum_channel_id=''`,
  `standings_forum_channel_id=''`, `vs_emoji_name='CTC'`,
  `bot_application_id=NULL`, `created_at/updated_at=CURRENT_TIMESTAMP`).
  `DiscordGlobalConfigRepository` exposes only `findFirstByOrderByIdAsc()`;
  service-layer `getOrInitialize()` returns this row deterministically
  (no Optional handling in calling code — the seed row guarantees
  non-empty). UI renders empty fields with "not configured" badges
  until operator saves; saves update the SAME row (id from `getOrInitialize()`
  → no second INSERT). H2 + MariaDB compatible (`CURRENT_TIMESTAMP`
  literal works on both; empty-string DEFAULT works on both).
  Rationale: simplest pattern that survives both DB engines without
  CHECK-constraint drift risk (H2 2.x enforces CHECK strictly, older
  MariaDB ignores CHECK silently — Phase 90 surfaced similar dialect
  drift on `LONGTEXT`); no admin-only insert-or-update branching at
  the controller; Phase 92 cross-engine compatibility discipline
  preserved. REJECTED: DB-CHECK-constraint `id = 1` (cross-engine
  enforcement drift risk per Phase 72 D-09 `LONGTEXT` precedent);
  application-property + DB hybrid (contradicts INFRA-03 acceptance
  criterion "admin page provides operator surface for guild-ID input,
  announcement-webhook-URL input, ..." — operator must NOT edit yml).

### Cache Implementation (EmojiCache + RateLimit-Bucket)

- **D-03: Hand-rolled `ConcurrentHashMap<String, CachedEntry<T>>` with
  `Instant`-based TTL, `Clock` injectable for tests.** Strict
  zero-new-production-dependency discipline (Design Spec § 8, STATE.md
  baseline). Two structurally identical caches:
  - `DiscordEmojiCache`: `ConcurrentHashMap<String, CachedEntry<String>>`
    keyed by `team.shortName`, value `<:NAME:id>` long-form. TTL 60 min;
    `refresh()` bulk-replaces via
    `DiscordRestClient.fetchGuildEmojis()`. Manual refresh button on
    `/admin/discord-config`.
  - `DiscordRateLimitInterceptor` bucket store:
    `ConcurrentHashMap<String, BucketState>` keyed by `X-RateLimit-Bucket`
    header; `BucketState` record carries `remaining (int)`, `resetAt
    (Instant)`. Per-bucket token-bucket semantics: pre-request check
    `bucket.remaining > 0` else sleep until `resetAt`; post-response
    update from `X-RateLimit-Remaining` / `X-RateLimit-Reset-After`.
    429-retry-after exhaustion (max 3) throws
    `DiscordTransientException`.
  - Common pattern: `record CachedEntry<T>(T value, Instant expiresAt)`
    in `org.ctc.discord.util.CachedEntry`; both caches consume it.
    `Clock` injected via Spring `@Bean Clock systemClock()` in
    `DiscordConfig` so tests can replace with `Clock.fixed(...)`.

  Rationale: ~80-100 LOC per cache, full test-control via clock
  injection, no transitive Guava-cache prod-exposure risk
  ([[feedback-spring-native-preference]] — Spring `RestClient` is
  Spring-native, but cache is small enough not to warrant pulling
  `com.google.common.cache` into the prod runtime classpath). Phase
  86 D-11 pattern (no-frills custom util preferred over framework
  pull when LOC is small). REJECTED: Spring `@Cacheable` +
  `ConcurrentMapCacheManager` (no TTL out-of-the-box; would need
  separate `@Scheduled` evict-task per cache + still needs custom
  rate-limit-bucket since `@Cacheable` doesn't fit per-request bucket
  state); Guava `CacheBuilder` (Guava IS already pinned `33.4.8-jre`
  via Phase 68 override, but only as transitive override; promoting
  `com.google.common.cache` to first-class prod-dep semantically
  changes the dependency contract — Phase 84 Renovate scope; defer
  to v1.14 if a 3rd cache emerges).

### Threat-Model Artifact Shape

- **D-04: Eigenständiges `93-THREAT-MODEL.md` artifact in
  `.planning/phases/93-discord-foundation/`.** Markdown file with
  tabular threat list (T-93-01..04 columns Threat / Likelihood / Impact
  / Mitigation / Verification) PLUS the 6 Mitigation-Surfaces from REQ
  INFRA-02 (a token env-var, b SSRF whitelist, c log-pattern mask, d
  `@ToString.Exclude`, e CSRF, f DTO mass-assignment). Referenced by:
  93-02-PLAN.md (as the authoritative mitigation manifest), 93-02-SUMMARY.md
  (final disposition), Phase 94 (T-93-03 channel-permission audit
  mitigation lands in CHAN-02 `DiscordChannelService.createMatchChannel`
  — Phase 94's CONTEXT.md will cite `93-THREAT-MODEL.md`), Phase 98
  DOCS-02 (`docs/operations/discord-integration.md` Troubleshooting
  section pulls from the table). Rationale: analog to
  `docs/security/sast-acceptance.md` Update-on-Triage discipline; single
  source-of-truth for future Phase-N threats; survives across phase
  boundaries without forcing planner to grep across N PLAN.md files;
  mirrors Phase 72 `BackupSchema` wire-contract pattern (artifact-first,
  code-second). REJECTED: Inline-block in 93-02-PLAN.md (Phase 94 would
  need to reference a "frozen" plan-file; planner-doc lifecycle is
  PLAN→EXECUTE→SUMMARY, not "shared cross-phase artifact"); distributed
  source-marker comments only (no central audit point; Phase 98
  Troubleshooting would require multi-file grep — too brittle).

### Plan Decomposition & Sequencing (carried forward from Phase 92 D-05)

- **D-05: Three plans, sequential inline on `gsd/v1.13-discord-integration`.**
  Mirrors Phase 92 D-05 pattern + Design Spec § 5. Order:
  - **Plan 93-01 — INFRA-01 Discord Clients + Utilities.** All of
    `org.ctc.discord.*` non-config code: `DiscordRestClient`,
    `DiscordWebhookClient`, `DiscordRateLimitInterceptor`,
    `DiscordApiException` sealed hierarchy + 4 permits +
    `DiscordApiExceptionMapper`, `DiscordTimestamps`,
    `DiscordEmojiCache`, `CachedEntry` record, `BucketState` record,
    `Clock` bean, WireMock-backed ITs for all 4 sealed-exception paths
    + rate-limit-retry + multipart-upload + emoji-cache-refresh.
    Production-code surface — no UI yet.
  - **Plan 93-02 — INFRA-02 Threat Model + Security Surfaces.**
    `93-THREAT-MODEL.md` artifact (D-04), `application*.yml` updates
    (bot-token env-var, allowed-hosts, app.timezone, log-pattern mask),
    SSRF-whitelist enforcement in both clients (constructor-arg validation),
    `DiscordLogMaskingTest` (log-snapshot regression),
    `DiscordClientHostWhitelistTest` (constructor rejects non-whitelisted
    URL). No new entities/controllers in this plan.
  - **Plan 93-03 — INFRA-03 Admin Config Page + V8.** Flyway
    `V8__discord_global_config.sql` (with seed-row INSERT per D-02),
    `DiscordGlobalConfig` JPA entity, `DiscordGlobalConfigRepository`
    (Spring Data, only `findFirstByOrderByIdAsc()` exposed),
    `DiscordGlobalConfigService.getOrInitialize() + save(form)`,
    `DiscordConfigForm` DTO, `DiscordConfigController` (CSRF active),
    Thymeleaf `admin/discord-config.html` with 6 form fields + 4
    test/refresh buttons, navigation entry, Playwright E2E covering
    the form fill + each test button + WireMock outbound-call
    assertion + success-badge render.
  No worktrees, no subagents per [[feedback-inline-sequential-execution]].

### PR Mechanics (carried forward from Phase 92 D-06)

- **D-06: Rolling v1.13 milestone PR — Plan 93-01 onwards updates body
  via `gh pr edit --body-file`.** Phase 92 Plan 92-01 opened the Draft
  PR; Phase 93 plans only update it. Each plan-ship appends a new
  entry to the rolling per-plan summary table (Plan # / REQ-ID /
  status / commit SHA / CI run URL). Final composite body shape per
  Phase 91 D-07b finalized by Plan 98-03. PR stays Draft until end of
  Phase 98. Subject locked: `feat(v1.13): discord integration &
  carry-forwards` ([[feedback-squash-merge-message]]). REJECTED: re-open
  PR per phase (breaks rolling thread); silent body (loses early CI
  validation surface).

### Quality Gates (carried forward from Phase 92 D-07)

- **D-07: Standard gates apply, no tightening, no loosening.**
  - JaCoCo line coverage ≥ 88.88 % at end of Phase 93. Phase 93 adds
    ~30-50 tests (WireMock-backed ITs for 4 sealed-exception paths +
    rate-limit + multipart + emoji-cache + log-masking + host-whitelist
    + DiscordGlobalConfig repository IT + Playwright E2E for
    `/admin/discord-config`). Coverage MUST hold above the recovered
    88.88 % from Phase 92.
  - SpotBugs `BugInstance` count = 0 (blocking, verify-bound check).
    `org.ctc.discord.*` package added without surfacing new
    `EI_EXPOSE_REP*` findings; `@ToString.Exclude` on webhook-URL field
    avoids `MS_EXPOSE_REP`.
  - CodeQL gate-step exit 0 on PR HEAD SHA. SSRF-whitelist enforcement
    in `DiscordRestClient` + `DiscordWebhookClient` constructor uses
    the same `startsWith` host-validation pattern as
    `FileStorageService.validateHostname` (Phase 85 D-19 3-layer FP
    suppression invariant carries forward — any new SSRF finding
    requires `codeql-config.yml` query-filter + source-marker +
    `sast-acceptance.md` row triad).
  - `EXPORT_ORDER` = 24 entities; `BackupSchema.SCHEMA_VERSION` = 1
    (Phase 93 adds `DiscordGlobalConfig` under `org.ctc.discord.*` —
    structurally EXCLUDED from `EXPORT_ORDER` by the
    `org.ctc.domain.model.*` package filter per Phase 72 D-15;
    `BackupSchemaGuardTest` MUST stay green at 24).
  - Flyway V1-V7 immutable; Phase 93 adds V8 only.
  - `./mvnw verify -Pe2e` finishes within v1.12 CI E2E 17:39 ± 20 %
    tolerance. Phase 93 adds ~5-10 WireMock-backed ITs (lightweight)
    + 1 Playwright E2E (`/admin/discord-config` form-fill +
    test-button-clicks). Expected impact: < 60 s.

### Test Discipline (carried forward from Phase 92 D-08/D-09)

- **D-08: Per-Plan Nyquist VALIDATION.md.** Plans 93-01..93-03 each
  ship with a VALIDATION.md. Phase 93 self-validates via
  `/gsd-validate-phase 93` before `/gsd-execute-phase 94` starts.

- **D-09: Tag every new test class per CLAUDE.md `@Tag` convention.**
  - WireMock-backed ITs (`DiscordRestClient*IT`, `DiscordWebhookClient*IT`,
    `DiscordRateLimitInterceptor*IT`, `DiscordGlobalConfigRepository*IT`)
    → `@Tag("integration")`.
  - Mockito-only unit tests (`DiscordApiExceptionMapperTest`,
    `DiscordTimestampsTest`, `DiscordEmojiCacheTest` (uses `Clock.fixed`),
    `DiscordLogMaskingTest`, `DiscordClientHostWhitelistTest`,
    `DiscordConfigFormTest`) → untagged (project convention).
  - Playwright E2E (`DiscordConfigPageE2ETest`) →
    `@Tag("e2e")`, package `org.ctc.e2e.discord` per
    `.planning/codebase/TESTING.md` § Test Categorization.

### Threat Model Anchors (preview for `93-THREAT-MODEL.md` content)

- **D-10: T-93-01..04 threat anchors.** Plan 93-02 authors the full
  table; preview:
  - **T-93-01 Bot-Token leak via stacktrace/logs.** Likelihood: Medium.
    Impact: High (token must be rotated, Discord support contact).
    Mitigations: `@ToString.Exclude` on any future entity field,
    `app.discord.bot-token` only via env-var (never `application.yml`
    literal), logging-pattern mask, `DiscordLogMaskingTest` regression
    fence, SpotBugs `BugInstance` count gate. Verification:
    `assertThat(toString-of-DiscordGlobalConfig).doesNotContain(token-literal)`
    + log-snapshot test.
  - **T-93-02 Webhook-URL leak via logs.** Likelihood: Medium. Impact:
    High (anyone with URL can post to channel impersonating
    CTC-Manager). Mitigations: `logging.pattern` regex masking in
    `application.yml` BASE config (applies to all profiles),
    `DiscordLogMaskingTest` regression fence,
    `@ToString.Exclude` on Match.discordChannelWebhookUrl when Phase 94
    adds it (forward-reference in `93-THREAT-MODEL.md`).
    Verification: log-snapshot test exercises both clients'
    transient-exception paths.
  - **T-93-03 Channel-permission bypass via wrong role-mapping.**
    Likelihood: Low (Phase-94 scope). Impact: High (opposing team
    sees match-channel pre-match). Mitigations: post-create
    permission-audit assertion in Phase 94 `DiscordChannelService`;
    documented as forward-reference in `93-THREAT-MODEL.md` — code
    lives in CHAN-02, not Phase 93. Verification: Phase 94 IT.
  - **T-93-04 Rate-limit burst triggering Discord-bot-ban.**
    Likelihood: Medium (matchday-batch posting Phase 97 risk).
    Impact: Medium (some posts missed, recovery requires bot-restart).
    Mitigations: `DiscordRateLimitInterceptor` per-bucket token-bucket
    + max-3-retries-with-Retry-After-sleep + exponential 5xx backoff
    + `DiscordTransientException` on exhaustion (D-03 cache shape
    enables clean test). Verification: WireMock IT exercises 429
    response with `Retry-After: 2` header and asserts the sleep +
    retry happens then succeeds.

### Production Behavior Boundary

- **D-11: Production code touched in all 3 plans, but strictly within
  `org.ctc.discord.*` package + `application*.yml` + Flyway V8 +
  template `admin/discord-config.html` + nav link.** No edits to
  `org.ctc.domain.model.*`, `org.ctc.admin.controller.*` (except
  the new `DiscordConfigController` lives in
  `org.ctc.admin.controller.discord` sub-package or
  `org.ctc.discord.web.*` — planner-discretion via Phase-92
  package-layout convention), `org.ctc.dataimport.*`,
  `org.ctc.backup.*`, `org.ctc.sitegen.*`, `org.ctc.gt7sync.*` —
  Phase 93 is INFRA-only. Each plan SUMMARY asserts `src/` clean
  outside the explicitly listed paths.

### Bootstrap UX

- **D-12: `/admin/discord-config` renders empty-config state with
  "not configured" badges, NOT redirect-to-setup-wizard.** When the
  seed-row from V8 has empty `guild_id`, the page still renders all
  6 form fields with placeholder hints + the 4 test buttons gated
  on per-field presence (Test Connection: needs `bot-token` env-var
  set; Test Announcement-Webhook: needs `announcement_webhook_url`
  filled; Refresh Server-Roles Cache: needs `guild_id` filled;
  Refresh Emoji Cache: needs `guild_id` filled). Disabled buttons
  carry a tooltip explaining the missing field. Rationale: matches
  the established admin-page UX (no wizards, all forms are
  flat-edit, "not configured" badges are familiar from
  `/admin/seasons` empty-state). REJECTED: setup-wizard
  (over-engineered for single-operator one-time-setup); auto-redirect
  to env-var docs (annoying for re-visits).

### Claude's Discretion

- **Package layout for `DiscordConfigController`** — either
  `org.ctc.admin.controller.discord` (sibling to existing admin
  controllers) or `org.ctc.discord.web` (cohesive with the rest of
  `org.ctc.discord.*`). Planner picks based on Phase-92 package-layout
  convention; both are valid per CLAUDE.md § Naming Patterns.
- **Exact CSS for "not configured" badges** — likely reuses
  `.error-badge` + a new neutral variant or `.badge-warning`; planner
  verifies against `admin.css` palette.
- **Navigation entry placement** — under existing "Integrations" group
  if it exists; otherwise sibling to Google-Sheets-Import link. Planner
  decides based on `admin/layout.html` actual nav structure.
- **Exact `application.yml` `logging.pattern` regex** — must mask
  webhook-URL pattern AND must not regress existing log readability.
  Planner picks the regex that surfaces in
  `DiscordLogMaskingTest` and verifies CI build remains noise-free.
- **WireMock `@Tag` already-inherited from parent IT or new explicit
  `@Tag("integration")`** — planner verifies each WireMock IT class
  inherits from a tagged parent or carries its own tag.
- **Exact wording of T-93-01..04 cells in `93-THREAT-MODEL.md`** — D-04
  + D-10 lock the structure (Threat / Likelihood / Impact / Mitigation
  / Verification columns); planner picks prose, mirrors
  `docs/security/sast-acceptance.md` style.
- **Whether `DiscordTimestamps` is a static-method utility class or a
  Spring `@Component`** — `Clock` injection is via `DiscordEmojiCache`
  constructor; `DiscordTimestamps` can stay static if it takes `ZoneId`
  + `Clock` as parameters, or be a singleton bean with injected
  `ZoneId` + `Clock`. Planner picks based on test-shape ergonomics.
- **Whether the 4 test-buttons on `/admin/discord-config` issue
  synchronous POST + page-reload or AJAX + inline-result** — planner
  picks based on Phase-30 CSRF pattern existing AJAX precedent
  (likely sync POST + flash + page-reload per CLAUDE.md § Flash
  Attributes).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope & Requirements

- `.planning/ROADMAP.md` § "Phase 93: Discord Foundation" — goal,
  Depends-on (Phase 92), Requirements (INFRA-01, INFRA-02, INFRA-03)
- `.planning/REQUIREMENTS.md` § "Discord Foundation (INFRA, Phase 93)"
  — INFRA-01/02/03 full requirement text + acceptance criteria
- `.planning/milestones/v1.13-ROADMAP.md` § "Phase 93" — 5 Success
  Criteria + Phase Dependency Graph (Phase 94 depends on Phase 93;
  forward-chain to Phases 95-98)
- `.planning/PROJECT.md` § "Current Milestone: v1.13" — Discord
  Integration framing, "zero new production dependencies" invariant,
  "outbound-only" architecture, "button-triggered" operator-control
- `.planning/STATE.md` § "Active Milestone — v1.13" + § "Baselines to
  Preserve" (JaCoCo ≥ 88.88 %, CI E2E 17:39 ± 20 %, SpotBugs 0,
  CodeQL exit 0, `EXPORT_ORDER` 24, SCHEMA_VERSION 1, Flyway V1-V7
  immutable, V8 lands here) + § "Blockers/Concerns" (live-Discord
  UAT required, security-critical permission-audit forward-reference)
- `docs/superpowers/specs/2026-05-20-discord-integration-design.md` —
  THE authoritative design document. § 1 Goal, § 2 Scope,
  § 3.1 Integration Model (Hybrid), § 3.2 Package Layout
  (`org.ctc.discord.*` skeleton), § 3.3 Data Model (V8 schema,
  V11 `discord_post`, V9/V10/V12 forward-reference), § 3.4 Security
  (T-93-01..04 + 6 mitigation surfaces), § 3.5 Error Handling (sealed
  exception pattern analog Phase 91), § 3.6 Rate-Limit Handling,
  § 3.7 Discord Timestamps, § 3.8 Emoji Resolution, § 4.1 New Pages
  (`/admin/discord-config`), § 5 Phase Breakdown (Phase 93 = 3
  plans), § 6 Risks (T-93-01..04 source), § 7 Baselines Preserved,
  § 8 Dependencies (zero new prod deps), § 9 Resolved Brainstorming
  Decisions (D-01..D-18)

### Phase 92 Hand-off (PRIMARY INPUT — sequencing + PR mechanics)

- `.planning/phases/92-carry-forwards-cleanup/92-CONTEXT.md` —
  **D-05 (inline-sequential on `gsd/v1.13-discord-integration`, no
  worktrees, no subagents)**, **D-06 (rolling milestone PR — Plan
  93-01 updates body via `gh pr edit --body-file`)**, **D-07
  (standard quality gates — JaCoCo ≥ 88.88 %, SpotBugs 0, CodeQL 0,
  `EXPORT_ORDER` 24, SCHEMA_VERSION 1, Flyway V1-V7 immutable)**,
  **D-08 (per-plan Nyquist VALIDATION.md + `/gsd-validate-phase` before
  next phase)**, **D-09 (`@Tag` convention per CLAUDE.md)**, **D-10
  (production code boundary discipline)**.

### Phase 91 Hand-off (PRIMARY INPUT — sealed exception + UX pattern)

- `.planning/milestones/v1.12-phases/91-perf-re-harvest-stretch-ux-polish-milestone-closer/91-CONTEXT.md`
  — **D-06 (sealed `GoogleApiException` + 4 permits +
  `GoogleApiExceptionMapper` static helper + hardcoded user-message
  constants — Phase 93 mirrors this shape for `DiscordApiException`)**,
  **D-07 (`errorMessage` + `errorCategory` flash-attribute pattern
  with BEM `.error-badge--{category}` CSS — Phase 93 adds the
  `category-full` variant to admin.css)**, D-13 (`application*.yml`
  invariant — Phase 93 ONLY touches application*.yml for bot-token
  env-var + allowed-hosts + app.timezone + logging.pattern mask).
- `src/main/java/org/ctc/dataimport/exception/GoogleApiException.java`
  — the EXACT structural template for `DiscordApiException` sealed
  base.
- `src/main/java/org/ctc/dataimport/exception/GoogleApiExceptionMapper.java`
  — the EXACT structural template for `DiscordApiExceptionMapper`
  (status-code switch + Category enum + hardcoded user-message
  constants).
- `src/main/java/org/ctc/dataimport/exception/{Transient,Auth,NotFound,Permission}GoogleApiException.java`
  — 4 permit classes; Phase 93 creates 4 analogs
  (`DiscordTransientException`, `DiscordAuthException`,
  `DiscordNotFoundException`, `DiscordCategoryFullException`).

### v1.5 Security Anchor (SSRF + CSRF + DTO patterns)

- `src/main/java/org/ctc/domain/service/FileStorageService.java` lines
  126-159 — `validateHostname` startsWith-chain pattern. Phase 93
  adapts to POSITIVE whitelist (only `discord.com` allowed) instead
  of negative blocklist. CodeQL FP-marker pattern (3-layer
  invariant: source-marker + `codeql-config.yml` query-filter +
  `docs/security/sast-acceptance.md` row) per Phase 85 D-19 carries
  forward.
- `src/main/java/org/ctc/admin/SecurityConfig.java` +
  `src/main/java/org/ctc/admin/OpenSecurityConfig.java` — dual-profile
  CSRF + auth chain (Phase 5 + Phase 30). Phase 93 adds endpoints
  under existing chain — no SecurityConfig modification needed.
- `src/main/java/org/ctc/admin/dto/MatchdayForm.java` — canonical
  Form-DTO shape Phase 93 `DiscordConfigForm` mirrors (Lombok
  `@Getter @Setter @NoArgsConstructor`, `jakarta.validation.constraints`,
  no entity fields).
- `docs/security/sast-acceptance.md` — Update-on-Triage discipline.
  Phase 93 adds a row for the new SSRF positive-whitelist suppression
  if CodeQL flags `DiscordRestClient` / `DiscordWebhookClient`.

### Flyway / Migration Conventions

- `src/main/resources/db/migration/V7__data_import_audit.sql` —
  H2 + MariaDB compatible migration shape Phase 93 V8 mirrors
  (CURRENT_TIMESTAMP literal, LONGTEXT for any large-text fields if
  needed, lowercase snake_case columns, plural snake_case table name).
- `src/main/resources/db/migration/V1__schema.sql` — base schema
  (READ-ONLY per CLAUDE.md `## Constraints`).
- `.planning/codebase/STACK.md` — Flyway version + DB engine
  compatibility constraints.

### Codebase Foundation Docs

- `.planning/codebase/ARCHITECTURE.md` — 3-tier layered architecture
  Phase 93 conforms to (Controller → Service → Repository); OSIV
  active.
- `.planning/codebase/CONVENTIONS.md` — naming patterns Phase 93
  follows.
- `.planning/codebase/STACK.md` — Spring Boot 4 + Spring 6.1+
  `RestClient` availability + Lombok + JaCoCo + SpotBugs constraints.
- `.planning/codebase/TESTING.md` § "Test Categorization (`@Tag`)" —
  `@Tag("integration")` for `*IT.java`, `@Tag("e2e")` for
  `org.ctc.e2e.*`, untagged for unit tests.
- `.planning/codebase/INTEGRATIONS.md` — current API surface
  (Google Sheets/Calendar, GT7, YouTube); Phase 93 adds Discord as
  new outbound integration analog (Bot+Webhook auth, allowed-hosts
  whitelist, sealed exception hierarchy).

### Conventions & Memory Anchors

- `CLAUDE.md` § "Test Naming (Given-When-Then)" + § "Tag Tests by
  Category" — every new test class follows BDD pattern + correct tag.
- `CLAUDE.md` § "Architectural Principles" — "DTOs instead of Entities
  in Controllers" (Phase 29 mass-assignment), "Keep Thymeleaf
  Templates Lean" (Phase 93 config-page logic in service, template
  for presentation only).
- `CLAUDE.md` § "Static Analysis (SpotBugs + find-sec-bugs)" — Phase
  93 must keep `BugInstance` count = 0; targeted
  `@SuppressFBWarnings({"CODE"}, justification="...")` only if needed.
- `CLAUDE.md` § "CodeQL SAST (Code Scanning)" — gate-step exit 0 on
  PR HEAD SHA; 3-layer FP suppression invariant applies to any new
  SSRF/path-injection findings on `DiscordRestClient` /
  `DiscordWebhookClient`.
- `CLAUDE.md` § "Subagent Rules" — execution per
  [[feedback-inline-sequential-execution]] (no subagents during
  Phase 93 execution).
- [[feedback-spring-native-preference]] — Spring `RestClient` over
  `java.net.http.HttpClient` (already locked by Design Spec § 3.2).
- [[feedback-inline-sequential-execution]] — D-05 sequential
  execution on `gsd/v1.13-discord-integration` is binding.
- [[feedback-wave-pause]] — pause for user feedback after each plan
  ship before starting the next plan.
- [[feedback-pr-description-update]] — D-06 rolling milestone PR
  body update pattern is binding.
- [[feedback-squash-merge-message]] — D-06 final PR subject
  `feat(v1.13): discord integration & carry-forwards` is binding
  for the MINOR bump.
- [[feedback-no-flaky-dismissal]] — Phase 93 tests that pass locally
  but fail in CI are regressions, never deferred.
- [[feedback-clean-build-only]] — Phase 93 verifies via
  `./mvnw clean test-compile` + `./mvnw verify -Pe2e`; no skip flags.
- [[feedback-clean-maven-build-authority]] — if compile errors appear
  in VS Code/Eclipse JDT, run `./mvnw clean test-compile` FIRST
  before assuming the new sealed-exception hierarchy is broken.
- [[feedback-playwright-cli]] — Phase 93 Playwright E2E
  (`DiscordConfigPageE2ETest`) must run on Desktop + Mobile per
  `playwright-cli` convention.

### Discord External API (informational, no offline doc)

- Discord REST API v10 (`https://discord.com/developers/docs/reference`
  + `https://discord.com/developers/docs/topics/rate-limits`) — not
  committed to repo; planner consults online. Phase 93 ITs encode
  the relevant request/response shapes via WireMock fixtures.
- `MANAGE_CHANNELS | MANAGE_ROLES | MANAGE_WEBHOOKS | VIEW_CHANNEL |
  SEND_MESSAGES | ATTACH_FILES | EMBED_LINKS | READ_MESSAGE_HISTORY |
  MANAGE_THREADS` — Bot OAuth permission bitmask (REQ DOCS-02 Phase
  98 publishes the operator runbook; Phase 93 records the bitmask
  in `93-THREAT-MODEL.md` mitigation column for T-93-01).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`org.ctc.dataimport.exception.*` package (Phase 91)** — the
  GoogleApiException sealed hierarchy + Mapper + 4 typed permits is
  the EXACT structural template for `org.ctc.discord.exception.*`.
  Copy the file shape verbatim, swap names, swap status-code mapping
  table to Discord's contract (5xx/429/network → TRANSIENT, 401/403
  → AUTH (split on `code: 50001`/permission-denied JSON), 404 →
  NOT_FOUND, 50-channel-category-limit (Discord error code `30013`)
  → CATEGORY_FULL).
- **`FileStorageService.validateHostname` (Phase 5/12) lines 126-159**
  — startsWith-chain hostname validation. Phase 93 inverts to a
  POSITIVE whitelist (only `discord.com` allowed). Pattern: extract
  the URL host, lowercase, check against the whitelist set. Same
  CodeQL FP source-marker discipline applies (3-layer invariant).
- **`MatchdayForm` + 11 other `*Form` DTOs in `org.ctc.admin.dto`** —
  Lombok pattern for `DiscordConfigForm`: `@Getter @Setter
  @NoArgsConstructor`, `@NotBlank` / `@Pattern(regexp=...)` for
  snowflake validation `^\d{17,20}$`, never entity fields.
- **`admin.css` `.error-badge` + `.error-badge--{auth|transient|not-found|permission}`
  BEM palette (Phase 91 D-07)** — Phase 93 ADDS a new variant
  `.error-badge--category-full` (yellow box-icon per Design Spec
  § 3.5). Plan 93-02 or 93-03 also adds a neutral `.badge-warning`
  variant for "not configured" config-page state.
- **`@Tag("integration")` discipline for WireMock-backed ITs** —
  existing `WireMockTest` / `WireMockExtension` usage in
  `src/test/java/org/ctc/dataimport/GoogleSheetsServiceIT.java` (or
  analog) is the canonical pattern for Plan 93-01's
  `DiscordRestClientWireMockIT` shape.

### Established Patterns

- **Sealed exception hierarchy + Mapper (Phase 91)** — `abstract
  sealed class ... permits ...` extending `IOException`. Mapper
  has private constructor, public static `from(...)` methods,
  switch-on-status-code + sub-switch on JSON-error-reason for fine
  dispatch.
- **Typed-catch + flash attribute (Phase 91 D-06/D-07)** —
  `catch (DiscordApiException e) { category = switch (e) { case ... }; ... }`.
  Whitelisted hardcoded `getUserMessage()` constants
  (`DiscordApiExceptionMapper.TRANSIENT_MESSAGE` etc), NEVER
  `e.getMessage()` echoed (T-91-02-IL info-leak invariant).
- **`@Tag("integration")` for WireMock-backed ITs** per
  `.planning/codebase/TESTING.md` § Test Categorization.
- **OSIV-active rendering** — `discord-config.html` can access
  lazy-loaded `DiscordGlobalConfig.toString()` (with
  `@ToString.Exclude`-stripped fields) directly per OSIV invariant.
- **Flash-attribute success/error pattern** — controllers use
  `redirectAttributes.addFlashAttribute("successMessage", ...)`
  or `("errorMessage", ...)` + `("errorCategory", ...)` per Phase
  91 carry-forward.
- **Spring-native preference** — `RestClient.create(...).get()
  .uri(...).header(...).retrieve().toEntity(...)` over
  `java.net.http.HttpClient` builder ([[feedback-spring-native-preference]]).
- **Profile-scoped seed data** — `DevDataSeeder` is
  `@Profile({"dev", "local"})` (Phase 83 QUAL-02). Phase 93 does
  NOT add Discord seed data — the V8 seed-row (D-02) is empty by
  design; operator fills the config page.

### Integration Points

- **`org.ctc.discord.DiscordRestClient` ← `RestClient` (Spring 6.1+
  core)** — `RestClient.builder().baseUrl("https://discord.com/api/v10")
  .defaultHeader(HttpHeaders.AUTHORIZATION, "Bot " + token)
  .requestInterceptor(rateLimitInterceptor).build()`. The interceptor
  reads `X-RateLimit-Bucket` from response, updates the
  `ConcurrentHashMap<String, BucketState>`, applies pre-request
  sleep on bucket-exhaustion.
- **`org.ctc.discord.DiscordWebhookClient` ← `RestClient` (per
  webhook URL)** — separate client because webhook URLs include the
  ID + token in the path (`/api/webhooks/{id}/{token}`). Multipart
  via `MultipartBodyBuilder` + `MultiValueMap<String, HttpEntity<?>>`
  to attach 1-10 PNGs per request (Discord supports up to 10).
- **`org.ctc.discord.web.DiscordConfigController` ←
  `DiscordGlobalConfigService` + `DiscordRestClient` (Test Connection)
  + `DiscordWebhookClient` (Test Announcement-Webhook) +
  `DiscordEmojiCache` (Refresh Emoji Cache)** — the controller
  delegates everything; no business logic in controller per
  CLAUDE.md § Keep Controllers Thin.
- **Flyway V8 ← schema register** — applies on H2 + MariaDB on
  startup; existing `BackupSchemaGuardTest` MUST stay at 24
  entities (DiscordGlobalConfig under `org.ctc.discord.*` is
  STRUCTURALLY EXCLUDED by Phase 72 D-15 package-name filter
  `org.ctc.domain.model.*` — no opt-out marker needed).
- **`application.yml` `logging.pattern` ← log-mask regex** —
  applies to ALL profiles base config. Mask:
  `https://discord.com/api/webhooks/[^/\s]+/[^/\s]+` →
  `https://discord.com/api/webhooks/***/***`. Phase 93 first writes
  the pattern; Plan 93-02 verifies via `DiscordLogMaskingTest`.
- **`org.ctc.discord` package ← `BackupSchema.EXPORT_ORDER`** —
  `DiscordGlobalConfig` lives under `org.ctc.discord.*` and is
  EXCLUDED from backup export per Phase 72 D-15 package filter.
  Operator-config-as-data is OUT-OF-SCOPE for backup (analog
  `data_import_audit` exclusion). Verified by
  `BackupSchemaGuardTest` (export count stays at 24).
- **Navigation entry ← `admin/layout.html` (or fragment)** — Phase
  93 adds nav link. Planner verifies fragment structure +
  active-state highlighting per Phase 40 pattern.

</code_context>

<specifics>
## Specific Ideas

- The Design Spec § 9 "Open Questions (Resolved During Brainstorming)"
  18-decision log is THE authoritative reference for Phase 93+ scope.
  Quote individual decisions (e.g., D-Spec-1 self-built-on-RestClient,
  D-Spec-7 webhook-PATCH-edit-strategy, D-Spec-14 native-Discord-timestamps,
  D-Spec-15 convention-based-emoji) in plan SUMMARYs to anchor
  rationale to the multi-round brainstorming session.
- The "category-full" badge variant is the only NEW BEM CSS class
  Phase 93 introduces; Phase 91 D-07's 4-class palette is otherwise
  carried forward unchanged.
- The seed-row INSERT in V8 is the SINGLE most-critical schema
  decision in Phase 93 — wrong default ('' vs NULL vs no-row) breaks
  page-render UX. Empty-string + UI "not configured" badge is the
  agreed pattern (D-02 + D-12).
- The hand-rolled cache pattern (D-03) sets a v1.13 precedent: if
  Phase 94+ needs additional caches (e.g., guild-roles cache for
  CHAN-01 dropdown), the same `CachedEntry<T>` record is reused.
- `93-THREAT-MODEL.md` (D-04) is a NEW artifact convention for
  v1.13+ — Phase 94 may add a `94-THREAT-MODEL.md` for CHAN-02
  permission-overwrite risks if T-93-03 demands phase-local
  elaboration.
- The "UAT-03 deferred" pattern (D-01) extends v1.11's UAT-02 +
  v1.12's QUAL-02 / UX-01 deferred-UAT discipline. STATE.md gets
  a new section entry under `Pending UATs`.

</specifics>

<deferred>
## Deferred Ideas

- **Guava `CacheBuilder` promotion to first-class prod-dep** —
  defer to v1.14 backlog if a 3rd cache emerges in Phase 94+ that
  outgrows the hand-rolled ConcurrentHashMap pattern. Phase 84
  Renovate scope would need a corresponding `<allowedVersions>`
  packageRule.
- **Spring `@Cacheable` + `@Scheduled`-evict adoption** — same
  deferral logic; revisit only if Phase 94 dropdown caches +
  Phase 97 emoji-cache reuse make declarative caching net-positive.
- **CodeQL FP suppression for the new positive-whitelist SSRF
  pattern** — if CodeQL flags `DiscordRestClient` or
  `DiscordWebhookClient`'s constructor host-validation as a
  potential SSRF, the 3-layer Phase 85 D-19 suppression invariant
  applies (`codeql-config.yml` query-filter + source-marker +
  `sast-acceptance.md` row). Plan 93-02 owns this if it surfaces.
- **`docs/operations/discord-integration.md` operator runbook** —
  Phase 98 DOCS-02 scope; Phase 93 plans only reference the runbook
  forward (e.g., from the "category-full" badge tooltip + from
  test-button failure messages).
- **Multi-Guild support** — DISC-FUTURE-04, REQUIREMENTS.md
  `Future Requirements`. The `discord_global_config` singleton
  pattern (D-02) makes a future multi-guild migration EASIER, not
  harder — a future V13 would add a `guild_id_pk` UNIQUE constraint
  + service-layer changes from `findFirstByOrderByIdAsc` to
  `findByGuildId(currentGuildId)`.
- **Always-online deployment + inbound interaction** —
  DISC-FUTURE-01, REQUIREMENTS.md `Future Requirements`. Phase 93
  is structurally outbound-only and does NOT introduce any
  always-online endpoints — preserves the deferral cleanly.
- **Per-user timezone override** — REQUIREMENTS.md `Out of Scope`.
  `DiscordTimestamps` reads `app.timezone` from `application.yml`;
  there is no per-user override surface in Phase 93+.
- **Caffeine adoption** — only if the hand-rolled cache impl
  surfaces concurrency / TTL / eviction bugs during Phase 94-97
  use; otherwise zero-new-deps wins.

</deferred>

---

*Phase: 93-discord-foundation*
*Context gathered: 2026-05-21*
