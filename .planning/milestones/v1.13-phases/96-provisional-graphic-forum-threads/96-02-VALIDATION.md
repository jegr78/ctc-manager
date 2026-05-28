---
phase: 96
plan: 96-02
slug: v13-schema-discord-config-season-discord-section-forum-service
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-23
completed: 2026-05-23
---

# Plan 96-02 — Validation Strategy (FORUM-01)

> Per-plan validation contract specializing `96-VALIDATION.md` for Plan 96-02.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + WireMock + Flyway + Playwright |
| **Quick run command** | `./mvnw test -Dtest=DiscordForumServiceTest` |
| **Wave run command** | `./mvnw verify -Dit.test='DiscordForumServiceIT,SeasonControllerLinkThreadIT,V13MigrationIT'` |
| **Plan-close command** | `./mvnw verify -Pe2e -Dit.test=SeasonEditDiscordSectionE2ETest` |
| **Estimated runtime** | Quick < 30 s · Wave < 16 m · Plan-close < 18 m |

---

## Sampling Rate

- **After every task commit:** Run the touched-class unit test or migration-IT (feedback < 30 s for unit, < 60 s for V13MigrationIT).
- **After each task:** Run `./mvnw verify` (Surefire + Failsafe + JaCoCo, no Playwright) for full IT coverage.
- **Before plan close (wave-pause):** Run `./mvnw verify -Pe2e -Dit.test=SeasonEditDiscordSectionE2ETest` for Playwright Desktop + Mobile sweep.
- **Max feedback latency:** 60 s for task-local quick run; 16 m for wave-level verify.

---

## Per-Task Verification Map

| Task | Test Class | Test Type | Tag | Automated Command | File Exists | Status |
|------|------------|-----------|-----|-------------------|-------------|--------|
| 96-02-01 | `V13MigrationIT` | IT (Flyway H2+MariaDB drill) | integration | `./mvnw verify -Dit.test=V13MigrationIT` | ✅ | ✅ 3/3 green |
| 96-02-01 | `DiscordConfigFormTest` + `SeasonFormTest` | unit (Bean Validation) | untagged | `./mvnw test -Dtest='DiscordConfigFormTest,SeasonFormTest'` | ✅ | ✅ 9 + 5 green |
| 96-02-02 | `DiscordForumServiceTest` | unit (Mockito) | untagged | `./mvnw test -Dtest=DiscordForumServiceTest` | ✅ | ✅ 7/7 green |
| 96-02-02 | `DiscordForumServiceIT` | IT (WireMock) | integration | `./mvnw verify -Dit.test=DiscordForumServiceIT` | ✅ | ✅ 4/4 green |
| 96-02-03 | `SeasonControllerLinkThreadIT` | IT (MockMvc + WireMock) | integration | `./mvnw verify -Dit.test=SeasonControllerLinkThreadIT` | ✅ | ✅ 6/6 green |
| 96-02-03 | `SeasonEditDiscordSectionE2ETest` | E2E (Playwright Desktop + Mobile) | e2e | `./mvnw verify -Pe2e -Dit.test=SeasonEditDiscordSectionE2ETest` | ✅ | ✅ 5/5 green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Validation Dimensions (Nyquist)

