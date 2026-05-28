---
phase: 94-team-roles-match-channel-lifecycle
reviewed: 2026-05-28T00:00:00Z
depth: standard
files_reviewed: 108
files_reviewed_list:
  - src/main/java/org/ctc/admin/controller/MatchController.java
  - src/main/java/org/ctc/admin/controller/TeamController.java
  - src/main/java/org/ctc/admin/dto/MatchForm.java
  - src/main/java/org/ctc/admin/dto/TeamForm.java
  - src/main/java/org/ctc/discord/DiscordBotIdentityCache.java
  - src/main/java/org/ctc/discord/DiscordConfig.java
  - src/main/java/org/ctc/discord/DiscordDevSeedProperties.java
  - src/main/java/org/ctc/discord/DiscordDevSeeder.java
  - src/main/java/org/ctc/discord/DiscordPermissions.java
  - src/main/java/org/ctc/discord/DiscordRestClient.java
  - src/main/java/org/ctc/discord/DiscordRoleCache.java
  - src/main/java/org/ctc/discord/DiscordWebhookClient.java
  - src/main/java/org/ctc/discord/dto/ArchiveCategory.java
  - src/main/java/org/ctc/discord/dto/Channel.java
  - src/main/java/org/ctc/discord/dto/ChannelCreateRequest.java
  - src/main/java/org/ctc/discord/dto/ChannelModifyRequest.java
  - src/main/java/org/ctc/discord/dto/DiscordConfigForm.java
  - src/main/java/org/ctc/discord/dto/DiscordPostFilterForm.java
  - src/main/java/org/ctc/discord/dto/DiscordPostRef.java
  - src/main/java/org/ctc/discord/dto/DiscordSnowflake.java
  - src/main/java/org/ctc/discord/dto/PermissionOverwrite.java
  - src/main/java/org/ctc/discord/dto/Webhook.java
  - src/main/java/org/ctc/discord/dto/WebhookPayload.java
  - src/main/java/org/ctc/discord/event/ChannelCreatedEvent.java
  - src/main/java/org/ctc/discord/event/MatchScheduleFieldsChangedEvent.java
  - src/main/java/org/ctc/discord/exception/DiscordApiExceptionMapper.java
  - src/main/java/org/ctc/discord/model/DiscordGlobalConfig.java
  - src/main/java/org/ctc/discord/model/DiscordPost.java
  - src/main/java/org/ctc/discord/model/DiscordPostType.java
  - src/main/java/org/ctc/discord/repository/DiscordPostRepository.java
  - src/main/java/org/ctc/discord/service/DiscordAutoPostListener.java
  - src/main/java/org/ctc/discord/service/DiscordCategoryResolver.java
  - src/main/java/org/ctc/discord/service/DiscordChannelService.java
  - src/main/java/org/ctc/discord/service/DiscordGlobalConfigService.java
  - src/main/java/org/ctc/discord/service/DiscordPostService.java
  - src/main/java/org/ctc/discord/web/DiscordConfigController.java
  - src/main/java/org/ctc/discord/web/DiscordPostController.java
  - src/main/java/org/ctc/domain/model/Match.java
  - src/main/java/org/ctc/domain/model/Team.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/domain/service/RaceService.java
  - src/main/java/org/ctc/domain/service/TeamManagementService.java
  - src/main/resources/application-dev.yml
  - src/main/resources/db/migration/V10__add_matches_discord_and_scheduling_fields.sql
  - src/main/resources/db/migration/V11__add_matches_discord_channel_archived_at.sql
  - src/main/resources/db/migration/V12__discord_post.sql
  - src/main/resources/db/migration/V9__add_discord_team_role_and_current_match_category.sql
  - src/main/resources/static/admin/css/admin.css
  - src/main/resources/templates/admin/discord-config.html
  - src/main/resources/templates/admin/discord-posts.html
  - src/main/resources/templates/admin/layout.html
  - src/main/resources/templates/admin/match-detail.html
  - src/main/resources/templates/admin/match-form-edit.html
  - src/main/resources/templates/admin/matchday-detail.html
  - src/main/resources/templates/admin/team-form.html
  - src/test/java/org/ctc/admin/controller/MatchControllerCreateChannelErrorCategoryTest.java
  - src/test/java/org/ctc/admin/controller/MatchControllerMoveToArchiveErrorCategoryTest.java
  - src/test/java/org/ctc/admin/controller/MatchControllerPostSettingsPreFlightIT.java
  - src/test/java/org/ctc/admin/controller/MatchControllerTeamCardsRefreshIT.java
  - src/test/java/org/ctc/admin/controller/MatchDetailMatchResultsStaleIT.java
  - src/test/java/org/ctc/admin/dto/MatchFormValidationTest.java
  - src/test/java/org/ctc/admin/dto/TeamFormSnowflakeValidationTest.java
  - src/test/java/org/ctc/discord/DiscordBotIdentityCacheTest.java
  - src/test/java/org/ctc/discord/DiscordDevSeederIT.java
  - src/test/java/org/ctc/discord/DiscordPermissionsTest.java
  - src/test/java/org/ctc/discord/DiscordRestClientIT.java
  - src/test/java/org/ctc/discord/DiscordRoleCacheTest.java
  - src/test/java/org/ctc/discord/DiscordWebhookClientIT.java
  - src/test/java/org/ctc/discord/DiscordWebhookClientMultipartEditIT.java
  - src/test/java/org/ctc/discord/DiscordWebhookClientMultipartIT.java
  - src/test/java/org/ctc/discord/dto/DiscordSnowflakeTest.java
  - src/test/java/org/ctc/discord/model/DiscordPostToStringTest.java
  - src/test/java/org/ctc/discord/repository/DiscordGlobalConfigRepositoryIT.java
  - src/test/java/org/ctc/discord/service/DiscordAutoPostListenerIT.java
  - src/test/java/org/ctc/discord/service/DiscordCategoryResolverTest.java
  - src/test/java/org/ctc/discord/service/DiscordCategoryResolverWireMockIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelArchiveServiceWireMockIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelServiceCleanupFailIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelServicePermissionAuditFailIT.java
  - src/test/java/org/ctc/discord/service/DiscordChannelServiceWireMockIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceLineupsBundleIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchResultsIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceScheduleIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceSettingsBundleIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceTeamCardsIT.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceWebhookUrlPatternTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceWireMockIT.java
  - src/test/java/org/ctc/discord/service/MatchEditFormIT.java
  - src/test/java/org/ctc/discord/web/DiscordConfigControllerIT.java
  - src/test/java/org/ctc/discord/web/DiscordConfigControllerTest.java
  - src/test/java/org/ctc/discord/web/DiscordPostFilterControllerIT.java
  - src/test/java/org/ctc/domain/model/MatchToStringTest.java
  - src/test/java/org/ctc/domain/model/TeamEffectiveDiscordRoleIdTest.java
  - src/test/java/org/ctc/domain/repository/MatchRepositoryDiscordFieldsIT.java
  - src/test/java/org/ctc/domain/repository/TeamRepositoryDiscordRoleIdIT.java
  - src/test/java/org/ctc/domain/service/MatchServiceScheduleEditHookIT.java
  - src/test/java/org/ctc/domain/service/MatchUpdatedAtNoopSaveIT.java
  - src/test/java/org/ctc/domain/service/RaceServiceSaveResultsReEditIT.java
  - src/test/java/org/ctc/domain/service/TeamManagementServiceTest.java
  - src/test/java/org/ctc/e2e/discord/ArchiveModalE2ETest.java
  - src/test/java/org/ctc/e2e/discord/MatchDetailControllerE2ETest.java
  - src/test/java/org/ctc/e2e/discord/TeamFormDiscordRoleDropdownE2ETest.java
  - src/test/java/org/ctc/e2e/discord/posts/DiscordPostsListE2ETest.java
  - src/test/java/org/ctc/e2e/discord/posts/MatchDetailMatchResultsButtonE2ETest.java
  - src/test/java/org/ctc/e2e/discord/posts/MatchDetailScheduleButtonE2ETest.java
  - src/test/java/org/ctc/e2e/discord/posts/MatchDetailSettingsLineupsButtonsE2ETest.java
  - src/test/java/org/ctc/e2e/discord/posts/MatchDetailTeamCardsButtonsE2ETest.java
