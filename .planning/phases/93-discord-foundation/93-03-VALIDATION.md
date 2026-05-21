---
phase: 93
plan: 03
slug: discord-foundation
status: shipped
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-21
---

# Plan 93-03 — Validation Slice

> Per-plan slice of `93-VALIDATION.md` per CONTEXT D-08.
> Substance: 7 rows 93-03-01..07 covering INFRA-03 admin discord-config page + V8 migration + 4 test/refresh buttons.

---

## Sampling Rate

- **Per-task compile gate** (after every implementation task): `./mvnw -q clean test-compile` (~10 s)
- **Per-test-class gate** (after each TDD pair): `./mvnw test -Dtest=<ClassName>` for unit, `./mvnw failsafe:integration-test failsafe:verify -Dit.test=<ClassName>` for ITs (~10–40 s)
- **Per-plan E2E gate** (Task 5): `./mvnw -Pe2e -DfailIfNoTests=false -Dtest=NoSuchTest -Dit.test=DiscordConfigPageE2ETest failsafe:integration-test failsafe:verify` (~32 s, 5 methods)
- **Per-plan full gate** (Task 7): `./mvnw clean verify -Pe2e` (full pipeline incl. JaCoCo + SpotBugs + Playwright E2E)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 93-03-01 | 03 | 1 | INFRA-03 | T-93-02 | V8 schema applies on H2 + MariaDB; seed row deterministic (single row, empty defaults + `vs_emoji_name='CTC'`). Entity in `org.ctc.discord.model` (outside `org.ctc.domain.model.*` filter). `@ToString(exclude = {"announcementWebhookUrl"})` on entity. Repository exposes only `findFirstByOrderByIdAsc`. Service `getOrInitialize/save` is `@Transactional` with field-by-field mutation. `DiscordConfigForm` mass-assignment-safe (no entity refs, no id setter). | unit + IT (compile + Flyway) | `./mvnw -q clean test-compile`; `./mvnw failsafe:integration-test failsafe:verify -Dit.test=DiscordGlobalConfigRepositoryIT` → 3/3 pass | ✅ | ✅ green |
| 93-03-02 | 03 | 1 | INFRA-03 | T-93-02, T-93-MA | RED `DiscordGlobalConfigGuardTest` (EXPORT_ORDER=24 + package guard) + `DiscordGlobalConfigToStringTest` (toString excludes webhook URL) + `DiscordConfigFormTest` (Jakarta Validation per field) drive the contracts. | RED tests | `./mvnw test -Dtest='DiscordGlobalConfigGuardTest,DiscordGlobalConfigToStringTest,DiscordConfigFormTest'` → 10/10 pass after GREEN | ✅ | ✅ green |
| 93-03-03 | 03 | 1 | INFRA-03 | T-93-CSRF, T-93-MA | `DiscordConfigController`: GET view + POST `/save` (with `@Valid` + `BindingResult` + flash) + 4 POST buttons (Test Connection, Test Webhook, Refresh Roles, Refresh Emoji). Typed-catch on 4 DiscordApiException permits → `errorCategory` flash via `Category.name().toLowerCase().replace('_','-')`. No `e.getMessage()` echo. Controller is the SOLE wiring between `DiscordRestClient.fetchGuildEmojis` and `DiscordEmojiCache.refresh`. Thymeleaf template: 6 fields + `.badge-warning` "not configured" badges + 4 buttons with `th:disabled` on prerequisite field. New "Integrations" sidebar group. New `.error-badge--category-full` BEM CSS variant. | unit + IT | `grep -c 'e\.getMessage' src/main/java/org/ctc/discord/web/DiscordConfigController.java` returns 0; `grep -c 'emojiCache.refresh' src/main/java/org/ctc/discord/web/DiscordConfigController.java` returns ≥ 1; `grep -c 'style=' src/main/resources/templates/admin/discord-config.html` returns 0; `grep -c '\.error-badge--category-full' src/main/resources/static/admin/css/admin.css` returns 1 | ✅ | ✅ green |
| 93-03-04 | 03 | 1 | INFRA-03 | all | Controller unit + IT + parameterized error-category mapping cover: save-success / binding-error / 4 typed exceptions / Test Connection success / refresh-emoji empty-guildId guard + happy / test-webhook empty-URL guard / refresh-roles happy / view attributes. Comment-pollution sweep removed all phase/threat-ID Javadocs and comments per [[feedback_no_unnecessary_comments]] recurrence on 2026-05-21. | unit + IT | `./mvnw test -Dtest='DiscordConfigControllerTest,DiscordConfigControllerErrorCategoryTest'` → 16/16 pass; `./mvnw failsafe:integration-test failsafe:verify -Dit.test=DiscordConfigControllerIT` → 5/5 pass | ✅ | ✅ green |
| 93-03-05 | 03 | 1 | INFRA-03 | all | Playwright E2E covers Desktop + Mobile rendering, form fill + save persistence, Test Connection with WireMock 200 stub, disabled-button state for empty guildId/webhook. `@DynamicPropertySource` overrides `app.discord.base-url` to WireMock; `@BeforeEach` resets `discord_global_config` to empty defaults so tests are order-independent. `PlaywrightConfig` fields elevated to `protected` so subpackage E2E can extend. | E2E (5 methods) | `./mvnw -Pe2e -DfailIfNoTests=false -Dtest=NoSuchTest -Dit.test=DiscordConfigPageE2ETest failsafe:integration-test failsafe:verify` → 5/5 pass (~32 s) | ✅ | ✅ green |
| 93-03-06 | 03 | 1 | INFRA-03 | all | Visual UAT checkpoint — operator-driven inspection on dev profile. Deferred to STATE.md UAT-03 entry per CONTEXT D-01 (operator runs Live-Discord UAT BEFORE Phase 94 CHAN-02 starts). | manual UAT | Operator follows STATE.md UAT-03 procedure | ⬜ | ⬜ pending (operator-driven) |
| 93-03-07 | 03 | 1 | INFRA-03 | all | Full pipeline GREEN: Surefire + Failsafe + Playwright E2E + JaCoCo + SpotBugs. JaCoCo line coverage ≥ 88.88 %; SpotBugs `BugInstance` count 0; BackupSchemaGuardTest at `EXPORT_ORDER.length == 24`; Flyway V8 applied on H2 (dev test) and on MariaDB (local profile IT if run). Branch identity preserved. | full pipeline | `./mvnw clean verify -Pe2e` exits 0; `git branch --show-current` returns `gsd/v1.13-discord-integration` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `src/main/resources/db/migration/V8__discord_global_config.sql` (Task 1)
- [x] `src/main/java/org/ctc/discord/{model/DiscordGlobalConfig,repository/DiscordGlobalConfigRepository,service/DiscordGlobalConfigService}.java` (Task 1)
- [x] `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` (Task 1)
- [x] `src/test/java/org/ctc/discord/{repository/DiscordGlobalConfigRepositoryIT,repository/DiscordGlobalConfigGuardTest,model/DiscordGlobalConfigToStringTest,dto/DiscordConfigFormTest}.java` (Task 2)
- [x] `src/main/java/org/ctc/discord/web/DiscordConfigController.java` + `src/main/resources/templates/admin/discord-config.html` + `src/main/resources/templates/admin/layout.html` nav + `src/main/resources/static/admin/css/admin.css` `.error-badge--category-full` (Task 3)
- [x] `src/test/java/org/ctc/discord/web/{DiscordConfigControllerTest,DiscordConfigControllerIT,DiscordConfigControllerErrorCategoryTest}.java` + comment-pollution sweep (Task 4)
- [x] `src/test/java/org/ctc/e2e/discord/DiscordConfigPageE2ETest.java` + `PlaywrightConfig` field-visibility elevation (Task 5)
- [⬜] Task 6 visual UAT — deferred to operator (STATE.md UAT-03)
- [x] Task 7 closes with `./mvnw clean verify -Pe2e` GREEN.

---

## Sign-Off

| Field | Value |
|-------|-------|
| Closed by | Plan 93-03 execution (7 tasks; TDD-paired Tasks 2→3 + Tasks 4→5) |
| Closed at | 2026-05-21 |
| Closing commit | recorded in `93-VERIFICATION.md` after Task 7 commit |
| Coverage at close | **89.5397 %** line coverage (8132/9082) on `./mvnw clean verify -Pe2e` 2026-05-21 18:06; discord package 92.12 % (386/419). |
| SpotBugs at close | 0 BugInstance (no new suppressions required — existing `~org\.ctc\.discord\.(dto\|util)\..+` covers DTO records) |
| Threat coverage | T-93-01 (continues mitigated, service logs no webhook URL), T-93-02 (`@ToString.Exclude` on entity + service log discipline + Plan 93-02 Logback mask carries forward), T-93-CSRF (structurally mitigated via inherited Phase 30 SecurityConfig; `prod`/`docker` only — `dev`/`local` no-auth per CLAUDE.md), T-93-MA (DiscordConfigForm has no entity refs, service mutates loaded entity field-by-field). T-93-03 forward-referenced to Phase 94 CHAN-02. |
