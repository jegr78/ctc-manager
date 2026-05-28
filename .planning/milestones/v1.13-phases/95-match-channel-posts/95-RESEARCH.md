# Phase 95: Match Channel Posts - Research

**Researched:** 2026-05-22
**Domain:** Discord-Webhook post lifecycle (POST + multipart-PATCH) on the per-match channel platform from Phase 94.
**Confidence:** HIGH (all findings rooted in concrete file inspection of the existing codebase plus the authoritative design spec `docs/superpowers/specs/2026-05-20-discord-integration-design.md`; no training-data extrapolation).

## Summary

Phase 95 wires five per-match post types (TEAM_CARDS / SETTINGS / LINEUPS / SCHEDULE / MATCH_RESULTS) on top of the Phase-94 channel-lifecycle platform. Every post type is persisted as one row in a new `discord_post` table keyed by `(channel_id, post_type, foreign_key)` — the row stores `message_id` so re-posts route through `DiscordWebhookClient` PATCH instead of POST, producing a Discord-side edit-indicator instead of a duplicate message. The reusable pivot is `DiscordPostService.postOrEdit(webhookUrl, type, payload, attachments, ref)` — every button (manual, auto-on-create, auto-on-save) collapses to this single call.

The Phase-93/94 platform already provides everything except the new domain entity + the new multipart-PATCH path: sealed exception hierarchy, 4-category flash-badge palette, multipart-POST `executeMultipart`, JSON-only `editMessage` PATCH, rate-limit interceptor, host-validator, timestamps utility, and per-match webhook URL stored in `matches.discord_channel_webhook_url`. Phase 95 adds Flyway V12, the `DiscordPost` entity (in `org.ctc.discord.model`), the multipart-PATCH method (`editMessageWithAttachments` — ~30 LOC scope-add), the service, the controller-listing page, and 6 new POST endpoints on `MatchController`.

**Primary recommendation:** Plan 95-01 lands the persistence + service-skeleton + multipart-PATCH + list page; 95-02 the Team-Cards hybrid trigger + auto-post hook in `DiscordChannelService.createMatchChannel`; 95-03 the Settings/Lineups multipart-bundle with pre-flight predicates; 95-04 the Match-Results stale-detection + Schedule embed + Match-edit auto-edit hook in `MatchService.updateDiscordFields`. **Critical override of CONTEXT D-95-07:** do NOT bump `BackupSchema.SCHEMA_VERSION` and do NOT add `DiscordPostMixIn` / `DiscordPostRestorer` — the existing `BackupSchema` package filter (`org.ctc.domain.model.*`) structurally excludes `org.ctc.discord.model.*`, and three guard tests (`BackupSchemaGuardTest` × 2, `DiscordGlobalConfigGuardTest`) pin SCHEMA_VERSION = 1 and EXPORT_ORDER = 24 (see Landmines § 1).

## User Constraints (from CONTEXT.md)

### Locked Decisions (verbatim from CONTEXT.md `<decisions>` section)

- **D-95-01: Hybrid Team-Cards trigger** — `DiscordChannelService.createMatchChannel` calls `discordPostService.postTeamCards(match)` at the end (after webhook persistence + permission audit). Match-Detail also shows a permanent Re-Post button.
- **D-95-01a: Auto-Post failure → channel stays + yellow badge + WARN log; no channel rollback.** Channel + webhook stay persisted; flash `errorMessage="Channel created. Team Cards post failed: {category} — click Re-Post Team Cards to retry."` with `errorCategory={transient|auth|not-found}`.
- **D-95-02: Auto-Gen-on-Demand + Refresh-Team-Cards link.** `postTeamCards()` checks `teamCardService.cardExists()`; if missing, calls `generateCard()` synchronously (Playwright ~10–30s/card). Match-Detail shows an additional "↻ Refresh Team Cards" link that combines generate + post in one click.
- **D-95-03: Multipart-Bundle for Settings/Lineups** — 1 post per type with N attachments (`files[0..N-1] = settings-race-{i}.png` / `lineups-race-{i}.png`). Discord allows ≤ 10 attachments / 25 MB.
- **D-95-03a: `DiscordWebhookClient` scope-add — `editMessageWithAttachments(String webhookUrl, String messageId, WebhookPayload payload, List<NamedAttachment> attachments)`.** Multipart-PATCH analog to the existing `executeMultipart` POST.
- **D-95-03b: Pre-Flight strict — Settings/Lineups buttons visible only when all races complete.** Tooltip `"Configure settings/lineups for all races first"` when incomplete; new `errorCategory="data-incomplete"` for the post-attempt-when-incomplete path; new CSS variant `.error-badge--data-incomplete`.
- **D-95-04: `MatchService` pre/post-diff for Schedule auto-edit.** Loads entity, compares `lobbyHost / raceDirector / streamer`, calls `discordPostService.autoEditScheduleIfNeeded(saved)` after save when any of the 3 differ AND a `SCHEDULE` post exists.
- **D-95-04a: Only the 3 Match-Schedule fields trigger; `Race.dateTime` change does NOT.** RaceService stays Discord-free in v1.13.
- **D-95-05: Four sequential inline plans on `gsd/v1.13-discord-integration`.** No worktrees, no subagents (per [[feedback-inline-sequential-execution]]); wave-pause after every plan close (per [[feedback-wave-pause]]).
- **D-95-06: Rolling v1.13 milestone PR.** Squash subject locked: `feat(v1.13): discord integration & carry-forwards`.
- **D-95-07 (PARTIAL):** Standard gates (coverage, SpotBugs, CodeQL, Flyway immutability, CI E2E budget) carry forward unchanged. **Override:** the "EXPORT_ORDER bumps to 25 + SCHEMA_VERSION 1→2 + DiscordPostMixIn + DiscordPostRestorer" claim conflicts with the existing baselines and guard tests (see Landmines § 1) — RESEARCH recommends keeping EXPORT_ORDER = 24, SCHEMA_VERSION = 1, no MixIn, no Restorer. Planner must reconcile this before Plan 95-01.
- **D-95-08: Per-Plan Nyquist VALIDATION.md.** Phase 95 self-validates via `/gsd-validate-phase 95`.
- **D-95-09: `@Tag` convention.** WireMock-backed `*IT.java` → `@Tag("integration")`. Mockito-only units → untagged. Playwright in `org.ctc.e2e.discord.posts` → `@Tag("e2e")`.
- **D-95-10: WireMock-IT-only Phase-95 close; UAT-05 staged as STATE.md Pending UAT.** Live-Discord 11-step operator script runs after PR merge.
- **D-95-11: Production path boundary.** Only the explicitly listed files in `org.ctc.discord.{model,repository,service,dto,web}`, `org.ctc.admin.controller.MatchController`, `org.ctc.domain.service.MatchService`, `src/main/resources/db/migration/V12__discord_post.sql`, `templates/admin/match-detail.html`, `templates/admin/discord-posts.html`, `static/admin/css/admin.css` are in scope. Backup paths are NOT in scope (see Landmines § 1).
- **D-95-12: `DiscordPostRef` sealed-record-hierarchy** (4 permits: `MatchRef`, `MatchdayRef`, `RaceRef`, `SeasonRef`). Planner-discretion: plain 4-field DTO is acceptable if sealed hierarchy explodes plan size.

### Claude's Discretion (per CONTEXT.md)

- `DiscordPostType` enum location (`org.ctc.discord.model` vs. `org.ctc.discord.dto`). **Research recommends `org.ctc.discord.model`** — it is the entity-field type and `@Enumerated(EnumType.STRING)` couples it to the entity package by convention (precedent: any project enum that is a JPA `@Enumerated` lives next to its entity).
- `DiscordPostRef` sealed-hierarchy vs. plain-record. **Research recommends sealed hierarchy** — Phase 96-97 will add Race/Matchday/Season-scoped posts and the sealed `permits` clause makes the forward-extension reviewable in one diff.
- `/admin/discord/posts` navigation. **Research recommends a sidebar entry** under a "Discord" group adjacent to "Discord Config" — operator already navigates to `/admin/discord-config` for related work.
- `DiscordPostFilterForm` field types. **Research recommends UUID dropdowns** for season + match (small operator-facing list — current test guild has ≤ 10 matches per season) and an enum-select for `postType`.
- Race sort order in multipart bundle. **Research recommends list-position from `match.getRaces()`** — there is no `raceNumber` field on `Race` (see Landmines § 2). The `@OrderBy("dateTime ASC NULLS LAST")` on `Match.races` provides deterministic ordering by chronological dateTime, falling back to insertion order for null-dateTime races.
- SCHEDULE embed color. **Research recommends Discord default (no color set)** — `Embed` DTO does not currently support a `color` field (see Landmines § 4). Adding color requires extending `Embed` record + serializer; defer to Phase 96 polish unless explicitly requested.
- Re-Edit vs. Re-Post semantics on the list page. **Research recommends a single "Re-Post" button per row** — both code paths funnel into the same `postOrEdit` call which always hits the PATCH path when a row exists. Two buttons would be UX-noise without a behavior delta.

### Deferred Ideas (OUT OF SCOPE for Phase 95)

- `Race.dateTime` change triggers `SCHEDULE` embed auto-edit (DISC-FUTURE ticket, v1.14).
- Spring `ApplicationEvent` for Discord auto-edit hooks (YAGNI in v1.13; potential refactor when Phase 97 MATCH_PREVIEW auto-edit lands as a 3rd consumer).
- K-of-N Settings/Lineups posting (iterative workflow). Pre-flight strict is the current choice; partial-data UX could revisit in v1.14.
- Bulk Re-Post button on `/admin/discord/posts` (v1.14).

## Phase Requirements