findings:
  critical: 2
  warning: 6
  info: 5
  total: 13
status: issues_found
---

# Phase 94: Code Review Report

**Reviewed:** 2026-05-28
**Depth:** standard
**Files Reviewed:** 108 (sampled, production-first)
**Status:** issues_found

## Summary

Phase 94 lands the Team Discord-role wiring, Match channel-lifecycle (create / audit / cleanup / archive), the `DiscordPostService` skeleton, `DiscordAutoPostListener`, Flyway V9-V12, and the form-snowflake validation. The core architecture is sound:

- `@TransactionalEventListener(phase = AFTER_COMMIT)` + `Propagation.REQUIRES_NEW` is correctly used for auto-posts — outer transaction commits before the post fires.
- Channel-creation has a real audit step (`assertPermissionAudit`) with cleanup-DELETE on audit failure, and the WireMock IT covers both happy path and the audit-fail + cleanup-fail composed-message path.
- Secrets are excluded from `toString()` (`DiscordPost.webhookToken`, `DiscordGlobalConfig` webhook URLs) and `DiscordPostToStringTest` pins this.
- Path-traversal in `readPng` is guarded by `uploadDir.startsWith` after `normalize()`, and `DiscordAutoPostListenerIT` exercises the `/uploads/../../etc/passwd` traversal attempt.
- `RaceService.saveResults` correctly calls `scoringService.aggregateMatchScores(race)` after every `raceRepository.save(race)` (line 259-260).
- DTOs (`MatchForm`, `TeamForm`) are used for POSTs — no `@ModelAttribute Match` / `@ModelAttribute Team` bindings.

