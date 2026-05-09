---
phase: 67-comment-cleanup-resweep
plan: 02
subsystem: templates
tags: [cleanup, comment-policy, templates, thymeleaf]
requires:
  - "Plan 67-01 production sweep complete (no regression on prod-side D-19 gates)"
provides:
  - "Templates-side comment policy re-baseline (`src/main/resources/templates`)"
  - "Zero decision-attribution markers in any HTML comment across 79 templates"
affects: []
tech-stack:
  added: []
  patterns: ["per-comment classification — keep section labels, strip attribution prefixes/suffixes (D-12)"]
key-files:
  created:
    - .planning/phases/67-comment-cleanup-resweep/67-02-SUMMARY.md
  modified:
    - src/main/resources/templates/admin/season-detail.html
    - src/main/resources/templates/admin/driver-import-preview.html
    - src/main/resources/templates/admin/standings.html
    - src/main/resources/templates/site/index.html
decisions:
  - "Two atomic per-file-pair commits (Task 1 + Task 2) followed by a SUMMARY commit (Task 3) — all on the worktree branch"
  - "Inline rewrite preserved every comment LINE — only contents changed (14/12/7/2 comment counts unchanged pre/post sweep). 28 insertions / 28 deletions."
  - "Pre-existing German section labels (`Saison-Header`, `Saison-Stamm-Display`) intentionally left untranslated — out of scope per Plan 67-02 attribution-strip-only contract; CONTEXT.md `<deferred>` flags comment-language audit as separate concern"
  - "`Bucket: New Drivers (D-39 inline badge, D-40 conditional Group column)` resolved via token-strip (kept descriptive parenthetical) rather than full parenthetical deletion — preserves more semantic content per D-12 case-by-case judgement"
metrics:
  completed: 2026-05-07
  task_count: 3
  file_count: 4
  tests_run: 1231
  tests_passing: 1231
---

# Phase 67 Plan 02: Templates Comment Cleanup Re-Sweep Summary

Stripped decision-attribution noise (`D-XX`, `Plan NN-NN`, `Phase NN`, `Pitfall N`,
`IMPORT-NN`, `UX-NN`, `YT-NN`, `B-N`) from 4 of 79 Thymeleaf templates while preserving
every structural section label intact. Pure inline-rewrite approach: 28 lines changed,
0 lines net delta — every original `<!-- ... -->` line still exists, just with attribution
tokens removed.

## Plan Goal

Apply the same CLAUDE.md comment-policy enforcement that Plan 67-01 applied to
`src/main/java` to the SSR view layer. Per RESEARCH.md inventory, only 4 of 79
templates carry attribution noise; the remaining 75 are clean section-label discipline
and were not touched.

## Files Swept (4 of 79 templates)

| File | `<!--` count (unchanged) | Noise rewrites | Section labels preserved |
|------|---:|---:|---:|
| `admin/season-detail.html` | 14 → 14 | 12 inline rewrites | 2 (`SeasonTeam Edit Modal`, `Replace Team Modal`) |
| `admin/driver-import-preview.html` | 12 → 12 | 9 inline rewrites | 3 (`Season dropdown`, `Bucket: Errors`, `Execute button`) |
| `admin/standings.html` | 7 → 7 | 5 inline rewrites | 2 (`Empty: no season selected and not alltime`, `Driver Ranking — render only when ...`) |
| `site/index.html` | 2 → 2 | 2 inline rewrites | 0 (both lines were attribution-tagged hybrids; both rewritten) |
| **TOTAL** | **35 → 35** | **28** | **7** |

Note: RESEARCH.md tally (9 + 9 + 5 + 1 = 24) counted attribution-bearing comments per
file; the SUMMARY tally (12 + 9 + 5 + 2 = 28) counts edit operations applied. The
discrepancy in `season-detail.html` (12 edits vs 9 reported attribution comments) comes
from comments that included section-label bodies *plus* attribution — those count once
each in RESEARCH.md but each is one edit here. The 4-file scope and `0` post-sweep
attribution-token count match RESEARCH.md.

## Per-File Edit Detail

### `admin/season-detail.html` (12 inline rewrites)