| ID | Description (from REQUIREMENTS.md) | Research Support |
|----|-----------------------------------|------------------|
| POST-01 | Flyway V12 `discord_post` table + `DiscordPostService.postOrEdit(...)` + `/admin/discord/posts` filter page; uniform error-handling routes 4 sealed-exception cases through the existing flash-badge pattern. | § Code Excerpts (DiscordWebhookClient.execute/editMessage), § Architecture Patterns (3-tier delegation), § Don't Hand-Roll (sealed-exception hierarchy reuse), § Standard Stack (Flyway V12 migration shape from V10/V11 precedent). |
| POST-02 | "Post Team Cards" button — ONE multipart Webhook-POST with both PNGs as `files[0]+files[1]`; re-post replaces both attachments via Webhook-PATCH. | § Code Excerpts (`executeMultipart` from Phase 93), Multipart-PATCH section, TeamCardService API (returns URL string, not byte[]), the file-loading pattern. |
| POST-03 | "Post Settings" / "Post Lineups" buttons — multipart Webhook-POST with N attachments (one per race); pre-flight requires complete settings/RaceLineup data. | § Code Excerpts (SettingsGraphicService.generateSettings returns URL string, LineupGraphicService analog), Pre-flight predicate pattern from `DiscordChannelService.assertPreconditions`, Multipart-Bundle section. |
| POST-04 | "Post Match Results" with stale-detection — label flips to "Update Match Results" when `discord_post.updated_at < match.updatedAt`; no PATCH on no-op save. | § Stale-Detection section, `BaseEntity.updatedAt` (`@LastModifiedDate`), `MatchResultsGraphicService.generateMatchResults` returns `byte[]` directly. |
| POST-05 | "Post Schedule Message" Discord embed (4 fields) + auto-edit on Match-form save when host/RD/streamer change. | § Schedule Embed Structure, § Auto-Edit Hook Architecture, `MatchService.updateDiscordFields` (the real method name, NOT `save(MatchForm)` as CONTEXT claims), `DiscordTimestamps.longDateTime`/`relative` from Phase 93. |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `DiscordPost` persistence + repository lookup | API/Backend (`org.ctc.discord.repository`) | Database (Flyway V12) | Standard repository + entity pattern; Spring Data JPA. |
| `DiscordPostService.postOrEdit` dispatch (POST-vs-PATCH) | API/Backend (`org.ctc.discord.service`) | — | Single source of idempotency; per CLAUDE.md "Keep Controllers Thin", domain-service holds the routing. |
| Webhook HTTP I/O (`executeMultipart` POST + new `editMessageWithAttachments` PATCH) | API/Backend (`org.ctc.discord.DiscordWebhookClient`) | External (Discord webhook endpoint) | Outbound-only per v1.13 invariant; uses Spring RestClient + MultipartBodyBuilder. |
| Auto-post hook (Plan 95-02) | API/Backend (`DiscordChannelService.createMatchChannel`) | — | Lives at the end of `createMatchChannel` per D-94-04 ordering: Discord-side complete → DB persist → hook. |
| Auto-edit hook for schedule fields (Plan 95-04) | API/Backend (`MatchService.updateDiscordFields`) | — | Pre/post-diff per D-95-04; domain-service is the right layer per CLAUDE.md and avoids JPA `@PostUpdate` hidden control-flow. |
| Pre-flight predicates (`matchHasCompleteSettings`, etc.) | API/Backend (`DiscordPostService`) | Frontend Server (`MatchController` adds them as `ModelAttribute`) | Pre-flight result is data, not view-logic; controller passes the boolean into the template. |
| Match-Detail buttons + visibility (Plans 95-02..04) | Frontend Server (Thymeleaf `th:if`) | API/Backend (Pre-flight + existing-row attributes from controller) | View renders booleans + flash badges; visibility logic uses pre-flight predicates pushed by the controller. |
| `/admin/discord/posts` filter listing | Frontend Server (`DiscordPostController` + Thymeleaf) | API/Backend (`DiscordPostRepository.findAll(Specification)`) | Standard Spring MVC GET → Specification → Pageable → view. |
| Flash-badge category propagation | API/Backend (`applyErrorFlash` on `MatchController`) | Frontend Server (Thymeleaf reads `errorCategory`) | Existing Phase 93/94 pattern (`DiscordConfigController.applyErrorFlash`, `MatchController.applyErrorFlash`); Phase 95 extends with `data-incomplete` for the BusinessRuleException-with-category path. |

## Standard Stack

### Core (already in the project — zero new prod deps per v1.13-invariant D-No-New-Deps)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.x | DI, MVC, Data JPA, transactions | Project default — see CLAUDE.md "Technology Stack". |
| Spring `RestClient` | 6.1+ | Discord webhook HTTP I/O | Phase 93 introduced — synchronous, blocking, sufficient for outbound-only architecture. Already configured with `DiscordRateLimitInterceptor` + `DiscordHostValidator`. |
| Spring `MultiValueMap` + `ByteArrayResource` | 6.x | multipart-form-data parts | Phase 93 `executeMultipart` precedent — `editMessageWithAttachments` reuses the same `LinkedMultiValueMap<String, HttpEntity<?>>` shape. |
| Lombok | (Spring-managed) | `@Slf4j @Getter @Setter @NoArgsConstructor @ToString(exclude=…)` | Project convention per CLAUDE.md "Lombok Usage" and `lombok.config` Phase-86 invariant (`lombok.extern.findbugs.addSuppressFBWarnings=true`). |
| Flyway | (Spring-managed) | V12 migration | Project default; V1-V11 immutable; H2 + MariaDB compatible. |
| Thymeleaf | (Spring-managed) | Server-side rendering of `admin/discord-posts.html` + Match-Detail extensions | Project default — no frontend build tool per CLAUDE.md. |
| WireMock | 3.9.2 (test scope) | `*IT.java` test doubles for the Discord HTTP API | Phase 93 introduced; reused in 22+ existing tests under `org.ctc.discord.*`. |
| Playwright | (compile scope) | `@Tag("e2e")` tests | Project default — Playwright is compile-scope (CLAUDE.md "Constraints"). |
| Jackson | (Spring-managed) | `WebhookPayload` / `Embed` JSON + `@JsonInclude(Include.NON_NULL)` on the `Embed` record | Phase 93 introduced. |
| AssertJ + JUnit 5 + Mockito | (test scope) | Unit + integration tests | Project default. |

### Phase-95 New Production Files (per D-95-11 path boundary)

| File | Purpose | Reference |
|------|---------|-----------|
| `src/main/resources/db/migration/V12__discord_post.sql` | New `discord_post` table + 5 FK-indexes. | Design spec § 3.3 (corrected V11→V12 per CONTEXT). |
| `src/main/java/org/ctc/discord/model/DiscordPost.java` | New entity, `extends BaseEntity`, `@ToString(exclude={"webhookToken"})`. | Sibling to `DiscordGlobalConfig`. |
| `src/main/java/org/ctc/discord/model/DiscordPostType.java` | Enum (12 values per design spec § 3.3). | Phase 95 uses 5 values; 96/97 use the remaining 7. |
| `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java` | `JpaRepository<DiscordPost, Long> & JpaSpecificationExecutor<DiscordPost>` with `findByChannelIdAndPostTypeAndMatchId`. | Sibling to `DiscordGlobalConfigRepository`. |
| `src/main/java/org/ctc/discord/service/DiscordPostService.java` | `postOrEdit(...)`, `postTeamCards(Match)`, `postSettings(Match)`, `postLineups(Match)`, `postMatchResults(Match)`, `postSchedule(Match)`, `autoEditScheduleIfNeeded(Match)`, `matchHasCompleteSettings(Match)`, `matchHasCompleteLineups(Match)`. | New domain service. |
| `src/main/java/org/ctc/discord/dto/DiscordPostFilterForm.java` | Form DTO for the listing page. | Sibling to `DiscordConfigForm`. |
| `src/main/java/org/ctc/discord/dto/DiscordPostRef.java` | Sealed record interface with 4 permits per D-95-12. | New. |
| `src/main/java/org/ctc/discord/web/DiscordPostController.java` | `GET /admin/discord/posts` + filter binding. | Sibling to `DiscordConfigController`. |
| `src/main/resources/templates/admin/discord-posts.html` | Filter form + paginated table. | Sibling to `discord-config.html`. |
| `src/main/java/org/ctc/discord/DiscordWebhookClient.java` (EDITED) | Add `editMessageWithAttachments(...)` (~30 LOC scope-add). | D-95-03a. |
| `src/main/java/org/ctc/discord/service/DiscordChannelService.java` (EDITED) | Auto-post hook at end of `createMatchChannel`. | D-95-01. |
| `src/main/java/org/ctc/admin/controller/MatchController.java` (EDITED) | 6 new POST endpoints (Post Team Cards, Refresh Team Cards, Post Settings, Post Lineups, Post Schedule, Post Match Results) + pre-flight predicates as model attributes. | D-95-11. |
| `src/main/java/org/ctc/domain/service/MatchService.java` (EDITED) | `updateDiscordFields(id, MatchForm)` pre/post-diff hook (NOT `save(MatchForm)` — that method does not exist). | D-95-04. |
| `src/main/resources/templates/admin/match-detail.html` (EDITED) | Fill the `.discord-actions--posts` placeholder (line 56-58) with the button cluster. | D-94-01. |
| `src/main/resources/static/admin/css/admin.css` (EDITED) | Add `.error-badge--data-incomplete` variant; possibly tighten `.discord-actions--posts` sub-cluster styling. | D-95-03b. |

### Alternatives Considered (and rejected per the locked decisions)

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Webhook-PATCH-edit-in-place | Delete + repost | Breaks ROADMAP success criterion 1 ("Discord's edit-indicator is visible, no duplicate appears"); also doubles Discord rate-limit consumption. |
| Multipart bundle for Settings/Lineups | N rows in `discord_post` (one per race) | Breaks PK shape and ROADMAP success criterion ("1 row per type"); doubles rate-limit consumption. |
| `MatchService.updateDiscordFields` pre/post-diff | Spring `ApplicationEvent` | YAGNI in v1.13 (only 1 consumer); revisit when Phase 97 MATCH_PREVIEW auto-edit lands as 3rd consumer. |
| `@LastModifiedDate` on `Match` for stale-detection | New `match.lastResultsUpdatedAt` field | Phase 94 already enabled `@LastModifiedDate` via `BaseEntity` — no schema change needed. |

**Installation:** No new dependencies needed. The plan only adds source files + Flyway V12.

**Version verification:** Carried over from Phase 93/94 — all libraries already in `pom.xml` and verified by the existing 1696-test green CI.

## Package Legitimacy Audit

> Phase 95 installs ZERO new external packages (D-No-New-Deps invariant). The "Standard Stack" table above lists only libraries that were verified during Phase 93 + already pin-locked in `pom.xml`. No slopcheck run required.

| Package | Registry | Disposition |
|---------|----------|-------------|
| (none — Phase 95 adds source files only) | — | Approved (no install action). |

## Architecture Patterns

### System Architecture Diagram

```
                                ┌────────────────────────────────────┐
                                │ Match-Detail Page (Thymeleaf SSR)  │
                                │ - Post/Re-Post/Refresh buttons     │
                                │ - error-badge with errorCategory   │
                                └─────────────┬──────────────────────┘
                                              │ POST /admin/matches/{id}/{action}
                                              ▼
┌────────────────────────────┐        ┌───────────────────────────┐
│ Match-Edit Form (Thymeleaf)│        │ MatchController           │
│ - lobbyHost/RD/streamer    ├──save──▶ (thin)                    │
└────────────────────────────┘        │  applyErrorFlash(...)     │
                                      │  delegate to services     │
                                      └───────┬───────────────────┘
                                              │
                ┌─────────────────────────────┼──────────────────────────────┐
                ▼                             ▼                              ▼
   ┌─────────────────────┐        ┌────────────────────────┐    ┌─────────────────────┐
   │ MatchService        │        │ DiscordPostService     │    │ TeamCardService     │
   │ updateDiscordFields │        │ - postOrEdit(...)      │    │ SettingsGraphic..   │
   │ - pre/post-diff     │        │ - postTeamCards(Match) │    │ LineupGraphic..     │
   │ - calls hook        │        │ - postSettings(Match)  │    │ MatchResultsGraphic │
   └──────────┬──────────┘        │ - postLineups(Match)   │    │ (existing services) │
              │                   │ - postMatchResults     │    └──────────┬──────────┘
              │ when 3 fields     │ - postSchedule(Match)  │               │ render PNG
              │ changed           │ - autoEditScheduleIf   │               │ (URL str or
              ▼                   │   Needed(Match)        │◀──────────────┘  byte[])
   autoEditScheduleIfNeeded ─────▶│ - matchHasComplete...  │
                                  │ - pre-flight predicate │
                                  └───────┬────────────────┘
                                          │ lookup row
                                          ▼
                               ┌─────────────────────────┐
                               │ DiscordPostRepository   │
                               │ findByChannelIdAndPost  │
                               │ TypeAndMatchId(...)     │
                               │ findAll(Specification)  │
                               └───────┬─────────────────┘
                                       │ row exists?
                                       ├─NO─▶ executeMultipart (POST)
                                       │       │
                                       │       ▼
                                       │   insert new row(message_id)
                                       │
                                       └─YES▶ editMessageWithAttachments (PATCH)
                                               │
                                               ▼
                                           update row (updated_at)

   ┌─────────────────────────────────┐
   │ DiscordChannelService.create    │
   │ MatchChannel(Match) — Phase 94  │
   │ End-of-method hook (D-95-01):   │
   │   discordPostService            │
   │     .postTeamCards(match)       │
   │ Failure → WARN log + flash      │
   │   (channel + webhook persist)   │
   └─────────────────────────────────┘

   ┌─────────────────────────────────┐         ┌─────────────────────────────┐
   │ DiscordWebhookClient            │ HTTP    │ Discord API                 │
   │ - execute (JSON POST)           ├────────▶│ POST /webhooks/{id}/{token} │
   │ - executeMultipart (POST)       │         │ PATCH /…/messages/{msgId}   │
   │ - editMessage (JSON PATCH)      │         └─────────────────────────────┘
   │ - editMessageWithAttachments    │
   │   (multipart PATCH — NEW)       │
   └─────────────────────────────────┘
            │
            ▼
   DiscordRateLimitInterceptor + DiscordHostValidator (Phase 93 — unchanged)

   ┌─────────────────────────────────┐
   │ /admin/discord/posts (NEW)      │
   │ DiscordPostController           │
   │ - filter form (season/match/    │
   │   type)                         │
   │ - paginated table with          │
   │   Re-Post button per row        │
   └─────────────────────────────────┘
```