| Dimension | Coverage Method | Tests / Artifact |
|-----------|-----------------|------------------|
| **Database — Flyway H2+MariaDB symmetry** | `V13MigrationIT` runs Flyway against H2; grep gate on V13 SQL file enforces NO `LONGTEXT`/`ENGINE=`/`CHECK`/MariaDB-only syntax | `V13MigrationIT` + static grep gate |
| **Code — Form validation** | Bean Validation `@Pattern(WEBHOOK_REGEX)` on 2 new DiscordConfigForm fields; `@Pattern(DiscordSnowflake.PATTERN)` on 2 new SeasonForm fields | `DiscordConfigFormTest` / `SeasonFormTest` (extend if existing; new if absent) |
| **Code — Discord forum thread orchestration** | Mockito unit + WireMock IT verify listActive (parentId filter) + listArchived (channel-scoped) + sort pinned > active-by-lastMessageId > archived-by-lastMessageId | `DiscordForumServiceTest` (sort + filter + pinned fallback) + `DiscordForumServiceIT` (WireMock end-to-end with 3+2 thread mix + empty-forum edge case + error paths) |
| **Code — Controller link/unlink** | MockMvc + WireMock + flash + DB-mutation verification | `SeasonControllerLinkThreadIT` (link + unlink + unknown-type + Discord-Integration model attrs preload) |
| **UI — Season-Edit Discord-Integration card** | Playwright Desktop: card visible, modal opens, pinned auto-pre-select, Confirm submits, Unlink clears DB only | `SeasonEditDiscordSectionE2ETest` |
| **UI — NO Create-new-Thread surface** | Explicit Playwright assertion that no "Create new Thread..." button or link exists (D-96-FOR-1c) | `SeasonEditDiscordSectionE2ETest` Test 11 |
| **Mobile-viewport** | Playwright Mobile sweep at 375 px on Season-Edit + modal | `SeasonEditDiscordSectionE2ETest` Mobile variant |
| **Backup wire-contract** | `BackupSchemaGuardTest` stays green (EXPORT_ORDER=25, SCHEMA_VERSION=2 unchanged); SeasonMixIn auto-exports thread-IDs (Saison-Identity); `DiscordGlobalConfigMixIn` audit decision logged in plan-summary | `BackupSchemaGuardTest`, optional `SeasonRoundTripIT` extension |
| **Static analysis — SpotBugs** | New `DiscordForumService` reuses existing constructor-injection + EI_EXPOSE_REP2 justification patterns | gate-step on `verify` |
| **Static analysis — CodeQL** | No new SSRF suppression expected (forum-webhook URLs use existing DiscordHostValidator | `gh run watch` after PR push |

---

## Wave 0 Requirements (Plan 96-02)

- [x] `V13MigrationIT` created in Task 96-02-01 (3 tests)
- [x] `DiscordForumServiceTest` created in Task 96-02-02 (7 tests)
- [x] `DiscordForumServiceIT` created in Task 96-02-02 (4 tests)
- [x] `SeasonControllerLinkThreadIT` created in Task 96-02-03 (6 tests)
- [x] `SeasonEditDiscordSectionE2ETest` created in Task 96-02-03 (5 tests)

---

## Manual-Only Verifications

| Behavior | Why Manual | Status / Evidence |
|----------|------------|-------------------|
| Live-MariaDB V13 migration | Schema migration symmetry per D-96-08 Nyquist | ✅ 2026-05-23 — `docker compose up --build -d` against existing V12 volume; `flyway_schema_history` shows V13 success=1; `DESCRIBE seasons` confirms `discord_race_results_thread_id`/`discord_standings_thread_id` VARCHAR(32) NULLABLE; `DESCRIBE discord_global_config` confirms `race_results_forum_webhook_url`/`standings_forum_webhook_url` VARCHAR(500) NULLABLE; `/actuator/health` + `/admin/seasons` both 200 |
| Operator visual review of Discord Integration card | Iterative UI/UX review per [[feedback-graphic-design-iteration]] | ✅ 2026-05-23 — playwright-cli session: card renders, race-results + standings modals open with pinned auto-pre-selected radios, Confirm produces "Thread linked." flash + linked state with Change Link + Unlink, Unlink produces "Thread unlinked." flash; mobile-viewport fix (commit `ed8a239b`) brings Discord card to 341 px at viewport 375 px |
| Backup round-trip with thread-IDs | Confirms SeasonMixIn auto-exports `discord*ThreadId` | ⬜ optional smoke check — not blocking; SeasonMixIn unchanged, thread-IDs use Lombok getters (RESEARCH A10 default) |

---

## Plan 96-02 Sign-Off

- [x] All 30 task behaviors verified across the 6 test classes (V13Migration + Forum unit + Forum IT + Controller IT + E2E + form-validation)
- [x] `./mvnw clean verify -Pe2e` exits 0 — 2085 tests green (1604 surefire + 396 failsafe + 85 E2E)
- [x] JaCoCo line coverage gate met (`All coverage checks have been met`)
- [x] BackupSchemaGuardTest stays green (EXPORT_ORDER=25, SCHEMA_VERSION=2 unchanged)
- [x] D-96-FOR-1c assertion-pin in place (`SeasonEditDiscordSectionE2ETest.givenDiscordIntegrationCard_whenInspectingDom_thenNoCreateNewThreadSurface`)
- [x] D-96-FOR-2 assertion-pin in place (`SeasonEditDiscordSectionE2ETest.givenSeasonAndForumChannels_whenOpenRaceResultsModal_thenPinnedAutoSelected`)
- [x] V13 migration H2 path verified via V13MigrationIT (static grep gate green; no LONGTEXT/CHECK/ENGINE=)
- [x] DiscordGlobalConfigMixIn audit decision logged in 96-02-SUMMARY → **N/A** (entity not in BackupSerializationModule, no MixIn required)
- [x] Wave-pause: PR rolling-summary row added for Plan 96-02
- [x] **Operator MariaDB drill** via `docker compose up --build -d` — V13 applies cleanly on top of V12 volume (Flyway success=1; columns exist with correct type + nullability; app healthcheck + `/admin/seasons` both 200)
- [x] **Operator visual review** of the Discord Integration card on `/admin/seasons/{id}/edit` (Desktop + Mobile, including the post-review mobile-overflow fix `ed8a239b`)
- [x] `nyquist_compliant: true` flipped in frontmatter

**Approval:** operator-accepted at wave-pause 2026-05-23 (visual review + Docker-MariaDB drill).