Surfaced defects fall into two buckets:
- One **NPE on bye matches** in `DiscordPostController.matchLabel` that ships uncovered.
- One **orphan-channel leak** in `DiscordChannelService.createMatchChannel` when webhook creation fails between channel create and audit — cleanup runs on audit-fail but not on webhook-fail.
- Plus a cluster of WARNINGs (mis-labelled error category, completeness-vs-existence semantic gap, role-cache race window, validation/regex inconsistency) and INFOs (comment pollution, tautological tests).

## Critical Issues

### CR-01: `DiscordPostController.matchLabel` NPEs on bye matches

**File:** `src/main/java/org/ctc/discord/web/DiscordPostController.java:69-72`
**Issue:** The match-label helper unconditionally calls `m.getAwayTeam().getShortName()`:

```java
private static String matchLabel(Match m) {
    return m.getMatchday().getSeason().getYear() + " | " + m.getMatchday().getLabel()
            + " | " + m.getHomeTeam().getShortName() + " vs. " + m.getAwayTeam().getShortName();
}
```

But `Match.awayTeam` is nullable (`@JoinColumn(name = "away_team_id")` without `nullable=false`; bye matches have `awayTeam=null` — see `MatchController.detail:107` which already handles this with `match.getAwayTeam() != null ? ... : "Bye"`). The listing endpoint calls `matchLabel` for every match returned by `matchRepository.findAll()` (line 57-58) to populate the searchable-dropdown options. As soon as the system contains a single bye match, **`GET /admin/discord/posts` throws NPE → HTTP 500** before any row is rendered.

The existing `DiscordPostFilterControllerIT` only seeds non-bye fixtures, so the bug ships without test coverage.

**Fix:**
```java
private static String matchLabel(Match m) {
    String away = m.getAwayTeam() != null ? m.getAwayTeam().getShortName() : "Bye";
    return m.getMatchday().getSeason().getYear() + " | " + m.getMatchday().getLabel()
            + " | " + m.getHomeTeam().getShortName() + " vs. " + away;
}
```

