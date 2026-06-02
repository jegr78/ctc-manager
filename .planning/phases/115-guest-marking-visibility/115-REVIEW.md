---
phase: 115-guest-marking-visibility
reviewed: 2026-06-02T00:00:00Z
depth: standard
files_reviewed: 29
files_reviewed_list:
  - src/main/java/org/ctc/admin/controller/RaceController.java
  - src/main/java/org/ctc/admin/service/LineupGraphicService.java
  - src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java
  - src/main/java/org/ctc/admin/service/RaceGraphicService.java
  - src/main/java/org/ctc/admin/service/ResultsGraphicService.java
  - src/main/java/org/ctc/domain/service/DriverRankingService.java
  - src/main/java/org/ctc/domain/service/RaceService.java
  - src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java
  - src/main/resources/static/admin/css/admin.css
  - src/main/resources/static/site/css/style.css
  - src/main/resources/templates/admin/fragments/guest-marker.html
  - src/main/resources/templates/admin/lineup-render.html
  - src/main/resources/templates/admin/matchday-detail.html
  - src/main/resources/templates/admin/provisional-scores-render.html
  - src/main/resources/templates/admin/race-detail.html
  - src/main/resources/templates/admin/results-render.html
  - src/main/resources/templates/admin/standings.html
  - src/main/resources/templates/site/alltime-driver-ranking.html
  - src/main/resources/templates/site/driver-profile.html
  - src/main/resources/templates/site/driver-ranking.html
  - src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java
  - src/test/java/org/ctc/admin/service/LineupGraphicServiceTest.java
  - src/test/java/org/ctc/admin/service/ProvisionalScoresGraphicServiceTest.java
  - src/test/java/org/ctc/admin/service/RaceGraphicServiceTest.java
  - src/test/java/org/ctc/admin/service/ResultsGraphicServiceTest.java
  - src/test/java/org/ctc/domain/service/DriverRankingServiceGuestIT.java
  - src/test/java/org/ctc/domain/service/RaceServiceTest.java
  - src/test/java/org/ctc/sitegen/DriverProfilePageGeneratorIT.java
  - src/test/resources/sitegen/baseline/single-league-driver-profile.html
findings:
  critical: 0
  warning: 4
  info: 5
  total: 9
status: issues_found
---

# Phase 115: Code Review Report

**Reviewed:** 2026-06-02
**Depth:** standard
**Files Reviewed:** 29
**Status:** issues_found

## Summary

Phase 115 adds a guest-driver star marker (`&#x2605;` / amber `--guest`) across all admin and public surfaces that render driver names, plus a standalone provisional-scores graphic path (`ProvisionalScoresGraphicService.generateProvisionalFile` + `RaceGraphicService.generateProvisional` + `POST /admin/races/{id}/generate-provisional`).

Overall the work is solid: guest detection uniformly goes through `RaceLineupRepository.findByRaceIdAndDriverId(...).map(RaceLineup::isGuest)`, sub-team rollup is consistent, the new POST endpoint follows the existing graphic-generation pattern exactly (mirrors `generate-results`), the dual-role guest path is correctly handled with a per-result lineup check, and the `single-league-driver-profile.html` byte-identity baseline is preserved. No security vulnerabilities found in the new endpoint (UUID `@PathVariable`, no user-supplied strings reach SQL/FS/HTML sinks; the `returnUrl` open-redirect guard on `quickScore` is pre-existing and correct).

No BLOCKER-level defects were proven. The findings below are quality/robustness concerns: an orphaned fragment, a coverage gap on the never-integration-rendered admin graphic templates' guest binding, comment pollution that violates the project "No Comment Pollution" rule, and an unguarded numeric-cell render for empty provisional rows.

## Warnings

### WR-01: New provisional graphic templates' guest binding is never integration-rendered — coverage blind spot

