---
phase: 108-missing-driver-n-a-rendering
reviewed: 2026-05-30T00:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - src/main/java/org/ctc/admin/service/AbstractGraphicService.java
  - src/main/java/org/ctc/admin/service/LineupGraphicService.java
  - src/main/java/org/ctc/admin/service/ResultsGraphicService.java
  - src/main/java/org/ctc/admin/service/ProvisionalScoresGraphicService.java
  - src/main/resources/templates/admin/lineup-render.html
  - src/main/resources/templates/admin/results-render.html
  - src/main/resources/templates/admin/provisional-scores-render.html
  - src/test/java/org/ctc/admin/service/LineupGraphicServiceTest.java
  - src/test/java/org/ctc/admin/service/ResultsGraphicServiceTest.java
  - src/test/java/org/ctc/admin/service/ProvisionalScoresGraphicServiceTest.java
  - src/test/java/org/ctc/domain/service/ScoringServiceTest.java
findings:
  critical: 0
  warning: 1
  info: 0
  total: 1
status: resolved
resolution: "WR-01 fixed in commit 1943e836 (--fix); clean verify -Pe2e green afterwards (1773 unit + 529 IT + 115 E2E, coverage met, SpotBugs 0)."
---

# Phase 108: Code Review Report

**Reviewed:** 2026-05-30
**Depth:** standard
**Files Reviewed:** 11
**Resolution:** WR-01 fixed (`1943e836`) — `winner` class now applies only to real-driver result rows, so an n/a points slot is rendered with only `empty-slot` (dimmed grey), matching the user-approved visual. Re-ran `./mvnw clean verify -Pe2e` → green.
**Status:** 1 warning, no critical issues

## Summary

Phase 108 adds render-time padding-to-6 across the three graphic services and their templates.
The core logic is correct: `TEAM_DRIVERS = 6` is a single constant in `AbstractGraphicService`;
`buildPairings` and `buildResultRows` always return exactly 6 rows; `emptyRow()` produces
`"n/a"` with all-zero numerics; the `ProvisionalScoresGraphicService` pads before the sum
streams so totals are unaffected. Templates migrated from `!= ''` to `!= 'n/a'` guards
cleanly; no `!= ''` remnants remain; no inline `style=""` attributes were added; no JS
`className` manipulation exists in any of the three templates; no Flyway/persistence change
leaked in; `ScoringService.java` is untouched by this phase (confirmed via git log).

The `@SuppressWarnings("unchecked")` cast in `ProvisionalScoresGraphicServiceTest` (line 200)
is the minimal necessary cast for `Context.getVariable` returning `Object` — no cleaner approach
exists without reflection; this is acceptable.

One warning is raised for a visual inconsistency in `results-render.html` where the `'winner'`
CSS class is applied to n/a point slots, combining dimming and the gold winner colour on the
same span. This was within the scope of the user-approved visual checkpoint ("Passt") but was
not explicitly reviewed for the winner+n/a overlap scenario.

---

## Warnings

### WR-01: `winner` class applied to n/a point spans — gold colour overrides opacity dimming

**File:** `src/main/resources/templates/admin/results-render.html:102-103`

**Issue:** The `th:classappend` expressions on the two `.points` spans concatenate the winner
flag with the empty-slot flag via string addition:

```html
th:classappend="${(homeIsWinner ? 'winner ' : '') + (row.homeDriver == 'n/a' ? 'empty-slot' : '')}"
```

When a team wins **and** a slot is `n/a`, the resulting class list is `'winner empty-slot'`.
The CSS rules `.points.winner { color: #f5c542 }` and `.empty-slot { opacity: 0.32 }` both
apply, so the dimmed `0` for the missing driver also receives the gold winner colour.
This makes the n/a zero visually identical to a real scored winner row apart from opacity,
which can be confusing — the visual intent (D-05) is that n/a slots are de-emphasised as
"not a real driver".

**Fix:** Guard the `winner` class so it only applies to real-driver rows:

```html
<span class="points"
  th:classappend="${row.homeDriver != 'n/a' and homeIsWinner ? 'winner ' : ''}
                 + ${row.homeDriver == 'n/a' ? 'empty-slot' : ''}"
  th:text="${row.homeDriver != 'n/a' ? row.homePoints : 0}"></span>
<span class="points"
  th:classappend="${row.awayDriver != 'n/a' and awayIsWinner ? 'winner ' : ''}
                 + ${row.awayDriver == 'n/a' ? 'empty-slot' : ''}"
  th:text="${row.awayDriver != 'n/a' ? row.awayPoints : 0}"></span>
```

This ensures n/a slots are always rendered with only the `empty-slot` treatment and never
receive the gold winner decoration.

---

## Structural Findings (fallow)

None — no structural pre-pass was provided.

---

_Reviewed: 2026-05-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