Add a regression test that seeds a bye match and asserts `GET /admin/discord/posts` returns 200.

### CR-02: Orphan Discord channel on webhook-creation failure

**File:** `src/main/java/org/ctc/discord/service/DiscordChannelService.java:85-114`
**Issue:** The flow is:

```java
Channel channel = restClient.createChannel(guildId, req);           // (1) creates channel in Discord
Webhook webhook = restClient.createWebhook(channel.id(), ...);      // (2) if this throws, channel from (1) leaks
try {
    assertPermissionAudit(channel.id(), teamRoleIds, botUserId);    // (3) audit-fail → cleanup runs
} catch (DiscordAuthException auditEx) {
    try { restClient.deleteChannel(channel.id()); } catch (...) {}
    ...
}
match.setDiscordChannelId(channel.id());                            // (4) DB write
```

If step (2) throws (`DiscordChannelServiceWireMockIT.givenWebhookCreationFails_...` already pins this: `createWebhook` returns 500 → `DiscordTransientException`), the channel created in step (1) remains in Discord, but the match record has `discordChannelId=null` — so the operator has no app-level handle to retry-or-archive that orphan. The `@Transactional` on `createMatchChannel` only protects the database; Discord side-effects don't roll back.

This is exactly the same orphan class the audit-fail cleanup path was built to prevent.

**Fix:** Wrap step (2) in try/catch and delete the channel before re-raising:

```java
Channel channel = restClient.createChannel(guildId, req);
Webhook webhook;
try {
    webhook = restClient.createWebhook(channel.id(), WEBHOOK_NAME);
} catch (DiscordApiException webhookEx) {
    try {
        restClient.deleteChannel(channel.id());
    } catch (DiscordApiException cleanupEx) {
        log.warn("Webhook-create cleanup DELETE failed for channel {}: {}",
                channel.id(), cleanupEx.toString());
    }
    throw webhookEx;
}
```

Extend `DiscordChannelServiceCleanupFailIT` with a `webhook-create-fail + delete-fail` composed-message test analogous to the existing audit-fail variant.

## Warnings

### WR-01: `moveToArchive` mis-labels "no category selected" as `CATEGORY_FULL`

**File:** `src/main/java/org/ctc/admin/controller/MatchController.java:332-335`
**Issue:**

```java
if (categoryId == null || categoryId.isBlank()) {
    throw new DiscordCategoryFullException(
            DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE, null);
}
```

When the user submits the modal with no radio button selected (or with `categoryId=""`), the operator sees "Discord archive category is full (50 channels). Create a new archive category." — which is factually wrong; categories exist and have free slots, the form is just incomplete. The flash classifies it as `errorCategory=category-full`. `MatchControllerMoveToArchiveErrorCategoryTest:40-66` pins this exact (mis)behavior.

The reachable path is: the UI button is disabled when `archiveCategories.isEmpty()`, but a user with categories present can still submit without selecting one — at that point the misleading message lands.

**Fix:** Either flash a dedicated "select a category first" message (`errorCategory=data-incomplete`) or — better — keep the `archived=Boolean.TRUE`-only PATCH off the table when no `parent_id` is set. Adjust the existing test to assert the new message + category.

```java
if (categoryId == null || categoryId.isBlank()) {
    throw new BusinessRuleException(
            "Select an archive category before confirming the move.");
}
```

### WR-02: `matchHasCompleteSettings` accepts unfilled `RaceSettings` rows

**File:** `src/main/java/org/ctc/discord/service/DiscordPostService.java:662-665`
**Issue:**

```java
public boolean matchHasCompleteSettings(Match match) {
    List<Race> races = match.getRaces();
    return !races.isEmpty() && races.stream().allMatch(r -> r.getSettings() != null);
}
```

This treats "has a `RaceSettings` row" as "has complete settings", but `Race.hasAllSettings()` (line 121) is stricter: `settings != null && settings.isComplete()`. The post-time pre-flight uses the loose check; the Race detail page (which can save a partially-filled `RaceSettings`) uses the strict check. Result: an operator who saved a half-populated `RaceSettings` form sees `Post Settings` enabled and posts a graphic that may have nulls in fields the renderer assumes are populated.

