---
phase: 98-polish-e2e-docs-close
reviewed: 2026-05-28T12:00:00Z
depth: standard
files_reviewed: 27
files_reviewed_list:
  - config/spotbugs-exclude.xml
  - pom.xml
  - src/main/java/org/ctc/admin/TestDataService.java
  - src/main/java/org/ctc/admin/controller/MatchdayController.java
  - src/main/java/org/ctc/admin/dto/MatchdayPairingsForm.java
  - src/main/java/org/ctc/admin/service/MatchdayPairingsGraphicService.java
  - src/main/java/org/ctc/discord/DiscordDevSeeder.java
  - src/main/java/org/ctc/discord/dto/DiscordConfigForm.java
  - src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java
  - src/main/java/org/ctc/discord/model/DiscordPostType.java
  - src/main/java/org/ctc/discord/service/DiscordGlobalConfigService.java
  - src/main/java/org/ctc/discord/service/DiscordPostService.java
  - src/main/java/org/ctc/discord/web/DiscordConfigController.java
  - src/main/java/org/ctc/domain/model/Matchday.java
  - src/main/java/org/ctc/domain/service/MatchdayService.java
  - src/main/resources/db/migration/V15__add_matchday_pairings_fields.sql
  - src/main/resources/static/admin/css/admin.css
  - src/main/resources/templates/admin/discord-config.html
  - src/main/resources/templates/admin/matchday-detail.html
  - src/main/resources/templates/admin/matchday-pairings-form.html
  - src/main/resources/templates/admin/matchday-pairings-render.html
  - src/test/java/org/ctc/admin/controller/MatchdayControllerPostEndpointsIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayPairingsIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayPairingsPreFlightTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayScheduleIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdaySchedulePreFlightTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceScheduleIT.java
  - src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java
  - src/test/java/org/ctc/e2e/discord/announcement/MatchdayDetailDiscordAnnouncementE2ETest.java
  - src/test/java/org/ctc/e2e/discord/lifecycle/DiscordFullMatchdayLifecycleE2ETest.java
findings:
  critical: 0
  blocker: 2
  warning: 6
  info: 5
  total: 13
status: issues_found
---

# Phase 98: Code Review Report

**Reviewed:** 2026-05-28T12:00:00Z
**Depth:** standard
**Files Reviewed:** 27
**Status:** issues_found

## Summary

Phase 98 closes v1.13 (Discord integration) by adding the matchday-pairings
announcement, V15 schema, central WireMock stubs, and a full-lifecycle E2E
test. The recent fix `be5d9285 fix(security): sanitize CRLF in
MatchdayService.savePairings log` correctly mitigates log injection on the new
`scheduledWeekend` form input — verified.

Two real defects were found that should be addressed before milestone-close:

1. `canPostMatchdayPairings` and `canPostMatchdaySchedule` use
   `Stream.allMatch(...)` over `matchday.getMatches()`, which is **vacuously
   true** when the matchday has zero matches (or only BYE matches in the
   schedule case). An operator with an empty matchday (admin newly created the
   matchday but has not yet added matches) sees the "Post Matchday Pairings /
   Schedule" button as enabled and triggers a post against Discord that
   contains an empty image / empty content. The peer pre-flight
   `allNonByeMatchesFinal` (for `MATCHDAY_OVERVIEW`) explicitly guards against
   the empty case — these two new methods do not.

2. `TestDataService.seedFullMatchdayLifecycle()` creates a `Team` with
   `shortName="T-ALF"` that collides with the existing regular `seed()` team
   `("Test Alpha Racing", "T-ALF")`. Although `teams.short_name` has no UNIQUE
   constraint at the schema level, the repository declares
   `Optional<Team> findByShortName(String)` — multiple matches will throw
   `IncorrectResultSizeDataAccessException` on lookup. The E2E test runs in
   the `dev` profile, so `DevDataSeeder.seed()` populates `T-ALF` at app boot
   first, and then `seedFullMatchdayLifecycle()` (called from `@BeforeEach`)
   creates a second row. Violates CLAUDE.md "Isolate Test Data Completely".

