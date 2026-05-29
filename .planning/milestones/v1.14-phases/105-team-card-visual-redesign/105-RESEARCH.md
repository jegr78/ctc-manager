# Phase 105: Carbon HUD Graphics Redesign — Research

**Researched:** 2026-05-29
**Domain:** Playwright-rendered Thymeleaf admin graphics — template replacement + two backend patches
**Confidence:** HIGH (all claims verified against live codebase)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Scope expanded from team-card-only to all admin graphics.
- **D-02:** Stay as single re-scoped Phase 105. Plan groups: Team Card / Composites / Matchday-List / Overlay+Analogy.
- **D-03:** REQUIREMENTS broadened — CARD-01/CARD-02 extended; CARD-03 and CARD-04 added.
- **D-04:** Apply TeamCardService color-robustness patch. After `gradientColor`, set `accentVisColor = computeAccentVisColor(accentColor, primaryColor)` and `onPrimaryColor = contrastColor(primaryColor)`. New helpers reuse existing `relativeLuminance` (0–255 scale). Template reads both via Thymeleaf-Elvis with graceful fallback. TeamCardService is JaCoCo-excluded — no coverage impact.
- **D-05:** Change `ProvisionalScoresGraphicService` so `raceLabel` is set ONLY for matches with > 1 race (else `null`). The existing ProvisionalScores unit test pinning `"Race N"` MUST be updated to assert the new conditional behavior (both branches).
- **D-06:** Extend Carbon system to all 4 non-handoff templates by analogy: `matchday-pairings-render.html` (mirror of `matchday-overview`) and `playoff-round-schedule/results/overview-render.html`. Each requires explicit `playwright-cli` visual verification.
- **D-07:** Verification by group (Team Card / Composites / Matchday-List / Overlay+Analogy). Visual approval via `playwright-cli` Desktop + Mobile, screenshots into `.screenshots/105-*/`. Dev server via `./scripts/app.sh start dev`.

### Claude's Discretion

- Exact mapping of handoff `matchday-overview-render.html` onto repo templates — RESOLVED below (Q1).
- Per-plan task granularity and wave grouping within the 4 groups.

### Deferred Ideas (OUT OF SCOPE)

None — all ideas were folded into scope during discussion.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CARD-01 | Team card PNG regenerated to Carbon HUD handoff. Includes TeamCardService color-robustness patch. | Q3 resolves exact patch location and helper signatures. Template is 1:1 drop-in from handoff. |
| CARD-02 | Existing card-consumer integration paths remain backward-compatible. No GraphicService calling-signature or model-variable changes (except two named backend tweaks). | Q6 maps all test classes that must stay green. Q2 confirms no new mandatory variables. |
| CARD-03 | Five composite graphics restyled as drop-in replacements. Includes ProvisionalScoresGraphicService raceLabel change and IT update. | Q4 pins exact current code location (line 98) and the two tests to update. Q2 confirms bindings unchanged. |
| CARD-04 | Matchday/list graphics + overlay restyled; 4 analogy templates rebuilt. Overlay geometry exact. | Q1 resolves template mapping. Q2 maps bindings for all 4 analogy templates. Q7 confirms geometry invariants. |
</phase_requirements>

---

## Summary

Phase 105 replaces all 16 Playwright-rendered admin graphic templates with Carbon/Gold HUD designs. Twelve of the sixteen are production-ready drop-in replacements delivered in the handoff bundle. The remaining four must be rebuilt by analogy to the Carbon system using only existing model bindings.

Two backend changes are required: a color-robustness patch to `TeamCardService` (two new helper methods + two new context variables) and a conditional `raceLabel` assignment in `ProvisionalScoresGraphicService` (line 98). Both changes are small, isolated, and well-bounded. All graphic services are JaCoCo-excluded, so the 82% coverage gate is unaffected by template or service changes. The primary test-impact is the `ProvisionalScoresGraphicServiceTest` unit test class (two methods assert `"Race N"` unconditionally and must be updated to cover both branches).

**Primary recommendation:** Execute the four plan groups as four sequential waves. Wave 1 (Team Card) is the highest-risk because it involves a backend patch; waves 2–4 are pure template replacements. Each wave ends with `playwright-cli` visual verification before merging.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Card/graphic generation (PNG) | Backend (GraphicService) | Thymeleaf template | Service builds context, template renders HTML, Playwright screenshots to PNG |
| Template storage / override | Filesystem (uploadDir) | Classpath (default) | Custom templates override classpath defaults via `uploadDir.resolve(filename)` |
| Color computation | Backend (TeamCardService) | CSS (Chromium) | Java for luminance-based branching; CSS `oklch(from …)` for the rest |
| Discord auto-post upload | Backend (DiscordPostService) | Spring `@TransactionalEventListener` | Phase 95 AFTER_COMMIT hook; template changes do not affect this path |
| Template editor preview | Frontend (admin UI, SSR) | — | `/admin/tools/template-editors` renders templates in-browser via MockMvc-style preview |

---

## Standard Stack

This phase involves no new library dependencies. The existing stack handles everything:

| Component | Class / Path | Role |
|-----------|-------------|------|
| Playwright/Chromium (compile-scoped) | `AbstractGraphicService`, `TeamCardService` | Screenshot rendering |
| Thymeleaf | Spring Boot auto-configured | Template processing |
| `encodeClasspathResource(path, mime)` | `AbstractGraphicService` line 88 | Encodes classpath assets to base64 |
| `ConthraxSb.woff2` | `static/admin/fonts/ConthraxSb.woff2` | Font — already present |
| `ctc-logo-white.png` | `static/admin/img/ctc-logo-white.png` | CTC logo — already present |
| `vs-badge.svg` | `static/admin/img/vs-badge.svg` | VS badge — already present |
| `commentator.png` | `static/admin/img/commentator.png` | Commentator image — already present |