### Recommended Project Structure (Phase 95 deltas only)

```
src/main/java/org/ctc/discord/
├── model/
│   ├── DiscordGlobalConfig.java          (existing — Phase 93)
│   ├── DiscordPost.java                  (NEW)
│   └── DiscordPostType.java              (NEW — enum)
├── repository/
│   ├── DiscordGlobalConfigRepository.java (existing — Phase 93)
│   └── DiscordPostRepository.java        (NEW)
├── service/
│   ├── DiscordGlobalConfigService.java    (existing — Phase 93)
│   ├── DiscordCategoryResolver.java       (existing — Phase 94)
│   ├── DiscordChannelService.java         (EDITED — auto-post hook at end of createMatchChannel)
│   └── DiscordPostService.java            (NEW)
├── dto/
│   ├── (existing 16 DTOs from Phase 93/94)
│   ├── DiscordPostFilterForm.java         (NEW)
│   └── DiscordPostRef.java                (NEW — sealed interface)
├── web/
│   ├── DiscordConfigController.java       (existing — Phase 93)
│   └── DiscordPostController.java         (NEW)
└── DiscordWebhookClient.java              (EDITED — editMessageWithAttachments PATCH method)

src/main/java/org/ctc/domain/service/
└── MatchService.java                      (EDITED — pre/post-diff hook in updateDiscordFields)

src/main/java/org/ctc/admin/controller/
└── MatchController.java                   (EDITED — 6 new POST endpoints + pre-flight model attrs)

src/main/resources/db/migration/
└── V12__discord_post.sql                  (NEW)

src/main/resources/templates/admin/
├── match-detail.html                      (EDITED — fill .discord-actions--posts placeholder)
└── discord-posts.html                     (NEW)

src/main/resources/static/admin/css/
└── admin.css                              (EDITED — .error-badge--data-incomplete + possibly .discord-actions--posts sub-cluster)
```

### Pattern 1: `postOrEdit` Idempotency Pivot

**What:** Single service method that looks up an existing `discord_post` row by `(channelId, postType, foreignKey)`; if found, calls `webhookClient.editMessageWithAttachments(...)` (or `editMessage` for SCHEDULE which has no attachments) and updates `updated_at`; if not, calls `webhookClient.executeMultipart(...)` (or `execute` for SCHEDULE) and inserts a new row.

**When to use:** Every post-type entry point (`postTeamCards`, `postSettings`, `postLineups`, `postMatchResults`, `postSchedule`) routes through this same pivot. Re-post buttons on the listing page and on Match-Detail are exactly the same call — the row's existence determines POST-vs-PATCH automatically.

**Example (pseudocode based on the existing Phase 93 client API):**
```java
// Source: design spec § 3.3 + DiscordWebhookClient.java (existing) + CONTEXT D-95-03a
@Transactional
public DiscordPost postOrEdit(
        String webhookUrl,
        DiscordPostType type,
        WebhookPayload payload,
        List<NamedAttachment> attachments,
        DiscordPostRef ref) throws DiscordApiException {

    String channelId = match.getDiscordChannelId();  // from ref via the helper switch
    Optional<DiscordPost> existing =
            discordPostRepository.findByChannelIdAndPostTypeAndMatchId(channelId, type, ref.matchId());

    if (existing.isPresent()) {
        DiscordPost row = existing.get();
        WebhookMessage edited = attachments.isEmpty()
                ? webhookClient.editMessage(webhookUrl, row.getMessageId(), payload)
                : webhookClient.editMessageWithAttachments(webhookUrl, row.getMessageId(), payload, attachments);
        row.touchUpdatedAt();  // @LastModifiedDate fires on save()
        return discordPostRepository.save(row);
    }

    WebhookMessage posted = attachments.isEmpty()
            ? webhookClient.execute(webhookUrl, payload)
            : webhookClient.executeMultipart(webhookUrl, payload, attachments);

    DiscordPost row = new DiscordPost();
    row.setChannelId(channelId);
    row.setMessageId(posted.id());
    row.setWebhookId(extractWebhookId(webhookUrl));
    row.setWebhookToken(extractWebhookToken(webhookUrl));
    row.setPostType(type);
    ref.applyTo(row);  // sets matchId / matchdayId / raceId / seasonId
    row.setPostedAt(LocalDateTime.now(clock));
    return discordPostRepository.save(row);
}
```

### Pattern 2: Multipart-PATCH (the new `editMessageWithAttachments`)

**What:** Mirror of `executeMultipart` (Phase 93) — same `LinkedMultiValueMap<String, HttpEntity<?>>` parts shape (`payload_json` + `files[i]` per attachment), same `MediaType.MULTIPART_FORM_DATA`, but `.patch().uri("/messages/{messageId}", messageId)` instead of `.post().uri("")`.

**When to use:** Re-post of TEAM_CARDS (2 attachments), SETTINGS / LINEUPS (N attachments), MATCH_RESULTS (1 attachment) when a `discord_post` row already exists.

**Reference:** `DiscordWebhookClient.executeMultipart` line 61-103 (full implementation) + `editMessage` line 105-115 (JSON PATCH pattern); the new method composes the two.

```java
// Source: composition of DiscordWebhookClient.executeMultipart + editMessage (Phase 93)
public WebhookMessage editMessageWithAttachments(
        String webhookUrl, String messageId, WebhookPayload payload,
        List<NamedAttachment> attachments) throws DiscordApiException {
    if (attachments.size() > MAX_ATTACHMENTS) {
        throw new IllegalArgumentException("Discord allows at most 10 attachments per webhook (got "
                + attachments.size() + ")");
    }
    if (attachments.isEmpty()) {
        return editMessage(webhookUrl, messageId, payload);
    }
    hostValidator.requireAllowed(webhookUrl);
    String payloadJson;
    try {
        payloadJson = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
        throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
    }
    MultiValueMap<String, HttpEntity<?>> parts = new LinkedMultiValueMap<>();
    HttpHeaders payloadHeaders = new HttpHeaders();
    payloadHeaders.setContentType(MediaType.APPLICATION_JSON);
    parts.add("payload_json", new HttpEntity<>(payloadJson, payloadHeaders));
    for (int i = 0; i < attachments.size(); i++) {
        NamedAttachment att = attachments.get(i);
        final String filename = att.filename();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_PNG);
        ByteArrayResource resource = new ByteArrayResource(att.bytes()) {
            @Override
            public String getFilename() { return filename; }
        };
        parts.add("files[" + i + "]", new HttpEntity<>(resource, fileHeaders));
    }
    return execute(() -> forWebhookUrl(webhookUrl)
            .patch()
            .uri("/messages/{messageId}", messageId)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(parts)
            .retrieve()
            .body(WebhookMessage.class));
}
```

Refactor opportunity (small): the `executeMultipart` POST body and the new PATCH body differ ONLY by `.post().uri("")` vs `.patch().uri("/messages/{messageId}", messageId)`. Planner may choose to extract a private `multipartCall(BodyBuilder, MultiValueMap)` helper or just duplicate the ~12 lines — both acceptable.

### Pattern 3: Auto-Post Hook in `DiscordChannelService.createMatchChannel`

**Where the hook lives:** End of `createMatchChannel` (`DiscordChannelService.java:97-100`), AFTER `matchRepository.save(match)`. The hook runs inside the existing `@Transactional` boundary on `createMatchChannel`.

**Failure handling per D-95-01a:** Catch `DiscordApiException` from `discordPostService.postTeamCards(match)` and log a WARN but do NOT throw — the channel + webhook persist. Surface the category to the controller via a side-channel (most natural: return a record from `createMatchChannel` that includes an `Optional<String> teamCardsErrorCategory`, then the controller branches its flash on the result).

```java
// Source: DiscordChannelService.java:48-100 + D-95-01a
@Transactional
public ChannelCreationResult createMatchChannel(Match match) throws DiscordApiException {
    // ... existing precondition, channel-create, audit, save logic unchanged ...
    matchRepository.save(match);

    Optional<String> teamCardsError = Optional.empty();
    try {
        discordPostService.postTeamCards(match);
    } catch (DiscordApiException e) {
        teamCardsError = Optional.of(e.category().name().toLowerCase().replace('_', '-'));
        log.warn("Auto-post TEAM_CARDS failed for match {}: category={}", match.getId(), teamCardsError.get());
    } catch (RuntimeException e) {  // covers Playwright failures from auto-gen
        teamCardsError = Optional.of("transient");
        log.warn("Auto-post TEAM_CARDS failed for match {}: {}", match.getId(), e.toString());
    }
    log.info("Discord channel created for match {} → {} (channelId={})",
            match.getId(), channel.name(), channel.id());
    return new ChannelCreationResult(teamCardsError);
}

public record ChannelCreationResult(Optional<String> teamCardsErrorCategory) {}
```

**Important:** `createMatchChannel` is `@Transactional`. The webhook POST inside `postTeamCards` is a network side-effect that must succeed BEFORE the DB row is inserted (otherwise the row will reference a non-existent Discord message). The existing `postOrEdit` pattern handles this correctly — `executeMultipart` happens first, then `discordPostRepository.save()`. But the outer `@Transactional` boundary on `createMatchChannel` means a thrown exception from `postTeamCards` would roll back the channel-id + webhook-url storage. D-95-01a explicitly requires that the channel stays, so the hook MUST swallow the exception (catch + log + record category) — never re-throw.

### Pattern 4: Pre/Post-Diff Hook in `MatchService.updateDiscordFields`

**The real method name is `updateDiscordFields(UUID, MatchForm)`, NOT `save(MatchForm)`** (see Landmines § 3). Current implementation (lines 64-72):

```java
@Transactional
public void updateDiscordFields(UUID id, MatchForm form) {
    Match match = findById(id);
    match.setDiscordTeaser(form.getDiscordTeaser());
    match.setStreamLink(form.getStreamLink());
    match.setLobbyHost(form.getLobbyHost());
    match.setRaceDirector(form.getRaceDirector());
    match.setStreamer(form.getStreamer());
    matchRepository.save(match);
}
```

**Phase-95 extension:**
```java
// Source: D-95-04 + MatchService.java line 63-72
@Transactional
public void updateDiscordFields(UUID id, MatchForm form) {
    Match match = findById(id);

    boolean scheduleFieldsChanged =
            !Objects.equals(match.getLobbyHost(),    form.getLobbyHost())
         || !Objects.equals(match.getRaceDirector(), form.getRaceDirector())
         || !Objects.equals(match.getStreamer(),     form.getStreamer());

    match.setDiscordTeaser(form.getDiscordTeaser());
    match.setStreamLink(form.getStreamLink());
    match.setLobbyHost(form.getLobbyHost());
    match.setRaceDirector(form.getRaceDirector());
    match.setStreamer(form.getStreamer());
    Match saved = matchRepository.save(match);

    if (scheduleFieldsChanged) {
        try {
            discordPostService.autoEditScheduleIfNeeded(saved);
        } catch (DiscordApiException e) {
            log.warn("Auto-edit SCHEDULE failed for match {}: {}", saved.getId(), e.category());
            // swallow — match update succeeded; operator can manually re-post via button
        }
    }
}
```