Beyond these blockers, six warnings cover: (a) DiscordPostType `MATCHDAY_PAIRINGS`
+ `MATCHDAY_SCHEDULE` not yet exported by backup MixIn enumeration (verify
post-restore enum compatibility before merge); (b) missing `@Size` cap on the
operator-controlled `matchdayPairingsTemplate` (DB column is `TEXT`, so an
unbounded POST body lands directly in the row); (c) inconsistent webhook URL
regex between `DiscordConfigForm` (no query) and `DiscordPostService.parseWebhookUrl`
(accepts query); (d) duplicate work in `populateMatchdayDiscordModel`
(two calls to `resolveAnnouncementChannelId(announcementWebhookUrl)` for the
same URL); (e) `MatchdayPairingsForm` accepts free-text into the markdown
template path with no upper bound or character filter — operator-only but
worth a defensive guard; (f) test for `editPairings` GET endpoint missing.

## Blockers

### BL-01: Empty/all-BYE matchday passes pre-flight for pairings + schedule

**File:** `src/main/java/org/ctc/discord/service/DiscordPostService.java:392-409` (pairings) and `:438-450` (schedule)
**Issue:**
`canPostMatchdayPairings`:
```java
boolean allMatchesHaveTeams = matchday.getMatches().stream()
        .allMatch(m -> m.getHomeTeam() != null && m.getAwayTeam() != null);
```
`canPostMatchdaySchedule`:
```java
boolean allMatchesHaveRaceTime = matchday.getMatches().stream()
        .filter(m -> !m.isBye())
        .allMatch(m -> firstRaceTime(m).isPresent());
```
`Stream.allMatch` returns `true` for an empty stream, so:
- A matchday with **zero** `matches` rows produces `canPost=true` for both posts.
- A matchday with only BYE matches produces `canPost=true` for schedule.

`postMatchdayPairings` / `postMatchdaySchedule` will then call
`matchdayPairingsGraphicService.generatePairings(matchday)` /
`matchdayScheduleGraphicService.generateSchedule(matchday)` with no rows to
render — the resulting PNG is an empty `.matches` container and gets pushed to
Discord. Compare to `allNonByeMatchesFinal` (line 593-603) which correctly
guards `if (contested.isEmpty()) return false;`.

**Fix:**
Add the same guard pattern that `allNonByeMatchesFinal` uses. For pairings,
require at least one non-bye match with both teams set. For schedule, require
at least one non-bye contested match.

```java
public MatchPreviewPreFlightResult canPostMatchdayPairings(Matchday matchday, DiscordGlobalConfig config) {
    if (matchday.getPickDeadline() == null) {
        return new MatchPreviewPreFlightResult(false, "Set pick deadline first");
    }
    if (matchday.getScheduledWeekend() == null || matchday.getScheduledWeekend().isBlank()) {
        return new MatchPreviewPreFlightResult(false, "Set scheduled weekend first");
    }
    List<Match> contested = matchday.getMatches().stream().filter(m -> !m.isBye()).toList();
    if (contested.isEmpty()) {
        return new MatchPreviewPreFlightResult(false, "Add at least one match first");
    }
    boolean allHaveTeams = contested.stream()
            .allMatch(m -> m.getHomeTeam() != null && m.getAwayTeam() != null);
    if (!allHaveTeams) {
        return new MatchPreviewPreFlightResult(false, "Assign teams to all matches first");
    }
    String webhookUrl = config.getAnnouncementWebhookUrl();
    if (webhookUrl == null || webhookUrl.isBlank()) {
        return new MatchPreviewPreFlightResult(false, "Configure announcement-webhook in Discord settings");
    }
    return new MatchPreviewPreFlightResult(true, null);
}

public MatchPreviewPreFlightResult canPostMatchdaySchedule(Matchday matchday, DiscordGlobalConfig config) {
    List<Match> contested = matchday.getMatches().stream().filter(m -> !m.isBye()).toList();
    if (contested.isEmpty()) {
        return new MatchPreviewPreFlightResult(false, "Add at least one match first");
    }
    boolean allHaveRaceTime = contested.stream().allMatch(m -> firstRaceTime(m).isPresent());
    if (!allHaveRaceTime) {
        return new MatchPreviewPreFlightResult(false, "Set Race date+time for all matches first");
    }
    String webhookUrl = config.getAnnouncementWebhookUrl();
    if (webhookUrl == null || webhookUrl.isBlank()) {
        return new MatchPreviewPreFlightResult(false, "Configure announcement-webhook in Discord settings");
    }
    return new MatchPreviewPreFlightResult(true, null);
}
```
Add IT/unit tests for the empty-matchday and all-BYE-matchday cases so the
regression is locked.

---

### BL-02: `seedFullMatchdayLifecycle` collides with regular dev-seed on `T-ALF` shortName

