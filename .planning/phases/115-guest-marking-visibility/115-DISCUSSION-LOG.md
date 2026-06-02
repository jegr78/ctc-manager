# Phase 115: Guest Marking & Visibility - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-01
**Phase:** 115-Guest Marking & Visibility
**Areas discussed:** Marker treatment & decision process, What the marker conveys, Graphic coverage (MARK-03), Admin detail indicator (MARK-04), Graphic legend, Profile fielding-team display, Sub-team→parent display, Accent color & accessibility

---

## Marker Treatment & Decision Process

### How is the final marker visual decided?

| Option | Description | Selected |
|--------|-------------|----------|
| Candidates now, visual-approval in execution | Lock 2-3 candidates, choose against rendered reference during execution | |
| Lock direction now | Decide the concrete treatment here; visual check only confirms | ✓ |
| Claude discretion | Claude picks a sensible default and renders for approval | |

**User's choice:** Lock direction now.

### Which concrete treatment?

| Option | Description | Selected |
|--------|-------------|----------|
| "Guest" badge/pill | Text pill, can carry fielding team | |
| Asterisk `*` with legend | Minimal footprint + footnote | |
| Icon + accent color | Symbol + row/name color accent; language-neutral | ✓ |

**User's choice:** Icon + accent color.

### Same treatment everywhere or per-surface?

| Option | Description | Selected |
|--------|-------------|----------|
| Identical icon + same accent color everywhere | One glyph + one color across graphics/admin/site | ✓ |
| Same icon, color adapted per context | Same glyph, color varies for contrast | |
| Decide at render time | Claude picks and adjusts per surface | |

**User's choice:** Identical icon + same accent color everywhere.
**Notes:** Exact glyph + concrete color value remain planner discretion, chosen against the rendered reference; icon is the primary meaning carrier (WCAG).

---

## What the Marker Conveys

### Binary flag only, or also fielding/origin team?

| Option | Description | Selected |
|--------|-------------|----------|
| Flag + fielding team where space allows | Team on profile + admin, binary on graphics/ranking | |
| Binary flag everywhere | Team only on profile (MARK-06 requires it) | ✓ |
| Fielding team everywhere | Team also in graphics/ranking | |

**User's choice:** Binary flag everywhere; fielding team only on the profile.

### Ranking-row trigger (MARK-05)

| Option | Description | Selected |
|--------|-------------|----------|
| As soon as ≥ 1 guest appearance | Mark pure guest AND dual-role; identical admin + site | ✓ |
| Only pure-guest rows | Dual-role unmarked (misses MARK-05 partly) | |
| Claude decides | | |

**User's choice:** Mark as soon as ≥ 1 guest appearance.
**Notes:** Requires a `hasGuestAppearance` flag computed in `DriverRankingService` (service-side, no template logic).

---

## Graphic Coverage (MARK-03)

| Option | Description | Selected |
|--------|-------------|----------|
| All graphics with driver names (audit-driven) | Audit every `*-render`, mark all that show names | ✓ |
| Only the three named | Scorecard, Provisional, matchday-results | |
| Claude decides per graphic | | |

**User's choice:** All graphics with driver names — audit-driven.
**Notes:** Shared marker fragment + per-row `isGuest` DTO flag treated as planner discretion (low blast-radius architecture).

---

## Admin Detail Indicator (MARK-04)

| Option | Description | Selected |
|--------|-------------|----------|
| Same icon+accent as graphics, admin may be richer | Consistent marker + tooltip/column with fielding team | ✓ |
| Strictly identical to graphic | Binary only, no admin extra | |
| Claude decides | | |

**User's choice:** Same icon+accent as graphics; admin may be richer (tooltip/column with fielding team). Reuse `admin.css` classes + Phase-113 guest UI anchor.

---

## Graphic Legend

| Option | Description | Selected |
|--------|-------------|----------|
| Small legend only when a guest is present | Conditional caption "icon = Guest" | |
| Always-on fixed legend | Permanent legend element | |
| No legend — icon self-explanatory | No explanatory text | ✓ |

**User's choice:** No legend — icon self-explanatory.
**Notes:** Accepted tradeoff — the glyph must be intuitive since external viewers get no explanatory text.

---

## Profile Fielding-Team Display (MARK-06)

| Option | Description | Selected |
|--------|-------------|----------|
| Inline sub-label "as guest for TEAM" | Annotation on the guest race row | ✓ |
| Separate "Fielded for" column | New table column | |
| Claude decides | | |

**User's choice:** Inline sub-label "as guest for TEAM"; no new column.

---

## Sub-team → Parent Display

| Option | Description | Selected |
|--------|-------------|----------|
| Sub-team name (actual lineup) | Show the concrete sub-team | ✓ |
| Parent-team name | Consistent with points attribution | |
| Claude decides | | |

**User's choice:** Sub-team name (actual lineup).
**Notes:** Display-only — points attribution stays parent-rollup (Phase 114 D-02). Display ≠ attribution, no conflict.

---

## Accent Color & Accessibility

| Option | Description | Selected |
|--------|-------------|----------|
| Planner chooses central CSS variable, against reference | One CSS variable mirrored everywhere; WCAG-checked | ✓ |
| Reuse existing theme accent color | No new token | |
| Claude decides | | |

**User's choice:** Planner chooses a central CSS variable against the rendered reference.
**Notes:** Icon is primary meaning carrier; color is reinforcement only; color-blind-safe; WCAG contrast verified.

---

## Claude's Discretion

- Exact icon glyph + concrete accent-color value (against rendered reference; constraints in D-03/D-12).
- Shared marker fragment shape + where each graphic data service computes the per-row `isGuest` flag.
- Whether one "is this row a guest" resolver is shared across surfaces or per-surface.
- Exact admin fielding-team presentation (tooltip vs. small column).
- Placement of `hasGuestAppearance` on the `DriverRankingService` ranking row.

## Deferred Ideas

None — discussion stayed within phase scope. Phase 115 is the final phase of milestone v1.17.