The "before" values come from the loaded entity (`match.getLobbyHost()` etc. BEFORE the setters fire). Spring's JPA dirty-checking is irrelevant — we compare in-process, not via DB.

**`autoEditScheduleIfNeeded` is no-op when no SCHEDULE row exists** (per CONTEXT D-95-04 wording: "kein automatischer Initial-Post"). It does a `findByChannelIdAndPostTypeAndMatchId(..., SCHEDULE, ...)` lookup and if empty, returns. Otherwise it calls `editMessage(...)` (no attachments) on the existing message_id.

### Pattern 5: Stale-Detection for MATCH_RESULTS Button Label

**The signal:** Compare `existingPost.getUpdatedAt()` to `match.getUpdatedAt()` (`BaseEntity.updatedAt` via `@LastModifiedDate`).

**The controller** preloads both into the model:
```java
// in MatchController.detail (line 84-95)
Optional<DiscordPost> matchResultsPost = discordPostRepository
        .findByChannelIdAndPostTypeAndMatchId(match.getDiscordChannelId(), MATCH_RESULTS, match.getId());
boolean matchResultsStale = matchResultsPost
        .map(p -> p.getUpdatedAt().isBefore(match.getUpdatedAt()))
        .orElse(false);
model.addAttribute("matchResultsPost", matchResultsPost.orElse(null));
model.addAttribute("matchResultsStale", matchResultsStale);
```

**The template** uses these:
```html
<form th:if="${match.discordChannelId != null}"
      th:action="@{/admin/matches/{id}/post-match-results(id=${match.id})}" method="post">
    <button type="submit" class="btn btn-primary btn-sm"
            th:text="${matchResultsPost == null ? 'Post Match Results'
                       : matchResultsStale ? 'Update Match Results' : 'Re-Post Match Results'}">
    </button>
</form>
```

**"No PATCH on a no-op save" semantics:** ROADMAP success criterion 3 says stale-detection only triggers when underlying data has actually changed. Critical clarification: `@LastModifiedDate` fires on **every** `matchRepository.save(match)` call. So if a future contributor adds a no-op `MatchService.save()` that goes through `repository.save` without changing any field, the timestamp DOES advance and the button would flip to "Update Match Results" inappropriately. The Phase-95 controller-test must verify that:

1. Posting MATCH_RESULTS — row inserted, `post.updatedAt == match.updatedAt` (or `post.updatedAt > match.updatedAt`); button reads "Re-Post Match Results".
2. Adding/editing a `RaceResult` for the match — `MatchService` propagates this through a path that bumps `match.updatedAt`; button flips to "Update Match Results".
3. Calling `MatchService.findById(id)` and immediately `matchRepository.save(match)` without any field change — Spring's dirty-checking should NOT emit an UPDATE statement (no actual mutations), so `@LastModifiedDate` does NOT advance. Verify this empirically in the IT.

Plan 95-04 should treat scenario 3 as a verification target, not an assumed behavior.

### Anti-Patterns to Avoid

- **Inline-style on Match-Detail buttons** — per CLAUDE.md "No Inline Styles on Buttons", use the existing `.btn`, `.btn-sm`, `.btn-primary`, `.btn-secondary` classes from `admin.css`. The `.discord-actions` cluster (line 214 of admin.css) already provides responsive-wrap.
- **Business logic in the Match-Detail template** — pre-flight predicates (`matchHasCompleteSettings`, `matchHasCompleteLineups`, `matchResultsStale`) are computed in the controller, passed as `Boolean` model attributes; the template only reads them with `th:if`.
- **`@PostUpdate` JPA listener on `Match`** — hidden control flow, hostile to testing. The locked decision D-95-04 explicitly rejects this in favor of explicit pre/post-diff in the service.
- **`new RuntimeException(e)` re-throw** in the auto-post hook — the channel-create transaction would roll back. D-95-01a is explicit: catch + log + flash badge, never throw.
- **Method comments restating the method name** — per CLAUDE.md "No Comment Pollution", do not add `// Phase 95 hook` or `// Plan 95-02 fix` annotations on the new methods. Use the git history and PR description instead.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Discord HTTP I/O | A new `RestTemplate`-based client | The existing `DiscordWebhookClient` + add the one new `editMessageWithAttachments` method (~30 LOC) | The existing client wires `DiscordRateLimitInterceptor` + `DiscordHostValidator` + sealed-exception mapping. Bypassing it loses all 4. |
| Multipart-form-data assembly | Custom byte stream | The existing `LinkedMultiValueMap<String, HttpEntity<?>>` + `ByteArrayResource` pattern from `executeMultipart` | Phase 93 verified the Discord wire-format with this exact pattern; WireMock tests pass. |
| Sealed exception → flash category mapping | New switch statement per controller | The existing `applyErrorFlash(RedirectAttributes, DiscordApiException, String)` private helper from `DiscordConfigController` + `MatchController` | Already handles all 4 categories with proper logging. Phase 95 only adds the `BusinessRuleException → "data-incomplete"` branch (Pre-flight failure). |
| Webhook-URL parsing (`webhook_id` + `webhook_token` extraction) | Substring slicing | The webhook URL pattern is `https://discord.com/api/webhooks/{webhook_id}/{webhook_token}`; the existing Phase 94 code in `DiscordChannelService` already stores the full URL in `match.discordChannelWebhookUrl`. | Phase 95 only needs `webhook_id` + `webhook_token` for the DB row. A small helper in `DiscordPostService` (or even inline) parses the URL once. Pattern: `Pattern.compile("^https://discord\\.com/api/webhooks/(?<id>\\d+)/(?<token>[^/]+)$")`. |
| Idempotency keying | A new `(channel_id, post_type, match_id)` hash | The composite repository finder `findByChannelIdAndPostTypeAndMatchId` translates directly to a `WHERE channel_id = ? AND post_type = ? AND match_id = ?` SQL query with the existing FK indexes. Spring Data JPA derives the method. | Standard Spring Data — no custom JPQL. |
| Filter listing | Custom query builder | `JpaSpecificationExecutor<DiscordPost>` + `Specification.where(byChannel).and(byMatch).and(byType)` | Standard Spring Data. Pageable for the 50-row pagination. |
| Stale-detection timestamp | Custom `match.lastResultsUpdatedAt` field | `BaseEntity.updatedAt` via `@LastModifiedDate` is already wired and fires on every `matchRepository.save(match)` mutation. | Zero schema delta. |
| `DiscordPostType` enum-to-string | `name().toLowerCase().replace('_','-')` re-implementation per call | `@Enumerated(EnumType.STRING)` on the entity field + `enum.name()` in service code. | Standard JPA. |
| Multipart > 10 attachments guard | Custom IndexOutOfBounds wrapping | `DiscordWebhookClient` already throws `IllegalArgumentException` with message containing "10 attachments" when > 10. Plan 95-03 just lets it propagate — operators see a developer-facing error which is fine because CTC matches have 3-4 races by spec (Plan 95-03 pre-flight guarantees ≤ 10). | Existing guard. |

**Key insight:** Phase 95 is mostly orchestration of existing Phase 93/94 primitives. The new code surface is small (~ 1 entity + 1 enum + 1 repo + 1 service + 1 controller + 1 sealed-ref + 1 form-DTO + 1 multipart-PATCH method + 1 Flyway migration + 1 new template + 6 new controller endpoints + 2 service-method extensions). Everything else is reuse.

## Common Pitfalls

### Pitfall 1: Multipart-PATCH wire-contract drift from Discord docs

**What goes wrong:** Discord's webhook-PATCH endpoint accepts multipart but expects `payload_json` + `files[i]` parts (same shape as POST). Some references suggest the PATCH path requires `attachments[i]` JSON metadata to keep certain files and replace others — Phase 95 always replaces ALL attachments (no partial-update semantics), so the simple `files[i]` shape works.

**Why it happens:** Discord's webhook-edit docs are sparse; community examples sometimes use the bot-channel-message PATCH endpoint shape (different).

**How to avoid:** WireMock `*IT` (`DiscordWebhookClientMultipartEditIT`) MUST assert the PATCH request body has `files[0]`, `files[1]`, … parts and a `payload_json` part — same assertion shape as the existing `DiscordWebhookClientMultipartIT.givenAttachments_whenExecuteMultipart_thenAssertsPerPartHeadersAndPayload` (line 64-86). The IT pins the wire-contract; UAT-05 verifies live Discord accepts it.

**Warning signs:** Discord returns 400 with code `40060` ("invalid form body") or silently keeps the old attachments — both surface as test failures in the WireMock IT body-assertion.

### Pitfall 2: Race ordering in the multipart bundle is NOT by `raceNumber`

**What goes wrong:** CONTEXT D-95-03 references `race.raceNumber` for the multipart bundle sort order. **There is no `raceNumber` field on `Race`.** (Verified by reading `org/ctc/domain/model/Race.java`.)

**Why it happens:** The `MatchResultsGraphicService.buildRaceRows` (line 91-114) computes a `raceNumber++` counter on the fly during iteration over `match.getRaces()` (which is `@OrderBy("dateTime ASC NULLS LAST")`). This counter is per-render, not persisted.