**File:** `src/main/java/org/ctc/admin/TestDataService.java:1064` (vs `:835`)
**Issue:**
`seedFullMatchdayLifecycle()` (called from `DiscordFullMatchdayLifecycleE2ETest@BeforeEach`)
unconditionally executes:
```java
var home = teamRepository.save(new Team("Test Alfa", "T-ALF"));
```
The regular `seed()` already creates `new Team("Test Alpha Racing", "T-ALF")`
at line 835. Both fire during the E2E run (Spring `dev` profile boots
`DevDataSeeder.run()` which invokes `seed()`; then the test's `@BeforeEach`
invokes `seedFullMatchdayLifecycle()`).

There is no `UNIQUE` constraint on `teams.short_name` (verified against
`V1__initial_schema.sql:48`), so the duplicate INSERT succeeds, but:
- `TeamRepository.findByShortName(String)` returns `Optional<Team>` — Spring
  Data throws `IncorrectResultSizeDataAccessException` on multi-row results.
  Any code path that resolves a team by shortName (e.g. `DiscordDevSeeder.assignTeamRoles`
  via `roleIdByName.get(team.getShortName())` is by-Team-iteration so it
  survives, but operator-facing controllers using `findByShortName` will
  break).
- `DiscordDevSeeder.assignTeamRoles` iterates `teamRepository.findAll()` and
  sets `discordRoleId` — the second `T-ALF` row would get the same role
  assigned, creating ambiguous state.

Violates CLAUDE.md "Isolate Test Data Completely": E2E fixture short-names must
NOT collide with the regular seed.

**Fix:**
Rename the lifecycle fixture short-names so they cannot collide with the
regular `seed()` (suggested: prefix the lifecycle fixture with a unique tag,
e.g. `T-LFH` / `T-LFA`, or test-class scope `T-E2EL-H` / `T-E2EL-A`).

```java
var home = teamRepository.save(new Team("Test Lifecycle Home", "T-LFH"));
home.setDiscordRoleId("100000000000000001");
home = teamRepository.save(home);

var away = teamRepository.save(new Team("Test Lifecycle Away", "T-LFA"));
away.setDiscordRoleId("100000000000000002");
away = teamRepository.save(away);
```
Optionally guard the lifecycle seeder with a "skip if already present" check
analogous to `if (seasonRepository.count() > 0) return;` so re-invocation
during a single Spring context is idempotent.

## Warnings

### WR-01: `matchdayPairingsTemplate` has no `@Size` cap on the form DTO

**File:** `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java:49`
**Issue:**
```java
private String matchdayPairingsTemplate = "";
```
Every other free-text webhook/snowflake field carries `@Size(max=N)` (e.g.
`announcementWebhookUrl` has `@Size(max = 500)`). The template field is
unbounded and lands in a `TEXT` column. An operator (or compromised admin
session) can POST a multi-megabyte payload, which Spring will accept, JPA will
persist, and every subsequent `DiscordPostService.buildMatchdayPairingsMarkdown`
will load + interpolate into a Discord message (Discord rejects messages over
2000 chars, so the result is a wasted DB row plus a runtime 4xx).
**Fix:**
```java
@Size(max = 4000, message = "Template must be at most 4000 characters")
private String matchdayPairingsTemplate = "";
```
4000 chars matches Discord's per-message content limit comfortably (2000
chars + interpolation budget).

---

### WR-02: Duplicate channel-ID resolution in `populateMatchdayDiscordModel`

**File:** `src/main/java/org/ctc/admin/controller/MatchdayController.java:134-148`
**Issue:**
`discordPostService.resolveAnnouncementChannelId(announcementWebhookUrl)` is
called twice in the same method against the same URL — once for the pairings
post lookup (line 134-135) and once for the schedule post lookup (line 147-148).
Each call runs a regex match. Hot-path: every matchday-detail render. Minor
correctness risk because the two channel IDs are guaranteed equal; this is
duplication rather than a bug. However, copy-paste duplication in a controller
method that already runs 9 model lookups invites drift (a future tweak to the
pairings branch may forget the schedule branch).
**Fix:**
```java
String announcementChannelId = matchdayAnnouncementActive
        ? discordPostService.resolveAnnouncementChannelId(announcementWebhookUrl)
        : null;

DiscordPost matchdayPairingsPost = matchdayAnnouncementActive
        ? discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(
                announcementChannelId, DiscordPostType.MATCHDAY_PAIRINGS, matchday.getId())
                .orElse(null)
        : null;

DiscordPost matchdaySchedulePost = matchdayAnnouncementActive
        ? discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(
                announcementChannelId, DiscordPostType.MATCHDAY_SCHEDULE, matchday.getId())
                .orElse(null)
        : null;
```