**File:** `src/main/resources/templates/admin/provisional-scores-render.html:135,183`; `src/main/resources/templates/admin/lineup-render.html:102,106`; `src/main/resources/templates/admin/results-render.html:100,106`
**Issue:** The guest marker on all three admin **graphic** templates binds via `${row.isGuest}` / `${p.homeIsGuest}` / `${row.homeIsGuest}` against record components (`ProvisionalRow.isGuest()`, `DriverPairing.homeIsGuest()`, `DriverResultRow.homeIsGuest()`). Every unit test for these services (`ProvisionalScoresGraphicServiceTest`, `LineupGraphicServiceTest`, `ResultsGraphicServiceTest`) **mocks the `TemplateEngine`** (`when(templateEngine.process(...)).thenReturn("<html>...</html>")`) and never renders the real template. These services are also JaCoCo-excluded (runtime-Playwright). The result: the actual SpEL property resolution of the record accessors in these three templates is verified by **no automated test at all**. By contrast, the *site* templates' guest binding (`driver-profile.html`) is genuinely rendered and asserted in `DriverProfilePageGeneratorIT` (`assertThat(html).contains("guest-marker")` / `"&#x2605;"`). The binding works on Spring 7 SpEL (record-component accessors are supported), so this is not a proven defect — but a typo or record-rename in any of these three templates would ship undetected.
**Fix:** Add a lightweight render assertion that does NOT mock the engine — e.g. a small `@SpringBootTest`/`@Tag("integration")` that calls `templateEngine.process("admin/provisional-scores-render", ctx)` with a context containing one `isGuest=true` row and asserts the output contains `guest-marker` and `&#x2605;`. One test per template (provisional/lineup/results) closes the blind spot the mocked unit tests leave open.

### WR-02: Orphaned `guest-marker.html` fragment — dead code that also contradicts the PLAT-07 inline-span decision

**File:** `src/main/resources/templates/admin/fragments/guest-marker.html:4`
**Issue:** `<th:block th:fragment="guestMarker(isGuest)">` is defined but never invoked anywhere in `src/` (`grep -rn ":: guestMarker"` / `fragments/guest-marker` returns only the definition). Every surface uses an inline `<span class="guest-marker" th:if="...">` instead — which is the deliberate PLAT-07 decision ("the project uses inline `.guest-marker` spans instead"). A parameterised fragment-call like `th:replace="~{admin/fragments/guest-marker :: guestMarker(${...})}"` is exactly the pattern the PLAT-07 build-guard (`pom.xml:499`) forbids. Leaving this file in place is misleading: a future contributor may "wire it up" and immediately trip the build guard, or assume it is the canonical mechanism.
**Fix:** Delete `src/main/resources/templates/admin/fragments/guest-marker.html` — it is unreferenced and structurally at odds with the established inline-span convention.

### WR-03: Empty provisional rows render literal "0" in all numeric cells (no `n/a`-guard parity with lineup/results)

**File:** `src/main/resources/templates/admin/provisional-scores-render.html:137-143,185-191`
**Issue:** `emptyRow()` (`ProvisionalScoresGraphicService.java:181`) produces `("n/a", 0, 0, false, 0, 0, 0, 0, false)`. The template renders the numeric cells unconditionally (`<td th:text="${row.position}">`, `${row.qualiPosition}`, `${row.ptsRace}`, …), so a padded empty slot shows a row of dimmed `0`s plus `n/a`. The sibling templates guard this: `lineup-render.html:103` and `results-render.html:101` wrap the secondary cells in `th:if="${...Driver != 'n/a'}"`, and `results-render.html:103` renders `0`/blank deliberately only for the points column. The provisional table is the new surface and is the only one that prints `0 0 0 0 0` for an unused slot, which reads as a real zero-score driver rather than an empty pad.
**Fix:** Guard the numeric `<td>`s on `${row.driverName != 'n/a'}` (render empty string for pad rows), mirroring `lineup-render.html`. Example for each data cell:
```html
<td th:text="${row.driverName != 'n/a' ? row.position : ''}"></td>
```
(Presentation-only; graphic service is JaCoCo-excluded, so verify visually via `playwright-cli`.)

