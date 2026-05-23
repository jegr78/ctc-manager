# Phase 96: Provisional Graphic + Forum Threads — Research

**Researched:** 2026-05-23
**Domain:** Discord forum-thread integration + per-Race PNG graphic generation + sealed-permit `DiscordPostService` extension on existing Phase-95 post-platform
**Confidence:** HIGH (codebase paths verified by Read; Discord-API contract cross-verified against current `docs.discord.com` v10 reference + WebSearch confirmation)

## Summary

Phase 96 ships **three sequential inline plans** on `gsd/v1.13-discord-integration` that complete the v1.13 milestone's missing two Discord capabilities: (1) per-Race provisional-scores PNG with Match-Detail multipart-button (Plan 96-01 / GRAFX-01); (2) Flyway V13 + Discord-Config + Season-Edit Discord-Integration section + Thread-Picker modal (Plan 96-02 / FORUM-01); (3) `DiscordPostService.postOrEdit` extension to all 4 `DiscordPostRef`-permits with `@Nullable String threadId` plumbing + Race-Detail forum-post button + Auto-Unarchive (Plan 96-03 / FORUM-02). All three plans land on the existing Phase-95 post-platform (`DiscordPostService.postOrEdit`, `DiscordWebhookClient.executeMultipart`/`editMessageWithAttachments`, sealed-`DiscordApiException`-hierarchy, `discord_post` entity, Multipart-Bundle pattern from Plan 95-03) with zero new production dependencies.

The codebase audit confirms a low-risk blueprint: `DiscordPostRef` already declares all four sealed permits (`MatchRef`, `MatchdayRef`, `RaceRef`, `SeasonRef`) with full record implementations — Phase 95 only `instanceof`-gates them at the postOrEdit entry point. The `DiscordWebhookClient` has exactly **5 call-sites** of its message-methods and all 5 are inside `DiscordPostService.postOrEdit` itself, so Variant A (add `@Nullable threadId` overloads) genuinely costs zero refactor on Phase-95 production code. `ChannelModifyRequest` is a 2-field record that needs widening for the unarchive payload. `Thread` DTO is a bare 3-field record with `@JsonIgnoreProperties(ignoreUnknown=true)` so adding `flags` + `thread_metadata` fields is a pure additive change.