Same issue applies to the gating logic in `MatchController.detail:117` and the public `canPostMatchPreview` check (line 248-250 → `matchHasCompleteSettings`).

**Fix:** Align both gates on `Race.hasAllSettings()`:

```java
public boolean matchHasCompleteSettings(Match match) {
    List<Race> races = match.getRaces();
    return !races.isEmpty() && races.stream().allMatch(Race::hasAllSettings);
}
```

Update `DiscordPostServicePreFlightTest:72-87` to seed a `RaceSettings` with partial values and assert false.

### WR-03: `DiscordRoleCache.refresh` opens a "no-roles" window

**File:** `src/main/java/org/ctc/discord/DiscordRoleCache.java:44-53`
**Issue:**

```java
public int refresh(List<Role> roles) {
    Map<String, CachedEntry<Role>> next = new HashMap<>(roles.size());
    for (Role role : roles) {
        next.put(role.id(), new CachedEntry<>(role, clock.instant().plus(TTL)));
    }
    store.clear();        // window opens
    store.putAll(next);   // window closes
    ...
}
```

Between `store.clear()` and `store.putAll(next)`, a concurrent `get(roleId)` returns null and `snapshot()` returns an empty map. In the dev/admin flow this is hit when `DiscordConfigController.refreshRolesCache` runs while a `TeamController.create` request is rendering the dropdown — the operator sees an empty dropdown and falls back to the manual snowflake input.

`ConcurrentHashMap` operations themselves are atomic per-key but the clear+putAll sequence is not. Probability is low but non-zero.

**Fix:** Compute the diff in-place, or swap to a single-step `replaceAll`:

```java
public int refresh(List<Role> roles) {
    Map<String, CachedEntry<Role>> next = new HashMap<>(roles.size());
    Instant expiry = clock.instant().plus(TTL);
    for (Role role : roles) {
        next.put(role.id(), new CachedEntry<>(role, expiry));
    }
    store.keySet().retainAll(next.keySet());
    store.putAll(next);
    return next.size();
}
```

### WR-04: `assertPermissionAudit` throws NumberFormatException on `null` allow

**File:** `src/main/java/org/ctc/discord/service/DiscordChannelService.java:188,196`
**Issue:**

```java
.filter(o -> (Long.parseLong(o.allow()) & VIEW_CHANNEL) != 0L)
```

`PermissionOverwrite.allow` is `String` and Discord *normally* returns `"0"` for empty masks, but the record carries `@JsonIgnoreProperties(ignoreUnknown = true)` and the `allow` field has no `@JsonInclude` / non-null fallback. If Discord ever returns the field as JSON null (it has done so for certain edge cases over the years), `Long.parseLong(null)` throws `NumberFormatException` from inside the audit and that propagates as a generic 500 to the operator — bypassing the careful `DiscordAuthException` / cleanup path.

**Fix:**

```java
private static long parseAllow(String allow) {
    return (allow == null || allow.isBlank()) ? 0L : Long.parseLong(allow);
}
```

and route both audit streams through it. Add a stub-variant of `DiscordChannelServicePermissionAuditFailIT` that returns `"allow":null` to exercise this.

### WR-05: Empty-string `discordRoleId` slips through validation

**File:** `src/main/java/org/ctc/discord/dto/DiscordSnowflake.java:5`, `src/main/java/org/ctc/domain/model/Team.java:78-83`
**Issue:** `DiscordSnowflake.PATTERN = "^$|^\\d{17,20}$"` (empty OR snowflake). Combined with `Team.getEffectiveDiscordRoleId()`:

```java
public String getEffectiveDiscordRoleId() {
    if (discordRoleId != null) {          // <-- empty string is non-null
        return discordRoleId;
    }
    return parentTeam != null ? parentTeam.getDiscordRoleId() : null;
}
```

