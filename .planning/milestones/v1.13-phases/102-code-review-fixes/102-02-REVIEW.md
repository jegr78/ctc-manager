---
phase: 102-code-review-fixes
plan: 02
reviewed: 2026-05-28T00:00:00Z
depth: standard
files_reviewed: 64
files_reviewed_list:
  - src/main/java/org/ctc/admin/controller/MatchController.java
  - src/main/java/org/ctc/admin/controller/MatchdayController.java
  - src/main/java/org/ctc/admin/controller/RaceController.java
  - src/main/java/org/ctc/admin/controller/SeasonController.java
  - src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java
  - src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java
  - src/main/java/org/ctc/backup/audit/BackupExecutedByResolver.java
  - src/main/java/org/ctc/backup/audit/DataImportAuditRepository.java
  - src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java
  - src/main/java/org/ctc/backup/exception/BackupImportException.java
  - src/main/java/org/ctc/backup/restore/entity/DiscordPostRestorer.java
  - src/main/java/org/ctc/backup/schema/BackupSchema.java
  - src/main/java/org/ctc/backup/serialization/DiscordGlobalConfigMixIn.java
  - src/main/java/org/ctc/backup/serialization/DiscordPostMixIn.java
  - src/main/java/org/ctc/backup/service/BackupArchiveService.java
  - src/main/java/org/ctc/backup/service/BackupExportService.java
  - src/main/java/org/ctc/backup/service/BackupImportLimits.java
  - src/main/java/org/ctc/backup/service/BackupImportPostCommitListener.java
  - src/main/java/org/ctc/backup/service/BackupImportService.java
  - src/main/java/org/ctc/dataimport/CsvImportController.java
  - src/main/java/org/ctc/discord/DiscordConfig.java
  - src/main/java/org/ctc/discord/DiscordDevSeeder.java
  - src/main/java/org/ctc/discord/DiscordRateLimitInterceptor.java
  - src/main/java/org/ctc/discord/DiscordRestClient.java
  - src/main/java/org/ctc/discord/DiscordRoleCache.java
  - src/main/java/org/ctc/discord/DiscordWebhookClient.java
  - src/main/java/org/ctc/discord/dto/DiscordConfigForm.java
  - src/main/java/org/ctc/discord/dto/DiscordPostRef.java
  - src/main/java/org/ctc/discord/repository/DiscordPostRepository.java
  - src/main/java/org/ctc/discord/service/DiscordChannelService.java
  - src/main/java/org/ctc/discord/service/DiscordPostService.java
  - src/main/java/org/ctc/domain/model/Team.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/domain/service/ScoringService.java
  - src/main/java/org/ctc/domain/service/StandingsService.java
  - src/test/java/org/ctc/admin/controller/MatchControllerDetailViewModelTest.java
  - src/test/java/org/ctc/admin/controller/MatchControllerMoveToArchiveErrorCategoryTest.java
  - src/test/java/org/ctc/admin/controller/MatchdayControllerPostEndpointsIT.java
  - src/test/java/org/ctc/admin/service/DiscordSeasonViewServiceTest.java
  - src/test/java/org/ctc/admin/service/ProvisionalScoresGraphicServiceTest.java
  - src/test/java/org/ctc/backup/schema/BackupSchemaGuardTest.java
  - src/test/java/org/ctc/backup/service/BackupImportSchemaMismatchIT.java
  - src/test/java/org/ctc/backup/service/BackupRoundTripIT.java
  - src/test/java/org/ctc/build/AssumptionsFencePredicateTest.java
  - src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java
  - src/test/java/org/ctc/dataimport/GoogleCalendarServiceIT.java
  - src/test/java/org/ctc/dataimport/GoogleSheetsServiceIT.java
  - src/test/java/org/ctc/discord/DiscordClientHostWhitelistTest.java
  - src/test/java/org/ctc/discord/DiscordRateLimitInterceptorIT.java
  - src/test/java/org/ctc/discord/DiscordRestClientIT.java
  - src/test/java/org/ctc/discord/DiscordRoleCacheTest.java
  - src/test/java/org/ctc/discord/dto/DiscordConfigFormTest.java
  - src/test/java/org/ctc/discord/dto/DiscordPostRefSeasonRefWidenedTest.java
  - src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceForumThreadIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceWebhookUrlPatternTest.java
  - src/test/java/org/ctc/discord/web/DiscordConfigControllerErrorCategoryTest.java
  - src/test/java/org/ctc/domain/model/TeamEffectiveDiscordRoleIdTest.java
  - src/test/java/org/ctc/domain/service/ScoringServiceTest.java
  - src/test/java/org/ctc/domain/service/StandingsServicePhaseScopedStaleDetectionIT.java
  - src/test/java/org/ctc/domain/service/StandingsServiceStalenessSnapshotTest.java
