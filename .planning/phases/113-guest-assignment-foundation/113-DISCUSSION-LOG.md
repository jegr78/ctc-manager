# Phase 113: Guest Assignment Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-01
**Phase:** 113-Guest Assignment Foundation
**Areas discussed:** Flag placement, Guest-add UX, Typeahead mechanism, Fielding-team scope, Form transport & edit/remove, Orphaned results, Duplicate-assignment validation, Re-edit prefill, Sub-team guests

---

## Flag placement in the data model

| Option | Description | Selected |
|--------|-------------|----------|
| Nur RaceLineup | `is_guest` boolean on `race_lineups` only; RaceResult & views derive guest status via the existing lineup join. One source of truth. | ✓ |
| RaceLineup + RaceResult | Denormalize `is_guest` onto `race_results` too — faster access, but drift risk. | |

**User's choice:** Nur RaceLineup
**Notes:** Consistent with "RaceLineup is Source of Truth" and "No Fallback Calculations". New migration V18.

---

## Guest-add UX in the lineup form

| Option | Description | Selected |
|--------|-------------|----------|
| Add-Guest-Abschnitt pro Team | Per-team "Add Guest Driver" block with all-drivers picker + Add button, multiple removable rows. | ✓ (modified) |
| Ein Gast-Picker für die ganze Race | Single guest area with driver + team selects. | |
| Du entscheidest | Claude picks at planning time. | |

**User's choice:** Option 1 — but with a **Typeahead** instead of a full all-drivers `<select>`.
**Notes:** Triggered the follow-up Typeahead-mechanism question below.

---

## Typeahead mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Native `<datalist>` | `<input list>` + `<datalist>` of all drivers, browser does the filtering; hidden field holds driverId. No new endpoint, no JS framework. | ✓ |
| JS-Autocomplete + Such-Endpoint | New `/admin/drivers/search` JSON endpoint + fetch-based autocomplete. | |
| Du entscheidest | Claude picks at planning time. | |

**User's choice:** Native `<datalist>`
**Notes:** Fits Thymeleaf SSR / no frontend build tool / Spring-native preference.

---

## Fielding-team scope

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Home/Away der Race | Guest team restricted to the race's two teams; sub-teams analogous to existing logic. | ✓ |
| Jedes Team im System | Guest could be fielded for any team. | |

**User's choice:** Nur Home/Away der Race
**Notes:** Consistent with home-vs-away race structure; a race-foreign team would break standings in Phase 114.

---

## Form transport & edit/remove

| Option | Description | Selected |
|--------|-------------|----------|
| Eigener guest-Param-Namespace + delete/recreate | `guest_<driverId>=teamId` alongside roster `driver_<id>=teamId`; delete-all-recreate stays; `is_guest=true` for guests. | ✓ |
| Separate Add/Remove-Endpoints für Gäste | Dedicated incremental CRUD endpoints; roster save must spare guests. | |
| Du entscheidest | Claude picks at planning time. | |

**User's choice:** Eigener guest-Param-Namespace + delete/recreate
**Notes:** Minimal-invasive to existing saveLineup logic.

---

## Orphaned results on guest removal

| Option | Description | Selected |
|--------|-------------|----------|
| Result mitlöschen | Cascade-delete the guest's RaceResult transactionally on removal, then aggregateMatchScores. | ✓ |
| Entfernen blockieren wenn Result existiert | Block removal until the result is deleted first. | |
| Du entscheidest | Claude picks at planning time. | |

**User's choice:** Result mitlöschen
**Notes:** Prevents orphaned results that fall back to `"?"` team in toRaceData; follows "Score Aggregation on Result Save".

---

## Duplicate-assignment validation

| Option | Description | Selected |
|--------|-------------|----------|
| Service-Validierung + DB-Unique | New `UNIQUE(race_id, driver_id)` on race_lineups + service-layer dedup check. | ✓ |
| Nur Service-Validierung | App-level check only, no DB constraint. | |
| Du entscheidest | Claude picks at planning time. | |

**User's choice:** Service-Validierung + DB-Unique
**Notes:** Planner must verify no existing duplicate `(race_id, driver_id)` rows before adding the constraint.

---

## Re-edit: prefill existing guests

| Option | Description | Selected |
|--------|-------------|----------|
| Service liefert getrennte Gast-Liste | Service returns existing `is_guest=true` lineups per team; template prefills guest rows. | ✓ |
| Du entscheidest | Claude picks at planning time. | |

**User's choice:** Service liefert getrennte Gast-Liste
**Notes:** Roster assignments continue via the existing driver→team map.

---

## Sub-team guests

| Option | Description | Selected |
|--------|-------------|----------|
| Sub-Team-Selector im Add-Guest-Block | Teams with sub-teams get an extra sub-team `<select>`; guest assigned to the concrete sub-team. | ✓ |
| Gast immer auf Parent-Team | Guest always assigned to the parent team. | |
| Du entscheidest | Claude picks at planning time. | |

**User's choice:** Sub-Team-Selector im Add-Guest-Block
**Notes:** Mirrors existing sub-team lineup semantics; avoids skewing Phase 114 scoring.

---

## Claude's Discretion

- Exact `<datalist>`→`driverId` resolution mechanism.
- Exact service/record signatures; whether the guest accessor extends `getLineupData` or is a new method.
- Whether `RaceLineup` gets a 4-arg constructor or a setter for the flag (keep the 3-arg constructor working for roster callers).

## Deferred Ideas

- Guest scoring & personal driver-ranking crediting → Phase 114 (SCORE-01..03).
- Visual guest marking across graphics, admin views, public site → Phase 115 (MARK-01..06).