---

### WR-03: Inconsistent webhook URL regex between Form DTO and `DiscordPostService.parseWebhookUrl`

**File:** `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java:15` vs `src/main/java/org/ctc/discord/service/DiscordPostService.java:72`
**Issue:**
Form regex:
```java
"^$|^https://discord\\.com/api/webhooks/\\d+/[A-Za-z0-9_-]+$"
```
Service regex:
```java
"^https?://[^/]+(?:/api)?(?:/v\\d+)?/webhooks/(\\d+)/([^/?]+)(?:\\?.*)?$"
```
The service regex is intentionally more permissive (allows HTTPs, wiremock
loopback URLs, `?wait=true` suffix, optional `/api/v10` prefix) so unit + IT
tests can pass synthetic URLs. The Form DTO regex is stricter, which is
correct for production. But the Form's "any webhook URL stored in
`discord_global_config`" assumption fails when a backup-restore (D-01,
26-entity scope from Phase 101) imports a URL that previously matched the
service regex but not the form regex. On the next edit-save, validation will
reject it without the operator having changed the field.
**Fix:**
Option 1 (preferred): align the Form regex to also allow an optional
`?wait=true` suffix and an optional `/v\\d+` prefix, mirroring the parser.
Option 2: document the divergence in `DiscordConfigForm` with a single-line
comment per CLAUDE.md "Allowed (rare): single-line comments for non-obvious
WHY". Skip until the backup-restore round-trip surfaces it.

---

### WR-04: `DiscordPostType` enum extended without backup-MixIn regression test

**File:** `src/main/java/org/ctc/discord/model/DiscordPostType.java:11-12`
**Issue:**
The new `MATCHDAY_PAIRINGS` + `MATCHDAY_SCHEDULE` enum constants must be
persisted as String in the backup JSON (via `BackupSerializationModule` MixIns
added in Phase 101). The Discord-post backup regression-fence
`BackupDiscordFieldRoundTripIT` was added at commit `9b4b9865` but pre-dates
the merge of these enum values. Without a per-enum round-trip assertion, a
typo in the JSON enum name (`MATCHDAY_PAIRING` etc.) would only surface during
an actual restore.
**Fix:**
Extend `BackupDiscordFieldRoundTripIT` to parameterise over all
`DiscordPostType` values, asserting export-then-restore byte-equality for each:
```java
@ParameterizedTest
@EnumSource(DiscordPostType.class)
void givenEachPostType_whenBackupRoundTrip_thenEnumPreserved(DiscordPostType type) {
    // … construct a DiscordPost with type, export, restore, assert reloaded.getPostType() == type
}
```

---

### WR-05: `DiscordDevSeeder.persistIfDirty` re-saves config that may have been mutated by `applyConfig` already

**File:** `src/main/java/org/ctc/discord/DiscordDevSeeder.java:42-93`
**Issue:**
The new control-flow does the following:
1. `cfg = configService.getOrInitialize()` (returns managed entity)
2. `templateBackfilled = backfillDefaultTemplates(cfg)` — mutates `cfg` if
   template is null
3. If `properties.hasGuildId()` is false → `persistIfDirty(cfg, templateBackfilled)` → returns
4. If `cfg.getGuildId()` is non-blank → `persistIfDirty` → returns
5. Otherwise → `applyConfig(cfg)` + `configRepository.save(cfg)`
   (this save includes the template backfill too)

The contract is OK, but the `backfillDefaultTemplates` mutation happens on a
managed JPA entity. Step 5 runs `configRepository.save(cfg)` which already
captures the template change — so when the early-return paths skip and the
guildId-branch runs, persistence works. However, in steps 3 and 4 the early
return invokes `persistIfDirty`, which catches `RuntimeException` but
ignores any thrown OptimisticLock / version mismatch — silent data loss is
possible if another transaction modified `discord_global_config` between
read and save. Low-likelihood (dev-only seeder, single thread at boot), but
the swallowed exception is logged at WARN and the seed completes "OK" — that
hides regressions.
**Fix:**
Either propagate the persist failure (this is dev-startup; failing fast is
acceptable) or log the exception with full stack trace at WARN+ exception
parameter, not `.toString()`:
```java
} catch (RuntimeException e) {
    log.warn("Discord dev-seed: failed to persist default-template backfill", e);
}
```
The `e` parameter expands the stack trace via SLF4J. Same fix applies to the
other two `log.warn(..., e.toString())` callers in this file.