findings:
  critical: 2
  warning: 11
  info: 8
  total: 21
status: issues-found
---

# Phase 102 Plan 02 — Warning-Fixes & Refactor-Extracts — Code Review Report

**Reviewed:** 2026-05-28
**Depth:** standard
**Files Reviewed:** 64
**Status:** issues-found

## Summary

Plan 102-02 lands 49 substantive warning closures plus 3 controller-thin refactor extracts on the
`gsd/v1.13-discord-integration` branch. The substantive fixes (DiscordRoleCache atomic swap,
parseAllow null-guard, Team blank-as-absent, MatchController `moveToArchive` distinct flash,
ScoringService split, BackupSchema generalization, Webhook regex parity, parseIntSafe/parseDoubleSafe,
single `discordUserAgent` bean) are largely correct and have accompanying regression tests. The
3 controller-thin extracts (`MatchService.buildMatchDetailModel`, `DiscordSeasonViewService.
buildDiscordIntegrationModel`, `StandingsService.snapshotMatchdayStaleness`) each carry a
boundary test.

However, the review surfaces two blockers that violate the plan's own success criteria, plus a
set of architectural / quality concerns:

1. **WR-thin-3 was not actually closed in production code** — `StandingsService.snapshotMatchdayStaleness`
   is invoked only from its boundary test; `MatchdayController.populateMatchdayDiscordModel` still
   houses 75 lines of staleness/model-population business logic (well above the plan's
   "≤ 20 lines" acceptance criterion). The refactor extract exists but is dead code from a
   production perspective.
2. **`DiscordSeasonViewService.buildDiscordIntegrationModel` performs DB writes during a GET**
   without an enclosing `@Transactional` annotation, and the legacy-row backfill it performs
   silently mutates production data on every season-edit render.

The remaining findings cluster around: unused imports (caused by the extract refactors), dead
phase-marker references in test Javadoc, redundant same-package import, missing playoff branch
in `ScoringService.recomputeMatchScoresFromAllLegs`, semantic mismatch between `DiscordRoleCache`'s
"atomic swap" claim in SUMMARY vs. the actual `putAll-then-retainAll` implementation, and minor
quality issues.

Marker-pollution grep across all 64 reviewed files: clean (zero `// Phase|Plan|D-NN|WR-NN|...`
markers in source code). One soft-marker in test Javadoc (`T-91-02-IL`) is flagged as INFO.

## Critical Issues

### CR-01: `WR-thin-3` not actually closed — MatchdayController still houses staleness business logic; `snapshotMatchdayStaleness` is dead code in production

**File:** `src/main/java/org/ctc/admin/controller/MatchdayController.java:86-160`
**File:** `src/main/java/org/ctc/domain/service/StandingsService.java:33-48`
**Issue:**
The plan's WR-thin-3 acceptance criterion (102-02-PLAN.md Task 3) requires:
> *"The controller's matchday-detail action is ≤ 20 lines of model-population."*

and the plan's `truths` say:
> *"MatchdayController staleness logic + its sole-use seasonTeamRepository field are extracted to StandingsService"*

The current `MatchdayController.populateMatchdayDiscordModel(...)` is **75 lines** (line 86 to
line 160) and still calls the per-staleness methods individually:

```java
boolean matchdayResultsStale = matchdayOverviewPost != null
        && standingsService.isMatchdayResultsStale(matchday, matchdayOverviewPost);
boolean powerRankingsStale = powerRankingsPost != null
        && standingsService.isPowerRankingsStale(matchday.getSeason(), powerRankingsPost);
// ...
boolean matchdayPairingsStale = matchdayPairingsPost != null
        && standingsService.isMatchdayPairingsStale(matchday, matchdayPairingsPost);
// ...
boolean matchdayScheduleStale = matchdaySchedulePost != null
        && standingsService.isMatchdayScheduleStale(matchday, matchdaySchedulePost);
```

The new `StandingsService.snapshotMatchdayStaleness(...)` is added (lines 33-48) but
`grep -rn snapshotMatchdayStaleness src/main/java` returns ONLY the declaration — no call site.
The only invocation is in `StandingsServiceStalenessSnapshotTest`. The extracted method is dead
code in production; the controller still owns the orchestration of the 4 stale flags + the
`DiscordPost` lookups + the `resolveAnnouncementChannelId(...)` calls. The `seasonTeamRepository`
field was correctly removed from the controller (SUMMARY claim is accurate on that single sub-task),
but the bulk of the extraction never landed.

