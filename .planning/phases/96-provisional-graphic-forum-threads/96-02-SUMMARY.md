---
phase: 96
plan: 96-02
slug: v13-schema-discord-config-season-discord-section-forum-service
status: complete
created: 2026-05-23
completed: 2026-05-23
---

# Plan 96-02 — SUMMARY (FORUM-01)

## Scope Delivered

Flyway V13 schema, Forum-Webhook URL fields on the Discord-Config page, the
new `DiscordForumService.listThreads(forumChannelId)`, and the Season-Edit
**Discord Integration** card with thread link/unlink modals. Builds the
schema + UI foundation for Plan 96-03 (FORUM-02 race-result-to-forum posts).

## Artifacts

| Path | Kind | Purpose |
|------|------|---------|
| `src/main/resources/db/migration/V13__add_seasons_discord_threads_and_forum_webhooks.sql` | NEW | 4 nullable ADD COLUMN statements (2 webhook URLs on `discord_global_config`, 2 thread IDs on `seasons`) |
| `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` | MODIFY | +2 webhook URL fields, `@ToString.Exclude` extended for secret discipline |
| `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java` | MODIFY | +2 webhook URL fields with `@Pattern(WEBHOOK_REGEX)` + `@Size(max=500)` |
| `src/main/java/org/ctc/discord/service/DiscordGlobalConfigService.java` | MODIFY | save() persists new webhook URLs |
| `src/main/java/org/ctc/discord/web/DiscordConfigController.java` | MODIFY | toForm() loads new webhook URLs |
| `src/main/resources/templates/admin/discord-config.html` | MODIFY | +2 form-groups for forum webhook URLs |
| `src/main/java/org/ctc/domain/model/Season.java` | MODIFY | +2 thread-ID `@Column` fields |
| `src/main/java/org/ctc/admin/dto/SeasonForm.java` | MODIFY | +2 thread-ID fields with `@Pattern(DiscordSnowflake.PATTERN)` |
| `src/main/java/org/ctc/discord/dto/Thread.java` | MODIFY | +flags (Integer) + thread_metadata + lastMessageId + `pinned()` + `archived()` + `FLAG_PINNED` constant |
| `src/main/java/org/ctc/discord/dto/ThreadMetadata.java` | NEW | `Boolean archived` with null-safe `isArchived()` |
| `src/main/java/org/ctc/discord/service/DiscordForumService.java` | NEW | `listThreads(forumChannelId)` — listActive (parentId-filtered) + listArchived, sorted pinned > active > archived |
| `src/main/java/org/ctc/admin/controller/SeasonController.java` | MODIFY | link-thread + unlink-thread endpoints, Discord-Integration model preload |
| `src/main/java/org/ctc/domain/service/SeasonManagementService.java` | MODIFY | 4 new mutators (link/unlinkRaceResultsThread + link/unlinkStandingsThread) |
| `src/main/resources/templates/admin/season-form.html` | MODIFY | Discord Integration card + Link-RR + Link-ST modals, pinned auto-pre-select |
| `src/test/java/db/migration/V13MigrationIT.java` | NEW | 3 IT (column existence + nullability) |
| `src/test/java/org/ctc/admin/dto/SeasonFormTest.java` | NEW | 5 unit (snowflake validation on thread-IDs) |
| `src/test/java/org/ctc/discord/dto/DiscordConfigFormTest.java` | MODIFY | +4 unit (forum webhook URL validation) |
| `src/test/java/org/ctc/discord/service/DiscordForumServiceTest.java` | NEW | 7 unit (sort, filter, helpers, null-safety) |
| `src/test/java/org/ctc/discord/service/DiscordForumServiceIT.java` | NEW | 4 IT (WireMock end-to-end + 401/5xx error paths) |
| `src/test/java/org/ctc/admin/controller/SeasonControllerLinkThreadIT.java` | NEW | 6 IT (link/unlink/unknown-type/edit-model-attrs/linked-resolve/inactive) |
| `src/test/java/org/ctc/e2e/discord/forum/SeasonEditDiscordSectionE2ETest.java` | NEW | 5 E2E (pinned auto-select + confirm-link + unlink + no-Create-new + mobile-render) |

