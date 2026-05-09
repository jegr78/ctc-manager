---
phase: 61
plan: 01
subsystem: docs
tags: [docs, roadmap, audit-trail, phase-61, scope-extension]
dependency_graph:
  requires: []
  provides:
    - "Authoritative Phase 61 SC1 wording (covers all 10 dropped seasons columns + 2 bridge FK columns + playoff_seasons table)"
    - "PROJECT.md Key-Decisions audit trail entry for D-01 scope extension"
  affects:
    - "Plan 61-02 (code cleanup) — references SC1 as authoritative scope"
    - "Plan 61-03 (V6 SQL migration) — references SC1 as authoritative scope"
tech_stack:
  added: []
  patterns:
    - "Plan-Phase pattern: ROADMAP-Update + PROJECT.md scope-decision as Plan 1 of phase (mirrors Phase 56-57 pre-step pattern)"
key_files:
  created: []
  modified:
    - ".planning/ROADMAP.md"
    - ".planning/PROJECT.md"
decisions:
  - "Match existing PROJECT.md Key-Decisions table shape (3 columns: Decision | Rationale | Outcome) per plan instruction line 119 — semantic equivalence preserved over header rewrite"
metrics:
  duration_minutes: 2
  completed_date: "2026-05-01"
  tasks_completed: 2
  files_modified: 2
  files_created: 0
requirements: [MIGR-06]
---

# Phase 61 Plan 01: Roadmap and Scope-Decision Update Summary

Pure docs change establishing the audit trail for Phase 61's extended cleanup scope: ROADMAP.md SC1 now lists `matchdays.season_id` and `playoffs.season_id` alongside the 8 seasons columns and the `playoff_seasons` join table, and PROJECT.md Key-Decisions documents the rationale ("denormalisiert + wartungsbelastend").

## What Changed

### `.planning/ROADMAP.md`

Phase 61 Success Criteria 1 (line 252) extended to include the two bridge FK columns:

**Before:**
> Flyway cleanup migration executes successfully, dropping `seasons.format`, ..., `seasons.match_scoring_id`, and the `playoff_seasons` join table; ...

**After:**
> Flyway cleanup migration executes successfully, dropping `seasons.format`, ..., `seasons.match_scoring_id`, **the `matchdays.season_id` and `playoffs.season_id` bridge FK columns**, and the `playoff_seasons` join table; ...

Goal sentence (line 244), `**Depends on**: Phase 60`, `**Requirements**: MIGR-06, QUAL-01, QUAL-02, QUAL-03`, and SC2/SC3/SC4 remain unchanged. No other phase section, table row, or details block was touched.

**Commit:** `7b0af03 docs(61-01): extend Phase 61 SC1 with bridge-column drops`

### `.planning/PROJECT.md`

Single new row appended to the v1.9 Key-Decisions table (3-column shape `Decision | Rationale | Outcome`). The plan template used a 4-column shape; per plan instruction line 119, the row was reshaped to match the existing in-file convention while keeping the semantic content (Phase 61 D-01, "denormalisiert + wartungsbelastend", `matchdays.season_id`, `playoffs.season_id`):

```
| Bridge-Spalten-Drop in V6 erweitert (Phase 61 D-01) | matchdays.season_id + playoffs.season_id sind denormalisiert + wartungsbelastend (vs. canonical Season → SeasonPhase → Matchday/Playoff); Phase 56 D-02 / Phase 57 SC5 superseded | Phase 61 (in progress) |
```

No existing rows modified.

**Commit:** `42b6a17 docs(61-01): append Phase 61 scope-extension to Key-Decisions table`

## Tasks

| # | Task                                                                       | Files                  | Commit    |
|---|----------------------------------------------------------------------------|------------------------|-----------|
| 1 | Extend ROADMAP.md Phase 61 SC1 with bridge-column drops                    | .planning/ROADMAP.md   | `7b0af03` |
| 2 | Append Phase 61 scope-extension entry to PROJECT.md Key-Decisions table    | .planning/PROJECT.md   | `42b6a17` |

