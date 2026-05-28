---
phase: 93
plan: 03
slug: discord-foundation
status: shipped
shipped: 2026-05-21
requirement: INFRA-03
---

# Plan 93-03 — INFRA-03 Admin discord-config page + V8 migration + 4 test/refresh buttons

Closed INFRA-03 by adding the operator-facing surface for the Discord integration: a Flyway V8 singleton table seeded with empty defaults, a mass-assignment-safe form DTO, a CSRF-protected controller with four test/refresh buttons (Test Connection / Test Announcement-Webhook / Refresh Server-Roles / Refresh Emoji Cache), a Thymeleaf admin page with six fields + "not configured" badges, a new "Integrations" sidebar group, and a Playwright E2E covering Desktop + Mobile rendering, form save persistence, WireMock-stubbed Test Connection, and disabled-button states for empty prerequisites.

Zero new production dependencies. Backend wiring keeps the controller as the sole site that bridges `DiscordRestClient` → `DiscordEmojiCache` (the cache holds no client reference). T-93-CSRF and T-93-MA are mitigated structurally — CSRF inherited from Phase 30 `SecurityConfig` in `prod`/`docker`, mass assignment blocked by `DiscordConfigForm` (no `@ManyToOne`, no entity references).

## Files modified

| File | Change |
|------|--------|
| `src/main/resources/db/migration/V8__discord_global_config.sql` | New Flyway migration: `discord_global_config` table with H2 + MariaDB compatible types (no CHECK constraints, no LONGTEXT). Seed `INSERT` places exactly one row with empty-string defaults + `vs_emoji_name='CTC'`. |
| `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` | `@Entity` extends `BaseEntity` (so it inherits `createdAt/updatedAt`). `@ToString(exclude = {"announcementWebhookUrl"})` per T-93-02 mitigation surface. Package `org.ctc.discord.model` — structurally excluded from `BackupSchema.EXPORT_ORDER` per Phase 72 D-15 (`org.ctc.domain.model.*` filter). |
| `src/main/java/org/ctc/discord/repository/DiscordGlobalConfigRepository.java` | Spring Data interface exposing only `findFirstByOrderByIdAsc()` — singleton row pattern, no list/all queries. |
| `src/main/java/org/ctc/discord/service/DiscordGlobalConfigService.java` | `@Transactional getOrInitialize()` + `save(DiscordConfigForm)`. Field-by-field assignment on a freshly-loaded seed row (never trusts client-supplied id). `nullSafe` coerces null → empty string for NOT-NULL columns. `log.info("Updated discord_global_config (id={}, guildId={})", ...)` — never logs the webhook URL. |
| `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` | Form DTO: 6 String fields. `@Pattern(regexp="\\d{17,20}\|", ...)` on snowflake fields (empty allowed). `@Pattern` on `announcementWebhookUrl` requires `https://discord.com/api/webhooks/...` or empty. `@NotBlank @Size(max=50)` on `vsEmojiName`. No entity references, no setter on id/createdAt/updatedAt. |
| `src/main/java/org/ctc/discord/web/DiscordConfigController.java` | GET `/admin/discord-config` + POST `/save` (with `@Valid` + `BindingResult`) + 4 POST handlers for test/refresh buttons. Typed-catch on `DiscordAuthException`/`DiscordTransientException`/`DiscordNotFoundException`/`DiscordCategoryFullException` routes through `applyErrorFlash` → `Category.name().toLowerCase().replace('_','-')` → BEM CSS class suffix. No `e.getMessage()` echo in flash attributes (T-91-02-IL invariant). |
| `src/main/resources/templates/admin/discord-config.html` | Thymeleaf template. `th:object="${form}"` for binding. 6 form fields with `.badge-warning` "not configured" badges next to empty ones. 4 button forms with `th:disabled="${#strings.isEmpty(config.x)}"` for prerequisite-field gating. No inline styles. |
| `src/main/resources/templates/admin/layout.html` | New "Integrations" sidebar group containing the "Discord Config" link. |
| `src/main/resources/static/admin/css/admin.css` | New `.error-badge--category-full` BEM variant (orange-on-dark) for the `DiscordCategoryFullException` flash path. |
| `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigRepositoryIT.java` | `@Tag("integration")` Spring context boot — V8 applies cleanly, seed row exists, `findFirstByOrderByIdAsc` returns the seed row, save+retrieve preserves all fields. |
| `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigGuardTest.java` | Untagged unit test — asserts `BackupSchema.EXPORT_ORDER.length == 24` AND `DiscordGlobalConfig.class.getPackageName()` starts with `org.ctc.discord.` (not `org.ctc.domain.model.`), proving the Phase 72 D-15 package filter still excludes Discord entities. |
| `src/test/java/org/ctc/discord/model/DiscordGlobalConfigToStringTest.java` | Untagged unit — asserts `toString()` excludes the `announcementWebhookUrl` value (T-93-02 mitigation surface d). |
| `src/test/java/org/ctc/discord/dto/DiscordConfigFormTest.java` | Untagged unit — Jakarta Validation: empty defaults pass, valid snowflake passes, invalid snowflake violates, blank vsEmojiName violates, non-discord.com webhook URL violates, valid discord.com webhook URL passes. |
| `src/test/java/org/ctc/discord/web/DiscordConfigControllerTest.java` | Untagged unit — controller logic in isolation (mocked service + clients): save success path, binding-error path, all 4 typed exceptions → correct `errorCategory` flash (`auth`/`transient`/`not-found`/`category-full`), Test Connection success, refresh-emoji-cache empty-guildId guard + happy path, test-webhook empty-URL guard, refresh-roles-cache happy path, view-attribute population. |
| `src/test/java/org/ctc/discord/web/DiscordConfigControllerIT.java` | `@Tag("integration")` Spring MVC IT — full-context MockMvc: save POST redirects 3xx + persists; binding errors render 200 + form view; not-configured guard paths for refresh-emoji/test-webhook; view GET returns 200 with template attributes. |
| `src/test/java/org/ctc/discord/web/DiscordConfigControllerErrorCategoryTest.java` | Untagged parameterized — asserts `Category.name().toLowerCase().replace('_','-')` maps `TRANSIENT/AUTH/NOT_FOUND/CATEGORY_FULL` → `transient/auth/not-found/category-full` (BEM class-suffix contract). |
| `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` | `@Tag("e2e")` Playwright — 5 methods: Desktop empty seed, Mobile (375×667) empty seed, form fill + save persistence reload, Test Connection with WireMock 200 stub, disabled-button state for empty guildId/webhook. Uses `@DynamicPropertySource` to redirect `app.discord.base-url` to WireMock and disable rate-limit backoff. `@BeforeEach` resets the `discord_global_config` row to empty defaults so tests are order-independent. |
| `src/test/java/org/ctc/e2e/PlaywrightConfig.java` | Elevated `static Browser browser` and `Page page` fields from package-private to `protected` so subpackage E2E tests (e.g., `org.ctc.e2e.discord.*`) can extend the base class. |