This violates the plan's own success criterion and leaves the architectural debt that
WR-thin-3 was meant to close. The Plan 102-04 close-loop review will flag this again unless it
is fixed first.

**Fix:**
Either (a) actually call `snapshotMatchdayStaleness` from the controller after fetching the 4
`DiscordPost` rows, replacing the 4 inline `standingsService.is*Stale(...)` calls with a single
record-destructure, OR (b) extend the new service method to also own the `DiscordPost` lookups
(taking `Matchday` + a `DiscordPostRepository`-like collaborator) so the controller hands off
the whole block. Option (b) matches the SUMMARY's "extracted" language better. Add at least one
production-path test (e.g., extend `MatchdayControllerPostEndpointsIT`) that fails if the
controller does not delegate to the new service method.

```java
// MatchdayController.populateMatchdayDiscordModel — proposed shape (~10 lines)
DiscordGlobalConfig config = discordGlobalConfigService.getOrInitialize();
StandingsService.MatchdayDiscordModel m = standingsService.buildMatchdayDiscordModel(matchday, config);
model.addAllAttributes(m.asAttributes());
```

---

### CR-02: `DiscordSeasonViewService.buildDiscordIntegrationModel` writes to the DB during a GET-render path without `@Transactional`

**File:** `src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java:90-107`
**File:** `src/main/java/org/ctc/admin/controller/SeasonController.java:88-106`
**Issue:**
`SeasonController.edit(...)` is a GET handler. It calls
`discordSeasonViewService.buildDiscordIntegrationModel(season.getId())` on line 104.
Inside the view service, `lookupPhaseScopedStandings(...)` (line 90) performs:

```java
DiscordPost legacy = discordPostRepository
        .findByChannelIdAndPostTypeAndSeasonIdAndPhaseIdIsNull(
                channelId, DiscordPostType.STANDINGS, seasonId)
        .orElse(null);
if (legacy == null) {
    return null;
}
legacy.setPhaseId(phase.getId());
return discordPostRepository.save(legacy);   // <-- DB WRITE during GET
```

Two problems:

1. **Side-effect during a GET-render path.** Every render of `/admin/seasons/{id}/edit` may
   mutate `discord_post` rows. The mutation is reasonable on its own (back-fill the new V14
   `phase_id` for legacy rows), but it should not live in a view-model assembler — it should
   be a one-time migration job (or migration script V16+) or an idempotent service method
   invoked from an explicit POST. Per CLAUDE.md "Architectural Principles / No Fallback
   Calculations": *"If derived data is missing, do not implement workarounds in templates or
   controllers. Instead, analyze the data model and service architecture and fix the root
   cause — data must be written consistently in the correct place."* The Phase 95 WR-06 fix
   (95 SUMMARY: "WR-06 (legacy STANDINGS row fallback via …PhaseIdIsNull finder + phase
   backfill)") landed the read-side correctly but converted it into a *write-during-GET*
   side-effect, which is exactly the anti-pattern CLAUDE.md forbids.
2. **No `@Transactional` boundary on the service method.** `buildDiscordIntegrationModel` is
   declared without `@Transactional` (neither read-only nor read-write). Spring Data JPA's
   `save(...)` will open its own implicit transaction, but the loop in lines 67-75 calls
   `lookupPhaseScopedStandings(...)` for every phase, meaning each phase potentially gets its
   own micro-transaction. With OSIV the read works, but the writes are not coordinated. If
   two phases both back-fill, a partial failure leaves one phase backfilled and one not, with
   no rollback. The expected pattern is `@Transactional` on the service method (read-write
   when the back-fill can fire, read-only otherwise).

**Fix:**
Either remove the back-fill from the view-model path (preferred per CLAUDE.md root-cause
fix — make it a one-shot Flyway data-migration V16) OR move the back-fill into a dedicated
`@Transactional` admin endpoint operator must call once, with a banner in the UI on
unbackfilled rows ("Click Back-fill Legacy Standings Posts"). If kept inline, at minimum add
`@Transactional` on `buildDiscordIntegrationModel` and guard the save with a log line so
the silent mutation is auditable. Also extend `DiscordSeasonViewServiceTest` with a test that
fails if the GET-render writes more than zero rows on the happy path (i.e., no legacy data).

```java
@Transactional  // or move the back-fill to a one-shot job
public Map<String, Object> buildDiscordIntegrationModel(UUID seasonId) {
    // ...
}
```