## Verification Results

| Check                                                                  | Expected | Actual |
|------------------------------------------------------------------------|----------|--------|
| `grep -c 'matchdays\.season_id' .planning/ROADMAP.md`                  | 1        | 1      |
| `grep -c 'playoffs\.season_id' .planning/ROADMAP.md`                   | 1        | 1      |
| `grep -c '\*\*Goal\*\*: The old bridge columns and join table are removed' .planning/ROADMAP.md` | 1 | 1 |
| `grep -c '\*\*Depends on\*\*: Phase 60' .planning/ROADMAP.md`          | 1        | 1      |
| `grep -c 'seasons\.format' .planning/ROADMAP.md`                       | >=1      | 1      |
| `grep -c 'playoff_seasons' .planning/ROADMAP.md`                       | >=1      | 1      |
| `grep -c 'denormalisiert + wartungsbelastend' .planning/PROJECT.md`    | >=1      | 1      |
| `grep -c 'Phase 61 D-01' .planning/PROJECT.md`                         | >=1      | 1      |
| `grep -c 'matchdays\.season_id' .planning/PROJECT.md`                  | >=1      | 1      |
| `git diff --stat HEAD~2 HEAD .planning/ROADMAP.md .planning/PROJECT.md` (additive only) | additive | 2 files: +2/-1 (1 SC1 line replaced + 1 PROJECT.md row appended) |

All four verification commands from the plan pass:

1. `grep -c 'matchdays\.season_id' .planning/ROADMAP.md` returns `1`
2. `grep -c 'denormalisiert' .planning/PROJECT.md` returns `1`
3. `git diff --stat` shows only additive/replacement lines, no unrelated removals
4. ROADMAP.md still parses as valid Markdown (numbered list intact, table headers untouched)

## Tracked Behavior Change (D-23)

**Phase 61 scope extended beyond original ROADMAP-SC1 wording.** Bridge FK columns `matchdays.season_id` + `playoffs.season_id` will be dropped in V6 (rationale: denormalized + maintenance-burdening). Phase 56 D-02 / Phase 57 SC5 ("bridge columns remain") are superseded by this Plan 61-01 update. External consumers (if any) must migrate their queries from `matchdays.season_id` / `playoffs.season_id` to `JOIN season_phases ON matchdays.phase_id = season_phases.id` (or `playoffs.phase_id`) once V6 ships in Plan 61-03.

Plans 61-02 (code cleanup) and 61-03 (V6 SQL migration) can now quote the updated SC1 as authoritative scope. The PROJECT.md Key-Decisions row provides the audit trail for the verify-phase step.

## Deviations from Plan

**None — plan executed exactly as written**, with one documented adjustment per plan instruction line 119:

The plan template proposed a 4-column row for PROJECT.md Key-Decisions. The existing table is 3-column (`Decision | Rationale | Outcome`). Per the explicit plan fallback ("If the existing Key-Decisions table column headers differ ... match the existing shape EXACTLY — keep the row content semantically equivalent"), the row was reshaped to fit. All required content (D-01 reference, "denormalisiert + wartungsbelastend" rationale, `matchdays.season_id` mention) is preserved.

This is not a deviation — it is the plan-specified fallback path.

## Threat Flags

None — pure docs change, no security-relevant surface introduced.

## Self-Check: PASSED

Verified:

- `[ -f .planning/ROADMAP.md ]` — FOUND: ROADMAP.md is on disk and modified.
- `[ -f .planning/PROJECT.md ]` — FOUND: PROJECT.md is on disk and modified.
- `git log --oneline | grep -q '7b0af03'` — FOUND: `7b0af03 docs(61-01): extend Phase 61 SC1 with bridge-column drops`.
- `git log --oneline | grep -q '42b6a17'` — FOUND: `42b6a17 docs(61-01): append Phase 61 scope-extension to Key-Decisions table`.
- All verification grep commands return the expected counts.
- No modifications to STATE.md, ROADMAP.md other phase sections, or any code files.