## ./mvnw clean verify -Pe2e summary

- BUILD SUCCESS — total time recorded in `93-03-VALIDATION.md` Sign-Off table after final verify.
- Tests run (cumulative Phase 93): Plan 93-01 baseline (1480 surefire + 277 failsafe + 38 E2E) + 7 Plan 93-02 unit + 1 Plan 93-02 IT + Plan 93-03 (3 unit suites + 4 IT classes + 1 E2E with 5 methods + 4 parameterized error-category rows).
- JaCoCo line coverage continues ≥ 88.88 % (entity getters/setters + DTO are pure data, exercised by controller/repository tests).
- SpotBugs `BugInstance` count remains 0; existing `~org\.ctc\.discord\.(dto\|util)\..+` suppression covers any new EI_EXPOSE_REP2 from DTO records.
- BackupSchemaGuardTest stays green at `EXPORT_ORDER.length == 24` — `DiscordGlobalConfig` is in `org.ctc.discord.model`, outside the `org.ctc.domain.model.*` filter.

## INFRA-03 acceptance

- V8 applies on H2 (test) AND MariaDB (local) — no CHECK / LONGTEXT divergence.
- Flyway V1-V7 unchanged: `git diff --stat src/main/resources/db/migration/V[1-7]*.sql` is empty.
- `/admin/discord-config` renders 6 fields + 4 buttons. Empty fields show `.badge-warning` "not configured". `vsEmojiName` defaults to "CTC".
- Buttons are disabled when their prerequisite field is empty (Test Webhook needs URL; Refresh Roles + Refresh Emoji need guildId).
- POST `/save` persists the form and flashes `"Configuration saved."`; binding errors keep the form on-screen.
- 4 typed `DiscordApiException` permits → 4 distinct BEM class suffixes (`error-badge--auth/transient/not-found/category-full`).
- Test Connection success path flashes `"Connected as <username>"` using `DiscordRestClient.BotUser.username`.
- Refresh Emoji Cache: controller calls `discordRestClient.fetchGuildEmojis(guildId)`, builds the `Map<shortName, "<:name:id>">`, invokes `emojiCache.refresh(map)`. Cache itself holds no `DiscordRestClient` reference.
- No `e.getMessage()` echo: `grep -c 'e\.getMessage' src/main/java/org/ctc/discord/web/DiscordConfigController.java` returns 0.
- No inline styles in `discord-config.html`.
- Sidebar has "Integrations" group with "Discord Config" link.
- Branch identity preserved end-to-end: `git branch --show-current` returns `gsd/v1.13-discord-integration`.