**No new packages to install.** Package Legitimacy Audit section omitted.

---

## Q1: Template-Mapping Resolution

### Definitive Mapping: 16 Repo Templates → Handoff / Analogy

| Repo template | Rendering service | `getDefaultTemplatePath()` | Custom file key | Handoff source | Category |
|---|---|---|---|---|---|
| `team-card-render.html` | `TeamCardService` | `admin/team-card-render` (hard-coded in `renderTemplate`) | `team-card-template.html` | `handoff-templates/team-card-render.html` | Drop-in |
| `settings-render.html` | `SettingsGraphicService` | `admin/settings-render` (hard-coded in `renderTemplate`) | `settings-template.html` | `handoff-templates/settings-render.html` | Drop-in |
| `lineup-render.html` | `LineupGraphicService` | `admin/lineup-render` (hard-coded) | `lineup-template.html` | `handoff-templates/lineup-render.html` | Drop-in |
| `results-render.html` | `ResultsGraphicService` | `admin/results-render` (hard-coded) | `results-template.html` | `handoff-templates/results-render.html` | Drop-in |
| `match-results-render.html` | `MatchResultsGraphicService` | `admin/match-results-render` (hard-coded) | `match-results-template.html` | `handoff-templates/match-results-render.html` | Drop-in |
| `provisional-scores-render.html` | `ProvisionalScoresGraphicService` | `admin/provisional-scores-render` (hard-coded) | `provisional-scores-template.html` | `handoff-templates/provisional-scores-render.html` | Drop-in + backend patch |
| `matchday-schedule-render.html` | `MatchdayScheduleGraphicService` | `"admin/matchday-schedule-render"` (constant) | `matchday-schedule-template.html` | `handoff-templates/matchday-schedule-render.html` | Drop-in |
| `matchday-overview-render.html` | `MatchdayOverviewGraphicService` | `"admin/matchday-overview-render"` (constant) | `matchday-overview-template.html` | `handoff-templates/matchday-overview-render.html` | Drop-in (see note below) |
| `standings-render.html` | `StandingsGraphicService` | `"admin/standings-render"` (TEMPLATE_PATH constant) | n/a — no custom template support | `handoff-templates/standings-render.html` | Drop-in |
| `matchday-results-render.html` | `MatchdayResultsGraphicService` | `"admin/matchday-results-render"` (constant) | `matchday-results-template.html` | `handoff-templates/matchday-results-render.html` | Drop-in |
| `power-rankings-render.html` | `PowerRankingsGraphicService` | `"admin/power-rankings-render"` (DEFAULT_TEMPLATE_PATH constant) | `power-rankings-template.html` | `handoff-templates/power-rankings-render.html` | Drop-in |
| `overlay-render.html` | `OverlayGraphicService` | `admin/overlay-render` (hard-coded) | `overlay-template.html` | `handoff-templates/overlay-render.html` | Drop-in (geometry exact) |
| `matchday-pairings-render.html` | `MatchdayPairingsGraphicService` | `"admin/matchday-pairings-render"` (constant) | `matchday-pairings-template.html` | **No handoff — analogy to `matchday-overview`** | Analogy rebuild |
| `playoff-round-schedule-render.html` | `PlayoffRoundScheduleGraphicService` | `"admin/playoff-round-schedule-render"` (constant) | `playoff-round-schedule-template.html` | **No handoff — analogy to `matchday-schedule`** | Analogy rebuild |
| `playoff-round-results-render.html` | `PlayoffRoundResultsGraphicService` | `"admin/playoff-round-results-render"` (constant) | `playoff-round-results-template.html` | **No handoff — analogy to `matchday-results`** | Analogy rebuild |
| `playoff-round-overview-render.html` | `PlayoffRoundOverviewGraphicService` | `"admin/playoff-round-overview-render"` (constant) | `playoff-round-overview-template.html` | **No handoff — analogy to `matchday-overview`** | Analogy rebuild |

**Handoff `matchday-overview-render.html` ("Pairings/Seeds") mapping — RESOLVED:**

The handoff file named `matchday-overview-render.html` maps **exclusively** to the repo's `matchday-overview-render.html` (rendered by `MatchdayOverviewGraphicService`). The repo's `matchday-pairings-render.html` is a distinct sibling rendered by `MatchdayPairingsGraphicService` using `getDefaultTemplatePath()` = `"admin/matchday-pairings-render"`. Inspection of the two current repo templates confirms they are near-identical in structure but differ in the title suffix ("Overview" vs. "Pairings") and the seed display style (seed number vs. `#N` with `rating` class). The handoff template targets the `matchday-overview` variant. The `matchday-pairings` template must be rebuilt by analogy (D-06).

**`StandingsGraphicService` note:** This service does NOT implement `TemplateManageable` and has no custom-template-override mechanism. It processes the template directly via `templateEngine.process(TEMPLATE_PATH, ctx)` at line 104. The drop-in replacement is therefore a pure file copy to `src/main/resources/templates/admin/standings-render.html` — no upload-dir override is involved.

---

## Q2: th:* Binding Inventory per Template

### Group A — Team Card (1 template)

**`team-card-render.html`** — rendered by `TeamCardService`. Context variables (lines 81–95 of `TeamCardService.java`):

| Variable | Type | Source |
|---|---|---|
| `teamName` | String | `team.getName()` or parent name for sub-team |
| `subTeamLabel` | String/null | derived from short names |
| `rating` | Integer | `seasonTeam.getRating()` |
| `points` | int | from standings |
| `record` | String | `"W - L - D"` |
| `primaryColor` | String | hex |
| `secondaryColor` | String | hex |
| `accentColor` | String | hex |
| `gradientColor` | String | `computeGradientColor(...)` |
| `logoBase64` | String/null | encoded from uploads |
| `fontBase64` | String | classpath `ConthraxSb.woff2` |