## Warnings

### WR-01: Unused imports in `MatchdayController` (`Objects`, `Optional`) — extract left them orphaned

**File:** `src/main/java/org/ctc/admin/controller/MatchdayController.java:7-8`
**Issue:**
After the WR-thin-3 extract (partial — see CR-01), the imports `java.util.Objects` and
`java.util.Optional` are no longer referenced anywhere in `MatchdayController`. Confirmed via
`grep -n "Objects\|Optional" src/main/java/org/ctc/admin/controller/MatchdayController.java`:
the only matches are the import lines themselves.
**Fix:**
Delete both imports. (Will need re-addition once CR-01 is fixed if `Optional` re-appears in
the simplified flow, but for the current state they are dead.)

### WR-02: Unused imports in `SeasonController` (`LinkedHashMap`, `Map`) — extract left them orphaned

**File:** `src/main/java/org/ctc/admin/controller/SeasonController.java:10-11`
**Issue:**
After the WR-thin-2 extract, `populateDiscordIntegrationModel` and its private helpers were
removed; the `Map<String, Object>` and `LinkedHashMap` they used are no longer referenced in
the controller. `grep -n "LinkedHashMap\|Map<" src/main/java/org/ctc/admin/controller/SeasonController.java`
returns only the import line.
**Fix:**
Delete both imports.

### WR-03: `ScoringService.recomputeMatchScoresFromAllLegs` only handles `Match`, silently leaves `PlayoffMatchup` stale on clear-results

