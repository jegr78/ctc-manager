---
phase: 110-lobby-settings-graphic
reviewed: 2026-05-31T12:00:00Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - src/main/java/org/ctc/admin/service/LobbySettingsGraphicService.java
  - src/main/java/org/ctc/admin/service/RaceGraphicService.java
  - src/main/java/org/ctc/admin/service/TemplatePreviewService.java
  - src/main/java/org/ctc/admin/controller/RaceController.java
  - src/main/java/org/ctc/admin/controller/MatchController.java
  - src/main/java/org/ctc/admin/controller/TemplateEditorController.java
  - src/main/java/org/ctc/domain/service/RaceService.java
  - src/main/java/org/ctc/domain/service/MatchService.java
  - src/main/java/org/ctc/discord/model/DiscordPostType.java
  - src/main/java/org/ctc/discord/service/DiscordPostService.java
  - src/main/resources/templates/admin/lobby-settings-render.html
  - src/main/resources/templates/admin/race-detail.html
  - src/main/resources/templates/admin/match-detail.html
  - src/main/resources/templates/admin/template-editors.html
  - pom.xml
  - src/test/java/org/ctc/admin/service/LobbySettingsWeatherParsingTest.java
  - src/test/java/org/ctc/admin/service/TemplatePreviewServiceTest.java
  - src/test/java/org/ctc/admin/controller/TemplateEditorControllerTest.java
  - src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServicePreFlightTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceRefBranchesTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceByeMatchdayGuardTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdaySchedulePreFlightTest.java
  - src/test/java/org/ctc/discord/service/DiscordPostServiceMatchdayPairingsPreFlightTest.java
findings:
  critical: 1
  warning: 2
  info: 1
  total: 4
status: resolved
resolved: 2026-05-31
---

## Resolution (2026-05-31)

All four findings fixed in-phase before milestone close:

- **CR-01 + WR-01** (`RaceService.getRaceDetailData`): `canGenerateLobbySettings` is now
  `lobbySettingsReady && lobbyTeamsPresent && !exists`, where
  `lobbySettingsReady = hasAllSettings() && track != null` (drops the unused `car` requirement,
  WR-01) and `lobbyTeamsPresent = home != null && away != null` (prevents the bye/team-less NPE
  in `generateAndSaveGraphic`, CR-01 — consistent with sibling per-race graphics).
  `lobbySettingsMissing = !lobbySettingsReady` so the "Settings incomplete." hint reflects only
  the settings/track prerequisite. Covered by two new `RaceServiceTest` cases.
- **WR-02** (`DiscordPostService.postLobbySettings`): added a pre-check throwing
  `BusinessRuleException("Configure a track for all races first")` when any race lacks a track,
  so a track-less match yields a clean flash instead of an HTTP 500. Covered by a new
  `DiscordPostServicePreFlightTest` case. (The pre-existing `postSettings` shares the same latent
  pattern but is out of phase-110 scope and left untouched.)
- **IN-01** (`template-editors.html`): `matchdayName` added to the editor variable table
  (available for custom templates).

Re-verified: `./mvnw clean verify -Pe2e` green (see 110-05-SUMMARY.md re-run note).


# Phase 110: Code Review Report

**Reviewed:** 2026-05-31T12:00:00Z
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

Phase 110 adds a `LobbySettingsGraphicService` that renders a 1920x1080 lobby-settings PNG, wires it into `RaceGraphicService`, surfaces a Generate button on the race-detail page, adds a Post button on the match-detail page, and plugs the new `lobby-settings` template type into the full template-editor pipeline. The implementation is structurally sound: weather parsing logic is correct, the record/constructor arity matches across all call sites, the `@SuppressFBWarnings` justification is updated, the JaCoCo exclusion is present, and the Thymeleaf template uses `th:text` throughout (no injection risk in the static render path).