**Primary recommendation:** Implement exactly as CONTEXT.md describes. Variant A overloads on `DiscordWebhookClient` (D-96-FOR-3a), defensive Auto-Unarchive GET-then-PATCH (D-96-FOR-4, even though Discord's webhook+`thread_id` may auto-unarchive — see Pitfall 3 below), Form-inline Season-Edit section (D-96-FOR-2b — same `.card` style as existing Season-Form sections), Backup MixIn = export thread-IDs / skip webhook-URLs (D-96-07).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|---|---|---|---|
| Render Provisional PNG (per-Race) | API/Backend (`org.ctc.admin.service`) | — | Pixel-accurate graphic via Thymeleaf + Playwright Chromium screenshot; sibling pattern to existing `MatchResultsGraphicService` + `ResultsGraphicService` |
| Multipart upload to Discord webhook | API/Backend (`org.ctc.discord.DiscordWebhookClient`) | — | Spring `RestClient` + `LinkedMultiValueMap<String,HttpEntity<?>>` (no JS, no client-side upload) |
| Forum-thread listing (active + archived) | API/Backend (`DiscordForumService` NEW) | — | Server-side combine of `DiscordRestClient.listActiveThreads(guildId)` (filtered by `parentId`) + `listArchivedThreads(channelId)` (sorted client-side) |
| Thread-Picker Modal (UI) | Frontend Server (Thymeleaf SSR) | — | Server-rendered modal in `season-form.html` mirrors Phase-94 D-94-06 Move-to-Archive pattern; no client-side JS framework |
| Auto-Unarchive before forum-thread post | API/Backend (`DiscordPostService.postOrEdit`) | — | Defensive: `DiscordRestClient.fetchChannel(threadId)` → check `thread_metadata.archived` → `modifyChannel(threadId, ChannelModifyRequest.unarchive())` → POST |
| Flyway V13 (4 columns) | Database/Storage | — | 2 ADD COLUMN on `seasons` + 2 ADD COLUMN on `discord_global_config`; nullable, no index (lookup runs via `seasons.id` PK and `discord_global_config.id=1` singleton row) |
| Backup wire-contract | API/Backend (`org.ctc.backup.serialization`) | — | `SeasonMixIn` (`@JsonIgnoreProperties` add nothing — thread-IDs export); `DiscordGlobalConfigMixIn` (NEW or extend pattern — webhook-URLs ignored as secrets) |

## Phase Requirements

| ID | Description | Research Support |
|---|---|---|
| **GRAFX-01** | New `ProvisionalScoresGraphicService` (per-Race PNG analog to `MatchResultsGraphicService`); template `provisional-scores-render.html` under `*-render.html` convention; "Post Provisional Scores" button on Match-Detail visible when race-result data exists but match not yet final | `AbstractGraphicService` (template+Playwright pipeline) is reusable verbatim; `MatchResultsGraphicService` (158 LOC, lines 22-89 = render path) is the structural blueprint; existing Phase-95 `.discord-actions--posts` cluster + `matchHasCompleteSettings`-style pre-flight pattern are reused; Multipart-bundle path through `postOrEdit(..., DiscordPostRef.match(match))` is **already working** in Phase 95 |
| **FORUM-01** | Flyway V13 adds `seasons.discord_race_results_thread_id` + `seasons.discord_standings_thread_id`; Season-Edit "Discord Integration" section with 2 thread-linker widgets (Link existing / Unlink — NO Create-new per D-96-FOR-1c) | V12 = Phase 95 `discord_post` (verified by `ls migration/`); V13 slot is next; `DiscordRestClient.listActiveThreads(guildId)` returns ALL guild threads — Plan 96-02 must client-side-filter by `parentId == forumChannelId`; `listArchivedThreads(channelId)` returns the per-channel public-archived list; existing Phase-94 D-94-06 Move-to-Archive Modal is the UI blueprint |
| **FORUM-02** | "Post Race Result to Forum-Thread" button on Race-Detail; Webhook POST with `?thread_id={id}` to `season.discordRaceResultsThreadId`; Auto-Unarchive logic (GET→PATCH if archived→POST) applies to **forum-thread posts only**, NOT to PROVISIONAL_SCORES (D-96-GRX-1c clarifies the ROADMAP wording ambiguity) | `DiscordPostRef` permits already exist (verified — see `DiscordPostRef.java` lines 1-149); `DiscordPostService.postOrEdit` lines 286-290 throws `UnsupportedOperationException` for non-MatchRef branches — Plan 96-03 replaces that guard with a sealed-`switch` over all 4 permits; `DiscordWebhookClient` has exactly 5 callsites of message-methods all inside `DiscordPostService` so threadId-Variant-A is genuinely zero-blast-radius |

## Project Constraints (from CLAUDE.md)

- **Language**: Communication German, all code/docs/UI English (CLAUDE.md § Language).
- **JaCoCo line coverage** ≥ 82% absolute floor (CLAUDE.md § Constraints); Phase 95 ended at 88.61% — Phase 96 must hold ≥ 88.61% (informal commitment per D-96-07).
- **Flyway invariant**: V1-V12 immutable, only V13+ allowed. H2 + MariaDB compatible (no CHECK constraints, no LONGTEXT — see V8 file-header conventions).
- **OSIV stays enabled**: lazy-init OK in Thymeleaf templates, no manual touch in controllers needed for Phase 96.
- **Backward compatibility**: no breaking changes to existing URLs/endpoints. New endpoints additive only.
- **Playwright compile-scope**: stays — used at runtime by `AbstractGraphicService.renderScreenshot`.
- **Keep controllers thin** (CLAUDE.md § Architectural Principles): `MatchController.postProvisional`, `RaceController.postRaceResultToForum`, `SeasonController.linkThread/unlinkThread` all delegate to services.
- **DTOs not entities**: `DiscordConfigForm` extends for 2 new fields; `SeasonForm` (or NEW `SeasonDiscordForm`) for thread-IDs — never bind `Season` entity directly.
- **No fallback calculations**: provisional-data + thread-id pre-flight at service-layer predicate methods.
- **Lean Thymeleaf templates**: no SpEL list-projections; data prepared in service.
- **No inline styles**: `.btn-provisional` etc. land in `admin.css` if needed.
- **Test data prefix**: E2E tests in `TestDataService` use `T-`/`Test_` prefixes.
- **`@Tag` discipline**: `@Tag("integration")` on `*IT.java`, `@Tag("e2e")` on `org.ctc.e2e.*` classes, untagged on Mockito-only `*Test.java`.
- **RaceLineup as source of truth**: Phase 96 only reads via `race.results` (RaceResult) for the provisional graphic — not RaceLineup itself (RaceLineup drives lineups graphic, not results).
- **No Flyway-mutation**: V13 file's content is locked the moment it ships in Plan 96-02.
- **No comment pollution**: no Phase/Plan/Task references in source; no Block-Javadoc on getters; only single-line WHY comments for non-obvious workarounds. Applies equally to subagents (none used — all inline per D-96-05).
- **Static Analysis**: SpotBugs 0 BugInstance + CodeQL exit 0 — both blocking gates inside `./mvnw verify`.
- **GSD skill prefix**: dash-form `/gsd-…` only (legacy colon-form forbidden in active planning files).

## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-96-GRX-1** — Provisional layout = "Anlehnung an MatchResults + per-Race-Detail-Spalten"; iterative `playwright-cli` per `[[feedback-graphic-pixel-positioning]]` + `[[feedback-graphic-design-iteration]]`.
- **D-96-GRX-1a** — **Per-Race-PNG, not per-Match.** Each race in the match produces its own provisional graphic. Layout: match-card-header + 2 team-blocks (home/away) with per-driver detail (`Driver | Position | Quali | FL | Pts-Race | Pts-Quali | Pts-FL | Total`) + Overall-Row per block.
- **D-96-GRX-1b** — **Multipart-Bundle on Match-Detail (1 Discord-Message, N PNGs).** Analog Phase 95 D-95-03 Settings/Lineups; one click posts all completed races as N-attachment-multipart-bundle to the match-channel webhook; Re-Post-PATCH replaces all attachments atomically; 1 `discord_post` row with `match_id` + `PROVISIONAL_SCORES`-type.
- **D-96-GRX-1c** — **Provisional-Target = match-channel only, NEVER forum-thread.** Auto-Unarchive logic applies only to `RACE_RESULTS`, never to `PROVISIONAL_SCORES`. Phase 96-03 tests explicitly assert no `?thread_id=` for `PROVISIONAL_SCORES`.
- **D-96-FOR-1** — Operator pastes 2 Webhook-URLs in `discord-config` (`raceResultsForumWebhookUrl` + `standingsForumWebhookUrl`); manual Discord-client setup.
- **D-96-FOR-1b** — Existing channel-IDs from V8 stay (used for thread enumeration on the Link-modal).
- **D-96-FOR-1c** — **NO "Create new Thread..." Modal.** Operator creates threads manually per season in Discord-client.
- **D-96-FOR-2** — Modal-Picker with Auto-Pre-Select of the pinned thread; sort: pinned > active (last_message_timestamp desc) > archived (last_message_timestamp desc).
- **D-96-FOR-2a** — Existing `Thread` DTO gets `pinned` flag; planner picks `flags`-bitfield vs `thread_metadata.archived` based on Discord-API actual-response shape.
- **D-96-FOR-2b** — Season-**Edit**-Page-Location (not Season-Detail); form-inline vs dedicated sub-page = planner-discretion.
- **D-96-FOR-3** — `DiscordPostService.postOrEdit` extended to all 4 `DiscordPostRef`-permits (Phase 96 implements RaceRef + SeasonRef; MatchdayRef stays Phase-97-scope).
- **D-96-FOR-3a** — **Variant A**: `?thread_id={id}` via method-overload (NOT WebhookTarget-wrapper-record); zero-refactor on Phase-95 callsites.
- **D-96-FOR-3b** — `DiscordPostRef.RaceRef` + `SeasonRef` already exist as sealed-permits (CONTEXT slightly underdescribes — they are fully implemented in `DiscordPostRef.java`; Plan 96-03 only switches the `instanceof`-guard in `postOrEdit` to a sealed-`switch`).
- **D-96-FOR-3c** — Pre-Flight Predicates on Race-Detail button: (a) `race.results.isEmpty() == false`, (b) `season.discordRaceResultsThreadId != null`, (c) `discordGlobalConfig.raceResultsForumWebhookUrl != null`.
- **D-96-FOR-3d** — `ResultsGraphicService.generateResultsBytes(Race) → byte[]` (NEW; existing String-variant unchanged).
- **D-96-FOR-4** — Auto-Unarchive before every forum-thread post; **NO Re-Archive** after (Discord's natural Inactivity-Auto-Archive handles it).
- **D-96-FOR-4a** — Auto-Unarchive applies to ALL forum-thread posts (Race-Result FORUM-02, Matchday-Overview/Power-Rankings Phase 97 POST-07, Standings Phase 97 POST-08); centrally implemented in `DiscordPostService.postOrEdit` in Plan 96-03 and reused by Phase 97.
- **D-96-05** — Three plans sequential inline on `gsd/v1.13-discord-integration`; mirrors Phase 92/93/94/95 + Design-Spec § 5; Wave-Pause + Mobile-Sweep after each plan; **no worktrees, no subagents** per `[[feedback-inline-sequential-execution]]`.
- **D-96-06** — Rolling v1.13 Milestone-PR continues; squash-subject locked: `feat(v1.13): discord integration & carry-forwards`.
- **D-96-07** — Standard quality gates unchanged (JaCoCo ≥ 88.88%, SpotBugs 0, CodeQL exit 0, `EXPORT_ORDER` stays 25, `SCHEMA_VERSION` stays 2, Flyway V1-V12 immutable, V13 lands here).
- **D-96-08** — Per-Plan Nyquist `VALIDATION.md` (3 plans, 3 files).
- **D-96-09** — `@Tag` Convention per CLAUDE.md; explicit test-class mapping in CONTEXT.
- **D-96-10** — WireMock-IT-only Phase-96-Close; **UAT-06 (Live Provisional + Forum-Thread Lifecycle)** staged as STATE.md Pending UAT for operator-run BEFORE Phase 97 POST-06 start.
- **D-96-11** — Production-Path-Boundary: explicit allow-list of files Phase 96 may touch (15 specific paths).

### Claude's Discretion

- **`Thread` DTO `pinned`-Detection Mechanic** (D-96-FOR-2a) — `thread.flags` (bitfield, PINNED = `1 << 1` = 2) vs `thread.thread_metadata.archived` boolean; planner chooses based on actual Discord-API response shape (see Pitfall 5 below; recommendation: BOTH — `flags` for pinned, `thread_metadata` for archived).
- **Season-Edit-Section-Location** (D-96-FOR-2b) — Form-inline below existing Season-Form fields VS dedicated sub-page `/admin/seasons/{id}/discord-integration`; planner picks based on form-size + Mobile-UX (recommendation: form-inline section, same `.card` style as existing fields, ≤ 4 form-groups added).
- **`SeasonMixIn` + `DiscordGlobalConfigMixIn` Backup-Wire-Contract** (D-96-07) — Default recommendation: **export thread-IDs (Saison-Identity), DO NOT export webhook-URLs (Secret-Discipline)**; the v1.10 backup wire-contract precedent treats webhook-secrets as out-of-backup.
- **`DiscordWebhookClient` Overload-Strategy** (D-96-FOR-3a) — Variant A Method-Overloads (recommended ✓) vs Variant B WebhookTarget-Wrapper-Record (cleaner long-term, but refactor-risiko). **Locked at Variant A in CONTEXT.**
- **`ResultsGraphicService.generateResultsBytes` Implementation Variant** (D-96-FOR-3d) — new method (recommended, side-effect-free, byte[]-return) vs refactor of existing `generateResults` with shared-private-helper. Recommendation: **shared-private-`buildHtml(Race) → String` helper extracted from existing path; `generateResults` writes the file (existing behaviour); `generateResultsBytes` calls `renderScreenshot` into a `Files.createTempFile` then `Files.readAllBytes` + `deleteIfExists` (analog `MatchResultsGraphicService.generateMatchResults` lines 80-88)**.
- **Visual-Regression Snapshot for ProvisionalScoresGraphicService** (Plan 96-01 optional) — pixel-hash compare against `.screenshots/96-01/provisional-reference.png`. Recommendation: **defer to Phase 98 polish** (avoid blocking Plan 96-01 close on a flaky pixel test).

### Deferred Ideas (OUT OF SCOPE)

- **Bot-side "Create new Thread..." Workflow** — `DiscordForumService.createThread()` not implemented; tracked as DISC-FUTURE for v1.14+.
- **Re-Archive-After-Post Configurability** — `app.yml` flag or per-click modal-option, deferred to v1.14 only if operator-workflow ever requires it.
- **Visual-Regression-Snapshot-Test for ProvisionalScoresGraphicService** — pixel-hash compare, deferred to Phase 98.
- **`DiscordForumService.createThread()` Implementation** — DTOs (`ThreadCreateRequest`) + REST-Client-Method exist from Phase 93/94 but no service-wrapper + no UI. Deferred.
- **`DiscordPostRef.MatchdayRef`-Permit-Implementation** — Phase 97 POST-07 implements MatchdayRef.
- **Webhook-URL-Export in Backup-Wire-Contract** — default-recommendation: DO NOT export (Secret-Discipline).
- **Mobile-Viewport `.card`-Overflow on Season-Edit after Discord-Integration-Section-Add** — surface-only in Plan 96-02; fix lands in Phase 98 polish-CSS-sweep.

## Standard Stack

### Core (Phase 96 reuses existing v1.13 stack — zero new dependencies)

| Library | Version | Purpose | Why Standard |
|---|---|---|---|
| Spring `RestClient` | Spring 6.1+ (via Spring Boot 4.x) | All Discord HTTP I/O | Established in Phase 93 INFRA-01 [VERIFIED: codebase grep — `DiscordRestClient` constructor injects `RestClient bot`] |
| Spring `LinkedMultiValueMap` | Spring Core | Multipart bundle assembly | Established in Phase 95 D-95-03 [VERIFIED: `DiscordWebhookClient.java` lines 81-105 use `LinkedMultiValueMap<String, HttpEntity<?>>` with `payload_json` + `files[i]` parts] |
| Spring `UriComponentsBuilder` | Spring Web | `?thread_id={id}` query-param append on webhook URLs | Already imported pattern in `DiscordWebhookClient.forWebhookUrl()` (line 173-179) [VERIFIED: codebase grep] |
| Playwright Java | already compile-scope | Graphic screenshot rendering | `AbstractGraphicService.renderScreenshot` already runs Chromium headless [VERIFIED: `AbstractGraphicService.java` lines 34-49] |
| Thymeleaf | Spring Boot starter | `provisional-scores-render.html` template | Conv. for all `*-render.html` graphic templates [VERIFIED: `match-results-render.html` + `results-render.html` patterns] |
| Lombok | already compile-scope | `@Getter @Setter @NoArgsConstructor @ToString(exclude=…)` on entities; `@Slf4j @RequiredArgsConstructor` on services | CLAUDE.md § Lombok Usage; `lombok.config` has the SpotBugs-suppression line per Phase-86 invariant [VERIFIED: `DiscordGlobalConfig.java` already uses this exact shape] |
| Spring Data JPA derived queries | Spring Boot starter | 3 new repo methods (`findByChannelIdAndPostTypeAndRaceId` / `…AndSeasonId` / `…AndMatchdayId`) | Existing `findByChannelIdAndPostTypeAndMatchId` is the precedent [VERIFIED: `DiscordPostRepository.java` line 13] |
| Jakarta Validation | Spring Boot starter | `@URL`-validation on 2 new webhook-URL fields in `DiscordConfigForm`; reuse existing `WEBHOOK_REGEX` static [VERIFIED: `DiscordConfigForm.java` line 15] | Same `@Pattern` + `WEBHOOK_REGEX` as `announcementWebhookUrl` keeps the contract uniform |
| WireMock (test-scope) | already test-scope | All Discord-API integration tests | `DiscordPostServiceWireMockIT.java` is the template (lines 45-57: `WireMockExtension.newInstance().options(options().dynamicPort()).build()`) [VERIFIED] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|---|---|---|
| Variant A overloads on `DiscordWebhookClient` (recommended) | Variant B `WebhookTarget(url, threadId)` wrapper-record | Variant B cleaner long-term but requires editing all 5 Phase-95 callsites + ~10 IT test files [Variant A locked by D-96-FOR-3a] |
| `ResultsGraphicService.generateResultsBytes` (NEW byte[] method) | Refactor `generateResults` to return both String + byte[] | Refactor adds a 2-return-shape on a stable service — recommendation: keep additive, extract private `buildHtml` helper, reuse |
| `Thread.flags` bitfield (PINNED = 2) | `thread_metadata.archived` only (skip pinned detection) | If Bot-Permission insufficient for `flags` → fallback to no-pre-select; **recommendation: include both — they answer different questions (archived≠pinned)** |
| Sealed-`switch` over all 4 permits in postOrEdit | `instanceof` chain | Java 25 has exhaustive `switch` over sealed-permits; compiler error if a permit is forgotten — **strictly safer** |

**Installation:** none (zero new production dependencies per `D-No-New-Deps`).

**Version verification:** all libraries verified by codebase grep against `pom.xml` indirectly — Spring Boot 4.x manages Spring 6.1+, Thymeleaf, Lombok, WireMock, Playwright versions transitively (CLAUDE.md § Technology Stack).

## Package Legitimacy Audit

> Phase 96 installs **zero new external packages** (D-No-New-Deps). slopcheck not run because no install commands are emitted. All production code reuses existing Phase 93/95 dependencies already in `pom.xml`.

| Package | Disposition |
|---|---|
| (none — zero new dependencies) | N/A |

**Packages removed due to slopcheck [SLOP] verdict:** none.
**Packages flagged as suspicious [SUS]:** none.

## Architecture Patterns

### System Architecture Diagram

```
                                    ┌─────────────────────────────────────┐
                                    │   Admin Operator (browser)          │
                                    └────────────────┬────────────────────┘
                                                     │ HTTPS
                                                     ▼
   ┌───────────────────────────────────────────────────────────────────────────────────────┐
   │                              CTC Manager (Spring Boot 4.x)                              │
   │                                                                                         │
   │  ┌─Match-Detail──────┐     ┌─Race-Detail───┐     ┌─Season-Edit───────┐                  │
   │  │ Post Provisional  │     │ Post Race     │     │ Discord Integ.    │                  │
   │  │ (Multipart, N×PNG)│     │ to Forum      │     │ Thread Picker     │                  │
   │  └────────┬──────────┘     └───────┬───────┘     └────────┬──────────┘                  │
   │           │ POST                  │ POST                 │ POST                          │
   │           ▼                       ▼                      ▼                              │
   │  MatchController          RaceController         SeasonController                       │
   │  postProvisional/         postRaceResult-        link/unlinkThread                      │
   │  refreshProvisional        ToForum                                                       │
   │           │                       │                      │                              │
   │           ▼                       ▼                      ▼                              │
   │  ┌─────────────────────────────────────────────────────────────────────────────────┐    │
   │  │  Service Layer                                                                   │    │
   │  │  ┌──────────────────────────┐  ┌──────────────────────────────────────────────┐  │    │
   │  │  │ ProvisionalScoresGraphic │  │ DiscordPostService.postOrEdit               │  │    │
   │  │  │ Service (NEW Plan 96-01) │  │  (sealed-switch over 4 RefBranches +         │  │    │
   │  │  │  ↳ extends AbstractGraph │  │   @Nullable threadId)                       │  │    │
   │  │  │    -> Thymeleaf+Playwrt  │  │  ↳ unarchiveIfArchived(threadId) (Plan 96-03)│ │    │
   │  │  └──────────┬───────────────┘  └─────────────┬────────────────────────────────┘  │    │
   │  │             │ byte[]                          │                                    │    │
   │  │             └──────────────┬──────────────────┘                                    │    │
   │  │                            │                                                       │    │
   │  │             ┌──────────────▼──────────────────────────┐                            │    │
   │  │             │  DiscordForumService (NEW Plan 96-02)   │                            │    │
   │  │             │  listThreads(forumChannelId)            │                            │    │
   │  │             │   ↳ combines listActive (filter parent) │                            │    │
   │  │             │     + listArchived (channel-scoped)     │                            │    │
   │  │             │   ↳ sorts: pinned > active > archived   │                            │    │
   │  │             └──────────────┬──────────────────────────┘                            │    │
   │  │                            │                                                       │    │
   │  │             ┌──────────────▼──────────────────────────┐    ┌──────────────────┐  │    │
   │  │             │  DiscordRestClient (existing P93)        │    │ Discord-         │  │    │
   │  │             │  + DiscordWebhookClient (P93+96-03)      │───►│ HostValidator    │  │    │
   │  │             │   ↳ executeMultipart(url, payload,       │    │ (allowlist)      │  │    │
   │  │             │      attachments, @Nullable threadId)    │    └──────────────────┘  │    │
   │  │             │   ↳ editMessageWithAttachments(…+thrId)  │                            │    │
   │  │             │   ↳ fetchChannel(threadId) [unarchive]   │                            │    │
   │  │             │   ↳ modifyChannel(threadId, unarchive()) │                            │    │
   │  │             └──────────────┬──────────────────────────┘                            │    │
   │  │                            │                                                       │    │
   │  │                            ▼                                                       │    │
   │  │             ┌──────────────────────────────────────────┐                            │    │
   │  │             │ DiscordRateLimitInterceptor (P93)        │                            │    │
   │  │             │  ↳ X-RateLimit-* token-bucket            │                            │    │
   │  │             │  ↳ 429 retry (max 3) + 5xx backoff       │                            │    │
   │  │             └──────────────┬──────────────────────────┘                            │    │
   │  │                            │                                                       │    │
   │  │                  ┌─────────▼──────────┐  ┌──────────────────────────┐              │    │
   │  │                  │ DiscordPost-       │  │ MariaDB (prod) /         │              │    │
   │  │                  │ Repository         │──│ H2 (dev/test)            │              │    │
   │  │                  │ (+3 derived queries)│  │ Flyway V13               │              │    │
   │  │                  └────────────────────┘  └──────────────────────────┘              │    │
   │  └─────────────────────────────────────────────────────────────────────────────────┘    │
   └───────────────────────────────────┬──────────────────────────────────────────────────┘
                                       │ HTTPS (api/v10)
                                       ▼
   ┌──────────────────────────────────────────────────────────────────────────────────────┐
   │  Discord API (api/v10)                                                                 │
   │  • POST /webhooks/{id}/{token}?thread_id={tid}   ←──── forum-thread route (FORUM-02)  │
   │  • PATCH /webhooks/{id}/{token}/messages/{mid}?thread_id={tid}   ←─── thread edit     │
   │  • GET   /guilds/{gid}/threads/active            ←──── active threads (FORUM-01)      │
   │  • GET   /channels/{cid}/threads/archived/public ←──── archived threads (FORUM-01)    │
   │  • GET   /channels/{tid}                         ←──── archive-status check (FORUM-02)│
   │  • PATCH /channels/{tid}  body: {archived:false} ←──── unarchive (FORUM-02)           │
   └──────────────────────────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure (additions only)

```
src/main/java/org/ctc/
├── admin/
│   ├── service/
│   │   ├── ProvisionalScoresGraphicService.java       # NEW (Plan 96-01)
│   │   └── ResultsGraphicService.java                 # ADD generateResultsBytes(Race) → byte[]
│   └── controller/
│       ├── MatchController.java                       # ADD POST /post-provisional
│       ├── RaceController.java                        # ADD POST /post-race-result-to-forum
│       └── SeasonController.java                      # ADD POST /link-thread, /unlink-thread
├── discord/
│   ├── DiscordWebhookClient.java                      # ADD ?thread_id= overloads (Plan 96-03)
│   ├── service/
│   │   ├── DiscordForumService.java                   # NEW (Plan 96-02)
│   │   └── DiscordPostService.java                    # ADD postProvisionalScores + sealed-switch + unarchiveIfArchived
│   ├── repository/
│   │   └── DiscordPostRepository.java                 # ADD 3 derived queries
│   ├── dto/
│   │   ├── Thread.java                                # ADD flags + thread_metadata
│   │   └── ChannelModifyRequest.java                  # ADD archived field + unarchive() factory
│   └── model/
│       └── DiscordGlobalConfig.java                   # ADD 2 webhook-URL fields + @ToString.Exclude
├── domain/
│   └── model/
│       └── Season.java                                # ADD 2 thread-ID fields
└── backup/serialization/
    ├── SeasonMixIn.java                               # MAYBE extend (export thread-IDs)
    └── DiscordGlobalConfigMixIn.java                  # NEW (if absent — secret-skip webhook-URLs)

src/main/resources/
├── db/migration/
│   └── V13__add_seasons_discord_threads_and_forum_webhooks.sql   # NEW (Plan 96-02)
├── templates/admin/
│   ├── provisional-scores-render.html                 # NEW (Plan 96-01)
│   ├── match-detail.html                              # ADD Provisional buttons
│   ├── race-detail.html                               # ADD Discord-actions cluster
│   ├── season-form.html                               # ADD Discord-Integration section + Modal
│   └── discord-config.html                            # ADD 2 webhook-URL form-groups
└── static/admin/css/
    └── admin.css                                      # MAYBE add .discord-integration / modal styles
```

### Pattern 1: `*GraphicService extends AbstractGraphicService implements TemplateManageable`

**What:** All graphic-renderer services in `org.ctc.admin.service` follow this pattern: template + render + custom-template-override + byte[] OR uploads-URL output.

**When to use:** Plan 96-01 follows it for `ProvisionalScoresGraphicService`; Plan 96-03 extends `ResultsGraphicService` with a 2nd output-shape (byte[]).

**Example** (verified from `MatchResultsGraphicService.java` lines 36-89, lightly compressed):
```java
// Source: src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java
public byte[] generateMatchResults(Match match) throws IOException {
    if (match.getRaces().isEmpty()) throw new IllegalStateException("Match has no races");
    // ... validation
    String homeCardBase64 = encodeCardBase64(buildCardPath(seasonId, homeTeam.getShortName()));
    var ctx = new Context();
    ctx.setVariable("homeCardBase64", homeCardBase64);
    ctx.setVariable("ctcLogoBase64", encodeClasspathResource(CTC_LOGO_CLASSPATH, "image/png"));
    ctx.setVariable("fontBase64", encodeClasspathResource(FONT_CLASSPATH, "font/woff2"));
    // ... more ctx vars
    String html = renderTemplate(ctx);
    Path tempFile = Files.createTempFile("match-results-", ".png");
    try {
        renderScreenshot(html, tempFile);
        return Files.readAllBytes(tempFile);
    } finally {
        Files.deleteIfExists(tempFile);
    }
}
```

### Pattern 2: Multipart-Bundle via `postOrEdit(…, DiscordPostRef.match(match))`

**What:** Plan 95-03 established the N-PNG-bundle-pattern; Phase 95 D-95-03. Plan 96-01 reuses verbatim for Provisional.

**Example** (verified from `DiscordPostService.postLineups` lines 237-265):
```java
// Source: src/main/java/org/ctc/discord/service/DiscordPostService.java
@Transactional
public DiscordPost postProvisionalScores(Match match) throws DiscordApiException {
    if (!matchHasProvisionalData(match)) {
        throw new BusinessRuleException("Provisional needs at least one completed race");
    }
    List<NamedAttachment> attachments = match.getRaces().stream()
        .filter(r -> !r.getResults().isEmpty())
        .sorted(Comparator.comparing(Race::getDateTime, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(r -> {
            try {
                byte[] png = provisionalScoresGraphicService.generateProvisional(r);
                return new NamedAttachment("provisional-race-" + raceIndexOf(match, r) + ".png", png);
            } catch (IOException e) { throw new DiscordTransientException(TRANSIENT_MESSAGE, e); }
        })
        .toList();
    return postOrEdit(match.getDiscordChannelId(), match.getDiscordChannelWebhookUrl(),
        DiscordPostType.PROVISIONAL_SCORES, WebhookPayload.empty(), attachments,
        DiscordPostRef.match(match));
}
```

**Note:** Race-ordering — `Match.races` is `@OrderBy("dateTime ASC NULLS LAST")` (verified by grep on `Match.java:48`); use a deterministic index (loop-counter) for filename so re-post produces identical filenames → identical attachment-descriptors → Discord edits in place cleanly.

### Pattern 3: Sealed-Switch over `DiscordPostRef`-Permits

**What:** Java 25 supports exhaustive `switch` over sealed-permits; the compiler enforces all permits are handled. Plan 96-03 replaces the current `instanceof DiscordPostRef.MatchRef` guard (lines 286-290) with a sealed-switch.

**Example** (Plan 96-03 reference implementation):
```java
// Source: Plan 96-03 implementation sketch
private Optional<DiscordPost> findExistingPost(
        String channelId, DiscordPostType type, DiscordPostRef ref) {
    return switch (ref) {
        case DiscordPostRef.MatchRef m   -> repo.findByChannelIdAndPostTypeAndMatchId(channelId, type, m.id());
        case DiscordPostRef.RaceRef r    -> repo.findByChannelIdAndPostTypeAndRaceId(channelId, type, r.id());
        case DiscordPostRef.SeasonRef s  -> repo.findByChannelIdAndPostTypeAndSeasonId(channelId, type, s.id());
        case DiscordPostRef.MatchdayRef d -> repo.findByChannelIdAndPostTypeAndMatchdayId(channelId, type, d.id());
    };
}
```

**Note:** The CONTEXT.md code-sketch (lines 226-237) uses `m.matchId()` / `r.raceId()`; the actual record-field accessor is `m.id()` (verified — `DiscordPostRef.java:42` declares `record MatchRef(UUID id) implements DiscordPostRef`). The interface still exposes `matchId()` as a method that returns `id` for the MatchRef branch and `null` for other branches — usable but slightly noisier than the record-accessor `m.id()`. **Recommendation: use `m.id()` style in the switch**.

### Pattern 4: `?thread_id=` Append via `UriComponentsBuilder`

**What:** Plan 96-03 adds 4 overloads on `DiscordWebhookClient` accepting `@Nullable String threadId`. Append via Spring's `UriComponentsBuilder`. Reuse existing `RestClient.uri(uriBuilder -> uriBuilder.queryParam("wait", "true").build())` pattern (verified `DiscordWebhookClient.java:52, 100`).

**Example:**
```java
// Source: Plan 96-03 implementation sketch
public WebhookMessage executeMultipart(
        String webhookUrl, WebhookPayload payload,
        List<NamedAttachment> attachments, @Nullable String threadId) throws DiscordApiException {
    // ... existing validation + parts assembly ...
    return execute(() -> forWebhookUrl(webhookUrl)
        .post()
        .uri(uriBuilder -> {
            uriBuilder.path("").queryParam("wait", "true");
            if (threadId != null) uriBuilder.queryParam("thread_id", threadId);
            return uriBuilder.build();
        })
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(parts)
        .retrieve()
        .body(WebhookMessage.class));
}
public WebhookMessage executeMultipart(String webhookUrl, WebhookPayload payload,
        List<NamedAttachment> attachments) throws DiscordApiException {
    return executeMultipart(webhookUrl, payload, attachments, null);  // delegate (Phase 95 callsites unchanged)
}
```

### Anti-Patterns to Avoid

- **Bind `Season` entity directly via `@ModelAttribute`** for the link-thread POST endpoint. Mass-assignment vector — use `LinkThreadForm(String threadId, String which)` DTO (CLAUDE.md § Controller & DTO Patterns).
- **Echo Discord exception messages to flash** (`e.getMessage()` leakage). Use whitelisted `e.getUserMessage()` per Phase 91 UX-01 pattern (sealed-exception → category + safe message).
- **Add Phase/Plan-numbered comments to source files** ("// Plan 96-03 fix" etc.) — banned by CLAUDE.md § No Comment Pollution; rotates badly with phase renumbers.
- **Skip the host-validator** on the new forum-webhook-URLs. The existing `DiscordHostValidator.requireAllowed(webhookUrl)` is the SSRF guard from v1.5 + Phase 93 INFRA-02. Plan 96-03 MUST call it inside the new overloads (the current overloads do — see `DiscordWebhookClient.java:48, 74, 109, 125` — confirm parity).
- **`thread.flags & 2` raw arithmetic** instead of named constant. Define `public static final int THREAD_FLAG_PINNED = 1 << 1;` on the `Thread` record.
- **Forget `attachments` JSON-descriptor on PATCH-multipart**: the Phase-95 `editMessageWithAttachments` (lines 119-171) builds an explicit `payload_json` containing `{attachments: [{id:0,filename:…}, {id:1,filename:…}, …]}`. Plan 96-01 + 96-03 inherit this — Discord requires it to identify which attachments to replace vs keep.
- **Mutate Flyway V8/V9/V10/V11/V12** to retroactively add `discord_race_results_thread_id` etc. — checksum-fail; only V13 may add columns.
- **Render template via `processStringTemplate` when a classpath template exists** — `AbstractGraphicService.processStringTemplate` is for custom-template-overrides only (admin UI feature). Plan 96-01's default path is `templateEngine.process("admin/provisional-scores-render", ctx)`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---|---|---|---|
| Multipart-bundle assembly | New `byte[]+filename` Pojo + manual `Content-Disposition` headers | `NamedAttachment` record + existing `DiscordWebhookClient.executeMultipart` | Already battle-tested in Phase 95 (5 IT classes verify the boundary) |
| Discord-API exception classification | Custom `if (statusCode == 401) throw …` | `DiscordApiExceptionMapper.from(RestClientResponseException)` | Maps to all 4 sealed permits; Phase 91 UX-01 pattern |
| Snowflake validation | New regex literal | `DiscordSnowflake.PATTERN` + `DiscordSnowflake.MESSAGE` constants | Already used by every existing Discord form field |
| `<t:UNIX:STYLE>` timestamp formatting | Manual epoch math + concat | `DiscordTimestamps.longDateTime(LocalDateTime)` | Phase 93 service with `app.timezone` config injection |
| Forum-thread listing | Stitched manual logic in `SeasonController` | New `DiscordForumService.listThreads(forumChannelId)` (Plan 96-02) | Separates client-side filter+sort from REST plumbing; testable in isolation |
| URL parsing of webhook URL | Regex on `https://discord.com/...` | `DiscordPostService.parseWebhookUrl(String)` static + `WEBHOOK_URL_PATTERN` | Already extracts `(webhookId, webhookToken)`; Phase 95 |
| Rate-limit handling | `Thread.sleep(retryAfter * 1000)` | `DiscordRateLimitInterceptor` | Token-bucket + 429 retry + 5xx backoff already wired; tested in `DiscordRateLimitInterceptorIT` |
| SSRF host check | New `URL.getHost().equals("discord.com")` | `DiscordHostValidator.requireAllowed(webhookUrl)` | v1.5 SSRF pattern + Phase 93 INFRA-02 allowlist |
| Image base64 encoding | Manual `Base64.encode()` | `AbstractGraphicService.encodeCardBase64()` / `encodeClasspathResource()` | Already with path-traversal-defense (`Path.startsWith(uploadDir)` guard) |
| File-per-mock WireMock stub | One `wm.stubFor` call per IT method | `WireMockExtension.newInstance().options(options().dynamicPort()).build()` static at class-level + `@DynamicPropertySource` | Phase 95 IT precedent in `DiscordPostServiceWireMockIT.java` |
| Custom Modal CSS | Bespoke `<div class="modal-overlay">` styling | Reuse the Phase-94 Archive-Modal pattern (`onclick="document.getElementById('archiveModal').style.display='flex'"` + existing CSS classes from `admin.css`) | Plan 96-02 uses the same modal-show/hide JS handler as Phase 94 D-94-06 |

**Key insight:** Phase 96 should be ~80% reuse and ~20% additive code. Plan 96-01 is the highest novel content (new template + new service); Plans 96-02 + 96-03 are mostly composition of existing primitives.

## Runtime State Inventory

> Phase 96 is **additive feature work**, not a rename/refactor/migration. Marked SKIPPED.

| Category | Items Found | Action Required |
|---|---|---|
| Stored data | None — no string identifiers being renamed | none |
| Live service config | None — Discord-side configuration (channel-IDs, webhook-URLs) is operator-pasted into the new V13 columns; nothing on Discord-side needs renaming. UAT-06 staged for operator-run | none in code; operator UAT staged |
| OS-registered state | None — CTC Manager runs as a single Spring Boot process; no OS-registered services touched | none |
| Secrets/env vars | `DISCORD_BOT_TOKEN` env-var unchanged; webhook-URLs land in `discord_global_config` table (V13 columns) — operator paste required at UAT-06 time | code-edit only (Form + Entity); operator-side: paste 2 webhook URLs into `/admin/discord-config` |
| Build artifacts | None — no Maven artifact-id changes, no Docker image-tag changes | none |

**Nothing found in category:** verified — Phase 96 is purely additive code that lands on `gsd/v1.13-discord-integration` and gets shipped on `v1.13.0` tag at milestone close.

## Common Pitfalls

### Pitfall 1: `DiscordPostRef.RaceRef.id()` vs `DiscordPostRef.RaceRef.raceId()`

**What goes wrong:** The CONTEXT code-sketch (lines 226-237) uses `r.raceId()` (the interface method) inside the switch; the **record accessor** is `r.id()`. Both compile, but mixing them within the codebase causes cognitive load.
**Why it happens:** `DiscordPostRef` declares 4 interface methods (`matchId()`, `matchdayId()`, `raceId()`, `seasonId()`) that each return either the local-record `id` or `null`. They were added in Phase 95 for `instanceof` patterns; the sealed-switch should prefer the record accessor.
**How to avoid:** Inside the sealed-switch, always use the pattern-variable's local accessor (`r.id()`, not `r.raceId()`). The interface methods become unused once all 4 permits are switched — leave them for backwards-compat with the existing `MatchRef` call-site in postOrEdit's log statement (line 305: `ref.matchId()`).
**Warning signs:** Code review flags `r.raceId()` calls that always return non-null (redundant null-check); `m.matchId()` calls when `m` is `MatchRef` and `m.id()` would suffice.

### Pitfall 2: Race-ordering for filename-stability on re-post

**What goes wrong:** Plan 96-01 generates `provisional-race-N.png` filenames. If Race-ordering shifts between post and re-post (e.g. Race 2 gains a `dateTime` earlier than Race 1 after operator-edit), the index changes → re-post produces different filenames → Discord PATCH-attachments-descriptor mismatch → either duplicates or replace-wrong-attachment.
**Why it happens:** `Match.races` is `@OrderBy("dateTime ASC NULLS LAST")` (verified `Match.java:48`). The order is stable as long as `dateTime` values don't reorder, but data-entry edits CAN reorder them.
**How to avoid:** Use a derived "race number" that's stable across the match lifecycle. Phase 95's `MatchResultsGraphicService.buildRaceRows` (lines 91-114) uses an iterator-counter (`raceNumber++` over filtered races with results); that's plate-stable while filter is "has results". **Plan 96-01 recommendation: use the same iterator-counter (1, 2, 3, …) over the filtered+sorted-by-`@OrderBy` race-list.** Pin with an IT (`DiscordPostServiceProvisionalScoresIT.givenSameRacesInSameOrder_whenRePost_thenSameFilenames`).
**Warning signs:** Operator edits `Race.dateTime` mid-match, then re-posts provisional → see new attachments not edited attachments in Discord.

### Pitfall 3: Webhook + `thread_id` may already auto-unarchive — defensive GET+PATCH is redundant but harmless

**What goes wrong:** D-96-FOR-4 mandates an explicit `GET /channels/{threadId}` → check `archived` → `PATCH /channels/{threadId} {archived:false}` → `POST` sequence. However, **community-confirmed Discord behavior is that webhook POSTs with `?thread_id=` auto-unarchive the thread on Discord's side** (search-verified 2026-05-23 — see Sources, source: discord.com/developers/docs/topics/threads + multiple community guides).
**Why it happens:** Discord's official channel-message endpoint behavior states: "Sending a message will automatically unarchive the thread, unless the thread has been locked by a moderator." Webhook messages on threads inherit this behavior per practical reports; however the canonical webhook docs do NOT explicitly confirm it.
**How to avoid:** Keep D-96-FOR-4's defensive 3-call pattern as specified. It costs 1 extra GET + at most 1 extra PATCH per archived-thread post, but it gives:
  - **deterministic behavior** in WireMock ITs (we don't have to test Discord's implicit-unarchive against the live API),
  - **defensive correctness** if a Discord-API behavior change ever lands,
  - **observability** (the log message "Unarchiving forum thread {} before post" gives the operator visibility),
  - **locked-thread error visibility** (a locked thread still requires `MANAGE_THREADS`; the PATCH returns 403/`DiscordAuthException` BEFORE the POST runs, yielding clean error attribution).
The cost (2 extra Discord-API calls per post on archived threads only) is acceptable. Stay with the locked decision. Plan 96-03 RESEARCH note: re-evaluate in v1.14 if rate-limit budget pressure surfaces.
**Warning signs:** Webhook returns 200 OK but the thread stays in archived-listing — that's Discord's stale-cache, NOT a CTC bug. Operator confirms via Discord-client refresh.

### Pitfall 4: Discord active-threads endpoint returns ALL guild active threads, not just forum-channel threads

**What goes wrong:** `DiscordRestClient.listActiveThreads(guildId)` calls `GET /guilds/{guildId}/threads/active` which returns **every active thread in the guild** (forum threads, public threads, private threads, news threads). Plan 96-02 must filter by `thread.parent_id == forumChannelId` to scope to the forum-channel's threads. Forgetting this filter shows operator threads from unrelated channels.
**Why it happens:** Discord's API is guild-scoped for active threads (efficient single call) but channel-scoped for archived threads (per-channel call). The asymmetry is documented in the v10 spec.
**How to avoid:** In `DiscordForumService.listThreads`:
```java
List<Thread> active = restClient.listActiveThreads(guildId).stream()
    .filter(t -> Objects.equals(t.parentId(), forumChannelId))
    .toList();
List<Thread> archived = restClient.listArchivedThreads(forumChannelId);  // already channel-scoped
// merge, sort by pinned > last_message_timestamp desc
```
Cover with an IT (`DiscordForumServiceIT.givenMixedThreadsAcrossChannels_whenListThreads_thenOnlyForumChannelThreadsReturned`).
**Warning signs:** Modal shows threads named like `md1-AHR-vs-TNR` (match channels) — that's an unfiltered guild-active-list.

### Pitfall 5: `Thread.flags` may not be populated in all response shapes

**What goes wrong:** The `flags` field on Channel/Thread objects is documented but its presence in `GET /guilds/{guildId}/threads/active` vs `GET /channels/{cid}/threads/archived/public` may differ. CONTEXT D-96-FOR-2a says "fallback: if pinned-detection blocks → skip pre-select".
**Why it happens:** Discord's API has been quiet about exact field-presence guarantees per endpoint; `@JsonIgnoreProperties(ignoreUnknown=true)` on the `Thread` record (line 6 of `Thread.java`) means missing fields deserialize to `null`/`0`, not errors.
**How to avoid:**
  1. Add `int flags` (defaults to 0 if absent in JSON) AND `ThreadMetadata thread_metadata` (nested record) to `Thread`.
  2. Compute `boolean pinned = (flags & (1 << 1)) != 0;`
  3. Compute `boolean archived = thread_metadata != null && thread_metadata.archived();`
  4. WireMock ITs use 2 fixture-shapes: one with `flags` present, one without — assert sort falls back gracefully when `flags==0` (no pre-select).
**Warning signs:** ITs that explicitly stub `flags` pass; live-UAT-06 against a Discord that doesn't return `flags` shows no pre-select → operator manually picks the right thread.

### Pitfall 6: `?thread_id=` query-param vs `payload_json` `attachments` interaction on PATCH-multipart

**What goes wrong:** Phase 95's `editMessageWithAttachments` (lines 119-171) injects an `attachments` JSON array into `payload_json` to identify which uploaded files replace which existing attachments. When adding `?thread_id=` to PATCH, Discord routes to the thread but still expects the attachments-JSON-descriptor.
**Why it happens:** Discord's PATCH webhook-message contract requires explicit attachment identification — without it, attachments are *added* instead of *replaced*, producing duplicates.
**How to avoid:** Plan 96-03's `editMessageWithAttachments(…, threadId)` overload delegates to the existing implementation (same body construction); the `threadId` only changes the URL, not the body. Verify via WireMock body-pattern assertion that the multipart `payload_json` part still contains `"attachments":[{"id":0,"filename":"…"}]`.
**Warning signs:** Re-post produces 2 copies of the PNG in Discord instead of editing in place.

### Pitfall 7: `Match.lastModifiedAt` is NOT a stale-detection signal (carry-over from Phase 95 Pitfall-4)

**What goes wrong:** Plan 95-04 SUMMARY documents this empirically — Spring Data JPA's `@LastModifiedDate` fires on every entity merge, not only on dirty fields. Plan 96-01's `matchHasProvisionalData` does NOT need stale-detection (Provisional re-posts are always operator-button-triggered, never auto-edit), but if Phase 96 ever needs a "match data changed since post"-signal, the answer is `max(race.results[*].updatedAt)`, not `match.lastModifiedAt`.
**Why it happens:** Pinned by `MatchUpdatedAtNoopSaveIT.java` (Phase 95 contingency-first test).
**How to avoid:** Plan 96-01 explicitly states no stale-detection on PROVISIONAL_SCORES — re-post is always operator-triggered. Document this in the Plan 96-01 task-list comment.
**Warning signs:** A future RFC asks for "auto-edit Provisional on race-result-save" — don't reach for `match.lastModifiedAt`; reuse Plan 95-04's pivot pattern.

## Code Examples

### Example 1: Flyway V13 migration (Plan 96-02)

```sql
-- src/main/resources/db/migration/V13__add_seasons_discord_threads_and_forum_webhooks.sql
-- Compatible with H2 2.x and MariaDB 10.7+ — no CHECK constraints, no LONGTEXT
-- (both drift between engines). DiscordConfigForm / SeasonForm Jakarta-Validation
-- owns the snowflake/webhook regex contract at the controller layer; the DB only
-- enforces VARCHAR length ceilings.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").

ALTER TABLE discord_global_config ADD COLUMN race_results_forum_webhook_url VARCHAR(500);
ALTER TABLE discord_global_config ADD COLUMN standings_forum_webhook_url VARCHAR(500);

ALTER TABLE seasons ADD COLUMN discord_race_results_thread_id VARCHAR(32);
ALTER TABLE seasons ADD COLUMN discord_standings_thread_id VARCHAR(32);
```

**Notes:**
- 4 nullable `ADD COLUMN` — both H2 (`ALTER TABLE seasons ADD COLUMN col VARCHAR(32)`) and MariaDB 10.7+ accept this identically [VERIFIED against V9/V10 precedents — both use same shape].
- VARCHAR(500) matches existing `announcement_webhook_url` width [VERIFIED `V8` line 3].
- VARCHAR(32) matches existing snowflake-storage convention (snowflake IDs are 18-21 char digits — 32 gives buffer) [VERIFIED V8/V9 + design-spec § 3.3].
- No FK constraint on `discord_*_thread_id` columns — thread-IDs live on Discord side, not in local DB; CTC has no `threads` table.
- No index — `seasons` table is small (single-digit rows); access path is always `seasons.id` PK.

### Example 2: Sealed-switch over `DiscordPostRef` (Plan 96-03)

```java
// Source: Plan 96-03 implementation sketch for DiscordPostService.postOrEdit
@Transactional
public DiscordPost postOrEdit(
        String channelId, String webhookUrl, DiscordPostType type,
        WebhookPayload payload, List<NamedAttachment> attachments,
        DiscordPostRef ref, @Nullable String threadId) throws DiscordApiException {
    hostValidator.requireAllowed(webhookUrl);
    if (threadId != null) {
        unarchiveIfArchived(threadId);
    }
    WebhookCredentials creds = parseWebhookUrl(webhookUrl);
    Optional<DiscordPost> existing = switch (ref) {
        case DiscordPostRef.MatchRef m    -> repo.findByChannelIdAndPostTypeAndMatchId(channelId, type, m.id());
        case DiscordPostRef.RaceRef r     -> repo.findByChannelIdAndPostTypeAndRaceId(channelId, type, r.id());
        case DiscordPostRef.SeasonRef s   -> repo.findByChannelIdAndPostTypeAndSeasonId(channelId, type, s.id());
        case DiscordPostRef.MatchdayRef d -> repo.findByChannelIdAndPostTypeAndMatchdayId(channelId, type, d.id());
    };
    LocalDateTime now = LocalDateTime.now(clock);
    if (existing.isPresent()) {
        DiscordPost row = existing.get();
        if (attachments.isEmpty()) {
            webhookClient.editMessage(webhookUrl, row.getMessageId(), payload, threadId);
        } else {
            webhookClient.editMessageWithAttachments(webhookUrl, row.getMessageId(), payload, attachments, threadId);
            row.setAttachmentsReplacedAt(now);
        }
        return discordPostRepository.save(row);
    }
    WebhookMessage msg = attachments.isEmpty()
        ? webhookClient.execute(webhookUrl, payload, threadId)
        : webhookClient.executeMultipart(webhookUrl, payload, attachments, threadId);
    DiscordPost row = new DiscordPost();
    row.setChannelId(channelId); row.setMessageId(msg.id());
    row.setWebhookId(creds.id()); row.setWebhookToken(creds.token());
    row.setPostType(type); row.setPostedAt(now);
    if (!attachments.isEmpty()) row.setAttachmentsReplacedAt(now);
    ref.applyTo(row);  // existing per-permit FK setter via sealed-interface method
    return discordPostRepository.save(row);
}

// 5-arg overload preserves Phase-95 call-sites unchanged
@Transactional
public DiscordPost postOrEdit(
        String channelId, String webhookUrl, DiscordPostType type,
        WebhookPayload payload, List<NamedAttachment> attachments,
        DiscordPostRef ref) throws DiscordApiException {
    return postOrEdit(channelId, webhookUrl, type, payload, attachments, ref, null);
}

private void unarchiveIfArchived(String threadId) throws DiscordApiException {
    Channel thread = discordRestClient.fetchChannel(threadId);
    ThreadMetadata md = thread.threadMetadata();
    if (md != null && md.archived()) {
        log.info("Unarchiving forum thread {} before post", threadId);
        discordRestClient.modifyChannel(threadId, ChannelModifyRequest.unarchive());
    }
}
```

### Example 3: `ChannelModifyRequest.unarchive()` factory (Plan 96-03)

```java
// src/main/java/org/ctc/discord/dto/ChannelModifyRequest.java (extended)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelModifyRequest(
        String name,
        @JsonProperty("parent_id") String parentId,
        Boolean archived) {

    public ChannelModifyRequest(String name, String parentId) {
        this(name, parentId, null);
    }

    public static ChannelModifyRequest unarchive() {
        return new ChannelModifyRequest(null, null, Boolean.FALSE);
    }
}
```

**Notes:**
- `Boolean` (not primitive `boolean`) so `null` ≠ "do not modify" can be distinguished from `false` ≠ "set to false". `@JsonInclude(NON_NULL)` ensures `null` fields are not serialized.
- Existing 2-arg constructor preserved for Phase-94 call-sites.

### Example 4: `Thread` DTO with `flags` + `thread_metadata` (Plan 96-02)

```java
// src/main/java/org/ctc/discord/dto/Thread.java (extended)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Thread(
        String id,
        String name,
        @JsonProperty("parent_id") String parentId,
        int flags,
        @JsonProperty("thread_metadata") ThreadMetadata threadMetadata,
        @JsonProperty("last_message_id") String lastMessageId) {

    public static final int FLAG_PINNED = 1 << 1;  // 2

    public boolean pinned() {
        return (flags & FLAG_PINNED) != 0;
    }

    public boolean archived() {
        return threadMetadata != null && threadMetadata.archived();
    }
}

// src/main/java/org/ctc/discord/dto/ThreadMetadata.java (NEW)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreadMetadata(
        boolean archived,
        @JsonProperty("auto_archive_duration") Integer autoArchiveDuration,
        @JsonProperty("archive_timestamp") String archiveTimestamp,
        boolean locked) {
}
```

**Notes:**
- `last_message_id` is a snowflake; treated as `String` (timestamp is encoded in it but extracting needs `(snowflake >> 22) + DISCORD_EPOCH`). For sorting "by last_message_timestamp desc", lex-compare on snowflake string works (snowflakes are time-sortable).
- `flags` defaults to `0` if absent → `pinned()` returns false.
- `threadMetadata` defaults to `null` if absent → `archived()` returns false.

### Example 5: `DiscordForumService.listThreads` (Plan 96-02)

```java
// src/main/java/org/ctc/discord/service/DiscordForumService.java (NEW)
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordForumService {

    private final DiscordRestClient restClient;
    private final DiscordGlobalConfigService globalConfigService;

    public List<Thread> listThreads(String forumChannelId) throws DiscordApiException {
        String guildId = globalConfigService.getOrThrow().getGuildId();
        List<Thread> active = restClient.listActiveThreads(guildId).stream()
                .filter(t -> Objects.equals(t.parentId(), forumChannelId))
                .toList();
        List<Thread> archived = restClient.listArchivedThreads(forumChannelId);
        return Stream.concat(active.stream(), archived.stream())
                .sorted(THREAD_PICKER_ORDER)
                .toList();
    }

    // pinned first; then by last_message_id desc (snowflake = lex-sortable timestamp)
    private static final Comparator<Thread> THREAD_PICKER_ORDER =
            Comparator.comparing(Thread::pinned, Comparator.reverseOrder())
                    .thenComparing(Thread::archived, Comparator.naturalOrder())  // false (active) before true (archived)
                    .thenComparing(Thread::lastMessageId, Comparator.nullsLast(Comparator.reverseOrder()));
}
```

### Example 6: `ProvisionalScoresGraphicService` skeleton (Plan 96-01)

```java
// src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java (NEW)
@Slf4j
@Service
public class ProvisionalScoresGraphicService extends AbstractGraphicService implements TemplateManageable {

    private static final String DEFAULT_TEMPLATE = "templates/admin/provisional-scores-render.html";
    private static final String CUSTOM_TEMPLATE_FILE = "provisional-scores-template.html";

    private final ScoringService scoringService;

    public ProvisionalScoresGraphicService(TemplateEngine templateEngine,
                                           ScoringService scoringService,
                                           @Value("${app.upload-dir:uploads}") String uploadDir) {
        super(templateEngine, uploadDir);
        this.scoringService = scoringService;
    }

    public byte[] generateProvisional(Race race) throws IOException {
        if (race.getHomeTeam() == null || race.getAwayTeam() == null) {
            throw new IllegalStateException("Race has no teams assigned");
        }
        if (race.getResults().isEmpty()) {
            throw new IllegalStateException("No results for this race");
        }
        // ... build per-driver rows (Position/Quali/FL/Pts-Race/Pts-Quali/Pts-FL/Total + Overall)
        // ... encode team-cards + logo + font
        // ... renderTemplate(ctx)
        Path tempFile = Files.createTempFile("provisional-", ".png");
        try {
            renderScreenshot(html, tempFile);
            return Files.readAllBytes(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
    // ... TemplateManageable boilerplate (loadTemplate/saveTemplate/resetTemplate/hasCustomTemplate)
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|---|---|---|---|
| Phase 95 `instanceof DiscordPostRef.MatchRef m` guard with `throw UnsupportedOperationException` for other permits | Phase 96-03 exhaustive sealed-`switch` over all 4 permits | 2026-05-23 (Plan 96-03) | Compile-time exhaustiveness; future MatchdayRef (Phase 97 POST-07) adds without forgetting any branch |
| Bot direct-POST to forum thread | Webhook with `?thread_id={id}` query-param | 2026-05-20 (design spec § 3.1) | Uniform Webhook-PATCH edit-path across all post-types; webhook permission-scope is narrower than Bot's full channel-permission |
| Bot creates new forum threads from app UI | Operator creates threads manually in Discord-client | 2026-05-23 (D-96-FOR-1c, user override of FORUM-01 spec) | Less code, less bot-API surface, less UI complexity; ~1-min operator-onboarding per season |
| Re-archive forum thread after every post | Leave unarchived — Discord's inactivity-auto-archive handles it (24h/3d/7d/30d) | 2026-05-23 (D-96-FOR-4) | 1 fewer Discord API call per post on archived threads |
| Persist provisional via Google-Sheets-manual-screenshot | App-generated `ProvisionalScoresGraphicService` | 2026-05-20 (design-spec § 9 Q-8) | Eliminates manual workflow; consistent visual style |

**Deprecated/outdated:**
- The Phase-95 `postOrEdit(…, ref)` 6-arg signature is now an overload — production code should call the 7-arg `postOrEdit(…, ref, threadId)` directly when targeting forum threads. Phase-95 callsites unchanged.
- The `UnsupportedOperationException` in `DiscordPostService.postOrEdit` lines 286-290 is removed by Plan 96-03.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| A1 | Discord's `GET /guilds/{gid}/threads/active` returns ALL active threads guild-wide (not just one channel's) | Pitfall 4, Pattern code Ex 5 | If false (e.g. Discord changes API): Plan 96-02 modal under-shows threads → operator can't pick. Mitigation: WireMock-IT stubs both shapes and asserts `parent_id`-filter is applied; live UAT-06 reveals discrepancy |
| A2 | `thread.flags` integer bitfield with `PINNED = 1 << 1` is populated in `GET /guilds/{gid}/threads/active` response | Pitfall 5, Example 4 | If `flags=0` always returned, pinned pre-select silently falls back to no-pre-select. Acceptable graceful degradation. WireMock-IT covers both shapes |
| A3 | Webhook with `?thread_id=` targeting an archived thread auto-unarchives on Discord side (community-confirmed, not in canonical docs) | Pitfall 3 | If false (Discord changes): explicit GET+PATCH defensive sequence makes us correct anyway. Zero functional risk; cost is 2 extra API calls per archived-thread post |
| A4 | `PATCH /channels/{threadId}` with `{archived: false}` requires only `SEND_MESSAGES` permission (Discord doc cite via `docs.discord.com/developers/resources/channel`) — `MANAGE_THREADS` only needed if also setting `locked` | Auto-Unarchive, Example 3 | If bot lacks `SEND_MESSAGES` on the forum channel: 403 → `DiscordAuthException` → operator sees `auth` badge. Action: Phase 98 DOCS-02 runbook lists required permissions explicitly |
| A5 | Snowflake string lex-sort = chronological sort (snowflake encodes `(timestamp_ms - DISCORD_EPOCH) << 22 \| …`) | Example 5 sort | Always true by Discord-snowflake specification (mathematically guaranteed via fixed-width binary representation) |
| A6 | `Match.races` `@OrderBy("dateTime ASC NULLS LAST")` ordering is stable as long as no race-dateTime is edited mid-match | Pitfall 2 | If operator edits Race.dateTime between post + re-post → filenames drift → Discord PATCH-attachments-descriptor mismatch. Mitigation: pin a `givenSameRacesInSameOrder_whenRePost_thenSameFilenames` IT |
| A7 | The 4 webhookClient.* callsites in `DiscordPostService.postOrEdit` (lines 299, 301, 310, 311) are the **only** callsites of the message-methods in the codebase | Tool-strategy analysis | Verified by grep (5 hits — all within `postOrEdit`); Variant A overloads are guaranteed zero-blast-radius for Phase-95 code |
| A8 | `Thread.last_message_id` snowflake is present in both active and archived listings | Example 5 sort fallback | Documented as optional in some Discord guides. If null on an active thread: `Comparator.nullsLast` keeps it at end of its tier. Acceptable graceful degradation |
| A9 | `EXPORT_ORDER` stays 25 (Phase 95's pre-bumped value) — Phase 96 adds NO new entities, only adds columns to existing `Season` + `DiscordGlobalConfig` | D-96-07 | Verified — `Season` and `DiscordGlobalConfig` already in `EXPORT_ORDER` per Phase 95 D-95-07; no entity-class additions in Phase 96 (see D-96-11 file-list) |
| A10 | `BackupSchema.SCHEMA_VERSION` stays 2 (per Phase 72 D-15 convention — column-adds are backward-compatible via Jackson `ignoreUnknown`) | D-96-07 | Verified by precedent. Plan 96-02 SUMMARY task: confirm `BackupSchemaGuardTest` still green; if Jackson rejects the new fields, the test catches it pre-merge |

**If this table is empty:** N/A — 10 assumptions logged; all have either codebase verification, fallback paths, or test coverage planned. None block planning.

## Open Questions (RESOLVED)

1. **Provisional reference image fidelity** (Plan 96-01 visual loop)
   - **RESOLVED:** Plan 96-01 stages the User-Reference under `.screenshots/96-01/provisional-reference.png` BEFORE Task 96-01-02 and iterates with wave-pause operator approval before plan-close (no pixel-perfect mockup required upfront — iterate to match).
   - What we know: User provided 2 chat-screenshots 2026-05-23 (Google-Sheets workflow + existing CTC Race-Detail results table); CONTEXT documents an iterative `playwright-cli` loop per `[[feedback-graphic-pixel-positioning]]`.
   - What's unclear: pixel-exact spacing/font/color targets — user has not provided a pixel-perfect mockup. The PNG is iterated, not specified.
   - Recommendation: Plan 96-01 stages the User-Reference under `.screenshots/96-01/provisional-reference.png` BEFORE the implementation task; each iteration commits to `gsd/v1.13-discord-integration` with the rendered output also in `.screenshots/96-01/`. Wave-pause after each rendered iteration per `[[feedback-wave-pause]]` + `[[feedback-graphic-design-iteration]]`. User approves before plan-close.

2. **Mobile-viewport `.card` overflow on Season-Edit after Discord-Integration section add**
   - **RESOLVED:** Plan 96-02 sweeps Desktop + Mobile via `playwright-cli` for surface-only detection; the actual fix is DEFERRED to Phase 98 polish-CSS-sweep (success criterion 6).
   - What we know: Phase 98 success criterion 6 covers `.card`/`.form-group` overflow on all Discord-touching pages.
   - What's unclear: Whether Plan 96-02's new section measurably worsens the overflow vs being absorbed into the Phase-98 polish.
   - Recommendation: Plan 96-02 sweeps Desktop + Mobile via `playwright-cli`, captures `.screenshots/96-02/season-form-discord-section-{desktop,mobile}.png`. Surface-only — actual fix in Phase 98.

3. **Backup `DiscordGlobalConfigMixIn` — does it exist?**
   - **RESOLVED:** Plan 96-02 Task 96-02-02 audits-and-acts: if DiscordGlobalConfig participates in backup, create `DiscordGlobalConfigMixIn` with `@JsonIgnoreProperties` excluding all 3 webhook-URLs; if it does not participate, document the absence in 96-02-SUMMARY (no action needed — operator restores webhook-URLs out-of-band).
   - What we know: `BackupSerializationModule` (verified via `ls` on `src/main/java/org/ctc/backup/serialization/`) lists 24 MixIn files for top-level entities. `DiscordGlobalConfigMixIn` is **not** in that list — Phase 93 INFRA-03 didn't add one.
   - What's unclear: Whether `DiscordGlobalConfig` is currently in `EXPORT_ORDER` at all (CONTEXT D-96-07 says "EXPORT_ORDER stays 25" — that's referring to Phase 95 D-95-07's bump for `DiscordPost`; whether `DiscordGlobalConfig` was included separately is open).
   - Recommendation: Plan 96-02's Task to add 2 webhook-URL columns should also audit whether `DiscordGlobalConfig` participates in backup at all. If yes — add `DiscordGlobalConfigMixIn` with `@JsonIgnoreProperties({"announcementWebhookUrl", "raceResultsForumWebhookUrl", "standingsForumWebhookUrl"})` for secret-discipline. If no — no action needed (operator restores webhook-URLs out-of-band).

4. **Whether Discord's `?thread_id=` query-param triggers a separate rate-limit bucket vs the parent webhook**
   - **RESOLVED:** Out of Phase 96 scope. Phase 97 will surface the issue empirically if matchday-batch forum-posts trigger 429s; Phase 93's `DiscordRateLimitInterceptor` already handles 429 with 3-retry + jitter, so worst case is slower posts.
   - What we know: Discord's rate-limit interceptor uses `X-RateLimit-Bucket` header (per Phase 93). Each webhook+thread combination might bucket separately; we won't know until live UAT-06.
   - What's unclear: Whether matchday-batch forum-posts (Phase 97 POST-07) trigger 429s on the same webhook bucket.
   - Recommendation: Out of Phase 96 scope. Phase 97 will surface it if it bites — Phase 93's `DiscordRateLimitInterceptor` already handles 429 with 3-retry + jitter, so worst case is slower posts.


## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|---|---|---|---|---|
| Java 25 (Eclipse Temurin) | All compile/runtime | ✓ | 25 | — |
| Maven via `./mvnw` | Build | ✓ | 3.x | — |
| Spring Boot 4.x | All Spring beans | ✓ | 4.0.6 (per v1.10 upgrade) | — |
| MariaDB 10.7+ | `local`/`docker`/`prod` profile DB | ✓ | local: operator's MariaDB; CI: H2 | H2 covers dev/test, MariaDB covers local UAT |
| H2 2.x | `dev`/`test` profile DB | ✓ | 2.x | — |
| Flyway | Migrations | ✓ | Spring Boot managed | — |
| Playwright Java (Chromium) | `AbstractGraphicService.renderScreenshot` (GRAFX-01) | ✓ | compile-scope per CLAUDE.md | E2E profile installs Chromium browser separately |
| WireMock | All Discord ITs | ✓ | test-scope; `WireMockExtension.newInstance().options(options().dynamicPort()).build()` pattern | — |
| Discord Bot Token (`DISCORD_BOT_TOKEN`) | UAT-06 only — never required for CI/unit/IT | ✗ in CI, ✓ on operator workstation for UAT-06 | — | WireMock covers all Phase-96-close gates; UAT-06 staged post-PR |
| Discord Test Guild (live) | UAT-06 only | ✓ on operator workstation | — | Same as above |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** Live Discord — covered by WireMock for Phase-96-close; UAT-06 deferred to post-plan operator action per D-96-10.

## Validation Architecture

> `nyquist_validation_enabled: true` for Phase 96 → this section is mandatory; each Plan 96-01/02/03 ships its own `VALIDATION.md` per D-96-08; phase-level `96-VALIDATION.md` synthesizes them at `/gsd-validate-phase 96` time.

### Test Framework

| Property | Value |
|---|---|
| Framework | JUnit 5 (Jupiter) + Mockito 4.x + WireMock + Playwright Java + AssertJ + Spring Boot Test |
| Config file | `pom.xml` Surefire (lines 266-279, excludes integration/e2e/flaky), Failsafe-integration (291-308 `<groups>integration</groups>`), Failsafe-E2E (440-460, `-Pe2e` profile) |
| Quick run command | `./mvnw test -Dtest=ProvisionalScoresGraphicServiceTest` (unit, no Spring) or `./mvnw verify -Dit.test=DiscordPostServiceForumThreadIT` (single IT) |
| Full suite command | `./mvnw verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|---|---|---|---|---|
| **GRAFX-01** | Service renders byte[] PNG from Race | unit (Mockito) | `./mvnw test -Dtest=ProvisionalScoresGraphicServiceTest` | ❌ Plan 96-01 |
| **GRAFX-01** | Multipart-POST + PATCH-edit via DiscordPostService | integration (WireMock) | `./mvnw verify -Dit.test=DiscordPostServiceProvisionalScoresIT` | ❌ Plan 96-01 |
| **GRAFX-01** | Controller POST `/post-provisional` + pre-flight branches | integration | `./mvnw verify -Dit.test=MatchControllerProvisionalPostIT` | ❌ Plan 96-01 |
| **GRAFX-01** | Match-Detail buttons render correctly across viewport | e2e (Playwright) | `./mvnw verify -Pe2e -Dtest=MatchDetailProvisionalButtonsE2ETest` | ❌ Plan 96-01 |
| **GRAFX-01** | Visual smoke (Desktop + Mobile) via `playwright-cli` | manual | `playwright-cli open http://localhost:9090/admin/matches/{id}` after `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo` | ❌ Plan 96-01 |
| **FORUM-01** | V13 migration applies on H2 + MariaDB | integration | `./mvnw verify -Dit.test=V13MigrationSmokeIT` (or full `verify`) | ❌ Plan 96-02 (or rely on Spring's auto-migrate on context-load — covered by every `*IT`) |
| **FORUM-01** | `DiscordForumService.listThreads` sort + filter + pinned-fallback | unit (Mockito) | `./mvnw test -Dtest=DiscordForumServiceTest` | ❌ Plan 96-02 |
| **FORUM-01** | `DiscordForumService.listThreads` with WireMock active/archived shapes | integration | `./mvnw verify -Dit.test=DiscordForumServiceIT` | ❌ Plan 96-02 |
| **FORUM-01** | `SeasonController.linkThread/unlinkThread` endpoints | integration | `./mvnw verify -Dit.test=SeasonControllerLinkThreadIT` | ❌ Plan 96-02 |
| **FORUM-01** | Season-Edit modal: open + auto-pre-select + submit + unlink + mobile | e2e | `./mvnw verify -Pe2e -Dtest=SeasonEditDiscordSectionE2ETest` | ❌ Plan 96-02 |
| **FORUM-02** | `DiscordPostService.postOrEdit` sealed-switch dispatches to all 4 RefBranches | unit (Mockito) | `./mvnw test -Dtest=DiscordPostServiceRefBranchesTest` | ❌ Plan 96-03 |
| **FORUM-02** | `?thread_id=` URL append + Auto-Unarchive before post | integration (WireMock) | `./mvnw verify -Dit.test=DiscordPostServiceForumThreadIT` | ❌ Plan 96-03 |
| **FORUM-02** | `DiscordWebhookClient` `?thread_id=` on all 4 message-methods | integration (WireMock) | `./mvnw verify -Dit.test=DiscordWebhookClientThreadIdIT` | ❌ Plan 96-03 |
| **FORUM-02** | `RaceController.postRaceResultToForum` pre-flight branches | integration | `./mvnw verify -Dit.test=RaceControllerPostRaceResultToForumIT` | ❌ Plan 96-03 |
| **FORUM-02** | Race-Detail button + Mobile-sweep | e2e | `./mvnw verify -Pe2e -Dtest=RaceDetailForumPostButtonE2ETest` | ❌ Plan 96-03 |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=<focused-class>` (or `./mvnw verify -Dit.test=<focused-IT>`) — < 60 s per focused run.
- **Per wave merge (= per plan close):** `./mvnw verify -Pe2e` — full suite green; capture JaCoCo % from `target/site/jacoco/jacoco.csv`; SpotBugs 0; CodeQL pipe through.
- **Phase gate:** `./mvnw verify -Pe2e` green + JaCoCo ≥ 88.88% (or Phase 95's 88.61% honored as floor) + UAT-06 staged in STATE.md.

### Wave 0 Gaps

- [ ] `src/test/java/org/ctc/admin/service/ProvisionalScoresGraphicServiceTest.java` — covers GRAFX-01 service-layer
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceProvisionalScoresIT.java` — covers multipart + PATCH
- [ ] `src/test/java/org/ctc/admin/controller/MatchControllerProvisionalPostIT.java` — covers controller endpoint
- [ ] `src/test/java/org/ctc/e2e/discord/forum/MatchDetailProvisionalButtonsE2ETest.java` — covers visible button states
- [ ] `src/test/java/org/ctc/discord/service/DiscordForumServiceTest.java` — covers FORUM-01 service-layer
- [ ] `src/test/java/org/ctc/discord/service/DiscordForumServiceIT.java` — covers active + archived WireMock paths
- [ ] `src/test/java/org/ctc/admin/controller/SeasonControllerLinkThreadIT.java` — covers link/unlink endpoints
- [ ] `src/test/java/org/ctc/e2e/discord/forum/SeasonEditDiscordSectionE2ETest.java` — covers modal interactions
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java` — covers sealed-switch
- [ ] `src/test/java/org/ctc/discord/service/DiscordPostServiceForumThreadIT.java` — covers ?thread_id= + Auto-Unarchive
- [ ] `src/test/java/org/ctc/discord/DiscordWebhookClientThreadIdIT.java` — covers all 4 overloads
- [ ] `src/test/java/org/ctc/admin/controller/RaceControllerPostRaceResultToForumIT.java` — covers race controller endpoint
- [ ] `src/test/java/org/ctc/e2e/discord/forum/RaceDetailForumPostButtonE2ETest.java` — covers e2e + mobile sweep

Total new test classes: **13** (3 plans × 4-5 classes each); aligned with CONTEXT D-96-07 estimate of "30-50 new tests".

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---|---|---|
| V2 Authentication | partial | Operator-only admin app on `prod`/`docker` profile (Spring Security from Phase 30); Discord Bot-Token in env-var only |
| V3 Session Management | yes | Spring Security session — already established in Phase 30; Phase 96 adds CSRF-protected endpoints inheriting the pattern |
| V4 Access Control | yes | All `POST /admin/discord/**`, `POST /admin/seasons/{id}/link-thread`, `POST /admin/matches/{id}/post-provisional`, `POST /admin/races/{id}/post-race-result-to-forum` are CSRF-protected (Phase 30 pattern) + operator-auth-required |
| V5 Input Validation | yes | `DiscordConfigForm` `@URL` + `@Pattern(WEBHOOK_REGEX)` on 2 new webhook fields; `LinkThreadForm` (NEW) with `@Pattern(DiscordSnowflake.PATTERN)` on threadId |
| V6 Cryptography | n/a | No new cryptographic operations — Discord Bot-Token + webhook-URLs are bearer secrets stored at-rest in DB (operator-trusted, single-tenant); never hand-roll crypto |
| V8 Data Protection | yes | `@ToString.Exclude` on `DiscordGlobalConfig.raceResultsForumWebhookUrl` + `standingsForumWebhookUrl` (Phase 93 D-93-02 pattern); log-pattern mask already redacts `https://discord.com/api/webhooks/[^/\s]+/[^/\s]+` (Phase 93 INFRA-02); backup MixIn excludes webhook-URLs by default (D-96-07 planner-discretion → recommendation: exclude) |
| V10 Malicious Code | partial | Static analysis: SpotBugs + find-sec-bugs blocking (`./mvnw verify`); CodeQL on PR. No new dependencies introduced |
| V13 API & Web Service | yes | `DiscordHostValidator.requireAllowed(url)` SSRF allowlist (`app.discord.allowed-hosts=discord.com`) applies to the 2 new forum-webhook-URLs before every outbound call |

### Known Threat Patterns for {Spring + Discord-Bot + Webhook}

| Pattern | STRIDE | Standard Mitigation |
|---|---|---|
| Webhook-URL leak via stacktrace / `toString()` | Information Disclosure | `@ToString.Exclude` on `raceResultsForumWebhookUrl` + `standingsForumWebhookUrl` (analog Match.discordChannelWebhookUrl) |
| Webhook-URL leak via log line | Information Disclosure | Existing log-pattern mask (`https://discord.com/api/webhooks/[^/\s]+/[^/\s]+`) per Phase 93 INFRA-02; verify regression test still passes after Plan 96-02 edits |
| SSRF via attacker-controlled webhook URL | Tampering | `DiscordHostValidator.requireAllowed(url)` allowlist gate inside every `DiscordWebhookClient` public method (call at line 1 of every overload — Plan 96-03 task) |
| Mass-assignment via direct entity binding (e.g. `Season` via `@ModelAttribute`) | Tampering | Use form DTOs: `DiscordConfigForm` (extended), `LinkThreadForm` (NEW), `SeasonForm` (extended with read-only thread-display fields if any) |
| CSRF on POST endpoints | Tampering | Spring Security CSRF token from Phase 30 — Plan 96-01/02/03 endpoints inherit |
| Path traversal via `provisional-race-N.png` upload-dir write | Tampering | `AbstractGraphicService.encodeCardBase64` already `Path.normalize().startsWith(uploadDir)` guard — same code-path is reused |
| Information leak via Discord-API exception messages | Information Disclosure | Catch sealed `DiscordApiException`, switch on category, flash `e.getUserMessage()` only — never `e.getMessage()` (Phase 91 UX-01 pattern + carried-forward UX-01 from Phase 92) |
| Backup file leaks webhook-URL secrets | Information Disclosure | `DiscordGlobalConfigMixIn` (NEW) `@JsonIgnoreProperties({"announcementWebhookUrl", "raceResultsForumWebhookUrl", "standingsForumWebhookUrl"})` |
| Unbounded Discord API calls for forum-thread enumeration → rate-limit-burst | DoS | `DiscordRateLimitInterceptor` (Phase 93) per-bucket token-bucket + max-5-parallel handles it; CTC test-guild has < 100 threads so single GET suffices (no pagination needed in v1.13) |

## Sources

### Primary (HIGH confidence — codebase-verified)

- `src/main/java/org/ctc/discord/DiscordRestClient.java` — listActiveThreads + listArchivedThreads + modifyChannel + fetchChannel signatures
- `src/main/java/org/ctc/discord/DiscordWebhookClient.java` — execute / executeMultipart / editMessage / editMessageWithAttachments (5 callsites verified)
- `src/main/java/org/ctc/discord/service/DiscordPostService.java` — current MatchRef-only guard at lines 286-290; postOrEdit shape at lines 277-326
- `src/main/java/org/ctc/discord/dto/DiscordPostRef.java` — 4 sealed permits already declared with full record bodies
- `src/main/java/org/ctc/discord/dto/Thread.java` — current 3-field shape (id, name, parent_id) + `@JsonIgnoreProperties(ignoreUnknown=true)`
- `src/main/java/org/ctc/discord/dto/ChannelModifyRequest.java` — current 2-field shape
- `src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java` — entity with `@ToString(exclude={"announcementWebhookUrl"})`
- `src/main/java/org/ctc/admin/service/MatchResultsGraphicService.java` — structural blueprint for `ProvisionalScoresGraphicService`
- `src/main/java/org/ctc/admin/service/ResultsGraphicService.java` — refactor target for `generateResultsBytes`
- `src/main/java/org/ctc/admin/service/AbstractGraphicService.java` — Template + Playwright pipeline
- `src/main/resources/db/migration/V8__discord_global_config.sql` — ADD COLUMN precedent
- `src/main/resources/db/migration/V12__discord_post.sql` — Phase 95 = V12 confirmed (V13 is next)
- `src/main/resources/templates/admin/match-detail.html` — `.discord-actions--posts` cluster + disabled-span pattern (lines 22-153)
- `src/test/java/org/ctc/discord/service/DiscordPostServiceWireMockIT.java` — WireMock IT scaffolding pattern
- `.planning/phases/95-match-channel-posts/95-04-SUMMARY.md` — Phase 95 stale-detection pivot (Pitfall 7 source)
- `.planning/phases/96-provisional-graphic-forum-threads/96-CONTEXT.md` — locked decisions D-96-GRX-1..1c + D-96-FOR-1..4a + D-96-05..11
- `CLAUDE.md` — Project guidelines: language, constraints, conventions, SAST gates, GSD invocation prefix
- `.planning/codebase/TESTING.md` — `@Tag` convention + filename / Spring-context / Failsafe routing

### Secondary (MEDIUM confidence — official docs)

- `docs/superpowers/specs/2026-05-20-discord-integration-design.md` § 3.1/3.2/3.3/4.1/4.3/4.7/5 — authoritative design spec from 2026-05-20 brainstorming
- `https://docs.discord.com/developers/resources/channel` — Discord v10 Channel object, thread_metadata, flags bitfield (PINNED = 1 << 1), Modify Channel `{archived: false}` payload
- `https://docs.discord.com/developers/resources/webhook` — Webhook ?thread_id= on POST and PATCH
- `https://docs.discord.com/developers/topics/threads` — Thread auto-archive timeouts (24h/3d/7d/30d)

### Tertiary (LOW confidence — community-confirmed, not in canonical Discord docs)

- WebSearch 2026-05-23: "discord webhook thread_id archived thread auto-unarchive behavior" — Multiple community sources confirm webhook POST with `thread_id` auto-unarchives the target thread (treated as A3 assumption — Pitfall 3 mitigates via defensive 3-call sequence)
- WebSearch 2026-05-23: "discord webhook attachment size limit 25 MB" — Total per-message cap 25MB (50MB with Nitro); per-attachment limit not formally documented but practical cap ≈ 25MB total. CTC's max 4 PNGs × ~200KB = ~800KB → unkritisch (matches CONTEXT D-96-GRX-1b analysis)

## Metadata

**Confidence breakdown:**
- Standard Stack: **HIGH** — every library verified by codebase grep; zero new dependencies (`D-No-New-Deps` locked).
- Architecture: **HIGH** — every reused service/DTO/template verified by Read; sealed-permit topology verified to already exist in code (not just CONTEXT).
- Pitfalls: **HIGH** — Pitfall 1, 2, 4, 6, 7 are codebase-derived; Pitfall 3 is WebSearch-confirmed with defensive fallback; Pitfall 5 is API-shape-dependent with graceful-degradation fallback.
- Discord-API specifics: **MEDIUM** — official docs cited but the *interaction* between `thread_id` and archived threads has authoritative-source ambiguity; defensive coding via D-96-FOR-4 handles both possible behaviors correctly.
- Test Architecture: **HIGH** — IT pattern verified against `DiscordPostServiceWireMockIT.java` lines 1-80; @Tag routing verified against `.planning/codebase/TESTING.md`.

**Research date:** 2026-05-23
**Valid until:** 2026-06-22 (30 days — Discord API + Spring Boot 4.x both classified as stable; reassess if Discord v11 ships in that window).

## RESEARCH COMPLETE

Phase 96 research locks the three-plan execution blueprint with HIGH confidence: zero new production dependencies, sealed-permit infrastructure already in code (4/4 permits declared — Phase 95 only `instanceof`-gates them), `DiscordWebhookClient` has exactly 5 callsites all inside `DiscordPostService` so Variant A overloads cost zero refactor on Phase-95 code, and the multipart-bundle + PATCH-with-attachments-descriptor pattern is battle-tested. Phase 95's Pitfall-4 lesson (max-child-updatedAt for stale-detection) is not load-bearing for Phase 96 because PROVISIONAL_SCORES + RACE_RESULTS to forum are operator-button-triggered, never auto-edit. Discord's `?thread_id=` query-param on webhooks may auto-unarchive threads on its own, but D-96-FOR-4's defensive GET-then-PATCH sequence is correct under both behaviors and gives clean operator observability + locked-thread error attribution. Plan 96-01 (per-Race Provisional Multipart) is autark on Phase 95; Plan 96-02 (V13 + Discord-Config + DiscordForumService + Link-Modal) provides the schema + UI foundation; Plan 96-03 (sealed-switch + threadId-plumbing + Auto-Unarchive + Race-Detail-button) lifts Phase 95's `UnsupportedOperationException` guard and centralizes Auto-Unarchive for Phase 97 reuse. 13 new test classes mapped to GRAFX-01 + FORUM-01 + FORUM-02 via Nyquist-validation architecture. UAT-06 (Live Provisional + Forum-Thread Lifecycle) staged in STATE.md for post-PR operator-run per D-96-10. JaCoCo gate ≥ 88.88% achievable; SpotBugs 0 invariant maintained; Flyway V13 lands additive (4 nullable ADD COLUMN, H2+MariaDB compatible). Ready for `/gsd-plan-phase 96` to produce Plans 96-01, 96-02, 96-03 sequentially per D-96-05.