**How to avoid:** Phase 95 uses the iteration index over `match.getRaces()` (the entity's `@OrderBy` provides deterministic order). Filename: `"settings-race-" + (i + 1) + ".png"` where `i` is the list index. UAT-05 step 5 verifies the operator sees the bundle attachments in the expected order.

**Warning signs:** Operator reports race-attachment order looks wrong in Discord (e.g. an unscheduled race is in the wrong slot). Fix: explicitly filter races with `null` dateTime out of the bundle or surface a pre-flight failure when dateTime is missing.

### Pitfall 3: Auto-post hook re-entering its own transaction boundary

**What goes wrong:** `DiscordChannelService.createMatchChannel` is `@Transactional`. If `postTeamCards` throws and the catch swallows it but the catch BLOCK still mutates DB rows (e.g. flags `match.teamCardsPostFailedAt`), Spring's transaction-rollback-on-exception semantics no longer apply — but the trickier case is `DiscordPostService.postOrEdit` being `@Transactional` itself: when called inside the outer transaction, Spring's default propagation is `REQUIRED` which means both run in the same transaction. A network roundtrip inside the transaction is fine, but if the SECOND DB write inside `postOrEdit` (`discordPostRepository.save(row)`) throws, the entire transaction rolls back including the Phase-94 channel + webhook persistence.

**Why it happens:** Default `REQUIRED` propagation merges callee transaction into caller.

**How to avoid:** Two options, planner picks:
- **Option A** (preferred): `discordPostService.postTeamCards(match)` annotated `@Transactional(propagation = REQUIRES_NEW)` so the auto-post has its own transaction; a failure there rolls back only its DB row, not the channel.
- **Option B**: Catch every kind of exception in the auto-post block, log + record category, never re-throw. This relies on Phase 94's transaction continuing to commit because the catch swallowed the exception. Spring's default rule is "rollback on `RuntimeException`/`Error`" so as long as the catch swallows, the transaction commits.

Option B is simpler and what D-95-01a literally says. Option A is more defensible if `postOrEdit`'s DB write itself fails (DB constraint violation, etc.) — that scenario is exotic but worth documenting.

**Warning signs:** Test `DiscordChannelServiceAutoPostHookIT.givenAutoPostFails_whenCreateMatchChannel_thenChannelStaysPersisted` — assert that after a WireMock-stubbed Discord 5xx for the team-cards POST, the `match.discord_channel_id` is still in the DB.

### Pitfall 4: `BaseEntity.updatedAt` advances on EVERY save, including no-op saves

**What goes wrong:** Spring's `@LastModifiedDate` is updated on every `entityManager.merge` / `repository.save` regardless of whether any field actually changed. So calling `matchRepository.save(match)` where no setters fired between `findById` and `save` would advance `updated_at` and (per Stale-Detection logic) flip the button to "Update Match Results".

**Why it happens:** Spring Data JPA dirty-checking compares entity state to DB state; if no fields changed, no UPDATE is emitted. BUT `@LastModifiedDate` is updated in-Java BEFORE the UPDATE check. Empirical observation needed.

**How to avoid:** Empirically pin the behavior in a dedicated test (`MatchUpdatedAtNoopSaveIT`). If Spring's behavior is "no update on no-op", the ROADMAP criterion 3 is automatically satisfied. If Spring DOES update `updated_at` on no-op saves, Plan 95-04 needs an explicit pre-save dirty-check OR a different stale-signal (e.g. compare on `match.races[].results[].updatedAt` max instead of `match.updatedAt`).

**Warning signs:** Match-Detail page shows "Update Match Results" immediately after navigating to the match without editing anything — symptom of no-op save advancing the timestamp.

### Pitfall 5: Webhook PATCH does NOT use `?wait=true` parameter

**What goes wrong:** Discord's webhook POST endpoint accepts `?wait=true` to return the message body (`WebhookMessage` with `id`). Phase 93's `executeMultipart` and `execute` do NOT pass `?wait=true` but DO return `WebhookMessage` — which only works because Discord ALSO returns the body without `?wait=true` for webhook POSTs that have a JSON response body. For PATCH on `/messages/{id}`, Discord returns the updated message body by default (no `?wait` needed).

**Why it happens:** Wire-contract confusion between bot-message-PATCH (`/channels/{id}/messages/{msgId}`) and webhook-message-PATCH (`/webhooks/{id}/{token}/messages/{msgId}`).

**How to avoid:** The WireMock IT (`DiscordWebhookClientMultipartEditIT`) returns `200 OK` with a JSON body `{"id":"msg-X", "channel_id":"chan-1"}`. The client treats this as the canonical response. **No `?wait=true` query parameter needed on PATCH** — the existing JSON-only `editMessage` (line 105-115) confirms this works without a query param.

**Warning signs:** WireMock IT fails because the request URI has `?wait=true` (it shouldn't) — or live Discord returns 204 instead of 200 (PATCH-with-empty-body convention) and Jackson fails to deserialize. Mitigation: make the return type `Optional<WebhookMessage>` or accept `null` from `.body(WebhookMessage.class)`. Verify against the existing Phase-93 `editMessage` test.

### Pitfall 6: Pre-Flight checks against `RaceLineup` need the right repository

**What goes wrong:** D-95-03b says SETTINGS visible when all races have settings, LINEUPS visible when all races have a `RaceLineup` with ≥1 driver per team. `Race.settings` is a `@OneToOne(mappedBy = "race")` direct association; checking `race.getSettings() != null` works. But `RaceLineup` is queried via `RaceLineupRepository.findByRaceId(race.getId())` — there is no direct association field on Race. Plan 95-03's pre-flight must batch-query the lineup repository (avoid N+1: ideally a single `existsByRaceIdInAndTeamId` aggregate query).

**Why it happens:** The lineup-per-race relationship is repository-discovered, not entity-mapped.

**How to avoid:** Add a repository method like `int countDistinctRaceIdByRaceIdIn(List<UUID> raceIds)` or do the simpler `match.getRaces().stream().allMatch(r -> !raceLineupRepository.findByRaceId(r.getId()).isEmpty())` accepting the N+1. For 3-4 races per match the N+1 is acceptable; the pre-flight runs only on Match-Detail page-load, not in a hot path.

**Warning signs:** SpotBugs flags an N+1 (it won't — SpotBugs doesn't detect N+1) — really the warning sign is slow Match-Detail page loads. Mitigation: extract pre-flight to `MatchService.computePreFlightFlags(match)` and JPA-fetch-join races + lineups in one query.

### Pitfall 7: `SettingsGraphicService.generateSettings` returns a URL string, not bytes

**What goes wrong:** The signature is `public String generateSettings(Race race)` returning `/uploads/races/{id}/settings.png`. To build a `NamedAttachment`, Phase 95 needs `byte[]`. Reading from disk requires the same `app.upload-dir` path resolution that `TeamCardService.encodeLogoBase64` uses (line 184-206).

**Why it happens:** Phase 93 / 94 didn't anticipate the multipart-bundle use case — the existing graphic services were designed for in-page `<img src="/uploads/...">` display.

**How to avoid:** Add a helper in `DiscordPostService`:
```java
private byte[] readPng(String url) throws IOException {
    if (!url.startsWith("/uploads/")) {
        throw new IllegalStateException("Expected /uploads/ URL, got: " + url);
    }
    Path file = uploadDir.resolve(url.substring("/uploads/".length())).normalize();
    if (!file.startsWith(uploadDir)) {
        throw new SecurityException("Path traversal attempt: " + url);
    }
    return Files.readAllBytes(file);
}
```
`MatchResultsGraphicService.generateMatchResults(Match)` is the exception — it returns `byte[]` directly (line 36 `public byte[] generateMatchResults`).

**Warning signs:** `NoSuchFileException` at runtime when the file isn't on disk (Playwright cache cleared, fresh dev DB, etc.). Pre-flight + auto-gen-on-demand (D-95-02 for team-cards) mitigates for TEAM_CARDS; for SETTINGS / LINEUPS, the strict pre-flight (D-95-03b) gates the button so the operator can't trigger a post when the PNG isn't on disk… EXCEPT the strict pre-flight only checks `race.settings != null` and `RaceLineup` exists, NOT that the PNG file exists. Plan 95-03's `postSettings` must call `settingsGraphicService.generateSettings(race)` (which renders and writes the PNG) for every race in the bundle — accept the synchronous Playwright cost (~ 2-3s per PNG × 4 races = up to 12s). UAT-05 step 5 will observe the operator-visible delay.

### Pitfall 8: SCHEDULE embed has no `Embed.color` field

**What goes wrong:** Design spec § 4.6 shows the embed JSON without `color`. The existing `Embed` record (`dto/Embed.java`) has only `title`, `description`, `fields` — no `color`. CONTEXT discretion item "SCHEDULE-Embed-Color" suggests CTC-brand-color but adding it requires extending the record.

**Why it happens:** Phase 93 designed `Embed` for the minimum surface needed.

**How to avoid:** Either (a) accept Discord's default (no color set — works out of the box) OR (b) extend `Embed` to include `color` as a nullable `Integer` (Discord-style decimal-encoded RGB). Option (a) avoids touching the wire-contract; option (b) is < 5 LOC. Research recommends (a) for Phase 95, defer (b) to Phase 98 polish.

## Runtime State Inventory

> Phase 95 is greenfield within v1.13 — no rename / refactor / migration. The only "runtime state" surfaces are:
>
> | Category | Items Found | Action Required |
> |----------|-------------|------------------|
> | Stored data | None — V12 creates a fresh `discord_post` table; no existing data to migrate. | None. |
> | Live service config | Discord webhook URLs in `matches.discord_channel_webhook_url` (Phase 94) — Phase 95 reads them, never writes. | None. |
> | OS-registered state | None. | None. |
> | Secrets / env vars | `DISCORD_BOT_TOKEN` already wired (Phase 93); webhook URLs already in DB (Phase 94). | None. |
> | Build artifacts | None — Phase 95 adds source files + 1 migration only. | None. |
>
> **Nothing additional to migrate or re-register.** Phase 95 is a pure feature add on top of Phase 94's data model.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Eclipse Temurin) | Compile + runtime | ✓ (per CLAUDE.md) | 25 | — |
| Spring Boot 4.x | All service code | ✓ | 4.x | — |
| H2 (test) + MariaDB (local/docker/prod) | Flyway V12 migration | ✓ | per profile | — |
| WireMock | Discord-IT tests | ✓ | 3.9.2 (test scope per `pom.xml`) | — |
| Playwright | E2E tests + TeamCardService PNG render | ✓ | compile scope | If Chromium not installed: `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"` (one-time per machine) |
| `DISCORD_BOT_TOKEN` env var | UAT-05 (live-Discord run only) | Operator-provided post-merge | per operator | WireMock-IT-only Phase-95-close is the no-token fallback (per D-95-10) |
| `app.upload-dir` resolvable to a writable dir | TeamCardService + SettingsGraphic + LineupGraphic disk reads | ✓ (configured in `application-{profile}.yml`) | per profile | — |

**Missing dependencies with no fallback:** None — Phase 95 is fully implementable on the current dev/CI environment.

**Missing dependencies with fallback:** Live Discord access is required only for UAT-05; the Phase-95 PR can ship green via WireMock-IT-only validation per D-95-10.

## Code Examples

### Multipart-POST request body shape (already-verified Phase 93 pattern)

```java
// Source: src/main/java/org/ctc/discord/DiscordWebhookClient.java line 61-103
public WebhookMessage executeMultipart(
        String webhookUrl, WebhookPayload payload, List<NamedAttachment> attachments)
        throws DiscordApiException {
    if (attachments.size() > MAX_ATTACHMENTS) { /* throw */ }
    if (attachments.isEmpty()) { return execute(webhookUrl, payload); }
    hostValidator.requireAllowed(webhookUrl);
    String payloadJson = objectMapper.writeValueAsString(payload);
    MultiValueMap<String, HttpEntity<?>> parts = new LinkedMultiValueMap<>();
    HttpHeaders payloadHeaders = new HttpHeaders();
    payloadHeaders.setContentType(MediaType.APPLICATION_JSON);
    parts.add("payload_json", new HttpEntity<>(payloadJson, payloadHeaders));
    for (int i = 0; i < attachments.size(); i++) {
        NamedAttachment att = attachments.get(i);
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_PNG);
        ByteArrayResource resource = new ByteArrayResource(att.bytes()) {
            @Override public String getFilename() { return att.filename(); }
        };
        parts.add("files[" + i + "]", new HttpEntity<>(resource, fileHeaders));
    }
    return execute(() -> forWebhookUrl(webhookUrl)
            .post().uri("")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(parts)
            .retrieve()
            .body(WebhookMessage.class));
}
```

### JSON-only PATCH (template for SCHEDULE embed re-edit)

```java
// Source: src/main/java/org/ctc/discord/DiscordWebhookClient.java line 105-115
public WebhookMessage editMessage(String webhookUrl, String messageId, WebhookPayload payload)
        throws DiscordApiException {
    hostValidator.requireAllowed(webhookUrl);
    return execute(() -> forWebhookUrl(webhookUrl)
            .patch()
            .uri("/messages/{messageId}", messageId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(WebhookMessage.class));
}
```

### WireMock-IT multipart assertion shape (template for `DiscordWebhookClientMultipartEditIT`)

```java
// Source: src/test/java/org/ctc/discord/DiscordWebhookClientMultipartIT.java line 64-86
wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/" + msgId))
        .withHeader("Content-Type", containing("multipart/form-data"))
        .withMultipartRequestBody(aMultipart("payload_json")
                .withHeader("Content-Type", containing("application/json"))
                .withBody(matchingJsonPath("$.content", containing("..."))))
        .withMultipartRequestBody(aMultipart("files[0]")
                .withHeader("Content-Type", equalTo("image/png")))
        .withMultipartRequestBody(aMultipart("files[1]")
                .withHeader("Content-Type", equalTo("image/png")))
        .willReturn(okJson("{\"id\":\"msg-2\",\"channel_id\":\"chan-1\"}")));
```
(Use `WireMock.patch(...)` instead of `WireMock.post(...)`.)

### SCHEDULE embed payload assembly

```java
// Source: design spec § 4.6 + DiscordTimestamps.java
LocalDateTime firstRaceTime = match.getRaces().stream()
        .map(Race::getDateTime).filter(Objects::nonNull)
        .min(Comparator.naturalOrder())
        .orElse(null);  // pre-flight should reject null

String dateField = firstRaceTime == null
        ? "_TBD_"
        : discordTimestamps.longDateTime(firstRaceTime)
                + " (" + discordTimestamps.relative(firstRaceTime) + ")";

List<EmbedField> fields = List.of(
        new EmbedField("Date", dateField, false),
        new EmbedField("Lobby Host", orTbd(match.getLobbyHost()), false),
        new EmbedField("Race Director", orTbd(match.getRaceDirector()), false),
        new EmbedField("Streamer", orTbd(match.getStreamer()), false));

WebhookPayload payload = new WebhookPayload(
        /* content */ null,
        List.of(new Embed("Match Schedule", /* description */ null, fields)));

private static String orTbd(String value) {
    return (value == null || value.isBlank()) ? "_TBD_" : value;
}
```

### Match-Detail button visibility (Thymeleaf template excerpt)

```html
<!-- Source: design spec § 4.3 + D-95-01, D-95-02, D-95-03b -->
<div class="discord-actions discord-actions--posts" th:if="${match.discordChannelId != null}">

  <!-- TEAM_CARDS — always visible while channel exists (Hybrid trigger D-95-01) -->
  <form th:if="${teamCardsPost == null}"
        th:action="@{/admin/matches/{id}/post-team-cards(id=${match.id})}" method="post">
    <button class="btn btn-primary btn-sm" data-testid="post-team-cards">Post Team Cards</button>
  </form>
  <form th:if="${teamCardsPost != null}"
        th:action="@{/admin/matches/{id}/post-team-cards(id=${match.id})}" method="post">
    <button class="btn btn-secondary btn-sm" data-testid="repost-team-cards">Re-Post Team Cards</button>
  </form>
  <form th:if="${teamCardsPost != null}"
        th:action="@{/admin/matches/{id}/refresh-team-cards(id=${match.id})}" method="post">
    <button class="btn btn-secondary btn-sm" data-testid="refresh-team-cards">↻ Refresh Cards</button>
  </form>

  <!-- SETTINGS — pre-flight gated (D-95-03b) -->
  <form th:if="${settingsPost == null and matchHasCompleteSettings}"
        th:action="@{/admin/matches/{id}/post-settings(id=${match.id})}" method="post">
    <button class="btn btn-primary btn-sm" data-testid="post-settings">Post Settings</button>
  </form>
  <span th:if="${settingsPost == null and not matchHasCompleteSettings}"
        class="btn btn-secondary btn-sm disabled"
        title="Configure settings for all races first">Post Settings</span>
  <!-- … LINEUPS, MATCH_RESULTS (with stale-label), SCHEDULE buttons analog … -->

</div>
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual operator copy/paste of PNGs into Discord | Webhook multipart POST | Phase 93 (INFRA-01) | Operator UX |
| Delete + repost on data change | Webhook-PATCH-edit-in-place via stored `message_id` | Phase 95 (POST-01) | No duplicate messages; preserves Discord-side reactions / pins / replies. |
| Separate buttons per race for Settings/Lineups | Single multipart-bundle button per type | Phase 95 (D-95-03) | Fewer DB rows; aligns with ROADMAP success criterion 1. |
| Auto-trigger on DB events | Button-triggered only (with the 2 explicit exceptions: Team-Cards auto-on-create per D-95-01, Schedule auto-edit per D-95-04) | v1.13 invariant D-Operator-Control | Operator maintains full control. |

**Deprecated / outdated:**
- The `discord_post` table was originally planned as Flyway V11 per design spec § 3.3, BUT Phase 94 Plan-04 took V11 for `matches.discord_channel_archived_at`. **Phase 95 uses V12** (the design spec is implicitly corrected by Plan history). Phase 96's planned V12 (`seasons.discord_*_thread_id`) **shifts to V13** when Phase 96 starts.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + AssertJ + WireMock 3.9.2 (test scope) + Playwright (compile scope, `@Tag("e2e")`) |
| Config file | `pom.xml` (Surefire / Failsafe / JaCoCo / `-Pe2e` profile) |
| Quick run command | `./mvnw -Dit.test=DiscordPostService*IT verify` |
| Full suite command | `./mvnw verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| POST-01 | `postOrEdit` POSTs when no row exists | integration | `./mvnw -Dit.test=DiscordPostServiceWireMockIT verify` | ❌ Wave 0 (Plan 95-01) |
| POST-01 | `postOrEdit` PATCHes when row exists | integration | same | ❌ Wave 0 (Plan 95-01) |
| POST-01 | `editMessageWithAttachments` multipart-PATCH wire-format (1, 2, 3, 10 attachments) | integration | `./mvnw -Dit.test=DiscordWebhookClientMultipartEditIT verify` | ❌ Wave 0 (Plan 95-01) |
| POST-01 | `editMessageWithAttachments` rejects > 10 attachments | unit / integration | same | ❌ Wave 0 (Plan 95-01) |
| POST-01 | `/admin/discord/posts` GET renders empty + populated + filtered states | integration | `./mvnw -Dit.test=DiscordPostFilterControllerIT verify` | ❌ Wave 0 (Plan 95-01) |
| POST-01 | Listing-page Playwright happy-path | e2e | `./mvnw verify -Pe2e -Dit.test=DiscordPostsListE2ETest` | ❌ Wave 0 (Plan 95-01) |
| POST-02 | `postTeamCards` happy-path with both cards on disk → multipart-POST with 2 PNG parts | integration | `./mvnw -Dit.test=DiscordPostServiceTeamCardsIT verify` | ❌ Wave 0 (Plan 95-02) |
| POST-02 | `postTeamCards` re-post → multipart-PATCH on stored message_id | integration | same | ❌ Wave 0 (Plan 95-02) |
| POST-02 | `postTeamCards` missing card → triggers `generateCard()` then posts | integration | same | ❌ Wave 0 (Plan 95-02) |
| POST-02 | Auto-post hook in `createMatchChannel` fires once after channel + webhook persist | integration | `./mvnw -Dit.test=DiscordChannelServiceAutoPostHookIT verify` | ❌ Wave 0 (Plan 95-02) |
| POST-02 | Auto-post failure leaves channel + webhook persisted (D-95-01a) | integration | same | ❌ Wave 0 (Plan 95-02) |
| POST-02 | Refresh-Team-Cards endpoint regenerates + re-posts | integration | `./mvnw -Dit.test=MatchControllerTeamCardsRefreshIT verify` | ❌ Wave 0 (Plan 95-02) |
| POST-02 | Match-Detail buttons appear correctly per state | e2e | `./mvnw verify -Pe2e -Dit.test=MatchDetailTeamCardsButtonsE2ETest` | ❌ Wave 0 (Plan 95-02) |
| POST-03 | `postSettings` builds N-attachment multipart with correct file naming | integration | `./mvnw -Dit.test=DiscordPostServiceSettingsBundleIT verify` | ❌ Wave 0 (Plan 95-03) |
| POST-03 | `postLineups` analog | integration | `./mvnw -Dit.test=DiscordPostServiceLineupsBundleIT verify` | ❌ Wave 0 (Plan 95-03) |
| POST-03 | Pre-flight predicates: all-complete / one-missing / empty-races (3 branches each for SETTINGS and LINEUPS) | unit | `./mvnw test -Dtest=DiscordPostServicePreFlightTest` | ❌ Wave 0 (Plan 95-03) |
| POST-03 | Pre-flight failure → `BusinessRuleException` → flash with `data-incomplete` category | integration | `./mvnw -Dit.test=MatchControllerPostSettingsPreFlightIT verify` | ❌ Wave 0 (Plan 95-03) |
| POST-03 | Match-Detail Settings/Lineups buttons appear/disable correctly | e2e | `./mvnw verify -Pe2e -Dit.test=MatchDetailSettingsLineupsButtonsE2ETest` | ❌ Wave 0 (Plan 95-03) |
| POST-04 | `postMatchResults` multipart-POST with `byte[]` from `generateMatchResults` | integration | `./mvnw -Dit.test=DiscordPostServiceMatchResultsIT verify` | ❌ Wave 0 (Plan 95-04) |
| POST-04 | Stale-detection: row.updatedAt < match.updatedAt → button-label flips to "Update" | integration (controller) | `./mvnw -Dit.test=MatchDetailMatchResultsStaleIT verify` | ❌ Wave 0 (Plan 95-04) |
| POST-04 | No-op `matchRepository.save()` does NOT advance `match.updatedAt` (or research finding documented) | integration | `./mvnw -Dit.test=MatchUpdatedAtNoopSaveIT verify` | ❌ Wave 0 (Plan 95-04) |
| POST-04 | Match-Detail Match-Results button (Post / Re-Post / Update labels) | e2e | `./mvnw verify -Pe2e -Dit.test=MatchDetailMatchResultsButtonE2ETest` | ❌ Wave 0 (Plan 95-04) |
| POST-05 | `postSchedule` builds Embed with 4 fields + `_TBD_` for blanks | integration | `./mvnw -Dit.test=DiscordPostServiceScheduleIT verify` | ❌ Wave 0 (Plan 95-04) |
| POST-05 | `DiscordTimestamps.longDateTime`+`relative` integrated correctly | integration | same | ❌ Wave 0 (Plan 95-04) |
| POST-05 | Auto-edit hook fires on host/RD/streamer change (3 branches: changed→PATCH; unchanged→no PATCH; SCHEDULE post missing→no PATCH) | integration | `./mvnw -Dit.test=MatchServiceScheduleEditHookIT verify` | ❌ Wave 0 (Plan 95-04) |
| POST-05 | Match-Detail Schedule button visibility (firstRaceTime != null) | e2e | `./mvnw verify -Pe2e -Dit.test=MatchDetailScheduleButtonE2ETest` | ❌ Wave 0 (Plan 95-04) |

### Sampling Rate

- **Per task commit:** `./mvnw -Dit.test={current-IT-class} verify` (~ 15-45s per IT)
- **Per wave merge:** Full Phase-95 IT suite — `./mvnw -Dit.test=DiscordPost*IT,MatchController*Post*IT,MatchService*ScheduleEdit*IT,DiscordChannelService*Auto*IT verify`
- **Per plan close:** `./mvnw verify -Pe2e` (full suite green; ~ 18 minutes CI E2E)
- **Phase gate:** `./mvnw verify -Pe2e` green + JaCoCo line coverage ≥ 88.88 % + SpotBugs `BugInstance` count = 0 + CodeQL gate-step exit 0 + `BackupSchemaGuardTest` still green (size 24, SCHEMA_VERSION 1) → invoke `/gsd-validate-phase 95`.

### Wave 0 Gaps

Per-plan (D-95-08 says each plan ships its own VALIDATION.md):

**Plan 95-01 Wave 0:**
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceWireMockIT.java` — POST-01 happy paths (POST + PATCH branches of `postOrEdit`)
- [ ] `src/test/java/org/ctc/discord/DiscordWebhookClientMultipartEditIT.java` — multipart-PATCH wire-format
- [ ] `src/test/java/org/ctc/discord/web/DiscordPostFilterControllerIT.java` — GET + filter binding
- [ ] `src/test/java/org/ctc/e2e/discord/posts/DiscordPostsListE2ETest.java` — Playwright listing page

**Plan 95-02 Wave 0:**
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceTeamCardsIT.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordChannelServiceAutoPostHookIT.java`
- [ ] `src/test/java/org/ctc/admin/controller/MatchControllerTeamCardsRefreshIT.java`
- [ ] `src/test/java/org/ctc/e2e/discord/posts/MatchDetailTeamCardsButtonsE2ETest.java`

**Plan 95-03 Wave 0:**
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceSettingsBundleIT.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceLineupsBundleIT.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java` (Mockito-only)
- [ ] `src/test/java/org/ctc/admin/controller/MatchControllerPostSettingsPreFlightIT.java`
- [ ] `src/test/java/org/ctc/e2e/discord/posts/MatchDetailSettingsLineupsButtonsE2ETest.java`

**Plan 95-04 Wave 0:**
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceMatchResultsIT.java`
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceScheduleIT.java`
- [ ] `src/test/java/org/ctc/domain/service/MatchServiceScheduleEditHookIT.java`
- [ ] `src/test/java/org/ctc/admin/controller/MatchDetailMatchResultsStaleIT.java`
- [ ] `src/test/java/org/ctc/domain/service/MatchUpdatedAtNoopSaveIT.java` (research-validation test for Pitfall 4)
- [ ] `src/test/java/org/ctc/e2e/discord/posts/MatchDetailMatchResultsButtonE2ETest.java`
- [ ] `src/test/java/org/ctc/e2e/discord/posts/MatchDetailScheduleButtonE2ETest.java`

**Framework install:** N/A — JUnit 5 + Mockito + AssertJ + WireMock + Playwright all already in `pom.xml`.

**Test count budget:** ~ 50-70 new tests across the 4 plans (in line with CONTEXT D-95-07 estimate).

## Security Domain

> Phase 95 is outbound-only HTTP to `discord.com` + DB writes to a new `discord_post` table. No new inbound surfaces beyond CSRF-protected `POST /admin/**` endpoints (already covered by the project default Spring Security config).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (admin profile only) | Spring Security `prod`+`docker` profiles; `dev`+`local` no auth (CLAUDE.md "Constraints") — Phase 95 inherits unchanged. |
| V3 Session Management | yes | Spring Session — unchanged. |
| V4 Access Control | yes | `/admin/**` requires admin role in `prod`/`docker`; Phase 95 endpoints follow the existing pattern. |
| V5 Input Validation | yes | `@Valid` + `BindingResult` on the new `DiscordPostFilterForm`. Snowflake-string regex validation per Phase 94 precedent for UUID/snowflake inputs. |
| V6 Cryptography | NO | No new cryptographic surface — webhook URLs are stored as-is (already `@ToString.Exclude` on `Match.discordChannelWebhookUrl` via Phase 94; same on `DiscordPost.webhookToken` per D-95-07). |
| V9 Communication | yes | `DiscordHostValidator.requireAllowed(webhookUrl)` enforces the `discord.com` allowlist on every outbound call (already wired in `DiscordWebhookClient`). |
| V10 Malicious Code | yes | SpotBugs + CodeQL gate steps from Phase 93. |
| V12 File and Resources | yes | Path-traversal guard required when reading `/uploads/...` URLs in `DiscordPostService.readPng` — mirror the existing `TeamCardService.encodeLogoBase64` pattern (lines 184-206: `file.startsWith(uploadDir)` check). |

### Known Threat Patterns for Spring Boot 4 + Discord + Thymeleaf

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Webhook URL / token in logs | Information disclosure | Existing log-pattern mask (Phase 93 INFRA-02) — verify `DiscordLogMaskingIT` still passes after Plan 95-01 lands; `@ToString(exclude = {"webhookToken"})` on `DiscordPost`. |
| SSRF via attacker-controlled webhook URL | Server-side request forgery | `DiscordHostValidator.requireAllowed(...)` enforces `discord.com` allowlist — already called in `executeMultipart` / `editMessage`; the new `editMessageWithAttachments` must also call it (Pitfall 9 below). |
| Path traversal via `/uploads/...` filename | Tampering | `Path.startsWith(uploadDir)` guard in the new `DiscordPostService.readPng` helper (mirror Phase 93 / 94 / TeamCardService precedent). |
| Mass-assignment via Form binding | Tampering | `DiscordPostFilterForm` DTO (not entity); `@ModelAttribute("form")` binding pattern from `DiscordConfigController`. |
| CSRF on new endpoints | Tampering | Spring Security default CSRF on all `POST /admin/**` — already in place from Phase 30. |
| Stored XSS via match.discordTeaser (free-text Markdown) | XSS | Match-Detail template uses `<pre class="markdown-source" th:text="${match.discordTeaser ?: '-'}">` (line 75 — `th:text` HTML-escapes). Phase 95 does not change the template's escape policy. |
| Rate-limit-burst → bot-ban | DoS | Existing `DiscordRateLimitInterceptor` (Phase 93) + per-bucket token-bucket. Phase 95's multipart-bundle Settings post sends ONE multipart with N attachments (1 HTTP call), not N HTTP calls — burst pattern is mitigated by the bundle decision (D-95-03). |

### Pitfall 9 (security-specific): the new PATCH method MUST call `hostValidator.requireAllowed`

The existing `executeMultipart` (line 72) and `editMessage` (line 107) both call `hostValidator.requireAllowed(webhookUrl)` as the FIRST line of the method body. The new `editMessageWithAttachments` MUST do the same — otherwise an attacker who controls the webhook-URL DB column could SSRF arbitrary hosts via the PATCH path. Plan 95-01 task verification must grep for the call.

## Sources

### Primary (HIGH confidence)

- `docs/superpowers/specs/2026-05-20-discord-integration-design.md` § 3.3 / 3.5 / 3.6 / 3.7 / 4.1 / 4.3 / 4.6 / 5 / 6 / 9 — authoritative design reference.
- `.planning/phases/95-match-channel-posts/95-CONTEXT.md` — 12 decisions D-95-01..12 + Claude's Discretion list.
- `src/main/java/org/ctc/discord/DiscordWebhookClient.java` — current Phase 93 client; the new multipart-PATCH method composes its existing POST + JSON-PATCH paths.
- `src/main/java/org/ctc/discord/service/DiscordChannelService.java` — Phase 94 channel-create flow, including the audit + cleanup + persist sequence that the Phase-95 auto-post hook extends.
- `src/main/java/org/ctc/domain/service/MatchService.java` — real method name is `updateDiscordFields(UUID, MatchForm)`, NOT `save(MatchForm)` (corrects a CONTEXT mis-reference).
- `src/main/java/org/ctc/discord/dto/{WebhookPayload, WebhookMessage, NamedAttachment, Embed, EmbedField}.java` — wire-shape records.
- `src/main/java/org/ctc/discord/exception/{DiscordApiException, DiscordApiExceptionMapper}.java` — sealed exception hierarchy + 4-category mapping.
- `src/main/java/org/ctc/admin/service/{TeamCardService, SettingsGraphicService, LineupGraphicService, MatchResultsGraphicService}.java` — graphic service return types (URL-string vs byte[]).
- `src/main/java/org/ctc/admin/controller/MatchController.java` — existing thin-controller + `applyErrorFlash` pattern.
- `src/main/java/org/ctc/backup/schema/BackupSchema.java` — confirms `org.ctc.domain.model.*` package filter (Discord entities structurally excluded — Landmine § 1).
- `src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java` — 24 MixIn registrations, all `org.ctc.domain.model.*`.
- `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java` + `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigGuardTest.java` — guard tests pin SCHEMA_VERSION = 1 + EXPORT_ORDER = 24.
- `src/test/java/org/ctc/discord/DiscordWebhookClientMultipartIT.java` — WireMock IT pattern template for the new PATCH test.
- `src/main/resources/templates/admin/match-detail.html` (line 22-58) — `.discord-actions--posts` placeholder from Phase 94 D-94-01.
- `src/main/resources/static/admin/css/admin.css` (lines 211-227, 379-394) — existing `.discord-actions` cluster + error-badge palette.
- `src/main/resources/db/migration/V10__add_matches_discord_and_scheduling_fields.sql` + `V11__add_matches_discord_channel_archived_at.sql` — Flyway migration shape precedent for V12.
- `.planning/REQUIREMENTS.md` § POST-01..05 (lines 50-58) — acceptance criteria.
- `.planning/STATE.md` — baselines (EXPORT_ORDER 24, SCHEMA_VERSION 1, JaCoCo 88.88%, CI E2E 17:39 ± 20%).
- `.planning/milestones/v1.13-ROADMAP.md` § "Phase 95" — success criteria + dependency graph.
- `CLAUDE.md` — project conventions (thin controllers, DTOs, no comment pollution, @Tag discipline, Lombok usage).

### Secondary (MEDIUM confidence)

- `.planning/phases/93-discord-foundation/93-CONTEXT.md` (referenced via CONTEXT canonical refs) — for the secret-discipline + per-plan VALIDATION pattern.
- `.planning/phases/94-team-roles-match-channel-lifecycle/94-CONTEXT.md` (referenced via CONTEXT canonical refs) — for D-94-01 (placeholder) + D-94-04 (ordering) + D-94-15 (bot-self-override) handoff.

### Tertiary (LOW confidence)

- Discord webhook PATCH documentation (publicly documented API; cross-referenced by community examples — wire-format pinned by WireMock IT to insulate the project from drift).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | [ASSUMED] Spring Data JPA's dirty-checking suppresses the UPDATE statement on no-op `repository.save()`, AND `@LastModifiedDate` does NOT advance when no UPDATE fires. | Pitfalls § 4 (no-op save behavior) | Stale-detection on POST-04 would flip the button label inappropriately. Plan 95-04 includes `MatchUpdatedAtNoopSaveIT` to empirically verify; if the assumption is wrong, the plan needs a different stale-signal (e.g. compare `match.races[].results[].updatedAt` max). |
| A2 | [ASSUMED] Discord webhook-PATCH on `/messages/{messageId}` accepts the same `files[i]` + `payload_json` multipart shape as POST, with no `?wait=true` query parameter required. | Pitfalls § 1 + Pitfalls § 5 | If wrong, multipart PATCH returns 400; WireMock IT catches it but live UAT-05 might surface a divergence. Mitigation: WireMock IT body-assertion + UAT-05 step 3 verifies edit-indicator visible. |
| A3 | [ASSUMED] The 12-value `DiscordPostType` enum can be declared in Plan 95-01 with all 12 values (TEAM_CARDS, SETTINGS, …, STANDINGS) even though Phase 95 only uses 5 — the other 7 are wired in Phase 96/97. Avoids a backward-incompatible enum bump. | Standard Stack (Phase-95 new files) + design spec § 3.3 | If wrong (e.g. enum-only-as-used pattern preferred), Plan 95-01 needs to remove the unused 7 and Phase 96/97 must re-add them. Low impact — refactor cost is small. |
| A4 | [ASSUMED] `DiscordPostRef` sealed-record-hierarchy compiles cleanly under Java 25 + Jackson (`@JsonInclude(NON_NULL)`) + JPA without requiring an explicit deserializer. The 4 permits don't have JPA mappings; the `DiscordPostRef` is a Java-only abstraction that gets flattened to (matchId, matchdayId, raceId, seasonId) via a `applyTo(DiscordPost)` helper. | Pattern 1 (postOrEdit) | If wrong, planner-discretion fallback (4-field plain record) is acceptable per D-95-12. |
| A5 | [ASSUMED] `/admin/discord/posts` is reachable via Spring MVC routing with the existing `admin/layout.html` (which Phase 93 set up for the Discord-Config page). No new layout file needed. | Standard Stack + Architecture Patterns (Project Structure) | If wrong, planner adds an `activeRoute="discord-posts"` model attribute analog to `DiscordConfigController.view`. Low impact. |
| A6 | [ASSUMED] `MatchService.updateDiscordFields(UUID, MatchForm)` is the right hook point for the Schedule auto-edit (NOT `MatchService.save(MatchForm)` as CONTEXT D-95-04 claims). Verified by reading the actual code (lines 64-72). | Pattern 4 (Pre/Post-Diff Hook) | If wrong (e.g. a different controller endpoint also writes schedule fields), the hook misses some changes. Mitigation: grep for `match.setLobbyHost\|setRaceDirector\|setStreamer` in `src/main/java` — only one production-code path mutates these (`MatchController.saveEdit` → `MatchService.updateDiscordFields`). |
| A7 | [ASSUMED] The webhook URL format `https://discord.com/api/webhooks/{webhook_id}/{webhook_token}` is stable enough that a regex `^https://discord\.com/api/webhooks/(\d+)/([^/]+)$` is safe to use in `DiscordPostService` for extracting `webhook_id` + `webhook_token` for the new DB row. | Don't Hand-Roll (Webhook-URL parsing) | If wrong (Discord changes URL shape), every new `DiscordPost` row's `webhook_id` / `webhook_token` columns are wrong. Mitigation: WireMock-IT pins the assertion; UAT-05 step 2 verifies the row has plausible values. |

## Open Questions

> **None block planning.** All operational questions have an Assumptions-Log entry above; the planner can proceed and Plan 95-NN tasks include the empirical verification.

1. **Should `editMessageWithAttachments` and `executeMultipart` share a private helper?**
   - What we know: The two methods differ by ~12 lines (the `.post().uri("")` vs `.patch().uri("/messages/{messageId}", messageId)` builder chain).
   - What's unclear: Sharing introduces a private `multipartCall(builder, parts)` method that may be over-engineered for ~12 LOC.
   - Recommendation: Plan 95-01 keeps them parallel (no shared helper); revisit if Phase 97 adds a 3rd multipart method.

2. **Should the Match-Detail page batch-fetch all 5 `DiscordPost` rows in one query, or do 5 separate lookups?**
   - What we know: `findByChannelIdAndPostTypeAndMatchId` is fast (5-FK-index lookups, < 5ms total).
   - What's unclear: Single-query optimization adds custom JPQL or `IN` clause complexity.
   - Recommendation: 5 separate lookups for Plan 95-04 (controller code is clearer); profile in Phase 98 only if Match-Detail page-load > 200ms.

3. **Should pre-flight predicates be exposed as service methods (`DiscordPostService.matchHasCompleteSettings(Match)`) or as `Match`-domain methods (`match.hasAllSettingsConfigured()`)?**
   - What we know: CLAUDE.md says "Keep Thymeleaf Templates Lean" — calculations in the service.
   - What's unclear: Domain-rich methods on `Match` are also clean, and avoid an additional service dependency in the controller.
   - Recommendation: Plan 95-03 puts them on `DiscordPostService` (per D-95-11 path boundary — `Match` entity is NOT in the Phase-95 edit list).

## Landmines for the Planner

> **These directly affect Plan 95-01..04 task decomposition. Read before writing tasks.**

### Landmine 1 — `BackupSchema` SCHEMA_VERSION + EXPORT_ORDER MUST NOT change

**CONTEXT D-95-07 claims:** `BackupSchema.SCHEMA_VERSION` bumps 1 → 2, `EXPORT_ORDER` grows 24 → 25, Plan 95-01 adds `DiscordPostMixIn` + `DiscordPostRestorer`.

**Reality (verified by code inspection):**
- `src/main/java/org/ctc/backup/schema/BackupSchema.java:42` — `entity.getJavaType().getPackage().getName().startsWith("org.ctc.domain.model")` is the structural filter. `DiscordPost` lives in `org.ctc.discord.model.*` per D-95-11. It is automatically excluded.
- `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java:29-34` — asserts `SCHEMA_VERSION == 1`. Bumping would require updating the test.
- `src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java:38-44` — asserts `EXPORT_ORDER.size() == 24`. Bumping would require updating the test.
- `src/test/java/org/ctc/discord/repository/DiscordGlobalConfigGuardTest.java:20-26` — Phase 93 introduced this **specifically to forbid** bumping EXPORT_ORDER when adding Discord entities. Quote: *"Phase 93 must not bump BackupSchema.EXPORT_ORDER; DiscordGlobalConfig is structurally excluded by the org.ctc.domain.model.* package filter."* The same invariant applies to `DiscordPost` per the same package filter logic.
- `.planning/STATE.md` "Baselines to Preserve" — *"BackupSchema.SCHEMA_VERSION: 1 (must remain 1 unless backup wire contract changes)"* + *"EXPORT_ORDER size: 24 entities (guard test active; Discord entities under `org.ctc.discord.*` are structurally excluded by the `org.ctc.domain.model.*` package filter per Phase 72 D-15)"*.
- `.planning/milestones/v1.13-ROADMAP.md:225-226` — same invariants.

**Planner action:**
1. **DROP** the SCHEMA_VERSION bump from Plan 95-01 scope.
2. **DROP** the `DiscordPostMixIn` and `DiscordPostRestorer` task items.
3. **DROP** the `BackupSchemaGuardTest` update task — the guard test stays as-is (asserting `== 1` and `== 24`) and continues to pass because `DiscordPost` is structurally excluded.
4. **ADD** a new task: *"Mirror `DiscordGlobalConfigGuardTest` for `DiscordPost` — `DiscordPostGuardTest` asserts that `BackupSchema.getExportOrder()` neither grows to 25 nor contains a row with `tableName == 'discord_post'`."* This is the Phase-95 analog of the Phase-93 guard test.
5. **DOCUMENT** in Plan 95-01 SUMMARY.md that the Phase-95 RESEARCH explicitly overrode CONTEXT D-95-07 here, with the 4-source citation chain above.

If the user later DECIDES (in a discussion-phase round) that operator-restored backups should include the `discord_post` table, that becomes a separate phase / decision with the full SCHEMA_VERSION bump ceremony (and would necessarily span Phase 93's `DiscordGlobalConfig` + Phase 95's `DiscordPost` + Phase 96's `seasons.discord_*_thread_id` together — three entities, one bump).

### Landmine 2 — Race ordering for the multipart bundle has no `raceNumber` field

**CONTEXT D-95-03 sample code:**
```java
.sorted(Comparator.comparingInt(Race::getRaceNumber))
```

**Reality:** `Race` has no `raceNumber` field or method. See `src/main/java/org/ctc/domain/model/Race.java` — the closest is `MatchResultsGraphicService.buildRaceRows` (line 92-114) which computes a per-render counter.

**Planner action:** Use `match.getRaces()` (already `@OrderBy("dateTime ASC NULLS LAST")` per `Match.java:48`) and the iteration index. Filename: `"settings-race-" + (i + 1) + ".png"` where `i` is the list index over `match.getRaces()`. Document this in the plan task and add an IT assertion that filenames are `1`-indexed in chronological dateTime order.

### Landmine 3 — `MatchService.save(MatchForm)` doesn't exist

**CONTEXT D-95-04 sample code:**
```java
Match before = matchRepository.findById(form.id()).orElseThrow();
...
Match saved = matchRepository.save(before);
```
calls a method `MatchService.save(form)`.

**Reality:** `MatchService` has `updateDiscordFields(UUID id, MatchForm form)` (line 64-72), not `save(MatchForm)`. The controller endpoint that handles the form-edit submission is `MatchController.saveEdit` (line 113-125) which calls `matchService.updateDiscordFields(id, form)`.

**Planner action:** Plan 95-04 hook target is `MatchService.updateDiscordFields(UUID, MatchForm)`. The pre/post-diff pattern works identically (see Pattern 4 above) — just adjust the method name in tasks + task verification.

### Landmine 4 — `Embed` record has no `color` field

**CONTEXT Claude's Discretion item:** "SCHEDULE-Embed-Color: Discord-Default vs. CTC-Branding-Hex".

**Reality:** `src/main/java/org/ctc/discord/dto/Embed.java` — `public record Embed(String title, String description, List<EmbedField> fields)`. No `color`.

**Planner action:** Either accept the discretion default (Discord native), or extend `Embed` to include `Integer color` in Plan 95-04 (≤ 5 LOC + 1 IT for the JSON serialization). Research recommends accepting the default for v1.13 — deferring color polish to Phase 98 keeps the v1.13 surface minimal.

### Landmine 5 — `Match` has no `final` field for MATCH_RESULTS button visibility

**CONTEXT D-95-04 partial claim:** *"Button visible wenn `match.final == true` (Stewarding abgeschlossen)."*

**Reality:** `Match` has `bye`, `homeScore`, `awayScore` — no `final` field. Stewarding completion is implicit (all races have results, scores are aggregated).

**Planner action:** Plan 95-04 visibility predicate for "Post Match Results" button is: **all `match.getRaces()` have non-empty `RaceResult` list**. This matches `MatchResultsGraphicService.buildRaceRows` (line 96-99) which skips races with empty results. Encode as `boolean matchCanRenderResults` on `DiscordPostService` (or a helper on `Match` — but D-95-11 forbids entity edits, so keep it in the service).

### Landmine 6 — The auto-post hook needs `REQUIRES_NEW` transaction OR exception swallowing

**See Pitfall 3 above.** Plan 95-02 task verification must include: assert that channel + webhook persist after `postTeamCards` failure (WireMock 5xx). The simplest fix is exception swallowing in the hook; the more defensible fix is `@Transactional(propagation = REQUIRES_NEW)` on `DiscordPostService.postTeamCards`. Planner picks; document choice in Plan 95-02 SUMMARY.md.

### Landmine 7 — The `webhook_token` column needs path-traversal-safe parsing

**See Pitfall 9 above + Don't-Hand-Roll § Webhook-URL-Parsing.** The new `DiscordPostService` MUST call `DiscordHostValidator.requireAllowed(webhookUrl)` BEFORE parsing the URL into `(webhook_id, webhook_token)`. The validator is a Spring `@Component` already injected into `DiscordWebhookClient`; Plan 95-01 wires the same bean into `DiscordPostService`.

### Landmine 8 — Auto-gen-on-demand for missing team cards is a 10-30s synchronous Playwright call

**D-95-02** says `postTeamCards` calls `teamCardService.generateCard()` synchronously when a card is missing. Playwright headless takes 10-30s per card. For a fresh match with no cached cards, the auto-post hook in Plan 95-02 will block `createMatchChannel` for up to 60s (2 cards × 30s).

**Planner action:**
- Document the worst-case latency in Plan 95-02 SUMMARY.md (operator UX).
- The HTTP request thread holds the auto-post for 60s — Tomcat default connection timeout is 60s; this is at the boundary. Consider running the auto-gen ASYNC in a `@Async` task, but YAGNI in v1.13 — operators have learned to wait for graphic generation. Document the latency, accept the synchronous behavior, revisit in Phase 98 if needed.
- The `Refresh Team Cards` link has the same latency; Plan 95-02's E2E test should use a stubbed `TeamCardService.generateCard` (Mockito `doReturn` or test-only `@MockBean`) to keep test runtimes < 30s per case.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries are in `pom.xml` and verified by 22+ existing Discord ITs.
- Architecture: HIGH — Patterns 1-5 are direct extensions of Phase 93/94 code that I read line-by-line.
- Pitfalls: HIGH — Pitfalls 1, 2, 5, 7, 9 are rooted in existing code or design-spec reading; Pitfall 3 is a Spring-transaction-semantics reading (HIGH confidence on the failure mode, MEDIUM on the preferred fix); Pitfall 4 requires empirical verification per Assumption A1.
- Landmines: HIGH — every landmine is a concrete code or test reference; Landmine 1 cites 5 sources for the SCHEMA_VERSION/EXPORT_ORDER contradiction.

**Research date:** 2026-05-22
**Valid until:** 2026-06-22 (1 month — stable phase, no upstream dependencies expected to drift)