**File:** `src/main/java/org/ctc/domain/service/ScoringService.java:51-87`
**Issue:**
`aggregateMatchScores` (lines 89-149) handles both branches: `race.getMatch()` AND
`race.getPlayoffMatchup()`. The new `recomputeMatchScoresFromAllLegs` (introduced by Plan 102-02
late-regression-fold-back per SUMMARY "Task 6 WR-02 — initial fix tightened later via
`recomputeMatchScoresFromAllLegs`") handles only `race.getMatch()` (lines 63-86). Caller
`RaceService.saveResults` line 267-271 routes empty-results to `recomputeMatchScoresFromAllLegs`,
empty-results-for-a-playoff-race will return early without recomputing the playoff matchup
score, leaving stale `PlayoffMatchup.homeScore` / `awayScore` from the prior leg's run.

This violates CLAUDE.md "Score Aggregation on Result Save": *"After every
`raceRepository.save(race)` that wrote results (controller, import, or service), call
`scoringService.aggregateMatchScores(race)`. Standings depend on aggregated scores on
`Match`/`PlayoffMatchup` — never recompute in templates or controllers."* The principle
spans BOTH match types; `recomputeMatchScoresFromAllLegs` only honors half.
**Fix:**
Mirror `aggregateMatchScores`'s playoff branch inside `recomputeMatchScoresFromAllLegs`:

```java
@Transactional
public void recomputeMatchScoresFromAllLegs(Race race) {
    if (race.isBye()) return;
    if (race.getMatch() != null && race.getMatch().getHomeTeam() != null) {
        // ... existing match branch ...
    }
    if (race.getPlayoffMatchup() != null && race.getPlayoffMatchup().getTeam1() != null) {
        // Same pattern but on raceRepository.findByPlayoffMatchupId(...)
    }
}
```

Add a `ScoringServiceTest` case `givenClearedPlayoffRace_whenRecompute_thenPlayoffMatchupScoresRecomputed`.

### WR-04: `DiscordRoleCache.refresh` is NOT atomic — claim in SUMMARY misstates the semantics

**File:** `src/main/java/org/ctc/discord/DiscordRoleCache.java:45-55`
**Issue:**
Plan 102-02-SUMMARY says:
> *"WR-03 (`DiscordRoleCache.refresh` putAll-before-retainAll closes no-roles window)"*

The implementation:
```java
public int refresh(List<Role> roles) {
    Map<String, CachedEntry<Role>> next = new HashMap<>(roles.size());
    Instant expiry = clock.instant().plus(TTL);
    for (Role role : roles) {
        next.put(role.id(), new CachedEntry<>(role, expiry));
    }
    store.putAll(next);
    store.keySet().retainAll(next.keySet());  // <-- non-atomic window opens here
    // ...
}
```

A concurrent reader between `store.putAll(next)` and `store.keySet().retainAll(next.keySet())`
sees BOTH the new entries AND the old entries that should be evicted — i.e., a transiently
larger snapshot, not a smaller one. The original "no-roles window" (clear-then-fill) was
indeed closed, but it has been replaced with a "stale-entries window" of slightly different
shape: callers between the two lines may see deleted-but-still-cached roles. For role-membership
checks this could approve access to a deleted role. The atomic-swap claim in SUMMARY is not
backed by the code.
**Fix:**
Either declare the actual semantics in a one-line WHY comment ("two-phase update: new entries
materialise before old entries are evicted; readers may briefly see both sets")
OR use a true atomic swap via a volatile reference:

```java
private volatile Map<String, CachedEntry<Role>> store = Map.of();

public int refresh(List<Role> roles) {
    Map<String, CachedEntry<Role>> next = new HashMap<>(roles.size());
    Instant expiry = clock.instant().plus(TTL);
    for (Role role : roles) {
        next.put(role.id(), new CachedEntry<>(role, expiry));
    }
    this.store = Map.copyOf(next);  // single volatile write — atomic swap
    return next.size();
}
```

The second form genuinely closes both windows (no-roles AND stale-roles).

### WR-05: `MatchService.buildMatchDetailModel` invokes `discordPostService.matchHasCompleteSettings` (and 3 sibling preflight calls) even when no Discord-channel exists on the match

**File:** `src/main/java/org/ctc/domain/service/MatchService.java:95-97`
**Issue:**
`buildMatchDetailModel` unconditionally calls:
```java
model.put("matchHasCompleteSettings", discordPostService.matchHasCompleteSettings(match));
model.put("matchHasCompleteLineups", discordPostService.matchHasCompleteLineups(match));
model.put("matchHasProvisionalData", discordPostService.matchHasProvisionalData(match));
```

`matchHasCompleteLineups` issues a `raceLineupRepository.findByRaceId(...)` query per race
(`!raceLineupRepository.findByRaceId(r.getId()).isEmpty()`). For a match that does not have a
Discord channel (`match.getDiscordChannelId() == null` — the same condition that short-circuits
`findMatchPost`), these checks are still rendered into the model and the model attributes are
consumed by the template. The pre-Phase-102 controller code did the same thing, so this is NOT
a regression introduced here — but the extract was an opportunity to move the per-race lineup
queries behind a `discordAnnouncementsConfigured` or `match.getDiscordChannelId() != null`
guard (the template would render the bare detail without the Discord enrichment).
**Fix:**
Behind a `match.getDiscordChannelId() != null` guard, populate the 3 boolean attributes;
default them to `false` when no channel is provisioned. Validate the template still renders
the bare match detail by extending `MatchControllerDetailViewModelTest` with a case where
`match.getDiscordChannelId()` is null. Lower-risk alternative: leave the attribute names but
short-circuit inside the helper methods (preserve template contract).

### WR-06: `DiscordPostService.unarchiveIfArchived` retains stale-archived state silently; only a `log.warn` is emitted on rejection

**File:** `src/main/java/org/ctc/discord/service/DiscordPostService.java:845-856`
**Issue:**
When Discord rejects the PATCH unarchive (or a race between bot and another client re-archives
the thread immediately), `after.threadMetadata().isArchived()` may remain `true`. The method
proceeds to call `webhookClient.execute(webhookUrl, payload, threadId)` on the still-archived
thread — Discord will then 403 or 404 the post, and the operator gets a transient/auth flash
message that doesn't surface the actual root cause ("forum thread cannot be unarchived"). The
log.warn (line 853) records the bug but does not stop the post from being attempted.
**Fix:**
Either throw a `BusinessRuleException("Forum thread {threadId} is still archived — operator
must unarchive manually via Discord")` on the still-archived path, OR re-throw a transient
exception, so the controller's flash text is accurate. Existing IT
`DiscordPostServiceForumThreadIT.givenArchivedThread_…` only covers the happy
path (unarchive succeeds); add a negative case `givenArchivedThreadStaysArchived_…thenThrowsBusinessRuleException`.

### WR-07: `parseAllow` guard is too narrow — handles null/blank but not malformed (non-numeric) allow strings

**File:** `src/main/java/org/ctc/discord/service/DiscordChannelService.java:221-223`
**Issue:**
The audit-fail fix (94 WR-04) tightened the null/blank case correctly:
```java
private static long parseAllow(String allow) {
    return (allow == null || allow.isBlank()) ? 0L : Long.parseLong(allow);
}
```

But a Discord response with `"allow": "garbage"` (malformed JSON value that survived
deserialization as a string) would throw `NumberFormatException` and propagate up to the
caller. `DiscordChannelServicePermissionAuditFailIT` only covers the null path
(`givenFetchChannelReturnsOverwriteWithNullAllow_…`). The new `parseIntSafe` /
`parseDoubleSafe` pattern from Phase 93 WR-01 (`DiscordRateLimitInterceptor` lines 100-120)
shows the team's preferred pattern; `parseAllow` should match.
**Fix:**
```java
private static long parseAllow(String allow) {
    if (allow == null || allow.isBlank()) return 0L;
    try {
        return Long.parseLong(allow.trim());
    } catch (NumberFormatException _) {
        log.warn("Discord audit returned non-numeric allow value: {}", allow);
        return 0L;  // treat as no permissions granted — fail-closed
    }
}
```

Add a `DiscordChannelServiceNamingTest` case
`givenNonNumericAllow_whenAssertPermissionAudit_thenTreatedAsZeroNotThrown`.

### WR-08: `DiscordRoleCache.snapshot` builds an intermediate `LinkedHashMap` then copies — unnecessary copy on hot path

**File:** `src/main/java/org/ctc/discord/DiscordRoleCache.java:27-35`
**Issue:**
The hot path for role checks is `snapshot()` (called via `DiscordRoleCache.snapshot()` from
the admin role-mapping view-model). Each call builds a `LinkedHashMap`, populates it via
forEach, then converts to `Map.copyOf(valid)` — two allocations and copies per call. Per
CLAUDE.md "Architectural Principles", performance is out of v1 scope, but the existing
`Map.copyOf` already serves as defensive immutability — the LinkedHashMap intermediate is
not load-bearing (callers don't rely on insertion order; they grep by role id).
**Fix:**
This is borderline INFO but called out as WARNING because the same hot path is exercised on
every admin role-mapping render — drop the LinkedHashMap and stream straight into the copy:
```java
return store.entrySet().stream()
        .filter(e -> e.getValue().isValid(clock))
        .collect(toUnmodifiableMap(Map.Entry::getKey, e -> e.getValue().value()));
```

### WR-09: `DiscordSeasonViewService` builds two `LinkedHashMap`s (`standingsPostByPhase`, `standingsStaleByPhase`) for the disabled branch with redundant null/false sentinels

**File:** `src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java:67-81`
**Issue:**
The disabled branch (`canPostStandings == false`) seeds both maps with `null` / `false` for every
phase. The template consumers (Thymeleaf `season-form.html`) use `${standingsPostByPhase[phaseId]}`
which would gracefully return null on missing keys via Spring's `MessageExpression` — i.e., the
sentinel-fill is defensive but redundant. More importantly, the active branch's `for (SeasonPhase
p : allPhases)` and the disabled branch's `for (SeasonPhase p : allPhases)` produce different
LinkedHashMap iteration semantics that the template might rely on.
**Fix:**
Either (a) explicitly document the disabled-branch sentinel behaviour with a 1-line WHY comment
(template-iteration contract), OR (b) collapse both branches by extracting a `phaseLookup(phase, canPostStandings)`
helper that returns `null` when disabled, and use a streaming `Collectors.toMap` so the iteration
order is preserved without two parallel maps.

### WR-10: `DiscordSeasonViewService.lookupPhaseScopedStandings` reflows REGULAR-phase fallback ONLY — non-REGULAR phases skip the legacy backfill silently

**File:** `src/main/java/org/ctc/admin/service/DiscordSeasonViewService.java:90-107`
**Issue:**
```java
if (post != null || phase.getPhaseType() != org.ctc.domain.model.PhaseType.REGULAR) {
    return post;
}
```

Pre-V14 `STANDINGS` rows might have been posted under PLAYOFF or PLACEMENT phases too (the
pre-V14 schema had no phase_id at all — all 3 phase types collapsed into one). The backfill
ONLY rescues the REGULAR-phase case; PLAYOFF and PLACEMENT phases will silently lose their
historical posts (the legacy row stays in `discord_post` with `phase_id IS NULL` and never
matches the `phaseId` lookup). Given this is a backfill for a one-shot data migration that
should be doing the right thing for all 3 phase types, the REGULAR-only filter is
under-scoped.
**Fix:**
Either drop the `phase.getPhaseType() != REGULAR` short-circuit (back-fill for every phase
type, assigning to the first phase that asks) OR add a comment explaining why PLAYOFF /
PLACEMENT legacy posts are intentionally orphaned. Better: extract the back-fill out of
the GET-render path entirely (see CR-02) so this whole concern moves to a one-time data
migration where the cross-phase contention can be resolved deterministically.

### WR-11: `ScoringService.aggregateMatchScores` Javadoc duplicated — two consecutive Javadoc blocks on the same method

**File:** `src/main/java/org/ctc/domain/service/ScoringService.java:51-87`
**Issue:**
The `recomputeMatchScoresFromAllLegs` method declaration is preceded by TWO Javadoc blocks
back-to-back (lines 51-56 and lines 57-60):
```java
/**
 * Aggregates race result scores onto the parent Match or PlayoffMatchup.  // <-- describes aggregateMatchScores
 * Call this after saving race results to keep match scores in sync.
 * Uses database query to ensure all legs are included, even when lazy-loaded collections are incomplete.
 */
/**
 * Re-derive Match home/away scores from the persisted legs of {@code race}'s match,
 * even when {@code race} itself has no results. Used by {@code RaceService.saveResults}
 * when the operator clears a race so the match score doesn't stay stale.
 */
@Transactional
public void recomputeMatchScoresFromAllLegs(Race race) {
```

The first Javadoc clearly belongs to `aggregateMatchScores` and was left orphaned when
`recomputeMatchScoresFromAllLegs` was inserted above it. Javadoc tools will attach the
first comment to neither method (it's not the immediately-preceding Javadoc). At minimum,
this is misleading documentation; at worst, it's a copy-paste artifact from the
late-regression-fold-back commit `14dcd49a`.
**Fix:**
Move the first Javadoc to immediately precede `aggregateMatchScores` (around line 88) or
delete it if `recomputeMatchScoresFromAllLegs`'s Javadoc already covers the contract.

## Info

### IN-01: Phase-marker comment in test Javadoc — `T-91-02-IL invariant`

**File:** `src/test/java/org/ctc/dataimport/CsvImportControllerExceptionTest.java:34`
**Issue:**
```java
/**
 * Focused tests for CsvImportController exception-handling behavior.
 * Verifies the 5-arm typed-catch surface (4 sealed permits + defensive base) on
 * previewSheet() and execute(), and the T-91-02-IL invariant: typed GoogleApiException
 * arms must render a whitelisted literal — never echo e.getMessage().
 */
```

The `T-91-02-IL` reference is a phase/task-ID marker, the exact pattern CLAUDE.md "No
Comment Pollution" hard-bans for source code. The regex in 102-02-PLAN.md "specifics"
section only matches `^\s*(//|--|#)\s*(Phase |Plan |D-NN|UAT-|WR-NN|...)` — a Javadoc
block-comment slips past the grep oracle. But the spirit of the rule applies: tests
should describe behavior, not phase history.
**Fix:**
Replace `T-91-02-IL invariant` with the actual semantic name (e.g., "typed-catch info-leak
invariant"). Memo for the repo maintainer: extend the marker-grep regex to also catch
`T-[0-9]+-[0-9]+-[A-Z]+` patterns.

### IN-02: Redundant same-package import in `DiscordDevSeeder`

**File:** `src/main/java/org/ctc/discord/DiscordDevSeeder.java:14`
**Issue:**
`DiscordDevSeeder` is in package `org.ctc.discord`. The import
`import org.ctc.discord.DiscordEmojiCache;` is for a class in the same package — no
import is required and most style guides (Google, IDE auto-organize) treat this as
removable noise.
**Fix:**
Delete the import line.

### IN-03: `DiscordRoleCache.snapshot` returns `Map.copyOf` while documentation claims expiry-filter — naming-vs-implementation mismatch

**File:** `src/main/java/org/ctc/discord/DiscordRoleCache.java:27-35`
**Issue:**
`snapshot()` returns a `LinkedHashMap` populated only with entries that pass
`entry.isValid(clock)`, then `Map.copyOf(valid)`. The class-level Javadoc / contract does
not document the expiry-filter semantics (no Javadoc at all on the method). Callers must
read the body to understand that expired entries are silently dropped.
**Fix:**
Add a single-line Javadoc:
```java
/** Returns a snapshot of all non-expired role entries. */
public Map<String, Role> snapshot() { … }
```

### IN-04: `MatchService.buildMatchDetailModel` uses `LinkedHashMap` with no comment on the contract — Thymeleaf doesn't depend on iteration order, but the model attribute set IS the public contract

**File:** `src/main/java/org/ctc/domain/service/MatchService.java:85`
**Issue:**
The choice of `LinkedHashMap` over `HashMap` was deliberate per the SUMMARY (deterministic
attribute order for downstream consumers, though `model.addAllAttributes(...)` uses an
internal `LinkedHashMap` anyway). No comment explains this. A future maintainer might
"optimize" to `HashMap` and break nothing visible — but future debug logs / test
diff-snapshots would show non-deterministic order.
**Fix:**
Either (a) drop the LinkedHashMap for the simpler `HashMap` (no consumer depends on order),
OR (b) add a 1-line WHY comment: `// LinkedHashMap: deterministic attribute order for
template debug-log inspection`.

### IN-05: `DiscordPostService.escapeMarkdownLinkUrl` only escapes `)` — misses other Discord-markdown breaking chars (`>`, `<`)

**File:** `src/main/java/org/ctc/discord/service/DiscordPostService.java:669-671`
**Issue:**
The 95 WR-04 fix escapes `)` so URLs ending in `)` don't break Discord's `[label](url)` parser.
Discord's markdown also treats `<URL>` as auto-link suppression and `>` inside the URL can
cause similar parsing edge cases. The current escape is narrow and may not protect against
other URLs in the wild (e.g., a Twitch stream URL with query params containing `)`).
**Fix:**
This is INFO because the specific 95 WR-04 finding is closed and the broader URL-escape
hardening is out of scope for Plan 102-02. Memo for a future hardening pass: extend the
escape to also handle `>` (`>` → `\>` per Discord's markdown spec).

### IN-06: `BackupSchema.pinFkEntitiesLast` private static method is now generic but only called with one argument shape

**File:** `src/main/java/org/ctc/backup/schema/BackupSchema.java:72-84`
**Issue:**
The 101 WR-07 fix generalized `pinDiscordPostLast` to `pinFkEntitiesLast(List<EntityRef>,
Set<Class<?>>)` but the only caller is the `@PostConstruct` initializer with
`FK_TAIL_ENTITIES = Set.of(DiscordPost.class)`. The generalization is a no-op at the
current call shape. Per CLAUDE.md "Default: no comments", the additional flexibility
should be exercised by at least one alternative caller in a test (e.g., a unit test
that calls `pinFkEntitiesLast(...)` with `Set.of(SomeFutureEntity.class)` to prove
the generalization actually works for the documented future use case).
**Fix:**
Add a `BackupSchemaPinFkEntitiesLastTest` unit test that calls the method directly with
a synthetic 2-entity set and asserts both are reordered to the tail. (Currently the only
test coverage is the side-effect through `BackupSchemaGuardTest.exportOrderHasTwentySixEntities`,
which does not exercise the generalization.)

### IN-07: `BackupArchiveService.openHardened` does nothing beyond `new ZipInputStream` — the name is misleading

**File:** `src/main/java/org/ctc/backup/service/BackupArchiveService.java:548-551`
**Issue:**
```java
private ZipInputStream openHardened(Path zipPath) throws IOException {
    InputStream fis = Files.newInputStream(zipPath);
    return new ZipInputStream(fis);
}
```

The Javadoc above admits the hardening doesn't happen here ("Per-entry guarantees … are NOT
applied here; they live in `assertEntrySafe(...)`"). The method name suggests safety the
implementation does not provide. A code reviewer skimming for "is this hardened?" would
falsely conclude yes.
**Fix:**
Rename to `openZipInputStream(...)` and inline the body into the 3 call sites (it's a
2-line method) OR keep the name but add `assertEntrySafe`-style invariants here (e.g.,
file-size cap before opening).

### IN-08: `BackupExportService.lookupRepository` throws `IllegalArgumentException` referencing `BackupSchema.getExportOrder()` size; consistent with 101 WR-01 but message could include the missing entity name explicitly

**File:** `src/main/java/org/ctc/backup/service/BackupExportService.java:300-309`
**Issue:**
The error message references `repositoriesByEntityClass.size()` as the "26 entities" count
(per 101 WR-01 / WR-02). The operator sees: *"No repository registered for entity class
org.ctc.domain.model.WhatsItsName — must be one of the 26 BackupSchema.getExportOrder()
entities"*. The message is correct but the operator now has to grep the codebase to find
which entity was actually missing. Including `BackupSchema.getExportOrder().stream().map(EntityRef::tableName).toList()`
in the error message (or at least the missing class name) shortens the diagnostic loop.
**Fix:**
Append `+ ". Registered entities: " + repositoriesByEntityClass.keySet().stream().map(Class::getSimpleName).sorted().toList()`
to the exception message. (Low-priority polish; the find-by-grep is fast.)

---

_Reviewed: 2026-05-28_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
_Plan scope: 102-02 (warning fixes + 3 controller-thin refactor extracts)_
_Marker-pollution grep: clean (0 hits on source-code marker patterns)_