One BLOCKER exists: `generateAndSaveGraphic` unconditionally dereferences both team short names to build the attachment name, but the `canGenerateLobbySettings` gate does not require teams to be present. A bye race with complete settings will satisfy the gate, the Generate button will appear, and clicking it will throw a `NullPointerException` on the away-team dereference. Two warnings follow: the gate reuses `hasAllSettings` (which includes `car != null`) even though `LobbySettingsGraphicService` never uses the car, creating a false-negative that blocks generation when only the car is absent; and `DiscordPostService.postLobbySettings` propagates `IllegalStateException` uncaught through `MatchController.postLobbySettings`, which only catches `BusinessRuleException` and `DiscordApiException`, resulting in an unhandled HTTP 500 when a race in the match has settings but no track.

---

## Critical Issues

### CR-01: NPE in `generateAndSaveGraphic` for bye/teamless races

**File:** `src/main/java/org/ctc/admin/service/RaceGraphicService.java:57-58`

**Issue:** `generateAndSaveGraphic` builds the attachment name by unconditionally calling `race.getHomeTeam().getShortName()` and `race.getAwayTeam().getShortName()`. `race.getAwayTeam()` returns `null` for bye matches (where `match.getAwayTeam() == null`). The `canGenerateLobbySettings` gate in `RaceService.getRaceDetailData` is `hasAllSettings && !lobbySettingsGraphicExists` — it has no team-presence requirement. A bye race with complete race settings, a car, and a track will show the "Generate Lobby Settings Graphic" button; clicking it routes through `RaceController.generateLobbySettings` → `RaceGraphicService.generateLobbySettings` → `generateAndSaveGraphic` → NPE at line 58. The other graphic generators (lineup, results, settings, overlay) avoid this because their gates include `hasHomeCard && hasAwayCard` or `hasMatch && homeTeam != null && awayTeam != null`, which are false for bye races.

**Fix:** Null-guard the attachment name in `generateAndSaveGraphic`, or add a team-presence check to the `canGenerateLobbySettings` gate. The safest fix is to null-guard directly in the shared helper so no future generator can repeat the mistake:

```java
// RaceGraphicService.java — generateAndSaveGraphic
private void generateAndSaveGraphic(UUID raceId, String suffix, GraphicGenerator generator) {
    var race = raceRepository.findById(raceId).orElseThrow();
    try {
        String url = generator.generate(race);
        Team home = race.getHomeTeam();
        Team away = race.getAwayTeam();
        String teamPart = (home != null && away != null)
                ? home.getShortName() + "-" + away.getShortName()
                : (home != null ? home.getShortName() : "bye");
        String attachmentName = race.getMatchday().getLabel() + "-" + teamPart + "-" + suffix;
        var attachment = new RaceAttachment(race, AttachmentType.FILE, attachmentName, url);
        raceAttachmentRepository.save(attachment);
    } catch (IOException e) {
        log.error("{} generation failed for race {}", suffix, raceId, e);
        throw new RuntimeException("Generation failed: " + e.getMessage(), e);
    }
}
```

Alternatively, add `&& race.getHomeTeam() != null && race.getAwayTeam() != null` to the `canGenerateLobbySettings` condition in `RaceService.getRaceDetailData` (line 149) so the Generate button never appears for bye races.

---

## Warnings

### WR-01: `canGenerateLobbySettings` gate over-restricts when car is absent (false negative)

**File:** `src/main/java/org/ctc/domain/service/RaceService.java:125, 149`

**Issue:** The `hasAllSettings` local variable (line 125) is `race.hasAllSettings() && race.getCar() != null && race.getTrack() != null`. It is reused for both `canGenerateSettings` (line 145) and `canGenerateLobbySettings` (line 149). `SettingsGraphicService` uses `race.getCar().getDisplayName()` so the car requirement is correct there. `LobbySettingsGraphicService` never reads `race.getCar()` — it only uses track, settings fields, season, and team names. Consequently, if a race has complete race settings and a track but no car assigned, `canGenerateLobbySettings` is `false` and the UI shows "Settings incomplete." — a misleading message that hides the fact that the lobby graphic is actually generatable. The gate is over-restrictive relative to what the service actually needs.