| Line | Before → After |
|---:|---|
| 6 | `Saison-Header (D-02, D-07): saison-wide actions only` → `Saison-Header: saison-wide actions only` |
| 21 | `Saison-Stamm-Display (D-15)` → `Saison-Stamm-Display` |
| 259 | `D-08 Empty-State: no REGULAR phase` → `Empty-State: no REGULAR phase` |
| 266 | `D-01 Phase-Tabs Row 1 (only when REGULAR phase exists)` → `Phase-Tabs Row 1 (only when REGULAR phase exists)` |
| 277 | `D-29 Group-Sub-Tabs Row 2 (only when GROUPS layout)` → `Group-Sub-Tabs Row 2 (only when GROUPS layout)` |
| 292 | `Per-Phase content (D-13 sub-title, D-04 sections)` → `Per-Phase content` |
| 295 | `Per-Phase Header with Edit/Delete actions (D-07)` → `Per-Phase Header with Edit/Delete actions` |
| 307 | `D-04 Roster Section` → `Roster Section` |
| 326 | `D-20 Multi-select Roster Editor (inline; submits to ...)` → `Multi-select Roster Editor (inline; submits to ...)` |
| 366 | `B-3 PLAYOFF Bracket Section (only when PLAYOFF layout)` → `PLAYOFF Bracket Section (only when PLAYOFF layout)` |
| 388 | `D-04 Matchdays Section (placeholder; full content in Plan 60-06)` → `Matchdays Section` |
| 399 | `D-04 Standings Section (placeholder; full UI in Plan 60-05)` → `Standings Section` |

KEEP unchanged: lines 95 (`SeasonTeam Edit Modal`), 155 (`Replace Team Modal`).

### `admin/driver-import-preview.html` (9 inline rewrites)

| Line | Before → After |
|---:|---|
| 13 | `D-37 / Pitfall 10: heading uses raw tabName ...` → `heading uses raw tabName ...` |
| 16 | `D-38: ambiguous-tab plain banner — clear, never hidden ...` → `ambiguous-tab plain banner — clear, never hidden ...` |
| 36 | `TabWarning badges: ... (IMPORT-04 / D-06)` → `TabWarning badges: ...` |
| 46 | `Bucket: New Drivers (D-39 inline badge, D-40 conditional Group column)` → `Bucket: New Drivers (inline badge, conditional Group column)` |
| 70 | `Bucket: New Assignments (D-39, D-40)` → `Bucket: New Assignments` |
| 94 | `Bucket: Conflicts (Skip checkbox) — UX-07, D-40` → `Bucket: Conflicts (Skip checkbox)` |
| 128 | `Bucket: Fuzzy Match Suggestions (Accept checkbox) — UX-08, D-40` → `Bucket: Fuzzy Match Suggestions (Accept checkbox)` |
| 162 | `Bucket: Unchanged (display-only, no DB write on execute) — D-40` → `Bucket: Unchanged (display-only, no DB write on execute)` |
| 206 | `Ambiguous-season warning banner — D-16` → `Ambiguous-season warning banner` |

KEEP unchanged: lines 23 (`Season dropdown`), 186 (`Bucket: Errors (display-only, never imported)`), 213 (`Execute button — outside per-tab loop, at form bottom`).

### `admin/standings.html` (5 inline rewrites)

| Line | Before → After |
|---:|---|
| 27 | `D-29 Two-Row Tabs Row 1: Phase tabs (only when season + REGULAR phase)` → `Two-Row Tabs Row 1: Phase tabs (only when season + REGULAR phase)` |
| 36 | `D-29 Two-Row Tabs Row 2: Group sub-tabs (only when GROUPS layout)` → `Two-Row Tabs Row 2: Group sub-tabs (only when GROUPS layout)` |
| 54 | `D-08 + Pitfall 4: season selected but no REGULAR phase` → `Empty-state: season selected but no REGULAR phase` |
| 60 | `D-36 Empty-state: phase exists but no results` → `Empty-state: phase exists but no results` |
| 65 | `Team Standings table (unified with conditional columns D-32, D-33)` → `Team Standings table (unified with conditional columns)` |

KEEP unchanged: lines 49 (`Empty: no season selected and not alltime`), 104 (`Driver Ranking — render only when driverRanking is non-null + non-empty`).

### `site/index.html` (2 inline rewrites)

| Line | Before → After |
|---:|---|
| 6 | `Hero with YouTube background video (Phase 51: YT-01, YT-02)` → `Hero with YouTube background video` |
| 62 | `Tile Navigation (D-10, D-11, D-12, D-13)` → `Tile Navigation` |

## D-19 Templates-Side Grep Gate (post-commit)

