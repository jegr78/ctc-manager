# Phase 38: Season Content & Data Filtering - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-16
**Phase:** 38-season-content-data-filtering
**Areas discussed:** Season display format, Test season filter scope, Match-meta visibility, Archive period handling
**Mode:** --auto (all decisions auto-selected)

---

## Season Display Format

| Option | Description | Selected |
|--------|-------------|----------|
| Formatted subtitle | Year/number as subtitle/badge below main heading | ✓ |
| Replace name with displayLabel | Use getDisplayLabel() as the main heading | |
| Year prefix | Prefix all headings with year (e.g., "2025 — CTC Season 3") | |

**User's choice:** [auto] Formatted subtitle (recommended default)
**Notes:** Keeps existing heading structure intact. Year and number enrich the display without breaking existing layout expectations. Season name remains the primary identifier.

---

## Test Season Filter Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Filter in archive AND page generation | Don't generate any pages for test seasons | ✓ |
| Filter only in archive listing | Still generate pages but hide from archive | |
| Filter nowhere (manual cleanup) | Leave filtering to manual data management | |

**User's choice:** [auto] Filter in both archive AND page generation (recommended default)
**Notes:** Prevents stale test directories in output. Cleaner site with no dead links to test content.

---

## Match-Meta Visibility

| Option | Description | Selected |
|--------|-------------|----------|
| Hide entire div when both null | Add th:if guard on match-meta div | ✓ |
| Always show div (empty) | Keep div visible even when empty | |
| Show placeholder text | Display "TBD" when track/car missing | |

**User's choice:** [auto] Hide entire match-meta div when both track and car are null (recommended default)
**Notes:** Cleanest visual result. No empty space below match teams when data is missing.

---

## Archive Period Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Hide per-row when null | Keep column, show em-dash or blank for missing dates | ✓ |
| Hide column entirely | Remove Period column from archive table | |
| Show "N/A" placeholder | Display explicit placeholder text | |

**User's choice:** [auto] Hide per-row when dates are null (recommended default)
**Notes:** Period column provides useful context when dates exist. Per-row hiding handles missing data gracefully.

---

## Claude's Discretion

- CSS styling for season year/number subtitle elements
- Whether to add dedicated template variables for formatted season info vs. using entity getters
- Stream filter placement for test season exclusion

## Deferred Ideas

None — discussion stayed within phase scope
