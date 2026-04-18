# Phase 47: Teams & Drivers Overview Pages - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.

**Date:** 2026-04-17
**Phase:** 47-teams-drivers-overview-pages
**Areas discussed:** Teams Data, Drivers Data, Season Filter, Profile Linking, Layout
**Mode:** --auto

---

## Teams Data Structure

| Option | Description | Selected |
|--------|-------------|----------|
| Short name + logo + season tags | Essential info, compact cards | ✓ |
| Full name + stats per season | Detailed but heavy | |
| Name only with expand toggle | Minimalist but hides info | |

**User's choice:** [auto] Short name + logo + season tags (recommended)

---

## Drivers Data Structure

| Option | Description | Selected |
|--------|-------------|----------|
| PSN ID + team tags + season tags | Shows identity, affiliation, participation | ✓ |
| PSN ID + stats summary | Requires aggregation across seasons | |
| PSN ID only with season links | Too minimal | |

**User's choice:** [auto] PSN ID + team tags + season tags (recommended)

---

## Season Filter Mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| data-seasons + select + vanilla JS | Minimal, no framework, fast | ✓ |
| Client-side search/filter library | Overkill for static site | |
| Generate per-season pages | Multiplies page count, complex | |

**User's choice:** [auto] data-seasons + select + vanilla JS (recommended)

---

## Profile Linking

| Option | Description | Selected |
|--------|-------------|----------|
| Link to most recent season profile | Shows latest data | ✓ |
| Link to first season profile | Historical but outdated | |
| Dropdown per entity to choose season | Complex UX | |

**User's choice:** [auto] Most recent season profile (recommended)

---

## Layout Design

| Option | Description | Selected |
|--------|-------------|----------|
| Responsive grid (2col/1col) | Consistent with match-grid | ✓ |
| Table layout | Structured but not card-like | |
| Single column list | Simple but wastes space | |

**User's choice:** [auto] Responsive grid (recommended)

## Claude's Discretion

- Filter JS details, sort order, breadcrumb text, CSS refinements

## Deferred Ideas

None
