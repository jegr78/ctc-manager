---
phase: 105-team-card-visual-redesign
reviewed: 2026-05-29T00:00:00Z
depth: standard
files_reviewed: 20
files_reviewed_list:
  - src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java
  - src/main/java/org/ctc/admin/service/TeamCardService.java
  - src/main/resources/templates/admin/lineup-render.html
  - src/main/resources/templates/admin/match-results-render.html
  - src/main/resources/templates/admin/matchday-overview-render.html
  - src/main/resources/templates/admin/matchday-pairings-render.html
  - src/main/resources/templates/admin/matchday-results-render.html
  - src/main/resources/templates/admin/matchday-schedule-render.html
  - src/main/resources/templates/admin/overlay-render.html
  - src/main/resources/templates/admin/playoff-round-overview-render.html
  - src/main/resources/templates/admin/playoff-round-results-render.html
  - src/main/resources/templates/admin/playoff-round-schedule-render.html
  - src/main/resources/templates/admin/power-rankings-render.html
  - src/main/resources/templates/admin/provisional-scores-render.html
  - src/main/resources/templates/admin/results-render.html
  - src/main/resources/templates/admin/settings-render.html
  - src/main/resources/templates/admin/standings-render.html
  - src/test/java/org/ctc/admin/service/ProvisionalScoresGraphicServiceTest.java
  - src/test/java/org/ctc/admin/service/TeamCardServiceTest.java
findings:
  critical: 1
  warning: 3
  info: 2
  total: 6
status: fixed
---

# Phase 105: Code Review Report

**Reviewed:** 2026-05-29
**Depth:** standard
**Status:** fixed

## Summary

Phase 105 re-styled 16 Playwright-rendered admin graphic templates to a Carbon/Gold
design system and made two narrow backend tweaks: TeamCardService now exposes
`accentVisColor` (via `computeAccentVisColor`) and `onPrimaryColor` (via `contrastColor`),
and ProvisionalScoresGraphicService gates `raceLabel` on whether the match has more than
one race. The bulk of the work is CSS/markup restyling that correctly honours the
"no new model variables" constraint — templates bind only variables the services already
expose, and the inline `th:style="'--c: ' + ${...primaryColor ?: '#5a5a64'} + ';'"`
custom-property pattern is used consistently across all 16 templates as the established
convention. No injection, secret, or data-loss vector was introduced; these are
server-side render templates fed by trusted service models, not user input.

No comment-pollution markers (Phase/Plan/Wave/UAT) were found in the touched files; the
German CSS section comments present are pre-existing styling notes, not phase markers. No
Thymeleaf expression embeds large inline data — base64 vars are referenced directly in
`@font-face`/`th:src`, never inside a larger ternary — so the Spring Boot 4 EL1079E
10,000-char SpEL limit is not at risk anywhere in this set.

The one Critical finding is a latent crash path introduced by the TeamCardService change.
Two pre-identified items from the prompt could **not** be reproduced on this branch and
are recorded as informational rather than as defects — see the Verification Note.

**Verification Note (pre-identified items):**
1. **`standings-render.html.bak`** — does **not** exist on branch
   `gsd/v1.14-team-card-redesign`: `ls`/`test -f`/`Read` all report missing,
   `git ls-files '*.bak'` is empty, and a recursive `find` over the worktree finds no
   `.bak`. Commit `8302da8a` named in the prompt is **not a valid object** on this branch
   (`git cat-file -t 8302da8a` → "Not a valid object name"). There is nothing to delete;
   flagging a nonexistent file would be a false positive. Recorded as IN-02 (guard only).
2. **`TeamCardServiceColorRobustnessTest.java`** — listed for review but does **not exist**
   on this branch. Its absence is material because it leaves CR-01's crash path untested
   (IN-01).

## Critical Issues

### CR-01: `contrastColor` / `computeAccentVisColor` now parse the raw primary color and throw on non-hex input, crashing team-card generation

**File:** `src/main/java/org/ctc/admin/service/TeamCardService.java:94-95, 211-229`
**Issue:** New lines 94-95 call `computeAccentVisColor(accentColor, primaryColor)` and
`contrastColor(primaryColor)` at card-build time. Both delegate to `relativeLuminance`
(lines 222-229), which guards only `hex == null || hex.length() < 7` and then does
`Integer.parseInt(hex.substring(1,3), 16)` / `(3,5)` / `(5,7)` unconditionally.
`primaryColor` comes from `seasonTeam.getEffectivePrimaryColor()` — a free-form,
admin-entered string. Any stored value with length ≥ 7 that is not valid `#RRGGBB` hex
throws an unchecked exception:

- `"transparent"` (11 chars) → `NumberFormatException` parsing `"ra"`.
- `"rgb(0,0,0)"` (10 chars) → `NumberFormatException`.
- `"darkslategray"` / any CSS keyword ≥ 7 chars → `NumberFormatException`.
- `"#GGGGGG"` → `NumberFormatException`.

