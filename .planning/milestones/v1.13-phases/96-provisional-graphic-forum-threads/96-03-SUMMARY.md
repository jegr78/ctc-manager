---
phase: 96
plan: 96-03
slug: discord-post-service-refbranches-thread-id-race-detail-forum-post
status: complete
created: 2026-05-23
completed: 2026-05-23
---

# Plan 96-03 — SUMMARY (FORUM-02)

## Scope Delivered

Closes the v1.13 Discord forum-thread integration. Replaces the
Phase-95 `instanceof DiscordPostRef.MatchRef` guard in
`DiscordPostService.postOrEdit` with an exhaustive Java 25 sealed-switch
over all 4 RefBranches; plumbs an optional `@Nullable String threadId`
end-to-end via Variant-A overloads on every `DiscordWebhookClient`
message-method; adds an `unarchiveIfArchived(threadId)` defensive hook
before any forum-thread POST/PATCH; extracts a `byte[]`-returning
variant of the results-graphic pipeline; and lights up the Race-Detail
page with a Post / Disabled / Re-Post Race Result triplet with 3
distinct pre-flight tooltips.

End-of-plan = Phase-96-Close gate. `./mvnw clean verify -Pe2e` runs
the full Phase-96 suite (14 test classes) green at 2120 tests.

## Artifacts

| Path | Kind | Purpose |
|------|------|---------|
| `src/main/java/org/ctc/discord/dto/ChannelModifyRequest.java` | MODIFY | +Boolean archived + 2-arg compact constructor + unarchive() factory |
| `src/main/java/org/ctc/discord/dto/Channel.java` | MODIFY | +thread_metadata record-component with 4-arg + 5-arg backward-compat constructors |
| `src/main/java/org/ctc/discord/repository/DiscordPostRepository.java` | MODIFY | +findByChannelIdAndPostTypeAnd{Race,Season,Matchday}Id + findByPostTypeAndRaceId |
| `src/main/java/org/ctc/discord/DiscordWebhookClient.java` | MODIFY | +4 @Nullable threadId overloads with `appendThreadId` UriBuilder helper; uses JSpecify `@Nullable` |
| `src/main/java/org/ctc/discord/service/DiscordPostService.java` | MODIFY | Sealed-switch over all 4 DiscordPostRef permits + 7-arg postOrEdit + unarchiveIfArchived + canPostRaceResultToForum + postRaceResultToForumThread; injects DiscordRestClient + DiscordGlobalConfigService |
| `src/main/java/org/ctc/admin/service/ResultsGraphicService.java` | MODIFY | Extracts `buildHtml(Race)` shared helper; adds `generateResultsBytes(Race) -> byte[]` |
| `src/main/java/org/ctc/admin/controller/RaceController.java` | MODIFY | +POST /{id}/post-race-result-to-forum endpoint, 3 model attrs on detail() GET, applyErrorFlash overloads |
| `src/main/resources/templates/admin/race-detail.html` | MODIFY | Discord Actions card with 3-state .discord-actions--posts cluster |
| `src/test/java/org/ctc/discord/DiscordWebhookClientThreadIdIT.java` | NEW | 6 WireMock IT (URL append on all 4 methods + null-omit + host-validator) |
| `src/test/java/org/ctc/discord/dto/ChannelRecordTest.java` | NEW | 4 unit (backward-compat constructors + thread_metadata Jackson round-trip) |
| `src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java` | NEW | 5 Mockito unit (RefBranch dispatch + null-threadId path) |
| `src/test/java/org/ctc/discord/service/DiscordPostServiceForumThreadIT.java` | NEW | 6 WireMock IT (happy-post, auto-unarchive sequence, no-PATCH-when-not-archived, re-post PATCH, BusinessRuleException, null-threadId zero-GET regression) |
| `src/test/java/org/ctc/admin/controller/RaceControllerPostRaceResultToForumIT.java` | NEW | 8 MockMvc+WireMock IT (happy + 3 pre-flight failures + re-post + auto-unarchive + 2 model-attr branches) |
| `src/test/java/org/ctc/e2e/discord/forum/RaceDetailForumPostButtonE2ETest.java` | NEW | 6 Playwright E2E (enabled + 3 disabled tooltips + click flow + Mobile sweep) |
| `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java` | MODIFY | Constructor call updated to include 3 new dependencies |
| `src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java` | MODIFY | Constructor call updated to include DiscordRestClient + DiscordGlobalConfigService |

## Key Decisions