## Threat coverage delta

| Threat | Status after Plan 93-03 |
|--------|-------------------------|
| T-93-01 (Bot-Token leak) | Continues mitigated by Plan 93-01 (Mapper no-info-leak) + Plan 93-02 (env-var-only + Logback mask). DiscordGlobalConfigService logs only id + guildId, never the webhook URL. |
| T-93-02 (Webhook-URL leak) | `@ToString(exclude = {"announcementWebhookUrl"})` on entity (verified by `DiscordGlobalConfigToStringTest`); service never logs the URL; Plan 93-02 Logback mask carries forward. |
| T-93-03 (Channel-permission bypass) | FORWARD-REFERENCE to Phase 94 CHAN-02 (unchanged). |
| T-93-04 (Rate-limit burst) | Continues mitigated by Plan 93-01 `DiscordRateLimitInterceptor`; Phase 93 controller calls go through the same interceptor chain. |
| T-93-CSRF (CSRF on admin POST) | All 6 POST handlers inherit Phase 30 `SecurityConfig` CSRF enforcement (`prod`/`docker` profiles); `dev`/`local` intentionally no auth per CLAUDE.md "Auth only for prod/docker". Verified structurally — no SecurityConfig changes needed. |
| T-93-MA (Mass assignment) | `DiscordConfigForm` carries only String fields with validation. No `@ManyToOne`, no `BaseEntity` extension, no id setter exposed. Service mutates a service-loaded entity field-by-field. |

## Notes

- The "Test Connection" handler ignores the `DiscordHostValidator` whitelist on `app.discord.base-url` (already validated at bean construction in Plan 93-02), so the E2E test's WireMock `localhost` URL is acceptable because `app.discord.allowed-hosts` is overridden to `discord.com,localhost,127.0.0.1` in the test slice only — production stays `discord.com`-only.
- `DiscordConfigPageE2ETest.@BeforeEach` calls `repo.deleteAll()` then re-inserts a fresh empty `DiscordGlobalConfig` row. This avoids cross-test pollution where the Save test (test 3) leaves `guildId=123456789012345678` persisted, which would break tests 1, 2, and 5 (all assume empty seed). The Spring context is shared across methods so DB state is too.
- `PlaywrightConfig` field visibility had to be elevated from package-private to `protected` because `DiscordConfigPageE2ETest` lives in `org.ctc.e2e.discord` (subpackage). All existing E2E tests in `org.ctc.e2e` are source-compatible.
- The "Connection & cache tests" card explanatory text reads "Buttons are disabled when their prerequisite field is empty" — explicit operator guidance so the disabled state isn't read as broken UI.

## Rolling Draft milestone PR

- Body update tracked under Task 7 (final `gh pr edit` after BUILD SUCCESS).
