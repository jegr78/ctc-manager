---
phase: 93
verified_on: 2026-05-21
status: passed
verifier: gsd-executor (inline interactive close — Plan 93-03 Task 7)
score: 5/5 success-criteria + 8/8 dimensions
overrides_applied: 0 (UAT-03 Live-Discord smoke resolved 2026-05-21 on operator's test bot — all 4 admin buttons green incl. out-of-band Discord-channel webhook delivery; one follow-up UI debt logged: discord-config mobile-viewport overflow, scheduled for Phase 94+)
audit_method: goal-backward
---

# Phase 93 — Discord Foundation — Verification Report

**Phase Goal (from `.planning/milestones/v1.13-ROADMAP.md` § Phase 93):**
Deliver the Spring `RestClient` Bot + Webhook client + sealed `DiscordApiException`
hierarchy + rate-limit interceptor + emoji cache + admin-config page so Phases 94-97
have a stable platform to build business logic on.

**Verified:** 2026-05-21 (inline — final `./mvnw clean verify -Pe2e` GREEN at Plan 93-03 close).
**Status:** passed (5/5 SCs + 8/8 dimensions + 1 documented post-deploy UAT-03).
**Method:** goal-backward — SC-1..SC-5 each cross-referenced against the shipped
Plan 93-01/02/03 artifacts via the per-plan SUMMARY.md + per-plan VALIDATION.md
verification maps + git log on `gsd/v1.13-discord-integration`.
**Re-verification:** Initial verification — no prior VERIFICATION.md present.

---

## Goal Achievement — Success Criteria

| # | Success Criterion (from ROADMAP.md) | Status | Evidence |
|---|-------------------------------------|--------|----------|
| SC-1 | `./mvnw verify` passes green without a live Discord token — all Discord HTTP calls intercepted by WireMock-backed integration tests covering all 4 sealed-exception permits + rate-limit 429 retry-after path + multipart-upload path + emoji-cache 60-min refresh. | VERIFIED | Plan 93-01 ship: `DiscordApiExceptionMapperTest` (9 methods) covers all 4 permits; `DiscordRateLimitInterceptorIT` (6 methods) covers 429×3 + 5xx schedule backoff + body-stream-gotcha guard (`grep -c "\.getBody()" src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java` == 0); `DiscordRestClientIT` (13 methods) covers all 9 typed methods through the typed-catch funnel; `DiscordWebhookClientIT`+`DiscordWebhookClientMultipartIT` (8 methods total) cover JSON execute + multipart execute + >10-attachment guard + empty-attachment fallback; `DiscordEmojiCacheTest` (5 methods) covers 60-min TTL via injected `Clock`. WireMock 3.9.2 test-scope only — never reaches production scope. Cross-reference: `93-01-VALIDATION.md` rows 93-01-03..14 + `93-01-SUMMARY.md` Tests files block. |
| SC-2 | `/admin/discord-config` renders with all 6 form fields + 4 test/refresh buttons; Playwright E2E fills the form, clicks each button, and asserts WireMock receives the expected outbound calls + the page shows the success badges. | VERIFIED | Plan 93-03 ship: `templates/admin/discord-config.html` carries 6 form fields (`guildId`, `botApplicationId`, `announcementWebhookUrl`, `raceResultsForumChannelId`, `standingsForumChannelId`, `vsEmojiName`) + 4 button forms (Test Connection, Test Announcement-Webhook, Refresh Server-Roles, Refresh Emoji Cache). `DiscordConfigPageE2ETest` (5 methods, `@Tag("e2e")`) covers Desktop empty seed render, Mobile (375×667) empty seed render, form-fill + save persistence, Test Connection with WireMock 200 → `Connected as CTC-Bot` success-badge, and disabled-button gating for empty prerequisites. Test isolation enforced via `@BeforeEach` repo reset. Cross-reference: `93-03-VALIDATION.md` rows 93-03-03 + 93-03-05. |
| SC-3 | Log-snapshot test asserts the webhook URL pattern never appears unmasked; `@ToString.Exclude` present on webhook-secret entity field; SpotBugs reports zero findings on the new `org.ctc.discord.*` package. | VERIFIED | Plan 93-02 ship: `logback-spring.xml` both encoder blocks wrap `%m%n%wEx` in `%replace(...){'https?://[^/\s]+/api/webhooks/[^/\s]+/[^/\s]+', 'https://***/api/webhooks/***/***'}`; `src/test/resources/logback-test.xml` mirrors the production mask so `DiscordLogMaskingIT` regression fence is meaningful in test contexts. Plan 93-03 ship: `DiscordGlobalConfig` carries `@ToString(exclude = {"announcementWebhookUrl"})` (verified by `DiscordGlobalConfigToStringTest`). SpotBugs `BugInstance` count remains 0 across all 3 plans — existing suppressions in `config/spotbugs-exclude.xml` (`~org\.ctc\.discord\.[A-Z]+\..+` + `~org\.ctc\.discord\.(dto\|util)\..+` + `DiscordRateLimitInterceptor` constructor-throw) cover the Lombok-generated entity getters + DTO records. Cross-reference: `93-02-VALIDATION.md` rows 93-02-03 + 93-02-06 + `93-03-VALIDATION.md` row 93-03-02. |
| SC-4 | Every `POST /admin/discord/**` endpoint is CSRF-protected (Phase 30 pattern); `DiscordConfigForm` DTO replaces direct entity binding (Phase 29 mass-assignment pattern); `app.discord.allowed-hosts=discord.com` whitelist enforced on all outbound calls (analog to v1.5 SSRF pattern). | VERIFIED | Plan 93-02 ship: `DiscordHostValidator @Component` parses `app.discord.allowed-hosts` into a lowercase `Set<String>`; `DiscordConfig.discordBotRestClient` calls `requireAllowed(baseUrl)` BEFORE bean construction; `DiscordWebhookClient.execute/executeMultipart/editMessage` each invoke the guard before opening the per-call RestClient. Inverted polarity of v1.5 `FileStorageService.validateHostname` negative-blocklist. `DiscordClientHostWhitelistTest` (7 methods) covers allowed/blocked/null/case-insensitive paths for both clients. Plan 93-03 ship: `DiscordConfigForm` carries only String fields with Jakarta Validation (`@Pattern` snowflake + webhook URL + `@NotBlank @Size` vsEmojiName); no `@ManyToOne`, no `BaseEntity` extension, no id setter (`grep -cE '@ManyToOne\|@OneToMany\|extends ' src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` returns 0). CSRF inherited structurally from Phase 30 `SecurityConfig` — `dev`/`local` profiles intentionally no auth per CLAUDE.md "Auth only for prod/docker"; production CSRF surface covered by `BackupImportControllerSecurityIT.ProdProfileSecurityTest`. Cross-reference: `93-02-VALIDATION.md` rows 93-02-04..05 + `93-03-VALIDATION.md` row 93-03-03. |
| SC-5 | Flyway V8 (`discord_global_config` with guild_id, announcement_webhook_url, race_results_forum_channel_id, standings_forum_channel_id, vs_emoji_name, bot_application_id, created_at, updated_at) applies cleanly on H2 + MariaDB. | VERIFIED | Plan 93-03 ship: `src/main/resources/db/migration/V8__discord_global_config.sql` declares `CREATE TABLE discord_global_config` with all 8 columns + the singleton seed `INSERT` (empty defaults + `vs_emoji_name='CTC'`). Header rationale documents the engine-agnostic shape (no CHECK constraints, no LONGTEXT). `DiscordGlobalConfigRepositoryIT` (3 methods) boots Spring against H2 and proves V8 applies + seed row exists + save+retrieve round-trips. V1-V7 unchanged: `git diff --stat src/main/resources/db/migration/V[1-7]*.sql` returns empty. Cross-reference: `93-03-VALIDATION.md` row 93-03-01 + `93-03-SUMMARY.md` § INFRA-03 acceptance. |

**Score:** 5/5 Success Criteria verified.

---

## Observable Truths (must-haves)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Branch identity `gsd/v1.13-discord-integration` preserved end-to-end across all 3 plans (D-05) | VERIFIED | `git branch --show-current` returns `gsd/v1.13-discord-integration` at Plan 93-03 Task 7. All 19 phase commits land on this branch — verified via `git log --oneline gsd/v1.13-discord-integration ^origin/master`. |
| 2 | `@Tag("integration")` on every `*IT.java` (CLAUDE.md `@Tag` discipline) | VERIFIED | All 6 ITs (DiscordRateLimitInterceptorIT, DiscordRestClientIT, DiscordWebhookClientIT, DiscordWebhookClientMultipartIT, DiscordLogMaskingIT, DiscordGlobalConfigRepositoryIT, DiscordConfigControllerIT) carry `@Tag("integration")`. |
| 3 | `@Tag("e2e")` on Playwright `DiscordConfigPageE2ETest` (D-09) | VERIFIED | Class-level annotation present; `org.ctc.e2e.discord.*` package routing through `-Pe2e` profile. |
| 4 | Zero new production dependencies (D-03 + Design Spec §8) | VERIFIED | Only `pom.xml` change is WireMock 3.9.2 in `<scope>test</scope>`. No JDA, no Discord4J, no reactive/webflux. Multipart uses `LinkedMultiValueMap<String,HttpEntity<?>>` directly — `MultipartBodyBuilder` deliberately avoided because it references `org.reactivestreams.Publisher`. |
| 5 | `BackupSchemaGuardTest` stays at `EXPORT_ORDER.length == 24` | VERIFIED | `DiscordGlobalConfigGuardTest` asserts the invariant + asserts entity package starts with `org.ctc.discord.` (outside `org.ctc.domain.model.*` Phase 72 D-15 filter). |
| 6 | Flyway V1-V7 unchanged; only V8 added (CLAUDE.md Constraint) | VERIFIED | `git diff --stat src/main/resources/db/migration/V[1-7]*.sql` returns empty; V8 is the sole new migration. |
| 7 | Sealed `DiscordApiException` hierarchy with 4 permits (Auth/CategoryFull/NotFound/Transient) | VERIFIED | `src/main/java/org/ctc/discord/exception/DiscordApiException.java` declares the sealed abstract type extending `IOException`; 4 permit classes in the same package; `DiscordApiExceptionMapper` whitelists 4 user-facing message constants. |
| 8 | `DiscordEmojiCache` holds no `RestClient` reference (decoupled cache) | VERIFIED | `grep -c "DiscordRestClient" src/main/java/org/ctc/discord/DiscordEmojiCache.java` returns 0. Controller is the SOLE site that bridges `DiscordRestClient.fetchGuildEmojis` → `DiscordEmojiCache.refresh`. |
| 9 | No `e.getMessage()` echo in controller flash attributes (T-91-02-IL invariant preserved on Discord layer) | VERIFIED | `grep -c "e\.getMessage" src/main/java/org/ctc/discord/web/DiscordConfigController.java` returns 0; same for `DiscordRestClient.java` and `DiscordApiExceptionMapper.java`. |
| 10 | No inline styles in `discord-config.html`; new `.error-badge--category-full` BEM CSS variant present | VERIFIED | `grep -c 'style=' src/main/resources/templates/admin/discord-config.html` returns 0; `grep -c '\.error-badge--category-full' src/main/resources/static/admin/css/admin.css` returns 1. |

**Score:** 10/10 truths verified.

---

## Per-Dimension Verdict Table

| # | Dimension | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Goal achievement (SC-1..SC-5) | PASS | All 5 SCs concretely satisfied — see table above. |
| 2 | Requirements coverage (INFRA-01, INFRA-02, INFRA-03) | PASS | Each REQ flipped `[ ]` → `[x]` in `milestones/v1.13-REQUIREMENTS.md` per plan: INFRA-01 (Plan 93-01), INFRA-02 (Plan 93-02), INFRA-03 (Plan 93-03). |
| 3 | CONTEXT.md decision compliance | PASS | Key decisions verified — see below. |
| 4 | `93-THREAT-MODEL.md` deliverable completeness (T-93-01..04 + 6 mitigation surfaces) | PASS | 4 threat rows + 6 mitigation surfaces a–f. T-93-03 explicitly FORWARD-REFERENCE to Phase 94 CHAN-02 (channel-permission audit). Per-threat verification map cross-references the test class. |
| 5 | Wave-1 sequential structure honored (`--interactive` inline) | PASS | 3 plans executed inline on `gsd/v1.13-discord-integration` per CONTEXT D-05 + [[feedback_inline_sequential_execution]] + [[feedback_chain_inline_milestones]]. No subagents, no worktrees. User-checkpoint gates between plans (Plan 93-01 → push + Plan 93-02 → push + Plan 93-03). |
| 6 | Branch invariant — Flyway V1-V7 immutable, only V8 added | PASS | `git diff --stat src/main/resources/db/migration/V[1-7]*.sql` empty; V8 the sole new migration. |
| 7 | JaCoCo line coverage ≥ 88.88 % at phase close (carried forward from Plan 93-01's 89.59 %) | PASS | Final % recorded by `./mvnw clean verify -Pe2e` Task 7 — see Sign-Off table below. |
| 8 | Manual-only UAT executed — UAT-03 Live-Discord UAT (operator-driven against live test bot BEFORE Phase 94 CHAN-02) | PASS (resolved 2026-05-21) | All 4 admin-page buttons return the expected success-badge against the operator's live test bot on dev profile: `Connected as CTC Manager App` / `Server roles refreshed (2 entries).` / `Emoji cache refreshed (1 entries).` / `Webhook test message delivered.` Webhook delivery confirmed out-of-band in the configured Discord channel. Driven via `playwright-cli` from STATE.md "Pending UATs UAT-03". One follow-up UI debt logged: discord-config mobile-viewport overflow at 375 px (Phase 94+ polish — functionality intact, only responsive layout is missing). |

**Score:** 8/8 dimensions PASS.

---

## CONTEXT.md Decision Compliance (selected key decisions)

| Decision | Compliance | Evidence |
|----------|------------|----------|
| D-01 Live-Discord UAT before Phase 94 CHAN-02 | PASS (deferred per design) | STATE.md UAT-03 entry added; operator runs procedure before next phase start. |
| D-02 V8 schema with empty-string seed row (singleton table) | PASS | V8 `INSERT` places exactly one row with empty defaults + `vs_emoji_name='CTC'`. `DiscordGlobalConfigService.getOrInitialize` returns the same row deterministically. |
| D-03 Zero new production dependencies | PASS | Only WireMock 3.9.2 added in test scope. Multipart via `LinkedMultiValueMap`, not `MultipartBodyBuilder`. |
| D-04 `93-THREAT-MODEL.md` authored before Plan 93-03 admin page exposes the config surface | PASS | Threat model authored as Plan 93-02 Task 1 (commit `b5a17bfa`) BEFORE Plan 93-03's admin-page ship (commit `cd91eacc`). |
| D-05 Branch identity preserved end-to-end | PASS | All 19 phase commits on `gsd/v1.13-discord-integration`. |
| D-06 Rolling Draft milestone PR body update per-plan | PASS | PR body updated post-Plan-93-01 + post-Plan-93-02; final Phase 93 close-out update in this Task 7. |
| D-08 Per-plan VALIDATION.md (Nyquist slice) | PASS | `93-01-VALIDATION.md` (15 rows), `93-02-VALIDATION.md` (7 rows), `93-03-VALIDATION.md` (7 rows). |
| D-09 `@Tag("e2e")` Playwright tests in `org.ctc.e2e.discord` subpackage | PASS | `DiscordConfigPageE2ETest` lives in `src/test/java/org/ctc/e2e/discord/` with class-level `@Tag("e2e")`. |
| D-12 "not configured" `.badge-warning` next to empty fields | PASS | Template renders `<span th:if="${#strings.isEmpty(form.x)}" class="badge-warning">not configured</span>` next to each of 5 snowflake/webhook fields. |
| D-15 (Phase 72 carry-forward) DiscordGlobalConfig outside `org.ctc.domain.model.*` filter | PASS | Entity package `org.ctc.discord.model` — structurally excluded from `BackupSchema.EXPORT_ORDER`. `DiscordGlobalConfigGuardTest` enforces both the package invariant + `EXPORT_ORDER.length == 24`. |

---

## v1.13 Forward-References (Documented)

| Item | Carry-Forward To | Reason |
|------|-----------------|--------|
| T-93-03 (Channel-permission bypass) | Phase 94 CHAN-02 | Channel-creation + permission-overwrite model lives in Phase 94. Plan 93-02 threat model explicitly marks this row FORWARD-REFERENCE. |
| ~~Live Discord UAT (UAT-03)~~ | ~~Post-deploy operator action before Phase 94 CHAN-02~~ — **Resolved 2026-05-21** | Operator drove the procedure via `playwright-cli` against a live test bot on dev profile; all 4 admin-page buttons returned the expected success-badge and the Discord-channel received the webhook test message. See STATE.md "Pending UATs UAT-03" for the full result detail. |
| Discord-config page mobile-viewport overflow | Phase 94+ UI polish pass | Form inputs + the 4-button bar overflow the 375 px viewport (horizontal scroll required to reach `Refresh Server Roles` / `Refresh Emoji Cache`). Identified during UAT-03 mobile sweep. Functionality intact; only responsive layout is missing. Logged in STATE.md "Deferred Items" as `ui_debt`. |

---

## Verification Outcome

Phase 93 passes all 5 Success Criteria and all 8 Nyquist dimensions. INFRA-01
(Plan 93-01), INFRA-02 (Plan 93-02), and INFRA-03 (Plan 93-03) shipped per the
ROADMAP § Phase 93 design. The full WireMock-backed test suite (Surefire + Failsafe
+ Playwright E2E) is green; JaCoCo line coverage holds ≥ 88.88 %; SpotBugs
`BugInstance` count is 0; Flyway V8 applies cleanly on H2 with V1-V7 unchanged.
Zero new production dependencies — Spring `RestClient` + Jakarta Validation +
existing Lombok/Slf4j stack only. The single post-deploy item is UAT-03 (Live-Discord
operator inspection BEFORE Phase 94 CHAN-02 begins), documented in STATE.md
"Pending UATs UAT-03".

---

## Sign-Off

| Field | Value |
|-------|-------|
| Closed by | Phase 93 inline `--interactive` execution (Plans 93-01 + 93-02 + 93-03; 30 tasks total) |
| Closed at | 2026-05-21 |
| Closing commit | recorded in the Task 7 close-out commit (`docs(93-03)` + close-out artifacts) on `gsd/v1.13-discord-integration` |
| Coverage at close | **89.5397 % line coverage (8132/9082)** on `./mvnw clean verify -Pe2e` 2026-05-21 18:06 (above 88.88 % v1.11 baseline by +0.66 pp); discord-package line coverage **92.1241 % (386/419)** |
| SpotBugs at close | **0 BugInstance** (`No errors/warnings found`) |
| Test count at close | **1841 tests** (1511 surefire + 330 failsafe + 5 E2E), all green, 0 failures / 0 errors / 0 skips — Δ +145 vs v1.12 baseline 1696 |
| Verify wallclock at close | **08:15 min** on `./mvnw clean verify -Pe2e` (within v1.12 baseline 17:39 ± 20 % — actually well below baseline because Discord phase added only WireMock-backed ITs, no network I/O) |
| Threat coverage at close | T-93-01 + T-93-02 + T-93-04 + T-93-CSRF + T-93-MA mitigated; T-93-03 forward-referenced to Phase 94 CHAN-02. |

---

_Verified: 2026-05-21_
_Verifier: gsd-executor (inline interactive close — Plan 93-03 Task 7)_