After D-04 patch, TWO new variables are added:
| `accentVisColor` | String | `computeAccentVisColor(accentColor, primaryColor)` |
| `onPrimaryColor` | String | `contrastColor(primaryColor)` |

The handoff template reads these via Thymeleaf-Elvis: `${accentVisColor ?: accentColor}` and `${onPrimaryColor ?: '#ffffff'}`. No new mandatory variables — backward compatible.

**CTC logo note:** The team-card template embeds a fallback base64-encoded CTC mark directly in the template (visible in handoff line 146 — the long `iVBORw0KGgo…` data URI). It uses `th:src="${ctcLogoBase64 != null ? ctcLogoBase64 : 'data:image/png;base64,...'}"`  — the service does NOT set `ctcLogoBase64`, but the template gracefully falls back to the embedded mark. This is self-contained and requires no service change.

### Group B — Composites (5 templates)

All composite services set these common variables via `encodeClasspathResource`:
- `ctcLogoBase64` — from `CTC_LOGO_CLASSPATH`
- `fontBase64` — from `FONT_CLASSPATH`

**`settings-render.html`** (SettingsGraphicService lines 91–116):
`seasonYear`, `matchdayName`, `seasonName`, `homeCardBase64`, `awayCardBase64`, `homePosition`, `awayPosition`, `ctcLogoBase64`, `fontBase64`, `carName`, `trackName`, `numberOfLaps`, `tyreWearMultiplier`, `fuelConsumptionMultiplier`, `refuelingSpeed`, `initialFuel`, `numberOfRequiredPitStops`, `timeProgressionMultiplier`, `weather`, `timeOfDay`, `availableTyres`, `mandatoryTyres`

**`lineup-render.html`** (LineupGraphicService lines 97–107):
`seasonYear`, `matchdayName`, `seasonName`, `homeCardBase64`, `awayCardBase64`, `homePosition`, `awayPosition`, `pairings` (List of `DriverPairing{homeDriver, homeNickname, awayDriver, awayNickname}`), `ctcLogoBase64`, `fontBase64`

**`results-render.html`** (ResultsGraphicService lines 92–106):
`seasonYear`, `matchdayName`, `seasonName`, `homeCardBase64`, `awayCardBase64`, `homeTotal`, `awayTotal`, `homeIsWinner`, `awayIsWinner`, `resultRows` (List of `DriverResultRow{homeDriver, homeNickname, homePoints, awayPoints, awayDriver, awayNickname}`), `ctcLogoBase64`, `fontBase64`

**`match-results-render.html`** (MatchResultsGraphicService lines 64–77):
`seasonYear`, `matchdayName`, `seasonName`, `homeCardBase64`, `awayCardBase64`, `homeTotal`, `awayTotal`, `homeIsWinner`, `awayIsWinner`, `raceRows` (List of `RaceResultRow{label, homePoints, awayPoints}`), `ctcLogoBase64`, `fontBase64`

**`provisional-scores-render.html`** (ProvisionalScoresGraphicService lines 94–115):
`seasonYear`, `seasonName`, `matchdayName`, `raceLabel` (String/null after D-05 patch), `homeTeamName`, `awayTeamName`, `homeCardBase64`, `awayCardBase64`, `homeRows` / `awayRows` (List of `ProvisionalRow{driverName, position, qualiPosition, fastestLap, ptsRace, ptsQuali, ptsFl, total}`), `homeOverallPtsRace`, `homeOverallPtsQuali`, `homeOverallPtsFl`, `homeOverallTotal`, `awayOverall*`, `ctcLogoBase64`, `fontBase64`

Handoff `provisional-scores-render.html` confirmed uses `th:if="${raceLabel != null}"` on the `.race-chip` div (line 193). All other bindings unchanged.

### Group C — Matchday/List (5 templates)

All matchday/list templates receive a single `data` model variable of type `MatchdayGraphicData`:

```
MatchdayGraphicData {
  matchdayLabel: String
  seasonName: String
  seasonYear: String
  ctcLogoBase64: String
  fontBase64: String
  matches: List<MatchGraphicRow>
}
MatchGraphicRow {
  homeTeamName, homeTeamShortName, homeLogoBase64
  homePrimaryColor, homeSecondaryColor, homeAccentColor
  homeSeed: int, homeRecord: String
  awayTeamName, awayTeamShortName, awayLogoBase64
  awayPrimaryColor, awaySecondaryColor, awayAccentColor
  awaySeed: int, awayRecord: String
  scheduledDateTime: String/null
  homeScore: Integer/null, awayScore: Integer/null
}
```

**`matchday-schedule-render.html`** uses: `data.fontBase64`, `data.ctcLogoBase64`, `data.matchdayLabel`, `data.seasonName`, `data.seasonYear`, per-row: `m.scheduledDateTime`, `m.homePrimaryColor`, `m.homeLogoBase64`, `m.homeTeamName`, `m.awayPrimaryColor`, `m.awayLogoBase64`, `m.awayTeamName`

**`matchday-overview-render.html`** uses: same as schedule plus `m.homeSeed`, `m.homeRecord`, `m.awaySeed`, `m.awayRecord`; handoff confirmed same bindings, style change to `--c: <color>` CSS custom property via `th:style`.

**`standings-render.html`** (StandingsGraphicService lines 93–101):
Context set directly (not via `data` wrapper): `seasonYear`, `phaseLabel`, `standings` (List of `StandingsRow{position, teamName, teamShortName, teamLogoBase64, primaryColor, wins, draws, losses, points}`), `rowHeightPx`, `fontSizePx`, `logoSizePx`, `posFontSizePx`, `fontBase64`, `ctcLogoBase64`