`generateCard` has no try/catch around the color helpers, so a single team with a
non-hex primary color throws straight out of `generateCard`. That method is on the
Discord auto-post path and the "regenerate all cards" path (`generateAllCards`, lines
161-176, has no per-team error isolation), so one bad value aborts the whole batch. This
is a behaviour change introduced by this phase: before it, neither helper was invoked on
`primaryColor` during card build, so a malformed color was previously harmless. The
existing `TeamCardServiceTest` only feeds well-formed `#RRGGBB` values, so the crash path
is untested (and the named robustness test does not exist — see IN-01).
**Fix:** Make `relativeLuminance` tolerant and fall back to the existing safe default
instead of throwing:
```java
private double relativeLuminance(String hex) {
    if (hex == null || !hex.matches("(?i)#[0-9a-f]{6}")) {
        return 1.0; // unparseable → treat as "bright"; same branch as the < 7 guard today
    }
    int r = Integer.parseInt(hex.substring(1, 3), 16);
    int g = Integer.parseInt(hex.substring(3, 5), 16);
    int b = Integer.parseInt(hex.substring(5, 7), 16);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
```
Add unit tests asserting `contrastColor("transparent")`,
`computeAccentVisColor("#zzzzzz", "#336699")`, and a 3-digit `"#abc"` shorthand return a
fallback rather than throwing.

**Resolution (FIXED):** `relativeLuminance` renamed to `perceivedBrightness255` and guarded
with a strict `#RRGGBB` regex; non-hex input now returns the same `1.0` fallback the old
length-only guard produced instead of throwing. Behaviour for valid hex is unchanged.
Pinned by `TeamCardServiceColorRobustnessTest` (TDD red→green). Commit `22f1b3f8`.

## Warnings

### WR-01: `raceLabel` suppression depends on `match.getRaces().size()`; verify against the real persisted graph, not just hand-seeded test collections

**File:** `src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java:98-99`
**Issue:** The change replaced unconditional `"Race " + raceIndex` with
`totalRaces > 1 ? "Race " + raceIndex : null`, where
`totalRaces = race.getMatch() != null ? race.getMatch().getRaces().size() : 0`. The
template (`provisional-scores-render.html:193`) renders the chip only
`th:if="${raceLabel != null}"`, so the styling side is correct, and putting the decision
in the service (not the template) is the right layer per "no fallback calculations in
templates". The risk is data-shape: if `race.getMatch()` is non-null but its `races`
collection is lazily under-populated/not yet flushed at call time, a genuine multi-race
match could read `size()==1` and wrongly suppress the label. The unit tests seed the
collection by hand (`match.getRaces().add(...)`), so they do not exercise the real
OSIV/JPA load path — `ProvisionalScoresGraphicServiceTest` mocks everything.
**Fix:** Confirm via an integration test (real `@SpringBootTest` load of a multi-race
`Match` through `generateProvisional`) that `match.getRaces().size()` is fully populated
at that point, or derive `totalRaces` from a value the service is certain is eager. If
existing IT coverage already loads a real multi-race match through this path, no code
change is needed.

**Resolution (ACCEPTED — verification gap):** No existing integration test loads a real
multi-race `Match` through `ProvisionalScoresGraphicService.generateProvisionalScoresGraphic`
— all graphic services have only mock-based `*Test.java`, none has an `*IT.java`, and the
method drives Playwright screenshotting (JaCoCo-excluded, heavy). Adding a faithful
`@SpringBootTest` IT would require substantial new fixture scaffolding with no existing
graphic-service IT to model on, so per the bounded instruction it was not forced. No
symptom workaround was added; the code is left as-is. This remains an open verification gap:
the `match.getRaces().size()` read on the real OSIV/JPA load path is not exercised by a test.

### WR-02: Undocumented magic-number thresholds and a misleading method name in the new color helpers

**File:** `src/main/java/org/ctc/admin/service/TeamCardService.java:212, 219, 222`
**Issue:** `computeAccentVisColor` uses floor `< 28` and `contrastColor` uses `> 140`,
both operating on the value returned by `relativeLuminance`. That method is **not** WCAG
relative luminance (which is 0–1 and gamma-corrected); it is an un-normalized 0–255
weighted channel sum. The two thresholds are bare magic numbers and the name invites a
future reader to mis-reason about the scale (e.g. comparing against 0.5). Not a
correctness bug today, but maintainability debt on freshly added code.
**Fix:** Extract named constants (e.g. `ACCENT_VISIBILITY_FLOOR = 28`,
`DARK_TEXT_THRESHOLD = 140`) and either rename `relativeLuminance` to
`perceivedBrightness255` or add a one-line WHY comment that the return is an
un-normalized 0–255 weighted sum, not WCAG luminance.