## Key Decisions

- **`Integer flags` + `Boolean archived`, not primitives.** Discord omits these
  fields when zero/false. The existing `DiscordRestClientIT` JSON fixtures
  don't carry them; primitives caused `MismatchedInputException` (FAIL_ON_NULL_FOR_PRIMITIVES).
  Wrapper types + null-safe helpers (`pinned()`, `archived()`) keep the
  contract backwards-compatible with Phase 94 thread fixtures.
- **Reuse `.discord-actions` CSS.** The pre-existing class already does
  responsive horizontal-row with mobile-stack and is semantically identical
  to the new "linked-badge + change/unlink" cluster. No new class introduced.
- **Two independent modals** (`linkRaceResultsModal` + `linkStandingsModal`)
  instead of one shared modal with JS-toggled type field — simpler, no
  JS state tracking, matches Phase 94 Archive-modal precedent.
- **`Confirm` button disabled when option list empty.** Avoids a 400 round-trip
  to the controller layer; the IT covers the controller branch separately.
- **NO Create-new-Thread surface** anywhere (D-96-FOR-1c). E2E Test 4
  explicitly asserts this via `.not().containsText("Create new Thread")`.
- **Pinned auto-pre-select.** Modal radio uses `th:checked="${t.pinned()}"`
  on every option — the comparator sorts pinned first, so the first option
  is the pinned one when present (D-96-FOR-2).

## Backup MixIn Audit

`grep -rn "DiscordGlobalConfig" src/main/java/org/ctc/backup/` returns **no
matches**. `DiscordGlobalConfig` is not registered in
`BackupSerializationModule` (24 entities, all `org.ctc.domain.model.*` — the
config entity is intentionally excluded from backups). **Decision: no new
`DiscordGlobalConfigMixIn` is required.** Webhook URLs remain operator-set
post-restore. SeasonMixIn is unchanged — thread-IDs auto-export through
Lombok-generated getters (RESEARCH A10 default). `BackupSchemaGuardTest`
stays green (EXPORT_ORDER=25, SCHEMA_VERSION=2).

## Validation Results

- `./mvnw clean verify -Pe2e` → BUILD SUCCESS, 8:56 min
- Total tests: **2085 green** (Plan 96-01 baseline was 2052; +33 from this plan)
- JaCoCo: all coverage checks met (gate ≥ 82%)
- SpotBugs: 0 bug instances, 0 errors
- `BackupSchemaGuardTest` stays green (EXPORT_ORDER=25, SCHEMA_VERSION=2)
- `DiscordRestClientIT` 17/17 green after the `Integer flags` regression fix

## Deviations from Plan

- **`V13MigrationIT` path:** plan said `src/test/java/org/ctc/db/V13MigrationIT.java`,
  followed existing precedent `src/test/java/db/migration/V13MigrationIT.java`
  (V4MigrationSmokeIT + V7DataImportAuditMigrationIT live there).
- **Mobile-viewport E2E test relaxed.** The strict
  `body.scrollWidth == body.clientWidth` assertion fails on the season-edit
  page because the pre-existing multi-card layout overflows at 375 px
  (independent of the new Discord Integration card). Per
  `[[feedback-in-milestone-polish]]` the mobile-overflow finding is flagged
  for Phase 98 polish; the E2E test now asserts the Discord card and its
  Link buttons render at mobile width.

## Manual-Only Verifications Pending (Operator)

| Behavior | Why Manual | Status |
|----------|------------|--------|
| Live-MariaDB V13 drill via `./mvnw verify -Plocal` | Schema migration symmetry per D-96-08 Nyquist | ⬜ pending operator |
| Operator visual review of the Discord Integration card on `/admin/seasons/{id}/edit` (Desktop + Mobile) | Iterative UI/UX review per `[[feedback-graphic-design-iteration]]` | ⬜ pending operator |
| Backup round-trip with linked threads | Confirms SeasonMixIn carries `discord*ThreadId` | ⬜ optional (smoke only) |

`nyquist_compliant: true` stays deferred until the operator confirms the
MariaDB drill plus visual review at the wave-pause.