**`matchday-results-render.html`** uses: same as `matchday-overview` bindings plus scores: `m.homeScore`, `m.awayScore`

**`power-rankings-render.html`** (PowerRankingsGraphicService — receives `data` of type `PowerRankingsGraphicData`):
`data.title`, `data.subtitle`, `data.ctcLogoBase64`, `data.fontBase64`, `data.entries`, `data.leftColumn`, `data.rightColumn`; each entry: `rank`, `teamName`, `teamShortName`, `logoBase64`, `primaryColor`, `secondaryColor`, `accentColor`

### Group D — Overlay (1 template)

**`overlay-render.html`** (OverlayGraphicService lines 72–91):
`homeTeamName`, `homeTeamNameHtml`, `homeTeamShortName`, `homeLogoBase64`, `homePrimaryColor`, `homeSecondaryColor`, `homeRecord`, `awayTeamName`, `awayTeamNameHtml`, `awayTeamShortName`, `awayLogoBase64`, `awayPrimaryColor`, `awaySecondaryColor`, `awayRecord`, `seasonYear`, `matchdayName`, `ctcLogoBase64`, `vsBadgeBase64`, `commentatorBase64`, `fontBase64`

Handoff overlay confirmed uses all these bindings unchanged. New Carbon styling uses `--c: <color>` CSS custom property via `th:style` (same pattern as matchday templates).

### Group E — Analogy Templates (4 templates, NO handoff)

All four analogy templates receive a single `data` variable of type `MatchdayGraphicData` (same fields as Group C above). The two matchday analogy templates are driven by `AbstractMatchdayGraphicService.prepareBaseContext(Matchday)`. The three playoff analogy templates are driven by `AbstractPlayoffRoundGraphicService.prepareBaseContext(PlayoffRound)` — identical DTO structure. The difference: playoff record field is hardcoded `"—"` (string), not W-L-D.

**`matchday-pairings-render.html`** current bindings (verified from source):
`data.fontBase64`, `data.ctcLogoBase64`, `data.matchdayLabel`, `data.seasonName`, `data.seasonYear`, per-row: `m.homeSeed` (displayed as `#N`), `m.homePrimaryColor`, `m.homeLogoBase64`, `m.homeTeamName`, `m.homeRecord`, `m.awaySeed`, `m.awayPrimaryColor`, `m.awayLogoBase64`, `m.awayTeamName`, `m.awayRecord`

Key difference from `matchday-overview`: uses `.rating` class with `#${m.homeSeed}` format instead of bare seed number. Both share identical MatchdayGraphicData structure.

**`playoff-round-schedule-render.html`** current bindings:
`data.fontBase64`, `data.ctcLogoBase64`, `data.matchdayLabel` (round label), `data.seasonName` (playoff name), `data.seasonYear`, per-row: `m.scheduledDateTime`, `m.homePrimaryColor`, `m.homeLogoBase64`, `m.homeTeamName`, `m.awayPrimaryColor`, `m.awayLogoBase64`, `m.awayTeamName`

**`playoff-round-results-render.html`** current bindings:
Same as schedule plus `m.homeScore`, `m.awayScore`

**`playoff-round-overview-render.html`** current bindings:
`data.fontBase64`, `data.ctcLogoBase64`, `data.matchdayLabel`, `data.seasonName`, `data.seasonYear`, per-row: `m.homeSeed`, `m.homePrimaryColor`, `m.homeLogoBase64`, `m.homeTeamName`, `m.awaySeed`, `m.awayPrimaryColor`, `m.awayLogoBase64`, `m.awayTeamName` (no record field, unlike matchday-overview)

**No new mandatory variables needed for any analogy template.** All bindings are already exposed by the existing abstract service base contexts.

---

## Q3: TeamCardService Color-Robustness Patch (D-04)

**File:** `src/main/java/org/ctc/admin/service/TeamCardService.java`

**Existing methods confirmed:**

- `relativeLuminance(String hex)` — private, lines 209–217. Returns `0.2126*R + 0.7152*G + 0.0722*B` (0–255 scale). Returns `1.0` if hex is null or too short.
- `computeGradientColor(String primary, String secondary, String accent)` — package-private, lines 194–207. Iterates all three colors, picks the darkest by luminance.

**Exact insertion point:** `generateCard()` method, immediately AFTER line 93 (`ctx.setVariable("gradientColor", ...)`), BEFORE line 95 (`ctx.setVariable("logoBase64", ...)`).

**New helpers to add (style mirrors `computeGradientColor`):**

```java
String computeAccentVisColor(String accent, String primary) {
    if (accent == null) return primary;
    return relativeLuminance(accent) < 28 ? primary : accent;
}

String contrastColor(String hex) {
    return relativeLuminance(hex) > 140 ? "#0b0b10" : "#ffffff";
}
```

Both methods should be package-private (not private) for unit testability, matching the style of `computeGradientColor`.

**Context variable additions:**

```java
ctx.setVariable("accentVisColor", computeAccentVisColor(accentColor, primaryColor));
ctx.setVariable("onPrimaryColor", contrastColor(primaryColor));
```

**Custom-template-override path implication:** `TeamCardService.renderTemplate()` at line 258 checks for `uploadDir.resolve(CUSTOM_TEMPLATE_FILE)` (`team-card-template.html`). An operator with a saved custom team-card template will NOT see the new Carbon design until they reset the template via the editor (`/admin/tools/template-editors`). This must be documented in the plan as an operator note.

**JaCoCo exclusion:** Confirmed at pom.xml line 373 — `TeamCardService.class` is excluded. The two new helpers (`computeAccentVisColor`, `contrastColor`) do need unit tests (they are pure functions, easily testable without Playwright). Add to `TeamCardServiceTest`.