If a sub-team's `discordRoleId` is `""` (e.g., the parent team value was set then cleared via the form, and an external write path bypassed `TeamManagementService.save`'s `blankToNull`), the getter returns `""` instead of falling back to the parent. The downstream `DiscordChannelService.assertPreconditions` only checks `== null`, not `isBlank()`, so the empty string travels into the API as a role id and Discord returns 400 → mapped to TRANSIENT, masking the real cause.

`TeamManagementService.save` is the only writer that does normalize, but backup import (`TeamRestorer`) and direct JPA writes are not gated.

**Fix:** Treat blank as absent inside the getter — keeps the convention centralized:

```java
public String getEffectiveDiscordRoleId() {
    if (discordRoleId != null && !discordRoleId.isBlank()) {
        return discordRoleId;
    }
    return parentTeam != null && parentTeam.getDiscordRoleId() != null
            && !parentTeam.getDiscordRoleId().isBlank()
            ? parentTeam.getDiscordRoleId() : null;
}
```

### WR-06: Webhook-URL form regex stricter than `parseWebhookUrl` regex

**File:** `src/main/java/org/ctc/discord/dto/DiscordConfigForm.java:15`, `src/main/java/org/ctc/discord/service/DiscordPostService.java:71-72`
**Issue:** The form-side regex is

```
^$|^https://discord\.com/api/webhooks/\d+/[A-Za-z0-9_-]+$
```

but the parser accepts `discordapp.com`, `/api/vN/`, and trailing query strings (and the test `DiscordPostServiceWebhookUrlPatternTest.givenDiscordappLegacyHost_...` pins the legacy host as valid). The result is that an operator copy-pasting a legacy Discord webhook URL (still served by Discord) gets a bind-error on the form even though the URL would parse and post fine. The form-side regex also rejects HTTP for local WireMock fixtures, but that's intentional — the legacy-host case is the operator-visible defect.

**Fix:** Either widen the form regex to match the parser, or document that operators must paste the modern `discord.com` URL only. Aligning the regexes is the lower-surprise option:

```java
private static final String WEBHOOK_REGEX =
        "^$|^https://(?:discord|discordapp)\\.com/api(?:/v\\d+)?/webhooks/\\d+/[A-Za-z0-9_-]+$";
```

## Info

### IN-01: Comment pollution in V10 migration and application-dev.yml

**File:** `src/main/resources/db/migration/V10__add_matches_discord_and_scheduling_fields.sql:1,4-6`, `src/main/resources/application-dev.yml:31`
**Issue:** Violates the "No Comment Pollution" rule (CLAUDE.md "Conventions / No Comment Pollution"):

```sql
-- Phase 94 V10 D-13: extend matches table with Discord channel-handle + 5 scheduling/team-facing fields.
-- All columns nullable — operator-populated post-match-creation.
-- Compatible with H2 2.x and MariaDB 10.7+ — no CHECK constraints, no LONGTEXT
-- (both drift between engines). MatchForm Jakarta-Validation owns the @Size
-- bounds at the controller layer; the DB only enforces VARCHAR length ceilings.
-- DO NOT mutate this file after release (CLAUDE.md "Do Not Modify Flyway Migrations").
```

`V9` has the same "DO NOT mutate" header pollution (line 4) and the H2/MariaDB-compat note (lines 1-3). `application-dev.yml:31-33` carries `# … (Plan 94-04)`.

Phase / Plan / D-NN references rot and the "Do Not Modify" rule is in CLAUDE.md already.

**Fix:** Strip the headers from V9, V10, and the `application-dev.yml` block. The single-line `Compatible with H2 + MariaDB` note can stay if reduced to the non-obvious WHY, but the Phase/Plan/D-NN identifiers must go. Re-running `grep -rnE "Phase 9[0-9]\|Plan [0-9]+-\|D-[0-9]\|UAT-" src/main/resources/db/migration/ src/main/resources/application-*.yml` should return zero hits afterward.

Same rule also surfaces in V12 / V11 (clean) and several existing-codebase files (V7, BackupArchiveService, etc.) — those are out of Phase 94 scope but should be addressed in a follow-up cleanup phase.