---

### WR-06: `editPairings` GET endpoint lacks integration test coverage

**File:** `src/main/java/org/ctc/admin/controller/MatchdayController.java:253-263`
**Issue:**
`MatchdayControllerPostEndpointsIT` covers the POST endpoints (results, power
rankings, edge-case flashes) but does NOT exercise the new GET
`/admin/matchdays/{id}/edit-pairings` or POST `/admin/matchdays/{id}/save-pairings`
endpoints. `DiscordPostServiceMatchdayPairingsIT` covers the service contract
but not the controller's form-binding + redirect + flash semantics. A
regression to `editPairings` (e.g. removing the form prepopulation, breaking
the `@DateTimeFormat(iso=DATE_TIME)` binding) would only be caught by the E2E
suite, which is slow + Playwright-dependent.
**Fix:**
Add at least these IT cases to `MatchdayControllerPostEndpointsIT`:
- `givenValidMatchday_whenGetEditPairings_thenViewWithPrepopulatedForm`
- `givenValidForm_whenPostSavePairings_thenRedirectToDetailWithSuccessFlash`
- `givenBindingErrors_whenPostSavePairings_thenRendersFormWithErrors`

## Info

### IN-01: Test fixture comment polution in WireMockDiscordStubs

**File:** `src/test/java/org/ctc/discord/wiremock/WireMockDiscordStubs.java`
**Issue:**
Per CLAUDE.md "No Comment Pollution", the file is acceptable as written —
zero phase/plan/wave references, no greppable cross-references. The stub
methods are well-named and self-documenting. No change required. Listed here
only to confirm compliance.

---

### IN-02: `populateMatchdayDiscordModel` has 80+ lines; consider splitting

**File:** `src/main/java/org/ctc/admin/controller/MatchdayController.java:89-165`
**Issue:**
The method is long (76 lines) and now juggles five distinct concerns
(matchday-results post, power-rankings post, pairings post, schedule post, +
two webhook lookups + four stale-checks). Adding another DiscordPostType in
v1.14+ will compound. Functional but a maintenance hazard.
**Fix:**
Split into helpers per concern, e.g.
`addRaceResultsForumPosts(model, matchday, config)` and
`addAnnouncementPosts(model, matchday, config)`. Defer to a future
refactor; not v1.13 blocker.

---

### IN-03: `MatchdayPairingsForm.id` lacks `@NotNull`

**File:** `src/main/java/org/ctc/admin/dto/MatchdayPairingsForm.java:14`
**Issue:**
`private UUID id;` has no validation annotation. The controller binds `id`
from path-variable too, so a null binding from the body is overwritten in
practice — not a security gap. However, if the form is ever submitted via a
JSON API or accidentally posted without the hidden `<input type="hidden"
th:field="*{id}">`, the save would proceed using only the path-variable. Low
risk; defensive.
**Fix:**
```java
@NotNull
private UUID id;
```

---

### IN-04: `DiscordPostType` enum lacks doc-comment for `MATCHDAY_PAIRINGS` vs `MATCHDAY_SCHEDULE` semantics

**File:** `src/main/java/org/ctc/discord/model/DiscordPostType.java`
**Issue:**
13 enum constants, all without per-constant Javadoc. The new
`MATCHDAY_PAIRINGS` (announcement channel, pre-race) and `MATCHDAY_SCHEDULE`
(announcement channel, pre-race with race times) are distinct concepts that
both target the announcement channel; in v1.14 maintenance an operator may
ask "what's the difference". Skill-rule "default: no comments" applies, but
CLAUDE.md allows non-obvious WHY comments.
**Fix:**
Optionally add a single-line `// rendered as pure-image post in the
announcement channel` next to each new constant. Defer.

---

### IN-05: `MatchdayPairingsGraphicService` indent uses TABs while the rest of `admin.service` is spaces

**File:** `src/main/java/org/ctc/admin/service/MatchdayPairingsGraphicService.java`
**Issue:**
File uses TAB indentation (confirmed by raw read). The siblings
`MatchdayOverviewGraphicService.java`, `MatchdayScheduleGraphicService.java`
in the same package use spaces (4). Mixed-indent files break IDE auto-format
on save for some editors and complicate diffs. Cosmetic.
**Fix:**
Run `./mvnw -Prewrite rewrite:run` (the codebase already has an OpenRewrite
profile in pom.xml) to normalize, or hand-fix to 4-space indent matching the
package convention.

---

_Reviewed: 2026-05-28T12:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