**Resolution (FIXED):** Both done — `relativeLuminance` renamed to `perceivedBrightness255`
(all call sites updated), thresholds extracted to `ACCENT_VISIBILITY_FLOOR = 28` and
`DARK_TEXT_THRESHOLD = 140`, plus a single WHY comment that the value is an un-normalized
0–255 weighted channel sum, not WCAG luminance. Rode along in commit `22f1b3f8`.

### WR-03: Light data shaping in `th:text` concatenations belongs in the row/data DTO

**File:** `settings-render.html:95, 99, 103, 107, 115`; `results-render.html:97, 99-100, 103`;
`lineup-render.html:93, 97`
**Issue:** Per "Keep Thymeleaf Templates Lean", multi-token presentation strings such as
`${tyreWearMultiplier + 'x / ' + fuelConsumptionMultiplier + 'x / ' + numberOfRequiredPitStops}`
(settings 99), `${initialFuel + ' / ' + refuelingSpeed + ' l/s'}` (settings 103), and the
conditional `${row.homeDriver != '' ? row.homePoints : ''}` (results 99-100) do
formatting/conditional shaping in the template that the service-produced DTO should own.
These are small, within this template set's established style, and several pre-date this
phase, so none is a blocker. All are far under the EL1079E SpEL size limit. Flagged so the
pattern is not expanded further in new markup.
**Fix:** Where this phase touched these lines, push the composed string/points into the
existing row/data DTO field. If the lines are untouched pre-existing pattern, leave them
and capture as backlog — do not add new instances of the pattern.

**Resolution (ACCEPTED / backlog — no code change):** Fixing this by pushing the composed
strings/points into a DTO field would require introducing NEW model/template variables,
which the Phase 105 hard constraint forbids (templates may bind only variables the existing
`*GraphicService` classes already expose). These are also pre-existing, established-pattern
lines. No template/DTO change was made. Constraint for future work: do not expand this
pattern in new markup.

## Info

### IN-01: `TeamCardServiceColorRobustnessTest` (in the review file list) does not exist on this branch

**File:** `src/test/java/org/ctc/admin/service/TeamCardServiceColorRobustnessTest.java` (absent)
**Issue:** The phase file list references a color-robustness test, but the file is not
present (`Read` → "File does not exist"; not in `git ls-files`). Combined with CR-01, the
new `contrastColor(primaryColor)` crash path has zero coverage. A color-robustness helper
without a robustness test is incomplete — malformed-input tolerance is exactly what is
untested.
**Fix:** Add the missing test class (Given-When-Then names, untagged plain unit test)
pinning the malformed-hex tolerance from CR-01's fix snippet.

**Resolution (FIXED):** Added `TeamCardServiceColorRobustnessTest` — a plain untagged unit
test (no `@SpringBootTest`, no Playwright) exercising the package-private helpers directly:
`contrastColor` and `computeAccentVisColor` with `"transparent"`, `"rgb(0,0,0)"`,
`"#GGGGGG"`, `"#abc"`, `null`, empty, `"#zzzzzz"`, plus a valid-hex regression and a
`computeGradientColor` mixed-garbage case. Written red first (commit `59cf2eb6`), green
after the CR-01 fix.

### IN-02: Backup-file guard (pre-identified Warning could not be reproduced)

**File:** `src/main/resources/templates/admin/standings-render.html.bak` (absent)
**Issue:** The pre-identified Warning about a committed `.bak` editor backup could not be
verified on this branch — the file does not exist, no `*.bak` is tracked, recursive
`find` finds none, and the cited commit `8302da8a` is not a valid object here. Recorded
for traceability, not as an open defect; flagging a nonexistent file would be a false
positive.
**Fix:** No action needed on this branch. As a permanent guard, ensure `.gitignore`
contains `*.bak`. If a different ref/worktree under review still contains the file,
`git rm` it there.

**Resolution (FIXED — guard only):** `*.bak` added to `.gitignore` as a permanent guard.
No `git rm` performed — the file does not exist on this branch. Commit `0f8b3f4e`.

---

## Fixes Applied

| Finding | Resolution | Commit |
|---------|-----------|--------|
| CR-01 | FIXED — strict `#RRGGBB` regex guard in `perceivedBrightness255`; non-hex input falls back instead of throwing | `22f1b3f8` |
| WR-01 | ACCEPTED (verification gap) — no faithful IT exists; one would need substantial new fixture scaffolding; code left as-is, no symptom workaround | — |
| WR-02 | FIXED — `relativeLuminance` → `perceivedBrightness255`, named threshold constants, one WHY comment | `22f1b3f8` |
| WR-03 | ACCEPTED / backlog — DTO fix needs new model variables, forbidden by the Phase 105 no-new-variable constraint; pre-existing pattern, not expanded | — |
| IN-01 | FIXED — `TeamCardServiceColorRobustnessTest` added (TDD red→green) | `59cf2eb6` |
| IN-02 | FIXED (guard only) — `*.bak` added to `.gitignore`; nothing to `git rm` | `0f8b3f4e` |

---

_Reviewed: 2026-05-29_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