---

## Q4: ProvisionalScoresGraphicService raceLabel Change (D-05)

**File:** `src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java`

**Current code at line 98:**
```java
ctx.setVariable("raceLabel", "Race " + raceIndex);
```

**Required change:** Replace with conditional logic. The race index alone is not enough — the service needs to know whether the match has > 1 race. The service receives `race` (which has `race.getMatch().getRaces()`) and `raceIndex`. Proposed implementation:

```java
int totalRaces = race.getMatch() != null ? race.getMatch().getRaces().size() : 0;
ctx.setVariable("raceLabel", totalRaces > 1 ? "Race " + raceIndex : null);
```

**Test-impact — tests that must be updated:**

Class: `src/test/java/org/ctc/admin/service/ProvisionalScoresGraphicServiceTest.java`

1. **`whenValidRace_thenTemplateContextIncludesRaceLabelAndExpectedVariables()`** (line 174) — currently asserts `assertThat(ctx.getVariable("raceLabel")).isEqualTo("Race 3")`. The test fixture uses `createValidRace(...)` which creates a Match with `match.setRaces(new ArrayList<>())` (zero races added to match). After the patch, with 0 races in the match, `raceLabel` will be `null`. This test must be updated to either: (a) add 2 races to the match and keep the `"Race N"` assertion, OR (b) test both branches. Per CONTEXT.md D-05, both branches must be asserted.

2. **`givenSameRaceTwice_whenGenerateProvisionalWithSameIndex_thenSameRaceLabel()`** (line 208) — asserts `"Race 2"` on two invocations. Same fixture issue: match has no races added, so `totalRaces` = 0, `raceLabel` = null. This test needs a 2-race match fixture to test the multi-race branch.

**Recommended test expansion:** Add a third test method:
- `givenSingleRaceMatch_whenBuildContext_thenRaceLabelIsNull()`
- Keep existing tests updated to use a ≥2-race match fixture.

**ProvisionalScoresGraphicService JaCoCo exclusion:** Confirmed at pom.xml line 381. The `buildContext` method IS covered by unit tests (MockitoBean approach, service is tested without actual Playwright). The existing test stubs `templateEngine.process()` and `screenshotter.apply()`. The patch logic in `buildContext` IS directly unit-testable.

---

## Q5: Shared-Asset Wiring

**`AbstractGraphicService.encodeClasspathResource(path, mime)`** — confirmed at lines 88–101 of `AbstractGraphicService.java`. Returns `"data:<mime>;base64,<encoded>"` or `null` on failure.

**Classpath constants in `AbstractGraphicService`:**
- `FONT_CLASSPATH = "static/admin/fonts/ConthraxSb.woff2"` (line 23)
- `CTC_LOGO_CLASSPATH = "static/admin/img/ctc-logo-white.png"` (line 24)

**Services that call `encodeClasspathResource` for CTC logo + font (verified):**
- `SettingsGraphicService` (lines 101–102)
- `LineupGraphicService` (lines 106–107)
- `ResultsGraphicService` (lines 103–104)
- `MatchResultsGraphicService` (lines 76–77)
- `ProvisionalScoresGraphicService` (lines 113–114)
- `AbstractMatchdayGraphicService.prepareBaseContext()` (lines 78–81) — covers MatchdaySchedule, MatchdayOverview, MatchdayPairings, MatchdayResults
- `AbstractPlayoffRoundGraphicService.prepareBaseContext()` (lines 65–70) — covers PlayoffRoundSchedule, PlayoffRoundResults, PlayoffRoundOverview
- `StandingsGraphicService` (lines 101–102)
- `PowerRankingsGraphicService` (lines 129–130)

**OverlayGraphicService** additionally calls `encodeClasspathResource` for:
- `VS_BADGE_CLASSPATH = "static/admin/img/vs-badge.svg"` (line 89)
- `COMMENTATOR_CLASSPATH = "static/admin/img/commentator.png"` (line 90)

**Shared assets verified present:**
- `src/main/resources/static/admin/fonts/ConthraxSb.woff2` — confirmed
- `src/main/resources/static/admin/fonts/ConthraxSb.ttf` — also present (unused by templates)
- `src/main/resources/static/admin/img/ctc-logo-white.png` — confirmed
- `src/main/resources/static/admin/img/vs-badge.svg` — confirmed
- `src/main/resources/static/admin/img/commentator.png` — confirmed

**Conclusion:** New and analogy templates need NO service changes for assets. All base64-encoded asset variables are already bound by the existing services.

---

## Q6: Backward-Compat / Test-Surface Map

### Tests that must stay green

| Test class | Location | What it pins | Risk |
|---|---|---|---|
| `ProvisionalScoresGraphicServiceTest` | `src/test/java/org/ctc/admin/service/` | `raceLabel = "Race N"` (2 methods) | MUST UPDATE (D-05) |
| `TemplateRenderingSmokeIT` | `src/test/java/org/ctc/admin/controller/integration/` | No `TemplateProcessingException` on any `/admin/**` GET | Stay green — template-only changes don't affect model |
| `DiscordAutoPostListenerIT` | `src/test/java/org/ctc/discord/service/` | `AFTER_COMMIT` hook creates `TEAM_CARDS` Discord post after match channel create; WireMock stubs webhook POST | Stay green — only TeamCardService template changes |
| `DiscordPostServiceProvisionalScoresIT` | `src/test/java/org/ctc/discord/service/` | Multi-race match (3 races, 2 with results) posts 2 multipart PNGs; `provisionalScoresGraphicService.generateProvisional()` called × 2 | Stay green — mocks the service, no raceLabel assertion here |
| `MatchControllerProvisionalPostIT` | `src/test/java/org/ctc/admin/controller/` | POST to `/admin/matches/{id}/provisional` invokes service | Stay green — service mocked |
| `DiscordPostServiceMatchResultsIT` | `src/test/java/org/ctc/discord/service/` | MATCH_RESULTS bundles 1 overview + 1 per-race PNG | Stay green — template-only changes |
| `DiscordPostServiceTeamCardsIT` | `src/test/java/org/ctc/discord/service/` | TEAM_CARDS post with 2 team-card PNGs | Stay green — service mocked |