### WR-04: `resolveRaceIndex` and `buildContext` disagree on `match.getRaces()` null-safety

**File:** `src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java:74,156`
**Issue:** `resolveRaceIndex` defensively null-checks `match.getRaces() == null` (line 74) before iterating, but `buildContext` computes `int totalRaces = race.getMatch() != null ? race.getMatch().getRaces().size() : 0;` (line 156) with **no** `getRaces() == null` guard. In production `Match.races` is initialized to `new ArrayList<>()` (`Match.java:53`) so this cannot NPE today, and `resolveRaceIndex`'s extra guard is therefore also redundant — but the two methods making **opposite** assumptions about the same field in the same class is an inconsistency that invites a future NPE if either the field initializer or a detached/partially-constructed `Match` ever changes. The standalone path (`generateProvisionalFile` → `resolveRaceIndex`) and the byte path (`generateProvisional(race, raceIndex)`) both flow into `buildContext`, so the unguarded line is on every code path.
**Fix:** Make the two consistent — either drop the now-redundant `getRaces() == null` branch in `resolveRaceIndex` (relying on the non-null initializer), or add the same guard in `buildContext`: `int totalRaces = (race.getMatch() != null && race.getMatch().getRaces() != null) ? race.getMatch().getRaces().size() : 0;`. Prefer one documented assumption over two contradictory ones.

## Info

### IN-01: Comment pollution — Plan/Decision references in source violate "No Comment Pollution"

**File:** `src/main/java/org/ctc/domain/service/DriverRankingService.java:118-120`; `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java:112-115,130-131,134-135`; `src/test/java/org/ctc/admin/service/RaceGraphicServiceTest.java:51,125,216`
**Issue:** CLAUDE.md "No Comment Pollution" hard-bans Phase/Plan/Task/UAT references and "per CLAUDE.md"-style cross-refs in source and tests. Several touched files carry them: `DriverRankingService.java:118` "Tracked Behavior Change (v1.9 / D-19)"; `DriverProfilePageGenerator.java:114-115` "(per CLAUDE.md \"RaceLineup is Source of Truth\")" and `:130` "(PLAYOFF results aren't seeded in test fixtures)"; `RaceGraphicServiceTest.java:51,125,216` repeated "Matchday is bound via SeasonPhase (Convenience-Getter exposes the season)." These rot and duplicate convention text that belongs once in CLAUDE.md. The rule explicitly says: "When refactoring, remove pollution from touched files."
**Fix:** Strip the Plan/Decision/"per CLAUDE.md" references. Keep only genuine non-obvious WHY comments (e.g. the participation-via-RaceLineup rationale may stay if reworded to drop the CLAUDE.md cross-ref).

### IN-02: `RaceControllerCalendarTest` header Javadoc references "v1.12 COV-01 audit"

**File:** `src/test/java/org/ctc/admin/controller/RaceControllerCalendarTest.java:38-52`
**Issue:** The class-level Javadoc names "v1.12 COV-01 audit" and "sealed permits" rationale — a phase/audit cross-reference banned by "No Comment Pollution." (This file was only minimally touched this phase — the `RaceGraphicService` mock import — so this is pre-existing, but the rule says remove pollution from touched files.)
**Fix:** Trim the Javadoc to the behavioral contract (what the test covers) without the v1.12/COV-01 audit reference.

### IN-03: `RaceController` is wide but still thin — acceptable, noted for trend

**File:** `src/main/java/org/ctc/admin/controller/RaceController.java:328-336`
**Issue:** The new `generateProvisional` handler is correctly thin (delegates to `raceGraphicService`, sets flash, redirects) and identical in shape to the seven sibling generate-* handlers. No business logic leaked in. The only observation: the controller now has 8 near-identical `generate-*` POST handlers (lineup/results/provisional/settings/lobby-settings/overlay) differing only by service method + success string. This is duplication, not a defect.
**Fix:** Optional future refactor — a single `@PostMapping("/{id}/generate/{kind}")` dispatch or a small map of `kind -> (Consumer, message)` would collapse the six pure-graphic handlers. Not in scope for v1; flagged only as a maintainability trend.