### IN-02: Tautological constant tests

**File:** `src/test/java/org/ctc/discord/dto/DiscordSnowflakeTest.java:10-17`
**Issue:** The two test methods assert that `DiscordSnowflake.PATTERN` equals its own source literal and `DiscordSnowflake.MESSAGE` equals its own source string:

```java
assertThat(DiscordSnowflake.PATTERN).isEqualTo("^$|^\\d{17,20}$");
assertThat(DiscordSnowflake.MESSAGE).isEqualTo("Must be a Discord snowflake (17-20 digits) or empty");
```

This is a self-referential pin — it catches accidental constant renames but provides no semantic coverage of the regex behavior. Same pattern exists for `MatchControllerCreateChannelErrorCategoryTest` whose only assertion is `category.name().toLowerCase().replace('_', '-') == expected` — already covered by `MatchControllerMoveToArchiveErrorCategoryTest` with the same `@CsvSource`.

**Fix:** Either delete the tautological tests or replace them with cases that exercise the regex (e.g., `"123"` → no match, `"12345678901234567"` (17 digits) → match, `"123456789012345678901"` (21 digits) → no match). Drop the duplicate enum-conversion test in `MatchControllerCreateChannelErrorCategoryTest` — the move-to-archive variant already covers it.

### IN-03: Inline styles in `match-detail.html` archive modal

**File:** `src/main/resources/templates/admin/match-detail.html:50,257`
**Issue:** The archive modal toggles visibility via inline `style.display`:

```html
onclick="document.getElementById('archiveModal').style.display='flex'"
...
onclick="document.getElementById('archiveModal').style.display='none'"
```

This is JS-driven inline style rather than a CSS class toggle (e.g., `.modal-overlay.is-open { display: flex }`). The rule "No Inline Styles on Buttons" targets the `style="..."` attribute on `.btn` elements, which is not technically violated here — but the same intent applies. The pattern also exists elsewhere in the codebase (`matchday-detail.html` uses `th:styleappend` for `opacity` and `border-bottom`), so this is consistent with the existing baseline.

**Fix:** Introduce `.modal-overlay--open` in `admin.css` and toggle the class via JS. Optional follow-up; not phase-critical.

### IN-04: Hard-coded "Discord Config" / "Discord Posts" string match in `layout.html` nav

**File:** `src/main/resources/templates/admin/layout.html:80-81`
**Issue:**

```html
<a ... th:classappend="${title.contains('Discord Config') ? 'active' : ''}">Discord Config</a>
<a ... th:classappend="${title.contains('Discord Posts') ? 'active' : ''}">Discord Posts</a>
```

The Discord controllers already set `activeRoute=discord-config` and `activeRoute=discord-posts` model attributes — using those is the convention used by `MatchController.detail` and the other admin pages, and is robust to title rewording.

**Fix:**

```html
<a ... th:classappend="${activeRoute == 'discord-config' ? 'active' : ''}">Discord Config</a>
<a ... th:classappend="${activeRoute == 'discord-posts' ? 'active' : ''}">Discord Posts</a>
```

### IN-05: `DiscordPostController.matchLabel` builds an N×All-Matches dropdown per request

**File:** `src/main/java/org/ctc/discord/web/DiscordPostController.java:51-58`
**Issue:** Every `GET /admin/discord/posts` runs `matchRepository.findAll()`, sorts all matches in memory, and computes labels for every match — to populate the "filter by match" dropdown. With 26 entities now wired into backup scope and the v1.13 growing match catalog, this means traversing every Match, its Matchday, its Season for every page load.

Performance issues are out of v1 scope per the review brief, but flagging for the maintainer: at minimum this should be a single JPQL projection (id + label fields), not full entity hydration. Combined with the CR-01 NPE fix, both can be addressed together.

**Fix:** Add a `findAllForMatchLabel()` JPQL that returns `(id, year, label, homeShort, awayShort)` directly. Defer until v1.14 if backlog allows.

---

_Reviewed: 2026-05-28_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