### Live-preview entry points (for playwright-cli verification)

| Entry point | URL | What it shows |
|---|---|---|
| Template editor (team card) | `/admin/tools/template-editors?tab=team-cards` | Live preview of team-card template with Sample "Team Alpha" |
| Team card generation | `/admin/tools/team-cards` → Generate | Real PNG generation via TeamCardService |
| Template editors (composite/matchday) | `/admin/tools/template-editors` | Live preview of all configurable templates |
| Matchday graphics | Matchday detail page → generate buttons | Renders via AbstractMatchdayGraphicService subclasses |
| Standings graphics | Season detail → Standings graphic | Renders via StandingsGraphicService |

### Discord backward-compat paths

- **POST-02 auto-post** (`AFTER_COMMIT`): `DiscordAutoPostListenerIT` — posts `TEAM_CARDS` type, uploads 2 PNGs. Template changes do not affect the upload path; the PNG bytes are generated before posting.
- **Re-Post button**: `/admin/discord/posts` PATCH — re-sends existing attachment. No re-render triggered.
- **Refresh button**: calls the graphic service to re-generate, then PATCH. Template changes affect the refreshed PNG visually but not the API contract.

---

## Q7: Overlay Hard-Constraints

Verified from `handoff-templates/overlay-render.html`:

```
.top-bar    { position: absolute; left: 500px; top: 0; width: 921px; height: 120px; }
.bottom-wrapper { position: absolute; left: 218px; top: 924px; width: 1275px; height: 148px; }
```

CTC corner logo: `.ctc-logo-corner { position: absolute; top: 16px; right: 16px; width: 80px; height: 80px; }`

Background: `body { background: transparent; }` — confirmed transparent.

Skew: present on individual elements within bars (not on `.top-bar` itself). The Carbon redesign preserves all absolute positions and dimensions exactly.

The `OverlayGraphicService` calls `renderScreenshotTransparent()` (not `renderScreenshot()`), which passes `.setOmitBackground(true)` to Playwright. This is a service-level concern unaffected by template changes.

**Invariants the planner must verify after template copy:**
- Top-bar: 921×120 at left:500px, top:0
- Bottom-bar: 1275×148 at left:218px, top:924px
- Background: transparent (no `background-color` on `body`)
- `vsBadgeBase64`, `commentatorBase64`, `ctcLogoBase64` bindings present and used without filter

---

## Q8: Suggested Plan Grouping

### Wave 1 — Team Card (highest risk: backend patch)

**Plans:**
1. `105-01`: TeamCardService backend patch — add `computeAccentVisColor`, `contrastColor` helpers; set `accentVisColor` and `onPrimaryColor` context variables. Unit tests for both helpers in `TeamCardServiceTest`.
2. `105-02`: Copy `handoff-templates/team-card-render.html` → `src/main/resources/templates/admin/team-card-render.html`. Visual verification via `playwright-cli`.

**Wave 1 gate:** `./mvnw clean verify` (no E2E needed at this stage). `playwright-cli` visual check at `/admin/tools/team-cards` → Generate.

### Wave 2 — Composites (medium risk: raceLabel backend patch + 5 template copies)

**Plans:**
3. `105-03`: ProvisionalScoresGraphicService raceLabel patch + update both failing unit tests in `ProvisionalScoresGraphicServiceTest` to cover both branches.
4. `105-04`: Copy 4 remaining composite handoff templates (`settings-`, `lineup-`, `results-`, `match-results-render.html`). Copy `provisional-scores-render.html` (already patched service).

**Wave 2 gate:** `./mvnw clean verify`. `playwright-cli` visual check via template editors.

### Wave 3 — Matchday-List (pure template replacement, lower risk)

**Plans:**
5. `105-05`: Copy 5 matchday/list handoff templates (`matchday-schedule-`, `matchday-overview-`, `standings-`, `matchday-results-`, `power-rankings-render.html`).

**Wave 3 gate:** `./mvnw clean verify`. `playwright-cli` visual checks for matchday graphics.

### Wave 4 — Overlay + Analogy (geometry-critical + 4 analogy rebuilds)

**Plans:**
6. `105-06`: Copy `overlay-render.html` handoff template. Verify geometry invariants exactly (top-bar 921×120@500/0, bottom-bar 1275×148@218/924, transparent background).
7. `105-07`: Rebuild 4 analogy templates by applying Carbon system to current bindings: `matchday-pairings-render.html` (sibling of `matchday-overview`), `playoff-round-schedule/results/overview-render.html` (analogous to matchday counterparts).

**Wave 4 gate:** `./mvnw clean verify -Pe2e` (full E2E run). `playwright-cli` visual checks for overlay (over simulated video background) and all 4 analogy templates.

---

## Architecture Patterns

### Render Pipeline

```
HTTP request / event trigger
        |
        v
GraphicService.generate*()
  |-- builds Thymeleaf Context (model variables)
  |-- checks uploadDir for custom template
  |-- if custom: loads file → processStringTemplate()
  |-- else: templateEngine.process(DEFAULT_TEMPLATE, ctx) → HTML string
        |
        v
AbstractGraphicService.renderScreenshot[Transparent]()
  |-- writes HTML to temp file
  |-- Playwright launches headless Chromium
  |-- page.navigate("file://...") → page.screenshot()
  |-- returns PNG bytes / saves to uploadDir
```