- **Java 25 sealed-switch, NO `default:` clause.** Adding a future permit
  to `DiscordPostRef` would now fail to compile in `DiscordPostService`
  — exactly the compile-time enforcement the sealed hierarchy is meant
  to provide. Record-component accessor `m.id()` used uniformly (not
  the `m.matchId()` interface-method that returns the same UUID via
  permit-specific overrides) per RESEARCH Pitfall 1.
- **Variant A method-overloads on `DiscordWebhookClient`** (D-96-FOR-3a)
  — not a `WebhookTarget` wrapper-record. Existing 3-arg / 4-arg
  callsites stay untouched; new overloads accept `@Nullable String
  threadId` as the trailing parameter and delegate via the same body.
  Tiny `appendThreadId(UriBuilder, threadId)` helper is the single
  switching point.
- **JSpecify `@Nullable`** instead of `org.springframework.lang.Nullable`
  — Spring 7 deprecated the latter, JSpecify ships in the existing
  Spring Boot 4 dependency tree.
- **Auto-Unarchive runs only when `thread_metadata.archived == true`,
  NO re-archive after post** (D-96-FOR-4). Discord's natural
  inactivity auto-archive owns the back-to-archive side; the bot only
  re-opens when needed for the immediate post. The IT pins both
  branches (with and without the PATCH).
- **`generateResultsBytes` via shared `buildHtml` helper, not via a
  cross-cutting refactor.** The Phase-95 `generateResults(Race) ->
  String` uploads-URL contract is preserved verbatim — the new
  `byte[]`-returning sibling just runs the same render pipeline into a
  temp file and reads it back. Matches `MatchResultsGraphicService`
  precedent (Plan 95-04 pattern).
- **Disabled-button uses existing `.btn .btn-secondary .btn-sm
  .disabled` triple** (Phase-94 archive-modal precedent). No new CSS;
  the only template addition is the Discord Actions card + the
  three-state form / span / form cluster.
- **D-96-GRX-1c regression-fence honored.** The Plan-96-01
  `DiscordPostServiceProvisionalScoresIT` is the same file, re-run
  post-sealed-switch-refactor — its `noThreadIdEverAppended`
  assertion still proves zero `thread_id=` URL params slip into
  PROVISIONAL_SCORES requests.

## Validation Results

- `./mvnw clean verify -Pe2e` -> BUILD SUCCESS, 10:08 min
- Total tests: **2120 green** (Plan 96-02 baseline was 2085; +35 from this plan)
- JaCoCo: all coverage checks met (gate >= 82%)
- SpotBugs: 0 bug instances, 0 errors
- `BackupSchemaGuardTest` stays green (EXPORT_ORDER=25, SCHEMA_VERSION=2)
- `DiscordPostServiceProvisionalScoresIT` 8/8 green post-refactor (D-96-GRX-1c regression-fence honored)

## Deviations from Plan

- **DiscordPostRepository gained 4 derived queries, not 3.** Added
  `findByPostTypeAndRaceId(DiscordPostType, UUID)` for the Race-Detail
  page lookup. The plan whitelisted 3, but the simple 2-key overload
  was the cleanest path for the new `raceResultsForumPost` model
  attribute; matches the Phase-95 lookup-by-context-and-target idiom.
- **`generateResultsBytes` uses temp-file roundtrip, not direct byte[]
  return from the screenshot engine.** Matches existing
  `MatchResultsGraphicService` pattern (`Files.createTempFile` ->
  `renderScreenshot` -> `readAllBytes` -> `deleteIfExists`). The plan
  hinted at this in the "shared-private-helper variant" section.
- **Model attribute `raceResultsForumPost = null`** does not appear in
  Spring's model when null (Spring's `model.addAttribute(name, null)`
  is a no-op). The E2E happy-path IT was relaxed to assert
  `canPostRaceResultToForum == true` + `forumPostDisabledReason ==
  null`; the absent-row branch is exercised by a separate IT.

## Operator Wave-Pause Verification (pending)

| Behavior | Status |
|----------|--------|
| Live Discord post-to-forum (manual UAT-06) | ⬜ pending operator |
| Auto-unarchive on real archived forum thread | ⬜ pending operator |
| `/gsd-validate-phase 96` Nyquist sampling sweep | ⬜ optional (full clean verify already gates the same coverage) |

UAT-06 is staged in `.planning/STATE.md` per D-96-10 for operator
action before Phase 97 starts. `nyquist_compliant: true` is flipped on
`96-03-VALIDATION.md` since the automated-coverage and regression-fence
gates are all green.