```
grep -rE "<!--.*\b(D-[0-9]+|Plan [0-9]+-[0-9]+|Phase [56][0-9]|Pitfall [0-9]+|IMPORT-[0-9]+|UX-[0-9]+|YT-[0-9]+|B-[0-9]+)\b" src/main/resources/templates | wc -l
=> 0
```

## D-19 Production-Side Regression Check (Plan 67-01 gates still clean)

```
Gate 1 (// Phase [56][0-9]):       0
Gate 4 (long ^\s*// ---):          0
```

Plan 67-01's production gates uncompromised — no regression introduced by this plan.

## Whitelist Preservation Evidence

| Anchor | File | Expected | Actual |
|--------|------|---:|---:|
| `Hero with YouTube background video` | `site/index.html` | ≥ 1 | 1 |
| `Tile Navigation` | `site/index.html` | ≥ 1 | 1 |
| `Phase tabs` (Two-Row Tabs body + aria-label) | `admin/standings.html` | ≥ 1 | 2 |
| `Bucket: Errors` (display-only never imported) | `admin/driver-import-preview.html` | ≥ 1 | 1 |
| `SeasonTeam Edit Modal` + `Replace Team Modal` | `admin/season-detail.html` | ≥ 1 each | 1 + 1 = 2 |

## Non-Comment-Change Scan (D-18)

```
git diff HEAD~2..HEAD -- 'src/main/resources/templates/**/*.html' \
  | grep -E '^[+-][^+-]' \
  | grep -vE '^[+-]\s*<!--' \
  | grep -vE '^[+-]\s*-->' \
  | grep -vE '^[+-]\s*$'
=> (empty output)
```

Every changed line is a comment line. Zero Thymeleaf attribute changes, zero
template-engine syntax touched, zero behavior delta possible.

## Behavior Gate (D-17)

- `./mvnw test` → BUILD SUCCESS, exit 0.
- `Tests run: 1231, Failures: 0, Errors: 0, Skipped: 4` — unchanged from Phase 66 baseline (1231) and Plan 67-01 baseline (1231).
- JaCoCo not run in this plan per D-17 (deferred to Plan 67-03 final `./mvnw verify`). Comments in HTML cannot affect Java bytecode coverage.

## Out of Scope (Documented for the Verifier)

- **74 other templates** — RESEARCH.md inventory confirms zero decision-attribution noise; not touched. Their `<!-- Errors -->`, `<!-- Generate Graphics -->`, `<!-- Lineup Button -->`, `<!-- Multi-race header -->` style structural labels stay verbatim.
- **Thymeleaf parser comments `<!--/* */-->`** — RESEARCH.md confirms zero exist in the codebase. D-11 had no work in this plan.
- **Pre-existing German section labels** (`Saison-Header`, `Saison-Stamm-Display` in `season-detail.html`) — out of scope per Plan 67-02 attribution-strip contract. CLAUDE.md mandates English code/comments; this is a comment-language audit concern flagged as a `<deferred>` item in 67-CONTEXT.md, separate from the attribution-policy enforcement of this plan.
- **Tests** — Plan 67-03 territory.

## Commits

| # | Hash | Subject |
|---:|---|---|
| 1 | `5502de4` | `style(67-02): strip attribution noise from season-detail + driver-import-preview templates` |
| 2 | `fa4a352` | `style(67-02): strip attribution noise from standings + site/index templates` |
| 3 | (this SUMMARY commit) | `docs(67-02): plan summary — templates sweep, 4 files, 28 inline rewrites, 0 attribution tokens remain` |

Cumulative diff: `4 files changed, 28 insertions(+), 28 deletions(-)`.

## Self-Check: PASSED

- [x] All 4 listed template files modified (verified via `git diff --stat HEAD~2..HEAD`)
- [x] Commits `5502de4` and `fa4a352` exist in `git log --oneline -3`
- [x] Templates-side D-19 grep gate returns `0`
- [x] Plan 67-01 production gates (Gate 1 / Gate 4) still return `0` — no regression
- [x] Whitelist anchors present (Hero, Tile Navigation, Phase tabs, Bucket: Errors, Modals)
- [x] `./mvnw test` exits 0 with `Tests run: 1231, Failures: 0, Errors: 0, Skipped: 4`
- [x] Non-comment-change scan returned empty — pure comments-only diff
- [x] No file deletions (post-edit `git status --short` only shows `M` entries for the 4 templates)
- [x] No modifications to STATE.md / ROADMAP.md (per orchestrator contract)
- [x] No modifications to any of the 22 files touched by Plan 67-01
- [x] No modifications to any of the 75 templates outside the offender list