### Template Override System

All services (except `StandingsGraphicService`) support custom templates stored in `uploadDir/<service>-template.html`. The `TemplateManageable` interface provides `loadTemplate()`, `saveTemplate()`, `resetTemplate()`, `hasCustomTemplate()`. The template editor UI at `/admin/tools/template-editors` is the standard entry point.

`StandingsGraphicService` does NOT implement `TemplateManageable` — it renders directly via `templateEngine.process(TEMPLATE_PATH, ctx)` with no custom-template override path.

### Anti-Patterns to Avoid

- **Introducing new mandatory model variables:** Hard-banned by CONTEXT.md invariant. Any `th:text="${newVar}"` without Elvis fallback that would throw when `newVar` is absent must use `th:if` or `?: ''` fallback.
- **Changing CSS without preserving absolute positions (overlay):** The overlay geometry is production-critical — elements intentionally obscure video elements at specific coordinates.
- **Modifying the shared `AbstractGraphicService` or abstract base classes:** No changes needed or allowed in this phase.
- **Modifying `StandingsGraphicService` logic:** The dynamic row-height calculation (`rowHeightPx`, `fontSizePx`, etc.) must remain server-side; the handoff template is designed to use these server-computed values.

---

## Common Pitfalls

### Pitfall 1: `StandingsGraphicService` has no custom-template mechanism

**What goes wrong:** Planner assumes all services support `uploadDir` template override. `StandingsGraphicService` does not implement `TemplateManageable`. The drop-in is a classpath file copy only.

**How to avoid:** Direct copy to `src/main/resources/templates/admin/standings-render.html`. No upload-dir file involved.

### Pitfall 2: `oklch(from …)` / `color-mix` require current Chromium

**What goes wrong:** Very old Playwright Chromium installation falls back silently for `oklch(from var(--primary) max(l,.62) c h)` and similar relative-color/color-mix expressions.

**How to avoid:** The project already uses a bundled Playwright Chromium. If Carbon styling looks flat/wrong during visual verification, run `./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"` to update.

### Pitfall 3: ProvisionalScores test fixture has zero-race match

**What goes wrong:** After the D-05 raceLabel patch, existing test fixtures in `ProvisionalScoresGraphicServiceTest` create a match with `match.setRaces(new ArrayList<>())` (no races). With 0 races, `totalRaces = 0`, so `raceLabel` will be `null`. Tests asserting `"Race 3"` / `"Race 2"` will fail immediately.

**How to avoid:** Update the test fixture helper `createValidRace(...)` to add 2+ races to the match, OR add a separate fixture for the multi-race branch. Both branches (null and "Race N") must be tested.

### Pitfall 4: Custom team-card template in uploadDir hides redesign

**What goes wrong:** Operator has a saved `team-card-template.html` in `uploadDir`. After the classpath template is updated, the operator's card generation still uses the old custom template — the Carbon redesign is invisible.

**How to avoid:** Document in plan as an operator note: "Any saved custom team-card template must be reset via `/admin/tools/template-editors` → Reset to Default to pick up the new Carbon design."

### Pitfall 5: `matchday-pairings` vs `matchday-overview` confusion

**What goes wrong:** The handoff file is named `matchday-overview-render.html`. A planner might attempt to drop it into both `matchday-overview-render.html` AND `matchday-pairings-render.html`. The pairings template is a distinct file with different seed display (`#N` format) and must be rebuilt by analogy.

**How to avoid:** The handoff `matchday-overview-render.html` copies ONLY to `matchday-overview-render.html`. The `matchday-pairings` template is an analogy rebuild (D-06), using `matchday-overview` as the Carbon reference but preserving the current pairings-specific seed format.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---|---|---|
| Base64 encoding of classpath resources | Custom encoder | `AbstractGraphicService.encodeClasspathResource(path, mime)` |
| Luminance computation | New helper | `TeamCardService.relativeLuminance(hex)` (0–255 scale) |
| Gradient color selection | New helper | `TeamCardService.computeGradientColor(primary, secondary, accent)` |
| Custom template loading | New file reader | `AbstractMatchdayGraphicService.loadTemplate()` / `renderTemplate()` pattern |
| PNG generation | Direct Playwright use | Existing `renderScreenshot()` / `renderScreenshotTransparent()` in `AbstractGraphicService` |

---

## Runtime State Inventory

This is a template-replacement phase with two small backend patches. No data migration is required.

| Category | Items Found | Action Required |
|---|---|---|
| Stored data | None — templates are classpath resources; PNGs are regenerated on demand | None |
| Live service config | Custom templates in operator's `uploadDir` (`team-card-template.html`, etc.) | Operator action: reset via template editor to pick up Carbon design |
| OS-registered state | None | None |
| Secrets/env vars | None | None |
| Build artifacts | None — JaCoCo-excluded services have no instrumentation artifacts | None |

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + Spring Boot Test |
| Config file | `pom.xml` (Surefire/Failsafe configuration) |
| Quick run command | `./mvnw clean verify` |
| Full suite command | `./mvnw clean verify -Pe2e` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Exists? |
|---|---|---|---|---|
| CARD-01 | TeamCardService sets `accentVisColor` and `onPrimaryColor` | unit | `./mvnw clean verify -Dtest=TeamCardServiceTest` | Partial — new helper tests needed |
| CARD-01 | `computeAccentVisColor`: accent < 28 → primary fallback | unit | Same | New test needed |
| CARD-01 | `contrastColor`: luminance > 140 → dark text | unit | Same | New test needed |
| CARD-02 | Template smoke — no `TemplateProcessingException` on any admin GET | integration | `./mvnw clean verify -Dit.test=TemplateRenderingSmokeIT -DfailIfNoTests=false` | Exists |
| CARD-02 | Auto-post AFTER_COMMIT creates TEAM_CARDS post | integration | `./mvnw clean verify -Dit.test=DiscordAutoPostListenerIT -DfailIfNoTests=false` | Exists |
| CARD-03 | raceLabel null for single-race match | unit | `./mvnw clean verify -Dtest=ProvisionalScoresGraphicServiceTest` | NEW test needed |
| CARD-03 | raceLabel "Race N" for multi-race match | unit | Same | Existing test updated |
| CARD-04 | Overlay rendered with correct geometry | manual/visual | `playwright-cli` | N/A (visual only) |
| CARD-04 | Analogy templates render without exception | integration | `./mvnw clean verify -Dit.test=TemplateRenderingSmokeIT -DfailIfNoTests=false` | Covered by smoke |