**Fix:** Introduce a separate `hasLobbySettings` boolean that excludes the car requirement:

```java
// RaceService.getRaceDetailData
boolean hasAllSettings = race.hasAllSettings() && race.getCar() != null && race.getTrack() != null;
boolean hasLobbySettings = race.hasAllSettings() && race.getTrack() != null;
// ...
return new RaceDetailData(/* ... */
        hasLobbySettings && !lobbySettingsGraphicExists,
        !hasLobbySettings, lobbySettingsGraphicExists);
```

Also update `lobbySettingsMissing` to use `!hasLobbySettings` for an accurate "Settings incomplete" hint.

### WR-02: `postLobbySettings` in `DiscordPostService` can throw uncaught `IllegalStateException`

**File:** `src/main/java/org/ctc/discord/service/DiscordPostService.java:743-749`  
**Related:** `src/main/java/org/ctc/admin/controller/MatchController.java:183-193`

**Issue:** `postLobbySettings` guards with `matchHasCompleteSettings`, which calls `Race.hasAllSettings()` → `settings.isComplete()`. `isComplete()` does not check whether the race has a track. `LobbySettingsGraphicService.generateLobbySettings` throws `IllegalStateException("Race has no track")` if `race.getTrack() == null` (line 32). This exception is not `BusinessRuleException` or `DiscordApiException`, so `MatchController.postLobbySettings` does not catch it and it propagates to the global exception handler as HTTP 500 — no user-facing flash error message, no `errorCategory` badge. The same gap exists in the pre-existing `postSettings` method (symmetry does not make it correct), but the new `postLobbySettings` reproduces it.

**Fix:** Either extend `matchHasCompleteSettings` to also require a non-null track for each race, or wrap the `IllegalStateException` from the generator into a `BusinessRuleException` inside `postRaceBundle`. A minimal targeted fix:

```java
// DiscordPostService — postLobbySettings
@Transactional
public DiscordPost postLobbySettings(Match match) throws DiscordApiException {
    if (!matchHasCompleteSettings(match)) {
        throw new BusinessRuleException("Configure settings for all races first");
    }
    boolean allHaveTracks = match.getRaces().stream().allMatch(r -> r.getTrack() != null);
    if (!allHaveTracks) {
        throw new BusinessRuleException("All races must have a track assigned before posting lobby settings");
    }
    return postRaceBundle(match, DiscordPostType.LOBBY_SETTINGS, "lobby-settings-race-",
            race -> readRaceGraphic(lobbySettingsGraphicService.generateLobbySettings(race)));
}
```

---

## Info

### IN-01: Context variable `matchdayName` set but not used in default template

**File:** `src/main/java/org/ctc/admin/service/LobbySettingsGraphicService.java:68`

**Issue:** `ctx.setVariable("matchdayName", race.getMatchday().getLabel())` is set in `generateLobbySettings` but the variable `matchdayName` appears nowhere in `lobby-settings-render.html`. The `seasonName` footer (line 67 of the service, line 206 of the template) already includes matchday-like context via `season.getName() + " · " + season.getYear()`. The unused variable creates a false impression for custom-template authors that `matchdayName` is separately accessible (it is, but the default template doesn't demonstrate it). This is minor and custom templates can legitimately use it, but the default template should ideally model all exposed variables.

**Fix:** Either remove line 68 from the service (if the variable is not intended to be part of the public template API), or add a placeholder use of `${matchdayName}` in the default template's header/footer to document it. If it is intentionally provided for custom templates, document it in the template-editors variable table at `template-editors.html:768-782`.

---

_Reviewed: 2026-05-31T12:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