### IN-04: `DriverProfilePageGenerator` uses fully-qualified inline types instead of imports

**File:** `src/main/java/org/ctc/sitegen/DriverProfilePageGenerator.java:57,83,122`
**Issue:** `new java.util.HashSet<java.util.UUID>()`, `new java.util.LinkedHashMap<>()`, `java.util.HashSet::new` are written fully-qualified inline while `java.util.*` types are otherwise imported in the file's neighbours. This is stylistic inconsistency, not a bug (the Checkstyle UnusedImports gate would not flag it). Reduces readability.
**Fix:** Add `import java.util.HashSet;`, `import java.util.LinkedHashMap;`, `import java.util.UUID;` and use the short names (Checkstyle will keep them honest if they later become unused).

### IN-05: `guest-marker` CSS duplicated across `admin.css` and `style.css` with divergent context

**File:** `src/main/resources/static/admin/css/admin.css:2117`; `src/main/resources/static/site/css/style.css:1092`
**Issue:** `.guest-marker` is defined in both stylesheets (admin uses `color: var(--guest)`, the graphic-render templates inline `color: #f59e0b`). The three sets of declarations (admin.css, style.css, inline `<style>` in lineup/results/provisional render templates) must stay in sync manually; the inline ones hard-code `#f59e0b` while the page CSS uses the `--guest` custom property. The graphic templates are standalone Playwright-rendered documents that cannot reference `admin.css`, so inlining is justified there — but the hard-coded hex vs `--guest` token split means a future palette change to `--guest` silently won't reach the graphics.
**Fix:** No code change required for v1 (the graphics legitimately can't import the variable). Document the `--guest` = `#f59e0b` coupling once, or define the hex in a single shared place. Flagged as a consistency hazard only.

---

## Resolution (2026-06-02)

All four warnings and the actionable info items were fixed in-milestone:

- **WR-01 — RESOLVED.** Added `GraphicTemplateGuestRenderIT` (`@SpringBootTest @Tag("integration")`) that renders `provisional-scores-render`, `lineup-render`, and `results-render` through the real Spring `TemplateEngine` (record-component SpEL binding) with a mixed guest/non-guest row set, asserting exactly one `&#x2605;` / `guest-marker` per template. Closes the mocked-engine blind spot.
- **WR-02 — RESOLVED.** Deleted the orphaned `templates/admin/fragments/guest-marker.html` (unreferenced; its parameterised-call form would trip the PLAT-07 guard).
- **WR-03 — RESOLVED.** Guarded the numeric `<td>`s in both provisional tables on `${row.driverName != 'n/a'}`, so empty pad rows no longer print `0 0 0 0 0`.
- **WR-04 — RESOLVED.** `buildContext` now applies the same `getRaces() == null` guard as `resolveRaceIndex` (one consistent assumption).
- **IN-01 / IN-02 — RESOLVED.** Stripped Plan/Decision/version cross-refs and the repeated SeasonPhase comment from `DriverRankingService`, `DriverProfilePageGenerator`, `RaceGraphicServiceTest`, `RaceControllerCalendarTest`; kept genuine WHY notes.
- **IN-04 — RESOLVED.** Replaced fully-qualified inline `java.util.*` types in `DriverProfilePageGenerator` with imports.
- **IN-03 — ACCEPTED (no change).** The 8 near-identical `generate-*` handlers are duplication, not a defect; collapse deferred as an optional future refactor.
- **IN-05 — ACCEPTED (no change).** Graphic templates legitimately cannot reference the `--guest` CSS variable (standalone Playwright documents); the `#f59e0b` ↔ `--guest` coupling is a known, documented consistency hazard.

_Reviewed: 2026-06-02_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