### Wave 0 Gaps

- [ ] New unit tests for `computeAccentVisColor` and `contrastColor` in `TeamCardServiceTest`
- [ ] Updated tests in `ProvisionalScoresGraphicServiceTest` (both branches of raceLabel)
- [ ] New test for single-race branch: `givenSingleRaceMatch_whenBuildContext_thenRaceLabelIsNull()`

---

## Security Domain

No new authentication, session management, access control, input validation, or cryptography is introduced. All changes are to internal rendering templates and two pure-function helper methods. The graphic services are admin-only and protected by existing Spring Security configuration (prod/docker profile). ASVS V5 (input validation) is not applicable — no user-supplied data reaches the new template CSS.

---

## JaCoCo Coverage Impact

**All affected graphic services are excluded from JaCoCo instrumentation** (pom.xml lines 373–388):

Excluded: `TeamCardService`, `LineupGraphicService`, `ResultsGraphicService`, `SettingsGraphicService`, `OverlayGraphicService`, `MatchResultsGraphicService`, `PowerRankingsGraphicService`, `StandingsGraphicService`, `ProvisionalScoresGraphicService`, `AbstractGraphicService`, `PlaywrightScreenshotter`, `PlayoffRoundOverviewGraphicService`, `PlayoffRoundScheduleGraphicService`, `PlayoffRoundResultsGraphicService`, `MatchdayPairingsGraphicService`, `MatchdayScheduleGraphicService`.

**NOT in the JaCoCo exclusion list:** `MatchdayOverviewGraphicService`, `MatchdayResultsGraphicService`, `AbstractMatchdayGraphicService`, `AbstractPlayoffRoundGraphicService`. These services are thin (single `generate*()` method delegating to the abstract base context builder). They do not call Playwright directly and may be covered by Spring context loading. However, they are not test targets for this phase.

**Net effect:** The D-04 backend helpers (`computeAccentVisColor`, `contrastColor`) in `TeamCardService` are JaCoCo-excluded. Adding unit tests for them is still the right practice (correctness, not coverage). The D-05 `buildContext` change in `ProvisionalScoresGraphicService` is also JaCoCo-excluded — but the existing unit test uses MockitoBean stubs and DOES execute `buildContext`, so the two updated test methods will run and catch regressions.

The 82% coverage gate will NOT be disturbed by this phase.

---

## Assumptions Log

No assumptions — all claims in this research were verified against the live codebase by reading source files and running grep commands.

| # | Claim | Section | Risk if Wrong |
|---|---|---|---|
| — | All claims verified via direct source inspection | — | — |

**This table is intentionally empty.** No `[ASSUMED]` claims were made.

---

## Sources

### Primary (HIGH confidence — verified via direct source file reads)

- `src/main/java/org/ctc/admin/service/TeamCardService.java` — confirmed `relativeLuminance`, `computeGradientColor`, `renderTemplate`, context variable assignments
- `src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java` — confirmed line 98 `raceLabel` assignment, `buildContext` method signature
- `src/main/java/org/ctc/admin/service/AbstractGraphicService.java` — confirmed `encodeClasspathResource`, classpath constants
- `src/main/java/org/ctc/admin/service/AbstractMatchdayGraphicService.java` — confirmed `getDefaultTemplatePath()` abstract method, `MatchdayGraphicData` binding
- `src/main/java/org/ctc/admin/service/AbstractPlayoffRoundGraphicService.java` — confirmed same structure as matchday counterpart
- All 8 concrete graphic service files — confirmed `getDefaultTemplatePath()` string constants and custom template filenames
- `src/main/java/org/ctc/admin/dto/MatchdayGraphicData.java` — confirmed all DTO fields
- `src/main/resources/templates/admin/*.html` (all 16) — confirmed th:* bindings
- `.planning/phases/105-team-card-visual-redesign/design-handoff/handoff-templates/*.html` (12 files sampled) — confirmed bindings unchanged
- `src/test/java/org/ctc/admin/service/ProvisionalScoresGraphicServiceTest.java` — confirmed 2 methods asserting `"Race N"`
- `pom.xml` lines 369–389 — confirmed JaCoCo exclusions for all Playwright-dependent services
- `src/main/resources/static/admin/img/` and `static/admin/fonts/` — confirmed all 4 shared assets present

---

## Metadata

**Confidence breakdown:**
- Template mapping: HIGH — read every `getDefaultTemplatePath()` and `renderTemplate()` directly
- th:* binding inventory: HIGH — read all 16 current templates and sampled handoff templates
- Backend patch locations: HIGH — read source with line numbers
- Test-impact: HIGH — read test source, confirmed method names and assertion strings
- JaCoCo exclusions: HIGH — read pom.xml exclusion list verbatim

**Research date:** 2026-05-29
**Valid until:** Stable — no external dependencies or fast-moving ecosystem; valid until source files change
